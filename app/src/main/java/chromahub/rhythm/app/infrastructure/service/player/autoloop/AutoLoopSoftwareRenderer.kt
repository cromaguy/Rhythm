package chromahub.rhythm.app.infrastructure.service.player.autoloop

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Util
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.MediaClock
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import java.nio.ByteBuffer

/** Exact decoded-PCM boundaries for marked ALAC loop sources; ordinary playback is rejected. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AutoLoopSoftwareRenderer private constructor(
    private val decoder: FfmpegAudioRenderer, private val trim: TrimSink
) : ForwardingRenderer(decoder) {
    companion object {
        fun create(handler: Handler, listener: AudioRendererEventListener, sink: AudioSink): Renderer {
            val trim = TrimSink(sink)
            return AutoLoopSoftwareRenderer(FfmpegAudioRenderer(handler, listener, trim), trim)
        }
    }

    private var inputOffsetUs = 0L
    private var minimumClockUs = 0L
    private var inputEndUs = 0L
    private val loopCapabilities = object : RendererCapabilities by decoder.capabilities {
        override fun supportsFormat(format: Format): Int =
            if (format.sampleMimeType == MimeTypes.AUDIO_ALAC && format.customData is AutoLoopAudioBounds)
                decoder.capabilities.supportsFormat(format)
            else RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        override fun setListener(listener: RendererCapabilities.Listener) = decoder.capabilities.setListener(listener)
        override fun clearListener() = decoder.capabilities.clearListener()
    }
    private val loopClock = object : MediaClock by decoder {
        // Decoding preroll must not rewind the clock exposed to the player or its UI.
        override fun getPositionUs() = maxOf(minimumClockUs, decoder.positionUs)
        override fun hasSkippedSilenceSinceLastCall() = decoder.hasSkippedSilenceSinceLastCall()
    }

    override fun getCapabilities() = loopCapabilities
    override fun getMediaClock(): MediaClock = loopClock

    private fun register(formats: Array<out Format>, startUs: Long, offsetUs: Long) {
        inputOffsetUs = offsetUs
        inputEndUs = requireNotNull(formats.firstNotNullOfOrNull { it.customData as? AutoLoopAudioBounds }).endUs
        trim.cuts[offsetUs] = Cut(startUs, inputEndUs + offsetUs)
    }

    override fun enable(configuration: RendererConfiguration, formats: Array<out Format>,
        stream: SampleStream, positionUs: Long, joining: Boolean, mayRenderStartOfStream: Boolean,
        startPositionUs: Long, offsetUs: Long, mediaPeriodId: MediaSource.MediaPeriodId) {
        trim.cuts.clear()
        register(formats, maxOf(positionUs, startPositionUs), offsetUs)
        minimumClockUs = maxOf(positionUs, startPositionUs)
        // FFmpeg's base renderer otherwise drops an entire decoded block before this
        // position, including samples needed after the splice. Decode preroll and slice it.
        decoder.enable(configuration, formats, stream, maxOf(offsetUs, positionUs - 250_000L),
            joining, mayRenderStartOfStream, maxOf(offsetUs, startPositionUs - 250_000L), offsetUs, mediaPeriodId)
    }

    override fun replaceStream(formats: Array<out Format>, stream: SampleStream,
        startPositionUs: Long, offsetUs: Long, mediaPeriodId: MediaSource.MediaPeriodId) {
        register(formats, startPositionUs, offsetUs)
        decoder.replaceStream(formats, stream, startPositionUs, offsetUs, mediaPeriodId)
    }

    override fun resetPosition(positionUs: Long, sampleStreamIsResetToKeyFrame: Boolean) {
        minimumClockUs = positionUs
        trim.cuts[inputOffsetUs] = Cut(positionUs, inputEndUs + inputOffsetUs)
        decoder.resetPosition(maxOf(inputOffsetUs, positionUs - 250_000L), sampleStreamIsResetToKeyFrame)
    }

    private data class Cut(val startUs: Long, val endUs: Long)

    private class TrimSink(sink: AudioSink) : ForwardingAudioSink(sink) {
        val cuts = mutableMapOf<Long, Cut>()
        private var offsetUs = 0L
        private var frameSize = 0
        private var rate = 0
        private var pendingTimestampUs: Long? = null

        override fun configure(config: AudioSink.AudioSinkConfig) {
            frameSize = Util.getPcmFrameSize(config.format.pcmEncoding, config.format.channelCount)
            rate = config.format.sampleRate
            super.configure(config)
        }

        override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
            // replaceStream can run before old output drains. Switch cuts only when FFmpeg
            // actually starts emitting the next stream, not when it starts reading its input.
            offsetUs = outputStreamOffsetUs
            cuts.keys.removeAll { it < offsetUs }
            super.setOutputStreamOffsetUs(outputStreamOffsetUs)
        }

        override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
            if (pendingTimestampUs == null) {
                val cut = requireNotNull(cuts[offsetUs])
                pendingTimestampUs = AutoLoopDecodedFrames.slice(buffer, frameSize, rate,
                    presentationTimeUs, cut.startUs, cut.endUs)
            }
            if (!buffer.hasRemaining()) {
                pendingTimestampUs = null
                return true
            }
            val consumed = super.handleBuffer(buffer, requireNotNull(pendingTimestampUs), encodedAccessUnitCount)
            if (consumed) pendingTimestampUs = null
            return consumed
        }

        override fun flush() {
            pendingTimestampUs = null
            super.flush()
        }
    }
}
