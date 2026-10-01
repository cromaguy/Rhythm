package chromahub.rhythm.app.features.streaming.infrastructure.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProgressThrottleTest {

    private var now = 0L
    private val throttle = SyncProgressThrottle(minIntervalMs = 500L, clock = { now })

    @Test
    fun firstUpdateIsAlwaysEmitted() {
        now = 12_345L
        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun updatesInsideTheIntervalAreDropped() {
        assertTrue(throttle.tryAcquire())
        now = 100L
        assertFalse(throttle.tryAcquire())
        now = 499L
        assertFalse(throttle.tryAcquire())
        now = 500L
        assertTrue(throttle.tryAcquire())
    }

    @Test
    fun forcedUpdatesBypassTheInterval() {
        assertTrue(throttle.tryAcquire())
        now = 1L
        assertTrue(throttle.tryAcquire(force = true))
        now = 2L
        assertFalse(throttle.tryAcquire())
    }

    @Test
    fun perAlbumTicksAreReducedToTheRateLimit() {
        // 460 album callbacks arriving every 20 ms (~9 s of sync) -> at most one post per 500 ms.
        var emitted = 0
        repeat(460) { i ->
            now = i * 20L
            if (throttle.tryAcquire()) emitted++
        }
        assertEquals(19, emitted)
    }
}
