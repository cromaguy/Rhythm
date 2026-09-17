/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.model

/**
 * Represents an AutoEQ profile for a specific headphone/earphone model
 * 
 * AutoEQ profiles are based on measurements and compensations to achieve
 * a neutral, balanced sound signature for different audio devices.
 */
data class AutoEQProfile(
    val name: String,
    val brand: String,
    val type: String, // "Over-Ear", "On-Ear", "In-Ear"
    val bands: List<Float> // 10 bands for full control
) {
    companion object {
        // Standard 10-band equalizer frequencies (Hz)
        val BAND_FREQUENCIES = listOf(
            31,    // Sub-bass
            62,    // Bass
            125,   // Low-mid
            250,   // Mid
            500,   // Mid
            1000,  // Upper-mid
            2000,  // Presence
            4000,  // Presence
            8000,  // Brilliance
            16000  // Air
        )
        
        private val gson = com.google.gson.Gson()

        fun listToJson(profiles: List<AutoEQProfile>): String {
            return gson.toJson(profiles)
        }

        fun listFromJson(json: String?): List<AutoEQProfile> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val type = object : com.google.gson.reflect.TypeToken<List<AutoEQProfile>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun getFrequencyLabel(bandIndex: Int): String {
            return when {
                bandIndex < 0 || bandIndex >= BAND_FREQUENCIES.size -> "?"
                BAND_FREQUENCIES[bandIndex] < 1000 -> "${BAND_FREQUENCIES[bandIndex]}Hz"
                else -> "${BAND_FREQUENCIES[bandIndex] / 1000}kHz"
            }
        }
    }
}

/**
 * Container for all AutoEQ profiles
 * Supports metadata from AutoEQ project
 */
data class AutoEQDatabase(
    val version: Int = 1,
    val source: String = "AutoEQ",
    val license: String = "MIT",
    val bandFrequencies: List<Int> = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000),
    val profiles: List<AutoEQProfile> = emptyList()
) {
    fun searchProfiles(query: String): List<AutoEQProfile> {
        if (query.isBlank()) return profiles
        
        val lowerQuery = query.lowercase()
        return profiles.filter { profile ->
            profile.name.lowercase().contains(lowerQuery) ||
            profile.brand.lowercase().contains(lowerQuery) ||
            profile.type.lowercase().contains(lowerQuery)
        }
    }
    
    fun getProfilesByBrand(brand: String): List<AutoEQProfile> {
        return profiles.filter { it.brand.equals(brand, ignoreCase = true) }
    }
    
    fun getProfilesByType(type: String): List<AutoEQProfile> {
        return profiles.filter { it.type.equals(type, ignoreCase = true) }
    }
    
    fun getAllBrands(): List<String> {
        return profiles.map { it.brand }.distinct().sorted()
    }
    
    fun getAllTypes(): List<String> {
        return profiles.map { it.type }.distinct().sorted()
    }
}
