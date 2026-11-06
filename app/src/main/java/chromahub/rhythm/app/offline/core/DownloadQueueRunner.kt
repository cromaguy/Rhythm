// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import kotlinx.coroutines.CancellationException

/** What became of one job the worker took from the queue. */
enum class ItemOutcome {
    /** Finished, failed for good or moved on: take the next. */
    DONE,

    /** The job left the ledger (cancelled, removed): take the next. */
    REMOVED,

    /** The network went; the job is back in the queue and the run ends to wait for it. */
    NETWORK_LOST,

    /** Offline mode went on; the job is back in the queue and the run ends. */
    PAUSED,
}

/** Why a run of the queue ended. */
enum class RunEnd {
    /** Nothing runnable is left. */
    DRAINED,

    /** Offline mode: leave the server alone; the app starts the worker again when it goes off. */
    PAUSED,

    /** No network: continue when there is one. */
    NETWORK_LOST,

    /** The time budget of a run is spent but jobs remain: hand over to a fresh run (WorkManager caps a job's length). */
    BUDGET,
}

/**
 * The loop of the download worker, with its stop handling made explicit and testable (no Android in it).
 *
 * - **Chunked.** A run takes jobs until [budgetMs] is spent, then ends with [RunEnd.BUDGET] so the worker can hand
 *   over to a fresh one instead of being cut by the system at ~10 minutes (a cut costs a backoff that grows each time).
 * - **A failed attempt is not the end of the run.** A job whose attempt failed goes back to QUEUED (until
 *   [DownloadStateStore.MAX_ATTEMPTS], then FAILED) - and the store would hand that same job straight back, since a
 *   re-queued job keeps its [DownloadItem.queuedAt] and so its place at the head of the queue. One server blip would
 *   then burn all three attempts in seconds. So each run remembers what it already tried ([skip]) and passes over it:
 *   the run carries on with the other jobs and ends [RunEnd.DRAINED] with the failed one still queued, for a later run.
 * - **Stopping is cheap.** When the coroutine is cancelled (the system stopped the job: constraints lost, battery
 *   saver, timeout, app update) the running job goes back to QUEUED - only if it is still DOWNLOADING, so a job that
 *   finished in that instant stays finished - and the cancellation continues. That is a few field writes; the ledger
 *   itself is written by the store's own thread, and the transfer ends at its next chunk (`shouldContinue`).
 *   Nothing here blocks the thread the system called `onStopJob` on.
 * - **One bad job cannot take the queue down.** An exception from [process] counts an attempt against that job (it is
 *   FAILED after [DownloadStateStore.MAX_ATTEMPTS]) with [DownloadErrors.describe] as the reason, and the run goes on.
 */
class DownloadQueueRunner(
    private val store: DownloadStateStore,
    private val clock: () -> Long,
    /** Length of this run; re-read each time, so a worker promoted to the foreground can be given more. */
    private val budgetMs: () -> Long,
    private val isPaused: () -> Boolean,
    /** A pause after a failed job (a server hiccup gets a moment). */
    private val pauseAfterError: suspend () -> Unit = {},
    /**
     * Whether this run already had a go at a job (by [DownloadItem.songId]): such a job is passed over, so one blip
     * cannot use up an attempt budget in a single run. The caller keeps the set ([attemptedInRun] is one).
     */
    private val skip: (String) -> Boolean = { false },
    private val process: suspend (DownloadItem) -> ItemOutcome,
) {
    /** The id of the job being processed, or null between jobs (tests, and the worker's notification). */
    @Volatile
    var current: String? = null
        private set

    suspend fun run(): RunEnd {
        val started = clock()
        while (true) {
            if (isPaused()) return RunEnd.PAUSED
            // A job this run already tried is waited out here, not handed back at once (see the class comment).
            val item = store.nextRunnable(skip) ?: return RunEnd.DRAINED
            if (clock() - started >= budgetMs()) return RunEnd.BUDGET
            current = item.songId
            val outcome = try {
                process(item)
            } catch (e: CancellationException) {
                store.requeueIfDownloading(item.songId)
                throw e
            } catch (e: Exception) {
                store.markAttemptFailed(item.songId, DownloadErrors.describe(e))
                pauseAfterError()
                ItemOutcome.DONE
            } finally {
                if (current == item.songId) current = null
            }
            when (outcome) {
                ItemOutcome.DONE, ItemOutcome.REMOVED -> Unit
                ItemOutcome.NETWORK_LOST -> return RunEnd.NETWORK_LOST
                ItemOutcome.PAUSED -> return RunEnd.PAUSED
            }
        }
    }
}

/**
 * Whether the worker should (still) try to become a foreground service. Starting one from the background is refused
 * (Android 12+), and the worker used to ask again every second, each time posting a notification the system then
 * dropped and logging a denial. After a refusal it now waits [retryAfterMs] (the app may have come to the foreground
 * meanwhile), and once promoted it only updates.
 */
class ForegroundPromotion(private val clock: () -> Long, private val retryAfterMs: Long = 60_000L) {

    @Volatile
    var promoted = false
        private set
    private var deniedAt = Long.MIN_VALUE

    /** True if the worker should call setForeground now (to promote itself, or to update the notification once promoted). */
    fun shouldAttempt(): Boolean = promoted || deniedAt == Long.MIN_VALUE || clock() - deniedAt >= retryAfterMs

    fun record(success: Boolean) {
        promoted = success
        deniedAt = if (success) Long.MIN_VALUE else clock()
    }
}
