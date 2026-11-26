/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_tracks",
    indices = [Index(value = ["status"])]
)
data class OfflineTrackEntity(
    @PrimaryKey val songId: String,
    val provider: String,
    val serverUrl: String,
    val localPath: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkPath: String?,
    val fileSizeBytes: Long,
    val status: String,
    val progress: Float,
    val downloadedAtMs: Long,
    val errorMessage: String? = null
)

@Entity(
    tableName = "pending_scrobbles",
    indices = [Index(value = ["provider", "playedAtMs"])]
)
data class PendingScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val provider: String,
    val songId: String,
    val artist: String,
    val title: String,
    val album: String,
    val durationMs: Long,
    val playedAtMs: Long,
    val attemptCount: Int = 0,
    val lastAttemptAtMs: Long? = null,
    val lastError: String? = null
)
