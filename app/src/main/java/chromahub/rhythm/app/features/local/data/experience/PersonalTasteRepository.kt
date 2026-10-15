// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PersonalTasteRepository {
    @Volatile private var reasons: Map<String, List<String>> = emptyMap()
    fun reasons(key: String): List<String> = reasons[key].orEmpty()
    @Synchronized fun recommend(context: Context, songs: List<Song>, favoriteIds: List<String>, currentId: String?, count: Int, seed: Long = System.currentTimeMillis() / 86_400_000L): List<Song> {
        val ledger = ListeningLedger.get(context)
        val history = ledger.affinities().map { h ->
            if (h.songKey.startsWith("legacy:")) songs.firstOrNull { it.id == h.songKey.removePrefix("legacy:") }?.let { h.copy(songKey = it.uri.toString()) } ?: h else h
        }
        val profile = ExperienceProfile.read(context)
        val now = System.currentTimeMillis()
        val current = songs.firstOrNull { it.id == currentId }
        val situation = ListeningContext.get(context).snapshot(now)
        val learning = RecommendationLearning.get(context)
        val model = learning.model()
        // A screen refresh reads compact indexed summaries; it never replays the listening journal.
        val combined = ContextPreferenceIndex(context).use { it.preferences(situation, current?.genre) }
            .copy(learnedWeights = model.weights, learnedSamples = model.samples, explicitFeedback = model.explicit)
        val anchors = (listOfNotNull(songs.firstOrNull { it.id == currentId }?.uri?.toString()) + history.take(3).map { it.songKey }).distinct().mapNotNull { ledger.feature(it) }
        val tasteVector = AudioVectors.taste(history.take(50).mapNotNull { h ->
            ledger.feature(h.songKey)?.let { feature ->
                val completion = combined.completionRates[h.songKey] ?: .5
                val decay = kotlin.math.exp(-(now - h.lastAt).coerceAtLeast(0).toDouble() / (90 * 86_400_000L))
                feature to (kotlin.math.ln(1 + h.listenedMs.toDouble()) * completion * decay * (1 - (combined.skipRates[h.songKey] ?: 0.0)).coerceAtLeast(.05))
            }
        })
        val exclusions = PersonalCollections.options(context)
        // Keep large libraries responsive. Rotate the exploration window rather than decoding every song.
        val pool = linkedMapOf<String, Song>()
        fun add(song: Song?) { if (song != null && pool.size < 2000 && !PersonalCollections.excluded(exclusions, song.uri.toString(), now)) pool[song.uri.toString()] = song }
        val favorites = favoriteIds.toHashSet()
        songs.asSequence().filter { it.id in favorites }.take(300).forEach(::add)
        val catalog = songs.associateBy { it.uri.toString() }
        history.take(500).forEach { add(catalog[it.songKey]) }
        if (songs.isNotEmpty()) {
            val random = kotlin.random.Random(seed)
            for (i in 0 until minOf(songs.size * 2L, 6000L).toInt()) { add(songs[random.nextInt(songs.size)]); if (pool.size >= 2000) break }
            if (songs.size <= 2000) songs.forEach(::add)
        }
        val memory = TasteMemory.get(context)
        val likedArtists = memory.weights(TasteKind.ARTIST, now)
        val likedAlbums = memory.weights(TasteKind.ALBUM, now)
        val likedSongs = memory.weights(TasteKind.SONG, now)
        val candidates = pool.values.map { song ->
            RecommendationCandidate(song.uri.toString(), song.artist, song.genre, song.dateAdded,
                song.id in favorites, song.id == currentId, title = song.title,
                entityAffinity = .35 * (likedArtists[TasteMemory.artistKey(song.artist)] ?: 0.0) +
                    .35 * (likedAlbums[TasteMemory.albumKey(song.album, song.albumArtist ?: song.artist)] ?: 0.0) +
                    .3 * (likedSongs[song.uri.toString()] ?: 0.0))
        }
        val preliminary = PersonalRecommendation.rank(candidates, history, profile, now, combined)
        val acousticKeys = preliminary.take(128).map { it.key }.toHashSet()
        val enriched = candidates.map { candidate ->
            if (candidate.key !in acousticKeys || anchors.isEmpty() || profile.audioWeight <= 0) candidate else {
                val acoustic = ledger.feature(candidate.key)?.let { f -> maxOf(anchors.maxOf { SignalAnalysis.similarity(f, it) },
                    tasteVector?.let { AudioVectors.cosine(AudioVectors.describe(f), it) } ?: 0.0) } ?: 0.0
                candidate.copy(acousticSimilarity = acoustic)
            }
        }
        val byKey = songs.associateBy { it.uri.toString() }
        // Limit adjacent artist repetition while retaining the original ranking within each group.
        val scores = PersonalRecommendation.rank(enriched, history, profile, now, combined).map {
            val jitter = ((it.key.hashCode().toLong() xor seed) * 0x5DEECE66DL and 0xffffL) / 65535.0
            it.copy(score = it.score + .06 * jitter)
        }.sortedByDescending { it.score }
        val mix = AdaptiveRecommendation.diversify(scores, enriched, count, profile.exploration)
        reasons = mix.associate { it.key to it.reasons }
        learning.offer(mix, enriched)
        return mix.mapNotNull { byKey[it.key] }
    }
}
