/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveScrollBar

/**
 * Standardized bottom sheet header component
 * Used across all bottom sheets for consistency
 * Matches the pattern from CastHeader with title and subtitle
 */
@Composable
fun StandardBottomSheetHeader(
    title: String,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    icon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    if (visible) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start
                )
                if (subtitle.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            text = subtitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}

/**
 * Standard scrollable container for bottom sheets and tablet dialogs.
 *
 * - Sits below fixed/sticky headers so the scrollbar NEVER overlaps the header.
 * - Provides top and bottom vertical gradient blends (fade-out transitions) identical to QueueBottomSheet.
 * - Provides dynamic end padding (32.dp when scrollbar is present, 0.dp otherwise) to content items so the scrollbar doesn't overlap cards/switches.
 * - Displays an ExpressiveScrollBar aligned to the center end of the scrollable region.
 */
@Composable
fun AdaptiveSheetScrollContainer(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState? = null,
    gridState: LazyGridState? = null,
    scrollState: ScrollState? = null,
    enableBlend: Boolean = true,
    enableScrollBar: Boolean = true,
    blendHeight: Dp = 24.dp,
    blendColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    scrollBarPaddingEnd: Dp = 4.dp,
    content: @Composable BoxScope.(endPadding: Dp) -> Unit
) {
    val canScrollForward by remember(lazyListState, gridState, scrollState) {
        derivedStateOf {
            lazyListState?.canScrollForward ?: gridState?.canScrollForward ?: scrollState?.canScrollForward ?: false
        }
    }
    val canScrollBackward by remember(lazyListState, gridState, scrollState) {
        derivedStateOf {
            lazyListState?.canScrollBackward ?: gridState?.canScrollBackward ?: scrollState?.canScrollBackward ?: false
        }
    }
    val canScroll = canScrollForward || canScrollBackward

    val animatedEndPadding by animateDpAsState(
        targetValue = if (canScroll && enableScrollBar) 12.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "SheetScrollEndPadding"
    )

    val topBlendAlpha by animateFloatAsState(
        targetValue = if (enableBlend && canScrollBackward) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "TopBlendAlpha"
    )

    val bottomBlendAlpha by animateFloatAsState(
        targetValue = if (enableBlend && canScrollForward) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BottomBlendAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        // Main scrollable content with animated end padding
        content(animatedEndPadding)

        if (enableBlend) {
            // Top sticky blend: visible ONLY when user scrolls down from the top
            if (topBlendAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(blendHeight)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = topBlendAlpha }
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    blendColor,
                                    blendColor.copy(alpha = 0.72f),
                                    blendColor.copy(alpha = 0.32f),
                                    Color.Transparent
                                )
                            )
                        )
                        .zIndex(5f)
                )
            }

            // Bottom blend: visible ONLY when more scrollable content exists below
            if (bottomBlendAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(blendHeight)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = bottomBlendAlpha }
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    blendColor.copy(alpha = 0.32f),
                                    blendColor.copy(alpha = 0.72f),
                                    blendColor
                                )
                            )
                        )
                        .zIndex(5f)
                )
            }
        }

        // Expressive Scrollbar inside the content region (strictly below the header)
        if (enableScrollBar && (lazyListState != null || gridState != null || scrollState != null)) {
            ExpressiveScrollBar(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = scrollBarPaddingEnd, top = 4.dp, bottom = 4.dp)
                    .zIndex(10f),
                listState = lazyListState,
                gridState = gridState,
                scrollState = scrollState,
                visible = canScroll
            )
        }
    }
}

fun groupedBottomSheetItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    if (totalCount <= 1) return RoundedCornerShape(24.dp)
    return when (index) {
        0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        totalCount - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

