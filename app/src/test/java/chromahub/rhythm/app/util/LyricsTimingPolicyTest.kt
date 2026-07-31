package chromahub.rhythm.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTimingPolicyTest {
    @Test
    fun multiLineTimelineCollapsedAtZero_isRejected() {
        assertFalse(LyricsTimingPolicy.hasUsableTimeline(listOf(0L, 0L, 0L)))
    }

    @Test
    fun timelineMayStartAtZeroWhenLaterLinesAdvance() {
        assertTrue(LyricsTimingPolicy.hasUsableTimeline(listOf(0L, 2_500L, 7_900L)))
    }
}
