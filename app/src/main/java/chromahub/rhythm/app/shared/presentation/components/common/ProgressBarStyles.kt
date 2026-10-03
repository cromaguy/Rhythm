/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.max
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Progress bar style options for MiniPlayer and Player
 * Following Compose December 2025 best practices with optimized animations
 */
enum class ProgressStyle {
    NORMAL,     // Standard LinearProgressIndicator
    WAVY,       // Animated wavy line
    ROUNDED,    // Rounded pill-shaped progress
    THIN,       // Thin elegant line
    THICK,      // Thick bold progress bar
    GRADIENT,   // Gradient colored progress
    SEGMENTED,  // Segmented/dotted progress
    DOTS        // Dots indicator
}

/**
 * Thumb style options for the progress bar slider
 */
enum class ThumbStyle(val shapeId: String?, val sizeScale: Float = 1f) {
    NONE(null),
    DEFAULT(null),
    CIRCLE("CIRCLE"),
    SQUARE("SQUARE"),
    PILL("PILL", 1.25f),
    DIAMOND("DIAMOND", 1.25f),
    FLOWER("FLOWER", 1.25f),
    HEART("HEART", 1.25f),
    COOKIE("COOKIE_6", 1.25f),
    PUFFY("PUFFY", 1.25f),
    CLOVER("CLOVER_4_LEAF", 1.25f),
    CLOVER_8("CLOVER_8_LEAF", 1.25f),
    BURST("BURST", 1.25f),
    SOFT_BURST("SOFT_BURST", 1.25f),
    SUNNY("SUNNY", 1.25f),
    BOOM("BOOM", 1.25f),
    PUFFY_DIAMOND("PUFFY_DIAMOND", 1.25f),
    GEM("GEM", 1.25f),
    TRIANGLE("TRIANGLE", 1.25f),
    PENTAGON("PENTAGON", 1.25f),
    COOKIE_12("COOKIE_12", 1.25f),
    CLAM_SHELL("CLAM_SHELL", 1.25f);

    companion object {
        /** Resolves a stored style name, mapping legacy names to the M3 set. */
        fun fromStorage(value: String?): ThumbStyle = when (value) {
            "NONE" -> NONE
            "DEFAULT", "GLOW", "ARROW" -> DEFAULT
            "OUTLINE", "DOT", "RING" -> CIRCLE
            "CIRCLE" -> CIRCLE
            "SQUARE" -> SQUARE
            "PILL", "LINE" -> PILL
            "DIAMOND" -> DIAMOND
            "FLOWER" -> FLOWER
            "HEART" -> HEART
            "COOKIE" -> COOKIE
            "PUFFY" -> PUFFY
            "CLOVER" -> CLOVER
            "CLOVER_8" -> CLOVER_8
            "BURST" -> BURST
            "SOFT_BURST" -> SOFT_BURST
            "SUNNY" -> SUNNY
            "BOOM" -> BOOM
            "PUFFY_DIAMOND" -> PUFFY_DIAMOND
            "GEM" -> GEM
            "TRIANGLE" -> TRIANGLE
            "PENTAGON" -> PENTAGON
            "COOKIE_12" -> COOKIE_12
            "CLAM_SHELL" -> CLAM_SHELL
            else -> DEFAULT
        }
    }
}

/**
 * Material 3 Expressive thumb for the progress bar slider.
 * Supports interactive press/scrub morphing and scaling matching WaveSlider behavior,
 * smooth rotation when playing, and clean Material 3 shapes without extra dots.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3Thumb(
    style: ThumbStyle,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    rotateWhenPlaying: Boolean = false,
    isInteracting: Boolean = false,
    interactionSource: MutableInteractionSource? = null
) {
    if (style == ThumbStyle.NONE) return

    val effectiveSize = size * style.sizeScale
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val active = isInteracting || isPressed

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "M3ThumbInteraction"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (active) 1.25f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "M3ThumbScale"
    )

    val rotation: Float = if (rotateWhenPlaying && isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "thumbRotate")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "thumbRotation"
        ).value
    } else {
        0f
    }

    val rotatedModifier = modifier.graphicsLayer { rotationZ = rotation }

    when (style) {
        ThumbStyle.NONE -> Unit
        ThumbStyle.DEFAULT, ThumbStyle.CIRCLE -> {
            val idleWidth = effectiveSize
            val idleHeight = effectiveSize
            val activeWidth = (effectiveSize * 0.42f).coerceIn(4.dp, 6.dp)
            val activeHeight = (effectiveSize * 1.7f).coerceIn(20.dp, 28.dp)

            val currentWidth = lerp(idleWidth, activeWidth, thumbInteractionFraction)
            val currentHeight = lerp(idleHeight, activeHeight, thumbInteractionFraction)

            Box(
                modifier = rotatedModifier
                    .size(width = currentWidth, height = currentHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(color, RoundedCornerShape(percent = 50))
            )
        }
        ThumbStyle.PILL -> {
            val idleWidth = 6.dp
            val idleHeight = effectiveSize.coerceAtLeast(16.dp)
            val activeWidth = 5.dp
            val activeHeight = (effectiveSize * 1.7f).coerceIn(22.dp, 28.dp)

            val currentWidth = lerp(idleWidth, activeWidth, thumbInteractionFraction)
            val currentHeight = lerp(idleHeight, activeHeight, thumbInteractionFraction)

            Box(
                modifier = rotatedModifier
                    .size(width = currentWidth, height = currentHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(color, RoundedCornerShape(percent = 50))
            )
        }
        else -> {
            val shape = remember(style) {
                ExpressiveShapeProvider.getShapeById(style.shapeId ?: "CIRCLE", CircleShape)
            }
            Box(
                modifier = rotatedModifier
                    .size(effectiveSize * thumbScale)
                    .background(color, shape)
            )
        }
    }
}

/**
 * Animated thumb geometry shared by every linear progress style.
 *
 * [effectiveSize] is the resting (largest) thumb diameter, while [halfWidth] is
 * the live half-width as the thumb morphs or spring-scales during interaction.
 * Keeping both in one place means the track gap and the drawn thumb can never
 * disagree.
 */
private data class ThumbMorph(
    val effectiveSize: Dp,
    val halfWidth: Dp
)

@Composable
private fun rememberThumbMorph(
    thumbStyle: ThumbStyle,
    thumbSize: Dp,
    isInteracting: Boolean
): ThumbMorph {
    val interactionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteraction"
    )
    val interactionScale by animateFloatAsState(
        targetValue = if (isInteracting) 1.25f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ThumbScale"
    )

    val effectiveSize = thumbSize * thumbStyle.sizeScale
    val halfWidth = when {
        thumbStyle == ThumbStyle.PILL -> lerp(3.dp, 2.5.dp, interactionFraction)
        thumbStyle == ThumbStyle.DEFAULT || thumbStyle == ThumbStyle.CIRCLE -> {
            val idleHalfWidth = effectiveSize / 2f
            val activeHalfWidth = (effectiveSize * 0.42f).coerceIn(4.dp, 6.dp) / 2f
            lerp(idleHalfWidth, activeHalfWidth, interactionFraction)
        }
        else -> (effectiveSize * interactionScale) / 2f
    }
    return ThumbMorph(effectiveSize, halfWidth)
}

