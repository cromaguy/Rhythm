// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

/**
 * Why a song is kept on the device ("pin reasons", offline cache: keep-downloaded sync). A download
 * stays until no reason is left; the auto-cache is a different store and never holds a pinned song.
 */
object PinSource {
    /** The user downloaded it (song menu, album/playlist button) or it predates pins. Never touched by sync. */
    const val MANUAL = "manual"

    /** Starred songs of the server library. */
    const val FAVORITES = "favorites"

    private const val PLAYLIST_PREFIX = "playlist:"

    /** The pin reason of the kept playlist called [playlistName]. A playlist is kept by NAME: re-created on the server with a new id, it is the same reason. */
    fun playlist(playlistName: String): String = PLAYLIST_PREFIX + playlistName

    fun isPlaylist(source: String): Boolean = source.startsWith(PLAYLIST_PREFIX)

    /** The playlist name a [playlist] reason carries. */
    fun playlistName(source: String): String = source.removePrefix(PLAYLIST_PREFIX)

    /**
     * True for a reason written before playlists were kept by name (`playlist:<app playlist id>`, an id looks like
     * `SUBSONIC::playlist::123`). The sync treats it as a switched-off source, so it is dropped once the name
     * reason holds the song, and a song only that reason held is released like any other that nothing wants.
     */
    fun isLegacyPlaylistId(source: String): Boolean = isPlaylist(source) && LEGACY_ID.containsMatchIn(playlistName(source))

    private val LEGACY_ID = Regex("^[A-Za-z_]+::playlist::")

    /** True for the reasons the sync owns (favorites, playlists); false for [MANUAL]. */
    fun isManaged(source: String): Boolean = source != MANUAL
}

/** What reading one pin source from the server found. */
sealed interface SourceState {
    /** The whole source: songs not listed lose this pin. */
    data class Complete(val ids: Set<String>) : SourceState

    /** Part of the source (a truncated list): songs listed gain the pin, nothing loses it. */
    data class Partial(val ids: Set<String>) : SourceState

    /** The read failed, or looked untrustworthy: this source's pins stay exactly as they are. */
    data object Unavailable : SourceState

    companion object {
        /**
         * The state a fetch of one source gives. A failure is [Unavailable]. A list shorter than the
         * [declaredCount] the server announced for it is [Partial] (the server or the client cut it off).
         */
        fun of(fetch: Result<Collection<String>>, declaredCount: Int? = null): SourceState {
            val ids = fetch.getOrNull() ?: return Unavailable
            val set = ids.toSet()
            return if (declaredCount != null && set.size < declaredCount) Partial(set) else Complete(set)
        }

        /**
         * One source made of several reads (playlists that share a name): the union of their songs. It is only
         * as sure as its weakest part: any [Unavailable] part makes it [Unavailable], any [Partial] part makes it [Partial].
         */
        fun union(parts: List<SourceState>): SourceState {
            if (parts.isEmpty() || parts.any { it == Unavailable }) return Unavailable
            val ids = parts.flatMapTo(HashSet()) { if (it is Complete) it.ids else (it as Partial).ids }
            return if (parts.any { it is Partial }) Partial(ids) else Complete(ids)
        }
    }
}

/** The changes to make so the ledger's pins match what the server says. */
data class PinPlan(
    /** Songs that gain pin reasons (a song not in the ledger yet is also queued). */
    val adds: Map<String, Set<String>>,
    /** Songs that lose pin reasons. */
    val drops: Map<String, Set<String>>,
    /** Ledger songs left with no reason at all: they are released (the file goes). */
    val release: Set<String>,
) {
    val isEmpty: Boolean get() = adds.isEmpty() && drops.isEmpty() && release.isEmpty()
}

/**
 * Works out the [PinPlan] for keep-downloaded sync as a diff of **sets of song ids**. Order never
 * matters: a playlist the server shuffles or re-orders gives the same sets and so an empty plan.
 *
 * Safety rules, because a wrong "release" deletes the user's music:
 *  - a source whose read failed ([SourceState.Unavailable]) changes nothing;
 *  - a truncated read ([SourceState.Partial]) can add, never remove;
 *  - an empty complete read of a source that holds pins today is not believed (a proxy that answers
 *    with an empty list, an error body parsed as nothing) and changes nothing;
 *  - [PinSource.MANUAL] pins are never changed here;
 *  - a song whose download is still working (in flight, or with automatic tries left) is not released, whatever
 *    reasons the plan leaves it with.
 */
