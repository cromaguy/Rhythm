package chromahub.rhythm.app.infrastructure.service.player.autoloop

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource

/** Called only when installing/removing the temporary Automatic Loop clip. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AutoLoopQueueInstaller(private val factory: MediaSource.Factory) {
    fun install(player: ExoPlayer, items: List<MediaItem>, index: Int, positionMs: Long) {
        val sources = items.mapIndexed { i, item ->
            val window = if (i == index) AutoLoopState.windowFor(item) else null
            // Keep upcoming entries gated, but bypass the gate for the already-analyzed
            // current clip. Gating that clip again would wait for analysis indefinitely.
            if (window == null) AutoLoopPreparationMediaSource(factory.createMediaSource(item)) else {
                val base = factory.createMediaSource(item.buildUpon()
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.UNSET).build())
                val pcm = AutoLoopPcmMediaSource(base, item, 0L, window.endMs * 1000, window.startMs * 1000)
                androidx.media3.exoplayer.source.ClippingMediaSource.Builder(pcm)
                    .setClippingConfiguration(item.clippingConfiguration)
                    .setEnableClippingInMediaPeriod(true).build()
            }
        }
        player.setMediaSources(sources, index, positionMs)
    }
}
