/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import chromahub.rhythm.app.ui.theme.getAlbumArtColorScheme
import chromahub.rhythm.app.ui.theme.getCustomColorScheme
import chromahub.rhythm.app.ui.theme.resolveIntensity
import chromahub.rhythm.app.util.ColorExtractor
import chromahub.rhythm.app.util.ExtractedColors
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSchemeTest {
    @Test
    fun dynamicSchemeIntensityScalesContrastAndChroma() {
        val standardIntensity = resolveIntensity("STANDARD")
        val mediumIntensity = resolveIntensity("MEDIUM")
        val vividIntensity = resolveIntensity("VIVID")

        assertEquals(0.0, standardIntensity.first, 0.001)
        assertEquals(1.0, standardIntensity.second, 0.001)

        assertEquals(0.12, mediumIntensity.first, 0.001)
        assertEquals(1.12, mediumIntensity.second, 0.001)

        assertEquals(0.22, vividIntensity.first, 0.001)
        assertEquals(1.25, vividIntensity.second, 0.001)

        val darkDefaultStandard = getCustomColorScheme("Default", darkTheme = true, themeIntensity = "STANDARD")
        val darkDefaultVivid = getCustomColorScheme("Default", darkTheme = true, themeIntensity = "VIVID")
        assertNotEquals(darkDefaultStandard.primaryContainer.toArgb(), darkDefaultVivid.primaryContainer.toArgb())

        val lightDefaultStandard = getCustomColorScheme("Default", darkTheme = false, themeIntensity = "STANDARD")
        val lightDefaultVivid = getCustomColorScheme("Default", darkTheme = false, themeIntensity = "VIVID")
        assertNotEquals(lightDefaultStandard.primary.toArgb(), lightDefaultVivid.primary.toArgb())

        val darkCrimsonStandard = getCustomColorScheme("Crimson", darkTheme = true, themeIntensity = "STANDARD")
        val darkCrimsonVivid = getCustomColorScheme("Crimson", darkTheme = true, themeIntensity = "VIVID")
        assertNotEquals(darkCrimsonStandard.primaryContainer.toArgb(), darkCrimsonVivid.primaryContainer.toArgb())
    }

    @Test
    fun albumArtColorSchemePreservesHueAndDoesNotRotateToPurple() {
        val yellowSeed = 0xFFFFD600.toInt()
        val hct = Hct.fromInt(yellowSeed)

        val darkVibrant = ColorExtractor.createDynamicScheme(hct, "VIBRANT", true)
        val darkExpressive = ColorExtractor.createDynamicScheme(hct, "EXPRESSIVE", true)

        // Expressive scheme rotates yellow hue to purple (0xFFF7B0E8) and secondary to olive green (0xFFB3CEA6)
        assertEquals(0xFFF7B0E8.toInt(), darkExpressive.primary.toArgb())
        assertEquals(0xFFB3CEA6.toInt(), darkExpressive.secondary.toArgb())

        // Vibrant scheme keeps yellow hue
        assertEquals(0xFFE9C400.toInt(), darkVibrant.primary.toArgb())

        // Test getAlbumArtColorScheme integration with JSON
        val extracted = ExtractedColors(
            seedColor = yellowSeed,
            isMonochrome = false,
            primary = yellowSeed,
            onPrimary = 0xFF000000.toInt(),
            primaryContainer = yellowSeed,
            onPrimaryContainer = 0xFF000000.toInt(),
            secondary = yellowSeed,
            onSecondary = 0xFF000000.toInt(),
            secondaryContainer = yellowSeed,
            onSecondaryContainer = 0xFF000000.toInt(),
            tertiary = yellowSeed,
            onTertiary = 0xFF000000.toInt(),
            tertiaryContainer = yellowSeed,
            onTertiaryContainer = 0xFF000000.toInt(),
            darkPrimary = yellowSeed,
            darkOnPrimary = 0xFF000000.toInt(),
            darkPrimaryContainer = yellowSeed,
            darkOnPrimaryContainer = 0xFF000000.toInt(),
            darkSecondary = yellowSeed,
            darkOnSecondary = 0xFF000000.toInt(),
            darkSecondaryContainer = yellowSeed,
            darkOnSecondaryContainer = 0xFF000000.toInt(),
            darkTertiary = yellowSeed,
            darkOnTertiary = 0xFF000000.toInt(),
            darkTertiaryContainer = yellowSeed,
            darkOnTertiaryContainer = 0xFF000000.toInt(),
            surface = 0xFF000000.toInt(),
            onSurface = 0xFFFFFFFF.toInt(),
            surfaceVariant = 0xFF000000.toInt(),
            onSurfaceVariant = 0xFFFFFFFF.toInt()
        )
        val json = ColorExtractor.colorsToJson(extracted)
        val darkScheme = getAlbumArtColorScheme(json, darkTheme = true, themeIntensity = "STANDARD")

        assertNotEquals(0xFFF7B0E8.toInt(), darkScheme.primary.toArgb())
        assertEquals(darkVibrant.primary, darkScheme.primary)
    }

    @Test
    fun albumArtColorSchemeAppliesIntensity() {
        val yellowSeed = 0xFFFFD600.toInt()
        val extracted = ExtractedColors(
            seedColor = yellowSeed,
            isMonochrome = false,
            primary = yellowSeed,
            onPrimary = 0xFF000000.toInt(),
            primaryContainer = yellowSeed,
            onPrimaryContainer = 0xFF000000.toInt(),
            secondary = yellowSeed,
            onSecondary = 0xFF000000.toInt(),
            secondaryContainer = yellowSeed,
            onSecondaryContainer = 0xFF000000.toInt(),
            tertiary = yellowSeed,
            onTertiary = 0xFF000000.toInt(),
            tertiaryContainer = yellowSeed,
            onTertiaryContainer = 0xFF000000.toInt(),
            darkPrimary = yellowSeed,
            darkOnPrimary = 0xFF000000.toInt(),
            darkPrimaryContainer = yellowSeed,
            darkOnPrimaryContainer = 0xFF000000.toInt(),
            darkSecondary = yellowSeed,
            darkOnSecondary = 0xFF000000.toInt(),
            darkSecondaryContainer = yellowSeed,
            darkOnSecondaryContainer = 0xFF000000.toInt(),
            darkTertiary = yellowSeed,
            darkOnTertiary = 0xFF000000.toInt(),
            darkTertiaryContainer = yellowSeed,
            darkOnTertiaryContainer = 0xFF000000.toInt(),
            surface = 0xFF000000.toInt(),
            onSurface = 0xFFFFFFFF.toInt(),
            surfaceVariant = 0xFF000000.toInt(),
            onSurfaceVariant = 0xFFFFFFFF.toInt()
        )
        val json = ColorExtractor.colorsToJson(extracted)
        val standardScheme = getAlbumArtColorScheme(json, darkTheme = true, themeIntensity = "STANDARD")
        val vividScheme = getAlbumArtColorScheme(json, darkTheme = true, themeIntensity = "VIVID")

        // In dark theme, container saturation and tone scale with intensity
        assertNotEquals(standardScheme.primaryContainer.toArgb(), vividScheme.primaryContainer.toArgb())

        // In light theme, primary tone also scales
        val lightStandardScheme = getAlbumArtColorScheme(json, darkTheme = false, themeIntensity = "STANDARD")
        val lightVividScheme = getAlbumArtColorScheme(json, darkTheme = false, themeIntensity = "VIVID")
        assertNotEquals(lightStandardScheme.primary.toArgb(), lightVividScheme.primary.toArgb())
    }

    @Test
    fun expressiveSchemePreservesDistinctContainerElevation() {
        val darkCrimson = getCustomColorScheme("Crimson", darkTheme = true, themeIntensity = "STANDARD")
        val lightCrimson = getCustomColorScheme("Crimson", darkTheme = false, themeIntensity = "STANDARD")

        // Dark theme: surfaceContainer and surfaceContainerHigh must never match
        assertNotEquals(darkCrimson.surfaceContainer.toArgb(), darkCrimson.surfaceContainerHigh.toArgb())

        // Light theme: surfaceContainer (White) and surfaceContainerHigh (tinted) must never match
        assertNotEquals(lightCrimson.surfaceContainer.toArgb(), lightCrimson.surfaceContainerHigh.toArgb())
    }

    @Test
    fun mediumAndVividColorsNeverWashOutInDarkOrBlackenInLight() {
        val presets = listOf("Default", "Crimson", "Ocean", "Lavender", "Amber", "Mint", "Forest", "Rose")
        val contrasts = listOf("STANDARD" to 0.0, "MEDIUM" to 0.12, "VIVID" to 0.22)

        for (preset in presets) {
            for (dark in listOf(false, true)) {
                for ((_, contrast) in contrasts) {
                    val pDef = chromahub.rhythm.app.ui.theme.PRESET_THEMES.find { it.name == preset }!!
                    val hct = Hct.fromInt(pDef.seedColor.toArgb())
                    val scheme = ColorExtractor.createDynamicScheme(hct, pDef.paletteStyle, dark, contrastLevel = contrast)
                    val p = Hct.fromInt(scheme.primary.toArgb())

                    if (dark) {
                        // Dark theme primary should stay vibrant, not washed out to tone 87+
                        assertTrue("Primary in dark mode was washed out for $preset", p.tone <= 83.0)
                    } else {
                        // Light theme primary should stay readable, not blackened down to accessibility tone 22
                        assertTrue("Primary in light mode was too dark for $preset (tone=${p.tone})", p.tone >= 30.0)
                    }
                }
            }
        }
    }
}
