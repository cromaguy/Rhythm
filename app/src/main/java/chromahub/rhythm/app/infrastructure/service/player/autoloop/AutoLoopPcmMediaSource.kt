package chromahub.rhythm.app.infrastructure.service.player.autoloop

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Util
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import java.nio.ByteBuffer

/**
 * Used only for the selected Automatic Loop source. The engine's normal source factory,
 * other queue entries and compressed audio packets are unchanged.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AutoLoopPcmMediaSource(
    source: MediaSource, private var item: androidx.media3.common.MediaItem,
    private val startUs: Long, private val endUs: Long, private val repeatPositionUs: Long
) : WrappingMediaSource(source) {
    override fun getMediaItem(): androidx.media3.common.MediaItem = item

    // Publish the repeat default before preparation. Otherwise Media3 treats an explicit
    // initial seek to zero as the old placeholder default and moves it to the loop start.
    override fun getInitialTimeline(): androidx.media3.common.Timeline = withLoopDefault(
        androidx.media3.exoplayer.source.SinglePeriodTimeline(endUs, true, false, false, null, item)
    )

    private fun withLoopDefault(timeline: androidx.media3.common.Timeline): androidx.media3.common.Timeline =
        object : androidx.media3.exoplayer.source.ForwardingTimeline(timeline) {
            override fun getWindow(index: Int, window: androidx.media3.common.Timeline.Window,
                defaultPositionProjectionUs: Long): androidx.media3.common.Timeline.Window =
                super.getWindow(index, window, defaultPositionProjectionUs).also {
                    it.mediaItem = item
                    it.defaultPositionUs = repeatPositionUs
                }
        }

    override fun onChildSourceInfoRefreshed(newTimeline: androidx.media3.common.Timeline) {
        refreshSourceInfo(withLoopDefault(newTimeline))
    }

    override fun updateMediaItem(mediaItem: androidx.media3.common.MediaItem) {
        item = mediaItem
        super.updateMediaItem(mediaItem.buildUpon()
            .setClippingConfiguration(androidx.media3.common.MediaItem.ClippingConfiguration.UNSET).build())
    }
    override fun createPeriod(
        id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long
    ): MediaPeriod = PcmLoopMediaPeriod(super.createPeriod(id, allocator, startPositionUs),
        startUs, maxOf(startUs, startPositionUs), endUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        super.releasePeriod((mediaPeriod as PcmLoopMediaPeriod).child)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class PcmLoopMediaPeriod(
    val child: MediaPeriod, private val clipStartUs: Long,
    private var startUs: Long, private val endUs: Long
) : MediaPeriod by child {
    private var preciseSelection = false
    private var compressedSelection = false
    private var softwareSelection = false
    private var prerollUs = 250_000L
    private var streamsToRefresh = emptyList<PcmStream>()

    private val loopGroups by lazy {
        val groups = child.trackGroups
        androidx.media3.exoplayer.source.TrackGroupArray(*Array(groups.length) { index ->
            val group = groups[index]
            androidx.media3.common.TrackGroup(group.id, *Array(group.length) {
                loopFormat(group.getFormat(it))
            })
        })
    }

    override fun getTrackGroups() = loopGroups

    // Let this period feed compressed decoder postroll. The outer clip still bounds the
    // timeline and decoded rendering; cutting encoded input there corrupts MP3's last block.
    override fun setEndPositionUs(endPositionUs: Long): Long =
        if (endPositionUs == endUs) endUs else C.TIME_END_OF_SOURCE

    private fun loopFormat(format: androidx.media3.common.Format): androidx.media3.common.Format =
        if (AutoLoopAudioBounds.supports(format.sampleMimeType)) format.buildUpon()
            .setCustomData(AutoLoopAudioBounds(endUs))
            // Encoder priming applies at the file beginning, not at each mid-file repeat.
            .setEncoderDelay(if (startUs == 0L) format.encoderDelay else 0)
            .setEncoderPadding(0).build() else format
    // Kotlin delegation does not forward Java interface default methods automatically.
    // Losing this call lets Media3 discard the partial block spanning the loop start.
    override fun setUsesStreamPrerollFlags() = child.setUsesStreamPrerollFlags()

    override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
        child.prepare(object : MediaPeriod.Callback {
            override fun onPrepared(mediaPeriod: MediaPeriod) = callback.onPrepared(this@PcmLoopMediaPeriod)
            override fun onContinueLoadingRequested(source: MediaPeriod) =
                callback.onContinueLoadingRequested(this@PcmLoopMediaPeriod)
        }, (positionUs - 250_000L).coerceAtLeast(0L))
    }

    override fun selectTracks(
        selections: Array<out ExoTrackSelection?>,
        mayRetainStreamFlags: BooleanArray,
        streams: Array<SampleStream?>,
        streamResetFlags: BooleanArray,
        positionUs: Long
    ): Long {
        val old = streams.copyOf()
        val children = Array<SampleStream?>(streams.size) { (streams[it] as? PcmStream)?.child }
        softwareSelection = selections.any { it?.selectedFormat?.sampleMimeType == MimeTypes.AUDIO_ALAC }
        compressedSelection = selections.any { AutoLoopAudioBounds.supports(it?.selectedFormat?.sampleMimeType) }
        preciseSelection = selections.any {
            it?.selectedFormat?.sampleMimeType == MimeTypes.AUDIO_RAW ||
                AutoLoopAudioBounds.supports(it?.selectedFormat?.sampleMimeType)
        }
        val groups = trackGroups
        // Decorated formats select the loop renderer, but the child period identifies
        // tracks by its original TrackGroups. Translate back before selecting its streams.
        val childSelections = selections.map { selection ->
            selection?.let {
                val originalGroup = child.trackGroups[groups.indexOf(it.trackGroup)]
                object : ExoTrackSelection by it {
                    override fun getTrackGroup() = originalGroup
                }
            }
        }.toTypedArray()
        // Warm up compressed decoding and retain the complete block spanning the splice.
        prerollUs = selections.mapNotNull { it?.selectedFormat }.maxOfOrNull(AutoLoopAudioBounds::prerollUs) ?: 250_000L
        val readPosition = if (preciseSelection) (positionUs - prerollUs).coerceAtLeast(0L) else positionUs
        val position = child.selectTracks(childSelections, mayRetainStreamFlags, children, streamResetFlags, readPosition)
        startUs = minOf(endUs, maxOf(clipStartUs, positionUs))
        for (i in streams.indices) {
            val stream = children[i]
            streams[i] = if (stream == null) null else
                (old[i] as? PcmStream)?.takeIf { it.child === stream } ?: PcmStream(stream)
        }
        streamsToRefresh = streams.filterIsInstance<PcmStream>()
        return if (preciseSelection) positionUs else position
    }

    override fun seekToUs(positionUs: Long): Long {
        startUs = minOf(endUs, maxOf(clipStartUs, positionUs))
        // Seeking back to the intro must restore file-start encoder delay; a mid-file
        // repeat clears that delay. Force a format read even when the codec is reused.
        streamsToRefresh.forEach { it.forceFormat = true }
        val position = child.seekToUs(if (preciseSelection) (positionUs - prerollUs).coerceAtLeast(0L) else positionUs)
        return if (preciseSelection) positionUs else position
    }

    private inner class PcmStream(val child: SampleStream) : SampleStream by child {
        var forceFormat = false
        // ALAC preroll is handled inside our software renderer. Advertising it to the
        // player would force a renderer/audio-output restart at every repeat.
        override fun getFlags(): Int = if (softwareSelection)
            child.flags and (SampleStream.FLAG_MAYBE_HAS_PREROLL or SampleStream.FLAG_HAS_PREROLL).inv()
        else if (compressedSelection && startUs > 0L)
            (child.flags and SampleStream.FLAG_MAYBE_HAS_PREROLL.inv()) or SampleStream.FLAG_HAS_PREROLL
        else child.flags
        private var inputEndUs = endUs
        private var bytesPerFrame = 0
        private var sampleRate = 0
        override fun readData(formatHolder: FormatHolder, buffer: DecoderInputBuffer, readFlags: Int): Int {
            val result = child.readData(formatHolder, buffer,
                if (forceFormat) readFlags or SampleStream.FLAG_REQUIRE_FORMAT else readFlags)
            if (result == C.RESULT_FORMAT_READ) {
                forceFormat = false
                val format = loopFormat(requireNotNull(formatHolder.format))
                formatHolder.format = format
                sampleRate = format.sampleRate
                inputEndUs = AutoLoopEncodedTail.inputEndUs(format, endUs)
                bytesPerFrame = if (format.sampleMimeType == MimeTypes.AUDIO_RAW &&
                    format.channelCount > 0 && sampleRate > 0)
                    Util.getPcmFrameSize(format.pcmEncoding, format.channelCount) else 0
            } else if (result == C.RESULT_BUFFER_READ && !buffer.isEndOfStream &&
                buffer.timeUs >= inputEndUs) {
                buffer.clear()
                buffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM)
            } else if (result == C.RESULT_BUFFER_READ && !buffer.isEndOfStream &&
                bytesPerFrame > 0 && readFlags and SampleStream.FLAG_OMIT_SAMPLE_DATA == 0) {
                buffer.data?.let {
                    buffer.timeUs = AutoLoopPcmFrames.trim(it, bytesPerFrame, sampleRate,
                        buffer.timeUs, startUs, endUs)
                }
            }
            return result
        }
    }
}

/** Frame-aligned slice of one loop PCM packet, in write mode. No allocation or sample synthesis. */
internal object AutoLoopPcmFrames {
    fun trim(
        data: ByteBuffer, bytesPerFrame: Int, sampleRate: Int,
        timestampUs: Long, startUs: Long, endUs: Long
    ): Long {
        require(bytesPerFrame > 0 && sampleRate > 0 && startUs <= endUs)
        val frames = data.position() / bytesPerFrame
        fun frameAt(timeUs: Long): Int {
            val delta = timeUs - timestampUs
            if (delta <= 0) return 0
            return ((delta * sampleRate + 999_999L) / 1_000_000L).coerceAtMost(frames.toLong()).toInt()
        }
        val first = frameAt(startUs)
        val last = frameAt(endUs).coerceAtLeast(first)
        if (first == 0 && last == frames) return timestampUs
        data.flip()
        data.position(first * bytesPerFrame)
        data.limit(last * bytesPerFrame)
        data.compact()
        return timestampUs + first * 1_000_000L / sampleRate
    }
}
