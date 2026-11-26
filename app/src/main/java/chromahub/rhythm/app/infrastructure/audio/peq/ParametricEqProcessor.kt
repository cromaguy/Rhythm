/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.peq

import chromahub.rhythm.app.infrastructure.audio.dsp.ParameterGlide
import kotlin.math.pow

/**
 * Real-time 64-bit IEEE Double-precision parametric EQ engine supporting up to 20 cascaded
 * RBJ Direct Form II Transposed biquads + smoothed preamp gain + multi-channel layouts (up to 8ch).
 */
class ParametricEqProcessor {
    private var sampleRate: Int = 48_000
    private var channelCount: Int = 2
    private var enabled: Boolean = false
    private var settings: PeqSettings = PeqSettings()

    private class BiquadCoeffs(
        var b0: Double = 1.0,
        var b1: Double = 0.0,
        var b2: Double = 0.0,
        var a1: Double = 0.0,
        var a2: Double = 0.0,
        var active: Boolean = false
    )

    private val coeffs = Array(PeqSettings.MAX_FILTERS) { BiquadCoeffs() }
    private var z1 = Array(PeqSettings.MAX_FILTERS) { DoubleArray(2) }
    private var z2 = Array(PeqSettings.MAX_FILTERS) { DoubleArray(2) }
    private var activeFilterCount = 0

    private val preampGlide = ParameterGlide(1.0)

    @Volatile
    private var dirty = true

    fun configure(sampleRate: Int, channelCount: Int) {
        val safeRate = sampleRate.coerceAtLeast(8_000)
        val safeChannels = channelCount.coerceIn(1, 8)
        if (this.sampleRate != safeRate || this.channelCount != safeChannels) {
            this.sampleRate = safeRate
            this.channelCount = safeChannels
            z1 = Array(PeqSettings.MAX_FILTERS) { DoubleArray(safeChannels) }
            z2 = Array(PeqSettings.MAX_FILTERS) { DoubleArray(safeChannels) }
            preampGlide.setSampleRate(safeRate)
            dirty = true
        }
    }

    fun setEnabled(enable: Boolean) {
        if (this.enabled != enable) {
            this.enabled = enable
            if (!enable) resetState()
        }
    }

    fun isBypassed(): Boolean = !enabled || settings.isFlat

    fun updateSettings(newSettings: PeqSettings) {
        val sanitized = newSettings.sanitized()
        if (sanitized != settings) {
            settings = sanitized
            dirty = true
        }
    }

    fun currentSettings(): PeqSettings = settings

    fun resetState() {
        for (i in coeffs.indices) {
            z1[i].fill(0.0)
            z2[i].fill(0.0)
        }
        preampGlide.snapTo(if (enabled && !settings.isFlat) 10.0.pow(settings.preampDb / 20.0) else 1.0)
    }

    private fun refreshCoefficients() {
        val list = settings.filters.take(PeqSettings.MAX_FILTERS)
        activeFilterCount = list.size
        val targetPreamp = 10.0.pow(settings.preampDb / 20.0)
        preampGlide.setTarget(targetPreamp)

        for (i in coeffs.indices) {
            if (i < list.size) {
                val filter = list[i]
                val active = filter.enabled && (!filter.type.hasGain || kotlin.math.abs(filter.gainDb) >= 0.001)
                if (active) {
                    val c = PeqMath.coefficients(filter, sampleRate.toDouble())
                    coeffs[i].b0 = c[0]
                    coeffs[i].b1 = c[1]
                    coeffs[i].b2 = c[2]
                    coeffs[i].a1 = c[3]
                    coeffs[i].a2 = c[4]
                    coeffs[i].active = true
                } else {
                    coeffs[i].active = false
                }
            } else {
                coeffs[i].active = false
            }
        }
        dirty = false
    }

    /** Processes interleaved normalized Double PCM frames `[-1.0, 1.0]` in-place. */
    fun process(samples: DoubleArray, sampleCount: Int) {
        if (dirty) refreshCoefficients()
        if (isBypassed()) return

        val chCount = channelCount
        var idx = 0
        while (idx + chCount <= sampleCount) {
            val preamp = preampGlide.next()
            for (ch in 0 until chCount) {
                var x = samples[idx + ch] * preamp
                for (f in 0 until activeFilterCount) {
                    val c = coeffs[f]
                    if (!c.active) continue
                    val state1 = z1[f]
                    val state2 = z2[f]
                    val y = c.b0 * x + state1[ch]
                    val nextZ1 = c.b1 * x - c.a1 * y + state2[ch]
                    val nextZ2 = c.b2 * x - c.a2 * y
                    // Subnormal flush prevents FPU stalls on silent sections
                    state1[ch] = if (kotlin.math.abs(nextZ1) < 1e-24) 0.0 else nextZ1
                    state2[ch] = if (kotlin.math.abs(nextZ2) < 1e-24) 0.0 else nextZ2
                    x = y
                }
                samples[idx + ch] = x
            }
            idx += chCount
        }
    }
}
