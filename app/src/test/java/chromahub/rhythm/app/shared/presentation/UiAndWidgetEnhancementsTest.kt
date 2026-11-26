/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation

import chromahub.rhythm.app.infrastructure.widget.glance.RhythmMusicWidget
import chromahub.rhythm.app.shared.presentation.components.common.DragSelectGeometry
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.screens.settings.SearchableSettingItem
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingsFuzzyMatcher
import chromahub.rhythm.app.util.ColorExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiAndWidgetEnhancementsTest {

    @Test
    fun settingsFuzzyMatcherFindsSubsequencesAndMergesHighlights() {
        val entries = listOf(
            SearchableSettingItem(
                id = "eq",
                title = "Parametric Equalizer",
                description = "20-band AutoEQ & DSP sound stage",
                keywords = listOf("eq", "peq", "dsp"),
                icon = MaterialSymbolIcon("equalizer"),
                route = "eq",
                parentScreen = "Audio"
            ),
            SearchableSettingItem(
                id = "usb",
                title = "Bit-Perfect USB DAC",
                description = "Direct USB exclusive output routing",
                keywords = listOf("usb", "dac", "hi-res"),
                icon = MaterialSymbolIcon("usb"),
                route = "audio",
                parentScreen = "Audio"
            )
        )

        val fuzzyEq = SettingsFuzzyMatcher.search("eqlz", entries)
        assertTrue(fuzzyEq.isNotEmpty())
        assertEquals("eq", fuzzyEq.first().item.id)
        assertTrue(fuzzyEq.first().titleMatchedRanges.isNotEmpty())

        val multiWord = SettingsFuzzyMatcher.search("usb exclusive", entries)
        assertTrue(multiWord.isNotEmpty())
        assertEquals("usb", multiWord.first().item.id)
    }

    @Test
    fun dragSelectGeometryComputesEdgeAutoScrollAndBidirectionalRanges() {
        val topScroll = DragSelectGeometry.autoScrollFraction(pointerY = 10f, viewportTop = 0f, viewportBottom = 1000f, zonePx = 50f)
        val centerScroll = DragSelectGeometry.autoScrollFraction(pointerY = 500f, viewportTop = 0f, viewportBottom = 1000f, zonePx = 50f)
        val bottomScroll = DragSelectGeometry.autoScrollFraction(pointerY = 990f, viewportTop = 0f, viewportBottom = 1000f, zonePx = 50f)

        assertTrue(topScroll < -0.5f)
        assertEquals(0f, centerScroll, 1e-4f)
        assertTrue(bottomScroll > 0.5f)

        assertEquals(listOf(5, 4, 3, 2), DragSelectGeometry.rangeIndices(5, 2).toList())
        assertEquals(listOf(2, 3, 4), DragSelectGeometry.rangeIndices(2, 4).toList())
    }

    @Test
    fun glanceWidgetSerializesAndParsesUpNextQueueCap() {
        val serialized = RhythmMusicWidget.serializeUpNext(
            listOf("  Song One — Artist A  ", "Song\nTwo", "Song Three", "Song Four Ignored")
        )
        val parsed = RhythmMusicWidget.parseUpNext(serialized)
        assertEquals(listOf("Song One — Artist A", "Song Two", "Song Three"), parsed)
    }

    @Test
    fun monochromeCoverWithTinyColoredBadgeStaysNeutral() {
        val grayscaleBins = mapOf(
            0xFF141414.toInt() to 450,
            0xFFE2E2E2.toInt() to 480,
            0xFFD42020.toInt() to 70 // 7% red sticker
        )
        assertTrue(ColorExtractor.isMostlyNeutralArtwork(grayscaleBins))

        val vibrantCover = mapOf(
            0xFF102080.toInt() to 600,
            0xFFD84020.toInt() to 300,
            0xFF202020.toInt() to 100
        )
        assertFalse(ColorExtractor.isMostlyNeutralArtwork(vibrantCover))
    }
}
