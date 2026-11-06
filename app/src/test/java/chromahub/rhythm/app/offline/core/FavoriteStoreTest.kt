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

/**
 * Offline favorites: the local wish of a favourite must be durable before (and independent of) the
 * server call, so a star made on the subway is neither lost nor forgotten when the connection returns.
 */
class FavoriteStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "streaming_downloads/favorites.json")
    private fun store(file: File = file()) = FavoriteStore(file)

    private fun song(id: String, title: String = "T $id") =
        FavoriteSong(songId = id, title = title, artist = "A", album = "Al", durationMs = 1_000L)

    // (a) like while offline persists locally and is visible in the liked list.
    @Test
    fun likeIsWrittenBeforeAnythingIsSent() {
        val s = store()
        s.setLiked(song("SUBSONIC::1"), true)

        assertTrue("liked at once", s.isLiked("SUBSONIC::1"))
        assertEquals(listOf("SUBSONIC::1"), s.likedSongs().map { it.songId })
        assertEquals("nothing sent yet", listOf("SUBSONIC::1"), s.pending().map { it.songId })
        assertTrue("pending is a like", s.pending().single().liked)
        assertNotNull(s.pending().single().song)
    }

    @Test
    fun theWishAndTheQueueSurviveARestart() {
        val before = store()
        before.setLiked(song("SUBSONIC::1"), true)
        before.setLiked(song("SUBSONIC::2"), true)
        val reopened = store()
        assertEquals(setOf("SUBSONIC::1", "SUBSONIC::2"), reopened.isLikedIds())
        assertEquals(listOf("SUBSONIC::1", "SUBSONIC::2"), reopened.likedSongs().map { it.songId })
        assertEquals(2, reopened.pending().size)
    }

    // (c) last write wins, per song: like then unlike before a sync is ONE final unstar.
    @Test
    fun likeThenUnlikeCollapsesToASingleUnstar() {
        val s = store()
        s.setLiked(song("SUBSONIC::1"), true)
        s.setLiked(song("SUBSONIC::1"), false)

        assertFalse(s.isLiked("SUBSONIC::1"))
        assertEquals("one entry, not two", 1, s.pending().size)
        assertFalse("and it is the last word", s.pending().single().liked)
        assertEquals(emptyList<String>(), s.likedSongs().map { it.songId })
    }

    @Test
    fun unlikeThenLikeAgainCollapsesToASingleLike() {
        val s = store()
        s.setLiked(song("SUBSONIC::1"), true)
        s.acknowledge("SUBSONIC::1")
        s.setLiked(song("SUBSONIC::1"), false)
        s.setLiked(song("SUBSONIC::1"), true)

        assertEquals(1, s.pending().size)
        assertTrue(s.pending().single().liked)
        assertTrue(s.isLiked("SUBSONIC::1"))
    }

    // (e) unlike of a never-liked song is a no-op.
    @Test
    fun unlikeOfANeverLikedSongChangesNothing() {
        val s = store()
        s.setLiked(song("SUBSONIC::1"), false)

        assertFalse(s.isLiked("SUBSONIC::1"))
        assertTrue("nothing to send", s.pending().isEmpty())
        assertTrue(s.likedSongs().isEmpty())
    }

    // (b) the pending like is delivered when the network returns, and stops being pending.
    @Test
    fun acknowledgingADeliveredLikeClearsItFromTheQueueButKeepsItLiked() {
        val s = store()
        s.setLiked(song("SUBSONIC::1"), true)

        val batch = s.takeBatch()
        assertEquals(listOf("SUBSONIC::1"), batch.map { it.songId })
        s.claim(batch)
        s.acknowledge("SUBSONIC::1")

        assertTrue("no pending left", s.pending().isEmpty())
        assertTrue("still liked", s.isLiked("SUBSONIC::1"))
        assertEquals("the local library keeps it", listOf("SUBSONIC::1"), store().likedSongs().map { it.songId })
    }

    @Test
    fun anUnsentLikeIsStillPendingAfterARestart() {
        store().setLiked(song("SUBSONIC::1"), true)

        val reopened = store()
        assertEquals(1, reopened.pending().size)
        assertTrue(reopened.isLiked("SUBSONIC::1"))
    }

    @Test
    fun aFailedDeliveryStaysPendingAndKeepsItsLocalState() {
        val s = store()
        s.setLiked(song("SUBSONIC::1"), true)
        s.claim(s.pending())

        s.release("SUBSONIC::1", error = "timeout")

        assertEquals(1, s.pending().size)
        assertTrue(s.pending().single().liked)
        assertEquals("timeout", s.pending().single().error)
        assertTrue("still liked locally", s.isLiked("SUBSONIC::1"))
    }

    @Test
    fun aClaimedEntryIsNotHandedOutTwice() {
        val s = store()
        s.setLiked(song("SUBSONIC::1"), true)
        assertTrue(s.claim(s.pending()))
        assertTrue("in flight", s.pending().isEmpty())
        assertTrue("plain takeBatch would duplicate it", s.takeBatch().isEmpty())
    }

    @Test
    fun theSameWishMadeTwiceIsOneEntry() {
        val s = store()
        s.setLiked(song("SUBSONIC::1", "first title"), true)
        s.setLiked(song("SUBSONIC::1", "second title"), true)

        assertEquals(1, s.pending().size)
        assertEquals("the newest song data is kept", "second title", s.pending().single().song?.title)
    }

    // (d) a successful online like does not leave a pending entry.
    @Test
    fun aLikeDeliveredRightAwayLeavesNothingPending() {
        val s = store()
        s.setLiked(song("SUBSONIC::1"), true)
        s.acknowledge("SUBSONIC::1")

        assertTrue(s.pending().isEmpty())
        assertTrue(store().pending().isEmpty())
    }

    // The server's list wins over the local wishes that were delivered; a wish the server has not
    // seen yet is not undone by a list that predates it.
    @Test
    fun adoptServerStarsKeepsUndeclaredWishesAndSettlesDeclaredOnes() {
        val s = store()
        s.setLiked(song("SUBSONIC::1"), true)
        s.setLiked(song("SUBSONIC::2"), true)
        s.acknowledge("SUBSONIC::2")

        s.adoptServerStars(listOf("SUBSONIC::2", "SUBSONIC::3"), "SUBSONIC::") { id -> FavoriteSong(songId = id, title = "T", artist = "A", album = "Al") }

        assertEquals("the server's list plus the undelivered wish", setOf("SUBSONIC::1", "SUBSONIC::2", "SUBSONIC::3"), s.likedSongs().map { it.songId }.toSet())
        assertEquals("and only the undelivered wish is queued", listOf("SUBSONIC::1"), s.pending().map { it.songId })
    }

    @Test
    fun adoptServerStarsLeavesOtherServicesAlone() {
        val s = store()
        s.setLiked(FavoriteSong(songId = "JELLYFIN::9", title = "J", artist = "A", album = "Al"), true)
        s.acknowledge("JELLYFIN::9")

        s.adoptServerStars(emptyList(), "SUBSONIC::")

        assertEquals(listOf("JELLYFIN::9"), s.likedSongs().map { it.songId })
    }

    @Test
    fun rejectingAWishTheServerRefusedDropsItAndUndoesTheLocalState() {
        val s = store()
        s.setLiked(song("SUBSONIC::1"), true)
        s.claim(s.pending())

        s.reject("SUBSONIC::1")

        assertTrue(s.pending().isEmpty())
        assertFalse(s.isLiked("SUBSONIC::1"))
    }

    // The sync worker runs in its own process and writes the same file: the app must not keep serving its old list.
    @Test
    fun aWriteFromAnotherProcessIsPickedUp() {
        val f = file()
        val app = store(f)
        app.setLiked(song("SUBSONIC::1"), true)

        val worker = store(f) // a second process, before the app's next read
        worker.acknowledge("SUBSONIC::1")

        assertTrue("the app sees the worker's delivery", app.pending().isEmpty())
        assertTrue("and keeps the like", app.isLiked("SUBSONIC::1"))
        assertEquals(listOf("SUBSONIC::1"), app.likedSongs().map { it.songId })
    }

    @Test
    fun aCorruptFileStartsEmptyRatherThanCrashing() {
        val f = file().apply { parentFile.mkdirs(); writeText("{not json") }
        val s = store(f)

        assertTrue(s.likedSongs().isEmpty())
        assertTrue(s.pending().isEmpty())
        assertNotNull(store(f))
        assertNull(s.pending().firstOrNull())
    }
}
