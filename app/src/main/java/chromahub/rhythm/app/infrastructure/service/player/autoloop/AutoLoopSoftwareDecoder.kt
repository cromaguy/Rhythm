package chromahub.rhythm.app.infrastructure.service.player.autoloop

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Uses the app's bundled software decoders for formats absent from MediaCodec, such as ALAC.
 * This private analysis player has no MediaSession, audio focus or physical audio output.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object AutoLoopSoftwareDecoder {
    suspend fun find(context: Context, uri: Uri): Pair<Long, Long>? {
        // Convert our own timeout to an analysis failure so the playback gate opens.
        // Cancellation by the caller still propagates and releases the private player.
        val samples = withTimeoutOrNull(60_000L) {
            withContext(Dispatchers.Main) {
                val collector = Collector()
                val renderers = object : DefaultRenderersFactory(context) {
                    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
                        enableAudioTrackPlaybackParams: Boolean): AudioSink? {
                        val sink = super.buildAudioSink(context, false, enableAudioTrackPlaybackParams) ?: return null
                        return object : ForwardingAudioSink(sink) {
                            private var positionUs = AudioSink.CURRENT_POSITION_NOT_SET
                            private var ended = false
                            override fun configure(config: AudioSink.AudioSinkConfig) {
                                collector.configure(config.format.sampleRate, config.format.channelCount, config.format.pcmEncoding)
                            }
                            override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
                                val frames = collector.consume(buffer)
                                positionUs = presentationTimeUs + frames * 1_000_000L / collector.rate
                                return true
                            }
                            // Consuming PCM immediately lets decoding run faster than real time.
                            // Never forward buffers to the underlying sink, which would play them.
                            override fun getCurrentPositionUs(sourceEnded: Boolean) = positionUs
                            // Advertise virtual pending audio until EOS, or ExoPlayer can stay
                            // buffering forever despite this sink already consuming every block.
                            override fun hasPendingData() = positionUs != AudioSink.CURRENT_POSITION_NOT_SET && !ended
                            override fun isEnded() = ended
                            override fun playToEndOfStream() { ended = true }
                        }
                    }
                }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                val player = ExoPlayer.Builder(context, renderers).build()
                try {
                    suspendCancellableCoroutine<FloatArray> { continuation ->
                        player.addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED && continuation.isActive)
                                    continuation.resume(collector.samples())
                            }
                            override fun onPlayerError(error: PlaybackException) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            }
                        })
                        player.setMediaItem(MediaItem.fromUri(uri))
                        player.prepare()
                        player.play()
                    }
                } finally {
                    // This also runs on cancellation when a new track replaces the analysis.
                    player.release()
                }
            }
        } ?: error("Software audio decoding timed out")
        return withContext(Dispatchers.Default) { LoopPointDetector.find(samples, samples.size) }
    }

    private class Collector {
        private val output = FloatArray(600 * 4000 + 4000)
        private var count = 0
        var rate = 0
            private set
        private var channels = 0
        private var encoding = C.ENCODING_INVALID
        private var phase = 0
        private var sum = 0.0
        private var framesInInterval = 0

        fun configure(rate: Int, channels: Int, encoding: Int) {
            require(rate >= 4000 && channels > 0)
            require(encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT)
            this.rate = rate
            this.channels = channels
            this.encoding = encoding
        }

        fun consume(buffer: ByteBuffer): Int {
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val bytes = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            val frames = buffer.remaining() / (bytes * channels)
            repeat(frames) {
                var mono = 0.0
                repeat(channels) { mono += if (bytes == 4) buffer.float.toDouble() else buffer.short / 32768.0 }
                sum += mono / channels
                framesInInterval++
                phase += 4000
                if (phase >= rate) {
                    phase -= rate
                    require(count < output.size) { "Track exceeds the automatic-loop analysis limit" }
                    output[count++] = (sum / framesInInterval).toFloat()
                    sum = 0.0
                    framesInInterval = 0
                }
            }
            buffer.position(buffer.limit())
            return frames
        }

        fun samples() = output.copyOf(count)
    }
}
