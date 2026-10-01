/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.infrastructure.notification

/**
 * Rate-limits sync progress notification updates to stay within platform notification quotas.
 */
class SyncProgressThrottle(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var lastEmitAtMs: Long? = null

    /** Returns true if an update should be posted now. [force] always emits (e.g. final state). */
    @Synchronized
    fun tryAcquire(force: Boolean = false): Boolean {
        val now = clock()
        val last = lastEmitAtMs
        if (force || last == null || now - last >= minIntervalMs) {
            lastEmitAtMs = now
            return true
        }
        return false
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS = 500L
    }
}
