package chromahub.rhythm.app.infrastructure.service.player.autoloop

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import chromahub.rhythm.app.infrastructure.service.player.RhythmShuffleOrder

/** Rebuilds the source graph atomically; replaceMediaItem may reuse an unclipped source. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class NativeAutoLoopSession(
    val player: ExoPlayer,
    val bounds: Pair<Long, Long>,
    private val installQueue: ((List<MediaItem>, Int, Long) -> Unit)? = null
) {
    private val original = requireNotNull(player.currentMediaItem)
    private val previousRepeat = player.repeatMode
    private val previousPauseAtEnd = player.pauseAtEndOfMediaItems
    private val window = AutoLoopWindow(original.mediaId, bounds.first, bounds.second,
        maxOf(bounds.second, player.duration, original.mediaMetadata.durationMs ?: 0L))
    private val clip = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(0L) // Keep the intro in the same source as the first loop pass.
        // The physical clip starts at file position zero. Our renderers handle repeat
        // preroll, so a clipping discontinuity must not tear down gapless audio output.
        .setStartsAtKeyFrame(true)
        .setEndPositionMs(bounds.second)
        .build()
    private var closed = false

    init {
        val position = window.clipPosition(player.currentPosition)
        val index = player.currentMediaItemIndex
        val queue = (0 until player.mediaItemCount).map { i ->
            player.getMediaItemAt(i).let {
                if (i == index) it.buildUpon().setClippingConfiguration(clip).build() else it
            }
        }
        AutoLoopState.windowState.value = window
        player.pauseAtEndOfMediaItems = false
        player.repeatMode = Player.REPEAT_MODE_ONE
        rebuildQueue(queue, index, position)
    }

    private fun rebuildQueue(items: List<MediaItem>, index: Int, position: Long) {
        // setMediaItems always invokes the engine's MediaSource.Factory, ensuring the
        // ClippingMediaSource is created (or removed), unlike same-URI replacement.
        val shuffle = if (player.shuffleModeEnabled) {
            val timeline = player.currentTimeline
            val order = mutableListOf<Int>()
            var i = timeline.getFirstWindowIndex(true)
            while (i != C.INDEX_UNSET && i !in order && order.size < items.size) {
                order += i
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
            }
            order.takeIf { it.size == items.size }?.toIntArray()
        } else null
        if (installQueue != null) installQueue.invoke(items, index, position)
        else player.setMediaItems(items, index, position)
        if (shuffle != null) player.setShuffleOrder(RhythmShuffleOrder(shuffle))
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED)
            player.prepare()
    }

    private fun matches(item: MediaItem?) = item?.mediaId == original.mediaId &&
        item.localConfiguration?.uri == original.localConfiguration?.uri &&
        item.clippingConfiguration == clip

    fun isCurrent() = !closed && matches(player.currentMediaItem)

    // Match Player.seekToPrevious(): restart the intro later in the track, but let a
    // second press near the beginning reach the previous queue item through the service.
    fun restartFromPreviousCommand(): Boolean {
        if (!isCurrent()) return false
        if (player.hasPreviousMediaItem() && player.currentPosition <= player.maxSeekToPreviousPosition) return false
        return restartFromBeginning()
    }

    fun restartFromBeginning(): Boolean {
        if (!isCurrent()) return false
        // The intro remains in this source, so Previous needs no source replacement.
        player.seekTo(player.currentMediaItemIndex, 0L)
        return true
    }

    fun close(restartPositionMs: Long? = null) {
        if (closed) return
        closed = true
        val currentIndex = player.currentMediaItemIndex
        val restoreCurrent = matches(player.currentMediaItem)
        val position = if (restoreCurrent) restartPositionMs ?: player.currentPosition else player.currentPosition
        var changed = false
        val queue = (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            if (matches(item)) {
                changed = true
                item.buildUpon().setClippingConfiguration(original.clippingConfiguration).build()
            } else item
        }
        if (changed) rebuildQueue(queue, currentIndex, position)
        AutoLoopState.windowState.value = null
        if (player.repeatMode == Player.REPEAT_MODE_ONE) player.repeatMode = previousRepeat
        player.pauseAtEndOfMediaItems = previousPauseAtEnd
    }
}
