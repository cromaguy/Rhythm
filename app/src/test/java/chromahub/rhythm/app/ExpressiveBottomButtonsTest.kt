/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app

import chromahub.rhythm.app.shared.data.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveBottomButtonsTest {

    @Test
    fun maxExpressiveBottomPinsNormal_isTwo() {
        assertEquals(2, AppSettings.MAX_EXPRESSIVE_BOTTOM_PINS_NORMAL)
    }

    @Test
    fun maxExpressiveBottomButtonsMerge_isFive() {
        assertEquals(5, AppSettings.MAX_EXPRESSIVE_BOTTOM_BUTTONS_MERGE)
    }

    @Test
    fun fixedBottomButtonsNormal_hasLyricsAndFavorite() {
        val fixed = AppSettings.fixedBottomButtonsNormal
        assertEquals(2, fixed.size)
        assertTrue(fixed.contains("LYRICS"))
        assertTrue(fixed.contains("FAVORITE"))
    }

    @Test
    fun defaultExpressiveBottomButtonsNormal_hasOnlyQueueDeviceMore() {
        val expected = listOf("DEVICE", "QUEUE", "MORE")
        assertEquals(3, expected.size)
        assertTrue(expected.contains("DEVICE"))
        assertTrue(expected.contains("QUEUE"))
        assertTrue(expected.contains("MORE"))
    }

    @Test
    fun defaultExpressiveBottomButtonsMerge_hasFiveButtons() {
        val expected = listOf("LYRICS", "FAVORITE", "DEVICE", "QUEUE", "MORE")
        assertEquals(AppSettings.MAX_EXPRESSIVE_BOTTOM_BUTTONS_MERGE, expected.size)
    }

    @Test
    fun normalModeSanitizationAllowsCustomButtonsAndPreservesMore() {
        val allButtons = listOf(
            "LYRICS", "FAVORITE", "DEVICE", "QUEUE", "MORE",
            "SHUFFLE", "REPEAT", "EQUALIZER", "SPEED", "SLEEP_TIMER",
            "ADD_TO_PLAYLIST", "ALBUM", "ARTIST", "SONG_INFO", "SHARE"
        )
        val fixed = AppSettings.fixedBottomButtonsNormal

        val input = listOf("SHUFFLE", "REPEAT", "LYRICS", "FAVORITE", "EQUALIZER")
        val sanitized = input.filter { it in allButtons && it !in fixed }.toMutableList()
        if (!sanitized.contains("MORE")) {
            sanitized.add("MORE")
        }

        assertFalse(sanitized.contains("LYRICS"))
        assertFalse(sanitized.contains("FAVORITE"))
        assertTrue(sanitized.contains("SHUFFLE"))
        assertTrue(sanitized.contains("REPEAT"))
        assertTrue(sanitized.contains("EQUALIZER"))
        assertTrue(sanitized.contains("MORE"))
    }

    @Test
    fun normalModeHiddenSanitizationNeverHidesMore() {
        val allButtons = listOf(
            "LYRICS", "FAVORITE", "DEVICE", "QUEUE", "MORE",
            "SHUFFLE", "REPEAT", "EQUALIZER"
        )
        val fixed = AppSettings.fixedBottomButtonsNormal

        val inputHidden = setOf("DEVICE", "QUEUE", "MORE", "LYRICS")
        val sanitized = inputHidden.filter { it in allButtons && it !in fixed && it != "MORE" }.toSet()

        assertTrue(sanitized.contains("DEVICE"))
        assertTrue(sanitized.contains("QUEUE"))
        assertFalse(sanitized.contains("MORE"))
        assertFalse(sanitized.contains("LYRICS"))
    }

    @Test
    fun normalModeLimitsPinsToTwoAndAlwaysIncludesMore() {
        val visibleButtons = listOf("SHUFFLE", "REPEAT", "SPEED", "EQUALIZER", "MORE")
        val pins = visibleButtons.filter { it != "MORE" }.take(AppSettings.MAX_EXPRESSIVE_BOTTOM_PINS_NORMAL)
        val active = visibleButtons.filter { it in pins || it == "MORE" }

        assertEquals(3, active.size)
        assertEquals(listOf("SHUFFLE", "REPEAT", "MORE"), active)
    }

    @Test
    fun mergeModeCapsActiveButtonsAtMaxLimit() {
        val allPinned = listOf(
            "LYRICS", "FAVORITE", "DEVICE", "QUEUE", "MORE",
            "SHUFFLE", "REPEAT", "EQUALIZER"
        )
        val hidden = emptySet<String>()
        val active = allPinned.filter { !hidden.contains(it) }.take(AppSettings.MAX_EXPRESSIVE_BOTTOM_BUTTONS_MERGE)

        assertEquals(AppSettings.MAX_EXPRESSIVE_BOTTOM_BUTTONS_MERGE, active.size)
        assertEquals(listOf("LYRICS", "FAVORITE", "DEVICE", "QUEUE", "MORE"), active)
    }
}
