/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player.automix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class BeatGrid(
    val bpm: Float,
    val downbeatMs: Double,
    val confidence: Float
) {
    val beatMs: Double get() = 60_000.0 / bpm.coerceAtLeast(1f)
    val barMs: Double get() = beatMs * 4.0

    fun downbeatAtOrAfter(timeMs: Double): Double =
        downbeatMs + ceil((timeMs - downbeatMs) / barMs - 1e-9) * barMs

    fun downbeatAtOrBefore(timeMs: Double): Double =
        downbeatMs + floor((timeMs - downbeatMs) / barMs + 1e-9) * barMs
}

enum class EndingStyle {
    FADE_OUT,
    COLD,
    NATURAL
}

enum class IntroStyle {
    COLD,
    BUILD
}

data class WindowAnalysis(
    val startMs: Long,
    val endMs: Long,
    val grid: BeatGrid?,
    val audibleStartMs: Long?,
    val audibleEndMs: Long?,
    val ending: EndingStyle,
    val fadeStartMs: Long?,
    val intro: IntroStyle
)

/**
 * Pure-Kotlin acoustic beat, tempo, phase, and intro/outro envelope detector operating on
 * mono Float PCM windows (`[-1f, 1f]`). Deterministic and verifiable via unit tests without codecs.
 */
object TrackAnalyzer {
    private const val HOP = 256
    private const val MIN_BPM = 70.0
    private const val MAX_BPM = 180.0
    private const val BASS_CUTOFF_HZ = 150.0
    private const val SILENCE_FLOOR_DB = -48.0

    fun analyzeMonoPcm(samples: FloatArray, sampleRateHz: Int, windowStartMs: Long = 0L): WindowAnalysis {
        val fs = sampleRateHz.coerceAtLeast(8_000)
        val durationMs = (samples.size * 1000L) / fs
        val windowEndMs = windowStartMs + durationMs

        if (samples.size < fs * 4) {
            return WindowAnalysis(
                startMs = windowStartMs,
                endMs = windowEndMs,
                grid = null,
                audibleStartMs = windowStartMs,
                audibleEndMs = windowEndMs,
                ending = EndingStyle.NATURAL,
                fadeStartMs = null,
                intro = IntroStyle.BUILD
            )
        }

        val numHops = samples.size / HOP
        val bassEnv = FloatArray(numHops)
        val fullEnv = FloatArray(numHops)
        val dt = 1.0 / fs
        val rc = 1.0 / (2.0 * PI * BASS_CUTOFF_HZ)
        val alpha = (dt / (rc + dt)).toFloat()
        var lpState = 0f

        var maxDb = -100.0
        val hopDb = DoubleArray(numHops)

        for (h in 0 until numHops) {
            val base = h * HOP
            var sumBassSq = 0.0
            var sumFullSq = 0.0
            for (k in 0 until HOP) {
                val x = samples[base + k]
                lpState += alpha * (x - lpState)
                sumBassSq += lpState * lpState
                sumFullSq += x * x
            }
            val rmsFull = sqrt(sumFullSq / HOP).toFloat()
            bassEnv[h] = sqrt(sumBassSq / HOP).toFloat()
            fullEnv[h] = rmsFull
            val db = 20.0 * log10(max(rmsFull.toDouble(), 1e-6))
            hopDb[h] = db
            if (db > maxDb) maxDb = db
        }

        val thresholdDb = max(SILENCE_FLOOR_DB, maxDb - 36.0)
        val firstAudibleHop = hopDb.indexOfFirst { it >= thresholdDb }.takeIf { it >= 0 } ?: 0
        val lastAudibleHop = hopDb.indexOfLast { it >= thresholdDb }.takeIf { it >= 0 } ?: (numHops - 1)

        val hopMs = (HOP * 1000.0) / fs
        val audibleStartMs = windowStartMs + (firstAudibleHop * hopMs).toLong()
        val audibleEndMs = windowStartMs + ((lastAudibleHop + 1) * hopMs).toLong()

        val tailWindowHops = min(numHops / 4, (5_000.0 / hopMs).toInt()).coerceAtLeast(4)
        val bodySlice = hopDb.slice((numHops / 6)..(numHops * 3 / 4)).sorted()
        val bodyRefDb = bodySlice.getOrElse((bodySlice.size * 0.85).toInt()) { maxDb }
        val tailStart = (lastAudibleHop - tailWindowHops).coerceAtLeast(0)
        val tailEndWindow = (lastAudibleHop - 2).coerceAtLeast(tailStart)..lastAudibleHop
        val tailEndDb = tailEndWindow.map { hopDb[it] }.average()
        val tailFirstDb = hopDb[tailStart]
        val fadeOutDetected = (bodyRefDb - tailEndDb > 12.0 || (tailFirstDb - tailEndDb > 8.0)) &&
            (lastAudibleHop - tailStart) * hopMs >= 1_200.0
        val ending = when {
            fadeOutDetected -> EndingStyle.FADE_OUT
            lastAudibleHop >= numHops - 3 && hopDb[lastAudibleHop] > maxDb - 10.0 -> EndingStyle.COLD
            else -> EndingStyle.NATURAL
        }
        val fadeStartMs = if (fadeOutDetected) {
            val dropThreshold = bodyRefDb - 6.0
            val fadeHop = (tailStart..lastAudibleHop).firstOrNull { hopDb[it] < dropThreshold } ?: tailStart
            windowStartMs + (fadeHop * hopMs).toLong()
        } else {
            null
        }
        val intro = if (hopDb[firstAudibleHop] > maxDb - 10.0) IntroStyle.COLD else IntroStyle.BUILD

        val novelty = FloatArray(numHops)
        for (h in 1 until numHops) {
            val dBass = (bassEnv[h] - bassEnv[h - 1]).coerceAtLeast(0f)
            val dFull = (fullEnv[h] - fullEnv[h - 1]).coerceAtLeast(0f)
            novelty[h] = dBass * 1.4f + dFull * 0.6f
        }

        val grid = estimateBeatGrid(novelty, hopMs, windowStartMs)
        return WindowAnalysis(
            startMs = windowStartMs,
            endMs = windowEndMs,
            grid = grid,
            audibleStartMs = audibleStartMs,
            audibleEndMs = audibleEndMs,
            ending = ending,
            fadeStartMs = fadeStartMs,
            intro = intro
        )
    }

