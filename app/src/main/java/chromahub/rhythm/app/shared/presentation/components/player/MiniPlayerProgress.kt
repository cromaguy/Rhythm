/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.presentation.components.common.ProgressStyle
import chromahub.rhythm.app.shared.presentation.components.common.StyledProgressBar

/**
 * Progress indicator for MiniPlayer with support for both:
 * 1. Expressive integrated background progress (fluid clip, organic spring interpolation).
 * 2. Material styled progress bar (NORMAL, WAVY, ROUNDED, etc.).
 */
@Composable
fun MiniPlayerIntegratedProgress(
    progress: () -> Float,
    isExpressive: Boolean,
    shape: Shape = CircleShape,
    color: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f),
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress(),
        animationSpec = spring(
            dampingRatio = if (isExpressive) MiniPlayerMotionTokens.ExpressiveSpatialDamping else MiniPlayerMotionTokens.StandardSpatialDamping,
            stiffness = if (isExpressive) MiniPlayerMotionTokens.ExpressiveSpatialStiffness else MiniPlayerMotionTokens.StandardSpatialStiffness
        ),
        label = "miniPlayerIntegratedProgress"
    )

    if (animatedProgress > 0f) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress.coerceIn(0.001f, 1f))
                .clip(shape)
                .background(color)
        )
    }
}

@Composable
fun MiniPlayerStyledProgress(
    progress: () -> Float,
    styleString: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
) {
    val currentProgress = progress()
    val progressStyle = try {
        ProgressStyle.valueOf(styleString)
    } catch (_: IllegalArgumentException) {
        ProgressStyle.NORMAL
    }

    StyledProgressBar(
        progress = currentProgress,
        style = progressStyle,
        modifier = modifier,
        progressColor = progressColor,
        trackColor = trackColor,
        height = height,
        isPlaying = isPlaying
    )
}
