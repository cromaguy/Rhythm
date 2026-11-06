// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Lyrics for songs that are already downloaded: rate-limited, resumable, and never touching the audio. */
class LyricsBackfillTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dir get() = File(tmp.root, "streaming_downloads")
    private val gaps = ArrayList<Long>()

    private fun items(n: Int, state: DownloadState = DownloadState.COMPLETE) =
        (1..n).map { DownloadItem(songId = "SUBSONIC::s$it", title = "T$it", artist = "A", album = "Al", state = state) }

    private fun backfill(
        passLimit: Int = 100,
        maxFailures: Int = 4,
        store: LyricsStore = LyricsStore(dir),
    ) = LyricsBackfill(store, passLimit = passLimit, gapMs = 250, maxConsecutiveFailures = maxFailures, pause = { gaps += it })

    private val text = StoredLyrics(plain = "la", synced = "[00:01.00]la", source = "Navidrome (Subsonic)")

    /** A fake server: [answers] by song id, everything else has lyrics. Counts the asks per song. */
    private class FakeServer(val answers: Map<String, LyricsFetch> = emptyMap(), val text: StoredLyrics) {
        val asks = ArrayList<String>()
        val fetch: suspend (DownloadItem) -> LyricsFetch = { item ->
            asks += item.songId
            answers[item.songId] ?: LyricsFetch.Found(text)
        }
    }

    @Test
    fun fillsEveryDownloadedSongAndAsksNothingOnTheNextPass() = runBlocking {
        val store = LyricsStore(dir)
        val server = FakeServer(text = text)
        val all = items(5)

        val first = backfill(store = store).run(all, server.fetch)
        assertEquals(BackfillResult(stored = 5, noLyrics = 0, failed = 0, remaining = 0, end = BackfillEnd.DONE), first)
        assertEquals(5, server.asks.size)

        val second = backfill(store = store).run(all, server.fetch)
        assertEquals(BackfillEnd.DONE, second.end)
        assertEquals("nothing left to ask", 5, server.asks.size)
        assertEquals(text, store.lyrics("SUBSONIC::s3"))
    }

    @Test
    fun onlyFinishedDownloadsAreBackfilled() = runBlocking {
        val server = FakeServer(text = text)
        val mixed = items(2) + items(2, DownloadState.QUEUED).map { it.copy(songId = "SUBSONIC::q" + it.songId) } +
            items(1, DownloadState.FAILED).map { it.copy(songId = "SUBSONIC::f1") }
        backfill().run(mixed, server.fetch)
        assertEquals(listOf("SUBSONIC::s1", "SUBSONIC::s2"), server.asks)
    }

    @Test
    fun aSongWithNoLyricsIsRecordedNotFailedAndNotAskedAgain() = runBlocking {
        val store = LyricsStore(dir)
        val server = FakeServer(mapOf("SUBSONIC::s2" to LyricsFetch.None), text)
        val all = items(3)

        val first = backfill(store = store).run(all, server.fetch)
        assertEquals(2, first.stored)
        assertEquals(1, first.noLyrics)
        assertEquals(0, first.failed)
        assertEquals(BackfillEnd.DONE, first.end)

        backfill(store = store).run(all, server.fetch)
        assertEquals("the sync after that skips it too", 1, server.asks.count { it == "SUBSONIC::s2" })
    }

    @Test
    fun aPassIsRateLimitedAndResumesWhereItStopped() = runBlocking {
        val store = LyricsStore(dir)
        val server = FakeServer(text = text)
        val all = items(8)

        val first = backfill(passLimit = 3, store = store).run(all, server.fetch)
        assertEquals(BackfillEnd.LIMIT, first.end)
        assertEquals(3, first.stored)
        assertEquals(5, first.remaining)
        assertEquals(listOf("SUBSONIC::s1", "SUBSONIC::s2", "SUBSONIC::s3"), server.asks)
        assertEquals("a pause between two asks, none before the first", listOf(250L, 250L), gaps)

        // A new process (new objects, same files) picks up exactly the rest.
        val second = backfill(passLimit = 3, store = LyricsStore(dir)).run(all, server.fetch)
        assertEquals(BackfillEnd.LIMIT, second.end)
        assertEquals(2, second.remaining)
        val third = backfill(passLimit = 3, store = LyricsStore(dir)).run(all, server.fetch)
        assertEquals(BackfillEnd.DONE, third.end)
        assertEquals(0, third.remaining)
        assertEquals("every song asked exactly once", all.map { it.songId }, server.asks)
    }

    @Test
    fun aStopMidPassKeepsWhatWasDoneAndTheNextPassResumes() = runBlocking {
        val store = LyricsStore(dir)
        val server = FakeServer(text = text)
        val all = items(6)
        var allowed = 2

        val first = backfill(store = store).run(all, { item -> server.fetch(item).also { allowed-- } }, shouldContinue = { allowed > 0 })
        assertEquals(BackfillEnd.STOPPED, first.end)
        assertEquals(2, first.stored)
        assertEquals(4, first.remaining)

        val second = backfill(store = store).run(all, server.fetch)
        assertEquals(BackfillEnd.DONE, second.end)
        assertEquals(all.map { it.songId }, server.asks)
    }

    @Test
    fun failedAsksLeaveNoRecordAndAreRetriedByTheNextPassOnly() = runBlocking {
        val store = LyricsStore(dir)
        var failS2 = true
        val asks = ArrayList<String>()
        val fetch: suspend (DownloadItem) -> LyricsFetch = { item ->
            asks += item.songId
            if (item.songId == "SUBSONIC::s2" && failS2) LyricsFetch.Failed("HTTP 503") else LyricsFetch.Found(text)
        }
        val all = items(4)

        val first = backfill(store = store).run(all, fetch)
        assertEquals(BackfillEnd.RETRY_LATER, first.end)
        assertEquals(1, first.failed)
        assertEquals(3, first.stored)
        assertEquals(1, first.remaining)
        assertNull(store.read("SUBSONIC::s2"))
        assertEquals("not asked twice in one pass", 1, asks.count { it == "SUBSONIC::s2" })

        failS2 = false
        val second = backfill(store = store).run(all, fetch)
        assertEquals(BackfillEnd.DONE, second.end)
        assertEquals(listOf("SUBSONIC::s2"), asks.drop(4))
    }

    @Test
    fun aDeadServerEndsThePassAfterAFewAsksInsteadOfTryingEverySong() = runBlocking {
        val store = LyricsStore(dir)
        var asks = 0
        val result = backfill(maxFailures = 3, store = store).run(items(50), { asks++; LyricsFetch.Failed("connection refused") })
        assertEquals(BackfillEnd.RETRY_LATER, result.end)
        assertEquals(3, asks)
        assertEquals(50, result.remaining)
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(LyricsStore.SUFFIX) })
    }

    @Test
    fun anAnswerResetsTheFailureStreak() = runBlocking {
        val server = FakeServer(
            mapOf("SUBSONIC::s1" to LyricsFetch.Failed("x"), "SUBSONIC::s2" to LyricsFetch.Failed("x"), "SUBSONIC::s4" to LyricsFetch.Failed("x"), "SUBSONIC::s5" to LyricsFetch.Failed("x")),
            text,
        )
        val result = backfill(maxFailures = 3).run(items(6), server.fetch)
        assertEquals("never three in a row", BackfillEnd.RETRY_LATER, result.end)
        assertEquals(6, server.asks.size)
        assertEquals(4, result.failed)
    }

    @Test
    fun theBackfillNeverTouchesTheAudioOrTheLedger() = runBlocking {
        dir.mkdirs()
        val audio = File(dir, "SUBSONIC__s1.flac").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        val before = audio.readBytes()
        val ledgerFile = File(dir, "download_state.json")
        val ledger = DownloadStateStore(ledgerFile)
        ledger.enqueue(items(1).single()); ledger.markComplete("SUBSONIC::s1", 64)
        val ledgerBefore = ledger.items.value

        backfill().run(ledger.items.value, FakeServer(text = text).fetch)

        assertTrue(before.contentEquals(audio.readBytes()))
        assertEquals(ledgerBefore, ledger.items.value)
    }

    @Test
    fun aBackfillPassAlsoSweepsLyricsOfSongsThatAreGone() = runBlocking {
        val store = LyricsStore(dir)
        store.putFound("SUBSONIC::removed", text)
        backfill(store = store).run(items(1), FakeServer(text = text).fetch)
        assertNull(store.read("SUBSONIC::removed"))
        assertEquals(text, store.lyrics("SUBSONIC::s1"))
    }

    @Test
    fun staleNoLyricsAnswersAreAskedAgainButFreshOnesAreNot() = runBlocking {
        var now = 5_000_000L
        val store = LyricsStore(dir) { now }
        store.putNone("SUBSONIC::s1")
        val server = FakeServer(text = text)
        val b = LyricsBackfill(store, gapMs = 0, pause = {}, clock = { now })
        val all = items(1)

        b.run(all, server.fetch)
        assertTrue(server.asks.isEmpty())

        now += LyricsStore.NONE_RECHECK_MS
        b.run(all, server.fetch)
        assertEquals(listOf("SUBSONIC::s1"), server.asks)
        assertEquals(text, store.lyrics("SUBSONIC::s1"))
    }
}
