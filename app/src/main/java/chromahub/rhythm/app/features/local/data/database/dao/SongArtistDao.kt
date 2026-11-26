/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import chromahub.rhythm.app.features.local.data.database.entity.SongArtistEntity

@Dao
interface SongArtistDao {
    @Query("SELECT * FROM song_artists WHERE groupByAlbumArtist = :groupByAlbumArtist")
    suspend fun getAllSongArtists(groupByAlbumArtist: Boolean): List<SongArtistEntity>

    @Query("SELECT DISTINCT artistName FROM song_artists WHERE groupByAlbumArtist = :groupByAlbumArtist")
    suspend fun getArtistNames(groupByAlbumArtist: Boolean): List<String>

    @Query("SELECT COUNT(*) FROM song_artists WHERE artistName = :artistName AND groupByAlbumArtist = :groupByAlbumArtist")
    suspend fun getTrackCountForArtist(artistName: String, groupByAlbumArtist: Boolean): Int

    @Query("SELECT COUNT(DISTINCT s.album) FROM song_artists sa INNER JOIN songs s ON sa.songId = s.id WHERE sa.artistName = :artistName AND sa.groupByAlbumArtist = :groupByAlbumArtist AND s.album IS NOT NULL AND s.album != ''")
    suspend fun getAlbumCountForArtist(artistName: String, groupByAlbumArtist: Boolean): Int
    
    // Optimized query to fetch all artists and their counts in a single query to avoid N+1 issue
    @Query("""
        SELECT sa.artistName,
               COUNT(sa.songId) as trackCount,
               COUNT(DISTINCT s.album) as albumCount
        FROM song_artists sa
        LEFT JOIN songs s ON sa.songId = s.id AND s.album IS NOT NULL AND s.album != ''
        WHERE sa.groupByAlbumArtist = :groupByAlbumArtist
        GROUP BY sa.artistName
    """)
    suspend fun getAggregatedArtists(groupByAlbumArtist: Boolean): List<ArtistAggregatedData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songArtists: List<SongArtistEntity>)

    @Query("DELETE FROM song_artists WHERE groupByAlbumArtist = :groupByAlbumArtist")
    suspend fun deleteByGroupType(groupByAlbumArtist: Boolean)

    @Query("DELETE FROM song_artists")
    suspend fun deleteAll()

    @Query("DELETE FROM song_artists WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("DELETE FROM song_artists WHERE songId IN (:songIdsChunk)")
    suspend fun deleteBySongIdsChunk(songIdsChunk: List<String>)

    suspend fun deleteBySongIds(songIds: List<String>) {
        if (songIds.isEmpty()) return
        songIds.distinct().chunked(450).forEach { chunk -> deleteBySongIdsChunk(chunk) }
    }

    @Transaction
    suspend fun replaceAll(songArtists: List<SongArtistEntity>, groupByAlbumArtist: Boolean) {
        deleteByGroupType(groupByAlbumArtist)
        insertAll(songArtists)
    }
}

data class ArtistAggregatedData(
    val artistName: String,
    val trackCount: Int,
    val albumCount: Int
)
