/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import chromahub.rhythm.app.features.local.data.database.entity.TransitionRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransitionRuleDao {
    @Query("SELECT * FROM transition_rules ORDER BY updatedAt DESC")
    fun getAllRulesFlow(): Flow<List<TransitionRuleEntity>>

    @Query("SELECT * FROM transition_rules ORDER BY updatedAt DESC")
    suspend fun getAllRules(): List<TransitionRuleEntity>

    @Query("SELECT * FROM transition_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: String): TransitionRuleEntity?

    @Query(
        """
        SELECT * FROM transition_rules
        WHERE (scopeType = 'PAIR' AND fromSongId = :fromSongId AND toSongId = :toSongId)
           OR (scopeType = 'PLAYLIST' AND scopeId = :playlistId)
           OR scopeType = 'GLOBAL'
        ORDER BY CASE scopeType
            WHEN 'PAIR' THEN 0
            WHEN 'PLAYLIST' THEN 1
            ELSE 2
        END
        LIMIT 1
        """
    )
    suspend fun resolveBestRule(
        fromSongId: String,
        toSongId: String,
        playlistId: String?
    ): TransitionRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: TransitionRuleEntity)

    @Query("DELETE FROM transition_rules WHERE id = :id")
    suspend fun deleteById(id: String)
}