object PinReconciler {

    /**
     * @param current song id to its pin reasons, for every ledger entry
     * @param sources every source that is wanted now (favorites on, playlists kept). A source that holds
     *   pins in [current] but is not in [sources] is no longer wanted (switched off): its pins are dropped.
     * @param stillWorking entries a sync must not release although no reason is left: a download in flight, or one
     *   with automatic tries left. Releasing is destructive (the file goes), so it is only ever done to an entry that
     *   is not going to succeed on its own.
     */
    fun plan(
        current: Map<String, Set<String>>,
        sources: Map<String, SourceState>,
        stillWorking: Set<String> = emptySet(),
    ): PinPlan {
        val adds = HashMap<String, MutableSet<String>>()
        val drops = HashMap<String, MutableSet<String>>()

        // Which songs hold each managed source today.
        val held = HashMap<String, MutableSet<String>>()
        for ((id, pins) in current) for (pin in pins) if (PinSource.isManaged(pin)) held.getOrPut(pin) { HashSet() }.add(id)

        for (source in sources.keys + held.keys) {
            if (!PinSource.isManaged(source)) continue
            val holds = held[source].orEmpty()
            val wanted: Set<String>
            var mayDrop = true
            val state = sources[source]
            when (state) {
                null -> wanted = emptySet() // switched off: every pin of it goes
                is SourceState.Complete -> {
                    wanted = state.ids
                    if (wanted.isEmpty() && holds.isNotEmpty()) mayDrop = false
                }
                is SourceState.Partial -> {
                    wanted = state.ids
                    mayDrop = false
                }
                SourceState.Unavailable -> continue
            }
            for (id in wanted) if (id !in holds) adds.getOrPut(id) { HashSet() }.add(source)
            if (mayDrop) for (id in holds) if (id !in wanted) drops.getOrPut(id) { HashSet() }.add(source)
        }

        val release = HashSet<String>()
        for ((id, pins) in current) {
            val after = (pins - drops[id].orEmpty()) + adds[id].orEmpty()
            if (after.isEmpty() && id !in stillWorking) release.add(id) // also an entry that already has no reason (a crash left it)
        }
        return PinPlan(adds, drops, release)
    }
}

/** A playlist as the server lists it. [id] is the app's playlist id (used to read its songs); the pin reason carries the [name]. */
data class PinPlaylist(val id: String, val name: String, val songCount: Int, val octoRadio: Boolean = false)

/**
 * What a sync reads, by playlist name.
 * @param fetch for each kept name the server has, every playlist that carries it (usually one; several = their songs are united)
 * @param absent playlist sources of kept names the server does not list (deleted there): left exactly as they are
 * @param frozen playlist sources left untouched because the listing could not be trusted; the sync is incomplete
 */
data class PlaylistSelection(
    val fetch: Map<String, List<PinPlaylist>>,
    val absent: Set<String>,
    val frozen: Set<String>,
)

/** Turns the server's playlist listing and the user's kept names into what a sync reads. */
object PlaylistSelector {

    /**
     * @param listing the server's playlists, or the failure of asking for them
     * @param keptNames the names of the playlists the user switched on
     * @param heldSources the playlist sources ([PinSource.playlist]) that hold pins in the ledger today
     *
     * A listing that failed, or came back empty while anything is kept or held, is not believed: every playlist
     * source that holds pins or is switched on is [PlaylistSelection.frozen] instead of being treated as deleted.
     * With a good listing, a kept name the server no longer has is [PlaylistSelection.absent]: its pins and files
     * stay until the user switches it off (then it is no longer a source, and the plan releases it).
     * Playlists that share a name are one source.
     */
    fun select(listing: Result<List<PinPlaylist>>, keptNames: Set<String>, heldSources: Set<String>): PlaylistSelection {
        val playlists = listing.getOrNull()
        if (playlists == null || (playlists.isEmpty() && (heldSources.isNotEmpty() || keptNames.isNotEmpty()))) {
            return PlaylistSelection(emptyMap(), emptySet(), heldSources + keptNames.map(PinSource::playlist))
        }
        val byName = playlists.groupBy { it.name }
        val fetch = LinkedHashMap<String, List<PinPlaylist>>()
        val absent = LinkedHashSet<String>()
        for (name in keptNames) {
            val found = byName[name]
            if (found.isNullOrEmpty()) absent.add(PinSource.playlist(name)) else fetch[name] = found
        }
        return PlaylistSelection(fetch, absent, emptySet())
    }
}

