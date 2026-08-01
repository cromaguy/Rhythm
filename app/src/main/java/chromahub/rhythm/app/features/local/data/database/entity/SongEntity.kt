package chromahub.rhythm.app.features.local.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
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
    return chromahub.rhythm.app.shared.data.model.Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        uri = android.net.Uri.parse(uri),
        artworkUri = artworkUri?.let { android.net.Uri.parse(it) },
        trackNumber = trackNumber,
        year = year,
        genre = genre,
        dateAdded = dateAdded,
        dateModified = dateModified.takeIf { it > 0L } ?: dateAdded,
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
        dateAdded = dateAdded,
        dateModified = dateModified,
        albumArtist = albumArtist,
        bitrate = bitrate,
        sampleRate = sampleRate,
        channels = channels,
        codec = codec,
        discNumber = discNumber,
        path = path
    )
}
