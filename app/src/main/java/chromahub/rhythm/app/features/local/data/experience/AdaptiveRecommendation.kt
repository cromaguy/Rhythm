// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import androidx.annotation.Keep
import chromahub.rhythm.app.features.local.data.metadata.PersonalArchive
import com.google.gson.Gson
import java.io.File
import kotlin.math.*

/** Fixed, versioned feature space allows old recommendation decisions to be replayed and evaluated. */
object AdaptiveRecommendation {
    const val SCHEMA = 4
    val featureNames = listOf("artist", "genre", "novelty", "repeat", "audio", "hour", "transition", "routine", "place", "output", "weekday", "completion", "recentTaste", "weather", "brightness", "volume", "ambient", "motion", "daylight", "entity")
    fun probability(weights: List<Double>, features: List<Double>): Double {
        require(weights.size == features.size && weights.size in 13..featureNames.size)
        val dot = weights.zip(features).sumOf { (w, x) -> w.coerceIn(-3.0, 3.0) * x.coerceIn(0.0, 1.0) }.coerceIn(-20.0, 20.0)
        return 1.0 / (1.0 + exp(-dot))
    }
    fun learn(weights: List<Double>, features: List<Double>, reward: Double, samples: Long): List<Double> {
        require(reward.isFinite() && reward in 0.0..1.0 && features.all { it.isFinite() })
        val prediction = probability(weights, features)
        val rate = .12 / sqrt(1.0 + samples / 100.0)
        return weights.mapIndexed { i, w -> (w + rate * ((reward - prediction) * features[i] - .005 * w)).coerceIn(-3.0, 3.0) }
    }
    fun normalizeGenre(genre: String?) = genre?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
    /** Confidence smoothing prevents one accidental skip or sparse place from dominating. */
    fun contextPreference(records: List<ListeningRecord>, situation: ListeningSituation, now: Long): RecommendationContext {
        val plays = records.filter { it.kind == "segment" && it.listenedMs > 0 }.groupBy { it.playId }.values
        val hour = mutableMapOf<String, Double>(); val place = mutableMapOf<String, Double>(); val output = mutableMapOf<String, Double>()
        val brightness = mutableMapOf<String, Double>(); val volume = mutableMapOf<String, Double>(); val ambient = mutableMapOf<String, Double>()
        val weather = mutableMapOf<String, Double>(); val day = mutableMapOf<String, Double>(); val recent = mutableMapOf<String, Double>(); val completions = mutableMapOf<String, Pair<Double, Double>>()
        val region = ListeningContext.region(situation)
        for (play in plays) {
            val r = play.last(); val genre = normalizeGenre(r.genre); if (genre.isBlank()) continue
            val listened = play.sumOf { it.listenedMs }.coerceAtMost(r.audioDurationMs.coerceAtLeast(1))
            val ratio = PlayCoverage.ratio(play) ?: .5
            val decay = exp(-(now - r.endedAt).coerceAtLeast(0).toDouble() / (30 * 86_400_000L))
            val value = ratio * decay
            val date = java.time.Instant.ofEpochMilli(r.startedAt).atZone(runCatching { java.time.ZoneId.of(r.timezone) }.getOrDefault(java.time.ZoneId.systemDefault()))
            val distance = abs(date.hour - situation.hour).let { minOf(it, 24 - it) }
            if (distance <= 1) hour[genre] = (hour[genre] ?: 0.0) + value * if (distance == 0) 1.0 else .5
            if (region != null && region == ListeningContext.region(r.situation)) place[genre] = (place[genre] ?: 0.0) + value
            if (r.situation?.let(ListeningContext::outputGroup) == ListeningContext.outputGroup(situation) && situation.routeType != "unknown") output[genre] = (output[genre] ?: 0.0) + value
            if (date.dayOfWeek.value == situation.weekday) day[genre] = (day[genre] ?: 0.0) + value
            r.situation?.let { past ->
                if (situation.brightnessSetting != null && past.brightnessSetting != null) brightness[genre] = (brightness[genre] ?: 0.0) + value * (1 - abs(situation.brightnessSetting - past.brightnessSetting) / 255.0).coerceIn(0.0, 1.0)
                if (situation.systemVolumeMax > 0 && past.systemVolumeMax > 0) {
                    val current = situation.systemVolume.toDouble() / situation.systemVolumeMax * (situation.playerVolume ?: 1f)
                    val before = past.systemVolume.toDouble() / past.systemVolumeMax * (past.playerVolume ?: 1f)
                    volume[genre] = (volume[genre] ?: 0.0) + value * (1 - abs(current - before)).coerceIn(0.0, 1.0)
                }
                if (situation.ambientSound != null && past.ambientSound != null) ambient[genre] = (ambient[genre] ?: 0.0) + value * exp(-abs(situation.ambientSound.rmsDbfs - past.ambientSound.rmsDbfs) / 15)
            }
            if (situation.weather != null && situation.weather.group == r.situation?.weather?.group) weather[genre] = (weather[genre] ?: 0.0) + value
            recent[genre] = (recent[genre] ?: 0.0) + value
            val previous = completions[r.songKey] ?: (0.0 to 0.0)
            completions[r.songKey] = previous.first + ratio to previous.second + 1
        }
        fun smooth(values: Map<String, Double>): Map<String, Double> {
            val maximum = values.values.maxOrNull() ?: return emptyMap()
            return values.mapValues { it.value / (maximum + 3.0) }
        }
        return RecommendationContext(hourlyGenres = smooth(hour), placeGenres = smooth(place), outputGenres = smooth(output),
            weekdayGenres = smooth(day), recentGenres = smooth(recent), weatherGenres = smooth(weather), brightnessGenres = smooth(brightness), volumeGenres = smooth(volume), ambientGenres = smooth(ambient), completionRates = completions.mapValues { (it.value.first + 1) / (it.value.second + 2) })
    }
    /** Penalize similar picks throughout the whole mix, while reserving a small exploration lane. */
    fun diversify(scores: List<RecommendationScore>, candidates: List<RecommendationCandidate>, count: Int, exploration: Double = .15): List<RecommendationScore> {
        val artistCounts = mutableMapOf<String, Int>(); val genreCounts = mutableMapOf<String, Int>()
        val catalog = candidates.associateBy { it.key }; val remaining = scores.toMutableList(); val selected = mutableListOf<RecommendationScore>()
        while (remaining.isNotEmpty() && selected.size < count) {
            val explore = exploration > 0 && selected.size > 0 && selected.size % (1.0 / exploration.coerceIn(.05, .5)).roundToInt() == 0
            val pool = if (explore) remaining.filter { "unplayed" in it.reasons }.ifEmpty { remaining } else remaining
            val next = pool.maxWithOrNull(compareBy<RecommendationScore> { score ->
                val c = catalog[score.key]
                score.score - .12 * (artistCounts[c?.artist?.lowercase(java.util.Locale.ROOT)] ?: 0) - .04 * (genreCounts[normalizeGenre(c?.genre)] ?: 0)
            }.thenByDescending { it.key }) ?: break
            selected += if (explore) next.copy(reasons = next.reasons + "exploration") else next
            catalog[next.key]?.let { c ->
                val artist = c.artist.lowercase(java.util.Locale.ROOT); val genre = normalizeGenre(c.genre)
                artistCounts[artist] = (artistCounts[artist] ?: 0) + 1; genreCounts[genre] = (genreCounts[genre] ?: 0) + 1
            }
            remaining.remove(next)
        }
        return selected
    }
}