/**
 * Vertical space a thumb slider needs: enough for the track plus the largest
 * thumb the style can reach while interacting, with a minimum touch target.
 * Shared by the outer [StyledProgressBar] box and the inner bars so they agree
 * on the very same height and nothing gets clipped.
 */
private fun thumbContainerHeight(trackHeight: Dp, effectiveThumbSize: Dp): Dp =
    max(trackHeight.coerceAtLeast(24.dp), effectiveThumbSize * 2f)

/**
 * Resolves the x position of the thumb centre along the track, clamped to the
 * bar bounds so scaled/morphing thumbs are never clipped at 0% or 100%.
 */
private fun resolveThumbCenter(
    barWidth: Dp,
    trackInset: Dp,
    halfWidth: Dp,
    progress: Float
): Dp {
    val travel = (barWidth - trackInset * 2).coerceAtLeast(0.dp)
    val rawCenter = trackInset + travel * progress.coerceIn(0f, 1f)
    val minCenter = halfWidth.coerceAtMost(barWidth / 2)
    val maxCenter = (barWidth - halfWidth).coerceAtLeast(minCenter)
    return rawCenter.coerceIn(minCenter, maxCenter)
}

/**
 * Draws the shared Material 3 Expressive thumb anchored at [center], vertically
 * centred and horizontally centred on that position.
 */
@Composable
private fun PositionedThumb(
    center: Dp,
    thumbStyle: ThumbStyle,
    thumbSize: Dp,
    color: Color,
    isPlaying: Boolean,
    rotateThumbWhenPlaying: Boolean,
    isInteracting: Boolean
) {
    Box(
        modifier = Modifier
            .offset(x = center)
            .graphicsLayer { translationX = -size.width / 2f }
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        M3Thumb(
            style = thumbStyle,
            color = color,
            size = thumbSize,
            isPlaying = isPlaying,
            rotateWhenPlaying = rotateThumbWhenPlaying,
            isInteracting = isInteracting
        )
    }
}

/**
 * Unified progress bar composable that renders different styles
 */
@Composable
fun StyledProgressBar(
    progress: Float,
    style: ProgressStyle,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
    height: Dp = 4.dp,
    isPlaying: Boolean = true,
    animated: Boolean = true,
    showThumb: Boolean = false,
    thumbStyle: ThumbStyle = ThumbStyle.DEFAULT,
    thumbSize: Dp = 12.dp,
    rotateThumbWhenPlaying: Boolean = false,
    waveAmplitudeWhenPlaying: Dp = 3.dp,
    waveLength: Dp = 40.dp,
    enabled: Boolean = true,
    isInteracting: Boolean = false,
    onSeek: ((Float) -> Unit)? = null,
    onSeekFinished: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnSeekFinished by rememberUpdatedState(onSeekFinished)

    var isPointerSeeking by remember { mutableStateOf(false) }
    var lastHapticValue by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val effectiveInteracting = isInteracting || isPointerSeeking

    val effectiveThumbSize = if (showThumb && thumbStyle != ThumbStyle.NONE) thumbSize * thumbStyle.sizeScale else 0.dp
    val trackEdgePadding = effectiveThumbSize / 2f
    val trackEdgePaddingPx = with(density) { trackEdgePadding.toPx() }

    val normalizedProgress = progress.coerceIn(0f, 1f)
    val renderedNormalizedProgress = remember { mutableFloatStateOf(normalizedProgress) }
    var lastProgressUpdateNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(normalizedProgress, effectiveInteracting, enabled) {
        val target = normalizedProgress
        if (!enabled || effectiveInteracting) {
            renderedNormalizedProgress.floatValue = target
            lastProgressUpdateNanos = System.nanoTime()
            return@LaunchedEffect
        }

        val nowNanos = System.nanoTime()
        val intervalMs = if (lastProgressUpdateNanos == 0L) 180L
        else ((nowNanos - lastProgressUpdateNanos) / 1_000_000L).coerceAtLeast(1L)
        lastProgressUpdateNanos = nowNanos

        val start = renderedNormalizedProgress.floatValue
        if (abs(start - target) <= 0.0001f) {
            renderedNormalizedProgress.floatValue = target
            return@LaunchedEffect
        }

        val durationNanos = (intervalMs * 900_000L).coerceAtLeast(1_000_000L)
        var startFrameNanos = 0L
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            if (startFrameNanos == 0L) startFrameNanos = frameNanos
            val elapsedNanos = (frameNanos - startFrameNanos).coerceAtLeast(0L)
            val fraction = (elapsedNanos.toDouble() / durationNanos.toDouble()).toFloat().coerceIn(0f, 1f)
            renderedNormalizedProgress.floatValue = start + (target - start) * fraction
            if (fraction >= 1f) break
        }
        renderedNormalizedProgress.floatValue = target
    }

    val hasThumb = showThumb && thumbStyle != ThumbStyle.NONE
    val displayProgress = if (onSeek != null) renderedNormalizedProgress.floatValue else progress
    val containerHeight = when {
        hasThumb -> thumbContainerHeight(height, effectiveThumbSize)
        style == ProgressStyle.DOTS -> height.coerceAtLeast(24.dp)
        onSeek != null -> height.coerceAtLeast(24.dp)
        else -> height
    }

    val gestureModifier = if (onSeek != null && enabled) {
        Modifier.pointerInput(enabled, trackEdgePaddingPx) {
            fun valueForX(rawX: Float): Float {
                val edgePadding = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                val trackStart = edgePadding
                val trackEnd = size.width - edgePadding
                val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
                return ((rawX - trackStart) / trackWidth).coerceIn(0f, 1f)
            }

            awaitEachGesture {
                try {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPointerSeeking = true
                    down.consume()
                    var latestGestureValue = valueForX(down.position.x)
                    latestOnSeek?.invoke(latestGestureValue)
                    lastHapticValue = (latestGestureValue * 100).roundToInt()
                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)

                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.firstOrNull { it.pressed }
                            ?: break

                        pointerId = change.id
                        if (!change.pressed) {
                            change.consume()
                            break
                        }

                        if (change.position != change.previousPosition) {
                            change.consume()
                            latestGestureValue = valueForX(change.position.x)
                            latestOnSeek?.invoke(latestGestureValue)
                            val newTick = (latestGestureValue * 100).roundToInt()
                            if (newTick != lastHapticValue) {
                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                lastHapticValue = newTick
                            }
                        }
                    }

                    latestOnSeekFinished?.invoke()
                } finally {
                    isPointerSeeking = false
                }
            }
        }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            ProgressStyle.NORMAL -> NormalProgressBar(
                progress = displayProgress,
                modifier = Modifier.fillMaxWidth(),
                progressColor = progressColor,
                trackColor = trackColor,
                height = height,
                isPlaying = isPlaying,
                showThumb = showThumb,
                thumbStyle = thumbStyle,
                thumbSize = thumbSize,
                rotateThumbWhenPlaying = rotateThumbWhenPlaying,
                isInteracting = effectiveInteracting
            )
            ProgressStyle.WAVY -> {
                if (showThumb && thumbStyle != ThumbStyle.NONE) {
                    WaveSlider(
                        value = displayProgress,
                        onValueChange = { latestOnSeek?.invoke(it) },
                        onValueChangeFinished = { latestOnSeekFinished?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled && onSeek != null,
                        isPlaying = isPlaying && animated,
                        activeTrackColor = progressColor,
                        inactiveTrackColor = trackColor,
                        thumbColor = progressColor
                    )
                } else {
                    WavyProgressBar(
                        progress = displayProgress,
                        modifier = Modifier.fillMaxWidth(),
                        progressColor = progressColor,
                        trackColor = trackColor,
                        height = height,
                        isPlaying = isPlaying && animated,
                        waveAmplitudeWhenPlaying = waveAmplitudeWhenPlaying,
                        waveLength = waveLength
                    )
                }
            }
            ProgressStyle.ROUNDED -> RoundedProgressBar(
                progress = displayProgress,
                modifier = Modifier.fillMaxWidth(),
                progressColor = progressColor,
                trackColor = trackColor,
                height = height,
                isPlaying = isPlaying,
                showThumb = showThumb,
                thumbStyle = thumbStyle,
                thumbSize = thumbSize,
                rotateThumbWhenPlaying = rotateThumbWhenPlaying,
                isInteracting = effectiveInteracting
            )
            ProgressStyle.THIN -> ThinProgressBar(
                progress = displayProgress,
                modifier = Modifier.fillMaxWidth(),
                progressColor = progressColor,
                trackColor = trackColor,
                isPlaying = isPlaying,
                showThumb = showThumb,
                thumbStyle = thumbStyle,
                thumbSize = thumbSize,
                rotateThumbWhenPlaying = rotateThumbWhenPlaying,
                isInteracting = effectiveInteracting
            )
            ProgressStyle.THICK -> ThickProgressBar(
                progress = displayProgress,
                modifier = Modifier.fillMaxWidth(),
                progressColor = progressColor,
                trackColor = trackColor,
                isPlaying = isPlaying,
                showThumb = showThumb,
                thumbStyle = thumbStyle,
                thumbSize = thumbSize,
                rotateThumbWhenPlaying = rotateThumbWhenPlaying,
                isInteracting = effectiveInteracting
            )
            ProgressStyle.GRADIENT -> GradientProgressBar(
                progress = displayProgress,
                modifier = Modifier.fillMaxWidth(),
                trackColor = trackColor,
                height = height,
                isPlaying = isPlaying,
                showThumb = showThumb,
                thumbStyle = thumbStyle,
                thumbSize = thumbSize,
                rotateThumbWhenPlaying = rotateThumbWhenPlaying,
                isInteracting = effectiveInteracting
            )
            ProgressStyle.SEGMENTED -> SegmentedProgressBar(
                progress = displayProgress,
                modifier = Modifier.fillMaxWidth(),
                progressColor = progressColor,
                trackColor = trackColor,
                height = height,
                isPlaying = isPlaying,
                showThumb = showThumb,
                thumbStyle = thumbStyle,
                thumbSize = thumbSize,
                rotateThumbWhenPlaying = rotateThumbWhenPlaying,
                isInteracting = effectiveInteracting
            )
            ProgressStyle.DOTS -> DotsProgressBar(
                progress = displayProgress,
                modifier = Modifier.fillMaxWidth(),
                activeColor = progressColor,
                inactiveColor = trackColor,
                isPlaying = isPlaying,
                showThumb = showThumb,
                thumbStyle = thumbStyle,
                thumbSize = thumbSize,
                rotateThumbWhenPlaying = rotateThumbWhenPlaying,
                isInteracting = effectiveInteracting
            )
        }
    }
}

