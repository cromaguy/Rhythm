/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.net.TestUri
import chromahub.rhythm.app.shared.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPreviewPlayerControllerTest {

    private fun sampleSong(): Song = Song(
        id = "preview_1",
        title = "Preview Track",
        artist = "Preview Artist",
        album = "Preview Album",
        duration = 180_000L,
        uri = TestUri("file", "/storage/emulated/0/Music/preview.flac")
    )

    @Test
    fun closeWithoutContinueSetting_doesNotTouchMainServiceQueue() {
        val song = sampleSong()
        val plan = ExternalPreviewHandoffPlan.planForClose(
            song = song,
            currentPositionMs = 42_500L,
            continueOnClose = false,
            alreadyOpenedInRhythm = false
        )

        assertFalse(plan.shouldHandOffToMainService)
        assertNull(plan.song)
    }

    @Test
    fun closeWithContinueSetting_handsOffSongAndPositionOnce() {
        val song = sampleSong()
        val plan = ExternalPreviewHandoffPlan.planForClose(
            song = song,
            currentPositionMs = 42_500L,
            continueOnClose = true,
            alreadyOpenedInRhythm = false
        )

        assertTrue(plan.shouldHandOffToMainService)
        assertEquals(song, plan.song)
        assertEquals(42_500L, plan.startPositionMs)

        // If user already tapped "Open in Rhythm", closing later must not double-trigger handoff.
        val duplicateClose = ExternalPreviewHandoffPlan.planForClose(
            song = song,
            currentPositionMs = 42_500L,
            continueOnClose = true,
            alreadyOpenedInRhythm = true
        )
        assertFalse(duplicateClose.shouldHandOffToMainService)
    }

    @Test
    fun openInApp_handsOffAtCurrentPreviewPositionClampedToZero() {
        val song = sampleSong()
        val plan = ExternalPreviewHandoffPlan.planForOpenInApp(song, currentPositionMs = -10L)

        assertTrue(plan.shouldHandOffToMainService)
        assertEquals(song, plan.song)
        assertEquals(0L, plan.startPositionMs)
    }
}
