package chromahub.rhythm.app.infrastructure.service.player.autoloop

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NativeAutoLoopSessionTest {
    private class Fake {
        val items = mutableListOf(
            MediaItem.Builder().setMediaId("first").build(),
            MediaItem.Builder().setMediaId("second").build()
        )
        var index = 0
        var position = 4000L
        var repeat = Player.REPEAT_MODE_ALL
        var pauseAtEnd = true
        var seeks = 0
        var resets = 0
        var sourceStartMs = 0L
        var sourceEndMs = 12000L
        val audioPosition get() = position + sourceStartMs
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentMediaItem" -> items.getOrNull(index)
                "getCurrentMediaItemIndex" -> index
                "getCurrentPosition" -> position
                "getMaxSeekToPreviousPosition" -> 3000L
                "hasPreviousMediaItem" -> index > 0
                "getDuration" -> sourceEndMs - sourceStartMs
                "getShuffleModeEnabled" -> false
                "setMediaItems" -> {
                    val replacement = (args!![0] as List<*>).map { it as MediaItem }
                    items.clear()
                    items.addAll(replacement)
                    index = args[1] as Int
                    position = args[2] as Long
                    val clipping = items[index].clippingConfiguration
                    sourceStartMs = clipping.startPositionMs
                    sourceEndMs = clipping.endPositionMs.takeIf { it >= 0 } ?: 12000L
                    resets++
                    null
                }
                "getMediaItemCount" -> items.size
                "getMediaItemAt" -> items[args!![0] as Int]
                "getRepeatMode" -> repeat
                "setRepeatMode" -> { repeat = args!![0] as Int; null }
                "getPauseAtEndOfMediaItems" -> pauseAtEnd
                "setPauseAtEndOfMediaItems" -> { pauseAtEnd = args!![0] as Boolean; null }
                // Models Media3's same-URI optimization: replacing an item changes the
                // item metadata but does not wrap an existing progressive source in a clip.
                "replaceMediaItem" -> { items[args!![0] as Int] = args[1] as MediaItem; null }
                "seekTo" -> { index = args!![0] as Int; position = args[1] as Long; seeks++; null }
                "getPlaybackState" -> Player.STATE_READY
                else -> error("Unexpected player call: ${method.name}")
            }
        } as ExoPlayer
    }

    @Test fun nativeClipRepeatsWithoutBoundarySeeksAndRestoresPosition() {
        val fake = Fake()
        val original = fake.items[0]
        val session = NativeAutoLoopSession(fake.player, 2000L to 8000L)
        assertEquals(4000, fake.position)
        assertEquals(4000, fake.audioPosition)
        assertEquals(8000, fake.player.duration)
        assertEquals(12000, AutoLoopState.windowFor(fake.items[0])!!.trackDurationMs)
        assertEquals(Player.REPEAT_MODE_ONE, fake.repeat)
        assertFalse(fake.pauseAtEnd)
        assertEquals(0, fake.items[0].clippingConfiguration.startPositionMs)
        assertEquals(8000, fake.items[0].clippingConfiguration.endPositionMs)
        assertTrue(session.isCurrent())
        assertEquals(0, fake.seeks)
        assertEquals(1, fake.resets) // Source and position are installed atomically.
        fake.position = 1000
        session.close()
        assertEquals(original, fake.items[0])
        assertEquals(1000, fake.position)
        assertEquals(Player.REPEAT_MODE_ALL, fake.repeat)
        assertTrue(fake.pauseAtEnd)
        session.close()
        assertEquals(0, fake.seeks)
        assertEquals(2, fake.resets) // Closing twice is harmless.
        assertEquals(12000, fake.player.duration)
    }

    @Test fun preparingBeforeLoopStartPreservesTheIntroPosition() {
        val fake = Fake()
        fake.position = 500L
        val session = NativeAutoLoopSession(fake.player, 2000L to 8000L)
        assertEquals(500L, fake.position)
        assertEquals(500L, fake.audioPosition)
        assertEquals(0L, fake.items[0].clippingConfiguration.startPositionMs)
        assertEquals(0, fake.seeks)
        assertEquals(1, fake.resets)
        session.close()
    }

    @Test fun restoresClipAfterQueueReordering() {
        val fake = Fake()
        val original = fake.items[0]
        val session = NativeAutoLoopSession(fake.player, 2000L to 8000L)
        java.util.Collections.swap(fake.items, 0, 1)
        fake.index = 1
        session.close()
        assertEquals(original, fake.items[1])
        assertEquals(1, fake.index)
    }

    @Test fun switchingTrackDoesNotSeekTheNewTrack() {
        val fake = Fake()
        val original = fake.items[0]
        val session = NativeAutoLoopSession(fake.player, 2000L to 8000L)
        fake.index = 1
        fake.position = 500
        assertFalse(session.isCurrent())
        session.close()
        assertEquals(original, fake.items[0])
        assertEquals(500, fake.position)
        assertEquals(0, fake.seeks)
        assertEquals(2, fake.resets)
    }

    @Test fun previousReplaysTheIntroWithoutReplacingTheSource() {
        val fake = Fake()
        val session = NativeAutoLoopSession(fake.player, 2000L to 8000L)
        fake.position = 3000
        assertTrue(session.restartFromBeginning())
        assertEquals(0L, fake.position)
        assertEquals(0L, fake.sourceStartMs)
        assertEquals(1, fake.resets)
        assertTrue(session.isCurrent())
        assertEquals(12000L, AutoLoopState.windowFor(fake.items[0])!!.trackDurationMs)
        session.close()
    }

    @Test fun pressingPreviousAgainNearTheIntroCanSelectThePreviousTrack() {
        val fake = Fake()
        fake.index = 1
        val session = NativeAutoLoopSession(fake.player, 2000L to 8000L)
        fake.position = 7000L
        assertTrue(session.restartFromPreviousCommand())
        assertEquals(0L, fake.position)
        assertFalse(session.restartFromPreviousCommand())
        assertEquals(1, fake.seeks)
        assertEquals(1, fake.resets)
        session.close()
    }

    @Test fun previousOnTheFirstTrackKeepsTheIntroInsteadOfSeekingTheLoopDefault() {
        val fake = Fake()
        fake.position = 0L
        val session = NativeAutoLoopSession(fake.player, 2000L to 8000L)
        assertTrue(session.restartFromPreviousCommand())
        assertEquals(0L, fake.position)
        assertEquals(1, fake.seeks)
        session.close()
    }

    @Test fun respectsRepeatModeChangedByUser() {
        val fake = Fake()
        val session = NativeAutoLoopSession(fake.player, 2000L to 8000L)
        fake.repeat = Player.REPEAT_MODE_OFF
        session.close()
        assertEquals(Player.REPEAT_MODE_OFF, fake.repeat)
    }
}
