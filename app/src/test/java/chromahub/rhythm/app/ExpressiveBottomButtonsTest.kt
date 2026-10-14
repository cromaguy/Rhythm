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
    fun normalModeLeavesThreeCustomSlotsOutsideTwoFixedActions() {
        assertEquals(5, AppSettings.MAX_EXPRESSIVE_BOTTOM_PINS_NORMAL + AppSettings.fixedBottomButtonsNormal.size)
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

}
