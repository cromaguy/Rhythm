package chromahub.rhythm.app.infrastructure.service.player.autoloop

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import chromahub.rhythm.app.infrastructure.service.player.RhythmShuffleOrder
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoLoopPlaybackCompatibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var file: File
    private lateinit var player: ExoPlayer
    private var session: NativeAutoLoopSession? = null

    @Before fun createPlayer() {
        AutoLoopState.setEnabled(false)
        AutoLoopState.windowState.value = null
        file = File.createTempFile("loop-compatibility-", ".wav", context.cacheDir)
        val wav = ByteBuffer.allocate(44 + 24000).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36 + 24000).put("WAVEfmt ".toByteArray())
        wav.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(24000)
        repeat(12000) { wav.putShort(it.toShort()) }
        file.writeBytes(wav.array())
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(AutoLoopPreparationMediaSource.Factory(DefaultMediaSourceFactory(context)))
                .build()
            player.volume = 0f
        }
    }

    @After fun releasePlayer() {
        instrumentation.runOnMainSync {
            AutoLoopState.setEnabled(false)
            session?.close()
            player.release()
            AutoLoopState.windowState.value = null
        }
        file.delete()
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri(file.toURI().toString())
        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setDurationMs(1500L).build()).build()

    private fun ready(action: () -> Unit) {
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

    private fun shuffleOrder(): List<Int> = buildList {
        val timeline = player.currentTimeline
        var index = timeline.getFirstWindowIndex(true)
        while (index != androidx.media3.common.C.INDEX_UNSET && index !in this) {
            add(index)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
    }

    @Test fun installAndDisablePreserveQueueShufflePlaybackParametersAndPause() {
        ready {
            player.setMediaItems(listOf(item("first"), item("second"), item("third")), 1, 700L)
            player.setShuffleOrder(RhythmShuffleOrder(intArrayOf(2, 0, 1)))
            player.shuffleModeEnabled = true
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.pauseAtEndOfMediaItems = true
            player.playbackParameters = PlaybackParameters(1.25f, 0.8f)
            player.volume = 0.4f
            player.prepare()
        }
        lateinit var order: List<Int>
        instrumentation.runOnMainSync { order = shuffleOrder() }
        ready {
            AutoLoopState.setEnabled(true)
            val installer = AutoLoopQueueInstaller(DefaultMediaSourceFactory(context))
            session = NativeAutoLoopSession(player, 500L to 1200L) { items, index, position ->
                installer.install(player, items, index, position)
            }
        }
        instrumentation.runOnMainSync {
            assertEquals(order, shuffleOrder())
            assertEquals("second", player.currentMediaItem!!.mediaId)
            assertEquals(700L, player.currentPosition)
            assertEquals(PlaybackParameters(1.25f, 0.8f), player.playbackParameters)
            assertEquals(0.4f, player.volume, 0f)
            assertFalse(player.playWhenReady)
            assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
        }
        ready {
            AutoLoopState.setEnabled(false)
            session!!.close()
        }
        instrumentation.runOnMainSync {
            assertEquals(order, shuffleOrder())
            assertEquals(listOf("first", "second", "third"),
                (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
            assertEquals(700L, player.currentPosition)
            assertEquals(1500L, player.duration)
            assertEquals(PlaybackParameters(1.25f, 0.8f), player.playbackParameters)
            assertEquals(0.4f, player.volume, 0f)
            assertFalse(player.playWhenReady)
            assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
            assertTrue(player.pauseAtEndOfMediaItems)
        }
    }

    @Test fun pauseDuringAnalysisIsStillPausedWhenFallbackBecomesReady() {
        var everPlayed = false
        instrumentation.runOnMainSync {
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { everPlayed = everPlayed || isPlaying }
            })
            AutoLoopState.setEnabled(true)
            player.setMediaItem(item("waiting"))
            player.prepare()
            player.play()
            assertTrue(player.playWhenReady)
            assertFalse(player.isPlaying)
            assertEquals(0L, player.currentPosition)
            player.pause()
        }
        ready { AutoLoopState.analysisFallbackKey.value = AutoLoopPreparation.key(player.currentMediaItem) }
        instrumentation.runOnMainSync {
            assertFalse(player.playWhenReady)
            assertFalse(player.isPlaying)
            assertFalse(everPlayed)
            assertEquals(0L, player.currentPosition)
        }
    }

    @Test fun disablingDuringAnalysisHonorsAnOutstandingPlayRequest() {
        instrumentation.runOnMainSync {
            AutoLoopState.setEnabled(true)
            player.setMediaItem(item("waiting"))
            player.prepare()
            player.play()
            assertTrue(player.playWhenReady)
            assertFalse(player.isPlaying)
        }
        ready { AutoLoopState.setEnabled(false) }
        instrumentation.runOnMainSync {
            assertTrue(player.playWhenReady)
            assertTrue(player.isPlaying)
            player.pause()
        }
    }
}
