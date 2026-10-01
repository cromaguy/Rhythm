/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.provider

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import chromahub.rhythm.app.shared.data.model.LyricsData

class SubsonicErrorException(val code: Int, message: String) : Exception(message)

/**
 * Subsonic-compatible API client used for Navidrome/Subsonic service support.
 */
class SubsonicApiClient internal constructor(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val libraryFetchRetryDelayMs: Long
) {

    constructor(context: Context) : this(context, buildHttpClient(), LIBRARY_FETCH_RETRY_DELAY_MS)

    private data class Credentials(
        val serverUrl: String,
        val username: String,
        val password: String
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var credentials: Credentials? = loadCredentials()

    @Volatile
    private var usePasswordAuth: Boolean = prefs.getBoolean(KEY_USE_PASSWORD_AUTH, false)

    fun isConnected(): Boolean = credentials?.let { it.serverUrl.isNotBlank() && it.username.isNotBlank() && it.password.isNotBlank() } == true

    fun getServerUrl(): String = credentials?.serverUrl.orEmpty()

    fun getUsername(): String = credentials?.username.orEmpty()

    suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        saveCredentials: Boolean = true
    ): Result<ProviderConnectionResult> {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        val validationError = validateServerUrl(normalizedUrl)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Username is required"))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password is required"))
        }

        credentials = Credentials(
            serverUrl = normalizedUrl,
            username = username.trim(),
            password = password
        )

        return ping().map {
            if (saveCredentials) {
                prefs.edit {
    putString(KEY_SERVER_URL, normalizedUrl)
    putString(KEY_USERNAME, username.trim())
    putString(KEY_PASSWORD, password)
    putBoolean(KEY_USE_PASSWORD_AUTH, usePasswordAuth)
}
            } else {
                prefs.edit { clear() }
            }
            ProviderConnectionResult(displayName = username.trim(), serverUrl = normalizedUrl)
        }.onFailure {
            credentials = null
        }
    }

    fun logout() {
        credentials = null
        stableCoverArtAuth = null
        usePasswordAuth = false
        prefs.edit { clear() }
    }

    suspend fun ping(): Result<Boolean> {
        return requestAndParse("ping").map { true }
    }

    suspend fun searchSongs(query: String, limit: Int = 30): Result<List<ProviderSong>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        val params = mapOf(
            "query" to query,
            "artistCount" to "0",
            "albumCount" to "0",
            "songCount" to limit.coerceIn(1, 100).toString()
        )

        return requestAndParse("search3", params) { response ->
            parseSongList(response.optJSONObject("searchResult3")?.opt("song"))
        }
    }

    suspend fun searchAlbums(query: String, limit: Int = 30): Result<List<ProviderAlbum>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        val params = mapOf(
            "query" to query,
            "artistCount" to "0",
            "songCount" to "0",
            "albumCount" to limit.coerceIn(1, 100).toString()
        )

        return requestAndParse("search3", params) { response ->
            parseAlbumListCompat(response.optJSONObject("searchResult3")?.opt("album"))
        }
    }

    suspend fun getArtists(): Result<List<ProviderArtist>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        return requestAndParse("getArtists", emptyMap()) { response ->
            val artistsObj = response.optJSONObject("artists")
            val indexElement = artistsObj?.opt("index")
            val result = mutableListOf<ProviderArtist>()
            if (indexElement is org.json.JSONArray) {
                for (i in 0 until indexElement.length()) {
                    val indexObj = indexElement.optJSONObject(i)
                    val artistArray = indexObj?.opt("artist")
                    if (artistArray != null) {
                        result.addAll(parseArtistListCompat(artistArray))
                    }
                }
            } else if (indexElement is JSONObject) {
                val artistArray = indexElement.opt("artist")
                if (artistArray != null) {
                    result.addAll(parseArtistListCompat(artistArray))
                }
            }
            result
        }
    }

    suspend fun searchArtists(query: String, limit: Int = 30): Result<List<ProviderArtist>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        val params = mapOf(
            "query" to query,
            "albumCount" to "0",
            "songCount" to "0",
            "artistCount" to limit.coerceIn(1, 100).toString()
        )

        return requestAndParse("search3", params) { response ->
            parseArtistListCompat(response.optJSONObject("searchResult3")?.opt("artist"))
        }
    }

    suspend fun getSimilarTracks(songId: String, limit: Int = 20): Result<List<ProviderSong>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }
        if (songId.isBlank()) {
            return Result.failure(IllegalArgumentException("Song id is required"))
        }

        val params = mapOf(
            "id" to songId,
            "count" to limit.coerceIn(1, 100).toString()
        )

        return requestAndParse("getSimilarSongs2", params) { response ->
            parseSongList(response.optJSONObject("similarSongs2")?.opt("song") ?: response.optJSONObject("similarSongs")?.opt("song"))
        }
    }

    suspend fun getSimilarArtists(artistId: String, limit: Int = 10): Result<List<ProviderArtist>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }
        if (artistId.isBlank()) {
            return Result.failure(IllegalArgumentException("Artist id is required"))
        }

        val params = mapOf(
            "id" to artistId,
            "count" to limit.coerceIn(1, 100).toString()
        )

        return requestAndParse("getArtistInfo2", params) { response ->
            parseArtistId3List(response.optJSONObject("artistInfo2")?.optJSONArray("similarArtist"))
        }
    }

    suspend fun getRandomSongs(limit: Int = 50): Result<List<ProviderSong>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        val params = mapOf(
            "size" to limit.coerceIn(1, 500).toString()
        )

        return requestAndParse("getRandomSongs", params) { response ->
            parseSongList(response.optJSONArray("randomSongs") ?: response.optJSONObject("randomSongs")?.opt("song"))
        }
    }

    suspend fun getAlbumList(type: String = "newest", limit: Int = 50): Result<List<ProviderAlbum>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        val params = mapOf(
            "type" to type,
            "size" to limit.coerceIn(1, 500).toString()
        )

        return requestAndParse("getAlbumList2", params) { response ->
            parseAlbumListCompat(response.optJSONObject("albumList2")?.opt("album") ?: response.optJSONObject("albumList")?.opt("album"))
        }
    }

    /**
     * @param onIncomplete called (possibly from several coroutines) when an album page or an
     *        album could not be fetched and was skipped, i.e. the result is not the full library.
     * @param startAlbumOffset getAlbumList2 offset to start from, to continue an interrupted fetch.
     * @param onPageFetched called after each album page with the songs it added and the offset
     *        of the next page, so the caller can checkpoint progress.
     */
    suspend fun fetchLibrarySongs(
        limit: Int = 5_000,
        onProgress: ((current: Int, total: Int, songsCount: Int) -> Unit)? = null,
        onIncomplete: (() -> Unit)? = null,
        startAlbumOffset: Int = 0,
        onPageFetched: (suspend (pageSongs: List<ProviderSong>, nextAlbumOffset: Int) -> Unit)? = null
    ): Result<List<ProviderSong>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val albumBatchSize = 100
                var albumOffset = startAlbumOffset
                val songs = LinkedHashMap<String, ProviderSong>()
                val semaphore = Semaphore(LIBRARY_FETCH_CONCURRENCY)
                val skippedAlbums = AtomicInteger(0)
                var totalAlbumsProcessed = startAlbumOffset

                while (songs.size < limit) {
                    val albumResult = requestAndParseWithRetry(
                        "getAlbumList2",
                        mapOf(
                            "type" to "alphabeticalByArtist",
                            "size" to albumBatchSize.toString(),
                            "offset" to albumOffset.toString()
                        )
                    )
                    if (albumResult.isFailure) onIncomplete?.invoke()
                    val responseObj = albumResult.getOrNull()
                    val albumList = responseObj?.optJSONObject("albumList2") ?: responseObj?.optJSONObject("albumList")
                    val albums = parseAlbumListCompat(albumList?.opt("album"))
                    if (albums.isEmpty()) break

                    val pageSongs = ArrayList<ProviderSong>()
                    coroutineScope {
                        val albumTasks = albums.map { album ->
                            async {
                                val albumId = album.providerId
                                if (albumId.isBlank()) return@async emptyList<ProviderSong>()
                                semaphore.withPermit {
                                    try {
                                        val albumResponse = requestAndParseWithRetry("getAlbum", mapOf("id" to albumId)).getOrNull()
                                            ?.optJSONObject("album")
                                        if (albumResponse == null) {
                                            skippedAlbums.incrementAndGet()
                                            onIncomplete?.invoke()
                                            return@withPermit emptyList()
                                        }
                                        parseSongList(albumResponse.opt("song"))
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to fetch album $albumId, skipping", e)
                                        skippedAlbums.incrementAndGet()
                                        onIncomplete?.invoke()
                                        emptyList()
                                    }
                                }
                            }
                        }

                        for (task in albumTasks) {
                            val albumSongs = task.await()
                            for (song in albumSongs) {
                                if (songs.putIfAbsent(song.providerId, song) == null) pageSongs.add(song)
                                if (songs.size >= limit) break
                            }
                            totalAlbumsProcessed++
                            onProgress?.invoke(totalAlbumsProcessed, totalAlbumsProcessed + albums.size, songs.size)
                            if (songs.size >= limit) break
                        }
                    }

                    albumOffset += albums.size
                    onPageFetched?.invoke(pageSongs, albumOffset)
                    if (albums.size < albumBatchSize) break
                }

                if (skippedAlbums.get() > 0) {
                    // Keep what was fetched: a library missing a few albums beats no library.
                    Log.w(TAG, "Library fetch skipped ${skippedAlbums.get()} album(s) that failed after retries")
                }
                Result.success(songs.values.take(limit).toList())
            } catch (e: Exception) {
                Log.e(TAG, "Subsonic library fetch failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Server-side library change state. [lastModified] is the `getIndexes` `lastModified` value
     * (Navidrome: start time of the last scan, in ms); [scanning] is true while a scan runs
     * (`getScanStatus`), when the library is in flux.
     */
    data class LibraryChangeState(val lastModified: Long, val scanning: Boolean) {
        /**
         * Marker for a catalog fetched now by the account [accountKey], or null if this state
         * cannot vouch for it (no `lastModified` reported, or a scan is running). The library
         * did not change between two fetches with equal non-null markers.
         */
        fun catalogMarker(accountKey: String?): String? {
            if (accountKey == null || scanning || lastModified <= 0L) return null
            return "$accountKey:$lastModified"
        }
    }

    /**
     * Marker for the server library as seen by the current account right now (see
     * [LibraryChangeState.catalogMarker]), or null if it cannot be determined.
     * Costs two small requests regardless of library size.
     */
    suspend fun getLibraryMarker(): String? {
        if (!isConnected()) return null
        // A far-future ifModifiedSince makes the server omit the artist index and return
        // only `lastModified`, so this stays a tiny request even for huge libraries.
        val farFuture = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000
        val indexes = requestAndParse("getIndexes", mapOf("ifModifiedSince" to farFuture.toString()))
            .getOrNull()
            ?.optJSONObject("indexes")
            ?: return null
        val scanning = requestAndParse("getScanStatus").getOrNull()
            ?.optJSONObject("scanStatus")
            ?.optBoolean("scanning", false) == true
        return LibraryChangeState(indexes.optLong("lastModified", 0L), scanning)
            .catalogMarker(catalogAccountKey())
    }

    /** Provider ids of the user's starred songs (one `getStarred2` request). */
    suspend fun getStarredSongIds(): Result<Set<String>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }
        return requestAndParse("getStarred2").map { response ->
            parseSongList(response.optJSONObject("starred2")?.opt("song"))
                .mapTo(HashSet()) { it.providerId }
        }
    }

    /**
     * Stable key for the current server + account + auth mode, so a cached catalog is only
     * trusted for the account that fetched it. Contains no password material.
     */
    private fun catalogAccountKey(): String? {
        val cred = credentials ?: return null
        val material = "${cred.serverUrl}\n${cred.username}\n$usePasswordAuth"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * The server's `getIndexes` `lastModified` (Navidrome: start time of the last scan), or null
     * if it is unknown. A far-future `ifModifiedSince` makes the server omit the artist index,
     * so this stays a tiny request even for huge libraries.
     */
    suspend fun getLibraryLastModified(): Long? {
        if (!isConnected()) return null
        val farFuture = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000
        return requestAndParse("getIndexes", mapOf("ifModifiedSince" to farFuture.toString()))
            .getOrNull()
            ?.optJSONObject("indexes")
            ?.optLong("lastModified", 0L)
            ?.takeIf { it > 0L }
    }

    suspend fun getPlaylists(limit: Int = 100): Result<List<ProviderPlaylist>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        return requestAndParse("getPlaylists", emptyMap()) { response ->
            val playlistsObj = response.optJSONObject("playlists")
            parsePlaylistList(playlistsObj?.opt("playlist") as? org.json.JSONArray ?: playlistsObj?.optJSONArray("playlist"), limit)
        }
    }

    suspend fun searchPlaylists(query: String, limit: Int = 30): Result<List<ProviderPlaylist>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        return getPlaylists(limit = 100).map { playlists ->
            playlists.filter {
                it.name.contains(query, ignoreCase = true) ||
                    (it.description?.contains(query, ignoreCase = true) == true)
            }.take(limit)
        }
    }

    suspend fun getPlaylistSongs(playlistId: String, limit: Int = 500): Result<List<ProviderSong>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }
        if (playlistId.isBlank()) {
            return Result.failure(IllegalArgumentException("Playlist id is required"))
        }

        return requestAndParse("getPlaylist", mapOf("id" to playlistId)) { response ->
            val entries = response.optJSONObject("playlist")?.opt("entry")
            parseSongList(entries).take(limit)
        }
    }

    suspend fun getAlbumSongs(albumId: String, limit: Int = 500): Result<List<ProviderSong>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }
        if (albumId.isBlank()) {
            return Result.failure(IllegalArgumentException("Album id is required"))
        }

        return requestAndParse("getAlbum", mapOf("id" to albumId)) { response ->
            parseSongList(response.optJSONObject("album")?.opt("song")).take(limit)
        }
    }

    suspend fun getAlbumById(albumId: String): Result<ProviderAlbum> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }
        if (albumId.isBlank()) {
            return Result.failure(IllegalArgumentException("Album id is required"))
        }

        return requestAndParse("getAlbum", mapOf("id" to albumId)) { response ->
            val albumJson = response.optJSONObject("album")
                ?: throw IllegalStateException("Album not found for id=$albumId")
            parseAlbumItem(albumJson)
        }
    }

    suspend fun getArtistTopTracks(artistQuery: String, limit: Int = 20): Result<List<ProviderSong>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        return searchSongs(artistQuery, limit)
    }

    suspend fun getArtistAlbums(artistQuery: String, limit: Int = 50): Result<List<ProviderAlbum>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        return searchArtists(artistQuery, limit = 20).map { artists ->
            val artist = artists.firstOrNull { it.name.equals(artistQuery, ignoreCase = true) } ?: artists.firstOrNull()
                ?: return@map emptyList()

            requestAndParse("getArtist", mapOf("id" to artist.providerId)).getOrNull()
                ?.optJSONObject("artist")
                ?.opt("album")
                ?.let { parseAlbumListCompat(it).take(limit) }
                .orEmpty()
        }
    }

    suspend fun getRelatedArtists(artistId: String, limit: Int = 10): Result<List<ProviderArtist>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }
        if (artistId.isBlank()) {
            return Result.failure(IllegalArgumentException("Artist id is required"))
        }

        val params = mapOf(
            "id" to artistId,
            "count" to limit.coerceIn(1, 100).toString()
        )

        return requestAndParse("getArtistInfo2", params) { response ->
            parseArtistId3List(response.optJSONObject("artistInfo2")?.optJSONArray("similarArtist"))
        }
    }

    suspend fun getRelatedTracks(songId: String, limit: Int = 20): Result<List<ProviderSong>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }
        if (songId.isBlank()) {
            return Result.failure(IllegalArgumentException("Song id is required"))
        }

        val params = mapOf(
            "id" to songId,
            "count" to limit.coerceIn(1, 100).toString()
        )

        return requestAndParse("getSimilarSongs2", params) { response ->
            parseSongList(response.optJSONObject("similarSongs2")?.opt("song") ?: response.optJSONObject("similarSongs")?.opt("song"))
        }
    }

    /** The user's starred songs, complete, in one `getStarred2` request. */
    suspend fun getStarredSongs(): Result<List<ProviderSong>> {
        if (!isConnected()) return Result.failure(IllegalStateException("Subsonic service is not connected"))

        return requestAndParse("getStarred2").map { response ->
            parseSongList(response.optJSONObject("starred2")?.opt("song"))
        }
    }

    suspend fun markFavorite(id: String, isFavorite: Boolean): Result<Boolean> {
        if (!isConnected()) return Result.failure(IllegalStateException("Subsonic service is not connected"))
        if (id.isBlank()) return Result.failure(IllegalArgumentException("Id is required"))

        val endpoint = if (isFavorite) "star" else "unstar"
        return requestAndParse(endpoint, mapOf("id" to id)).map { true }
    }

    suspend fun scrobble(id: String, submission: Boolean): Result<Boolean> {
        if (!isConnected()) return Result.failure(IllegalStateException("Subsonic service is not connected"))
        if (id.isBlank()) return Result.failure(IllegalArgumentException("Id is required"))

        return requestAndParse(
            "scrobble", 
            mapOf("id" to id, "submission" to submission.toString(), "time" to System.currentTimeMillis().toString())
        ).map { true }
    }

    suspend fun reportPlaybackProgress(id: String, positionMs: Long, isPaused: Boolean): Result<Boolean> {
        if (!isConnected()) return Result.failure(IllegalStateException("Subsonic service is not connected"))
        if (id.isBlank()) return Result.failure(IllegalArgumentException("Id is required"))

        val state = if (isPaused) "pause" else "progress"
        val openSubsonicResult = requestAndParse(
            "reportPlayback",
            mapOf(
                "mediaId" to id,
                "mediaType" to "song",
                "positionMs" to positionMs.toString(),
                "state" to state
            )
        )
        if (openSubsonicResult.isSuccess) return Result.success(true)

        return scrobble(id, submission = false)
    }

    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Result<ProviderPlaylist> {
        if (!isConnected()) return Result.failure(IllegalStateException("Subsonic service is not connected"))
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Playlist name is required"))

        return requestAndParse(
            "createPlaylist", 
            mapOf("name" to name),
            mapOf("songId" to songIds)
        ).map { response ->
            val playlist = response.optJSONObject("playlist")
            ProviderPlaylist(
                providerId = playlist?.optString("id", "") ?: "",
                name = playlist?.optString("name", name) ?: name,
                description = playlist?.optString("comment")?.takeIf { it.isNotBlank() },
                artworkUrl = playlist?.optString("coverArt")?.takeIf { it.isNotBlank() }?.let { buildCoverArtUrl(it, 500) },
                songCount = playlist?.optInt("songCount", songIds.size) ?: songIds.size,
                owner = playlist?.optString("owner")?.takeIf { it.isNotBlank() } ?: credentials?.username,
                isPublic = playlist?.optBoolean("public", true) ?: true
            )
        }
    }

    suspend fun updatePlaylist(
        playlistId: String,
        name: String? = null,
        songIdsToAdd: List<String> = emptyList(),
        songIndexesToRemove: List<Int> = emptyList()
    ): Result<Boolean> {
        if (!isConnected()) return Result.failure(IllegalStateException("Subsonic service is not connected"))
        if (playlistId.isBlank()) return Result.failure(IllegalArgumentException("Playlist id is required"))

        val params = buildMap {
            put("playlistId", playlistId)
            if (!name.isNullOrBlank()) put("name", name)
        }
        val listParams = buildMap {
            if (songIdsToAdd.isNotEmpty()) put("songIdToAdd", songIdsToAdd)
            if (songIndexesToRemove.isNotEmpty()) put("songIndexToRemove", songIndexesToRemove.map { it.toString() })
        }

        return requestAndParse(
            "updatePlaylist",
            params,
            listParams
        ).map { true }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Boolean> {
        if (!isConnected()) return Result.failure(IllegalStateException("Subsonic service is not connected"))
        if (playlistId.isBlank()) return Result.failure(IllegalArgumentException("Playlist id is required"))

        return requestAndParse("deletePlaylist", mapOf("id" to playlistId)).map { true }
    }

    /**
     * Fetches lyrics for a song from Subsonic/Navidrome.
     * First attempts OpenSubsonic getLyricsBySongId (supports synced structured lyrics).
     * If unavailable, falls back to legacy getLyrics(artist, title).
     */
    suspend fun getLyrics(
        songId: String,
        artist: String? = null,
        title: String? = null
    ): Result<LyricsData?> {
        if (!isConnected()) return Result.failure(IllegalStateException("Subsonic service is not connected"))
        if (songId.isBlank()) return Result.failure(IllegalArgumentException("Song id is required"))

        return withContext(Dispatchers.IO) {
            // 1. Try OpenSubsonic getLyricsBySongId
            val openSubsonicResult = requestAndParse("getLyricsBySongId", mapOf("id" to songId))
            if (openSubsonicResult.isSuccess) {
                val response = openSubsonicResult.getOrThrow()
                val lyricsList = response.optJSONObject("lyricsList")
                val structuredLyricsObj = lyricsList?.opt("structuredLyrics")
                val structuredLyricsList: List<JSONObject> = when (structuredLyricsObj) {
                    null -> emptyList()
                    is JSONArray -> (0 until structuredLyricsObj.length()).mapNotNull { structuredLyricsObj.optJSONObject(it) }
                    is JSONObject -> listOf(structuredLyricsObj)
                    else -> emptyList()
                }

                if (structuredLyricsList.isNotEmpty()) {
                    // Prefer synced lyrics if available
                    val targetLyrics = structuredLyricsList.firstOrNull { it.optBoolean("synced", false) }
                        ?: structuredLyricsList.first()

                    val isSynced = targetLyrics.optBoolean("synced", false)
                    val offset = targetLyrics.optLong("offset", 0L)
                    val linesObj = targetLyrics.opt("line")
                    val linesList: List<JSONObject> = when (linesObj) {
                        null -> emptyList()
                        is JSONArray -> (0 until linesObj.length()).mapNotNull { linesObj.optJSONObject(it) }
                        is JSONObject -> listOf(linesObj)
                        else -> emptyList()
                    }

                    if (linesList.isNotEmpty()) {
                        val plainLines = mutableListOf<String>()
                        val syncedLines = mutableListOf<String>()

                        for (lineObj in linesList) {
                            val text = lineObj.optString("value", "")
                            plainLines.add(text)
                            if (isSynced) {
                                val startMs = (lineObj.optLong("start", 0L) + offset).coerceAtLeast(0L)
                                syncedLines.add("${formatLrcTimestamp(startMs)}$text")
                            }
                        }

                        val plainLyrics = plainLines.joinToString("\n").takeIf { it.isNotBlank() }
                        val syncedLyrics = if (isSynced) syncedLines.joinToString("\n").takeIf { it.isNotBlank() } else null

                        if (plainLyrics != null || syncedLyrics != null) {
                            return@withContext Result.success(
                                LyricsData(
                                    plainLyrics = plainLyrics,
                                    syncedLyrics = syncedLyrics,
                                    source = "Navidrome (Subsonic)"
                                )
                            )
                        }
                    }
                }
            }

            // 2. Fallback to legacy getLyrics(artist, title)
            if (!artist.isNullOrBlank() && !title.isNullOrBlank()) {
                val legacyResult = requestAndParse("getLyrics", mapOf("artist" to artist, "title" to title))
                if (legacyResult.isSuccess) {
                    val lyricsObj = legacyResult.getOrThrow().optJSONObject("lyrics")
                    val content = lyricsObj?.optString("value", lyricsObj.optString("content", "")).orEmpty().trim()
                    if (content.isNotBlank()) {
                        val isLrc = content.lines().any { it.trim().matches(Regex("^\\[\\d{2}:\\d{2}.*?\\].*")) }
                        val plainLyrics = if (isLrc) {
                            content.lines().joinToString("\n") { it.replace(Regex("^\\[\\d{2}:\\d{2}.*?\\]"), "").trim() }
                        } else {
                            content
                        }
                        val syncedLyrics = if (isLrc) content else null

                        return@withContext Result.success(
                            LyricsData(
                                plainLyrics = plainLyrics.takeIf { it.isNotBlank() },
                                syncedLyrics = syncedLyrics?.takeIf { it.isNotBlank() },
                                source = "Navidrome (Subsonic)"
                            )
                        )
                    }
                }
            }

            Result.success(null)
        }
    }

    private fun formatLrcTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (ms % 1000) / 10
        return String.format(java.util.Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
    }

    fun buildStreamUrl(songId: String, maxBitRateKbps: Int = 0, format: String? = null): String? {
        val cred = credentials ?: return null
        if (songId.isBlank()) return null

        val parsedUrl = "${cred.serverUrl}/rest/stream.view".toHttpUrlOrNull() ?: return null
        val urlBuilder = parsedUrl.newBuilder()
            .addQueryParameter("u", cred.username)

        if (usePasswordAuth) {
            val obfuscated = "enc:" + cred.password.toByteArray(Charsets.UTF_8).toLowerHex()
            urlBuilder.addQueryParameter("p", obfuscated)
        } else {
            val (token, salt) = generateAuthParams(cred.password)
            urlBuilder.addQueryParameter("t", token)
            urlBuilder.addQueryParameter("s", salt)
        }

        urlBuilder.addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_ID)
            .addQueryParameter("f", "json")
            .addQueryParameter("id", songId)

        if (maxBitRateKbps > 0) {
            urlBuilder.addQueryParameter("maxBitRate", maxBitRateKbps.toString())
        }
        if (!format.isNullOrBlank()) {
            urlBuilder.addQueryParameter("format", format)
        }

        return urlBuilder.build().toString()
    }

    fun buildDownloadUrl(songId: String, format: String? = null): String? {
        val cred = credentials ?: return null
        if (songId.isBlank()) return null

        val parsedUrl = "${cred.serverUrl}/rest/download.view".toHttpUrlOrNull() ?: return null
        val urlBuilder = parsedUrl.newBuilder()
            .addQueryParameter("u", cred.username)

        if (usePasswordAuth) {
            val obfuscated = "enc:" + cred.password.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
            urlBuilder.addQueryParameter("p", obfuscated)
        } else {
            val (token, salt) = generateAuthParams(cred.password)
            urlBuilder.addQueryParameter("t", token)
            urlBuilder.addQueryParameter("s", salt)
        }

        urlBuilder.addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_ID)
            .addQueryParameter("f", "json")
            .addQueryParameter("id", songId)

        if (!format.isNullOrBlank()) {
            urlBuilder.addQueryParameter("format", format)
        }

        return urlBuilder.build().toString()
    }

    fun buildCoverArtUrl(coverArtId: String, size: Int = 500): String? {
        val cred = credentials ?: return null
        if (coverArtId.isBlank()) return null

        val parsedUrl = "${cred.serverUrl}/rest/getCoverArt.view".toHttpUrlOrNull() ?: return null
        val urlBuilder = parsedUrl.newBuilder()
            .addQueryParameter("u", cred.username)

        if (usePasswordAuth) {
            val obfuscated = "enc:" + cred.password.toByteArray(Charsets.UTF_8).toLowerHex()
            urlBuilder.addQueryParameter("p", obfuscated)
        } else {
            val (token, salt) = getStableCoverArtAuthParams(cred.password)
            urlBuilder.addQueryParameter("t", token)
            urlBuilder.addQueryParameter("s", salt)
        }

        return urlBuilder
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_ID)
            .addQueryParameter("f", "json")
            .addQueryParameter("id", coverArtId)
            .addQueryParameter("size", size.toString())
            .build()
            .toString()
    }

    private suspend fun request(
        endpoint: String, 
        params: Map<String, String> = emptyMap(),
        listParams: Map<String, List<String>> = emptyMap()
    ): Result<String> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Credentials not set"))

        return withContext(Dispatchers.IO) {
            try {
                val url = buildApiUrl(cred, endpoint, params, listParams)
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Rhythm/${chromahub.rhythm.app.BuildConfig.VERSION_NAME} (Android)")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                    }
                    Result.success(body)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Subsonic request failed for endpoint=$endpoint", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun requestAndParse(
        endpoint: String, 
        params: Map<String, String> = emptyMap(),
        listParams: Map<String, List<String>> = emptyMap()
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        // Parse on IO too: responses such as getArtists or getAlbum lists can be large, and
        // callers are often ViewModel coroutines on the main thread.
        val result = request(endpoint, params, listParams).fold(
            onSuccess = { parseSubsonicResponse(it) },
            onFailure = { Result.failure(it) }
        )
        if (result.isFailure) {
            val exception = result.exceptionOrNull()
            if (exception is SubsonicErrorException && exception.code == 41 && !usePasswordAuth) {
                usePasswordAuth = true
                if (isConnected()) {
                    prefs.edit { putBoolean(KEY_USE_PASSWORD_AUTH, true) }
                }
                return@withContext request(endpoint, params, listParams).fold(
                    onSuccess = { parseSubsonicResponse(it) },
                    onFailure = { Result.failure(it) }
                )
            }
        }
        result
    }

    /**
     * [requestAndParse] followed by [transform], both on [Dispatchers.IO]. Mapping a response
     * builds a signed cover-art URL (OkHttp URL parse) per song/album/artist, which blocked the
     * main thread for seconds on large libraries when it ran in the caller's coroutine.
     */
    private suspend fun <T> requestAndParse(
        endpoint: String,
        params: Map<String, String> = emptyMap(),
        listParams: Map<String, List<String>> = emptyMap(),
        transform: (JSONObject) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        requestAndParse(endpoint, params, listParams).map(transform)
    }

    /**
     * [requestAndParse] for library-sync requests: retries network failures (timeouts, reset
     * streams) with exponential backoff, so one slow or dropped response does not lose an album.
     * Server errors (HTTP or Subsonic error codes) are not retried.
     */
    private suspend fun requestAndParseWithRetry(
        endpoint: String,
        params: Map<String, String>
    ): Result<JSONObject> {
        var attempt = 0
        while (true) {
            val result = requestAndParse(endpoint, params)
            if (result.exceptionOrNull() !is IOException || attempt == LIBRARY_FETCH_RETRIES) {
                return result
            }
            delay(libraryFetchRetryDelayMs shl attempt)
            attempt++
            Log.w(TAG, "Retrying $endpoint after a network failure (attempt ${attempt + 1})")
        }
    }

    private fun parseSubsonicResponse(raw: String): Result<JSONObject> {
        return try {
            val root = JSONObject(raw)
            val wrapper = root.optJSONObject("subsonic-response")
                ?: return Result.failure(IllegalStateException("Invalid Subsonic response"))

            val status = wrapper.optString("status", "failed")
            if (status != "ok") {
                val error = wrapper.optJSONObject("error")
                val code = error?.optInt("code", -1) ?: -1
                val message = error?.optString("message", "Unknown error") ?: "Unknown error"
                return Result.failure(SubsonicErrorException(code, "Subsonic error $code: $message"))
            }

            Result.success(wrapper)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Credential-free reference for a stream or cover-art URL this client signed for the current
     * server (e.g. `getCoverArt?id=al-1&size=500`), for storing in a cache; null for any other
     * URL. [urlRefResolver] turns it back into a URL signed with the then-current credentials.
     */
    fun toUrlRef(url: String): String? {
        val cred = credentials ?: return null
        if (!url.startsWith("${cred.serverUrl}/rest/")) return null
        val parsed = url.toHttpUrlOrNull() ?: return null
        val endpoint = parsed.pathSegments.lastOrNull()?.removeSuffix(".view")
        if (endpoint != "stream" && endpoint != "getCoverArt") return null
        val params = (0 until parsed.querySize)
            .filter { parsed.queryParameterName(it) !in URL_AUTH_PARAMS }
            .joinToString("&") { i ->
                val value = parsed.queryParameterValue(i).orEmpty()
                parsed.queryParameterName(i) + "=" + java.net.URLEncoder.encode(value, "UTF-8")
            }
        return "$endpoint?$params"
    }

    /**
     * Resolves [toUrlRef] references to signed URLs. One resolver signs each distinct set of
     * parameters once and reuses that token for every id, so resolving tens of thousands of
     * references (a cached library) stays cheap.
     */
    fun urlRefResolver(): (String) -> String? {
        val templates = HashMap<String, String?>()
        return resolve@{ ref ->
            val endpoint = ref.substringBefore('?')
            val params = LinkedHashMap<String, String>()
            ref.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.forEach { pair ->
                params[pair.substringBefore('=')] = java.net.URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
            }
            val id = params["id"]?.takeIf { it.isNotEmpty() } ?: return@resolve null
            if (!id.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }) {
                // Ids that need URL encoding are rare; sign those one by one.
                return@resolve buildFromUrlRef(endpoint, id, params)
            }
            val key = endpoint + "?" + params.filterKeys { it != "id" }.entries.joinToString("&")
            val template = templates.getOrPut(key) { buildFromUrlRef(endpoint, URL_REF_ID_PLACEHOLDER, params) }
                ?: return@resolve null
            template.replaceFirst("id=$URL_REF_ID_PLACEHOLDER", "id=$id")
        }
    }

    private fun buildFromUrlRef(endpoint: String, id: String, params: Map<String, String>): String? = when (endpoint) {
        "getCoverArt" -> buildCoverArtUrl(id, params["size"]?.toIntOrNull() ?: 500)
        "stream" -> buildStreamUrl(id, params["maxBitRate"]?.toIntOrNull() ?: 0, params["format"])
        else -> null
    }

    private fun buildApiUrl(
        cred: Credentials, 
        endpoint: String, 
        params: Map<String, String>,
        listParams: Map<String, List<String>> = emptyMap()
    ): String {
        val parsedUrl = "${cred.serverUrl}/rest/$endpoint.view".toHttpUrlOrNull()
            ?: throw IllegalStateException("Invalid API URL: ${cred.serverUrl}")
        val builder = parsedUrl.newBuilder()
            .addQueryParameter("u", cred.username)

        if (usePasswordAuth) {
            val obfuscated = "enc:" + cred.password.toByteArray(Charsets.UTF_8).toLowerHex()
            builder.addQueryParameter("p", obfuscated)
        } else {
            val (token, salt) = generateAuthParams(cred.password)
            builder.addQueryParameter("t", token)
            builder.addQueryParameter("s", salt)
        }

        builder.addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_ID)
            .addQueryParameter("f", "json")

        params.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        
        listParams.forEach { (key, values) ->
            values.forEach { value ->
                builder.addQueryParameter(key, value)
            }
        }

        return builder.build().toString()
    }

    private fun parseSongList(songsElement: Any?): List<ProviderSong> {
        if (songsElement == null) return emptyList()
        return buildList {
            if (songsElement is org.json.JSONArray) {
                for (i in 0 until songsElement.length()) {
                    val song = songsElement.optJSONObject(i) ?: continue
                    parseSingleSong(song)?.let { add(it) }
                }
            } else if (songsElement is JSONObject) {
                parseSingleSong(songsElement)?.let { add(it) }
            }
        }
    }

    private fun parseSingleSong(song: JSONObject): ProviderSong? {
        val id = song.optString("id", "")
        if (id.isBlank()) return null

        val rawAlbumId = song.optString("albumId").takeIf { it.isNotBlank() }
        val rawCoverArt = song.optString("coverArt").takeIf { it.isNotBlank() }
        val parent = song.optString("parent").takeIf { it.isNotBlank() }

        // Prefer albumId over track-level 'mf-' IDs to reuse cached album art and prevent per-file ffmpeg extraction
        val coverArtId = when {
            rawCoverArt != null && !rawCoverArt.startsWith("mf-") -> rawCoverArt
            rawAlbumId != null -> rawAlbumId
            rawCoverArt != null -> rawCoverArt
            parent != null -> parent
            else -> id
        }
        
        val rawTrack = song.optString("track", "")
        val trackNum = song.optInt("track", 0).takeIf { it > 0 }
            ?: rawTrack.substringBefore('/').toIntOrNull()
        
        val yearVal = song.optInt("year", 0).takeIf { it > 0 }
        val genreVal = song.optString("genre", "").takeIf { it.isNotBlank() }
        
        val bitrateVal = song.optInt("bitRate", 0).takeIf { it > 0 }?.let { it * 1000 }
        val sampleRateVal = song.optInt("sampleRate", 0).takeIf { it > 0 }
        val codecVal = song.optString("suffix", "").takeIf { it.isNotBlank() }

        return ProviderSong(
            providerId = id,
            title = song.optString("title", song.optString("name", "Unknown title")),
            artist = song.optString("artist", "Unknown artist"),
            album = song.optString("album", "Unknown album"),
            durationMs = song.optLong("duration", 0L) * 1000L,
            artworkUrl = coverArtId?.let { buildCoverArtUrl(it, 500) },
            albumId = song.optString("albumId", "").takeIf { it.isNotBlank() },
            albumArtist = song.optString("albumArtist", "").takeIf { it.isNotBlank() },
            isFavorite = song.has("starred") && !song.isNull("starred"),
            trackNumber = trackNum,
            year = yearVal,
            genre = genreVal,
            bitrate = bitrateVal,
            sampleRate = sampleRateVal,
            channels = song.optInt("channels", 0).takeIf { it > 0 },
            codec = codecVal
        )
    }

    private fun parseAlbumListCompat(albumElement: Any?): List<ProviderAlbum> {
        if (albumElement == null) return emptyList()
        return buildList {
            if (albumElement is org.json.JSONArray) {
                for (i in 0 until albumElement.length()) {
                    val album = albumElement.optJSONObject(i) ?: continue
                    add(parseAlbumItem(album))
                }
            } else if (albumElement is JSONObject) {
                add(parseAlbumItem(albumElement))
            }
        }
    }

    private fun parseAlbumItem(album: JSONObject): ProviderAlbum {
        val id = album.optString("id", "")
        if (id.isBlank()) {
            throw IllegalStateException("Album id is missing in provider response")
        }

        return ProviderAlbum(
            providerId = id,
            title = album.optString("name", album.optString("album", "Unknown album")),
            artist = album.optString("artist", "Unknown artist"),
            artworkUrl = album.optString("coverArt").takeIf { it.isNotBlank() }?.let { buildCoverArtUrl(it, 500) },
            songCount = album.optInt("songCount", 0),
            year = album.optInt("year").takeIf { it > 0 },
            description = album.optString("comment").takeIf { it.isNotBlank() }
        )
    }

    private fun parseArtistListCompat(artistElement: Any?): List<ProviderArtist> {
        if (artistElement == null) return emptyList()
        return buildList {
            if (artistElement is org.json.JSONArray) {
                for (i in 0 until artistElement.length()) {
                    val artist = artistElement.optJSONObject(i) ?: continue
                    val id = artist.optString("id", "")
                    if (id.isNotBlank()) {
                        add(
                            ProviderArtist(
                                providerId = id,
                                name = artist.optString("name", "Unknown artist"),
                                artworkUrl = artist.optString("coverArt").takeIf { it.isNotBlank() }?.let { buildCoverArtUrl(it, 500) },
                                songCount = artist.optInt("songCount", 0),
                                albumCount = artist.optInt("albumCount", 0),
                                description = artist.optString("biography").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            } else if (artistElement is JSONObject) {
                val id = artistElement.optString("id", "")
                if (id.isNotBlank()) {
                    add(
                        ProviderArtist(
                            providerId = id,
                            name = artistElement.optString("name", "Unknown artist"),
                            artworkUrl = artistElement.optString("coverArt").takeIf { it.isNotBlank() }?.let { buildCoverArtUrl(it, 500) },
                            songCount = artistElement.optInt("songCount", 0),
                            albumCount = artistElement.optInt("albumCount", 0),
                            description = artistElement.optString("biography").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }
    }

    private fun parseArtistId3List(artists: org.json.JSONArray?): List<ProviderArtist> {
        return buildList {
            for (i in 0 until (artists?.length() ?: 0)) {
                val artist = artists?.optJSONObject(i) ?: continue
                val id = artist.optString("id", "")
                val name = artist.optString("name", "")
                if (id.isBlank() && name.isBlank()) continue

                add(
                    ProviderArtist(
                        providerId = id.ifBlank { name },
                        name = name.ifBlank { id },
                        artworkUrl = artist.optString("coverArt").takeIf { it.isNotBlank() }?.let { buildCoverArtUrl(it, 500) },
                        songCount = artist.optInt("songCount", 0),
                        albumCount = artist.optInt("albumCount", 0),
                        description = null
                    )
                )
            }
        }
    }

    private fun parsePlaylistList(playlists: org.json.JSONArray?, limit: Int = 100): List<ProviderPlaylist> {
        return buildList {
            for (i in 0 until minOf(limit, playlists?.length() ?: 0)) {
                val playlist = playlists?.optJSONObject(i) ?: continue
                val id = playlist.optString("id", "")
                if (id.isBlank()) continue

                val coverArtId = playlist.optString("coverArt").takeIf { it.isNotBlank() }
                add(
                    ProviderPlaylist(
                        providerId = id,
                        name = playlist.optString("name", "Unknown playlist"),
                        description = playlist.optString("comment").takeIf { it.isNotBlank() },
                        artworkUrl = coverArtId?.let { buildCoverArtUrl(it, 500) },
                        songCount = playlist.optInt("songCount", 0),
                        owner = playlist.optString("owner").takeIf { it.isNotBlank() },
                        isPublic = playlist.optBoolean("public", true)
                    )
                )
            }
        }
    }

    private fun generateAuthParams(password: String): Pair<String, String> {
        val salt = UUID.randomUUID().toString().take(6)
        val token = md5(password + salt)
        return token to salt
    }

    private fun getStableCoverArtAuthParams(password: String): Pair<String, String> {
        // Deterministic per password, and requested for every parsed song/album/artist, so
        // compute it once instead of two MD5s per cover-art URL.
        stableCoverArtAuth?.let { cached ->
            if (cached.password == password) return cached.token to cached.salt
        }
        val salt = md5(password).take(8)
        val token = md5(password + salt)
        stableCoverArtAuth = StableCoverArtAuth(password, token, salt)
        return token to salt
    }

    private class StableCoverArtAuth(val password: String, val token: String, val salt: String)

    @Volatile
    private var stableCoverArtAuth: StableCoverArtAuth? = null

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.toLowerHex()
    }

    private fun loadCredentials(): Credentials? {
        val server = prefs.getString(KEY_SERVER_URL, null).orEmpty()
        val user = prefs.getString(KEY_USERNAME, null).orEmpty()
        val pass = prefs.getString(KEY_PASSWORD, null).orEmpty()

        if (server.isBlank() || user.isBlank() || pass.isBlank()) {
            return null
        }

        return Credentials(serverUrl = server, username = user, password = pass)
    }

    private fun normalizeServerUrl(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            return trimmed
        }
        return "http://$trimmed"
    }

    private fun validateServerUrl(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return "Enter a valid server URL"
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return "Server URL must not contain embedded credentials"
        }

        return null
    }

    private fun isPrivateHost(host: String): Boolean {
        val lowerHost = host.lowercase()
        if (lowerHost == "localhost" ||
            lowerHost.endsWith(".local") ||
            lowerHost.endsWith(".localdomain") ||
            lowerHost.endsWith(".lan") ||
            lowerHost.endsWith(".home") ||
            lowerHost.endsWith(".home.arpa") ||
            lowerHost.endsWith(".ts.net") ||
            lowerHost.endsWith(".mesh") ||
            lowerHost.endsWith(".internal") ||
            lowerHost.endsWith(".host") ||
            lowerHost.endsWith(".priv") ||
            !lowerHost.contains(".")
        ) {
            return true
        }

        if (lowerHost == "::1" || lowerHost.startsWith("fe80:") || lowerHost.startsWith("fd") || lowerHost.startsWith("fc")) {
            return true
        }

        val parts = lowerHost.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }

        val first = octets[0]
        val second = octets[1]

        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 127) ||
            (first == 100 && second in 64..127)
    }

    private companion object {
        private const val TAG = "SubsonicApiClient"
        private const val PREFS_NAME = "streaming_subsonic_credentials"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_USE_PASSWORD_AUTH = "use_password_auth"

        private const val API_VERSION = "1.16.1"

        /** Query parameters that carry credentials or client info; not part of a URL reference. */
        private val URL_AUTH_PARAMS = setOf("u", "t", "s", "p", "v", "c", "f")
        private const val URL_REF_ID_PLACEHOLDER = "RHYTHMURLREFID"
        private const val CLIENT_ID = "Rhythm"

        /** Parallel getAlbum requests during a library sync. */
        private const val LIBRARY_FETCH_CONCURRENCY = 4
        /** Retries per library-sync request after a network failure. */
        private const val LIBRARY_FETCH_RETRIES = 2
        /** First retry delay; doubles on each further retry. */
        private const val LIBRARY_FETCH_RETRY_DELAY_MS = 1_000L

        private fun buildHttpClient(): OkHttpClient = UserTrustManager.buildUserTrustingHttpClientBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

private val LOWER_HEX_DIGITS = "0123456789abcdef".toCharArray()

/**
 * Lower-case hex encoding for fast byte-array to hex string conversion.
 */
internal fun ByteArray.toLowerHex(): String {
    val out = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xff
        out[i * 2] = LOWER_HEX_DIGITS[v ushr 4]
        out[i * 2 + 1] = LOWER_HEX_DIGITS[v and 0x0f]
    }
    return String(out)
}
