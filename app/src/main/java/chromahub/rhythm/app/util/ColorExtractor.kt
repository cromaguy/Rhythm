/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.annotation.SuppressLint

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeFidelity
import com.google.android.material.color.utilities.SchemeRainbow
import com.google.android.material.color.utilities.MathUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Data class to store extracted colors from album artwork
 * Stores full Material 3 color scheme for both light and dark themes
 */
data class ExtractedColors(
    val seedColor: Int = 0,
    val isMonochrome: Boolean = false,
    // Light theme colors
    // Primary colors
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,

    // Secondary colors
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,

    // Tertiary colors
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,

    // Dark theme colors
    // Primary colors
    val darkPrimary: Int,
    val darkOnPrimary: Int,
    val darkPrimaryContainer: Int,
    val darkOnPrimaryContainer: Int,

    // Secondary colors
    val darkSecondary: Int,
    val darkOnSecondary: Int,
    val darkSecondaryContainer: Int,
    val darkOnSecondaryContainer: Int,

    // Tertiary colors
    val darkTertiary: Int,
    val darkOnTertiary: Int,
    val darkTertiaryContainer: Int,
    val darkOnTertiaryContainer: Int,

    // Surface colors (shared or from dark scheme)
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int
)

/**
 * Color scoring configuration for album art extraction
 */
data class ColorScoringConfig(
    val targetChroma: Double = 30.0,
    val weightProportion: Double = 0.7,
    val weightChromaAbove: Double = 0.3,
    val weightChromaBelow: Double = 0.1,
    val cutoffChroma: Double = 5.0,
    val cutoffExcitedProportion: Double = 0.01,
    val maxColorCount: Int = 4,
    val maxHueDifference: Int = 90,
    val minHueDifference: Int = 15
)

/**
 * Color extraction configuration
 */
data class ColorExtractionConfig(
    val downscaleMaxDimension: Int = 128,
    val quantizerMaxColors: Int = 128,
    val scoring: ColorScoringConfig = ColorScoringConfig()
)

/**
 * Extracted color cache
 */
private val extractedColorCache = LruCache<Int, ExtractedColors>(32)

/**
 * Utility object for extracting color palettes from album artwork using Rhythm's palette algorithm
 */
@SuppressLint("RestrictedApi")
object ColorExtractor {

    private const val TAG = "ColorExtractor"
    private val gson = Gson()

    /**
    * Extract a Material 3 color palette from album artwork bitmap using Rhythm's palette algorithm
     * Returns null if extraction fails or bitmap is null
     */
    suspend fun extractColorsFromBitmap(
        bitmap: Bitmap?,
        config: ColorExtractionConfig = ColorExtractionConfig()
    ): ExtractedColors? = withContext(Dispatchers.Default) {
        try {
            if (bitmap == null) {
                android.util.Log.w(TAG, "Bitmap is null, cannot extract colors")
                return@withContext null
            }

            val cacheKey = 31 * bitmap.hashCode() + config.hashCode()
            extractedColorCache.get(cacheKey)?.let { cached ->
                return@withContext cached
            }

            val workingBitmap = resizeForExtraction(bitmap, config.downscaleMaxDimension)

            val extractedResult = runCatching {
                val pixels = IntArray(workingBitmap.width * workingBitmap.height)
                workingBitmap.getPixels(
                    pixels,
                    0,
                    workingBitmap.width,
                    0,
                    0,
                    workingBitmap.width,
                    workingBitmap.height
                )

                val fallbackArgb = averageColorArgb(pixels)
                val quantized = QuantizerCelebi.quantize(pixels, config.quantizerMaxColors)

                val (seedArgb, isMonochrome) = extractSeedAndNeutralStatus(quantized, fallbackArgb)
                generateColorSchemeFromSeed(seedArgb, isMonochrome)
            }.getOrElse {
                android.util.Log.e(TAG, "Failed to extract seed color", it)
                generateColorSchemeFromSeed(0xFF6750A4.toInt(), false)
            }

            // Recycle bitmap if it's not the original
            if (workingBitmap !== bitmap) {
                workingBitmap.recycle()
            }

            if (extractedResult != null) {
                extractedColorCache.put(cacheKey, extractedResult)
            }
            extractedResult

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to extract colors from bitmap", e)
            null
        }
    }

