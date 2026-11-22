/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

object PlayingEqIconDefaults {
    data class EqLayout(
        val barW: Float,
        val gap: Float,
        val startX: Float,
        val totalBarsWidth: Float
    )

    fun calculateEqLayout(
        width: Float,
        bars: Int = 3,
        gapFraction: Float = 0.30f
    ): EqLayout {
        if (width <= 0f || bars <= 0) return EqLayout(0f, 0f, 0f, 0f)
        val totalUnits = bars + (bars - 1) * gapFraction
        val barW = width / totalUnits
        val gap = barW * gapFraction
        val totalBarsWidth = bars * barW + (bars - 1) * gap
        val startX = ((width - totalBarsWidth) / 2f).coerceAtLeast(0f)
        return EqLayout(barW, gap, startX, totalBarsWidth)
    }
}

@Composable
fun PlayingEqIcon(
    modifier: Modifier = Modifier,
    color: Color,
    isPlaying: Boolean = true,
    bars: Int = 3,
    minHeightFraction: Float = 0.28f,
    maxHeightFraction: Float = 1.0f,
    phaseDurationMillis: Int = 2400,
    wanderDurationMillis: Int = 8000,
    gapFraction: Float = 0.30f
) {
    // Continuous driver (keeps running even when paused to preserve pattern)
    val infiniteTransition = rememberInfiniteTransition(label = "eqDriver")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = phaseDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val wander by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = wanderDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wander"
    )

    // Activity factor: 1 = bars, 0 = dots (smooth morph)
    val activity by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "activity"
    )

    // Integer speeds for perfect continuity at 2π wrap
    val speeds = remember(bars) { List(bars) { (it + 1).toFloat() } } // 1f, 2f, 3f
    val shifts = remember(bars) { List(bars) { i -> i * 0.9f } }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Layout bars with centered symmetry and mathematically exact unit divisor
        val layout = PlayingEqIconDefaults.calculateEqLayout(w, bars, gapFraction)
        val barW = layout.barW
        val gap = layout.gap
        val startX = layout.startX
        val corner = CornerRadius(barW / 2f, barW / 2f)

        repeat(bars) { i ->
            // Slow "breathing" pattern for natural look
            val slowShift = 0.6f * sin(wander + i * 0.4f)
            val slowAmp = 0.85f + 0.15f * sin(wander * 0.5f + 1.1f + i * 0.3f)

            // Main continuous signal (no jumps)
            val v = (sin(phase * speeds[i] + shifts[i] + slowShift) * slowAmp + 1f) * 0.5f

            // Smoothstep easing
            val eased = v * v * (3 - 2 * v)

            // "Live" height (bar mode)
            val fracBars = minHeightFraction + (maxHeightFraction - minHeightFraction) * eased
            val barH = h * fracBars

            // "Dot" height (circle → height = width)
            val dotH = barW

            // Morph: dot ⇄ bar
            val blendedH = dotH + (barH - dotH) * activity

            val top = (h - blendedH) / 2f
            val left = startX + i * (barW + gap)

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barW, blendedH),
                cornerRadius = corner
            )
        }
    }
}
