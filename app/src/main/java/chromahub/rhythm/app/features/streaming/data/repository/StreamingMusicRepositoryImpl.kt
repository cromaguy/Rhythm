/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import android.content.Context
import android.util.Log
import chromahub.rhythm.app.core.domain.model.AlbumItem
import chromahub.rhythm.app.core.domain.model.ArtistItem
import chromahub.rhythm.app.core.domain.model.PlayableItem
import chromahub.rhythm.app.core.domain.model.PlaylistItem
import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.core.utils.NetworkUtils
import chromahub.rhythm.app.features.streaming.data.provider.JellyfinApiClient
import chromahub.rhythm.app.features.streaming.data.provider.ProviderConnectionResult
import chromahub.rhythm.app.features.streaming.data.provider.ProviderAlbum
import chromahub.rhythm.app.features.streaming.data.provider.ProviderArtist
import chromahub.rhythm.app.features.streaming.data.provider.ProviderPlaylist
import chromahub.rhythm.app.features.streaming.data.provider.ProviderSong
import chromahub.rhythm.app.features.streaming.data.provider.SubsonicApiClient
import chromahub.rhythm.app.features.streaming.data.provider.UserTrustManager
import chromahub.rhythm.app.features.streaming.domain.model.BrowseCategory
import chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum
import chromahub.rhythm.app.features.streaming.domain.model.StreamingArtist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingServiceId
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import chromahub.rhythm.app.features.streaming.domain.repository.StreamingMusicRepository
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.network.NetworkClient
import chromahub.rhythm.app.network.DeezerApiService
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import chromahub.rhythm.app.util.ArtistSeparator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * Provider-backed implementation used by Rhythm GO mode.
 */
