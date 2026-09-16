/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Parametric 8-sided rounded star / cookie shape matching PixelPlayer design.
 */
class RoundedStarShape(
    private val sides: Int = 8,
    private val curve: Double = 0.1,
    private val rotation: Float = 0f,
    iterations: Int = 360
) : Shape {

    private companion object {
        const val TWO_PI = 2 * PI
    }

    private val steps = TWO_PI / min(iterations, 360)
    private val rotationDegree = (PI / 180) * rotation

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(Path().apply {
        val r = min(size.height, size.width) * 0.42 * mapRange(1.0, 0.0, 0.5, 1.0, curve)

        val xCenter = size.width * 0.5f
        val yCenter = size.height * 0.5f

        fun pointAt(t: Double): Pair<Float, Float> {
            val x = r * (cos(t - rotationDegree) * (1 + curve * cos(sides * t)))
            val y = r * (sin(t - rotationDegree) * (1 + curve * cos(sides * t)))
            return (x + xCenter).toFloat() to (y + yCenter).toFloat()
        }

        val (startX, startY) = pointAt(0.0)
        moveTo(startX, startY)

        var t = steps
        while (t < TWO_PI) {
            val (x, y) = pointAt(t)
            lineTo(x, y)
            t += steps
        }

        close()
    })

    private fun mapRange(a: Double, b: Double, c: Double, d: Double, x: Double): Double {
        return (x - a) / (b - a) * (d - c) + c
    }
}

/**
 * Horizontal Cookie Slider for Equalizer bands and general audio sliders.
 * Features:
 * - 8-sided cookie rotating thumb based on normalized progress
 * - Pill-shaped track container
 * - Tactile haptic feedback on integer step transitions
 * - Parent scroll lock during drag interaction
 * - 0 dB center tick indicator
 */
