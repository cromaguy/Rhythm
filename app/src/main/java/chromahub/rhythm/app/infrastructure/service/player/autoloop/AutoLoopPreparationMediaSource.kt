package chromahub.rhythm.app.infrastructure.service.player.autoloop

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.MaskingMediaPeriod
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.upstream.Allocator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps a newly selected track buffering until analysis has configured its replacement loop
 * source, or explicitly allowed ordinary playback. playWhenReady remains the user's intent.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AutoLoopPreparationMediaSource(
    source: MediaSource, private val preparationDispatcher: CoroutineDispatcher? = null
) : WrappingMediaSource(source) {
    private val pending = java.util.IdentityHashMap<MediaPeriod, Job>()
    private var waitingScope: CoroutineScope? = null

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod {
        if (!AutoLoopPreparation.shouldWait(mediaItem)) return super.createPeriod(id, allocator, startPositionUs)
        val period = MaskingMediaPeriod(id, allocator, startPositionUs).apply { setMediaSource(mediaSource) }
        // MediaPeriod creation and release must stay on this source's playback looper.
        // Only the readiness signal crosses over from the controller's main-thread state.
        val scope = waitingScope ?: CoroutineScope(
            preparationDispatcher ?: Handler(checkNotNull(Looper.myLooper())).asCoroutineDispatcher("AutoLoopPreparation")
        ).also { waitingScope = it }
        pending[period] = scope.launch {
            AutoLoopPreparation.awaitPlayback(mediaItem)
            period.createPeriod(id)
        }
        return period
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        val job = pending.remove(mediaPeriod)
        if (job != null) {
            // A late analysis result must not prepare a period that a skip already released.
            job.cancel()
            (mediaPeriod as MaskingMediaPeriod).releasePeriod()
        } else super.releasePeriod(mediaPeriod)
    }

    override fun releaseSourceInternal() {
        waitingScope?.cancel()
        waitingScope = null
        pending.clear()
        super.releaseSourceInternal()
    }

    class Factory(private val delegate: MediaSource.Factory) : MediaSource.Factory by delegate {
        override fun createMediaSource(mediaItem: MediaItem): MediaSource =
            AutoLoopPreparationMediaSource(delegate.createMediaSource(mediaItem))
    }
}

internal object AutoLoopPreparation {
    fun key(item: MediaItem?): String? = item?.let { it.mediaId + "|" + it.localConfiguration?.uri }

    fun shouldWait(item: MediaItem): Boolean =
        AutoLoopState.enabled.value && AutoLoopState.windowFor(item) == null &&
            AutoLoopState.analysisFallbackKey.value != key(item)

    // A successful analysis replaces this source with a configured loop source. Do not
    // open the original source on success: that would briefly play it before replacement.
    suspend fun awaitPlayback(item: MediaItem) {
        AutoLoopState.enabled.combine(AutoLoopState.analysisFallbackKey) { enabled, fallback ->
            !enabled || fallback == key(item)
        }.first { it }
    }
}
