// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.runBlocking

/**
 * Offline favorites, end to end at the store: the wish is written first, the server call decides
 * whether it was already delivered, and a failed call leaves it queued instead of losing it.
 */
class FavoriteWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var online = true
    private var prefix = "SUBSONIC::"

    private val s by lazy { FavoriteStore(File(tmp.root, "favorites.json")) }

    private fun song(id: String) = FavoriteSong(songId = id, title = "T " + id, artist = "A", album = "Al")

    private fun writer(deliver: suspend (String, Boolean) -> Boolean) =
        FavoriteWriter(
            store = s,
            deliver = deliver,
            servicePrefix = { prefix },
            networkAvailable = { online },
        )

    private fun ok(): FavoriteWriter = writer { _, _ -> true }

    // (a) like while offline persists locally and is visible in the liked list, with nothing sent.
    @Test
    fun aLikeMadeOfflineIsKeptAndVisibleAndNeverSent() = runBlocking {
        online = false
        var sent = 0
        val w = writer { _, _ -> sent++; true }

        assertTrue("accepted", w.setLiked(song("SUBSONIC::1"), true))

        assertEquals("nothing was sent while offline", 0, sent)
        assertTrue(s.isLiked("SUBSONIC::1"))
        assertEquals(listOf("SUBSONIC::1"), s.likedSongs().map { it.songId })
        assertEquals(listOf("SUBSONIC::1"), s.pending().map { it.songId })
    }

    // (d) a successful online like never leaves a pending entry behind.
    @Test
    fun aLikeDeliveredOnlineIsNotPending() = runBlocking {
        val sent = ArrayList<Pair<String, Boolean>>()
        val w = writer { id, liked -> sent.add(id to liked); true }

        assertTrue(w.setLiked(song("SUBSONIC::1"), true))

        assertEquals(listOf("SUBSONIC::1" to true), sent)
        assertTrue(s.isLiked("SUBSONIC::1"))
        assertTrue("delivered, so nothing to replay", s.pending().isEmpty())
    }

    // A failed call is not a lost favourite: the local state stays and the wish is queued.
    @Test
    fun aLikeThatFailedWhileOnlineStaysQueued() = runBlocking {
        val w = writer { _, _ -> false }

        assertTrue("the star is kept locally whatever the server says", w.setLiked(song("SUBSONIC::1"), true))

        assertTrue(s.isLiked("SUBSONIC::1"))
        assertEquals(1, s.pending().size)
        assertTrue(s.pending().single().liked)
    }

    @Test
    fun anExceptionFromTheClientIsTheSameAsAFailure() = runBlocking {
        online = true
        val w = writer { _, _ -> throw IllegalStateException("Subsonic service is not connected") }

        assertTrue(w.setLiked(song("SUBSONIC::1"), true))

        assertTrue(s.isLiked("SUBSONIC::1"))
        assertEquals(1, s.pending().size)
    }

    @Test
    fun aDuplicateLikeTheServerRefusesIsAlreadyInEffect() = runBlocking {
        // Subsonic answers 500 ("already starred") for a star the server already has: the wish holds there.
        val w = writer { _, _ -> throw CopyOfHttpCodeException(500) }

        assertTrue(w.setLiked(song("SUBSONIC::1"), true))

        val why = "code=" + CopyOfHttpCodeException(500).httpCode() + " pending=" + s.pending().map { it.songId + "/" + it.error }
        assertTrue(s.isLiked("SUBSONIC::1"))
        assertTrue(why, s.pending().isEmpty())
    }

    @Test
    fun aServerErrorThatIsNotADuplicateStaysQueued() = runBlocking {
        val w = writer { _, _ -> throw CopyOfHttpCodeException(503) }

        assertTrue(w.setLiked(song("SUBSONIC::1"), true))

        assertEquals("a real failure is retried later", 1, s.pending().size)
    }

    // (e) unlike of a never-liked song never reaches the network.
    @Test
    fun unlikeOfANeverLikedSongIsANoOp() = runBlocking {
        var sent = 0
        val w = writer { _, _ -> sent++; true }

        assertFalse(w.setLiked(song("SUBSONIC::1"), false))

        assertEquals(0, sent)
        assertTrue(s.pending().isEmpty())
        assertTrue(s.likedSongs().isEmpty())
    }

    // (c) like then unlike while offline is ONE final unstar when the network returns (b).
    @Test
    fun likeThenUnlikeOfflineSendsOnlyTheFinalUnstarWhenBackOnline() = runBlocking {
        online = false
        val sent = ArrayList<Pair<String, Boolean>>()
        val w = writer { id, liked -> sent.add(id to liked); true }

        w.setLiked(song("SUBSONIC::1"), true)
        w.setLiked(song("SUBSONIC::1"), false)
        assertEquals("nothing sent yet", 0, sent.size)
        assertEquals("one collapsed entry", 1, s.pending().size)

        online = true
        assertEquals(1, w.flush())

        assertEquals("no like was ever sent", listOf("SUBSONIC::1" to false), sent)
        assertTrue(s.pending().isEmpty())
        assertFalse(s.isLiked("SUBSONIC::1"))
    }

    // (b) a pending like is delivered once, when the network is back.
    @Test
    fun aPendingLikeIsDeliveredWhenTheNetworkReturns() = runBlocking {
        online = false
        val sent = ArrayList<Pair<String, Boolean>>()
        val w = writer { id, liked -> sent.add(id to liked); true }
        w.setLiked(song("SUBSONIC::1"), true)

        assertEquals("offline: nothing to do", 0, w.flush())

        online = true
        val n = w.flush()
        val diag = "n=" + n + " sent=" + sent + " online=" + online + " prefix=" + prefix +
            " storePending=" + s.pending().map { it.songId + "/" + it.liked } + " liked=" + s.likedSongs().map { it.songId }
        assertEquals(diag, 1, n)
        assertEquals(listOf("SUBSONIC::1" to true), sent)
        assertTrue(s.pending().isEmpty())
        assertTrue("still liked after delivery", s.isLiked("SUBSONIC::1"))
        assertEquals("a second flush has nothing left", 0, w.flush())
    }

    @Test
    fun aDeliveryThatFailsStaysQueuedForTheNextFlush() = runBlocking {
        online = false
        var succeed = false
        val w = writer { _, _ -> succeed }
        w.setLiked(song("SUBSONIC::1"), true)
        online = true

        assertEquals(0, w.flush())
        assertEquals(1, s.pending().size)

        succeed = true
        assertEquals(1, w.flush())
        assertTrue(s.pending().isEmpty())
    }

    @Test
    fun aWishForAServiceThatIsNotTheActiveOneIsKeptForLater() = runBlocking {
        val sent = ArrayList<String>()
        val w = writer { id, _ -> sent.add(id); true }

        assertTrue(w.setLiked(song("JELLYFIN::7"), true))

        assertEquals("not this service's call to make", 0, sent.size)
        assertTrue(s.isLiked("JELLYFIN::7"))
        assertEquals(1, s.pending().size)
    }

    @Test
    fun theServerListIsAdoptedForTheActiveServiceOnly() = runBlocking {
        online = false // the like is not sent: it must survive the server's list
        val w = ok()
        w.setLiked(song("SUBSONIC::1"), true)

        w.adoptServerStars(listOf("SUBSONIC::2")) { id -> FavoriteSong(songId = id, title = "T", artist = "A", album = "Al") }

        assertEquals(setOf("SUBSONIC::1", "SUBSONIC::2"), s.likedSongs().map { it.songId }.toSet())
        assertEquals("the undelivered like awaits its own sync", setOf("SUBSONIC::1"), s.pending().map { it.songId }.toSet())
        assertNull("SUBSONIC::2 came from the server", s.pending().firstOrNull { it.songId == "SUBSONIC::2" })
    }

    @Test
    fun flushSendsOnlyWhatBelongsToTheActiveService() = runBlocking {
        online = false
        val sent = ArrayList<String>()
        val w = writer { id, _ -> sent.add(id); true }
        w.setLiked(song("SUBSONIC::1"), true)
        prefix = "JELLYFIN::"
        w.setLiked(song("JELLYFIN::7"), true)

        online = true
        assertEquals(1, w.flush())
        assertEquals(listOf("JELLYFIN::7"), sent)

        prefix = "SUBSONIC::"
        val first = w.flush()
        assertEquals("pending=" + s.pending().map { it.songId } + " sent=" + sent, 1, first)
        assertEquals(listOf("JELLYFIN::7", "SUBSONIC::1"), sent)
        assertTrue(s.pending().isEmpty())
    }
}

/** A failed HTTP call as the Subsonic client reports it: the code decides whether it is a real failure. */
private class CopyOfHttpCodeException(val code: Int) : java.io.IOException("HTTP $code")
