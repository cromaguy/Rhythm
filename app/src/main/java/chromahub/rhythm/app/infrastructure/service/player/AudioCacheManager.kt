/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Standard singleton cache manager for Media3 ExoPlayer caching.
 * Caches streamed music tracks locally to reduce latency, save bandwidth, and prevent buffering.
 */
@OptIn(UnstableApi::class)
object AudioCacheManager {
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "audio_cache")
            val maxCacheSize = 150 * 1024 * 1024L // 150MB Cache Size
            val evictor = LeastRecentlyUsedCacheEvictor(maxCacheSize)
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return simpleCache!!
    }

    @Synchronized
    fun evict(key: String) {
        try {
            simpleCache?.removeResource(key)
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun evict(uri: android.net.Uri) {
        evict(uri.toString())
    }
}
