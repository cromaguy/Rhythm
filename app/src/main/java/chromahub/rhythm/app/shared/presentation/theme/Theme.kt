/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import chromahub.rhythm.app.R
import chromahub.rhythm.app.utils.FontLoader
import chromahub.rhythm.app.util.ColorExtractor
import chromahub.rhythm.app.util.ExtractedColors
import com.google.android.material.color.utilities.Hct
import chromahub.rhythm.app.shared.data.model.AppSettings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFruitSalad

private val DarkColorScheme: androidx.compose.material3.ColorScheme
    get() = getCustomColorScheme("Default", true)

private val LightColorScheme: androidx.compose.material3.ColorScheme
    get() = getCustomColorScheme("Default", false)

/**
 * Data class for theme customization color scheme option
 */
data class ColorSchemeOption(
    val name: String,
    val displayName: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color
)

/**
 * Specification for a preset color scheme
 */
data class ThemePresetDefinition(
    val name: String,
    val titleRes: Int,
    val descRes: Int,
    val seedColor: Color,
    val paletteStyle: String = "VIBRANT"
)

val PRESET_THEMES = listOf(
    ThemePresetDefinition("Default", R.string.color_scheme_default_title, R.string.color_scheme_default_desc, PresetDefaultSeed, "TONAL_SPOT"),
    ThemePresetDefinition("Warm", R.string.color_scheme_warm_title, R.string.color_scheme_warm_desc, PresetWarmSeed, "VIBRANT"),
    ThemePresetDefinition("Cool", R.string.color_scheme_cool_title, R.string.color_scheme_cool_desc, PresetCoolSeed, "VIBRANT"),
    ThemePresetDefinition("Forest", R.string.color_scheme_forest_title, R.string.color_scheme_forest_desc, PresetForestSeed, "TONAL_SPOT"),
    ThemePresetDefinition("Rose", R.string.color_scheme_rose_title, R.string.color_scheme_rose_desc, PresetRoseSeed, "VIBRANT"),
    ThemePresetDefinition("Monochrome", R.string.color_scheme_monochrome_title, R.string.color_scheme_monochrome_desc, PresetMonochromeSeed, "MONOCHROME"),
    ThemePresetDefinition("Lavender", R.string.color_scheme_lavender_title, R.string.color_scheme_lavender_desc, PresetLavenderSeed, "EXPRESSIVE"),
    ThemePresetDefinition("Ocean", R.string.color_scheme_ocean_title, R.string.color_scheme_ocean_desc, PresetOceanSeed, "VIBRANT"),
    ThemePresetDefinition("Aurora", R.string.color_scheme_aurora_title, R.string.color_scheme_aurora_desc, PresetAuroraSeed, "VIBRANT"),
    ThemePresetDefinition("Amber", R.string.color_scheme_amber_title, R.string.color_scheme_amber_desc, PresetAmberSeed, "VIBRANT"),
    ThemePresetDefinition("Crimson", R.string.color_scheme_crimson_title, R.string.color_scheme_crimson_desc, PresetCrimsonSeed, "VIBRANT"),
    ThemePresetDefinition("Emerald", R.string.color_scheme_emerald_title, R.string.color_scheme_emerald_desc, PresetEmeraldSeed, "VIBRANT"),
    ThemePresetDefinition("Mint", R.string.color_scheme_mint_title, R.string.color_scheme_mint_desc, PresetMintSeed, "EXPRESSIVE")
)

private val PRESET_THEMES_MAP = PRESET_THEMES.associateBy { it.name }
private val presetSchemeCache = java.util.concurrent.ConcurrentHashMap<String, ColorScheme>()

/**
 * Get preset color scheme options for settings and onboarding screens
 */
fun getPresetColorSchemeOptions(context: Context, darkTheme: Boolean = false): List<ColorSchemeOption> {
    return PRESET_THEMES.map { preset ->
        val scheme = getCustomColorScheme(preset.name, darkTheme)
        ColorSchemeOption(
            name = preset.name,
            displayName = context.getString(preset.titleRes),
            description = context.getString(preset.descRes),
            primaryColor = scheme.primary,
            secondaryColor = scheme.secondary,
            tertiaryColor = scheme.tertiary
        )
    }
}

/**
 * Get custom color scheme based on preset name
 */
@SuppressLint("RestrictedApi")
fun getCustomColorScheme(schemeName: String, darkTheme: Boolean): ColorScheme {
    // Check if it's a custom hex color scheme first
    val customScheme = parseCustomColorScheme(schemeName, darkTheme)
    if (customScheme != null) {
        return customScheme
    }

    val cacheKey = "${schemeName}_${if (darkTheme) "dark" else "light"}"
    presetSchemeCache[cacheKey]?.let { return it }

    val preset = PRESET_THEMES_MAP[schemeName] ?: return if (darkTheme) DarkColorScheme else LightColorScheme
    val hct = Hct.fromInt(preset.seedColor.toArgb())
    val scheme = ColorExtractor.createDynamicScheme(hct, preset.paletteStyle, darkTheme)
    presetSchemeCache[cacheKey] = scheme
    return scheme
}

