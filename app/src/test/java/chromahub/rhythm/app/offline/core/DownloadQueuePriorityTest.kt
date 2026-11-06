// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A song the user asks for runs before the keep-downloaded sync's long tail; first come first served within a class. */
class DownloadQueuePriorityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000L
    private fun file() = File(tmp.root, "downloads/download_state.json")
    private fun store() = DownloadStateStore(file()) { now++ }

    private fun item(id: String, priority: DownloadPriority = DownloadPriority.MANUAL, pins: Set<String> = setOf(PinSource.MANUAL)) =
        DownloadItem(songId = id, title = "T $id", artist = "A", album = "Al", priority = priority, pins = pins)

    private fun order(s: DownloadStateStore): List<String> {
        val out = ArrayList<String>()
        while (true) {
            val next = s.nextRunnable() ?: return out
            out.add(next.songId)
            s.markComplete(next.songId, 1)
        }
    }

    @Test
    fun aManualDownloadQueuedBehindHundredsOfBulkItemsRunsFirst() {
        val s = store()
        s.enqueueAll((1..870).map { item("bulk$it", DownloadPriority.BULK, setOf(PinSource.FAVORITES)) })
        assertTrue(s.enqueue(item("manual")))
        assertEquals("manual", s.nextRunnable()!!.songId)
        assertEquals("manual", order(s).first())
    }

    @Test
    fun fifoWithinAClassAndBulkKeepsItsEnqueueOrder() {
        val s = store()
        s.enqueueAll(listOf("b1", "b2", "b3").map { item(it, DownloadPriority.BULK) }) // one write, one timestamp each
        s.enqueue(item("m1"))
        s.enqueue(item("m2"))
        s.enqueueAll(listOf("b4").map { item(it, DownloadPriority.BULK) })
        s.enqueue(item("m3"))
        assertEquals(listOf("m1", "m2", "m3", "b1", "b2", "b3", "b4"), order(s))
    }

    @Test
    fun theSongThatIsPlayingGoesBeforeManualAndBulk() {
        val s = store()
        s.enqueue(item("b", DownloadPriority.BULK))
        s.enqueue(item("m"))
        s.enqueue(item("p", DownloadPriority.PLAYING))
        assertEquals(listOf("p", "m", "b"), order(s))
    }

    @Test
    fun askingAgainForASongTheBulkSyncQueuedMovesItUp() {
        val s = store()
        s.enqueueAll(listOf("a", "b", "c").map { item(it, DownloadPriority.BULK) })
        assertFalse("already queued", s.enqueue(item("c", DownloadPriority.MANUAL)))
        assertEquals(DownloadPriority.MANUAL, s.get("c")!!.priority)
        assertEquals("c", s.nextRunnable()!!.songId)
        // never down: a bulk request does not demote a manual one
        s.enqueue(item("c", DownloadPriority.BULK))
        assertEquals(DownloadPriority.MANUAL, s.get("c")!!.priority)
    }

    @Test
    fun aRunningJobIsNotReorderedByAPriorityRequest() {
        val s = store()
        s.enqueue(item("a", DownloadPriority.BULK))
        s.markDownloading("a")
        assertFalse(s.prioritize("a", DownloadPriority.PLAYING))
        assertEquals(DownloadPriority.BULK, s.get("a")!!.priority)
    }

    @Test
    fun prioritizeMovesAQueuedJobAndOnlyUp() {
        val s = store()
        s.enqueueAll(listOf("a", "b").map { item(it, DownloadPriority.BULK) })
        assertTrue(s.prioritize("b", DownloadPriority.PLAYING))
        assertFalse(s.prioritize("b", DownloadPriority.MANUAL))
        assertFalse(s.prioritize("missing", DownloadPriority.PLAYING))
        assertEquals(listOf("b", "a"), order(s))
    }

    @Test
    fun aRequeuedInterruptedJobKeepsItsPlaceInLine() {
        val s = store()
        s.enqueueAll(listOf("a", "b").map { item(it, DownloadPriority.BULK) })
        s.markDownloading("a")
        s.requeue("a") // network dropped
        assertEquals("a", s.nextRunnable()!!.songId)
    }

    @Test
    fun aFailedJobQueuedAgainJoinsTheEndOfItsClass() {
        val s = store()
        s.enqueue(item("a", DownloadPriority.BULK))
        repeat(DownloadStateStore.MAX_ATTEMPTS) { s.markAttemptFailed("a", "HTTP 500") }
        s.enqueue(item("b", DownloadPriority.BULK))
        assertTrue(s.enqueue(item("a", DownloadPriority.BULK)))
        assertEquals(listOf("b", "a"), order(s))
    }

    @Test
    fun priorityAndCompletionTimeSurviveARestart() {
        val s = store()
        s.enqueue(item("bulk", DownloadPriority.BULK))
        s.enqueue(item("play", DownloadPriority.PLAYING))
        s.markDownloading("play")
        s.markComplete("play", 10)
        val reopened = store()
        assertEquals(DownloadPriority.BULK, reopened.get("bulk")!!.priority)
        assertEquals(DownloadPriority.PLAYING, reopened.get("play")!!.priority)
        assertTrue(reopened.get("play")!!.completedAt > 0)
        assertEquals(0L, reopened.get("bulk")!!.completedAt)
    }

    @Test
    fun aLedgerWrittenBeforePriorityDerivesItFromThePins() {
        val s = store()
        s.enqueue(item("mine"))
        s.enqueue(item("synced", DownloadPriority.BULK, setOf(PinSource.FAVORITES)))
        // strip the new keys, as an older build wrote the file
        val json = JSONObject(file().readText())
        val items = json.getJSONArray("items")
        for (i in 0 until items.length()) items.getJSONObject(i).remove("priority")
        file().writeText(json.toString())
        val reopened = store()
        assertEquals(DownloadPriority.MANUAL, reopened.get("mine")!!.priority)
        assertEquals(DownloadPriority.BULK, reopened.get("synced")!!.priority)
        assertEquals("mine", reopened.nextRunnable()!!.songId)
    }
}
