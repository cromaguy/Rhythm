/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.nativeaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAudioModuleTest {

    @Test
    fun `safety guard blocks Tecno BG6 on Android 13 and JVM hosts`() {
        assertTrue(NativeAudioSafetyGuard.isTecnoRestrictedDevice(33, "TECNO", "BG6-H6116"))
        assertFalse(NativeAudioSafetyGuard.isTecnoRestrictedDevice(34, "TECNO", "BG6-H6116"))
        assertFalse(NativeAudioSafetyGuard.isTecnoRestrictedDevice(33, "motorola", "moto g67 power 5G"))
        assertFalse(NativeAudioSafetyGuard.canLoadNativeLibrary())
    }

    @Test
    fun `NativeDsdDecimator fallback converts DC silence cleanly`() {
        NativeDsdDecimator(channelCount = 2, decimationFactor = 16, lsbFirst = true).use { dec ->
            // Alternating 0x55 pattern encodes zero-amplitude 1-bit delta-sigma idle pattern
            val silenceBytes = ByteArray(128) { 0x55.toByte() }
            val out = FloatArray(128)
            val written = dec.processToFloats(
                input = silenceBytes,
                offset = 0,
                length = silenceBytes.size,
                planar = false,
                blockBytesPerChannel = 0,
                outputPcm = out
            )
            assertTrue(written > 0)
            for (i in 0 until written) {
                assertTrue("Expected near-zero DC output, got ${out[i]}", kotlin.math.abs(out[i]) < 0.05f)
            }
        }
    }

    @Test
    fun `NativeSincResampler fallback resamples 44100 to 48000 preserving DC gain`() {
        NativeSincResampler(channelCount = 2, inputRateHz = 44_100, outputRateHz = 48_000).use { resampler ->
            val inputFrames = 441
            val input = FloatArray(inputFrames * 2) { 0.5f }
            val out = FloatArray(resampler.maxOutputFrames(inputFrames) * 2)
            val outFrames = resampler.processInterleaved(input, inputFrames, out)
            assertTrue("Expected ~480 frames, got $outFrames", outFrames in 465..495)
            // Steady-state middle sample should closely track 0.5f DC input
            val midSample = out[(outFrames / 2) * 2]
            assertEquals(0.5f, midSample, 0.05f)
        }
    }
}
