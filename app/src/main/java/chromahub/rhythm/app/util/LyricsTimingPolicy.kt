package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.LyricsData

/**
 * Rejects "synced" provider/cache payloads whose timestamps cannot drive playback.
 *
 * A first line at 00:00 is valid. A multi-line song collapsed entirely to 00:00 is not:
 * it makes AVRCP select the final line immediately and can leave Romanization mode blank.
 */
object LyricsTimingPolicy {
    fun hasUsableTimeline(timestamps: List<Long>): Boolean {
        if (timestamps.isEmpty()) return false
        if (timestamps.size == 1) return timestamps.single() >= 0L

        val normalized = timestamps.map { it.coerceAtLeast(0L) }
        return normalized.distinct().size > 1 && normalized.maxOrNull()!! > 0L
    }
}

fun LyricsData.hasUsableSyncedTimeline(): Boolean {
    val parsed = syncedLyrics?.let(LyricsParser::parseLyrics).orEmpty()
        .filter { line -> line.text.any(Character::isLetterOrDigit) }
    return LyricsTimingPolicy.hasUsableTimeline(parsed.map { it.timestamp })
}
