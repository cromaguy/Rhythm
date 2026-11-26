/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import chromahub.rhythm.app.features.local.data.database.entity.OfflineTrackEntity
import chromahub.rhythm.app.features.local.data.database.entity.PendingScrobbleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineTrackDao {
    @Query("SELECT * FROM offline_tracks ORDER BY downloadedAtMs DESC")
    fun getAllFlow(): Flow<List<OfflineTrackEntity>>

    @Query("SELECT * FROM offline_tracks ORDER BY downloadedAtMs DESC")
    suspend fun getAll(): List<OfflineTrackEntity>

    @Query("SELECT * FROM offline_tracks WHERE songId = :songId LIMIT 1")
    suspend fun getById(songId: String): OfflineTrackEntity?

    @Query("SELECT * FROM offline_tracks WHERE status = :status ORDER BY downloadedAtMs ASC")
    suspend fun getByStatus(status: String): List<OfflineTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: OfflineTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<OfflineTrackEntity>)

    @Query("UPDATE offline_tracks SET status = :status, progress = :progress, errorMessage = :errorMessage WHERE songId = :songId")
    suspend fun updateProgress(songId: String, status: String, progress: Float, errorMessage: String?)

    @Query("DELETE FROM offline_tracks WHERE songId = :songId")
    suspend fun deleteById(songId: String)
}

@Dao
interface PendingScrobbleDao {
    @Query("SELECT * FROM pending_scrobbles ORDER BY playedAtMs ASC LIMIT :limit")
    suspend fun getPendingBatch(limit: Int = 50): List<PendingScrobbleEntity>

    @Query("SELECT COUNT(*) FROM pending_scrobbles")
    fun getPendingCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scrobble: PendingScrobbleEntity): Long

    @Query("UPDATE pending_scrobbles SET attemptCount = attemptCount + 1, lastAttemptAtMs = :attemptedAtMs, lastError = :error WHERE id = :id")
    suspend fun recordFailure(id: Long, attemptedAtMs: Long, error: String?)

    @Query("DELETE FROM pending_scrobbles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_scrobbles WHERE playedAtMs < :cutoffMs")
    suspend fun pruneExpired(cutoffMs: Long)
}
