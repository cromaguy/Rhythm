/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.core.net.toUri

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["artist"])
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String,
    val duration: Long,
    val uri: String,
    val artworkUri: String?,
    val trackNumber: Int,
    val year: Int,
    val genre: String?,
    val dateAdded: Long,
    val dateModified: Long,
    val albumArtist: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?,
    val codec: String?,
    val discNumber: Int = 1,
    val path: String? = null
)

fun SongEntity.toSong(): chromahub.rhythm.app.shared.data.model.Song {
    val normalizedDateAdded = if (dateAdded in 1..99_999_999_999L) dateAdded * 1000L else dateAdded
    val normalizedDateModified = if (dateModified in 1..99_999_999_999L) dateModified * 1000L else dateModified
    return chromahub.rhythm.app.shared.data.model.Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        uri = (uri).toUri(),
        artworkUri = artworkUri?.let { (it).toUri() },
        trackNumber = trackNumber,
        year = year,
        genre = genre,
        dateAdded = normalizedDateAdded,
        dateModified = normalizedDateModified.takeIf { it > 0L } ?: normalizedDateAdded,
        albumArtist = albumArtist,
        bitrate = bitrate,
        sampleRate = sampleRate,
        channels = channels,
        codec = codec,
        discNumber = discNumber,
        path = path
    )
}


fun chromahub.rhythm.app.shared.data.model.Song.toEntity(): SongEntity {
    val normalizedDateAdded = if (dateAdded in 1..99_999_999_999L) dateAdded * 1000L else dateAdded
    val normalizedDateModified = if (dateModified in 1..99_999_999_999L) dateModified * 1000L else dateModified
    return SongEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        uri = uri.toString(),
        artworkUri = artworkUri?.toString(),
        trackNumber = trackNumber,
        year = year,
        genre = genre,
        dateAdded = normalizedDateAdded,
        dateModified = normalizedDateModified.takeIf { it > 0L } ?: normalizedDateAdded,
        albumArtist = albumArtist,
        bitrate = bitrate,
        sampleRate = sampleRate,
        channels = channels,
        codec = codec,
        discNumber = discNumber,
        path = path
    )
}
