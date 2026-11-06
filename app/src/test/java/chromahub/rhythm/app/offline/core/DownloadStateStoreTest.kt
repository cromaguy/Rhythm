// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Offline cache, item 1: the persisted download queue and ledger. */
class DownloadStateStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000L
    private fun file() = File(tmp.root, "downloads/download_state.json")
    private fun store() = DownloadStateStore(file()) { now++ }

    private fun item(id: String, title: String = "T $id") =
        DownloadItem(songId = id, title = title, artist = "A", album = "Al", payload = """{"id":"$id"}""")

    /** A queued job that a keep-downloaded sync (not the user) asked for. */
    private fun item2(id: String, vararg pins: String) =
        DownloadItem(songId = id, title = "T $id", artist = "A", album = "Al", payload = """{"id":"$id"}""", pins = pins.toSet())

    @Test
    fun enqueueQueuesOnceAndDoesNotRequeueARunningOrCompleteJob() {
        val s = store()
        assertTrue(s.enqueue(item("SUBSONIC::1")))
        assertFalse("already queued", s.enqueue(item("SUBSONIC::1")))
        s.markDownloading("SUBSONIC::1")
        assertFalse("already running", s.enqueue(item("SUBSONIC::1")))
        s.markComplete("SUBSONIC::1", 5_000)
        assertFalse("already complete", s.enqueue(item("SUBSONIC::1")))
        assertEquals(DownloadState.COMPLETE, s.get("SUBSONIC::1")!!.state)
    }

    @Test
    fun nextRunnableIsTheOldestQueuedJob() {
        val s = store()
        s.enqueue(item("a"))
        s.enqueue(item("b"))
        s.enqueue(item("c"))
        assertEquals("a", s.nextRunnable()!!.songId)
        s.markDownloading("a")
        assertEquals("b", s.nextRunnable()!!.songId)
        s.markComplete("a", 1)
        s.markComplete("b", 1)
        assertEquals("c", s.nextRunnable()!!.songId)
        s.markComplete("c", 1)
        assertNull(s.nextRunnable())
    }

    @Test
    fun theLedgerSurvivesARestartAndAJobTheProcessDiedOnIsQueuedAgain() {
        val s = store()
        s.enqueue(item("a"))
        s.enqueue(item("b"))
        s.markDownloading("a")
        s.reportSourceProgress("a", 400, 1_000)
        s.markComplete("b", 7_000)

        val reopened = store()
        assertEquals(2, reopened.items.value.size)
        val a = reopened.get("a")!!
        assertEquals("the download died mid-way", DownloadState.DOWNLOADING, a.state)
        assertEquals("progress is not persisted; the partial file is the truth", 0L, a.sourceDone)
        assertEquals("""{"id":"a"}""", a.payload)
        assertEquals(1, reopened.recoverInterrupted())
        assertEquals(DownloadState.QUEUED, reopened.get("a")!!.state)
        assertEquals("a", reopened.nextRunnable()!!.songId)

        val b = reopened.get("b")!!
        assertEquals(DownloadState.COMPLETE, b.state)
        assertEquals(7_000L, b.sourceBytes)
    }

    @Test
    fun aJobWaitsForTheUserAfterThreeAttemptsAndCanBeQueuedAgain() {
        val s = store()
        s.enqueue(item("a"))
        repeat(3) {
            s.markAttemptFailed("a", "HTTP 500")
            now += DownloadStateStore.RETRY_DELAY_MS * 4
        }
        val waiting = s.get("a")!!
        assertEquals("out of automatic tries, but not written off", DownloadState.QUEUED, waiting.state)
        assertEquals("HTTP 500", waiting.error)
        assertNull("a waiting job is not picked up", s.nextRunnable())

        // Queued again from the menu: a fresh budget, runnable at once.
        assertTrue("a waiting job can be queued again", s.enqueue(item("a")))
        assertEquals("a", s.nextRunnable()!!.songId)
        s.markDownloading("a")
        s.markComplete("a", 10)
        assertEquals(DownloadState.COMPLETE, s.get("a")!!.state)
    }

    // 2026-10-27 "streaming downloads are failing sometimes": three tries half a second apart (the worker's 5 s pause)
    // turned one server blip into a permanently failed song. Automatic tries are spaced now, and the last failure
    // leaves the song waiting for the user instead of FAILED for good.
    @Test
    fun aFailedAttemptWaitsBeforeTheNextTryInsteadOfSpendingEveryTryAtOnce() {
        val s = store()
        s.enqueue(item("a"))
        for (attempt in 1 until DownloadStateStore.MAX_ATTEMPTS) {
            s.markAttemptFailed("a", "HTTP 503")
            val queued = s.get("a")!!
            assertEquals("still worth another automatic try", DownloadState.QUEUED, queued.state)
            assertTrue("the next try is not immediate", queued.retryNotBefore >= now)
            assertNull("nothing runs before the wait is over", s.nextRunnable())
            now = queued.retryNotBefore
            assertEquals("runnable once the wait is over", "a", s.nextRunnable()!!.songId)
        }
        s.markAttemptFailed("a", "HTTP 503")
        assertNull("it is not picked up again on its own", s.nextRunnable())
        val waiting = s.get("a")!!
        assertEquals("the automatic budget is spent, not the song", DownloadState.QUEUED, waiting.state)
        assertEquals("the reason is kept while it waits for the user", "HTTP 503", waiting.error)
        assertEquals(DownloadStateStore.MAX_ATTEMPTS, waiting.attempts)
    }

    @Test
    fun aUserRetryOfAWaitingSongGetsAFreshBudgetAndNoWait() {
        val s = store()
        s.enqueue(item("a"))
        repeat(DownloadStateStore.MAX_ATTEMPTS) { s.markAttemptFailed("a", "HTTP 503") }
        assertNull(s.nextRunnable())

        s.retryFailed()
        val retried = s.get("a")!!
        assertEquals(DownloadState.QUEUED, retried.state)
        assertEquals("a fresh budget", 0, retried.attempts)
        assertNull("the old reason is gone", retried.error)
        assertEquals(0L, retried.retryNotBefore)
        assertEquals("a", s.nextRunnable()!!.songId)
    }

    @Test
    fun aSongThatReallyFailsStopsTryingAndSaysWhy() {
        val s = store()
        s.enqueue(item("a"))
        repeat(DownloadStateStore.MAX_ATTEMPTS) { s.markAttemptFailed("a", "HTTP 404 (not found)") }
        assertNull("the automatic tries are over", s.nextRunnable())
        val row = s.get("a")!!
        assertEquals("the row says why, not just that it failed", "HTTP 404 (not found)", row.error)
        assertEquals(listOf("a"), s.waitingForUser().map { it.songId })
    }

    @Test
    fun aSongLeftWithNoReasonWhenTheBudgetRunsOutLeavesTheLedgerAndTheRestAreLifted() {
        val s = store()
        // Both came from a keep-downloaded sync. "gone" has lost its last reason (the sync dropped it before the file
        // went); "keep" still has one. A MANUAL pin would keep a song forever, by design, so neither has one.
        s.enqueue(item2("gone"))
        s.enqueue(item2("keep", PinSource.FAVORITES))
        repeat(DownloadStateStore.MAX_ATTEMPTS) { s.markAttemptFailed("gone", "HTTP 503") }
        repeat(DownloadStateStore.MAX_ATTEMPTS) { s.markAttemptFailed("keep", "HTTP 503") }
        val released = s.liftOrRelease()
        // The sync queues the pinned one again with its reason (mergePins), as PinSync does with its plan's adds.
        s.enqueue(DownloadItem(songId = "keep", title = "T keep", artist = "A", album = "Al", pins = setOf(PinSource.FAVORITES)))
        assertEquals("only what nothing wants any more", listOf("gone"), released)
        assertNull("released: it leaves the ledger", s.get("gone"))
        assertEquals("the pinned one is lifted for another automatic round", 0, s.get("keep")!!.attempts)
        assertEquals("keep", s.nextRunnable()!!.songId)
    }

    @Test
    fun theWaitBeforeARetrySurvivesARestart() {
        val s = store()
        s.enqueue(item("a"))
        s.markAttemptFailed("a", "HTTP 503")
        val wait = s.get("a")!!.retryNotBefore
        now = 0L
        val reopened = store()
        assertEquals(wait, reopened.get("a")!!.retryNotBefore)
        assertNull("the server is not hammered straight after a restart", reopened.nextRunnable())
    }

    @Test
    fun aFailureIsOnlyCountedWhileTheJobIsStillTheOneThatWasTried() {
        val s = store()
        s.enqueue(item("a"))
        s.markDownloading("a")
        assertTrue(s.markAttemptFailed("a", "HTTP 500", onlyIfDownloading = true))
        assertEquals(1, s.get("a")!!.attempts)

        // Offline mode or a lost network put it back in the queue during the attempt: not the server's fault.
        s.markDownloading("a")
        s.requeue("a")
        assertFalse(s.markAttemptFailed("a", "HTTP 500", onlyIfDownloading = true))
        assertEquals("no attempt used", 1, s.get("a")!!.attempts)
        assertEquals(DownloadState.QUEUED, s.get("a")!!.state)

        // A job that finished in the instant before the failure was recorded stays complete.
        s.markDownloading("a")
        s.markComplete("a", 10)
        assertFalse(s.markAttemptFailed("a", "HTTP 500", onlyIfDownloading = true))
        assertEquals(DownloadState.COMPLETE, s.get("a")!!.state)
    }

    @Test
    fun theNextJobSkipsWhatTheRunAlreadyTried() {
        val s = store()
        s.enqueue(item("a"))
        s.enqueue(item("b"))
        assertEquals("a", s.nextRunnable()!!.songId)
        s.markDownloading("a")
        s.markAttemptFailed("a", "HTTP 500") // still QUEUED, but waiting out its gap, so "b" goes next
        assertEquals("the failed job waits out its gap", "b", s.nextRunnable()!!.songId)
        assertEquals("the one this run already tried is passed over", "b", s.nextRunnable(skip = { it == "a" })!!.songId)
        assertNull("and nothing else is left for this run", s.nextRunnable(skip = { true }))
    }

    @Test
    fun progressUpdatesTheFlowButIsNotWrittenToDisk() {
        val s = store()
        s.enqueue(item("a"))
        s.markDownloading("a")
        val before = file().lastModified()
        val sizeBefore = file().length()
        s.reportSourceProgress("a", 300, 1_000)
        val live = s.items.value.single()
        assertEquals(300L, live.sourceDone)
        assertEquals(1_000L, live.sourceBytes)
        assertEquals(sizeBefore, file().length())
        assertEquals(before, file().lastModified())
    }

    @Test
    fun removingAJobForgetsItAndItsFlowEntry() {
        val s = store()
        s.enqueue(item("a"))
        s.enqueue(item("b"))
        assertNotNull(s.remove("a"))
        assertNull(s.get("a"))
        assertEquals(listOf("b"), s.items.value.map { it.songId })
        assertEquals(listOf("b"), store().items.value.map { it.songId })
        assertNull(s.remove("missing"))
    }

    @Test
    fun removeActiveKeepsTheCompletedDownloads() {
        val s = store()
        s.enqueue(item("a"))
        s.enqueue(item("b"))
        s.enqueue(item("c"))
        s.markComplete("b", 10)
        s.markAttemptFailed("c", "x", maxAttempts = 1)
        assertEquals(setOf("a", "c"), s.removeActive().toSet())
        assertEquals(listOf("b"), s.items.value.map { it.songId })
    }

    @Test
    fun aDownloadedFileIsMappedBackToItsTrack() {
        val s = store()
        s.enqueue(item("SUBSONIC::abc123"))
        assertEquals("SUBSONIC::abc123", s.songIdOfFile("SUBSONIC__abc123", OfflineFiles::safeName))
        assertNull(s.songIdOfFile("SUBSONIC__zzz", OfflineFiles::safeName))
    }

    @Test
    fun adoptCompleteRecordsADownloadFromBeforeTheLedger() {
        val s = store()
        assertTrue("a song the ledger never saw is recorded as complete", s.adoptComplete(item("old"), sourceBytes = 9_000))
        assertEquals(DownloadState.COMPLETE, s.get("old")!!.state)
        assertEquals(9_000L, s.get("old")!!.sourceBytes)
        assertFalse("it is already complete: nothing to do", s.adoptComplete(item("old"), sourceBytes = 9_000))
    }

    @Test
    fun aCorruptLedgerStartsEmptyInsteadOfCrashing() {
        file().parentFile!!.mkdirs()
        file().writeText("{not json")
        assertTrue(store().items.value.isEmpty())
    }

    // Review 2026-10-07: queueing a 1,500-song playlist rewrote the whole ledger once per song, on the
    // main thread (about 1 GB of writes): one write for the batch.
    @Test
    fun enqueueAllQueuesABatchWithOneWrite() {
        val s = store()
        s.enqueue(item("done"))
        s.markComplete("done", 1)
        val before = s.writeCount
        val queued = s.enqueueAll((1..500).map { item("s$it") } + item("done"))
        assertEquals(500, queued)
        assertEquals("one write for the whole batch", before + 1, s.writeCount)
        assertEquals(501, s.items.value.size)
        assertEquals("s1", s.nextRunnable()!!.songId)
        assertEquals(0, s.enqueueAll(listOf(item("s1"), item("s2"))))
        assertEquals("nothing queued, nothing written", before + 1, s.writeCount)
        assertEquals(501, DownloadStateStore(file()).items.value.size)
    }

    // The repository finishes a file and publishes its list before the worker has finished the job;
    // adopting that as COMPLETE would let a process death lose the rest of the job.
    @Test
    fun adoptCompleteLeavesAJobTheWorkerOwns() {
        val s = store()
        s.enqueue(item("a"))
        assertFalse(s.adoptComplete(item("a"), sourceBytes = 9))
        assertEquals(DownloadState.QUEUED, s.get("a")!!.state)
        s.markDownloading("a")
        assertFalse(s.adoptComplete(item("a"), sourceBytes = 9))
        assertEquals(DownloadState.DOWNLOADING, s.get("a")!!.state)
        repeat(DownloadStateStore.MAX_ATTEMPTS) { s.markAttemptFailed("a", "x") }
        assertTrue("a job that ran out of tries and whose file is on disk is complete", s.adoptComplete(item("a"), sourceBytes = 9))
        assertEquals(DownloadState.COMPLETE, s.get("a")!!.state)
    }

    @Test
    fun rewritePayloadsChangesOnlyWhatTheFunctionChangesWithOneWrite() {
        val s = store()
        s.enqueueAll(listOf(item("a"), item("b"), item("c").copy(payload = null)))
        val before = s.writeCount
        val changed = s.rewritePayloads { if (it.contains("a")) it.replace("a", "A") else it }
        assertEquals(1, changed)
        assertEquals(before + 1, s.writeCount)
        assertEquals("""{"id":"A"}""", DownloadStateStore(file()).get("a")!!.payload)
        assertEquals("""{"id":"b"}""", s.get("b")!!.payload)
        assertEquals(0, s.rewritePayloads { it })
        assertEquals("nothing changed, nothing written", before + 1, s.writeCount)
    }

    // ---- pin reasons (keep-downloaded sync) ----

    private fun pinned(id: String, vararg pins: String) = item(id).copy(pins = pins.toSet())

    @Test
    fun aPlaylistReasonSurvivesTheLedgerWhateverTheNameHoldsAndALegacyIdReasonReadsBackAsIs() {
        val odd = PinSource.playlist("Mix: \"Chill\" \\ 夜 / 2")
        val legacy = "playlist:SUBSONIC::playlist::42"
        val s = store()
        s.enqueueAll(listOf(pinned("a", odd, legacy)))
        s.markComplete("a", 5)
        val pins = DownloadStateStore(file()).get("a")!!.pins
        assertEquals(setOf(odd, legacy), pins)
        assertEquals("Mix: \"Chill\" \\ 夜 / 2", PinSource.playlistName(odd))
        assertEquals(setOf(legacy), pins.filter(PinSource::isLegacyPlaylistId).toSet())
    }

    @Test
    fun pinsPersistAndALedgerFromBeforePinsIsManual() {
        val s = store()
        s.enqueueAll(listOf(pinned("a", PinSource.FAVORITES, "playlist:Road trip"), item("b")))
        s.markComplete("a", 5)
        val reloaded = DownloadStateStore(file())
        assertEquals(setOf(PinSource.FAVORITES, "playlist:Road trip"), reloaded.get("a")!!.pins)
        assertEquals(setOf(PinSource.MANUAL), reloaded.get("b")!!.pins)

        // A ledger written by the build before pins existed has no "pins" key: those are the user's own downloads.
        file().writeText("""{"version":1,"items":[{"songId":"old","title":"T","artist":"A","album":"B","state":"COMPLETE","sourceBytes":7}]}""")
        assertEquals(setOf(PinSource.MANUAL), DownloadStateStore(file()).get("old")!!.pins)
    }

    @Test
    fun anEmptyPinListStaysEmptyAfterARestart() {
        val s = store()
        s.enqueue(item("a"))
        s.applyPins(emptyMap(), mapOf("a" to setOf(PinSource.MANUAL)))
        assertEquals(emptySet<String>(), DownloadStateStore(file()).get("a")!!.pins)
    }

    @Test
    fun enqueuingAnAlreadyDownloadedSongJustAddsTheReason() {
        val s = store()
        s.enqueue(pinned("a", PinSource.FAVORITES))
        s.markComplete("a", 5)
        val before = s.writeCount
        assertEquals("not queued again", 0, s.enqueueAll(listOf(pinned("a", PinSource.MANUAL))))
        assertEquals(setOf(PinSource.FAVORITES, PinSource.MANUAL), s.get("a")!!.pins)
        assertEquals(DownloadState.COMPLETE, s.get("a")!!.state)
        assertEquals(before + 1, s.writeCount)
        assertEquals(0, s.enqueueAll(listOf(pinned("a", PinSource.MANUAL))))
        assertEquals("nothing new, nothing written", before + 1, s.writeCount)
    }

    @Test
    fun applyPinsChangesOnlyLedgerEntriesAndDeletesNothing() {
        val s = store()
        s.enqueueAll(listOf(pinned("a", PinSource.FAVORITES), pinned("b", PinSource.FAVORITES, "playlist:Road trip")))
        val before = s.writeCount
        s.applyPins(
            adds = mapOf("c" to setOf("playlist:Road trip"), "a" to setOf("playlist:Daily mix")),
            drops = mapOf("b" to setOf(PinSource.FAVORITES), "a" to setOf(PinSource.FAVORITES)),
        )
        assertEquals(before + 1, s.writeCount)
        assertEquals(setOf("playlist:Daily mix"), s.get("a")!!.pins)
        assertEquals(setOf("playlist:Road trip"), s.get("b")!!.pins)
        assertNull("not in the ledger: the caller queues it", s.get("c"))
        s.applyPins(emptyMap(), mapOf("b" to setOf("not-a-pin")))
        assertEquals("no change, no write", before + 1, s.writeCount)
    }

    @Test
    fun addPinAndPinsById() {
        val s = store()
        s.enqueueAll(listOf(pinned("a", PinSource.FAVORITES), item("b")))
        assertEquals(1, s.addPin(listOf("a", "b", "zzz"), PinSource.MANUAL))
        assertEquals(
            mapOf("a" to setOf(PinSource.FAVORITES, PinSource.MANUAL), "b" to setOf(PinSource.MANUAL)),
            s.pinsById(),
        )
    }

    @Test
    fun aFailedPinnedJobKeepsItsReasonsWhenItIsQueuedAgain() {
        val s = store()
        s.enqueue(pinned("a", PinSource.FAVORITES))
        repeat(DownloadStateStore.MAX_ATTEMPTS) { s.markAttemptFailed("a", "x") }
        assertTrue(s.enqueue(pinned("a", "playlist:Road trip")))
        assertEquals(setOf(PinSource.FAVORITES, "playlist:Road trip"), s.get("a")!!.pins)
        assertEquals(DownloadState.QUEUED, s.get("a")!!.state)
    }
}