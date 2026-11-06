// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

/**
 * Offline followed artists, the bridge between a tap on "follow" and the server - the same shape as
 * [FavoriteWriter], because it is the same bug: the follow used to be lost when the network call
 * failed.
 *
 * Every follow/unfollow is written to the [ArtistFollowStore] **before** anything is sent, so it is
 * never lost and shows at once. Then, when [deliver] answers, the wish goes out and stops being
 * pending; when it does not, the wish stays queued and [flush] delivers it later.
 *
 * [deliver] returns true when the server accepted the wish and false when the call failed, and
 * throws when the call could not be made at all. Follow/star is idempotent on Subsonic (star then
 * star, unstar then unstar), so none of the duplicate handling [FavoriteWriter] has is needed here.
 *
 * [flush] only sends the wishes of the active service: the provider's star call goes to the active
 * client, and a wish for another service waits for that service.
 */
class ArtistFollowWriter(
    private val store: ArtistFollowStore,
    private val deliver: suspend (artistId: String, followed: Boolean) -> Boolean,
    /** The service id prefix of the active provider's artist ids (for example `"SUBSONIC::"`). Read on every call: it changes. */
    private val servicePrefix: () -> String,
    /** Whether there is a network at all; false keeps everything local (offline mode included). */
    private val networkAvailable: () -> Boolean = { true },
) {

    /**
     * The user followed ([isFollowed] true) or unfollowed [artist]. True when the local state has the
     * artist followed afterwards. The local state changes first; the server call is made here but
     * never at the risk of the wish, and its failure only leaves the wish queued.
     */
    suspend fun setFollowed(artist: FollowedArtist, isFollowed: Boolean): Boolean {
        // Written first: from here on the follow exists, whatever happens to the network.
        if (!store.setFollowed(artist, isFollowed)) return store.isFollowing(artist.artistId)
        if (!networkAvailable()) return store.isFollowing(artist.artistId)
        if (!artist.artistId.startsWith(servicePrefix())) return store.isFollowing(artist.artistId)
        // The wish just written, claimed so a flush running at the same time cannot take it too.
        val mine = store.pending().firstOrNull { it.artistId == artist.artistId && it.followed == isFollowed }
        if (mine == null || !store.claim(listOf(mine))) return store.isFollowing(artist.artistId)
        dispatch(artist.artistId, isFollowed)
        return store.isFollowing(artist.artistId)
    }

    /**
     * Delivers the wishes waiting for the active service, oldest first, and returns how many the
     * server accepted. Stops at the first failure: the network is the likely reason and the rest
     * would only repeat it. A wish made while this runs (a newer tap) is left for the next flush, so
     * the newer wish is never sent as the older one.
     */
    suspend fun flush(): Int {
        if (!networkAvailable()) return 0
        val prefix = servicePrefix()
        val batch = store.takeBatch { it.artistId.startsWith(prefix) }
        if (batch.isEmpty()) return 0
        var delivered = 0
        for (wish in batch) {
            // Superseded while it waited: the newer wish is the truth, and it is queued already.
            val current = store.wish(wish.artistId) ?: continue
            if (current != wish) continue
            if (!dispatch(wish.artistId, wish.followed)) break
            delivered++
        }
        return delivered
    }

    /** The server's followed list for the active service just arrived: merge it into the local list. */
    fun adoptServerFollows(ids: Collection<String>, resolve: (String) -> FollowedArtist? = store::artist) {
        store.adoptServerFollows(ids, servicePrefix(), resolve)
    }

    /** One delivery. True when the wish is settled. */
    private suspend fun dispatch(artistId: String, followed: Boolean): Boolean {
        val outcome = try {
            val ok = deliver(artistId, followed)
            // Released before acknowledged: the wish is unclaimed whenever the ack looks at it.
            if (!ok) store.release(artistId, "rejected by the server")
            ok
        } catch (e: Exception) {
            store.release(artistId, e.message ?: e.javaClass.simpleName)
            return false
        }
        if (outcome) store.acknowledge(artistId)
        return outcome
    }
}
