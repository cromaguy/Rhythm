/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.haptics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

interface HapticPulseListener {
    fun onHapticPulse(
        bassEnergy: Float,
        vocalEnergy: Float,
        isKick: Boolean,
        isSharpTransient: Boolean
    )

    fun onRhythmLevel(level: Float) {}

    fun onAnalysisFlushed() {}
}

/**
 * Dual-band real-time transient & envelope analyzer separating sub-bass kick energy (100 Hz LP)
 * from vocal/snare articulation (1.1 kHz BP) using auto-gain peak decay and valley-floor
 * tracking so quiet tracks drive musical haptics as reliably as heavily mastered tracks.
 */
class MusicHapticsAnalyzer(
    private val pulseListener: HapticPulseListener
) {
    private var sampleRate = 48_000
    private var decimatedCounter = 0

    // 100 Hz Biquad LP (sub-bass / kick)
    private var lpB0 = 1f; private var lpB1 = 0f; private var lpB2 = 0f
    private var lpA1 = 0f; private var lpA2 = 0f
    private var lpX1 = 0f; private var lpX2 = 0f
    private var lpY1 = 0f; private var lpY2 = 0f

    // 1.1 kHz Biquad BP (vocal / snare)
    private var bpB0 = 1f; private var bpB1 = 0f; private var bpB2 = 0f
    private var bpA1 = 0f; private var bpA2 = 0f
    private var bpX1 = 0f; private var bpX2 = 0f
    private var bpY1 = 0f; private var bpY2 = 0f

    private var fastBassEnv = 0f
    private var slowBassEnv = 0.01f
    private var fastVocalEnv = 0f
    private var slowVocalEnv = 0.01f
    private var bassPeak = 0.05f
    private var vocalPeak = 0.05f
    private var bassFloor = 0f
    private var vocalFloor = 0f

    private var windowFrames = 240
    private var windowCount = 0
    private var windowBassAccum = 0f
    private var windowVocalAccum = 0f
    private var framesSinceBassTrigger = COUNTER_CAP
    private var framesSinceAnyTrigger = COUNTER_CAP
    private var minBassIntervalFrames = 5280
    private var minAnyIntervalFrames = 3840

    fun configure(sampleRateHz: Int) {
        val safeFs = sampleRateHz.coerceAtLeast(8_000)
        if (safeFs == this.sampleRate && minBassIntervalFrames > 0) return
        this.sampleRate = safeFs
        val effRate = safeFs.toFloat() / DECIMATION
        windowFrames = (effRate * 0.010f).toInt().coerceAtLeast(64)
        minBassIntervalFrames = (safeFs * BASS_COOLDOWN_S).toInt()
        minAnyIntervalFrames = (safeFs * ANY_COOLDOWN_S).toInt()
        computeCoefficients(effRate)
        reset()
    }

    fun reset() {
        lpX1 = 0f; lpX2 = 0f; lpY1 = 0f; lpY2 = 0f
        bpX1 = 0f; bpX2 = 0f; bpY1 = 0f; bpY2 = 0f
        fastBassEnv = 0f; slowBassEnv = 0.01f; bassFloor = 0f
        fastVocalEnv = 0f; slowVocalEnv = 0.01f; vocalFloor = 0f
        bassPeak = 0.05f; vocalPeak = 0.05f
        windowBassAccum = 0f; windowVocalAccum = 0f; windowCount = 0
        framesSinceBassTrigger = COUNTER_CAP
        framesSinceAnyTrigger = COUNTER_CAP
        decimatedCounter = 0
        pulseListener.onAnalysisFlushed()
    }

    /** Analyzes interleaved normalized Double frames read-only without mutating [samples]. */
    fun analyzeInterleaved(samples: DoubleArray, sampleCount: Int, channelCount: Int, sampleRateHz: Int) {
        configure(sampleRateHz)
        val ch = channelCount.coerceAtLeast(1)
        var i = 0
        while (i + ch <= sampleCount) {
            var sum = 0.0
            for (c in 0 until ch) sum += samples[i + c]
            val mono = (sum / ch).toFloat().coerceIn(-1f, 1f)
            framesSinceBassTrigger = min(framesSinceBassTrigger + 1, COUNTER_CAP)
            framesSinceAnyTrigger = min(framesSinceAnyTrigger + 1, COUNTER_CAP)

            decimatedCounter = (decimatedCounter + 1) % DECIMATION
            if (decimatedCounter == 0) {
                stepDecimated(mono)
            }
            i += ch
        }
    }

    private fun stepDecimated(x: Float) {
        val yLp = lpB0 * x + lpB1 * lpX1 + lpB2 * lpX2 - lpA1 * lpY1 - lpA2 * lpY2
        lpX2 = lpX1; lpX1 = x; lpY2 = lpY1; lpY1 = yLp

        val yBp = bpB0 * x + bpB1 * bpX1 + bpB2 * bpX2 - bpA1 * bpY1 - bpA2 * bpY2
        bpX2 = bpX1; bpX1 = x; bpY2 = bpY1; bpY1 = yBp

        windowBassAccum += yLp * yLp
        windowVocalAccum += yBp * yBp
        windowCount++

        if (windowCount >= windowFrames) {
            val bassRms = sqrt(windowBassAccum / windowCount)
            val vocalRms = sqrt(windowVocalAccum / windowCount)
            windowBassAccum = 0f
            windowVocalAccum = 0f
            windowCount = 0
            evaluateWindow(bassRms, vocalRms)
        }
    }

    private fun evaluateWindow(bassRms: Float, vocalRms: Float) {
        fastBassEnv = if (bassRms > fastBassEnv) fastBassEnv * 0.45f + bassRms * 0.55f else fastBassEnv * 0.85f + bassRms * 0.15f
        slowBassEnv = slowBassEnv * 0.96f + bassRms * 0.04f
        fastVocalEnv = if (vocalRms > fastVocalEnv) fastVocalEnv * 0.50f + vocalRms * 0.50f else fastVocalEnv * 0.88f + vocalRms * 0.12f
        slowVocalEnv = slowVocalEnv * 0.95f + vocalRms * 0.05f

        bassPeak = max(fastBassEnv, bassPeak * PEAK_DECAY).coerceAtLeast(MIN_PEAK)
        vocalPeak = max(fastVocalEnv, vocalPeak * PEAK_DECAY).coerceAtLeast(MIN_PEAK)

        val normBass = (fastBassEnv / bassPeak).coerceIn(0f, 1f)
        val normVocal = (fastVocalEnv / vocalPeak).coerceIn(0f, 1f)
        val bassRatio = fastBassEnv / max(slowBassEnv, 0.004f)
        val vocalRatio = fastVocalEnv / max(slowVocalEnv, 0.004f)

        bassFloor = if (fastBassEnv < bassFloor) fastBassEnv else bassFloor + (fastBassEnv - bassFloor) * FLOOR_RISE
        vocalFloor = if (fastVocalEnv < vocalFloor) fastVocalEnv else vocalFloor + (fastVocalEnv - vocalFloor) * FLOOR_RISE

        val bassLevel = aboveFloor(fastBassEnv, bassFloor, bassPeak, 0.006f)
        val midLevel = aboveFloor(fastVocalEnv, vocalFloor, vocalPeak, 0.005f)
        pulseListener.onRhythmLevel(max(bassLevel, MID_WEIGHT * midLevel))

        val isKick = framesSinceBassTrigger >= minBassIntervalFrames &&
            fastBassEnv > 0.006f && bassRatio > 1.30f && normBass > 0.45f
        val isAccent = framesSinceAnyTrigger >= minAnyIntervalFrames &&
            fastVocalEnv > 0.005f && vocalRatio > 1.28f && normVocal > 0.45f

        if (!isKick && !isAccent) return
        framesSinceAnyTrigger = 0
        if (isKick) framesSinceBassTrigger = 0
        pulseListener.onHapticPulse(
            bassEnergy = if (isKick) max(normBass, 0.55f) else normBass,
            vocalEnergy = normVocal,
            isKick = isKick,
            isSharpTransient = isKick && bassRatio > 1.55f
        )
    }

    private fun aboveFloor(env: Float, floor: Float, peak: Float, gate: Float): Float =
        if (env > gate) ((env - floor) / max(peak - floor, MIN_PEAK)).coerceIn(0f, 1f).let { it * it } else 0f

    private fun computeCoefficients(rate: Float) {
        val fcLp = 100f.coerceAtMost(rate * 0.45f)
        val w0Lp = (2.0 * PI * fcLp / rate).toFloat()
        val cosLp = cos(w0Lp)
        val alphaLp = sin(w0Lp) / (2f * 0.7071f)
        val a0Lp = 1f + alphaLp
        lpB0 = ((1f - cosLp) * 0.5f) / a0Lp
        lpB1 = (1f - cosLp) / a0Lp
        lpB2 = lpB0
        lpA1 = (-2f * cosLp) / a0Lp
        lpA2 = (1f - alphaLp) / a0Lp

        val fcBp = 1100f.coerceAtMost(rate * 0.45f)
        val w0Bp = (2.0 * PI * fcBp / rate).toFloat()
        val cosBp = cos(w0Bp)
        val alphaBp = sin(w0Bp) / (2f * 0.85f)
        val a0Bp = 1f + alphaBp
        bpB0 = alphaBp / a0Bp
        bpB1 = 0f
        bpB2 = -alphaBp / a0Bp
        bpA1 = (-2f * cosBp) / a0Bp
        bpA2 = (1f - alphaBp) / a0Bp
    }

    companion object {
        const val DECIMATION = 2
        const val BASS_COOLDOWN_S = 0.110f
        const val ANY_COOLDOWN_S = 0.080f
        const val PEAK_DECAY = 0.9985f
        const val MIN_PEAK = 0.008f
        const val FLOOR_RISE = 0.003f
        const val MID_WEIGHT = 0.8f
        const val COUNTER_CAP = 1 shl 28
    }
}
