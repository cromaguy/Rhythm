/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.widget.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetResponsiveScaleTest {

    @Test
    fun testWideShortWidgetConstrainedByHeight() {
        // Standard 4x1 widget: wide horizontal space (360dp) but tight vertical space (70dp)
        val width = 360f
        val height = 70f

        val legacyScale = (width / 250f).coerceIn(0.7f, 1.2f)
        assertEquals("Legacy scale ballooned to 1.2x max solely based on width", 1.2f, legacyScale, 0.001f)

        val newScale = RhythmMusicWidget.computeResponsiveScale(width, height)
        assertTrue("New scale must be constrained by height (< 1.0x)", newScale < 1.0f)
        assertEquals("Scale matches height budget", 70f / 80f, newScale, 0.001f)

        // Verify remaining space for controls:
        val padding = 14f * newScale * 2f // top + bottom padding
        val remainingHeight = height - padding
        val playButtonHeight = 48f * newScale
        assertTrue("Play button ($playButtonHeight dp) must comfortably fit inside remaining height ($remainingHeight dp)", playButtonHeight < remainingHeight)
    }

    @Test
    fun testUltraWideFoldableStripWidget() {
        // Foldable unfolded home screen 4x1 / 5x1 strip
        val width = 500f
        val height = 65f

        val scale = RhythmMusicWidget.computeResponsiveScale(width, height)
        assertTrue("Scale must not balloon to 1.2x", scale < 1.0f)
        assertEquals(65f / 80f, scale, 0.001f)
    }

    @Test
    fun testLargeWidgetCanScaleUpToMax() {
        // Large widget with ample width and height
        val width = 350f
        val height = 350f

        val scale = RhythmMusicWidget.computeResponsiveScale(width, height)
        assertEquals(1.2f, scale, 0.001f)
    }

    @Test
    fun testTinyWidgetRespectsScaleFloor() {
        // Very small 1x1 widget
        val width = 50f
        val height = 50f

        val scale = RhythmMusicWidget.computeResponsiveScale(width, height)
        assertEquals("Scale should be clamped to minimum floor of 0.65", 0.65f, scale, 0.001f)
    }

    @Test
    fun testZeroOrNegativeDimensionsReturnFallback() {
        assertEquals(1.0f, RhythmMusicWidget.computeResponsiveScale(0f, 100f), 0.001f)
        assertEquals(1.0f, RhythmMusicWidget.computeResponsiveScale(100f, 0f), 0.001f)
        assertEquals(1.0f, RhythmMusicWidget.computeResponsiveScale(-10f, -10f), 0.001f)
    }
}
