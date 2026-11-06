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

/**
 * Offline followed artists, end to end at the store - the same rules #229 gave favourites, for the
 * follow that used to be lost when the network call failed.
 */
class ArtistFollowWriterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var online = true
    private var prefix = "SUBSONIC::"

    private val file by lazy { File(tmp.root, "followed_artists.json") }
    private val s by lazy { ArtistFollowStore(file) }

    private fun artist(id: String) = FollowedArtist(artistId = id, name = "A " + id)

    private fun writer(deliver: suspend (String, Boolean) -> Boolean) =
        ArtistFollowWriter(
            store = s,
            deliver = deliver,
            servicePrefix = { prefix },
            networkAvailable = { online },
        )

    // (a) a follow made offline is kept locally, shows in the followed list, and nothing is sent.
    @Test
    fun aFollowMadeOfflineIsKeptAndVisibleAndNeverSent() = runBlocking {
        online = false
        var sent = 0
        val w = writer { _, _ -> sent++; true }

        assertTrue("accepted", w.setFollowed(artist("SUBSONIC::artist::radiohead"), true))

        assertEquals("nothing was sent while offline", 0, sent)
        assertTrue(s.isFollowing("SUBSONIC::artist::radiohead"))
        assertEquals(listOf("SUBSONIC::artist::radiohead"), s.followedArtists().map { it.artistId })
        assertEquals(listOf("SUBSONIC::artist::radiohead"), s.pending().map { it.artistId })
        assertEquals(listOf("SUBSONIC::artist::radiohead"), s.followedArtistsFlow.value.map { it.artistId })
    }

    // (a, continued) the followed list is readable with no network, from a new store on the same file.
    @Test
    fun theFollowedListIsReadableOfflineFromTheFile() = runBlocking {
        online = false
        writer { _, _ -> true }.setFollowed(artist("SUBSONIC::artist::clairo"), true)

        online = false
        val reopened = ArtistFollowStore(file)

        assertEquals(listOf("SUBSONIC::artist::clairo"), reopened.followedArtists().map { it.artistId })
        assertEquals("A SUBSONIC::artist::clairo", reopened.followedArtists().single().name)
        assertTrue("still undelivered", reopened.pending().single().artistId == "SUBSONIC::artist::clairo")
    }

    // (b) a follow queued offline is delivered by the next flush once the network is back, and stops
    // being pending.
    @Test
    fun flushDeliversTheOfflineFollowAndClearsIt() = runBlocking {
        online = false
        val sent = ArrayList<Pair<String, Boolean>>()
        val w = writer { id, followed -> sent.add(id to followed); true }
        w.setFollowed(artist("SUBSONIC::artist::beach-house"), true)

        online = true
        assertEquals(1, w.flush())

        assertEquals(listOf("SUBSONIC::artist::beach-house" to true), sent)
        assertTrue("no pending entry left", s.pending().isEmpty())
        assertTrue("still followed locally", s.isFollowing("SUBSONIC::artist::beach-house"))
    }

    // (c) follow then unfollow while offline leaves ONE final state to send: the unfollow.
    @Test
    fun followThenUnfollowOfflineSendsOneFinalState() = runBlocking {
        online = false
        val sent = ArrayList<Pair<String, Boolean>>()
        val w = writer { id, followed -> sent.add(id to followed); true }
        w.setFollowed(artist("SUBSONIC::artist::wilco"), true)
        w.setFollowed(artist("SUBSONIC::artist::wilco"), false)

        assertEquals("one entry per artist", 1, s.pending().size)
        assertFalse("not in the followed list", s.isFollowing("SUBSONIC::artist::wilco"))

        online = true
        assertEquals(1, w.flush())

        assertEquals(listOf("SUBSONIC::artist::wilco" to false), sent)
        assertTrue(s.pending().isEmpty())
        assertFalse(s.isFollowing("SUBSONIC::artist::wilco"))
    }

    // (c, continued) and the other way round is one follow.
    @Test
    fun unfollowThenFollowOfflineSendsOneFollow() = runBlocking {
        online = true
        s.setFollowed(artist("SUBSONIC::artist::wilco"), true)
        s.acknowledge("SUBSONIC::artist::wilco") // the server has it (an online follow, as #229's own test does)

        online = false
        val sent = ArrayList<Pair<String, Boolean>>()
        val w = writer { id, followed -> sent.add(id to followed); true }
        w.setFollowed(artist("SUBSONIC::artist::wilco"), false)
        w.setFollowed(artist("SUBSONIC::artist::wilco"), true)

        assertEquals(1, s.pending().size)
        online = true
        w.flush()

        assertEquals(listOf("SUBSONIC::artist::wilco" to true), sent)
        assertTrue(s.isFollowing("SUBSONIC::artist::wilco"))
    }

    // (d) an online follow is delivered at once and leaves no pending entry.
    @Test
    fun aFollowDeliveredOnlineIsNotPending() = runBlocking {
        val sent = ArrayList<Pair<String, Boolean>>()
        val w = writer { id, followed -> sent.add(id to followed); true }

        assertTrue(w.setFollowed(artist("SUBSONIC::artist::radiohead"), true))

        assertEquals(listOf("SUBSONIC::artist::radiohead" to true), sent)
        assertTrue("no pending entry", s.pending().isEmpty())
        assertTrue(s.isFollowing("SUBSONIC::artist::radiohead"))
    }

    // The network call failing leaves the wish pending (that is the bug #229 fixed for stars).
    @Test
    fun aFailedDeliveryKeepsTheFollowPending() = runBlocking {
        val w = writer { _, _ -> throw java.io.IOException("timeout") }

        assertTrue("still followed locally", w.setFollowed(artist("SUBSONIC::artist::slowdive"), true))

        assertTrue(s.isFollowing("SUBSONIC::artist::slowdive"))
        assertEquals("timeout", s.pending().single().error)
    }

    // 5xx: the call answers but the server refused it.
    @Test
    fun aRefusedDeliveryKeepsTheFollowPending() = runBlocking {
        val w = writer { _, _ -> false }

        w.setFollowed(artist("SUBSONIC::artist::slowdive"), true)

        assertTrue(s.isFollowing("SUBSONIC::artist::slowdive"))
        assertEquals(1, s.pending().size)
    }

    // A wish for another service waits for that service: the active client cannot star it.
    @Test
    fun aWishForAnotherServiceWaits() = runBlocking {
        var sent = 0
        val w = writer { _, _ -> sent++; true }

        w.setFollowed(artist("JELLYFIN::artist::someone"), true)

        assertEquals("nothing sent to the active (Subsonic) client", 0, sent)
        assertEquals(1, s.pending().size)
        w.setFollowed(artist("JELLYFIN::artist::someone"), false)
        assertEquals("the blanket list is not touched by an unrelated wish", 0, sent)
    }

    // The server's followed list merges in without undoing an undelivered wish.
    @Test
    fun adoptingTheServerListKeepsUndeliveredWishes() = runBlocking {
        online = false
        writer { _, _ -> true }.setFollowed(artist("SUBSONIC::artist::new"), true)

        s.adoptServerFollows(listOf("SUBSONIC::artist::old"), prefix) { artist(it) }

        assertEquals(
            setOf("SUBSONIC::artist::new", "SUBSONIC::artist::old"),
            s.followedArtists().map { it.artistId }.toSet(),
        )
        assertEquals("the wish is still to send", "SUBSONIC::artist::new", s.pending().single().artistId)
    }

    // Unfollowing an artist that is not followed is a no-op, not a wish.
    @Test
    fun unfollowingSomethingNotFollowedChangesNothing() = runBlocking {
        val w = writer { _, _ -> true }

        assertFalse(w.setFollowed(artist("SUBSONIC::artist::nobody"), false))

        assertTrue(s.pending().isEmpty())
        assertTrue(s.followedArtists().isEmpty())
    }

    // The worker is another process: the store re-reads the file when it changed under it.
    @Test
    fun theStoreSeesWhatAnotherProcessWrote() = runBlocking {
        online = false
        writer { _, _ -> true }.setFollowed(artist("SUBSONIC::artist::porches"), true)

        // The worker's process delivers it.
        val workerStore = ArtistFollowStore(file)
        val delivered = ArtistFollowWriter(workerStore, { _, _ -> true }, { prefix }, { true }).flush()
        assertEquals(1, delivered)

        assertEquals("the app's store sees the delivery", emptyList<ArtistFollowWish>(), s.pending())
        assertTrue(s.isFollowing("SUBSONIC::artist::porches"))
        assertNull(s.pending().firstOrNull())
    }
}