@Keep
data class RecommendationEvent(val id: String, val at: Long, val type: String, val key: String, val schema: Int = AdaptiveRecommendation.SCHEMA,
    val features: List<Double> = emptyList(), val reward: Double? = null, val prediction: Double? = null, val algorithm: String = "contextual-sgd-v3", val situation: ListeningSituation? = null, val rank: Int? = null,
    val title: String? = null, val artist: String? = null, val genre: String? = null, val candidateKeys: List<String> = emptyList())
@Keep
data class RecommendationIntents(val explicit: Map<String, Double?> = emptyMap(), val collections: Map<String, Double?> = emptyMap())
@Keep
data class RecommendationModel(val weights: List<Double> = List(AdaptiveRecommendation.featureNames.size) { 0.0 }, val samples: Long = 0,
    val squaredError: Double = 0.0, val baselineError: Double = 0.0, val positive: Long = 0, val negative: Long = 0, val explicit: Map<String, Double> = emptyMap(), val collections: Map<String, Double> = emptyMap())

/** Immutable events remain the source of truth. The model is a disposable derived view of the journal. */
class RecommendationLearning private constructor(private val context: Context) {
    private val root = File(context.filesDir, "music_metadata/recommendations").apply { mkdirs() }
    private val gson = Gson()
    private var signature = ""
    private var loadedSnapshot = false
    private var currentIntents = RecommendationIntents()
    private val snapshotFile get() = File(root, "trained-model.json")
    private val intentsFile get() = File(root, "current-intents.json")
    private var cached = RecommendationModel()
    private var collectionEvents = emptyMap<String, RecommendationEvent>()
    private val offers = linkedMapOf<String, RecommendationScore>()
    private val selected = mutableMapOf<String, Pair<Long, RecommendationScore>>()
    private val descriptions = linkedMapOf<String, RecommendationCandidate>()
    private var lastPresentation = ""
    private var lastPresentationAt = 0L
    @Synchronized fun offer(scores: List<RecommendationScore>, candidates: List<RecommendationCandidate> = emptyList()) {
        scores.forEach { offers[it.key] = it }
        val keys = scores.map { it.key }.toSet(); candidates.filter { it.key in keys }.forEach { descriptions[it.key] = it }
        while (offers.size > 1000) { val oldest = offers.keys.first(); offers.remove(oldest); descriptions.remove(oldest) }
    }
    @Synchronized fun captureFeatures(keys: List<String>): Map<String, List<Double>> = keys.mapNotNull { key ->
        offers[key]?.features?.takeIf { it.size == AdaptiveRecommendation.featureNames.size }?.let { key to it }
    }.toMap()
    @Synchronized fun restoreOffers(snapshot: MixSnapshot) {
        offer(snapshot.features.orEmpty().mapNotNull { (key, features) ->
            features.takeIf { it.size in 13..AdaptiveRecommendation.featureNames.size && it.all { value -> value.isFinite() && value in 0.0..1.0 } }?.let {
                RecommendationScore(key, 0.0, emptyList(), it + List(AdaptiveRecommendation.featureNames.size - it.size) { 0.0 })
            }
        })
    }
    @Synchronized fun presented(keys: List<String>) {
        val now = System.currentTimeMillis(); val identity = keys.joinToString("|")
        if (identity == lastPresentation && now - lastPresentationAt < 5 * 60_000) return
        lastPresentation = identity; lastPresentationAt = now
        val situation = ListeningContext.get(context).snapshot(now)
        val weights = model().weights
        val events = keys.take(30).mapIndexedNotNull { index, key -> offers[key]?.let { score ->
            val song = descriptions[key]
            RecommendationEvent(java.util.UUID.randomUUID().toString(), now, "presented", key, features = score.features,
                prediction = AdaptiveRecommendation.probability(weights, score.features), situation = situation, rank = index + 1,
                title = song?.title, artist = song?.artist, genre = song?.genre)
        } }
        if (events.isNotEmpty()) appendBatch(events)
    }
    fun selected(key: String) = decision(key, "selected")
    fun playbackStarted(key: String) = decision(key, "play_started")
    @Synchronized private fun decision(key: String, type: String) {
        offers[key]?.let { score ->
            val probability = AdaptiveRecommendation.probability(model().weights, score.features)
            selected.entries.removeAll { System.currentTimeMillis() - it.value.first > 6 * 3600_000L }
            if (selected.size >= 64) selected.minByOrNull { it.value.first }?.key?.let(selected::remove)
            selected[key] = System.currentTimeMillis() to score.copy(score = probability)
            append(RecommendationEvent(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), type, key, features = score.features, prediction = model().let { AdaptiveRecommendation.probability(it.weights, score.features) }, situation = ListeningContext.get(context).snapshot())) }
    }
    @Synchronized fun playOutcome(key: String, reward: Double, playId: String) {
        model() // Recover recent decisions after a process restart.
        val decision = selected.remove(key) ?: return
        if (System.currentTimeMillis() - decision.first > 6 * 3600_000L) return
        append(RecommendationEvent("outcome:$playId", System.currentTimeMillis(), "outcome", key, features = decision.second.features, reward = reward, prediction = decision.second.score))
    }
    @Synchronized fun feedback(key: String, reward: Double?) {
        val score = offers[key]
        append(RecommendationEvent(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), "explicit", key,
            features = score?.features.orEmpty(), reward = reward, prediction = score?.features?.takeIf { it.size == AdaptiveRecommendation.featureNames.size }?.let { AdaptiveRecommendation.probability(model().weights, it) }))
    }
    @Synchronized fun collectionFeedback(id: String, keys: List<String>, reward: Double?) {
        model()
        val previous = collectionEvents[id]
        val vectors = keys.mapNotNull { offers[it]?.features?.takeIf { it.size == AdaptiveRecommendation.featureNames.size } }
        val features = if (vectors.isEmpty()) previous?.features.orEmpty() else List(AdaptiveRecommendation.featureNames.size) { i -> vectors.map { it[i] }.average() }
        append(RecommendationEvent(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), "collection_explicit", id,
            features = features, reward = reward, prediction = features.takeIf { it.isNotEmpty() }?.let { AdaptiveRecommendation.probability(model().weights, it) }, situation = ListeningContext.get(context).snapshot(), candidateKeys = keys.ifEmpty { previous?.candidateKeys.orEmpty() }))
    }
    /** UI/playback reads a materialized model. Journal replay never runs in this path. */
    @Synchronized fun model(): RecommendationModel {
        if (!loadedSnapshot) {
            loadedSnapshot = true
            runCatching {
                val f = File(root, "collection-events.json")
                if (f.length() in 1..1_000_000) collectionEvents = gson.fromJson(f.readText(), Array<RecommendationEvent>::class.java).takeLast(512).associateBy { it.key }
            }
            runCatching {
                val f = File(root, "pending-decisions.json")
                if (f.length() in 1..1_000_000) {
                    val restored: Map<String, Pair<Long, RecommendationScore>> = gson.fromJson(f.readText(), object : com.google.gson.reflect.TypeToken<Map<String, Pair<Long, RecommendationScore>>>() {}.type)
                    selected.putAll(restored.filterValues { System.currentTimeMillis() - it.first in 0..6 * 3600_000L }.entries.toList().takeLast(48).associate { it.toPair() })
                }
            }
            cached = runCatching { if (snapshotFile.length() in 1..4_000_000) gson.fromJson(snapshotFile.readText(), RecommendationModel::class.java) else null }.getOrNull() ?: RecommendationModel()
            cached = cached.copy(weights = cached.weights.take(AdaptiveRecommendation.featureNames.size) + List((AdaptiveRecommendation.featureNames.size - cached.weights.size).coerceAtLeast(0)) { 0.0 })
            runCatching { if (intentsFile.length() in 1..4_000_000) gson.fromJson(intentsFile.readText(), RecommendationIntents::class.java) else null }.getOrNull()?.let {
                currentIntents = it
                val explicit = cached.explicit.toMutableMap(); val collections = cached.collections.toMutableMap()
                it.explicit.orEmpty().forEach { (key, value) -> if (value == null) explicit.remove(key) else explicit[key] = value }
                it.collections.orEmpty().forEach { (key, value) -> if (value == null) collections.remove(key) else collections[key] = value }
                cached = cached.copy(explicit = explicit, collections = collections)
            }
        }
        return cached
    }
    /** Heavy replay has no object monitor: UI and playback never wait behind training. */
    internal suspend fun rebuild(): RecommendationModel {
        val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        val budget = AnalysisBudget(context, { job?.isActive != false }, 90_000)
        TasteMemory.get(context).recover(budget)
        ListeningLedger.get(context).backfillAffinities(budget)
        ContextPreferenceIndex(context).use { it.rebuild(ListeningLedger.get(context), budget) }
        val result = RecommendationTrainingIndex(context).train(root, budget)
        ListeningInsightCache.refresh(context, budget)
        synchronized(this) {
            model()
            val explicit = result.explicit.toMutableMap(); val collections = result.collections.toMutableMap()
            currentIntents.explicit.forEach { (key, value) -> if (value == null) explicit.remove(key) else explicit[key] = value }
            currentIntents.collections.forEach { (key, value) -> if (value == null) collections.remove(key) else collections[key] = value }
            cached = result.copy(explicit = explicit, collections = collections)
            PersonalArchive.atomicWrite(snapshotFile, gson.toJson(cached).toByteArray())
            return cached
        }
    }
    private fun append(event: RecommendationEvent) = appendBatch(listOf(event))
    private fun appendBatch(events: List<RecommendationEvent>) {
        model()
        events.forEach { event ->
            when (event.type) {
                "explicit" -> {
                    currentIntents = currentIntents.copy(explicit = currentIntents.explicit + (event.key to event.reward))
                    cached = cached.copy(explicit = if (event.reward == null) cached.explicit - event.key else cached.explicit + (event.key to event.reward))
                }
                "collection_explicit" -> {
                    currentIntents = currentIntents.copy(collections = currentIntents.collections + (event.key to event.reward))
                    collectionEvents = (collectionEvents + (event.key to event)).entries.toList().takeLast(512).associate { it.key to it.value }
                    cached = cached.copy(collections = if (event.reward == null) cached.collections - event.key else cached.collections + (event.key to event.reward))
                }
            }
        }
        if (events.any { it.type in setOf("explicit", "collection_explicit") })
            PersonalArchive.atomicWrite(intentsFile, gson.toJson(currentIntents).toByteArray())
        val file = File(root, "${java.time.LocalDate.now()}.jsonl")
        // Isolate an interrupted final record before appending.
        if (file.length() > 0) java.io.RandomAccessFile(file, "r").use { it.seek(file.length() - 1); if (it.read() != 10) java.io.FileOutputStream(file, true).use { out -> out.write(10); out.fd.sync() } }
        java.io.FileOutputStream(file, true).use { it.write(events.joinToString("\n", postfix = "\n") { gson.toJson(it) }.toByteArray()); it.fd.sync() }
        if (events.any { it.type == "collection_explicit" }) PersonalArchive.atomicWrite(File(root, "collection-events.json"), gson.toJson(collectionEvents.values).toByteArray())
        if (events.any { it.type in setOf("selected", "play_started", "outcome") }) PersonalArchive.atomicWrite(File(root, "pending-decisions.json"), gson.toJson(selected.entries.toList().takeLast(48).associate { it.toPair() }).toByteArray())
        signature = ""; ListeningLedger.get(context).invalidate(); PersonalArchive.enqueue(context); RecommendationTrainingWorker.enqueue(context)
    }
    companion object {
        @Volatile private var instance: RecommendationLearning? = null
        fun get(context: Context): RecommendationLearning = instance ?: synchronized(this) { instance ?: RecommendationLearning(context.applicationContext).also { instance = it } }
    }
}
