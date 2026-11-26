/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transition_rules",
    indices = [Index(value = ["scopeType", "scopeId"])]
)
data class TransitionRuleEntity(
    @PrimaryKey val id: String,
    val scopeType: String,
    val scopeId: String?,
    val fromSongId: String?,
    val toSongId: String?,
    val mode: String,
    val durationMs: Int,
    val curveIn: String,
    val curveOut: String,
    val overlapPercent: Float,
    val bpmSync: Boolean,
    val harmonicMatch: Boolean,
    val updatedAt: Long
)
