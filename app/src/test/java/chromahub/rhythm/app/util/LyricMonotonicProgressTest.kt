/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LyricMonotonicProgressTest {

    /**
     * Replicates the monotonic position resolution implemented in MusicViewModel and SyncedLyricsView.
     */
    private class MonotonicProgressClamper(private val seekThresholdMs: Long = 1000L) {
        var lastMonotonicPositionMs = 0L

        fun resolve(rawPositionMs: Long, isPlaying: Boolean = true): Long {
            val delta = lastMonotonicPositionMs - rawPositionMs
            val effective = if (!isPlaying || rawPositionMs >= lastMonotonicPositionMs || delta > seekThresholdMs) {
                lastMonotonicPositionMs = rawPositionMs
                rawPositionMs
            } else {
                lastMonotonicPositionMs
            }
            return effective
        }
    }

    @Test
    fun `audio clock jitter does not cause playback position to decrease`() {
        val clamper = MonotonicProgressClamper()

        // Simulated Media3 audio clock micro-drifts:
        // 1000ms -> 1080ms -> 1060ms (jitter) -> 1140ms -> 1120ms (jitter) -> 1200ms
        val inputs = listOf(1000L, 1080L, 1060L, 1140L, 1120L, 1200L)
        val outputs = inputs.map { clamper.resolve(it) }

        assertEquals(listOf(1000L, 1080L, 1080L, 1140L, 1140L, 1200L), outputs)

        for (i in 1 until outputs.size) {
            assertTrue(
                "Position must be monotonically non-decreasing during continuous playback",
                outputs[i] >= outputs[i - 1]
            )
        }
    }

    @Test
    fun `monotonic clamping prevents lyric highlight from bouncing back across line boundary`() {
        val clamper = MonotonicProgressClamper()

        // Lyrics lines:
        // Line 0: [00:00.00] Intro
        // Line 1: [00:01.07] Chorus Starts (1070ms)
        val lyricLines = listOf(
            LyricLine(0L, "Intro"),
            LyricLine(1070L, "Chorus Starts")
        )

        fun findCurrentLineIndex(timeMs: Long): Int {
            return lyricLines.indexOfLast { it.timestamp <= timeMs }
        }

        // Without clamping, raw positions: 1000ms, 1080ms, 1060ms (drops below 1070ms!), 1140ms
        val rawLineIndices = listOf(1000L, 1080L, 1060L, 1140L).map { findCurrentLineIndex(it) }
        // Line indices bounce: Line 0 -> Line 1 -> Line 0 (BOUNCE/ROLLBACK!) -> Line 1
        assertEquals(listOf(0, 1, 0, 1), rawLineIndices)

        // With monotonic clamping:
        val clampedLineIndices = listOf(1000L, 1080L, 1060L, 1140L)
            .map { clamper.resolve(it) }
            .map { findCurrentLineIndex(it) }

        // Bouncing is completely eliminated!
        assertEquals(listOf(0, 1, 1, 1), clampedLineIndices)
    }

    @Test
    fun `intentional user seeks are respected and reset monotonic position`() {
        val clamper = MonotonicProgressClamper(seekThresholdMs = 1000L)

        // Normal playback up to 30000ms
        clamper.resolve(29000L)
        clamper.resolve(30000L)

        // User seeks backward to 5000ms (> 1000ms jump)
        val seekBack = clamper.resolve(5000L)
        assertEquals(5000L, seekBack)

        // Resumes playback from 5000ms
        val next = clamper.resolve(5100L)
        assertEquals(5100L, next)

        // User seeks forward to 45000ms
        val seekForward = clamper.resolve(45000L)
        assertEquals(45000L, seekForward)
    }

    @Test
    fun `paused playback accepts exact position`() {
        val clamper = MonotonicProgressClamper()

        clamper.resolve(10000L, isPlaying = true)
        // User scrubs when paused
        val scrubbed = clamper.resolve(9500L, isPlaying = false)
        assertEquals(9500L, scrubbed)
    }
}
