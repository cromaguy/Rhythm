// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "N left - about M min": remaining count, bytes, the recent rate and the ETA. */
class QueueEtaTest {

    private val minute = 60_000L

    private fun done(id: String, at: Long, bytes: Long = 5_000_000) =
        DownloadItem(songId = id, title = id, artist = "A", album = "Al", state = DownloadState.COMPLETE, sourceBytes = bytes, completedAt = at)

    private fun queued(id: String, bytes: Long = 0, done: Long = 0, state: DownloadState = DownloadState.QUEUED) =
        DownloadItem(songId = id, title = id, artist = "A", album = "Al", state = state, sourceBytes = bytes, sourceDone = done)

    @Test
    fun anEmptyQueueHasNothingLeft() {
        assertEquals(QueueEta.EMPTY, QueueEtas.compute(emptyList(), 0))
        assertEquals(QueueEta.EMPTY, QueueEtas.compute(listOf(done("a", 1)), 100))
    }

    @Test
    fun theRateIsMeasuredOverTheLatestCompletions() {
        // 5 songs finished 1 minute apart: 4 intervals in 4 minutes = 1 song per minute
        val items = (0..4).map { done("d$it", 1_000 * minute + it * minute) }
        assertEquals(1.0, QueueEtas.rate(items, 1_005 * minute)!!, 1e-9)
        // an older slow stretch falls out of the window
        val old = (0..4).map { done("o$it", (it + 1) * 30 * minute) }
        assertEquals(1.0, QueueEtas.rate(old + items, 1_005 * minute, window = 5)!!, 1e-9)
    }

    @Test
    fun etaIsRemainingOverRateRoundedUpToAWholeMinute() {
        val finished = (0..4).map { done("d$it", 1 + it * minute / 2) } // 2 songs a minute
        val waiting = (1..7).map { queued("q$it") }
        val eta = QueueEtas.compute(finished + waiting, 2 * minute + 1)
        assertEquals(7, eta.remaining)
        assertEquals(2.0, eta.songsPerMinute!!, 1e-9)
        assertEquals("ceil(7 / 2)", 4, eta.etaMinutes)
    }

    @Test
    fun noRateWithoutTwoCompletionsOrWhenNothingFinishedLately() {
        val waiting = listOf(queued("q"))
        assertNull(QueueEtas.compute(waiting, 0).songsPerMinute)
        assertNull(QueueEtas.compute(waiting + done("a", 1), 5 * minute).etaMinutes)
        val finished = listOf(done("a", 1), done("b", minute + 1))
        assertTrue(QueueEtas.compute(waiting + finished, 2 * minute).etaMinutes != null)
        val stale = QueueEtas.compute(waiting + finished, minute + 1 + QueueEtas.STALE_MS + 1)
        assertNull("a queue that finished nothing for a quarter of an hour has no recent rate", stale.songsPerMinute)
        assertNull(stale.etaMinutes)
        assertEquals(1, stale.remaining)
    }

    @Test
    fun songsFinishingAtTheSameInstantGiveNoRate() {
        assertNull(QueueEtas.rate(listOf(done("a", 5), done("b", 5)), 6))
    }

    @Test
    fun bytesCountWhatIsLeftAndGuessWhatIsUnknown() {
        val finished = listOf(done("a", 1, bytes = 4_000_000), done("b", minute, bytes = 6_000_000)) // average 5 MB
        val running = queued("r", bytes = 10_000_000, done = 4_000_000, state = DownloadState.DOWNLOADING)
        val unknown = queued("u")
        val eta = QueueEtas.compute(finished + running + unknown, minute)
        assertEquals(6_000_000L + 5_000_000L, eta.remainingBytes)
        assertTrue(eta.bytesEstimated)
        val exact = QueueEtas.compute(finished + running, minute)
        assertEquals(6_000_000L, exact.remainingBytes)
        assertFalse(exact.bytesEstimated)
    }
}
