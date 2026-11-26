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
import chromahub.rhythm.app.features.local.data.database.entity.ListeningSpanEntity
import chromahub.rhythm.app.features.local.data.database.entity.SongEngagementEntity

@Dao
interface ListeningStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpan(span: ListeningSpanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpans(spans: List<ListeningSpanEntity>)

    @Query("SELECT COUNT(*) FROM listening_spans")
    suspend fun getSpanCount(): Int

    @Query("SELECT * FROM listening_spans WHERE startedAtMs BETWEEN :startMs AND :endMs ORDER BY startedAtMs DESC")
    suspend fun getSpansInRange(startMs: Long, endMs: Long): List<ListeningSpanEntity>

    @Query("DELETE FROM listening_spans WHERE endedAtMs < :cutoffMs")
    suspend fun pruneSpansBefore(cutoffMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagement(engagement: SongEngagementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagements(engagements: List<SongEngagementEntity>)

    @Query("SELECT * FROM song_engagements WHERE songId = :songId LIMIT 1")
    suspend fun getEngagement(songId: String): SongEngagementEntity?

    @Query("SELECT * FROM song_engagements ORDER BY totalPlayDurationMs DESC")
    suspend fun getAllEngagements(): List<SongEngagementEntity>

    @Transaction
    suspend fun recordListeningEvent(
        songId: String,
        songTitle: String?,
        artist: String?,
        album: String?,
        genre: String?,
        startedAtMs: Long,
        endedAtMs: Long,
        durationMs: Long,
        completed: Boolean
    ) {
        insertSpan(
            ListeningSpanEntity(
                songId = songId,
                songTitle = songTitle,
                artist = artist,
                album = album,
                genre = genre,
                startedAtMs = startedAtMs,
                endedAtMs = endedAtMs,
                durationMs = durationMs,
                completed = completed
            )
        )
        val existing = getEngagement(songId)
        val updated = SongEngagementEntity(
            songId = songId,
            playCount = (existing?.playCount ?: 0L) + 1L,
            totalPlayDurationMs = (existing?.totalPlayDurationMs ?: 0L) + durationMs,
            lastPlayedAtMs = maxOf(existing?.lastPlayedAtMs ?: 0L, endedAtMs),
            skipCount = existing?.skipCount ?: 0L
        )
        upsertEngagement(updated)
    }
}
