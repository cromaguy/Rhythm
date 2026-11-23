/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.viper.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Direct Form II Transposed Biquad Filter.
 * High numerical stability, zero-latency sample-by-sample or buffer processing.
 */
class BiquadFilter {
    enum class Type {
        LOW_PASS,
        HIGH_PASS,
        BAND_PASS,
        PEAKING,
        LOW_SHELF,
        HIGH_SHELF,
        NOTCH
    }

    private class Coefficients(
        val b0: Float,
        val b1: Float,
        val b2: Float,
        val a1: Float,
        val a2: Float
    )

    @Volatile
    private var coeffs: Coefficients = Coefficients(1.0f, 0.0f, 0.0f, 0.0f, 0.0f)

    // State memory for Stereo channels
    private var z1L = 0.0f
    private var z2L = 0.0f
    private var z1R = 0.0f
    private var z2R = 0.0f

    fun reset() {
        z1L = 0.0f
        z2L = 0.0f
        z1R = 0.0f
        z2R = 0.0f
    }

    fun configure(type: Type, sampleRate: Int, frequency: Float, q: Float = 0.707f, gainDb: Float = 0.0f) {
        val safeFs = sampleRate.coerceAtLeast(8000).toFloat()
        val nyquist = safeFs * 0.499f
        val safeFc = frequency.coerceIn(10.0f, nyquist)
        val safeQ = q.coerceAtLeast(0.01f)

        val w0 = (2.0 * PI * safeFc / safeFs).toFloat()
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0f * safeQ)
        val aLinear = 10.0f.pow(gainDb / 40.0f) // sqrt of 10^(gainDb/20)

        var b0 = 1.0f
        var b1 = 0.0f
        var b2 = 0.0f
        var a0 = 1.0f
        var a1 = 0.0f
        var a2 = 0.0f

        when (type) {
            Type.LOW_PASS -> {
                b0 = (1.0f - cosW0) * 0.5f
                b1 = 1.0f - cosW0
                b2 = (1.0f - cosW0) * 0.5f
                a0 = 1.0f + alpha
                a1 = -2.0f * cosW0
                a2 = 1.0f - alpha
            }
            Type.HIGH_PASS -> {
                b0 = (1.0f + cosW0) * 0.5f
                b1 = -(1.0f + cosW0)
                b2 = (1.0f + cosW0) * 0.5f
                a0 = 1.0f + alpha
                a1 = -2.0f * cosW0
                a2 = 1.0f - alpha
            }
            Type.BAND_PASS -> {
                b0 = alpha
                b1 = 0.0f
                b2 = -alpha
                a0 = 1.0f + alpha
                a1 = -2.0f * cosW0
                a2 = 1.0f - alpha
            }
            Type.PEAKING -> {
                b0 = 1.0f + alpha * aLinear
                b1 = -2.0f * cosW0
                b2 = 1.0f - alpha * aLinear
                a0 = 1.0f + alpha / aLinear
                a1 = -2.0f * cosW0
                a2 = 1.0f - alpha / aLinear
            }
            Type.LOW_SHELF -> {
                val sqrtA = sqrt(aLinear)
                val twoSqrtAAlpha = 2.0f * sqrtA * alpha
                b0 = aLinear * ((aLinear + 1.0f) - (aLinear - 1.0f) * cosW0 + twoSqrtAAlpha)
                b1 = 2.0f * aLinear * ((aLinear - 1.0f) - (aLinear + 1.0f) * cosW0)
                b2 = aLinear * ((aLinear + 1.0f) - (aLinear - 1.0f) * cosW0 - twoSqrtAAlpha)
                a0 = (aLinear + 1.0f) + (aLinear - 1.0f) * cosW0 + twoSqrtAAlpha
                a1 = -2.0f * ((aLinear - 1.0f) + (aLinear + 1.0f) * cosW0)
                a2 = (aLinear + 1.0f) + (aLinear - 1.0f) * cosW0 - twoSqrtAAlpha
            }
            Type.HIGH_SHELF -> {
                val sqrtA = sqrt(aLinear)
                val twoSqrtAAlpha = 2.0f * sqrtA * alpha
                b0 = aLinear * ((aLinear + 1.0f) + (aLinear - 1.0f) * cosW0 + twoSqrtAAlpha)
                b1 = -2.0f * aLinear * ((aLinear - 1.0f) + (aLinear + 1.0f) * cosW0)
                b2 = aLinear * ((aLinear + 1.0f) + (aLinear - 1.0f) * cosW0 - twoSqrtAAlpha)
                a0 = (aLinear + 1.0f) - (aLinear - 1.0f) * cosW0 + twoSqrtAAlpha
                a1 = 2.0f * ((aLinear - 1.0f) - (aLinear + 1.0f) * cosW0)
                a2 = (aLinear + 1.0f) - (aLinear - 1.0f) * cosW0 - twoSqrtAAlpha
            }
            Type.NOTCH -> {
                b0 = 1.0f
                b1 = -2.0f * cosW0
                b2 = 1.0f
                a0 = 1.0f + alpha
                a1 = -2.0f * cosW0
                a2 = 1.0f - alpha
            }
        }

        // Normalize by a0 and update coeffs atomically
        val invA0 = 1.0f / a0
        coeffs = Coefficients(
            b0 = b0 * invA0,
            b1 = b1 * invA0,
            b2 = b2 * invA0,
            a1 = a1 * invA0,
            a2 = a2 * invA0
        )
    }

    /**
     * Process Left sample in Direct Form II Transposed
     */
    fun processLeft(input: Float): Float {
        val c = coeffs
        val out = c.b0 * input + z1L
        z1L = c.b1 * input - c.a1 * out + z2L
        z2L = c.b2 * input - c.a2 * out
        return out
    }

    /**
     * Process Right sample in Direct Form II Transposed
     */
    fun processRight(input: Float): Float {
        val c = coeffs
        val out = c.b0 * input + z1R
        z1R = c.b1 * input - c.a1 * out + z2R
        z2R = c.b2 * input - c.a2 * out
        return out
    }
}
