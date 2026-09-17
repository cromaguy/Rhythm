/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.content.Context
import android.net.Uri
import chromahub.rhythm.app.shared.data.model.AutoEQProfile
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utility class for importing and exporting AutoEQ profiles
 * Supports multiple formats:
 * - FixedBandEQ text format (standard AutoEQ output)
 * - JSON format (for sharing and backup)
 * - Parametric EQ text format
 */
object AutoEQImportExport {
    
    private val gson = Gson()
    
    // Standard 10-band frequencies in Hz
    private val BAND_FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    
    /**
     * Parse a FixedBandEQ text format from AutoEQ
     * Format:
     * Preamp: -5.8 dB
     * Filter 1: ON PK Fc 31 Hz Gain -4.3 dB Q 1.41
     * Filter 2: ON PK Fc 62 Hz Gain +1.8 dB Q 1.41
     * ...
     */
    fun parseFixedBandEQ(text: String, name: String = "Imported Profile"): AutoEQProfile? {
        return try {
            val bands = MutableList(10) { 0f }
            val lines = text.lines()
            var matchedFilterCount = 0
            
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("#") || trimmedLine.contains(": OFF", ignoreCase = true)) {
                    continue
                }
                
                // Match pattern: Filter N: ON PK Fc XXX Hz Gain YYY dB Q ZZZ (or similar variations)
                val filterMatch = Regex("""Filter\s+(\d+).*?Gain\s+([+-]?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE).find(trimmedLine)
                if (filterMatch != null) {
                    val filterNum = filterMatch.groupValues[1].toIntOrNull() ?: continue
                    val gain = filterMatch.groupValues[2].toFloatOrNull() ?: continue
                    
                    val fcMatch = Regex("""Fc\s+(\d+)\s*Hz""", RegexOption.IGNORE_CASE).find(trimmedLine)
                    val explicitFreq = fcMatch?.groupValues?.get(1)?.toIntOrNull()
                    
                    val bandIndex = if (explicitFreq != null) {
                        val exactIndex = BAND_FREQUENCIES.indexOf(explicitFreq)
                        if (exactIndex >= 0) exactIndex else findNearestBandIndex(explicitFreq)
                    } else if (filterNum in 1..10) {
                        filterNum - 1
                    } else {
                        -1
                    }
                    
                    if (bandIndex in 0..9) {
                        bands[bandIndex] = gain.coerceIn(-15f, 15f)
                        matchedFilterCount++
                    }
                }
            }
            
            // Check if we got any valid data
            if (matchedFilterCount > 0 && bands.any { it != 0f }) {
                AutoEQProfile(
                    name = name,
                    brand = extractBrandFromName(name),
                    type = "Unknown",
                    bands = bands.map { it.round(1) }
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse a parametric EQ text format
     * Supports PK (peaking), LSC/LOW_SHELF (low shelf), and HSC/HIGH_SHELF (high shelf) filters.
     * Cascaded filters in series are additive in decibels.
     * Format:
     * Filter N: ON PK Fc XXXX Hz Gain YY.Y dB Q Z.ZZ
     */
    fun parseParametricEQ(text: String, name: String = "Imported Profile"): AutoEQProfile? {
        return try {
            val bandSums = FloatArray(10)
            val lines = text.lines()
            var filterFound = false
            
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("#") || trimmedLine.contains(": OFF", ignoreCase = true)) {
                    continue
                }
                
                // Match parametric filter frequency and gain
                val filterMatch = Regex("""(?:Filter\s+\d+:\s*(?:ON\s+)?)?([A-Z_]+)?.*?Fc\s+(\d+)\s*Hz.*?Gain\s+([+-]?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE).find(trimmedLine)
                if (filterMatch != null) {
                    val filterType = filterMatch.groupValues[1].uppercase()
                    val freq = filterMatch.groupValues[2].toIntOrNull() ?: continue
                    val gain = filterMatch.groupValues[3].toFloatOrNull() ?: continue
                    filterFound = true
                    
                    val isLowShelf = filterType == "LSC" || filterType.contains("LOW_SHELF")
                    val isHighShelf = filterType == "HSC" || filterType.contains("HIGH_SHELF")
                    
                    if (isLowShelf) {
                        for (i in BAND_FREQUENCIES.indices) {
                            val f = BAND_FREQUENCIES[i]
                            if (f <= freq * 0.7f) {
                                bandSums[i] += gain
                            } else if (f <= freq * 1.4f) {
                                bandSums[i] += gain * 0.5f
                            }
                        }
                    } else if (isHighShelf) {
                        for (i in BAND_FREQUENCIES.indices) {
                            val f = BAND_FREQUENCIES[i]
                            if (f >= freq * 1.4f) {
                                bandSums[i] += gain
                            } else if (f >= freq * 0.7f) {
                                bandSums[i] += gain * 0.5f
                            }
                        }
                    } else {
                        val bandIndex = findNearestBandIndex(freq)
                        if (bandIndex in 0..9) {
                            bandSums[bandIndex] += gain
                        }
                    }
                }
            }
            
            if (filterFound && bandSums.any { it != 0f }) {
                AutoEQProfile(
                    name = name,
                    brand = extractBrandFromName(name),
                    type = "Unknown",
                    bands = bandSums.map { it.coerceIn(-15f, 15f).round(1) }
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse JSON format profile
     * Supports both single profile and array of profiles
     */
    fun parseJSON(text: String): List<AutoEQProfile> {
        return try {
            val trimmedText = text.trim()
            val profiles = mutableListOf<AutoEQProfile>()
            val jsonElement = JsonParser.parseString(trimmedText)
            
            if (jsonElement.isJsonArray) {
                val array = jsonElement.asJsonArray
                for (elem in array) {
                    if (elem.isJsonObject) {
                        parseJsonObject(elem.asJsonObject)?.let { profiles.add(it) }
                    }
                }
            } else if (jsonElement.isJsonObject) {
                val obj = jsonElement.asJsonObject
                if (obj.has("profiles") && obj.get("profiles").isJsonArray) {
                    val array = obj.getAsJsonArray("profiles")
                    for (elem in array) {
                        if (elem.isJsonObject) {
                            parseJsonObject(elem.asJsonObject)?.let { profiles.add(it) }
                        }
                    }
                } else {
                    parseJsonObject(obj)?.let { profiles.add(it) }
                }
            }
            
            profiles
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseJsonObject(obj: JsonObject): AutoEQProfile? {
        return try {
            val name = if (obj.has("name") && !obj.get("name").isJsonNull) obj.get("name").asString else "Imported Profile"
            val brand = if (obj.has("brand") && !obj.get("brand").isJsonNull) obj.get("brand").asString else extractBrandFromName(name)
            val type = if (obj.has("type") && !obj.get("type").isJsonNull) obj.get("type").asString else "Unknown"
            
            val bandsElem = obj.get("bands")
            val bands = if (bandsElem != null && bandsElem.isJsonArray) {
                val array = bandsElem.asJsonArray
                val list = (0 until minOf(array.size(), 10)).map { i ->
                    array[i].asFloat
                }
                if (list.size < 10) list + List(10 - list.size) { 0f } else list
            } else {
                List(10) { 0f }
            }
            
            AutoEQProfile(name = name, brand = brand, type = type, bands = bands)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Export profile to FixedBandEQ text format
     */
    fun exportToFixedBandEQ(profile: AutoEQProfile): String {
        val sb = StringBuilder()
        
        // Calculate preamp to prevent clipping
        val maxGain = profile.bands.maxOrNull() ?: 0f
        val preamp = if (maxGain > 0) -maxGain else 0f
        
        sb.appendLine("Preamp: ${preamp.round(1)} dB")
        
        for (i in profile.bands.indices) {
            val freq = BAND_FREQUENCIES.getOrElse(i) { 1000 }
            val gain = profile.bands[i].round(1)
            sb.appendLine("Filter ${i + 1}: ON PK Fc $freq Hz Gain $gain dB Q 1.41")
        }
        
        return sb.toString()
    }
    
    /**
     * Export profile to JSON format
     */
    fun exportToJSON(profile: AutoEQProfile): String {
        return gson.toJson(profile)
    }
    
    /**
     * Export multiple profiles to JSON format
     */
    fun exportToJSON(profiles: List<AutoEQProfile>): String {
        return gson.toJson(mapOf(
            "version" to 1,
            "source" to "Rhythm App Export",
            "bandFrequencies" to BAND_FREQUENCIES,
            "profiles" to profiles
        ))
    }
    
    /**
     * Auto-detect format and parse
     */
    fun autoDetectAndParse(text: String, name: String = "Imported Profile"): List<AutoEQProfile> {
        val trimmedText = text.trim()
        
        return when {
            // JSON format
            trimmedText.startsWith("{") || trimmedText.startsWith("[") -> {
                parseJSON(trimmedText)
            }
            // FixedBandEQ or Parametric EQ format
            trimmedText.contains("Filter", ignoreCase = true) && trimmedText.contains("Gain", ignoreCase = true) -> {
                listOfNotNull(parseFixedBandEQ(trimmedText, name) ?: parseParametricEQ(trimmedText, name))
            }
            trimmedText.contains("Fc", ignoreCase = true) && trimmedText.contains("Gain", ignoreCase = true) -> {
                listOfNotNull(parseParametricEQ(trimmedText, name) ?: parseFixedBandEQ(trimmedText, name))
            }
            // Comma-separated values (simple format: name,brand,type,b1,b2,b3,...,b10)
            trimmedText.contains(",") && !trimmedText.contains("{") -> {
                parseCSV(trimmedText)
            }
            else -> {
                listOfNotNull(parseSpaceSeparated(trimmedText, name))
            }
        }
    }
    
    /**
     * Parse simple CSV format
     * Format: name,brand,type,b1,b2,b3,b4,b5,b6,b7,b8,b9,b10
     */
    fun parseCSV(text: String): List<AutoEQProfile> {
        return try {
            val profiles = mutableListOf<AutoEQProfile>()
            val lines = text.lines().filter { it.isNotBlank() }
            
            for (line in lines) {
                val parts = line.split(",").map { it.trim() }
                if (parts.size >= 13) {
                    // Full format with name, brand, type
                    val name = parts[0]
                    val brand = parts[1]
                    val type = parts[2]
                    val bands = parts.drop(3).take(10).mapNotNull { it.toFloatOrNull() }
                    
                    if (bands.size == 10) {
                        profiles.add(AutoEQProfile(name, brand, type, bands))
                    }
                } else if (parts.size >= 10) {
                    // Just 10 band values
                    val bands = parts.take(10).mapNotNull { it.toFloatOrNull() }
                    if (bands.size == 10) {
                        profiles.add(AutoEQProfile("Imported Profile", "Unknown", "Unknown", bands))
                    }
                }
            }
            
            profiles
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Parse space/tab/newline separated list of 10 band gains
     */
    fun parseSpaceSeparated(text: String, name: String = "Imported Profile"): AutoEQProfile? {
        return try {
            // Split by any whitespace: space, tab, carriage return, newline
            val tokens = text.trim().split(Regex("""\s+""")).map { it.trim() }
            val bands = tokens.mapNotNull { it.toFloatOrNull() }
            
            if (bands.size >= 10) {
                AutoEQProfile(
                    name = name,
                    brand = extractBrandFromName(name),
                    type = "Unknown",
                    bands = bands.take(10)
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Read file content from URI
     */
    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Generate a shareable URL-like format for the profile
     */
    fun generateShareableText(profile: AutoEQProfile): String {
        val bandsStr = profile.bands.joinToString(",") { it.round(1).toString() }
        return buildString {
            appendLine("# Rhythm EQ Profile")
            appendLine("Name: ${profile.name}")
            appendLine("Brand: ${profile.brand}")
            appendLine("Type: ${profile.type}")
            appendLine("Bands: $bandsStr")
            appendLine()
            appendLine("# FixedBandEQ Format (for other apps)")
            append(exportToFixedBandEQ(profile))
        }
    }
    
    // Helper functions
    private fun findNearestBandIndex(freq: Int): Int {
        if (freq <= 0) return -1
        var minDiff = Double.MAX_VALUE
        var nearestIndex = -1
        
        val logFreq = kotlin.math.log10(freq.toDouble())
        for (i in BAND_FREQUENCIES.indices) {
            val logBand = kotlin.math.log10(BAND_FREQUENCIES[i].toDouble())
            val diff = kotlin.math.abs(logBand - logFreq)
            if (diff < minDiff) {
                minDiff = diff
                nearestIndex = i
            }
        }
        
        return nearestIndex
    }
    
    private fun extractBrandFromName(name: String): String {
        val knownBrands = listOf(
            "Sony", "Apple", "Bose", "Sennheiser", "Samsung", "Beats", "Audio-Technica",
            "Jabra", "OnePlus", "Anker", "Soundcore", "Xiaomi", "Marshall", "JBL", "Google",
            "Shure", "Focal", "Beyerdynamic", "AKG", "Nothing", "Oppo", "Realme", "HyperX",
            "SteelSeries", "Razer", "Logitech", "1MORE", "Creative", "HIFIMAN", "Moondrop",
            "FiiO", "Audeze", "Meze", "Dan Clark Audio", "Grado", "Koss", "Etymotic",
            "Final Audio", "Campfire Audio", "ThieAudio", "KZ", "CCA", "TRN", "BLON",
            "Truthear", "Tripowin", "Tanchjim", "Dunu", "iBasso", "Fostex", "Philips"
        )
        
        for (brand in knownBrands) {
            if (name.startsWith(brand, ignoreCase = true)) {
                return brand
            }
        }
        
        return name.split(" ").firstOrNull() ?: "Unknown"
    }
    
    private fun Float.round(decimals: Int): Float {
        var multiplier = 1f
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