    /**
     * Determine seed color and whether the artwork is monochromatic/neutral
     */
    private fun extractSeedAndNeutralStatus(
        quantized: Map<Int, Int>,
        fallbackArgb: Int
    ): Pair<Int, Boolean> {
        if (quantized.isEmpty()) return fallbackArgb to false

        val isNeutral = isMostlyNeutralArtwork(quantized)
        if (isNeutral) {
            val dominantNeutral = quantized.entries
                .filter { isArgbNearGrayscale(it.key) || Hct.fromInt(it.key).chroma <= 8.0 }
                .maxByOrNull { it.value }?.key ?: fallbackArgb
            return dominantNeutral to true
        }

        val dominantSeed = scoreMajorityColor(quantized, fallbackArgb)
        return dominantSeed to false
    }

    /**
     * Score and extract the majority / dominant color from quantized pixels
     */
    private fun scoreMajorityColor(
        colorsToPopulation: Map<Int, Int>,
        fallbackColorArgb: Int
    ): Int {
        if (colorsToPopulation.isEmpty()) return fallbackColorArgb

        val totalPopulation = colorsToPopulation.values.sumOf { it.toLong() }.toDouble()
        if (totalPopulation <= 0.0) return fallbackColorArgb

        // Filter out near-grayscale/noise pixels when colorful pixels are present
        val colorfulEntries = colorsToPopulation.entries.filter { (argb, pop) ->
            pop > 0 && !isArgbNearGrayscale(argb) && Hct.fromInt(argb).chroma >= 6.0
        }

        if (colorfulEntries.isEmpty()) {
            return colorsToPopulation.maxByOrNull { it.value }?.key ?: fallbackColorArgb
        }

        // Group into 12 hue bins (30 degrees each) to cluster shades of the same color family
        val hueBins = DoubleArray(12)
        val hueBinColors = Array(12) { mutableListOf<Pair<Int, Int>>() }

        for ((argb, pop) in colorfulEntries) {
            val hct = Hct.fromInt(argb)
            val bin = ((MathUtils.sanitizeDegreesDouble(hct.hue) / 30.0).toInt()) % 12
            hueBins[bin] += pop.toDouble()
            hueBinColors[bin].add(argb to pop)
        }

        // Find the hue bin with the highest total population (majority artwork color)
        var bestBinIndex = 0
        var maxBinPopulation = 0.0
        for (i in 0 until 12) {
            if (hueBins[i] > maxBinPopulation) {
                maxBinPopulation = hueBins[i]
                bestBinIndex = i
            }
        }

        val dominantCluster = hueBinColors[bestBinIndex]
        if (dominantCluster.isEmpty()) {
            return colorfulEntries.maxByOrNull { it.value }?.key ?: fallbackColorArgb
        }

        // Within the dominant hue cluster, pick the best representative color:
        // Prioritize higher population and balanced tone/chroma
        val bestColor = dominantCluster.maxByOrNull { (argb, pop) ->
            val hct = Hct.fromInt(argb)
            val popScore = (pop.toDouble() / maxBinPopulation) * 100.0

            val tonePenalty = when {
                hct.tone < 20.0 -> (20.0 - hct.tone) * 2.0
                hct.tone > 85.0 -> (hct.tone - 85.0) * 2.0
                else -> 0.0
            }

            val chromaBonus = hct.chroma.coerceIn(0.0, 60.0) * 0.5

            popScore - tonePenalty + chromaBonus
        }?.first

        return bestColor ?: fallbackColorArgb
    }

