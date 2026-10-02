/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.model

/**
 * Defines transition modes for crossfade between tracks.
 */
enum class TransitionMode {
    /** No transition — default gap behavior */
    NONE,
    /** Overlap crossfade — both tracks play simultaneously during transition */
    OVERLAP
}

/**
 * Volume curve shapes for crossfade transitions.
 */
enum class Curve {
    /** Linear volume ramp */
    LINEAR,
    /** Exponential curve — slow rise, fast finish */
    EXP,
    /** Logarithmic curve — fast rise, slow finish */
    LOG,
    /** S-curve (sigmoid) — smooth start and end */
    S_CURVE,
    /** Equal power curve (sine/cosine) — constant acoustic energy without loudness dip */
    EQUAL_POWER
}

/**
 * Settings for a crossfade transition.
 *
 * @param mode The transition mode (NONE or OVERLAP)
 * @param durationMs Duration of the crossfade in milliseconds
 * @param curveIn Volume curve for the incoming (new) track
 * @param curveOut Volume curve for the outgoing (current) track
 */
data class TransitionSettings(
    val mode: TransitionMode = TransitionMode.OVERLAP,
    val durationMs: Int = 6000,
    val curveIn: Curve = Curve.EQUAL_POWER,
    val curveOut: Curve = Curve.EQUAL_POWER,
    val isManualSkip: Boolean = false,
    val isSkipPrevious: Boolean = false,
)
