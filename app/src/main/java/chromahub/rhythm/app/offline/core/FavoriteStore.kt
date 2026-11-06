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
 * A song as the favourites store keeps it: only what a list needs, so the local library survives a
 * restart without the catalog. No URL is kept (they carry the login; see the ledger's payload note).
 */
data class FavoriteSong(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0L,
    val albumId: String? = null,
)

/** One wish that the server has not confirmed yet. */
data class FavoriteWish(
    val song: FavoriteSong,
    val liked: Boolean,
    /** What the last delivery attempt failed with, null when none failed. */
    val error: String? = null,
) {
    val songId: String get() = song.songId
}

/**
 * The persisted favourite state (offline favorites) - the fork's answer to "starring on the subway
 * loses the star": the user's wish is written here **first** and independently of the server call,
 * so the like shows at once, survives a restart, and is delivered by [FavoriteWriter] when the
 * network allows. The wish is the source of truth for the liked list; the server's starred list is
 * merged in when it is read.
 *
 * One entry per song, last write wins: like then unlike before a sync leaves ONE final unstar to
 * send, never a star followed by an unstar. A wish stays [pending] until [acknowledge]d (the server
 * accepted it) or [reject]ed (the server refused it as invalid: the local state is undone).
 *
 * File-backed JSON, rewritten atomically (temp file + rename) on every change, thread-safe.
 */
class FavoriteStore(private val file: File) {

    private val lock = Any()

    /** The liked songs in the order they were liked (the order the list shows). */
    private val liked = LinkedHashMap<String, FavoriteSong>()

    /** The wishes the server has not confirmed yet, by song id, oldest first. */
    private val byId = LinkedHashMap<String, FavoriteWish>()

    /** The song data of wishes only an unlike holds (the server still has the star): shown again by a like that follows. */
    private val unsentSongs = HashMap<String, FavoriteSong>()

    /** Ids claimed by a delivery in flight, so no second delivery sends the same wish at once. */
    private val inFlight = HashSet<String>()

    /** The file's content this store last read or wrote: another process (the sync worker) writes it too. */
    private var seenJson: String? = null

    private val likedFlow = MutableStateFlow<List<FavoriteSong>>(emptyList())
    private val pendingFlow = MutableStateFlow<List<FavoriteWish>>(emptyList())

    /** The liked songs, live. */
    val likedSongsFlow: StateFlow<List<FavoriteSong>> = likedFlow.asStateFlow()

    /** The wishes still to deliver, live (what a "syncing favourites" UI would read). Claimed ones are not in it. */
    val pendingFlowOut: StateFlow<List<FavoriteWish>> get() = pendingFlow.asStateFlow()

    init {
        load()
        publish()
    }

    /**
     * The liked songs, in the order they were liked. Re-reads the file when another process wrote it (the sync
     * worker delivers wishes in its own process), so the app never shows a list from before the worker's delivery.
     */
    fun likedSongs(): List<FavoriteSong> {
        refreshIfChanged()
        return likedFlow.value
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
            liked.clear()
            byId.clear()
            unsentSongs.clear()
            parse(json)
            inFlight.addAll(claimed.filter { it in byId })
            publish()
        }
    }

    fun isLikedIds(): Set<String> = synchronized(lock) { liked.keys.toSet() }

    fun isLiked(songId: String): Boolean {
        refreshIfChanged()
        return synchronized(lock) { songId in liked }
    }

    /** The song data kept for [songId]: the liked entry first, then an unsent wish (an unlike keeps it for the replay). */
    fun song(songId: String): FavoriteSong? = synchronized(lock) { liked[songId] ?: byId[songId]?.song ?: unsentSongs[songId] }

    /** The wishes waiting to be delivered, oldest first, excluding the ones claimed by a delivery. */
    fun pending(): List<FavoriteWish> {
        refreshIfChanged()
        return synchronized(lock) { byId.values.filter { it.songId !in inFlight } }
    }

    /** The stored wish for [songId] whatever its claim, null when there is none: what a delivery of it is about. */
    fun wish(songId: String): FavoriteWish? = synchronized(lock) { byId[songId] }

    /**
     * Records the user's wish for [song]. True when it changed something. Writing the wish is what
     * makes the like durable: the caller sends it afterwards, and a write that fails is harmless
     * (it is the last word already).
     *  - unlike of a song that is not liked and has no wish is a no-op (nothing to send, nothing to show);
     *  - a wish equal to the wish already queued only refreshes the song data (no second entry).
     */
    fun setLiked(song: FavoriteSong, isLiked: Boolean): Boolean = synchronized(lock) {
        val current = byId[song.songId]
        if (isLiked) {
            liked[song.songId] = song
            byId[song.songId] = FavoriteWish(song, liked = true)
        } else {
            if (song.songId !in liked && current == null) return false
            liked.remove(song.songId)
            unsentSongs[song.songId] = song
            // An unlike after a like that never went out is one unstar; an unlike of a song the server
            // already has (no wish) is a wish of its own.
            byId[song.songId] = FavoriteWish(song, liked = false)
        }
        inFlight.remove(song.songId)
        if (isLiked) unsentSongs.remove(song.songId)
        publish()
        save()
        true
    }

    /**
     * The server accepted the wish for [songId]: it is no longer pending. The local state is NOT
     * touched (it already is the wish; for a like the song stays in the list).
     */
    fun acknowledge(songId: String) = synchronized(lock) {
        inFlight.remove(songId)
        if (byId.remove(songId) != null) {
            publish()
            save()
        }
    }

    /** The delivery of [songId] failed: it stays pending, with [error] for the log. */
    fun release(songId: String, error: String?) = synchronized(lock) {
        inFlight.remove(songId)
        val wish = byId[songId] ?: return
        byId[songId] = wish.copy(error = error)
        publish()
        save()
    }

    /**
     * The server refused the wish for [songId] for good (an invalid id, deleted on the server):
     * it is dropped and the local state goes back to what the server says.
     */
    fun reject(songId: String) = synchronized(lock) { // the refused wish's local state goes either way: the server has no star
        inFlight.remove(songId)
        byId.remove(songId) ?: return
        liked.remove(songId)
        publish()
        save()
    }

    /** Takes every unclaimed wish for sending (they are in flight until [acknowledge], [release] or [reject]). */
    fun takeBatch(keep: (FavoriteWish) -> Boolean = { true }): List<FavoriteWish> = synchronized(lock) {
        // Claimed here: what is taken is in flight until it is acknowledged, released or rejected. What is kept back
        // is not claimed at all, so another service's flush cannot strand it.
        val batch = byId.values.filter { it.songId !in inFlight && keep(it) }
        inFlight.addAll(batch.map { it.songId })
        batch
    }

    /**
     * Claims exactly [wishes] for a delivery in flight (the flush path), unless one of them changed
     * or was claimed since: then nothing is claimed and false is returned.
     */
    fun claim(wishes: Collection<FavoriteWish>): Boolean = synchronized(lock) {
        if (wishes.any { byId[it.songId] != it || it.songId in inFlight }) return false
        inFlight.addAll(wishes.map { it.songId })
        true
    }

    /**
     * Replaces the liked songs of the service with [servicePrefix] by the server's starred list
     * ([ids], in the server's order), keeping the songs of other services and every undispatched
     * wish: a list read before a wish was delivered must not undo the wish.
     */
    fun adoptServerStars(ids: Collection<String>, servicePrefix: String, resolve: (String) -> FavoriteSong? = ::song) =
        synchronized(lock) {
            val order = ArrayList<String>()
            val songs = HashMap<String, FavoriteSong>()
            val wishes = LinkedHashMap(byId) // read before any is settled below
            for ((id, s) in liked) {
                if (id in byId) continue // its wish decides it, below
                if (id.startsWith(servicePrefix)) continue // the server's list decides this service, below
                order.add(id)
                songs[id] = s
            }
            // What the server lists, plus what this store holds that it does not list: the order the list shows.
            for (id in ids + liked.keys + wishes.keys) {
                val wish = wishes[id]
                // An unsent wish decides its song until it is delivered: a list that predates it must not undo it.
                // A wish only an unlike has (the server still has the star) is shown as liked, so the list does not drop it.
                if (wish != null && wish.liked) {
                    if (id !in songs) order.add(id)
                    songs[id] = wish.song
                    continue
                }
                // A delivered like the server does not list any more stands by itself. A wish only an unlike has
                // (the server still has the star) also shows, or a like that follows it has nothing to show.
                // The server gave up on a wish it answered for: it is settled, so it must not outlive the list.
                if (wish != null) byId.remove(id) // ...and must not outlive the list it answered for
                val s = resolve(id) ?: wish?.song ?: continue
                if (id !in songs) order.add(id)
                songs[id] = s
            }
            liked.clear()
            for (id in order) songs[id]?.let { liked[id] = it }
            // Whatever this store holds is shown, whatever the list says: it is the authority for what it knows.
            for (s in songs.values) if (s.songId !in liked) liked[s.songId] = s

            publish()
            save()
            Unit
        }

    private fun publish() {
        likedFlow.value = liked.values.toList()
        pendingFlow.value = byId.values.toList()
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val songs = JSONArray().also { a -> liked.values.forEach { a.put(it.toJson()) } }
            val wishes = JSONArray().also { a -> byId.values.forEach { a.put(it.toJson()) } }
            val tmp = File(file.parentFile, file.name + ".tmp")
            val kept = JSONArray().also { a -> unsentSongs.values.forEach { a.put(it.toJson()) } }
            val json = JSONObject().put("version", 1).put("liked", songs).put("pending", wishes).put("songs", kept).toString()
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
            liked.clear()
            byId.clear()
            unsentSongs.clear()
        }
    }

    private fun parse(text: String) {
        try {
            val json = JSONObject(text)
            json.optJSONArray("liked")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let(::songOf)?.let { liked[it.songId] = it }
            }
            json.optJSONArray("pending")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let(::wishOf)?.let { byId[it.songId] = it }
            }
            json.optJSONArray("songs")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let(::songOf)?.let { unsentSongs[it.songId] = it }
            }
        } catch (_: Exception) {
            liked.clear()
            byId.clear()
            unsentSongs.clear()
        }
    }

    private fun FavoriteSong.toJson() = JSONObject()
        .put("songId", songId).put("title", title).put("artist", artist).put("album", album)
        .put("durationMs", durationMs).put("albumId", albumId ?: JSONObject.NULL)

    private fun FavoriteWish.toJson() = song.toJson().put("liked", liked).put("error", error ?: JSONObject.NULL)

    private fun songOf(j: JSONObject): FavoriteSong? {
        val id = j.optString("songId").takeIf { it.isNotBlank() } ?: return null
        return FavoriteSong(
            songId = id,
            title = j.optString("title"),
            artist = j.optString("artist"),
            album = j.optString("album"),
            durationMs = j.optLong("durationMs"),
            albumId = j.optString("albumId").takeIf { !j.isNull("albumId") && it.isNotEmpty() },
        )
    }

    private fun wishOf(j: JSONObject): FavoriteWish? {
        val song = songOf(j) ?: return null
        return FavoriteWish(song, liked = j.optBoolean("liked"), error = j.optString("error").takeIf { !j.isNull("error") && it.isNotEmpty() })
    }
}
