// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.shared.data.model

import org.junit.Assert.*
import org.junit.Test

class LyricsApiOrderTest {
    @Test fun existingPersistedPrioritiesKeepTheirOrdinals() {
        assertEquals(0, LyricsApiPriority.LYRICALLY_FIRST.ordinal)
        assertEquals(1, LyricsApiPriority.LRCLIB_FIRST.ordinal)
        assertEquals(2, LyricsApiPriority.BETTERLYRICS_FIRST.ordinal)
        assertEquals(3, LyricsApiPriority.MUSIC_CRAWLER_FIRST.ordinal)
    }
    @Test fun unsetPriorityKeepsRhythmsDefaultChainBeforeCrawler() {
        assertEquals(listOf(LyricsApiPriority.BETTERLYRICS_FIRST, LyricsApiPriority.LYRICALLY_FIRST,
            LyricsApiPriority.LRCLIB_FIRST, LyricsApiPriority.MUSIC_CRAWLER_FIRST),
            lyricsApiOrder(LyricsApiPriority.LRCLIB_FIRST, false, true))
    }
    @Test fun disabledFallbackDoesNotAddCrawlerToExistingSelection() {
        assertEquals(listOf(LyricsApiPriority.LRCLIB_FIRST), lyricsApiOrder(LyricsApiPriority.LRCLIB_FIRST, true, false))
        assertEquals(listOf(LyricsApiPriority.LYRICALLY_FIRST), lyricsApiOrder(LyricsApiPriority.LYRICALLY_FIRST, true, false))
        assertEquals(listOf(LyricsApiPriority.BETTERLYRICS_FIRST, LyricsApiPriority.LYRICALLY_FIRST, LyricsApiPriority.LRCLIB_FIRST),
            lyricsApiOrder(LyricsApiPriority.BETTERLYRICS_FIRST, true, false))
    }
    @Test fun crawlerPriorityUsesExistingFallbackOnlyWhenEnabled() {
        assertEquals(listOf(LyricsApiPriority.MUSIC_CRAWLER_FIRST), lyricsApiOrder(LyricsApiPriority.MUSIC_CRAWLER_FIRST, true, false))
        assertEquals(listOf(LyricsApiPriority.MUSIC_CRAWLER_FIRST, LyricsApiPriority.BETTERLYRICS_FIRST,
            LyricsApiPriority.LYRICALLY_FIRST, LyricsApiPriority.LRCLIB_FIRST),
            lyricsApiOrder(LyricsApiPriority.MUSIC_CRAWLER_FIRST, true, true))
    }
}
