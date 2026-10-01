/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs at most one [run] block at a time. A caller that arrives while one is running joins it
 * and gets its result instead of queueing a second run behind it.
 */
internal class SingleFlight<T> {

    private val lock = Any()
    private var running: CompletableDeferred<T>? = null

    suspend fun run(block: suspend () -> T): T {
        val (deferred, owner) = synchronized(lock) {
            running?.let { it to false }
                ?: CompletableDeferred<T>().also { running = it }.let { it to true }
        }
        if (!owner) return deferred.await()

        try {
            val result = block()
            deferred.complete(result)
            return result
        } catch (e: Throwable) {
            // Joiners see the same failure (or cancellation) instead of waiting forever.
            deferred.completeExceptionally(e)
            throw e
        } finally {
            synchronized(lock) { running = null }
        }
    }
}

/** True for the first [tryEnter] only; used for work that must run once per process. */
internal class OnceGate {
    private val entered = AtomicBoolean(false)

    fun tryEnter(): Boolean = entered.compareAndSet(false, true)
}

/**
 * Remembers the catalog cache content last written to (or read from) disk, so an identical
 * save can be skipped: a sync that changed nothing then costs no multi-megabyte rewrite.
 */
internal class CatalogSaveFilter {

    @Volatile
    private var last: StreamingCatalogCache? = null

    /** True if [cache] differs from the last remembered content (ignoring the timestamp). */
    fun hasChanged(cache: StreamingCatalogCache): Boolean {
        val previous = last ?: return true
        return previous.copy(lastSyncTimestamp = 0L) != cache.copy(lastSyncTimestamp = 0L)
    }

    fun remember(cache: StreamingCatalogCache?) {
        last = cache
    }
}
