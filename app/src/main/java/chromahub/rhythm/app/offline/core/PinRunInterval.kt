// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

/**
 * When a keep-downloaded sync may run, and how long after one that changed nothing the next one waits.
 *
 * Fork (offline cache), 2026-10-26 device finding: on a Pixel 9 Pro XL the app process sat at ~53 % of one core and
 * WorkManager wrote "Moving WorkSpec to the foreground" once a second. A finished keep-downloaded worker could be
 * enqueued again at once (the unique work it named was finished, so KEEP replaced nothing), and nothing spaced the
 * runs after the first one. Every run then read the whole library again from the server.
 *
 * The gap starts at zero - a sync the user asked for (a star, a changed setting) runs at its own short delay - and
 * after a run that queued songs it is [GAP_MIN_MS], doubling for each further run that queues something up to
 * [GAP_MAX_MS]: a library the server still lists songs of that are downloading, or a phone on a slow link that cannot
 * keep up, is looked at less and less often instead of once a second. A run that changes nothing puts the gap back to
 * [GAP_MIN_MS], so the next real change is seen within two minutes.
 */
object PinRunInterval {

    /** How long after a run that queued songs the next look waits, at least. */
    const val GAP_MIN_MS = 2L * 60_000L

    /** The longest the gap grows to: a slow bulk download is still noticed within this. */
    const val GAP_MAX_MS = 5L * 60_000L

    /** What one run did: the reasons it added, dropped or released, and the downloads it queued. */
    data class Run(val plan: PinPlan, val queued: Int)

    /** True when the run changed the ledger or the queue: only then is it a "real change". */
    fun changed(run: Run): Boolean = !run.plan.isEmpty || run.queued > 0

    /** The gap after [run], given the gap the run before it used ([previousGapMs], 0 when there was none). */
    fun nextGapMs(previousGapMs: Long, run: Run): Long = when {
        !changed(run) -> GAP_MIN_MS
        previousGapMs <= 0L -> GAP_MIN_MS
        else -> (previousGapMs * 2).coerceAtMost(GAP_MAX_MS)
    }

    /**
     * How long to wait before the next run starts, given when the last one started ([lastStartMs], 0 for none) and
     * the gap it left ([gapMs], from [nextGapMs]). 0 means "now".
     */
    fun delayMs(now: Long, lastStartMs: Long, gapMs: Long): Long = when {
        lastStartMs <= 0L || gapMs <= 0L -> 0L
        // A clock that went backwards (a time-zone change, a boot with no RTC) must not hold a sync back for hours.
        now < lastStartMs -> 0L
        else -> (lastStartMs + gapMs - now).coerceAtLeast(0L)
    }
}
