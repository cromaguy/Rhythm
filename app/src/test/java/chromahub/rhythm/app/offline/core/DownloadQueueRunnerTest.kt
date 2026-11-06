// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import java.io.IOException
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The worker's loop and its stop handling, on a fake clock: runs are chunked, a stop never leaves a job DOWNLOADING
 * and never blocks, one bad job cannot take the queue down.
 */
class DownloadQueueRunnerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 0L
    private fun store(executor: Executor? = null) = DownloadStateStore(File(tmp.root, "d/state.json"), executor) { now }
    private fun queue(s: DownloadStateStore, vararg ids: String, priority: DownloadPriority = DownloadPriority.MANUAL) =
        ids.forEach { s.enqueue(DownloadItem(songId = it, title = it, artist = "A", album = "Al", priority = priority)) }

    /** A runner as the worker builds it: it remembers what the run already tried and passes those jobs over. */
    private fun runner(
        s: DownloadStateStore,
        budget: Long = 10 * 60_000L,
        paused: () -> Boolean = { false },
        process: suspend (DownloadItem) -> ItemOutcome,
    ): DownloadQueueRunner {
        val attemptedInRun = HashSet<String>()
        return DownloadQueueRunner(
            s, { now }, { budget }, paused,
            skip = { it in attemptedInRun },
            process = { item ->
                attemptedInRun += item.songId
                process(item)
            },
        )
    }

    @Test
    fun drainsEveryJobInPriorityOrder() = runBlocking {
        val s = store()
        queue(s, "bulk1", priority = DownloadPriority.BULK)
        queue(s, "m1", "m2")
        val seen = ArrayList<String>()
        val end = runner(s) { item ->
            s.markDownloading(item.songId)
            seen.add(item.songId)
            s.markComplete(item.songId, 1)
            ItemOutcome.DONE
        }.run()
        assertEquals(RunEnd.DRAINED, end)
        assertEquals(listOf("m1", "m2", "bulk1"), seen)
    }

    @Test
    fun aRunEndsWhenItsBudgetIsSpentAndHandsOverWhatIsLeft() = runBlocking {
        val s = store()
        queue(s, "a", "b", "c", "d", "e")
        val ran = ArrayList<String>()
        val end = runner(s, budget = 10 * 60_000L) { item ->
            s.markDownloading(item.songId)
            ran.add(item.songId)
            now += 4 * 60_000L // each song takes four minutes
            s.markComplete(item.songId, 1)
            ItemOutcome.DONE
        }.run()
        assertEquals(RunEnd.BUDGET, end)
        assertEquals("a 0-4, b 4-8, c 8-12 then the budget (10 min) is spent", listOf("a", "b", "c"), ran)
        assertEquals("d", s.nextRunnable()!!.songId)
    }

    @Test
    fun theBudgetIsReadAgainSoAForegroundPromotionExtendsTheRun() = runBlocking {
        val s = store()
        queue(s, "a", "b", "c")
        var budget = 5 * 60_000L
        val ran = ArrayList<String>()
        val end = DownloadQueueRunner(s, { now }, { budget }, { false }) { item ->
            s.markDownloading(item.songId)
            ran.add(item.songId)
            now += 4 * 60_000L
            budget = 45 * 60_000L // promoted to a foreground service during the first song
            s.markComplete(item.songId, 1)
            ItemOutcome.DONE
        }.run()
        assertEquals(RunEnd.DRAINED, end)
        assertEquals(listOf("a", "b", "c"), ran)
    }

    @Test
    fun aQueueThatEmptiesExactlyAtTheBudgetIsDrainedNotAHandover() = runBlocking {
        val s = store()
        queue(s, "a")
        val end = runner(s, budget = 1) { item ->
            now += 10
            s.markDownloading(item.songId)
            s.markComplete(item.songId, 1)
            ItemOutcome.DONE
        }.run()
        assertEquals(RunEnd.DRAINED, end)
    }

    @Test
    fun offlineModeEndsTheRunBeforeTakingAJob() = runBlocking {
        val s = store()
        queue(s, "a")
        var called = false
        val end = runner(s, paused = { true }) { called = true; ItemOutcome.DONE }.run()
        assertEquals(RunEnd.PAUSED, end)
        assertFalse(called)
        assertEquals(DownloadState.QUEUED, s.get("a")!!.state)
    }

    @Test
    fun networkLostAndPausedOutcomesEndTheRun() = runBlocking {
        val s = store()
        queue(s, "a", "b")
        assertEquals(RunEnd.NETWORK_LOST, runner(s) { item -> s.requeue(item.songId); ItemOutcome.NETWORK_LOST }.run())
        assertEquals(RunEnd.PAUSED, runner(s) { item -> s.requeue(item.songId); ItemOutcome.PAUSED }.run())
        assertEquals(setOf(DownloadState.QUEUED), s.items.value.map { it.state }.toSet())
    }

    @Test
    fun aStopRequeuesTheRunningJobAtOnceAndLeavesNothingDownloading() = runBlocking {
        val s = store()
        queue(s, "a", "b")
        val started = CompletableDeferred<Unit>()
        val r = runner(s) { item ->
            s.markDownloading(item.songId)
            started.complete(Unit)
            awaitCancellation() // the transfer; ends when the system stops the job
        }
        val job = launch(Dispatchers.Default) { r.run() }
        started.await()
        assertEquals(DownloadState.DOWNLOADING, s.get("a")!!.state)
        withTimeout(2_000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
        assertEquals("back in the queue, not stuck", DownloadState.QUEUED, s.get("a")!!.state)
        assertEquals("no attempt was used", 0, s.get("a")!!.attempts)
        assertEquals(setOf(DownloadState.QUEUED), s.items.value.map { it.state }.toSet())
        assertEquals("a", s.nextRunnable()!!.songId)
        assertNull(r.current)
    }

    @Test
    fun aStopIsNotWaitingOnTheLedgerWrite() = runBlocking {
        // The ledger's own thread is stuck (a slow disk): requeueing the job and stopping must still finish.
        val parked = ArrayList<Runnable>()
        val s = store(executor = Executor { parked.add(it) })
        queue(s, "a")
        val started = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            runner(s) { item ->
                s.markDownloading(item.songId)
                started.complete(Unit)
                awaitCancellation()
            }.run()
        }
        started.await()
        withTimeout(2_000) { job.cancelAndJoin() }
        assertEquals(DownloadState.QUEUED, s.get("a")!!.state)
        assertTrue("the write is queued behind the stuck thread, not run on the stopping one", parked.isNotEmpty())
        assertFalse("nothing was written on the stopping thread", File(tmp.root, "d/state.json").exists())
        parked.forEach { it.run() }
        assertTrue(File(tmp.root, "d/state.json").exists())
    }

    @Test
    fun aJobThatFinishedInTheInstantOfTheStopStaysFinished() = runBlocking {
        val s = store()
        queue(s, "a")
        val job = launch(Dispatchers.Default) {
            runner(s) { item ->
                s.markDownloading(item.songId)
                s.markComplete(item.songId, 5)
                throw CancellationException("stopped just after the last byte")
            }.run()
        }
        job.join()
        assertEquals(DownloadState.COMPLETE, s.get("a")!!.state)
    }

    @Test
    fun aJobThatLeftTheLedgerIsNotResurrectedByAStop() = runBlocking {
        val s = store()
        queue(s, "a")
        val job = launch(Dispatchers.Default) {
            runner(s) { item ->
                s.markDownloading(item.songId)
                s.remove(item.songId) // the user cancelled it
                throw CancellationException("stopped")
            }.run()
        }
        job.join()
        assertNull(s.get("a"))
    }

    // ---- a failed attempt does not strand the job (fix/download-retry-not-stranded)

    @Test
    fun oneFailedAttemptDoesNotEndTheRun() = runBlocking {
        val s = store()
        queue(s, "blip", "healthy")
        val seen = ArrayList<String>()
        val end = runner(s, process = { item ->
            s.markDownloading(item.songId)
            seen.add(item.songId)
            if (item.songId == "blip") {
                s.markAttemptFailed(item.songId, "HTTP 503")
                ItemOutcome.DONE
            } else {
                s.markComplete(item.songId, 1)
                ItemOutcome.DONE
            }
        }).run()
        assertEquals("a server blip must not end the run and strand the healthy job behind it", RunEnd.DRAINED, end)
        assertEquals(listOf("blip", "healthy"), seen)
        assertEquals(DownloadState.COMPLETE, s.get("healthy")!!.state)
    }

    @Test
    fun aJobThatFailedIsNotRetriedTwiceInTheSameRun() = runBlocking {
        val s = store()
        queue(s, "blip", "healthy")
        val seen = ArrayList<String>()
        runner(s, process = { item ->
            s.markDownloading(item.songId)
            seen.add(item.songId)
            if (item.songId == "blip") {
                s.markAttemptFailed(item.songId, "HTTP 503") // still QUEUED: pickNext would hand it back at once
                ItemOutcome.DONE
            } else {
                s.markComplete(item.songId, 1)
                ItemOutcome.DONE
            }
        }).run()
        assertEquals("one attempt per job per run", 1, seen.count { it == "blip" })
        assertEquals("the attempt is counted, the budget is not burned", 1, s.get("blip")!!.attempts)
        assertEquals("back in the queue for a later run", DownloadState.QUEUED, s.get("blip")!!.state)
    }

    @Test
    fun aTransientFailureIsRetriedOnASubsequentRunAndSucceeds() = runBlocking {
        val s = store()
        queue(s, "blip")
        var fail = true
        val end = runner(s, process = { item ->
            s.markDownloading(item.songId)
            if (fail) {
                s.markAttemptFailed(item.songId, "HTTP 503")
                ItemOutcome.DONE
            } else {
                s.markComplete(item.songId, 1)
                ItemOutcome.DONE
            }
        }).run()
        assertEquals(RunEnd.DRAINED, end)
        assertEquals(DownloadState.QUEUED, s.get("blip")!!.state)
        assertEquals(1, s.get("blip")!!.attempts)

        fail = false // the server is back
        // The retry waits out the gap the failure set (the spaced retries of 2026-10-27); the clock moves past it.
        now += DownloadStateStore.RETRY_DELAY_MS * 2
        assertEquals(RunEnd.DRAINED, runner(s, process = { item ->
            s.markDownloading(item.songId)
            s.markComplete(item.songId, 1)
            ItemOutcome.DONE
        }).run())
        assertEquals(DownloadState.COMPLETE, s.get("blip")!!.state)
    }

    @Test
    fun aJobThatLeftTheLedgerStillHandsOverToTheNextJob() = runBlocking {
        val s = store()
        queue(s, "gone", "healthy")
        val seen = ArrayList<String>()
        val end = runner(s, process = { item ->
            s.markDownloading(item.songId)
            seen.add(item.songId)
            if (item.songId == "gone") {
                s.remove(item.songId)
                ItemOutcome.REMOVED
            } else {
                s.markComplete(item.songId, 1)
                ItemOutcome.DONE
            }
        }).run()
        assertEquals(RunEnd.DRAINED, end)
        assertEquals(listOf("gone", "healthy"), seen)
        assertNull(s.get("gone"))
        assertEquals(1, s.items.value.size)
    }

    @Test
    fun oneBadJobCountsAnAttemptWithItsReasonAndTheRunGoesOn() = runBlocking {
        // A run without the per-run skip (an older caller): each exception is retried at once until the job is FAILED.
        val s = store()
        queue(s, "bad", "good")
        val order = ArrayList<String>()
        val end = DownloadQueueRunner(s, { now }, { 10 * 60_000L }, { false }) { item ->
            s.markDownloading(item.songId)
            order.add(item.songId)
            if (item.songId == "bad") throw IOException()
            s.markComplete(item.songId, 1)
            ItemOutcome.DONE
        }.run()
        assertEquals(RunEnd.DRAINED, end)
        // "bad" gets one try per run now: the next one waits out its gap, so the run cannot spend the budget on three
        // tries seconds apart. It keeps the reason (an empty IOException used to leave none) and never blocks "good".
        assertEquals(RunEnd.DRAINED, end)
        assertEquals(DownloadState.COMPLETE, s.get("good")!!.state)
        assertEquals(DownloadState.QUEUED, s.get("bad")!!.state)
        assertEquals("IOException", s.get("bad")!!.error)
        assertEquals("one try in this run", 1, s.get("bad")!!.attempts)
        assertEquals(listOf("bad", "good"), order)
        now = s.get("bad")!!.retryNotBefore
        assertEquals("and it is runnable again once the gap is over", "bad", s.nextRunnable()!!.songId)
    }

    @Test
    fun aThrowingJobIsTriedOncePerRunAsTheWorkerBuildsIt() = runBlocking {
        val s = store()
        queue(s, "bad", "good")
        val order = ArrayList<String>()
        val end = runner(s) { item ->
            s.markDownloading(item.songId)
            order.add(item.songId)
            if (item.songId == "bad") throw IOException()
            s.markComplete(item.songId, 1)
            ItemOutcome.DONE
        }.run()
        assertEquals(RunEnd.DRAINED, end)
        assertEquals("one attempt per run, not the whole budget on one bad moment", 1, order.count { it == "bad" })
        assertEquals(1, s.get("bad")!!.attempts)
        assertEquals("the reason is recorded", "IOException", s.get("bad")!!.error)
        assertEquals(DownloadState.QUEUED, s.get("bad")!!.state)
        assertEquals(DownloadState.COMPLETE, s.get("good")!!.state)
    }

    @Test
    fun theStoreWritesOnItsOwnThreadAndCoalescesABurst() {
        val parked = ArrayList<Runnable>()
        val s = store(executor = Executor { parked.add(it) })
        val file = File(tmp.root, "d/state.json")
        queue(s, "a", "b", "c")
        s.markDownloading("a")
        s.markComplete("a", 5)
        assertEquals("one pending write for the whole burst", 1, parked.size)
        assertFalse(file.exists())
        parked.single().run()
        assertEquals(1, s.writeCount)
        assertEquals("the newest state was written", DownloadState.COMPLETE, DownloadStateStore(file).get("a")!!.state)
        // and a later change queues the next write
        s.markDownloading("b")
        assertEquals(1, parked.size - 1)
        s.flush()
        assertEquals(DownloadState.DOWNLOADING, DownloadStateStore(file).get("b")!!.state)
    }

    // ---- foreground promotion ----

    @Test
    fun aRefusedPromotionIsNotAskedAgainEverySecond() {
        var t = 0L
        val p = ForegroundPromotion({ t }, retryAfterMs = 60_000L)
        assertTrue("first try", p.shouldAttempt())
        p.record(false)
        repeat(59) { t += 1_000; assertFalse("second ${t / 1000}", p.shouldAttempt()) }
        t += 1_000
        assertTrue("a minute later, again (the app may be in the foreground now)", p.shouldAttempt())
        p.record(true)
        assertTrue(p.promoted)
        t += 1
        assertTrue("once promoted it only updates the notification", p.shouldAttempt())
    }

    @Test
    fun orphanedPartialFilesAreFoundButAResumableOneIsKept() {
        val names = listOf(
            "SUBSONIC__queued.part", "SUBSONIC__removed.part", "SUBSONIC__gone.tx.part", "SUBSONIC__queued.tx.part",
            "SUBSONIC__done.opus", "SUBSONIC__x_album_art.jpg",
        )
        val orphans = OfflineFiles.orphanedParts(names, resumableIds = listOf("SUBSONIC::queued"))
        assertEquals(listOf("SUBSONIC__removed.part", "SUBSONIC__gone.tx.part", "SUBSONIC__queued.tx.part"), orphans)
    }
}