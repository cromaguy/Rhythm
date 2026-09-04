/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.provider

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

/**
 * Jellyfin API client for authentication, search and stream URL generation.
 */
class JellyfinApiClient(context: Context) {

    private data class Credentials(
        val serverUrl: String,
        val username: String,
        val accessToken: String,
        val userId: String
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var credentials: Credentials? = loadCredentials()

    private val okHttpClient = UserTrustManager.buildUserTrustingHttpClientBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun isConnected(): Boolean = credentials != null

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

        return authenticateByName(
            serverUrl = normalizedUrl,
            username = username.trim(),
            password = password
        ).map { (token, userId) ->
            val cred = Credentials(
                serverUrl = normalizedUrl,
                username = username.trim(),
                accessToken = token,
                userId = userId
            )
            credentials = cred
            if (saveCredentials) {
                prefs.edit {
    putString(KEY_SERVER_URL, normalizedUrl)
    putString(KEY_USERNAME, username.trim())
    putString(KEY_ACCESS_TOKEN, token)
    putString(KEY_USER_ID, userId)
}
            } else {
                prefs.edit { clear() }
            }
            ProviderConnectionResult(displayName = username.trim(), serverUrl = normalizedUrl)
        }
    }

    fun logout() {
        credentials = null
        prefs.edit { clear() }
    }

    suspend fun ping(): Result<Boolean> {
        return request("/System/Ping").map { true }
    }

