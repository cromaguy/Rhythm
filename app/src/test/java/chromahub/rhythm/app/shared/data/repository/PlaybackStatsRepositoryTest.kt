/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.repository

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class PlaybackStatsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var testHistoryFile: File
    private val testScope = CoroutineScope(Dispatchers.IO + Job())

    private class MockTestContext(private val baseFilesDir: File) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = baseFilesDir
    }

    @Before
    fun setUp() {
        val filesDir = tempFolder.newFolder("files")
        mockContext = MockTestContext(filesDir)
        testHistoryFile = File(filesDir, "playback_history.json")
        PlaybackStatsRepository.resetInstanceForTesting()
    }

    @After
    fun tearDown() {
        PlaybackStatsRepository.resetInstanceForTesting()
    }

    private fun recordTestSong(
        repo: PlaybackStatsRepository,
        id: String,
        title: String = "Test Song",
        artist: String = "Test Artist",
        album: String = "Test Album",
        genre: String? = "Rock",
        durationMs: Long = 60_000L,
        timestamp: Long = System.currentTimeMillis()
    ) {
        repo.recordPlayback(
            songId = id,
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            durationMs = durationMs,
            timestamp = timestamp
        )
    }

    @Test
    fun recordPlayback_updatesInMemoryImmediately() = runBlocking {
        val repo = PlaybackStatsRepository.createForTesting(mockContext, testHistoryFile, testScope)
        recordTestSong(repo, id = "song_1", title = "Song One", durationMs = 60_000L, timestamp = 1_000_000L)
        recordTestSong(repo, id = "song_2", title = "Song Two", durationMs = 120_000L, timestamp = 2_000_000L)

        val recentIds = repo.getRecentlyPlayedSongIds()
        assertEquals(listOf("song_2", "song_1"), recentIds)
    }

    @Test
    fun recordSimplePlayback_updatesInMemoryImmediately() = runBlocking {
        val repo = PlaybackStatsRepository.createForTesting(mockContext, testHistoryFile, testScope)
        repo.recordSimplePlayback(
            songId = "simple_1",
            title = "Simple One",
            artist = "Simple Artist",
            album = "Simple Album",
            genre = "Rock",
            durationMs = 90_000L,
            timestamp = 100_000L
        )

        val recentIds = repo.getRecentlyPlayedSongIds()
        assertEquals(listOf("simple_1"), recentIds)
    }

    @Test
    fun concurrentRecordPlayback_noLockContentionOrDeadlock() = runBlocking {
        val repo = PlaybackStatsRepository.createForTesting(mockContext, testHistoryFile, testScope)
        val numThreads = 10
        val recordsPerThread = 50

        val jobs = (1..numThreads).map { threadIdx ->
            testScope.launch {
                for (i in 1..recordsPerThread) {
                    val songId = "song_${threadIdx}_$i"
                    recordTestSong(
                        repo,
                        id = songId,
                        title = "Title $songId",
                        durationMs = 5_000L + i,
                        timestamp = System.currentTimeMillis() + (threadIdx * 1000) + i
                    )
                }
            }
        }
        jobs.joinAll()

        val recentIds = repo.getRecentlyPlayedSongIds(limit = 1000)
        assertEquals(numThreads * recordsPerThread, recentIds.size)
    }

    @Test
    fun flushPendingWrites_persistsToDiskAndReloads() = runBlocking {
        val repo1 = PlaybackStatsRepository.createForTesting(mockContext, testHistoryFile, testScope)
        recordTestSong(repo1, id = "persist_1", title = "Persistent Song", durationMs = 90_000L, timestamp = 5_000_000L)
        repo1.flushPendingWrites()

        assertTrue("History file should exist on disk after flush", testHistoryFile.exists())
        assertTrue("History file should not be empty", testHistoryFile.length() > 0)

        // Create a fresh repository instance pointing to the same file
        val repo2 = PlaybackStatsRepository.createForTesting(mockContext, testHistoryFile, testScope)
        val reloadedIds = repo2.getRecentlyPlayedSongIds()
        assertEquals(listOf("persist_1"), reloadedIds)
    }

    @Test
    fun clearHistory_clearsMemoryAndDisk() = runBlocking {
        val repo = PlaybackStatsRepository.createForTesting(mockContext, testHistoryFile, testScope)
        recordTestSong(repo, id = "clear_1", title = "To Be Cleared", durationMs = 45_000L)
        repo.flushPendingWrites()
        assertTrue(testHistoryFile.exists())

        repo.clearHistory()
        assertEquals(emptyList<String>(), repo.getRecentlyPlayedSongIds())

        repo.flushPendingWrites()
        assertEquals(emptyList<String>(), repo.getRecentlyPlayedSongIds())
    }

    @Test
    fun oldEventsBeyond90Days_arePruned() = runBlocking {
        val repo = PlaybackStatsRepository.createForTesting(mockContext, testHistoryFile, testScope)
        val now = 100_000_000_000L
        val oldTimestamp = now - TimeUnit.DAYS.toMillis(95) // 95 days ago

        recordTestSong(repo, id = "old_song", title = "Old Song", durationMs = 30_000L, timestamp = oldTimestamp)
        recordTestSong(repo, id = "new_song", title = "New Song", durationMs = 30_000L, timestamp = now)

        val recentIds = repo.getRecentlyPlayedSongIds()
        assertEquals(listOf("new_song"), recentIds)
        assertFalse(recentIds.contains("old_song"))
    }

    @Test
    fun corruptedFileOnDisk_recoversGracefullyWithoutCrash() = runBlocking {
        testHistoryFile.writeText("{ this is invalid json !!! }")

        val repo = PlaybackStatsRepository.createForTesting(mockContext, testHistoryFile, testScope)

        // Should not crash, and should record new playback cleanly
        recordTestSong(repo, id = "valid_song", title = "Valid Song", durationMs = 30_000L, timestamp = 1_000_000L)
        assertEquals(listOf("valid_song"), repo.getRecentlyPlayedSongIds())
    }
}
