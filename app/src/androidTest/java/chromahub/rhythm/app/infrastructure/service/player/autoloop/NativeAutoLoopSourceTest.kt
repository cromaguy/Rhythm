package chromahub.rhythm.app.infrastructure.service.player.autoloop

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NativeAutoLoopSourceTest {
    @Test fun rebuildingReallyClipsTheSourceAndPreservesTrackPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File.createTempFile("loop-source-", ".wav", context.cacheDir)
        val dataSize = 12000 * 2 // 1.5 seconds, 8 kHz mono PCM.
        val wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(dataSize)
        repeat(12000) { wav.putShort((sin(2 * PI * 440 * it / 8000) * 1000).toInt().toShort()) }
        file.writeBytes(wav.array())
        lateinit var player: ExoPlayer
        var session: NativeAutoLoopSession? = null
        fun ready(action: () -> Unit) {
            val latch = CountDownLatch(1)
            instrumentation.runOnMainSync {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            player.removeListener(this)
                            latch.countDown()
                        }
                    }
                }
                player.addListener(listener)
                action()
                if (player.playbackState == Player.STATE_READY) {
                    player.removeListener(listener)
                    latch.countDown()
                }
            }
            assertTrue("Player did not become ready", latch.await(15, TimeUnit.SECONDS))
        }
        try {
            instrumentation.runOnMainSync { player = ExoPlayer.Builder(context).build() }
            ready {
                player.setMediaItem(MediaItem.Builder().setMediaId("wav").setUri(file.toURI().toString()).build())
                player.prepare()
            }
            ready { player.seekTo(700) }
            instrumentation.runOnMainSync {
                assertEquals(1500L, player.duration)
                assertEquals(700L, player.currentPosition)
            }
            ready {
                val installer = AutoLoopQueueInstaller(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context))
                session = NativeAutoLoopSession(player, 500L to 1000L) { items, index, position ->
                    installer.install(player, items, index, position)
                }
            }
            instrumentation.runOnMainSync {
                assertEquals("First-pass source must retain the intro and end at the loop boundary", 1000L, player.duration)
                assertEquals("Installing the loop must preserve full-track coordinates", 700L, player.currentPosition)
                assertEquals(500L, player.currentTimeline.getWindow(0, androidx.media3.common.Timeline.Window()).defaultPositionMs)
                assertEquals(1500L, AutoLoopState.windowFor(player.currentMediaItem)!!.trackDurationMs)
            }
            ready { session!!.close() }
            instrumentation.runOnMainSync {
                assertEquals(1500L, player.duration)
                assertEquals(700L, player.currentPosition)
            }
            ready {
                val installer = AutoLoopQueueInstaller(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context))
                session = NativeAutoLoopSession(player, 500L to 1000L) { items, index, position ->
                    installer.install(player, items, index, position)
                }
            }
            ready { player.seekTo(0) }
            instrumentation.runOnMainSync {
                assertEquals("Previous keeps the intro-enabled loop source", 1000L, player.duration)
                assertEquals("Previous must reach the intro, not the clip start", 0L, player.currentPosition)
            }
        } finally {
            instrumentation.runOnMainSync { session?.close(); player.release() }
            file.delete()
        }
    }
}
