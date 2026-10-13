// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.shared.data.model

/** Keep existing defaults and per-user fallback behavior; append the new opt-in crawler source. */
fun lyricsApiOrder(priority: LyricsApiPriority, explicitlySet: Boolean, fallback: Boolean): List<LyricsApiPriority> {
    val primary = if (explicitlySet) priority else LyricsApiPriority.BETTERLYRICS_FIRST
    val oldOrder = when (primary) {
        LyricsApiPriority.BETTERLYRICS_FIRST -> listOf(primary, LyricsApiPriority.LYRICALLY_FIRST, LyricsApiPriority.LRCLIB_FIRST)
        LyricsApiPriority.LYRICALLY_FIRST -> listOf(primary, LyricsApiPriority.BETTERLYRICS_FIRST, LyricsApiPriority.LRCLIB_FIRST)
        LyricsApiPriority.LRCLIB_FIRST -> listOf(primary, LyricsApiPriority.BETTERLYRICS_FIRST, LyricsApiPriority.LYRICALLY_FIRST)
        LyricsApiPriority.MUSIC_CRAWLER_FIRST -> listOf(primary, LyricsApiPriority.BETTERLYRICS_FIRST, LyricsApiPriority.LYRICALLY_FIRST, LyricsApiPriority.LRCLIB_FIRST)
    }
    val selected = if (fallback || primary == LyricsApiPriority.BETTERLYRICS_FIRST) oldOrder else oldOrder.take(1)
    return if (fallback && LyricsApiPriority.MUSIC_CRAWLER_FIRST !in selected) selected + LyricsApiPriority.MUSIC_CRAWLER_FIRST else selected
}
