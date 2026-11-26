/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.library

import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song

/**
 * Combines local MediaStore models and Go Mode streaming models when Unified Library
 * mode is active, while keeping origin classification deterministic for item clicks,
 * navigation targets, favorites, and offline downloads.
 */
object UnifiedLibraryMerger {

    fun isStreamingSong(song: Song, streamingSongIds: Set<String>): Boolean {
        if (streamingSongIds.contains(song.id)) return true
        val scheme = runCatching { song.uri.scheme }.getOrNull()
        return scheme == "streaming" || song.id.startsWith("subsonic::") || song.id.startsWith("jellyfin::")
    }

    fun isStreamingAlbum(
        album: Album,
        streamingAlbumIds: Set<String>,
        streamingSongIds: Set<String>
    ): Boolean {
        if (streamingAlbumIds.contains(album.id)) return true
        if (album.id.startsWith("subsonic::") || album.id.startsWith("jellyfin::")) return true
        return album.songs.isNotEmpty() && album.songs.all { isStreamingSong(it, streamingSongIds) }
    }

    fun isStreamingArtist(artist: Artist, streamingArtistIds: Set<String>): Boolean {
        if (streamingArtistIds.contains(artist.id)) return true
        return artist.id.startsWith("derived::") ||
            artist.id.startsWith("subsonic::") ||
            artist.id.startsWith("jellyfin::")
    }

    fun isStreamingPlaylist(playlist: Playlist, streamingPlaylistIds: Set<String>): Boolean {
        if (streamingPlaylistIds.contains(playlist.id)) return true
        return playlist.id.startsWith("subsonic::") || playlist.id.startsWith("jellyfin::")
    }

    fun mergeSongs(
        local: List<Song>,
        streaming: List<Song>,
        unifiedEnabled: Boolean,
        isStreamingMode: Boolean
    ): List<Song> {
        if (unifiedEnabled) {
            if (streaming.isEmpty()) return local
            if (local.isEmpty()) return streaming
            return (local + streaming).distinctBy { it.id }
        }
        return if (isStreamingMode) streaming else local
    }

    fun mergeAlbums(
        local: List<Album>,
        streaming: List<Album>,
        unifiedEnabled: Boolean,
        isStreamingMode: Boolean
    ): List<Album> {
        if (unifiedEnabled) {
            if (streaming.isEmpty()) return local
            if (local.isEmpty()) return streaming
            return (local + streaming).distinctBy { it.id }
        }
        return if (isStreamingMode) streaming else local
    }

    fun mergeArtists(
        local: List<Artist>,
        streaming: List<Artist>,
        unifiedEnabled: Boolean,
        isStreamingMode: Boolean
    ): List<Artist> {
        if (unifiedEnabled) {
            if (streaming.isEmpty()) return local
            if (local.isEmpty()) return streaming
            return (local + streaming).distinctBy { it.id }
        }
        return if (isStreamingMode) streaming else local
    }

    fun mergePlaylists(
        local: List<Playlist>,
        streaming: List<Playlist>,
        unifiedEnabled: Boolean,
        isStreamingMode: Boolean
    ): List<Playlist> {
        if (unifiedEnabled) {
            if (streaming.isEmpty()) return local
            if (local.isEmpty()) return streaming
            return (local + streaming).distinctBy { it.id }
        }
        return if (isStreamingMode) streaming else local
    }
}
