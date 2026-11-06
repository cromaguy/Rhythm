// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Where a download is in its life. Persisted by name. */
enum class DownloadState { QUEUED, DOWNLOADING, COMPLETE, FAILED }

/**
 * Which queued job runs first. Higher [rank] first; FIFO (by [DownloadItem.queuedAt], then ledger order) within a rank.
 * Persisted by name. A bulk keep-downloaded sync of hundreds of songs must not make a download the user asked
 * for a minute ago wait behind all of them.
 */
enum class DownloadPriority(val rank: Int) {
    /** Keep-downloaded sync (favorites, kept playlists): the long tail. */
    BULK(0),

    /** The user asked for it (song menu, album or playlist button, "download all"). */
    MANUAL(1),

    /** The song that is playing right now : ahead of everything. */
    PLAYING(2),
}

/**
 * One download job. [payload] is opaque to this module (`:app` keeps the serialized song there so
 * the worker can resume without the catalog). Progress ([sourceDone]) is live
 * only; after a restart the partial files on disk are the truth and the downloader resumes them.
 */
data class DownloadItem(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val payload: String? = null,
    val state: DownloadState = DownloadState.QUEUED,
    val error: String? = null,
    val attempts: Int = 0,
    /** Size of the original file in bytes, 0 until known. */
    val sourceBytes: Long = 0L,
    val sourceDone: Long = 0L,
    val queuedAt: Long = 0L,
    /**
     * Why the song is on the device ([PinSource]): the user asked for it, it is a favorite, it is in a kept
     * playlist. It is released only when no reason is left. An entry written before pins existed is MANUAL.
     */
    val pins: Set<String> = setOf(PinSource.MANUAL),
    /** Length of the song in ms, 0 when unknown: what the size estimates of the Keep-playlists screen average. */
    val durationMs: Long = 0L,
    /**
     * What the file on the device actually is, lower case without the dot (`opus`, `flac`); null until it is
     * downloaded, and for an entry written before this was recorded.
     */
    val format: String? = null,
    /** The bitrate of the file in kbps: the transcode's cap, or the original's as the library lists it; 0 if unknown. */
    val bitrateKbps: Int = 0,
    /** True when the server re-encoded the file for "Download quality"; false for an original (the default). */
    val transcoded: Boolean = false,
    /** Which queued job runs first ([DownloadPriority]); persisted. An entry written before this existed is derived from [pins]. */
    val priority: DownloadPriority = DownloadPriority.MANUAL,
    /** When the original first landed (epoch ms), 0 while it has not: the data the queue's rate and ETA come from. */
    val completedAt: Long = 0L,
    /**
     * Earliest time (epoch ms) this job's next automatic try may run after a failed one (0 = now). It grows with
     * each failed attempt, so a server hiccup or a dropped connection is not spent on three tries seconds apart.
     */
    val retryNotBefore: Long = 0L,
) {
    val active: Boolean get() = state == DownloadState.QUEUED || state == DownloadState.DOWNLOADING

    /** Bytes this song occupies (or will) on the device. */
    val totalBytes: Long get() = sourceBytes
}

/**
 * The persisted queue and ledger of offline downloads (offline cache feature, item 1).
 *
 * File-backed JSON, rewritten atomically (temp file + rename) on every change but progress ticks.
 * Thread-safe: the download worker, the UI and the menus all touch it. [items] is a hot flow of
 * the whole list, oldest first, for the Downloads screen and the menus' "downloading" marks.
 *
 * A job that was DOWNLOADING when the process died is put back to QUEUED by [recoverInterrupted];
 * its partial files stay and the downloader resumes them with an HTTP Range request.
 */