class StreamingMusicRepositoryImpl(
    private val context: Context
) : StreamingMusicRepository {

    data class ServiceConnectionInfo(
        val displayName: String,
        val serverUrl: String
    )

    private val appSettings = AppSettings.getInstance(context)

    private val subsonicClient = SubsonicApiClient(context)
    private val jellyfinClient = JellyfinApiClient(context)

    private val songsFlow = MutableStateFlow<List<PlayableItem>>(emptyList())
    private val albumsFlow = MutableStateFlow<List<AlbumItem>>(emptyList())
    private val providerAlbumsFlow = MutableStateFlow<List<StreamingAlbum>>(emptyList())  // Keep provider albums separate
    private val artistsFlow = MutableStateFlow<List<ArtistItem>>(emptyList())
    private val playlistsFlow = MutableStateFlow<List<PlaylistItem>>(emptyList())

    private val likedSongsFlow = MutableStateFlow<List<StreamingSong>>(emptyList())
    private val followedArtistsFlow = MutableStateFlow<List<StreamingArtist>>(emptyList())
    private val savedAlbumsFlow = MutableStateFlow<List<StreamingAlbum>>(emptyList())
    private val downloadedSongsFlow = MutableStateFlow<List<StreamingSong>>(emptyList())

    private val likedSongIds = linkedSetOf<String>()
    private val followedArtistIds = linkedSetOf<String>()
    private val savedAlbumIds = linkedSetOf<String>()
    private val followedPlaylistIds = linkedSetOf<String>()

    private val songCache = LinkedHashMap<String, StreamingSong>()
    private val artistArtworkCache = LinkedHashMap<String, String>()
    private val providerAlbumCache = LinkedHashMap<String, StreamingAlbum>()

    private val downloadDirectory by lazy {
        java.io.File(context.filesDir, "streaming_downloads").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private val downloadedSongsMap = LinkedHashMap<String, StreamingSong>()
    private val gson = com.google.gson.Gson()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        loadDownloadedSongsIndex()
    }

    private fun loadDownloadedSongsIndex() {
        try {
            val indexFile = java.io.File(downloadDirectory, "downloaded_songs.json")
            if (indexFile.exists()) {
                val json = indexFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<List<StreamingSong>>() {}.type
                val list: List<StreamingSong> = gson.fromJson(json, type) ?: emptyList()
                downloadedSongsMap.clear()
                list.forEach { song ->
                    downloadedSongsMap[song.id] = song
                }
                downloadedSongsFlow.value = list
            } else {
                downloadedSongsMap.clear()
                downloadedSongsFlow.value = emptyList()
            }
        } catch (e: Exception) {
            Log.e("StreamingMusicRepo", "Error loading downloaded songs index", e)
        }
    }

    private fun saveDownloadedSongsIndex() {
        val list = downloadedSongsMap.values.toList()
        downloadedSongsFlow.value = list
        try {
            val indexFile = java.io.File(downloadDirectory, "downloaded_songs.json")
            val json = gson.toJson(list)
            indexFile.writeText(json)
        } catch (e: Exception) {
            Log.e("StreamingMusicRepo", "Error saving downloaded songs index", e)
        }
    }

    private fun getDownloadFile(songId: String): java.io.File {
        val safeName = songId.replace(":", "_").replace("/", "_").replace("\\", "_")
        return java.io.File(downloadDirectory, "$safeName.mp3")
    }

    override val currentService: SourceType
        get() = serviceToSourceType(activeServiceId())

    suspend fun connect(
        serviceId: String,
        serverUrl: String,
        username: String,
        password: String,
        saveCredentials: Boolean = true
    ): ServiceConnectionInfo {
        val normalizedService = normalizeServiceId(serviceId)

        val result = when (normalizedService) {
            StreamingServiceId.SUBSONIC -> subsonicClient.login(serverUrl, username, password, saveCredentials)
            StreamingServiceId.JELLYFIN -> jellyfinClient.login(serverUrl, username, password, saveCredentials)
            else -> Result.failure(IllegalArgumentException("Unsupported streaming service"))
        }

        val connection = result.getOrElse { throw it }
        return ServiceConnectionInfo(
            displayName = connection.displayName,
            serverUrl = connection.serverUrl
        )
    }

    suspend fun disconnect(serviceId: String) {
        when (normalizeServiceId(serviceId)) {
            StreamingServiceId.SUBSONIC -> subsonicClient.logout()
            StreamingServiceId.JELLYFIN -> jellyfinClient.logout()
        }

        if (activeServiceId() == normalizeServiceId(serviceId)) {
            clearInMemoryCatalog()
        }
    }

    fun isServiceConnected(serviceId: String): Boolean {
        return when (normalizeServiceId(serviceId)) {
            StreamingServiceId.SUBSONIC -> subsonicClient.isConnected()
            StreamingServiceId.JELLYFIN -> jellyfinClient.isConnected()
            else -> false
        }
    }

    override suspend fun isAuthenticated(): Boolean {
        return isServiceConnected(activeServiceId())
    }

    override suspend fun authenticate(): Boolean {
        return when (activeServiceId()) {
            StreamingServiceId.SUBSONIC -> subsonicClient.ping().isSuccess
            StreamingServiceId.JELLYFIN -> jellyfinClient.ping().isSuccess
            else -> false
        }
    }

    override suspend fun logout() {
        disconnect(activeServiceId())
    }

    override suspend fun getRecommendations(limit: Int): List<StreamingSong> {
        if (appSettings.offlineMode.value) {
            return downloadedSongsMap.values.shuffled().take(limit.coerceAtLeast(1))
        }
        val activePrefix = "${activeServiceId()}::"
        return songCache.values
            .asSequence()
            .filter { it.id.startsWith(activePrefix) }
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    override suspend fun getNewReleases(limit: Int): List<StreamingAlbum> {
        if (appSettings.offlineMode.value) {
            return deriveAlbumsFromSongs(downloadedSongsMap.values.toList(), limit)
        }
        if (!isServiceConnected(activeServiceId())) {
            return emptyList()
        }
        
        return when (activeServiceId()) {
            StreamingServiceId.SUBSONIC -> {
                subsonicClient.getAlbumList("newest", limit).getOrNull() ?: emptyList()
            }
            StreamingServiceId.JELLYFIN -> {
                jellyfinClient.getAlbumList("newest", limit).getOrNull() ?: emptyList()
            }
            else -> emptyList()
        }.mapNotNull { providerAlbum ->
            try {
                mapProviderAlbum(activeServiceId(), providerAlbum)
            } catch (e: Exception) {
                null
            }
        }.let { albums ->
            enrichAlbumsWithTrackCounts(activeServiceId(), albums)
        }
    }

    override suspend fun getFeaturedPlaylists(limit: Int): List<StreamingPlaylist> {
        return playlistsFlow.value
            .filterIsInstance<StreamingPlaylist>()
            .take(limit.coerceAtLeast(1))
    }

    override suspend fun getBrowseCategories(): List<BrowseCategory> {
        return listOf(
            BrowseCategory(id = "recent", name = "Recent"),
            BrowseCategory(id = "favorites", name = "Favorites"),
            BrowseCategory(id = "library", name = "Library")
        )
    }

    override suspend fun getCategoryPlaylists(categoryId: String, limit: Int): List<StreamingPlaylist> {
        val normalizedCategory = categoryId.trim().lowercase()
        val safeLimit = limit.coerceAtLeast(1)
        val playlists = playlistsFlow.value.filterIsInstance<StreamingPlaylist>()

        return when (normalizedCategory) {
            "recent", "library" -> playlists.take(safeLimit)
            "favorites" -> playlists.filter { it.id in followedPlaylistIds }.take(safeLimit)
            else -> playlists.filter {
                it.name.contains(categoryId, ignoreCase = true) ||
                    (it.description?.contains(categoryId, ignoreCase = true) == true)
            }.take(safeLimit).ifEmpty { playlists.take(safeLimit) }
        }
    }

    override suspend fun getTopCharts(limit: Int): List<StreamingSong> {
        return getRecommendations(limit)
    }

    override fun getLikedSongs(): Flow<List<StreamingSong>> = likedSongsFlow.asStateFlow()

    override suspend fun likeSong(songId: String): Boolean {
        if (appSettings.offlineMode.value) return false
        val decoded = decodeSongId(songId)
        if (decoded != null && isServiceConnected(decoded.first)) {
            val (serviceId, providerId) = decoded
            val success = when (serviceId) {
                StreamingServiceId.SUBSONIC -> subsonicClient.markFavorite(providerId, true).isSuccess
                StreamingServiceId.JELLYFIN -> jellyfinClient.markFavorite(providerId, true).isSuccess
                else -> false
            }
            if (!success) return false
        }
        
        likedSongIds.add(songId)
        updateLikedSongsFlow()
        return true
    }

    override suspend fun unlikeSong(songId: String): Boolean {
        if (appSettings.offlineMode.value) return false
        val decoded = decodeSongId(songId)
        if (decoded != null && isServiceConnected(decoded.first)) {
            val (serviceId, providerId) = decoded
            val success = when (serviceId) {
                StreamingServiceId.SUBSONIC -> subsonicClient.markFavorite(providerId, false).isSuccess
                StreamingServiceId.JELLYFIN -> jellyfinClient.markFavorite(providerId, false).isSuccess
                else -> false
            }
            if (!success) return false
        }
        
        val removed = likedSongIds.remove(songId)
        updateLikedSongsFlow()
        return removed
    }

    override suspend fun isSongLiked(songId: String): Boolean = likedSongIds.contains(songId)

    override suspend fun followArtist(artistId: String): Boolean {
        followedArtistIds.add(artistId)
        updateFollowedArtistsFlow()
        return true
    }

    override suspend fun unfollowArtist(artistId: String): Boolean {
        val removed = followedArtistIds.remove(artistId)
        updateFollowedArtistsFlow()
        return removed
    }

    override suspend fun isArtistFollowed(artistId: String): Boolean = followedArtistIds.contains(artistId)

    override fun getFollowedArtists(): Flow<List<StreamingArtist>> = followedArtistsFlow.asStateFlow()

    override suspend fun saveAlbum(albumId: String): Boolean {
        if (appSettings.offlineMode.value) return false
        val decoded = decodeAlbumId(albumId)
        if (decoded != null && isServiceConnected(decoded.serviceId)) {
            val serviceId = decoded.serviceId
            var providerId = decoded.providerAlbumId
            if (providerId.isNullOrBlank()) {
                val matchedAlbum = albumsFlow.value.firstOrNull { it.id == albumId } as? StreamingAlbum
                providerId = matchedAlbum?.externalId
            }
            if (!providerId.isNullOrBlank()) {
                val success = when (serviceId) {
                    StreamingServiceId.SUBSONIC -> subsonicClient.markFavorite(providerId, true).isSuccess
                    StreamingServiceId.JELLYFIN -> jellyfinClient.markFavorite(providerId, true).isSuccess
                    else -> false
                }
                if (!success) return false
            }
        }

        savedAlbumIds.add(albumId)
        updateSavedAlbumsFlow()
        return true
    }

    override suspend fun unsaveAlbum(albumId: String): Boolean {
        if (appSettings.offlineMode.value) return false
        val decoded = decodeAlbumId(albumId)
        if (decoded != null && isServiceConnected(decoded.serviceId)) {
            val serviceId = decoded.serviceId
            var providerId = decoded.providerAlbumId
            if (providerId.isNullOrBlank()) {
                val matchedAlbum = albumsFlow.value.firstOrNull { it.id == albumId } as? StreamingAlbum
                providerId = matchedAlbum?.externalId
            }
            if (!providerId.isNullOrBlank()) {
                val success = when (serviceId) {
                    StreamingServiceId.SUBSONIC -> subsonicClient.markFavorite(providerId, false).isSuccess
                    StreamingServiceId.JELLYFIN -> jellyfinClient.markFavorite(providerId, false).isSuccess
                    else -> false
                }
                if (!success) return false
            }
        }

        val removed = savedAlbumIds.remove(albumId)
        updateSavedAlbumsFlow()
        return removed
    }

    override fun getSavedAlbums(): Flow<List<StreamingAlbum>> = savedAlbumsFlow.asStateFlow()

    override suspend fun followPlaylist(playlistId: String): Boolean {
        if (appSettings.offlineMode.value) return false
        val playlist = getPlaylistById(playlistId) ?: return false
        followedPlaylistIds.add(playlist.id)
        return true
    }

    override suspend fun unfollowPlaylist(playlistId: String): Boolean {
        if (appSettings.offlineMode.value) return false
        return deletePlaylist(playlistId)
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String): Boolean {
        if (appSettings.offlineMode.value) return false
        val decodedPlaylist = decodePlaylistId(playlistId) ?: return false
        val serviceId = decodedPlaylist.first
        val providerPlaylistId = decodedPlaylist.second

        if (!isServiceConnected(serviceId)) {
            return false
        }

        val trimmedName = newName.trim()
        if (providerPlaylistId.isBlank() || trimmedName.isBlank()) {
            return false
        }

        return when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.updatePlaylist(providerPlaylistId, name = trimmedName).isSuccess
            StreamingServiceId.JELLYFIN -> jellyfinClient.updatePlaylist(providerPlaylistId, name = trimmedName).isSuccess
            else -> false
        }.also { success ->
            if (success) {
                syncPlaylists()
            }
        }
    }

    override suspend fun deletePlaylist(playlistId: String): Boolean {
        if (appSettings.offlineMode.value) return false
        val decodedPlaylist = decodePlaylistId(playlistId) ?: return false
        val serviceId = decodedPlaylist.first
        val providerPlaylistId = decodedPlaylist.second

        if (!isServiceConnected(serviceId)) {
            return false
        }

        return when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.deletePlaylist(providerPlaylistId).isSuccess
            StreamingServiceId.JELLYFIN -> jellyfinClient.deletePlaylist(providerPlaylistId).isSuccess
            else -> false
        }.also { success ->
            if (success) {
                syncPlaylists()
            }
        }
    }

    override suspend fun createPlaylist(
        name: String,
        description: String?,
        isPublic: Boolean
    ): StreamingPlaylist? {
        if (appSettings.offlineMode.value) return null
        val serviceId = activeServiceId()
        if (!isServiceConnected(serviceId)) {
            return null
        }

        val createdPlaylist = when (serviceId) {
            StreamingServiceId.JELLYFIN -> jellyfinClient.createPlaylist(
                name = name,
                songIds = emptyList(),
                description = description,
                isPublic = isPublic
            ).getOrNull()

            StreamingServiceId.SUBSONIC -> subsonicClient.createPlaylist(
                name = name,
                songIds = emptyList()
            ).getOrNull()
            else -> null
        } ?: return null

        val mappedPlaylist = mapProviderPlaylist(serviceId, createdPlaylist)
        playlistsFlow.value = (playlistsFlow.value.filterNot { it.id == mappedPlaylist.id } + mappedPlaylist)
            .sortedBy { it.name.lowercase() }
        return mappedPlaylist
    }

    override suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>): Boolean {
        if (appSettings.offlineMode.value) return false
        val decodedPlaylist = decodePlaylistId(playlistId) ?: return false
        val serviceId = decodedPlaylist.first
        val providerPlaylistId = decodedPlaylist.second
        val providerSongIds = songIds.mapNotNull { decodeSongId(it)?.second ?: it }.filter { it.isNotBlank() }

        if (providerSongIds.isEmpty() || !isServiceConnected(serviceId)) {
            return false
        }

        val success = when (serviceId) {
            StreamingServiceId.JELLYFIN -> jellyfinClient.addSongsToPlaylist(providerPlaylistId, providerSongIds).isSuccess
            StreamingServiceId.SUBSONIC -> subsonicClient.updatePlaylist(providerPlaylistId, songIdsToAdd = providerSongIds).isSuccess
            else -> false
        }

        if (success) {
            syncPlaylists()
        }

        return success
    }

    override suspend fun removeSongsFromPlaylist(playlistId: String, songIds: List<String>): Boolean {
        if (appSettings.offlineMode.value) return false
        val decodedPlaylist = decodePlaylistId(playlistId) ?: return false
        val serviceId = decodedPlaylist.first
        val providerPlaylistId = decodedPlaylist.second
        val providerSongIds = songIds.mapNotNull { decodeSongId(it)?.second ?: it }.filter { it.isNotBlank() }

        if (providerSongIds.isEmpty() || !isServiceConnected(serviceId)) {
            return false
        }

        val success = when (serviceId) {
            StreamingServiceId.JELLYFIN -> jellyfinClient.removeSongsFromPlaylist(providerPlaylistId, providerSongIds).isSuccess
            StreamingServiceId.SUBSONIC -> {
                val songsResult = subsonicClient.getPlaylistSongs(providerPlaylistId, limit = 1000)
                val songs = songsResult.getOrElse { emptyList() }
                val indexesToRemove = providerSongIds.mapNotNull { id -> 
                    val index = songs.indexOfFirst { it.providerId == id }
                    if (index >= 0) index else null
                }
                if (indexesToRemove.isNotEmpty()) {
                    subsonicClient.updatePlaylist(providerPlaylistId, songIndexesToRemove = indexesToRemove).isSuccess
                } else false
            }
            else -> false
        }

        if (success) {
            syncPlaylists()
        }

        return success
    }

    override suspend fun getStreamingUrl(songId: String): String? {
        val (serviceId, providerId) = decodeSongId(songId) ?: return null
        
        // 1. If downloaded, return the local downloaded file URI
        val localFile = getDownloadFile(songId)
        if (localFile.exists() && localFile.length() > 0 && downloadedSongsMap.containsKey(songId)) {
            return Uri.fromFile(localFile).toString()
        }

        // 2. Check if offline mode is enabled - fallback to cache or return null
        if (appSettings.offlineMode.value) {
            // In offline mode, only allow cached/downloaded files
            // If not downloaded, but we have a previously cached stream URL, return it
            val cached = songCache[songId]
            return cached?.streamingUrl // May be null if not previously cached
        }

        // Check if streaming is allowed by network settings
        val allowCellular = appSettings.allowCellularStreaming.value
        if (!NetworkUtils.canStream(context, allowCellular)) {
            return null // Network conditions don't allow streaming
        }
        
        // Check if service is connected
        if (!isServiceConnected(serviceId)) {
            return null // Provider is not connected
        }
        
        val bitrate = desiredBitrateKbps()

        val resolved = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.buildStreamUrl(providerId, bitrate)
            StreamingServiceId.JELLYFIN -> jellyfinClient.buildStreamUrl(providerId, bitrate)
            else -> null
        }

        if (!resolved.isNullOrBlank()) {
            val existing = songCache[songId]
            if (existing != null) {
                songCache[songId] = existing.copy(streamingUrl = resolved)
            }
        }

        return resolved
    }

    override suspend fun reportPlaybackStart(songId: String): Boolean {
        val decoded = decodeSongId(songId) ?: return false
        val (serviceId, providerId) = decoded
        if (!isServiceConnected(serviceId)) return false
        
        return when (serviceId) {
            StreamingServiceId.JELLYFIN -> jellyfinClient.reportPlaybackStart(providerId).isSuccess
            StreamingServiceId.SUBSONIC -> true // Subsonic typically only scrobbles during/at the end
            else -> false
        }
    }

    override suspend fun reportPlaybackStop(songId: String, positionMs: Long): Boolean {
        val decoded = decodeSongId(songId) ?: return false
        val (serviceId, providerId) = decoded
        if (!isServiceConnected(serviceId)) return false
        
        return when (serviceId) {
            StreamingServiceId.JELLYFIN -> jellyfinClient.reportPlaybackStop(providerId, positionMs * 10_000L).isSuccess // Convert ms to ticks
            StreamingServiceId.SUBSONIC -> subsonicClient.scrobble(providerId, true).isSuccess
            else -> false
        }
    }

    /**
     * Invalidate cached streaming URL for a specific song.
     */
    fun invalidateStreamingUrlCache(songId: String) {
        songCache.remove(songId)
    }

    /**
     * Clear all cached streaming URLs so subsequent requests re-resolve.
     */
    fun invalidateAllStreamingUrlCache() {
        songCache.clear()
    }

    override suspend fun getRelatedTracks(songId: String, limit: Int): List<StreamingSong> {
        if (appSettings.offlineMode.value) {
            val song = downloadedSongsMap[songId] ?: return emptyList()
            return downloadedSongsMap.values.filter { 
                it.artist.equals(song.artist, ignoreCase = true) && it.id != song.id 
            }.take(limit)
        }
        val decodedSong = decodeSongId(songId) ?: return emptyList()
        val serviceId = decodedSong.first
        val providerTracks = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.getRelatedTracks(decodedSong.second, limit)
            StreamingServiceId.JELLYFIN -> jellyfinClient.getRelatedTracks(decodedSong.second, limit)
            else -> Result.success(emptyList())
        }.getOrElse { emptyList() }

        return providerTracks.map { mapProviderSong(serviceId, it) }
            .take(limit.coerceAtLeast(1))
    }

    override suspend fun getArtistTopTracks(artistId: String, limit: Int): List<StreamingSong> {
        val artistName = extractArtistNameFromId(artistId)
        if (artistName.isBlank()) {
            return emptyList()
        }

        if (!appSettings.offlineMode.value) {
            val serviceId = artistId.substringBefore("::", activeServiceId())
            if (isServiceConnected(serviceId)) {
                try {
                    val providerTracksResult = when (serviceId) {
                        StreamingServiceId.SUBSONIC -> subsonicClient.getArtistTopTracks(artistName, limit)
                        StreamingServiceId.JELLYFIN -> jellyfinClient.getArtistTopTracks(artistName, limit)
                        else -> Result.success(emptyList())
                    }
                    val providerTracks = providerTracksResult.getOrNull()
                    if (providerTracks != null && providerTracks.isNotEmpty()) {
                        return providerTracks.map { mapProviderSong(serviceId, it) }.take(limit.coerceAtLeast(1))
                    }
                } catch (e: Exception) {
                    Log.w("StreamingMusicRepo", "Network getArtistTopTracks failed, falling back to offline", e)
                }
            }
        }

        // Offline / Failure Fallback:
        return cachedSongsForArtist(artistName).take(limit.coerceAtLeast(1))
    }

    override suspend fun getAlbumSongs(albumId: String): List<StreamingSong> {
        val decodedId = decodeAlbumId(albumId) ?: return emptyList()
        val providerAlbumId = decodedId.providerAlbumId
        
        var resolvedProviderAlbumId = providerAlbumId
        if (resolvedProviderAlbumId == null && !appSettings.offlineMode.value) {
            val albumTitle = decodedId.title
            val albumArtist = decodedId.artist
            if (albumTitle.isNotBlank()) {
                val searchResults = when (decodedId.serviceId) {
                    StreamingServiceId.SUBSONIC -> subsonicClient.searchAlbums(albumTitle).getOrNull()
                    StreamingServiceId.JELLYFIN -> jellyfinClient.searchAlbums(albumTitle).getOrNull()
                    else -> null
                }
                val matchedAlbum = searchResults?.firstOrNull {
                    it.title.equals(albumTitle, ignoreCase = true) &&
                    (albumArtist.isBlank() || it.artist.equals(albumArtist, ignoreCase = true))
                } ?: searchResults?.firstOrNull()
                if (matchedAlbum != null) {
                    resolvedProviderAlbumId = matchedAlbum.providerId
                }
            }
        }

        if (!appSettings.offlineMode.value && resolvedProviderAlbumId != null) {
            try {
                val providerSongs = when (decodedId.serviceId) {
                    StreamingServiceId.SUBSONIC -> subsonicClient.getAlbumSongs(resolvedProviderAlbumId).getOrNull()
                    StreamingServiceId.JELLYFIN -> jellyfinClient.getAlbumSongs(resolvedProviderAlbumId).getOrNull()
                    else -> null
                }
                if (!providerSongs.isNullOrEmpty()) {
                    return providerSongs.mapNotNull { providerSong ->
                        try {
                            mapProviderSong(decodedId.serviceId, providerSong)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("StreamingMusicRepo", "Network getAlbumSongs failed for album $albumId, falling back to offline", e)
            }
        }

        // Artist-track fallback: for singles / song-albums the server may not expose an album
        // object at all; query the artist's tracks and filter by album title instead.
        val albumTitle = decodedId.title
        val albumArtist = decodedId.artist
        if (!appSettings.offlineMode.value && albumTitle.isNotBlank() && albumArtist.isNotBlank()) {
            try {
                val artistTracks = when (decodedId.serviceId) {
                    StreamingServiceId.SUBSONIC -> subsonicClient.getArtistTopTracks(albumArtist, limit = 100).getOrNull()
                    StreamingServiceId.JELLYFIN -> jellyfinClient.getArtistTopTracks(albumArtist, limit = 100).getOrNull()
                    else -> null
                }
                if (!artistTracks.isNullOrEmpty()) {
                    val filtered = artistTracks
                        .filter { it.album.equals(albumTitle, ignoreCase = true) }
                        .map { mapProviderSong(decodedId.serviceId, it) }
                    if (filtered.isNotEmpty()) return filtered
                }
            } catch (e: Exception) {
                Log.w("StreamingMusicRepo", "Artist-track fallback failed for album $albumId", e)
            }
        }

        // Offline / Failure Fallback:
        // 1. Try matching exactly by albumId
        val matchesByAlbumId = downloadedSongsMap.values.filter { it.albumId == albumId }
        if (matchesByAlbumId.isNotEmpty()) {
            return matchesByAlbumId
        }
        
        // 2. Try matching by legacy title/artist
        if (albumTitle.isNotBlank()) {
            return downloadedSongsMap.values.filter {
                it.album.equals(albumTitle, ignoreCase = true) &&
                (it.albumArtist.equals(albumArtist, ignoreCase = true) || it.artist.equals(albumArtist, ignoreCase = true))
            }
        }
        
        return emptyList()
    }

    override suspend fun getArtistAlbums(artistId: String): List<StreamingAlbum> {
        val artistName = extractArtistNameFromId(artistId)
        if (artistName.isBlank()) return emptyList()
        
        if (!appSettings.offlineMode.value) {
            val serviceId = activeServiceId()
            if (isServiceConnected(serviceId)) {
                try {
                    val providerResult = when (serviceId) {
                        StreamingServiceId.SUBSONIC -> subsonicClient.getArtistAlbums(artistName)
                        StreamingServiceId.JELLYFIN -> jellyfinClient.getArtistAlbums(artistName)
                        else -> Result.success(emptyList())
                    }
                    val albums = providerResult.getOrNull()?.map { mapProviderAlbum(serviceId, it) }
                    if (albums != null) {
                        return enrichAlbumsWithTrackCounts(serviceId, albums)
                    }
                } catch (e: Exception) {
                    Log.w("StreamingMusicRepo", "Network getArtistAlbums failed, falling back to offline", e)
                }
            }
        }

        // Offline / Failure Fallback:
        val artistSongs = cachedSongsForArtist(artistName)
        return deriveAlbumsFromSongs(artistSongs, 100)
    }
    
    private fun extractArtistNameFromId(artistId: String): String {
        // Handle multiple ID formats:
        // "JELLYFIN::artist::artist_name" -> "artist_name"
        if (artistId.contains("::")) {
            val parts = artistId.split("::") 
            if (parts.size >= 3 && parts[1] == "artist") {
                // Format: "JELLYFIN::artist::artist_name"
                return parts[2].replace("_", " ").trim()
            }
        }
        
        // "derived:JELLYFIN:artist:artist_name" -> "artist_name"
        if (artistId.contains(":artist:")) {
            val afterArtist = artistId.substringAfter(":artist:")
            return afterArtist.replace("_", " ").trim()
        }
        
        // Fallback: assume the whole ID is a name
        return artistId.replace("_", " ").trim()
    }

            private suspend fun resolveProviderArtistId(serviceId: String, artistName: String): String? {
                val providerArtists = when (serviceId) {
                    StreamingServiceId.SUBSONIC -> subsonicClient.searchArtists(artistName, limit = 20)
                    StreamingServiceId.JELLYFIN -> jellyfinClient.searchArtists(artistName, limit = 20)
                    else -> Result.success(emptyList())
                }.getOrElse { emptyList() }

                val exactMatch = providerArtists.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                return (exactMatch ?: providerArtists.firstOrNull())?.providerId
            }

    override suspend fun getRelatedArtists(artistId: String, limit: Int): List<StreamingArtist> {
        if (appSettings.offlineMode.value) {
            return emptyList()
        }
        val artistName = extractArtistNameFromId(artistId)
        if (artistName.isBlank()) {
            return emptyList()
        }

        val serviceId = artistId.substringBefore("::", activeServiceId())
                val providerArtistId = resolveProviderArtistId(serviceId, artistName) ?: return emptyList()

        val providerArtists = when (serviceId) {
                    StreamingServiceId.SUBSONIC -> subsonicClient.getRelatedArtists(providerArtistId, limit)
                    StreamingServiceId.JELLYFIN -> jellyfinClient.getRelatedArtists(providerArtistId, limit)
            else -> Result.success(emptyList())
        }.getOrElse { emptyList() }

                return providerArtists.map { mapProviderArtist(serviceId, it) }
    }

    override suspend fun downloadSong(songId: String): Boolean = withContext(Dispatchers.IO) {
        if (isDownloaded(songId)) return@withContext true
        
        val (serviceId, providerId) = decodeSongId(songId) ?: return@withContext false
        
        // 1. Resolve stream URL directly bypassing any temporary offline modes or network checks specifically for the download.
        val bitrate = desiredBitrateKbps()
        val streamUrl = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.buildStreamUrl(providerId, bitrate)
            StreamingServiceId.JELLYFIN -> jellyfinClient.buildStreamUrl(providerId, bitrate)
            else -> null
        } ?: return@withContext false

        // 2. Fetch the song object to save its metadata
        val song = songCache[songId]
            ?: songsFlow.value.filterIsInstance<StreamingSong>().firstOrNull { it.id == songId }
            ?: likedSongsFlow.value.firstOrNull { it.id == songId }
            ?: downloadedSongsMap[songId]
            ?: return@withContext false

        // 3. Download the file using OkHttpClient with support for user-trusted and self-signed CAs
        val client = UserTrustManager.buildUserTrustingHttpClientBuilder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = okhttp3.Request.Builder().url(streamUrl).build()
        
        val file = getDownloadFile(songId)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body
                
                body.byteStream().use { inputStream ->
                    file.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            
            // 4. Download and cache the artwork if present and not a local file
            var localArtworkPath: String? = null
            val artworkUri = song.artworkUri
            if (!artworkUri.isNullOrBlank() && (artworkUri.startsWith("http://") || artworkUri.startsWith("https://"))) {
                val artworkFile = java.io.File(downloadDirectory, "${songId.replace(":", "_").replace("/", "_").replace("\\", "_")}_art.jpg")
                val artRequest = okhttp3.Request.Builder().url(artworkUri).build()
                try {
                    client.newCall(artRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body.byteStream().use { artInput ->
                                artworkFile.outputStream().use { artOutput ->
                                    artInput.copyTo(artOutput)
                                }
                            }
                            localArtworkPath = Uri.fromFile(artworkFile).toString()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("StreamingMusicRepo", "Error downloading artwork for song $songId", e)
                }
            }

            // 5. Update metadata index
            val localSongUrl = Uri.fromFile(file).toString()
            val downloadedSong = song.copy(
                streamingUrl = localSongUrl,
                artworkUri = localArtworkPath ?: song.artworkUri
            )
            
            downloadedSongsMap[songId] = downloadedSong
            saveDownloadedSongsIndex()
            true
        } catch (e: Exception) {
            Log.e("StreamingMusicRepo", "Error downloading song $songId", e)
            if (file.exists()) {
                file.delete()
            }
            false
        }
    }

    override suspend fun removeDownload(songId: String): Boolean = withContext(Dispatchers.IO) {
        val file = getDownloadFile(songId)
        val deletedFile = if (file.exists()) file.delete() else true
        
        // Also delete downloaded artwork if exists
        val artworkFile = java.io.File(downloadDirectory, "${songId.replace(":", "_").replace("/", "_").replace("\\", "_")}_art.jpg")
        if (artworkFile.exists()) {
            artworkFile.delete()
        }
        
        val removedFromIndex = downloadedSongsMap.remove(songId) != null
        if (removedFromIndex) {
            saveDownloadedSongsIndex()
        }
        
        deletedFile || removedFromIndex
    }

    override suspend fun isDownloaded(songId: String): Boolean = withContext(Dispatchers.IO) {
        val file = getDownloadFile(songId)
        file.exists() && file.length() > 0 && downloadedSongsMap.containsKey(songId)
    }

    override fun getDownloadedSongs(): Flow<List<StreamingSong>> = downloadedSongsFlow.asStateFlow()

    override suspend fun getRandomSongs(limit: Int): List<StreamingSong> {
        if (appSettings.offlineMode.value) {
            return downloadedSongsMap.values.shuffled().take(limit.coerceAtLeast(1))
        }
        val serviceId = activeServiceId()
        if (!isServiceConnected(serviceId)) {
            return emptyList()
        }

        val providerResult: Result<List<ProviderSong>> = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.getRandomSongs(limit)
            StreamingServiceId.JELLYFIN -> jellyfinClient.getRandomSongs(limit)
            else -> Result.success(emptyList())
        }

        return providerResult.getOrElse { emptyList() }
            .map { mapProviderSong(serviceId, it) }
    }

    override suspend fun getAlbumList(type: String, limit: Int): List<StreamingAlbum> {
        if (!appSettings.offlineMode.value) {
            val serviceId = activeServiceId()
            if (isServiceConnected(serviceId)) {
                try {
                    val providerResult = when (serviceId) {
                        StreamingServiceId.SUBSONIC -> subsonicClient.getAlbumList(type, limit)
                        StreamingServiceId.JELLYFIN -> jellyfinClient.getAlbumList(type, limit)
                        else -> Result.success(emptyList())
                    }
                    val albums = providerResult.getOrNull()?.map { mapProviderAlbum(serviceId, it) }
                    if (albums != null) {
                        return enrichAlbumsWithTrackCounts(serviceId, albums)
                    }
                } catch (e: Exception) {
                    Log.w("StreamingMusicRepo", "Network getAlbumList failed, falling back to offline", e)
                }
            }
        }

        // Offline / Failure Fallback:
        return deriveAlbumsFromSongs(downloadedSongsMap.values.toList(), limit)
    }

    private suspend fun enrichAlbumsWithTrackCounts(
        serviceId: String,
        albums: List<StreamingAlbum>
    ): List<StreamingAlbum> {
        return withContext(Dispatchers.IO) {
            albums.map { album ->
                val providerAlbumId = album.externalId
                if (providerAlbumId.isNullOrBlank()) {
                    album
                } else {
                    val providerSongs = when (serviceId) {
                        StreamingServiceId.SUBSONIC -> subsonicClient.getAlbumSongs(providerAlbumId).getOrNull()
                        StreamingServiceId.JELLYFIN -> jellyfinClient.getAlbumSongs(providerAlbumId).getOrNull()
                        else -> null
                    }.orEmpty()

                    val tracks = providerSongs.mapNotNull { providerSong ->
                        try {
                            mapProviderSong(serviceId, providerSong)
                        } catch (_: Exception) {
                            null
                        }
                    }

                    album.copy(
                        songCount = tracks.size,
                        tracks = tracks
                    )
                }
            }
        }
    }

    override suspend fun searchSongs(query: String): List<PlayableItem> {
        if (appSettings.offlineMode.value) {
            val results = downloadedSongsMap.values.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
            mergeCatalog(results)
            return results
        }
        val serviceId = activeServiceId()
        if (!isServiceConnected(serviceId)) {
            clearInMemoryCatalog()
            return emptyList()
        }

        val providerResult: Result<List<ProviderSong>> = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.searchSongs(query, SEARCH_LIMIT)
            StreamingServiceId.JELLYFIN -> jellyfinClient.searchSongs(query, SEARCH_LIMIT)
            else -> Result.success(emptyList())
        }

        val mappedSongs = providerResult.getOrElse { emptyList() }
            .map { mapProviderSong(serviceId, it) }

        mergeCatalog(mappedSongs)
        return mappedSongs
    }

    override suspend fun searchAlbums(query: String): List<AlbumItem> {
        if (appSettings.offlineMode.value) {
            return deriveAlbumsFromSongs(downloadedSongsMap.values.toList(), SEARCH_LIMIT).filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
            }
        }
        val serviceId = activeServiceId()
        if (!isServiceConnected(serviceId)) {
            return emptyList()
        }
        val providerResult = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.searchAlbums(query, SEARCH_LIMIT)
            StreamingServiceId.JELLYFIN -> jellyfinClient.searchAlbums(query, SEARCH_LIMIT)
            else -> Result.success(emptyList())
        }
        return providerResult.getOrElse { emptyList() }.map { 
            mapProviderAlbum(serviceId, it)
        }
    }

    override suspend fun searchArtists(query: String): List<ArtistItem> {
        if (appSettings.offlineMode.value) {
            val songs = searchSongs(query).filterIsInstance<StreamingSong>()
            return buildDerivedArtistItems(activeServiceId(), songs)
        }
        val serviceId = activeServiceId()
        if (!isServiceConnected(serviceId)) {
            return emptyList()
        }

        val providerArtists = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.searchArtists(query)
            StreamingServiceId.JELLYFIN -> jellyfinClient.searchArtists(query)
            else -> Result.success(emptyList())
        }.getOrElse { emptyList() }

        if (providerArtists.isNotEmpty()) {
            return providerArtists.map { mapProviderArtist(serviceId, it) }
        }

        val songs = searchSongs(query).filterIsInstance<StreamingSong>()
        return buildDerivedArtistItems(serviceId, songs)
    }

    override suspend fun searchPlaylists(query: String): List<PlaylistItem> {
        if (appSettings.offlineMode.value) {
            return emptyList()
        }
        val serviceId = activeServiceId()
        if (!isServiceConnected(serviceId)) {
            return emptyList()
        }

        val result = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.searchPlaylists(query)
            StreamingServiceId.JELLYFIN -> jellyfinClient.searchPlaylists(query)
            else -> Result.success(emptyList())
        }

        return result.getOrElse { emptyList() }
            .map { providerPlaylist ->
                val tracks = fetchPlaylistTracks(serviceId, providerPlaylist.providerId)
                mapProviderPlaylist(serviceId, providerPlaylist, tracks)
            }
    }

    override fun getSongs(): Flow<List<PlayableItem>> = songsFlow.asStateFlow()

    override fun getAlbums(): Flow<List<AlbumItem>> = albumsFlow.asStateFlow()

    override fun getArtists(): Flow<List<ArtistItem>> = artistsFlow.asStateFlow()

    override fun getPlaylists(): Flow<List<PlaylistItem>> = playlistsFlow.asStateFlow()

    override suspend fun getSongById(id: String): PlayableItem? = songCache[id]

    override suspend fun syncCatalog(
        limit: Int,
        onProgress: ((current: Int, total: Int, songsCount: Int) -> Unit)?
    ): List<StreamingSong> {
        if (appSettings.offlineMode.value) {
            val downloadedList = downloadedSongsMap.values.toList()
            replaceCatalog(downloadedList)
            return downloadedList
        }
        val serviceId = activeServiceId()
        if (!isServiceConnected(serviceId)) {
            clearInMemoryCatalog()
            return emptyList()
        }

        // Sync artists directly from provider first so all artists appear immediately
        syncArtists()

        val providerSongs = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.fetchLibrarySongs(limit, onProgress)
            StreamingServiceId.JELLYFIN -> jellyfinClient.fetchLibrarySongs(limit, onProgress)
            else -> Result.success(emptyList())
        }.getOrElse { emptyList() }

        val mappedSongs = providerSongs.map { mapProviderSong(serviceId, it) }
        syncLikedSongIdsFromProviderSongs(serviceId, providerSongs)
        replaceCatalog(mappedSongs)
        
        // Also sync playlists
        syncPlaylists()
        
        return mappedSongs
    }

    private fun syncLikedSongIdsFromProviderSongs(serviceId: String, providerSongs: List<ProviderSong>) {
        val servicePrefix = "$serviceId::"

        likedSongIds.removeIf { it.startsWith(servicePrefix) }

        providerSongs
            .asSequence()
            .filter { it.isFavorite }
            .map { encodeSongId(serviceId, it.providerId) }
            .forEach { likedSongIds.add(it) }
    }

    override suspend fun syncPlaylists(): List<StreamingPlaylist> {
        if (appSettings.offlineMode.value) {
            playlistsFlow.value = emptyList()
            return emptyList()
        }
        val serviceId = activeServiceId()
        if (!isServiceConnected(serviceId)) {
            playlistsFlow.value = emptyList()
            return emptyList()
        }

        val result = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.getPlaylists()
            StreamingServiceId.JELLYFIN -> jellyfinClient.getPlaylists()
            else -> Result.success(emptyList())
        }

        val playlists = coroutineScope {
            result.getOrElse { emptyList() }
                .map { providerPlaylist ->
                    async {
                        val tracks = fetchPlaylistTracks(serviceId, providerPlaylist.providerId)
                        mapProviderPlaylist(serviceId, providerPlaylist, tracks)
                    }
                }.awaitAll()
        }

        playlistsFlow.value = playlists
        return playlists
    }

    override suspend fun getAlbumById(id: String): AlbumItem? {
        albumsFlow.value.firstOrNull { it.id == id }?.let { return it }

        val decodedId = decodeAlbumId(id) ?: return null
        var providerAlbumId = decodedId.providerAlbumId
        
        if (!appSettings.offlineMode.value) {
            if (providerAlbumId == null) {
                val albumTitle = decodedId.title
                val albumArtist = decodedId.artist
                if (albumTitle.isNotBlank()) {
                    val searchResults = when (decodedId.serviceId) {
                        StreamingServiceId.SUBSONIC -> subsonicClient.searchAlbums(albumTitle).getOrNull()
                        StreamingServiceId.JELLYFIN -> jellyfinClient.searchAlbums(albumTitle).getOrNull()
                        else -> null
                    }
                    val matchedAlbum = searchResults?.firstOrNull {
                        it.title.equals(albumTitle, ignoreCase = true) &&
                        (albumArtist.isBlank() || it.artist.equals(albumArtist, ignoreCase = true))
                    } ?: searchResults?.firstOrNull()
                    if (matchedAlbum != null) {
                        providerAlbumId = matchedAlbum.providerId
                    }
                }
            }

            if (providerAlbumId != null) {
                try {
                    val providerResult = when (decodedId.serviceId) {
                        StreamingServiceId.SUBSONIC -> subsonicClient.getAlbumById(providerAlbumId)
                        StreamingServiceId.JELLYFIN -> jellyfinClient.getAlbumById(providerAlbumId)
                        else -> Result.failure(Exception("Unknown service"))
                    }
                    val mapped = providerResult.getOrNull()?.let {
                        mapProviderAlbum(decodedId.serviceId, it)
                    }
                    if (mapped != null) {
                        return mapped
                    }
                } catch (e: Exception) {
                    Log.w("StreamingMusicRepo", "Network getAlbumById failed, falling back to offline", e)
                }
            }
        }

        // Offline / Failure Fallback:
        val derived = deriveAlbumsFromSongs(downloadedSongsMap.values.toList(), 100)
        derived.firstOrNull { it.id == id }?.let { return it }
        
        return derived.firstOrNull { derivedAlbum ->
            val derivedDecoded = decodeAlbumId(derivedAlbum.id)
            derivedDecoded != null && 
            derivedDecoded.title.equals(decodedId.title, ignoreCase = true) &&
            derivedDecoded.artist.equals(decodedId.artist, ignoreCase = true)
        }
    }

    override suspend fun getArtistById(id: String): ArtistItem? {
        artistsFlow.value.firstOrNull { it.id == id }?.let { return it }

        if (appSettings.offlineMode.value) {
            return null
        }

        val artistName = extractArtistNameFromId(id)
        if (artistName.isBlank()) {
            return null
        }

        val serviceId = id.substringBefore("::", activeServiceId())
        val providerArtists = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.searchArtists(artistName, limit = 20)
            StreamingServiceId.JELLYFIN -> jellyfinClient.searchArtists(artistName, limit = 20)
            else -> Result.success(emptyList())
        }.getOrElse { emptyList() }

        val providerArtist = providerArtists.firstOrNull {
            it.name.equals(artistName, ignoreCase = true)
        } ?: providerArtists.firstOrNull()

        return providerArtist?.let { mapProviderArtist(serviceId, it) }
    }

    override suspend fun getPlaylistById(id: String): PlaylistItem? {
        return playlistsFlow.value.firstOrNull { it.id == id }
    }

    override suspend fun getSongsForAlbum(albumId: String): List<PlayableItem> {
        return getAlbumSongs(albumId)
    }

    private suspend fun replaceCatalog(songs: List<StreamingSong>) {
        val serviceId = activeServiceId()

        songCache.clear()
        songs.forEach { song ->
            songCache[song.id] = song
        }

        songsFlow.value = songs
        // Only populate albumsFlow with derived albums if no provider albums are cached
        if (providerAlbumCache.isEmpty()) {
            albumsFlow.value = buildAlbumItems(serviceId, songs)
        }
        val rawArtists = buildArtistItems(serviceId, songs)
        if (artistsFlow.value.isEmpty() || rawArtists.size >= artistsFlow.value.size) {
            artistsFlow.value = rawArtists
        }

        updateLikedSongsFlow()
        updateSavedAlbumsFlow()
        updateFollowedArtistsFlow()

        // Asynchronously enrich with Deezer images in background to avoid blocking
        repositoryScope.launch {
            try {
                val enriched = enrichArtistsWithDeezerImages(rawArtists)
                artistsFlow.value = enriched
                updateFollowedArtistsFlow()
            } catch (e: Exception) {
                Log.e("StreamingMusicRepo", "Background Deezer artist enrichment failed", e)
            }
        }
    }

    private suspend fun mergeCatalog(songs: List<StreamingSong>) {
        if (songs.isEmpty()) {
            return
        }

        val serviceId = activeServiceId()
        songs.forEach { song ->
            songCache[song.id] = song
        }
        trimSongCache()

        val mergedSongs = (songsFlow.value.filterIsInstance<StreamingSong>() + songs)
            .distinctBy { it.id }

        songsFlow.value = mergedSongs
        // Only populate albumsFlow with derived albums if no provider albums are cached
        if (providerAlbumCache.isEmpty()) {
            albumsFlow.value = buildAlbumItems(serviceId, mergedSongs)
        }
        val rawArtists = buildArtistItems(serviceId, mergedSongs)
        artistsFlow.value = rawArtists
        playlistsFlow.value = emptyList()

        updateLikedSongsFlow()
        updateSavedAlbumsFlow()
        updateFollowedArtistsFlow()

        // Asynchronously enrich with Deezer images in background to avoid blocking
        repositoryScope.launch {
            try {
                val enriched = enrichArtistsWithDeezerImages(rawArtists)
                artistsFlow.value = enriched
                updateFollowedArtistsFlow()
            } catch (e: Exception) {
                Log.e("StreamingMusicRepo", "Background Deezer artist enrichment failed", e)
            }
        }
    }

    private fun clearInMemoryCatalog() {
        songCache.clear()
        followedPlaylistIds.clear()
        songsFlow.value = emptyList()
        albumsFlow.value = emptyList()
        artistsFlow.value = emptyList()
        playlistsFlow.value = emptyList()
    }

    private fun updateLikedSongsFlow() {
        likedSongsFlow.value = likedSongIds.mapNotNull { id -> songCache[id] }
    }

    private fun updateSavedAlbumsFlow() {
        savedAlbumsFlow.value = savedAlbumIds.mapNotNull { id ->
            albumsFlow.value.firstOrNull { it.id == id } as? StreamingAlbum
        }
    }

    private fun updateFollowedArtistsFlow() {
        followedArtistsFlow.value = followedArtistIds.mapNotNull { id ->
            artistsFlow.value.firstOrNull { it.id == id } as? StreamingArtist
        }
    }

    private fun mapProviderSong(serviceId: String, providerSong: ProviderSong): StreamingSong {
        val encodedId = encodeSongId(serviceId, providerSong.providerId)
        val sourceType = serviceToSourceType(serviceId)
        val encodedAlbumId = providerSong.albumId
            ?.takeIf { it.isNotBlank() }
            ?.let { encodeAlbumId(serviceId, it) }
        val streamingUrl = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.buildStreamUrl(providerSong.providerId, desiredBitrateKbps())
            StreamingServiceId.JELLYFIN -> jellyfinClient.buildStreamUrl(providerSong.providerId, desiredBitrateKbps())
            else -> null
        }

        return StreamingSong(
            id = encodedId,
            title = providerSong.title,
            artist = providerSong.artist,
            album = providerSong.album,
            duration = providerSong.durationMs,
            artworkUri = providerSong.artworkUrl,
            sourceType = sourceType,
            streamingUrl = streamingUrl,
            previewUrl = null,
            isPlayable = true,
            externalId = providerSong.providerId,
            albumId = encodedAlbumId,
            albumArtist = providerSong.albumArtist,
            isFavorite = providerSong.isFavorite,
            trackNumber = providerSong.trackNumber,
            year = providerSong.year,
            genre = providerSong.genre,
            bitrate = providerSong.bitrate,
            sampleRate = providerSong.sampleRate,
            channels = providerSong.channels,
            codec = providerSong.codec
        )
    }

    private suspend fun fetchPlaylistTracks(serviceId: String, providerPlaylistId: String): List<StreamingSong> {
        val result = when (serviceId) {
            StreamingServiceId.SUBSONIC -> subsonicClient.getPlaylistSongs(providerPlaylistId)
            StreamingServiceId.JELLYFIN -> jellyfinClient.getPlaylistSongs(providerPlaylistId)
            else -> Result.success(emptyList())
        }

        return result.getOrElse { emptyList() }
            .map { mapProviderSong(serviceId, it) }
    }

    private fun mapProviderPlaylist(
        serviceId: String,
        providerPlaylist: ProviderPlaylist,
        tracks: List<StreamingSong> = emptyList()
    ): StreamingPlaylist {
        val encodedId = encodePlaylistId(serviceId, providerPlaylist.providerId)
        val resolvedTracks = if (tracks.isNotEmpty()) tracks else emptyList()
        
        return StreamingPlaylist(
            id = encodedId,
            name = providerPlaylist.name,
            description = providerPlaylist.description,
            artworkUri = providerPlaylist.artworkUrl ?: resolvedTracks.firstOrNull()?.artworkUri,
            songCount = providerPlaylist.songCount.takeIf { it > 0 } ?: resolvedTracks.size,
            isEditable = when (serviceId) {
                StreamingServiceId.SUBSONIC -> providerPlaylist.owner?.equals(subsonicClient.getUsername(), ignoreCase = true) == true
                StreamingServiceId.JELLYFIN -> providerPlaylist.owner?.equals(jellyfinClient.getUsername(), ignoreCase = true) == true
                else -> false
            },
            sourceType = serviceToSourceType(serviceId),
            externalId = providerPlaylist.providerId,
            owner = if (providerPlaylist.owner != null) {
                chromahub.rhythm.app.features.streaming.domain.model.PlaylistOwner(
                    id = normalizeKey(providerPlaylist.owner),
                    displayName = providerPlaylist.owner,
                    imageUrl = null,
                    isCurrentUser = false
                )
            } else null,
            isPublic = providerPlaylist.isPublic,
            isCollaborative = false,
            followers = null,
            snapshotId = null,
            tracks = resolvedTracks
        )
    }

    private fun buildAlbumItems(serviceId: String, songs: List<StreamingSong>): List<StreamingAlbum> {
        return songs
            .filter { it.album.isNotBlank() }
            .groupBy { song -> song.albumId ?: buildLegacyAlbumId(serviceId, song.artist, song.album) }
            .map { (albumKey, tracks) ->
                val firstSong = tracks.first()
                StreamingAlbum(
                    id = albumKey,
                    title = firstSong.album,
                    artist = firstSong.albumArtist?.takeIf { it.isNotBlank() } ?: firstSong.artist,
                    artworkUri = tracks.firstNotNullOfOrNull { it.artworkUri },
                    songCount = tracks.size,
                    year = firstSong.releaseDate?.take(4)?.toIntOrNull(),
                    sourceType = serviceToSourceType(serviceId)
                )
            }
            .sortedWith(compareBy<StreamingAlbum> { it.title.lowercase() }.thenBy { it.artist.lowercase() })
    }

    private fun mapProviderAlbum(serviceId: String, providerAlbum: ProviderAlbum): StreamingAlbum {
        return StreamingAlbum(
            id = encodeAlbumId(serviceId, providerAlbum.providerId),
            title = providerAlbum.title,
            artist = providerAlbum.artist,
            artworkUri = providerAlbum.artworkUrl,
            songCount = providerAlbum.songCount,
            year = providerAlbum.year,
            sourceType = serviceToSourceType(serviceId),
            externalId = providerAlbum.providerId,
            releaseDate = null,
            albumType = chromahub.rhythm.app.features.streaming.domain.model.AlbumType.ALBUM,
            genres = emptyList(),
            label = null,
            copyright = null,
            isExplicit = false
        )
    }

    private fun buildArtistItems(serviceId: String, songs: List<StreamingSong>): List<StreamingArtist> {
        // Apply artist separator settings to split collaborations, matching ViewModel behavior
        val separatorEnabled = appSettings.artistSeparatorEnabled.value
        val separatorDelimiters = if (separatorEnabled) {
            appSettings.artistSeparatorDelimiters.value.ifBlank { AppSettings.DEFAULT_ARTIST_SEPARATOR_DELIMITERS }
        } else {
            ""
        }
        
        return songs
            .filter { it.artist.isNotBlank() }
            .flatMap { song ->
                val artistNames = ArtistSeparator.splitArtistNames(
                    song.artist,
                    delimiters = separatorDelimiters,
                    enabled = separatorEnabled
                )
                
                if (artistNames.isEmpty()) {
                    listOf(song to song.artist.trim())
                } else {
                    artistNames.mapNotNull { artistName ->
                        artistName.trim().takeIf { it.isNotBlank() }?.let { trimmedName ->
                            song to trimmedName
                        }
                    }
                }
            }
            .groupBy { (song, artistName) -> "${song.sourceType.name}:${artistName.lowercase()}" }
            .values
            .sortedByDescending { artistSongs -> artistSongs.size }
            .map { artistSongs ->
                val firstSong = artistSongs.first().first
                val artistName = artistSongs.first().second
                val tracks = artistSongs.map { it.first }
                val albumCount = tracks.map { it.album }.distinct().size
                
                // Look up enriched artwork from cache, with fallback to song artwork
                val artworkUri = artistArtworkCache[normalizeKey(artistName)] 
                    ?: tracks.firstNotNullOfOrNull { it.artworkUri }
                
                StreamingArtist(
                    id = buildArtistId(serviceId, artistName),
                    name = artistName,
                    artworkUri = artworkUri,
                    songCount = tracks.size,
                    albumCount = albumCount,
                    sourceType = serviceToSourceType(serviceId)
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private suspend fun buildDerivedArtistItems(
        serviceId: String,
        songs: List<StreamingSong>
    ): List<StreamingArtist> {
        return enrichArtistsWithDeezerImages(buildArtistItems(serviceId, songs))
    }

    private fun mapProviderArtist(serviceId: String, providerArtist: ProviderArtist): StreamingArtist {
        val cachedArtistSongs = cachedSongsForArtist(providerArtist.name)
        val cachedArtist = buildArtistItems(serviceId, cachedArtistSongs)
            .firstOrNull { it.name.equals(providerArtist.name, ignoreCase = true) }

        return StreamingArtist(
            id = buildArtistId(serviceId, providerArtist.name),
            name = providerArtist.name,
            artworkUri = providerArtist.artworkUrl
                ?: artistArtworkCache[normalizeKey(providerArtist.name)]
                ?: cachedArtist?.artworkUri,
            songCount = providerArtist.songCount.takeIf { it > 0 }
                ?: cachedArtist?.songCount
                ?: cachedArtistSongs.size,
            albumCount = providerArtist.albumCount.takeIf { it > 0 }
                ?: cachedArtist?.albumCount
                ?: cachedArtistSongs.map { it.album }.distinct().size,
            sourceType = serviceToSourceType(serviceId),
            externalId = providerArtist.providerId,
            genres = emptyList(),
            followers = null,
            popularity = null,
            bio = providerArtist.description
        )
    }

    private fun artistIdForSong(song: StreamingSong): String {
        val serviceId = decodeSongId(song.id)?.first ?: activeServiceId()
        return buildArtistId(serviceId, song.artist)
    }

    private fun cachedSongsForArtist(artistName: String): List<StreamingSong> {
        if (artistName.isBlank()) {
            return emptyList()
        }

        return (songsFlow.value.filterIsInstance<StreamingSong>() + songCache.values + downloadedSongsMap.values)
            .asSequence()
            .filter { song ->
                if (!song.artist.contains(artistName, ignoreCase = true)) {
                    false
                } else {
                    val artistNames = ArtistSeparator.splitArtistNames(
                        song.artist,
                        delimiters = appSettings.artistSeparatorDelimiters.value.ifBlank { AppSettings.DEFAULT_ARTIST_SEPARATOR_DELIMITERS },
                        enabled = appSettings.artistSeparatorEnabled.value
                    )

                    artistNames.isEmpty() || artistNames.any { it.equals(artistName, ignoreCase = true) }
                }
            }
            .distinctBy { it.id }
            .toList()
    }

    private fun artistIdForAlbum(album: StreamingAlbum): String {
        val serviceId = album.id.substringBefore("::", activeServiceId())
        return buildArtistId(serviceId, album.artist)
    }

    private fun deriveAlbumsFromSongs(
        songs: List<StreamingSong>,
        limit: Int
    ): List<StreamingAlbum> {
        if (songs.isEmpty()) {
            return emptyList()
        }

        return songs
            .filter { it.album.isNotBlank() }
            .groupBy { song -> song.albumId ?: "${song.sourceType.name}:${song.artist.lowercase()}:${song.album.lowercase()}" }
            .values
            .sortedByDescending { albumSongs -> albumSongs.size }
            .take(limit.coerceAtLeast(1))
            .map { albumSongs ->
                val firstSong = albumSongs.first()
                StreamingAlbum(
                    id = firstSong.albumId ?: buildLegacyAlbumId(
                        serviceId = firstSong.sourceType.name,
                        artist = firstSong.artist,
                        album = firstSong.album
                    ),
                    title = firstSong.album,
                    artist = firstSong.albumArtist?.takeIf { it.isNotBlank() } ?: firstSong.artist,
                    artworkUri = albumSongs.firstNotNullOfOrNull { it.artworkUri },
                    songCount = albumSongs.size,
                    year = firstSong.releaseDate?.take(4)?.toIntOrNull(),
                    sourceType = firstSong.sourceType,
                    tracks = albumSongs
                )
            }
    }
    
    /**
     * Legacy matching - kept for backward compatibility with exact ID matching.
     * Use extractArtistNameFromId and match by artist name instead.
     */
    @Deprecated("Use extractArtistNameFromId and match by artist name")
    private fun matchArtistIdByFormat(artistId: String, generatedId: String): Boolean {
        return artistId == generatedId || extractArtistNameFromId(artistId) == extractArtistNameFromId(generatedId)
    }

    override fun buildArtistId(serviceId: String, artist: String): String {
        return "$serviceId::artist::${normalizeKey(artist)}"
    }

    private fun buildLegacyAlbumId(serviceId: String, artist: String, album: String): String {
        return "$serviceId::album::${normalizeKey(artist)}::${normalizeKey(album)}"
    }

    private fun decodeAlbumId(albumId: String): DecodedAlbumId? {
        val marker = "::album::"
        val separatorIndex = albumId.indexOf(marker)
        if (separatorIndex <= 0 || separatorIndex >= albumId.length - marker.length) {
            return null
        }

        val serviceId = albumId.substring(0, separatorIndex)
        val payload = albumId.substring(separatorIndex + marker.length)
        if (payload.isBlank()) {
            return null
        }

        val payloadParts = payload.split("::")
        return if (payloadParts.size >= 2) {
            val artist = payloadParts.first().replace("_", " ").trim()
            val album = payloadParts.drop(1).joinToString("::").replace("_", " ").trim()

            if (artist.isBlank() || album.isBlank()) {
                null
            } else {
                DecodedAlbumId(
                    serviceId = serviceId,
                    providerAlbumId = null,
                    title = album,
                    artist = artist,
                    isLegacy = true
                )
            }
        } else {
            val restoredPayload = payload.replace("_slash_", "/")
            DecodedAlbumId(
                serviceId = serviceId,
                providerAlbumId = restoredPayload,
                title = "",
                artist = "",
                isLegacy = false
            )
        }
    }

    private fun encodeAlbumId(serviceId: String, providerAlbumId: String): String {
        val safeProviderId = providerAlbumId.replace("/", "_slash_")
        return "$serviceId::album::$safeProviderId"
    }

    private fun legacyAlbumIdForSong(song: StreamingSong, fallbackServiceId: String?): String {
        val songServiceId = decodeSongId(song.id)?.first ?: fallbackServiceId ?: activeServiceId()
        return buildLegacyAlbumId(songServiceId, song.artist, song.album)
    }

    private data class DecodedAlbumId(
        val serviceId: String,
        val providerAlbumId: String?,
        val title: String,
        val artist: String,
        val isLegacy: Boolean
    )

    @Deprecated("Use encodeAlbumId(serviceId, providerAlbumId) for streaming album IDs")
    private fun buildAlbumId(serviceId: String, artist: String, album: String): String {
        return buildLegacyAlbumId(serviceId, artist, album)
    }

    @Deprecated("Use decodeAlbumId(albumId) and provider album IDs")
    private fun decodeLegacyAlbumId(albumId: String): Triple<String, String, String>? {
        val decoded = decodeAlbumId(albumId)
        if (decoded == null || !decoded.isLegacy) {
            return null
        }

        return Triple(decoded.serviceId, decoded.title, decoded.artist)
    }

    private fun normalizeKey(value: String): String {
        return value.trim().lowercase().replace("\\s+".toRegex(), "_")
    }

    private fun trimSongCache() {
        if (songCache.size <= MAX_CACHE_SIZE) return

        val toRemove = songCache.size - MAX_CACHE_SIZE
        repeat(toRemove) {
            val firstKey = songCache.entries.firstOrNull()?.key ?: return
            songCache.remove(firstKey)
        }
    }

    private fun activeServiceId(): String {
        return normalizeServiceId(appSettings.streamingService.value)
    }

    private fun encodeSongId(serviceId: String, providerId: String): String {
        return "$serviceId::$providerId"
    }

    private fun decodeSongId(songId: String): Pair<String, String>? {
        val separatorIndex = songId.indexOf("::")
        if (separatorIndex <= 0 || separatorIndex >= songId.length - 2) {
            return null
        }

        val serviceId = songId.substring(0, separatorIndex)
        val providerId = songId.substring(separatorIndex + 2)
        return serviceId to providerId
    }

    private fun decodePlaylistId(playlistId: String): Pair<String, String>? {
        val marker = "::playlist::"
        val separatorIndex = playlistId.indexOf(marker)
        if (separatorIndex <= 0 || separatorIndex >= playlistId.length - marker.length) {
            return null
        }

        val serviceId = playlistId.substring(0, separatorIndex)
        val providerId = playlistId.substring(separatorIndex + marker.length)
        if (providerId.isBlank()) {
            return null
        }

        return serviceId to providerId
    }

    private fun encodePlaylistId(serviceId: String, providerId: String): String {
        return "$serviceId::playlist::$providerId"
    }

    private fun desiredBitrateKbps(): Int {
        return when (appSettings.streamingQuality.value.uppercase()) {
            "LOW" -> 96
            "NORMAL" -> 160
            "HIGH" -> 320
            "LOSSLESS" -> 0   // No cap — server streams the original file at native quality
            else -> 0          // Unknown/future values also default to lossless (highest available)
        }
    }
    
    /**
     * Validate if the requested quality is supported by the provider.
     * Some providers may not support all quality levels.
     */
    private fun isSupportedQuality(quality: String): Boolean {
        // All major providers (Subsonic/Navidrome, Jellyfin) support these quality levels
        return quality.uppercase() in listOf("LOW", "NORMAL", "HIGH", "LOSSLESS")
    }
    
    /**
     * Get the fallback quality if the requested one is not supported.
     */
    private fun getFallbackQuality(unsupportedQuality: String): String {
        return when (unsupportedQuality.uppercase()) {
            else -> "HIGH" // Default fallback
        }
    }
    private fun resolveCookieInput(username: String, password: String): String {
        return when {
            looksLikeCookieBlob(password) -> password
            looksLikeCookieBlob(username) -> username
            else -> password
        }
    }

    private fun looksLikeCookieBlob(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.startsWith("{") || trimmed.contains('=')
    }

    private fun serviceToSourceType(serviceId: String): SourceType {
        return when (serviceId) {
            StreamingServiceId.SUBSONIC -> SourceType.SUBSONIC
            StreamingServiceId.JELLYFIN -> SourceType.JELLYFIN
            else -> SourceType.UNKNOWN
        }
    }

    private fun normalizeServiceId(serviceId: String): String {
        val normalized = serviceId.uppercase()
        return if (StreamingServiceId.all.contains(normalized)) {
            normalized
        } else {
            StreamingServiceId.SUBSONIC
        }
    }

    /**
     * Enrich streaming artists with artwork from Deezer API
     * Call this when loading artists to fill in missing artwork
     */
    suspend fun enrichArtistsWithDeezerImages(artists: List<StreamingArtist>): List<StreamingArtist> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if Deezer API is available and enabled in settings
                val deezerService = NetworkClient.deezerApiService
                if (deezerService == null || !NetworkClient.isDeezerApiEnabled()) {
                    Log.d("StreamingMusicRepo", "Deezer API service not available or disabled in settings, skipping enrichment")
                    return@withContext artists // Return unchanged if API not available/enabled
                }

                val enriched = mutableListOf<StreamingArtist>()

                for (artist in artists) {
                    try {
                        // Skip unknown/blank artists
                        if (artist.name.isBlank() || artist.name.equals("Unknown", ignoreCase = true)) {
                            enriched.add(artist)
                            continue
                        }

                        // Always try Deezer enrichment, even if artist has existing artwork
                        // This ensures we prefer actual artist images over album art
                        var enrichedArtist = artist
                        try {
                            // Search for artist on Deezer
                            val searchResponse = deezerService.searchArtists(artist.name, limit = 5)
                            val deezerArtist = searchResponse.data.firstOrNull { 
                                it.name.equals(artist.name, ignoreCase = true)
                            } ?: searchResponse.data.firstOrNull() // Fallback to best match

                            if (deezerArtist != null) {
                                // Choose best quality image available
                                val imageUrl = when {
                                    !deezerArtist.pictureXl.isNullOrEmpty() -> deezerArtist.pictureXl
                                    !deezerArtist.pictureBig.isNullOrEmpty() -> deezerArtist.pictureBig
                                    !deezerArtist.pictureMedium.isNullOrEmpty() -> deezerArtist.pictureMedium
                                    !deezerArtist.picture.isNullOrEmpty() -> deezerArtist.picture
                                    else -> null
                                }

                                if (!imageUrl.isNullOrEmpty()) {
                                    Log.d("StreamingMusicRepo", "Found Deezer image for ${artist.name}")
                                    artistArtworkCache[normalizeKey(artist.name)] = imageUrl
                                    enrichedArtist = artist.copy(artworkUri = imageUrl)
                                } else {
                                    Log.d("StreamingMusicRepo", "Deezer artist found but no image: ${deezerArtist.name}")
                                }
                            } else {
                                Log.d("StreamingMusicRepo", "No Deezer artist found for: ${artist.name}")
                            }
                        } catch (e: Exception) {
                            Log.w("StreamingMusicRepo", "Failed to fetch Deezer image for ${artist.name}: ${e.message}", e)
                        }

                        // Cache the final artwork (Deezer or original)
                        enrichedArtist.artworkUri?.takeIf { it.isNotBlank() }?.let { cachedUri ->
                            artistArtworkCache[normalizeKey(artist.name)] = cachedUri
                        }
                        enriched.add(enrichedArtist)
                    } catch (e: Exception) {
                        Log.w("StreamingMusicRepo", "Failed to process artist ${artist.name}: ${e.message}", e)
                        enriched.add(artist)
                    }
                }

                Log.d("StreamingMusicRepo", "Enriched ${enriched.count { it.artworkUri != null }} of ${artists.size} artists with Deezer images")
                enriched
            } catch (e: Exception) {
                Log.e("StreamingMusicRepo", "Error enriching artists with Deezer images", e)
                artists // Return unchanged on error
            }
        }
    }

    /**
     * Update the repository's artistsFlow with enriched artists.
     * This ensures enriched artwork persists across recompositions and navigations.
     */
    fun updateArtistsFlow(enrichedArtists: List<StreamingArtist>) {
        artistsFlow.value = enrichedArtists
    }

    override suspend fun syncArtists(): List<StreamingArtist> {
        val serviceId = activeServiceId()
        if (!isServiceConnected(serviceId)) return emptyList()

        return try {
            val providerArtists = when (serviceId) {
                StreamingServiceId.SUBSONIC -> subsonicClient.getArtists().getOrNull()
                StreamingServiceId.JELLYFIN -> jellyfinClient.getArtists().getOrNull()
                else -> null
            }
            providerArtists?.forEach { artist ->
                if (!artist.artworkUrl.isNullOrBlank()) {
                    artistArtworkCache[normalizeKey(artist.name)] = artist.artworkUrl
                }
            }
            if (!providerArtists.isNullOrEmpty()) {
                val directArtists = providerArtists.map { mapProviderArtist(serviceId, it) }
                artistsFlow.value = directArtists
                updateFollowedArtistsFlow()
                Log.d("StreamingMusicRepo", "Synced ${directArtists.size} artists directly from provider")
                directArtists
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w("StreamingMusicRepo", "Failed to sync artists from provider", e)
            emptyList()
        }
    }

    private suspend fun syncArtistArtworkCache(serviceId: String) {
        syncArtists()
    }

    private companion object {
        private const val SEARCH_LIMIT = 100
        private const val MAX_CACHE_SIZE = 4000
    }
}
