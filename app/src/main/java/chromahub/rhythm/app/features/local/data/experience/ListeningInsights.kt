// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class SongHabit(val songKey: String, val title: String, val artist: String, val plays: Int,
    val listenedMs: Long, val medianGapMs: Long?, val maxConsecutiveRepeats: Int, val completed: Int,
    val earlySkips: Int, val firstListenDelayMs: Long?)
data class ListeningInsights(val monthly: Map<String, Long>, val hourly: List<Long>, val genres: Map<String, Long>,
    val artists: Map<String, Long>, val transitions: Map<Pair<String, String>, Int>, val habits: List<SongHabit>, val totalPlays: Int)

object ListeningInsightAnalysis {
    fun analyze(records: List<ListeningRecord>): ListeningInsights {
        val segments = records.filter { it.kind == "segment" && it.listenedMs > 0 }
        val monthly = mutableMapOf<String, Long>(); val hours = LongArray(24)
        val genres = mutableMapOf<String, Long>(); val artists = mutableMapOf<String, Long>()
        segments.forEach { r ->
            val zone = runCatching { ZoneId.of(r.timezone) }.getOrDefault(ZoneId.systemDefault())
            var cursor = r.startedAt
            // Split across calendar boundaries instead of assigning a whole overnight play to one hour.
            while (cursor < r.endedAt) {
                val date = Instant.ofEpochMilli(cursor).atZone(zone)
                val next = minOf(r.endedAt, date.plusHours(1).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli())
                if (next <= cursor) break
                val duration = next - cursor
                val month = YearMonth.from(date).toString()
                monthly[month] = (monthly[month] ?: 0) + duration; hours[date.hour] += duration; cursor = next
            }
            val genre = r.genre?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown"
            genres[genre] = (genres[genre] ?: 0) + r.listenedMs
            artists[r.artist] = (artists[r.artist] ?: 0) + r.listenedMs
        }
        val plays = segments.groupBy { it.playId }.values.sortedBy { it.minOf(ListeningRecord::startedAt) }
        val actions = records.filter { it.kind == "action" }.groupBy { it.playId }
        val transitions = mutableMapOf<Pair<String, String>, Int>()
        plays.zipWithNext().forEach { (before, after) ->
            // Only transitions within a listening session, never infer a transition across days of silence.
            if (after.minOf { it.startedAt } - before.maxOf { it.endedAt } > 30 * 60_000) return@forEach
            val a = before.first().genre; val b = after.first().genre
            if (!a.isNullOrBlank() && !b.isNullOrBlank()) { val pair = a to b; transitions[pair] = (transitions[pair] ?: 0) + 1 }
        }
        val streaks = mutableMapOf<String, Int>(); var previous: String? = null; var streak = 0; var previousEnd = 0L
        plays.forEach { play ->
            val key = play.first().songKey
            streak = if (key == previous && play.minOf { it.startedAt } - previousEnd <= 30 * 60_000) streak + 1 else 1
            streaks[key] = maxOf(streaks[key] ?: 0, streak); previous = key; previousEnd = play.maxOf { it.endedAt }
        }
        val habits = plays.groupBy { it.first().songKey }.map { (key, group) ->
            val first = group.first().first(); val starts = group.map { it.minOf(ListeningRecord::startedAt) }.sorted()
            val gaps = starts.zipWithNext().map { (a, b) -> b - a }.sorted()
            val completed = group.count { play -> play.any { it.completed } || actions[play.first().playId].orEmpty().any { it.reason in setOf("ended", "next_auto", "repeat") } || first.audioDurationMs > 0 && play.sumOf { it.listenedMs } >= first.audioDurationMs * .9 }
            val skips = group.count { play -> first.audioDurationMs > 0 && play.sumOf { it.listenedMs } < minOf(30_000, first.audioDurationMs / 4) && (play.any { it.reason == "skip_or_queue_change" } || actions[play.first().playId].orEmpty().any { it.reason == "track_change" }) }
            SongHabit(key, first.title, first.artist, group.size, group.flatten().sumOf { it.listenedMs },
                gaps.takeIf { it.isNotEmpty() }?.get(gaps.size / 2), streaks[key] ?: 1, completed, skips,
                first.libraryAddedAt.takeIf { it > 0 && starts.first() >= it }?.let { starts.first() - it })
        }.sortedByDescending { it.listenedMs }
        return ListeningInsights(monthly.toSortedMap(), hours.toList(), genres.toList().sortedByDescending { it.second }.toMap(),
            artists.toList().sortedByDescending { it.second }.toMap(), transitions, habits, plays.size)
    }
}
