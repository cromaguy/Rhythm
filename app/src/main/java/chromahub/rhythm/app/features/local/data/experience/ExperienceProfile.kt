// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import androidx.annotation.Keep
import chromahub.rhythm.app.features.local.data.metadata.PersonalArchive
import com.google.gson.JsonParser
import java.io.File

@Keep
data class ExperienceProfile(val version: Int = 1, val artistWeight: Double = 0.30, val genreWeight: Double = 0.25,
    val noveltyWeight: Double = 0.20, val repeatWeight: Double = 0.10, val audioWeight: Double = 0.15,
    val homeSections: List<String> = emptyList()) {
    companion object {
        fun decode(json: String): ExperienceProfile {
            require(json.length <= 64_000)
            val root = JsonParser.parseString(json).asJsonObject
            require(root.get("schemaVersion").asInt == 1)
            fun weight(name: String, default: Double): Double = root.getAsJsonObject("recommendation")?.get(name)?.asDouble?.also { require(it.isFinite() && it in 0.0..1.0) } ?: default
            val version = root.get("version").asInt.also { require(it >= 1) }
            val sections = root.getAsJsonArray("homeSections")?.map { it.asString }.orEmpty()
            require(sections.size <= 15 && sections.distinct().size == sections.size)
            val allowed = setOf("DISCOVER", "RECENTLY_PLAYED", "ARTISTS", "RHYTHM_GUARD", "NEW_RELEASES", "RECENTLY_ADDED", "RECOMMENDED", "STATS", "MOOD")
            require(sections.all { it in allowed })
            return ExperienceProfile(version, weight("artist", .30), weight("genre", .25), weight("novelty", .20), weight("repeat", .10), weight("audio", .15), sections)
                .also { require(it.artistWeight + it.genreWeight + it.noveltyWeight + it.repeatWeight + it.audioWeight > 0) }
        }
        fun read(context: Context): ExperienceProfile = runCatching { decode(File(context.filesDir, "experience.json").readText()) }.getOrDefault(ExperienceProfile())
        fun apply(context: Context, json: String): ExperienceProfile {
            val profile = decode(json)
            PersonalArchive.atomicWrite(File(context.filesDir, "experience.json"), json.toByteArray())
            if (profile.homeSections.isNotEmpty()) chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context).setHomeSectionOrder(profile.homeSections)
            PersonalArchive.enqueue(context)
            ListeningLedger.get(context).invalidate()
            return profile
        }
    }
}

data class RecommendationCandidate(val key: String, val artist: String, val genre: String?, val addedAt: Long,
    val favorite: Boolean = false, val current: Boolean = false, val acousticSimilarity: Double = 0.0)
data class RecommendationScore(val key: String, val score: Double, val reasons: List<String>)
data class RecommendationContext(val hourlyGenres: Map<String, Double> = emptyMap(), val genreTransitions: Map<String, Double> = emptyMap(),
    val skipRates: Map<String, Double> = emptyMap(), val replayDue: Map<String, Double> = emptyMap())

/** Deterministic, explainable ranking over lifetime history; never uploads listening data. */
object PersonalRecommendation {
    fun rank(candidates: List<RecommendationCandidate>, history: List<ListeningLedger.Affinity>, profile: ExperienceProfile,
             now: Long = System.currentTimeMillis(), context: RecommendationContext = RecommendationContext()): List<RecommendationScore> {
        val artist = history.groupBy { it.artist }.mapValues { it.value.sumOf { h -> h.listenedMs }.toDouble() }
        val genre = history.filter { !it.genre.isNullOrBlank() }.groupBy { it.genre }.mapValues { it.value.sumOf { h -> h.listenedMs }.toDouble() }
        val bySong = history.associateBy { it.songKey }
        val maxArtist = artist.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val maxGenre = genre.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        fun strength(value: Double, max: Double) = kotlin.math.ln(1 + value) / kotlin.math.ln(1 + max)
        return candidates.filterNot { it.current }.map { c ->
            val h = bySong[c.key]
            val a = strength(artist[c.artist] ?: 0.0, maxArtist)
            val g = strength(genre[c.genre] ?: 0.0, maxGenre)
            val novelty = if (h == null) 1.0 else ((now - h.lastAt).coerceAtLeast(0).toDouble() / (14 * 24 * 3600_000L)).coerceIn(0.0, 1.0)
            val repeat = if (h == null) 0.0 else kotlin.math.ln(1 + h.plays.toDouble()) / 5.0
            val recentPenalty = if (h != null && now - h.lastAt < 2 * 3600_000) .4 else 0.0
            val hour = context.hourlyGenres[c.genre.orEmpty()] ?: 0.0
            val transition = context.genreTransitions[c.genre.orEmpty()] ?: 0.0
            val skip = context.skipRates[c.key] ?: 0.0
            val due = context.replayDue[c.key] ?: 0.0
            val newlyAdded = if (h == null && c.addedAt > 0) (1 - (now - c.addedAt).coerceAtLeast(0).toDouble() / (30 * 24 * 3600_000L)).coerceIn(0.0, 1.0) else 0.0
            val score = a * profile.artistWeight + g * profile.genreWeight + novelty * profile.noveltyWeight +
                repeat.coerceAtMost(1.0) * profile.repeatWeight + c.acousticSimilarity * profile.audioWeight + (if (c.favorite) .1 else 0.0) - recentPenalty +
                profile.genreWeight * .25 * (hour + transition) + profile.repeatWeight * .25 * due + profile.noveltyWeight * .2 * newlyAdded - .35 * skip
            RecommendationScore(c.key, score, buildList {
                if (a > .4) add("artist"); if (g > .4) add("genre"); if (c.acousticSimilarity > .8) add("audio")
                if (h == null) add("unplayed") else if (novelty > .7) add("rediscovery")
                if (c.favorite) add("favorite")
                if (hour > .5) add("time"); if (transition > .5) add("transition"); if (due > .5) add("routine")
            })
        }.sortedWith(compareByDescending<RecommendationScore> { it.score }.thenBy { it.key })
    }
}
