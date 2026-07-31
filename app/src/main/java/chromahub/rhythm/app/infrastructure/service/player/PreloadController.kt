package chromahub.rhythm.app.infrastructure.service.player

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class PreloadController {
    companion object {
        private const val TAG = "PreloadController"
    }

    init {
        initialize()
    }

    fun initialize() {
        // A DefaultPreloadManager must share the builder that creates the playback ExoPlayer.
        Log.w(TAG, "Queue preloading is disabled: it must share RhythmPlayerEngine's player builder")
    }

    fun setPlayingIndex(index: Int) {
    }

    fun addOrUpdateQueue(mediaItems: List<MediaItem>) {
    }

    fun remove(mediaItem: MediaItem) {
    }

    fun release() {
    }
}
