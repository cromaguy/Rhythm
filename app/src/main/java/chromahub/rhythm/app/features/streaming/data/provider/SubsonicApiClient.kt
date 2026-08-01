package chromahub.rhythm.app.features.streaming.data.provider

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class SubsonicErrorException(val code: Int, message: String) : Exception(message)

/**
 * Subsonic-compatible API client used for Navidrome/Subsonic service support.
 */
class SubsonicApiClient(context: Context) {

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

    private val okHttpClient = UserTrustManager.buildUserTrustingHttpClientBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

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
                prefs.edit()
                    .putString(KEY_SERVER_URL, normalizedUrl)
                    .putString(KEY_USERNAME, username.trim())
                    .putString(KEY_PASSWORD, password)
                    .putBoolean(KEY_USE_PASSWORD_AUTH, usePasswordAuth)
                    .apply()
            } else {
                prefs.edit().clear().apply()
            }
            ProviderConnectionResult(displayName = username.trim(), serverUrl = normalizedUrl)
        }.onFailure {
            credentials = null
        }
    }

    fun logout() {
        credentials = null
        usePasswordAuth = false
        prefs.edit().clear().apply()
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

        return requestAndParse("search3", params).map { response ->
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

        return requestAndParse("search3", params).map { response ->
            parseAlbumListCompat(response.optJSONObject("searchResult3")?.opt("album"))
        }
    }

    suspend fun getArtists(): Result<List<ProviderArtist>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        return requestAndParse("getArtists", emptyMap()).map { response ->
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

        return requestAndParse("search3", params).map { response ->
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

        return requestAndParse("getSimilarSongs2", params).map { response ->
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

        return requestAndParse("getArtistInfo2", params).map { response ->
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

        return requestAndParse("getRandomSongs", params).map { response ->
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

        return requestAndParse("getAlbumList2", params).map { response ->
            parseAlbumListCompat(response.optJSONObject("albumList2")?.opt("album") ?: response.optJSONObject("albumList")?.opt("album"))
        }
    }

    suspend fun fetchLibrarySongs(limit: Int = 5_000): Result<List<ProviderSong>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val albumBatchSize = 200
                var albumOffset = 0
                val songs = LinkedHashMap<String, ProviderSong>()

                while (songs.size < limit) {
                    val albumResult = requestAndParse(
                        "getAlbumList2",
                        mapOf(
                            "type" to "alphabeticalByArtist",
                            "size" to albumBatchSize.toString(),
                            "offset" to albumOffset.toString()
                        )
                    )
                    val responseObj = albumResult.getOrNull()
                    val albumList = responseObj?.optJSONObject("albumList2") ?: responseObj?.optJSONObject("albumList")
                    val albums = parseAlbumListCompat(albumList?.opt("album"))
                    if (albums.isEmpty()) break

                    for (album in albums) {
                        val albumId = album.providerId
                        if (albumId.isBlank()) continue

                        val albumResponse = requestAndParse("getAlbum", mapOf("id" to albumId)).getOrNull()
                            ?.optJSONObject("album")
                            ?: continue

                        val albumSongs = parseSongList(albumResponse.opt("song"))
                        for (song in albumSongs) {
                            songs.putIfAbsent(song.providerId, song)
                            if (songs.size >= limit) {
                                break
                            }
                        }

                        if (songs.size >= limit) {
                            break
                        }
                    }

                    albumOffset += albums.size
                    if (albums.size < albumBatchSize) break
                }

                Result.success(songs.values.take(limit).toList())
            } catch (e: Exception) {
                Log.e(TAG, "Subsonic library fetch failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getPlaylists(limit: Int = 100): Result<List<ProviderPlaylist>> {
        if (!isConnected()) {
            return Result.failure(IllegalStateException("Subsonic service is not connected"))
        }

        return requestAndParse("getPlaylists", emptyMap()).map { response ->
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

        return requestAndParse("getPlaylist", mapOf("id" to playlistId)).map { response ->
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

        return requestAndParse("getAlbum", mapOf("id" to albumId)).map { response ->
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

        return requestAndParse("getAlbum", mapOf("id" to albumId)).map { response ->
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

        return requestAndParse("getArtistInfo2", params).map { response ->
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

        return requestAndParse("getSimilarSongs2", params).map { response ->
            parseSongList(response.optJSONObject("similarSongs2")?.opt("song") ?: response.optJSONObject("similarSongs")?.opt("song"))
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

    fun buildStreamUrl(songId: String, maxBitRateKbps: Int = 0, format: String? = null): String? {
        val cred = credentials ?: return null
        if (songId.isBlank()) return null

        val parsedUrl = "${cred.serverUrl}/rest/stream.view".toHttpUrlOrNull() ?: return null
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

        if (maxBitRateKbps > 0) {
            urlBuilder.addQueryParameter("maxBitRate", maxBitRateKbps.toString())
        }
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
            val obfuscated = "enc:" + cred.password.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
            urlBuilder.addQueryParameter("p", obfuscated)
        } else {
            val (token, salt) = generateAuthParams(cred.password)
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
    ): Result<JSONObject> {
        val result = request(endpoint, params, listParams).fold(
            onSuccess = { parseSubsonicResponse(it) },
            onFailure = { Result.failure(it) }
        )
        if (result.isFailure) {
            val exception = result.exceptionOrNull()
            if (exception is SubsonicErrorException && exception.code == 41 && !usePasswordAuth) {
                usePasswordAuth = true
                if (isConnected()) {
                    prefs.edit().putBoolean(KEY_USE_PASSWORD_AUTH, true).apply()
                }
                return request(endpoint, params, listParams).fold(
                    onSuccess = { parseSubsonicResponse(it) },
                    onFailure = { Result.failure(it) }
                )
            }
        }
        return result
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
            val obfuscated = "enc:" + cred.password.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
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

        val coverArtId = song.optString("coverArt").takeIf { it.isNotBlank() }
        
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

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
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
        private const val CLIENT_ID = "Rhythm"
    }
}
