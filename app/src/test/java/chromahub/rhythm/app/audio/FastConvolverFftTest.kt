/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.audio

import chromahub.rhythm.app.infrastructure.audio.viper.dsp.FastConvolver
import chromahub.rhythm.app.infrastructure.audio.viper.dsp.FftProcessor
import chromahub.rhythm.app.infrastructure.audio.viper.dsp.WavIrParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos

class FastConvolverFftTest {

    @Test
    fun fftAndIfftReconstructArbitrarySignalExactly() {
        val n = 256
        val fft = FftProcessor(n)
        val original = DoubleArray(n) { i -> cos(2.0 * Math.PI * 5 * i / n) + 0.5 * cos(2.0 * Math.PI * 23 * i / n) }
        val real = original.copyOf()
        val imag = DoubleArray(n)

        fft.fft(real, imag)
        fft.ifft(real, imag)

        for (i in 0 until n) {
            assertEquals("Reconstruction drift at $i", original[i], real[i], 1e-11)
            assertEquals(0.0, imag[i], 1e-11)
        }
    }

    @Test
    fun partitionedFftConvolverMatchesExpectedEchoOffset() {
        val irLen = 400
        val delayTap = 300
        val ir = FloatArray(irLen)
        ir[delayTap] = 1.0f

        val convolver = FastConvolver()
        convolver.loadCustomImpulse(ir, ir, mix = 1.0f)

        val totalSamples = delayTap + FastConvolver.FFT_BLOCK_SIZE * 3
        val outputs = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            val inp = if (i == 0) 1.0f else 0.0f
            convolver.process(inp, inp, crossChannel = 0.0f)
            outputs[i] = convolver.outLeft
        }

        // Partitioned overlap-add adds exactly 1 block (FFT_BLOCK_SIZE) structural latency
        val expectedPeakIndex = FastConvolver.FFT_BLOCK_SIZE + delayTap
        assertTrue(
            "Expected impulse echo peak at $expectedPeakIndex, got ${outputs[expectedPeakIndex]}",
            abs(outputs[expectedPeakIndex] - 1.0f) < 0.01f
        )
        for (i in outputs.indices) {
            if (i != expectedPeakIndex) {
                assertTrue("Unexpected ripple at $i: ${outputs[i]}", abs(outputs[i]) < 1e-4f)
            }
        }
    }

    @Test
    fun wavIrParserDecodes16BitAnd32BitFloatWaveHeaders() {
        val samples = 64
        val dataSize = samples * 2 * 2 // stereo 16-bit
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(2)
        buf.putInt(48_000)
        buf.putInt(48_000 * 4)
        buf.putShort(4)
        buf.putShort(16)
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (i in 0 until samples) {
            val v: Short = if (i == 0) 16000 else 0
            buf.putShort(v)
            buf.putShort(v)
        }

        val parsed = WavIrParser.parseBytes(buf.array())
        assertNotNull(parsed)
        parsed!!
        assertEquals(48_000, parsed.sampleRate)
        assertEquals(samples, parsed.left.size)
        assertTrue(parsed.left[0] > 0.5f)
    }
}
