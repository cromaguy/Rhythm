package chromahub.rhythm.app.features.local.data.repository

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

internal class LyricsRequestCoordinator<K, V>(
    private val scope: CoroutineScope
) {
    private class SharedRequest<V>(val deferred: Deferred<V>) {
        private var acceptingWaiters = true
        private var waiterCount = 0

        @Synchronized
        fun tryAcquire(): Boolean {
            if (!acceptingWaiters) return false
            waiterCount++
            return true
        }

        @Synchronized
        fun release(): Boolean {
            waiterCount--
            if (waiterCount != 0) return false
            acceptingWaiters = false
            return true
        }
    }

    private val requests = ConcurrentHashMap<K, SharedRequest<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        val request = acquire(key, block)
        try {
            return request.deferred.await()
        } finally {
            if (request.release()) {
                requests.remove(key, request)
                request.deferred.cancel()
            }
        }
    }

    private fun acquire(key: K, block: suspend () -> V): SharedRequest<V> {
        while (true) {
            val candidateDeferred = scope.async(start = CoroutineStart.LAZY) { block() }
            val candidate = SharedRequest(candidateDeferred)
            check(candidate.tryAcquire())
            val existing = requests.putIfAbsent(key, candidate)

            if (existing == null) {
                candidateDeferred.invokeOnCompletion { requests.remove(key, candidate) }
                candidateDeferred.start()
                return candidate
            }

            candidate.release()
            candidateDeferred.cancel()
            if (existing.tryAcquire()) return existing
            requests.remove(key, existing)
        }
    }
}
