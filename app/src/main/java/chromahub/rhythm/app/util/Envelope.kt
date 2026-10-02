/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.Curve
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calculates a volume multiplier based on the progress of a transition and a given curve.
 *
 * This function maps a linear progress value (0.0 to 1.0) to a non-linear value
 * according to the selected curve, which can be used to shape volume fades.
 *
 * @param progress A Float from 0.0 (start) to 1.0 (end) representing the transition's progress.
 * @param curve The volume curve to apply.
 * @return A Float from 0.0 to 1.0 representing the calculated volume multiplier.
 */
fun envelope(progress: Float, curve: Curve): Float {
    val clampedProgress = progress.coerceIn(0f, 1f)

    return when (curve) {
        Curve.LINEAR -> clampedProgress
        Curve.S_CURVE -> ((1 - cos(PI * clampedProgress)) / 2f).toFloat()
        Curve.LOG -> sqrt(clampedProgress)
        Curve.EXP -> clampedProgress * clampedProgress
        Curve.EQUAL_POWER -> sin(clampedProgress * (PI / 2.0)).toFloat()
    }
}

/**
 * Calculates the incoming track's volume multiplier (0.0 to 1.0) based on progress and curve.
 */
fun calculateVolumeIn(progress: Float, curve: Curve): Float {
    val clampedProgress = progress.coerceIn(0f, 1f)
    return when (curve) {
        Curve.EQUAL_POWER -> sin(clampedProgress * (PI / 2.0)).toFloat()
        else -> envelope(clampedProgress, curve)
    }
}

/**
 * Calculates the outgoing track's volume multiplier (1.0 to 0.0) based on progress and curve.
 * For equal power, cos^2(p * PI/2) + sin^2(p * PI/2) == 1.0 at all times, avoiding any volume dip.
 */
fun calculateVolumeOut(progress: Float, curve: Curve): Float {
    val clampedProgress = progress.coerceIn(0f, 1f)
    return when (curve) {
        Curve.EQUAL_POWER -> cos(clampedProgress * (PI / 2.0)).toFloat()
        else -> 1f - envelope(clampedProgress, curve)
    }
}
