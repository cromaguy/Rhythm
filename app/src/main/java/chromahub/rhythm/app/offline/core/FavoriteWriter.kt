// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

/**
 * Offline favorites: the bridge between a tap on the star and the server.
 *
 * Every wish is written to the [FavoriteStore] **before** anything is sent, so it is never lost
 * (that is offline favorites' whole point: the old path showed "Failed to save song" and dropped
 * the star). Then, when the network is there and [deliver] answers, the wish goes out at once and
 * stops being pending; when it does not, the wish simply stays queued and [flush] delivers it later
 * - the same shape as the keep-downloaded sync: desired state first, applied state when it can.
 *
 * [deliver] returns true when the server accepted the wish, false when the call failed, and throws
 * when the call could not be made at all (the provider reports a refused request as an exception).
 * A duplicate star (the server already has it) is a success.
 *
 * [flush] only sends the wishes of the active service: the provider's star call is the active
 * client's, and a wish for another service waits for that service.
 */
class FavoriteWriter(
    private val store: FavoriteStore,
    private val deliver: suspend (songId: String, liked: Boolean) -> Boolean,
    /** The service id prefix of the active provider's song ids (`"SUBSONIC::"`). Read on every call: it changes. */
    private val servicePrefix: () -> String,
    /** Whether there is a network at all; false keeps everything local (offline mode included). */
    private val networkAvailable: () -> Boolean = { true },
) {

    /**
     * The user liked ([isLiked] true) or unliked [song]. True when the local state has the song liked
     * afterwards. The local state changes first; the server call is made here but never at the risk of
     * the wish, and its failure only leaves the wish queued.
     */
    suspend fun setLiked(song: FavoriteSong, isLiked: Boolean): Boolean {
        // Written first: from here on the like exists, whatever happens to the network.
        if (!store.setLiked(song, isLiked)) return store.isLiked(song.songId)
        if (!networkAvailable()) return store.isLiked(song.songId)
        if (!song.songId.startsWith(servicePrefix())) return store.isLiked(song.songId)
        // The wish just written, claimed so a flush running at the same time cannot take it too.
        val mine = store.pending().firstOrNull { it.songId == song.songId && it.liked == isLiked }
        if (mine == null || !store.claim(listOf(mine))) return store.isLiked(song.songId)
        dispatch(song.songId, isLiked)
        return store.isLiked(song.songId)
    }

    /**
     * Delivers the wishes waiting for the active service, oldest first, and returns how many the
     * server accepted. Stops at the first failure: the network is the likely reason and the rest
     * would only repeat it. A wish made while this runs (a newer tap on the same song) is left for
     * the next flush, so the newer wish is never sent as the older one.
     */
    suspend fun flush(): Int {
        if (!networkAvailable()) return 0
        val prefix = servicePrefix()
        val batch = store.takeBatch { it.songId.startsWith(prefix) }
        if (batch.isEmpty()) return 0
        var delivered = 0
        for (wish in batch) {
            // Superseded while it waited: the newer wish is the truth, and it is queued already.
            val current = store.wish(wish.songId) ?: continue
            if (current != wish) continue
            if (!dispatch(wish.songId, wish.liked)) break
            delivered++
        }
        return delivered
    }

    /** The server's starred list for the active service just arrived: merge it into the local list. */
    fun adoptServerStars(ids: Collection<String>, resolve: (String) -> FavoriteSong? = store::song) {
        store.adoptServerStars(ids, servicePrefix(), resolve)
    }

    /** One delivery. True when the wish is settled (the server has it, or already had it). */
    private suspend fun dispatch(songId: String, liked: Boolean): Boolean {
        val outcome = try {
            val ok = deliver(songId, liked)
            // Released before acknowledged: the wish is unclaimed whenever the ack looks at it.
            if (!ok) store.release(songId, "rejected by the server")
            ok
        } catch (e: Exception) {
            // The provider reports a refused request as an exception. "Already starred" (500) is what a
            // star answers when the server has the song; anything else is a real failure to retry.
            if (e.isDuplicateStar()) {
                true
            } else {
                store.release(songId, e.message ?: e.javaClass.simpleName)
                return false
            }
        }
        if (outcome) store.acknowledge(songId)
        return outcome
    }
}

/** The HTTP code a failed provider call carries: on the response, or on a cause it wraps. */
internal fun Throwable.httpCode(): Int? {
    var t: Throwable? = this
    // walk the throwable and what it wraps; a response (OkHttp) carries the number as `code`
    while (t != null) {
        val direct = (t as? Any)?.let { codeOf(it) }
        if (direct != null) return direct
        t = t.cause
    }
    return null
}

/** [o]'s `code` property when it is an HTTP status (a Kotlin exception's `val code`, or a Java getter). */
private fun codeOf(o: Any): Int? {
    for (m in o.javaClass.methods) {
        if (m.parameterCount != 0) continue
        val name = m.name
        if (name != "code" && name != "getCode") continue
        val value = try {
            m.invoke(o)
        } catch (_: Exception) {
            continue
        }
        if (value is Int && value in 100..599) return value
    }
    return null
}

/** A star the server already has ("already starred", HTTP 500 on Subsonic) is not a failure to retry. */
internal fun Throwable.isDuplicateStar(): Boolean = httpCode() == 500
