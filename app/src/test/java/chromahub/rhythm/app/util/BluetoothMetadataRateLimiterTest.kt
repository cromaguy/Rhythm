package chromahub.rhythm.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothMetadataRateLimiterTest {
    private val limiter = BluetoothMetadataRateLimiter(minimumIntervalMs = 1_500L)

    @Test
    fun coalescesRapidLineChangesAndEventuallyPublishesTheLatestLine() {
        assertTrue(limiter.shouldPublish("song", "first", 0L))
        assertFalse(limiter.shouldPublish("song", "second", 350L))
        assertFalse(limiter.shouldPublish("song", "third", 700L))
        assertTrue(limiter.shouldPublish("song", "third", 1_500L))
        assertFalse(limiter.shouldPublish("song", "third", 1_850L))
    }

    @Test
    fun publishesTheFirstLineOfANewSongImmediately() {
        assertTrue(limiter.shouldPublish("first", "line", 0L))
        assertTrue(limiter.shouldPublish("second", "line", 100L))
    }
}
