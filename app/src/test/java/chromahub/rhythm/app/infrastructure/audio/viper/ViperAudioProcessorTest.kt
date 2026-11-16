/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.viper

import chromahub.rhythm.app.infrastructure.audio.viper.dsp.BiquadFilter
import chromahub.rhythm.app.infrastructure.audio.viper.dsp.FastConvolver
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class ViperAudioProcessorTest {

    @Test
    fun testBiquadFilterProcessing() {
        val filter = BiquadFilter()
        filter.configure(
            type = BiquadFilter.Type.LOW_PASS,
            sampleRate = 44100,
            frequency = 1000.0f,
            q = 0.707f,
            gainDb = 0.0f
        )

        var lastOutL = 0.0f
        var lastOutR = 0.0f

        // Feed sine wave
        for (i in 0 until 500) {
            val sampleIn = sin(2.0 * Math.PI * 440.0 * (i.toDouble() / 44100.0)).toFloat()
            lastOutL = filter.processLeft(sampleIn)
            lastOutR = filter.processRight(sampleIn)

            assertFalse("Filter output left is NaN", lastOutL.isNaN())
            assertFalse("Filter output right is NaN", lastOutR.isNaN())
            assertTrue("Filter output exceeds bounds", Math.abs(lastOutL) <= 2.0f)
        }

        assertTrue("Filter output should produce non-zero signal", Math.abs(lastOutL) > 0.0001f)
    }

    @Test
    fun testFastConvolverPresets() {
        val convolver = FastConvolver()
        for (preset in FastConvolver.BUILTIN_PRESETS) {
            convolver.loadPreset(preset, 44100)
            val (outL, outR) = convolver.process(0.5f, 0.5f, 0.0f)

            assertFalse("Convolver output left is NaN for $preset", outL.isNaN())
            assertFalse("Convolver output right is NaN for $preset", outR.isNaN())
            assertTrue("Convolver output left magnitude is valid", Math.abs(outL) <= 1.0f)
        }
    }

    @Test
    fun testViperMasterConfigDefaults() {
        val config = ViperMasterConfig()
        assertFalse(config.masterEnabled)
        assertTrue(config.masterGate.limiterEnabled)
        assertEquals(0.0f, config.masterGate.outputVolumeDb, 0.001f)
        assertEquals(0.0f, config.masterGate.channelPan, 0.001f)
        assertEquals(60, config.viperBass.frequencyHz)
        assertEquals(3.5f, config.viperClarity.gainDb, 0.001f)
    }

    @Test
    fun testViperAudioProcessorBufferProcessing() {
        val processor = ViperAudioProcessor()
        val config = ViperMasterConfig(
            masterEnabled = true,
            masterGate = MasterGateConfig(limiterEnabled = true, outputVolumeDb = 2.0f),
            viperBass = ViperBassConfig(enabled = true, gainDb = 6.0f),
            viperClarity = ViperClarityConfig(enabled = true, gainDb = 4.0f),
            tubeSimulator = TubeSimulatorConfig(enabled = true, drive = 0.5f, mix = 0.4f),
            fieldSurround = FieldSurroundConfig(enabled = true, widening = 0.5f)
        )
        processor.updateConfig(config)

        // Generate 1024 frames of 16-bit PCM stereo audio (2048 shorts)
        val buffer = ShortArray(2048)
        for (i in 0 until 1024) {
            val sample = (sin(2.0 * Math.PI * 440.0 * (i.toDouble() / 44100.0)) * 16000.0).toInt().toShort()
            buffer[i * 2] = sample
            buffer[i * 2 + 1] = sample
        }

        processor.processSamples(buffer, buffer.size)

        // Verify output buffer
        for (i in buffer.indices) {
            val s = buffer[i]
            assertTrue("Sample within 16-bit limits", s >= Short.MIN_VALUE && s <= Short.MAX_VALUE)
        }

        // Verify audio was actively processed and not zeroed
        var maxSample = 0
        for (s in buffer) {
            if (Math.abs(s.toInt()) > maxSample) maxSample = Math.abs(s.toInt())
        }
        assertTrue("Processed audio should contain signal", maxSample > 1000)
    }

    @Test
    fun testFireEqualizerAndDdcPresets() {
        val config = ViperMasterConfig(
            masterEnabled = true,
            fireEqualizer = FireEqualizerConfig(enabled = true, presetName = "Rock"),
            viperDdc = ViperDdcConfig(enabled = true, deviceName = "Apple AirPods Pro")
        )
        val activeCount = ViperAudioProcessor.countActiveEffects(config)
        // limiter + fireEq + viperDdc = 3 active effects
        assertEquals(3, activeCount)
        assertTrue(config.fireEqualizer.bands.size == 10)
        assertTrue(ViperDdcConfig.BUILTIN_DEVICES.containsKey("Apple AirPods Pro"))
    }
}