@Composable
fun CookieHorizontalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    step: Float = 0.5f,
    enabled: Boolean = true,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    thumbColor: Color = MaterialTheme.colorScheme.surface,
    trackThickness: Dp = Dp.Unspecified,
    thumbSize: Dp = 24.dp,
    thumbShape: Shape? = null,
    showCenterMarker: Boolean = true
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current

    val thumbSizePx = with(density) { thumbSize.toPx() }
    val thumbRadiusPx = thumbSizePx / 2f

    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    var lastHapticValue by remember { mutableFloatStateOf(value) }
    var isInteracting by remember { mutableStateOf(false) }
    var dragNormalizedValue by remember { mutableFloatStateOf(normalizedValue) }

    LaunchedEffect(normalizedValue, isInteracting) {
        if (!isInteracting) {
            dragNormalizedValue = normalizedValue
        }
    }

    val defaultStarShape = remember { RoundedStarShape(sides = 8, curve = 0.1) }
    val finalShape = thumbShape ?: defaultStarShape

    val thumbPath = remember(thumbSizePx, finalShape) {
        val outline = finalShape.createOutline(
            Size(thumbSizePx, thumbSizePx),
            LayoutDirection.Ltr,
            density
        )
        when (outline) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
    }

    val actualActiveTrackColor = if (enabled) activeTrackColor else activeTrackColor.copy(alpha = 0.38f)
    val actualInactiveTrackColor = inactiveTrackColor
    val actualThumbColor = if (enabled) thumbColor else MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val pillHeight = if (trackThickness != Dp.Unspecified) with(density) { trackThickness.toPx() } else heightPx
        val pillRadius = pillHeight / 2f
        val usableTrackWidth = (widthPx - pillHeight).coerceAtLeast(1f)
        val displayNormalizedValue = if (isInteracting) dragNormalizedValue else normalizedValue

        val thumbCenterX = pillRadius + (displayNormalizedValue * usableTrackWidth)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(50))
                .pointerInput(enabled, valueRange.start, valueRange.endInclusive, usableTrackWidth, pillRadius, step) {
                    if (!enabled) return@pointerInput

                    fun dispatchValue(touchX: Float, forceHaptic: Boolean = false) {
                        val relativeX = (touchX - pillRadius).coerceIn(0f, usableTrackWidth)
                        val newNormalized = (relativeX / usableTrackWidth).coerceIn(0f, 1f)

                        val rawValue = valueRange.start + newNormalized * (valueRange.endInclusive - valueRange.start)
                        val snappedValue = if (valueRange.start < 0f && valueRange.endInclusive > 0f && abs(rawValue) <= (step * 0.7f)) {
                            0.0f
                        } else if (step > 0f) {
                            round(rawValue / step) * step
                        } else {
                            round(rawValue * 10f) / 10f
                        }.coerceIn(valueRange.start, valueRange.endInclusive)

                        val snappedNormalized = ((snappedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                        dragNormalizedValue = snappedNormalized

                        onValueChange(snappedValue)

                        if (forceHaptic || snappedValue != lastHapticValue) {
                            val hapticType = if (snappedValue == 0f && lastHapticValue != 0f) {
                                HapticType.MEDIUM
                            } else {
                                HapticType.LIGHT
                            }
                            HapticUtils.performHapticFeedback(context, hapticFeedback, hapticType)
                            lastHapticValue = snappedValue
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        isInteracting = true
                        down.consume()
                        dispatchValue(down.position.x, forceHaptic = true)

                        var activePointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointerChange = event.changes.firstOrNull { it.id == activePointerId }
                                ?: event.changes.firstOrNull { it.pressed }?.also { activePointerId = it.id }
                                ?: break

                            if (!pointerChange.pressed) {
                                pointerChange.consume()
                                break
                            }

                            if (pointerChange.position.x != pointerChange.previousPosition.x) {
                                pointerChange.consume()
                                dispatchValue(pointerChange.position.x)
                            }
                        }

                        isInteracting = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
        ) {
            val centerY = size.height / 2f
            val trackTop = centerY - pillRadius

            // 1. Inactive Track (Full Big Pill)
            drawRoundRect(
                color = actualInactiveTrackColor,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, pillHeight),
                cornerRadius = CornerRadius(pillRadius)
            )

            // 2. Active Track (Filled pill up to thumb center, capped with circle)
            drawCircle(
                color = actualActiveTrackColor,
                radius = pillRadius,
                center = Offset(thumbCenterX, centerY)
            )

            val activeRectWidth = thumbCenterX
            if (activeRectWidth > 0f) {
                val activeTrackPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(
                                offset = Offset(0f, trackTop),
                                size = Size(activeRectWidth, pillHeight)
                            ),
                            topLeft = CornerRadius(pillRadius),
                            bottomLeft = CornerRadius(pillRadius),
                            topRight = CornerRadius.Zero,
                            bottomRight = CornerRadius.Zero
                        )
                    )
                }
                drawPath(
                    path = activeTrackPath,
                    color = actualActiveTrackColor
                )
            }

            // 3. Center Marker (0 dB indication)
            if (showCenterMarker && valueRange.start < 0f && valueRange.endInclusive > 0f) {
                val centerFraction = ((0f - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                val centerMarkerX = pillRadius + (centerFraction * usableTrackWidth)
                val markerColor = if (thumbCenterX >= centerMarkerX) {
                    actualThumbColor.copy(alpha = 0.5f)
                } else {
                    actualActiveTrackColor.copy(alpha = 0.45f)
                }
                drawLine(
                    color = markerColor,
                    start = Offset(centerMarkerX, trackTop + (pillHeight * 0.22f)),
                    end = Offset(centerMarkerX, trackTop + (pillHeight * 0.78f)),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 4. Rotating Cookie Thumb
            translate(
                left = thumbCenterX - thumbRadiusPx,
                top = centerY - thumbRadiusPx
            ) {
                rotate(
                    degrees = displayNormalizedValue * 360f,
                    pivot = Offset(thumbRadiusPx, thumbRadiusPx)
                ) {
                    drawPath(
                        path = thumbPath,
                        color = actualThumbColor
                    )
                }
            }
        }
    }
}
