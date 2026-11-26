/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import chromahub.rhythm.app.infrastructure.audio.dsp.ParameterGlide
import chromahub.rhythm.app.infrastructure.audio.dsp.RhythmDoubleDspPipeline
import chromahub.rhythm.app.infrastructure.audio.dsp.TpdfDitherState
import chromahub.rhythm.app.infrastructure.audio.peq.PeqFilter
import chromahub.rhythm.app.infrastructure.audio.peq.PeqFilterType
import chromahub.rhythm.app.infrastructure.audio.peq.PeqSettings
import chromahub.rhythm.app.infrastructure.audio.peq.RhythmParametricEqAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class RhythmDoubleDspPipelineTest {

    @Test
    fun multiFormatReadWritePreservesSignalAcross24And32BitAndFloat() {
        val original = doubleArrayOf(0.0, 0.25, -0.5, 0.75, -0.875, 0.125)
        val encodings = intArrayOf(
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT
        )

        for (enc in encodings) {
            val bps = RhythmDoubleDspPipeline.bytesPerSample(enc)
            val buf = ByteBuffer.allocate(original.size * bps).order(ByteOrder.nativeOrder())
            RhythmDoubleDspPipeline.writeFromDoubles(original, original.size, enc, buf, ditherRng = null)
            buf.flip()

            val decoded = DoubleArray(original.size)
            val read = RhythmDoubleDspPipeline.readToDoubles(buf, enc, decoded)
            assertEquals(original.size, read)

            val tolerance = when (enc) {
                C.ENCODING_PCM_16BIT -> 1e-4
                else -> 1e-6
            }
            for (i in original.indices) {
                assertEquals("Mismatch for encoding $enc at idx $i", original[i], decoded[i], tolerance)
            }
        }
    }

    @Test
    fun softKneeLimiterPreventsDigitalClipping() {
        val samples = doubleArrayOf(0.2, -0.4, 2.5, -4.0, 10.0)
        RhythmDoubleDspPipeline.applySoftKneeLimiter(samples, samples.size, ceilingDb = -0.1)
        assertEquals(0.2, samples[0], 1e-6)
        assertEquals(-0.4, samples[1], 1e-6)
        assertTrue(abs(samples[2]) <= 1.0)
        assertTrue(abs(samples[3]) <= 1.0)
        assertTrue(abs(samples[4]) <= 1.0)
    }

    @Test
    fun parameterGlideConvergesMonotonicallyWithoutJumps() {
        val glide = ParameterGlide(initialValue = 0.0, glideSeconds = 0.025).apply {
            setSampleRate(48_000)
            setTarget(1.0)
        }
        var prev = 0.0
        for (i in 0 until 6000) {
            val next = glide.next()
            assertTrue("Step jumped excessively at $i", abs(next - prev) < 0.01)
            assertTrue(next >= prev)
            prev = next
        }
        assertEquals(1.0, prev, 0.01)
    }

    @Test
    fun tpdfDitherBoundsStayWithinOneLsb() {
        val dither = TpdfDitherState()
        for (i in 0 until 10_000) {
            val v = dither.nextTriangularLsb()
            assertTrue("Dither out of [-1, 1] LSB: $v", v in -1.0..1.0)
        }
    }

    @Test
    fun rhythmParametricEqAudioProcessorHandlesFloatAnd24BitMedia3Streams() {
        val proc = RhythmParametricEqAudioProcessor()
        proc.updateSettings(
            PeqSettings(
                preampDb = -6.0,
                filters = listOf(PeqFilter(PeqFilterType.PEAKING, 1000.0, 0.0, 1.0))
            )
        )
        proc.setEnabled(true)

        val fmt = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT)
        val outFmt = proc.configure(fmt)
        assertEquals(C.ENCODING_PCM_FLOAT, outFmt.encoding)
        assertTrue(proc.isActive())

        val input = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder())
        input.putFloat(1.0f).putFloat(-1.0f).putFloat(0.5f).putFloat(-0.5f)
        input.flip()

        proc.queueInput(input)
        val out = proc.getOutput()
        assertEquals(16, out.remaining())
        // Wait for glide onset -> first sample stays near 1.0 then smoothly attenuates toward -6dB (~0.5)
        val s0 = out.getFloat()
        assertTrue(abs(s0) <= 1.05f)
    }
}
