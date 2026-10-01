/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player.replaygain

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory cache for track ReplayGain tags.
 * Allows zero-latency instant retrieval of ReplayGainInfo by mediaId across player swaps,
 * gapless transitions, and queue preloading to eliminate volume jump scares.
 */
object ReplayGainCache {
    private val cache = ConcurrentHashMap<String, ReplayGainUtil.ReplayGainInfo>()

    fun get(mediaId: String?): ReplayGainUtil.ReplayGainInfo? {
        if (mediaId.isNullOrEmpty()) return null
        return cache[mediaId]
    }

    fun put(mediaId: String?, info: ReplayGainUtil.ReplayGainInfo?) {
        if (mediaId.isNullOrEmpty() || info == null) return
        cache[mediaId] = info
    }

    fun remove(mediaId: String?) {
        if (mediaId.isNullOrEmpty()) return
        cache.remove(mediaId)
    }

    fun clear() {
        cache.clear()
    }
}
