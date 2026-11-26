/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/**
 * Rhythm Spatialization Processor - Real-time 3D audio enhancement
 * 
 * Uses advanced mid-side (M/S) processing for stereo widening to create immersive spatial audio.
 * Provides immediate spatial enhancement without file I/O latency, optimized for the Rhythm player.
 * 
 * Algorithm: Mid-side stereo widening with soft clipping
 * - Mid (M) = (L + R) / 2 (mono/center content)
 * - Side (S) = (L - R) / 2 (stereo/width content)
 * - Enhanced L = M + S * width
 * - Enhanced R = M - S * width
 * 
 * Width range: 1.0x to 3.0x based on strength (0-1000)
 * Works on: All devices with stereo audio
 * Latency: <1ms per buffer
 */
@OptIn(UnstableApi::class)
class RhythmSpatializationProcessor : RhythmAudioProcessor() {
    
    companion object {
        private const val TAG = "RhythmSpatialization"
    }
    
    // Parent processor for dynamic configuration sharing (crossfade thread safety)
    @Volatile
    private var parentProcessor: RhythmSpatializationProcessor? = null

    // Spatialization strength (0-1000)
    @Volatile
    private var strength: Short = 0
    @Volatile
    private var enabled: Boolean = false
    
    /**
     * Set the parent processor for dynamic synchronization
     */
    fun setParent(parent: RhythmSpatializationProcessor?) {
        this.parentProcessor = parent
    }

    /**
     * Enable or disable spatialization
     */
    fun setEnabled(enable: Boolean) {
        Log.d(TAG, "Spatialization enabled: $enable")
        this.enabled = enable
    }
    
    /**
     * Set spatialization strength
     * @param strength Strength value from 0 to 1000
     */
    fun setStrength(strength: Short) {
        this.strength = strength.coerceIn(0, 1000)
        Log.d(TAG, "Spatialization strength set to: ${this.strength}")
    }
    
    /**
     * Get current strength
     */
    fun getStrength(): Short = parentProcessor?.getStrength() ?: strength
    
    override fun isEnabled(): Boolean = parentProcessor?.isEnabled() ?: enabled

    override fun isBypassed(): Boolean {
        return !isEnabled() || getStrength() == 0.toShort() || channelCount != 2
    }
    
    /**
     * Check if spatialization is available for current audio format
     * @return true if audio is stereo (2 channels)
     */
    fun isAvailable(): Boolean = channelCount == 2
    
    override fun processSamples(samples: ShortArray, sampleCount: Int) {
        if (!isEnabled() || getStrength() == 0.toShort() || channelCount != 2) return
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
        if (!currentEnabled || currentStrength == 0.toShort() || channelCount != 2) return

        val width = when {
            currentStrength == 0.toShort() -> 1.0
            currentStrength <= 300 -> 1.0 + (currentStrength / 300.0) * 0.5
            currentStrength <= 700 -> 1.5 + ((currentStrength - 300) / 400.0) * 0.8
            else -> 2.3 + ((currentStrength - 700) / 300.0) * 0.7
        }

        for (i in 0 until sampleCount - 1 step 2) {
            val left = samples[i]
            val right = samples[i + 1]
            val mid = (left + right) * 0.5
            val wideSide = ((left - right) * 0.5) * width
            samples[i] = softClip((mid + wideSide).toFloat()).toDouble()
            samples[i + 1] = softClip((mid - wideSide).toFloat()).toDouble()
        }
    }
    
    /**
     * Soft clipping using saturating math (tanh-like approximation) to prevent harsh distortion.
     * Gracefully transitions from linear (|x| <= 1) to compression (|x| > 1).
     * Formula: sign(x) * (1.0 - 1.0/(1.0 + abs(x)))
     * 
     * At x=0: output=0. At x=1: output≈0.5. As x→∞: output→1.0 (saturates smoothly).
     * This is superior to hard clipping which creates audible aliasing at clipping boundary.
     * 
     * @param x Input signal (-∞ to +∞)
     * @return Soft-clipped signal (-1.0 to 1.0)
     */
    private fun softClip(x: Float): Float {
        if (x == 0f) return 0f
        val absValue = kotlin.math.abs(x)
        
        // Check if we're already in linear region
        val threshold = 0.8f
        if (absValue <= threshold) {
            return x
        }
        
        // In compression region: apply smooth saturation
        // Gracefully transitions from linear to an asymptote of 1.0
        val over = absValue - threshold
        val maxOver = 1.0f - threshold
        val limited = threshold + over / (1.0f + over / maxOver)
        
        return kotlin.math.sign(x) * limited
    }
}
