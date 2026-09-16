/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Represents a user's audio device with associated AutoEQ profile
 */
@Immutable
data class UserAudioDevice(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: DeviceType = DeviceType.HEADPHONES,
    val brand: String = "",
    val autoEQProfileName: String? = null, // Reference to AutoEQ profile
    val customBandLevels: List<Float>? = null, // Custom EQ if not using AutoEQ
    val monoAudioEnabled: Boolean = false, // Single earpiece mono audio downmix
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class DeviceType(val displayName: String, val icon: String) {
        HEADPHONES("Headphones", "headphones"),
        EARBUDS("Earbuds", "earbuds"),
        IEM("IEMs", "earbuds"),
        SPEAKERS("Speakers", "speaker"),
        BLUETOOTH_SPEAKER("Bluetooth Speaker", "speaker_bluetooth"),
        CAR_AUDIO("Car Audio", "directions_car"),
        STUDIO_MONITORS("Studio Monitors", "speaker"),
        OTHER("Other", "audio")
    }
    
    companion object {
        private val gson = Gson()
        
        /**
         * Serialize a list of devices to JSON string
         */
        fun toJson(devices: List<UserAudioDevice>): String {
            return gson.toJson(devices)
        }
        
        /**
         * Deserialize JSON string to a list of devices
         */
        fun fromJson(json: String?): List<UserAudioDevice> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val type = object : TypeToken<List<UserAudioDevice>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

        private val KNOWN_BRANDS = mapOf(
            "realme" to "realme",
            "sony" to "Sony",
            "sennheiser" to "Sennheiser",
            "apple" to "Apple",
            "airpod" to "Apple",
            "samsung" to "Samsung",
            "galaxy bud" to "Samsung",
            "bose" to "Bose",
            "jbl" to "JBL",
            "soundcore" to "Soundcore",
            "anker" to "Soundcore",
            "audio-technica" to "Audio-Technica",
            "audiotechnica" to "Audio-Technica",
            "ath-" to "Audio-Technica",
            "beyerdynamic" to "Beyerdynamic",
            "oneplus" to "OnePlus",
            "xiaomi" to "Xiaomi",
            "redmi" to "Xiaomi",
            "nothing" to "Nothing",
            "cmf" to "Nothing",
            "moondrop" to "Moondrop",
            "tangzu" to "Tangzu",
            "salnotes" to "7Hz",
            "7hz" to "7Hz",
            "qcy" to "QCY",
            "skullcandy" to "Skullcandy",
            "beats" to "Beats",
            "pixel bud" to "Google",
            "google" to "Google",
            "freebud" to "Huawei",
            "huawei" to "Huawei",
            "enco" to "Oppo",
            "oppo" to "Oppo",
            "vivo" to "Vivo",
            "boat" to "boAt",
            "noise" to "Noise",
            "boult" to "Boult",
            "marshall" to "Marshall",
            "edifier" to "Edifier",
            "fiio" to "FiiO",
            "shure" to "Shure",
            "akg" to "AKG",
            "jlab" to "JLab",
            "1more" to "1MORE",
            "creative" to "Creative",
            "bang & olufsen" to "Bang & Olufsen",
            "b&o" to "Bang & Olufsen",
            "bowers & wilkins" to "Bowers & Wilkins",
            "b&w" to "Bowers & Wilkins",
            "denon" to "Denon",
            "philips" to "Philips",
            "jabra" to "Jabra",
            "final" to "Final",
            "hifiman" to "HIFIMAN",
            "focal" to "Focal",
            "audeze" to "Audeze",
            "truthear" to "Truthear",
            "dunu" to "DUNU",
            "kz" to "KZ",
            "motorola" to "Motorola",
            "moto" to "Motorola"
        )

        /**
         * Infer brand from device name and optional list of database brands.
         */
        fun inferDeviceBrand(name: String, availableBrands: List<String> = emptyList()): String {
            if (name.isBlank()) return ""
            val trimmed = name.trim()

            // 1. Check against available database brands first if provided
            if (availableBrands.isNotEmpty()) {
                val matchedDbBrand = availableBrands.firstOrNull { brand ->
                    trimmed.startsWith(brand, ignoreCase = true) ||
                    Regex("""\b${Regex.escape(brand)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)
                }
                if (matchedDbBrand != null) return matchedDbBrand
            }

            // 2. Check against known brands map
            for ((pattern, brandName) in KNOWN_BRANDS) {
                if (trimmed.contains(pattern, ignoreCase = true)) {
                    val matchingDbBrand = availableBrands.firstOrNull { it.equals(brandName, ignoreCase = true) }
                    return matchingDbBrand ?: brandName
                }
            }

            // 3. Fallback: check if first word matches any database brand
            val firstWord = trimmed.split(" ", "-", "_").firstOrNull().orEmpty()
            if (firstWord.length >= 2) {
                val dbMatch = availableBrands.firstOrNull { it.equals(firstWord, ignoreCase = true) }
                if (dbMatch != null) return dbMatch
            }

            return ""
        }

        /**
         * Infer DeviceType from device name and device ID.
         */
        fun inferDeviceType(name: String, deviceId: String = ""): DeviceType {
            val lower = name.lowercase().trim()
            val lowerId = deviceId.lowercase().trim()

            if (lowerId == "wired_headset" || lowerId == "wired_headphones" || lower.contains("wired")) {
                return DeviceType.HEADPHONES
            }

            if (lower.contains("car") || lower.contains("sync") || lower.contains("uconnect") || lower.contains("carplay") || lower.contains("auto")) {
                return DeviceType.CAR_AUDIO
            }

            if (lower.contains("speaker") || lower.contains("soundbar") || lower.contains("boombox") || lower.contains("charge") || lower.contains("flip") || lower.contains("clip")) {
                return if (lowerId.startsWith("bt_")) DeviceType.BLUETOOTH_SPEAKER else DeviceType.SPEAKERS
            }

            if (lower.contains("iem") || lower.contains("in-ear") || lower.contains("tangzu") || lower.contains("salnotes") || lower.contains("chu") || lower.contains("wan'er")) {
                return DeviceType.IEM
            }

            if (lower.contains("bud") || lower.contains("airpod") || lower.contains("tws") || lower.contains("freebud") || lower.contains("enco") || lower.contains("tune") || lower.contains("dot") || lower.contains("neo") || lower.contains("wireless")) {
                return DeviceType.EARBUDS
            }

            if (lower.contains("headphone") || lower.contains("over-ear") || lower.contains("on-ear") || lower.contains("wh-") || lower.contains("xm4") || lower.contains("xm5")) {
                return DeviceType.HEADPHONES
            }

            if (lower.contains("studio") || lower.contains("monitor")) {
                return DeviceType.STUDIO_MONITORS
            }

            return DeviceType.HEADPHONES
        }
    }
}