/**
 * Shared Material 3 Expressive linear track with thumb spacing and morph animations.
 * The filled track runs into the thumb, and the clearance sits on the remaining
 * (inactive) side only, matching the wavy indicator. Dynamic edge padding keeps the
 * thumb aligned 1:1 with 0% and 100% progress without dead zones, and the thumb stays
 * anchored under the centre while it morphs during interaction.
 */
@Composable
private fun ThumbTrackProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackHeight: Dp,
    progressColor: Color,
    trackColor: Color,
    gradientColors: List<Color>? = null,
    isPlaying: Boolean = true,
    thumbStyle: ThumbStyle = ThumbStyle.DEFAULT,
    thumbSize: Dp = 12.dp,
    rotateThumbWhenPlaying: Boolean = false,
    isInteracting: Boolean = false
) {
    val progressCoerced = progress.coerceIn(0f, 1f)

    val morph = rememberThumbMorph(thumbStyle, thumbSize, isInteracting)
    val trackEdgePaddingDp = morph.effectiveSize / 2f
    val totalGap = morph.halfWidth + 6.dp
    val containerHeight = thumbContainerHeight(trackHeight, morph.effectiveSize)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        val thumbCenterDp = resolveThumbCenter(maxWidth, trackEdgePaddingDp, morph.halfWidth, progressCoerced)
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = trackHeight.toPx()
            val centerY = size.height / 2f
            val trackEdgePaddingPx = trackEdgePaddingDp.toPx()
            val trackStart = trackEdgePaddingPx
            val trackEnd = (size.width - trackEdgePaddingPx).coerceAtLeast(trackStart)
            val thumbCenterXPx = thumbCenterDp.toPx()
            val totalGapPx = totalGap.toPx()

            val activeEndX = thumbCenterXPx.coerceAtMost(trackEnd)
            if (activeEndX > trackStart) {
                if (gradientColors != null && gradientColors.size >= 2) {
                    drawLine(
                        brush = Brush.horizontalGradient(gradientColors, startX = trackStart, endX = trackEnd),
                        start = Offset(trackStart, centerY),
                        end = Offset(activeEndX, centerY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                } else {
                    drawLine(
                        color = progressColor,
                        start = Offset(trackStart, centerY),
                        end = Offset(activeEndX, centerY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }
            }

            val inactiveStartX = (thumbCenterXPx + totalGapPx + strokePx / 2f).coerceAtLeast(trackStart)
            if (inactiveStartX < trackEnd) {
                drawLine(
                    color = trackColor,
                    start = Offset(inactiveStartX, centerY),
                    end = Offset(trackEnd, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
        }

        PositionedThumb(
            center = thumbCenterDp,
            thumbStyle = thumbStyle,
            thumbSize = thumbSize,
            color = if (gradientColors != null && gradientColors.isNotEmpty()) gradientColors.last() else progressColor,
            isPlaying = isPlaying,
            rotateThumbWhenPlaying = rotateThumbWhenPlaying,
            isInteracting = isInteracting
        )
    }
}

/**
 * Standard Material3 Expressive LinearProgressIndicator
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NormalProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    progressColor: Color,
    trackColor: Color,
    height: Dp,
    isPlaying: Boolean = true,
    showThumb: Boolean = false,
    thumbStyle: ThumbStyle = ThumbStyle.DEFAULT,
    thumbSize: Dp = 12.dp,
    rotateThumbWhenPlaying: Boolean = false,
    isInteracting: Boolean = false
) {
    val progressCoerced = progress.coerceIn(0f, 1f)
    if (showThumb && thumbStyle != ThumbStyle.NONE) {
        ThumbTrackProgressBar(
            progress = progressCoerced,
            modifier = modifier,
            trackHeight = height,
            progressColor = progressColor,
            trackColor = trackColor,
            isPlaying = isPlaying,
            thumbStyle = thumbStyle,
            thumbSize = thumbSize,
            rotateThumbWhenPlaying = rotateThumbWhenPlaying,
            isInteracting = isInteracting
        )
    } else {
        LinearProgressIndicator(
            progress = { progressCoerced },
            modifier = modifier
                .fillMaxWidth()
                .height(height),
            color = progressColor,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round,
            gapSize = 4.dp
        )
    }
}

/**
 * Material 3 Expressive LinearWavyProgressIndicator
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WavyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    progressColor: Color,
    trackColor: Color,
    height: Dp,
    isPlaying: Boolean,
    waveAmplitudeWhenPlaying: Dp = 3.dp,
    waveLength: Dp = 40.dp
) {
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "amplitude"
    )

    val waveContainerHeight = max(
        height,
        max(WavyProgressIndicatorDefaults.LinearContainerHeight, 24.dp)
    )

    LinearWavyProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(waveContainerHeight),
        color = progressColor,
        trackColor = trackColor,
        gapSize = 4.dp,
        stopSize = 3.dp,
        amplitude = { p -> if (p > 0f) animatedAmplitude else 0f },
        wavelength = waveLength
    )
}

/**
 * Rounded pill-shaped progress bar
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoundedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    progressColor: Color,
    trackColor: Color,
    height: Dp,
    isPlaying: Boolean = true,
    showThumb: Boolean = false,
    thumbStyle: ThumbStyle = ThumbStyle.DEFAULT,
    thumbSize: Dp = 12.dp,
    rotateThumbWhenPlaying: Boolean = false,
    isInteracting: Boolean = false
) {
    val actualHeight = height.coerceAtLeast(6.dp)
    val progressCoerced = progress.coerceIn(0f, 1f)
    
    if (showThumb && thumbStyle != ThumbStyle.NONE) {
        ThumbTrackProgressBar(
            progress = progressCoerced,
            modifier = modifier,
            trackHeight = actualHeight,
            progressColor = progressColor,
            trackColor = trackColor,
            isPlaying = isPlaying,
            thumbStyle = thumbStyle,
            thumbSize = thumbSize,
            rotateThumbWhenPlaying = rotateThumbWhenPlaying,
            isInteracting = isInteracting
        )
    } else {
        LinearProgressIndicator(
            progress = { progressCoerced },
            modifier = modifier
                .fillMaxWidth()
                .height(actualHeight),
            color = progressColor,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round,
            gapSize = 4.dp
        )
    }
}

/**
 * Thin elegant progress line - 2.5dp height
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    progressColor: Color,
    trackColor: Color,
    isPlaying: Boolean = true,
    showThumb: Boolean = false,
    thumbStyle: ThumbStyle = ThumbStyle.DEFAULT,
    thumbSize: Dp = 10.dp,
    rotateThumbWhenPlaying: Boolean = false,
    isInteracting: Boolean = false
) {
    val progressCoerced = progress.coerceIn(0f, 1f)
    val actualHeight = 2.5.dp
    if (showThumb && thumbStyle != ThumbStyle.NONE) {
        ThumbTrackProgressBar(
            progress = progressCoerced,
            modifier = modifier,
            trackHeight = actualHeight,
            progressColor = progressColor,
            trackColor = trackColor,
            isPlaying = isPlaying,
            thumbStyle = thumbStyle,
            thumbSize = thumbSize,
            rotateThumbWhenPlaying = rotateThumbWhenPlaying,
            isInteracting = isInteracting
        )
    } else {
        LinearProgressIndicator(
            progress = { progressCoerced },
            modifier = modifier
                .fillMaxWidth()
                .height(actualHeight),
            color = progressColor,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round,
            gapSize = 3.dp
        )
    }
}

/**
 * Thick bold progress bar - 10dp height
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThickProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    progressColor: Color,
    trackColor: Color,
    isPlaying: Boolean = true,
    showThumb: Boolean = false,
    thumbStyle: ThumbStyle = ThumbStyle.DEFAULT,
    thumbSize: Dp = 14.dp,
    rotateThumbWhenPlaying: Boolean = false,
    isInteracting: Boolean = false
) {
    val progressCoerced = progress.coerceIn(0f, 1f)
    val actualHeight = 10.dp
    if (showThumb && thumbStyle != ThumbStyle.NONE) {
        ThumbTrackProgressBar(
            progress = progressCoerced,
            modifier = modifier,
            trackHeight = actualHeight,
            progressColor = progressColor,
            trackColor = trackColor,
            isPlaying = isPlaying,
            thumbStyle = thumbStyle,
            thumbSize = thumbSize,
            rotateThumbWhenPlaying = rotateThumbWhenPlaying,
            isInteracting = isInteracting
        )
    } else {
        LinearProgressIndicator(
            progress = { progressCoerced },
            modifier = modifier
                .fillMaxWidth()
                .height(actualHeight),
            color = progressColor,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round,
            gapSize = 6.dp
        )
    }
}

/**
 * Gradient colored progress bar
 */
@Composable
private fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color,
    height: Dp,
    isPlaying: Boolean = true,
    showThumb: Boolean = false,
    thumbStyle: ThumbStyle = ThumbStyle.DEFAULT,
    thumbSize: Dp = 12.dp,
    rotateThumbWhenPlaying: Boolean = false,
    isInteracting: Boolean = false
) {
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )
    val actualHeight = height.coerceAtLeast(6.dp)
    val progressCoerced = progress.coerceIn(0f, 1f)

    if (showThumb && thumbStyle != ThumbStyle.NONE) {
        ThumbTrackProgressBar(
            progress = progressCoerced,
            modifier = modifier,
            trackHeight = actualHeight,
            progressColor = gradientColors.first(),
            trackColor = trackColor,
            gradientColors = gradientColors,
            isPlaying = isPlaying,
            thumbStyle = thumbStyle,
            thumbSize = thumbSize,
            rotateThumbWhenPlaying = rotateThumbWhenPlaying,
            isInteracting = isInteracting
        )
    } else {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(actualHeight)
        ) {
            val centerY = size.height / 2
            val strokePx = actualHeight.toPx()
            val progressWidth = size.width * progressCoerced

            val gapPx = 5.dp.toPx()
            val trackStartX = (progressWidth + gapPx).coerceAtMost(size.width)
            if (trackStartX < size.width - strokePx / 2) {
                drawLine(
                    color = trackColor,
                    start = Offset(trackStartX, centerY),
                    end = Offset(size.width - strokePx / 2, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }

            if (progressWidth > strokePx / 2) {
                drawLine(
                    brush = Brush.horizontalGradient(gradientColors, endX = size.width),
                    start = Offset(strokePx / 2, centerY),
                    end = Offset(progressWidth, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Segmented progress bar with modern rounded segments and spring filling
 */
@Composable
private fun SegmentedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    progressColor: Color,
    trackColor: Color,
    height: Dp,
    isPlaying: Boolean = true,
    showThumb: Boolean = false,
    thumbStyle: ThumbStyle = ThumbStyle.DEFAULT,
    thumbSize: Dp = 12.dp,
    rotateThumbWhenPlaying: Boolean = false,
    isInteracting: Boolean = false
) {
    val segments = 16
    val actualHeight = height.coerceAtLeast(5.dp)
    val progressCoerced = progress.coerceIn(0f, 1f)
    
    if (showThumb && thumbStyle != ThumbStyle.NONE) {
        val morph = rememberThumbMorph(thumbStyle, thumbSize, isInteracting)
        val trackEdgePaddingDp = morph.effectiveSize / 2f
        val totalGap = morph.halfWidth + 6.dp
        val containerHeight = thumbContainerHeight(actualHeight, morph.effectiveSize)

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(containerHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            val thumbCenterDp = resolveThumbCenter(maxWidth, trackEdgePaddingDp, morph.halfWidth, progressCoerced)
            Canvas(Modifier.fillMaxSize()) {
                val trackEdgePaddingPx = trackEdgePaddingDp.toPx()
                val trackStart = trackEdgePaddingPx
                val trackEnd = (size.width - trackEdgePaddingPx).coerceAtLeast(trackStart)
                val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
                val thumbCenterXPx = thumbCenterDp.toPx()
                val totalGapPx = totalGap.toPx()
                val activeEndX = thumbCenterXPx.coerceAtMost(trackEnd)
                val inactiveStartX = (thumbCenterXPx + totalGapPx).coerceAtLeast(trackStart)

                val gapPx = 4.dp.toPx()
                val totalGaps = (segments - 1) * gapPx
                val segmentWidth = (trackWidth - totalGaps) / segments
                val barHeight = actualHeight.toPx()
                val topY = (size.height - barHeight) / 2f
                val cornerRadius = CornerRadius(barHeight / 2f)

                for (i in 0 until segments) {
                    val segStartX = trackStart + i * (segmentWidth + gapPx)
                    val segEndX = segStartX + segmentWidth

                    if (segEndX > inactiveStartX) {
                        val drawStart = segStartX.coerceAtLeast(inactiveStartX)
                        val drawWidth = segEndX - drawStart
                        if (drawWidth > 0f) {
                            drawRoundRect(
                                color = trackColor,
                                topLeft = Offset(drawStart, topY),
                                size = Size(drawWidth, barHeight),
                                cornerRadius = cornerRadius
                            )
                        }
                    } else if (segEndX <= activeEndX) {
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(segStartX, topY),
                            size = Size(segmentWidth, barHeight),
                            cornerRadius = cornerRadius
                        )
                    }

                    val segmentProgressStart = i.toFloat() / segments
                    val fillFraction = ((progressCoerced - segmentProgressStart) * segments).coerceIn(0f, 1f)
                    if (fillFraction > 0f && segStartX < activeEndX) {
                        val activeRight = (segStartX + segmentWidth * fillFraction).coerceAtMost(activeEndX)
                        val drawWidth = activeRight - segStartX
                        if (drawWidth > 0f) {
                            drawRoundRect(
                                color = progressColor,
                                topLeft = Offset(segStartX, topY),
                                size = Size(drawWidth, barHeight),
                                cornerRadius = cornerRadius
                            )
                        }
                    }
                }
            }

            PositionedThumb(
                center = thumbCenterDp,
                thumbStyle = thumbStyle,
                thumbSize = thumbSize,
                color = progressColor,
                isPlaying = isPlaying,
                rotateThumbWhenPlaying = rotateThumbWhenPlaying,
                isInteracting = isInteracting
            )
        }
    } else {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(actualHeight)
        ) {
            val gapPx = 4.dp.toPx()
            val totalGaps = (segments - 1) * gapPx
            val segmentWidth = (size.width - totalGaps) / segments
            val cornerRadius = CornerRadius(size.height / 2f)

            for (i in 0 until segments) {
                val segmentStartFraction = i.toFloat() / segments
                val x = i * (segmentWidth + gapPx)
                val fillFraction = ((progressCoerced - segmentStartFraction) * segments).coerceIn(0f, 1f)

                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(segmentWidth, size.height),
                    cornerRadius = cornerRadius
                )

                if (fillFraction > 0f) {
                    drawRoundRect(
                        color = progressColor,
                        topLeft = Offset(x, 0f),
                        size = Size(segmentWidth * fillFraction, size.height),
                        cornerRadius = cornerRadius
                    )
                }
            }
        }
    }
}

/**
 * Dots progress indicator with animated spring scaling
 */
@Composable
private fun DotsProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    activeColor: Color,
    inactiveColor: Color,
    isPlaying: Boolean = true,
    showThumb: Boolean = false,
    thumbStyle: ThumbStyle = ThumbStyle.DEFAULT,
    thumbSize: Dp = 12.dp,
    rotateThumbWhenPlaying: Boolean = false,
    isInteracting: Boolean = false
) {
    val dotCount = 14
    val progressCoerced = progress.coerceIn(0f, 1f)

    if (showThumb && thumbStyle != ThumbStyle.NONE) {
        val morph = rememberThumbMorph(thumbStyle, thumbSize, isInteracting)
        val trackEdgePaddingDp = morph.effectiveSize / 2f
        val totalGap = morph.halfWidth + 6.dp
        val containerHeight = thumbContainerHeight(0.dp, morph.effectiveSize)

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(containerHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            val thumbCenterDp = resolveThumbCenter(maxWidth, trackEdgePaddingDp, morph.halfWidth, progressCoerced)
            Canvas(Modifier.fillMaxSize()) {
                val trackEdgePaddingPx = trackEdgePaddingDp.toPx()
                val trackStart = trackEdgePaddingPx
                val trackEnd = (size.width - trackEdgePaddingPx).coerceAtLeast(trackStart)
                val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
                val thumbCenterXPx = thumbCenterDp.toPx()
                val totalGapPx = totalGap.toPx()
                val centerY = size.height / 2f

                for (i in 0 until dotCount) {
                    val dotX = trackStart + (i.toFloat() / (dotCount - 1).coerceAtLeast(1)) * trackWidth
                    if (dotX > thumbCenterXPx && dotX < thumbCenterXPx + totalGapPx + 2.5.dp.toPx()) {
                        continue
                    }
                    val dotThreshold = (i + 1).toFloat() / dotCount
                    val isActive = progressCoerced >= dotThreshold - (1f / dotCount / 2f)
                    val dotRadius = (if (isActive) 3.5.dp else 2.5.dp).toPx()
                    drawCircle(
                        color = if (isActive) activeColor else inactiveColor,
                        radius = dotRadius,
                        center = Offset(dotX, centerY)
                    )
                }
            }

            PositionedThumb(
                center = thumbCenterDp,
                thumbStyle = thumbStyle,
                thumbSize = thumbSize,
                color = activeColor,
                isPlaying = isPlaying,
                rotateThumbWhenPlaying = rotateThumbWhenPlaying,
                isInteracting = isInteracting
            )
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until dotCount) {
                val dotThreshold = (i + 1).toFloat() / dotCount
                val isActive = progressCoerced >= dotThreshold - (1f / dotCount / 2f)
                val animatedDotSize by animateDpAsState(
                    targetValue = if (isActive) 8.dp else 5.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "dotSize"
                )

                Box(
                    modifier = Modifier
                        .size(animatedDotSize)
                        .clip(CircleShape)
                        .background(if (isActive) activeColor else inactiveColor)
                )
            }
        }
    }
}

/**
 * Compact mini progress bar for MiniPlayer - optimized for small spaces
 */
@Composable
fun MiniProgressBar(
    progress: Float,
    style: String,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
    isPlaying: Boolean = true
) {
    val progressStyle = try {
        ProgressStyle.valueOf(style.uppercase())
    } catch (e: IllegalArgumentException) {
        ProgressStyle.NORMAL
    }
    
    StyledProgressBar(
        progress = progress,
        style = progressStyle,
        modifier = modifier,
        progressColor = progressColor,
        trackColor = trackColor,
        height = when (progressStyle) {
            ProgressStyle.THIN -> 2.dp
            ProgressStyle.THICK -> 6.dp
            ProgressStyle.WAVY -> 8.dp
            ProgressStyle.DOTS -> 6.dp
            ProgressStyle.SEGMENTED -> 4.dp
            else -> 4.dp
        },
        isPlaying = isPlaying,
        animated = true
    )
}

/**
 * Circular styled progress bar that wraps around content (like play/pause button)
 * Supports all progress styles including wavy, segmented, dots, etc.
 * The cornerRadius parameter allows the progress to adapt to button shape changes
 * (e.g., from circle when paused to rounded rect when playing)
 */
@Composable
fun CircularStyledProgressBar(
    progress: Float,
    style: ProgressStyle,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
    strokeWidth: Dp = 3.dp,
    isPlaying: Boolean = true,
    cornerRadius: Dp = 50.dp, // 50.dp = circle, lower values = more rounded rect
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            ProgressStyle.WAVY -> WavyCircularProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = strokeWidth,
                isPlaying = isPlaying,
                cornerRadius = cornerRadius
            )
            ProgressStyle.SEGMENTED -> SegmentedCircularProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = strokeWidth,
                cornerRadius = cornerRadius
            )
            ProgressStyle.DOTS -> DottedCircularProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = strokeWidth,
                cornerRadius = cornerRadius
            )
            ProgressStyle.GRADIENT -> GradientCircularProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = strokeWidth,
                cornerRadius = cornerRadius
            )
            ProgressStyle.THIN -> ThinCircularProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = strokeWidth * 0.6f,
                cornerRadius = cornerRadius
            )
            ProgressStyle.THICK -> ThickCircularProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = strokeWidth * 1.5f,
                cornerRadius = cornerRadius
            )
            ProgressStyle.ROUNDED -> RoundedCircularProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = strokeWidth,
                cornerRadius = cornerRadius
            )
            ProgressStyle.NORMAL -> NormalCircularProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = strokeWidth,
                cornerRadius = cornerRadius
            )
        }
        
        content()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WavyCircularProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Dp,
    isPlaying: Boolean,
    cornerRadius: Dp = 50.dp
) {
    val isRoundedRect = cornerRadius < 40.dp
    if (!isRoundedRect) {
        val animatedAmplitude by animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "circularWavyAmplitude"
        )
        CircularWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxSize()
                .padding(strokeWidth / 2),
            color = progressColor,
            trackColor = trackColor,
            gapSize = 4.dp,
            amplitude = { if (progress > 0f) animatedAmplitude else 0f }
        )
    } else {
        val phaseShiftAnim = remember { Animatable(0f) }
        val phaseShift = phaseShiftAnim.value

        val waveAmplitudeAnim by animateFloatAsState(
            targetValue = if (isPlaying) 0.3f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "waveAmplitude"
        )

        LaunchedEffect(isPlaying) {
            if (isPlaying) {
                val fullRotation = (2 * PI).toFloat()
                while (isPlaying) {
                    val start = (phaseShiftAnim.value % fullRotation).let {
                        if (it < 0f) it + fullRotation else it
                    }
                    phaseShiftAnim.snapTo(start)
                    phaseShiftAnim.animateTo(
                        targetValue = start + fullRotation,
                        animationSpec = tween(durationMillis = 4000, easing = LinearEasing)
                    )
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val rectCornerRadius = cornerRadius.toPx().coerceAtMost(size.minDimension / 2)
            drawRoundedRectProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = stroke,
                cornerRadius = rectCornerRadius,
                isWavy = true,
                waveOffset = phaseShift,
                waveAmplitude = waveAmplitudeAnim
            )
        }
    }
}

