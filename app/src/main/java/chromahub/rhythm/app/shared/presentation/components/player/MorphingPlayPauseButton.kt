/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import chromahub.rhythm.app.shared.presentation.components.common.M3CircularLoader
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import kotlinx.coroutines.isActive

/**
 * Play/pause button with M3 & M3 Expressive motion specifications:
 * - When in Expressive mode: Container morphs between a smooth circle (paused) and a 9-lobed
 *   cookie (playing). Rotation runs with a decisive current during playback, and smoothly
 *   decelerates to an organic resting stop when paused.
 * - When in Standard mode: Container is a crisp, elevated circle with standard fast-effects icon morph.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isMediaLoading: Boolean = false,
    isExpressive: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val morphPath = remember { Path() }
    val matrix = remember { Matrix() }

    val morphProgress by animateFloatAsState(
        targetValue = if (isPlaying && isExpressive) 1f else 0f,
        animationSpec = spring(
            dampingRatio = MiniPlayerMotionTokens.ExpressiveSpatialDamping,
            stiffness = MiniPlayerMotionTokens.ExpressiveSpatialStiffness
        ),
        label = "morphProgress"
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) {
            if (isExpressive) 0.92f else 0.96f
        } else {
            1f
        },
        animationSpec = spring(
            dampingRatio = if (isExpressive) MiniPlayerMotionTokens.ExpressiveFastSpatialDamping else MiniPlayerMotionTokens.StandardFastSpatialDamping,
            stiffness = if (isExpressive) MiniPlayerMotionTokens.ExpressiveFastSpatialStiffness else MiniPlayerMotionTokens.StandardFastSpatialStiffness
        ),
        label = "pressScale"
    )

    val rotationAngle = remember { Animatable(0f) }

    LaunchedEffect(isPlaying, isExpressive) {
        if (!isExpressive) return@LaunchedEffect

        if (isPlaying) {
            while (isActive) {
                rotationAngle.animateTo(
                    targetValue = rotationAngle.value + 360f,
                    animationSpec = tween(durationMillis = 14000, easing = LinearEasing)
                )
            }
        } else {
            if (rotationAngle.value > 0f) {
                rotationAngle.animateTo(
                    targetValue = rotationAngle.value + 24f,
                    animationSpec = tween(
                        durationMillis = MiniPlayerMotionTokens.DurationLong1,
                        easing = MiniPlayerMotionTokens.EasingEmphasizedDecelerate
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .then(if (modifier == Modifier) Modifier.size(size) else Modifier)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isMediaLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                M3CircularLoader(
                    modifier = Modifier.size(size * 0.42f),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.24f),
                    strokeWidth = 2.5f
                )
            }
        } else {
            if (isExpressive) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    rotate(degrees = rotationAngle.value) {
                        morph.toPath(progress = morphProgress, path = morphPath)
                        matrix.reset()
                        matrix.scale(this.size.width, this.size.height)
                        morphPath.transform(matrix)
                        val bounds = morphPath.getBounds()
                        morphPath.translate(center - bounds.center)
                        drawPath(path = morphPath, color = containerColor, style = Fill)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(containerColor)
                )
            }

            Crossfade(
                targetState = isPlaying,
                animationSpec = tween(
                    durationMillis = MiniPlayerMotionTokens.DurationShort4,
                    easing = MiniPlayerMotionTokens.EasingEmphasized
                ),
                label = "playPauseMorph"
            ) { playing ->
                Icon(
                    imageVector = if (playing) RhythmIcons.Pause else RhythmIcons.Play,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = contentColor,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
    }
}
