// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import kotlin.math.ceil
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample

/**
 * Where the download queue stands, for "N left - about M min".
 *
 * @property remaining songs still to download (queued or running).
 * @property remainingBytes what is left to transfer: the known remainder of each job, plus the average size of
 * a finished one for each job whose size is not known yet.
 * @property bytesEstimated true when [remainingBytes] includes such a guess.
 * @property songsPerMinute the recent rate, or null when too little finished lately to say (see [QueueEtas.compute]).
 * @property etaMinutes whole minutes (rounded up, at least 1) until [remaining] is done at [songsPerMinute]; null when
 * either is unknown, 0 when nothing is left.
 */
data class QueueEta(
    val remaining: Int,
    val remainingBytes: Long,
    val bytesEstimated: Boolean,
    val songsPerMinute: Double?,
    val etaMinutes: Int?,
) {
    companion object {
        val EMPTY = QueueEta(0, 0L, false, null, 0)
    }
}

object QueueEtas {

    /** The rate is measured over this many of the latest completions. */
    const val RATE_WINDOW = 10

    /** A queue that has finished nothing for this long has no "recent rate": it is stuck or waiting, not slow. */
    const val STALE_MS = 15 * 60_000L

    private const val MINUTE_MS = 60_000.0

    /**
     * @param items the ledger.
     * @param now the current time on the same clock as [DownloadItem.completedAt].
     * @param window how many of the latest completions the rate is measured over (at least 2 are needed).
     */
    fun compute(items: List<DownloadItem>, now: Long, window: Int = RATE_WINDOW, staleMs: Long = STALE_MS): QueueEta {
        val pending = items.filter { it.active }
        if (pending.isEmpty()) return QueueEta.EMPTY

        val finished = items.filter { it.state == DownloadState.COMPLETE && it.sourceBytes > 0 }
        val average = if (finished.isEmpty()) 0L else finished.sumOf { it.sourceBytes } / finished.size
        var bytes = 0L
        var estimated = false
        for (item in pending) {
            if (item.sourceBytes > 0) {
                bytes += (item.sourceBytes - item.sourceDone).coerceAtLeast(0)
            } else {
                bytes += average
                estimated = true
            }
        }

        val rate = rate(items, now, window, staleMs)
        val eta = when {
            pending.isEmpty() -> 0
            rate == null || rate <= 0.0 -> null
            else -> ceil(pending.size / rate).toInt().coerceAtLeast(1)
        }
        return QueueEta(pending.size, bytes, estimated, rate, eta)
    }

    /** Songs per minute over the latest [window] completions: (n - 1) intervals between the first and last; null if not measurable. */
    fun rate(items: List<DownloadItem>, now: Long, window: Int = RATE_WINDOW, staleMs: Long = STALE_MS): Double? {
        val times = items.asSequence().filter { it.completedAt > 0 }.map { it.completedAt }.sortedDescending().take(window.coerceAtLeast(2)).toList()
        if (times.size < 2) return null
        if (now - times.first() > staleMs) return null
        val spanMinutes = (times.first() - times.last()) / MINUTE_MS
        if (spanMinutes <= 0.0) return null
        return (times.size - 1) / spanMinutes
    }

    /**
     * The queue's [QueueEta] as a flow: recomputed at most once a [intervalMs] (the ledger changes on every progress
     * tick), and only emitting when it differs.
     */
    @OptIn(FlowPreview::class)
    fun flow(items: Flow<List<DownloadItem>>, clock: () -> Long = System::currentTimeMillis, intervalMs: Long = 1_000L): Flow<QueueEta> =
        items.sample(intervalMs).map { compute(it, clock()) }.distinctUntilChanged()
}
