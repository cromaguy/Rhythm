// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.metadata

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.MediaUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.co.metadata.crawler.CatalogFileStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Save the winner of Rhythm's existing lyrics source chain, keeping both timing and provenance. */
class AutomaticLyricsStore private constructor(private val context: Context) {
    private val catalog = CatalogEnrichmentService.get(context)
    val root = File(catalog.root, "lyrics")
    private val prefs = context.getSharedPreferences("catalog_lyrics_writes", Context.MODE_PRIVATE)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private fun key(song: Song) = CatalogFileStore.hash(catalog.songKey(song))
    fun read(song: Song): LyricsData? = runCatching {
        val file = File(root, "${key(song)}.data.json")
        if (!file.isFile || file.length() > 2_000_000) null else Gson().fromJson(file.readText(), LyricsData::class.java)
    }.getOrNull()
    suspend fun saveFetched(song: Song, data: LyricsData, embed: Boolean = true) = withContext(Dispatchers.IO) {
        if (!catalog.preferences.options.value.autoLyrics || !data.hasLyrics() || data.isErrorMessage()) return@withContext
        locks.computeIfAbsent(key(song)) { Mutex() }.withLock {
            root.mkdirs()
            write(File(root, "${key(song)}.data.json"), Gson().toJson(data))
            saveFormat(File(root, "${key(song)}.lrc"), data.syncedLyrics)
            saveFormat(File(root, "${key(song)}.txt"), data.plainLyrics)
            saveFormat(File(root, "${key(song)}.json"), data.wordByWordLyrics)
            if (embed && catalog.preferences.options.value.embedLyrics) {
                prefs.edit(commit = true) { putString("pending:${key(song)}", CatalogSongCodec.encode(song)) }
                attemptEmbedding(song, data)
            }
            catalog.notifyChanged()
        }
        chromahub.rhythm.app.infrastructure.worker.CatalogWriteWorker.enqueue(context)
    }
    private fun write(destination: File, text: String) {
        if (destination.isFile && destination.readText() == text) return
        val file = android.util.AtomicFile(destination)
        val stream = file.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    private fun saveFormat(destination: File, text: String?) {
        if (text.isNullOrBlank()) destination.delete() else write(destination, text)
    }
    private fun lyricText(data: LyricsData): String? = data.syncedLyrics?.takeIf(String::isNotBlank)
        ?: data.plainLyrics?.takeIf(String::isNotBlank) ?: data.wordByWordLyrics?.takeIf(String::isNotBlank)
    suspend fun attemptEmbedding(song: Song, data: LyricsData? = read(song)) = withContext(Dispatchers.IO) {
        if (!catalog.preferences.options.value.embedLyrics) return@withContext
        val text = data?.let(::lyricText) ?: return@withContext
        val hash = CatalogFileStore.hash(text)
        if (prefs.getString("embedded:${key(song)}", null) == hash) {
            prefs.edit(commit = true) { remove("pending:${key(song)}") }; return@withContext
        }
        if (catalog.withAudioWrite(song) { PersonalArchive(context).captureSong(song); MediaUtils.embedLyricsAutomatically(context, song, text) }) {
            prefs.edit(commit = true) { putString("embedded:${key(song)}", hash); remove("pending:${key(song)}") }
        }
    }
    fun pending(): List<Song> = prefs.all.filterKeys { it.startsWith("pending:") }.values.mapNotNull {
        (it as? String)?.let { encoded -> runCatching { CatalogSongCodec.decode(encoded) }.getOrNull() }
    }
    companion object {
        @Volatile private var instance: AutomaticLyricsStore? = null
        fun get(context: Context): AutomaticLyricsStore = instance ?: synchronized(this) {
            instance ?: AutomaticLyricsStore(context.applicationContext).also { instance = it }
        }
    }
}