@Composable
private fun SegmentedCircularProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Dp,
    cornerRadius: Dp = 50.dp
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = strokeWidth.toPx()
        val rectCornerRadius = cornerRadius.toPx().coerceAtMost(size.minDimension / 2)
        val isRoundedRect = rectCornerRadius < size.minDimension / 2 - 1
        
        if (isRoundedRect) {
            drawRoundedRectSegmentedProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = stroke,
                cornerRadius = rectCornerRadius
            )
        } else {
            val radius = (size.minDimension / 2) - stroke
            val center = Offset(size.width / 2, size.height / 2)
            val segments = 20
            val segmentAngle = 360f / segments
            val gapAngle = 4f
            
            for (i in 0 until segments) {
                val startAngle = i * segmentAngle - 90f
                val segmentProgress = ((progress * segments) - i).coerceIn(0f, 1f)
                
                drawArc(
                    color = if (segmentProgress > 0) progressColor else trackColor,
                    startAngle = startAngle + gapAngle / 2,
                    sweepAngle = (segmentAngle - gapAngle) * segmentProgress.coerceAtLeast(0.01f),
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }
        }
    }
}

@Composable
private fun DottedCircularProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Dp,
    cornerRadius: Dp = 50.dp
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = strokeWidth.toPx()
        val rectCornerRadius = cornerRadius.toPx().coerceAtMost(size.minDimension / 2)
        val isRoundedRect = rectCornerRadius < size.minDimension / 2 - 1
        
        if (isRoundedRect) {
            drawRoundedRectDottedProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = stroke,
                cornerRadius = rectCornerRadius
            )
        } else {
            val radius = (size.minDimension / 2) - stroke
            val center = Offset(size.width / 2, size.height / 2)
            val dots = 24
            val dotRadius = stroke * 0.8f
            
            for (i in 0 until dots) {
                val angle = (i.toFloat() / dots) * 360f - 90f
                val angleRad = Math.toRadians(angle.toDouble())
                val dotProgress = ((progress * dots) - i).coerceIn(0f, 1f)
                
                val x = center.x + (radius * kotlin.math.cos(angleRad)).toFloat()
                val y = center.y + (radius * kotlin.math.sin(angleRad)).toFloat()
                
                drawCircle(
                    color = if (dotProgress > 0) progressColor else trackColor,
                    radius = dotRadius * (0.5f + dotProgress * 0.5f),
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun GradientCircularProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Dp,
    cornerRadius: Dp = 50.dp
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = strokeWidth.toPx()
        val rectCornerRadius = cornerRadius.toPx().coerceAtMost(size.minDimension / 2)
        val isRoundedRect = rectCornerRadius < size.minDimension / 2 - 1
        
        if (isRoundedRect) {
            drawRoundedRectGradientProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = stroke,
                cornerRadius = rectCornerRadius
            )
        } else {
            val radius = (size.minDimension / 2) - stroke
            val center = Offset(size.width / 2, size.height / 2)
            
            // Draw track
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = stroke)
            )
            
            // Draw gradient progress
            if (progress > 0f) {
                val sweepAngle = 360f * progress
                
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            progressColor,
                            progressColor.copy(alpha = 0.7f),
                            progressColor.copy(alpha = 0.9f),
                            progressColor
                        ),
                        center = center
                    ),
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }
        }
    }
}

