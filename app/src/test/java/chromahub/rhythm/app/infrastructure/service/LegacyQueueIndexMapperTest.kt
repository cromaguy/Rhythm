package chromahub.rhythm.app.infrastructure.service

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyQueueIndexMapperTest {
    @Test
    fun playNextFromCollapsedQueue_insertsAfterCurrentOriginalSong() {
        assertEquals(
            7,
            resolveLegacyQueueInsertionIndex(
                requestedIndex = 1,
                legacyQueueCollapsed = true,
                currentOriginalIndex = 6,
                originalQueueSize = 20
            )
        )
    }

    @Test
    fun fullQueueIndex_isKeptUnchanged() {
        assertEquals(
            7,
            resolveLegacyQueueInsertionIndex(
                requestedIndex = 7,
                legacyQueueCollapsed = true,
                currentOriginalIndex = 6,
                originalQueueSize = 20
            )
        )
    }

    @Test
    fun insertionWithoutLegacyCollapse_isKeptUnchanged() {
        assertEquals(
            1,
            resolveLegacyQueueInsertionIndex(
                requestedIndex = 1,
                legacyQueueCollapsed = false,
                currentOriginalIndex = 6,
                originalQueueSize = 20
            )
        )
    }
}
