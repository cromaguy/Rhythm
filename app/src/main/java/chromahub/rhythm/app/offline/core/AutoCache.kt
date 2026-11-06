// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import java.util.TreeSet

/**
 * Media3's least-recently-used eviction with a cap that can change while the cache is open (the
 * Streaming settings picker: off / 1 / 2 / 5 / 10 GB). `LeastRecentlyUsedCacheEvictor` fixes its
 * cap at construction, and a [androidx.media3.datasource.cache.SimpleCache] a player already
 * holds cannot be swapped, so the cap is read through [maxBytes] on every decision, and [trim]
 * applies a lowered cap at once.
 *
 * Spans are ordered by their last touch, so a track played yesterday goes before one played now.
 */
@UnstableApi
class BudgetedLruEvictor(private val maxBytes: () -> Long) : CacheEvictor {

    private val leastRecentlyUsed = TreeSet<CacheSpan> { a, b ->
        val delta = a.lastTouchTimestamp - b.lastTouchTimestamp
        when {
            delta == 0L -> a.compareTo(b)
            delta < 0L -> -1
            else -> 1
        }
    }
    private var currentSize = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    @Synchronized
    override fun onCacheInitialized() {
        // Spans found on disk arrive through onSpanAdded; the first trim happens on the first write.
    }

    @Synchronized
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    @Synchronized
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evict(cache, 0)
    }

    @Synchronized
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
    }

    @Synchronized
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /**
     * Evicts least-recently-used spans until the cache fits the current cap. Called from outside the
     * cache, so it must not hold this lock while it calls the cache: the cache calls this evictor
     * with its own lock held, and the opposite order is a deadlock.
     */
    fun trim(cache: Cache) {
        while (true) {
            val victim = synchronized(this) { overCap(0L) } ?: return
            cache.removeSpan(victim) // calls onSpanRemoved under the cache's lock
            synchronized(this) {
                // A cache that did not report the removal must still let this loop end.
                if (leastRecentlyUsed.remove(victim)) currentSize -= victim.length
            }
        }
    }

    /** The span to evict next if the cache plus [requiredSpace] exceeds the cap, else null. */
    private fun overCap(requiredSpace: Long): CacheSpan? {
        val cap = maxBytes()
        // A cap of zero is "Off": nothing new is written (see AutoCacheDataSourceFactory) and what
        // was cached before stays playable. Emptying the cache is "Clear cache".
        if (cap <= 0L) return null
        return if (currentSize + requiredSpace > cap) leastRecentlyUsed.firstOrNull() else null
    }

    private fun evict(cache: Cache, requiredSpace: Long) {
        while (true) {
            val victim = overCap(requiredSpace) ?: return
            cache.removeSpan(victim)
            if (leastRecentlyUsed.remove(victim)) currentSize -= victim.length
        }
    }
}

/** What the cache holds, by track. All reads; nothing here opens the network. */
@UnstableApi
object CachedTracks {

    /** True when every byte of [key] is in [cache] (the content length is known and covered). */
    fun isFullyCached(cache: Cache, key: String): Boolean {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length != C.LENGTH_UNSET.toLong() && length > 0 && cache.isCached(key, 0, length)
    }

    /** The fully cached stream of [songId] (any format and bit rate), or null. */
    fun findCompleteStream(cache: Cache, songId: String): OfflineCacheKeys.Parts? {
        val prefix = OfflineCacheKeys.songPrefix(songId)
        return cache.keys
            .asSequence()
            .filter { it.startsWith(prefix) }
            .mapNotNull { OfflineCacheKeys.partsOfKey(it) }
            .filter { isFullyCached(cache, OfflineCacheKeys.keyOf(it)) }
            // Prefer the untranscoded copy, then the higher bit rate.
            .sortedWith(
                compareBy<OfflineCacheKeys.Parts> { it.format != "raw" }
                    .thenByDescending { it.maxBitRate?.toIntOrNull() ?: Int.MAX_VALUE },
            )
            .firstOrNull()
    }

    /** Song ids with a complete cached stream. */
    fun completeSongIds(cache: Cache): Set<String> =
        cache.keys.asSequence()
            .mapNotNull { OfflineCacheKeys.partsOfKey(it) }
            .filter { isFullyCached(cache, OfflineCacheKeys.keyOf(it)) }
            .map { it.songId }
            .toSet()
}

/** The cache key of a load: stable for Subsonic streams, otherwise what Media3 would use. */
@UnstableApi
object StableCacheKeyFactory : CacheKeyFactory {
    override fun buildCacheKey(dataSpec: DataSpec): String =
        OfflineCacheKeys.keyFor(dataSpec.uri.toString()) ?: dataSpec.key ?: dataSpec.uri.toString()
}

/**
 * The auto-cache's data source: reads from [cache] always, and writes to it only while the cap in
 * [limitBytes] is above zero ("off" keeps the cache read-only, so nothing new is stored but what
 * was cached before still plays offline). Stable keys per [StableCacheKeyFactory].
 */
@UnstableApi
class AutoCacheDataSourceFactory(
    private val cache: Cache,
    private val upstream: DataSource.Factory,
    private val limitBytes: () -> Long = { OfflineState.autoCacheLimit.value.bytes },
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        val factory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(StableCacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        if (limitBytes() <= 0L) factory.setCacheWriteDataSinkFactory(null)
        return factory.createDataSource()
    }
}
