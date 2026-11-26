/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player.automix

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import chromahub.rhythm.app.infrastructure.audio.RhythmAudioProcessor
import chromahub.rhythm.app.infrastructure.audio.dsp.ParameterGlide
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Per-deck swept 2nd-order Butterworth DJ EQ stage used during AutoMix overlaps to cleanly hand
 * off sub-bass energy between outgoing and incoming tracks without muddiness or clipping.
 *
 * When inactive (no cut scheduled), returns instantaneously in `isBypassed() == true` for zero-copy
 * bit-exact passthrough. Runs natively on Double/Float/24-bit/32-bit/16-bit PCM via [RhythmAudioProcessor].
 */
@OptIn(UnstableApi::class)
class DeckEqAudioProcessor : RhythmAudioProcessor() {

    private val bassLevelGlide = ParameterGlide(1.0, glideSeconds = 0.025)
    private val z1 = DoubleArray(8)
    private val z2 = DoubleArray(8)

    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var tunedCutoffHz = -1.0

    @Volatile
    private var activeCut: Boolean = false

    @Volatile
    private var targetOpenLevel: Double = 1.0 // 1.0 = 10 Hz open, 0.0 = 160 Hz bass cut

    /** Smoothly sweeps bass cutoff up to 160 Hz (cutting kick/sub-bass on outgoing or incoming deck). */
    fun setBassCut(cut: Boolean) {
        activeCut = cut
        targetOpenLevel = if (cut) 0.0 else 1.0
        bassLevelGlide.setTarget(targetOpenLevel)
    }

    /** Immediately clears any cut cleanly (called after player settles idle post-crossfade). */
    fun resetCutImmediately() {
        activeCut = false
        targetOpenLevel = 1.0
        bassLevelGlide.snapTo(1.0)
        z1.fill(0.0)
        z2.fill(0.0)
    }

    override fun isEnabled(): Boolean = activeCut || targetOpenLevel < 0.999

    override fun isBypassed(): Boolean = !isEnabled()

    override fun configure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
        val out = super.configure(inputAudioFormat)
        bassLevelGlide.setSampleRate(out.sampleRate)
        return out
    }

    override fun flush() {
        super.flush()
        z1.fill(0.0)
        z2.fill(0.0)
        bassLevelGlide.setSampleRate(sampleRate)
    }

    override fun processDoubleSamples(samples: DoubleArray, sampleCount: Int) {
        if (!isEnabled()) return
        val fs = sampleRate.coerceAtLeast(8_000)
        val chCount = channelCount.coerceIn(1, 8)
        var idx = 0
        var frameIdx = 0

        while (idx + chCount <= sampleCount) {
            val openLevel = bassLevelGlide.next().coerceIn(0.0, 1.0)
            if (frameIdx % 32 == 0) {
                val cutoffHz = 10.0 * (160.0 / 10.0).pow(1.0 - openLevel)
                tuneHighPass(fs, cutoffHz)
            }
            for (ch in 0 until chCount) {
                val x = samples[idx + ch]
                val y = b0 * x + z1[ch]
                z1[ch] = b1 * x - a1 * y + z2[ch]
                z2[ch] = b2 * x - a2 * y
                samples[idx + ch] = y
            }
            idx += chCount
            frameIdx++
        }
    }

    override fun processSamples(samples: ShortArray, sampleCount: Int) {
        if (!isEnabled()) return
        val doubles = DoubleArray(sampleCount) { samples[it].toDouble() / 32768.0 }
        processDoubleSamples(doubles, sampleCount)
        for (i in 0 until sampleCount) {
            samples[i] = (doubles[i].coerceIn(-1.0, 1.0) * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun tuneHighPass(sampleRate: Int, hz: Double) {
        val clamped = hz.coerceIn(10.0, sampleRate * 0.45)
        if (kotlin.math.abs(clamped - tunedCutoffHz) < 0.25) return
        tunedCutoffHz = clamped
        val w0 = 2.0 * PI * clamped / sampleRate
        val alpha = sin(w0) / sqrt(2.0)
        val cs = cos(w0)
        val a0 = 1.0 + alpha
        b0 = ((1.0 + cs) * 0.5) / a0
        b1 = -(1.0 + cs) / a0
        b2 = b0
        a1 = (-2.0 * cs) / a0
        a2 = (1.0 - alpha) / a0
    }
}
