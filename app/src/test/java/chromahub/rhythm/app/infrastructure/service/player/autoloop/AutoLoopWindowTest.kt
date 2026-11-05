package chromahub.rhythm.app.infrastructure.service.player.autoloop

import org.junit.Assert.*
import org.junit.Test

class AutoLoopWindowTest {
    @Test fun installationPreservesTheAudiblePosition() {
        val window = AutoLoopWindow("song", 15126, 126926, 137152)
        val position = 60000L
        assertEquals(position, window.clipPosition(position))
    }

    @Test fun loopEndDoesNotChangeTrackDuration() {
        val window = AutoLoopWindow("song", 15126, 126926, 137152)
        assertEquals(137152, window.trackDurationMs)
        assertEquals(126925, window.clipPosition(137000))
    }

    @Test fun introRemainsSeekableAndTheEndIsClamped() {
        val window = AutoLoopWindow("song", 15126, 126926, 137152)
        assertEquals(0, window.clipPosition(0))
        assertEquals(126925, window.clipPosition(137152))
    }
}
