/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayingEqIconAlignmentTest {

    @Test
    fun testLayoutCalculationFillsWidthWithCenteredSymmetry() {
        val testWidths = listOf(24f, 48f, 72f, 100f, 120f)
        val gapFraction = 0.30f
        val bars = 3

        for (w in testWidths) {
            val layout = PlayingEqIconDefaults.calculateEqLayout(
                width = w,
                bars = bars,
                gapFraction = gapFraction
            )

            // Total bars width should equal available width
            assertEquals("Total width should span canvas width", w, layout.totalBarsWidth, 0.001f)

            // Left margin
            val leftMargin = layout.startX
            // Right margin = w - (startX + totalBarsWidth)
            val rightMargin = w - (layout.startX + layout.totalBarsWidth)
            assertEquals("Left and right margins must be symmetric", leftMargin, rightMargin, 0.001f)

            // Bar and gap ratio
            assertEquals(
                "Gap must be exact fraction of bar width",
                layout.barW * gapFraction,
                layout.gap,
                0.001f
            )

            // Individual bar start positions must be monotonically increasing and within bounds
            for (i in 0 until bars) {
                val barLeft = layout.startX + i * (layout.barW + layout.gap)
                val barRight = barLeft + layout.barW
                assertTrue("Bar $i must start within canvas", barLeft >= 0f)
                assertTrue("Bar $i must end within canvas (with margin of float rounding)", barRight <= w + 0.001f)
            }
        }
    }

    @Test
    fun testLayoutWithDifferentBarCounts() {
        for (bars in listOf(2, 3, 4, 5)) {
            val w = 100f
            val layout = PlayingEqIconDefaults.calculateEqLayout(
                width = w,
                bars = bars,
                gapFraction = 0.25f
            )

            assertEquals("Total width should span canvas width for $bars bars", w, layout.totalBarsWidth, 0.001f)
            val expectedBarW = w / (bars + (bars - 1) * 0.25f)
            assertEquals(expectedBarW, layout.barW, 0.001f)
        }
    }

    @Test
    fun testZeroOrNegativeWidthHandlesGracefully() {
        val zeroLayout = PlayingEqIconDefaults.calculateEqLayout(width = 0f, bars = 3)
        assertEquals(0f, zeroLayout.barW, 0.001f)
        assertEquals(0f, zeroLayout.startX, 0.001f)

        val negativeLayout = PlayingEqIconDefaults.calculateEqLayout(width = -10f, bars = 3)
        assertEquals(0f, negativeLayout.barW, 0.001f)

        val zeroBarsLayout = PlayingEqIconDefaults.calculateEqLayout(width = 100f, bars = 0)
        assertEquals(0f, zeroBarsLayout.barW, 0.001f)
    }

    @Test
    fun testDemonstrateLegacyBugFixed() {
        val w = 100f
        val bars = 3
        val gapFraction = 0.30f

        // Broken legacy divisor was: bars + (bars - 1) * (1f + gapFraction) = 3 + 2 * 1.3 = 5.6
        val legacyDivisor = bars + (bars - 1) * (1f + gapFraction)
        val legacyBarW = w / legacyDivisor // 17.857f
        val legacyGap = legacyBarW * gapFraction // 5.357f
        val legacyTotalWidth = bars * legacyBarW + (bars - 1) * legacyGap // 64.285f
        val legacyUnusedRightSpace = w - legacyTotalWidth // 35.714f (35.7% empty space on right!)

        assertTrue("Legacy formula left over 35% empty space on the right", legacyUnusedRightSpace > 35f)

        // New formula fills width and centers
        val newLayout = PlayingEqIconDefaults.calculateEqLayout(width = w, bars = bars, gapFraction = gapFraction)
        val newUnusedRightSpace = w - (newLayout.startX + newLayout.totalBarsWidth)
        assertEquals("New formula eliminates asymmetric empty space", 0f, newUnusedRightSpace, 0.001f)
    }
}
