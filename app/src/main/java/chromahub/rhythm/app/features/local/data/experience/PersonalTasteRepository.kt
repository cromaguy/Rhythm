// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PersonalTasteRepository {
    @Volatile private var reasons: Map<String, List<String>> = emptyMap()
    fun reasons(key: String): List<String> = reasons[key].orEmpty()
    fun recommend(context: Context, songs: List<Song>, favoriteIds: List<String>, currentId: String?, count: Int): List<Song> {
        val ledger = ListeningLedger.get(context)
        val history = ledger.affinities().map { h ->
            if (h.songKey.startsWith("legacy:")) songs.firstOrNull { it.id == h.songKey.removePrefix("legacy:") }?.let { h.copy(songKey = it.uri.toString()) } ?: h else h
        }
        val profile = ExperienceProfile.read(context)
        val now = System.currentTimeMillis()
        val recent = ledger.records(now - 90 * 24 * 3600_000L) + ledger.records(now - 90 * 24 * 3600_000L, kind = "action")
        val patterns = ListeningInsightAnalysis.analyze(recent)
        val hour = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).hour
        val hourly = recent.filter { it.kind == "segment" && java.time.Instant.ofEpochMilli(it.startedAt).atZone(java.time.ZoneId.of(it.timezone)).hour == hour }
            .groupBy { it.genre }.mapValues { it.value.sumOf { row -> row.listenedMs }.toDouble() }
        val maximum = hourly.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val previousGenre = songs.firstOrNull { it.id == currentId }?.genre ?: recent.filter { it.kind == "segment" }.maxByOrNull { it.endedAt }?.genre
        val transitions = patterns.transitions.filterKeys { it.first == previousGenre }.mapKeys { it.key.second }.mapValues { it.value.toDouble() }
        val transitionMax = transitions.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val behavior = RecommendationContext(hourly.filterKeys { it != null }.mapKeys { it.key!! }.mapValues { it.value / maximum },
            transitions.mapValues { it.value / transitionMax }, patterns.habits.associate { it.songKey to it.earlySkips.toDouble() / it.plays.coerceAtLeast(1) },
            patterns.habits.mapNotNull { habit -> habit.medianGapMs?.takeIf { it > 0 }?.let { gap ->
                val last = history.firstOrNull { it.songKey == habit.songKey }?.lastAt ?: now
                habit.songKey to ((now - last).toDouble() / gap).coerceIn(0.0, 1.0)
            } }.toMap())
        val anchors = history.take(3).mapNotNull { ledger.feature(it.songKey) }
        val candidates = songs.map { song ->
            val acoustic = if (profile.audioWeight > 0 && anchors.isNotEmpty()) ledger.feature(song.uri.toString())?.let { f -> anchors.maxOf { SignalAnalysis.similarity(f, it) } } ?: 0.0 else 0.0
            RecommendationCandidate(song.uri.toString(), song.artist, song.genre, song.dateAdded, song.id in favoriteIds, song.id == currentId, acoustic)
        }
        val byKey = songs.associateBy { it.uri.toString() }
        // Limit adjacent artist repetition while retaining the original ranking within each group.
        val scores = PersonalRecommendation.rank(candidates, history, profile, now, behavior)
        reasons = scores.take(count * 2).associate { it.key to it.reasons }
        val ranked = scores.mapNotNull { byKey[it.key] }.toMutableList()
        val result = mutableListOf<Song>()
        while (result.size < count && ranked.isNotEmpty()) {
            val index = ranked.indexOfFirst { it.artist != result.lastOrNull()?.artist }.takeIf { it >= 0 } ?: 0
            result += ranked.removeAt(index)
        }
        return result
    }
}
