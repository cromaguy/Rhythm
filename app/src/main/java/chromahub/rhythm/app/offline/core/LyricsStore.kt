// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import java.io.File
import org.json.JSONObject

/** The lyrics of one song as the server gave them. Any of the three texts may be missing; at least one is not blank. */
data class StoredLyrics(
    val plain: String?,
    val synced: String?,
    /** Rhythm's word-by-word JSON, for providers that have it; null for a Subsonic server. */
    val wordByWord: String? = null,
    /** Where they came from ("Navidrome (Subsonic)"), shown on the lyrics screen. */
    val source: String? = null,
) {
    val hasLyrics: Boolean get() = !plain.isNullOrBlank() || !synced.isNullOrBlank() || !wordByWord.isNullOrBlank()
}

/** What is on the device about one song's lyrics. A song with no record has not been asked about. */
sealed interface LyricsRecord {
    /** The server had lyrics; they are stored. */
    data class Found(val lyrics: StoredLyrics, val savedAt: Long) : LyricsRecord

    /** The server was asked and has none: not a failure, and not asked again until [LyricsStore.NONE_RECHECK_MS] has passed. */
    data class None(val checkedAt: Long) : LyricsRecord
}

/**
 * Offline cache: the lyrics that go with a downloaded song, one small JSON file per song in the downloads directory,
 * next to the audio and named by the same rule (`SUBSONIC__abc.lyrics.json`, from the song id, see [OfflineFiles.safeName]).
 * Lyrics belong to the song, not to a file: every copy of a song shares the one record.
 *
 * "The server has no lyrics" is a record too ([LyricsRecord.None]), so a song without lyrics is asked about once, not at
 * every sync. A failed ask (no network, a server error) writes nothing and is tried again.
 *
 * Writes are atomic (temp file, rename); a file that cannot be read is treated as missing.
 */
class LyricsStore(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {

    fun fileFor(songId: String): File = File(dir, OfflineFiles.safeName(songId) + SUFFIX)

    /** The record of [songId], or null when it was never asked about (or the file is unreadable). */
    fun read(songId: String): LyricsRecord? {
        val file = fileFor(songId)
        if (!file.isFile) return null
        return try {
            val j = JSONObject(file.readText())
            when (j.optString("status")) {
                STATUS_FOUND -> {
                    val lyrics = StoredLyrics(
                        plain = j.text("plain"),
                        synced = j.text("synced"),
                        wordByWord = j.text("wordByWord"),
                        source = j.text("source"),
                    )
                    if (lyrics.hasLyrics) LyricsRecord.Found(lyrics, j.optLong("at")) else null
                }
                STATUS_NONE -> LyricsRecord.None(j.optLong("at"))
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** The stored lyrics of [songId], or null (none stored, or the server has none). */
    fun lyrics(songId: String): StoredLyrics? = (read(songId) as? LyricsRecord.Found)?.lyrics

    fun putFound(songId: String, lyrics: StoredLyrics): Boolean {
        if (!lyrics.hasLyrics) return false
        return write(
            songId,
            JSONObject().put("v", 1).put("songId", songId).put("status", STATUS_FOUND).put("at", clock())
                .put("plain", lyrics.plain ?: JSONObject.NULL).put("synced", lyrics.synced ?: JSONObject.NULL)
                .put("wordByWord", lyrics.wordByWord ?: JSONObject.NULL).put("source", lyrics.source ?: JSONObject.NULL),
        )
    }

    fun putNone(songId: String): Boolean =
        write(songId, JSONObject().put("v", 1).put("songId", songId).put("status", STATUS_NONE).put("at", clock()))

    /** True when [songId] has no record, or only a "no lyrics" one old enough to ask again. */
    fun needsFetch(songId: String, now: Long = clock()): Boolean = when (val r = read(songId)) {
        null -> true
        is LyricsRecord.Found -> false
        is LyricsRecord.None -> now - r.checkedAt >= NONE_RECHECK_MS || r.checkedAt > now
    }

    /** Deletes the record of [songId]. True if there was one. */
    fun delete(songId: String): Boolean {
        val file = fileFor(songId)
        val had = file.exists()
        file.delete()
        File(dir, file.name + ".tmp").delete()
        return had
    }

    /**
     * Deletes the records of songs that are not in [knownSongIds] (the ledger): what a removal that was cut short, or a
     * cancelled download, leaves behind. Returns how many went. Never touches a song the ledger still holds, whatever
     * its pins say - lyrics leave with the download, not with a pin reason.
     */
    fun sweepOrphans(knownSongIds: Collection<String>): Int {
        val keep = knownSongIds.mapTo(HashSet(), OfflineFiles::safeName)
        var n = 0
        for (f in dir.listFiles().orEmpty()) {
            val name = f.name
            if (name.endsWith(SUFFIX + ".tmp")) {
                f.delete()
            } else if (name.endsWith(SUFFIX) && name.removeSuffix(SUFFIX) !in keep) {
                if (f.delete()) n++
            }
        }
        return n
    }

    private fun write(songId: String, json: JSONObject): Boolean = try {
        dir.mkdirs()
        val file = fileFor(songId)
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        file.isFile
    } catch (_: Exception) {
        false
    }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        const val SUFFIX = ".lyrics.json"
        private const val STATUS_FOUND = "found"
        private const val STATUS_NONE = "none"

        /** A song the server had no lyrics for is asked about again after this long (a server may gain them). */
        const val NONE_RECHECK_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/** What asking a server for one song's lyrics came to. */
sealed interface LyricsFetch {
    data class Found(val lyrics: StoredLyrics) : LyricsFetch

    /** The server answered and has no lyrics for the song. */
    data object None : LyricsFetch

    /** The ask did not get an answer (no network, offline mode, a server error, a login problem): try again later. */
    data class Failed(val reason: String) : LyricsFetch
}

/** What [LyricsSync.ensure] did. */
enum class LyricsOutcome { HAD_IT, STORED, NO_LYRICS, FAILED }

/**
 * Fetches and stores the lyrics of one song. It never throws (but a cancellation), and a song without lyrics is not a
 * failure: it records "none". A failed ask records nothing, so the backfill (or the next download pass) tries again.
 */
object LyricsSync {

    suspend fun ensure(store: LyricsStore, songId: String, fetch: suspend () -> LyricsFetch): LyricsOutcome {
        if (!store.needsFetch(songId)) return LyricsOutcome.HAD_IT
        val result = try {
            fetch()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LyricsFetch.Failed(e.message ?: e.javaClass.simpleName)
        }
        return when (result) {
            is LyricsFetch.Found -> if (store.putFound(songId, result.lyrics)) LyricsOutcome.STORED else LyricsOutcome.FAILED
            LyricsFetch.None -> if (store.putNone(songId)) LyricsOutcome.NO_LYRICS else LyricsOutcome.FAILED
            is LyricsFetch.Failed -> LyricsOutcome.FAILED
        }
    }
}