class DownloadStateStore(
    private val file: File,
    /**
     * Where the ledger is written. Null (tests): on the calling thread, inside the lock, as before. `:app` gives it a
     * single background thread, so a state change costs the caller (the download worker, and any UI thread that touches
     * the store) a snapshot, not a rewrite of the whole ledger - with hundreds of songs queued that is about a megabyte
     * of JSON per change, five or more times per song. Writes are coalesced: a burst is one file write of the newest state.
     */
    private val saveExecutor: Executor? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val lock = Any()
    private val byId = LinkedHashMap<String, DownloadItem>()
    private val flow = MutableStateFlow<List<DownloadItem>>(emptyList())

    /** The whole ledger, oldest first. */
    val items: StateFlow<List<DownloadItem>> = flow.asStateFlow()

    init {
        load()
    }

    fun get(songId: String): DownloadItem? = synchronized(lock) { byId[songId] }

    /**
     * Queues [item] unless it is already queued, running or complete. A FAILED job is queued
     * again with its attempts reset. Returns true when the job will run (newly or again).
     */
    fun enqueue(item: DownloadItem): Boolean = synchronized(lock) {
        val queued = enqueueLocked(item)
        val merged = !queued && (mergePinsLocked(item) or bumpPriorityLocked(item))
        if (queued || merged) publishAndSave()
        queued
    }

    /**
     * Queues every item of [items] that [enqueue] would, with one write of the ledger: an album or a
     * playlist is hundreds of songs, and a write per song is quadratic. Returns how many will run.
     */
    fun enqueueAll(items: List<DownloadItem>): Int = synchronized(lock) {
        var n = 0
        var changed = false
        for (item in items) {
            if (enqueueLocked(item)) {
                n++
                changed = true
            } else if (mergePinsLocked(item) or bumpPriorityLocked(item)) {
                changed = true
            }
        }
        if (changed) publishAndSave()
        n
    }

    /**
     * Queues [item] if it is new, FAILED, or waiting for the user (pins merged); false when the entry is already
     * queued, running or complete.
     */
    private fun enqueueLocked(item: DownloadItem): Boolean {
        val existing = byId[item.songId]
        when {
            existing == null -> byId[item.songId] = item.copy(state = DownloadState.QUEUED, queuedAt = clock())
            existing.state == DownloadState.FAILED || isWaitingForUser(existing) ->
                byId[item.songId] = existing.copy(
                    state = DownloadState.QUEUED, error = null, attempts = 0, queuedAt = clock(), pins = existing.pins + item.pins,
                    priority = maxOf(existing.priority, item.priority), retryNotBefore = 0L,
                )
            else -> return false
        }
        return true
    }

    /**
     * A song already waiting in the queue that is asked for again with a higher priority (the user taps download on
     * a song the keep-downloaded sync queued) moves up; it keeps its place in line within the new class.
     */
    private fun bumpPriorityLocked(item: DownloadItem): Boolean {
        val existing = byId[item.songId] ?: return false
        if (existing.state != DownloadState.QUEUED || item.priority <= existing.priority) return false
        byId[item.songId] = existing.copy(priority = item.priority)
        return true
    }

    /** Adds [item]'s pin reasons to an existing entry. True if that changed anything. */
    private fun mergePinsLocked(item: DownloadItem): Boolean {
        val existing = byId[item.songId] ?: return false
        if (existing.pins.containsAll(item.pins)) return false
        byId[item.songId] = existing.copy(pins = existing.pins + item.pins)
        return true
    }

    /** Every entry's pin reasons: what [PinReconciler] diffs the server against. */
    fun pinsById(): Map<String, Set<String>> = synchronized(lock) { byId.mapValues { it.value.pins } }

    /** Adds the reason [pin] to each of [songIds] that is in the ledger (one write). Returns how many changed. */
    fun addPin(songIds: Collection<String>, pin: String): Int = synchronized(lock) {
        var n = 0
        for (id in songIds) {
            val item = byId[id] ?: continue
            if (pin in item.pins) continue
            byId[id] = item.copy(pins = item.pins + pin)
            n++
        }
        if (n > 0) publishAndSave()
        n
    }

    /**
     * Applies pin reasons to entries already in the ledger, with one write: [adds] and [drops] map a song
     * id to reasons to add / remove. Songs not in the ledger are ignored (the caller queues those). It never
     * deletes: a song left with no reason is for the caller to release (file and all).
     */
    fun applyPins(adds: Map<String, Set<String>>, drops: Map<String, Set<String>>) = synchronized(lock) {
        var changed = false
        for (id in adds.keys + drops.keys) {
            val item = byId[id] ?: continue
            val next = (item.pins - drops[id].orEmpty()) + adds[id].orEmpty()
            if (next != item.pins) {
                byId[id] = item.copy(pins = next)
                changed = true
            }
        }
        if (changed) publishAndSave()
    }

    /** True when this entry is the worker's to finish (queued, running, or still to be retried automatically). */
    private fun ownsTheJob(item: DownloadItem): Boolean =
        item.state == DownloadState.DOWNLOADING || (item.state == DownloadState.QUEUED && item.attempts < MAX_ATTEMPTS)

    /** True when the entry used up its automatic tries and is not running: it waits for the user ([retryFailed]). */
    fun isWaitingForUser(item: DownloadItem): Boolean =
        item.state == DownloadState.QUEUED && item.attempts >= MAX_ATTEMPTS

    /**
     * Records that [songId] is already on the device (a download from before this ledger, or one
     * the repository finished). Adds a COMPLETE entry; leaves an existing one as it is.
     */
    fun adoptComplete(item: DownloadItem, sourceBytes: Long): Boolean = synchronized(lock) {
        val existing = byId[item.songId]
        if (existing != null) {
            // Already recorded: nothing to do. A queued or running job - or one still to be retried - is the worker's:
            // it finishes the file, then completes it. Only a job waiting on the user, or failed, is nobody's, so
            // there the file on disk wins.
            if (existing.state == DownloadState.COMPLETE || ownsTheJob(existing)) return false
            byId[item.songId] = existing.copy(state = DownloadState.COMPLETE, sourceBytes = sourceBytes, sourceDone = sourceBytes, error = null)
        } else {
            byId[item.songId] = item.copy(state = DownloadState.COMPLETE, sourceBytes = sourceBytes, sourceDone = sourceBytes, queuedAt = clock())
        }
        publishAndSave()
        true
    }

    /**
     * The next job to run: the QUEUED one with the highest [DownloadPriority], oldest first within it, that is not
     * waiting out a gap after a failed try ([DownloadItem.retryNotBefore]); or null.
     * [skip] passes over the jobs this run already tried (see [DownloadQueueRunner]).
     */
    fun nextRunnable(skip: (String) -> Boolean = { false }): DownloadItem? = synchronized(lock) {
        pickNext(byId.values, clock(), skip)
    }

    /**
     * Raises a QUEUED job to [priority] (the song the user started playing). Does nothing for a job that is running,
     * done, failed or already at least that important. True if it moved up.
     */
    fun prioritize(songId: String, priority: DownloadPriority): Boolean = synchronized(lock) {
        val item = byId[songId] ?: return false
        if (item.state != DownloadState.QUEUED || priority <= item.priority) return false
        byId[songId] = item.copy(priority = priority)
        publishAndSave()
        true
    }

    /** Puts jobs a dead process left DOWNLOADING back in the queue. Returns how many. */
    fun recoverInterrupted(): Int = synchronized(lock) {
        var n = 0
        for ((id, item) in byId.entries.toList()) {
            if (item.state == DownloadState.DOWNLOADING) {
                byId[id] = item.copy(state = DownloadState.QUEUED)
                n++
            }
        }
        if (n > 0) publishAndSave()
        n
    }

    fun markDownloading(songId: String) = update(songId) { it.copy(state = DownloadState.DOWNLOADING, error = null) }

    /** Source file progress. Not persisted: the partial file on disk is the truth after a restart. */
    fun reportSourceProgress(songId: String, done: Long, total: Long) = update(songId, persist = false) {
        it.copy(sourceDone = done, sourceBytes = if (total > 0) total else it.sourceBytes)
    }

    /** [format], [bitrateKbps] and [transcoded] describe the file that landed; null / 0 keeps what the entry has. */
    fun markComplete(
        songId: String,
        sourceBytes: Long,
        format: String? = null,
        bitrateKbps: Int = 0,
        transcoded: Boolean? = null,
    ) = update(songId) {
        it.copy(
            state = DownloadState.COMPLETE, sourceBytes = sourceBytes, sourceDone = sourceBytes, error = null,
            // The first landing counts for the queue's rate.
            completedAt = it.completedAt.takeIf { at -> at > 0L } ?: clock(),
            format = format ?: it.format,
            bitrateKbps = if (format != null) bitrateKbps else it.bitrateKbps,
            transcoded = transcoded ?: it.transcoded,
        )
    }

    /**
     * Rewrites each entry with [transform] (one write; entries it returns unchanged cost nothing). `:app` uses it
     * to fill in what older ledgers did not record (duration, format). Returns how many changed.
     */
    fun migrate(transform: (DownloadItem) -> DownloadItem): Int = synchronized(lock) {
        var n = 0
        for ((id, item) in byId.entries.toList()) {
            val next = transform(item)
            if (next != item) {
                byId[id] = next
                n++
            }
        }
        if (n > 0) publishAndSave()
        n
    }

    /**
     * A try failed. Back to QUEUED with a growing wait while automatic attempts remain
     * ([retryDelayMs], under [MAX_ATTEMPTS] attempts); after that it waits for the user, who is told why and whose
     * retry ([retryFailed]) starts a fresh budget. It is never FAILED on its own: three tries seconds apart used to
     * turn one server blip or one lost connection into a song the user had to find and start again by hand.
     *
     * [onlyIfDownloading] (what the worker passes) records it only while the job is still the one that was being tried:
     * when offline mode or a lost network re-queued it during the attempt, or it finished meanwhile, that is not a
     * failure of the server. Counting it would burn an attempt of a job that was only paused.
     *
     * @return true when the failure was recorded.
     */
    fun markAttemptFailed(
        songId: String,
        error: String?,
        maxAttempts: Int = MAX_ATTEMPTS,
        onlyIfDownloading: Boolean = false,
    ): Boolean {
        var recorded = false
        update(songId) {
            if (onlyIfDownloading && it.state != DownloadState.DOWNLOADING) return@update it
            val attempts = it.attempts + 1
            recorded = true
            it.copy(
                attempts = attempts,
                error = error,
                state = DownloadState.QUEUED,
                retryNotBefore = if (attempts < maxAttempts) clock() + retryDelayMs(attempts) else 0L,
            )
        }
        return recorded
    }

    /**
     * The songs waiting on the user after their automatic tries ran out: still QUEUED with the reason kept but not
     * picked up by [nextRunnable]. The Downloads screen shows them and offers the retry.
     */
    fun waitingForUser(): List<DownloadItem> = items.value.filter(::isWaitingForUser)

    /**
     * A fresh start for every song waiting on the user (and every FAILED one from an older ledger): no attempts used,
     * no reason, runnable now. The user asked, so nothing is given up for it. Returns how many were queued.
     */
    fun retryFailed(): Int = synchronized(lock) {
        var n = 0
        for ((id, item) in byId.entries.toList()) {
            if (item.state != DownloadState.QUEUED && item.state != DownloadState.FAILED) continue
            val ranOut = item.state == DownloadState.FAILED || item.attempts >= MAX_ATTEMPTS
            if (!ranOut) continue
            byId[id] = item.copy(state = DownloadState.QUEUED, attempts = 0, error = null, retryNotBefore = 0L, queuedAt = clock())
            n++
        }
        if (n > 0) publishAndSave()
        n
    }

    /**
     * After every automatic try is used up, a song nothing wants any more (no pin reason left) is not worth keeping in
     * the ledger: it is forgotten and returned for release (its partial file goes with it). The others are lifted
     * (attempts reset) for another automatic round, at the back of the queue. Returns the ids to release.
     */
    fun liftOrRelease(): List<String> = synchronized(lock) {
        val waiting = byId.values.filter { it.state == DownloadState.QUEUED && it.attempts >= MAX_ATTEMPTS }
        if (waiting.isEmpty()) return emptyList()
        val release = ArrayList<String>()
        for (item in waiting) {
            if (item.pins.isEmpty()) {
                byId.remove(item.songId)
                release.add(item.songId)
            } else {
                byId[item.songId] = item.copy(attempts = 0, error = null, retryNotBefore = 0L, queuedAt = clock())
            }
        }
        publishAndSave()
        return release
    }

    /** Gives the job another go without counting the interruption (e.g. the network dropped). */
    fun requeue(songId: String) = update(songId) { it.copy(state = DownloadState.QUEUED) }

    /**
     * What a stopped worker does with the job it was running: back to QUEUED, but only if it is still DOWNLOADING - a
     * job that finished (or failed, or was removed) in the instant before the stop must stay as it is.
     * Returns true if it was put back.
     */
    fun requeueIfDownloading(songId: String): Boolean = synchronized(lock) {
        val item = byId[songId] ?: return false
        if (item.state != DownloadState.DOWNLOADING) return false
        byId[songId] = item.copy(state = DownloadState.QUEUED)
        publishAndSave()
        true
    }

    /**
     * Replaces each job's payload with [transform] of it (one write). Returns how many changed.
     * `:app` uses it to strip anything an older version stored that should not be on disk.
     */
    fun rewritePayloads(transform: (String) -> String): Int = synchronized(lock) {
        var n = 0
        for ((id, item) in byId.entries.toList()) {
            val payload = item.payload ?: continue
            val next = transform(payload)
            if (next != payload) {
                byId[id] = item.copy(payload = next)
                n++
            }
        }
        if (n > 0) publishAndSave()
        n
    }

    /** Forgets [songId] (cancelled or removed). Returns the item it was. */
    fun remove(songId: String): DownloadItem? = synchronized(lock) {
        val removed = byId.remove(songId)
        if (removed != null) publishAndSave()
        removed
    }

    /** Forgets every job that is not complete. Returns the ids. */
    fun removeActive(): List<String> = synchronized(lock) {
        val ids = byId.values.filter { it.state != DownloadState.COMPLETE }.map { it.songId }
        ids.forEach { byId.remove(it) }
        if (ids.isNotEmpty()) publishAndSave()
        ids
    }

    val activeCount: Int get() = flow.value.count { it.active }

    /** Bytes the completed downloads hold. */
    fun sizes(): Sizes = flow.value.let { list ->
        Sizes(sourceBytes = list.filter { it.state == DownloadState.COMPLETE }.sumOf { it.sourceBytes })
    }

    data class Sizes(val sourceBytes: Long)

    /** The track a downloaded file belongs to: [fileBaseName] is the file name without extension. */
    fun songIdOfFile(fileBaseName: String, safeName: (String) -> String): String? = synchronized(lock) {
        byId.keys.firstOrNull { safeName(it) == fileBaseName }
    }

    private fun update(songId: String, persist: Boolean = true, change: (DownloadItem) -> DownloadItem) {
        synchronized(lock) {
            val current = byId[songId] ?: return
            byId[songId] = change(current)
            if (persist) publishAndSave() else flow.value = byId.values.toList()
        }
    }

    private fun publishAndSave() {
        flow.value = byId.values.toList()
        save()
    }

    /** How many times the ledger was written (tests). */
    @Volatile
    internal var writeCount = 0
        private set

    private val writePending = AtomicBoolean(false)
    private val writeLock = Any()

    private fun save() {
        val executor = saveExecutor
        if (executor == null) {
            writeSnapshot(byId.values.toList())
        } else if (writePending.compareAndSet(false, true)) {
            // One queued write at a time; it reads the newest published state when it runs.
            try {
                executor.execute {
                    writePending.set(false)
                    writeSnapshot(flow.value)
                }
            } catch (_: Exception) {
                writePending.set(false)
            }
        }
    }

    /**
     * Writes the ledger now, on the calling thread (a worker that is ending, a test). Cheap to call when nothing
     * changed; never needed for correctness while the process lives, only to narrow the window in which a kill loses
     * the last few state changes (which [recoverInterrupted] and the files on disk make good again anyway).
     */
    fun flush() {
        if (saveExecutor == null) return
        writeSnapshot(flow.value)
    }

    private fun writeSnapshot(snapshot: List<DownloadItem>) = synchronized(writeLock) {
        writeCount++
        try {
            file.parentFile?.mkdirs()
            val array = JSONArray()
            snapshot.forEach { array.put(it.toJson()) }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(JSONObject().put("version", 1).put("items", array).toString())
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (_: Exception) {
            // A ledger that cannot be written must not stop a download; the files on disk remain.
        }
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val items = JSONObject(file.readText()).optJSONArray("items") ?: return
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i)?.let(::itemOf) ?: continue
                byId[item.songId] = item
            }
            flow.value = byId.values.toList()
        } catch (_: Exception) {
            byId.clear()
        }
    }

    private fun DownloadItem.toJson() = JSONObject()
        .put("songId", songId).put("title", title).put("artist", artist).put("album", album)
        .put("payload", payload ?: JSONObject.NULL)
        .put("state", state.name).put("error", error ?: JSONObject.NULL).put("attempts", attempts)
        .put("sourceBytes", sourceBytes)
        .put("queuedAt", queuedAt)
        .put("pins", JSONArray().also { a -> pins.sorted().forEach { a.put(it) } })
        .put("durationMs", durationMs).put("format", format ?: JSONObject.NULL)
        .put("bitrateKbps", bitrateKbps).put("transcoded", transcoded)
        .put("priority", priority.name).put("completedAt", completedAt)
        .put("retryNotBefore", retryNotBefore)

    private fun itemOf(j: JSONObject): DownloadItem? {
        val id = j.optString("songId").takeIf { it.isNotBlank() } ?: return null
        val state = enumOr(j.optString("state"), DownloadState.QUEUED)
        val sourceBytes = j.optLong("sourceBytes")
        val pins = j.optJSONArray("pins")?.let { a -> (0 until a.length()).mapNotNullTo(LinkedHashSet()) { a.optString(it).takeIf(String::isNotEmpty) } }
            ?: setOf(PinSource.MANUAL)
        return DownloadItem(
            songId = id,
            title = j.optString("title"),
            artist = j.optString("artist"),
            album = j.optString("album"),
            payload = j.optString("payload").takeIf { !j.isNull("payload") && it.isNotEmpty() },
            state = state,
            error = j.optString("error").takeIf { !j.isNull("error") && it.isNotEmpty() },
            attempts = j.optInt("attempts"),
            sourceBytes = sourceBytes,
            sourceDone = if (state == DownloadState.COMPLETE) sourceBytes else 0L,
            queuedAt = j.optLong("queuedAt"),
            // No "pins" key = written before pins existed: the user's own download. A present, empty list stays empty
            // (a release a crash cut short), and the next sync finishes it.
            pins = pins,
            // Absent in a ledger written before download quality existed: unknown, and not transcoded.
            durationMs = j.optLong("durationMs"),
            format = j.optString("format").takeIf { !j.isNull("format") && it.isNotEmpty() },
            bitrateKbps = j.optInt("bitrateKbps"),
            transcoded = j.optBoolean("transcoded"),
            // No "priority" key = written before queue priority existed: the user's own if it has that reason, else bulk.
            priority = if (j.has("priority")) enumOr(j.optString("priority"), DownloadPriority.MANUAL)
            else if (PinSource.MANUAL in pins) DownloadPriority.MANUAL else DownloadPriority.BULK,
            completedAt = j.optLong("completedAt"),
            retryNotBefore = j.optLong("retryNotBefore"),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    companion object {
        /** Automatic tries a job gets (in a row) before it waits for the user. */
        const val MAX_ATTEMPTS = 3

        /** How far apart two automatic tries of the same song are: 2 minutes, then 10. */
        const val RETRY_DELAY_MS = 2 * 60_000L

        /** The wait after the [attempts]-th failed try: [RETRY_DELAY_MS] doubled each time, capped at four. */
        fun retryDelayMs(attempts: Int): Long = RETRY_DELAY_MS shl (attempts - 1).coerceIn(0, 2)

        /**
         * The QUEUED job to run first among [items] (in ledger order): highest priority, then oldest [DownloadItem.queuedAt];
         * a tie keeps ledger order (the first wins), so a bulk enqueue of hundreds stays in the order it was queued.
         * A job that ran out of automatic tries, or whose [DownloadItem.retryNotBefore] is still in the future, is not
         * picked: it waits for the user or for its gap to end.
         *
         * [skip] leaves the jobs the current run already tried out of it: a job put back in the queue by a failed
         * attempt keeps its old [DownloadItem.queuedAt], so without this it would be picked again at once.
         */
        fun pickNext(
            items: Collection<DownloadItem>,
            now: Long = Long.MAX_VALUE,
            skip: (String) -> Boolean = { false },
        ): DownloadItem? {
            var best: DownloadItem? = null
            for (item in items) {
                if (item.state != DownloadState.QUEUED || skip(item.songId)) continue
                // Waiting on the user: out of automatic tries, or still inside the gap after a failed one.
                if (item.attempts >= MAX_ATTEMPTS) continue
                if (item.retryNotBefore > now) continue
                val b = best
                if (b == null || item.priority > b.priority || (item.priority == b.priority && item.queuedAt < b.queuedAt)) best = item
            }
            return best
        }
    }
}
