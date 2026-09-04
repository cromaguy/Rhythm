/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.domain.repository

import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.core.domain.repository.MusicRepository
import chromahub.rhythm.app.features.streaming.domain.model.BrowseCategory
import chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum
import chromahub.rhythm.app.features.streaming.domain.model.StreamingArtist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for streaming music operations.
 * Extends the base MusicRepository with streaming-specific operations.
 */
interface StreamingMusicRepository : MusicRepository {
    
    /**
     * Get the current streaming service source type.
     */
    val currentService: SourceType
    
    /**
     * Check if the user is authenticated with the current service.
     */
    suspend fun isAuthenticated(): Boolean
    
    /**
     * Authenticate with the streaming service.
     * @return True if authentication was successful.
     */
    suspend fun authenticate(): Boolean
    
    /**
     * Log out from the streaming service.
     */
    suspend fun logout()
    
    /**
     * Get personalized recommendations for the user.
     */
    suspend fun getRecommendations(limit: Int = 20): List<StreamingSong>
    
    /**
     * Get new releases.
     */
    suspend fun getNewReleases(limit: Int = 20): List<StreamingAlbum>
    
    /**
     * Get featured/editorial playlists.
     */
    suspend fun getFeaturedPlaylists(limit: Int = 20): List<StreamingPlaylist>

    /**
     * Sync playlists from the active streaming provider.
     */
    suspend fun syncPlaylists(): List<StreamingPlaylist>

    /**
     * Sync artists directly from the active streaming provider.
     */
    suspend fun syncArtists(): List<StreamingArtist>

    /**
     * Sync the provider library catalog so songs, albums, and artists are derived from real track data.
     */
    suspend fun syncCatalog(
        limit: Int = 5_000,
        onProgress: ((current: Int, total: Int, songsCount: Int) -> Unit)? = null
    ): List<StreamingSong>
    
    /**
     * Get browse categories/genres.
     */
    suspend fun getBrowseCategories(): List<BrowseCategory>
    
    /**
     * Get playlists for a specific category.
     */
    suspend fun getCategoryPlaylists(categoryId: String, limit: Int = 20): List<StreamingPlaylist>
    
    /**
     * Get top charts.
     */
    suspend fun getTopCharts(limit: Int = 50): List<StreamingSong>
    
    /**
     * Get user's saved/liked songs.
     */
    fun getLikedSongs(): Flow<List<StreamingSong>>
    
    /**
     * Like/save a song.
     */
    suspend fun likeSong(songId: String): Boolean
    
    /**
     * Unlike/unsave a song.
     */
    suspend fun unlikeSong(songId: String): Boolean
    
    /**
     * Check if a song is liked.
     */
    suspend fun isSongLiked(songId: String): Boolean
    
    /**
     * Follow an artist.
     */
    suspend fun followArtist(artistId: String): Boolean
    
    /**
     * Unfollow an artist.
     */
    suspend fun unfollowArtist(artistId: String): Boolean
    
    /**
     * Check if an artist is followed.
     */
    suspend fun isArtistFollowed(artistId: String): Boolean
    
    /**
     * Get followed artists.
     */
    fun getFollowedArtists(): Flow<List<StreamingArtist>>

    /**
     * Build consistent artist ID.
     */
    fun buildArtistId(serviceId: String, artist: String): String
    
    /**
     * Save an album to library.
     */
    suspend fun saveAlbum(albumId: String): Boolean
    
    /**
     * Remove an album from library.
     */
    suspend fun unsaveAlbum(albumId: String): Boolean
    
    /**
     * Get saved albums.
     */
    fun getSavedAlbums(): Flow<List<StreamingAlbum>>
    
    /**
     * Follow/save a playlist.
     */
    suspend fun followPlaylist(playlistId: String): Boolean
    
    /**
     * Unfollow a playlist.
     */
    suspend fun unfollowPlaylist(playlistId: String): Boolean
    
    /**
     * Create a new playlist on the streaming service.
     */
    suspend fun createPlaylist(name: String, description: String? = null, isPublic: Boolean = false): StreamingPlaylist?

    /**
     * Rename an existing playlist on the streaming service.
     */
    suspend fun renamePlaylist(playlistId: String, newName: String): Boolean

    /**
     * Delete a playlist on the streaming service.
     */
    suspend fun deletePlaylist(playlistId: String): Boolean
    
    /**
     * Add songs to a playlist.
     */
    suspend fun addSongsToPlaylist(playlistId: String, songIds: List<String>): Boolean
    
    /**
     * Remove songs from a playlist.
     */
    suspend fun removeSongsFromPlaylist(playlistId: String, songIds: List<String>): Boolean
    
    /**
     * Get the streaming URL for a song.
     * May require additional authentication or token refresh.
     */
    suspend fun getStreamingUrl(songId: String): String?
    
    /**
     * Get related/similar tracks for a song.
     */
    suspend fun getRelatedTracks(songId: String, limit: Int = 20): List<StreamingSong>
    
    /**
     * Get an artist's top tracks.
     */
    suspend fun getArtistTopTracks(artistId: String, limit: Int = 10): List<StreamingSong>
    
    /**
     * Get an artist's albums.
     */
    suspend fun getArtistAlbums(artistId: String): List<StreamingAlbum>
    
    /**
     * Get related artists.
     */
    suspend fun getRelatedArtists(artistId: String, limit: Int = 10): List<StreamingArtist>
    
    /**
     * Download a song for offline playback.
     */
    suspend fun downloadSong(songId: String): Boolean

    /**
     * Download a song with metadata for offline playback.
     */
    suspend fun downloadSong(song: StreamingSong): Boolean = downloadSong(song.id)
    
    /**
     * Remove a downloaded song.
     */
    suspend fun removeDownload(songId: String): Boolean
    
    /**
     * Check if a song is downloaded.
     */
    suspend fun isDownloaded(songId: String): Boolean
    
    /**
     * Get all downloaded songs.
     */
    fun getDownloadedSongs(): Flow<List<StreamingSong>>
    
    /**
     * Get random songs from the service.
     */
    suspend fun getRandomSongs(limit: Int = 50): List<StreamingSong>

    /**
     * Get an album's tracks.
     */
    suspend fun getAlbumSongs(albumId: String): List<StreamingSong>
    
    /**
     * Get album list with optional type filtering.
     * @param type Type of album list: "newest", "recent", "random", "alphabetical", "frequent"
     */
    suspend fun getAlbumList(type: String = "newest", limit: Int = 50): List<StreamingAlbum>

    /**
     * Report that playback has started (scrobbling/now playing)
     */
    suspend fun reportPlaybackStart(songId: String): Boolean

    /**
     * Report that playback has stopped (scrobbling)
     */
    suspend fun reportPlaybackStop(songId: String, positionMs: Long): Boolean
}
