/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Represents a user-created custom equalizer preset with 10 frequency bands
 */
@Immutable
data class CustomEqualizerPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val bands: List<Float>,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        private val gson = Gson()

        fun toJson(presets: List<CustomEqualizerPreset>): String {
            return gson.toJson(presets)
        }

        fun fromJson(json: String?): List<CustomEqualizerPreset> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val type = object : TypeToken<List<CustomEqualizerPreset>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

/**
 * Preset category types for display, styling, and management
 */
enum class EqualizerPresetType {
    BUILT_IN,
    CUSTOM,
    AUTO_EQ
}

/**
 * Unified representation of any Equalizer Preset (Built-in, Custom, or AutoEQ)
 * used for unified listing, selection, and drag-and-drop reordering.
 */
@Immutable
data class UnifiedEqualizerPreset(
    val id: String,
    val name: String,
    val type: EqualizerPresetType,
    val bands: List<Float>,
    val iconName: String? = null,
    val brand: String? = null,
    val isDeletable: Boolean = false
)
