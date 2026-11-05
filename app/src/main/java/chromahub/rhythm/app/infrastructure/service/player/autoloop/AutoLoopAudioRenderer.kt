package chromahub.rhythm.app.infrastructure.service.player.autoloop

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.nio.ByteBuffer

/** Attached only to supported compressed formats inside the temporary Automatic Loop source. */
internal data class AutoLoopAudioBounds(val endUs: Long) {
    companion object {
        fun supports(mime: String?) = when (mime) {
            MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_ALAC -> true
            else -> false
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun prerollUs(format: Format): Long {
            if (format.sampleMimeType != MimeTypes.AUDIO_FLAC) return 250_000L
            val info = format.initializationData.firstOrNull { it.size >= 42 &&
                it[0] == 'f'.code.toByte() && it[1] == 'L'.code.toByte() &&
                it[2] == 'a'.code.toByte() && it[3] == 'C'.code.toByte() } ?: return 1_000_000L
            val metadata = androidx.media3.extractor.FlacStreamMetadata(info, 8)
            if (metadata.sampleRate <= 0) return 1_000_000L
            // At low sample rates a FLAC block can exceed the usual 250 ms preroll.
            // Seek two blocks earlier so the extractor supplies the block spanning the splice.
            return maxOf(250_000L, (2_000_000L * metadata.maxBlockSizeSamples +
                metadata.sampleRate - 1) / metadata.sampleRate)
        }
    }
}

/**
 * Compressed packets cannot be sliced by the source. Trim their decoded PCM instead,
 * including the first block straddling a seek and the last block straddling the loop end.
 * Ordinary playback and other formats use the engine's existing renderers.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AutoLoopAudioRenderer(
    context: Context, selector: MediaCodecSelector, fallback: Boolean,
    handler: Handler, listener: AudioRendererEventListener, sink: AudioSink
) : MediaCodecAudioRenderer(context, selector, fallback, handler, listener, sink) {
    private var frameSize = 0
    private var rate = 0
    private var pendingTimestampUs: Long? = null
    private var resetStreamOffsetUs = C.TIME_UNSET

    override fun getName() = "AutoLoopAudioRenderer"

    // Use a marker carried with the format, rather than global enabled state: the decoder
    // can still be draining an older stream after the controller switches tracks.
    override fun supportsFormat(selector: MediaCodecSelector, format: Format): Int =
        if (format.customData is AutoLoopAudioBounds && format.sampleMimeType != MimeTypes.AUDIO_ALAC)
            super.supportsFormat(selector, format)
        else RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)

    // Trimming requires PCM, even if the output device supports compressed passthrough.
    override fun shouldUseBypass(format: Format) = false

    override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
        rate = mediaFormat?.getInteger(MediaFormat.KEY_SAMPLE_RATE) ?: format.sampleRate
        val channels = mediaFormat?.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: format.channelCount
        val encoding = if (mediaFormat?.containsKey(MediaFormat.KEY_PCM_ENCODING) == true)
            mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else C.ENCODING_PCM_16BIT
        frameSize = Util.getPcmFrameSize(encoding, channels)
        super.onOutputFormatChanged(format, mediaFormat)
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean, sampleStreamIsResetToKeyFrame: Boolean) {
        pendingTimestampUs = null
        super.onPositionReset(positionUs, joining, sampleStreamIsResetToKeyFrame)
        resetStreamOffsetUs = outputStreamOffsetUs
    }

    override fun processOutputBuffer(
        positionUs: Long, elapsedRealtimeUs: Long, codec: MediaCodecAdapter?,
        buffer: ByteBuffer?, bufferIndex: Int, bufferFlags: Int, sampleCount: Int,
        bufferPresentationTimeUs: Long, isDecodeOnlyBuffer: Boolean, isLastBuffer: Boolean,
        format: Format
    ): Boolean {
        val bounds = format.customData as? AutoLoopAudioBounds
        if (buffer == null || bounds == null || frameSize <= 0 || rate <= 0) {
            return super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex,
                bufferFlags, sampleCount, bufferPresentationTimeUs, isDecodeOnlyBuffer, isLastBuffer, format)
        }
        // A seek may go earlier than this period's original start (Previous -> intro).
        // A new repeat has a different offset and uses its own stream start instead.
        val start = if (resetStreamOffsetUs == outputStreamOffsetUs)
            lastResetPositionUs else outputStreamStartPositionUs
        // Media3 adds an offset on each repeat to keep renderer timestamps increasing.
        // Translate the track's end into that clock before slicing decoded PCM.
        if (pendingTimestampUs == null) {
            pendingTimestampUs = AutoLoopDecodedFrames.slice(buffer, frameSize, rate,
                bufferPresentationTimeUs, start, bounds.endUs + outputStreamOffsetUs)
        }
        val consumed = super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex,
            bufferFlags, sampleCount, requireNotNull(pendingTimestampUs),
            !buffer.hasRemaining() || (isDecodeOnlyBuffer && bufferPresentationTimeUs >= start),
            isLastBuffer, format)
        // A sink can consume a buffer in several calls. Never trim its remaining bytes twice.
        if (consumed) pendingTimestampUs = null
        return consumed
    }
}

/** Read-mode slice; preserves interleaved channels and supports decoder buffers with an offset. */
internal object AutoLoopDecodedFrames {
    fun slice(data: ByteBuffer, frameSize: Int, rate: Int, timestampUs: Long, startUs: Long, endUs: Long): Long {
        require(frameSize > 0 && rate > 0)
        val origin = data.position()
        val frames = data.remaining() / frameSize
        // Round upward: keep the first complete frame at/after start, and exclude every
        // frame at/after end. This also handles boundaries between 44.1 kHz sample times.
        fun frameAt(timeUs: Long) = if (timeUs <= timestampUs) 0 else
            (((timeUs - timestampUs) * rate + 999_999L) / 1_000_000L).coerceAtMost(frames.toLong()).toInt()
        val first = frameAt(startUs)
        val last = frameAt(endUs).coerceAtLeast(first)
        data.position(origin + first * frameSize)
        data.limit(origin + last * frameSize)
        return timestampUs + first * 1_000_000L / rate
    }
}
