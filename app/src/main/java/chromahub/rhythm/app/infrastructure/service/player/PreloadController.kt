/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.DataSpec
import chromahub.rhythm.app.shared.data.model.AppSettings
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import androidx.core.net.toUri

@OptIn(UnstableApi::class)
class PreloadController(
    private val context: Context,
    private val appSettings: AppSettings
) {
    companion object {
        private const val TAG = "PreloadController"
    }

    private var preloadManager: DefaultPreloadManager? = null
    private val targetPreloadStatusControl = PlaylistTargetPreloadStatusControl()

    init {
        initialize()
    }

    fun initialize() {
        try {
            val limit = appSettings.preloadLimit.value
            targetPreloadStatusControl.preloadLimit = limit

            val resolvingDataSourceFactory = ResolvingDataSource.Factory(
                DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory()),
                object : ResolvingDataSource.Resolver {
                    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {

                        return dataSpec
                    }
                }
            )

            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(resolvingDataSourceFactory)

            @Suppress("DEPRECATION")
            val builder = DefaultPreloadManager.Builder(context, targetPreloadStatusControl as TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus>)
                .setMediaSourceFactory(mediaSourceFactory)

            preloadManager = builder.build()
            Log.d(TAG, "PreloadController: DefaultPreloadManager initialized with limit: $limit")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DefaultPreloadManager, using fallback precaching", e)
        }
    }

    fun setPlayingIndex(index: Int) {
        targetPreloadStatusControl.currentPlayingIndex = index
        preloadManager?.setCurrentPlayingIndex(index)
        preloadManager?.invalidate()
    }

    fun addOrUpdateQueue(mediaItems: List<MediaItem>) {
        val manager = preloadManager ?: return
        
        // Add items to preload manager with their list index as ranking data
        mediaItems.forEachIndexed { index, mediaItem ->
            manager.add(mediaItem, index)
        }
        manager.invalidate()
    }

    fun remove(mediaItem: MediaItem) {
        preloadManager?.remove(mediaItem)
    }

    fun release() {
        preloadManager?.release()
        preloadManager = null
    }

    private class PlaylistTargetPreloadStatusControl : TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {
        var currentPlayingIndex: Int = C.INDEX_UNSET
        var preloadLimit: Int = 3

        override fun getTargetPreloadStatus(rankingData: Int): DefaultPreloadManager.PreloadStatus {
            if (currentPlayingIndex == C.INDEX_UNSET) {
                return DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
            }
            
            val distance = rankingData - currentPlayingIndex
            return when {
                // Preload the next few songs
                distance in 1..preloadLimit -> {
                    // Stage: source prepared is excellent for warm caching and rapid starts
                    DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_SOURCE_PREPARED
                }
                // Everything else: still return a valid status but at minimum level
                else -> DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
            }
        }
    }
}
