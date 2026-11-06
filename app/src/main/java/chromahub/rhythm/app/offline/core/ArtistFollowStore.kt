// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * An artist as the followed-artists store keeps it: only what a list needs, so the followed list
 * survives a restart with no catalog and no network.
 */
data class FollowedArtist(
    val artistId: String,
    val name: String,
    val artworkUri: String? = null,
    val songCount: Int = 0,
    val albumCount: Int = 0,
)

/** One follow/unfollow the server has not confirmed yet. */
data class ArtistFollowWish(
    val artist: FollowedArtist,
    val followed: Boolean,
    /** What the last delivery attempt failed with, null when none failed. */
    val error: String? = null,
) {
    val artistId: String get() = artist.artistId
}

/**
 * The persisted followed-artist state - the same shape as [FavoriteStore], for the other half of
 * #229's bug: following an artist used to be lost when the network call failed, because the
 * repository only ever held the follow in memory and the ViewModel forgot it on an exception.
 *
 * The wish is written here **first** and independently of the server call, so the artist shows in
 * the followed list at once, survives a restart, and is delivered by [ArtistFollowWriter] when the
 * network allows. One entry per artist, last write wins: follow then unfollow before a sync leaves
 * ONE final state to send. A wish stays pending until [acknowledge]d or [reject]ed.
 *
 * File-backed JSON, rewritten atomically (temp file + rename) on every change, thread-safe. It
 * re-reads the file when the content changed, because the sync worker is another process.
 *
 * Kept beside [FavoriteStore] rather than folded into it: the two stores answer different
 * questions (a song's star, an artist's follow), their entries carry different data, and #229's
 * favourites tests stay exactly as they are. The rules are the same, in about 200 lines.
 */
class ArtistFollowStore(private val file: File) {

    private val lock = Any()

    /** The followed artists in the order they were followed (the order the list shows). */
    private val followed = LinkedHashMap<String, FollowedArtist>()

    /** The wishes the server has not confirmed yet, by artist id, oldest first. */
    private val byId = LinkedHashMap<String, ArtistFollowWish>()

    /** The artist data of wishes only an unfollow holds (the server still follows it): shown again by a follow that follows. */
    private val unsentArtists = HashMap<String, FollowedArtist>()

    /** Ids claimed by a delivery in flight, so no second delivery sends the same wish at once. */
    private val inFlight = HashSet<String>()

    /** The file's content this store last read or wrote: another process (the sync worker) writes it too. */
    private var seenJson: String? = null

    private val followedFlow = MutableStateFlow<List<FollowedArtist>>(emptyList())
    private val pendingFlow = MutableStateFlow<List<ArtistFollowWish>>(emptyList())

    /** The followed artists, live. */
    val followedArtistsFlow: StateFlow<List<FollowedArtist>> = followedFlow.asStateFlow()

    /** The wishes still to deliver, live. Claimed ones are not in it. */
    val pendingFlowOut: StateFlow<List<ArtistFollowWish>> get() = pendingFlow.asStateFlow()

    init {
        load()
        publish()
    }

    /**
     * The followed artists, in the order they were followed. Re-reads the file when another process
     * wrote it (the sync worker delivers wishes in its own process), so the app never shows a list
     * from before the worker's delivery.
     */
    fun followedArtists(): List<FollowedArtist> {
        refreshIfChanged()
        return followedFlow.value
    }

    /** Re-reads the file when another process changed it since this store last read or wrote it. */
    fun refreshIfChanged() {
        // The content, not the timestamp: two writes inside one file-system clock tick would go unnoticed.
        val json = readFile() ?: return
        if (json == seenJson) return
        synchronized(lock) {
            if (json == seenJson) return
            val claimed = inFlight.toSet()
            seenJson = json
            followed.clear()
            byId.clear()
            unsentArtists.clear()
            parse(json)
            inFlight.addAll(claimed.filter { it in byId })
            publish()
        }
    }

    fun isFollowing(artistId: String): Boolean {
        refreshIfChanged()
        return synchronized(lock) { artistId in followed }
    }

