/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listening_spans",
    indices = [
        Index(value = ["songId"]),
        Index(value = ["startedAtMs"])
    ]
)
data class ListeningSpanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val songId: String,
    val songTitle: String?,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationMs: Long,
    val completed: Boolean
)

@Entity(tableName = "song_engagements")
data class SongEngagementEntity(
    @PrimaryKey val songId: String,
    val playCount: Long,
    val totalPlayDurationMs: Long,
    val lastPlayedAtMs: Long,
    val skipCount: Long = 0L
)
