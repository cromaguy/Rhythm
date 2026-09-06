/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveScrollBar
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.DevicePosture
import chromahub.rhythm.app.util.rememberDevicePosture
import chromahub.rhythm.app.util.windowScreenHeightDp
import chromahub.rhythm.app.util.windowScreenWidthDp

/**
 * Defines the adaptive display presentation for sheets on tablets and large screen devices.
 */
enum class SheetAdaptiveType(val defaultMaxWidth: Dp) {
    /**
     * Compact centered dialog on tablet (ideal for simple selection, radios, single sliders, pickers).
     */
    COMPACT_DIALOG(540.dp),

    /**
     * Standard centered dialog on tablet (ideal for action grids, song options, multi-select, order lists).
     */
    AUTO_DIALOG(680.dp),

    /**
     * Wide centered dialog on tablet (ideal for 2-column control cards, queue, playback, autoeq pickers).
     */
    WIDE_DIALOG(880.dp),

    /**
     * Extra wide 2-pane dialog (ideal for rich metadata editors, device config, artist sheets).
     */
    TWO_PANE_DIALOG(1100.dp),

    /**
     * Always remains a bottom sheet (constrained to max 640dp).
     */
    BOTTOM_SHEET_ONLY(640.dp)
}

/**
 * Scope provided to content inside [RhythmAdaptiveModalSheet].
 */
class RhythmAdaptiveSheetScope(
    val isTablet: Boolean,
    val isLandscapeTablet: Boolean,
    val adaptiveType: SheetAdaptiveType
)

/**
 * Morphing Close Button with rounded-square on normal and circle on hover/press with red theme.
 */
@Composable
fun AdaptiveSheetCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isActive = isHovered || isPressed

    // Morph animation: rounded square (10.dp) to circle (18.dp)
    val cornerRadius by animateDpAsState(
        targetValue = if (isActive) 18.dp else 10.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "closeButtonCornerRadius"
    )

    // Color swap:
    // Normal: shape = onSurfaceVariant, icon = surfaceContainerHighest
    // Active / Hover: red themed errorContainer and onErrorContainer
    val containerColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.errorContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "closeButtonContainerColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isActive)
            MaterialTheme.colorScheme.onErrorContainer
        else
            MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "closeButtonIconColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else if (isHovered) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "closeButtonScale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isActive) 90f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "closeButtonRotation"
    )

    Box(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = RhythmIcons.Close,
            contentDescription = stringResource(R.string.bottomsheet_cancel),
            tint = iconColor,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    rotationZ = rotation
                }
        )
    }
}

/**
 * Morphing Action Button with rounded-square on normal and circle on hover/press.
 */
@Composable
fun AdaptiveSheetActionButton(
    onClick: () -> Unit,
    icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    normalContainerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
    normalContentColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    activeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isActive = (isHovered || isPressed) && enabled

    val cornerRadius by animateDpAsState(
        targetValue = if (isActive) 18.dp else 10.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "actionButtonCornerRadius"
    )

    val containerColor by animateColorAsState(
        targetValue = if (!enabled)
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
        else if (isActive)
            activeContainerColor
        else
            normalContainerColor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "actionButtonContainerColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (!enabled)
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else if (isActive)
            activeContentColor
        else
            normalContentColor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "actionButtonIconColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else if (isHovered && enabled) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "actionButtonScale"
    )

    Box(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun AdaptiveSheetActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    normalContainerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
    normalContentColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    activeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isActive = (isHovered || isPressed) && enabled

    val cornerRadius by animateDpAsState(
        targetValue = if (isActive) 18.dp else 10.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "actionButtonCornerRadius"
    )

    val containerColor by animateColorAsState(
        targetValue = if (!enabled)
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
        else if (isActive)
            activeContainerColor
        else
            normalContainerColor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "actionButtonContainerColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (!enabled)
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else if (isActive)
            activeContentColor
        else
            normalContentColor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "actionButtonIconColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else if (isHovered && enabled) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "actionButtonScale"
    )

    Box(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Standardized Adaptive Sheet container component for Rhythm.
 *
 * Automatically displays as a [ModalBottomSheet] on phone (compact screen width < 600dp),
 * and gracefully transitions into an elevated, centered [Dialog] on tablets, foldables,
 * and large displays (screen width >= 600dp / Medium & Expanded window classes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmAdaptiveModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    adaptiveType: SheetAdaptiveType = SheetAdaptiveType.AUTO_DIALOG,
    tabletMaxWidth: Dp? = null,
    showCloseButton: Boolean = true,
    lazyListState: LazyListState? = null,
    gridState: LazyGridState? = null,
    scrollState: ScrollState? = null,
    enableScrollBar: Boolean = true,
    enableBlend: Boolean = true,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    ),
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    dialogShape: Shape = RoundedCornerShape(32.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = {
        BottomSheetDefaults.DragHandle(
            color = MaterialTheme.colorScheme.primary
        )
    },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable RhythmAdaptiveSheetScope.() -> Unit
) {
    val screenWidth = windowScreenWidthDp()
    val screenHeight = windowScreenHeightDp()
    val postureState by rememberDevicePosture()

    val isTablet = screenWidth >= 600 || postureState is DevicePosture.Book || postureState is DevicePosture.Separated
    val isLandscapeTablet = isTablet && screenWidth > screenHeight
    val effectiveMaxWidth = tabletMaxWidth ?: adaptiveType.defaultMaxWidth

    val scope = RhythmAdaptiveSheetScope(
        isTablet = isTablet,
        isLandscapeTablet = isLandscapeTablet,
        adaptiveType = adaptiveType
    )

    if (isTablet && adaptiveType != SheetAdaptiveType.BOTTOM_SHEET_ONLY) {
        // Tablet / Large screen presentation: Centered Modal Dialog with dedicated top action row & outer tap-to-dismiss
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            // Fullscreen backdrop layer to reliably capture outer taps and dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onDismissRequest()
                    }
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(
                        horizontal = if (isLandscapeTablet) 36.dp else 24.dp,
                        vertical = 24.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = modifier
                        .widthIn(max = effectiveMaxWidth)
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .heightIn(max = (screenHeight * 0.90f).dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Swallow clicks inside the card so dialog does not dismiss
                        },
                    shape = dialogShape,
                    color = containerColor,
                    contentColor = contentColor,
                    tonalElevation = tonalElevation,
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(bottom = 16.dp)
                    ) {
                        // Top control bar for Tablet Dialog with morphing close button (sticky at the top)
                        if (showCloseButton) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, end = 16.dp, bottom = 0.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AdaptiveSheetCloseButton(
                                    onClick = onDismissRequest
                                )
                            }
                        }

                        // Content Column
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        ) {
                            scope.content()
                        }
                    }
                }
            }
        }
    } else {
        // Phone / Compact screen presentation: Standard Modal Bottom Sheet
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            sheetState = sheetState,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            dragHandle = dragHandle,
            properties = properties
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                scope.content()
            }
        }
    }
}
