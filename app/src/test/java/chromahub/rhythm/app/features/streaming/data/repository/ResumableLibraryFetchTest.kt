/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import chromahub.rhythm.app.features.streaming.data.provider.ProviderSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Collections

class ResumableLibraryFetchTest {

    private lateinit var dir: File
    private lateinit var checkpointDir: File
    private val writer = CatalogCacheWriter()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("library-fetch").toFile()
        checkpointDir = File(dir, "streaming_catalog_subsonic.partial")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun fetcher(clock: () -> Long = System::currentTimeMillis) =
        ResumableLibraryFetch(checkpointDir, writer, clock = clock)

    private fun pageFiles() = checkpointDir.listFiles { f -> f.name.startsWith("page-") }.orEmpty().sortedBy { it.name }

    /**
     * Pretends to be the server's album list: [pages] pages of [PAGE_SIZE] albums with one song
     * each, fetched page by page like SubsonicApiClient.fetchLibrarySongs. Records the pages it
     * fetched; after [stallAfterPages] pages it hangs until cancelled (the app being killed).
     */
    private open inner class FakeServer(
        private val stallAfterPages: Int = Int.MAX_VALUE,
        private val pages: Int = PAGES
    ) : ResumableLibraryFetch.PagedFetch {
        val fetchedPages: MutableList<Int> = Collections.synchronizedList(mutableListOf())
        val pagesCheckpointed = CompletableDeferred<Unit>()
        val startOffsets = mutableListOf<Int>()

        open suspend fun afterPage(page: Int) = Unit

        override suspend fun fetch(
            startAlbumOffset: Int,
            limit: Int,
            onProgress: ((current: Int, total: Int, songsCount: Int) -> Unit)?,
            onPage: suspend (pageSongs: List<ProviderSong>, nextAlbumOffset: Int) -> Unit
        ): Result<List<ProviderSong>> {
            startOffsets.add(startAlbumOffset)
            val songs = mutableListOf<ProviderSong>()
            var offset = startAlbumOffset
            var pagesThisRun = 0
            while (offset < pages * PAGE_SIZE && songs.size < limit) {
                if (pagesThisRun == stallAfterPages) {
                    pagesCheckpointed.complete(Unit)
                    awaitCancellation()
                }
                val page = (offset until offset + PAGE_SIZE).map { song(it) }
                fetchedPages.add(offset / PAGE_SIZE)
                songs += page
                offset += PAGE_SIZE
                pagesThisRun++
                onPage(page, offset)
                afterPage(offset / PAGE_SIZE - 1)
            }
            return Result.success(songs.take(limit))
        }
    }

    private suspend fun killAfter(pages: Int, lastModified: Long = LAST_MODIFIED) = kotlinx.coroutines.coroutineScope {
        val first = FakeServer(stallAfterPages = pages)
        val job = launch(Dispatchers.IO) { fetcher().fetch(lastModified, LIMIT, null, first) }
        first.pagesCheckpointed.await()
        job.cancelAndJoin()
        first
    }

    @Test
    fun killedFetchResumesFromPageFilesWithoutRefetchingThem() = runBlocking {
        val first = killAfter(pages = 2)
        assertEquals(listOf(0, 1), first.fetchedPages)
        assertEquals(2, pageFiles().size)

        // Restart: a new instance reads the two page files and continues at page 2.
        val second = FakeServer()
        val songs = fetcher().fetch(LAST_MODIFIED, LIMIT, null, second).getOrThrow()

        assertEquals(listOf(2 * PAGE_SIZE), second.startOffsets)
        assertEquals(listOf(2, 3, 4), second.fetchedPages)
        assertEquals((0 until PAGES * PAGE_SIZE).map { song(it) }, songs)
        assertFalse("checkpoint must be deleted after a complete fetch", checkpointDir.exists())
    }

    @Test
    fun bytesWrittenPerPageDoNotGrowWithTheLibrary() = runBlocking {
        val perPageWrites = mutableListOf<Long>()
        val snapshots = mutableListOf<Map<String, Long>>()
        val server = object : FakeServer(stallAfterPages = 40, pages = 41) {
            override suspend fun afterPage(page: Int) {
                val files = checkpointDir.listFiles().orEmpty().associate { it.name to it.length() }
                // What this page wrote: its own page file plus the small index.
                perPageWrites += files.getValue("page-%08d.json".format(page * PAGE_SIZE)) + files.getValue("index.json")
                snapshots += files
            }
        }
        val job = launch(Dispatchers.IO) { fetcher().fetch(LAST_MODIFIED, LIMIT, null, server) }
        server.pagesCheckpointed.await()
        job.cancelAndJoin()

        assertEquals(40, perPageWrites.size)
        // Constant per page (a single growing checkpoint would make page 40 ~40x page 1).
        assertTrue("per-page writes grew: $perPageWrites", perPageWrites.last() < perPageWrites.first() * 3 / 2)
        // Earlier pages are never rewritten: their sizes stay as first written.
        val first = snapshots.first().getValue("page-00000000.json")
        assertTrue(snapshots.all { it.getValue("page-00000000.json") == first })
        // Total on disk is linear in the number of pages.
        val total = snapshots.last().values.sum()
        assertTrue(total < 40L * perPageWrites.first() * 3 / 2)
    }

