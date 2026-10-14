// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.metadata

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Song
import kr.co.metadata.crawler.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class CatalogEnrichmentService private constructor(private val context: Context) {
    val preferences = CatalogPreferences(context)
    val root = File(context.filesDir, "music_metadata")
    private val store = CatalogFileStore(File(root, "records"))
    private val temporary = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, StoredCatalog>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StoredCatalog>?): Boolean = size > 256
    })
    private val projections = ConcurrentHashMap<String, StoredCatalog>()
    private val entityLocks = ConcurrentHashMap<String, Mutex>()
    private val audioLocks = ConcurrentHashMap<String, Mutex>()
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    @Volatile var activeSongId: String? = null
        private set
    private val recentlyActive = ConcurrentHashMap<String, Long>()
    fun setActiveSong(id: String?) {
        if (id != activeSongId) activeSongId?.let { recentlyActive[it] = System.currentTimeMillis() + 30_000 }
        activeSongId = id
        recentlyActive.entries.removeIf { it.value <= System.currentTimeMillis() }
    }
    fun audioBusy(song: Song) = song.id == activeSongId || (recentlyActive[song.id] ?: 0) > System.currentTimeMillis()
    private val cooldownPrefs = context.getSharedPreferences("catalog_cooldowns", Context.MODE_PRIVATE)
    val http = PageClient(minInterval = Duration.ofSeconds(3), backoff = HostBackoff(persistence = object : CooldownPersistence {
        override fun read(host: String): Long = cooldownPrefs.getLong(host, 0)
        override fun write(host: String, until: Long) { cooldownPrefs.edit { putLong(host, until) } }
    }))
    private val search = CatalogSearch(http)
    private val providers: Map<Provider, MusicProvider> = listOf(BugsMobileProvider(http), BugsPcProvider(http),
        GenieProvider(http), MelonProvider(http)).associateBy { it.provider }

    fun onlineArtworkEnabled() = enabled() && preferences.options.value.artwork && preferences.options.value.artworkSource !in setOf("LOCAL_ONLY", "LEGACY", "DISABLED") && preferences.options.value.providers.isNotEmpty()
    fun enabled() = preferences.options.value.enabled && AppSettings.getInstance(context).integrationsEnabled.value
    fun songKey(song: Song): String = song.path?.takeIf(String::isNotBlank) ?: song.uri.toString().substringBefore('?')
    fun albumKey(title: String, artist: String) = "${CatalogIdentity.normalize(artist)}:${CatalogIdentity.normalize(title)}"
    fun artistKey(name: String) = CatalogIdentity.normalize(name)
    private fun cacheKey(kind: EntityKind, key: String) = "${kind.name}:$key"
    fun read(kind: EntityKind, key: String): StoredCatalog? = temporary[cacheKey(kind, key)]
        ?: store.read(kind, key)?.also { temporary[cacheKey(kind, key)] = it }
    private fun projection(kind: EntityKind, key: String): StoredCatalog = projections.computeIfAbsent(cacheKey(kind, key)) {
        compact(read(kind, key) ?: StoredCatalog(CatalogDocument(kind, emptyList())))
    }
    private fun compact(record: StoredCatalog) = record.copy(document = record.document.copy(values = record.document.values.filter {
        it.field !in setOf(CatalogField.TRACK_LYRICS, CatalogField.ARTIST_DESCRIPTION, CatalogField.ALBUM_DESCRIPTION)
    }))
    fun artworkRemoved(song: Song) = context.getSharedPreferences("artwork_overrides", Context.MODE_PRIVATE)
        .getBoolean("removed_${song.id}", false)
    fun acceptForcedArtwork(song: Song, uri: Uri?) {
        if (preferences.options.value.replaces(CatalogField.TRACK_IMAGE) && uri?.scheme == "file" && usableArtwork(uri)) {
            context.getSharedPreferences("artwork_overrides", Context.MODE_PRIVATE).edit { remove("removed_${song.id}") }
        }
    }
    fun effectiveAlbumArtist(song: Song) = song.albumArtist?.takeUnless(CatalogIdentity::missing) ?: song.artist
    fun image(record: StoredCatalog?): Uri? = record?.imageFile?.let {
        val file = File(File(root, "images"), File(it).name)
        if (file.isFile) Uri.fromFile(file) else null
    }
    fun usableArtwork(uri: Uri?): Boolean {
        if (uri == null) return false
        return when (uri.scheme) {
            "file" -> uri.path?.let { path -> File(path).isFile && !(path.startsWith(context.cacheDir.path) &&
                File(path).name.contains("placeholder", ignoreCase = true)) } == true
            "http", "https" -> true
            "content" -> runCatching { context.contentResolver.openInputStream(uri)?.use { it.read() >= 0 } == true }.getOrDefault(false)
            else -> false
        }
    }
    private fun changed() { revision.update { it + 1 }; PersonalArchive.enqueue(context) }
    private fun save(kind: EntityKind, key: String, document: CatalogDocument, retryAfter: Long,
                     imageFile: String? = null, replace: Boolean = false, attemptedFields: Set<CatalogField> = emptySet(), reset: Boolean = false, matchingPolicy: String? = null): StoredCatalog {
        val result = if (preferences.options.value.permanent) store.merge(kind, key, document, retryAfter, imageFile, replace, attemptedFields, reset, matchingPolicy)
            else {
                val existing = if (reset) null else read(kind, key)
                val fields = existing?.document?.values.orEmpty().associateBy { it.field }.toMutableMap()
                document.values.filter { replace || it.hasValue() }.forEach { if (replace || fields[it.field]?.hasValue() != true) fields[it.field] = it }
                StoredCatalog(CatalogDocument(kind, fields.values.toList()), retryAfter, imageFile = imageFile ?: existing?.imageFile,
                    attemptedFields = attemptedFields + existing?.attemptedFields.orEmpty(), matchingPolicy = matchingPolicy ?: existing?.matchingPolicy)
            }
        temporary[cacheKey(kind, key)] = result
        projections[cacheKey(kind, key)] = compact(result)
        if (preferences.options.value.permanent && kind != EntityKind.TRACK) {
            val nameField = if (kind == EntityKind.ARTIST) CatalogField.ARTIST_NAME else CatalogField.ALBUM_TITLE
            val name = result.document[nameField]?.takeIf(String::isNotBlank) ?: key
            val safe = name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(70)
            val folder = File(root, "entities/${if (kind == EntityKind.ARTIST) "artists" else "albums"}/$safe-${CatalogFileStore.hash(key).take(12)}").apply { mkdirs() }
            val json = com.google.gson.JsonObject().apply {
                addProperty("key", key); addProperty("kind", kind.name)
                add("document", com.google.gson.JsonParser.parseString(CatalogDocumentCodec.encode(result.document)))
            }
            PersonalArchive.atomicWrite(File(folder, "metadata.json"), json.toString().toByteArray())
            val description = result.document[if (kind == EntityKind.ARTIST) CatalogField.ARTIST_DESCRIPTION else CatalogField.ALBUM_DESCRIPTION]
            description?.let { PersonalArchive.atomicWrite(File(folder, "about.txt"), it.toByteArray()) }
            image(result)?.path?.let { path ->
                val destination = File(folder, "artwork.webp")
                if (!destination.exists() || destination.lastModified() < File(path).lastModified()) {
                    if (File(path).extension == "webp") PersonalArchive.atomicWrite(destination, File(path).readBytes())
                    else chromahub.rhythm.app.util.ArtworkStorage.convertFile(File(path), destination)
                }
            }
        }
        changed()
        return result
    }

    /** Stable service order, exact identities and duration corroboration for audio writes. */
    suspend fun collect(kind: EntityKind, key: String, name: String, artist: String?, wanted: Set<CatalogField>,
                        durationMs: Long = 0, album: String? = null, selectedProviders: Set<Provider>? = null,
                        replace: Boolean = false): StoredCatalog? = withContext(Dispatchers.IO) {
        val selection = selectedProviders ?: preferences.options.value.providers
        val policy = selection.sortedByDescending { it.priority }.joinToString { it.name } + if (kind == EntityKind.TRACK) ":${durationMs}:${preferences.options.value.strictDuration}:${preferences.options.value.durationTolerancePercent}" else ""
        val recordKey = if (selectedProviders == null) key else "source:${selection.sortedByDescending { it.priority }.joinToString { it.name }}:$key"
        entityLocks.computeIfAbsent(cacheKey(kind, recordKey)) { Mutex() }.withLock {
            var record = read(kind, recordKey)
            val lengthVerified = kind != EntityKind.TRACK || durationMs <= 0 || !preferences.options.value.strictDuration || CatalogIdentity.durationMatches(durationMs, record?.document?.get(CatalogField.TRACK_DURATION_MS)?.toLongOrNull(), preferences.options.value.durationTolerancePercent)
            if ((!replace && lengthVerified && wanted.all { record?.document?.has(it) == true }) || !enabled() || CatalogIdentity.missing(name)) return@withLock record
            if (!replace && record?.matchingPolicy == policy && (record?.retryAfter ?: 0) > System.currentTimeMillis() && wanted.all {
                    record?.document?.has(it) == true || it in record?.attemptedFields.orEmpty()
                }) return@withLock record.takeIf { lengthVerified }
            val values = record?.document?.values.orEmpty().associateBy { it.field }.toMutableMap()
            if (!lengthVerified) values.clear() else if (replace) wanted.forEach(values::remove)
            for (provider in selection.intersect(preferences.options.value.providers).sortedByDescending { it.priority }) {
                if (wanted.all { values[it]?.hasValue() == true }) break
                val query = listOfNotNull(name, artist?.takeUnless(CatalogIdentity::missing)).joinToString(" ")
                val hits = search.search(provider, kind, query, 8)
                val candidates = if (kind == EntityKind.TRACK) hits.filter {
                    CatalogIdentity.titleMatches(name, it.title) && (it.artists == null || artist != null && CatalogIdentity.artistMatches(artist, it.artists.orEmpty()))
                }.distinctBy { it.providerId }.take(3) else listOfNotNull(CatalogIdentity.select(hits, name, artist))
                val source = providers.getValue(provider)
                val documents = candidates.mapNotNull { hit -> when (kind) {
                    EntityKind.TRACK -> source.track(hit.providerId)?.let(CatalogDocument::from)
                    EntityKind.ALBUM -> source.album(hit.providerId)?.let(CatalogDocument::from)
                    EntityKind.ARTIST -> source.artist(hit.providerId)?.let(CatalogDocument::from)
                } }.filter { doc -> when (kind) {
                    EntityKind.TRACK -> CatalogIdentity.titleMatches(name, doc[CatalogField.TRACK_TITLE].orEmpty()) &&
                        !artist.isNullOrBlank() && CatalogIdentity.artistMatches(artist, doc[CatalogField.TRACK_ARTISTS].orEmpty()) &&
                        (!preferences.options.value.strictDuration || CatalogIdentity.durationMatches(durationMs, doc[CatalogField.TRACK_DURATION_MS]?.toLongOrNull(), preferences.options.value.durationTolerancePercent))
                    EntityKind.ALBUM -> CatalogIdentity.titleMatches(name, doc[CatalogField.ALBUM_TITLE].orEmpty()) &&
                        !artist.isNullOrBlank() && CatalogIdentity.artistMatches(artist, doc[CatalogField.ALBUM_ARTISTS].orEmpty())
                    EntityKind.ARTIST -> CatalogIdentity.artistMatches(name, doc[CatalogField.ARTIST_NAME].orEmpty())
                } }
                val preferred = if (documents.size > 1 && !CatalogIdentity.missing(album)) documents.filter {
                    CatalogIdentity.titleMatches(album.orEmpty(), it[CatalogField.TRACK_ALBUM_TITLE].orEmpty())
                } else documents
                val doc = preferred.singleOrNull() ?: continue
                doc.values.forEach { if (values[it.field]?.hasValue() != true && it.hasValue()) values[it.field] = it }
            }
            val next = System.currentTimeMillis() + 7 * 24 * 3600_000L
            record = save(kind, recordKey, CatalogDocument(kind, values.values.toList()), next, replace = replace || !lengthVerified, attemptedFields = wanted, reset = !lengthVerified, matchingPolicy = policy)
            record.takeIf { kind != EntityKind.TRACK || durationMs <= 0 || !preferences.options.value.strictDuration || CatalogIdentity.durationMatches(durationMs, it?.document?.get(CatalogField.TRACK_DURATION_MS)?.toLongOrNull(), preferences.options.value.durationTolerancePercent) }
        }
    }

    suspend fun persistArtwork(kind: EntityKind, key: String, source: Uri?): Uri? = withContext(Dispatchers.IO) {
        if (source == null || !preferences.options.value.permanent) return@withContext source
        val directory = File(root, "images").apply { mkdirs() }
        val owned = source.scheme == "file" && source.path?.endsWith(".webp") == true && source.path?.startsWith(directory.path + "/") == true
        val target = if (owned) File(requireNotNull(source.path)) else {
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(source).size(1600).allowHardware(false).build()) as? SuccessResult
                ?: return@withContext source
            val bytes = java.io.ByteArrayOutputStream().use { stream ->
                check(result.drawable.toBitmap().compress(chromahub.rhythm.app.util.ArtworkStorage.format(), 86, stream)); stream.toByteArray()
            }
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            File(directory, "$hash.webp").also { target ->
                entityLocks.computeIfAbsent("image:$hash") { Mutex() }.withLock {
                    if (!target.isFile) PersonalArchive.atomicWrite(target, bytes)
                }
            }
        }
        val record = read(kind, key)
        if (record?.imageFile != target.name) save(kind, key, record?.document ?: CatalogDocument(kind, emptyList()), record?.retryAfter ?: 0, target.name)
        Uri.fromFile(target)
    }

    suspend fun fetchedImage(kind: EntityKind, key: String, name: String, artist: String? = null, durationMs: Long = 0, album: String? = null): Uri? {
        val field = when (kind) { EntityKind.TRACK -> CatalogField.TRACK_IMAGE; EntityKind.ALBUM -> CatalogField.ALBUM_IMAGE; EntityKind.ARTIST -> CatalogField.ARTIST_IMAGE }
        val selection = when (preferences.options.value.artworkSource) {
            "BUGS" -> setOf(Provider.BUGS_MOBILE)
            "MELON" -> setOf(Provider.MELON)
            "GENIE" -> setOf(Provider.GENIE)
            "LOCAL_ONLY", "DISABLED", "LEGACY" -> return image(read(kind, key))
            else -> null
        }
        val record = collect(kind, key, name, artist, setOf(field), durationMs, album, selection)
        image(record)?.let { return it }
        val url = record?.document?.get(field) ?: return null
        return persistArtwork(kind, key, Uri.parse(url))
    }
    fun apply(song: Song, fillMissingManual: Boolean = false): Song {
        val key = songKey(song)
        val includeMetadata = preferences.options.value.metadata
        val storedTrack = projection(EntityKind.TRACK, key)
        val verifiedTrack = !preferences.options.value.strictDuration || CatalogIdentity.durationMatches(song.duration, storedTrack.document[CatalogField.TRACK_DURATION_MS]?.toLongOrNull(), preferences.options.value.durationTolerancePercent)
        val automatic = if (includeMetadata && verifiedTrack) storedTrack.document else CatalogDocument(EntityKind.TRACK, emptyList())
        val manual = projection(EntityKind.TRACK, "manual:$key").document
        val album = projection(EntityKind.ALBUM, albumKey(song.album, effectiveAlbumArtist(song)))
        fun text(field: CatalogField, current: String?): String? = (if (preferences.options.value.replaces(field)) automatic.get(field) else null) ?: manual[field]?.takeIf {
            !fillMissingManual || !CatalogIdentity.missing(it)
        } ?: current?.takeUnless(CatalogIdentity::missing) ?: automatic.get(field) ?: when (field) {
                CatalogField.TRACK_GENRES -> if (includeMetadata) album.document[CatalogField.ALBUM_GENRES] else null
                CatalogField.TRACK_RELEASE_DATE -> if (includeMetadata) album.document[CatalogField.ALBUM_RELEASE_DATE] else null
                else -> null
            } ?: current
        val autoArt = (if (verifiedTrack) image(storedTrack) else null) ?: image(album)
        val removed = artworkRemoved(song) && !preferences.options.value.replaces(CatalogField.TRACK_IMAGE)
        return song.copy(title = text(CatalogField.TRACK_TITLE, song.title).orEmpty(),
            artist = text(CatalogField.TRACK_ARTISTS, song.artist).orEmpty(),
            album = text(CatalogField.TRACK_ALBUM_TITLE, song.album).orEmpty(), genre = text(CatalogField.TRACK_GENRES, song.genre),
            year = (if (preferences.options.value.replaces(CatalogField.TRACK_RELEASE_DATE)) automatic[CatalogField.TRACK_RELEASE_DATE].yearOrNull() else null) ?: manual[CatalogField.TRACK_RELEASE_DATE]?.toIntOrNull()?.takeIf { !fillMissingManual || it > 0 }
                ?: song.year.takeIf { it > 0 } ?: (automatic[CatalogField.TRACK_RELEASE_DATE] ?: if (includeMetadata) album.document[CatalogField.ALBUM_RELEASE_DATE] else null).yearOrNull() ?: 0,
            artworkUri = if (removed) null else {
                val original = song.artworkUri
                if (!preferences.options.value.replaces(CatalogField.TRACK_IMAGE) && original?.scheme == "file" && original.path?.let { File(it).isFile && !(it.startsWith(context.cacheDir.path) && File(it).name.contains("placeholder", true)) } == true) original
                else autoArt ?: original
            })
    }
    fun recordManualSong(song: Song) {
        if (!preferences.options.value.permanent) return
        val source = song.uri.toString()
        val values = listOf(CatalogField.TRACK_TITLE to song.title, CatalogField.TRACK_ARTISTS to song.artist,
            CatalogField.TRACK_ALBUM_TITLE to song.album, CatalogField.TRACK_GENRES to song.genre.orEmpty(),
            CatalogField.TRACK_RELEASE_DATE to song.year.toString()).map { CatalogValue(it.first, it.second, source) }
        save(EntityKind.TRACK, "manual:${songKey(song)}", CatalogDocument(EntityKind.TRACK, values), Long.MAX_VALUE, replace = true)
    }
    suspend fun artistDescription(name: String): String? {
        val wanted = setOf(CatalogField.ARTIST_DESCRIPTION, CatalogField.ARTIST_DEBUT_DATE, CatalogField.ARTIST_GENRES)
        val record = collect(EntityKind.ARTIST, artistKey(name), name, null, wanted)
        return record?.document?.values?.filter { it.field in wanted }?.joinToString("\n\n") { it.value }?.takeIf(String::isNotBlank)
    }
    fun preferredArtwork(current: Uri?, owned: Uri?): Uri? {
        if (current?.scheme == "file" && current.path?.let { File(it).isFile && !it.contains("/cache/") } == true) return current
        if (current?.scheme == "content" && !current.toString().startsWith("content://media/")) return current
        return owned ?: current
    }
    fun acceptMissingManualFields(song: Song) {
        val key = "manual:${songKey(song)}"
        if (read(EntityKind.TRACK, key) == null) return
        val source = song.uri.toString()
        val values = listOf(CatalogField.TRACK_TITLE to song.title, CatalogField.TRACK_ARTISTS to song.artist,
            CatalogField.TRACK_ALBUM_TITLE to song.album, CatalogField.TRACK_GENRES to song.genre.orEmpty(),
            CatalogField.TRACK_RELEASE_DATE to song.year.toString()).filter { !CatalogIdentity.missing(it.second) && it.second != "0" }
            .map { CatalogValue(it.first, it.second, source) }
        save(EntityKind.TRACK, key, CatalogDocument(EntityKind.TRACK, values), Long.MAX_VALUE)
    }
    fun descriptionLookupAllowed(kind: EntityKind, key: String): Boolean {
        val id = "description:${kind.name}:${CatalogFileStore.hash(key)}"
        if (cooldownPrefs.getLong(id, 0) > System.currentTimeMillis()) return false
        cooldownPrefs.edit { putLong(id, System.currentTimeMillis() + 24 * 3600_000L) }
        return true
    }
    fun cacheDescription(kind: EntityKind, key: String, text: String) {
        if (text.isBlank()) return
        val field = if (kind == EntityKind.ARTIST) CatalogField.ARTIST_DESCRIPTION else CatalogField.ALBUM_DESCRIPTION
        save(kind, key, CatalogDocument(kind, listOf(CatalogValue(field, text, "local:description"))), read(kind, key)?.retryAfter ?: 0)
    }
    fun notifyChanged() = changed()
    fun reloadStored() { temporary.clear(); projections.clear(); changed() }
    suspend fun setPermanent(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferences.update(preferences.options.value.copy(permanent = enabled))
        if (enabled) {
            val snapshot = synchronized(temporary) { temporary.toMap() }
            snapshot.forEach { (key, record) ->
                val kind = EntityKind.valueOf(key.substringBefore(':'))
                val stored = store.merge(kind, key.substringAfter(':'), record.document, record.retryAfter,
                    record.imageFile, attemptedFields = record.attemptedFields)
                temporary[key] = stored; projections[key] = compact(stored)
            }
            changed()
        }
    }
    suspend fun withAudioWrite(song: Song, write: suspend () -> Boolean): Boolean =
        audioLocks.computeIfAbsent(songKey(song)) { Mutex() }.withLock {
            if (audioBusy(song)) false else write()
        }
    companion object {
        @Volatile private var instance: CatalogEnrichmentService? = null
        fun get(context: Context): CatalogEnrichmentService = instance ?: synchronized(this) {
            instance ?: CatalogEnrichmentService(context.applicationContext).also { instance = it }
        }
    }
}