@Composable
private fun NormalCircularProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Dp,
    cornerRadius: Dp = 50.dp
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = strokeWidth.toPx()
        val rectCornerRadius = cornerRadius.toPx().coerceAtMost(size.minDimension / 2)
        val isRoundedRect = rectCornerRadius < size.minDimension / 2 - 1
        
        if (isRoundedRect) {
            drawRoundedRectProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = stroke,
                cornerRadius = rectCornerRadius,
                isWavy = false,
                waveOffset = 0f
            )
        } else {
            val radius = (size.minDimension / 2) - stroke
            val center = Offset(size.width / 2, size.height / 2)
            
            // Draw track
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = stroke)
            )
            
            // Draw progress arc
            if (progress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }
        }
    }
}

@Composable
private fun ThinCircularProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Dp,
    cornerRadius: Dp = 50.dp
) {
    NormalCircularProgress(
        progress = progress,
        progressColor = progressColor,
        trackColor = trackColor,
        strokeWidth = strokeWidth,
        cornerRadius = cornerRadius
    )
}

@Composable
private fun ThickCircularProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Dp,
    cornerRadius: Dp = 50.dp
) {
    NormalCircularProgress(
        progress = progress,
        progressColor = progressColor,
        trackColor = trackColor,
        strokeWidth = strokeWidth,
        cornerRadius = cornerRadius
    )
}