    suspend fun searchSongs(query: String, limit: Int = 30): Result<List<ProviderSong>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))

        val params = buildAudioBrowseParams(query = query, limit = limit)

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            parseAudioItems(response)
        }
    }

    suspend fun searchAlbums(query: String, limit: Int = 30): Result<List<ProviderAlbum>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))

        val params = buildLibraryBrowseParams(
            includeItemTypes = "MusicAlbum",
            query = query,
            limit = limit,
            fields = "Overview,Genres,ArtistItems,Artists,AlbumArtist,ProductionYear,ImageTags,RunTimeTicks,ParentId"
        )

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            parseAlbumItems(response)
        }
    }

    suspend fun searchArtists(query: String, limit: Int = 30): Result<List<ProviderArtist>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))

        val params = buildArtistBrowseParams(query = query, limit = limit)

        return requestJson("/Artists", params).map { response ->
            parseArtistItems(response)
        }
    }

    suspend fun getArtists(limit: Int = 5_000): Result<List<ProviderArtist>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))

        return withContext(Dispatchers.IO) {
            try {
                val pageSize = 200
                var startIndex = 0
                val artists = LinkedHashMap<String, ProviderArtist>()

                while (artists.size < limit) {
                    val params = buildArtistBrowseParams(
                        query = null,
                        limit = minOf(pageSize, limit - artists.size),
                        startIndex = startIndex
                    )
                    val response = requestJson("/Artists", params).getOrThrow()
                    val pageArtists = parseArtistItems(response)
                    if (pageArtists.isEmpty()) break

                    pageArtists.forEach { artist -> artists.putIfAbsent(artist.providerId, artist) }

                    val totalRecordCount = response.optInt("TotalRecordCount", -1)
                    startIndex += pageArtists.size

                    if (totalRecordCount >= 0 && startIndex >= totalRecordCount) break
                    val limitParam = params["limit"]?.toIntOrNull().orZero()
                    if (limitParam > 0 && pageArtists.size < limitParam) break
                }

                Result.success(artists.values.take(limit).toList())
            } catch (e: Exception) {
                Log.e(TAG, "Jellyfin getArtists fetch failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun fetchLibrarySongs(
        limit: Int = 5_000,
        onProgress: ((current: Int, total: Int, songsCount: Int) -> Unit)? = null
    ): Result<List<ProviderSong>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))

        return withContext(Dispatchers.IO) {
            try {
                val pageSize = 200
                var startIndex = 0
                val songs = LinkedHashMap<String, ProviderSong>()

                while (songs.size < limit) {
                    val params = buildAudioBrowseParams(
                        query = null,
                        limit = minOf(pageSize, limit - songs.size),
                        startIndex = startIndex
                    )
                    val response = requestJson("/Users/${cred.userId}/Items", params).getOrThrow()
                    val rawItems = response.optJSONArray("Items")
                    val rawItemsCount = rawItems?.length() ?: 0

                    if (rawItemsCount == 0) break

                    val pageSongs = parseAudioItems(response)
                    pageSongs.forEach { song -> songs.putIfAbsent(song.providerId, song) }

                    val totalRecordCount = response.optInt("TotalRecordCount", -1)
                    startIndex += rawItemsCount

                    onProgress?.invoke(
                        startIndex,
                        if (totalRecordCount > 0) totalRecordCount else limit,
                        songs.size
                    )

                    if (totalRecordCount >= 0 && startIndex >= totalRecordCount) break
                    val limitParam = params["Limit"]?.toIntOrNull().orZero()
                    if (limitParam > 0 && rawItemsCount < limitParam) break
                }

                Result.success(songs.values.take(limit).toList())
            } catch (e: Exception) {
                Log.e(TAG, "Jellyfin library fetch failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getPlaylists(limit: Int = 100): Result<List<ProviderPlaylist>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))

        val params = buildPlaylistBrowseParams(limit = limit)

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            parsePlaylistItems(response)
        }
    }

    suspend fun searchPlaylists(query: String, limit: Int = 30): Result<List<ProviderPlaylist>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))

        val params = buildPlaylistBrowseParams(query = query, limit = limit)

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            parsePlaylistItems(response)
        }
    }

    suspend fun getPlaylistSongs(playlistId: String, limit: Int = 500): Result<List<ProviderSong>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))
        if (playlistId.isBlank()) {
            return Result.failure(IllegalArgumentException("Playlist id is required"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val pageSize = limit.coerceIn(1, 100)
                var startIndex = 0
                val songs = LinkedHashMap<String, ProviderSong>()

                while (songs.size < limit) {
                    val params = buildAudioBrowseParams(
                        query = null,
                        limit = minOf(pageSize, limit - songs.size),
                        startIndex = startIndex
                    )
                    val response = requestJson("/Playlists/$playlistId/Items", params).getOrThrow()
                    val pageSongs = parseAudioItems(response)

                    if (pageSongs.isEmpty()) break

                    pageSongs.forEach { song -> songs.putIfAbsent(song.providerId, song) }

                    if (pageSongs.size < params["Limit"]?.toIntOrNull().orZero()) break
                    startIndex += pageSongs.size
                }

                Result.success(songs.values.take(limit).toList())
            } catch (e: Exception) {
                Log.e(TAG, "Jellyfin playlist fetch failed for playlistId=$playlistId", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getAlbumSongs(albumId: String, limit: Int = 500): Result<List<ProviderSong>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))
        if (albumId.isBlank()) {
            return Result.failure(IllegalArgumentException("Album id is required"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val pageSize = limit.coerceIn(1, 100)
                var startIndex = 0
                val songs = LinkedHashMap<String, ProviderSong>()

                while (songs.size < limit) {
                    val params = buildAudioBrowseParams(
                        query = null,
                        limit = minOf(pageSize, limit - songs.size),
                        startIndex = startIndex
                    ).toMutableMap()
                    params["ParentId"] = albumId
                    params["SortBy"] = "ParentIndexNumber,IndexNumber,SortName"
                    params["SortOrder"] = "Ascending"

                    val response = requestJson("/Users/${cred.userId}/Items", params).getOrThrow()
                    val pageSongs = parseAudioItems(response)
                    if (pageSongs.isEmpty()) break

                    pageSongs.forEach { song -> songs.putIfAbsent(song.providerId, song) }

                    if (pageSongs.size < params["Limit"]?.toIntOrNull().orZero()) break
                    startIndex += pageSongs.size
                }

                Result.success(songs.values.take(limit).toList())
            } catch (e: Exception) {
                Log.e(TAG, "Jellyfin album track fetch failed for albumId=$albumId", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getAlbumById(albumId: String): Result<ProviderAlbum> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))
        if (albumId.isBlank()) {
            return Result.failure(IllegalArgumentException("Album id is required"))
        }

        val params = mapOf(
            "Fields" to "Overview,Genres,ArtistItems,Artists,AlbumArtist,ProductionYear,ImageTags,RunTimeTicks,ParentId,ItemCounts"
        )

        return requestJson("/Users/${cred.userId}/Items/$albumId", params).map { response ->
            parseAlbumItem(response)
                ?: throw IllegalStateException("Album not found for id=$albumId")
        }
    }

    suspend fun getArtistTopTracks(artistQuery: String, limit: Int = 20): Result<List<ProviderSong>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))

        val artistSearch = searchArtists(artistQuery, limit = 5)
        val artistId = artistSearch.getOrNull()
            ?.firstOrNull { it.name.equals(artistQuery, ignoreCase = true) }?.providerId
            ?: artistSearch.getOrNull()?.firstOrNull()?.providerId

        val params = mutableMapOf<String, String>(
            "IncludeItemTypes" to "Audio",
            "Recursive" to "true",
            "Fields" to "Overview,Genres,Artists,AlbumArtist,ProductionYear,ImageTags,RunTimeTicks,ParentId",
            "Limit" to limit.toString(),
            "SortBy" to "PlayCount,Name",
            "SortOrder" to "Descending"
        )
        if (!artistId.isNullOrBlank()) {
            params["ArtistIds"] = artistId
        } else {
            params["SearchTerm"] = artistQuery
        }

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            parseAudioItems(response)
        }
    }

    suspend fun getArtistAlbums(artistQuery: String, limit: Int = 50): Result<List<ProviderAlbum>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))

        val artistSearch = searchArtists(artistQuery, limit = 5)
        val artistId = artistSearch.getOrNull()
            ?.firstOrNull { it.name.equals(artistQuery, ignoreCase = true) }?.providerId
            ?: artistSearch.getOrNull()?.firstOrNull()?.providerId

        val params = mutableMapOf<String, String>(
            "IncludeItemTypes" to "MusicAlbum",
            "Recursive" to "true",
            "Fields" to "Overview,Genres,ArtistItems,Artists,AlbumArtist,ProductionYear,ImageTags,RunTimeTicks,ParentId",
            "Limit" to limit.toString()
        )
        if (!artistId.isNullOrBlank()) {
            params["ArtistIds"] = artistId
        } else {
            params["SearchTerm"] = artistQuery
        }

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            parseAlbumItems(response)
        }
    }

    suspend fun getRelatedArtists(artistId: String, limit: Int = 10): Result<List<ProviderArtist>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))
        if (artistId.isBlank()) {
            return Result.failure(IllegalArgumentException("Artist id is required"))
        }

        val params = buildLibraryBrowseParams(
            includeItemTypes = "MusicArtist",
            limit = limit.coerceIn(1, 100),
            fields = "Overview,SongCount,AlbumCount,ImageTags"
        )

        return requestJson("/Artists/$artistId/Similar", params).map { response ->
            parseArtistItems(response)
        }
    }

    suspend fun getRelatedTracks(songId: String, limit: Int = 20): Result<List<ProviderSong>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))
        if (songId.isBlank()) {
            return Result.failure(IllegalArgumentException("Song id is required"))
        }

        val baseParams = buildAudioBrowseParams(
            query = null,
            limit = limit.coerceIn(1, 100)
        ).toMutableMap()
        baseParams["Fields"] = "Artists,Album,Genres,ImageTags,RunTimeTicks,MediaSources"

        return requestJson("/Items/$songId/Similar", baseParams).map { response ->
            parseAudioItems(response)
        }
    }

    suspend fun getRandomSongs(limit: Int = 50): Result<List<ProviderSong>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))

        val params = buildAudioBrowseParams(
            query = null,
            limit = limit.coerceIn(1, 100)
        ).toMutableMap()
        params["SortBy"] = "Random"

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            parseAudioItems(response)
        }
    }

    suspend fun getAlbumList(type: String = "newest", limit: Int = 50): Result<List<ProviderAlbum>> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))

        val sortBy = when (type.lowercase()) {
            "newest" -> "PremiereDate"
            "recent" -> "DateCreated"
            "random" -> "Random"
            "alphabetical" -> "Name"
            "frequent" -> "PlayCount"
            else -> "PremiereDate"
        }

        val params = buildLibraryBrowseParams(
            includeItemTypes = "MusicAlbum",
            query = null,
            limit = limit.coerceIn(1, 100),
            fields = "Overview,Genres,ArtistItems,Artists,AlbumArtist,ProductionYear,ImageTags,RunTimeTicks,ParentId"
        ).toMutableMap()
        params["SortBy"] = sortBy
        if (sortBy == "PremiereDate" || sortBy == "DateCreated" || sortBy == "PlayCount") {
            params["SortOrder"] = "Descending"
        }

        return requestJson("/Users/${cred.userId}/Items", params).map { response ->
            parseAlbumItems(response)
        }
    }

    suspend fun createPlaylist(
        name: String,
        songIds: List<String> = emptyList(),
        description: String? = null,
        isPublic: Boolean = true
    ): Result<ProviderPlaylist> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("Playlist name is required"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val bodyJson = JSONObject().apply {
                    put("Name", name.trim())
                    put("Ids", JSONArray(songIds))
                    put("UserId", cred.userId)
                    put("MediaType", "Audio")
                    put("IsPublic", isPublic)
                    if (!description.isNullOrBlank()) {
                        put("Overview", description.trim())
                    }
                }

                requestJson(
                    path = "/Playlists",
                    method = "POST",
                    body = bodyJson.toString().toRequestBody("application/json".toMediaType())
                ).map { response ->
                    val playlistId = response.optString("Id", response.optString("PlaylistId", ""))
                    if (playlistId.isBlank()) {
                        throw IllegalStateException("Invalid Jellyfin playlist creation response")
                    }

                    ProviderPlaylist(
                        providerId = playlistId,
                        name = name.trim(),
                        description = description?.trim()?.takeIf { it.isNotBlank() },
                        artworkUrl = buildImageUrl(playlistId),
                        songCount = songIds.size,
                        owner = cred.username,
                        isPublic = isPublic
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Jellyfin playlist creation failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>): Result<Boolean> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))
        if (playlistId.isBlank()) {
            return Result.failure(IllegalArgumentException("Playlist id is required"))
        }
        if (songIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one song id is required"))
        }

        val params = mapOf(
            "ids" to songIds.joinToString(","),
            "userId" to cred.userId
        )

        return request(
            path = "/Playlists/$playlistId/Items",
            params = params,
            method = "POST"
        ).map { true }
    }

    suspend fun updatePlaylist(
        playlistId: String,
        name: String? = null,
        songIds: List<String> = emptyList(),
        description: String? = null,
        isPublic: Boolean? = null
    ): Result<Boolean> {
        if (playlistId.isBlank()) {
            return Result.failure(IllegalArgumentException("Playlist id is required"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val bodyJson = JSONObject().apply {
                    if (!name.isNullOrBlank()) {
                        put("Name", name.trim())
                    }
                    if (songIds.isNotEmpty()) {
                        put("Ids", JSONArray(songIds))
                    }
                    if (!description.isNullOrBlank()) {
                        put("Overview", description.trim())
                    }
                    if (isPublic != null) {
                        put("IsPublic", isPublic)
                    }
                }

                request(
                    path = "/Playlists/$playlistId",
                    method = "POST",
                    body = bodyJson.toString().toRequestBody("application/json".toMediaType())
                ).map { true }
            } catch (e: Exception) {
                Log.e(TAG, "Jellyfin playlist update failed for playlistId=$playlistId", e)
                Result.failure(e)
            }
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Boolean> {
        if (playlistId.isBlank()) {
            return Result.failure(IllegalArgumentException("Playlist id is required"))
        }

        return request("/Items/$playlistId", method = "DELETE").map { true }
    }

    suspend fun removeSongsFromPlaylist(playlistId: String, songIds: List<String>): Result<Boolean> {
        credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))
        if (playlistId.isBlank()) {
            return Result.failure(IllegalArgumentException("Playlist id is required"))
        }
        if (songIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one song id is required"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val targetIds = songIds.toSet()
                val itemsResponse = requestJson(
                    path = "/Playlists/$playlistId/Items",
                    params = mapOf("Fields" to "Id", "Limit" to "1000")
                ).getOrNull()

                val entryIds = mutableListOf<String>()
                val itemsArray = itemsResponse?.optJSONArray("Items")
                if (itemsArray != null) {
                    for (i in 0 until itemsArray.length()) {
                        val item = itemsArray.optJSONObject(i) ?: continue
                        val id = item.optString("Id", "")
                        val playlistItemId = item.optString("PlaylistItemId", "")
                        if (targetIds.contains(id) || targetIds.contains(playlistItemId)) {
                            if (playlistItemId.isNotBlank()) {
                                entryIds.add(playlistItemId)
                            } else if (id.isNotBlank()) {
                                entryIds.add(id)
                            }
                        }
                    }
                }

                val idsToDelete = if (entryIds.isNotEmpty()) entryIds else songIds
                val params = mapOf(
                    "EntryIds" to idsToDelete.joinToString(",")
                )

                request(
                    path = "/Playlists/$playlistId/Items",
                    params = params,
                    method = "DELETE"
                ).map { true }
            } catch (e: Exception) {
                Log.e(TAG, "Jellyfin playlist removeSongs failed for playlistId=$playlistId", e)
                Result.failure(e)
            }
        }
    }

    suspend fun markFavorite(itemId: String, isFavorite: Boolean): Result<Boolean> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))
        if (itemId.isBlank()) {
            return Result.failure(IllegalArgumentException("Item id is required"))
        }

        val method = if (isFavorite) "POST" else "DELETE"
        return request(
            path = "/Users/${cred.userId}/FavoriteItems/$itemId",
            method = method
        ).map { true }
    }

    suspend fun reportPlaybackStart(itemId: String): Result<Boolean> {
        credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))
        if (itemId.isBlank()) return Result.failure(IllegalArgumentException("Item id is required"))

        val bodyJson = JSONObject().apply {
            put("ItemId", itemId)
            put("PlayMethod", "DirectStream") // We use direct stream, not transcoding typically
        }

        return request(
            path = "/Sessions/Playing",
            method = "POST",
            body = bodyJson.toString().toRequestBody("application/json".toMediaType())
        ).map { true }
    }

    suspend fun reportPlaybackStop(itemId: String, positionTicks: Long): Result<Boolean> {
        credentials ?: return Result.failure(IllegalStateException("Jellyfin service is not connected"))
        if (itemId.isBlank()) return Result.failure(IllegalArgumentException("Item id is required"))

        val bodyJson = JSONObject().apply {
            put("ItemId", itemId)
            put("PositionTicks", positionTicks)
            put("PlayMethod", "DirectStream")
        }

        return request(
            path = "/Sessions/Playing/Stopped",
            method = "POST",
            body = bodyJson.toString().toRequestBody("application/json".toMediaType())
        ).map { true }
    }

    fun buildStreamUrl(itemId: String, maxBitRateKbps: Int = 0): String? {
        val cred = credentials ?: return null
        if (itemId.isBlank()) return null

        val urlBuilder = "${cred.serverUrl}/Audio/$itemId/universal".toHttpUrl().newBuilder()
            .addQueryParameter("UserId", cred.userId)
            .addQueryParameter("DeviceId", DEVICE_ID)
            .addQueryParameter("Container", "mp3,flac,m4a,ogg,wav,aac,opus,webm")
            .addQueryParameter("AudioCodec", "mp3,flac,aac,opus")
            .addQueryParameter("api_key", cred.accessToken)

        if (maxBitRateKbps > 0) {
            urlBuilder.addQueryParameter("MaxStreamingBitrate", (maxBitRateKbps * 1000).toString())
        }

        return urlBuilder.build().toString()
    }

    fun buildImageUrl(itemId: String, maxWidth: Int = 500): String? {
        val cred = credentials ?: return null
        if (itemId.isBlank()) return null
        return "${cred.serverUrl}/Items/$itemId/Images/Primary?maxWidth=$maxWidth&quality=90&api_key=${cred.accessToken}"
    }

    private suspend fun authenticateByName(
        serverUrl: String,
        username: String,
        password: String
    ): Result<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("Username", username)
                    put("Pw", password)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("${serverUrl.trimEnd('/')}/Users/AuthenticateByName")
                    .header("Authorization", buildAuthorizationHeader(token = null))
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val raw = response.body.string()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                    }

                    val json = JSONObject(raw)
                    val token = json.optString("AccessToken", "")
                    val userId = json.optJSONObject("User")?.optString("Id", "") ?: ""

                    if (token.isBlank() || userId.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("Invalid Jellyfin authentication response"))
                    }

                    Result.success(token to userId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Jellyfin authentication failed", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun request(
        path: String,
        params: Map<String, String> = emptyMap(),
        method: String = "GET",
        body: RequestBody? = null
    ): Result<String> {
        val cred = credentials ?: return Result.failure(IllegalStateException("Credentials not set"))

        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "${cred.serverUrl}$path".toHttpUrl().newBuilder()
                params.forEach { (key, value) ->
                    urlBuilder.addQueryParameter(key, value)
                }

                val requestBuilder = Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", buildAuthorizationHeader(token = cred.accessToken))
                    .header("Accept", "application/json")
                when (method.uppercase()) {
                    "POST" -> requestBuilder.post(body ?: "".toRequestBody("text/plain".toMediaType()))
                    "DELETE" -> {
                        if (body != null) {
                            requestBuilder.delete(body)
                        } else {
                            requestBuilder.delete()
                        }
                    }
                    else -> requestBuilder.get()
                }

                val request = requestBuilder.build()

                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                    }
                    Result.success(body)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Jellyfin request failed for path=$path", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun requestJson(
        path: String,
        params: Map<String, String> = emptyMap(),
        method: String = "GET",
        body: RequestBody? = null
    ): Result<JSONObject> {
        return request(path, params, method, body).map { JSONObject(it) }
    }

    private fun buildAudioBrowseParams(
        query: String?,
        limit: Int,
        startIndex: Int = 0
    ): Map<String, String> {
        return buildMap {
            if (!query.isNullOrBlank()) {
                put("SearchTerm", query)
            }
            put("IncludeItemTypes", "Audio")
            put("MediaTypes", "Audio")
            put("Recursive", "true")
            put("Fields", "MediaSources,Genres,Path,Artists,AlbumArtist,AlbumId,Album,RunTimeTicks,ProductionYear,UserData,ParentId")
            put("enableUserData", "true")
            put("SortBy", "SortName")
            put("SortOrder", "Ascending")
            put("Limit", limit.coerceIn(1, 500).toString())
            if (startIndex > 0) {
                put("StartIndex", startIndex.toString())
            }
        }
    }

    private fun parseAudioItems(response: JSONObject): List<ProviderSong> {
        val items = response.optJSONArray("Items") ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val song = items.optJSONObject(i) ?: continue
                val id = song.optString("Id", song.optString("id", ""))
                if (id.isBlank()) continue

                val title = song.optString("Name", song.optString("name", "Unknown title"))
                val artist = parseArtist(song)
                val album = song.optString("Album", song.optString("album", "Unknown album"))
                val durationMs = song.optLong("RunTimeTicks", 0L) / 10_000L
                val isFavorite = song
                    .optJSONObject("UserData")
                    ?.optBoolean("IsFavorite", false)
                    ?: false

                val trackNum = song.optInt("IndexNumber", song.optInt("Index", 0)).takeIf { it > 0 }
                val yearVal = song.optInt("ProductionYear", 0).takeIf { it > 0 }
                val genres = song.optJSONArray("Genres")
                val genreVal = if (genres != null && genres.length() > 0) {
                    val first = genres.opt(0)
                    when (first) {
                        is String -> first
                        is JSONObject -> first.optString("Name", "")
                        else -> null
                    }
                } else null

                val mediaSources = song.optJSONArray("MediaSources")
                val firstSource = mediaSources?.optJSONObject(0)
                val bitrateVal = firstSource?.optInt("Bitrate", 0)?.takeIf { it > 0 }
                val codecVal = firstSource?.optString("Container")?.takeIf { it.isNotBlank() }

                val mediaStreams = firstSource?.optJSONArray("MediaStreams")
                var sampleRateVal: Int? = null
                var channelsVal: Int? = null
                if (mediaStreams != null) {
                    for (j in 0 until mediaStreams.length()) {
                        val stream = mediaStreams.optJSONObject(j)
                        if (stream?.optString("Type") == "Audio") {
                            sampleRateVal = stream.optInt("SampleRate", 0).takeIf { it > 0 }
                            channelsVal = stream.optInt("Channels", 0).takeIf { it > 0 }
                            break
                        }
                    }
                }

                add(
                    ProviderSong(
                        providerId = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        artworkUrl = buildImageUrl(id),
                        albumId = song.optString("AlbumId", "").takeIf { it.isNotBlank() }
                            ?: song.optString("ParentId", "").takeIf { it.isNotBlank() },
                        albumArtist = song.optString("AlbumArtist", "").takeIf { it.isNotBlank() },
                        isFavorite = isFavorite,
                        trackNumber = trackNum,
                        year = yearVal,
                        genre = genreVal,
                        bitrate = bitrateVal,
                        sampleRate = sampleRateVal,
                        channels = channelsVal,
                        codec = codecVal
                    )
                )
            }
        }
    }

    private fun parseArtist(song: JSONObject): String {
        val names = mutableListOf<String>()

        val artists = song.optJSONArray("Artists")
        if (artists != null) {
            for (i in 0 until artists.length()) {
                val item = artists.opt(i)
                when (item) {
                    is String -> if (item.isNotBlank()) names.add(item.trim())
                    is JSONObject -> {
                        val name = item.optString("Name", item.optString("name", "")).trim()
                        if (name.isNotBlank()) names.add(name)
                    }
                }
            }
        }

        if (names.isEmpty()) {
            val artistItems = song.optJSONArray("ArtistItems")
            if (artistItems != null) {
                for (i in 0 until artistItems.length()) {
                    val obj = artistItems.optJSONObject(i) ?: continue
                    val name = obj.optString("Name", obj.optString("name", "")).trim()
                    if (name.isNotBlank()) names.add(name)
                }
            }
        }

        if (names.isNotEmpty()) {
            return names.joinToString(", ")
        }

        return song.optString("AlbumArtist", "").takeIf { it.isNotBlank() } ?: "Unknown artist"
    }

    private fun buildPlaylistBrowseParams(
        query: String? = null,
        limit: Int = 100,
        startIndex: Int = 0
    ): Map<String, String> {
        return buildMap {
            if (!query.isNullOrBlank()) {
                put("SearchTerm", query)
            }
            put("IncludeItemTypes", "Playlist")
            put("Recursive", "true")
            put("Fields", "Overview,RunTimeTicks,ItemCounts")
            put("Limit", limit.coerceIn(1, 1000).toString())
            if (startIndex > 0) {
                put("StartIndex", startIndex.toString())
            }
        }
    }

    private fun buildLibraryBrowseParams(
        includeItemTypes: String,
        query: String? = null,
        limit: Int = 30,
        startIndex: Int = 0,
        fields: String = "Overview,RunTimeTicks,ItemCounts"
    ): Map<String, String> {
        return buildMap {
            if (!query.isNullOrBlank()) {
                put("SearchTerm", query)
            }
            put("IncludeItemTypes", includeItemTypes)
            put("Recursive", "true")
            put("Fields", fields)
            put("Limit", limit.coerceIn(1, 1000).toString())
            if (startIndex > 0) {
                put("StartIndex", startIndex.toString())
            }
        }
    }

    private fun buildArtistBrowseParams(
        query: String? = null,
        limit: Int = 30,
        startIndex: Int = 0
    ): Map<String, String> {
        return buildMap {
            if (!query.isNullOrBlank()) {
                put("searchTerm", query)
            }
            put("limit", limit.coerceIn(1, 1000).toString())
            if (startIndex > 0) {
                put("startIndex", startIndex.toString())
            }
            put("enableImages", "true")
            put("enableUserData", "true")
            put("fields", "PrimaryImageAspectRatio,SongCount,AlbumCount,ImageTags")
        }
    }

    private fun parseAlbumItems(response: JSONObject): List<ProviderAlbum> {
        val items = response.optJSONArray("Items")
        return buildList {
            for (i in 0 until (items?.length() ?: 0)) {
                val album = items?.optJSONObject(i) ?: continue
                parseAlbumItem(album)?.let { add(it) }
            }
        }
    }

    private fun parseAlbumItem(album: JSONObject): ProviderAlbum? {
        val id = album.optString("Id", "")
        if (id.isBlank()) {
            return null
        }

        return ProviderAlbum(
            providerId = id,
            title = album.optString("Name", "Unknown album"),
            artist = parseAlbumArtist(album),
            artworkUrl = buildImageUrl(id),
            songCount = album.optJSONObject("ItemCounts")?.optInt("MusicCount")?.takeIf { it > 0 }
                ?: album.optInt("SongCount").takeIf { it > 0 }
                ?: album.optInt("ChildCount", 0),
            year = album.optInt("ProductionYear").takeIf { it > 0 },
            description = album.optString("Overview").takeIf { it.isNotBlank() }
        )
    }

    private fun parseArtistItems(response: JSONObject): List<ProviderArtist> {
        val items = response.optJSONArray("Items") ?: response.optJSONArray("artists") ?: response.optJSONArray("Items")
        return buildList {
            for (i in 0 until (items?.length() ?: 0)) {
                val artist = items?.optJSONObject(i) ?: continue
                val id = artist.optString("Id", artist.optString("id", ""))
                if (id.isBlank()) continue

                add(
                    ProviderArtist(
                        providerId = id,
                        name = artist.optString("Name", artist.optString("name", "Unknown artist")),
                        artworkUrl = buildImageUrl(id),
                        songCount = artist.optInt("SongCount", artist.optInt("songCount", 0)),
                        albumCount = artist.optInt("AlbumCount", artist.optInt("albumCount", 0)),
                        description = artist.optString("Overview").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    private fun parseAlbumArtist(album: JSONObject): String {
        val albumArtist = album.optString("AlbumArtist", "").trim()
        if (albumArtist.isNotBlank()) {
            return albumArtist
        }

        val artists = album.optJSONArray("Artists")
        if (artists != null) {
            for (i in 0 until artists.length()) {
                val item = artists.opt(i)
                val name = when (item) {
                    is String -> item.trim()
                    is JSONObject -> item.optString("Name", item.optString("name", "")).trim()
                    else -> ""
                }
                if (name.isNotBlank()) return name
            }
        }

        val artistItems = album.optJSONArray("ArtistItems")
        if (artistItems != null) {
            for (i in 0 until artistItems.length()) {
                val obj = artistItems.optJSONObject(i) ?: continue
                val name = obj.optString("Name", obj.optString("name", "")).trim()
                if (name.isNotBlank()) return name
            }
        }

        return "Unknown artist"
    }

    private fun parsePlaylistItems(response: JSONObject): List<ProviderPlaylist> {
        val items = response.optJSONArray("Items")
        return buildList {
            for (i in 0 until (items?.length() ?: 0)) {
                val playlist = items?.optJSONObject(i) ?: continue
                val id = playlist.optString("Id", "")
                if (id.isBlank()) continue

                val name = playlist.optString("Name", "Unknown playlist")
                val description = playlist.optString("Overview").takeIf { it.isNotBlank() }
                val itemCounts = playlist.optJSONObject("ItemCounts")
                val songCount = itemCounts?.optInt("MusicCount", 0) ?: 0

                add(
                    ProviderPlaylist(
                        providerId = id,
                        name = name,
                        description = description,
                        artworkUrl = buildImageUrl(id),
                        songCount = songCount,
                        isPublic = true
                    )
                )
            }
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun buildAuthorizationHeader(token: String?): String {
        val tokenPart = if (!token.isNullOrBlank()) ", Token=\"$token\"" else ""
        return "MediaBrowser Client=\"$CLIENT_NAME\", Device=\"$DEVICE_NAME\", DeviceId=\"$DEVICE_ID\", Version=\"$CLIENT_VERSION\"$tokenPart"
    }

    private fun loadCredentials(): Credentials? {
        val server = prefs.getString(KEY_SERVER_URL, null).orEmpty().trim().trimEnd('/')
        val user = prefs.getString(KEY_USERNAME, null).orEmpty().trim()
        val token = prefs.getString(KEY_ACCESS_TOKEN, null).orEmpty().trim()
        val userId = prefs.getString(KEY_USER_ID, null).orEmpty().trim()

        if (server.isBlank() || user.isBlank() || token.isBlank() || userId.isBlank()) {
            return null
        }

        return Credentials(serverUrl = server, username = user, accessToken = token, userId = userId)
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
        private const val TAG = "JellyfinApiClient"
        private const val PREFS_NAME = "streaming_jellyfin_credentials"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"

        private const val CLIENT_NAME = "Rhythm"
        private const val CLIENT_VERSION = "1.0"
        private const val DEVICE_NAME = "Android"
        private const val DEVICE_ID = "Rhythm-Android"
    }
}
