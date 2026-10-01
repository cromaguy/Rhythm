/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches Deezer artist image lookups with configurable TTL to avoid redundant network requests.
 */
class DeezerArtistImageCache(
    private val file: File?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val hitTtlMs: Long = HIT_TTL_MS,
    private val missTtlMs: Long = MISS_TTL_MS,
    private val failureTtlMs: Long = FAILURE_TTL_MS,
) {
    /** A cached lookup; [imageUrl] is null when Deezer had no image for the artist. */
    data class Entry(
        @SerializedName("imageUrl") val imageUrl: String?,
        @SerializedName("fetchedAtMs") val fetchedAtMs: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()
    private val failures = ConcurrentHashMap<String, Long>()
    private val gson = Gson()

    @Volatile
    private var loaded = false

    @Volatile
    private var dirty = false

    /** Returns a fresh cached entry, or null if the artist should be looked up. */
    fun get(key: String): Entry? {
        ensureLoaded()
        val now = clock()
        failures[key]?.let { failedAt ->
            if (now - failedAt < failureTtlMs) return Entry(null, failedAt)
            failures.remove(key)
        }
        val entry = entries[key] ?: return null
        val ttl = if (entry.imageUrl != null) hitTtlMs else missTtlMs
        return entry.takeIf { now - it.fetchedAtMs < ttl }
    }

    fun put(key: String, imageUrl: String?) {
        ensureLoaded()
        failures.remove(key)
        entries[key] = Entry(imageUrl, clock())
        dirty = true
    }

    fun markFailed(key: String) {
        failures[key] = clock()
    }

    /** Writes pending entries to disk. Call off the main thread. */
    @Synchronized
    fun flush() {
        if (!dirty || file == null) return
        try {
            val now = clock()
            val live = entries.filterValues { entry ->
                now - entry.fetchedAtMs < if (entry.imageUrl != null) hitTtlMs else missTtlMs
            }
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(gson.toJson(live))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
            dirty = false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist Deezer artist image cache: ${e.message}")
        }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (file == null || !file.exists()) return
        try {
            val type = object : TypeToken<Map<String, Entry>>() {}.type
            val stored: Map<String, Entry>? = gson.fromJson(file.readText(), type)
            stored?.forEach { (key, entry) -> entries.putIfAbsent(key, entry) }
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring unreadable Deezer artist image cache: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "DeezerArtistImageCache"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        const val HIT_TTL_MS = 30 * DAY_MS
        const val MISS_TTL_MS = 7 * DAY_MS
        const val FAILURE_TTL_MS = 60L * 60 * 1000
    }
}