    private fun estimateBeatGrid(novelty: FloatArray, hopMs: Double, windowStartMs: Long): BeatGrid? {
        if (novelty.size < 20) return null
        val hopsPerSec = 1000.0 / hopMs
        val maxLag = ((60.0 / MIN_BPM) * hopsPerSec).roundToInt().coerceAtMost(novelty.size / 2)
        val minLag = ((60.0 / MAX_BPM) * hopsPerSec).roundToInt().coerceAtLeast(4)
        if (minLag >= maxLag) return null

        var bestLag = minLag
        var bestScore = 0.0
        var totalEnergy = 0.0
        for (v in novelty) totalEnergy += v.toDouble() * v.toDouble()
        if (totalEnergy < 1e-8) return null

        for (lag in minLag..maxLag) {
            var corr = 0.0
            for (i in lag until novelty.size) {
                corr += novelty[i].toDouble() * novelty[i - lag].toDouble()
            }
            val bpm = 60.0 * hopsPerSec / lag.toDouble()
            // Gentle perceptual prior centered near 115 BPM avoids octave errors
            val tempoPrior = exp(-0.5 * ((ln(bpm / 115.0)) / 0.45).let { it * it })
            val score = corr * tempoPrior
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }

        val bpm = (60.0 * hopsPerSec / bestLag.toDouble()).toFloat()
        // Phase detection: find offset in [0, bestLag) maximizing comb onset alignment
        var bestPhase = 0
        var bestPhaseScore = -1.0
        for (phase in 0 until bestLag) {
            var s = 0.0
            var idx = phase
            while (idx < novelty.size) {
                s += novelty[idx].toDouble()
                idx += bestLag
            }
            if (s > bestPhaseScore) {
                bestPhaseScore = s
                bestPhase = phase
            }
        }

        val confidence = (bestScore / totalEnergy).toFloat().coerceIn(0f, 1f)
        return BeatGrid(
            bpm = bpm,
            downbeatMs = windowStartMs + bestPhase * hopMs,
            confidence = confidence
        )
    }
}
