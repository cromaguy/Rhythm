package chromahub.rhythm.app.infrastructure.service.player.autoloop

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AutoLoopPcmFramesTest {
    private fun block(first: Int, frames: Int, channels: Int = 1): ByteBuffer =
        ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(frames) { frame -> repeat(channels) { putShort((first + frame).toShort()) } }
        }

    @Test fun cutsBothEdgesOfAStraddlingPacket() {
        val buffer = block(0, 8000)
        val timestamp = AutoLoopPcmFrames.trim(buffer, 2, 8000, 0, 501000, 903000)
        assertEquals(501000, timestamp)
        assertEquals(3216 * 2, buffer.position())
        buffer.flip()
        assertEquals(4008.toShort(), buffer.short)
        buffer.position(buffer.limit() - 2)
        assertEquals(7223.toShort(), buffer.short)
    }

    @Test fun respectsMidTrackSeekInsteadOfReplayingTheStartOfThePacket() {
        val buffer = block(4000, 800)
        val timestamp = AutoLoopPcmFrames.trim(buffer, 2, 8000, 500000, 537000, 1000000)
        assertEquals(537000, timestamp)
        buffer.flip()
        assertEquals(4296.toShort(), buffer.short)
        assertEquals(504 * 2 - 2, buffer.remaining())
    }

    @Test fun fractional44100HzBoundariesKeepWholeFrames() {
        val buffer = block(0, 44100)
        AutoLoopPcmFrames.trim(buffer, 2, 44100, 0, 501000, 903000)
        assertEquals((39823 - 22095) * 2, buffer.position())
        buffer.flip()
        assertEquals(22095.toShort(), buffer.short)
        buffer.position(buffer.limit() - 2)
        assertEquals(39822.toShort(), buffer.short)
    }

    @Test fun repeatedStereoSegmentsHaveExactlyTheSameFrameCount() {
        repeat(3) {
            val output = mutableListOf<Short>()
            for (first in 0 until 12000 step 800) {
                val buffer = block(first, 800, 2)
                AutoLoopPcmFrames.trim(buffer, 4, 8000, first * 125L, 501000, 1003000)
                buffer.flip()
                while (buffer.hasRemaining()) output += buffer.short
            }
            assertEquals(4016 * 2, output.size)
            assertEquals(4008.toShort(), output.first())
            assertEquals(8023.toShort(), output.last())
        }
    }
}