    @Test
    fun changedServerLibraryDiscardsCheckpointAndFetchesEverything() = runBlocking {
        killAfter(pages = 2)

        val second = FakeServer()
        val songs = fetcher().fetch(LAST_MODIFIED + 1, LIMIT, null, second).getOrThrow()

        assertEquals(listOf(0), second.startOffsets)
        assertEquals(listOf(0, 1, 2, 3, 4), second.fetchedPages)
        assertEquals(PAGES * PAGE_SIZE, songs.size)
    }

    @Test
    fun pageWrittenWithoutItsIndexIsFetchedAgain() = runBlocking {
        killAfter(pages = 2)
        // A kill between writing page 2 and its index leaves a page the index does not cover.
        File(checkpointDir, "page-%08d.json".format(2 * PAGE_SIZE)).writeText("""[{"providerId":"stale"}]""")

        val second = FakeServer()
        val songs = fetcher().fetch(LAST_MODIFIED, LIMIT, null, second).getOrThrow()

        assertEquals(listOf(2 * PAGE_SIZE), second.startOffsets)
        assertTrue(songs.none { it.providerId == "stale" })
        assertEquals(PAGES * PAGE_SIZE, songs.size)
    }

    @Test
    fun corruptPageDiscardsTheCheckpoint() = runBlocking {
        killAfter(pages = 2)
        File(checkpointDir, "page-%08d.json".format(PAGE_SIZE)).writeText("not json")

        val second = FakeServer()
        val songs = fetcher().fetch(LAST_MODIFIED, LIMIT, null, second).getOrThrow()

        assertEquals(listOf(0), second.startOffsets)
        assertEquals(PAGES * PAGE_SIZE, songs.size)
    }

    @Test
    fun failedFetchKeepsCheckpointForTheNextSync() = runBlocking {
        val failing = ResumableLibraryFetch.PagedFetch { start, _, _, onPage ->
            onPage((start until start + PAGE_SIZE).map { song(it) }, start + PAGE_SIZE)
            Result.failure(java.io.IOException("connection lost"))
        }

        val result = fetcher().fetch(LAST_MODIFIED, LIMIT, null, failing)

        assertTrue(result.isFailure)
        assertEquals(1, pageFiles().size)
        assertTrue(File(checkpointDir, "index.json").exists())
    }

    @Test
    fun unknownLastModifiedNeitherCheckpointsNorResumes() = runBlocking {
        val songs = fetcher().fetch(null, LIMIT, null, FakeServer()).getOrThrow()

        assertEquals(PAGES * PAGE_SIZE, songs.size)
        assertFalse(checkpointDir.exists())
    }

    @Test
    fun resumedSongsCountTowardsTheLimitAndProgress() = runBlocking {
        killAfter(pages = 1)

        var lastReportedSongs = 0
        val second = ResumableLibraryFetch.PagedFetch { start, limit, onProgress, _ ->
            assertEquals(150 - PAGE_SIZE, limit)
            val page = (start until start + limit).map { song(it) }
            onProgress?.invoke(1, 1, page.size)
            Result.success(page)
        }
        val songs = fetcher().fetch(LAST_MODIFIED, 150, { _, _, count -> lastReportedSongs = count }, second).getOrThrow()

        assertEquals(150, songs.size)
        assertEquals(150, lastReportedSongs)
    }

    @Test
    fun progressIsThrottled() = runBlocking {
        var now = 0L
        var reports = 0
        val server = ResumableLibraryFetch.PagedFetch { _, _, onProgress, _ ->
            // 100 albums in 1 s: one progress call every 10 ms.
            repeat(100) { onProgress?.invoke(it + 1, 100, it + 1); now += 10 }
            Result.success(emptyList())
        }

        fetcher(clock = { now }).fetch(LAST_MODIFIED, LIMIT, { _, _, _ -> reports++ }, server)

        // At most one report per 500 ms: at 0 and 500 ms.
        assertEquals(2, reports)
    }

    private companion object {
        const val PAGES = 5
        const val PAGE_SIZE = 100
        const val LIMIT = 50_000
        const val LAST_MODIFIED = 1_700_000_000_000L

        fun song(index: Int) = ProviderSong(
            providerId = "song-$index",
            title = "Song $index",
            artist = "Artist",
            album = "Album $index",
            durationMs = 180_000L,
            albumId = "album-$index"
        )
    }
}
