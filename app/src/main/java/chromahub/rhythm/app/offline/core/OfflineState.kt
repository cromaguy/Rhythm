// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val GIB_BYTES = 1024L * 1024L * 1024L

/**
 * The size cap of the auto-cache of played tracks (offline cache feature, item 2): off, or a
 * least-recently-used budget of 1, 2, 5 or 10 GB. The values are what the Streaming settings
 * picker offers; [id] is what is persisted.
 */
enum class AutoCacheLimit(val id: String, val bytes: Long) {
    OFF("off", 0L),
    GB1("1gb", 1L * GIB_BYTES),
    GB2("2gb", 2L * GIB_BYTES),
    GB5("5gb", 5L * GIB_BYTES),
    GB10("10gb", 10L * GIB_BYTES),
    ;

    val enabled: Boolean get() = bytes > 0L

    companion object {
        /** What a fresh install gets: upstream already cached (150 MB), so caching stays on. */
        val DEFAULT = GB1

        fun fromId(id: String?): AutoCacheLimit = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Process-wide offline switches, set by `:app`:
 * the user's offline-mode setting, a network probe and the auto-cache cap. The player's
 * resolvers read them on the loading thread, never on the audio thread.
 */
object OfflineState {
    @Volatile
    var offlineMode: Boolean = false

    /** True when the device has a usable network. Replaced by `:app` with a ConnectivityManager check. */
    @Volatile
    var networkAvailable: () -> Boolean = { true }

    private val autoCacheFlow = MutableStateFlow(AutoCacheLimit.DEFAULT)

    /** The auto-cache cap; changing it takes effect for the next load and trims the cache. */
    val autoCacheLimit: StateFlow<AutoCacheLimit> = autoCacheFlow.asStateFlow()

    fun setAutoCacheLimit(limit: AutoCacheLimit) {
        autoCacheFlow.value = limit
    }

    private val downloadQualityFlow = MutableStateFlow(DownloadQuality.DEFAULT)

    /**
     * What new downloads are fetched as ("Download quality"). Songs already downloaded keep the format they
     * have.
     */
    val downloadQuality: StateFlow<DownloadQuality> = downloadQualityFlow.asStateFlow()

    fun setDownloadQuality(quality: DownloadQuality) {
        downloadQualityFlow.value = quality
    }

    /** Offline mode is on, or there is no network: only downloaded and cached tracks may play. */
    fun isOffline(): Boolean = offlineMode || !networkAvailable()

    internal fun resetForTest() {
        offlineMode = false
        networkAvailable = { true }
        autoCacheFlow.value = AutoCacheLimit.DEFAULT
        downloadQualityFlow.value = DownloadQuality.DEFAULT
    }
}
