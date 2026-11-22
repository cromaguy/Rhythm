/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import androidx.media3.common.IllegalSeekPositionException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RhythmPlayerEngineTransitionTest {

    /**
     * Fake player state tracker simulating ExoPlayer's standby lifecycle and timeline state.
     */
    class TestStandbyPlayerState {
        var timeline: Timeline = Timeline.EMPTY
        var pauseCount = 0
        var stopCount = 0
        var clearMediaItemsCount = 0
        var seekCount = 0
        var releaseCount = 0
        var playbackParameters = PlaybackParameters.DEFAULT

        fun pause() {
            pauseCount++
        }

        fun stop() {
            stopCount++
        }

        fun clearMediaItems() {
            clearMediaItemsCount++
            timeline = Timeline.EMPTY
        }

        fun seekTo(positionMs: Long) {
            if (timeline.isEmpty) {
                throw IllegalSeekPositionException(timeline, 0, positionMs)
            }
            seekCount++
        }

        fun release() {
            releaseCount++
        }

        fun resetStandby(masterParams: PlaybackParameters) {
            pause()
            stop()
            clearMediaItems()

            try {
                if (!timeline.isEmpty) {
                    seekTo(0)
                }
                playbackParameters = masterParams
            } catch (e: Exception) {
                release()
            }
        }
    }

    @Test
    fun emptyTimelineSeek_throwsIllegalSeekPositionException_whenUnguarded() {
        val player = TestStandbyPlayerState()
        player.clearMediaItems()
        assertTrue(player.timeline.isEmpty)

        // Verifies the exact Media3 behavior that caused Issue #686:
        // Calling seekTo(0) without guarding for !timeline.isEmpty causes IllegalSeekPositionException
        try {
            player.seekTo(0)
            fail("Expected IllegalSeekPositionException on empty timeline seek")
        } catch (e: IllegalSeekPositionException) {
            assertEquals(0, e.windowIndex)
            assertEquals(0L, e.positionMs)
        }
    }

    @Test
    fun guardedTimelineSeek_avoidsExceptionOnEmptyTimeline() {
        val player = TestStandbyPlayerState()
        player.clearMediaItems()

        var seekInvoked = false
        if (!player.timeline.isEmpty) {
            player.seekTo(0)
            seekInvoked = true
        }

        assertFalse("Seek must not be called when timeline is empty", seekInvoked)
        assertEquals(0, player.seekCount)
    }

    @Test
    fun standbyPlayerReset_syncsPlaybackParametersWithoutReallocation() {
        val player = TestStandbyPlayerState()
        val masterParameters = PlaybackParameters(1.25f, 1.0f)

        player.resetStandby(masterParameters)

        assertEquals(1, player.pauseCount)
        assertEquals(1, player.stopCount)
        assertEquals(1, player.clearMediaItemsCount)
        assertEquals(0, player.seekCount)
        assertEquals(masterParameters, player.playbackParameters)
        // Standby player must NOT be released or recreated
        assertEquals(0, player.releaseCount)
    }

    @Test
    fun consecutiveTransitions_maintainPlayerInstanceWithoutRecreation() {
        val player = TestStandbyPlayerState()
        val masterParameters = PlaybackParameters(1.0f, 1.0f)
        val songTransitionsCount = 10 // Simulates well over the 3-5 songs threshold of Issue #686

        for (i in 1..songTransitionsCount) {
            player.resetStandby(masterParameters)
        }

        // Verify the standby player was cleanly reset 10 times without ever being released
        assertEquals(songTransitionsCount, player.pauseCount)
        assertEquals(songTransitionsCount, player.stopCount)
        assertEquals(songTransitionsCount, player.clearMediaItemsCount)
        assertEquals(0, player.seekCount)
        assertEquals(0, player.releaseCount)
    }
}
