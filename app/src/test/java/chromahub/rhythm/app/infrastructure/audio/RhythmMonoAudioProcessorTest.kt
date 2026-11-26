/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.media3.common.util.UnstableApi
class RhythmMonoAudioProcessorTest {

    private fun createStereoFormat(sampleRate: Int = 44100): AudioProcessor.AudioFormat {
        return AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
    }

    private fun createFloatFormat(sampleRate: Int = 44100): AudioProcessor.AudioFormat {
        return AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_FLOAT)
    }

    private fun createByteBufferWithShorts(vararg values: Short): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder())
        for (v in values) {
            buffer.putShort(v)
        }
        buffer.flip()
        return buffer
    }

    @Test
    fun configure_withPcm16Bit_succeedsAndSetsActive() {
        val processor = RhythmMonoAudioProcessor()
        val format = createStereoFormat()
        
        val outputFormat = processor.configure(format)
        
        assertEquals(format, outputFormat)
        // isActive must be true even if processor is currently disabled (bypassed),
        // ensuring ExoPlayer includes it in the active audio pipeline.
        assertTrue("Processor must be active in pipeline even when disabled", processor.isActive())
    }

    @Test
    fun configure_withFloatAnd24Bit_succeedsAndSetsActive() {
        val processor = RhythmMonoAudioProcessor()
        for (enc in intArrayOf(C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT)) {
            val format = AudioProcessor.AudioFormat(48000, 2, enc)
            val outputFormat = processor.configure(format)
            assertEquals(format, outputFormat)
            assertTrue(processor.isActive())
        }
    }

    @Test
    fun configure_withUnsupportedEncoding_throwsUnhandledAudioFormatException() {
        val processor = RhythmMonoAudioProcessor()
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_MP3)

        assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
            processor.configure(format)
        }
        assertFalse(processor.isActive())
    }

    @Test
    fun isBypassed_reflectsEnabledAndChannelState() {
        val processor = RhythmMonoAudioProcessor()
        processor.configure(createStereoFormat())
        
        // Disabled by default
        assertFalse(processor.isEnabled())
        assertTrue(processor.isBypassed())
        
        // Enabled
        processor.setEnabled(true)
        assertTrue(processor.isEnabled())
        assertFalse(processor.isBypassed())
        
        // Disabled again
        processor.setEnabled(false)
        assertFalse(processor.isEnabled())
        assertTrue(processor.isBypassed())
    }

    @Test
    fun queueInput_whenBypassed_passesAudioThroughUnmodified() {
        val processor = RhythmMonoAudioProcessor()
        processor.configure(createStereoFormat())
        processor.setEnabled(false)

        val input = createByteBufferWithShorts(1000, 3000, -2000, 4000)
        processor.queueInput(input)
        
        // Verify input buffer was consumed
        assertEquals(0, input.remaining())
        
        val output = processor.output
        assertEquals(8, output.remaining())
        val shortBuffer = output.asShortBuffer()
        assertEquals(1000.toShort(), shortBuffer.get())
        assertEquals(3000.toShort(), shortBuffer.get())
        assertEquals((-2000).toShort(), shortBuffer.get())
        assertEquals(4000.toShort(), shortBuffer.get())
    }

    @Test
    fun queueInput_whenEnabled_downmixesStereoToMono() {
        val processor = RhythmMonoAudioProcessor()
        processor.configure(createStereoFormat())
        processor.setEnabled(true)

        // Left = 1000, Right = 3000 -> Mono = 2000
        // Left = -2000, Right = 4000 -> Mono = 1000
        val input = createByteBufferWithShorts(1000, 3000, -2000, 4000)
        processor.queueInput(input)
        
        assertEquals(0, input.remaining())
        
        val output = processor.output
        assertEquals(8, output.remaining())
        val shortBuffer = output.asShortBuffer()
        assertEquals(2000.toShort(), shortBuffer.get())
        assertEquals(2000.toShort(), shortBuffer.get())
        assertEquals(1000.toShort(), shortBuffer.get())
        assertEquals(1000.toShort(), shortBuffer.get())
    }

    @Test
    fun dynamicSwitching_togglesBetweenPassthroughAndDownmixing() {
        val processor = RhythmMonoAudioProcessor()
        processor.configure(createStereoFormat())

        // 1. Initially disabled -> passthrough
        processor.setEnabled(false)
        val input1 = createByteBufferWithShorts(1000, 5000)
        processor.queueInput(input1)
        var out1 = processor.output.asShortBuffer()
        assertEquals(1000.toShort(), out1.get())
        assertEquals(5000.toShort(), out1.get())

        // 2. User enables mono audio -> downmixes (1000 + 5000) / 2 = 3000
        processor.setEnabled(true)
        val input2 = createByteBufferWithShorts(1000, 5000)
        processor.queueInput(input2)
        var out2 = processor.output.asShortBuffer()
        assertEquals(3000.toShort(), out2.get())
        assertEquals(3000.toShort(), out2.get())

        // 3. User disables mono audio again -> passthrough
        processor.setEnabled(false)
        val input3 = createByteBufferWithShorts(200, 800)
        processor.queueInput(input3)
        var out3 = processor.output.asShortBuffer()
        assertEquals(200.toShort(), out3.get())
        assertEquals(800.toShort(), out3.get())
    }

    @Test
    fun childProcessor_delegatesToParentProcessor() {
        val parent = RhythmMonoAudioProcessor()
        val child = RhythmMonoAudioProcessor().apply { setParent(parent) }
        child.configure(createStereoFormat())

        assertFalse(child.isEnabled())
        assertTrue(child.isBypassed())

        // Enable on parent -> child reflects state immediately
        parent.setEnabled(true)
        assertTrue(child.isEnabled())
        assertFalse(child.isBypassed())

        val input = createByteBufferWithShorts(4000, 6000)
        child.queueInput(input)
        val out = child.output.asShortBuffer()
        assertEquals(5000.toShort(), out.get())
        assertEquals(5000.toShort(), out.get())

        // Disable on parent
        parent.setEnabled(false)
        assertFalse(child.isEnabled())
        assertTrue(child.isBypassed())
    }
}
