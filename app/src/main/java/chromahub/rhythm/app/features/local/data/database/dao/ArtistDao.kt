package chromahub.rhythm.app.features.local.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import chromahub.rhythm.app.features.local.data.database.entity.ArtistEntity

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists WHERE groupByAlbumArtist = :groupByAlbumArtist")
    suspend fun getArtists(groupByAlbumArtist: Boolean): List<ArtistEntity>

    @Query("SELECT * FROM artists WHERE groupByAlbumArtist = :groupByAlbumArtist ORDER BY name ASC")
    fun getArtistsFlow(groupByAlbumArtist: Boolean): kotlinx.coroutines.flow.Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE groupByAlbumArtist = :groupByAlbumArtist ORDER BY name ASC")
    fun getArtistsPagingSource(groupByAlbumArtist: Boolean): androidx.paging.PagingSource<Int, ArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists WHERE groupByAlbumArtist = :groupByAlbumArtist")
    suspend fun deleteByGroupType(groupByAlbumArtist: Boolean)

    @Query("DELETE FROM artists")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(artists: List<ArtistEntity>, groupByAlbumArtist: Boolean) {
        deleteByGroupType(groupByAlbumArtist)
        insertAll(artists)
    }
}
