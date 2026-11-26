/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.os.StatFs
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import java.io.File

/**
 * Computes safe disk-cache budgets dynamically against remaining storage headroom,
 * preventing low-storage devices from hitting `ENOSPC` during cloud caching or preloading.
 */
object CacheStoragePolicy {

    const val DEFAULT_MAX_CACHE_BYTES: Long = 100L * 1024L * 1024L // 100 MB
    const val MIN_FLOOR_CACHE_BYTES: Long = 32L * 1024L * 1024L    // 32 MB
    const val LOW_STORAGE_THRESHOLD_BYTES: Long = 2L * 1024L * 1024L * 1024L // 2 GB free

    fun computeMaxCacheBytes(
        usableSpaceBytes: Long,
        configuredMaxBytes: Long = DEFAULT_MAX_CACHE_BYTES
    ): Long {
        if (usableSpaceBytes <= 0L) return MIN_FLOOR_CACHE_BYTES
        if (usableSpaceBytes >= LOW_STORAGE_THRESHOLD_BYTES) {
            return configuredMaxBytes.coerceAtLeast(MIN_FLOOR_CACHE_BYTES)
        }
        val fivePercentBudget = usableSpaceBytes / 20L
        return minOf(configuredMaxBytes, fivePercentBudget).coerceAtLeast(
            minOf(MIN_FLOOR_CACHE_BYTES, usableSpaceBytes / 4L).coerceAtLeast(4L * 1024L * 1024L)
        )
    }
}

/**
 * Storage-aware ExoPlayer [CacheEvictor] wrapping [LeastRecentlyUsedCacheEvictor] and re-evaluating
 * device free headroom whenever new media segments are written.
 */
@UnstableApi
class AdaptiveCacheEvictor(
    private val cacheDir: File,
    private val configuredMaxBytes: Long = CacheStoragePolicy.DEFAULT_MAX_CACHE_BYTES
) : CacheEvictor {

    private fun currentLimitBytes(): Long {
        val usable = runCatching { StatFs(cacheDir.absolutePath).availableBytes }.getOrDefault(CacheStoragePolicy.LOW_STORAGE_THRESHOLD_BYTES)
        return CacheStoragePolicy.computeMaxCacheBytes(usable, configuredMaxBytes)
    }

    @Volatile
    private var delegate = LeastRecentlyUsedCacheEvictor(currentLimitBytes())

    override fun requiresCacheSpanTouches(): Boolean = delegate.requiresCacheSpanTouches()

    override fun onCacheInitialized() {
        delegate.onCacheInitialized()
    }

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        val adaptiveLimit = currentLimitBytes()
        if (length > adaptiveLimit && adaptiveLimit < configuredMaxBytes) {
            delegate = LeastRecentlyUsedCacheEvictor(adaptiveLimit)
        }
        delegate.onStartFile(cache, key, position, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        delegate.onSpanAdded(cache, span)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        delegate.onSpanRemoved(cache, span)
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        delegate.onSpanTouched(cache, oldSpan, newSpan)
    }
}
