// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

/** Why a backfill pass ended. */
enum class BackfillEnd {
    /** Every downloaded song has a lyrics record (lyrics, or "none"). */
    DONE,

    /** The pass reached its size limit; songs remain, so the caller plans the next pass a while later. */
    LIMIT,

    /**
     * Some asks got no answer (server down, login problem, a dropped connection). Several in a row end the pass early
     * instead of trying every remaining song; either way the caller tries again later, with a backoff.
     */
    RETRY_LATER,

    /** [LyricsBackfill.run]'s `shouldContinue` said stop (Offline mode, the work was stopped). */
    STOPPED,
}

data class BackfillResult(
    val stored: Int,
    val noLyrics: Int,
    val failed: Int,
    /** Songs that still lack a record when the pass ended (including the ones that failed this time). */
    val remaining: Int,
    val end: BackfillEnd,
)

/**
 * Offline cache: gives the songs that are already downloaded their lyrics, without touching their audio.
 *
 * The progress marker is the lyrics record itself ([LyricsStore]): a song with lyrics, or with a "no lyrics" answer,
 * is done; a song with neither is pending. So the pass is resumable by nature - a killed process, a stopped worker
 * or a lost connection loses at most the ask in flight, and the next pass starts with exactly the songs left. It
 * can run as often as you like: when everything is done it asks nothing.
 *
 * Gentle on the server: at most [passLimit] asks per pass, [gapMs] between two, and after [maxConsecutiveFailures] asks
 * in a row without an answer the pass ends ([BackfillEnd.RETRY_LATER]) instead of trying every remaining song.
 * A song whose ask failed is not asked again in the same pass.
 */
class LyricsBackfill(
    private val store: LyricsStore,
    private val passLimit: Int = DEFAULT_PASS_LIMIT,
    private val gapMs: Long = DEFAULT_GAP_MS,
    private val maxConsecutiveFailures: Int = DEFAULT_MAX_CONSECUTIVE_FAILURES,
    private val pause: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** The downloaded songs that need an ask, in ledger order (oldest download first). */
    fun pending(items: Collection<DownloadItem>): List<DownloadItem> {
        val now = clock()
        return items.filter { it.state == DownloadState.COMPLETE && store.needsFetch(it.songId, now) }
    }

    suspend fun run(
        items: Collection<DownloadItem>,
        fetch: suspend (DownloadItem) -> LyricsFetch,
        shouldContinue: () -> Boolean = { true },
    ): BackfillResult {
        // An empty ledger (unreadable, or nothing downloaded) is no proof that the lyrics on disk are orphans.
        if (items.isNotEmpty()) store.sweepOrphans(items.map { it.songId })
        val todo = pending(items)
        var stored = 0
        var none = 0
        var failed = 0
        var asked = 0
        var streak = 0
        var end = BackfillEnd.DONE
        for (item in todo) {
            if (!shouldContinue()) {
                end = BackfillEnd.STOPPED
                break
            }
            if (asked >= passLimit) {
                end = BackfillEnd.LIMIT
                break
            }
            if (asked > 0) pause(gapMs)
            if (!shouldContinue()) {
                end = BackfillEnd.STOPPED
                break
            }
            asked++
            when (LyricsSync.ensure(store, item.songId) { fetch(item) }) {
                LyricsOutcome.STORED -> { stored++; streak = 0 }
                LyricsOutcome.NO_LYRICS -> { none++; streak = 0 }
                LyricsOutcome.HAD_IT -> streak = 0
                LyricsOutcome.FAILED -> {
                    failed++
                    streak++
                    if (streak >= maxConsecutiveFailures) {
                        end = BackfillEnd.RETRY_LATER
                        break
                    }
                }
            }
        }
        if (end == BackfillEnd.DONE && failed > 0) end = BackfillEnd.RETRY_LATER
        return BackfillResult(stored, none, failed, remaining = pending(items).size, end = end)
    }

    companion object {
        /** Asks per pass: a library of 900 songs is about nine passes, spaced by the worker's plan. */
        const val DEFAULT_PASS_LIMIT = 100
        const val DEFAULT_GAP_MS = 250L
        const val DEFAULT_MAX_CONSECUTIVE_FAILURES = 4

        /** The wait before the next pass when this one ended with songs left over. */
        const val NEXT_PASS_DELAY_MS = 2 * 60_000L
    }
}
