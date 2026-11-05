package chromahub.rhythm.app.infrastructure.service.player.autoloop

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AutoLoopDecodedFramesTest {
    @Test fun trimsTheFirstAndLastDecodedStereoFramesWithANonzeroBufferOffset() {
        val data = ByteBuffer.allocate(16 + 800 * 4).order(ByteOrder.LITTLE_ENDIAN)
        data.position(16)
        repeat(800) { data.putShort((4000 + it).toShort()).putShort((-4000 - it).toShort()) }
        data.flip()
        data.position(16)
        val timestamp = AutoLoopDecodedFrames.slice(data, 4, 8000, 500000, 537000, 563000)
        assertEquals(537000L, timestamp)
        assertEquals(208 * 4, data.remaining())
        assertEquals(4296.toShort(), data.short)
        assertEquals((-4296).toShort(), data.short)
        data.position(data.limit() - 4)
        assertEquals(4503.toShort(), data.short)
        assertEquals((-4503).toShort(), data.short)
    }

    @Test fun prerollAndTailEntirelyOutsideTheLoopProduceNoAudio() {
        for (timestamp in listOf(0L, 1100000L)) {
            val data = ByteBuffer.allocate(800 * 2)
            AutoLoopDecodedFrames.slice(data, 2, 8000, timestamp, 501000, 1003000)
            assertFalse(data.hasRemaining())
        }
    }

    @Test fun fractional44100HzBoundaryRetainsWholeFloatFrames() {
        val data = ByteBuffer.allocate(44100 * 8)
        val timestamp = AutoLoopDecodedFrames.slice(data, 8, 44100, 0, 501000, 903000)
        assertEquals(22095 * 1_000_000L / 44100, timestamp)
        assertEquals(22095 * 8, data.position())
        assertEquals((39823 - 22095) * 8, data.remaining())
    }

    @Test fun rendererTimestampOffsetDoesNotChangeTheSelectedFrames() {
        val data = ByteBuffer.allocate(800 * 2)
        val offset = 6_000_000_000_000L
        val timestamp = AutoLoopDecodedFrames.slice(data, 2, 8000,
            offset + 500000, offset + 537000, offset + 1003000)
        assertEquals(offset + 537000, timestamp)
        assertEquals(296 * 2, data.position())
        assertEquals(504 * 2, data.remaining())
    }
}
