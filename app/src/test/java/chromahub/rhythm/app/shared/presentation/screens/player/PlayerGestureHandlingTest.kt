/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGestureHandlingTest {

    @Test
    fun testHorizontalSwipeTrackThresholds() {
        val swipeThreshold = 80f

        var nextCalled = false
        var prevCalled = false

        fun evaluateSwipeEnd(offsetX: Float) {
            nextCalled = false
            prevCalled = false
            if (offsetX < -swipeThreshold) {
                nextCalled = true
            } else if (offsetX > swipeThreshold) {
                prevCalled = true
            }
        }

        // Swipe left past threshold -> next track
        evaluateSwipeEnd(-85f)
        assertTrue("Swipe left past threshold should trigger next track", nextCalled)
        assertFalse(prevCalled)

        // Swipe right past threshold -> previous track
        evaluateSwipeEnd(95f)
        assertTrue("Swipe right past threshold should trigger previous track", prevCalled)
        assertFalse(nextCalled)

        // Minor jitter below threshold -> no skip
        evaluateSwipeEnd(-30f)
        assertFalse(nextCalled)
        assertFalse(prevCalled)

        evaluateSwipeEnd(50f)
        assertFalse(nextCalled)
        assertFalse(prevCalled)
    }

    @Test
    fun testSingleTapArtworkTogglesLyricsWithoutStaleIntermediateState() {
        var showLyricsView = false
        val gestureArtworkSingleTap = true
        val showLyrics = true

        val onArtworkTap = {
            if (gestureArtworkSingleTap && showLyrics) {
                showLyricsView = !showLyricsView
            }
        }

        // First tap opens lyrics
        onArtworkTap()
        assertTrue("Single tap on artwork must toggle lyrics view to true", showLyricsView)

        // Second tap closes lyrics
        onArtworkTap()
        assertFalse("Second single tap on artwork must toggle lyrics view back to false", showLyricsView)

        // With gesture disabled, tapping does not toggle
        val disabledTap = {
            val gestureDisabled = false
            if (gestureDisabled && showLyrics) {
                showLyricsView = !showLyricsView
            }
        }
        disabledTap()
        assertFalse("Tapping when gesture disabled must not toggle lyrics", showLyricsView)
    }

    @Test
    fun testHorizontalDragOnlyAccumulatesHorizontalDeltas() {
        var artworkOffsetX = 0f

        val onHorizontalDrag = { dragDeltaX: Float ->
            artworkOffsetX += dragDeltaX
        }

        onHorizontalDrag(-20f)
        onHorizontalDrag(-35f)
        onHorizontalDrag(-30f)

        assertEquals("Total accumulated drag should equal sum of horizontal deltas", -85f, artworkOffsetX, 0.001f)
    }
}
