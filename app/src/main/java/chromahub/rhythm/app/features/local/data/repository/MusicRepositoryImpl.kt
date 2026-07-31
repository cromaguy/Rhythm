package chromahub.rhythm.app.features.local.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import chromahub.rhythm.app.network.NetworkClient
import chromahub.rhythm.app.network.ITunesSearchApiService
import chromahub.rhythm.app.network.RhythmLyricsApiService
import chromahub.rhythm.app.network.RhythmLyricsLine
import chromahub.rhythm.app.network.RhythmLyricsGenericSearchResult
import chromahub.rhythm.app.network.NeteaseSearchSong
import chromahub.rhythm.app.network.DeezerApiService
import chromahub.rhythm.app.network.DeezerArtist
import chromahub.rhythm.app.network.DeezerAlbum
import chromahub.rhythm.app.network.YTMusicApiService
import chromahub.rhythm.app.network.YTMusicSearchRequest
import chromahub.rhythm.app.network.YTMusicContext
import chromahub.rhythm.app.network.YTMusicClient
import chromahub.rhythm.app.network.YTMusicBrowseRequest
import chromahub.rhythm.app.network.extractArtistImageUrl
import chromahub.rhythm.app.network.extractAlbumImageUrl
import chromahub.rhythm.app.network.extractArtistBrowseId
import chromahub.rhythm.app.network.extractAlbumBrowseId
import chromahub.rhythm.app.util.MediaUtils
import chromahub.rhythm.app.network.extractArtistThumbnail
import chromahub.rhythm.app.network.extractAlbumCover
import okhttp3.Request
import com.google.gson.JsonParser
import com.google.gson.JsonElement
import com.google.gson.Gson
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URL
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.FolderNode
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.LyricsSourcePreference
import chromahub.rhythm.app.shared.data.model.MediaScanMode
import chromahub.rhythm.app.shared.data.model.ScanPhase
import chromahub.rhythm.app.shared.data.model.UserAudioDevice
import chromahub.rhythm.app.shared.data.model.PlaybackLocation
import chromahub.rhythm.app.shared.data.model.LyricsApiPriority
import chromahub.rhythm.app.shared.data.model.findAlbumForSong
import chromahub.rhythm.app.core.domain.model.PlayableItem
import chromahub.rhythm.app.core.domain.model.SourceType
import java.lang.ref.WeakReference
import chromahub.rhythm.app.util.AudioFormatDetector
import chromahub.rhythm.app.util.LyricsEnrichmentMerger
import chromahub.rhythm.app.util.LyricsParser
import chromahub.rhythm.app.util.LyricallyApiParser
import chromahub.rhythm.app.util.hasCjkOriginalCoverage
import chromahub.rhythm.app.util.hasUsableSyncedTimeline
import chromahub.rhythm.app.util.hasUsableTimedRomanization
import chromahub.rhythm.app.util.hasUsableTimedTranslation
import chromahub.rhythm.app.util.EnhancedLyricLine
import chromahub.rhythm.app.util.EnhancedWord
import chromahub.rhythm.app.util.RhythmLyricsParser
import chromahub.rhythm.app.util.LrcUtils
import chromahub.rhythm.app.util.SemanticLyrics
import android.content.SharedPreferences
import androidx.room.withTransaction
import androidx.documentfile.provider.DocumentFile
import chromahub.rhythm.app.features.local.data.database.RhythmDatabase
import chromahub.rhythm.app.features.local.data.database.entity.ArtistEntity
import chromahub.rhythm.app.features.local.data.database.entity.SongEntity
import chromahub.rhythm.app.features.local.data.database.entity.SongArtistEntity

/**
 * Scan progress data class for real-time updates
 */
data class ScanProgress(
    val current: Int,
    val total: Int,
    val stage: ScanPhase,
    val estimatedTimeMs: Long = 0
)

/**
 * Scan statistics for tracking library state
 */
data class ScanStatistics(
    val lastScanTime: Long,
    val scanDuration: Long,
    val totalSongs: Int,
    val filteredSongs: Int,
    val totalAlbums: Int,
    val totalArtists: Int,
    val storageUsedBytes: Long,
    val averageBitrate: Int,
    val duplicatesFound: Int
)

/**
 * Scan history entry
 */
data class ScanHistoryEntry(
    val timestamp: Long,
    val songsAdded: Int,
    val songsRemoved: Int,
    val duration: Long,
    val errorCount: Int
)

/**
 * Folder suggestion for smart blacklisting
 */
data class FolderSuggestion(
    val path: String,
    val reason: String,
    val songCount: Int,
    val confidence: Double
)

class MusicRepository(context: Context) {
    private val TAG = "MusicRepository"
    // Use WeakReference to prevent context leaks, but store applicationContext which is safe
    private val contextRef = WeakReference(context.applicationContext)
    private val context: Context
        get() = contextRef.get() ?: throw IllegalStateException("Context has been garbage collected")
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // In-memory buffer for pending date-added writes to avoid SharedPreferences write loops
    private val pendingDateAddedWrites = java.util.concurrent.ConcurrentHashMap<String, Long>()
    
    // Genre cache using SharedPreferences
    private val genrePrefs: SharedPreferences by lazy { context.getSharedPreferences("genre_cache", Context.MODE_PRIVATE) }
    private val artworkPrefs: SharedPreferences by lazy { context.getSharedPreferences("artwork_overrides", Context.MODE_PRIVATE) }
    private val dateAddedPrefs: SharedPreferences by lazy { context.getSharedPreferences("song_date_added_cache", Context.MODE_PRIVATE) }
    private val libraryScanPrefs: SharedPreferences by lazy { context.getSharedPreferences("library_scan_metadata", Context.MODE_PRIVATE) }
    
    // Scan progress tracking
    private val _scanProgress = MutableStateFlow(ScanProgress(0, 0, ScanPhase.Idle))
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()
    
    // Scan history
    private val _scanHistory = MutableStateFlow<List<ScanHistoryEntry>>(emptyList())
    val scanHistory: StateFlow<List<ScanHistoryEntry>> = _scanHistory.asStateFlow()
    
    // Song cache to avoid duplicate MediaStore queries
    private var cachedSongs: List<Song>? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_VALIDITY_MS = 60_000 // 1 minute
    
    // Room database for persistent storage backend
    private val roomDb by lazy { RhythmDatabase.getInstance(context) }
    val songDao by lazy { roomDb.songDao() }
    val playlistDao by lazy { roomDb.playlistDao() }
    private val appSettings by lazy { AppSettings.getInstance(context) }

    private enum class LyricsOrigin(val label: String) {
        LOCAL("Local .lrc"),
        EMBEDDED("Embedded"),
        API("Online API")
    }

    /**
     * Persists the current in-memory song cache to Room database.
     * Call after background processing (metadata extraction, genre detection, artwork extraction)
     * so that results survive app restarts.
     */
    fun persistSongCacheToDisk() {
        cachedSongs?.let { songs ->
            Log.d(TAG, "Persisting ${songs.size} songs to disk cache")
            val songsWithMetadata = songs.count { it.bitrate != null && it.sampleRate != null && it.channels != null && it.codec != null }
            Log.d(TAG, "Songs with complete metadata: $songsWithMetadata/${songs.size}")
            val previousSongs = cachedSongs
            repositoryScope.launch {
                saveSongsToRoom(songs, clearArtistCache = false, previousSongs = previousSongs) // Don't clear artist cache when persisting metadata
            }
        } ?: Log.w(TAG, "No cached songs to persist to disk")
    }

    /**
     * Updates the internal song cache with the given list and persists it to Room.
     * Call this when the ViewModel modifies (e.g. merges metadata) into the song list.
     */
    fun updateAndPersistSongs(songs: List<Song>) {
        val previousSongs = cachedSongs
        if (previousSongs == songs) {
            Log.d(TAG, "Skipping Room save because the song snapshot is unchanged")
            return
        }
        repositoryScope.launch {
            saveSongsToRoom(songs, clearArtistCache = false, previousSongs = previousSongs) // Don't clear artist cache for metadata updates
        }
    }

    private suspend fun saveSongsToRoom(
        songs: List<Song>,
        clearArtistCache: Boolean = true,
        previousSongs: List<Song>? = cachedSongs
    ) {
        try {
            // Load existing artwork URIs from DB to check for lossless versions
            val existingSongs = runCatching { songDao.getAllSongs().associate { it.id to it.artworkUri } }.getOrNull() ?: emptyMap()

            val entities = songs.map { song ->
                val existingArtworkUriStr = existingSongs[song.id]
                val incomingArtworkUriStr = song.artworkUri?.toString()
                
                val finalArtworkUriStr = if (existingArtworkUriStr != null && incomingArtworkUriStr != null) {
                    val existingIsEmbedded = isEmbeddedArtworkCacheUri(Uri.parse(existingArtworkUriStr))
                    val incomingIsEmbedded = isEmbeddedArtworkCacheUri(Uri.parse(incomingArtworkUriStr))
                    
                    if (existingIsEmbedded && incomingIsEmbedded) {
                        val existingKey = getArtworkCacheKeyFromUri(Uri.parse(existingArtworkUriStr))
                        val incomingKey = getArtworkCacheKeyFromUri(Uri.parse(incomingArtworkUriStr))
                        
                        if (existingKey != null && existingKey == incomingKey) {
                            val existingIsLossless = existingArtworkUriStr.contains("embedded_art_lossless_")
                            val incomingIsLossless = incomingArtworkUriStr.contains("embedded_art_lossless_")
                            
                            if (existingIsLossless && !incomingIsLossless) {
                                existingArtworkUriStr
                            } else {
                                incomingArtworkUriStr
                            }
                        } else {
                            incomingArtworkUriStr
                        }
                    } else {
                        incomingArtworkUriStr
                    }
                } else {
                    incomingArtworkUriStr ?: existingArtworkUriStr
                }

                SongEntity(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    albumId = song.albumId,
                    duration = song.duration,
                    uri = song.uri.toString(),
                    artworkUri = finalArtworkUriStr,
                    trackNumber = song.trackNumber,
                    year = song.year,
                    genre = song.genre,
                    dateAdded = song.dateAdded,
                    dateModified = song.dateModified,
                    albumArtist = song.albumArtist,
                    bitrate = song.bitrate,
                    sampleRate = song.sampleRate,
                    channels = song.channels,
                    codec = song.codec,
                    discNumber = song.discNumber,
                    path = song.path
                )
            }
            val entitiesById = entities.associateBy { it.id }

            val songsWithMetadata =
                songs.count { it.bitrate != null && it.sampleRate != null && it.channels != null && it.codec != null }
            Log.d(
                TAG,
                "Saving ${songs.size} songs to Room (${songsWithMetadata} with metadata, clearArtistCache=$clearArtistCache)"
            )

            val canUseIncrementalUpdate =
                !clearArtistCache &&
                        previousSongs != null &&
                        previousSongs.size == songs.size &&
                        previousSongs.map { it.id }.toSet() == songs.map { it.id }.toSet()

            if (canUseIncrementalUpdate) {
                val previousById = previousSongs.associateBy { it.id }
                val changedSongs = songs.filter { previousById[it.id] != it }

                if (changedSongs.isEmpty()) {
                    Log.d(TAG, "Skipping Room save because the incremental snapshot is unchanged")
                    return
                }

                val chunkSize = 200
                changedSongs.chunked(chunkSize).forEach { chunk ->
                    val chunkEntities = chunk.mapNotNull { song -> entitiesById[song.id] }
                    val chunkSongIds = chunk.map { it.id }
                    val relationshipSets = buildSongArtistRelationshipSets(chunk)

                    roomDb.withTransaction {
                        songDao.upsertAll(chunkEntities)
                        roomDb.songArtistDao().deleteBySongIds(chunkSongIds)
                        roomDb.songArtistDao().insertAll(relationshipSets.albumArtistRelationships)
                        roomDb.songArtistDao().insertAll(relationshipSets.trackArtistRelationships)
                    }
                    yield()
                }

                Log.d(
                    TAG,
                    "Saved ${changedSongs.size} changed songs to Room database in chunks of $chunkSize (incremental metadata update)"
                )
            } else {
                val relationshipSets = buildSongArtistRelationshipSets(songs)
                roomDb.withTransaction {
                    songDao.replaceAll(entities)
                    roomDb.songArtistDao()
                        .replaceAll(relationshipSets.albumArtistRelationships, true)
                    roomDb.songArtistDao()
                        .replaceAll(relationshipSets.trackArtistRelationships, false)

                    if (clearArtistCache) {
                        // Clear artist cache since songs have changed
                        roomDb.artistDao().deleteAll()
                    }
                }

                if (clearArtistCache) {
                    Log.d(
                        TAG,
                        "Saved ${songs.size} songs to Room database and cleared artist cache"
                    )
                } else {
                    Log.d(TAG, "Saved ${songs.size} songs to Room database (kept artist cache)")
                }
            }

            cachedSongs = songs
            cacheTimestamp = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save songs to Room database", e)
        }
    }

    /**
     * Saves song-artist relationships for both grouping modes.
     */
    private suspend fun saveSongArtistRelationships(songs: List<Song>) = withContext(Dispatchers.IO) {
        try {
            val relationshipSets = buildSongArtistRelationshipSets(songs)
            roomDb.withTransaction {
                roomDb.songArtistDao().replaceAll(relationshipSets.albumArtistRelationships, true)
                roomDb.songArtistDao().replaceAll(relationshipSets.trackArtistRelationships, false)
            }

            val totalRelationships = relationshipSets.albumArtistRelationships.size + relationshipSets.trackArtistRelationships.size
            Log.d(TAG, "Saved $totalRelationships song-artist relationships")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save song-artist relationships", e)
        }
    }

    private data class SongArtistRelationshipSets(
        val albumArtistRelationships: List<SongArtistEntity>,
        val trackArtistRelationships: List<SongArtistEntity>
    )

    private fun buildSongArtistRelationshipSets(songs: List<Song>): SongArtistRelationshipSets {
        val albumArtistRelationships = mutableListOf<SongArtistEntity>()
        val trackArtistRelationships = mutableListOf<SongArtistEntity>()

        // Read artist separator settings once
        val appSettings = AppSettings.getInstance(context)
        val artistSeparatorEnabled = appSettings.artistSeparatorEnabled.value
        val preloadedCharDelimiters: List<String> = if (artistSeparatorEnabled) {
            appSettings.artistSeparatorDelimiters.value.toList().map { it.toString() }
        } else {
            emptyList()
        }

        for (song in songs) {
            // For groupByAlbumArtist = true
            val explicitAlbumArtist = song.albumArtist?.trim().orEmpty()
            val albumArtistNames = if (explicitAlbumArtist.isNotBlank() && !explicitAlbumArtist.equals("<unknown>", ignoreCase = true)) {
                splitArtistNames(explicitAlbumArtist, preloadedCharDelimiters)
            } else {
                splitArtistNames(song.artist, preloadedCharDelimiters)
            }
            for (artistName in albumArtistNames) {
                val cleanName = artistName.trim()
                if (cleanName.isNotBlank() && !cleanName.equals("<unknown>", ignoreCase = true)) {
                    albumArtistRelationships.add(SongArtistEntity(song.id, cleanName, true))
                }
            }

            // For groupByAlbumArtist = false (split track artists)
            val trackArtistNames = splitArtistNames(song.artist, preloadedCharDelimiters)
            for (artistName in trackArtistNames) {
                val cleanName = artistName.trim()
                if (cleanName.isNotBlank() && !cleanName.equals("<unknown>", ignoreCase = true)) {
                    trackArtistRelationships.add(SongArtistEntity(song.id, cleanName, false))
                }
            }
        }

        return SongArtistRelationshipSets(
            albumArtistRelationships = albumArtistRelationships,
            trackArtistRelationships = trackArtistRelationships
        )
    }
    
    private suspend fun loadSongsFromRoom(): List<Song>? {
        return try {
            val entities = songDao.getAllSongs()
            if (entities.isEmpty()) return null
            val appSettings = AppSettings.getInstance(context)
            val useEmbeddedArt = appSettings.preferSongArtwork.value
            val losslessArtwork = appSettings.isLosslessArtworkActive.value

            // Batch-read artwork overrides to avoid per-song SharedPreferences IPC calls
            val artworkAll = artworkPrefs.all
            val removedOverrides = mutableMapOf<String, Boolean>()
            val uriOverrides = mutableMapOf<String, Uri?>()
            for ((key, value) in artworkAll) {
                when {
                    key.startsWith("removed_") -> removedOverrides[key.removePrefix("removed_")] = value as? Boolean ?: false
                    key.startsWith("uri_") -> {
                        val id = key.removePrefix("uri_")
                        uriOverrides[id] = (value as? String)?.let { runCatching { Uri.parse(it) }.getOrNull() }
                    }
                }
            }

            val songs = entities.mapNotNull { entity ->
                try {
                    val songUri = Uri.parse(entity.uri)
                    
                    val artworkRemovedOverride = removedOverrides[entity.id] ?: false
                    val artworkUriOverride = uriOverrides[entity.id]

                    val savedArtworkUri = entity.artworkUri?.let { Uri.parse(it) }
                    val savedArtworkUsable = when (savedArtworkUri?.scheme) {
                        "file", null -> savedArtworkUri?.path?.let { File(it).exists() } == true
                        else -> true
                    }
                    val savedArtworkIsEmbeddedCache = isEmbeddedArtworkCacheUri(savedArtworkUri)
                    val shouldUseSavedArtwork = savedArtworkUsable && (useEmbeddedArt || !savedArtworkIsEmbeddedCache)

                    val embeddedCachedArtwork = if (useEmbeddedArt) {
                        if (savedArtworkIsEmbeddedCache && savedArtworkUsable) {
                            val fileName = savedArtworkUri?.path?.let { File(it).name } ?: ""
                            val isLosslessFile = fileName.startsWith("embedded_art_lossless_")
                            if (isLosslessFile == losslessArtwork) {
                                savedArtworkUri
                            } else {
                                val savedFile = savedArtworkUri?.path?.let { File(it) }
                                val parentDir = savedFile?.parentFile ?: File(context.cacheDir, "embedded_artwork")
                                val baseName = fileName
                                    .removePrefix("embedded_art_lossless_")
                                    .removePrefix("embedded_art_")
                                    .substringBefore('.')
                                
                                val extensions = listOf("jpg", "png", "webp")
                                var preferredFile: File? = null
                                for (ext in extensions) {
                                    val candidate = File(parentDir, if (losslessArtwork) {
                                        "embedded_art_lossless_${baseName}.${ext}"
                                    } else {
                                        "embedded_art_${baseName}.${ext}"
                                    })
                                    if (candidate.exists() && candidate.length() > 0L) {
                                        preferredFile = candidate
                                        break
                                    }
                                }
                                
                                if (preferredFile != null) {
                                    Uri.fromFile(preferredFile)
                                } else {
                                    savedArtworkUri
                                }
                            }
                        } else {
                            chromahub.rhythm.app.util.MediaUtils.getCachedEmbeddedAlbumArtUri(
                                cacheDir = context.cacheDir,
                                songUri = songUri,
                                lossless = losslessArtwork
                            )
                        }
                    } else {
                        null
                    }

                    val fallbackAlbumArt = entity.albumId.toLongOrNull()?.let { albumId ->
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        )
                    }

                    val effectiveArtUri = when {
                        embeddedCachedArtwork != null -> embeddedCachedArtwork
                        shouldUseSavedArtwork -> savedArtworkUri
                        fallbackAlbumArt != null -> fallbackAlbumArt
                        else -> null
                    }

                    val resolvedArtworkUri = when {
                        artworkRemovedOverride -> null
                        artworkUriOverride != null -> {
                            val isUsable = when (artworkUriOverride.scheme) {
                                "file", null -> artworkUriOverride.path?.let { File(it).exists() } == true
                                else -> true
                            }
                            if (isUsable) {
                                artworkUriOverride
                            } else {
                                artworkPrefs.edit().remove("uri_${entity.id}").apply()
                                effectiveArtUri
                            }
                        }
                        else -> effectiveArtUri
                    }

                    Song(
                        id = entity.id,
                        title = entity.title,
                        artist = entity.artist,
                        album = entity.album,
                        albumId = entity.albumId,
                        duration = entity.duration,
                        uri = songUri,
                        artworkUri = resolvedArtworkUri,
                        trackNumber = entity.trackNumber,
                        year = entity.year,
                        genre = entity.genre,
                        dateAdded = entity.dateAdded,
                        dateModified = entity.dateModified.takeIf { it > 0L } ?: entity.dateAdded,
                        albumArtist = entity.albumArtist,
                        bitrate = entity.bitrate,
                        sampleRate = entity.sampleRate,
                        channels = entity.channels,
                        codec = entity.codec,
                        discNumber = entity.discNumber,
                        path = entity.path
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping corrupted Room entry: ${entity.id}", e)
                    null
                }
            }
            val songsWithMetadata = songs.count { it.bitrate != null && it.sampleRate != null && it.channels != null && it.codec != null }
            Log.d(TAG, "Loaded ${songs.size} songs from Room database (${songsWithMetadata} with metadata)")
            songs
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load songs from Room database", e)
            null
        }
    }

    fun isEmbeddedArtworkCacheUri(uri: Uri?): Boolean {
        if (uri == null) return false

        if (uri.scheme != "file" && uri.scheme != null) {
            return false
        }

        val path = uri.path ?: return false
        val file = File(path)
        val embeddedArtworkDir = File(context.cacheDir, "embedded_artwork")

        if (file.parentFile?.absolutePath == embeddedArtworkDir.absolutePath) {
            return file.name.startsWith("embedded_art_") || file.name.startsWith("embedded_art_lossless_")
        }

        if (file.parentFile?.absolutePath == context.cacheDir.absolutePath) {
            return file.name.startsWith("embedded_art_") || file.name.startsWith("embedded_art_lossless_")
        }

        return false
    }

    fun getArtworkCacheKeyFromUri(uri: Uri): String? {
        val path = uri.path ?: return null
        val fileName = File(path).name
        return fileName
            .removePrefix("embedded_art_lossless_")
            .removePrefix("embedded_art_")
            .substringBefore('.')
    }
    
    // ContentObserver for automatic updates
    private var mediaStoreObserver: ContentObserver? = null
    private var mediaStorePlaylistObserver: ContentObserver? = null
    private var onMediaStoreChangeCallback: (() -> Unit)? = null

    /**
     * API Fallback Strategy:
     * 
     * ARTIST IMAGES:
     * 1. Check local cache/storage
     * 2. Deezer API (primary)
     * 3. YouTube Music (fallback)
     * 4. Placeholder generation
     * 
     * ALBUM ARTWORK:
     * 1. Check existing local album art
     * 2. Check cache
     * 3. Deezer API (primary)
     * 4. YouTube Music (only when local album art is absent)
     * 5. Placeholder generation
     * 
     * TRACK IMAGES:
     * 1. Check if track already has artwork
     * 2. Check if album has artwork (inherit from album)
     * 3. YouTube Music (fallback when no local artwork available)
     */
    
    private val deezerApiService = NetworkClient.deezerApiService
    private val lrclibApiService = NetworkClient.lrclibApiService
    private val ytmusicApiService = NetworkClient.ytmusicApiService
    private val rhythmLyricsApiService = NetworkClient.rhythmLyricsApiService
    private val itunesSearchApiService = NetworkClient.itunesSearchApiService
    private val genericHttpClient = NetworkClient.genericHttpClient
    
    // Note: API services can be null if disabled via BuildConfig

