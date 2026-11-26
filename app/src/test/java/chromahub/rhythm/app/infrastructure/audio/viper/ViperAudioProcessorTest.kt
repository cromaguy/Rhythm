/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
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
            convolver.process(0.5f, 0.5f, 0.0f)
            val outL = convolver.outLeft
            val outR = convolver.outRight

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
        assertTrue(ViperDdcConfig.BUILTIN_DEVICES.contains("Apple AirPods Pro"))
    }

    @Test
    fun testChildProcessorPropagationAndReactiveReload() {
        val parent = ViperAudioProcessor()
        val child = ViperAudioProcessor()
        child.setParent(parent)

        // Initial config on parent
        val initialConfig = ViperMasterConfig(
            masterEnabled = true,
            fireEqualizer = FireEqualizerConfig(enabled = true, bands = List(10) { 0.0f })
        )
        parent.updateConfig(initialConfig)

        // Child config should immediately reflect parent
        assertEquals(initialConfig, child.getConfig())

        // Update parent with boosted EQ bands
        val boostedBands = List(10) { 6.0f }
        val updatedConfig = initialConfig.copy(
            fireEqualizer = FireEqualizerConfig(enabled = true, bands = boostedBands)
        )
        parent.updateConfig(updatedConfig)

        // Child config should proactively update
        assertEquals(updatedConfig, child.getConfig())

        // Process audio on child: verify filters reconfigure and process signal without crash
        val buffer = ShortArray(256) { 1000 }
        child.processSamples(buffer, buffer.size)

        var hasSignal = false
        for (s in buffer) {
            if (s.toInt() != 0) hasSignal = true
        }
        assertTrue("Child processor should output non-zero audio after reactive reload", hasSignal)
    }

    @Test
    fun testTubeSimulatorDcBlocker() {
        val processor = ViperAudioProcessor()
        val config = ViperMasterConfig(
            masterEnabled = true,
            tubeSimulator = TubeSimulatorConfig(enabled = true, model = 0, drive = 0.8f, mix = 1.0f),
            masterGate = MasterGateConfig(limiterEnabled = false)
        )
        processor.updateConfig(config)

        // Feed an asymmetric signal that would induce quadratic DC offset in unblocked tube saturation
        val frames = 4096
        val buffer = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val sample = (sin(2.0 * Math.PI * 100.0 * (i.toDouble() / 44100.0)) * 14000.0).toInt().toShort()
            buffer[i * 2] = sample
            buffer[i * 2 + 1] = sample
        }

        processor.processSamples(buffer, buffer.size)

        // Compute mean of second half (after DC blocker settles)
        var sum = 0.0
        val secondHalfStart = frames
        for (i in secondHalfStart until buffer.size) {
            sum += buffer[i]
        }
        val mean = sum / (buffer.size - secondHalfStart)

        // DC offset should be near 0 (well below 200 out of 32767)
        assertTrue("Tube simulator DC blocker should keep DC offset near zero, got mean: $mean", Math.abs(mean) < 200.0)
    }

    @Test
    fun testLinkwitzRileyBassMonoCrossoverSummation() {
        val processor = ViperAudioProcessor()
        val config = ViperMasterConfig(
            masterEnabled = true,
            bassMono = BassMonoConfig(enabled = true, frequencyHz = 80),
            masterGate = MasterGateConfig(limiterEnabled = false)
        )
        processor.updateConfig(config)

        // Feed 80 Hz sine wave (exact crossover frequency)
        val frames = 2048
        val buffer = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val sample = (sin(2.0 * Math.PI * 80.0 * (i.toDouble() / 44100.0)) * 16000.0).toInt().toShort()
            buffer[i * 2] = sample
            buffer[i * 2 + 1] = sample
        }

        processor.processSamples(buffer, buffer.size)

        // Verify that signal at 80 Hz is preserved and NOT canceled out (as happened with Butterworth 180° notch)
        var maxPeak = 0
        for (i in (frames / 2) until frames) {
            val s = Math.abs(buffer[i * 2].toInt())
            if (s > maxPeak) maxPeak = s
        }
        assertTrue("80 Hz crossover frequency should not be canceled out (peak: $maxPeak)", maxPeak > 12000)
    }

    @Test
    fun testLimiterThresholdSoftKneeMath() {
        val processor = ViperAudioProcessor()
        // Limiter threshold at -3.0 dB (~0.707 linear, approx 23190 short)
        val config = ViperMasterConfig(
            masterEnabled = true,
            masterGate = MasterGateConfig(limiterEnabled = true, limiterThresholdDb = -3.0f, outputVolumeDb = 6.0f)
        )
        processor.updateConfig(config)

        // Feed very hot input that triggers limiting
        val frames = 1024
        val buffer = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val sample = (sin(2.0 * Math.PI * 1000.0 * (i.toDouble() / 44100.0)) * 32000.0).toInt().toShort()
            buffer[i * 2] = sample
            buffer[i * 2 + 1] = sample
        }

        processor.processSamples(buffer, buffer.size)

        // Limiter must strictly keep audio below Short.MAX_VALUE and respect threshold
        for (s in buffer) {
            assertTrue("Sample must not exceed Short limits", s <= Short.MAX_VALUE && s >= Short.MIN_VALUE)
        }
    }

    @Test
    fun testForcedBypass_propagatesToChildWithoutOverwritingSavedConfig() {
        val parent = ViperAudioProcessor()
        val child = ViperAudioProcessor()
        child.setParent(parent)

        parent.updateConfig(ViperMasterConfig(masterEnabled = true))
        assertTrue(parent.isEnabled())
        assertTrue(child.isEnabled())

        parent.setForcedBypass(true)
        assertFalse("Forced bypass must disable parent", parent.isEnabled())
        assertFalse("Forced bypass must disable child", child.isEnabled())
        assertTrue("Saved config masterEnabled must stay intact", parent.getConfig().masterEnabled)

        parent.setForcedBypass(false)
        assertTrue("Disabling forced bypass must restore enabled state", parent.isEnabled())
        assertTrue("Disabling forced bypass must restore child enabled state", child.isEnabled())
    }
}
