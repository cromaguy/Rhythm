/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import chromahub.rhythm.app.features.local.data.database.entity.AudioBookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioBookmarkDao {
    @Query("SELECT * FROM audio_bookmarks WHERE songId = :songId ORDER BY positionMs ASC")
    fun getBookmarksForSongFlow(songId: String): Flow<List<AudioBookmarkEntity>>

    @Query("SELECT * FROM audio_bookmarks WHERE songId = :songId ORDER BY positionMs ASC")
    suspend fun getBookmarksForSong(songId: String): List<AudioBookmarkEntity>

    @Query("SELECT * FROM audio_bookmarks ORDER BY createdAt DESC")
    suspend fun getAllBookmarks(): List<AudioBookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: AudioBookmarkEntity): Long

    @Query("DELETE FROM audio_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteById(bookmarkId: Long)

    @Query("DELETE FROM audio_bookmarks WHERE songId = :songId")
    suspend fun deleteForSong(songId: String)
}
