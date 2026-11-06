// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a keep-downloaded sync may run (fork, 2026-10-26 device finding): the listener's Pixel 9 Pro XL sat at ~53 %
 * of one core with WorkManager logging "Moving WorkSpec to the foreground" once a second. Two things made that
 * loop: the worker, once finished, could be enqueued again at once (the unique work was already finished), and the
 * sync counted a FAILED download as work to do on every run - the recipe hundreds of songs over a slow subway link
 * that never converges. A run is now not repeated sooner than a gap that starts short and doubles while the sync
 * keeps finding something to queue; a sync that changes nothing drops the gap back to the shortest one.
 */
class PinRunIntervalTest {

    private val quiet = PinRunInterval.Run(plan = PinPlan(emptyMap(), emptyMap(), emptySet()), queued = 0)
    private val busy = PinRunInterval.Run(plan = PinPlan(mapOf("a" to setOf(PinSource.FAVORITES)), emptyMap(), emptySet()), queued = 1)

    @Test
    fun theFirstRunMayStartAtOnce() {
        assertEquals(0L, PinRunInterval.delayMs(now = 10_000, lastStartMs = 0, gapMs = 0))
    }

    @Test
    fun aRunThatChangedNothingStillWaitsTheFullGap() {
        val now = 10_000_000L
        assertEquals(595_000L, PinRunInterval.delayMs(now, lastStartMs = now - 5_000, gapMs = 600_000))
        assertEquals("1 s ago, with the shortest gap left", 119_000L, PinRunInterval.delayMs(now, lastStartMs = now - 1_000, gapMs = PinRunInterval.GAP_MIN_MS))
        assertEquals(0L, PinRunInterval.delayMs(now, lastStartMs = now - 600_000, gapMs = 600_000))
        assertEquals("a clock that went backwards does not wait forever", 0L, PinRunInterval.delayMs(now, lastStartMs = now + 60_000, gapMs = 600_000))
    }

    @Test
    fun aSyncThatFoundNothingQueuesNothingAndKeepsTheShortestGap() {
        assertEquals(PinRunInterval.GAP_MIN_MS, PinRunInterval.nextGapMs(previousGapMs = PinRunInterval.GAP_MAX_MS, run = quiet))
    }

    @Test
    fun aSyncThatKeptFindingSongsToQueueStretchesTheGapEachTimeUpToTheCeiling() {
        var gap = 0L
        val seen = ArrayList<Long>()
        repeat(6) {
            gap = PinRunInterval.nextGapMs(gap, busy)
            seen.add(gap)
        }
        assertEquals("2, 4, then the 5 minute ceiling", listOf(120_000L, 240_000L, 300_000L, 300_000L, 300_000L, 300_000L), seen)
        assertEquals("5 minutes", 300_000L, PinRunInterval.GAP_MAX_MS)
    }

    @Test
    fun aChangeAddsOnePlan() {
        assertTrue(PinRunInterval.changed(busy))
        assertFalse(PinRunInterval.changed(quiet))
        assertTrue("a reason-only change (a star on an already downloaded song) still updates the ledger", PinRunInterval.changed(quiet.copy(plan = PinPlan(mapOf("a" to setOf(PinSource.FAVORITES)), emptyMap(), emptySet()))))
    }
}
