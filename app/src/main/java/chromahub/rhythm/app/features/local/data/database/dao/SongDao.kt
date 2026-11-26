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
import chromahub.rhythm.app.features.local.data.database.entity.SongEntity

@Dao
interface SongDao {
    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("SELECT * FROM songs")
    fun getAllSongsFlow(): kotlinx.coroutines.flow.Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE duration >= :minDurationMs ORDER BY title ASC")
    fun getSongsFilteredFlow(minDurationMs: Long = 0L): kotlinx.coroutines.flow.Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getSongsPagingSource(): androidx.paging.PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE duration >= :minDurationMs AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%') ORDER BY title ASC")
    fun getSongsPagingSourceSearch(query: String, minDurationMs: Long = 0L): androidx.paging.PagingSource<Int, SongEntity>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN songs_fts fts ON s.rowid = fts.rowid
        WHERE s.duration >= :minDurationMs AND songs_fts MATCH :ftsQuery
        ORDER BY s.title ASC
        LIMIT :limit
        """
    )
    suspend fun searchSongsFts(
        ftsQuery: String,
        minDurationMs: Long = 0L,
        limit: Int = 200
    ): List<SongEntity>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN songs_fts fts ON s.rowid = fts.rowid
        WHERE s.duration >= :minDurationMs AND songs_fts MATCH :ftsQuery
        ORDER BY s.title ASC
        """
    )
    fun getSongsPagingSourceFtsSearch(
        ftsQuery: String,
        minDurationMs: Long = 0L
    ): androidx.paging.PagingSource<Int, SongEntity>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:songIdsChunk)")
    suspend fun getSongsByIdsChunk(songIdsChunk: List<String>): List<SongEntity>

    suspend fun getSongsByIds(songIds: List<String>): List<SongEntity> {
        if (songIds.isEmpty()) return emptyList()
        return songIds.distinct().chunked(450).flatMap { chunk -> getSongsByIdsChunk(chunk) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("DELETE FROM songs WHERE id IN (:songIdsChunk)")
    suspend fun deleteByIdsChunk(songIdsChunk: List<String>)

    suspend fun deleteByIds(songIds: List<String>) {
        if (songIds.isEmpty()) return
        songIds.distinct().chunked(450).forEach { chunk -> deleteByIdsChunk(chunk) }
    }

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM songs WHERE artworkUri LIKE '%embedded_artwork%'")
    suspend fun countSongsWithEmbeddedArtworkUri(): Int

    @Transaction
    suspend fun replaceAll(songs: List<SongEntity>) {
        deleteAll()
        insertAll(songs)
    }
}