@Composable
private fun RoundedCircularProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Dp,
    cornerRadius: Dp = 50.dp
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = strokeWidth.toPx()
        val rectCornerRadius = cornerRadius.toPx().coerceAtMost(size.minDimension / 2)
        val isRoundedRect = rectCornerRadius < size.minDimension / 2 - 1
        
        if (isRoundedRect) {
            drawRoundedRectProgress(
                progress = progress,
                progressColor = progressColor,
                trackColor = trackColor,
                strokeWidth = stroke,
                cornerRadius = rectCornerRadius,
                isWavy = false,
                waveOffset = 0f,
                useRoundCap = true
            )
        } else {
            val radius = (size.minDimension / 2) - stroke
            val center = Offset(size.width / 2, size.height / 2)
            
            // Draw track
            drawCircle(
                color = trackColor,
                radius = radius,
                center = center,
                style = Stroke(width = stroke)
            )
            
            // Draw progress arc with round cap
            if (progress > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }
        }
    }
}

// Helper function to draw rounded rectangle progress
private fun DrawScope.drawRoundedRectProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Float,
    cornerRadius: Float,
    isWavy: Boolean = false,
    waveOffset: Float = 0f,
    useRoundCap: Boolean = false,
    waveAmplitude: Float = 0.2f
) {
    val halfStroke = strokeWidth / 2
    val left = halfStroke
    val top = halfStroke
    val right = size.width - halfStroke
    val bottom = size.height - halfStroke
    val cr = cornerRadius.coerceAtMost((size.minDimension - strokeWidth) / 2)
    
    // Draw track as rounded rect stroke
    drawRoundRect(
        color = trackColor,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        cornerRadius = CornerRadius(cr),
        style = Stroke(width = strokeWidth)
    )
    
    // Draw progress - trace the rounded rect perimeter
    if (progress > 0f) {
        val path = createRoundedRectProgressPath(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            cornerRadius = cr,
            progress = progress,
            isWavy = isWavy,
            waveOffset = waveOffset,
            strokeWidth = strokeWidth,
            waveAmplitude = waveAmplitude
        )
        
        drawPath(
            path = path,
            color = progressColor,
            style = Stroke(
                width = strokeWidth,
                cap = if (useRoundCap) StrokeCap.Round else StrokeCap.Butt
            )
        )
    }
}

