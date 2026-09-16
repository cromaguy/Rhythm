/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.common
import androidx.compose.ui.platform.LocalContext
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import kotlin.math.abs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmallTabAnimation(
    modifier: Modifier = Modifier,
    index: Int,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onSelectedColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedColor: Color = MaterialTheme.colorScheme.surface,
    onUnselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
    title: String,
    selectedIndex: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val isSelected = index == selectedIndex
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }

    val animationSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor else unselectedColor,
        animationSpec = tween(durationMillis = 200),
        label = "Tab Background Color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) onSelectedColor else onUnselectedColor,
        animationSpec = tween(durationMillis = 200),
        label = "Tab Content Color"
    )

    LaunchedEffect(isSelected) {
        if (isSelected) {
            launch {
                scale.animateTo(1.05f, animationSpec = animationSpec)
                scale.animateTo(1f, animationSpec = animationSpec)
            }
        } else {
            scale.snapTo(1f)
        }
    }

    LaunchedEffect(selectedIndex) {
        if (!isSelected) {
            val distance = index - selectedIndex
            if (abs(distance) == 1) {
                val direction = if (distance > 0) 1 else -1
                val offsetValue = 12f * direction
                launch {
                    offsetX.animateTo(offsetValue, animationSpec = animationSpec)
                    offsetX.animateTo(0f, animationSpec = animationSpec)
                }
            } else {
                offsetX.snapTo(0f)
            }
        } else {
            offsetX.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .padding(all = 5.dp)
            .graphicsLayer {
                scaleX = scale.value
                translationX = offsetX.value
                this.transformOrigin = transformOrigin
            }
            .clip(CircleShape)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(50)
            )
            .combinedClickable(
                onClick = {
                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                    onClick()
                },
                onLongClick = onLongClick?.let { action ->
                    {
                        HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
                        action()
                    }
                }
            )
            .semantics {
                this.selected = isSelected
                this.role = Role.Tab
            },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor
        ) {
            content()
        }
    }
}
