/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.common
import androidx.compose.ui.platform.LocalContext
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType


import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun ArcProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackThickness: Dp = 4.dp,
    thumbSize: Dp = 16.dp,
    waveAmplitude: Dp = 2.dp,
    waveLength: Dp = 20.dp,
    startAngle: Float = 135f,
    sweepAngle: Float = 270f
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val trackThicknessPx = with(density) { trackThickness.toPx() }
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val waveAmplitudePx = with(density) { waveAmplitude.toPx() }
    val waveLengthPx = with(density) { waveLength.toPx() }
    val thumbRadius = thumbSizePx / 2

    // Normalize value
    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    val phaseShift = 0f

    var isInteracting by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (isInteracting) 1.25f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ThumbScale"
    )

    // Store integer value for haptic feedback
    var lastHapticValue by remember { mutableIntStateOf(value.roundToInt()) }

    LaunchedEffect(value, isInteracting) {
        if (!isInteracting) {
            lastHapticValue = value.roundToInt()
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val minDim = min(width, height)

        // Calculate arc geometry
        val arcDiameter = minDim - thumbSizePx * 2 - waveAmplitudePx * 2
        val arcRadius = arcDiameter / 2
        val arcCenter = Offset(width / 2, height / 2)

        fun mapTouchToValue(touchPoint: Offset): Float {
            val dx = touchPoint.x - arcCenter.x
            val dy = touchPoint.y - arcCenter.y
            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (angle < 0f) angle += 360f

            var relativeAngle = angle - startAngle
            if (relativeAngle < 0f) relativeAngle += 360f

            if (relativeAngle > sweepAngle) {
                val halfDeadZone = (360f - sweepAngle).coerceAtLeast(0f) / 2f
                relativeAngle = if (relativeAngle < sweepAngle + halfDeadZone) sweepAngle else 0f
            }

            val progress = if (sweepAngle == 0f) 0f else (relativeAngle / sweepAngle).coerceIn(0f, 1f)
            return valueRange.start + progress * (valueRange.endInclusive - valueRange.start)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, view, valueRange.start, valueRange.endInclusive, startAngle, sweepAngle, arcCenter) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            isInteracting = true
                            down.consume()

                            fun dispatchValue(point: Offset, forceHaptic: Boolean = false) {
                                val newValue = mapTouchToValue(point)
                                currentOnValueChange(newValue)
                                val newInt = newValue.roundToInt()
                                if (forceHaptic || newInt != lastHapticValue) {
                                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                    lastHapticValue = newInt
                                }
                            }

                            dispatchValue(down.position, forceHaptic = true)

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

                                pointerChange.consume()
                                dispatchValue(pointerChange.position)
                            }
                        } finally {
                            isInteracting = false
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            currentOnValueChangeFinished?.invoke()
                        }
                    }
                }
        ) {
            val activeSweep = sweepAngle * normalizedValue

            val thumbGapPx = thumbRadius * thumbScale + 6.dp.toPx() + trackThicknessPx / 2f
            val thumbGapDeg = if (arcRadius > 0f) {
                Math.toDegrees(thumbGapPx.toDouble() / arcRadius.toDouble()).toFloat()
            } else 0f
            val inactiveStartAngle = startAngle + activeSweep + thumbGapDeg
            val inactiveSweep = sweepAngle - activeSweep - thumbGapDeg

            if (inactiveSweep > 0f) {
                drawArc(
                    color = inactiveTrackColor,
                    startAngle = inactiveStartAngle,
                    sweepAngle = inactiveSweep,
                    useCenter = false,
                    style = Stroke(width = trackThicknessPx, cap = StrokeCap.Round),
                    topLeft = Offset(arcCenter.x - arcRadius, arcCenter.y - arcRadius),
                    size = Size(arcDiameter, arcDiameter)
                )
            }

            if (activeSweep > 0) {
                val wavePath = Path()

                val steps = (activeSweep * 2).toInt().coerceAtLeast(10)
                val angleStep = activeSweep / steps

                var firstPointSet = false

                for (i in 0..steps) {
                    val currentAngleDeg = startAngle + (i * angleStep)
                    val currentAngleRad = Math.toRadians(currentAngleDeg.toDouble())

                    val angleFromStartRad = Math.toRadians((i * angleStep).toDouble())
                    val distanceAlongArc = arcRadius * angleFromStartRad

                    val k = (2 * PI) / waveLengthPx
                    val h = if (enabled) waveAmplitudePx * sin(k * distanceAlongArc + phaseShift) else 0.0

                    val r = arcRadius + h

                    val x = arcCenter.x + r * cos(currentAngleRad)
                    val y = arcCenter.y + r * sin(currentAngleRad)

                    if (!firstPointSet) {
                        wavePath.moveTo(x.toFloat(), y.toFloat())
                        firstPointSet = true
                    } else {
                        wavePath.lineTo(x.toFloat(), y.toFloat())
                    }
                }

                drawPath(
                    path = wavePath,
                    color = activeTrackColor,
                    style = Stroke(width = trackThicknessPx, cap = StrokeCap.Round)
                )
            }

            val thumbAngleDeg = startAngle + activeSweep
            val thumbAngleRad = Math.toRadians(thumbAngleDeg.toDouble())
            val thumbX = arcCenter.x + arcRadius * cos(thumbAngleRad)
            val thumbY = arcCenter.y + arcRadius * sin(thumbAngleRad)

            drawCircle(
                color = thumbColor,
                radius = thumbRadius * thumbScale,
                center = Offset(thumbX.toFloat(), thumbY.toFloat())
            )
        }
    }
}
