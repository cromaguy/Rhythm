package chromahub.rhythm.app.infrastructure.service.player.autoloop

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoLoopPcmOutputTest {
    @Test fun emittedPcmHasNoPrerollAndNoExtraTailAcrossThreePasses() = verifySamples(537L)

    @Test fun introFlowsThroughLoopStartWithoutReplayingAnySamples() = verifySamples(0L)

    @Test fun flacDecodedBlocksAreTrimmedAtBothLoopBoundaries() = verifySamples(537L, flac = true)

    @Test fun flacIntroFlowsContinuouslyIntoTheFirstLoop() = verifySamples(0L, flac = true)

    @Test fun aacM4aHasNoExtraTailOrGapAcrossLoopBoundaries() = verifySamples(537L, aac = true)

    // Generate real AAC in an MP4 container with the device encoder, avoiding external fixtures.
    private fun encodeM4a(file: File, pcm: ByteArray) {
        val encoder = android.media.MediaCodec.createEncoderByType("audio/mp4a-latm")
        val muxer = android.media.MediaMuxer(file.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            val format = android.media.MediaFormat.createAudioFormat("audio/mp4a-latm", 8000, 1).apply {
                setInteger(android.media.MediaFormat.KEY_AAC_PROFILE,
                    android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(android.media.MediaFormat.KEY_BIT_RATE, 32000)
            }
            encoder.configure(format, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val info = android.media.MediaCodec.BufferInfo()
            var offset = 0
            var inputDone = false
            var outputDone = false
            var track = -1
            val deadline = android.os.SystemClock.elapsedRealtime() + 10000L
            while (!outputDone) {
                check(android.os.SystemClock.elapsedRealtime() < deadline) { "AAC encoder timed out" }
                if (!inputDone) {
                    val index = encoder.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val buffer = requireNotNull(encoder.getInputBuffer(index))
                        buffer.clear()
                        val count = minOf(buffer.remaining(), pcm.size - offset) and -2
                        buffer.put(pcm, offset, count)
                        encoder.queueInputBuffer(index, 0, count, offset / 2 * 125L,
                            if (count == 0) android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        offset += count
                        inputDone = count == 0
                    }
                }
                val index = encoder.dequeueOutputBuffer(info, 10000)
                if (index == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    started = true
                } else if (index >= 0) {
                    if (info.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        check(started)
                        muxer.writeSampleData(track, requireNotNull(encoder.getOutputBuffer(index)), info)
                    }
                    outputDone = info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(index, false)
                }
            }
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            try { if (started) muxer.stop() } finally { muxer.release() }
        }
    }

    // Verbatim FLAC subframes keep sample values exact without an external fixture or encoder.
    private fun flacRamp(): ByteArray {
        fun crc(bytes: ByteArray, bits: Int, polynomial: Int): Int {
            var value = 0
            for (byte in bytes) {
                value = value xor ((byte.toInt() and 255) shl (bits - 8))
                repeat(8) {
                    value = if (value and (1 shl (bits - 1)) != 0) (value shl 1) xor polynomial else value shl 1
                    value = value and ((1 shl bits) - 1)
                }
            }
            return value
        }
        val output = java.io.ByteArrayOutputStream()
        output.write("fLaC".toByteArray())
        val info = ByteBuffer.allocate(38).order(ByteOrder.BIG_ENDIAN)
        info.put(0x80.toByte()).put(0).put(0).put(34)
        info.putShort(3808).putShort(4096)
        repeat(6) { info.put(0) }
        info.putLong((8000L shl 44) or (15L shl 36) or 12000L)
        repeat(16) { info.put(0) }
        output.write(info.array())
        var first = 0
        var frame = 0
        while (first < 12000) {
            val count = minOf(4096, 12000 - first)
            val header = byteArrayOf(0xff.toByte(), 0xf8.toByte(), 0x74, 0x08,
                frame.toByte(), ((count - 1) shr 8).toByte(), (count - 1).toByte())
            val packet = java.io.ByteArrayOutputStream()
            packet.write(header)
            packet.write(crc(header, 8, 0x07))
            packet.write(0x02) // verbatim subframe
            repeat(count) { sample ->
                val value = first + sample
                packet.write(value shr 8)
                packet.write(value and 255)
            }
            val bytes = packet.toByteArray()
            val checksum = crc(bytes, 16, 0x8005)
            output.write(bytes)
            output.write(checksum shr 8)
            output.write(checksum and 255)
            first += count
            frame++
        }
        return output.toByteArray()
    }

    private fun verifySamples(initialPositionMs: Long, flac: Boolean = false, aac: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File.createTempFile("loop-pcm-", if (aac) ".m4a" else if (flac) ".flac" else ".wav", context.cacheDir)
        val wav = ByteBuffer.allocate(44 + 24000).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + 24000).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(24000)
        repeat(12000) {
            // The loop spans exactly 251 cycles, giving AAC a continuous seam to reproduce.
            val value = if (aac) (kotlin.math.sin(2 * Math.PI * it / 16) * 12000).toInt() else it
            wav.putShort(value.toShort())
        }
        if (aac) encodeM4a(file, wav.array().copyOfRange(44, wav.capacity()))
        else file.writeBytes(if (flac) flacRamp() else wav.array())
        // Initial seek is 537 ms, outside the extractor's normal block boundaries.
        val initialFrame = (initialPositionMs * 8).toInt()
        val firstPassFrames = 8024 - initialFrame
        val loopFrames = 8024 - 4008
        val expectedFrames = firstPassFrames + loopFrames * 2
        val received = Collections.synchronizedList(mutableListOf<Short>())
        val complete = CountDownLatch(1)
        var firstTimestampUs: Long? = null
        var maxTimestampErrorUs = 0L
        val chunks = mutableListOf<String>()
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioRenderers(
                context: Context, extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean, audioSink: AudioSink,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                out.add(AutoLoopAudioRenderer(context, mediaCodecSelector, enableDecoderFallback,
                    eventHandler, eventListener, audioSink))
                super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector,
                    enableDecoderFallback, audioSink, eventHandler, eventListener, out)
            }

            override fun buildAudioSink(
                context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                val sink = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams) ?: return null
                return object : ForwardingAudioSink(sink) {
                    private var pendingBuffer: ByteBuffer? = null
                    private var pendingStart = 0
                    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
                        val before = buffer.position()
                        if (pendingBuffer !== buffer) {
                            pendingBuffer = buffer
                            pendingStart = before
                        }
                        val consumed = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
                        val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                        data.limit(buffer.position())
                        data.position(before)
                        synchronized(received) {
                            if (data.hasRemaining() && received.size < expectedFrames) {
                                val timestampUs = presentationTimeUs + (before - pendingStart) * 1_000_000L / 16000
                                if (firstTimestampUs == null) firstTimestampUs = timestampUs
                                if (chunks.size < 20) chunks += "frame=${received.size}, time=${timestampUs - requireNotNull(firstTimestampUs)}, first=${data.getShort(data.position())}, samples=${data.remaining() / 2}"
                                val expectedUs = requireNotNull(firstTimestampUs) + received.size * 125L
                                maxTimestampErrorUs = maxOf(maxTimestampErrorUs, kotlin.math.abs(timestampUs - expectedUs))
                            }
                            while (data.remaining() >= 2 && received.size < expectedFrames) received += data.short
                            if (received.size == expectedFrames) complete.countDown()
                        }
                        if (consumed) pendingBuffer = null
                        return consumed
                    }
                }
            }
        }
        var player: ExoPlayer? = null
        var session: NativeAutoLoopSession? = null
        try {
            instrumentation.runOnMainSync {
                val p = ExoPlayer.Builder(context, renderers).build()
                player = p
                p.volume = 0f
                val installer = AutoLoopQueueInstaller(DefaultMediaSourceFactory(context))
                p.setMediaItems(listOf(MediaItem.Builder().setMediaId("pcm")
                    .setUri(file.toURI().toString()).build()), 0, initialPositionMs)
                session = NativeAutoLoopSession(p, 501L to 1003L) { items, index, position ->
                    installer.install(p, items, index, position)
                }
                p.play()
            }
            assertTrue("Timed out collecting loop PCM", complete.await(15, TimeUnit.SECONDS))
            val snapshot = synchronized(received) { received.toList() }
            assertEquals(expectedFrames, snapshot.size)
            assertTrue("PCM timestamps must be continuous; error=$maxTimestampErrorUs us; $chunks", maxTimestampErrorUs <= 125L)
            for (i in snapshot.indices) {
                val expected = if (i < firstPassFrames) initialFrame + i else 4008 + (i - firstPassFrames) % loopFrames
                if (aac) {
                    val sample = (kotlin.math.sin(2 * Math.PI * expected / 16) * 12000).toInt()
                    assertTrue("AAC seam mismatch at frame $i: expected ~$sample, got ${snapshot[i]}",
                        kotlin.math.abs(snapshot[i].toInt() - sample) <= 2000)
                } else assertEquals("Unexpected sample at frame $i", expected.toShort(), snapshot[i])
            }
        } finally {
            instrumentation.runOnMainSync { session?.close(); player?.release() }
            file.delete()
        }
    }
}
