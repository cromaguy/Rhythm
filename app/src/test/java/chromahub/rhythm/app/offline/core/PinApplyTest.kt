// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Keep-downloaded sync end to end on a real ledger file: the server's answer in, the ledger and the release list out. */
class PinApplyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val fav = PinSource.FAVORITES
    private val daily = PinSource.playlist("Daily mix")
    private val road = PinSource.playlist("Road trip")

    private fun file() = File(tmp.root, "downloads/download_state.json")
    private fun store() = DownloadStateStore(file())

    private fun song(id: String, vararg pins: String) =
        DownloadItem(songId = id, title = "T $id", artist = "A", album = "Al", payload = "{}", pins = pins.toSet())

    private fun downloaded(s: DownloadStateStore, id: String, vararg pins: String) {
        s.enqueue(song(id, *pins))
        s.markComplete(id, 1_000)
    }

    /**
     * One sync: what the server says goes in, the ledger changes, and the ids to delete come out. It passes the same
     * `stillWorking` guard as `PinSync`: a song still downloading itself, or with automatic tries left, is never
     * released (releasing cancels the transfer and deletes the file).
     */
    private fun sync(s: DownloadStateStore, sources: Map<String, SourceState>): PinApplied {
        val before = s.pinsById()
        val working = s.items.value
            .filter { it.state == DownloadState.QUEUED && it.attempts < DownloadStateStore.MAX_ATTEMPTS }
            .mapTo(HashSet()) { it.songId }
        val plan = PinReconciler.plan(before, sources, stillWorking = working)
        return PinApply.apply(s, before, plan) { id -> song(id) }
    }

    @Test
    fun aShuffledPlaylistWritesNothingAndQueuesNothing() {
        val s = store()
        val ids = (1..30).map { "s$it" }
        ids.forEach { downloaded(s, it, daily) }
        val writes = s.writeCount
        val shuffled = ids.shuffled(java.util.Random(7))
        val applied = sync(s, mapOf(daily to SourceState.of(Result.success(shuffled), declaredCount = 30)))
        assertEquals(0, applied.queued)
        assertTrue(applied.release.isEmpty())
        assertEquals("the ledger is not even rewritten", writes, s.writeCount)
        assertTrue(s.items.value.all { it.state == DownloadState.COMPLETE })
    }

    @Test
    fun aRotationQueuesTheNewSongsAndReleasesOnlyTheOnesNothingElseKeeps() {
        val s = store()
        downloaded(s, "stays", daily)
        downloaded(s, "leaves", daily)
        downloaded(s, "leavesButStarred", daily, fav)
        downloaded(s, "leavesButMine", daily, PinSource.MANUAL)
        val applied = sync(s, mapOf(daily to SourceState.Complete(setOf("stays", "new1", "new2")), fav to SourceState.Complete(setOf("leavesButStarred"))))
        assertEquals(2, applied.queued)
        assertEquals(listOf("leaves"), applied.release)
        assertEquals(setOf(daily), s.get("new1")!!.pins)
        assertEquals(DownloadState.QUEUED, s.get("new1")!!.state)
        assertEquals(setOf(fav), s.get("leavesButStarred")!!.pins)
        assertEquals(setOf(PinSource.MANUAL), s.get("leavesButMine")!!.pins)
        assertEquals(DownloadState.COMPLETE, s.get("stays")!!.state)
    }

    @Test
    fun aFailedOrEmptyReadLeavesTheLedgerAsItWas() {
        val s = store()
        downloaded(s, "a", fav)
        downloaded(s, "b", daily)
        val before = s.items.value
        val applied = sync(
            s,
            mapOf(
                fav to SourceState.of(Result.failure(java.io.IOException("down"))),
                daily to SourceState.of(Result.success(emptyList()), declaredCount = 0),
            ),
        )
        assertEquals(PinApplied(0, emptyList()), applied)
        assertEquals(before, s.items.value)
    }

    @Test
    fun theNewStarOfADownloadedPlaylistSongOnlyAddsAReason() {
        val s = store()
        downloaded(s, "a", daily)
        val applied = sync(s, mapOf(daily to SourceState.Complete(setOf("a")), fav to SourceState.Complete(setOf("a"))))
        assertEquals(0, applied.queued)
        assertEquals(setOf(daily, fav), s.get("a")!!.pins)
        assertEquals(DownloadState.COMPLETE, s.get("a")!!.state)
    }

    @Test
    fun aDownloadTheUserAsksForAfterThePlanWasMadeIsNotDeleted() {
        val s = store()
        downloaded(s, "a", fav)
        val before = s.pinsById()
        val plan = PinReconciler.plan(before, mapOf(fav to SourceState.Complete(setOf("x"))))
        assertEquals(setOf("a"), plan.release)
        s.addPin(listOf("a"), PinSource.MANUAL) // the user taps Download on it meanwhile
        val applied = PinApply.apply(s, before, plan) { song(it) }
        assertTrue(applied.release.isEmpty())
        assertEquals(setOf(PinSource.MANUAL), s.get("a")!!.pins)
    }

    @Test
    fun switchingFavoritesOffReleasesTheFavoritesOnly() {
        val s = store()
        downloaded(s, "onlyFav", fav)
        downloaded(s, "favAndPlaylist", fav, road)
        downloaded(s, "favAndMine", fav, PinSource.MANUAL)
        val applied = sync(s, mapOf(road to SourceState.Complete(setOf("favAndPlaylist"))))
        assertEquals(listOf("onlyFav"), applied.release)
        assertEquals(setOf(road), s.get("favAndPlaylist")!!.pins)
        assertEquals(setOf(PinSource.MANUAL), s.get("favAndMine")!!.pins)
    }

    // Fork, 2026-10-26: a sync used to queue every kept song whose download had FAILED, so a library with a few songs
    // the server cannot serve gave every run something to do (the device log: the same plan again, every few seconds).
    // The retry is the queue's own business; the sync only keeps the reasons right.
    @Test
    fun aKeptSongWhoseDownloadFailedIsLeftToTheQueueAndAReleasedOneIsReleased() {
        val s = store()
        s.enqueue(song("kept", fav))
        repeat(DownloadStateStore.MAX_ATTEMPTS) { s.markAttemptFailed("kept", "boom") }
        s.enqueue(song("gone", fav))
        repeat(DownloadStateStore.MAX_ATTEMPTS) { s.markAttemptFailed("gone", "boom") }
        // "kept" is queued again (its tries ran out, so it waits as QUEUED, not FAILED); "gone" has lost its last reason
        // and is waiting on the user, so the sync may release it.
        val applied = sync(s, mapOf(fav to SourceState.Complete(setOf("kept"))))
        assertEquals(0, applied.queued)
        // Running out of automatic tries no longer marks FAILED: the row waits for the user as QUEUED (2026-10-27).
        assertEquals(DownloadState.QUEUED, s.get("kept")!!.state)
        assertTrue("it is the row waiting for the user", s.isWaitingForUser(s.get("kept")!!))
        assertEquals("its reasons are right: it is still the favorite the server lists", setOf(fav), s.get("kept")!!.pins)
        assertEquals(listOf("gone"), applied.release)
        // PinApply never deletes (it has no files); PinSync releases each id it returns, which drops the entry.
        applied.release.forEach { s.remove(it) }
        assertEquals("released: it is not kept in the ledger to fail at", null, s.get("gone"))
    }

    @Test
    fun theLedgerKeepsItsReasonsAcrossARestartAndTheSecondSyncIsQuiet() {
        val s = store()
        val first = sync(s, mapOf(fav to SourceState.Complete(setOf("a", "b")), daily to SourceState.Complete(setOf("b", "c"))))
        assertEquals(3, first.queued)
        listOf("a", "b", "c").forEach { s.markComplete(it, 10) }

        val restarted = store()
        assertEquals(setOf(fav), restarted.get("a")!!.pins)
        assertEquals(setOf(fav, daily), restarted.get("b")!!.pins)
        val writes = restarted.writeCount
        val second = sync(restarted, mapOf(fav to SourceState.Complete(setOf("b", "a")), daily to SourceState.Complete(setOf("c", "b"))))
        assertEquals(PinApplied(0, emptyList()), second)
        assertEquals(writes, restarted.writeCount)
        assertNull(restarted.get("zzz"))
    }
}
