package chromahub.rhythm.app.infrastructure.service.player.autoloop

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The musical search uses downsampled mono. AAC needs a final check in playback coordinates:
 * encoder priming, stereo phase and rounding to milliseconds can invalidate a mono zero crossing.
 */
internal object AutoLoopAacSpliceRefiner {
    suspend fun refine(context: Context, uri: Uri, bounds: Pair<Long, Long>): Pair<Long, Long> {
        val head = decodeWindow(context, uri, bounds.first)
        val tail = decodeWindow(context, uri, bounds.second)
        return AutoLoopStereoSplice.choose(head, tail, bounds)
    }

    private suspend fun decodeWindow(context: Context, uri: Uri, centerMs: Long): AutoLoopStereoSplice.Window {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            val format = extractor.getTrackFormat(track)
            // Match Media3's mid-track decoder setup. It handles priming separately in its sink;
            // asking MediaCodec to trim priming again would shift the analysis waveform.
            format.setInteger("encoder-delay", 0)
            format.setInteger("encoder-padding", 0)
            extractor.selectTrack(track)
            val fromUs = ((centerMs - 30L).coerceAtLeast(0L)) * 1000L
            val untilUs = (centerMs + 30L) * 1000L
            extractor.seekTo((fromUs - 250_000L).coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var firstFrame = 0L
            var samples = FloatArray(0)
            var copiedFrames = 0
            var inputDone = false
            val info = MediaCodec.BufferInfo()
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000L
            while (true) {
                currentCoroutineContext().ensureActive()
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "AAC splice decoding timed out" }
                if (!inputDone) {
                    val input = decoder.dequeueInputBuffer(10_000L)
                    if (input >= 0) {
                        val buffer = requireNotNull(decoder.getInputBuffer(input))
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            inputDone = true
                            decoder.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            decoder.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val output = decoder.dequeueOutputBuffer(info, 10_000L)
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val decoded = decoder.outputFormat
                    rate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
                    firstFrame = (fromUs * rate + 999_999L) / 1_000_000L
                    val lastFrame = (untilUs * rate + 999_999L) / 1_000_000L
                    samples = FloatArray((lastFrame - firstFrame).toInt() * channels)
                } else if (output >= 0) {
                    val buffer = requireNotNull(decoder.getOutputBuffer(output)).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                    val frameCount = buffer.remaining() / (bytes * channels)
                    val packetFrame = (info.presentationTimeUs * rate / 1_000_000.0).roundToLong()
                    repeat(frameCount) { frame ->
                        val index = packetFrame + frame - firstFrame
                        repeat(channels) { channel ->
                            val value = if (bytes == 4) buffer.float else buffer.short / 32768f
                            if (index >= 0 && index < samples.size / channels)
                                samples[index.toInt() * channels + channel] = value
                        }
                        if (index >= 0 && index < samples.size / channels) copiedFrames++
                    }
                    decoder.releaseOutputBuffer(output, false)
                    if (packetFrame + frameCount >= firstFrame + samples.size / channels ||
                        info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            require(copiedFrames == samples.size / channels && samples.isNotEmpty()) { "Incomplete AAC splice window" }
            return AutoLoopStereoSplice.Window(firstFrame, rate, channels, samples)
        } finally {
            codec?.release()
            extractor.release()
        }
    }
}

/** Choose a nearby join on the actual millisecond grid without changing the loop period. */
internal object AutoLoopStereoSplice {
    data class Window(val firstFrame: Long, val rate: Int, val channels: Int, val samples: FloatArray) {
        fun frameAt(ms: Long) = (ms * rate + 999L) / 1000L
        fun contains(frame: Long) = frame >= firstFrame && frame < firstFrame + samples.size / channels
        fun sample(frame: Long, channel: Int) = samples[(frame - firstFrame).toInt() * channels + channel].toDouble()
    }

    fun choose(head: Window, tail: Window, bounds: Pair<Long, Long>): Pair<Long, Long> {
        if (head.rate != tail.rate || head.channels != tail.channels) return bounds
        var best = Double.POSITIVE_INFINITY
        var shift = 0L
        for (offset in -25L..25L) {
            val a = head.frameAt(bounds.first + offset)
            val b = tail.frameAt(bounds.second + offset)
            if (bounds.first + offset < 0L || !head.contains(a) || !head.contains(a + 1) ||
                !tail.contains(b - 1) || !tail.contains(b - 2)) continue
            var cost = 0.0
            repeat(head.channels) { channel ->
                val first = head.sample(a, channel)
                val next = head.sample(a + 1, channel)
                val last = tail.sample(b - 1, channel)
                val previous = tail.sample(b - 2, channel)
                // Minimize the value jump and slope change in each channel; averaging to
                // mono can hide opposite stereo jumps. Both bounds share the same shift.
                val jump = first - last
                val slope = (next - first) - (last - previous)
                cost += jump * jump + 0.25 * slope * slope
            }
            cost += abs(offset) * 1e-8
            if (cost < best) {
                best = cost
                shift = offset
            }
        }
        return bounds.first + shift to bounds.second + shift
    }
}
