/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.AudioProcessor
import kotlin.math.PI

/**
 * Rhythm Bass Boost Processor - Real-time bass enhancement
 * 
 * Uses a high-quality IIR low-pass filter with variable gain to enhance low frequencies.
 * Provides natural bass enhancement without file I/O latency, optimized for the Rhythm player.
 * 
 * Algorithm: Single-pole IIR low-pass filter + adaptive gain
 * Cutoff frequency: 150Hz
 * Gain range: 1.0x to 4.0x based on strength (0-1000)
 * Latency: <1ms per buffer
 */
@OptIn(UnstableApi::class)
class RhythmBassBoostProcessor : RhythmAudioProcessor() {
    
    companion object {
        private const val TAG = "RhythmBassBoost"
        private const val BASS_CUTOFF_FREQ = 150.0 // Hz - Optimized for music playback
    }
    
    // Parent processor for dynamic configuration sharing (crossfade thread safety)
    @Volatile
    private var parentProcessor: RhythmBassBoostProcessor? = null

    // Bass boost strength (0-1000, where 1000 = maximum boost)
    @Volatile
    private var strength: Short = 0
    @Volatile
    private var enabled: Boolean = false
    
    // Filter state (per channel) - maintains continuity across buffers
    private var prevSample = FloatArray(2) // Support stereo
    private var filterCoeff = 0f
    private var filterCoeffValid = false
    
    /**
     * Set the parent processor for dynamic synchronization
     */
    fun setParent(parent: RhythmBassBoostProcessor?) {
        this.parentProcessor = parent
    }

    /**
     * Enable or disable bass boost
     */
    fun setEnabled(enable: Boolean) {
        Log.d(TAG, "Bass boost enabled: $enable")
        this.enabled = enable
        if (!enable) {
            // Reset filter state to avoid artifacts
            prevSample.fill(0f)
        }
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val outputFormat = super.configure(inputAudioFormat)
        // Reset filter state and invalidate coefficient when format changes
        filterCoeffValid = false
        if (prevSample.size != inputAudioFormat.channelCount) {
            prevSample = FloatArray(inputAudioFormat.channelCount)
        } else {
            prevSample.fill(0f)
        }
        Log.d(TAG, "configure() - resetting filter state for ${inputAudioFormat.channelCount} channels")
        return outputFormat
    }

    override fun flush() {
        super.flush()
        onProcessorFlushed()
        Log.d(TAG, "flush() - filter state reset")
    }

    fun onProcessorFlushed() {
        // Reset filter state on flush to prevent artifacts across stream boundaries
        prevSample.fill(0f)
    }
    
    /**
     * Set bass boost strength
     * @param strength Strength value from 0 to 1000
     */
    fun setStrength(strength: Short) {
        this.strength = strength.coerceIn(0, 1000)
        updateFilterCoeff()
        Log.d(TAG, "Bass boost strength set to: ${this.strength}")
    }
    
    /**
     * Get current strength
     */
    fun getStrength(): Short = parentProcessor?.getStrength() ?: strength
    
    override fun isEnabled(): Boolean = parentProcessor?.isEnabled() ?: enabled

    override fun isBypassed(): Boolean {
        return !isEnabled() || getStrength() == 0.toShort()
    }
    
    /**
     * Update filter coefficient based on current sample rate.
     * Safety: validates sample rate before computing filter coefficient.
     */
    private fun updateFilterCoeff() {
        if (sampleRate <= 0) {
            Log.w(TAG, "Invalid sample rate: $sampleRate, skipping filter update")
            filterCoeffValid = false
            return
        }
        val rc = 1.0 / (2.0 * PI * BASS_CUTOFF_FREQ)
        val dt = 1.0 / sampleRate
        filterCoeff = (dt / (rc + dt)).toFloat()
        filterCoeffValid = true
    }

    /**
     * Soft-knee limiter using saturating math to prevent hard clipping distortion.
     * Gracefully transitions from linear (|x| <= 1) to compression (|x| > 1).
     * Formula: sign(x) * (1.0 - 1.0/(1.0 + abs(x)))
     * At x=0: output=0. At x=1: output≈0.5. As x→∞: output→1.0 (saturates smoothly).
     * This replaces hard clipping which creates audible aliasing and distortion.
     */
    private fun softLimitSample(sample: Float): Float {
        if (sample == 0f) return 0f
        val absValue = kotlin.math.abs(sample)
        
        // Check if we're in linear region (no limiting needed)
        val threshold = 0.8f
        if (absValue <= threshold) {
            return sample
        }
        
        // In compression region: apply soft-knee limiting to prevent hard clipping
        // Gracefully transitions from linear to an asymptote of 1.0
        val over = absValue - threshold
        val maxOver = 1.0f - threshold
        val limited = threshold + over / (1.0f + over / maxOver)
        
        return kotlin.math.sign(sample) * limited
    }
    
    override fun processSamples(samples: ShortArray, sampleCount: Int) {
        if (!isEnabled() || getStrength() == 0.toShort()) return
        val doubles = acquireDoubleArray(sampleCount)
        try {
            for (i in 0 until sampleCount) doubles[i] = samples[i] / 32768.0
            processDoubleSamples(doubles, sampleCount)
            for (i in 0 until sampleCount) {
                samples[i] = (doubles[i].coerceIn(-1.0, 1.0) * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            }
        } finally {
            releaseDoubleArray(doubles)
        }
    }

    override fun processDoubleSamples(samples: DoubleArray, sampleCount: Int) {
        val currentEnabled = isEnabled()
        val currentStrength = getStrength()
        if (!currentEnabled || currentStrength == 0.toShort()) return

        if (filterCoeff == 0f || !filterCoeffValid) {
            updateFilterCoeff()
        }

        val gain = when {
            currentStrength == 0.toShort() -> 1.0
            currentStrength <= 100 -> 1.0 + (currentStrength / 100.0) * 0.3
            currentStrength <= 500 -> 1.3 + ((currentStrength - 100) / 400.0) * 0.9
            else -> 2.2 + ((currentStrength - 500) / 500.0) * 1.8
        }

        val coeff = filterCoeff.toDouble()
        val activeChannels = if (channelCount > 0) channelCount else 1

        for (i in 0 until sampleCount) {
            val channelIdx = i % activeChannels
            val input = samples[i]
            val prev = prevSample.getOrElse(channelIdx) { 0f }.toDouble()
            val lowPass = prev + coeff * (input - prev)
            if (channelIdx < prevSample.size) {
                prevSample[channelIdx] = lowPass.toFloat()
            }
            val output = input + lowPass * (gain - 1.0)
            samples[i] = softLimitSample(output.toFloat()).toDouble()
        }
    }
}
