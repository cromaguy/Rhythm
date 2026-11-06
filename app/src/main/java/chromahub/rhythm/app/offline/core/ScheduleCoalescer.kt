// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

/**
 * Folds a burst of "run the download worker" requests into one. Toggling Offline mode used to queue the worker seven
 * times in a second (each setting flow, each enqueue, each sync did its own); every extra link in WorkManager's chain
 * later started, found nothing to do and stopped, churning the foreground service each time.
 *
 * The first [request] starts a timer of [windowMs]; requests that arrive before it fires are merged into it, and [action]
 * runs once with `replace = true` if any of them asked for a replacement. [startTimer] is how the caller waits
 * (a coroutine delay, a Handler); the timer calls the function it is given once. Thread-safe.
 */
class ScheduleCoalescer(
    private val windowMs: Long,
    private val startTimer: (delayMs: Long, fire: () -> Unit) -> Unit,
    private val action: (replace: Boolean) -> Unit,
) {
    private val lock = Any()
    private var armed = false
    private var replace = false

    /** How many requests were folded into runs of [action] (tests). */
    @Volatile
    var requests = 0
        private set

    fun request(replace: Boolean = false) {
        val start: Boolean
        synchronized(lock) {
            requests++
            this.replace = this.replace || replace
            start = !armed
            armed = true
        }
        if (start) startTimer(windowMs, ::fire)
    }

    private fun fire() {
        val replaceNow: Boolean
        synchronized(lock) {
            replaceNow = replace
            replace = false
            armed = false
        }
        action(replaceNow)
    }
}

/** What the queue worker's one unique WorkManager chain looks like, per link, as far as scheduling cares. */
data class WorkLink(val state: LinkState, val runAttemptCount: Int = 0)

enum class LinkState { RUNNING, WAITING, BLOCKED, FINISHED }

/**
 * Decides what a (coalesced) request does to the worker's unique work, given the links it already has. Every request
 * used to append another link; now there is never more than one link behind the running one.
 */
object ScheduleDecision {

    enum class Action {
        /** No live link: enqueue one. */
        ENQUEUE,

        /** The worker is running and nothing waits behind it: append one link, so work queued during its last moments still runs. */
        APPEND,

        /** A link already waits (for its constraints, or behind the running one): it will pick the new work up. Do nothing. */
        KEEP,

        /** Replace the chain: the caller asked for it, or a waiting link sits in a retry backoff the user should not have to wait out. */
        REPLACE,
    }

    fun decide(links: List<WorkLink>, replace: Boolean): Action {
        if (replace) return Action.REPLACE
        val live = links.filter { it.state != LinkState.FINISHED }
        if (live.isEmpty()) return Action.ENQUEUE
        // A waiting link in backoff (it was stopped or asked to retry): starting afresh beats waiting out 30 s x 2^n.
        if (live.any { it.state == LinkState.WAITING && it.runAttemptCount > 0 }) return Action.REPLACE
        if (live.any { it.state == LinkState.WAITING || it.state == LinkState.BLOCKED }) return Action.KEEP
        return Action.APPEND
    }
}