private fun DrawScope.drawRoundedRectSegmentedProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Float,
    cornerRadius: Float
) {
    val halfStroke = strokeWidth / 2
    val left = halfStroke
    val top = halfStroke
    val right = size.width - halfStroke
    val bottom = size.height - halfStroke
    val cr = cornerRadius.coerceAtMost((size.minDimension - strokeWidth) / 2)
    
    // Draw track
    drawRoundRect(
        color = trackColor,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        cornerRadius = CornerRadius(cr),
        style = Stroke(width = strokeWidth)
    )
    
    // Calculate perimeter for segmentation
    val segments = 20
    val perimeter = calculateRoundedRectPerimeter(right - left, bottom - top, cr)
    val segmentLength = perimeter / segments
    val gapLength = segmentLength * 0.15f
    
    for (i in 0 until segments) {
        val segmentProgress = ((progress * segments) - i).coerceIn(0f, 1f)
        if (segmentProgress > 0) {
            val startOffset = i * segmentLength + gapLength / 2
            val endOffset = startOffset + (segmentLength - gapLength) * segmentProgress
            
            val path = createRoundedRectSegmentPath(
                left = left, top = top, right = right, bottom = bottom,
                cornerRadius = cr, startOffset = startOffset, endOffset = endOffset
            )
            
            drawPath(
                path = path,
                color = progressColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    miter = 1f
                )
            )
        }
    }
}

private fun DrawScope.drawRoundedRectDottedProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Float,
    cornerRadius: Float
) {
    val halfStroke = strokeWidth / 2
    val left = halfStroke
    val top = halfStroke
    val right = size.width - halfStroke
    val bottom = size.height - halfStroke
    val cr = cornerRadius.coerceAtMost((size.minDimension - strokeWidth) / 2)
    
    val dots = 24
    val dotRadius = strokeWidth * 0.8f
    val perimeter = calculateRoundedRectPerimeter(right - left, bottom - top, cr)
    
    for (i in 0 until dots) {
        val offset = (i.toFloat() / dots) * perimeter
        val point = getPointOnRoundedRect(left, top, right, bottom, cr, offset)
        val dotProgress = ((progress * dots) - i).coerceIn(0f, 1f)
        
        drawCircle(
            color = if (dotProgress > 0) progressColor else trackColor,
            radius = dotRadius * (0.5f + dotProgress * 0.5f),
            center = point
        )
    }
}

