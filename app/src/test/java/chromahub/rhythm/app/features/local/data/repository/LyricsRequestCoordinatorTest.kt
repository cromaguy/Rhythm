package chromahub.rhythm.app.features.local.data.repository

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LyricsRequestCoordinatorTest {
    @Test
    fun concurrentCallersShareOneRequest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = LyricsRequestCoordinator<String, String>(scope)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()

        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.run("song") {
                    calls.incrementAndGet()
                    started.complete(Unit)
                    release.await()
                    "lyrics"
                }
            }
            started.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.run("song") {
                    calls.incrementAndGet()
                    "duplicate"
                }
            }

            release.complete(Unit)

            assertEquals("lyrics", withTimeout(2_000) { first.await() })
            assertEquals("lyrics", withTimeout(2_000) { second.await() })
            assertEquals(1, calls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingOneCallerDoesNotCancelSharedRequest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = LyricsRequestCoordinator<String, String>(scope)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.run("song") {
                    started.complete(Unit)
                    release.await()
                    "lyrics"
                }
            }
            started.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.run("song") { "duplicate" }
            }

            first.cancel()
            release.complete(Unit)

            assertEquals("lyrics", withTimeout(2_000) { second.await() })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingAllCallersCancelsSharedRequest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = LyricsRequestCoordinator<String, String>(scope)
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()

        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.run("song") {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            }
            started.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.run("song") { "duplicate" }
            }

            first.cancelAndJoin()
            assertFalse(cancelled.isCompleted)
            second.cancelAndJoin()

            withTimeout(2_000) { cancelled.await() }
        } finally {
            scope.cancel()
        }
    }
}
