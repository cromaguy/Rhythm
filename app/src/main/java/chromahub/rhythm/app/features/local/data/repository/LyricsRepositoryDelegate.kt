/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.repository

import android.content.Context
import android.util.Log
import chromahub.rhythm.app.shared.data.model.LyricsData
import com.google.gson.Gson
import java.io.File
import java.util.Collections

/**
 * Cohesive in-memory LRU + on-disk JSON lyrics cache coordinator extracted from
 * [MusicRepositoryImpl].
 */
class LyricsRepositoryDelegate(
    private val context: Context,
    maxInMemoryEntries: Int = 150
) {
    companion object {
        private const val TAG = "LyricsRepoDelegate"
        private val SAFE_CHARS = Regex("[^a-zA-Z0-9._-]")

        fun cacheFileName(artist: String, title: String): String =
            "${artist}_${title}.json".replace(SAFE_CHARS, "_")
    }

    private val gson = Gson()
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, LyricsData>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsData>?): Boolean {
                return size > maxInMemoryEntries
            }
        }
    )

    val size: Int
        get() = synchronized(cache) { cache.size }

    fun getCached(key: String): LyricsData? = synchronized(cache) { cache[key] }

    fun putCached(key: String, lyrics: LyricsData) {
        synchronized(cache) { cache[key] = lyrics }
    }

    fun updateForSong(artist: String, title: String, songId: String?, lyrics: LyricsData) {
        synchronized(cache) {
            if (songId != null) {
                cache["$songId:$artist:$title".lowercase()] = lyrics
            }
            cache["$artist:$title".lowercase()] = lyrics
        }
    }

    fun saveToDisk(artist: String, title: String, lyricsData: LyricsData) {
        runCatching {
            val lyricsDir = File(context.filesDir, "lyrics").apply { mkdirs() }
            File(lyricsDir, cacheFileName(artist, title)).writeText(gson.toJson(lyricsData))
        }.onFailure { e ->
            Log.e(TAG, "Error saving lyrics to local file: ${e.message}", e)
        }
    }

    fun clearSong(artist: String, title: String, songId: String?) {
        synchronized(cache) {
            if (songId != null) cache.remove("$songId:$artist:$title".lowercase())
            cache.remove("$artist:$title".lowercase())
        }
        runCatching {
            val file = File(File(context.filesDir, "lyrics"), cacheFileName(artist, title))
            if (file.exists()) file.delete()
        }.onFailure { e ->
            Log.w(TAG, "Error deleting saved lyrics file for $artist - $title: ${e.message}")
        }
    }

    fun clearAll() {
        synchronized(cache) { cache.clear() }
        runCatching {
            val lyricsDir = File(context.filesDir, "lyrics")
            if (lyricsDir.exists() && lyricsDir.isDirectory) {
                lyricsDir.listFiles()?.forEach { it.delete() }
            }
        }.onFailure { e ->
            Log.w(TAG, "Error deleting saved lyrics files: ${e.message}")
        }
    }
}