private fun DrawScope.drawRoundedRectGradientProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    strokeWidth: Float,
    cornerRadius: Float
) {
    val halfStroke = strokeWidth / 2
    val left = halfStroke
    val top = halfStroke
    val right = size.width - halfStroke
    val bottom = size.height - halfStroke
    val cr = cornerRadius.coerceAtMost((size.minDimension - strokeWidth) / 2)
    
    // Draw track
    drawRoundRect(
        color = trackColor,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        cornerRadius = CornerRadius(cr),
        style = Stroke(width = strokeWidth)
    )
    
    // Draw gradient progress
    if (progress > 0f) {
        val path = createRoundedRectProgressPath(
            left = left, top = top, right = right, bottom = bottom,
            cornerRadius = cr, progress = progress,
            isWavy = false, waveOffset = 0f, strokeWidth = strokeWidth
        )
        
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    progressColor,
                    progressColor.copy(alpha = 0.7f),
                    progressColor.copy(alpha = 0.9f),
                    progressColor
                )
            ),
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                miter = 1f
            )
        )
    }
}

// Helper function to create path for rounded rect progress
private fun DrawScope.createRoundedRectProgressPath(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    cornerRadius: Float,
    progress: Float,
    isWavy: Boolean,
    waveOffset: Float,
    strokeWidth: Float,
    waveAmplitude: Float = 0.2f
): Path {
    val path = Path()
    val perimeter = calculateRoundedRectPerimeter(right - left, bottom - top, cornerRadius)
    val targetLength = perimeter * progress
    
    var currentLength = 0f
    val steps = 200
    val stepLength = perimeter / steps
    
    for (i in 0..steps) {
        if (currentLength > targetLength) break
        
        val offset = i * stepLength
        var point = getPointOnRoundedRect(left, top, right, bottom, cornerRadius, offset)
        
        if (isWavy && waveAmplitude > 0f) {
            // Add wave effect - amplitude controls waviness (0 = flat circle, 0.2+ = wavy)
            val wave = sin((offset / perimeter * 12 * PI) + waveOffset).toFloat() * strokeWidth * waveAmplitude
            // Apply wave perpendicular to path
            val nextPoint = getPointOnRoundedRect(left, top, right, bottom, cornerRadius, (offset + 1).coerceAtMost(perimeter))
            val dx = nextPoint.x - point.x
            val dy = nextPoint.y - point.y
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            point = Offset(
                point.x + (-dy / len) * wave,
                point.y + (dx / len) * wave
            )
        }
        
        if (i == 0) {
            path.moveTo(point.x, point.y)
        } else {
            path.lineTo(point.x, point.y)
        }
        
        currentLength += stepLength
    }
    
    return path
}

private fun DrawScope.createRoundedRectSegmentPath(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    cornerRadius: Float,
    startOffset: Float,
    endOffset: Float
): Path {
    val path = Path()
    val perimeter = calculateRoundedRectPerimeter(right - left, bottom - top, cornerRadius)
    
    val steps = 20
    val length = endOffset - startOffset
    val stepLength = length / steps
    
    for (i in 0..steps) {
        val offset = (startOffset + i * stepLength).coerceAtMost(perimeter)
        val point = getPointOnRoundedRect(left, top, right, bottom, cornerRadius, offset)
        
        if (i == 0) {
            path.moveTo(point.x, point.y)
        } else {
            path.lineTo(point.x, point.y)
        }
    }
    
    return path
}

private fun calculateRoundedRectPerimeter(width: Float, height: Float, cornerRadius: Float): Float {
    val cornerArc = 2 * PI.toFloat() * cornerRadius / 4 // Quarter circle
    val straightWidth = (width - 2 * cornerRadius).coerceAtLeast(0f)
    val straightHeight = (height - 2 * cornerRadius).coerceAtLeast(0f)
    return 4 * cornerArc + 2 * straightWidth + 2 * straightHeight
}

private fun getPointOnRoundedRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    cornerRadius: Float,
    offset: Float
): Offset {
    val width = right - left
    val height = bottom - top
    val cr = cornerRadius.coerceAtMost(kotlin.math.min(width, height) / 2)
    
    val cornerArc = PI.toFloat() * cr / 2
    val straightWidth = (width - 2 * cr).coerceAtLeast(0f)
    val straightHeight = (height - 2 * cr).coerceAtLeast(0f)
    val perimeter = 4 * cornerArc + 2 * straightWidth + 2 * straightHeight
    
    var pos = offset % perimeter
    if (pos < 0) pos += perimeter
    
    // Start from top center, go clockwise
    val topCenterX = left + width / 2
    
    // Top edge (right half)
    val topRightStraight = straightWidth / 2
    if (pos < topRightStraight) {
        return Offset(topCenterX + pos, top)
    }
    pos -= topRightStraight
    
    // Top-right corner
    if (pos < cornerArc) {
        val angle = -PI.toFloat() / 2 + (pos / cornerArc) * (PI.toFloat() / 2)
        return Offset(
            right - cr + cr * kotlin.math.cos(angle),
            top + cr + cr * kotlin.math.sin(angle)
        )
    }
    pos -= cornerArc
    
    // Right edge
    if (pos < straightHeight) {
        return Offset(right, top + cr + pos)
    }
    pos -= straightHeight
    
    // Bottom-right corner
    if (pos < cornerArc) {
        val angle = 0f + (pos / cornerArc) * (PI.toFloat() / 2)
        return Offset(
            right - cr + cr * kotlin.math.cos(angle),
            bottom - cr + cr * kotlin.math.sin(angle)
        )
    }
    pos -= cornerArc
    
    // Bottom edge
    if (pos < straightWidth) {
        return Offset(right - cr - pos, bottom)
    }
    pos -= straightWidth
    
    // Bottom-left corner
    if (pos < cornerArc) {
        val angle = PI.toFloat() / 2 + (pos / cornerArc) * (PI.toFloat() / 2)
        return Offset(
            left + cr + cr * kotlin.math.cos(angle),
            bottom - cr + cr * kotlin.math.sin(angle)
        )
    }
    pos -= cornerArc
    
    // Left edge
    if (pos < straightHeight) {
        return Offset(left, bottom - cr - pos)
    }
    pos -= straightHeight
    
    // Top-left corner
    if (pos < cornerArc) {
        val angle = PI.toFloat() + (pos / cornerArc) * (PI.toFloat() / 2)
        return Offset(
            left + cr + cr * kotlin.math.cos(angle),
            top + cr + cr * kotlin.math.sin(angle)
        )
    }
    pos -= cornerArc
    
    // Top edge (left half)
    return Offset(left + cr + pos, top)
}

