/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player.automix

import chromahub.rhythm.app.shared.data.model.Curve
import chromahub.rhythm.app.shared.data.model.TransitionMode
import chromahub.rhythm.app.shared.data.model.TransitionSettings
import kotlin.math.abs

data class SmartMixInput(
    val baseDurationMs: Int,
    val baseMode: TransitionMode,
    val curveIn: Curve,
    val curveOut: Curve,
    val outgoingDurationMs: Long,
    val incomingDurationMs: Long,
    val outgoingBpm: Float? = null,
    val incomingBpm: Float? = null,
    val outgoingKey: String? = null,
    val incomingKey: String? = null,
    val outgoingAnalysis: WindowAnalysis? = null,
    val incomingAnalysis: WindowAnalysis? = null,
    val bpmSyncEnabled: Boolean = true,
    val sameAlbumSequence: Boolean = false
)

data class SmartMixPlan(
    val settings: TransitionSettings,
    val tempoRatio: Float = 1.0f,
    val useBassSwap: Boolean = false,
    val harmonicMatch: HarmonicMatchEvaluation? = null,
    val cueInMs: Long = 0L
)

/**
 * Two-tier AutoMix planner turning playlist rules (Tier 1) + BPM/Camelot tags + optional acoustic
 * [WindowAnalysis] (Tier 2) into an overlap duration, tempo lock ratio (within +-6%), and bass swap schedule.
 */
object SmartTransitionPlanner {
    private const val MIN_TRACK_MS = 20_000L
    private const val MAX_TEMPO_STRETCH = 0.06f

    fun plan(input: SmartMixInput): SmartMixPlan {
        if (input.baseMode == TransitionMode.NONE || input.baseDurationMs <= 0) {
            return SmartMixPlan(settings = TransitionSettings(mode = TransitionMode.NONE, durationMs = 0))
        }
        if (input.sameAlbumSequence) {
            // Preserve gapless artist transitions on consecutive album tracks
            return SmartMixPlan(settings = TransitionSettings(mode = TransitionMode.NONE, durationMs = 0))
        }
        if (input.outgoingDurationMs in 1 until MIN_TRACK_MS || input.incomingDurationMs in 1 until MIN_TRACK_MS) {
            return SmartMixPlan(settings = TransitionSettings(mode = TransitionMode.NONE, durationMs = 0))
        }

        val outBpm = input.outgoingBpm ?: input.outgoingAnalysis?.grid?.bpm
        val inBpm = input.incomingBpm ?: input.incomingAnalysis?.grid?.bpm
        val outKey = AutoMixCamelotClassifier.parseToCamelotKey(input.outgoingKey)
        val inKey = AutoMixCamelotClassifier.parseToCamelotKey(input.incomingKey)
        val harmonic = if (outKey.isValid || inKey.isValid) {
            AutoMixCamelotClassifier.evaluateCompatibility(outKey, inKey)
        } else null

        val tempoRatio = if (input.bpmSyncEnabled && outBpm != null && inBpm != null && outBpm > 30f && inBpm > 30f) {
            findSafeTempoRatio(outBpm, inBpm)
        } else {
            1.0f
        }

        var overlapMs = input.baseDurationMs.toLong()
        val outEnding = input.outgoingAnalysis?.ending
        if (outEnding == EndingStyle.COLD) {
            overlapMs = minOf(overlapMs, 2_000L)
        } else if (harmonic != null) {
            overlapMs = (overlapMs * harmonic.allowedDurationMultiplier).toLong().coerceIn(2_000L, 12_000L)
        }

        val maxSafe = ((input.outgoingDurationMs * 0.4f).toLong()).coerceAtLeast(1_000L)
        val finalDuration = overlapMs.coerceIn(500L, minOf(12_000L, maxSafe)).toInt()
        val useBassSwap = input.bpmSyncEnabled && finalDuration >= 3_500 && outEnding != EndingStyle.COLD

        val cueIn = input.incomingAnalysis?.audibleStartMs?.takeIf { it in 1L..5_000L } ?: 0L

        return SmartMixPlan(
            settings = TransitionSettings(
                mode = TransitionMode.OVERLAP,
                durationMs = finalDuration,
                curveIn = if (harmonic?.tier == HarmonicCompatibilityTier.PERFECT_LOCK) Curve.S_CURVE else input.curveIn,
                curveOut = if (harmonic?.tier == HarmonicCompatibilityTier.PERFECT_LOCK) Curve.S_CURVE else input.curveOut,
                tempoRatio = tempoRatio,
                useBassSwap = useBassSwap
            ),
            tempoRatio = tempoRatio,
            useBassSwap = useBassSwap,
            harmonicMatch = harmonic,
            cueInMs = cueIn
        )
    }

    /** Computes incoming speed multiplier (allowing half/double-time matching) clamped to +-6%. */
    fun findSafeTempoRatio(outgoingBpm: Float, incomingBpm: Float): Float {
        val candidates = listOf(0.5f, 1.0f, 2.0f).map { factor -> outgoingBpm / (incomingBpm * factor) }
        val closest = candidates.minByOrNull { abs(it - 1.0f) } ?: return 1.0f
        return if (abs(closest - 1.0f) <= MAX_TEMPO_STRETCH) closest else 1.0f
    }
}
