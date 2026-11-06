// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Lyrics beside the downloaded audio: stored by song id, "no lyrics" remembered, removed with the download. */
class LyricsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000_000L
    private fun dir() = File(tmp.root, "streaming_downloads")
    private fun store() = LyricsStore(dir()) { now }
    private fun ledger() = DownloadStateStore(File(dir(), "download_state.json"))

    private val synced = StoredLyrics(plain = "Hello\nWorld", synced = "[00:01.00]Hello\n[00:02.50]World", source = "Navidrome (Subsonic)")

    private fun song(id: String, vararg pins: String) =
        DownloadItem(songId = id, title = "T $id", artist = "A", album = "Al", payload = "{}", pins = pins.toSet())

    @Test
    fun storedLyricsComeBackByteForByteAndLiveBesideTheAudio() {
        val s = store()
        assertTrue(s.putFound("SUBSONIC::abc", synced))
        assertEquals(synced, s.lyrics("SUBSONIC::abc"))
        assertEquals(File(dir(), "SUBSONIC__abc.lyrics.json"), s.fileFor("SUBSONIC::abc"))
        assertTrue(s.fileFor("SUBSONIC::abc").isFile)
        // The same name rule as the audio, so the two are found together.
        assertEquals("SUBSONIC__abc", OfflineFiles.safeName("SUBSONIC::abc"))
    }

    @Test
    fun aNewStoreInstanceReadsWhatAnEarlierOneWrote() {
        store().putFound("SUBSONIC::abc", synced)
        assertEquals(synced, store().lyrics("SUBSONIC::abc"))
    }

    @Test
    fun plainOnlyAndUnicodeLyricsSurvive() {
        val plain = StoredLyrics(plain = "Zażółć gęślą jaźń \"quoted\" \\ back\nslash", synced = null)
        store().putFound("SUBSONIC::p", plain)
        assertEquals(plain, store().lyrics("SUBSONIC::p"))
    }

    @Test
    fun emptyLyricsAreNeverStored() {
        val s = store()
        assertFalse(s.putFound("SUBSONIC::e", StoredLyrics(plain = "  ", synced = "")))
        assertNull(s.read("SUBSONIC::e"))
    }

    @Test
    fun noLyricsIsARecordSoItIsNotAskedAgainEverySync() {
        val s = store()
        assertTrue(s.needsFetch("SUBSONIC::x"))
        s.putNone("SUBSONIC::x")
        assertTrue(s.read("SUBSONIC::x") is LyricsRecord.None)
        assertNull(s.lyrics("SUBSONIC::x"))
        assertFalse("a sync an hour later does not ask", s.needsFetch("SUBSONIC::x", now + 60 * 60_000L))
        assertFalse(s.needsFetch("SUBSONIC::x", now + LyricsStore.NONE_RECHECK_MS - 1))
        assertTrue("a month later the server may have gained some", s.needsFetch("SUBSONIC::x", now + LyricsStore.NONE_RECHECK_MS))
    }

    @Test
    fun foundLyricsAreNeverAskedAgain() {
        val s = store()
        s.putFound("SUBSONIC::f", synced)
        assertFalse(s.needsFetch("SUBSONIC::f", now + 10 * LyricsStore.NONE_RECHECK_MS))
    }

    @Test
    fun aCorruptFileIsMissingNotACrash() {
        val s = store()
        s.putFound("SUBSONIC::c", synced)
        s.fileFor("SUBSONIC::c").writeText("{ not json")
        assertNull(s.read("SUBSONIC::c"))
        assertTrue(s.needsFetch("SUBSONIC::c"))
    }

    @Test
    fun deleteRemovesTheRecordAndReportsIt() {
        val s = store()
        s.putFound("SUBSONIC::d", synced)
        assertTrue(s.delete("SUBSONIC::d"))
        assertNull(s.read("SUBSONIC::d"))
        assertFalse(s.delete("SUBSONIC::d"))
    }

    // ---- the fetch step: a song without lyrics is not a failure ----

    @Test
    fun ensureStoresWhatTheServerHasAndAsksOnlyOnce() = runBlocking {
        val s = store()
        var asks = 0
        val fetch: suspend () -> LyricsFetch = { asks++; LyricsFetch.Found(synced) }
        assertEquals(LyricsOutcome.STORED, LyricsSync.ensure(s, "SUBSONIC::a", fetch))
        assertEquals(LyricsOutcome.HAD_IT, LyricsSync.ensure(s, "SUBSONIC::a", fetch))
        assertEquals(1, asks)
    }

    @Test
    fun ensureRecordsNoLyricsAndDoesNotCountItAsAFailure() = runBlocking {
        val s = store()
        var asks = 0
        val fetch: suspend () -> LyricsFetch = { asks++; LyricsFetch.None }
        assertEquals(LyricsOutcome.NO_LYRICS, LyricsSync.ensure(s, "SUBSONIC::n", fetch))
        assertEquals(LyricsOutcome.HAD_IT, LyricsSync.ensure(s, "SUBSONIC::n", fetch))
        assertEquals("not retried at the next sync", 1, asks)
    }

    @Test
    fun ensureLeavesNoRecordWhenTheAskFailsSoItIsTriedAgain() = runBlocking {
        val s = store()
        assertEquals(LyricsOutcome.FAILED, LyricsSync.ensure(s, "SUBSONIC::z") { LyricsFetch.Failed("timeout") })
        assertNull(s.read("SUBSONIC::z"))
        assertEquals(LyricsOutcome.FAILED, LyricsSync.ensure(s, "SUBSONIC::z") { throw java.io.IOException("reset") })
        assertTrue(s.needsFetch("SUBSONIC::z"))
        assertEquals(LyricsOutcome.STORED, LyricsSync.ensure(s, "SUBSONIC::z") { LyricsFetch.Found(synced) })
    }

    // ---- removal follows the download, and pins decide whether the download is removed ----

    /** What `OfflineWiring.onRemoved` does for each song the keep-downloaded sync releases. */
    private fun release(ledger: DownloadStateStore, lyrics: LyricsStore, ids: List<String>) {
        for (id in ids) {
            ledger.remove(id)
            lyrics.delete(id)
        }
    }

    private fun sync(l: DownloadStateStore, sources: Map<String, SourceState>): PinApplied {
        val before = l.pinsById()
        return PinApply.apply(l, before, PinReconciler.plan(before, sources)) { song(it) }
    }

    @Test
    fun lyricsStayWhileAnyPinRemainsAndGoWhenTheLastPinIsGone() {
        val fav = PinSource.FAVORITES
        val mix = PinSource.playlist("Daily mix")
        val l = ledger()
        val lyrics = store()
        l.enqueue(song("SUBSONIC::both", fav, mix)); l.markComplete("SUBSONIC::both", 1_000)
        l.enqueue(song("SUBSONIC::mine", mix, PinSource.MANUAL)); l.markComplete("SUBSONIC::mine", 1_000)
        listOf("SUBSONIC::both", "SUBSONIC::mine").forEach { lyrics.putFound(it, synced) }

        // Un-starred: the playlist still holds "both"; the playlist dropped "mine" but the user asked for it.
        var applied = sync(l, mapOf(fav to SourceState.Complete(setOf("SUBSONIC::other")), mix to SourceState.Complete(setOf("SUBSONIC::both"))))
        release(l, lyrics, applied.release)
        assertTrue(applied.release.isEmpty())
        assertEquals(synced, lyrics.lyrics("SUBSONIC::both"))
        assertEquals(synced, lyrics.lyrics("SUBSONIC::mine"))
        assertEquals(setOf(mix), l.get("SUBSONIC::both")!!.pins)
        assertEquals(setOf(PinSource.MANUAL), l.get("SUBSONIC::mine")!!.pins)

        // The playlist is emptied too: now "both" has no reason left. "mine" is still the user's.
        applied = sync(l, mapOf(fav to SourceState.Complete(setOf("SUBSONIC::other")), mix to SourceState.Complete(setOf("SUBSONIC::other"))))
        release(l, lyrics, applied.release)
        assertEquals(listOf("SUBSONIC::both"), applied.release)
        assertNull(lyrics.read("SUBSONIC::both"))
        assertEquals(synced, lyrics.lyrics("SUBSONIC::mine"))
    }

    @Test
    fun aSweepRemovesOnlyTheLyricsOfSongsTheLedgerNoLongerHolds() {
        val l = ledger()
        val lyrics = store()
        l.enqueue(song("SUBSONIC::kept", PinSource.FAVORITES)); l.markComplete("SUBSONIC::kept", 1)
        l.enqueue(song("SUBSONIC::queued")) // a queued song with lyrics already (a re-download) keeps them
        lyrics.putFound("SUBSONIC::kept", synced)
        lyrics.putFound("SUBSONIC::queued", synced)
        lyrics.putFound("SUBSONIC::gone", synced)
        lyrics.putNone("SUBSONIC::gone2")
        File(dir(), "SUBSONIC__half.lyrics.json.tmp").writeText("{")

        val swept = lyrics.sweepOrphans(l.items.value.map { it.songId })

        assertEquals(2, swept)
        assertNull(lyrics.read("SUBSONIC::gone"))
        assertNull(lyrics.read("SUBSONIC::gone2"))
        assertEquals(synced, lyrics.lyrics("SUBSONIC::kept"))
        assertEquals(synced, lyrics.lyrics("SUBSONIC::queued"))
        assertFalse(File(dir(), "SUBSONIC__half.lyrics.json.tmp").exists())
        assertTrue("the ledger file is not a lyrics file", File(dir(), "download_state.json").exists())
    }
}