    /**
     * Generate a complete Material 3 color scheme from a seed color
     */
    private fun generateColorSchemeFromSeed(seedArgb: Int, isMonochrome: Boolean): ExtractedColors? {
        return runCatching {
            val sourceHct = Hct.fromInt(seedArgb)

            val lightScheme = if (isMonochrome) {
                createDynamicScheme(sourceHct, "MONOCHROME", false)
            } else {
                createDynamicScheme(sourceHct, "CONTENT", false)
            }

            val darkScheme = if (isMonochrome) {
                createDynamicScheme(sourceHct, "MONOCHROME", true)
            } else {
                createDynamicScheme(sourceHct, "CONTENT", true)
            }

            ExtractedColors(
                seedColor = seedArgb,
                isMonochrome = isMonochrome,
                primary = lightScheme.primary.toArgb(),
                onPrimary = lightScheme.onPrimary.toArgb(),
                primaryContainer = lightScheme.primaryContainer.toArgb(),
                onPrimaryContainer = lightScheme.onPrimaryContainer.toArgb(),
                secondary = lightScheme.secondary.toArgb(),
                onSecondary = lightScheme.onSecondary.toArgb(),
                secondaryContainer = lightScheme.secondaryContainer.toArgb(),
                onSecondaryContainer = lightScheme.onSecondaryContainer.toArgb(),
                tertiary = lightScheme.tertiary.toArgb(),
                onTertiary = lightScheme.onTertiary.toArgb(),
                tertiaryContainer = lightScheme.tertiaryContainer.toArgb(),
                onTertiaryContainer = lightScheme.onTertiaryContainer.toArgb(),
                darkPrimary = darkScheme.primary.toArgb(),
                darkOnPrimary = darkScheme.onPrimary.toArgb(),
                darkPrimaryContainer = darkScheme.primaryContainer.toArgb(),
                darkOnPrimaryContainer = darkScheme.onPrimaryContainer.toArgb(),
                darkSecondary = darkScheme.secondary.toArgb(),
                darkOnSecondary = darkScheme.onSecondary.toArgb(),
                darkSecondaryContainer = darkScheme.secondaryContainer.toArgb(),
                darkOnSecondaryContainer = darkScheme.onSecondaryContainer.toArgb(),
                darkTertiary = darkScheme.tertiary.toArgb(),
                darkOnTertiary = darkScheme.onTertiary.toArgb(),
                darkTertiaryContainer = darkScheme.tertiaryContainer.toArgb(),
                darkOnTertiaryContainer = darkScheme.onTertiaryContainer.toArgb(),
                surface = darkScheme.surface.toArgb(),
                onSurface = darkScheme.onSurface.toArgb(),
                surfaceVariant = darkScheme.surfaceVariant.toArgb(),
                onSurfaceVariant = darkScheme.onSurfaceVariant.toArgb()
            )
        }.getOrElse {
            android.util.Log.e(TAG, "Failed to generate color scheme", it)
            null
        }
    }

    /**
     * Resize bitmap for efficient color extraction
     */
    private fun resizeForExtraction(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val source = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        if (maxDimension <= 0) return source
        if (source.width <= maxDimension && source.height <= maxDimension) return source

        val scale = maxDimension.toFloat() / max(source.width, source.height).toFloat()
        val newWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
        val newHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
        return source.scale(newWidth, newHeight, true)
    }

