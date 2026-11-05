package chromahub.rhythm.app.infrastructure.service.player.autoloop

import org.junit.Assert.*
import org.junit.Test

class AutoLoopStereoSpliceTest {
    @Test fun checksBothStereoChannelsEvenWhenMonoCancelsToZero() {
        val head = AutoLoopStereoSplice.Window(970L, 1000, 2,
            FloatArray(120) { if (it % 2 == 0) 0.4f else -0.4f })
        val tail = AutoLoopStereoSplice.Window(4970L, 1000, 2,
            FloatArray(120) { if (it % 2 == 0) -0.4f else 0.4f })
        fun set(window: AutoLoopStereoSplice.Window, frame: Int, value: Float) {
            window.samples[frame * 2] = value
            window.samples[frame * 2 + 1] = -value
        }
        set(head, 37, 0.1f)
        set(head, 38, 0.11f)
        set(tail, 36, 0.099f)
        set(tail, 35, 0.089f)
        val result = AutoLoopStereoSplice.choose(head, tail, 1000L to 5000L)
        assertEquals(1007L to 5007L, result)
        assertEquals(4000L, result.second - result.first)
    }

    @Test fun retainsAnAlreadySmoothJoinWithoutDriftingTheLoop() {
        val head = AutoLoopStereoSplice.Window(970L, 1000, 2, FloatArray(120))
        val tail = AutoLoopStereoSplice.Window(4970L, 1000, 2, FloatArray(120))
        assertEquals(1000L to 5000L, AutoLoopStereoSplice.choose(head, tail, 1000L to 5000L))
    }

    @Test fun neverMovesTheStartBeforeTheBeginningOfTheTrack() {
        val head = AutoLoopStereoSplice.Window(0L, 1000, 1, FloatArray(30))
        val tail = AutoLoopStereoSplice.Window(70L, 1000, 1, FloatArray(60))
        val result = AutoLoopStereoSplice.choose(head, tail, 0L to 100L)
        assertTrue(result.first >= 0L)
        assertEquals(100L, result.second - result.first)
    }

    @Test fun keepsOriginalPointsWhenDecodedWindowsHaveDifferentFormats() {
        val head = AutoLoopStereoSplice.Window(970L, 1000, 1, FloatArray(60))
        val tail = AutoLoopStereoSplice.Window(4970L, 1000, 2, FloatArray(120))
        assertEquals(1000L to 5000L, AutoLoopStereoSplice.choose(head, tail, 1000L to 5000L))
    }
}