    /** The artist data kept for [artistId]: the followed entry first, then a wish's. */
    fun artist(artistId: String): FollowedArtist? = synchronized(lock) {
        followed[artistId] ?: byId[artistId]?.artist ?: unsentArtists[artistId]
    }

    /** The wishes waiting to be delivered, oldest first, excluding the ones claimed by a delivery. */
    fun pending(): List<ArtistFollowWish> {
        refreshIfChanged()
        return synchronized(lock) { byId.values.filter { it.artistId !in inFlight } }
    }

    /** The stored wish for [artistId] whatever its claim, null when there is none. */
    fun wish(artistId: String): ArtistFollowWish? = synchronized(lock) { byId[artistId] }

    /**
     * Records the user's wish for [artist]. True when it changed something. Writing the wish is what
     * makes the follow durable: the caller sends it afterwards, and a write that fails is harmless
     * (it is the last word already).
     *  - unfollow of an artist that is not followed and has no wish is a no-op;
     *  - a wish equal to the wish already queued only refreshes the artist data (no second entry),
     *    which is why follow-then-unfollow is ONE final wish.
     */
    fun setFollowed(artist: FollowedArtist, isFollowed: Boolean): Boolean = synchronized(lock) {
        val current = byId[artist.artistId]
        if (isFollowed) {
            followed[artist.artistId] = artist
            byId[artist.artistId] = ArtistFollowWish(artist, followed = true)
            unsentArtists.remove(artist.artistId)
        } else {
            if (artist.artistId !in followed && current == null) return false
            followed.remove(artist.artistId)
            unsentArtists[artist.artistId] = artist
            // An unfollow after a follow that never went out is one unstar; an unfollow of an artist
            // the server already follows (no wish) is a wish of its own.
            byId[artist.artistId] = ArtistFollowWish(artist, followed = false)
        }
        inFlight.remove(artist.artistId)
        publish()
        save()
        true
    }

    /**
     * The server accepted the wish for [artistId]: it is no longer pending. The local state is NOT
     * touched (it already is the wish).
     */
    fun acknowledge(artistId: String) = synchronized(lock) {
        inFlight.remove(artistId)
        unsentArtists.remove(artistId)
        if (byId.remove(artistId) != null) {
            publish()
            save()
        }
    }

    /** The delivery of [artistId] failed: it stays pending, with [error] for the log. */
    fun release(artistId: String, error: String?) = synchronized(lock) {
        inFlight.remove(artistId)
        val wish = byId[artistId] ?: return
        byId[artistId] = wish.copy(error = error)
        publish()
        save()
    }

    /**
     * The server refused the wish for [artistId] for good (an invalid id, gone on the server): it is
     * dropped and the local state goes back to what the server says.
     */
    fun reject(artistId: String) = synchronized(lock) {
        inFlight.remove(artistId)
        byId.remove(artistId) ?: return
        followed.remove(artistId)
        publish()
        save()
    }

    /** Takes every unclaimed wish matching [keep] for sending (in flight until acked, released or rejected). */
    fun takeBatch(keep: (ArtistFollowWish) -> Boolean = { true }): List<ArtistFollowWish> = synchronized(lock) {
        val batch = byId.values.filter { it.artistId !in inFlight && keep(it) }
        inFlight.addAll(batch.map { it.artistId })
        batch
    }

    /**
     * Claims exactly [wishes] for a delivery in flight (the tap's own path), unless one of them
     * changed or was claimed since: then nothing is claimed and false is returned.
     */
    fun claim(wishes: Collection<ArtistFollowWish>): Boolean = synchronized(lock) {
        if (wishes.any { byId[it.artistId] != it || it.artistId in inFlight }) return false
        inFlight.addAll(wishes.map { it.artistId })
        true
    }

