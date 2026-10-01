/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class CatalogSyncGuardsTest {

    @Test
    fun singleFlight_concurrentCallersJoinOneRun() = runBlocking {
        val flight = SingleFlight<Int>()
        val runs = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()

        val callers = (1..50).map {
            async {
                flight.run {
                    runs.incrementAndGet()
                    release.await()
                    42
                }
            }
        }
        repeat(10) { yield() }
        release.complete(Unit)

        assertEquals(List(50) { 42 }, callers.awaitAll())
        assertEquals("callers must join, not queue", 1, runs.get())
    }

    @Test
    fun singleFlight_runsAgainAfterCompletion() = runBlocking {
        val flight = SingleFlight<Int>()
        val runs = AtomicInteger(0)

        flight.run { runs.incrementAndGet() }
        flight.run { runs.incrementAndGet() }

        assertEquals(2, runs.get())
    }

    @Test
    fun singleFlight_joinersSeeTheFailureAndTheNextRunStartsFresh() = runBlocking {
        val flight = SingleFlight<Int>()
        val started = CompletableDeferred<Unit>()
        val fail = CompletableDeferred<Unit>()

        val owner = async {
            runCatching {
                flight.run {
                    started.complete(Unit)
                    fail.await()
                    throw IllegalStateException("server down")
                }
            }
        }
        started.await()
        val joiner = async { runCatching { flight.run { 1 } } }
        repeat(10) { yield() }
        fail.complete(Unit)

        assertTrue(owner.await().exceptionOrNull() is IllegalStateException)
        assertTrue(joiner.await().exceptionOrNull() is IllegalStateException)
        assertEquals(7, flight.run { 7 })
    }

    @Test
    fun singleFlight_cancelledOwnerDoesNotLeaveJoinersHanging() = runBlocking {
        val flight = SingleFlight<Int>()
        val started = CompletableDeferred<Unit>()

        val owner = async { flight.run { started.complete(Unit); CompletableDeferred<Int>().await() } }
        started.await()
        val joiner = async { runCatching { flight.run { 1 } } }
        repeat(10) { yield() }
        owner.cancel()

        assertTrue(joiner.await().exceptionOrNull() is CancellationException)
        assertEquals(3, flight.run { 3 })
    }

    @Test
    fun onceGate_opensOnlyOnce() {
        val gate = OnceGate()

        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())
        assertFalse(gate.tryEnter())
    }

    @Test
    fun saveFilter_skipsIdenticalContentAndIgnoresTimestamp() {
        val filter = CatalogSaveFilter()
        val cache = catalog(songCount = 3, lastSyncTimestamp = 1L)

        assertTrue("nothing saved yet", filter.hasChanged(cache))
        filter.remember(cache)

        // Same content rebuilt from fresh lists (as each save does), newer timestamp.
        assertFalse(filter.hasChanged(catalog(songCount = 3, lastSyncTimestamp = 2L)))
    }

    @Test
    fun saveFilter_detectsRealChanges() {
        val filter = CatalogSaveFilter()
        val cache = catalog(songCount = 3)
        filter.remember(cache)

        assertTrue(filter.hasChanged(catalog(songCount = 4)))
        assertTrue(filter.hasChanged(cache.copy(likedSongIds = listOf("subsonic::song-1"))))
        assertTrue(filter.hasChanged(cache.copy(libraryMarker = "account:2")))
        assertTrue(filter.hasChanged(cache.copy(playlists = listOf(playlist("p2")))))

        filter.remember(null)
        assertTrue("forgotten after logout", filter.hasChanged(cache))
    }

    private fun catalog(songCount: Int, lastSyncTimestamp: Long = 0L) = StreamingCatalogCache(
        serviceId = "subsonic",
        songs = (1..songCount).map { song(it) },
        playlists = listOf(playlist("p1")),
        likedSongIds = emptyList(),
        lastSyncTimestamp = lastSyncTimestamp,
        libraryMarker = "account:1"
    )

    private fun song(index: Int) = StreamingSong(
        id = "subsonic::song-$index",
        title = "Song $index",
        artist = "Artist",
        album = "Album",
        duration = 180_000L,
        artworkUri = null,
        sourceType = SourceType.SUBSONIC,
        streamingUrl = "http://server/stream/$index",
        previewUrl = null
    )

    private fun playlist(id: String) = StreamingPlaylist(
        id = "subsonic::$id",
        name = id,
        description = null,
        artworkUri = null,
        songCount = 0,
        isEditable = true,
        sourceType = SourceType.SUBSONIC
    )
}