/** What applying a [PinPlan] to the ledger did. */
data class PinApplied(
    /** Songs queued for download. */
    val queued: Int,
    /** Songs now without any reason: the caller deletes their downloads. */
    val release: List<String>,
)

/** Applies a [PinPlan] to a [DownloadStateStore]. */
object PinApply {

    /**
     * @param ledgerPins the pins the plan was made against ([DownloadStateStore.pinsById] at the time)
     * @param itemFor the download job for a song id with no reasons set (null if the song is unknown: it is skipped)
     *
     * Kept songs are never queued because a download of theirs FAILED: their pins are right, and a run that queued
     * them again would be a run with something to do every time - which is what kept a keep-downloaded sync busy on
     * every launch of a phone with a slow link (2026-10-26). Retrying a failed download is the queue's own business
     * ([DownloadRetryPolicy], the Downloads screen's Retry). The one case the sync does queue is a song the user asked
     * for with no ledger entry at all ([itemFor] answers with [PinSource.MANUAL] reasons and the live ledger has none):
     * a release a crash cut short, whose entry is gone while its file stays.
     *
     * Reasons are added and dropped with one write; and only songs that have no reason **now** are returned for
     * release, so a download the user asked for after the plan was made is not deleted.
     */
    fun apply(store: DownloadStateStore, ledgerPins: Map<String, Set<String>>, plan: PinPlan, itemFor: (String) -> DownloadItem?): PinApplied {
        val prepared = prepare(ledgerPins, plan, { id -> store.get(id)?.pins }, itemFor)
        val queued = store.enqueueAll(prepared.items.values.toList())
        store.applyPins(plan.adds, plan.drops)
        return PinApplied(queued, plan.release.filter { store.get(it)?.pins?.isEmpty() == true })
    }

    /**
     * The ledger entries [apply] would queue, worked out without writing anything, so a caller that only wants to know
     * whether a sync has anything to do ([chromahub.rhythm.app.offline.PinSyncWorker]) can ask without queuing twice.
     *
     * @param entryPins the pins of each song's ledger entry now, or null for a song with no entry (a song's entry
     *   already has the reasons the plan adds for it); defaults to [ledgerPins], the ledger the plan was made against.
     */
    fun prepare(
        ledgerPins: Map<String, Set<String>>,
        plan: PinPlan,
        entryPins: (String) -> Set<String>? = { id -> ledgerPins[id] },
        itemFor: (String) -> DownloadItem?,
    ): PinPrepared {
        val items = LinkedHashMap<String, DownloadItem>()
        var pinsChanged = false
        for ((id, why) in plan.adds) {
            val existing = entryPins(id)
            if (existing == null) {
                itemFor(id)?.let { items[id] = it.copy(pins = why) }
            } else if (!existing.containsAll(why)) {
                pinsChanged = true
            }
        }
        // A released entry keeps its old reasons (adding and dropping them in turn leaves them as they were), so it is
        // looked at through the ledger at the time: this is what tells a no-op plan from a real one.
        for ((id, why) in plan.drops) {
            val existing = ledgerPins[id] ?: continue
            if (existing.any(why::contains)) pinsChanged = true
        }
        return PinPrepared(items, pinsChanged)
    }
}

/** What a [PinPlan] would queue and change (see [PinApply.prepare]). */
data class PinPrepared(
    /** The ledger entries to queue, by song id. */
    val items: Map<String, DownloadItem>,
    /** True when a song in the ledger gains or loses a reason (the plan alone cannot tell: it lists a song in `adds` even when it already has every reason). */
    val pinsChanged: Boolean,
)
