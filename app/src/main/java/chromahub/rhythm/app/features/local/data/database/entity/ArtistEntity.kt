package chromahub.rhythm.app.features.local.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artworkUri: String?,
    val numberOfAlbums: Int,
    val numberOfTracks: Int,
    val groupByAlbumArtist: Boolean
)

fun ArtistEntity.toArtist(): chromahub.rhythm.app.shared.data.model.Artist {
    return chromahub.rhythm.app.shared.data.model.Artist(
        id = id,
        name = name,
        artworkUri = artworkUri?.let { android.net.Uri.parse(it) },
        numberOfAlbums = numberOfAlbums,
        numberOfTracks = numberOfTracks
    )
}