/**
 * Parse custom color scheme from format: custom_primaryHex_secondaryHex_tertiaryHex or custom_primaryHex
 */
@SuppressLint("RestrictedApi")
fun parseCustomColorScheme(schemeName: String, darkTheme: Boolean): ColorScheme? {
    if (!schemeName.startsWith("custom_")) return null

    val cacheKey = "${schemeName}_${if (darkTheme) "dark" else "light"}"
    presetSchemeCache[cacheKey]?.let { return it }

    val parts = schemeName.split("_")
    if (parts.size < 2) return null

    return try {
        val primaryHex = parts[1].padStart(6, '0')
        val primaryArgb = ("FF$primaryHex").toLong(16).toInt()
        val hct = Hct.fromInt(primaryArgb)
        val scheme = ColorExtractor.createDynamicScheme(hct, "TONAL_SPOT", darkTheme)
        presetSchemeCache[cacheKey] = scheme
        scheme
    } catch (e: Exception) {
        null
    }
}

/**
 * Create a color scheme from extracted album art colors
 */
@SuppressLint("RestrictedApi")
fun getAlbumArtColorScheme(
    colorsJson: String,
    darkTheme: Boolean,
    useExactArtworkColors: Boolean
): androidx.compose.material3.ColorScheme {
    val extractedColors = chromahub.rhythm.app.util.ColorExtractor.jsonToColors(colorsJson)
    
    // Fallback to default if parsing fails
    if (extractedColors == null) {
        return getCustomColorScheme("Default", darkTheme)
    }
    
    val seedArgb = if (extractedColors.seedColor != 0) extractedColors.seedColor else {
        if (darkTheme) extractedColors.darkPrimary else extractedColors.primary
    }
    val sourceHct = com.google.android.material.color.utilities.Hct.fromInt(seedArgb)

    val isMonochrome = extractedColors.isMonochrome ||
        sourceHct.chroma <= 8.0 ||
        chromahub.rhythm.app.util.ColorExtractor.isArgbNearGrayscale(seedArgb)

    return if (isMonochrome) {
        chromahub.rhythm.app.util.ColorExtractor.createDynamicScheme(sourceHct, "MONOCHROME", darkTheme)
    } else if (useExactArtworkColors) {
        chromahub.rhythm.app.util.ColorExtractor.createDynamicScheme(sourceHct, "CONTENT", darkTheme)
    } else {
        val schemeType = when {
            sourceHct.chroma > 45.0 -> "VIBRANT"
            sourceHct.chroma > 18.0 -> "EXPRESSIVE"
            else -> "TONAL_SPOT"
        }
        chromahub.rhythm.app.util.ColorExtractor.createDynamicScheme(sourceHct, schemeType, darkTheme)
    }
}

@Composable
fun RhythmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledTheme: Boolean = false,
    dynamicColor: Boolean = false,
    customColorScheme: String = "Default",
    customFont: String = "System",
    fontSource: String = "SYSTEM",
    customFontPath: String? = null,
    colorSource: String = "CUSTOM",
    extractedAlbumColorsJson: String? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember(context) { AppSettings.getInstance(context) }
    val useExactArtworkColors by appSettings.useExactArtworkColors.collectAsState()
    
    val colorScheme = remember(
        darkTheme, amoledTheme, dynamicColor, customColorScheme,
        colorSource, extractedAlbumColorsJson, useExactArtworkColors
    ) {
        when {
            // Album art colors take highest priority when available
            colorSource == "ALBUM_ART" && extractedAlbumColorsJson != null -> {
                getAlbumArtColorScheme(extractedAlbumColorsJson, darkTheme, useExactArtworkColors)
            }
            // Dynamic Material You colors
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            // Custom preset color schemes & default scheme
            else -> getCustomColorScheme(customColorScheme, darkTheme)
        }.let { scheme ->
            // Apply AMOLED theme modifications if enabled and in dark mode
            if (amoledTheme && darkTheme) {
                scheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceVariant = Color(0xFF121212),
                    surfaceContainer = Color(0xFF121212),
                    surfaceContainerLow = Color(0xFF0A0A0A),
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerHigh = Color(0xFF1E1E1E),
                    surfaceContainerHighest = Color(0xFF2A2A2A),
                    surfaceDim = Color.Black,
                    surfaceBright = Color(0xFF2A2A2A)
                )
            } else scheme
        }
    }
    
    // Load typography based on font source (cached across recompositions)
    val typography = remember(customFont, fontSource, customFontPath, context) {
        when (fontSource) {
            "CUSTOM" -> {
                // Try to load custom font
                val customFontFamily = FontLoader.loadCustomFont(context, customFontPath)
                if (customFontFamily != null) {
                    getTypographyWithCustomFont(customFontFamily)
                } else {
                    // Fall back to system font if custom font fails to load
                    getTypographyForFont(customFont)
                }
            }
            else -> {
                // Use system fonts
                getTypographyForFont(customFont)
            }
        }
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = Shapes,
        content = content
    )
}
