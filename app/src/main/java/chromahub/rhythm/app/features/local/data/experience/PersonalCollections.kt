// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import android.content.Context
import androidx.annotation.Keep
import chromahub.rhythm.app.features.local.data.metadata.PersonalArchive
import chromahub.rhythm.app.shared.data.model.Song
import com.google.gson.Gson
import java.io.File

@Keep enum class PersonalCollection { DAYLIST, DAILY_MIX, REDISCOVERY, SIMILAR, FAVORITES, FEWER_REPEATS }
@Keep data class PersonalOptions(val snoozed: Map<String, Long> = emptyMap(), val hidden: Set<String> = emptySet(), val autoplay: Boolean = false, val labels: Map<String, String> = emptyMap())

object PersonalCollections {
    private val gson = Gson()
    private fun file(context: Context) = File(context.filesDir, "music_metadata/personalization/options.json")
    private var cached: Pair<String, PersonalOptions>? = null
    @Synchronized fun options(context: Context): PersonalOptions {
        val source = file(context); val signature = "${source.path}:${source.lastModified()}:${source.length()}"
        cached?.takeIf { it.first == signature }?.let { return it.second }
        val value = runCatching { gson.fromJson(source.readText(), PersonalOptions::class.java) }.getOrNull() ?: PersonalOptions()
        cached = signature to value; return value
    }
    fun autoplay(context: Context) = options(context).autoplay
    @Synchronized fun update(context: Context, change: (PersonalOptions) -> PersonalOptions) {
        val next = change(options(context))
        PersonalArchive.atomicWrite(file(context), gson.toJson(next).toByteArray())
        cached = null
        ListeningLedger.get(context).invalidate(); PersonalArchive.enqueue(context)
    }
    fun excluded(options: PersonalOptions, key: String, now: Long) = key in options.hidden || (options.snoozed[key] ?: 0L) > now
    fun snooze(context: Context, key: String, title: String = key) = update(context) { it.copy(snoozed = it.snoozed + (key to System.currentTimeMillis() + 30 * 86_400_000L), labels = it.labels + (key to title)) }
    fun hide(context: Context, key: String, title: String = key) = update(context) { it.copy(hidden = it.hidden + key, labels = it.labels + (key to title)) }
    fun restore(context: Context, key: String) = update(context) { it.copy(hidden = it.hidden - key, snoozed = it.snoozed - key) }

    /** Existing playlists and their ordering are never mutated by generating a collection. */
    fun generate(context: Context, songs: List<Song>, favorites: List<String>, currentId: String?, mode: PersonalCollection,
                 count: Int = 30, genre: String? = null, seed: Long = System.currentTimeMillis() / 86_400_000L): List<Song> {
        val now = System.currentTimeMillis(); val ledger = ListeningLedger.get(context)
        val options = options(context); val history = ledger.affinities().associateBy { it.songKey }
        val eligible = songs.filterNot { excluded(options, it.uri.toString(), now) }.filter { song -> when(mode) {
            PersonalCollection.FAVORITES -> song.id in favorites && (genre == null || AdaptiveRecommendation.normalizeGenre(song.genre) == AdaptiveRecommendation.normalizeGenre(genre))
            PersonalCollection.REDISCOVERY -> history[song.uri.toString()]?.lastAt?.let { now - it >= 14 * 86_400_000L } == true
            PersonalCollection.DAILY_MIX -> genre == null || AdaptiveRecommendation.normalizeGenre(song.genre) == AdaptiveRecommendation.normalizeGenre(genre)
            else -> true
        } }
        val ranked = PersonalTasteRepository.recommend(context, eligible, favorites, if (mode == PersonalCollection.FAVORITES) null else currentId, minOf(if (mode == PersonalCollection.SIMILAR) 128 else 500, eligible.size), seed)
        val selected = when (mode) {
            PersonalCollection.DAYLIST -> ranked
            PersonalCollection.DAILY_MIX -> {
                val group = genre ?: ranked.groupingBy { AdaptiveRecommendation.normalizeGenre(it.genre) }.eachCount()
                    .filterKeys { it.isNotBlank() }.maxByOrNull { it.value }?.key
                ranked.filter { group == null || AdaptiveRecommendation.normalizeGenre(it.genre) == AdaptiveRecommendation.normalizeGenre(group) }
            }
            PersonalCollection.REDISCOVERY -> ranked.filter { history[it.uri.toString()]?.lastAt?.let { last -> now - last >= 14 * 86_400_000L } == true }
            PersonalCollection.FAVORITES -> ranked.filter { it.id in favorites && (genre == null || AdaptiveRecommendation.normalizeGenre(it.genre) == AdaptiveRecommendation.normalizeGenre(genre)) }
            PersonalCollection.FEWER_REPEATS -> ranked.sortedWith(compareBy<Song> { (history[it.uri.toString()]?.lastAt ?: 0L) > now - 24 * 3600_000L }
                .thenBy { history[it.uri.toString()]?.lastAt ?: 0L })
            PersonalCollection.SIMILAR -> {
                val anchor = songs.firstOrNull { it.id == currentId }; val feature = anchor?.let { ledger.feature(it.uri.toString()) }
                ranked.sortedByDescending { song ->
                    val acoustic = SignalAnalysis.similarity(feature, ledger.feature(song.uri.toString()))
                    acoustic + (if (song.artist.equals(anchor?.artist, true)) .12 else 0.0) +
                        (if (!anchor?.genre.isNullOrBlank() && song.genre.equals(anchor?.genre, true)) .1 else 0.0)
                }
            }
        }
        return selected.take(count.coerceIn(1, 500))
    }
    /** Insert new recommendations without shuffling the user's original relative order. */
    fun smartInsert(queue: List<Song>, recommendations: List<Song>): List<Song> {
        val existing = queue.map { it.uri.toString() }.toSet()
        val additions = recommendations.distinctBy { it.uri.toString() }.filterNot { it.uri.toString() in existing }.iterator()
        return buildList { queue.forEachIndexed { i, song -> add(song); if ((i + 1) % 3 == 0 && additions.hasNext()) add(additions.next()) } }
    }
    fun reorder(songs: List<Song>, features: Map<String, AudioFeatures>): List<Song> {
        if (songs.size < 2) return songs
        val remaining = songs.drop(1).toMutableList(); val result = mutableListOf(songs.first())
        while (remaining.isNotEmpty()) {
            val before = features[result.last().uri.toString()]
            val next = if (before?.estimatedBpm == null) remaining.first() else remaining.minBy { song ->
                val candidate = features[song.uri.toString()]
                val bpm = candidate?.estimatedBpm ?: return@minBy 2.0
                val ratio = kotlin.math.abs(kotlin.math.ln(bpm / before.estimatedBpm))
                ratio + if (before.estimatedKey != null && before.estimatedKey == candidate.estimatedKey) 0.0 else .12
            }
            result += next; remaining.remove(next)
        }
        return result
    }
}