    /**
     * Register ContentObservers to monitor MediaStore changes (audio and playlists).
     */
    fun registerMediaStoreObserver(onChange: () -> Unit) {
        if (mediaStoreObserver != null) {
            Log.d(TAG, "ContentObserver already registered")
            return
        }
        
        onMediaStoreChangeCallback = onChange
        
        // Observer for audio media changes (new/deleted/modified songs)
        val audioObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d(TAG, "MediaStore audio changed, triggering callback")
                onChange()
            }
        }
        
        // Observer for playlist changes
        val playlistObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d(TAG, "MediaStore playlists changed, triggering callback")
                onChange()
            }
        }
        
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            audioObserver
        )
        context.contentResolver.registerContentObserver(
            Uri.parse("content://media/external/audio/playlists"),
            true,
            playlistObserver
        )
        mediaStoreObserver = audioObserver
        mediaStorePlaylistObserver = playlistObserver
        Log.d(TAG, "ContentObservers registered for MediaStore audio and playlist changes")
    }
    
    /**
     * Unregister ContentObservers
     */
    fun unregisterMediaStoreObserver() {
        mediaStoreObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            mediaStoreObserver = null
        }
        mediaStorePlaylistObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            mediaStorePlaylistObserver = null
        }
        onMediaStoreChangeCallback = null
        Log.d(TAG, "ContentObservers unregistered")
    }

    // LRU caches for artist images, album artwork, and lyrics to avoid memory leaks
    private val artistImageCache = Collections.synchronizedMap(object : LinkedHashMap<String, Uri?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Uri?>?): Boolean {
            return size > MAX_ARTIST_CACHE_SIZE
        }
    })
    private val albumImageCache = Collections.synchronizedMap(object : LinkedHashMap<String, Uri?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Uri?>?): Boolean {
            return size > MAX_ALBUM_CACHE_SIZE
        }
    })
    private val lyricsCache: MutableMap<String, LyricsData>
        get() = sharedLyricsCache
    
    // Rate limiting for API calls
    private val lastApiCalls = mutableMapOf<String, Long>()
    private val apiCallCounts = mutableMapOf<String, Int>()
    
    companion object {
        private const val MAX_ARTIST_CACHE_SIZE = 100
        private const val MAX_ALBUM_CACHE_SIZE = 200
        private const val MAX_LYRICS_CACHE_SIZE = 150
        private const val LYRICS_LOOKUP_TIMEOUT_MS = 20_000L
        private const val LYRICS_CACHE_VERSION = 2

        private val sharedLyricsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val lyricsRequests = LyricsRequestCoordinator<String, LyricsData?>(sharedLyricsScope)
        private val sharedLyricsCache = Collections.synchronizedMap(
            object : LinkedHashMap<String, LyricsData>(50, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, LyricsData>?): Boolean {
                    return size > MAX_LYRICS_CACHE_SIZE
                }
            }
        )
        
        // API rate limiting constants
        private const val DEEZER_MIN_DELAY = 200L
        private const val YTMUSIC_MIN_DELAY = 300L
        private const val LRCLIB_MIN_DELAY = 100L
        private const val MAX_CALLS_PER_MINUTE = 30
    }
    
    private fun calculateApiDelay(apiName: String, currentTime: Long): Long {
        val lastCall = lastApiCalls[apiName] ?: 0L
        val minDelay = when (apiName.lowercase()) {
            "deezer" -> DEEZER_MIN_DELAY
            "ytmusic" -> YTMUSIC_MIN_DELAY
            "lrclib" -> LRCLIB_MIN_DELAY
            else -> 250L
        }
        
        val timeSinceLastCall = currentTime - lastCall
        if (timeSinceLastCall < minDelay) {
            return minDelay - timeSinceLastCall
        }
        
        // Check if we're making too many calls per minute
        val callsInLastMinute = apiCallCounts[apiName] ?: 0
        if (callsInLastMinute >= MAX_CALLS_PER_MINUTE) {
            // Exponential backoff
            return minDelay * 2
        }
        
        return 0L
    }
    
    private fun updateLastApiCall(apiName: String, timestamp: Long) {
        lastApiCalls[apiName] = timestamp
        
        // Update call count for rate limiting
        val currentCount = apiCallCounts[apiName] ?: 0
        apiCallCounts[apiName] = currentCount + 1
        
        // Reset counter every minute
        if (currentCount == 0) {
            repositoryScope.launch {
                delay(60000)
                apiCallCounts[apiName] = 0
            }
        }
    }

    fun normalizeStoragePath(path: String): String {
        var normalized = path.trim().replace('\\', '/')
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/")
        }
        if (normalized.length > 1 && normalized.endsWith('/')) {
            normalized = normalized.substring(0, normalized.length - 1)
        }
        val symlinks = listOf("/sdcard", "/storage/self/primary")
        for (symlink in symlinks) {
            if (normalized.startsWith(symlink, ignoreCase = true)) {
                normalized = "/storage/emulated/0" + normalized.substring(symlink.length)
                break
            }
        }
        return normalized
    }

    suspend fun loadSongs(
        forceRefresh: Boolean = false,
        allowedFormats: Set<String>? = null,
        minimumBitrate: Int = 0,
        minimumDuration: Long = 0L
    ): List<Song> = withContext(Dispatchers.IO) {
        // Check MediaStore permissions before scanning
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires READ_MEDIA_AUDIO
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            // Android 12 and below require READ_EXTERNAL_STORAGE
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        if (!hasPermission) {
            Log.w(TAG, "MediaStore permission not granted. Cannot scan music library.")
            // Return empty list with error indication
            _scanProgress.value = ScanProgress(0, 0, ScanPhase.PermissionDenied, 0)
            return@withContext emptyList()
        }

        val appSettings = AppSettings.getInstance(context)
        var shouldForceRefresh = forceRefresh

        if (appSettings.consumePendingFullMediaRescanRequest()) {
            Log.i(TAG, "Pending full media rescan detected, invalidating persistent library caches")
            invalidatePersistentLibraryCachesForForcedRescan()
            shouldForceRefresh = true
        }

        // Check in-memory cache first
        if (!shouldForceRefresh && 
            cachedSongs != null && 
            System.currentTimeMillis() - cacheTimestamp < CACHE_VALIDITY_MS) {
            Log.d(TAG, "Returning cached songs (${cachedSongs!!.size})")
            return@withContext cachedSongs!!
        }
        
        // On cold start (no in-memory cache), try loading from Room cache
        if (!shouldForceRefresh && cachedSongs == null) {
            val diskCached = loadSongsFromRoom()
            if (diskCached != null && diskCached.isNotEmpty()) {
                if (isLibraryStale(appSettings.lastScanTimestamp.value, diskCached.size)) {
                    Log.d(TAG, "Newer files found in MediaStore, invalidating Room cache and rescanning")
                    invalidatePersistentLibraryCachesForForcedRescan()
                    shouldForceRefresh = true
                } else {
                    cachedSongs = diskCached
                    cacheTimestamp = System.currentTimeMillis()
                    Log.d(TAG, "Restored ${diskCached.size} songs from Room cache")
                    return@withContext diskCached
                }
            }
        }
        
        val startTime = System.currentTimeMillis()
        val songs = mutableListOf<Song>()
        val errors = mutableListOf<Pair<Int, Exception>>()
        val seenIds = mutableSetOf<String>()
        val seenPaths = mutableSetOf<String>()
        var duplicatesFound = 0
        var filteredByFormat = 0
        var filteredByQuality = 0

        val mediaScanMode: MediaScanMode = appSettings.mediaScanMode.value
        val whitelistedFolders = appSettings.whitelistedFolders.value
        val includeHiddenWhitelistedMedia = appSettings.includeHiddenWhitelistedMedia.value
        
        Log.d(TAG, "Starting media scan on Android ${Build.VERSION.SDK_INT} (API ${Build.VERSION.SDK_INT})")
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        
        Log.d(TAG, "Using MediaStore URI: $collection")

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA // For path-based duplicate detection
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
            }
        }.toTypedArray()

        // Include entries tagged as music or generic audio MIME types.
        // Some OTG/USB indexed tracks are not marked with IS_MUSIC=1.
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} = 1 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') AND ${MediaStore.Audio.Media.DURATION} > 10000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            _scanProgress.value = ScanProgress(0, 0, ScanPhase.Songs, 0)
            
            Log.d(TAG, "Querying MediaStore with selection: $selection")
            
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val count = cursor.count
                Log.d(TAG, "MediaStore query successful: Found $count audio files to process")
                _scanProgress.value = ScanProgress(0, count, ScanPhase.Songs, 0)
                
                if (count == 0) {
                    Log.w(TAG, "No audio files found in MediaStore - this may indicate a permission or storage access issue on Android ${Build.VERSION.SDK_INT}")
                    Log.w(TAG, "Please verify:")
                    Log.w(TAG, "1. Storage permission is granted")
                    Log.w(TAG, "2. Files are visible in MediaStore (try MediaScanner)")
                    Log.w(TAG, "3. Files meet criteria: IS_MUSIC=1 AND DURATION>10000ms")
                    // On Android 8-10, MediaStore indexing can be slow or incomplete on first query.
                    // Retry once after a short delay to give MediaStore time to index.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        Log.d(TAG, "Android 8/9 detected: retrying MediaStore query after 3s delay")
                        delay(3000L)
                        val retryCount = context.contentResolver.query(
                            collection, projection, selection, null, sortOrder
                        )?.use { it.count } ?: 0
                        if (retryCount == 0) {
                            Log.w(TAG, "Retry also found 0 files — library is empty or MediaStore not yet indexed")
                        } else {
                            Log.d(TAG, "Retry found $retryCount files — re-running full scan")
                            // Recursive call: MediaStore is now ready
                            return@withContext loadSongs(
                                forceRefresh = true,
                                allowedFormats = allowedFormats,
                                minimumBitrate = minimumBitrate,
                                minimumDuration = minimumDuration
                            )
                        }
                    }
                    return@withContext emptyList()
                }

                // Pre-allocate list with known size for better performance
                if (songs is ArrayList) {
                    songs.ensureCapacity(count)
                }
                
                // Cache all column indices once
                val columnIndices = try {
                    ColumnIndices(
                        id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID),
                        title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE),
                        displayName = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME),
                        artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST),
                        album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM),
                        albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID),
                        duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION),
                        track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK),
                        year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR),
                        dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED),
                        dateModified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED),
                        size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE),
                        // GENRE was added to MediaStore in API 30; use getColumnIndex (may be -1 on Android 8/9)
                        genre = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE),
                        albumArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST), // May be -1 on older devices
                        discNumber = cursor.getColumnIndex("disc_number"),
                        data = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    )
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Required column not found in MediaStore on Android ${Build.VERSION.SDK_INT}", e)
                    _scanProgress.value = ScanProgress(0, 0, ScanPhase.Error, 0)
                    return@withContext emptyList()
                }

                var processedCount = 0
                val batchSize = 100
                val pathColumnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                // Pre-loaded AppSettings is reused here to avoid repeated getInstance calls.
                
                while (cursor.moveToNext()) {
                    try {
                        val song = createSongFromCursor(cursor, columnIndices, appSettings)
                        if (song != null) {
                            // Duplicate detection by ID
                            if (seenIds.contains(song.id)) {
                                Log.d(TAG, "Skipping duplicate ID: ${song.id} - ${song.title}")
                                duplicatesFound++
                                processedCount++
                                continue
                            }
                            
                            // Duplicate detection by path
                            if (pathColumnIndex >= 0) {
                                val path = cursor.getString(pathColumnIndex)
                                if (path != null) {
                                    val normalizedPath = normalizeStoragePath(path)
                                    if (seenPaths.contains(normalizedPath)) {
                                        Log.d(TAG, "Skipping duplicate path: $path (normalized: $normalizedPath) - ${song.title}")
                                        duplicatesFound++
                                        processedCount++
                                        continue
                                    }
                                    
                                    // Format filtering
                                    if (allowedFormats != null && path.isNotEmpty()) {
                                        val extension = path.substringAfterLast('.', "").lowercase()
                                        if (extension.isNotEmpty() && !allowedFormats.contains(extension)) {
                                            filteredByFormat++
                                            processedCount++
                                            continue
                                        }
                                    }
                                    
                                    seenPaths.add(normalizedPath)
                                }
                            }

                            // Duration filtering (quality check)
                            
                            // Duration filtering (quality check)
                            if (minimumDuration > 0 && song.duration < minimumDuration) {
                                filteredByQuality++
                                processedCount++
                                continue
                            }
                            
                            // Note: Bitrate filtering is done later since we need to extract metadata
                            // For now, we add the song and can filter in post-processing if needed
                            
                            seenIds.add(song.id)
                            songs.add(song)
                        }
                        
                        processedCount++
                        
                        // Update progress periodically (dynamic frequency based on count to avoid UI lag)
                        val progressUpdateInterval = if (count > 500) 100 else if (count > 100) 50 else 10
                        if (processedCount % progressUpdateInterval == 0 || processedCount == count) {
                            _scanProgress.value = ScanProgress(processedCount, count, ScanPhase.Songs, 0)
                        }
                        
                        // Yield control periodically to avoid blocking
                        if (processedCount % batchSize == 0) {
                            yield()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing song at position ${cursor.position}", e)
                        errors.add(cursor.position to e)
                        processedCount++
                        continue
                    }
                }
                
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                // In whitelist mode, also scan explicit folders to include hidden/.nomedia files
                // that MediaStore may not index.
                if (mediaScanMode == MediaScanMode.WHITELIST && includeHiddenWhitelistedMedia && whitelistedFolders.isNotEmpty()) {
                    val whitelistSongs = loadSongsFromWhitelistedFolders(
                        whitelistedFolders = whitelistedFolders,
                        seenPaths = seenPaths,
                        allowedFormats = allowedFormats,
                        minimumDuration = minimumDuration,
                        appSettings = appSettings
                    )
                    if (whitelistSongs.isNotEmpty()) {
                        songs.addAll(whitelistSongs)
                        Log.d(
                            TAG,
                            "Added ${whitelistSongs.size} songs from whitelisted folders (hidden/.nomedia support)"
                        )
                    }
                }

                Log.d(TAG, "Loaded ${songs.size} songs in ${duration}ms")
                Log.d(TAG, "Filtering stats - Duplicates: $duplicatesFound, Format: $filteredByFormat, Quality: $filteredByQuality, Errors: ${errors.size}")
                
                // Persist to Room synchronously so other initialization tasks can safely use the DB
                Log.d(TAG, "Persisting ${songs.size} songs to disk cache synchronously before proceeding")
                _scanProgress.value = ScanProgress(songs.size, count, ScanPhase.SavingDb, 0)
                saveSongsToRoom(songs, clearArtistCache = true, previousSongs = cachedSongs)
                
                // Update scan progress to complete
                _scanProgress.value = ScanProgress(songs.size, count, ScanPhase.Complete, duration)
                appSettings.setLastScanTimestamp(System.currentTimeMillis())
                libraryScanPrefs.edit().putInt("last_scan_mediastore_count", count).apply()
                
                // Log errors if any
                if (errors.isNotEmpty()) {
                    Log.w(TAG, "Scan completed with ${errors.size} errors. First error: ${errors.first().second.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during song scan", e)
            _scanProgress.value = ScanProgress(0, 0, ScanPhase.Error, 0)
            // Return partial results if available
            return@withContext songs
        } finally {
            flushPendingDateAddedWrites()
        }

        return@withContext songs
    }
    
    /**
     * Perform incremental scan for newly added songs only
     */
    suspend fun performIncrementalScan(
        lastScanTimestamp: Long,
        allowedFormats: Set<String>? = null,
        minimumBitrate: Int = 0,
        minimumDuration: Long = 0L
    ): List<Song> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting incremental scan since timestamp: $lastScanTimestamp")
        val startTime = System.currentTimeMillis()
        val newSongs = mutableListOf<Song>()
        var filteredByFormat = 0
        var filteredByQuality = 0
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
            }
        }.toTypedArray()

        // Only scan songs added after last scan. Keep OTG/USB compatibility by accepting audio MIME entries.
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} = 1 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') AND ${MediaStore.Audio.Media.DURATION} > 10000 AND ${MediaStore.Audio.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf((lastScanTimestamp / 1000).toString())
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val count = cursor.count
                Log.d(TAG, "Found $count new audio files")
                _scanProgress.value = ScanProgress(0, count, ScanPhase.Incremental, 0)
                
                if (count == 0) {
                    return@withContext emptyList()
                }

                val columnIndices = try {
                    ColumnIndices(
                        id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID),
                        title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE),
                        displayName = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME),
                        artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST),
                        album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM),
                        albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID),
                        duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION),
                        track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK),
                        year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR),
                        dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED),
                        dateModified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED),
                        size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE),
                        // GENRE column is optional; not available on all Android versions (pre-API 30)
                        genre = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE),
                        albumArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST),
                        discNumber = cursor.getColumnIndex("disc_number"),
                        data = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    )
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Required column not found in MediaStore", e)
                    return@withContext emptyList()
                }

                var processedCount = 0
                val pathColumnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                // Pre-load AppSettings once to avoid per-song getInstance calls (performance fix for Android 8-10)
                val appSettings = AppSettings.getInstance(context)
                
                while (cursor.moveToNext()) {
                    try {
                        val song = createSongFromCursor(cursor, columnIndices, appSettings)
                        if (song != null) {
                            // Format filtering
                            if (allowedFormats != null && pathColumnIndex >= 0) {
                                val path = cursor.getString(pathColumnIndex)
                                if (path != null && path.isNotEmpty()) {
                                    val extension = path.substringAfterLast('.', "").lowercase()
                                    if (extension.isNotEmpty() && !allowedFormats.contains(extension)) {
                                        filteredByFormat++
                                        processedCount++
                                        continue
                                    }
                                }
                            }
                            
                            // Duration filtering
                            if (minimumDuration > 0 && song.duration < minimumDuration) {
                                filteredByQuality++
                                processedCount++
                                continue
                            }
                            
                            newSongs.add(song)
                        }
                        processedCount++
                        // Update progress periodically (dynamic frequency based on count to avoid UI lag)
                        val progressUpdateInterval = if (count > 500) 100 else if (count > 100) 50 else 10
                        if (processedCount % progressUpdateInterval == 0 || processedCount == count) {
                            _scanProgress.value = ScanProgress(processedCount, count, ScanPhase.Incremental, 0)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing new song at position ${cursor.position}", e)
                    }
                }
                
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Incremental scan complete: ${newSongs.size} new songs in ${duration}ms")
                Log.d(TAG, "Filtering stats - Format: $filteredByFormat, Quality: $filteredByQuality")
                _scanProgress.value = ScanProgress(newSongs.size, count, ScanPhase.Complete, duration)
                
                // Update raw MediaStore count cache after incremental scan
                try {
                    val countCursor = context.contentResolver.query(
                        collection,
                        arrayOf(MediaStore.Audio.Media._ID),
                        "(${MediaStore.Audio.Media.IS_MUSIC} = 1 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') AND ${MediaStore.Audio.Media.DURATION} > 10000",
                        null,
                        null
                    )
                    val mediaStoreCount = countCursor?.use { it.count } ?: 0
                    libraryScanPrefs.edit().putInt("last_scan_mediastore_count", mediaStoreCount).apply()
                    Log.d(TAG, "Saved incremental MediaStore count: $mediaStoreCount")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to save incremental MediaStore count", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during incremental scan", e)
        } finally {
            flushPendingDateAddedWrites()
        }

        return@withContext newSongs
    }
    
    private data class ColumnIndices(
        val id: Int,
        val title: Int,
        val displayName: Int,
        val artist: Int,
        val album: Int,
        val albumId: Int,
        val duration: Int,
        val track: Int,
        val year: Int,
        val dateAdded: Int,
        val dateModified: Int,
        val size: Int,
        val genre: Int,
        val albumArtist: Int, // May be -1 if not available on older devices
        val discNumber: Int,
        val data: Int
    )

    private fun loadSongsFromWhitelistedFolders(
        whitelistedFolders: List<String>,
        seenPaths: MutableSet<String>,
        allowedFormats: Set<String>?,
        minimumDuration: Long,
        appSettings: AppSettings
    ): List<Song> {
        val discovered = mutableListOf<Song>()

        for (folderPath in whitelistedFolders) {
            val root = File(folderPath)
            if (!root.exists() || !root.isDirectory) {
                continue
            }

            root.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach

                val extension = file.extension.lowercase()
                if (!isSupportedAudioExtension(extension, allowedFormats)) {
                    return@forEach
                }

                val absolutePath = file.absolutePath
                val normalizedPath = normalizeStoragePath(absolutePath)
                if (seenPaths.contains(normalizedPath)) {
                    return@forEach
                }

                val song = createSongFromFile(file, appSettings) ?: return@forEach
                if (song.duration <= 10_000L) {
                    return@forEach
                }
                if (minimumDuration > 0 && song.duration < minimumDuration) {
                    return@forEach
                }

                seenPaths.add(normalizedPath)
                discovered.add(song)
            }
        }

        return discovered
    }

    private fun isSupportedAudioExtension(extension: String, allowedFormats: Set<String>?): Boolean {
        if (extension.isBlank()) return false
        if (allowedFormats != null) {
            return allowedFormats.contains(extension)
        }

        return extension in setOf(
            "mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "alac", "aiff", "aif", "wma", "mkv", "mka",
            "ac3", "ac4", "oga", "mid", "midi", "adts", "m4b"
        )
    }

    private fun dateAddedCacheKeyForPath(filePath: String): String {
        val normalizedPath = filePath
            .trim()
            .replace('\\', '/')
            .lowercase(Locale.ROOT)

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalizedPath.toByteArray(Charsets.UTF_8))

        val hex = buildString(digest.size * 2) {
            digest.forEach { byte ->
                append(((byte.toInt() ushr 4) and 0xF).toString(16))
                append((byte.toInt() and 0xF).toString(16))
            }
        }

        return "date_added_$hex"
    }

    private fun resolveStableDateAdded(filePath: String?, observedDateAddedMs: Long): Long {
        val normalizedObservedDate = observedDateAddedMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        val resolvedPath = filePath?.trim()?.takeIf { it.isNotBlank() } ?: return normalizedObservedDate

        val key = try {
            dateAddedCacheKeyForPath(resolvedPath)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute date-added cache key for path", e)
            return normalizedObservedDate
        }

        val cachedDate = pendingDateAddedWrites[key] ?: dateAddedPrefs.getLong(key, -1L)
        if (cachedDate > 0L) {
            val stableDate = minOf(cachedDate, normalizedObservedDate)
            if (stableDate != cachedDate) {
                pendingDateAddedWrites[key] = stableDate
            }
            return stableDate
        }

        pendingDateAddedWrites[key] = normalizedObservedDate
        return normalizedObservedDate
    }

    private fun flushPendingDateAddedWrites() {
        if (pendingDateAddedWrites.isEmpty()) return
        
        val updates = mutableMapOf<String, Long>()
        synchronized(pendingDateAddedWrites) {
            updates.putAll(pendingDateAddedWrites)
            pendingDateAddedWrites.clear()
        }
        
        if (updates.isEmpty()) return
        Log.d(TAG, "Flushing ${updates.size} stable date-added values to SharedPreferences")
        try {
            val editor = dateAddedPrefs.edit()
            for ((key, value) in updates) {
                editor.putLong(key, value)
            }
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush stable date-added values to SharedPreferences", e)
        }
    }

    private fun createSongFromFile(file: File, appSettings: AppSettings): Song? {
        val retriever = android.media.MediaMetadataRetriever()
        var title: String = file.nameWithoutExtension
        var artist: String = "Unknown Artist"
        var album: String = "Unknown Album"
        var albumArtist: String? = null
        var duration: Long = 0L
        var year: Int = 0
        var trackNumber = 0
        var fallbackDiscNumber = 1

        try {
            retriever.setDataSource(file.absolutePath)

            val extractedTitle = normalizeMetadataText(
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            )?.trim()
            title = if (!extractedTitle.isNullOrBlank() && !isLikelyCorruptedMetadata(extractedTitle)) {
                extractedTitle
            } else {
                selectBestMetadataText(extractedTitle, file.nameWithoutExtension)
            } ?: file.nameWithoutExtension

            val extractedArtist = normalizeMetadataText(
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            )?.trim()
            val artistFromVorbisComments = extractArtistFromVorbisCommentTags(file.absolutePath)
            artist = artistFromVorbisComments
                ?: extractedArtist?.takeUnless { isUnknownArtistValue(it) }
                ?: "Unknown Artist"

            album = normalizeMetadataText(
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
            )?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Album"

            albumArtist = normalizeMetadataText(
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            )?.trim()?.takeIf { it.isNotBlank() }

            duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            year = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.toIntOrNull() ?: 0

            val trackRaw = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore("/")
                ?.toIntOrNull() ?: 0
            trackNumber = if (trackRaw >= 1000) trackRaw % 1000 else trackRaw
            fallbackDiscNumber = if (trackRaw >= 1000) trackRaw / 1000 else 1
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed for ${file.absolutePath}, using fallback values", e)
            val artistFromVorbisComments = extractArtistFromVorbisCommentTags(file.absolutePath)
            artist = artistFromVorbisComments ?: "Unknown Artist"
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release exceptions.
            }
        }

        if (title.isBlank() || title.equals("<unknown>", ignoreCase = true)) {
            title = file.nameWithoutExtension
        }
        if (title.isBlank()) {
            return null
        }

        return Song(
            id = "file_${file.absolutePath.hashCode()}_${file.length()}",
            title = title,
            artist = artist,
            album = album,
            albumId = "local_${album.lowercase().hashCode()}",
            duration = duration,
            uri = Uri.fromFile(file),
            artworkUri = null,
            trackNumber = trackNumber,
            year = year,
            genre = null,
            dateAdded = resolveStableDateAdded(file.absolutePath, file.lastModified()),
            dateModified = file.lastModified(),
            albumArtist = albumArtist,
            bitrate = null,
            sampleRate = null,
            channels = null,
            codec = file.extension.uppercase().ifBlank { null },
            discNumber = fallbackDiscNumber.coerceAtLeast(1),
            path = file.absolutePath
        )
    }

    private fun isUnknownArtistValue(value: String?): Boolean {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) return true

        return normalized.equals("<unknown>", ignoreCase = true) ||
            normalized.equals("unknown", ignoreCase = true) ||
            normalized.equals("unknown artist", ignoreCase = true)
    }

    private fun extractArtistFromVorbisCommentTags(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null

        val extension = filePath.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("opus", "ogg", "oga")) {
            return null
        }

        val commentEntries = extractVorbisCommentEntriesFromOgg(filePath) ?: return null

        val repeatedArtists = commentEntries
            .asSequence()
            .filter { it.first == "ARTISTS" }
            .map { it.second.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .toList()

        if (repeatedArtists.isNotEmpty()) {
            return repeatedArtists.joinToString(" / ")
        }

        return commentEntries
            .asSequence()
            .firstOrNull { it.first == "ARTIST" && it.second.isNotBlank() }
            ?.second
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractVorbisCommentEntriesFromOgg(filePath: String): List<Pair<String, String>>? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return null

            java.io.RandomAccessFile(file, "r").use { raf ->
                val signature = ByteArray(4)
                if (raf.read(signature) != 4 || String(signature, Charsets.ISO_8859_1) != "OggS") {
                    return@use null
                }

                raf.seek(0)
                val packetBuffer = ByteArrayOutputStream()
                var pageCount = 0
                val maxPages = 256

                while (raf.filePointer < raf.length() && pageCount < maxPages) {
                    pageCount++

                    val pageSignature = ByteArray(4)
                    if (raf.read(pageSignature) != 4) break
                    if (String(pageSignature, Charsets.ISO_8859_1) != "OggS") {
                        val nextOggS = findNextOggSPage(raf)
                        if (nextOggS == -1L) break
                        raf.seek(nextOggS)
                        packetBuffer.reset()
                        continue
                    }

                    // Skip version + header type + granule/serial/sequence/checksum.
                    raf.skipBytes(2)
                    raf.skipBytes(20)

                    val segmentCount = raf.read()
                    if (segmentCount < 0) break

                    val segmentTable = ByteArray(segmentCount)
                    if (raf.read(segmentTable) != segmentCount) break

                    val pageSize = segmentTable.sumOf { it.toInt() and 0xFF }
                    if (pageSize < 0 || pageSize > 1_000_000) {
                        if (pageSize > 0) {
                            raf.seek(raf.filePointer + pageSize)
                        }
                        packetBuffer.reset()
                        continue
                    }

                    val pageData = ByteArray(pageSize)
                    if (pageSize > 0 && raf.read(pageData) != pageSize) break

                    var payloadOffset = 0
                    for (segment in segmentTable) {
                        val segmentLength = segment.toInt() and 0xFF

                        if (segmentLength > 0) {
                            if (payloadOffset + segmentLength > pageData.size) {
                                packetBuffer.reset()
                                break
                            }

                            packetBuffer.write(pageData, payloadOffset, segmentLength)
                            payloadOffset += segmentLength
                        }

                        // A lacing value <255 marks the end of a packet.
                        if (segmentLength < 255) {
                            val packet = packetBuffer.toByteArray()
                            packetBuffer.reset()

                            val commentData = extractVorbisCommentDataFromPacket(packet) ?: continue
                            return@use parseVorbisCommentEntries(commentData) ?: emptyList()
                        }
                    }
                }

                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract OGG/Opus comments from $filePath: ${e.message}")
            null
        }
    }

    private fun extractVorbisCommentDataFromPacket(packet: ByteArray): ByteArray? {
        if (packet.size >= 8 &&
            String(packet.copyOfRange(0, 8), Charsets.ISO_8859_1) == "OpusTags"
        ) {
            return packet.copyOfRange(8, packet.size)
        }

        if (packet.size >= 7 &&
            packet[0] == 0x03.toByte() &&
            String(packet.copyOfRange(1, 7), Charsets.ISO_8859_1) == "vorbis"
        ) {
            return packet.copyOfRange(7, packet.size)
        }

        return null
    }
    
    private fun normalizeMetadataText(value: String?): String? {
        val raw = value?.trim() ?: return value
        if (raw.isBlank()) return raw

        val hasCommonUtf8MojibakeMarkers =
            raw.contains('Ã') ||
                raw.contains('Â') ||
                raw.contains("\u00E2\u20AC")
        if (!hasCommonUtf8MojibakeMarkers) return raw

        val hasNonLatin1CodePoints = raw.any { it.code > 0xFF }
        if (hasNonLatin1CodePoints && !raw.contains("\u00E2\u20AC")) {
            // Avoid damaging valid Unicode (for example Vietnamese) with a forced Latin-1 round-trip.
            return raw
        }

        return runCatching {
            val repaired = String(raw.toByteArray(java.nio.charset.Charset.forName("windows-1252")), Charsets.UTF_8)
            val repairedHasMoreReplacementChars =
                repaired.count { it == '\uFFFD' } > raw.count { it == '\uFFFD' }
            if (repaired.isBlank() || repairedHasMoreReplacementChars) raw else repaired
        }.getOrDefault(raw)
    }

    private fun titleFromDisplayName(displayName: String?): String? {
        val normalized = normalizeMetadataText(displayName)?.trim() ?: return null
        if (normalized.isBlank()) return null

        return normalized.substringBeforeLast('.', normalized)
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun isLikelyCorruptedMetadata(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed.equals("<unknown>", ignoreCase = true)) {
            return true
        }

        if (trimmed.any { it == '\uFFFD' }) {
            return true
        }

        val questionMarkCount = trimmed.count { it == '?' }
        if (questionMarkCount >= 2) {
            return true
        }

        // A question mark embedded inside a word usually indicates lossy character conversion.
        return questionMarkCount > 0 && Regex("\\p{L}\\?\\p{L}").containsMatchIn(trimmed)
    }

    private fun metadataQualityScore(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return Int.MIN_VALUE

        var score = trimmed.length * 4
        score -= trimmed.count { it == '\uFFFD' } * 40
        score -= trimmed.count { it == '?' } * 25
        if (trimmed.equals("<unknown>", ignoreCase = true)) {
            score -= 200
        }
        if (trimmed.any { it.code > 0x7F }) {
            score += 8
        }

        return score
    }

    private fun selectBestMetadataText(vararg candidates: String?): String? {
        return candidates
            .mapNotNull { candidate -> candidate?.trim()?.takeIf { it.isNotBlank() } }
            .maxByOrNull { candidate -> metadataQualityScore(candidate) }
    }

    private val discTrackFromFilenameRegex = Regex(
        """(?:cd|disc|disk|side)[\s._-]*(\d+)[\s._-]+(\d+)""", RegexOption.IGNORE_CASE
    )
    private val leadingTrackRegex = Regex("""^(\d{1,3})[-\s._]+""")

    private fun inferTrackDiscFromFilename(filenameWithoutExt: String): Pair<Int, Int> {
        if (filenameWithoutExt.isBlank()) return 0 to 1

        val discTrackMatch = discTrackFromFilenameRegex.find(filenameWithoutExt)
        if (discTrackMatch != null) {
            val disc = discTrackMatch.groupValues[1].toIntOrNull() ?: 1
            val track = discTrackMatch.groupValues[2].toIntOrNull() ?: 0
            return track.coerceIn(0, 999) to disc
        }

        val leadingMatch = leadingTrackRegex.find(filenameWithoutExt)
        if (leadingMatch != null) {
            val track = leadingMatch.groupValues[1].toIntOrNull() ?: 0
            return track.coerceIn(0, 999) to 1
        }

        return 0 to 1
    }

    private fun createSongFromCursor(cursor: android.database.Cursor, indices: ColumnIndices, appSettings: AppSettings = AppSettings.getInstance(context)): Song? {
        return try {
            val id = cursor.getLong(indices.id)
            val rawTitle = normalizeMetadataText(cursor.getString(indices.title))?.trim()
            val displayNameTitle = if (indices.displayName >= 0 && !cursor.isNull(indices.displayName)) {
                titleFromDisplayName(cursor.getString(indices.displayName))
            } else {
                null
            }
            val filePath = if (indices.data >= 0 && !cursor.isNull(indices.data)) {
                cursor.getString(indices.data)
            } else {
                null
            }
            val pathTitle = if (!filePath.isNullOrBlank()) {
                File(filePath).nameWithoutExtension
                    .trim()
                    .takeIf { it.isNotBlank() }
            } else {
                null
            }

            val title = if (!rawTitle.isNullOrBlank() && !isLikelyCorruptedMetadata(rawTitle)) {
                rawTitle
            } else {
                selectBestMetadataText(rawTitle, displayNameTitle, pathTitle)
            } ?: return null
            val rawArtist = normalizeMetadataText(cursor.getString(indices.artist))?.trim()
            val artistFromVorbisComments = extractArtistFromVorbisCommentTags(filePath)

            // Keep the full artist string so songs appear under all their artists.
            // The display-time splitArtistNames logic handles splitting for grouping/filtering.
            val artist = artistFromVorbisComments
                ?: rawArtist?.takeUnless { isUnknownArtistValue(it) }
                ?: "Unknown Artist"
            
            val album = normalizeMetadataText(cursor.getString(indices.album))?.trim() ?: "Unknown Album"
            val albumId = cursor.getLong(indices.albumId)
            val duration = cursor.getLong(indices.duration)
            val rawTrack = cursor.getInt(indices.track)
            // MediaStore encodes disc number in track: e.g. 1001 = disc 1, track 1.
            // Extract the actual track number by taking modulo 1000.
            val mediaStoreTrack = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack
            
            // Extract disc number natively or fallback to track offset logic
            val fallbackDiscInfo = if (rawTrack >= 1000) rawTrack / 1000 else 1
            val mediaStoreDisc = if (indices.discNumber >= 0) {
                cursor.getInt(indices.discNumber).takeIf { it > 0 } ?: fallbackDiscInfo
            } else fallbackDiscInfo

            // Infer track/disc from filename when MediaStore values are missing or zero
            val filenameWithoutExt = filePath?.let { File(it).nameWithoutExtension }?.trim()
            val (inferredTrack, inferredDisc) = if (filenameWithoutExt != null) {
                inferTrackDiscFromFilename(filenameWithoutExt)
            } else {
                0 to 1
            }

            val track = if (mediaStoreTrack > 0) mediaStoreTrack else inferredTrack
            val discNumber = if (mediaStoreDisc > 0) mediaStoreDisc else inferredDisc
            val year = cursor.getInt(indices.year)
            val observedDateAdded = cursor.getLong(indices.dateAdded) * 1000L
            val dateAdded = resolveStableDateAdded(filePath, observedDateAdded)
            val observedDateModified = cursor.getLong(indices.dateModified) * 1000L
            val dateModified = observedDateModified
                .takeIf { it > 0L }
                ?: observedDateAdded.takeIf { it > 0L }
                ?: dateAdded
            val size = cursor.getLong(indices.size)
            val genreId = if (indices.genre >= 0) normalizeMetadataText(cursor.getString(indices.genre))?.trim() else null
            val albumArtist = if (indices.albumArtist >= 0) {
                normalizeMetadataText(cursor.getString(indices.albumArtist))?.trim()?.takeIf { it.isNotBlank() }
            } else null

            // Skip files that are too small (likely invalid)
            if (size < 1024) { // Less than 1KB
                Log.d(TAG, "Skipping file too small: $title ($size bytes)")
                return null
            }

            // Skip files with empty titles
            if (title.isBlank() || title.equals("<unknown>", ignoreCase = true)) {
                Log.d(TAG, "Skipping file with invalid title: $title")
                return null
            }

            val contentUri: Uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id
            )

            // Use MediaStore album art URI by default.
            // When preferSongArtwork is enabled, use previously cached embedded art so
            // songs can show unique per-track artwork instead of shared album art.
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
            val artworkRemovedOverride = artworkPrefs.getBoolean("removed_${id}", false)
            val artworkUriOverride = artworkPrefs.getString("uri_${id}", null)
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }

            val useEmbeddedArt = appSettings.preferSongArtwork.value
            val effectiveArtUri = if (useEmbeddedArt) {
                val lossless = appSettings.isLosslessArtworkActive.value
                chromahub.rhythm.app.util.MediaUtils.getCachedEmbeddedAlbumArtUri(
                    cacheDir = context.cacheDir,
                    songUri = contentUri,
                    lossless = lossless
                ) ?: albumArtUri // Fallback; background task will extract later
            } else {
                albumArtUri
            }

            val finalArtworkUri = when {
                artworkRemovedOverride -> null
                artworkUriOverride != null -> {
                    val isUsable = when (artworkUriOverride.scheme) {
                        "file", null -> artworkUriOverride.path?.let { File(it).exists() } == true
                        else -> true
                    }
                    if (isUsable) {
                        artworkUriOverride
                    } else {
                        artworkPrefs.edit().remove("uri_${id}").apply()
                        effectiveArtUri
                    }
                }
                else -> effectiveArtUri
            }

            // Load cached genre if available
            val cachedGenre = try {
                genrePrefs.getString("genre_$id", null)
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank() &&
                            !it.equals("unknown", ignoreCase = true) &&
                            it != "-"
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cached genre for song ID $id", e)
                null
            }
            if (cachedGenre != null) {
                Log.d(TAG, "Loaded cached genre '$cachedGenre' for song ID $id: $title")
            }

            // Note: Audio metadata extraction moved to background task to avoid blocking initial scan
            // Use extractAudioMetadata() separately when needed for detailed info

            Song(
                id = id.toString(),
                title = title,
                artist = artist,
                album = album,
                albumId = albumId.toString(),
                duration = duration,
                uri = contentUri,
                artworkUri = finalArtworkUri,
                trackNumber = track,
                year = year,
                dateAdded = dateAdded,
                dateModified = dateModified,
                genre = cachedGenre ?: genreId, // Use cached genre first, then MediaStore genre
                albumArtist = albumArtist,
                bitrate = null, // Will be extracted lazily when needed
                sampleRate = null,
                channels = null,
                codec = null,
                discNumber = discNumber,
                path = filePath
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error creating song from cursor", e)
            null
        }
    }
    
    /**
     * Data class to hold audio metadata
     */
    private data class AudioMetadata(
        val bitrate: Int? = null,
        val sampleRate: Int? = null,
        val channels: Int? = null,
        val codec: String? = null
    )
    
    /**
     * Extract audio quality metadata using MediaMetadataRetriever
     */
    private fun extractAudioMetadata(uri: Uri): AudioMetadata {
        val retriever = android.media.MediaMetadataRetriever()
        var extractor: android.media.MediaExtractor? = null
        
        try {
            retriever.setDataSource(context, uri)
            
            val bitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()
            
            val sampleRateStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            val sampleRate = sampleRateStr?.toIntOrNull()
            
            // Extract number of channels using MediaExtractor (correct way)
            var channels: Int? = null
            try {
                extractor = android.media.MediaExtractor()
                extractor.setDataSource(context, uri, null)
                
                // Find the audio track
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                    
                    if (mime?.startsWith("audio/") == true) {
                        // Get channel count from MediaFormat
                        if (format.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = format.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error extracting channel count with MediaExtractor: ${e.message}")
            }
            
            // Default to stereo if channel count couldn't be determined
            if (channels == null || channels <= 0) {
                channels = 2
            }
            
            // Get codec/mime type
            val mimeType = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            var codec = mimeType?.let { 
                when {
                    it.contains("mp3", ignoreCase = true) -> "MP3"
                    it.contains("aac", ignoreCase = true) -> "AAC"
                    it.contains("flac", ignoreCase = true) -> "FLAC"
                    it.contains("alac", ignoreCase = true) -> "ALAC"
                    it.contains("opus", ignoreCase = true) -> "Opus"
                    it.contains("vorbis", ignoreCase = true) -> "Vorbis"
                    it.contains("wav", ignoreCase = true) -> "WAV"
                    it.contains("m4a", ignoreCase = true) -> "M4A"
                    it.contains("ac4", ignoreCase = true) -> "AC-4"
                    it.contains("ac3", ignoreCase = true) || it.contains("ac-3", ignoreCase = true) -> "AC-3"
                    it.contains("eac3", ignoreCase = true) || it.contains("ec-3", ignoreCase = true) -> "E-AC-3"
                    it.contains("truehd", ignoreCase = true) -> "TrueHD"
                    it.contains("atmos", ignoreCase = true) -> "Dolby Atmos"
                    it.contains("dts-hd", ignoreCase = true) || it.contains("dtshd", ignoreCase = true) -> "DTS-HD MA"
                    it.contains("dts", ignoreCase = true) -> "DTS"
                    it.contains("dsd", ignoreCase = true) -> "DSD"
                    it.contains("wma", ignoreCase = true) -> "WMA"
                    it.contains("midi", ignoreCase = true) -> "MIDI"
                    it.contains("mp2", ignoreCase = true) -> "MP2"
                    else -> it.substringAfter("/").uppercase()
                }
            }
            
            // For M4A containers, try to detect if it's ALAC (lossless) or AAC (lossy)
            // ALAC typically has bitrate > 700 kbps, AAC is usually < 320 kbps
            if (codec == "M4A" && bitrate != null) {
                codec = if (bitrate >= 700000) "ALAC" else "AAC"
                Log.d(TAG, "M4A container detected: bitrate=${bitrate/1000}kbps, classified as $codec")
            }
            
            return AudioMetadata(bitrate, sampleRate, channels, codec)
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting audio metadata for $uri: ${e.message}")
            return AudioMetadata()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaMetadataRetriever: ${e.message}")
            }
            try {
                extractor?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaExtractor: ${e.message}")
            }
        }
    }

    /**
     * Enhanced genre detection with multiple fallback methods
     * @param context The application context
     * @param songUri The URI of the song file
     * @param songId The song ID for MediaStore queries
     * @return The detected genre name, or null if not found
     */
    private fun getGenreForSong(context: Context, songUri: Uri, songId: Int): String? {
        // Method 1: Try MediaStore.Audio.Media.GENRE column (may contain genre ID or name)
        try {
            val genreFromMediaStoreColumn = getGenreFromMediaStoreColumn(songId)
            if (!genreFromMediaStoreColumn.isNullOrBlank()) {
                Log.d(TAG, "Found genre from MediaStore column: $genreFromMediaStoreColumn for song ID: $songId")
                return genreFromMediaStoreColumn
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get genre from MediaStore column", e)
        }

        // Method 2: Try MediaStore.Audio.Genres table lookup
        try {
            val genreFromGenresTable = getGenreNameFromMediaStore(context.contentResolver, songId)
            if (!genreFromGenresTable.isNullOrBlank()) {
                Log.d(TAG, "Found genre from Genres table: $genreFromGenresTable for song ID: $songId")
                return genreFromGenresTable
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get genre from Genres table", e)
        }

        // Method 3: Try MediaMetadataRetriever
        try {
            val genreFromRetriever = getGenreFromMediaMetadataRetriever(songUri)
            if (!genreFromRetriever.isNullOrBlank()) {
                Log.d(TAG, "Found genre from MediaMetadataRetriever: $genreFromRetriever for song URI: $songUri")
                return genreFromRetriever
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get genre from MediaMetadataRetriever", e)
        }

        // Method 4: Try to infer genre from file path or filename patterns
        try {
            val genreFromPath = inferGenreFromPath(songUri)
            if (!genreFromPath.isNullOrBlank()) {
                Log.d(TAG, "Inferred genre from path: $genreFromPath for song URI: $songUri")
                return genreFromPath
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to infer genre from path", e)
        }

        Log.d(TAG, "No genre found for song ID: $songId, URI: $songUri")
        return null
    }

    /**
     * Gets genre directly from MediaStore.Audio.Media.GENRE column
     * This column may contain either a genre ID or genre name depending on Android version
     */
    private fun getGenreFromMediaStoreColumn(songId: Int): String? {
        // GENRE column only available on API 30+ (Android 11)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val projection = arrayOf(MediaStore.Audio.Media.GENRE)
            val selection = "${MediaStore.Audio.Media._ID} = ?"
            val selectionArgs = arrayOf(songId.toString())

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val genreIndex = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                    if (genreIndex != -1) {
                        val genreValue = cursor.getString(genreIndex)?.trim()
                        if (!genreValue.isNullOrBlank()) {
                            // Check if it's a numeric genre ID or a genre name
                            val genreId = genreValue.toLongOrNull()
                            if (genreId != null && genreId > 0) {
                                // It's a genre ID, try to convert it to name
                                return getGenreNameFromId(context.contentResolver, genreId)
                            } else {
                                // It's already a genre name
                                return genreValue
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting genre from MediaStore column", e)
            null
        }
    }

    /**
     * Converts a genre ID to genre name using the Genres table
     */
    private fun getGenreNameFromId(contentResolver: android.content.ContentResolver, genreId: Long): String? {
        return try {
            val projection = arrayOf(MediaStore.Audio.Genres.NAME)
            val selection = "${MediaStore.Audio.Genres._ID} = ?"
            val selectionArgs = arrayOf(genreId.toString())

            contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.Audio.Genres.NAME)
                    if (nameIndex != -1) {
                        return cursor.getString(nameIndex)?.trim()
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error converting genre ID to name", e)
            null
        }
    }

    /**
     * Gets genre from MediaMetadataRetriever
     */
    private fun getGenreFromMediaMetadataRetriever(songUri: Uri): String? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, songUri)
            val genre = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)
            genre?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting genre from MediaMetadataRetriever", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }
    }

    /**
     * Attempts to infer genre from file path or filename patterns
     */
    private fun inferGenreFromPath(songUri: Uri): String? {
        return try {
            val path = songUri.toString().lowercase()

            // Common genre patterns in file paths
            when {
                path.contains("rock") -> "Rock"
                path.contains("pop") -> "Pop"
                path.contains("hip.hop") || path.contains("hiphop") || path.contains("rap") -> "Hip Hop"
                path.contains("jazz") -> "Jazz"
                path.contains("classical") || path.contains("classic") -> "Classical"
                path.contains("electronic") || path.contains("electro") || path.contains("edm") -> "Electronic"
                path.contains("country") -> "Country"
                path.contains("blues") -> "Blues"
                path.contains("reggae") -> "Reggae"
                path.contains("folk") -> "Folk"
                path.contains("metal") -> "Metal"
                path.contains("punk") -> "Punk"
                path.contains("indie") -> "Indie"
                path.contains("alternative") -> "Alternative"
                path.contains("r&b") || path.contains("rnb") -> "R&B"
                path.contains("soul") -> "Soul"
                path.contains("funk") -> "Funk"
                path.contains("disco") -> "Disco"
                path.contains("dance") -> "Dance"
                path.contains("house") -> "House"
                path.contains("techno") -> "Techno"
                path.contains("trance") -> "Trance"
                path.contains("ambient") -> "Ambient"
                path.contains("soundtrack") || path.contains("ost") -> "Soundtrack"
                path.contains("instrumental") -> "Instrumental"
                path.contains("vocal") -> "Vocal"
                path.contains("christmas") || path.contains("holiday") -> "Holiday"
                path.contains("world") -> "World"
                path.contains("latin") -> "Latin"
                path.contains("african") -> "African"
                path.contains("asian") -> "Asian"
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inferring genre from path", e)
            null
        }
    }

    /**
     * Gets the genre name from MediaStore.Audio.Genres table using the song ID
     * @param contentResolver The ContentResolver to use for queries
     * @param songId The song ID to look up genre for
     * @return The genre name, or null if not found
     */
    private fun getGenreNameFromMediaStore(contentResolver: android.content.ContentResolver, songId: Int): String? {
        return try {
            // Try to get genre directly from the URI - works on newer Android versions
            val genreUri = android.provider.MediaStore.Audio.Genres.getContentUriForAudioId("external", songId)
            val projection = arrayOf(android.provider.MediaStore.Audio.Genres.NAME)
            
            contentResolver.query(
                genreUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Genres.NAME)
                    val genreName = cursor.getString(nameIndex)
                    if (!genreName.isNullOrBlank()) {
                        Log.d(TAG, "Found genre: $genreName for song ID: $songId")
                        return genreName
                    }
                }
            }

            null
        } catch (e: IllegalArgumentException) {
            // Column doesn't exist on this Android version - silently ignore
            Log.d(TAG, "Genre column not available on this device (Android API limitation)")
            null
        } catch (e: Exception) {
            // Other errors - log but don't spam
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Could not get genre for song ID: $songId: ${e.message}")
            }
            null
        }
    }

    private fun findBestAlbumArtist(songs: List<Song>): String? {
        val foundAlbumArtists = songs
            .map { it.albumArtist?.trim()?.takeIf { a -> a.isNotBlank() && !a.equals("<unknown>", ignoreCase = true) } }
            .groupBy { it }
            .mapValues { it.value.size }
        val nonNullCount = foundAlbumArtists.filterKeys { it != null }.size
        val nullCount = foundAlbumArtists[null] ?: 0

        val theAlbumArtist = foundAlbumArtists.keys.find { it != null }
        if (theAlbumArtist != null) {
            val countWithAlbumArtist = foundAlbumArtists[theAlbumArtist]!!
            if (countWithAlbumArtist >= songs.size / 2 && countWithAlbumArtist > nullCount * 2) {
                return theAlbumArtist
            }
            return null
        }

        val bestMatch = songs
            .map { it.artist.trim().takeUnless { a -> a.isBlank() || a.equals("<unknown>", ignoreCase = true) } ?: "Unknown Artist" }
            .groupBy { it }
            .maxByOrNull { it.value.size }
        if (bestMatch == null || bestMatch.key.isBlank()) return null
        if ((bestMatch.value.size.toFloat() / songs.size) < 0.6f) return null
        return bestMatch.key
    }

    private fun rawGroupKeyForSong(song: Song): String =
        (song.albumArtist?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            ?: song.artist.trim().takeIf { it.isNotBlank() }
            ?: "Unknown Artist").lowercase(Locale.ROOT)

    private fun isCompilationAlbum(songs: List<Song>): Boolean {
        val groups = songs.groupBy { rawGroupKeyForSong(it) }
        if (groups.size <= 1) return false
        val maxCount = groups.maxOf { it.value.size }
        val ratio = maxCount.toFloat() / songs.size
        return ratio < 0.6f
    }

    suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val allSongs = loadSongs()

        val albums = buildList {
            val byAlbumName = allSongs.groupBy { song ->
                song.album.trim().lowercase(Locale.ROOT)
            }

            for ((normalizedAlbumName, songsWithName) in byAlbumName) {
                val byArtist = songsWithName.groupBy { rawGroupKeyForSong(it) }

                if (byArtist.size <= 1 || !isCompilationAlbum(songsWithName)) {
                    for ((artistKey, albumSongs) in byArtist) {
                        val albumName = albumSongs.first().album.trim().ifBlank { "Unknown Album" }

                        val smartArtist = findBestAlbumArtist(albumSongs)
                            ?: albumSongs.first().albumArtist?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                            ?: albumSongs.first().artist.trim().takeIf { it.isNotBlank() }
                            ?: "Unknown Artist"

                        val albumId = "hash_${normalizedAlbumName}|${smartArtist.lowercase(Locale.ROOT)}"

                        val year = albumSongs.maxOfOrNull { it.year } ?: 0
                        val dateModified = albumSongs.maxOfOrNull { it.dateModified } ?: System.currentTimeMillis()

                        val sortedSongs = albumSongs.sortedWith(
                            compareBy<Song> { it.discNumber }
                                .thenBy { it.trackNumber }
                                .thenBy { it.title.lowercase(Locale.ROOT) }
                        )

                        add(Album(
                            id = albumId,
                            title = albumName,
                            artist = smartArtist,
                            artworkUri = albumSongs.first().artworkUri,
                            year = year,
                            songs = sortedSongs,
                            numberOfSongs = sortedSongs.size,
                            dateModified = dateModified
                        ))
                    }
                } else {
                    val albumName = songsWithName.first().album.trim().ifBlank { "Unknown Album" }
                    val albumArtist = "Various Artists"

                    val year = songsWithName.maxOfOrNull { it.year } ?: 0
                    val dateModified = songsWithName.maxOfOrNull { it.dateModified } ?: System.currentTimeMillis()

                    val sortedSongs = songsWithName.sortedWith(
                        compareBy<Song> { it.discNumber }
                            .thenBy { it.trackNumber }
                            .thenBy { it.title.lowercase(Locale.ROOT) }
                    )

                    val albumId = "hash_${normalizedAlbumName}|variousartists"

                    add(Album(
                        id = albumId,
                        title = albumName,
                        artist = albumArtist,
                        artworkUri = songsWithName.first().artworkUri,
                        year = year,
                        songs = sortedSongs,
                        numberOfSongs = sortedSongs.size,
                        dateModified = dateModified
                    ))
                }
            }
        }.sortedBy { it.title.lowercase(Locale.ROOT) }

        Log.d(TAG, "Loaded ${albums.size} albums from songs directly")
        albums
    }

    /**
     * Loads artists from device storage with enhanced metadata extraction.
     * Supports grouping by album artist or track artist based on user preference.
     * Uses Room cache when available to avoid recomputation.
     */
    suspend fun loadArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val appSettings = AppSettings.getInstance(context)
        val groupByAlbumArtist = appSettings.groupByAlbumArtist.value

        Log.d(TAG, "Loading artists (groupByAlbumArtist=$groupByAlbumArtist)")

        // Separator config affects relationship rows; refresh relationships/cache when it changes.
        refreshArtistRelationshipsIfNeeded(appSettings)

        // Try to load from Room cache first
        val cachedArtists = loadArtistsFromRoom(groupByAlbumArtist)
        if (cachedArtists != null && cachedArtists.isNotEmpty()) {
            Log.d(TAG, "Loaded ${cachedArtists.size} artists from Room cache (groupByAlbumArtist=$groupByAlbumArtist)")
            return@withContext cachedArtists
        }

        Log.d(TAG, "No cached artists found, computing from relationships (groupByAlbumArtist=$groupByAlbumArtist)")
        // Cache miss - compute from relationships
        val artists = loadArtistsFromRelationships(groupByAlbumArtist)

        // Cache the result
        saveArtistsToRoom(artists, groupByAlbumArtist)
        Log.d(TAG, "Cached ${artists.size} artists to Room (groupByAlbumArtist=$groupByAlbumArtist)")

        artists
    }

    private var cachedArtistSplitConfig: String? = appSettings.getArtistSeparatorCacheSignature()

    private suspend fun refreshArtistRelationshipsIfNeeded(appSettings: AppSettings) {
        val artistSeparatorEnabled = appSettings.artistSeparatorEnabled.value
        val delimiters = if (artistSeparatorEnabled) appSettings.artistSeparatorDelimiters.value else ""
        val configKey = "$artistSeparatorEnabled|$delimiters"

        if (configKey == cachedArtistSplitConfig) {
            return
        }

        val songs = loadSongs()
        val relationshipSets = buildSongArtistRelationshipSets(songs)
        roomDb.withTransaction {
            roomDb.songArtistDao().replaceAll(relationshipSets.albumArtistRelationships, true)
            roomDb.songArtistDao().replaceAll(relationshipSets.trackArtistRelationships, false)
            roomDb.artistDao().deleteAll()
        }
        cachedArtistSplitConfig = configKey
        appSettings.setArtistSeparatorCacheSignature(configKey)

        Log.d(
            TAG,
            "Refreshed artist relationships for separators (enabled=$artistSeparatorEnabled, delimiters='$delimiters')"
        )
    }

    /**
     * Loads artists from cached song-artist relationships.
     */
    private suspend fun loadArtistsFromRelationships(groupByAlbumArtist: Boolean): List<Artist> = withContext(Dispatchers.IO) {
        try {
            val aggregatedData = roomDb.songArtistDao().getAggregatedArtists(groupByAlbumArtist)
            val artists = mutableListOf<Artist>()

            for (data in aggregatedData) {
                val artist = Artist(
                    id = if (groupByAlbumArtist) "album_artist_${data.artistName.hashCode()}" else "track_artist_${data.artistName.hashCode()}",
                    name = data.artistName,
                    numberOfAlbums = data.albumCount,
                    numberOfTracks = data.trackCount
                )
                artists.add(artist)
            }

            artists.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load artists from relationships, falling back to computation", e)
            // Fallback to old method
            if (groupByAlbumArtist) {
                loadArtistsGroupedByAlbumArtist()
            } else {
                loadArtistsFromMediaStore()
            }
        }
    }

    /**
     * Loads artists from Room cache if available.
     */
    private suspend fun loadArtistsFromRoom(groupByAlbumArtist: Boolean): List<Artist>? = withContext(Dispatchers.IO) {
        try {
            val artistEntities = roomDb.artistDao().getArtists(groupByAlbumArtist)
            if (artistEntities.isEmpty()) return@withContext null

            artistEntities.map { entity ->
                Artist(
                    id = entity.id,
                    name = entity.name,
                    artworkUri = entity.artworkUri?.let { Uri.parse(it) },
                    numberOfAlbums = entity.numberOfAlbums,
                    numberOfTracks = entity.numberOfTracks
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load artists from Room cache", e)
            null
        }
    }

    /**
     * Saves artists to Room cache.
     */
    private suspend fun saveArtistsToRoom(artists: List<Artist>, groupByAlbumArtist: Boolean) = withContext(Dispatchers.IO) {
        try {
            val artistEntities = artists.map { artist ->
                ArtistEntity(
                    id = artist.id,
                    name = artist.name,
                    artworkUri = artist.artworkUri?.toString(),
                    numberOfAlbums = artist.numberOfAlbums,
                    numberOfTracks = artist.numberOfTracks,
                    groupByAlbumArtist = groupByAlbumArtist
                )
            }
            roomDb.artistDao().replaceAll(artistEntities, groupByAlbumArtist)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save artists to Room cache", e)
        }
    }

    /**
     * Original method: Loads artists using MediaStore.Audio.Artists
     * This groups by track artist, showing collaborations as separate entries
     */
    private suspend fun loadArtistsFromMediaStore(): List<Artist> = withContext(Dispatchers.IO) {
        // Load all songs to properly count tracks/albums for split artists
        val allSongs = loadSongs()
        val artistMap = mutableMapOf<String, MutableList<Song>>()
        val albumsByArtist = mutableMapOf<String, MutableSet<String>>()

        // Read artist separator settings ONCE before the loop to avoid
        // calling AppSettings.getInstance() for every song (O(n) cost)
        val appSettings = AppSettings.getInstance(context)
        val artistSeparatorEnabled = appSettings.artistSeparatorEnabled.value
        val preloadedCharDelimiters: List<String> = if (artistSeparatorEnabled) {
            appSettings.artistSeparatorDelimiters.value.toList().map { it.toString() }
        } else {
            emptyList()
        }

        // Group songs by individual track artists (splitting collaborations)
        for (song in allSongs) {
            // Split artist names on common separators using pre-loaded settings
            val artistNames = splitArtistNames(song.artist, preloadedCharDelimiters)
            
            for (artistName in artistNames) {
                val cleanName = artistName.trim()
                
                // Skip invalid artist names
                if (cleanName.isBlank() || cleanName.equals("<unknown>", ignoreCase = true)) {
                    continue
                }
                
                // Add song to artist's collection
                artistMap.getOrPut(cleanName) { mutableListOf() }.add(song)
                
                // Track unique albums for this artist
                if (song.album.isNotBlank()) {
                    albumsByArtist.getOrPut(cleanName) { mutableSetOf() }.add(song.album)
                }
            }
        }
        
        // Create Artist objects from grouped data
        val artists = artistMap.map { (artistName, songs) ->
            val albums = albumsByArtist[artistName] ?: emptySet()
            Artist(
                id = "track_artist_${artistName.hashCode()}", // Generate unique ID based on name
                name = artistName,
                numberOfAlbums = albums.size,
                numberOfTracks = songs.size
            )
        }.sortedBy { it.name.lowercase() }

        Log.d(TAG, "Loaded ${artists.size} artists from track artists (split collaborations)")
        artists
    }
    
    private var cachedDelimitersString: String? = null
    private var cachedDelimitersList: List<String> = emptyList()

    /**
     * Splits artist names on common collaboration separators.
     * Returns a list of individual artist names.
     * Reads AppSettings internally — use [splitArtistNames(String, List<String>)] in hot loops.
     */
    fun splitArtistNames(artistName: String): List<String> {
        val appSettings = AppSettings.getInstance(context)
        val artistSeparatorEnabled = appSettings.artistSeparatorEnabled.value
        val delimiters = if (artistSeparatorEnabled) appSettings.artistSeparatorDelimiters.value else ""
        return chromahub.rhythm.app.util.ArtistSeparator.splitArtistNames(artistName, delimiters, artistSeparatorEnabled)
    }

    /**
     * Splits artist names using pre-loaded character delimiters.
     * Use this overload in hot loops to avoid reading AppSettings per call.
     */
    fun splitArtistNames(artistName: String, preloadedCharDelimiters: List<String>): List<String> {
        val delimiters = preloadedCharDelimiters.joinToString("")
        return chromahub.rhythm.app.util.ArtistSeparator.splitArtistNames(artistName, delimiters, preloadedCharDelimiters.isNotEmpty())
    }
    
    /**
     * New method: Groups artists by album artist from all songs.
     * This provides proper album-based grouping, showing single artist for collaboration albums.
     */
    private suspend fun loadArtistsGroupedByAlbumArtist(): List<Artist> = withContext(Dispatchers.IO) {
        val allSongs = loadSongs()
        val artistMap = mutableMapOf<String, MutableList<Song>>()
        val albumsByArtist = mutableMapOf<String, MutableSet<String>>()
        
        // Group songs by album artist (or track artist if album artist is not available)
        for (song in allSongs) {
            val artistName = (song.albumArtist?.takeIf { it.isNotBlank() } ?: song.artist).trim()
            
            // Skip invalid artist names
            if (artistName.isBlank() || artistName.equals("<unknown>", ignoreCase = true)) {
                continue
            }
            
            // Add song to artist's collection
            artistMap.getOrPut(artistName) { mutableListOf() }.add(song)
            
            // Track unique albums for this artist
            if (song.album.isNotBlank()) {
                albumsByArtist.getOrPut(artistName) { mutableSetOf() }.add(song.album)
            }
        }
        
        // Create Artist objects from grouped data
        val artists = artistMap.map { (artistName, songs) ->
            val albums = albumsByArtist[artistName] ?: emptySet()
            Artist(
                id = "album_artist_${artistName.hashCode()}", // Generate unique ID based on name
                name = artistName,
                numberOfAlbums = albums.size,
                numberOfTracks = songs.size
            )
        }.sortedBy { it.name.lowercase() }
        
        Log.d(TAG, "Loaded ${artists.size} artists grouped by album artist")
        artists
    }

    /**
     * Tries to find a better artist name from their tracks
     */
    private suspend fun findBetterArtistName(artistId: String): String? =
        withContext(Dispatchers.IO) {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Audio.Media.ARTIST_ID} = ?"
            val selectionArgs = arrayOf(artistId)

            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media.ARTIST),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artists = mutableSetOf<String>()

                while (cursor.moveToNext()) {
                    val artist = cursor.getString(artistColumn)
                    if (!artist.isNullOrBlank() && !artist.equals("<unknown>", ignoreCase = true)) {
                        artists.add(artist)
                    }
                }

                // Return the most common non-blank artist name
                artists.maxByOrNull { name ->
                    cursor.moveToFirst()
                    var count = 0
                    while (cursor.moveToNext()) {
                        if (cursor.getString(artistColumn) == name) count++
                    }
                    count
                }
            }
        }

    /**
     * Triggers a full rescan of music data from the device's MediaStore.
     * This method will reload songs, albums, and artists, and return them.
     */
    suspend fun refreshMusicData(
        allowedFormats: Set<String>? = null,
        minimumBitrate: Int = 0,
        minimumDuration: Long = 0L
    ): Triple<List<Song>, List<Album>, List<Artist>> {
        Log.d(TAG, "Refreshing music data...")
        
        // Invalidate in-memory cache to force fresh query
        cachedSongs = null
        cacheTimestamp = 0L
        
        val songs = loadSongs(
            forceRefresh = true,
            allowedFormats = allowedFormats,
            minimumBitrate = minimumBitrate,
            minimumDuration = minimumDuration
        )
        val albums = loadAlbums()
        val artists = loadArtists()
        Log.d(TAG, "Music data refresh complete.")
        
        return Triple(songs, albums, artists)
    }

    private var cachedFolderTree: FolderNode? = null
    private var cachedFolderTreeSongsHash: Int = 0

    private fun fastSongsHash(songs: List<Song>): Int {
        var hash = songs.size
        val step = maxOf(1, songs.size / 100)
        for (i in songs.indices step step) {
            hash = 31 * hash + songs[i].id.hashCode()
        }
        return hash
    }

    suspend fun getFolderTree(): FolderNode = withContext(Dispatchers.IO) {
        val songs = loadSongs()
        val songsHash = fastSongsHash(songs)
        if (cachedFolderTree == null || songsHash != cachedFolderTreeSongsHash) {
            cachedFolderTree = buildFolderTree(songs)
            cachedFolderTreeSongsHash = songsHash
        }
        cachedFolderTree!!
    }
    
    /**
     * Suggest folders to blacklist based on common patterns
     */
    suspend fun suggestFoldersToBlacklist(songs: List<Song>): List<FolderSuggestion> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Analyzing folders for blacklist suggestions...")
        val suggestions = mutableListOf<FolderSuggestion>()
        
        // Get folder statistics
        val folderStats = mutableMapOf<String, MutableList<Song>>()
        songs.forEach { song ->
            try {
                val uri = song.uri
                val projection = arrayOf(MediaStore.Audio.Media.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        val path = cursor.getString(dataIndex)
                        val folder = path.substringBeforeLast("/")
                        folderStats.getOrPut(folder) { mutableListOf() }.add(song)
                    }
                }
            } catch (e: Exception) {
                // Skip songs we can't get path for
            }
        }
        
        // Common system folders to suggest blacklisting
        val systemFolders = listOf(
            "Ringtones", "ringtones", "Notifications", "notifications",  
            "Alarms", "alarms", "WhatsApp", "Telegram", "ui", "system"
        )
        
        folderStats.forEach { (folder, folderSongs) ->
            // Check for system folder names
            systemFolders.forEach { sysFolder ->
                if (folder.contains(sysFolder, ignoreCase = true)) {
                    suggestions.add(FolderSuggestion(
                        path = folder,
                        reason = "System folder: $sysFolder",
                        songCount = folderSongs.size,
                        confidence = 0.9
                    ))
                }
            }
            
            // Check for folders with very short files (likely ringtones/notifications)
            val avgDuration = folderSongs.mapNotNull { it.duration }.average()
            if (avgDuration < 30000) { // Less than 30 seconds
                suggestions.add(FolderSuggestion(
                    path = folder,
                    reason = "Contains short audio files (avg ${(avgDuration / 1000).toInt()}s)",
                    songCount = folderSongs.size,
                    confidence = 0.8
                ))
            }
        }
        
        Log.d(TAG, "Generated ${suggestions.size} folder suggestions")
        return@withContext suggestions.distinctBy { it.path }.sortedByDescending { it.confidence }
    }
    
    /**
     * Calculate comprehensive scan statistics
     */
    fun calculateScanStatistics(
        allSongs: List<Song>,
        filteredSongs: List<Song>,
        albums: List<Album>,
        artists: List<Artist>,
        lastScanTime: Long,
        lastScanDuration: Long
    ): ScanStatistics {
        // Note: storageUsed set to 0 since Song doesn't track file size
        // Would require additional file system access to calculate
        val storageUsed = 0L
        val bitrates = allSongs.mapNotNull { it.bitrate }
        val avgBitrate = if (bitrates.isNotEmpty()) bitrates.average().toInt() else 0
        val duplicatesRemoved = allSongs.size - filteredSongs.size
        
        return ScanStatistics(
            lastScanTime = lastScanTime,
            scanDuration = lastScanDuration,
            totalSongs = allSongs.size,
            filteredSongs = filteredSongs.size,
            totalAlbums = albums.size,
            totalArtists = artists.size,
            storageUsedBytes = storageUsed,
            averageBitrate = avgBitrate,
            duplicatesFound = duplicatesRemoved
        )
    }

    /**
     * Fetches artist images from Deezer API for artists without images
     */
    suspend fun fetchArtistImages(artists: List<Artist>): List<Artist> =
        withContext(Dispatchers.IO) {
            val updatedArtists = mutableListOf<Artist>()

            val allSongsForLocalLookup = runCatching { loadSongs() }.getOrDefault(emptyList())
            val appSettings = AppSettings.getInstance(context)
            val groupByAlbumArtist = appSettings.groupByAlbumArtist.value
            val preloadedCharDelimiters: List<String> = if (appSettings.artistSeparatorEnabled.value) {
                appSettings.artistSeparatorDelimiters.value.toList().map { it.toString() }
            } else {
                emptyList()
            }

            // NetworkClient will handle API key dynamically (user-provided or fallback to default)

            for (artist in artists) {
                try {
                    Log.d(TAG, "Processing artist: ${artist.name}")

                    if (artist.artworkUri != null) {
                        Log.d(
                            TAG,
                            "Artist ${artist.name} already has artwork: ${artist.artworkUri}"
                        )
                        updatedArtists.add(artist)
                        continue
                    }

                    // Check cache first
                    val cachedUri = artistImageCache[artist.name]
                    if (cachedUri != null) {
                        Log.d(TAG, "Using cached image for artist: ${artist.name}")
                        updatedArtists.add(artist.copy(artworkUri = cachedUri))
                        continue
                    }

                    // Check local storage for artist image
                    val localImage = findLocalArtistImage(artist.name)
                    if (localImage != null) {
                        Log.d(TAG, "Using local image for artist: ${artist.name}")
                        artistImageCache[artist.name] = localImage
                        updatedArtists.add(artist.copy(artworkUri = localImage))
                        continue
                    }

                    // Check music-library folders for artist.jpg / band.jpg style images.
                    val localFolderImage = findArtistImageInLibraryFolders(
                        artistName = artist.name,
                        songs = allSongsForLocalLookup,
                        groupByAlbumArtist = groupByAlbumArtist,
                        preloadedCharDelimiters = preloadedCharDelimiters
                    )
                    if (localFolderImage != null) {
                        Log.d(TAG, "Using folder image for artist: ${artist.name} -> $localFolderImage")
                        artistImageCache[artist.name] = localFolderImage
                        updatedArtists.add(artist.copy(artworkUri = localFolderImage))
                        continue
                    }

                    // Skip artists with empty or "Unknown" names
                    if (artist.name.isBlank() || artist.name.equals(
                            "Unknown Artist",
                            ignoreCase = true
                        )
                    ) {
                        Log.d(TAG, "Skipping unknown/blank artist name")
                        val placeholderUri =
                            chromahub.rhythm.app.util.ImageUtils.generatePlaceholderImage(
                                name = "Unknown Artist",
                                size = 500,
                                cacheDir = context.cacheDir
                            )
                        artistImageCache[artist.name] = placeholderUri
                        updatedArtists.add(artist.copy(artworkUri = placeholderUri))
                        continue
                    }

                    // Only try online fetch if network is available and Deezer API is enabled
                    if (isNetworkAvailable() && NetworkClient.isDeezerApiEnabled() && deezerApiService != null) {
                        Log.d(TAG, "Searching for artist on Deezer: ${artist.name}")
                        
                        // Intelligent delay based on API and previous request timing
                        val apiDelay = calculateApiDelay("deezer", System.currentTimeMillis())
                        if (apiDelay > 0) {
                            delay(apiDelay)
                        }
                        updateLastApiCall("deezer", System.currentTimeMillis())
                        
                        try {
                            var deezerArtist: DeezerArtist? = null
                            
                            // First attempt: exact artist name with fuzzy matching
                            var searchResponse = deezerApiService.searchArtists(artist.name)
                            deezerArtist = findBestMatch(searchResponse.data, artist.name)
                            
                            // Second attempt: try with cleaned artist name if first failed
                            if (deezerArtist == null && artist.name.isNotBlank()) {
                                val cleanedName = artist.name
                                    .replace(Regex("\\s*\\([^)]*\\)\\s*"), "") // Remove text in parentheses
                                    .replace(Regex("\\s*&\\s*.*"), "") // Remove everything after &
                                    .replace(Regex("\\s*,\\s*.*"), "") // Remove everything after comma
                                    .replace(Regex("\\s*feat\\.?\\s*.*", RegexOption.IGNORE_CASE), "") // Remove feat.
                                    .replace(Regex("\\s*ft\\.?\\s*.*", RegexOption.IGNORE_CASE), "") // Remove ft.
                                    .trim()
                                
                                if (cleanedName.isNotEmpty() && cleanedName != artist.name) {
                                    Log.d(TAG, "Retrying Deezer search with cleaned name: $cleanedName")
                                    searchResponse = deezerApiService.searchArtists(cleanedName)
                                    deezerArtist = findBestMatch(searchResponse.data, artist.name)
                                }
                            }
                            
                            // Third attempt: try with first word only for multi-word artists
                            if (deezerArtist == null && artist.name.contains(" ")) {
                                val firstWord = artist.name.split(" ").first().trim()
                                if (firstWord.isNotEmpty() && firstWord.length > 2) {
                                    Log.d(TAG, "Retrying Deezer search with first word: $firstWord")
                                    searchResponse = deezerApiService.searchArtists(firstWord)
                                    deezerArtist = findBestMatch(searchResponse.data, artist.name)
                                }
                            }

                            if (deezerArtist != null) {
                                Log.d(TAG, "Found Deezer artist: ${deezerArtist.name} for ${artist.name}")
                                
                                // Choose the highest quality image available
                                val imageUrl = when {
                                    !deezerArtist.pictureXl.isNullOrEmpty() -> deezerArtist.pictureXl
                                    !deezerArtist.pictureBig.isNullOrEmpty() -> deezerArtist.pictureBig
                                    !deezerArtist.pictureMedium.isNullOrEmpty() -> deezerArtist.pictureMedium
                                    !deezerArtist.picture.isNullOrEmpty() -> deezerArtist.picture
                                    else -> null
                                }
                                
                                if (!imageUrl.isNullOrEmpty()) {
                                    val imageUri = Uri.parse(imageUrl)
                                    Log.d(TAG, "Found image URL for ${artist.name}: $imageUrl")
                                    artistImageCache[artist.name] = imageUri
                                    saveLocalArtistImage(artist.name, imageUrl)
                                    updatedArtists.add(artist.copy(artworkUri = imageUri))
                                    continue
                                }
                            } else {
                                Log.d(TAG, "No Deezer artist found for: ${artist.name}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Deezer lookup failed for ${artist.name}: ${e.message}")
                        }
                    } else {
                        Log.d(
                            TAG,
                            "No network connection available while fetching ${artist.name} image"
                        )
                    }

                    // -------- YouTube Music fallback (right after Deezer fails) --------
                    if (NetworkClient.isYTMusicApiEnabled() && ytmusicApiService != null) {
                        try {
                            Log.d(TAG, "Trying YTMusic for artist: ${artist.name}")
                            
                        // Create search request for artist
                        val searchRequest = YTMusicSearchRequest(
                            context = YTMusicContext(YTMusicClient()),
                            query = artist.name,
                            params = "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D" // Artist search filter
                        )
                        
                        val searchResponse = ytmusicApiService.search(request = searchRequest)
                        if (searchResponse.isSuccessful) {
                            val imageUrl = searchResponse.body()?.extractArtistImageUrl()
                            if (!imageUrl.isNullOrEmpty()) {
                                val imageUri = Uri.parse(imageUrl)
                                artistImageCache[artist.name] = imageUri
                                saveLocalArtistImage(artist.name, imageUrl)
                                updatedArtists.add(artist.copy(artworkUri = imageUri))
                                Log.d(TAG, "Found YTMusic image for ${artist.name}: $imageUrl")
                                continue
                            }
                            
                            // If search result has browseId, try to get detailed artist info for better quality image
                            val browseId = searchResponse.body()?.extractArtistBrowseId()
                            if (!browseId.isNullOrEmpty()) {
                                val browseRequest = YTMusicBrowseRequest(
                                    context = YTMusicContext(YTMusicClient()),
                                    browseId = browseId
                                )
                                val artistResponse = ytmusicApiService.getArtist(request = browseRequest)
                                if (artistResponse.isSuccessful) {
                                    val detailedImageUrl = artistResponse.body()?.extractArtistThumbnail()
                                    if (!detailedImageUrl.isNullOrEmpty()) {
                                        val imageUri = Uri.parse(detailedImageUrl)
                                        artistImageCache[artist.name] = imageUri
                                        saveLocalArtistImage(artist.name, detailedImageUrl)
                                        updatedArtists.add(artist.copy(artworkUri = imageUri))
                                        Log.d(TAG, "Found detailed YTMusic image for ${artist.name}: $detailedImageUrl")
                                        continue
                                    }
                                }
                            }
                        }
                        } catch (e: Exception) {
                            Log.w(TAG, "YTMusic fallback failed for ${artist.name}: ${e.message}")
                        }
                    }

                    // If we get here, generate a placeholder image
                    Log.d(TAG, "Generating placeholder for artist: ${artist.name}")
                    val placeholderUri =
                        chromahub.rhythm.app.util.ImageUtils.generatePlaceholderImage(
                            name = artist.name,
                            size = 500,
                            cacheDir = context.cacheDir
                        )
                    artistImageCache[artist.name] = placeholderUri
                    updatedArtists.add(artist.copy(artworkUri = placeholderUri))

                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching artist image for ${artist.name}", e)
                    try {
                        val placeholderUri =
                            chromahub.rhythm.app.util.ImageUtils.generatePlaceholderImage(
                                name = artist.name,
                                size = 500,
                                cacheDir = context.cacheDir
                            )
                        updatedArtists.add(artist.copy(artworkUri = placeholderUri))
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error generating placeholder for ${artist.name}", e2)
                        updatedArtists.add(artist)
                    }
                }
            }

            // Persist the updated artists to the Room database cache
            try {
                val artistEntities = updatedArtists.map { artist ->
                    ArtistEntity(
                        id = artist.id,
                        name = artist.name,
                        artworkUri = artist.artworkUri?.toString(),
                        numberOfAlbums = artist.numberOfAlbums,
                        numberOfTracks = artist.numberOfTracks,
                        groupByAlbumArtist = groupByAlbumArtist
                    )
                }
                roomDb.artistDao().insertAll(artistEntities)
                Log.d(TAG, "Successfully persisted ${artistEntities.size} updated artists to Room")
            } catch (e: Exception) {
                Log.e(TAG, "Error persisting updated artists to Room", e)
            }

            updatedArtists
        }
    /**
     * Extracts embedded lyrics from audio file metadata - REDONE FROM SCRATCH
     * 
     * Improved extraction with:
     * 1. Better ID3v2.3/v2.4 USLT frame parsing
     * 2. Proper synchsafe integer handling
     * 3. Multiple charset support (ISO-8859-1, UTF-16, UTF-8)
     * 4. Safety checks to prevent hangs and crashes
     * 5. Support for both synced LRC and plain text lyrics
     * 6. Fallback to MediaMetadataRetriever for FLAC/other formats
     */
    private fun getEmbeddedLyrics(songUri: Uri): LyricsData? {
        return try {
            Log.d(TAG, "===== GET EMBEDDED LYRICS START: $songUri =====")
            
            // Primary method: jaudiotagger (supports robust extraction for multiple formats directly)
            val filePath = getFilePathFromUri(songUri)
            if (filePath != null) {
                try {
                    val audioFile = org.jaudiotagger.audio.AudioFileIO.read(java.io.File(filePath))
                    val tag = audioFile.tag
                    if (tag != null) {
                        val lyrics = tag.getFirst(org.jaudiotagger.tag.FieldKey.LYRICS)
                        if (!lyrics.isNullOrBlank()) {
                            Log.d(TAG, "===== FOUND LYRICS VIA JAUDIOTAGGER =====")
                            val parsed = parseLyricsData(lyrics)
                            if (parsed != null) return parsed
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "===== JAUDIOTAGGER LYRICS EXTRACTION FAILED: ${e.message} =====")
                }
            }
            
            // Secondary method: Direct ID3v2 tag parsing (for MP3)
            val id3Lyrics = extractLyricsFromID3v2(songUri)
            if (id3Lyrics != null) {
                Log.d(TAG, "===== FOUND LYRICS VIA ID3V2 =====")
                return id3Lyrics
            }
            
            // Fallback: MediaMetadataRetriever (for FLAC, M4A, etc.)
            Log.d(TAG, "===== TRYING MediaMetadataRetriever =====")
            val retrieverLyrics = extractLyricsViaRetriever(songUri)
            if (retrieverLyrics != null) {
                Log.d(TAG, "===== FOUND LYRICS VIA RETRIEVER =====")
                return retrieverLyrics
            }
            
            // Final fallback: TagLib (handles formats that jaudiotagger cannot read)
            Log.d(TAG, "===== TRYING TAGLIB =====")
            val tagLibLyrics = filePath?.let { MediaUtils.readLyricsViaTagLib(it) }
            if (!tagLibLyrics.isNullOrBlank()) {
                Log.d(TAG, "===== FOUND LYRICS VIA TAGLIB =====")
                val parsed = parseLyricsData(tagLibLyrics)
                if (parsed != null) return parsed
            }
            
            Log.d(TAG, "===== NO EMBEDDED LYRICS FOUND =====")
            null
        } catch (e: Exception) {
            Log.w(TAG, "===== EMBEDDED LYRICS EXTRACTION FAILED: ${e.message} =====")
            null
        }
    }
    
    /**
     * Extract lyrics using MediaMetadataRetriever (works for FLAC, M4A, etc.)
     * For FLAC: Checks Vorbis comments (LYRICS/UNSYNCEDLYRICS tags)
     */
    private fun extractLyricsViaRetriever(songUri: Uri): LyricsData? {
        return try {
            Log.d(TAG, "===== extractLyricsViaRetriever START: $songUri =====")
            val retriever = android.media.MediaMetadataRetriever()
            retriever.use {
                context.contentResolver.openFileDescriptor(songUri, "r")?.use { pfd ->
                    it.setDataSource(pfd.fileDescriptor)
                    
                    // Try different metadata keys that might contain lyrics
                    // FLAC uses custom Vorbis comments, but Android exposes some through standard keys
                    val possibleKeys = listOf(
                        android.media.MediaMetadataRetriever.METADATA_KEY_WRITER,  // Sometimes contains lyrics
                        android.media.MediaMetadataRetriever.METADATA_KEY_COMPOSER, // Fallback
                    )
                    
                    for (key in possibleKeys) {
                        val value = it.extractMetadata(key)
                        if (value != null && value.isNotBlank() && value.length > 50) { // Likely lyrics if > 50 chars
                            Log.d(TAG, "Found potential lyrics in metadata key $key (${value.length} chars)")
                            val parsed = parseLyricsData(value)
                            if (parsed != null) return@use parsed
                        }
                    }
                    
                    // For FLAC files, try direct Vorbis comment parsing
                    val filePath = getFilePathFromUri(songUri)
                    Log.d(TAG, "===== Resolved file path: $filePath =====")
                    
                    if (filePath?.endsWith(".flac", ignoreCase = true) == true) {
                        Log.d(TAG, "===== Detected FLAC file, trying FLAC extraction =====")
                        return@use extractLyricsFromFLAC(filePath)
                    }
                    
                    // For M4A files, try direct iTunes metadata parsing
                    if (filePath?.endsWith(".m4a", ignoreCase = true) == true) {
                        Log.d(TAG, "===== Detected M4A file, trying M4A extraction =====")
                        return@use extractLyricsFromM4A(filePath)
                    }
                    
                    // For OGG/Opus files, try direct Vorbis comment parsing
                    if (filePath?.endsWith(".ogg", ignoreCase = true) == true || 
                        filePath?.endsWith(".oga", ignoreCase = true) == true ||
                        filePath?.endsWith(".opus", ignoreCase = true) == true) {
                        Log.d(TAG, "===== Detected OGG/Opus file, trying OGG extraction =====")
                        return@use extractLyricsFromOGG(filePath)
                    }
                    
                    Log.d(TAG, "===== No specific format handler matched =====")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "===== extractLyricsViaRetriever FAILED: ${e.message} =====")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Extract lyrics from FLAC Vorbis comments
     */
    private fun extractLyricsFromFLAC(filePath: String): LyricsData? {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists() || !file.canRead()) return null
            
            java.io.RandomAccessFile(file, "r").use { raf ->
                // Check FLAC signature
                val signature = ByteArray(4)
                if (raf.read(signature) != 4) return@use null
                if (String(signature, Charsets.ISO_8859_1) != "fLaC") {
                    Log.d(TAG, "Not a valid FLAC file")
                    return@use null
                }
                
                // Read metadata blocks
                var lastBlock = false
                while (!lastBlock && raf.filePointer < file.length()) {
                    val blockHeader = raf.read()
                    if (blockHeader == -1) break
                    
                    lastBlock = (blockHeader and 0x80) != 0
                    val blockType = blockHeader and 0x7F
                    
                    // Read block length (24-bit big-endian)
                    val length = ((raf.read() and 0xFF) shl 16) or
                               ((raf.read() and 0xFF) shl 8) or
                               (raf.read() and 0xFF)
                    
                    if (length <= 0 || length > 16_777_215) break // Max 16MB block
                    
                    // Block type 4 = VORBIS_COMMENT
                    if (blockType == 4) {
                        val commentData = ByteArray(length)
                        if (raf.read(commentData) != length) break
                        
                        val lyrics = parseVorbisComments(commentData)
                        if (lyrics != null) {
                            Log.d(TAG, "Found lyrics in FLAC Vorbis comments")
                            return@use lyrics
                        }
                    } else {
                        // Skip this block
                        raf.seek(raf.filePointer + length)
                    }
                }
                
                Log.d(TAG, "No LYRICS tag found in FLAC Vorbis comments")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "FLAC lyrics extraction failed: ${e.message}")
            null
        }
    }
    
    /**
     * Parse Vorbis comments to extract LYRICS or UNSYNCEDLYRICS tags
     */
    private fun parseVorbisComments(data: ByteArray): LyricsData? {
        val commentEntries = parseVorbisCommentEntries(data) ?: return null

        for ((key, value) in commentEntries) {
            if ((key == "LYRICS" || key == "UNSYNCEDLYRICS") && value.isNotBlank()) {
                Log.d(TAG, "Found $key tag in Vorbis comments (${value.length} chars)")
                return parseLyricsData(value)
            }
        }

        return null
    }

    private fun parseVorbisCommentEntries(data: ByteArray): List<Pair<String, String>>? {
        return try {
            var pos = 0

            // Read vendor string length (little-endian 32-bit).
            if (pos + 4 > data.size) return null
            val vendorLength = readLittleEndianInt(data, pos)
            pos += 4

            // Skip vendor string.
            if (vendorLength < 0 || pos + vendorLength > data.size) return null
            pos += vendorLength

            // Read number of comments.
            if (pos + 4 > data.size) return null
            val commentCount = readLittleEndianInt(data, pos)
            pos += 4
            if (commentCount < 0 || commentCount > 10_000) return null

            val comments = mutableListOf<Pair<String, String>>()
            for (i in 0 until commentCount) {
                if (pos + 4 > data.size) break

                val commentLength = readLittleEndianInt(data, pos)
                pos += 4
                if (commentLength < 0 || pos + commentLength > data.size) break

                val comment = String(data, pos, commentLength, Charsets.UTF_8)
                pos += commentLength

                val parts = comment.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].uppercase()
                    val value = normalizeMetadataText(parts[1])?.trim().orEmpty()
                    if (key.isNotBlank() && value.isNotBlank()) {
                        comments.add(key to value)
                    }
                }
            }

            comments
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Vorbis comments: ${e.message}")
            null
        }
    }

    private fun readLittleEndianInt(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
    
    /**
     * Extract lyrics from OGG/Vorbis files
     * OGG uses the same Vorbis comment format as FLAC
     */
    private fun extractLyricsFromOGG(filePath: String): LyricsData? {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists() || !file.canRead()) return null
            
            java.io.RandomAccessFile(file, "r").use { raf ->
                // Check OGG signature
                val signature = ByteArray(4)
                if (raf.read(signature) != 4) return@use null
                if (String(signature, Charsets.ISO_8859_1) != "OggS") {
                    Log.d(TAG, "Not a valid OGG file")
                    return@use null
                }
                
                // OGG files are organized into pages
                // Vorbis comments are typically in the second page (after the identification header)
                var foundCommentHeader = false
                var attemptCount = 0
                val maxAttempts = 100 // Prevent infinite loops
                
                // Reset to beginning
                raf.seek(0)
                
                while (raf.filePointer < file.length() && attemptCount < maxAttempts) {
                    attemptCount++
                    
                    // Read OGG page header
                    val pageSignature = ByteArray(4)
                    if (raf.read(pageSignature) != 4) break
                    if (String(pageSignature, Charsets.ISO_8859_1) != "OggS") {
                        // Try to resync - search for next OggS
                        val nextOggS = findNextOggSPage(raf)
                        if (nextOggS == -1L) break
                        raf.seek(nextOggS)
                        continue
                    }
                    
                    // Skip version (1 byte) and header type (1 byte)
                    raf.skipBytes(2)
                    
                    // Skip granule position (8 bytes), serial number (4 bytes), 
                    // sequence number (4 bytes), checksum (4 bytes)
                    raf.skipBytes(20)
                    
                    // Read number of page segments
                    val numSegments = raf.read()
                    if (numSegments == -1 || numSegments < 0) break
                    
                    // Read segment table
                    val segmentTable = ByteArray(numSegments)
                    if (raf.read(segmentTable) != numSegments) break
                    
                    // Calculate total page payload size
                    val pageSize = segmentTable.sumOf { (it.toInt() and 0xFF) }
                    
                    // Read the page data
                    if (pageSize > 0 && pageSize < 1_000_000) { // Safety limit: 1MB max page
                        val pageData = ByteArray(pageSize)
                        if (raf.read(pageData) != pageSize) break
                        
                        // Check if this is a Vorbis comment header
                        // Vorbis comment header starts with packet type 0x03 followed by "vorbis"
                        if (pageData.size >= 7 && 
                            pageData[0] == 0x03.toByte() &&
                            String(pageData.copyOfRange(1, 7), Charsets.ISO_8859_1) == "vorbis") {
                            
                            Log.d(TAG, "Found Vorbis comment header in OGG file")
                            foundCommentHeader = true
                            
                            // Parse the Vorbis comments (skip the 7-byte header)
                            val commentData = pageData.copyOfRange(7, pageData.size)
                            val lyrics = parseVorbisComments(commentData)
                            if (lyrics != null) {
                                Log.d(TAG, "Found lyrics in OGG Vorbis comments")
                                return@use lyrics
                            }
                        }
                    } else {
                        // Skip invalid or too-large page
                        if (pageSize > 0) {
                            raf.seek(raf.filePointer + pageSize)
                        }
                    }
                    
                    // If we found the comment header but no lyrics, we can stop
                    if (foundCommentHeader) break
                }
                
                Log.d(TAG, "No LYRICS tag found in OGG Vorbis comments (checked $attemptCount pages)")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "OGG lyrics extraction failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Helper function to find the next OggS page signature in the file
     */
    private fun findNextOggSPage(raf: java.io.RandomAccessFile): Long {
        val buffer = ByteArray(4096)
        var bytesRead: Int
        val targetPattern = "OggS".toByteArray(Charsets.ISO_8859_1)
        
        while (raf.filePointer < raf.length()) {
            bytesRead = raf.read(buffer)
            if (bytesRead == -1) return -1
            
            for (i in 0 until bytesRead - 3) {
                if (buffer[i] == targetPattern[0] &&
                    buffer[i + 1] == targetPattern[1] &&
                    buffer[i + 2] == targetPattern[2] &&
                    buffer[i + 3] == targetPattern[3]) {
                    return raf.filePointer - bytesRead + i
                }
            }
        }
        return -1
    }
    
    /**
     * Extract lyrics from M4A iTunes metadata (©lyr atom and alternatives)
     */
    private fun extractLyricsFromM4A(filePath: String): LyricsData? {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists() || !file.canRead()) return null
            
            java.io.RandomAccessFile(file, "r").use { raf ->
                // M4A files use MP4/QuickTime container format with atoms
                // We need to find the 'moov' atom, then 'udta', then 'meta', then 'ilst', then lyrics atoms
                
                // Try multiple possible lyrics atom names (different taggers use different formats)
                val lyricsAtomNames = listOf(
                    "©lyr",  // Standard iTunes lyrics
                    "\u00a9lyr", // Alternative encoding of ©
                    "lyr ",  // Alternative with space
                    "lyr\u0000",  // Null-terminated variant
                    "USLT",  // Unsynchronized lyrics (ID3-style)
                    "©day",  // Some taggers incorrectly use this
                    "----",  // Custom freeform atom (may contain lyrics)
                    "desc",  // Description field sometimes used
                    "©des",  // Description variant
                    "©cmt",  // Comment field (sometimes contains lyrics)
                    "©CMT"   // Comment uppercase variant
                )
                
                for (atomName in lyricsAtomNames) {
                    val lyricsAtom = findM4AAtom(raf, atomName)
                    if (lyricsAtom != null && lyricsAtom.isNotBlank()) {
                        Log.d(TAG, "Found text in M4A atom '$atomName' (length: ${lyricsAtom.length}): ${lyricsAtom.take(100)}...")
                        val parsed = parseLyricsData(lyricsAtom)
                        if (parsed != null) {
                            Log.d(TAG, "✓ Accepted lyrics from atom '$atomName'")
                            return@use parsed
                        } else {
                            Log.d(TAG, "✗ Text from atom '$atomName' was rejected as not lyrics-like")
                        }
                    }
                }
                
                // Last resort: scan all text atoms and look for lyrics-like content
                Log.d(TAG, "Attempting comprehensive M4A atom scan for lyrics-like content")
                val allTextAtoms = findAllTextAtoms(raf)
                Log.d(TAG, "Found ${allTextAtoms.size} text atoms in M4A file")
                
                for ((atomName, content) in allTextAtoms) {
                    Log.d(TAG, "Checking text atom '$atomName' (length: ${content.length}): ${content.take(100)}...")
                    if (content.length > 100 && looksLikeLyrics(content)) {
                        Log.d(TAG, "Text atom '$atomName' passed lyrics validation")
                        val parsed = parseLyricsData(content)
                        if (parsed != null) {
                            Log.d(TAG, "✓ Accepted lyrics from comprehensive scan atom '$atomName'")
                            return@use parsed
                        }
                    } else {
                        Log.d(TAG, "Text atom '$atomName' rejected: length=${content.length}, looksLikeLyrics=${looksLikeLyrics(content)}")
                    }
                }
                
                Log.d(TAG, "No lyrics atoms found in M4A file (checked: ${lyricsAtomNames.joinToString(", ")}, scanned ${allTextAtoms.size} text atoms)")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "M4A lyrics extraction failed: ${e.message}")
            null
        }
    }
    
    /**
     * Find and extract a specific atom from M4A file
     */
    private fun findM4AAtom(raf: java.io.RandomAccessFile, targetAtom: String): String? {
        try {
            // Recursively search for atoms
            return searchM4AAtoms(raf, 0, raf.length(), targetAtom, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to search M4A atoms: ${e.message}")
            return null
        }
    }
    
    /**
     * Scan M4A file and collect all text atoms (for debugging and fallback lyrics search)
     */
    private fun findAllTextAtoms(raf: java.io.RandomAccessFile): Map<String, String> {
        val textAtoms = mutableMapOf<String, String>()
        try {
            collectTextAtoms(raf, 0, raf.length(), 0, textAtoms)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to collect text atoms: ${e.message}")
        }
        return textAtoms
    }
    
    /**
     * Recursively collect all text-containing atoms
     */
    private fun collectTextAtoms(
        raf: java.io.RandomAccessFile,
        offset: Long,
        endOffset: Long,
        depth: Int,
        results: MutableMap<String, String>
    ) {
        if (depth > 10 || results.size > 50) return // Prevent excessive recursion/results
        
        var pos = offset
        val atomsAtThisLevel = mutableListOf<String>()
        
        while (pos < endOffset - 8) {
            try {
                raf.seek(pos)
                
                // Read atom size and type
                val sizeBytes = ByteArray(4)
                if (raf.read(sizeBytes) != 4) break
                var atomSize = ((sizeBytes[0].toInt() and 0xFF) shl 24) or
                              ((sizeBytes[1].toInt() and 0xFF) shl 16) or
                              ((sizeBytes[2].toInt() and 0xFF) shl 8) or
                              (sizeBytes[3].toInt() and 0xFF)
                
                val typeBytes = ByteArray(4)
                if (raf.read(typeBytes) != 4) break
                val atomType = String(typeBytes, Charsets.ISO_8859_1)
                
                if (atomSize <= 0 || atomSize > 100_000_000) break
                
                // Track atoms for debugging
                atomsAtThisLevel.add("$atomType($atomSize)")
                
                // Look for 'data' atoms that contain text
                if (atomType == "data" && atomSize > 16 && atomSize < 500_000) {
                    raf.seek(pos + 16)
                    val dataBytes = ByteArray((atomSize - 16).coerceAtMost(100_000))
                    if (raf.read(dataBytes) == dataBytes.size) {
                        val text = String(dataBytes, Charsets.UTF_8).trim('\u0000', ' ', '\n', '\r')
                        if (text.length > 10 && text.any { it.isLetter() }) {
                            Log.d(TAG, "Found text in 'data' atom at depth $depth: ${text.take(50)}...")
                            results["data_${pos}_${depth}"] = text
                        }
                    }
                }
                
                // Try to extract text from ANY atom with reasonable size
                if (atomSize > 20 && atomSize < 500_000 && atomType.length == 4) {
                    raf.seek(pos + 8)
                    val contentBytes = ByteArray((atomSize - 8).coerceAtMost(100_000))
                    if (raf.read(contentBytes) == contentBytes.size) {
                        // Skip binary data (check for text-like content)
                        val possibleText = String(contentBytes, Charsets.UTF_8)
                        val alphaCount = possibleText.count { it.isLetter() }
                        if (alphaCount > 50 && alphaCount > possibleText.length * 0.3) {
                            val text = possibleText.trim('\u0000', ' ', '\n', '\r')
                            if (text.length > 100 && looksLikeLyrics(text)) {
                                Log.d(TAG, "Found lyrics-like text in '$atomType' atom at depth $depth: ${text.take(50)}...")
                                results["${atomType}_${pos}_${depth}"] = text
                            }
                        }
                    }
                }
                
                // Special handling for 'meta' atom (has 4 bytes version/flags before children)
                var childOffset = pos + 8
                if (atomType == "meta") {
                    childOffset = pos + 12 // Skip version/flags
                }
                
                // Recurse into container atoms
                if (atomType == "moov" || atomType == "udta" || atomType == "meta" || 
                    atomType == "ilst" || atomType.startsWith("©") || atomType == "----") {
                    collectTextAtoms(raf, childOffset, pos + atomSize, depth + 1, results)
                }
                
                pos += atomSize
                if (atomSize < 8) break
            } catch (e: Exception) {
                Log.w(TAG, "Error scanning atom at pos $pos, depth $depth: ${e.message}")
                pos += 8 // Skip problematic atom
            }
        }
        
        // Log atoms found at this level
        if (atomsAtThisLevel.isNotEmpty() && depth <= 5) {
            Log.d(TAG, "M4A depth $depth atoms: ${atomsAtThisLevel.take(10).joinToString(", ")}${if (atomsAtThisLevel.size > 10) "... (${atomsAtThisLevel.size} total)" else ""}")
        }
    }
    
    /**
     * Recursively search through M4A atom hierarchy
     */
    private fun searchM4AAtoms(
        raf: java.io.RandomAccessFile,
        offset: Long,
        endOffset: Long,
        targetAtom: String,
        depth: Int
    ): String? {
        if (depth > 10) return null // Prevent infinite recursion
        
        var pos = offset
        val foundAtoms = mutableListOf<String>() // Track what we find for debugging
        
        while (pos < endOffset - 8) {
            raf.seek(pos)
            
            // Read atom size (big-endian 32-bit)
            val sizeBytes = ByteArray(4)
            if (raf.read(sizeBytes) != 4) break
            var atomSize = ((sizeBytes[0].toInt() and 0xFF) shl 24) or
                          ((sizeBytes[1].toInt() and 0xFF) shl 16) or
                          ((sizeBytes[2].toInt() and 0xFF) shl 8) or
                          (sizeBytes[3].toInt() and 0xFF)
            
            // Read atom type (4 ASCII chars)
            val typeBytes = ByteArray(4)
            if (raf.read(typeBytes) != 4) break
            val atomType = String(typeBytes, Charsets.ISO_8859_1)
            
            // Log atoms at interesting depths (metadata level) - more inclusive pattern
            if (depth >= 2 && atomType.isNotEmpty()) {
                val isPrintable = atomType.all { it.isLetterOrDigit() || it in "©@- _" }
                if (isPrintable) {
                    foundAtoms.add(atomType)
                }
            }
            
            // Handle extended size (size = 1 means 64-bit size follows)
            if (atomSize == 1) {
                val extSizeBytes = ByteArray(8)
                if (raf.read(extSizeBytes) != 8) break
                atomSize = 0 // Skip large atoms for safety
            }
            
            if (atomSize <= 0 || atomSize > 100_000_000) break // Max 100MB per atom
            
            // Check if this is our target atom
            if (atomType == targetAtom) {
                // For ©lyr, data is usually in a nested 'data' atom
                val dataAtom = searchM4AAtoms(raf, pos + 8, pos + atomSize, "data", depth + 1)
                if (dataAtom != null) return dataAtom
                
                // If no 'data' atom, try reading directly
                if (atomSize > 16 && atomSize < 1_000_000) {
                    val dataBytes = ByteArray(atomSize - 8)
                    raf.seek(pos + 8)
                    raf.read(dataBytes)
                    val text = String(dataBytes, Charsets.UTF_8).trim('\u0000')
                    if (text.isNotBlank()) return text
                }
            }
            
            // Check if this is a 'data' atom (contains actual text)
            if (atomType == "data" && atomSize > 16 && atomSize < 1_000_000) {
                // Read data type flag (at position 8-11)
                raf.seek(pos + 8)
                val typeFlag = ByteArray(4)
                raf.read(typeFlag)
                val dataType = ((typeFlag[0].toInt() and 0xFF) shl 24) or
                              ((typeFlag[1].toInt() and 0xFF) shl 16) or
                              ((typeFlag[2].toInt() and 0xFF) shl 8) or
                              (typeFlag[3].toInt() and 0xFF)
                
                // Type 1 = UTF-8 text, Type 0 = binary/implicit
                // Skip 16 bytes total (8 for size+type, 8 for version+flags+reserved)
                raf.seek(pos + 16)
                val dataBytes = ByteArray(atomSize - 16)
                if (raf.read(dataBytes) == dataBytes.size) {
                    // Try both UTF-8 and ISO-8859-1 encodings
                    var text = String(dataBytes, Charsets.UTF_8).trim('\u0000', ' ', '\n', '\r')
                    
                    // If UTF-8 decode fails or looks wrong, try ISO-8859-1
                    if (text.isEmpty() || text.any { it == '\uFFFD' }) {
                        text = String(dataBytes, Charsets.ISO_8859_1).trim('\u0000', ' ', '\n', '\r')
                    }
                    
                    if (text.isNotBlank() && text.length > 5) { // Reasonable lyrics length
                        Log.d(TAG, "Extracted text from 'data' atom (type: $dataType, length: ${text.length})")
                        return text
                    }
                }
            }
            
            // Recurse into container atoms
            // Special handling for 'meta' atom which has 4 bytes version/flags before children
            val childOffset = if (atomType == "meta") pos + 12 else pos + 8
            
            if (atomType == "moov" || atomType == "udta" || atomType == "meta" || 
                atomType == "ilst" || atomType.startsWith("©")) {
                val result = searchM4AAtoms(raf, childOffset, pos + atomSize, targetAtom, depth + 1)
                if (result != null) return result
            }
            
            // Move to next atom
            pos += atomSize
            if (atomSize < 8) break // Prevent infinite loop on malformed files
        }
        
        // Log what atoms we found at metadata level (helps debugging)
        if (foundAtoms.isNotEmpty() && depth >= 2) {
            Log.d(TAG, "M4A atoms found at depth $depth: ${foundAtoms.take(20).joinToString(", ")}${if (foundAtoms.size > 20) "..." else ""}")
        }
        
        return null
    }
    
    /**
     * NEW: Extract lyrics from ID3v2 tags with improved parsing
     * Supports ID3v2.3 and ID3v2.4 with synchsafe integers
     */
    private fun extractLyricsFromID3v2(songUri: Uri): LyricsData? {
        val filePath = getFilePathFromUri(songUri)
        
        if (filePath == null) {
            Log.d(TAG, "Could not resolve file path from URI")
            return null
        }
        
        val file = java.io.File(filePath)
        
        if (!file.exists()) {
            Log.d(TAG, "File does not exist: $filePath")
            return null
        }
        
        if (!file.canRead()) {
            Log.d(TAG, "Cannot read file: $filePath")
            return null
        }
        
        // Check if file is MP3 (ID3 tags only work for MP3)
        if (!filePath.endsWith(".mp3", ignoreCase = true)) {
            Log.d(TAG, "Not an MP3 file, skipping ID3v2 parsing: $filePath")
            return null
        }
        
        Log.d(TAG, "Parsing ID3v2 tags from: $filePath")
        
        return java.io.RandomAccessFile(file, "r").use { raf ->
            // Read and validate ID3v2 header
            val header = ByteArray(10)
            if (raf.read(header) != 10) return@use null
            
            // Check ID3v2 signature
            if (header[0] != 'I'.code.toByte() || 
                header[1] != 'D'.code.toByte() || 
                header[2] != '3'.code.toByte()) {
                return@use null
            }
            
            val majorVersion = header[3].toInt() and 0xFF
            val minorVersion = header[4].toInt() and 0xFF
            val flags = header[5].toInt() and 0xFF
            
            Log.d(TAG, "ID3v2.$majorVersion.$minorVersion detected, flags: $flags")
            
            // Support v2.2, v2.3, and v2.4
            if (majorVersion < 2 || majorVersion > 4) {
                Log.d(TAG, "Unsupported ID3 version: $majorVersion")
                return@use null
            }
            
            // Parse synchsafe integer for tag size
            val tagSize = decodeSynchsafe(
                header[6].toInt() and 0xFF,
                header[7].toInt() and 0xFF,
                header[8].toInt() and 0xFF,
                header[9].toInt() and 0xFF
            )
            
            // Validate tag size (max 10MB)
            if (tagSize <= 0 || tagSize > 10_485_760) {
                Log.w(TAG, "Invalid tag size: $tagSize")
                return@use null
            }
            
            // Read tag data
            val tagData = ByteArray(tagSize)
            val bytesRead = raf.read(tagData)
            if (bytesRead != tagSize) {
                Log.w(TAG, "Failed to read complete tag data")
                return@use null
            }

            // ID3 unsynchronization inserts 0x00 bytes after 0xFF; remove them before frame parsing.
            val normalizedTagData = if ((flags and 0x80) != 0) {
                removeId3Unsynchronization(tagData)
            } else {
                tagData
            }
            
            // Parse frames and find USLT
            parseID3v2Frames(normalizedTagData, majorVersion)
        }
    }
    
    /**
     * NEW: Decode synchsafe integer (ID3v2 size encoding)
     */
    private fun decodeSynchsafe(b1: Int, b2: Int, b3: Int, b4: Int): Int {
        return ((b1 and 0x7F) shl 21) or
               ((b2 and 0x7F) shl 14) or
               ((b3 and 0x7F) shl 7) or
               (b4 and 0x7F)
    }

    private fun removeId3Unsynchronization(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data

        val output = ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            val current = data[i]
            output.write(current.toInt())

            if (current == 0xFF.toByte() && i + 1 < data.size && data[i + 1] == 0.toByte()) {
                i += 2
            } else {
                i += 1
            }
        }

        return output.toByteArray()
    }
    
    /**
     * Parse ID3v2 frames and extract USLT (lyrics) or SYLT (synchronized lyrics) frame
     * Supports ID3v2.2 (3-byte frame IDs), v2.3, and v2.4
     */
    private fun parseID3v2Frames(tagData: ByteArray, version: Int): LyricsData? {
        var pos = 0
        var frameCount = 0
        val maxFrames = 1000
        val isV2_2 = version == 2
        
        val frameHeaderSize = if (isV2_2) 6 else 10
        
        while (pos < tagData.size - frameHeaderSize && frameCount < maxFrames) {
            frameCount++
            
            if (tagData[pos] == 0.toByte()) break
            
            val frameIdLen = if (isV2_2) 3 else 4
            val frameId = String(tagData.copyOfRange(pos, pos + frameIdLen), Charsets.ISO_8859_1)
            
            val frameSize = if (isV2_2) {
                // v2.2 uses 3-byte big-endian integer
                ((tagData[pos + 3].toInt() and 0xFF) shl 16) or
                ((tagData[pos + 4].toInt() and 0xFF) shl 8) or
                (tagData[pos + 5].toInt() and 0xFF)
            } else if (version == 4) {
                decodeSynchsafe(
                    tagData[pos + 4].toInt() and 0xFF,
                    tagData[pos + 5].toInt() and 0xFF,
                    tagData[pos + 6].toInt() and 0xFF,
                    tagData[pos + 7].toInt() and 0xFF
                )
            } else {
                ((tagData[pos + 4].toInt() and 0xFF) shl 24) or
                ((tagData[pos + 5].toInt() and 0xFF) shl 16) or
                ((tagData[pos + 6].toInt() and 0xFF) shl 8) or
                (tagData[pos + 7].toInt() and 0xFF)
            }
            
            if (frameSize <= 0 || frameSize > tagData.size - pos - frameHeaderSize || frameSize > 2_097_152) {
                Log.w(TAG, "Invalid frame size: $frameSize for $frameId")
                break
            }
            
            // Map v2.2 frame IDs to v2.3+ equivalents
            val mappedFrameId = when (frameId) {
                "ULT" -> "USLT"  // v2.2 unsynchronized lyrics
                "SLT" -> "SYLT"  // v2.2 synchronized lyrics
                else -> frameId
            }
            
            when (mappedFrameId) {
                "USLT" -> {
                    val lyricsData = parseUSLTFrame(tagData, pos + frameHeaderSize, frameSize)
                    if (lyricsData != null) {
                        Log.d(TAG, "Successfully extracted USLT lyrics")
                        return lyricsData
                    }
                }
                "SYLT" -> {
                    val lyricsData = parseSYLTFrame(tagData, pos + frameHeaderSize, frameSize)
                    if (lyricsData != null) {
                        Log.d(TAG, "Successfully extracted SYLT lyrics")
                        return lyricsData
                    }
                }
            }
            
            pos += frameHeaderSize + frameSize
        }
        
        return null
    }
    
    /**
     * Parse SYLT (Synchronized lyrics) frame content
     */
    private fun parseSYLTFrame(data: ByteArray, offset: Int, size: Int): LyricsData? {
        try {
            if (size < 6) return null
            
            val encoding = data[offset].toInt() and 0xFF
            val charset = when (encoding) {
                0 -> Charsets.ISO_8859_1
                1 -> Charsets.UTF_16
                2 -> Charsets.UTF_16
                3 -> Charsets.UTF_8
                else -> Charsets.ISO_8859_1
            }
            val lang = String(data, offset + 1, 3, Charsets.ISO_8859_1)
            val timestampFormat = data[offset + 4].toInt() and 0xFF
            val contentType = data[offset + 5].toInt() and 0xFF
            
            var pos = offset + 6
            val frameEnd = offset + size
            
            // Skip content descriptor
            if (encoding == 1 || encoding == 2) {
                while (pos + 1 < frameEnd) {
                    if (data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
                        pos += 2
                        break
                    }
                    pos += 2
                }
            } else {
                while (pos < frameEnd && data[pos] != 0.toByte()) pos++
                if (pos < frameEnd) pos++
            }
            
            // Parse sync entries: each entry has text (null-terminated) + timestamp (4 bytes)
            val sb = StringBuilder()
            while (pos + 5 <= frameEnd) {
                val textEnd = if (encoding == 1 || encoding == 2) {
                    var end = pos
                    while (end + 1 < frameEnd) {
                        if (data[end] == 0.toByte() && data[end + 1] == 0.toByte()) break
                        end += 2
                    }
                    end
                } else {
                    var end = pos
                    while (end < frameEnd && data[end] != 0.toByte()) end++
                    end
                }
                
                val text = String(data, pos, textEnd - pos, charset)
                val delimLen = if (encoding == 1 || encoding == 2) 2 else 1
                pos = textEnd + delimLen
                
                if (pos + 4 > frameEnd) break
                
                // Read timestamp based on format
                val timestamp = when (timestampFormat) {
                    1 -> { // Absolute milliseconds (32-bit big-endian)
                        ((data[pos].toInt() and 0xFF) shl 24) or
                        ((data[pos + 1].toInt() and 0xFF) shl 16) or
                        ((data[pos + 2].toInt() and 0xFF) shl 8) or
                        (data[pos + 3].toInt() and 0xFF)
                    }
                    2 -> { // Frames (for ID3v2, not CD frames)
                        val frames = ((data[pos].toInt() and 0xFF) shl 24) or
                                     ((data[pos + 1].toInt() and 0xFF) shl 16) or
                                     ((data[pos + 2].toInt() and 0xFF) shl 8) or
                                     (data[pos + 3].toInt() and 0xFF)
                        (frames * 1000) / 75
                    }
                    else -> 0
                }
                pos += 4
                
                if (text.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append("[$timestamp]$text")
                }
            }
            
            val lyrics = sb.toString()
            if (lyrics.isNotBlank()) {
                return parseLyricsData(lyrics)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse SYLT frame: ${e.message}")
        }
        return null
    }
    
    /**
     * NEW: Parse USLT frame content
     */
    private fun parseUSLTFrame(data: ByteArray, offset: Int, size: Int): LyricsData? {
        try {
            if (size < 4) return null
            
            // USLT frame structure:
            // - Text encoding (1 byte)
            // - Language (3 bytes, ISO-639-2)
            // - Content descriptor (null-terminated string)
            // - Lyrics text (rest of frame)
            
            val encoding = data[offset].toInt() and 0xFF
            // Skip language (3 bytes)
            var pos = offset + 4
            val frameEnd = offset + size
            
            // Skip content descriptor (null-terminated, encoding-aware).
            if (encoding == 1 || encoding == 2) {
                val maxDescriptorEnd = minOf(frameEnd, offset + 4 + 512)
                while (pos + 1 < maxDescriptorEnd) {
                    if (data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
                        pos += 2
                        break
                    }
                    pos += 2
                }
            } else {
                val maxDescriptorEnd = minOf(frameEnd, offset + 4 + 256)
                while (pos < maxDescriptorEnd && data[pos] != 0.toByte()) {
                    pos++
                }
                if (pos < frameEnd && data[pos] == 0.toByte()) {
                    pos++
                }
            }
            
            // Extract lyrics text
            if (pos >= frameEnd) return null
            if ((encoding == 1 || encoding == 2) && ((pos - offset) % 2 != 0) && pos + 1 < frameEnd) {
                pos++
            }
            
            val lyricsBytes = data.copyOfRange(pos, frameEnd)
            
            // Decode based on encoding
            val charset = when (encoding) {
                0 -> Charsets.ISO_8859_1
                1 -> Charsets.UTF_16  // UTF-16 with BOM
                2 -> Charsets.UTF_16BE
                3 -> Charsets.UTF_8
                else -> Charsets.UTF_8
            }
            
            val lyricsText = String(lyricsBytes, charset)
                .trim()
                .replace("\u0000", "")
            
            if (lyricsText.isBlank()) return null
            
            return parseLyricsData(lyricsText)
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse USLT frame: ${e.message}")
            return null
        }
    }
    
    /**
     * Helper to determine if text looks like lyrics
     */
    private fun looksLikeLyrics(text: String): Boolean {
        val trimmed = text.trim()
        
        // Reject if too short (likely just metadata)
        if (trimmed.length < 50) return false
        
        // Reject if it's just a single line (likely song title or metadata)
        val lines = trimmed.lines().filter { it.trim().isNotEmpty() }
        if (lines.size < 3) return false
        
        // Accept if has LRC timestamps
        val hasTimestamp = text.contains(Regex("\\[\\d{2}:\\d{2}"))
        if (hasTimestamp) return true
        
        // Reject common metadata patterns
        val lowerText = trimmed.lowercase()
        val metadataKeywords = listOf(
            "track", "album", "artist", "genre", "year", "composer",
            "copyright", "encoded", "encoder", "itunes", "id3"
        )
        val hasMetadataKeywords = metadataKeywords.any { lowerText.contains(it) }
        
        // Check for common lyrics structure markers
        val hasCommonLyricsWords = lowerText.let { 
            it.contains("verse") || it.contains("chorus") || it.contains("bridge") ||
            it.contains("refrain") || it.contains("intro") || it.contains("outro")
        }
        
        // Calculate how "lyrics-like" the text is
        val avgLineLength = lines.map { it.length }.average()
        val hasRepeatingPatterns = lines.distinct().size < lines.size * 0.8 // Some repetition expected
        val isProseLength = avgLineLength in 20.0..80.0 // Typical lyrics line length
        
        // Accept if it has multiple qualities of lyrics
        return (lines.size >= 5 && isProseLength && !hasMetadataKeywords) || 
               (hasCommonLyricsWords && lines.size >= 3) ||
               (hasRepeatingPatterns && lines.size >= 8 && avgLineLength < 100)
    }
    
    /**
     * Parses lyrics text into LyricsData with proper format detection and cleaning
     */
    private fun parseLyricsData(lyrics: String): LyricsData? {
        if (lyrics.isBlank()) {
            return null
        }
        
        // Log the first 200 characters to see what we're parsing
        Log.d(TAG, "Parsing lyrics data: ${lyrics.take(200)}${if (lyrics.length > 200) "..." else ""}")
        
        // Check for word-by-word JSON format before sanitizing
        val trimmedInput = lyrics.trim()
        val isWordByWordJson = (trimmedInput.startsWith("[") || trimmedInput.startsWith("{")) && 
            (trimmedInput.contains("\"timestamp\"") || trimmedInput.contains("\"words\""))
            
        if (isWordByWordJson) {
            try {
                val parsed = RhythmLyricsParser.parseWordByWordLyrics(lyrics)
                if (parsed.isNotEmpty()) {
                    val plainText = try {
                        RhythmLyricsParser.toPlainText(parsed)
                    } catch (e: Exception) {
                        null
                    }
                    val syncedLrc = try {
                        RhythmLyricsParser.toLRCFormat(parsed)
                    } catch (e: Exception) {
                        null
                    }
                    Log.d(TAG, "Successfully parsed embedded word-by-word JSON lyrics")
                    return LyricsData(plainText, syncedLrc, lyrics)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing embedded word-by-word JSON", e)
            }
        }
        
        val customTimedLyrics = parseCustomTimedLyrics(lyrics)
        if (customTimedLyrics != null) {
            Log.d(TAG, "Successfully parsed embedded custom word-timing lyrics")
            return customTimedLyrics
        }

        val cleanedLyrics = sanitizeLyricsText(lyrics)

        if (cleanedLyrics.isBlank()) {
            Log.w(TAG, "Rejected lyrics: text became empty after sanitization")
            return null
        }
        
        // Check if this looks like just a song title or metadata
        val lines = cleanedLyrics.lines().filter { it.trim().isNotEmpty() }
        if (lines.size == 1) {
            Log.w(TAG, "Rejected lyrics: single line detected (likely metadata): ${cleanedLyrics.take(100)}")
            return null
        }
        
        // Check if lyrics are synced (contain LRC-style timestamps)
        // Support multiple timestamp formats: [mm:ss.xx], [mm:ss.xxx], [mm:ss]
        val lrcPattern = Regex("\\[\\d{1,2}:\\d{2}(?:\\.\\d{2,3})?]")
        val isSynced = lrcPattern.containsMatchIn(cleanedLyrics)
        
        return if (isSynced) {
            // Synced lyrics - validate format
            val hasLyricsContent = cleanedLyrics.lines().any { line ->
                lrcPattern.replace(line, "").trim().isNotEmpty()
            }
            
            if (hasLyricsContent) {
                // Check for karaoke format with syllable-level timestamps [mm:ss.xxx]text[mm:ss.xxx]text
                // Karaoke format has multiple timestamps on the SAME line, each followed by a character/syllable
                // Check if any line has more than 2 timestamps (indicating karaoke format)
                val hasKaraokeTimestamps = cleanedLyrics.lines().any { line ->
                    val timestampPattern = Regex("\\[\\d{1,2}:\\d{2}\\.\\d{3}\\]")
                    val timestampCount = timestampPattern.findAll(line).count()
                    timestampCount > 2 // More than 2 timestamps on same line = karaoke
                }
                
                if (hasKaraokeTimestamps) {
                    Log.d(TAG, "Detected karaoke format with syllable-level timestamps")
                    
                    // Parse karaoke format and convert to word-by-word format
                    val enhancedLines = parseKaraokeLyrics(cleanedLyrics)
                    
                    if (enhancedLines.isNotEmpty()) {
                        // Convert to Rhythm word-by-word format (JSON)
                        val wordByWordJson = convertEnhancedLRCToWordByWord(enhancedLines)
                        
                        // Also extract plain text and line-synced LRC
                        val plainText = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                            line.words.joinToString("") { word: EnhancedWord -> word.text }
                        }
                        
                        val syncedLrc = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                            val timestamp = formatLRCTimestamp(line.lineTimestamp)
                            val text = line.words.joinToString("") { word: EnhancedWord -> word.text }
                            "[$timestamp]$text"
                        }
                        
                        Log.d(TAG, "Successfully converted karaoke lyrics to word-by-word format (${enhancedLines.size} lines)")
                        return LyricsData(plainText, syncedLrc, wordByWordJson)
                    }
                }
                
                // Check for Enhanced LRC format with word-level timestamps <mm:ss.xx>
                val hasWordTimestamps = LyricsParser.hasWordTimestamps(cleanedLyrics)
                
                if (hasWordTimestamps) {
                    Log.d(TAG, "Detected Enhanced LRC format with word-level timestamps")
                    
                    // Parse Enhanced LRC and convert to word-by-word format
                    val enhancedLines = LyricsParser.parseEnhancedLRC(cleanedLyrics)
                    
                    if (enhancedLines.isNotEmpty()) {
                        // Convert to Rhythm word-by-word format (JSON)
                        val wordByWordJson = convertEnhancedLRCToWordByWord(enhancedLines)
                        
                        // Also extract plain text and line-synced LRC
                        val plainText = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                            line.words.joinToString(" ") { word: EnhancedWord -> word.text }
                        }
                        
                        val syncedLrc = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                            val timestamp = formatLRCTimestamp(line.lineTimestamp)
                            val text = line.words.joinToString(" ") { word: EnhancedWord -> word.text }
                            "[$timestamp]$text"
                        }
                        
                        Log.d(TAG, "Successfully converted Enhanced LRC to word-by-word format (${enhancedLines.size} lines)")
                        return LyricsData(plainText, syncedLrc, wordByWordJson)
                    }
                }
                
                // Standard LRC format (line-by-line only) - normalize fragmented words
                val normalizedLyrics = normalizePlainLRC(cleanedLyrics)
                LyricsData(null, normalizedLyrics, null)
            } else {
                // Empty synced lyrics
                null
            }
        } else {
            // Plain text lyrics - validate it's not just metadata
            val meaningfulLines = cleanedLyrics.lines().filter { line ->
                val trimmed = line.trim()
                // Filter out common metadata markers
                trimmed.isNotEmpty() && 
                !trimmed.startsWith("//") &&
                !trimmed.startsWith("#") &&
                trimmed.length > 2 &&
                isLikelyLyricsLine(trimmed)
            }
            
            if (meaningfulLines.isNotEmpty()) {
                LyricsData(cleanedLyrics, null, null)
            } else {
                null
            }
        }
    }

    private fun parseCustomTimedLyrics(lyrics: String): LyricsData? {
        if (!lyrics.contains("|")) return null

        val tagRegex = Regex("<([^>]*?)>", RegexOption.DOT_MATCHES_ALL)
        val wordTimestamps = mutableListOf<Pair<Long, Long>>()

        for (tagMatch in tagRegex.findAll(lyrics)) {
            val content = tagMatch.groupValues[1]
            if (!content.contains(":")) continue
            val entries = content.split("|").map { it.trim().trimEnd('>').trim() }
            for (entry in entries) {
                if (entry.isBlank()) continue
                val lastColon = entry.lastIndexOf(':')
                if (lastColon < 0) continue
                val secondLastColon = entry.lastIndexOf(':', lastColon - 1)
                if (secondLastColon < 0) continue
                val endSecs = entry.substring(lastColon + 1).toDoubleOrNull() ?: continue
                val startSecs = entry.substring(secondLastColon + 1, lastColon).toDoubleOrNull() ?: continue
                wordTimestamps.add((startSecs * 1000).toLong() to (endSecs * 1000).toLong())
            }
        }
        if (wordTimestamps.isEmpty()) return null

        val lrcTimestampRegex = Regex("\\[\\d{1,2}:\\d{2}(?:\\.\\d{2,3})?]")
        val cleanText = lyrics.replace(tagRegex, "").replace(lrcTimestampRegex, "").trim()

        val textLines = cleanText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val enhancedLines = mutableListOf<EnhancedLyricLine>()
        var wordIdx = 0

        for (line in textLines) {
            val lineWords = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (lineWords.isEmpty()) continue

            val lineEnhancedWords = lineWords.mapIndexed { localIdx, wordText ->
                val globalIdx = wordIdx + localIdx
                if (globalIdx < wordTimestamps.size) {
                    val (startMs, endMs) = wordTimestamps[globalIdx]
                    EnhancedWord(text = wordText, timestamp = startMs, endtime = endMs)
                } else {
                    val prevWord = enhancedLines.lastOrNull()?.words?.lastOrNull()
                    val fallbackMs = prevWord?.endtime ?: 0L
                    EnhancedWord(text = wordText, timestamp = fallbackMs, endtime = fallbackMs)
                }
            }
            wordIdx += lineWords.size

            val lineStart = lineEnhancedWords.firstOrNull()?.timestamp ?: 0L
            val lineEnd = lineEnhancedWords.lastOrNull()?.endtime ?: lineStart
            enhancedLines.add(EnhancedLyricLine(lineEnhancedWords, lineStart, lineEnd))
        }

        if (enhancedLines.isEmpty()) return null

        val wordByWordJson = convertEnhancedLRCToWordByWord(enhancedLines)
        val plainText = enhancedLines.joinToString("\n") { line ->
            line.words.joinToString(" ") { it.text }
        }

        Log.d(TAG, "Parsed ${wordTimestamps.size} word timestamps across ${enhancedLines.size} lines")
        return LyricsData(plainText, syncedLyrics = null, wordByWordJson, source = "Embedded")
    }

    private fun sanitizeLyricsText(input: String): String {
        val normalized = input
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val cleanedLines = normalized.lines().mapNotNull { rawLine ->
            val cleaned = rawLine
                .filter { ch ->
                    when {
                        ch == '\t' -> true
                        ch == '\uFFFD' -> false
                        Character.isISOControl(ch) -> false
                        else -> true
                    }
                }
                .trimEnd()

            if (cleaned.isBlank()) {
                null
            } else {
                cleaned
            }
        }.filter { line ->
            isLikelyLyricsLine(line)
        }

        return cleanedLines
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun isLikelyLyricsLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false

        // Keep standard LRC metadata/timestamp lines that are part of synced lyrics.
        if (trimmed.matches(Regex("\\[[a-zA-Z]{1,10}:[^\\]]*]"))) return true
        if (trimmed.matches(Regex("(\\[\\d{1,2}:\\d{2}(?:\\.\\d{2,3})?])+.*"))) return true

        val body = trimmed
            .replace(Regex("\\[[^\\]]*]"), "")
            .replace(Regex("<[^>]*>"), "")
            .trim()

        if (body.isEmpty()) return true
        if (body.length < 20) return true

        val readableChars = body.count { ch ->
            ch.isLetterOrDigit() ||
                ch.isWhitespace() ||
                isLyricsPunctuation(ch)
        }

        val ratio = readableChars.toDouble() / body.length.toDouble()
        return ratio >= 0.55
    }

    private fun isLyricsPunctuation(ch: Char): Boolean {
        return when (Character.getType(ch)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            Character.MATH_SYMBOL.toInt(),
            Character.CURRENCY_SYMBOL.toInt(),
            Character.MODIFIER_SYMBOL.toInt(),
            Character.OTHER_SYMBOL.toInt() -> true
            else -> false
        }
    }
    
    /**
    * Convert Enhanced LRC format to Rhythm word-by-word JSON format
     */
    private fun convertEnhancedLRCToWordByWord(enhancedLines: List<EnhancedLyricLine>): String {
        val rhythmWordLines = enhancedLines.map { line: EnhancedLyricLine ->
            val words = line.words.map { word: EnhancedWord ->
                mapOf(
                    "text" to word.text,
                    "part" to word.isPart,
                    "timestamp" to word.timestamp,
                    "endtime" to word.endtime
                )
            }
            
            val lineMap = mutableMapOf<String, Any>(
                "text" to words,
                "background" to false,
                "timestamp" to line.lineTimestamp,
                "endtime" to line.lineEndtime
            )
            
            val backgroundText = mutableListOf<String>()
            line.translation?.let { backgroundText.add(it) }
            line.romanization?.let { backgroundText.add(it) }
            if (backgroundText.isNotEmpty()) {
                lineMap["backgroundText"] = backgroundText
            }
            
            lineMap
        }
        
        return com.google.gson.Gson().toJson(rhythmWordLines)
    }
    
    private fun convertSemanticLyricsToWordByWord(syncedLyrics: SemanticLyrics.SyncedLyrics): String? {
        return LrcUtils.convertSemanticLyricsToWordByWord(syncedLyrics)
    }
    
    /**
     * Parse karaoke lyrics format with syllable-level timestamps [mm:ss.xxx]text[mm:ss.xxx]text
     */
    private fun parseKaraokeLyrics(lyrics: String): List<EnhancedLyricLine> {
        val enhancedLines = mutableListOf<EnhancedLyricLine>()
        val lines = lyrics.trim().split("\n", "\r\n", "\r")
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            
            // Pattern to match [mm:ss.xxx]text sequences
            val karaokePattern = Regex("\\[(\\d{1,2}):(\\d{2})\\.(\\d{3})\\]([^\\[]*)")
            val matches = karaokePattern.findAll(trimmedLine)
            
            val words = mutableListOf<EnhancedWord>()
            var lineStartTime = Long.MAX_VALUE
            var lineEndTime = Long.MIN_VALUE
            
            for (match in matches) {
                try {
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toLong()
                    val milliseconds = match.groupValues[3].toLong()
                    val text = match.groupValues[4]
                    
                    val timestamp = (minutes * 60 * 1000) + (seconds * 1000) + milliseconds
                    
                    if (text.isNotBlank()) {
                        // For karaoke format, each syllable gets a minimal duration
                        // We'll estimate end time as the start of the next syllable or add a small duration
                        val endtime = timestamp + 100 // 100ms default duration per syllable
                        
                        words.add(EnhancedWord(
                            text = text,
                            timestamp = timestamp,
                            endtime = endtime
                        ))
                        
                        lineStartTime = minOf(lineStartTime, timestamp)
                        lineEndTime = maxOf(lineEndTime, endtime)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing karaoke timestamp: ${match.value}", e)
                }
            }
            
            // Adjust end times based on next syllable's start time
            val adjustedWords = words.mapIndexed { index, word ->
                if (index < words.size - 1) {
                    // Set end time to the start of the next word
                    word.copy(endtime = words[index + 1].timestamp)
                } else {
                    // Last word: if it still has the default duration, extend it
                    if (word.endtime == word.timestamp + 100) {
                        word.copy(endtime = word.timestamp + 500) // 500ms for last syllable
                    } else {
                        word
                    }
                }
            }
            
            // Update lineEndTime based on adjusted words
            if (adjustedWords.isNotEmpty()) {
                lineEndTime = adjustedWords.last().endtime
            }
            
            if (adjustedWords.isNotEmpty()) {
                // Use the first timestamp as line timestamp, or estimate if not available
                val actualLineStart = if (lineStartTime != Long.MAX_VALUE) lineStartTime else adjustedWords.first().timestamp
                val actualLineEnd = if (lineEndTime != Long.MIN_VALUE) lineEndTime else adjustedWords.last().endtime
                
                enhancedLines.add(EnhancedLyricLine(
                    words = adjustedWords,
                    lineTimestamp = actualLineStart,
                    lineEndtime = actualLineEnd
                ))
            }
        }
        
        return enhancedLines.sortedBy { it.lineTimestamp }
    }
    
    /**
     * Format timestamp to LRC format [mm:ss.xx]
     */
    /**
     * Normalize plain LRC text by fixing fragmented words (e.g., "some thing" -> "something")
     * This handles LRC files where words have been split with spaces.
     */
    private fun normalizePlainLRC(lrcContent: String): String {
        val lrcRegex = Regex("""^\[(\d{1,2}):(\d{2}(?:\.\d{2,3})?)\](.*)$""", RegexOption.MULTILINE)
        val wordTimestampRegex = Regex("<\\d{1,3}:\\d{2}(?:\\.\\d{2,3})?>")
        return lrcContent.lines().map { line ->
            val matchResult = lrcRegex.matchEntire(line.trim())
            if (matchResult != null) {
                val timestamp = matchResult.groupValues[0].substring(0, matchResult.groupValues[0].indexOf(']') + 1)
                val text = matchResult.groupValues[3].replace(wordTimestampRegex, "")
                val normalizedText = LyricsParser.normalizeWordFlowText(text)
                "$timestamp$normalizedText"
            } else {
                line
            }
        }.joinToString("\n")
    }

    private fun formatLRCTimestamp(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = (milliseconds % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, millis)
    }
    
    // TODO: Implement export functionality for Enhanced LRC format
    /**
     * Export word-by-word lyrics to Enhanced LRC format
     * @param lyricsData Lyrics data containing word-by-word JSON
     * @return Enhanced LRC formatted string, or null if not available
     */
    fun exportToEnhancedLRC(lyricsData: LyricsData): String? {
        val wordByWordJson = lyricsData.wordByWordLyrics ?: return null
        
        try {
            // Parse the word-by-word JSON directly
            val gson = com.google.gson.Gson()
            val listType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            val parsedLines: List<Map<String, Any>> = gson.fromJson(wordByWordJson, listType)
            
            if (parsedLines.isEmpty()) return null
            
            // Convert to Enhanced LRC format
            val enhancedLines = parsedLines.mapNotNull { lineMap ->
                @Suppress("UNCHECKED_CAST")
                val wordsData = lineMap["text"] as? List<Map<String, Any>> ?: return@mapNotNull null
                val lineTimestamp = (lineMap["timestamp"] as? Number)?.toLong() ?: 0L
                val lineEndtime = (lineMap["endtime"] as? Number)?.toLong() ?: 0L
                
                val words = wordsData.map { wordMap ->
                    EnhancedWord(
                        text = wordMap["text"] as? String ?: "",
                        timestamp = (wordMap["timestamp"] as? Number)?.toLong() ?: 0L,
                        endtime = (wordMap["endtime"] as? Number)?.toLong() ?: 0L
                    )
                }
                
                EnhancedLyricLine(
                    words = words,
                    lineTimestamp = lineTimestamp,
                    lineEndtime = lineEndtime
                )
            }
            
            return LyricsParser.toEnhancedLRC(enhancedLines)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to Enhanced LRC: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Helper to get file path from content URI
     */
    private fun getFilePathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Audio.Media.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)
                        if (columnIndex >= 0) {
                            return cursor.getString(columnIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get file path from URI: ${e.message}")
            }
        }
        
        return null
    }

    /**
     * Fetches lyrics for a song using various sources based on user preference.
     * @param artist Song artist
     * @param title Song title
     * @param songId Optional song ID for cache key - prevents wrong lyrics for songs with similar names
     * @param songUri Optional song URI for embedded lyrics extraction
     * @param sourcePreference User's preferred lyrics source order
     * @param forceRefresh If true, bypasses cache and forces fresh extraction
     * @param requireRomanization If true, keep searching sources until timed romanization is found
     * @param requireTranslation If true, keep searching sources until timed translation is found
     */
    suspend fun fetchLyrics(
        artist: String, 
        title: String, 
        songId: String? = null,
        songUri: Uri? = null,
        sourcePreference: LyricsSourcePreference = LyricsSourcePreference.API_FIRST,
        forceRefresh: Boolean = false,
        requireRomanization: Boolean = false,
        requireTranslation: Boolean = false
    ): LyricsData? {
        val appSettings = AppSettings.getInstance(context)
        val songSpecificPreference = songId?.let(appSettings::getSongLyricsPreference)
        val effectiveSourcePreference = when (songSpecificPreference) {
            "online" -> LyricsSourcePreference.API_FIRST
            "embedded" -> LyricsSourcePreference.EMBEDDED_FIRST
            "lrc" -> LyricsSourcePreference.LOCAL_FIRST
            else -> sourcePreference
        }
        val effectiveSongUri = songUri
            ?.takeIf { it.toString().isNotBlank() }
            ?: songId?.toLongOrNull()?.let { id ->
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            }
        val requestKey = listOf(
            songId.orEmpty(),
            artist.trim().lowercase(Locale.ROOT),
            title.trim().lowercase(Locale.ROOT),
            effectiveSourcePreference.name,
            forceRefresh.toString(),
            requireRomanization.toString(),
            requireTranslation.toString()
        ).joinToString("\u0000")

        return lyricsRequests.run(requestKey) {
            fetchLyricsUncoalesced(
                artist = artist,
                title = title,
                songId = songId,
                songUri = effectiveSongUri,
                sourcePreference = effectiveSourcePreference,
                forceRefresh = forceRefresh,
                requireRomanization = requireRomanization,
                requireTranslation = requireTranslation
            )
        }
    }

    private suspend fun fetchLyricsUncoalesced(
        artist: String,
        title: String,
        songId: String?,
        songUri: Uri?,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean,
        requireRomanization: Boolean,
        requireTranslation: Boolean
    ): LyricsData? = withContext(Dispatchers.IO) {
        Log.d(
            TAG,
            "===== FETCH LYRICS START: $artist - $title (songId=$songId, forceRefresh=$forceRefresh, requireRomanization=$requireRomanization, requireTranslation=$requireTranslation, source=$sourcePreference) ====="
        )
        
        if (artist.isBlank() || title.isBlank())
            return@withContext LyricsData("No lyrics available for this song", null, null)

        // Use song ID in cache key if available to prevent wrong lyrics for songs with similar metadata
        val lyricsIdentityKey = if (songId != null) {
            "$songId:$artist:$title".lowercase()
        } else {
            "$artist:$title".lowercase()
        }
        // Keep results from different source policies isolated.
        val baseCacheKey = "$lyricsIdentityKey:source:${sourcePreference.name}"
        val requirementsSuffix = buildString {
            if (requireRomanization) append(":romanized")
            if (requireTranslation) append(":translated")
        }
        val cacheKey = "$baseCacheKey$requirementsSuffix"
        
        Log.d(TAG, "===== Cache key: $cacheKey, Cache size: ${lyricsCache.size} =====")
        
        // Check cache unless force refresh is requested
        if (!forceRefresh) {
            lyricsCache[cacheKey]?.let { cached ->
                val lyricLength = cached.plainLyrics?.length ?: cached.syncedLyrics?.length ?: 0
                Log.d(TAG, "===== RETURNING IN-MEMORY CACHED LYRICS ($lyricLength chars) =====")
                return@withContext cached
            }
            Log.d(TAG, "===== NO IN-MEMORY CACHE HIT, proceeding to fetch =====")
        } else {
            Log.d(TAG, "===== FORCE REFRESH - BYPASSING IN-MEMORY CACHE =====")
        }

        var localFetched = false
        var localLyrics: LyricsData? = null
        suspend fun fetchFromLocal(): LyricsData? {
            if (!localFetched) {
                localFetched = true
                localLyrics = findLocalLyrics(artist, title, songId)
            }
            return localLyrics
        }

        var embeddedFetched = false
        var embeddedLyrics: LyricsData? = null
        suspend fun fetchFromEmbedded(): LyricsData? {
            if (embeddedFetched) return embeddedLyrics
            embeddedFetched = true

            // Try to get embedded lyrics from the provided songUri first
            embeddedLyrics = songUri?.let { uri -> getEmbeddedLyrics(uri) }
            
            // If no embedded lyrics found and songUri is remote/streaming, 
            // try to find a local file matching this song by artist/title
            if (embeddedLyrics == null && songUri != null) {
                val isRemoteUri = songUri.scheme?.let { scheme ->
                    scheme != "file" && scheme != "content"
                } ?: false
                
                if (isRemoteUri) {
                    Log.d(TAG, "Songuri is remote ($songUri), attempting to find local file for embedded lyrics extraction")
                    embeddedLyrics = tryGetEmbeddedLyricsFromLocalFile(artist, title)
                }
            }

            return embeddedLyrics
        }

        val apiLyricsByRequirements = mutableMapOf<Pair<Boolean, Boolean>, LyricsData?>()
        val fetchedApiRequirements = mutableSetOf<Pair<Boolean, Boolean>>()
        suspend fun fetchFromAPI(
            wantRomanization: Boolean,
            wantTranslation: Boolean
        ): LyricsData? {
            val requirements = wantRomanization to wantTranslation
            if (requirements in fetchedApiRequirements) {
                return apiLyricsByRequirements[requirements]
            }
            fetchedApiRequirements += requirements
            if (isNetworkAvailable()) {
                val result = withTimeoutOrNull(LYRICS_LOOKUP_TIMEOUT_MS) {
                    fetchLyricsFromAPIs(
                        artist = artist,
                        title = title,
                        requireRomanization = wantRomanization,
                        requireTranslation = wantTranslation
                    )
                }
                if (result == null) {
                    Log.d(
                        TAG,
                        "Online lyrics lookup (romanization=$wantRomanization, translation=$wantTranslation) returned no result within ${LYRICS_LOOKUP_TIMEOUT_MS}ms"
                    )
                }
                apiLyricsByRequirements[requirements] = result
            } else {
                apiLyricsByRequirements[requirements] = null
            }
            return apiLyricsByRequirements[requirements]
        }

        suspend fun fetch(
            origin: LyricsOrigin,
            wantRomanization: Boolean = false,
            wantTranslation: Boolean = false
        ): LyricsData? =
            when (origin) {
                LyricsOrigin.LOCAL -> fetchFromLocal()
                LyricsOrigin.EMBEDDED -> fetchFromEmbedded()
                LyricsOrigin.API -> fetchFromAPI(wantRomanization, wantTranslation)
            }?.let { lyrics ->
                if (lyrics.source.isNullOrBlank()) lyrics.copy(source = origin.label) else lyrics
            }

        val baseOrder = when (sourcePreference) {
            LyricsSourcePreference.API_FIRST -> listOf(
                LyricsOrigin.API,
                LyricsOrigin.EMBEDDED,
                LyricsOrigin.LOCAL
            )

            LyricsSourcePreference.EMBEDDED_FIRST -> listOf(
                LyricsOrigin.EMBEDDED,
                LyricsOrigin.API,
                LyricsOrigin.LOCAL
            )

            LyricsSourcePreference.LOCAL_FIRST -> listOf(
                LyricsOrigin.LOCAL,
                LyricsOrigin.EMBEDDED,
                LyricsOrigin.API
            )
        }

        var baseLyrics =
            if ((requireRomanization || requireTranslation) && !forceRefresh) {
                lyricsCache[baseCacheKey]
            } else {
                null
            }
        if (baseLyrics == null) {
            for (origin in baseOrder) {
                val candidate = try {
                    fetch(origin, wantRomanization = false)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Error fetching base lyrics from ${origin.label}: ${e.message}")
                    null
                }
                if (candidate?.hasLyrics() == true) {
                    baseLyrics = candidate
                    Log.d(
                        TAG,
                        "Selected base lyrics from ${candidate.source} for: $artist - $title"
                    )
                    lyricsCache[baseCacheKey] = candidate
                    if (origin == LyricsOrigin.API) saveLocalLyrics(artist, title, candidate)
                    break
                }
            }
        }

        if (!requireRomanization && !requireTranslation && baseLyrics != null) {
            return@withContext baseLyrics
        }

        var enrichedLyrics = baseLyrics

        // Reuse persisted online enrichment after a process restart.
        if (!forceRefresh) {
            val cachedRomanized = findLocalRomanizedLyrics(artist, title)
            if (cachedRomanized?.hasUsableTimedRomanization() == true) {
                enrichedLyrics = if (enrichedLyrics == null) {
                    cachedRomanized
                } else {
                    LyricsEnrichmentMerger.merge(enrichedLyrics, cachedRomanized)
                }
                val reusableLyrics = enrichedLyrics
                if (
                    reusableLyrics.meetsLyricsRequirements(
                        requireRomanization,
                        requireTranslation
                    ) == true &&
                    (!requireRomanization || reusableLyrics.hasCjkOriginalCoverage())
                ) {
                    lyricsCache[cacheKey] = reusableLyrics
                    Log.d(
                        TAG,
                        "===== Reusing persisted lyric enrichment for: $artist - $title (source=${reusableLyrics.source}) ====="
                    )
                    return@withContext reusableLyrics
                }
            }
        }

        // Supplement the selected base source from local data before querying providers.
        val enrichmentOrder = listOf(LyricsOrigin.LOCAL, LyricsOrigin.EMBEDDED, LyricsOrigin.API)
        for (origin in enrichmentOrder) {
            try {
                val candidate = fetch(
                    origin = origin,
                    wantRomanization = origin == LyricsOrigin.API && requireRomanization,
                    wantTranslation = origin == LyricsOrigin.API && requireTranslation
                ) ?: continue
                enrichedLyrics = when {
                    enrichedLyrics == null || candidate == enrichedLyrics -> candidate
                    else -> LyricsEnrichmentMerger.merge(enrichedLyrics, candidate)
                }
                val currentEnriched = enrichedLyrics

                val requirementsMet = currentEnriched.meetsLyricsRequirements(
                        requireRomanization,
                        requireTranslation
                    )
                val shouldProbeApiForCjk =
                    origin != LyricsOrigin.API &&
                        requireRomanization &&
                        currentEnriched.hasUsableTimedRomanization() &&
                        !currentEnriched.hasCjkOriginalCoverage()
                if (requirementsMet && !shouldProbeApiForCjk) {
                    lyricsCache[cacheKey] = currentEnriched
                    if (origin == LyricsOrigin.API && requireRomanization) {
                        saveLocalLyrics(
                            artist,
                            title,
                            currentEnriched,
                            variant = ".romanized"
                        )
                    }
                    Log.d(
                        TAG,
                        "Selected lyric enrichment from ${candidate.source ?: origin.label} for: $artist - $title"
                    )
                    return@withContext currentEnriched
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Error fetching lyric enrichment from ${origin.label}: ${e.message}")
            }
        }

        if (enrichedLyrics != null) {
            // Required-track cache entries must satisfy all requested tracks.
            Log.d(
                TAG,
                "Requested lyric enrichment is incomplete; returning best lyrics for: $artist - $title"
            )
            return@withContext enrichedLyrics
        }

        // No lyrics found from any source
        Log.d(TAG, "No lyrics found from any source for: $artist - $title")
        return@withContext LyricsData("No lyrics found for this song", null, null)
    }

    private fun canonicalizeForMatch(str: String): String {
        val normalized = java.text.Normalizer.normalize(str.lowercase(), java.text.Normalizer.Form.NFD)
        val withoutAccents = Regex("\\p{InCombiningDiacriticalMarks}+").replace(normalized, "")
        return withoutAccents
            .replace(Regex("\\(.*?\\)"), "") // remove parenthetical content
            .replace(Regex("\\[.*?\\]"), "") // remove brackets content
            .replace(Regex("\\b(feat|ft|featuring|and|with|&|vs|prod|by)\\b"), "") // remove collaboration keywords
            .filter { it.isLetterOrDigit() }
    }
    
    /**
     * Fetches lyrics from online APIs (LRCLib, etc.)
     * Extracted as a separate method for cleaner code
     */
    private suspend fun fetchLyricsFromAPIs(
        artist: String,
        title: String,
        requireRomanization: Boolean = false,
        requireTranslation: Boolean = false
    ): LyricsData? {
        val cleanArtist = artist.trim().replace(Regex("\\(.*?\\)"), "").trim()
        val cleanTitle = title.trim().replace(Regex("\\(.*?\\)"), "").trim()

        var lyricallyBackup: LyricsData? = null
        var lrclibPlainBackup: LyricsData? = null

        val appSettings = AppSettings.getInstance(context)

        val fetchLyrically: suspend () -> LyricsData? = fetchLyrically@ {
            if (NetworkClient.isLyricallyApiEnabled() && rhythmLyricsApiService != null) {
                try {
                    val activeSources = appSettings.lyricallySourcesOrder.value.filter { it !in appSettings.disabledLyricallySources.value }
                    Log.d(TAG, "Lyrically API: active sources: $activeSources")

                    suspend fun fetchActiveSources(
                        sources: List<String>,
                        requestTranslation: Boolean
                    ): List<Pair<String, LyricsData>> = coroutineScope {
                        sources.map { source ->
                            async {
                                val result = withTimeoutOrNull(7_500L) {
                                    fetchSourceLyrics(
                                        source = source,
                                        artist = artist,
                                        title = title,
                                        cleanArtist = cleanArtist,
                                        cleanTitle = cleanTitle,
                                        preferWordByWord =
                                            source == "APPLE_MUSIC" ||
                                                source == "NETEASE" ||
                                                source == "KUGOU",
                                        requestTranslation = requestTranslation
                                    )
                                }
                                if (result == null) null else source to result
                            }
                        }.awaitAll().filterNotNull()
                    }

                    val requiresSupplement = requireRomanization || requireTranslation
                    if (requiresSupplement) {
                        // Apple and NetEase expose supplemental timed tracks in provider metadata.
                        val sourcesToTry = activeSources.sortedBy { source ->
                            when (source) {
                                "APPLE_MUSIC" -> 0
                                "NETEASE" -> 1
                                "MUSIXMATCH" -> 2
                                else -> 3
                            }
                        }
                        var accumulated: LyricsData? = null
                        for ((source, result) in fetchActiveSources(
                            sourcesToTry,
                            requestTranslation = requireTranslation
                        )) {
                            accumulated = if (accumulated == null) {
                                result
                            } else {
                                LyricsEnrichmentMerger.merge(accumulated, result)
                            }
                            if (
                                accumulated.meetsLyricsRequirements(
                                    requireRomanization,
                                    requireTranslation
                                )
                            ) {
                                Log.d(
                                    TAG,
                                    "Lyrically API: requirements satisfied after $source"
                                )
                                return@fetchLyrically accumulated
                            }
                        }
                        return@fetchLyrically accumulated
                    }

                    val appleCandidate = if ("APPLE_MUSIC" in activeSources) {
                        withTimeoutOrNull(3_500L) {
                            fetchSourceLyrics(
                                source = "APPLE_MUSIC",
                                artist = artist,
                                title = title,
                                cleanArtist = cleanArtist,
                                cleanTitle = cleanTitle,
                                preferWordByWord = true
                            )
                        }
                    } else {
                        null
                    }
                    if (!appleCandidate?.wordByWordLyrics.isNullOrBlank()) {
                        Log.d(TAG, "Lyrically API: selected fast Apple Music word-by-word lyrics")
                        return@fetchLyrically appleCandidate
                    }

                    val candidates = buildList {
                        if (appleCandidate != null) add("APPLE_MUSIC" to appleCandidate)
                        addAll(
                            fetchActiveSources(
                                activeSources.filter { it != "APPLE_MUSIC" },
                                requestTranslation = false
                            )
                        )
                    }
                    val selected = candidates.firstOrNull { (_, lyrics) ->
                        !lyrics.wordByWordLyrics.isNullOrBlank()
                    } ?: candidates.firstOrNull { (_, lyrics) -> lyrics.hasSyncedLyrics() }
                        ?: candidates.firstOrNull()
                    selected?.also { (source, _) ->
                        Log.d(TAG, "Lyrically API: selected base lyrics from $source")
                    }?.second
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Lyrically API fetch failed: ${e.message}", e)
                    null
                }
            } else null
        }

        val fetchLrcLib = suspend {
            if (NetworkClient.isLrcLibApiEnabled() && lrclibApiService != null) {
                try {
                    // Strategy 1: Search by track name and artist name
                    var results = lrclibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)

                    // If no results, try fallback strategies
                    if (results.isEmpty()) {
                        // Strategy 2: Search with generic query combining artist and title
                        val query = "$cleanArtist $cleanTitle"
                        results = lrclibApiService.searchLyrics(query = query)
                    }

                    if (results.isEmpty()) {
                        // Strategy 3: Try without parenthetical content and with simplified names
                        val simplifiedArtist = cleanArtist.split(" feat.", " ft.", " featuring").first().trim()
                        val simplifiedTitle = cleanTitle.split(" feat.", " ft.", " featuring").first().trim()
                        results = lrclibApiService.searchLyrics(
                            trackName = simplifiedTitle,
                            artistName = simplifiedArtist
                        )
                    }

                    // Find the best match - prioritize exact matches, then synced lyrics, then any lyrics
                    val bestMatch = results.firstOrNull { result ->
                        val resultArtist = canonicalizeForMatch(result.artistName.orEmpty())
                        val resultTitle = canonicalizeForMatch(result.trackName.orEmpty())
                        val targetArtist = canonicalizeForMatch(cleanArtist)
                        val targetTitle = canonicalizeForMatch(cleanTitle)
                        val artistMatch =
                            resultArtist.isNotBlank() &&
                                targetArtist.isNotBlank() &&
                                (resultArtist.contains(targetArtist) ||
                                    targetArtist.contains(resultArtist))
                        val titleMatch =
                            resultTitle.isNotBlank() &&
                                targetTitle.isNotBlank() &&
                                (resultTitle.contains(targetTitle) ||
                                    targetTitle.contains(resultTitle))

                        (artistMatch && titleMatch) && result.hasLyrics()
                    }

                    bestMatch?.let { bm ->
                        val syncedLyrics = bm.getSyncedLyricsOrNull()
                        val plainLyrics = bm.getPlainLyricsOrNull()

                        if (syncedLyrics != null) {
                            Log.d(TAG, "LRCLib: Synced lyrics found, returning immediately")
                            LyricsData(plainLyrics, syncedLyrics, null, "LRCLib")
                        } else if (plainLyrics != null) {
                            Log.d(TAG, "LRCLib: Plain lyrics found, caching as fallback")
                            lrclibPlainBackup = LyricsData(plainLyrics, null, null, "LRCLib")
                            null
                        } else null
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "LRCLib lyrics fetch failed: ${e.message}", e)
                    null
                }
            } else null
        }

        val apiPriority = appSettings.lyricsApiPriority.value
        val fallbackRetry = appSettings.lyricsApiFallbackRetry.value

        Log.d(TAG, "fetchLyricsFromAPIs: priority=$apiPriority, fallbackRetry=$fallbackRetry")

        // LRCLib cannot satisfy supplemental-track requests by itself.
        if (requireRomanization || requireTranslation) {
            return fetchLyrically()
        }

        if (apiPriority == LyricsApiPriority.LYRICALLY_FIRST) {
            val lyricallyResult = fetchLyrically()
            if (lyricallyResult != null) return lyricallyResult

            if (fallbackRetry) {
                val lrcResult = fetchLrcLib()
                if (lrcResult != null) return lrcResult
            }
        } else {
            val lrcResult = fetchLrcLib()
            if (lrcResult != null) return lrcResult

            if (fallbackRetry) {
                val lyricallyResult = fetchLyrically()
                if (lyricallyResult != null) return lyricallyResult
            }
        }

        if (lrclibPlainBackup != null) {
            Log.d(TAG, "Fallback Chain: Returning cached LRCLib plain lyrics")
            return lrclibPlainBackup
        }
        
        if (lyricallyBackup != null) {
            Log.d(TAG, "Fallback Chain: Returning cached Lyrically line-synced or plain lyrics")
            return lyricallyBackup
        }

        // No lyrics found from APIs
        return null
    }

    private suspend fun fetchSourceLyrics(
        source: String,
        artist: String,
        title: String,
        cleanArtist: String,
        cleanTitle: String,
        preferWordByWord: Boolean,
        requestTranslation: Boolean = false
    ): LyricsData? {
        val apiService = NetworkClient.rhythmLyricsApiService ?: return null
        val term = "$cleanArtist $cleanTitle"
        val translationLanguage =
            AppSettings.getInstance(context).lyricsTranslationLanguage.value

        return when (source) {
            "APPLE_MUSIC" -> {
                val searchService = NetworkClient.itunesSearchApiService ?: return null
                val terms = listOf(
                    term,
                    "$cleanArtist $cleanTitle",
                    "$artist $title"
                ).distinct()

                val allResults = mutableListOf<chromahub.rhythm.app.network.ITunesTrackResult>()
                for (t in terms) {
                    try {
                        val resp = searchService.searchSongs(term = t, limit = 10)
                        allResults.addAll(resp.results)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "iTunes search failed for term $t", e)
                    }
                }

                val bestTrack = allResults.firstOrNull { result ->
                    val resultTitleCanon = canonicalizeForMatch(result.trackName ?: "")
                    val resultArtistCanon = canonicalizeForMatch(result.artistName ?: "")
                    val targetTitleCanon = canonicalizeForMatch(cleanTitle)
                    val targetArtistCanon = canonicalizeForMatch(cleanArtist)
                    (resultTitleCanon.contains(targetTitleCanon) || targetTitleCanon.contains(resultTitleCanon)) &&
                    (resultArtistCanon.contains(targetArtistCanon) || targetArtistCanon.contains(resultArtistCanon))
                } ?: allResults.firstOrNull { result ->
                    val resultTitleCanon = canonicalizeForMatch(result.trackName ?: "")
                    val targetTitleCanon = canonicalizeForMatch(cleanTitle)
                    resultTitleCanon.contains(targetTitleCanon) || targetTitleCanon.contains(resultTitleCanon)
                }

                bestTrack?.let { track ->
                    try {
                        val response = apiService.getAppleMusicLyrics(track.trackId.toString())
                        LyricallyApiParser.parseLyricsResponse(
                            response,
                            "Lyrically (Apple Music)",
                            translationLanguage
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.d(TAG, "Apple Music lyrics fetch failed: ${e.message}")
                        null
                    }
                }
            }
            "SPOTIFY" -> {
                try {
                    val results = LyricallyApiParser.parseGenericSearch(
                        apiService.searchSpotify(term)
                    )
                    val bestTrack = matchGenericResult(results, cleanTitle, cleanArtist)
                    bestTrack?.let { track ->
                        (track.trackId ?: track.id)?.let { id ->
                            val response = apiService.getSpotifyLyrics(id)
                            LyricallyApiParser.parseLyricsResponse(
                                response,
                                "Lyrically (Spotify)",
                                translationLanguage
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.d(TAG, "Spotify lyrics search/fetch failed: ${e.message}")
                    null
                }
            }
            "NETEASE" -> {
                try {
                    val results = LyricallyApiParser.parseNeteaseSearch(
                        apiService.searchNetease(term)
                    )
                    val bestTrack = matchNeteaseResult(results, cleanTitle, cleanArtist)
                        ?: findNeteaseTrackWithITunesTitle(
                            apiService = apiService,
                            artist = artist,
                            title = title,
                            cleanArtist = cleanArtist,
                            cleanTitle = cleanTitle
                        )
                    bestTrack?.let { track ->
                        val response = apiService.getNeteaseLyrics(track.id.toString(), preferWordByWord)
                        LyricallyApiParser.parseLyricsResponse(
                            response,
                            "Lyrically (NetEase)",
                            translationLanguage
                        )
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.d(TAG, "NetEase lyrics search/fetch failed: ${e.message}")
                    null
                }
            }
            "QQ_MUSIC" -> {
                try {
                    val results = LyricallyApiParser.parseGenericSearch(
                        apiService.searchQQ(term)
                    )
                    val bestTrack = matchGenericResult(results, cleanTitle, cleanArtist)
                    bestTrack?.let { track ->
                        (track.songmid ?: track.trackId ?: track.id)?.let { id ->
                            val response = apiService.getQQLyrics(id)
                            LyricallyApiParser.parseLyricsResponse(
                                response,
                                "Lyrically (QQ Music)",
                                translationLanguage
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.d(TAG, "QQ Music lyrics search/fetch failed: ${e.message}")
                    null
                }
            }
            "KUGOU" -> {
                try {
                    val results = LyricallyApiParser.parseGenericSearch(
                        apiService.searchKugou(term)
                    )
                    val bestTrack = matchGenericResult(results, cleanTitle, cleanArtist)
                    bestTrack?.let { track ->
                        (track.hash ?: track.trackId ?: track.id)?.let { id ->
                            val response = apiService.getKugouLyrics(id, preferWordByWord)
                            LyricallyApiParser.parseLyricsResponse(
                                response,
                                "Lyrically (Kugou)",
                                translationLanguage
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.d(TAG, "Kugou lyrics search/fetch failed: ${e.message}")
                    null
                }
            }
            "YOUTUBE" -> {
                try {
                    val results = LyricallyApiParser.parseGenericSearch(
                        apiService.searchYouTube(term)
                    )
                    val bestTrack = matchGenericResult(results, cleanTitle, cleanArtist)
                    bestTrack?.let { track ->
                        (track.videoId ?: track.trackId ?: track.id)?.let { id ->
                            val response = apiService.getYouTubeLyrics(id)
                            LyricallyApiParser.parseLyricsResponse(
                                response,
                                "Lyrically (YouTube)",
                                translationLanguage
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.d(TAG, "YouTube lyrics search/fetch failed: ${e.message}")
                    null
                }
            }
            "DEEZER" -> {
                try {
                    if (deezerApiService != null) {
                        val searchResponse = deezerApiService.searchTracks(term)
                        val bestTrack = searchResponse.data.firstOrNull { result ->
                            val resTitle = canonicalizeForMatch(result.title)
                            val resArtist = canonicalizeForMatch(result.artist?.name ?: "")
                            val targetTitle = canonicalizeForMatch(cleanTitle)
                            val targetArtist = canonicalizeForMatch(cleanArtist)
                            (resTitle.contains(targetTitle) || targetTitle.contains(resTitle)) &&
                            (resArtist.contains(targetArtist) || targetArtist.contains(resArtist))
                        }
                        bestTrack?.let { track ->
                            val response = apiService.getDeezerLyrics(track.id.toString())
                            LyricallyApiParser.parseLyricsResponse(
                                response,
                                "Lyrically (Deezer)",
                                translationLanguage
                            )
                        }
                    } else null
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.d(TAG, "Deezer lyrics search/fetch failed: ${e.message}")
                    null
                }
            }
            "MUSIXMATCH" -> {
                try {
                    val response = apiService.getMusixmatchLyrics(
                        query = "$cleanArtist $cleanTitle",
                        type = if (requestTranslation) "translate" else "default",
                        language = if (requestTranslation) translationLanguage else null
                    )
                    LyricallyApiParser.parseLyricsResponse(
                        response,
                        "Lyrically (Musixmatch)",
                        translationLanguage
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.d(TAG, "Musixmatch lyrics fetch failed: ${e.message}")
                    null
                }
            }
            "GENIUS" -> {
                fetchGeniusLyrics(
                    apiService = apiService,
                    term = term,
                    cleanArtist = cleanArtist,
                    cleanTitle = cleanTitle,
                    translationLanguage = translationLanguage
                )
            }
            else -> null
        }
    }

    private suspend fun fetchGeniusLyrics(
        apiService: RhythmLyricsApiService,
        term: String,
        cleanArtist: String,
        cleanTitle: String,
        translationLanguage: String
    ): LyricsData? {
        return try {
            val encodedTerm = URLEncoder.encode(term, Charsets.UTF_8.name())
            val request = Request.Builder()
                .url("https://genius.com/api/search/multi?q=$encodedTerm")
                .header("Accept", "application/json")
                .build()
            val searchPayload = genericHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string()
            }
            val sections = JsonParser.parseString(searchPayload)
                .asJsonObject
                .getAsJsonObject("response")
                ?.getAsJsonArray("sections")
                ?: return null
            val candidates = sections
                .asSequence()
                .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
                .filter { it.get("type")?.asString == "song" }
                .flatMap { section ->
                    section.getAsJsonArray("hits")
                        ?.asSequence()
                        .orEmpty()
                }
                .mapNotNull { hit ->
                    hit.takeIf(JsonElement::isJsonObject)
                        ?.asJsonObject
                        ?.getAsJsonObject("result")
                }
                .mapNotNull { result ->
                    val url = result.get("url")?.asString?.takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    val candidateTitle = result.get("title")?.asString.orEmpty()
                    val candidateArtist = result.getAsJsonObject("primary_artist")
                        ?.get("name")
                        ?.asString
                        .orEmpty()
                    Triple(url, candidateTitle, candidateArtist)
                }
                .toList()

            val targetTitle = canonicalizeForMatch(cleanTitle)
            val targetArtist = canonicalizeForMatch(cleanArtist)
            val best = candidates
                .filter { (_, candidateTitle, _) ->
                    val title = canonicalizeForMatch(candidateTitle)
                    title.contains(targetTitle) || targetTitle.contains(title)
                }
                .maxByOrNull { (_, candidateTitle, candidateArtist) ->
                    val artist = canonicalizeForMatch(candidateArtist)
                    val artistMatches = artist.contains(targetArtist) || targetArtist.contains(artist)
                    val supplementalPage = candidateTitle.contains("Romanized", ignoreCase = true) ||
                        candidateTitle.contains("Translation", ignoreCase = true)
                    (if (artistMatches) 100 else 0) - (if (supplementalPage) 25 else 0)
                }
                ?: return null

            LyricallyApiParser.parseLyricsResponse(
                apiService.getGeniusLyrics(best.first),
                "Lyrically (Genius)",
                translationLanguage
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d(TAG, "Genius lyrics search/fetch failed: ${e.message}")
            null
        }
    }

    private suspend fun findNeteaseTrackWithITunesTitle(
        apiService: RhythmLyricsApiService,
        artist: String,
        title: String,
        cleanArtist: String,
        cleanTitle: String
    ): NeteaseSearchSong? {
        val searchService = NetworkClient.itunesSearchApiService ?: return null
        val fallbackTitle = try {
            searchService.searchSongs(term = "$artist $title", limit = 10).results
                .asSequence()
                .filter { candidate ->
                    val candidateArtist = canonicalizeForMatch(candidate.artistName.orEmpty())
                    val targetArtist = canonicalizeForMatch(cleanArtist)
                    candidateArtist.contains(targetArtist) || targetArtist.contains(candidateArtist)
                }
                .mapNotNull { it.trackName?.trim()?.takeIf(String::isNotEmpty) }
                .minByOrNull(String::length)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d(TAG, "iTunes title fallback failed for NetEase: ${e.message}")
            null
        } ?: return null

        Log.d(TAG, "NetEase retry using iTunes title '$fallbackTitle' for $artist - $title")
        val results = LyricallyApiParser.parseNeteaseSearch(
            apiService.searchNetease("$cleanArtist $fallbackTitle")
        )
        return matchNeteaseResult(results, fallbackTitle, cleanArtist)
    }

    private fun matchNeteaseResult(
        results: List<NeteaseSearchSong>,
        cleanTitle: String,
        cleanArtist: String
    ): NeteaseSearchSong? {
        val targetTitle = canonicalizeForMatch(cleanTitle)
        val targetArtist = canonicalizeForMatch(cleanArtist)
        fun matches(candidate: String, target: String): Boolean =
            candidate.isNotBlank() && target.isNotBlank() &&
                (candidate.contains(target) || target.contains(candidate))

        val canonicalResults = results.map { result ->
            val resultTitle = canonicalizeForMatch(result.name.orEmpty())
            val resultArtist = canonicalizeForMatch(
                result.artists.orEmpty().joinToString(" ") { it.name.orEmpty() }
            )
            Triple(result, matches(resultTitle, targetTitle), matches(resultArtist, targetArtist))
        }

        return canonicalResults.firstOrNull { (_, titleMatches, artistMatches) ->
            titleMatches && artistMatches
        }?.first
            // Providers often keep the original CJK title even when the local tag is
            // romanized. A strong artist match is safer than accepting an unrelated
            // remix/cover merely because its title contains the search terms.
            ?: canonicalResults.firstOrNull { (_, _, artistMatches) -> artistMatches }?.first
            ?: canonicalResults.firstOrNull { (_, titleMatches, _) -> titleMatches }?.first
    }

    private fun LyricsData.meetsLyricsRequirements(
        requireRomanization: Boolean,
        requireTranslation: Boolean
    ): Boolean =
        (!requireRomanization || hasUsableTimedRomanization()) &&
            (!requireTranslation || hasUsableTimedTranslation())

    private fun matchGenericResult(
        results: List<RhythmLyricsGenericSearchResult>,
        cleanTitle: String,
        cleanArtist: String
    ): RhythmLyricsGenericSearchResult? {
        val bestTrack = results.firstOrNull { result ->
            val resTitle = canonicalizeForMatch(result.getCanonicalName() ?: "")
            val resArtist = canonicalizeForMatch(result.getCanonicalArtist() ?: "")
            val targetTitle = canonicalizeForMatch(cleanTitle)
            val targetArtist = canonicalizeForMatch(cleanArtist)
            resTitle.isNotBlank() &&
                targetTitle.isNotBlank() &&
                resArtist.isNotBlank() &&
                targetArtist.isNotBlank() &&
                (resTitle.contains(targetTitle) || targetTitle.contains(resTitle)) &&
                (resArtist.contains(targetArtist) || targetArtist.contains(resArtist))
        }
        return bestTrack ?: results.firstOrNull { result ->
            val resTitle = canonicalizeForMatch(result.getCanonicalName() ?: "")
            val targetTitle = canonicalizeForMatch(cleanTitle)
            resTitle.isNotBlank() &&
                targetTitle.isNotBlank() &&
                (resTitle.contains(targetTitle) || targetTitle.contains(resTitle))
        }
    }

    /**
     * Attempts to find and extract embedded lyrics from a local audio file matching artist/title.
     * Used as fallback when song URI is from a streaming source (remote URL).
     */
    private fun tryGetEmbeddedLyricsFromLocalFile(artist: String, title: String): LyricsData? {
        return try {
            // Query MediaStore for a local file matching this artist and title
            val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
            val selection = "${MediaStore.Audio.Media.TITLE} = ? AND ${MediaStore.Audio.Media.ARTIST} = ?"
            val selectionArgs = arrayOf(title, artist)
            
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val localFilePath = cursor.getString(dataIndex)
                    
                    if (localFilePath != null) {
                        val localFile = File(localFilePath)
                        if (localFile.exists() && localFile.canRead()) {
                            val localUri = Uri.fromFile(localFile)
                            Log.d(TAG, "Found local file for streaming song, attempting embedded lyrics extraction: $localFilePath")
                            return getEmbeddedLyrics(localUri)
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Error trying to find local file for embedded lyrics: ${e.message}")
            null
        }
    }

    /**
     * Finds local lyrics file in app's files directory OR next to the music file
     * Supports both .lrc files (in music folder) and .json cache files (in app folder)
     */
    private fun findLocalLyrics(artist: String, title: String, songId: String? = null): LyricsData? {
        Log.d(TAG, "===== findLocalLyrics START: $artist - $title (songId=$songId) =====")
        
        // First, check for .lrc file next to the music file
        try {
            val lrcLyrics = findLrcFileForSong(artist, title, songId)
            if (lrcLyrics != null) {
                Log.d(TAG, "===== FOUND LOCAL .LRC FILE =====")
                return lrcLyrics
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking for .lrc file: ${e.message}")
        }
        
        // Second, check for cached JSON lyrics in app's files directory
        val fileName = "${artist}_${title}.json".replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(context.filesDir, "lyrics/$fileName")
        Log.d(TAG, "===== Checking for saved JSON file: $fileName (exists=${file.exists()}) =====")
        return try {
            if (file.exists()) {
                val json = file.readText()
                var data = Gson().fromJson(json, LyricsData::class.java)
                Log.d(TAG, "===== LOADED LYRICS FROM SAVED JSON FILE =====")

                if (
                    !data.syncedLyrics.isNullOrBlank() &&
                    !data.hasUsableSyncedTimeline()
                ) {
                    Log.w(
                        TAG,
                        "Rejecting cached synced lyrics with a collapsed/invalid timeline: $fileName"
                    )
                    return null
                }
                
                // Self-healing migration for old cached Lyrically lyrics containing the shift bug
                if (data.source == "Lyrically" && !data.wordByWordLyrics.isNullOrBlank() && data.isCorrected != true) {
                    Log.d(TAG, "===== OLD CACHED APPLE MUSIC LYRICS DETECTED, APPLYING CORRECTION PASS =====")
                    try {
                        val originalListType = object : com.google.gson.reflect.TypeToken<List<RhythmLyricsLine>>() {}.type
                        val originalLines: List<RhythmLyricsLine> = Gson().fromJson(data.wordByWordLyrics, originalListType)
                        
                        // Shift the part flags to the right for each line
                        val correctedContent = originalLines.map { line ->
                            val words = line.text
                            if (words != null && words.isNotEmpty()) {
                                val shiftedWords = words.mapIndexed { idx, word ->
                                    val isPart = if (idx > 0) {
                                        words[idx - 1].part ?: false
                                    } else {
                                        false
                                    }
                                    word.copy(part = isPart)
                                }
                                line.copy(text = shiftedWords)
                            } else {
                                line
                            }
                        }
                        
                        val correctedWordByWordJson = Gson().toJson(correctedContent)
                        val parsedLines = RhythmLyricsParser.parseWordByWordLyrics(correctedWordByWordJson)
                        val lrc = RhythmLyricsParser.toLRCFormat(parsedLines)
                        val plain = RhythmLyricsParser.toPlainText(parsedLines)
                        
                        data = data.copy(
                            plainLyrics = plain,
                            syncedLyrics = lrc,
                            wordByWordLyrics = correctedWordByWordJson,
                            isCorrected = true
                        )
                        
                        // Save the corrected version back to cache
                        saveLocalLyrics(artist, title, data)
                        Log.d(TAG, "===== SUCCESSFULLY CORRECTED AND RESAVED CACHED LYRICS =====")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to correct cached Lyrically lyrics", e)
                    }
                }

                if (
                    data.cacheVersion != LYRICS_CACHE_VERSION &&
                    data.source?.startsWith("Lyrically") == true &&
                    !data.wordByWordLyrics.isNullOrBlank()
                ) {
                    try {
                        val parsed = RhythmLyricsParser.parseWordByWordLyrics(
                            data.wordByWordLyrics.orEmpty()
                        )
                        if (parsed.isNotEmpty()) {
                            data = data.copy(
                                plainLyrics = RhythmLyricsParser.toPlainText(parsed),
                                syncedLyrics = RhythmLyricsParser.toLRCFormat(parsed),
                                cacheVersion = LYRICS_CACHE_VERSION
                            )
                            saveLocalLyrics(artist, title, data)
                            Log.d(TAG, "Migrated cached Lyrically word spacing: $fileName")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not migrate cached word spacing: ${e.message}")
                    }
                }
                data
            } else {
                Log.d(TAG, "===== NO SAVED JSON FILE FOUND =====")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading local lyrics file: ${e.message}", e)
            null
        }
    }
    
    /** Reads the persisted online Romanization cache. */
    private fun findLocalRomanizedLyrics(artist: String, title: String): LyricsData? {
        return try {
            val fileName = "${artist}_${title}.romanized.json".replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val file = File(File(context.filesDir, "lyrics"), fileName)
            if (!file.exists()) return null
            Log.d(TAG, "===== LOADED ROMANIZED LYRICS FROM CACHE: $fileName =====")
            Gson().fromJson(file.readText(), LyricsData::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Error reading romanized lyrics cache: ${e.message}")
            null
        }
    }

    /**
     * Searches for .lrc file next to the music file
     * Looks for files with same name as the song or generic patterns
     */
    private fun findLrcFileForSong(artist: String, title: String, songId: String? = null): LyricsData? {
        try {
            // Find the song in MediaStore to get its path
            val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
            val selection = "${MediaStore.Audio.Media.TITLE} = ? AND ${MediaStore.Audio.Media.ARTIST} = ?"
            val selectionArgs = arrayOf(title, artist)
            
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val songPath = cursor.getString(dataIndex)
                    
                    if (songPath != null) {
                        val songFile = File(songPath)
                        val directory = songFile.parentFile
                        val songNameWithoutExt = songFile.nameWithoutExtension
                        val appSettings = AppSettings.getInstance(context)
                        val customLrcName =
                            songId?.let { appSettings.getSongCustomLrcFile(it) }
                        val extensions = listOf("lrc", "elrc", "ttml", "srt")
                        val cleanArtist = artist.replace(Regex("[^a-zA-Z0-9]"), "_")
                        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9]"), "_")
                        val candidateNames = buildList {
                            customLrcName?.let(::add)
                            extensions.forEach { ext -> add("$songNameWithoutExt.$ext") }
                            extensions.forEach { ext -> add("${cleanArtist}_${cleanTitle}.$ext") }
                        }.distinct()
                        
                        if (directory != null && directory.exists()) {
                            // Check for custom tagged LRC file name
                            if (customLrcName != null) {
                                val lyricFile = File(directory, customLrcName)
                                if (lyricFile.exists() && lyricFile.canRead()) {
                                    val ext = lyricFile.extension.lowercase()
                                    val content = lyricFile.readText()
                                    val parsed = parseLocalLyricsFile(content, ext)
                                    if (parsed != null) return parsed
                                }
                            }

                            for (ext in extensions) {
                                val lyricFile = File(directory, "$songNameWithoutExt.$ext")
                                if (lyricFile.exists() && lyricFile.canRead()) {
                                    val content = lyricFile.readText()
                                    val parsed = parseLocalLyricsFile(content, ext)
                                    if (parsed != null) return parsed
                                }
                            }
                            
                            // Also try with artist - title pattern
                            for (ext in extensions) {
                                val lyricFile = File(directory, "${cleanArtist}_${cleanTitle}.$ext")
                                if (lyricFile.exists() && lyricFile.canRead()) {
                                    val content = lyricFile.readText()
                                    val parsed = parseLocalLyricsFile(content, ext)
                                    if (parsed != null) return parsed
                                }
                            }
                        }

                        findSidecarInGrantedTrees(songPath, candidateNames)?.let {
                            return it
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for local lyric file", e)
        }
        return null
    }

    /** Searches user-granted SAF trees for sidecars unavailable through media permissions. */
    private fun findSidecarInGrantedTrees(
        songPath: String,
        candidateNames: List<String>
    ): LyricsData? {
        val grantedTrees = AppSettings.getInstance(context).lyricsFolderTreeUris.value
        if (grantedTrees.isEmpty()) return null

        for (treeString in grantedTrees) {
            try {
                val treeUri = Uri.parse(treeString)
                val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                val volume = treeDocumentId.substringBefore(':')
                val treeRelativePath = treeDocumentId.substringAfter(':', "").trim('/')
                val songRelativePath = when {
                    volume.equals("primary", ignoreCase = true) &&
                        songPath.startsWith("/storage/emulated/0/") ->
                        songPath.removePrefix("/storage/emulated/0/")

                    songPath.startsWith("/storage/$volume/") ->
                        songPath.removePrefix("/storage/$volume/")

                    else -> null
                } ?: continue
                val songDirectory = songRelativePath.substringBeforeLast('/', "")
                if (
                    treeRelativePath.isNotEmpty() &&
                    songDirectory != treeRelativePath &&
                    !songDirectory.startsWith("$treeRelativePath/")
                ) {
                    continue
                }

                var directory: DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)
                val pathBelowTree = if (treeRelativePath.isEmpty()) {
                    songDirectory
                } else {
                    songDirectory.removePrefix(treeRelativePath).trim('/')
                }
                for (segment in pathBelowTree.split('/').filter(String::isNotBlank)) {
                    directory = directory?.findFile(segment)
                        ?.takeIf(DocumentFile::isDirectory)
                }
                val resolvedDirectory = directory?.takeIf(DocumentFile::isDirectory) ?: continue

                for (candidateName in candidateNames) {
                    val lyricDocument = resolvedDirectory.findFile(candidateName)
                        ?.takeIf { it.isFile && it.canRead() }
                        ?: continue
                    val content = context.contentResolver.openInputStream(lyricDocument.uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: continue
                    val parsed = parseLocalLyricsFile(
                        content = content,
                        ext = candidateName.substringAfterLast('.', "")
                    )
                    if (parsed != null) {
                        Log.d(TAG, "Found sidecar through granted lyrics folder: $candidateName")
                        return parsed
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read granted lyrics folder '$treeString': ${e.message}")
            }
        }
        return null
    }

    private fun parseLocalLyricsFile(content: String, ext: String): LyricsData? {
        try {
            if (content.isBlank()) return null
            
            if (ext.equals("lrc", ignoreCase = true) || ext.equals("elrc", ignoreCase = true)) {
                return parseLrcFile(content)
            }
            
            val semanticLyrics = when (ext.lowercase()) {
                "ttml" -> chromahub.rhythm.app.util.parseTtml(null, content)
                "srt" -> chromahub.rhythm.app.util.parseSrt(content, true)
                else -> null
            } ?: return null
            
            return when (semanticLyrics) {
                is chromahub.rhythm.app.util.SemanticLyrics.UnsyncedLyrics -> {
                    val plainLyrics = semanticLyrics.unsyncedText.joinToString("\n") { it.first }
                    LyricsData(plainLyrics = plainLyrics, syncedLyrics = null, wordByWordLyrics = null, source = "Local File")
                }
                is chromahub.rhythm.app.util.SemanticLyrics.SyncedLyrics -> {
                    val plainLyrics = semanticLyrics.text.joinToString("\n") { it.text }
                    val syncedLyrics = semanticLyrics.text.joinToString("\n") { line ->
                        val min = (line.start / 60000uL)
                        val sec = (line.start % 60000uL) / 1000uL
                        val ms = (line.start % 1000uL) / 10uL // 2 digits
                        val timestamp = java.lang.String.format("%02d:%02d.%02d", min.toInt(), sec.toInt(), ms.toInt())
                        "[$timestamp]${line.text}"
                    }
                    
                    val rhythmWordLines = semanticLyrics.text.map { line ->
                        val words = line.words?.map { word ->
                            val wordText = try {
                                line.text.substring(word.charRange.first, word.charRange.last + 1)
                            } catch (e: Exception) {
                                ""
                            }
                            mapOf(
                                "text" to wordText,
                                "part" to false,
                                "timestamp" to word.begin.toLong(),
                                "endtime" to (word.endInclusive?.toLong() ?: word.begin.toLong())
                            )
                        } ?: listOf(
                            mapOf(
                                "text" to line.text,
                                "part" to false,
                                "timestamp" to line.start.toLong(),
                                "endtime" to line.end.toLong()
                            )
                        )
                        
                        mapOf(
                            "text" to words,
                            "background" to false,
                            "timestamp" to line.start.toLong(),
                            "endtime" to line.end.toLong()
                        )
                    }
                    val wordByWordJson = com.google.gson.Gson().toJson(rhythmWordLines)
                    
                    LyricsData(plainLyrics, syncedLyrics, wordByWordJson, source = "Local File")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing local $ext lyrics file", e)
        }
        return null
    }
    
    /**
     * Parses .lrc file content into LyricsData format
     * LRC format: [mm:ss.xx]lyrics text
     */
    private fun parseLrcFile(lrcContent: String): LyricsData? {
        try {
            if (lrcContent.isBlank()) return null
            
            val syncedLines = mutableListOf<String>()
            val plainLines = mutableListOf<String>()
            var hasSyncedLyrics = false
            
            // Pattern to match LRC timestamps [mm:ss.xx] or [mm:ss]
            val timestampPattern = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\](.*)") 
            
            val customTimedLyrics = parseCustomTimedLyrics(lrcContent)
            if (customTimedLyrics != null) {
                Log.d(TAG, "Successfully parsed custom word-timing lyrics in .lrc file")
                return customTimedLyrics
            }

            val hasWordTimestamps = LyricsParser.hasWordTimestamps(lrcContent)
            
            if (hasWordTimestamps) {
                Log.d(TAG, "Detected Enhanced LRC format in .lrc file with word-level timestamps")
                
                // Parse Enhanced LRC and convert to word-by-word format
                val enhancedLines = LyricsParser.parseEnhancedLRC(lrcContent)
                
                if (enhancedLines.isNotEmpty()) {
                    // Convert to Rhythm word-by-word format (JSON)
                    val wordByWordJson = convertEnhancedLRCToWordByWord(enhancedLines)
                    
                    // Also extract plain text and line-synced LRC
                    val plainText = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                        line.words.joinToString(" ") { word: EnhancedWord -> word.text }
                    }
                    
                    val syncedLrc = enhancedLines.joinToString("\n") { line: EnhancedLyricLine ->
                        val timestamp = formatLRCTimestamp(line.lineTimestamp)
                        val text = line.words.joinToString(" ") { word: EnhancedWord -> word.text }
                        "[$timestamp]$text"
                    }
                    
                    Log.d(TAG, "Successfully converted Enhanced LRC from .lrc file to word-by-word format (${enhancedLines.size} lines)")
                    return LyricsData(plainText, syncedLrc, wordByWordJson)
                }
            }
            
            val lines = lrcContent.lines()
            
            // Standard LRC format (line-by-line only)
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) continue
                
                // Check if line has timestamp
                val match = timestampPattern.find(trimmedLine)
                if (match != null) {
                    hasSyncedLyrics = true
                    syncedLines.add(trimmedLine) // Keep the timestamp for synced lyrics
                    val lyricsText = LyricsParser.normalizeWordFlowText(match.groupValues[4].trim())
                    if (lyricsText.isNotEmpty()) {
                        plainLines.add(lyricsText) // Extract just the lyrics text for plain version
                    }
                } else {
                    // Metadata line (like [ar:], [ti:], [al:]) or plain text
                    if (!trimmedLine.startsWith("[") || !trimmedLine.contains("]")) {
                        plainLines.add(LyricsParser.normalizeWordFlowText(trimmedLine))
                    }
                }
            }
            
            val plainLyrics = if (plainLines.isNotEmpty()) plainLines.joinToString("\n") else null
            val syncedLyrics = if (hasSyncedLyrics && syncedLines.isNotEmpty()) syncedLines.joinToString("\n") else null
            
            if (plainLyrics != null || syncedLyrics != null) {
                Log.d(TAG, "Successfully parsed .lrc file - Synced: ${syncedLyrics != null}, Plain: ${plainLyrics != null}")
                return LyricsData(plainLyrics, syncedLyrics, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing .lrc file", e)
        }
        return null
    }

    /**

     * Saves lyrics to a local file
     */
    private fun saveLocalLyrics(artist: String, title: String, lyricsData: LyricsData, variant: String = "") {
        try {
            val fileName = "${artist}_${title}${variant}.json".replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val lyricsDir = File(context.filesDir, "lyrics")
            lyricsDir.mkdirs()

            val file = File(lyricsDir, fileName)
            val json = Gson().toJson(lyricsData.copy(cacheVersion = LYRICS_CACHE_VERSION))
            file.writeText(json)
            Log.d(TAG, "Saved lyrics to local file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lyrics to local file: ${e.message}", e)
        }
    }

    /**
     * Fetches album art from Deezer API for albums without artwork
     */
    suspend fun fetchAlbumArtwork(albums: List<Album>): List<Album> = withContext(Dispatchers.IO) {
        val updatedAlbums = mutableListOf<Album>()

        for (album in albums) {
            // Check if the album has a content:// URI and if it actually exists
            if (album.artworkUri != null) {
                if (album.artworkUri.toString()
                        .startsWith("content://media/external/audio/albumart")
                ) {
                    // Try to open the input stream to check if the artwork exists
                    var artworkExists = false
                    try {
                        context.contentResolver.openInputStream(album.artworkUri)?.use {
                            artworkExists = true
                        }
                    } catch (e: Exception) {
                        Log.d(
                            TAG,
                            "Album artwork URI exists but can't be accessed for ${album.title}: ${album.artworkUri}",
                            e
                        )
                        artworkExists = false
                    }

                    if (artworkExists) {
                        updatedAlbums.add(album)
                        continue
                    }
                } else {
                    updatedAlbums.add(album)
                    continue
                }
            }

            // Check cache first
            val cacheKey = "${album.artist}:${album.title}"
            val cachedUri = albumImageCache[cacheKey]
            if (cachedUri != null) {
                updatedAlbums.add(album.copy(artworkUri = cachedUri))
                continue
            }

            try {
                // Skip albums with empty or "Unknown" artist/title
                if (album.artist.isBlank() || album.title.isBlank() ||
                    album.artist.equals("Unknown", ignoreCase = true) ||
                    album.title.equals("Unknown", ignoreCase = true)
                ) {
                    updatedAlbums.add(album)
                    continue
                }

                // Generate a custom placeholder image based on album name
                val placeholderUri = chromahub.rhythm.app.util.ImageUtils.generatePlaceholderImage(
                    name = album.title,
                    size = 500,
                    cacheDir = context.cacheDir
                )

                // Add delay to avoid rate limiting
                delay(500)

                Log.d(TAG, "Searching for album: ${album.title} by ${album.artist}")

                // Search for the album on Deezer (only if enabled)
                if (NetworkClient.isDeezerApiEnabled() && deezerApiService != null) {
                    // Add delay to avoid rate limiting
                    delay(300)
                    
                    try {
                        // First try searching for the album by title and artist
                        val searchQuery = "${album.title} ${album.artist}"
                        var albumSearchResponse = deezerApiService.searchAlbums(searchQuery)
                        var deezerAlbum = findBestAlbumMatch(albumSearchResponse.data, album.title, album.artist)

                        // If no match, try with just the album title
                        if (deezerAlbum == null && album.title.isNotBlank()) {
                            albumSearchResponse = deezerApiService.searchAlbums(album.title)
                            deezerAlbum = findBestAlbumMatch(albumSearchResponse.data, album.title, album.artist)
                        }

                        if (deezerAlbum != null) {
                            // Choose the highest quality image available for album artwork
                            val imageUrl = when {
                                !deezerAlbum.coverXl.isNullOrEmpty() -> deezerAlbum.coverXl
                                !deezerAlbum.coverBig.isNullOrEmpty() -> deezerAlbum.coverBig
                                !deezerAlbum.coverMedium.isNullOrEmpty() -> deezerAlbum.coverMedium
                                !deezerAlbum.cover.isNullOrEmpty() -> deezerAlbum.cover
                                else -> null
                            }

                            if (!imageUrl.isNullOrEmpty() && imageUrl.startsWith("http")) {
                                val imageUri = Uri.parse(imageUrl)
                                albumImageCache[cacheKey] = imageUri
                                updatedAlbums.add(album.copy(artworkUri = imageUri))
                                Log.d(TAG, "Found Deezer album artwork for: ${album.title}, URL: $imageUrl")
                                continue
                            }
                        } else {
                            Log.d(TAG, "No Deezer album found for: ${album.title} by ${album.artist}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Deezer album search failed for ${album.title}: ${e.message}")
                    }
                }

                // -------- YTMusic fallback (only when local album art is absent) --------
                // Check if we should use YTMusic fallback for albums
                if (NetworkClient.isYTMusicApiEnabled() && ytmusicApiService != null) {
                    var foundAlbumArt = false
                    try {
                        Log.d(TAG, "Trying YTMusic for album: ${album.title} by ${album.artist}")
                        
                        // Create search request for album
                        val searchQuery = "${album.title} ${album.artist}"
                    val searchRequest = YTMusicSearchRequest(
                        context = YTMusicContext(YTMusicClient()),
                        query = searchQuery,
                        params = "EgWKAQIYAWoKEAoQAxAEEAkQBQ%3D%3D" // Album search filter
                    )
                    
                    val searchResponse = ytmusicApiService.search(request = searchRequest)
                    if (searchResponse.isSuccessful) {
                        val imageUrl = searchResponse.body()?.extractAlbumImageUrl()
                        if (!imageUrl.isNullOrEmpty()) {
                            val imageUri = Uri.parse(imageUrl)
                            albumImageCache[cacheKey] = imageUri
                            updatedAlbums.add(album.copy(artworkUri = imageUri))
                            Log.d(TAG, "Found YTMusic album art for ${album.title}: $imageUrl")
                            foundAlbumArt = true
                        } else {
                            // Try to get detailed album info for better quality cover art
                            val browseId = searchResponse.body()?.extractAlbumBrowseId()
                            if (!browseId.isNullOrEmpty()) {
                                val browseRequest = YTMusicBrowseRequest(
                                    context = YTMusicContext(YTMusicClient()),
                                    browseId = browseId
                                )
                                val albumResponse = ytmusicApiService.getAlbum(request = browseRequest)
                                if (albumResponse.isSuccessful) {
                                    val detailedImageUrl = albumResponse.body()?.extractAlbumCover()
                                    if (!detailedImageUrl.isNullOrEmpty()) {
                                        val imageUri = Uri.parse(detailedImageUrl)
                                        albumImageCache[cacheKey] = imageUri
                                        updatedAlbums.add(album.copy(artworkUri = imageUri))
                                        Log.d(TAG, "Found detailed YTMusic album art for ${album.title}: $detailedImageUrl")
                                        foundAlbumArt = true
                                    }
                                }
                            }
                        }
                    }
                    } catch (e: Exception) {
                        Log.w(TAG, "YTMusic fallback failed for album ${album.title}: ${e.message}")
                    }

                    // If YTMusic didn't find anything, use placeholder
                    if (!foundAlbumArt) {
                        Log.d(
                            TAG,
                            "No valid image found for album: ${album.title}, using generated placeholder"
                        )
                        albumImageCache[cacheKey] = placeholderUri
                        updatedAlbums.add(album.copy(artworkUri = placeholderUri))
                    }
                } else {
                    // YTMusic is disabled, use placeholder
                    Log.d(
                        TAG,
                        "No valid image found for album: ${album.title}, using generated placeholder"
                    )
                    albumImageCache[cacheKey] = placeholderUri
                    updatedAlbums.add(album.copy(artworkUri = placeholderUri))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching album artwork for ${album.title}", e)
                // Try to use a generated placeholder
                try {
                    val placeholderUri =
                        chromahub.rhythm.app.util.ImageUtils.generatePlaceholderImage(
                            name = album.title,
                            size = 500,
                            cacheDir = context.cacheDir
                        )
                    updatedAlbums.add(album.copy(artworkUri = placeholderUri))
                } catch (e2: Exception) {
                    // If even placeholder generation fails, keep the original album without changes
                    updatedAlbums.add(album)
                }
            }
        }

        updatedAlbums
    }

    /**
     * Extracts embedded album art from audio files in a background pass.
      * Called post-scan when preferSongArtwork or losslessArtwork is enabled.
     * Updates [cachedSongs] with embedded art URIs and returns songs that were updated.
     */
    suspend fun extractEmbeddedArtworkForSongs(
        songs: List<Song>,
        lossless: Boolean = false
    ): List<Song> = withContext(Dispatchers.IO) {
        val updatedSongs = songs.toMutableList()
        var anyUpdated = false
        for (i in updatedSongs.indices) {
            val song = updatedSongs[i]
            
            val hasValidArtwork = song.artworkUri?.let { uri ->
                if (isEmbeddedArtworkCacheUri(uri)) {
                    val path = uri.path ?: return@let false
                    val fileName = File(path).name
                    val isLosslessFile = fileName.startsWith("embedded_art_lossless_")
                    File(path).exists() && isLosslessFile == lossless
                } else {
                    false
                }
            } ?: false
            
            if (hasValidArtwork) {
                continue
            }

            try {
                val embeddedUri = chromahub.rhythm.app.util.MediaUtils.extractEmbeddedAlbumArt(
                    context, song.uri, context.cacheDir, lossless
                )
                if (embeddedUri != null && embeddedUri != song.artworkUri) {
                    updatedSongs[i] = song.copy(artworkUri = embeddedUri)
                    anyUpdated = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract embedded art for ${song.title}", e)
            }
        }
        if (anyUpdated) {
            cachedSongs = updatedSongs
        }
        updatedSongs
    }

    /**
     * Fetches track/song images from YTMusic API for songs without artwork.
     * This is used as a fallback when local album art is absent and other APIs fail.
     */
    suspend fun fetchTrackArtwork(songs: List<Song>): List<Song> = withContext(Dispatchers.IO) {
        val updatedSongs = mutableListOf<Song>()

        for (song in songs) {
            // Only fetch if song doesn't have artwork already
            if (song.artworkUri != null) {
                updatedSongs.add(song)
                continue
            }

            // Check if album has artwork - if yes, we don't need track-specific artwork
            val album = loadAlbums().findAlbumForSong(song)
            if (album?.artworkUri != null) {
                // Album has artwork, no need for track-specific image
                updatedSongs.add(song)
                continue
            }

            val cacheKey = "${song.artist}:${song.title}"
            
            // Check cache first
            val cachedUri = albumImageCache[cacheKey] // Reuse album cache for tracks
            if (cachedUri != null) {
                updatedSongs.add(song.copy(artworkUri = cachedUri))
                continue
            }

            try {
                // Skip songs with empty or "Unknown" artist/title
                if (song.artist.isBlank() || song.title.isBlank() ||
                    song.artist.equals("Unknown", ignoreCase = true) ||
                    song.title.equals("Unknown", ignoreCase = true)
                ) {
                    updatedSongs.add(song)
                    continue
                }

                if (!NetworkClient.isYTMusicApiEnabled() || ytmusicApiService == null) {
                    Log.d(TAG, "YTMusic API is disabled or unavailable, skipping track artwork for: ${song.title}")
                    updatedSongs.add(song)
                    continue
                }

                Log.d(TAG, "Searching YTMusic for track: ${song.title} by ${song.artist}")

                // Add delay to avoid rate limiting
                delay(200)

                // Create search request for song/track
                val searchQuery = "${song.title} ${song.artist}"
                val searchRequest = YTMusicSearchRequest(
                    context = YTMusicContext(YTMusicClient()),
                    query = searchQuery,
                    params = "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D" // Song search filter
                )

                val searchResponse = ytmusicApiService.search(request = searchRequest)
                if (searchResponse.isSuccessful) {
                    // For tracks, we can extract image from the first result
                    val imageUrl = searchResponse.body()?.extractAlbumImageUrl() // Tracks use same thumbnail structure
                    if (!imageUrl.isNullOrEmpty()) {
                        val imageUri = Uri.parse(imageUrl)
                        albumImageCache[cacheKey] = imageUri // Cache for future use
                        updatedSongs.add(song.copy(artworkUri = imageUri))
                        Log.d(TAG, "Found YTMusic track image for ${song.title}: $imageUrl")
                        continue
                    }
                }

                Log.d(TAG, "No YTMusic image found for track: ${song.title}")
                updatedSongs.add(song)

            } catch (e: Exception) {
                Log.w(TAG, "YTMusic track image fetch failed for ${song.title}: ${e.message}")
                updatedSongs.add(song)
            }
        }

        updatedSongs
    }

    suspend fun getSongsForArtist(artistId: String): List<Song> = withContext(Dispatchers.IO) {
        val allSongs = loadSongs() // Ensure songs are loaded once
        val allArtists = loadArtists() // Ensure artists are loaded once
        val appSettings = AppSettings.getInstance(context)
        val groupByAlbumArtist = appSettings.groupByAlbumArtist.value
        val artistSeparatorEnabled = appSettings.artistSeparatorEnabled.value
        val delimiters = if (artistSeparatorEnabled) appSettings.artistSeparatorDelimiters.value else ""
        val preloadedCharDelimiters = delimiters.map { it.toString() }

        Log.d("MusicRepository", "Getting songs for artist ID: $artistId")

        // Find the artist by ID
        val artist = allArtists.find { it.id == artistId }
        if (artist == null) {
            Log.e("MusicRepository", "Artist not found with ID: $artistId")
            return@withContext emptyList()
        }

        Log.d("MusicRepository", "Found artist: ${artist.name} (ID: $artistId, groupByAlbumArtist=$groupByAlbumArtist)")

        // Filter songs that match the artist's name
        val artistSongs = allSongs.filter { song ->
            if (groupByAlbumArtist) {
                // Match against split album artist names, falling back to split track artists.
                val explicitAlbumArtist = song.albumArtist?.trim().orEmpty()
                val songArtistNames = if (explicitAlbumArtist.isNotBlank() && !explicitAlbumArtist.equals("<unknown>", ignoreCase = true)) {
                    splitArtistNames(explicitAlbumArtist, preloadedCharDelimiters)
                } else {
                    splitArtistNames(song.artist, preloadedCharDelimiters)
                }
                songArtistNames.any { it.equals(artist.name, ignoreCase = true) }
            } else {
                // When not grouping, check if artist name appears in the track artist field (exact or as part of collaboration)
                val artistNames = splitArtistNames(song.artist, preloadedCharDelimiters)
                artistNames.any { it.equals(artist.name, ignoreCase = true) }
            }
        }

        Log.d("MusicRepository", "Found ${artistSongs.size} songs for artist: ${artist.name}")
        return@withContext artistSongs
    }

    suspend fun getAlbumsForArtist(artistId: String): List<Album> = withContext(Dispatchers.IO) {
        val allAlbums = loadAlbums() // Ensure albums are loaded once
        val allArtists = loadArtists() // Ensure artists are loaded once
        val allSongs = loadSongs() // Need songs to check album artist
        val appSettings = AppSettings.getInstance(context)
        val groupByAlbumArtist = appSettings.groupByAlbumArtist.value
        val artistSeparatorEnabled = appSettings.artistSeparatorEnabled.value
        val delimiters = if (artistSeparatorEnabled) appSettings.artistSeparatorDelimiters.value else ""
        val preloadedCharDelimiters = delimiters.map { it.toString() }

        Log.d("MusicRepository", "Getting albums for artist ID: $artistId")

        // Find the artist by ID
        val artist = allArtists.find { it.id == artistId }
        if (artist == null) {
            Log.e("MusicRepository", "Artist not found with ID: $artistId")
            return@withContext emptyList()
        }

        Log.d("MusicRepository", "Found artist: ${artist.name} (ID: $artistId, groupByAlbumArtist=$groupByAlbumArtist)")

        // Filter albums that match the artist's name
        val artistAlbums = allAlbums.filter { album ->
            if (groupByAlbumArtist) {
                // When grouping by album artist, check if any song in the album has matching album artist
                album.songs.any { song ->
                    val explicitAlbumArtist = song.albumArtist?.trim().orEmpty()
                    val songArtistNames = if (explicitAlbumArtist.isNotBlank() && !explicitAlbumArtist.equals("<unknown>", ignoreCase = true)) {
                        splitArtistNames(explicitAlbumArtist, preloadedCharDelimiters)
                    } else {
                        splitArtistNames(song.artist, preloadedCharDelimiters)
                    }
                    songArtistNames.any { it.equals(artist.name, ignoreCase = true) }
                }
            } else {
                // When not grouping, check if artist appears in any song's track artist field for this album
                album.songs.any { song ->
                    splitArtistNames(song.artist, preloadedCharDelimiters).any { it.equals(artist.name, ignoreCase = true) }
                }
            }
        }

        Log.d("MusicRepository", "Found ${artistAlbums.size} albums for artist: ${artist.name}")
        return@withContext artistAlbums
    }

    suspend fun createPlaylist(name: String): Playlist {
        return Playlist(
            id = System.currentTimeMillis().toString(),
            name = name
        )
    }

    // Mock data for locations
    fun getLocations(): List<PlaybackLocation> {
        return listOf(
            PlaybackLocation(
                id = "living_room",
                name = "Living room",
                icon = 0 // Replace with actual icon resource
            ),
            PlaybackLocation(
                id = "bedroom",
                name = "Bedroom",
                icon = 0 // Replace with actual icon resource
            ),
            PlaybackLocation(
                id = "kitchen",
                name = "Kitchen",
                icon = 0 // Replace with actual icon resource
            )
        )
    }

    /**
     * Checks if the device is currently connected to the internet
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    /**
     * Finds locally stored artist image
     */
    private fun findLocalArtistImage(artistName: String): Uri? {
        val fileName = "${artistName}.jpg".replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(context.filesDir, "artist_images/$fileName")
        return if (file.exists()) Uri.fromFile(file) else null
    }

    /**
     * Finds artist image from song-adjacent folders.
     * Checks common names like artist.jpg or band.jpg in album/artist parent folders.
     */
    private fun findArtistImageInLibraryFolders(
        artistName: String,
        songs: List<Song>,
        groupByAlbumArtist: Boolean,
        preloadedCharDelimiters: List<String>
    ): Uri? {
        if (artistName.isBlank() || songs.isEmpty()) return null

        val normalizedArtistName = artistName.trim()
        val candidateSongs = songs.filter { song ->
            val explicitAlbumArtist = song.albumArtist?.trim().orEmpty()
            val names = if (groupByAlbumArtist) {
                if (explicitAlbumArtist.isNotBlank() && !explicitAlbumArtist.equals("<unknown>", ignoreCase = true)) {
                    splitArtistNames(explicitAlbumArtist, preloadedCharDelimiters)
                } else {
                    splitArtistNames(song.artist, preloadedCharDelimiters)
                }
            } else {
                splitArtistNames(song.artist, preloadedCharDelimiters)
            }

            names.any { it.equals(normalizedArtistName, ignoreCase = true) }
        }

        if (candidateSongs.isEmpty()) return null

        val candidateDirs = linkedSetOf<File>()
        candidateSongs.forEach { song ->
            val songPath = getFilePathFromUri(song.uri) ?: return@forEach
            val songFile = File(songPath)
            val albumDir = songFile.parentFile ?: return@forEach

            candidateDirs.add(albumDir)
            albumDir.parentFile?.let { candidateDirs.add(it) }
            albumDir.parentFile?.parentFile?.let { candidateDirs.add(it) }
        }

        if (candidateDirs.isEmpty()) return null

        val preferredNames = listOf(
            "artist.jpg",
            "artist.jpeg",
            "artist.png",
            "artist.webp",
            "band.jpg",
            "band.jpeg",
            "band.png",
            "band.webp"
        )

        for (dir in candidateDirs) {
            val files = dir.listFiles() ?: continue
            if (files.isEmpty()) continue

            val byLowerName = files
                .filter { it.isFile }
                .associateBy { it.name.lowercase() }

            for (preferred in preferredNames) {
                val match = byLowerName[preferred] ?: continue
                return Uri.fromFile(match)
            }
        }

        return null
    }

    /**
     * Saves artist image to local storage
     */
    private fun saveLocalArtistImage(artistName: String, imageUrl: String) {
        try {
            val fileName = "${artistName}.jpg".replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val imageDir = File(context.filesDir, "artist_images")
            imageDir.mkdirs()

            val file = File(imageDir, fileName)
            URL(imageUrl).openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Saved artist image to local file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving artist image to local file: ${e.message}", e)
        }
    }
    
    /**
     * Clears all in-memory caches
     */
    fun clearInMemoryCaches() {
        try {
            synchronized(artistImageCache) {
                artistImageCache.clear()
            }
            synchronized(albumImageCache) {
                albumImageCache.clear()
            }
            synchronized(lyricsCache) {
                lyricsCache.clear()
            }
            // Also clear the song in-memory cache
            cachedSongs = null
            cacheTimestamp = 0L
            Log.d(TAG, "Cleared all in-memory caches (artist images, album images, lyrics, songs)")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing in-memory caches", e)
        }
    }

    private suspend fun invalidatePersistentLibraryCachesForForcedRescan() {
        try {
            roomDb.withTransaction {
                songDao.deleteAll()
                roomDb.songArtistDao().deleteAll()
                roomDb.artistDao().deleteAll()
            }
            genrePrefs.edit().clear().apply()
            libraryScanPrefs.edit().clear().apply()
            clearInMemoryCaches()
            Log.i(TAG, "Invalidated Room + metadata caches for forced full media rescan")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to invalidate persistent caches for forced media rescan", e)
        }
    }

    fun isLibraryStale(lastScan: Long, cachedCount: Int): Boolean {
        try {
            if (lastScan <= 0L) return true

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            // 1. Get the current count of eligible audio files in MediaStore to check for additions/deletions
            val selection = "(${MediaStore.Audio.Media.IS_MUSIC} = 1 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') AND ${MediaStore.Audio.Media.DURATION} > 10000"
            val countCursor = context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                selection,
                null,
                null
            )
            val mediaStoreCount = countCursor?.use { it.count } ?: 0
            
            // Compare the current MediaStore count with the MediaStore count at the time of the last scan
            val lastScanCount = libraryScanPrefs.getInt("last_scan_mediastore_count", -1)
            if (lastScanCount == -1) {
                // Fallback: if we do not have the stored count, check against cachedCount as a backup
                if (mediaStoreCount != cachedCount) {
                    Log.d(TAG, "Staleness check: MediaStore count ($mediaStoreCount) != cached count ($cachedCount) (no lastScanCount)")
                    return true
                }
            } else if (mediaStoreCount != lastScanCount) {
                Log.d(TAG, "Staleness check: MediaStore count ($mediaStoreCount) != last scan MediaStore count ($lastScanCount)")
                return true
            }

            // 2. Check if any file has been added or modified after the last scan
            // DATE_ADDED and DATE_MODIFIED in MediaStore are in SECONDS, so we divide lastScan by 1000.
            val lastScanSeconds = lastScan / 1000L
            val staleSelection = "$selection AND (${MediaStore.Audio.Media.DATE_ADDED} > ? OR ${MediaStore.Audio.Media.DATE_MODIFIED} > ?)"
            val staleCursor = context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                staleSelection,
                arrayOf(lastScanSeconds.toString(), lastScanSeconds.toString()),
                null
            )
            val hasNewerFiles = staleCursor?.use { it.count > 0 } ?: false
            if (hasNewerFiles) {
                Log.d(TAG, "Staleness check: newer files found after lastScan=$lastScan")
                return true
            }

            return false
        } catch (e: Exception) {
            Log.w(TAG, "Staleness check failed, assuming cache is valid", e)
            return false
        }
    }

    fun hasArtworkMatchingLossless(song: Song, lossless: Boolean): Boolean {
        val uri = song.artworkUri ?: return false
        if (!isEmbeddedArtworkCacheUri(uri)) return false
        val path = uri.path ?: return false
        val fileName = File(path).name
        val isLosslessFile = fileName.startsWith("embedded_art_lossless_")
        return File(path).exists() && isLosslessFile == lossless
    }

    private fun clearEmbeddedArtworkFileCaches() {
        try {
            val artworkCacheDir = File(context.cacheDir, "embedded_artwork")
            if (artworkCacheDir.exists()) {
                artworkCacheDir.deleteRecursively()
            }

            // Remove legacy cache files used by older versions.
            context.cacheDir.listFiles()?.forEach { file ->
                if (
                    file.isFile &&
                    (file.name.startsWith("embedded_art_") || file.name.startsWith("embedded_art_lossless_"))
                ) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear embedded artwork file caches", e)
        }
    }
    
    /**
     * Clears all song cache data from Room database.
     * Call this when the user explicitly clears cache from settings.
     */
    fun clearSongCacheData() {
        try {
            repositoryScope.launch {
                try {
                    roomDb.withTransaction {
                        songDao.deleteAll()
                        roomDb.songArtistDao().deleteAll()
                        roomDb.artistDao().deleteAll()
                    }
                    genrePrefs.edit().clear().apply()
                    artworkPrefs.edit().clear().apply()
                    libraryScanPrefs.edit().clear().apply()
                    clearEmbeddedArtworkFileCaches()
                    clearInMemoryCaches()
                    Log.d(TAG, "Cleared Room song/artist/link tables and metadata caches")
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing Room database", e)
                }
            }
            Log.d(TAG, "Scheduled full song cache clear (Room, metadata, in-memory)")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing song cache data", e)
        }
    }

    /**
     * Returns the number of songs stored in the Room database.
     */
    suspend fun getRoomSongCount(): Int = songDao.getCount()
    
    /**
     * Clears only the lyrics cache
     */
    fun clearLyricsCache() {
        try {
            synchronized(lyricsCache) {
                val count = lyricsCache.size
                lyricsCache.clear()
                Log.d(TAG, "===== CLEARED IN-MEMORY LYRICS CACHE ($count entries) =====")
            }
            
            // Also delete all saved local lyrics files
            try {
                val lyricsDir = File(context.filesDir, "lyrics")
                if (lyricsDir.exists() && lyricsDir.isDirectory) {
                    val files = lyricsDir.listFiles()
                    val deletedCount = files?.count { it.delete() } ?: 0
                    Log.d(TAG, "===== DELETED $deletedCount SAVED LYRICS FILES FROM DISK =====")
                } else {
                    Log.d(TAG, "===== NO LYRICS DIRECTORY FOUND ON DISK =====")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting saved lyrics files: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing lyrics cache", e)
        }
    }
    
    /**
     * Performs cache maintenance - removes expired entries and optimizes memory usage
     */
    suspend fun performCacheMaintenance() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting cache maintenance...")
            
            // Check if cache cleanup is needed based on app settings
            val maxCacheSize = try {
                val settings = chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context)
                settings.maxCacheSize.value
            } catch (e: Exception) {
                Log.w(TAG, "Error getting cache size setting, using default", e)
                1024L * 1024L * 1024L // Default 1GB
            }
            
            // Auto-trim file system cache if usage exceeds 90% of max
            val cacheManager = chromahub.rhythm.app.util.CacheManager
            val cacheCleanedUp = cacheManager.autoTrimCache(context, maxCacheSize)
            
            if (cacheCleanedUp) {
                Log.d(TAG, "File system cache was cleaned up")
            }
            
            // Optimize in-memory caches
            val initialArtistCacheSize = artistImageCache.size
            val initialAlbumCacheSize = albumImageCache.size
            val initialLyricsCacheSize = lyricsCache.size
            
            // The LinkedHashMap LRU implementation will automatically evict oldest entries
            // when new entries are added and the cache exceeds its limit
            
            Log.d(TAG, "Cache maintenance completed. " +
                    "Artist cache: $initialArtistCacheSize entries, " +
                    "Album cache: $initialAlbumCacheSize entries, " +
                    "Lyrics cache: $initialLyricsCacheSize entries")
                    
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache maintenance", e)
        }
    }
    
    /**
     * Gets the current size of in-memory caches
     * @return A map containing cache names and their sizes
     */
    fun getInMemoryCacheInfo(): Map<String, Int> {
        return mapOf(
            "artistImageCache" to artistImageCache.size,
            "albumImageCache" to albumImageCache.size,
            "lyricsCache" to lyricsCache.size
        )
    }
    
    /**
     * Finds the best matching Deezer artist from search results using fuzzy matching
     */
    private fun findBestMatch(artists: List<DeezerArtist>, originalName: String): DeezerArtist? {
        if (artists.isEmpty()) return null
        
        val lowerOriginal = originalName.lowercase().trim()
        
        // First, try exact match (case insensitive)
        artists.find { it.name.lowercase().trim() == lowerOriginal }?.let { return it }
        
        // Second, try starts with match
        artists.find { it.name.lowercase().trim().startsWith(lowerOriginal) }?.let { return it }
        
        // Third, try contains match
        artists.find { it.name.lowercase().contains(lowerOriginal) }?.let { return it }
        
        // Fourth, try reversed contains (original contains artist name)
        artists.find { lowerOriginal.contains(it.name.lowercase().trim()) }?.let { return it }
        
        // Fifth, try word-by-word matching
        val originalWords = lowerOriginal.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (originalWords.isNotEmpty()) {
            artists.find { artist ->
                val artistWords = artist.name.lowercase().split(Regex("\\s+"))
                originalWords.any { originalWord ->
                    artistWords.any { artistWord ->
                        originalWord == artistWord || 
                        originalWord.startsWith(artistWord) || 
                        artistWord.startsWith(originalWord)
                    }
                }
            }?.let { return it }
        }
        
        // Finally, return the first result with the most fans (most popular)
        return artists.maxByOrNull { it.nbFan }
    }
    
    /**
     * Finds the best matching Deezer album from search results using fuzzy matching
     */
    private fun findBestAlbumMatch(albums: List<DeezerAlbum>, originalTitle: String, originalArtist: String): DeezerAlbum? {
        if (albums.isEmpty()) return null

        val lowerTitle = originalTitle.lowercase().trim()
        val lowerArtist = originalArtist.lowercase().trim()

        // First, try exact match (case insensitive) for both title and artist
        albums.find { album ->
            album.title.lowercase().trim() == lowerTitle &&
            (album.artist?.name?.lowercase()?.trim() == lowerArtist || lowerArtist.contains(album.artist?.name?.lowercase()?.trim() ?: ""))
        }?.let { return it }

        // Second, try exact title match with fuzzy artist match
        albums.find { album ->
            album.title.lowercase().trim() == lowerTitle
        }?.let { return it }

        // Third, try contains match for title
        albums.find { album ->
            album.title.lowercase().contains(lowerTitle) || lowerTitle.contains(album.title.lowercase())
        }?.let { return it }

        // Fourth, try word-by-word matching for title
        val titleWords = lowerTitle.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (titleWords.isNotEmpty()) {
            albums.find { album ->
                val albumWords = album.title.lowercase().split(Regex("\\s+"))
                titleWords.any { titleWord ->
                    albumWords.any { albumWord ->
                        titleWord == albumWord ||
                        titleWord.startsWith(albumWord) ||
                        albumWord.startsWith(titleWord)
                    }
                }
            }?.let { return it }
        }

        // Finally, return the first result with the most tracks (most complete album)
        return albums.maxByOrNull { it.nbTracks }
    }

    /**
     * Detects genres for songs in background after initial app load
     * This method processes songs in batches to avoid blocking the UI
     * @param songs List of songs to detect genres for
     * @param onProgress Callback to report progress (current, total)
     * @param onComplete Callback when genre detection is complete
     */
    suspend fun detectGenresInBackground(
        songs: List<Song>,
        onProgress: ((Int, Int) -> Unit)? = null,
        onComplete: ((List<Song>) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val songsWithoutGenres = songs.filter { 
                it.genre == null || it.genre.isBlank() || it.genre.equals("unknown", ignoreCase = true) 
            }
            
            if (songsWithoutGenres.isEmpty()) {
                Log.d(TAG, "All songs already have genres, skipping background detection")
                onComplete?.invoke(songs)
                return@withContext
            }

            Log.d(TAG, "Starting background genre detection for ${songsWithoutGenres.size} songs out of ${songs.size} total")
            val updatedSongs = mutableListOf<Song>()
            val batchSize = 50 // Process in smaller batches for better responsiveness
            var processedCount = 0

            songsWithoutGenres.chunked(batchSize).forEach { batch ->
                val batchStartTime = System.currentTimeMillis()

                batch.forEach { song ->
                    try {
                        val songId = song.id.toLongOrNull()
                        if (songId == null) {
                            Log.w(TAG, "Invalid song ID for ${song.title}, skipping")
                            updatedSongs.add(song)
                            processedCount++
                            onProgress?.invoke(processedCount, songsWithoutGenres.size)
                            return@forEach
                        }
                        
                        val contentUri = song.uri
                        val genre = getGenreForSong(context, contentUri, songId.toInt())

                        val cacheKey = "genre_$songId"
                        val existingCachedGenre = try {
                            genrePrefs.getString(cacheKey, null)
                                ?.trim()
                                ?.takeIf {
                                    it.isNotBlank() &&
                                        !it.equals("unknown", ignoreCase = true) &&
                                        it != "-"
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to read existing genre cache for song ID $songId", e)
                            null
                        }

                        if (existingCachedGenre != null) {
                            // Keep previously cached valid genre (e.g., user-edited) and avoid overwriting it
                            val updatedSong = song.copy(genre = existingCachedGenre)
                            updatedSongs.add(updatedSong)
                            Log.d(TAG, "Keeping existing cached genre '$existingCachedGenre' for song ID $songId")
                        } else if (genre != null && genre.isNotBlank() && !genre.equals("unknown", ignoreCase = true)) {
                            val updatedSong = song.copy(genre = genre)
                            updatedSongs.add(updatedSong)
                            // Cache the detected genre using async apply() to prevent blocking
                            try {
                                genrePrefs.edit().putString(cacheKey, genre).apply()
                                Log.d(TAG, "Detected and cached genre '$genre' for song ID $songId: ${song.title}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to cache genre for song ID $songId", e)
                            }
                        } else {
                            // Cache sentinel genre "-" to avoid infinite retries on startups
                            Log.d(TAG, "No genre found for $songId, caching minus sign sentinel")
                            val updatedSong = song.copy(genre = "-")
                            updatedSongs.add(updatedSong)
                            try {
                                genrePrefs.edit().putString(cacheKey, "-").apply()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to cache negative genre sentinel", e)
                            }
                        }

                        processedCount++
                        onProgress?.invoke(processedCount, songsWithoutGenres.size)

                    } catch (e: Exception) {
                        Log.w(TAG, "Error detecting genre for song ${song.title}", e)
                        updatedSongs.add(song) // Keep original song on error
                        processedCount++
                        onProgress?.invoke(processedCount, songsWithoutGenres.size)
                    }
                }

                val batchEndTime = System.currentTimeMillis()
                val batchDuration = batchEndTime - batchStartTime
                Log.d(TAG, "Processed batch of ${batch.size} songs in ${batchDuration}ms")

                // Yield control to allow other coroutines to run
                yield()

                // Small delay between batches to prevent overwhelming the system
                if (batchDuration < 100) { // If batch processed quickly, add a small delay
                    delay(50)
                }
            }

            val finalSongs = songs.map { originalSong ->
                updatedSongs.find { it.id == originalSong.id } ?: originalSong
            }

            val genreCount = finalSongs.count { song -> 
                song.genre != null && song.genre.isNotBlank() && !song.genre.equals("unknown", ignoreCase = true)
            }
            Log.d(TAG, "Background genre detection complete. $genreCount out of ${finalSongs.size} songs now have genres")
            onComplete?.invoke(finalSongs)
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during genre detection", e)
            // Always invoke onComplete to prevent hanging UI
            onComplete?.invoke(songs)
        }
    }
    
    /**
     * Extracts audio metadata (bitrate, sample rate, channels, codec) for songs in background
     * This is done lazily to avoid slowing down initial library load
     */
    suspend fun extractAudioMetadataInBackground(
        songs: List<Song>,
        onProgress: ((Int, Int) -> Unit)? = null,
        onComplete: ((List<Song>) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val songsWithoutMetadata = songs.filter { 
            it.bitrate == null || it.sampleRate == null || it.channels == null || it.codec == null 
        }
        
        Log.d(TAG, "Songs total: ${songs.size}, songs without metadata: ${songsWithoutMetadata.size}")
        if (songsWithoutMetadata.isNotEmpty()) {
            Log.d(TAG, "Sample songs without metadata: ${songsWithoutMetadata.take(3).map { "${it.title} (bitrate=${it.bitrate}, sampleRate=${it.sampleRate}, channels=${it.channels}, codec=${it.codec})" }}")
        }
        
        if (songsWithoutMetadata.isEmpty()) {
            Log.d(TAG, "All songs already have audio metadata, skipping background extraction")
            onComplete?.invoke(songs)
            return@withContext
        }

        Log.d(TAG, "Starting background audio metadata extraction for ${songsWithoutMetadata.size} songs")
        val updatedSongs = mutableListOf<Song>()
        val batchSize = 10 // Keep media-service bursts short on large libraries
        var processedCount = 0

        songsWithoutMetadata.chunked(batchSize).forEach { batch ->
            val batchStartTime = System.currentTimeMillis()

            batch.forEach { song ->
                try {
                    // Use AudioFormatDetector for more accurate metadata extraction
                    val formatInfo = AudioFormatDetector.detectFormat(context, song.uri, song)
                    
                    val updatedSong = song.copy(
                        bitrate = if (formatInfo.bitrateKbps > 0) formatInfo.bitrateKbps * 1000 else -1,
                        sampleRate = if (formatInfo.sampleRateHz > 0) formatInfo.sampleRateHz else -1,
                        channels = if (formatInfo.channelCount > 0) formatInfo.channelCount else -1,
                        codec = if (formatInfo.codec != "Unknown") formatInfo.codec else "-"
                    )
                    updatedSongs.add(updatedSong)
                    
                    processedCount++
                    onProgress?.invoke(processedCount, songsWithoutMetadata.size)

                } catch (e: Exception) {
                    Log.w(TAG, "Error extracting audio metadata for song ${song.title}", e)
                    // Save sentinels so we don't infinitely retry failed songs in the background on next startup
                    val failedSong = song.copy(bitrate = -1, sampleRate = -1, channels = -1, codec = "-")
                    updatedSongs.add(failedSong) // Assign sentinels
                    processedCount++
                    onProgress?.invoke(processedCount, songsWithoutMetadata.size)
                }
            }

            val batchEndTime = System.currentTimeMillis()
            val batchDuration = batchEndTime - batchStartTime
            Log.d(TAG, "Processed metadata batch of ${batch.size} songs in ${batchDuration}ms")

            // Yield control to allow other coroutines to run
            yield()

            // Always give foreground work a scheduling window. Previously the slowest batches
            // received no cooldown, exactly when media-service contention was already highest.
            delay(150)
        }

        val updatedSongsById = updatedSongs.associateBy { it.id }
        val finalSongs = songs.map { originalSong ->
            updatedSongsById[originalSong.id] ?: originalSong
        }

        Log.d(TAG, "Background audio metadata extraction complete. Updated ${updatedSongs.size} songs")
        onComplete?.invoke(finalSongs)
    }
    
    /**
     * Cleanup method to clear caches and cancel coroutines
     * Call this when the repository is no longer needed
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up MusicRepository...")
        
        // Cancel all coroutines
        repositoryScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        
        // Clear all caches
        artistImageCache.clear()
        albumImageCache.clear()
        lyricsCache.clear()
        
        // Clear rate limiting maps
        lastApiCalls.clear()
        apiCallCounts.clear()
        
        Log.d(TAG, "MusicRepository cleaned up")
    }
    
    /**
     * Get all songs for a specific album by album ID.
     * This retrieves the songs list from the songs flow and filters by album ID.
     */
    suspend fun getSongsForAlbum(albumId: String): List<PlayableItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val allAlbums = loadAlbums()

            Log.d(TAG, "Getting songs for album ID: $albumId")

            // Find the album by ID
            val album = allAlbums.find { it.id == albumId }
            if (album == null) {
                Log.e(TAG, "Album not found with ID: $albumId")
                return@withContext emptyList()
            }

            Log.d(TAG, "Found album: ${album.title} (ID: $albumId)")

            val albumSongs = album.songs

            Log.d(TAG, "Found ${albumSongs.size} songs for album: ${album.title}")

            return@withContext albumSongs.map { song ->
                object : PlayableItem {
                    override val id: String = song.id
                    override val title: String = song.title
                    override val artist: String = song.artist
                    override val album: String = song.album
                    override val duration: Long = song.duration
                    override val artworkUri: String? = song.artworkUri?.toString()

                    override fun getPlaybackUri(): String = song.uri.toString()

                    override val sourceType: SourceType = SourceType.LOCAL
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting songs for album $albumId", e)
            emptyList()
        }
    }

    /**
     * Local method to get songs for album that returns Song objects (not PlayableItem)
     * This is used by the local MusicViewModel which works with Song objects directly
     */
    suspend fun getSongsForAlbumLocal(albumId: String): List<Song> = withContext(Dispatchers.IO) {
        return@withContext try {
            val allAlbums = loadAlbums()

            Log.d(TAG, "Getting songs for album ID (local): $albumId")

            // Find the album by ID
            val album = allAlbums.find { it.id == albumId }
            if (album == null) {
                Log.e(TAG, "Album not found with ID: $albumId")
                return@withContext emptyList()
            }

            Log.d(TAG, "Found album: ${album.title} (ID: $albumId)")

            val albumSongs = album.songs

            Log.d(TAG, "Found ${albumSongs.size} songs for album: ${album.title}")

            return@withContext albumSongs
        } catch (e: Exception) {
            Log.e(TAG, "Error getting songs for album $albumId", e)
            emptyList()
        }
    }

    private val allowedCoverExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    fun findBestCover(songFolder: File): File? {
        var bestScore = 0
        var bestFile: File? = null
        try {
            val files = songFolder.listFiles() ?: return null
            for (file in files) {
                if (file.extension.lowercase() !in allowedCoverExtensions) continue
                var score = 1
                when (file.extension.lowercase()) {
                    "jpg" -> score += 3
                    "png" -> score += 2
                    "jpeg" -> score += 1
                }
                val nameLower = file.nameWithoutExtension.lowercase()
                if (nameLower == "albumart") score += 24
                else if (nameLower == "cover") score += 20
                else if (nameLower.startsWith("albumart")) score += 16
                else if (nameLower.startsWith("cover")) score += 12
                else if (nameLower.contains("albumart")) score += 8
                else if (nameLower.contains("cover")) score += 4
                if (bestScore < score) {
                    bestScore = score
                    bestFile = file
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for cover art", e)
        }
        if (bestScore >= 3) return bestFile
        return null
    }

    suspend fun buildFolderTree(songs: List<Song>): FolderNode = withContext(Dispatchers.IO) {
        val rootMap = mutableMapOf<String, FolderTreeBuilderNode>()

        for (song in songs) {
            val filePath = song.path ?: continue
            val file = File(filePath)
            val parentPath = file.parent ?: continue
            val segments = parentPath.replace("\\", "/")
                .trim('/')
                .split('/')
                .filter { it.isNotBlank() }

            var currentLevel = rootMap
            var currentPath = ""
            for (i in segments.indices) {
                val segment = segments[i]
                currentPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
                val node = currentLevel.getOrPut(segment) {
                    FolderTreeBuilderNode(segment, currentPath)
                }
                if (i == segments.lastIndex) {
                    node.songList.add(song)
                    val trackAlbumId = song.albumId.toLongOrNull()
                    if (node.albumId == null && node.songList.size == 1) {
                        node.albumId = trackAlbumId
                    } else if (trackAlbumId != null && trackAlbumId != node.albumId) {
                        node.albumId = null
                    }
                }
                currentLevel = node.children
            }
        }

        fun buildNode(name: String, builderNode: FolderTreeBuilderNode): FolderNode {
            val sortedSongs = builderNode.songList.sortedBy { it.trackNumber }
            val coverUri = findBestCover(File(builderNode.path))?.let { Uri.fromFile(it) }
            val subFolders = builderNode.children.map { (childName, childNode) ->
                childName to buildNode(childName, childNode)
            }.toMap()

            val totalCount = sortedSongs.size + subFolders.values.sumOf { it.totalSongCount }

            return FolderNode(
                name = name,
                path = builderNode.path,
                subFolders = subFolders,
                songs = sortedSongs,
                albumId = builderNode.albumId,
                coverUri = coverUri,
                totalSongCount = totalCount
            )
        }

        val rootSubFolders = rootMap.map { (name, node) -> name to buildNode(name, node) }.toMap()
        FolderNode(
            name = "",
            path = "/",
            subFolders = rootSubFolders,
            songs = emptyList(),
            totalSongCount = rootSubFolders.values.sumOf { it.totalSongCount }
        )
    }

    private class FolderTreeBuilderNode(
        val name: String,
        val path: String
    ) {
        val children = mutableMapOf<String, FolderTreeBuilderNode>()
        val songList = mutableListOf<Song>()
        var albumId: Long? = null
    }
}