    /**
     * Create dynamic color scheme using Material Design utilities
     */
    fun createDynamicScheme(
        sourceHct: Hct,
        paletteStyle: String,
        isDark: Boolean,
        contrastLevel: Double = 0.0,
        chromaMultiplier: Double = 1.0
    ): androidx.compose.material3.ColorScheme {
        val effectiveHct = if (chromaMultiplier != 1.0 && sourceHct.chroma > 0.0) {
            val adjustedChroma = (sourceHct.chroma * chromaMultiplier).coerceIn(0.0, 150.0)
            Hct.from(sourceHct.hue, adjustedChroma, sourceHct.tone)
        } else {
            sourceHct
        }

        val clampedContrast = contrastLevel.coerceIn(-1.0, 1.0)
        val scheme = when (paletteStyle) {
            "TONAL_SPOT" -> SchemeTonalSpot(effectiveHct, isDark, clampedContrast)
            "VIBRANT" -> SchemeVibrant(effectiveHct, isDark, clampedContrast)
            "EXPRESSIVE" -> SchemeExpressive(effectiveHct, isDark, clampedContrast)
            "FRUIT_SALAD" -> SchemeFruitSalad(effectiveHct, isDark, clampedContrast)
            "CONTENT" -> SchemeContent(effectiveHct, isDark, clampedContrast)
            "MONOCHROME" -> SchemeMonochrome(effectiveHct, isDark, clampedContrast)
            "NEUTRAL" -> SchemeNeutral(effectiveHct, isDark, clampedContrast)
            "FIDELITY" -> SchemeFidelity(effectiveHct, isDark, clampedContrast)
            "RAINBOW" -> SchemeRainbow(effectiveHct, isDark, clampedContrast)
            else -> SchemeTonalSpot(effectiveHct, isDark, clampedContrast)
        }

        return androidx.compose.material3.ColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            inversePrimary = Color(scheme.inversePrimary),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary),
            onTertiary = Color(scheme.onTertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            surfaceTint = Color(scheme.primary), // Use primary color as surface tint
            inverseSurface = Color(scheme.inverseSurface),
            inverseOnSurface = Color(scheme.inverseOnSurface),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            scrim = Color(scheme.scrim),
            surfaceBright = Color(scheme.surfaceBright),
            surfaceDim = Color(scheme.surfaceDim),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
            surfaceContainerLow = Color(scheme.surfaceContainerLow),
            surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
            primaryFixed = Color(scheme.primaryFixed),
            primaryFixedDim = Color(scheme.primaryFixedDim),
            onPrimaryFixed = Color(scheme.onPrimaryFixed),
            onPrimaryFixedVariant = Color(scheme.onPrimaryFixedVariant),
            secondaryFixed = Color(scheme.secondaryFixed),
            secondaryFixedDim = Color(scheme.secondaryFixedDim),
            onSecondaryFixed = Color(scheme.onSecondaryFixed),
            onSecondaryFixedVariant = Color(scheme.onSecondaryFixedVariant),
            tertiaryFixed = Color(scheme.tertiaryFixed),
            tertiaryFixedDim = Color(scheme.tertiaryFixedDim),
            onTertiaryFixed = Color(scheme.onTertiaryFixed),
            onTertiaryFixedVariant = Color(scheme.onTertiaryFixedVariant)
        )
    }

    /**
     * Calculate average color from pixels
     */
    private fun averageColorArgb(pixels: IntArray): Int {
        if (pixels.isEmpty()) return 0xFF6750A4.toInt()

        var totalRed = 0L
        var totalGreen = 0L
        var totalBlue = 0L

        for (argb in pixels) {
            totalRed += (argb ushr 16) and 0xFF
            totalGreen += (argb ushr 8) and 0xFF
            totalBlue += argb and 0xFF
        }

        val size = pixels.size.toLong()
        val r = (totalRed / size).toInt().coerceIn(0, 255)
        val g = (totalGreen / size).toInt().coerceIn(0, 255)
        val b = (totalBlue / size).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Check if artwork is mostly neutral (low chroma / black & white / grayscale)
     */
    fun isMostlyNeutralArtwork(colorsToPopulation: Map<Int, Int>): Boolean {
        if (colorsToPopulation.isEmpty()) return false

        var totalPopulation = 0.0
        var neutralPopulation = 0.0
        var highChromaPopulation = 0.0
        var weightedChroma = 0.0

        for ((argb, populationInt) in colorsToPopulation) {
            if (populationInt <= 0) continue
            val population = populationInt.toDouble()
            val hct = Hct.fromInt(argb)
            val isGrayscale = isArgbNearGrayscale(argb)
            val chroma = if (isGrayscale) 0.0 else hct.chroma

            totalPopulation += population
            weightedChroma += chroma * population

            if (chroma <= 8.0 || isGrayscale) {
                neutralPopulation += population
            }
            if (chroma >= 16.0 && !isGrayscale) {
                highChromaPopulation += population
            }
        }

        if (totalPopulation <= 0.0) return false

        val neutralRatio = neutralPopulation / totalPopulation
        val highChromaRatio = highChromaPopulation / totalPopulation
        val meanChroma = weightedChroma / totalPopulation

        return neutralRatio >= 0.82 || (meanChroma <= 8.0 && highChromaRatio <= 0.04)
    }

    /**
     * Check if ARGB color is near grayscale
     */
    fun isArgbNearGrayscale(argb: Int): Boolean {
        val red = (argb ushr 16) and 0xFF
        val green = (argb ushr 8) and 0xFF
        val blue = argb and 0xFF
        return maxOf(abs(red - green), abs(green - blue), abs(red - blue)) <= 10
    }

    /**
     * Clear the extracted color cache
     */
    fun clearExtractedColorCache() {
        extractedColorCache.evictAll()
    }

    /**
     * Convert ExtractedColors to JSON string for storage
     */
    fun colorsToJson(colors: ExtractedColors): String {
        return gson.toJson(colors)
    }

    /**
     * Convert JSON string back to ExtractedColors
     * Returns null if parsing fails
     */
    fun jsonToColors(json: String?): ExtractedColors? {
        if (json == null) return null
        return try {
            gson.fromJson(json, ExtractedColors::class.java)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to parse colors JSON", e)
            null
        }
    }
}