    /**
     * Replaces the followed artists of the service with [servicePrefix] by the server's list ([ids]),
     * keeping the artists of other services and every undispatched wish: a list read before a wish was
     * delivered must not undo the wish.
     */
    fun adoptServerFollows(ids: Collection<String>, servicePrefix: String, resolve: (String) -> FollowedArtist? = ::artist) =
        synchronized(lock) {
            val order = ArrayList<String>()
            val artists = HashMap<String, FollowedArtist>()
            val wishes = LinkedHashMap(byId) // read before any is settled below
            for ((id, a) in followed) {
                if (id in byId) continue // its wish decides it, below
                if (id.startsWith(servicePrefix)) continue // the server's list decides this service, below
                order.add(id)
                artists[id] = a
            }
            for (id in ids + followed.keys + wishes.keys) {
                val wish = wishes[id]
                // An unsent wish decides its artist until it is delivered: a list that predates it must not undo it.
                if (wish != null && wish.followed) {
                    if (id !in artists) order.add(id)
                    artists[id] = wish.artist
                    continue
                }
                // The server answered for this wish with a list: it is settled and must not outlive the list.
                if (wish != null) byId.remove(id)
                val a = resolve(id) ?: wish?.artist ?: continue
                if (id !in artists) order.add(id)
                artists[id] = a
            }
            followed.clear()
            for (id in order) artists[id]?.let { followed[id] = it }
            // Whatever this store holds is shown, whatever the list says: it is the authority for what it knows.
            for (a in artists.values) if (a.artistId !in followed) followed[a.artistId] = a

            publish()
            save()
            Unit
        }

    private fun publish() {
        followedFlow.value = followed.values.toList()
        pendingFlow.value = byId.values.toList()
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val artists = JSONArray().also { a -> followed.values.forEach { a.put(it.toJson()) } }
            val wishes = JSONArray().also { a -> byId.values.forEach { a.put(it.toJson()) } }
            val kept = JSONArray().also { a -> unsentArtists.values.forEach { a.put(it.toJson()) } }
            val json = JSONObject().put("version", 1).put("followed", artists).put("pending", wishes).put("artists", kept).toString()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            seenJson = json
        } catch (_: Exception) {
            // A store that cannot be written must not stop the tap; the state is still live in this process.
        }
    }

    private fun readFile(): String? = try {
        if (file.exists()) file.readText() else null
    } catch (_: Exception) {
        null
    }

    private fun load() {
        try {
            val text = readFile() ?: return
            seenJson = text
            parse(text)
        } catch (_: Exception) {
            followed.clear()
            byId.clear()
            unsentArtists.clear()
        }
    }

    private fun parse(text: String) {
        try {
            val json = JSONObject(text)
            json.optJSONArray("followed")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let(::artistOf)?.let { followed[it.artistId] = it }
            }
            json.optJSONArray("pending")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let(::wishOf)?.let { byId[it.artistId] = it }
            }
            json.optJSONArray("artists")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let(::artistOf)?.let { unsentArtists[it.artistId] = it }
            }
        } catch (_: Exception) {
            followed.clear()
            byId.clear()
            unsentArtists.clear()
        }
    }

    private fun FollowedArtist.toJson() = JSONObject()
        .put("artistId", artistId).put("name", name)
        .put("artworkUri", artworkUri ?: JSONObject.NULL)
        .put("songCount", songCount).put("albumCount", albumCount)

    private fun ArtistFollowWish.toJson() = artist.toJson().put("followed", followed).put("error", error ?: JSONObject.NULL)

    private fun artistOf(j: JSONObject): FollowedArtist? {
        val id = j.optString("artistId").takeIf { it.isNotBlank() } ?: return null
        return FollowedArtist(
            artistId = id,
            name = j.optString("name"),
            artworkUri = j.optString("artworkUri").takeIf { !j.isNull("artworkUri") && it.isNotEmpty() },
            songCount = j.optInt("songCount"),
            albumCount = j.optInt("albumCount"),
        )
    }

    private fun wishOf(j: JSONObject): ArtistFollowWish? {
        val artist = artistOf(j) ?: return null
        return ArtistFollowWish(
            artist,
            followed = j.optBoolean("followed"),
            error = j.optString("error").takeIf { !j.isNull("error") && it.isNotEmpty() },
        )
    }
}
