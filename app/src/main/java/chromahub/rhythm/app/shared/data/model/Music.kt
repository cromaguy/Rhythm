/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.model

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import java.util.Locale
import java.util.Objects

@Immutable
@Parcelize
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String = "",
    val duration: Long,
    val uri: Uri,
    val artworkUri: Uri? = null,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String? = null,
    val dateAdded: Long = System.currentTimeMillis(), // New field for date added
    val dateModified: Long = System.currentTimeMillis(),
    val albumArtist: String? = null, // Album artist for grouping
    // Audio quality metadata
    val bitrate: Int? = null, // Bitrate in bps
    val sampleRate: Int? = null, // Sample rate in Hz
    val channels: Int? = null, // Number of audio channels (1=mono, 2=stereo, 6=5.1, etc.)
    val codec: String? = null, // Audio codec (AAC, MP3, FLAC, etc.)
    val discNumber: Int = 1, // Multi-disc support
    val path: String? = null
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Song

        if (id != other.id) return false
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (albumId != other.albumId) return false
        if (duration != other.duration) return false
        if (uri != other.uri) return false
        if (artworkUri != other.artworkUri) return false
        if (trackNumber != other.trackNumber) return false
        if (year != other.year) return false
        if (!Objects.equals(genre, other.genre)) return false
        if (dateAdded != other.dateAdded) return false
        if (dateModified != other.dateModified) return false
        if (!Objects.equals(albumArtist, other.albumArtist)) return false
        if (!Objects.equals(bitrate, other.bitrate)) return false
        if (!Objects.equals(sampleRate, other.sampleRate)) return false
        if (!Objects.equals(channels, other.channels)) return false
        if (!Objects.equals(codec, other.codec)) return false
        if (discNumber != other.discNumber) return false
        if (path != other.path) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(
            id,
            title,
            artist,
            album,
            albumId,
            duration,
            uri,
            artworkUri,
            trackNumber,
            year,
            genre,
            dateAdded,
            dateModified,
            albumArtist,
            bitrate,
            sampleRate,
            channels,
            codec,
            discNumber,
            path
        )
    }
}

@Immutable
@Parcelize
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUri: Uri? = null,
    val year: Int = 0,
    val songs: List<Song> = emptyList(),
    val numberOfSongs: Int = 0,
    val dateModified: Long = System.currentTimeMillis()
) : Parcelable

private fun String.normalizedAlbumMatchKey(): String =
    trim().lowercase(Locale.ROOT)

private fun String.isUsableAlbumArtist(): Boolean =
    isNotBlank() && !equals("<unknown>", ignoreCase = true)

private fun Song.albumGroupMatchKey(): String =
    (albumArtist?.trim()?.takeIf { it.isUsableAlbumArtist() }
        ?: artist.trim().takeIf { it.isNotBlank() }
        ?: "Unknown Artist").normalizedAlbumMatchKey()

private fun Album.hasTitle(title: String): Boolean {
    val cleanThis = this.title.trim()
    val cleanTarget = title.trim()
    return cleanThis.equals(cleanTarget, ignoreCase = true) ||
        cleanThis.replace('/', '_').equals(cleanTarget.replace('/', '_'), ignoreCase = true)
}

private fun Album.isCompilation(): Boolean =
    artist.trim().equals("Various Artists", ignoreCase = true) ||
    artist.trim().equals("VA", ignoreCase = true) ||
    artist.trim().equals("Compilation", ignoreCase = true) ||
    artist.trim().equals("Soundtrack", ignoreCase = true)

fun List<Album>.findAlbumForSong(song: Song): Album? {
    val songAlbumId = song.albumId.trim()
    val songAlbumTitle = song.album.trim()
    val songGroupKey = song.albumGroupMatchKey()

    return firstOrNull { album ->
        album.songs.any { it.id == song.id }
    } ?: firstOrNull { album ->
        songAlbumId.isNotBlank() &&
            album.hasTitle(songAlbumTitle) &&
            album.songs.any { it.albumId.trim() == songAlbumId }
    } ?: firstOrNull { album ->
        album.hasTitle(songAlbumTitle) &&
            album.songs.any { it.albumGroupMatchKey() == songGroupKey }
    } ?: firstOrNull { album ->
        album.hasTitle(songAlbumTitle) &&
            !album.isCompilation() &&
            album.artist.trim().equals(song.albumArtist?.trim().takeIf { !it.isNullOrBlank() } ?: song.artist.trim(), ignoreCase = true)
    } ?: firstOrNull { album ->
        album.hasTitle(songAlbumTitle)
    }
}

fun List<Album>.findAlbumForRoute(albumId: String, albumName: String): Album? {
    val routeAlbumId = albumId.trim()
    val routeAlbumName = albumName.trim()
    val legacyUnknownName = routeAlbumId
        .takeIf { it.startsWith("unknown_") }
        ?.removePrefix("unknown_")
        ?.trim()
        .orEmpty()
    val fallbackAlbumName = routeAlbumName.ifBlank { legacyUnknownName }

    return firstOrNull { it.id == routeAlbumId || it.id.replace('/', '_') == routeAlbumId }
        ?: firstOrNull { album ->
            routeAlbumId.isNotBlank() &&
                album.songs.any {
                    val id = it.albumId.trim()
                    id == routeAlbumId || id.replace('/', '_') == routeAlbumId
                }
        }
        ?: fallbackAlbumName.takeIf { it.isNotBlank() }?.let { name ->
            firstOrNull { it.hasTitle(name) }
        }
}

@Immutable
@Parcelize
data class Artist(
    val id: String,
    val name: String,
    val artworkUri: Uri? = null,
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val numberOfAlbums: Int = 0,
    val numberOfTracks: Int = 0
) : Parcelable

@Immutable
@Parcelize
data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList(),
    val dateCreated: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
    val artworkUri: Uri? = null
) : Parcelable {
    val isDefault: Boolean
        get() = id == "1" || id == "2" || id == "3"  // Favorites, Recently Added, Most Played
}

// Represents the current playback queue
@Immutable
data class Queue(
    val songs: List<Song>,
    val currentIndex: Int = 0
)

// Represents a location in the app (for the "Living room" feature)
@Immutable
data class PlaybackLocation(
    val id: String,
    val name: String,
    val icon: Int
)
