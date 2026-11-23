/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.components.common
import androidx.media3.common.Player
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource

// ============================================================================
// EXPRESSIVE SHAPES
// ============================================================================

/**
 * Expressive shape tokens for Material 3 Expressive design
 * Uses organic, rounded shapes inspired by M3 Expressive guidelines
 */
object ExpressiveShapes {
    // Fully rounded pill shapes for buttons and FABs
    val Full = CircleShape
    val ExtraLarge = RoundedCornerShape(28.dp)
    val Large = RoundedCornerShape(24.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Small = RoundedCornerShape(12.dp)
    val ExtraSmall = RoundedCornerShape(8.dp)
    
    // Squircle-inspired shapes (rounded with flatter curves)
    val SquircleExtraLarge = RoundedCornerShape(32.dp)
    val SquircleLarge = RoundedCornerShape(28.dp)
    val SquircleMedium = RoundedCornerShape(20.dp)
    val SquircleSmall = RoundedCornerShape(14.dp)
}

/**
 * Expressive elevation values
 */
object ExpressiveElevation {
    val Level0 = 0.dp
    val Level1 = 1.dp
    val Level2 = 3.dp
    val Level3 = 6.dp
    val Level4 = 8.dp
    val Level5 = 12.dp
    
    // Pressed state elevations
    val PressedButton = 2.dp
    val HoveredButton = 4.dp
}

// ============================================================================
// EXPRESSIVE BUTTONS
// ============================================================================

/**
 * Expressive filled button with bouncy animation and pill shape
 * Perfect for primary actions like "Play All", "Save", "Continue"
 */
@Composable
fun ExpressiveFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ExpressiveShapes.Full,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Expressive filled tonal button for secondary actions
 */
@Composable
fun ExpressiveFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ExpressiveShapes.Full,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Expressive outlined button for tertiary actions
 */
@Composable
fun ExpressiveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ExpressiveShapes.Full,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Expressive text button for low-emphasis actions
 */
@Composable
fun ExpressiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ExpressiveShapes.Full,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )
    
    TextButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

// ============================================================================
// EXPRESSIVE ICON BUTTONS
// ============================================================================

/**
 * Expressive filled icon button with bouncy press animation
 * Perfect for player controls, action buttons
 */
@Composable
fun ExpressiveFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ExpressiveShapes.Full,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_button_scale"
    )
    
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Expressive filled tonal icon button
 */
@Composable
fun ExpressiveFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ExpressiveShapes.Full,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_button_scale"
    )
    
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Expressive standard icon button
 */
@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_button_scale"
    )
    
    IconButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Large expressive icon button for primary player controls
 * Uses larger hit target and more prominent visual feedback
 */
@Composable
fun ExpressiveLargeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 72.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "large_icon_button_scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isPressed) ExpressiveElevation.Level2 else ExpressiveElevation.Level3,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "large_icon_button_elevation"
    )
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = ExpressiveShapes.Full,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = elevation,
        shadowElevation = elevation,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

// ============================================================================
// EXPRESSIVE FLOATING ACTION BUTTONS
// ============================================================================

/**
 * Expressive small FAB with bouncy animation
 */
@Composable
fun ExpressiveSmallFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveShapes.Large,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "small_fab_scale"
    )
    
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Expressive medium FAB - standard size with bouncy animation
 */
@Composable
fun ExpressiveMediumFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveShapes.Large,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "medium_fab_scale"
    )
    
    MediumFloatingActionButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Expressive large FAB for primary actions
 */
@Composable
fun ExpressiveLargeFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveShapes.ExtraLarge,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "large_fab_scale"
    )
    
    LargeFloatingActionButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
        content = content
    )
}

/**
 * Expressive extended FAB with text label
 */
@Composable
fun ExpressiveExtendedFAB(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    shape: Shape = ExpressiveShapes.Full,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "extended_fab_scale"
    )
    
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        expanded = expanded,
        icon = icon,
        text = text,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource
    )
}

// ============================================================================
// EXPRESSIVE CARDS
// ============================================================================

/**
 * Expressive card with organic shape and subtle elevation animation
 */
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = ExpressiveShapes.Large,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors
        ) {
            content()
        }
    }
}

/**
 * Expressive elevated card with shadow and depth
 */
@Composable
fun ExpressiveElevatedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = ExpressiveShapes.Large,
    colors: CardColors = CardDefaults.elevatedCardColors(),
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors
        ) {
            content()
        }
    } else {
        ElevatedCard(
            modifier = modifier,
            shape = shape,
            colors = colors
        ) {
            content()
        }
    }
}

/**
 * Expressive outlined card
 */
@Composable
fun ExpressiveOutlinedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = ExpressiveShapes.Large,
    colors: CardColors = CardDefaults.outlinedCardColors(),
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors
        ) {
            content()
        }
    } else {
        OutlinedCard(
            modifier = modifier,
            shape = shape,
            colors = colors
        ) {
            content()
        }
    }
}

// ============================================================================
// EXPRESSIVE CHIPS
// ============================================================================

/**
 * Expressive filter chip with pill shape and bouncy animation
 */
@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = ExpressiveShapes.Full,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
    ),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "filter_chip_scale"
    )
    
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        interactionSource = interactionSource
    )
}

/**
 * Expressive assist chip
 */
@Composable
fun ExpressiveAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = ExpressiveShapes.Full,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "assist_chip_scale"
    )
    
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        interactionSource = interactionSource
    )
}

/**
 * Expressive input chip
 */
@Composable
fun ExpressiveInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    avatar: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = ExpressiveShapes.Full,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "input_chip_scale"
    )
    
    androidx.compose.material3.InputChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        leadingIcon = leadingIcon,
        avatar = avatar,
        trailingIcon = trailingIcon,
        shape = shape,
        interactionSource = interactionSource
    )
}

/**
 * Expressive suggestion chip
 */
@Composable
fun ExpressiveSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    shape: Shape = ExpressiveShapes.Full,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "suggestion_chip_scale"
    )
    
    androidx.compose.material3.SuggestionChip(
        onClick = onClick,
        label = label,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        icon = icon,
        shape = shape,
        interactionSource = interactionSource
    )
}

// ============================================================================
// EXPRESSIVE SURFACES
// ============================================================================

/**
 * Expressive surface with organic shape
 */
@Composable
fun ExpressiveSurface(
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveShapes.Large,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        content = content
    )
}

/**
 * Expressive clickable surface
 */
@Composable
fun ExpressiveClickableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ExpressiveShapes.Large,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "surface_scale"
    )
    
    Surface(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        interactionSource = interactionSource,
        content = content
    )
}

// ============================================================================
// CONVENIENCE COMPOSABLES
// ============================================================================

/**
 * Expressive button with icon and text
 */
@Composable
fun ExpressiveButtonWithIcon(
    onClick: () -> Unit,
    text: String,
    icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconOnStart: Boolean = true
) {
    ExpressiveFilledButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    ) {
        if (iconOnStart) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
        if (!iconOnStart) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Expressive tonal button with icon and text
 */
@Composable
fun ExpressiveTonalButtonWithIcon(
    onClick: () -> Unit,
    text: String,
    icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconOnStart: Boolean = true
) {
    ExpressiveFilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    ) {
        if (iconOnStart) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
        if (!iconOnStart) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Play button - primary action for music playback
 */
@Composable
fun ExpressivePlayButton(
    onClick: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val playIcon = if (isPlaying) RhythmIcons.Pause else RhythmIcons.Play
    
    ExpressiveLargeIconButton(
        onClick = onClick,
        modifier = modifier,
        size = size,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Icon(
            imageVector = playIcon,
            contentDescription = if (isPlaying) "Pause" else "Play",
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * Action row with multiple expressive buttons
 */
@Composable
fun ExpressiveActionRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ============================================================================
// EXPRESSIVE BUTTON GROUP
// ============================================================================

/**
 * Material 3 Expressive Button Group for connected button actions
 * Perfect for Play/Shuffle, Sort/Filter, etc.
 * 
 * Creates visually grouped buttons with continuous background and minimal spacing
 */
@Composable
fun ExpressiveButtonGroup(
    modifier: Modifier = Modifier,
    style: ButtonGroupStyle = ButtonGroupStyle.Tonal,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Button Group Style
 */
enum class ButtonGroupStyle {
    Filled,
    Tonal,
    Outlined
}

/**
 * Individual button within an ExpressiveButtonGroup
 * Automatically handles start/end/middle positioning
 */
@Composable
fun ExpressiveGroupButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isStart: Boolean = false,
    isEnd: Boolean = false,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val shape = when {
        isStart && isEnd -> ExpressiveShapes.Full // Single button
        isStart -> RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 8.dp, bottomEnd = 8.dp)
        isEnd -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 20.dp, bottomEnd = 20.dp)
        else -> RoundedCornerShape(8.dp) // Middle button
    }
    
    if (onLongClick != null) {
        var isPressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "button_scale"
        )
        Surface(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(onClick, onLongClick, enabled) {
                    detectTapGestures(
                        onPress = {
                            if (enabled) {
                                isPressed = true
                                tryAwaitRelease()
                                isPressed = false
                            }
                        },
                        onTap = { if (enabled) onClick() },
                        onLongPress = { if (enabled) onLongClick() }
                    )
                },
            shape = shape,
            color = colors.containerColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .defaultMinSize(
                        minWidth = ButtonDefaults.MinWidth,
                        minHeight = ButtonDefaults.MinHeight
                    )
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    } else {
        ExpressiveFilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            contentPadding = contentPadding,
            content = content
        )
    }
}

// ============================================================================
// EXPRESSIVE SETTINGS GROUP
// ============================================================================

/**
 * Material 3 Expressive Settings Group
 * Replaces individual settings with dividers by using a unified card container
 * with subtle spacing between items
 */
@Composable
fun ExpressiveSettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
        
        ExpressiveCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = containerColor
            ),
            shape = ExpressiveShapes.Large
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * Individual setting item within an ExpressiveSettingsGroup
 * No dividers - spacing handled by padding
 */
@Composable
fun ExpressiveSettingItem(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        content()
    }
}

// ============================================================================
// EXPRESSIVE PLAYER CONTROLS
// ============================================================================

/**
 * Expressive Player Control Button Group
 * Groups playback controls (Prev, SeekBack, Play/Pause, SeekForward, Next) 
 * with unified background and native expressive grouping for spacing.
 */
private enum class PlaybackButtonType { PREVIOUS, SEEK_BACK, PLAY_PAUSE, SEEK_FORWARD, NEXT }

@Composable
fun ExpressivePlayerControlGroup(
    isPlaying: Boolean,
    showSeekButtons: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
    isExtraSmallWidth: Boolean = false,
    isCompactWidth: Boolean = false,
    isCompactHeight: Boolean = false,
    isLoading: Boolean = false,
    useGlassEffect: Boolean = false,
    showContainer: Boolean = true,
    sizeScale: Float = 1f
) {
    // Animation state management
    var lastClicked by remember { mutableStateOf<PlaybackButtonType?>(null) }
    val isPlayPauseLocked =
        lastClicked == PlaybackButtonType.NEXT || lastClicked == PlaybackButtonType.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(lastClicked) {
        if (lastClicked != null) {
            delay(220L) // releaseDelay
            lastClicked = null
        }
    }

    LaunchedEffect(isPlaying) {
        pendingPlayPauseState = isPlaying
    }

    LaunchedEffect(isPlayPauseLocked, pendingPlayPauseState) {
        if (!isPlayPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    // Container height and padding — scale down for compact-height phones
    // Container height and padding — scale down for compact-height phones but ensure 48.dp minimum touch target
    val containerHeight = when {
        isExtraSmallWidth && isCompactHeight -> 68.dp
        isExtraSmallWidth -> 72.dp
        isCompactHeight -> 68.dp
        isCompactWidth -> 76.dp
        else -> 80.dp
    } * sizeScale
    
    val containerPadding = when {
        isCompactHeight -> 8.dp
        isExtraSmallWidth -> 10.dp
        isCompactWidth -> 12.dp
        else -> 12.dp
    }
    
    // Button sizes (minimum 48.dp touch target)
    val prevNextSize = when {
        isExtraSmallWidth && isCompactHeight -> 48.dp
        isExtraSmallWidth -> 48.dp
        isCompactHeight -> 48.dp
        isCompactWidth -> 48.dp
        else -> 50.dp
    } * sizeScale
    
    val seekButtonSize = when {
        isExtraSmallWidth && isCompactHeight -> 48.dp
        isExtraSmallWidth -> 48.dp
        isCompactHeight -> 48.dp
        isCompactWidth -> 54.dp
        else -> 60.dp
    } * sizeScale
    
    // Spacing using native expressive pattern
    val buttonSpacing = when {
        isExtraSmallWidth -> 6.dp
        isCompactHeight -> 6.dp
        isCompactWidth -> 8.dp
        else -> 8.dp
    }
    
    // Weight calculation function
    fun weightFor(button: PlaybackButtonType): Float = when (lastClicked) {
        button -> when (button) {
            PlaybackButtonType.PLAY_PAUSE -> 1.3f // play/pause expands more
            else -> 1.1f
        }
        null -> when (button) {
            PlaybackButtonType.PLAY_PAUSE -> 1.2f // play/pause is wider by default
            else -> 1f
        }
        else -> when (button) {
            PlaybackButtonType.PLAY_PAUSE -> 0.8f // play/pause compresses less
            else -> 0.65f
        }
    }
    
    // Animation specs
    val pressAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    
    // Animated weights
    val prevWeight by animateFloatAsState(
        targetValue = weightFor(PlaybackButtonType.PREVIOUS),
        animationSpec = pressAnimationSpec,
        label = "prevWeight"
    )
    val seekBackWeight by animateFloatAsState(
        targetValue = if (showSeekButtons) weightFor(PlaybackButtonType.SEEK_BACK) else 0f,
        animationSpec = pressAnimationSpec,
        label = "seekBackWeight"
    )
    val playWeight by animateFloatAsState(
        targetValue = weightFor(PlaybackButtonType.PLAY_PAUSE),
        animationSpec = pressAnimationSpec,
        label = "playWeight"
    )
    val seekForwardWeight by animateFloatAsState(
        targetValue = if (showSeekButtons) weightFor(PlaybackButtonType.SEEK_FORWARD) else 0f,
        animationSpec = pressAnimationSpec,
        label = "seekForwardWeight"
    )
    val nextWeight by animateFloatAsState(
        targetValue = weightFor(PlaybackButtonType.NEXT),
        animationSpec = pressAnimationSpec,
        label = "nextWeight"
    )
    
    // Corner radius animation for play/pause
    val playCorner by animateDpAsState(
        targetValue = if (!playPauseVisualState) 60.dp else 26.dp, // playPauseCornerPlaying : playPauseCornerPaused
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "playCorner"
    )

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Unified background surface
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            when {
                isCompactHeight -> 28.dp
                isExtraSmallWidth -> 32.dp
                isCompactWidth -> 36.dp
                else -> 40.dp
            }
        ),
        color = when {
            !showContainer -> Color.Transparent
            useGlassEffect -> (if (isDark) Color.White else Color.Black).copy(alpha = 0.07f)
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        border = if (showContainer && useGlassEffect) BorderStroke(1.dp, (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f)) else null,
        tonalElevation = if (showContainer && !useGlassEffect) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
                .padding(horizontal = containerPadding, vertical = containerPadding),
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous button
            Box(
                modifier = Modifier
                    .weight(prevWeight.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(containerHeight / 2))
                    .background(
                        if (useGlassEffect) {
                            (if (isDark) Color.White else Color.Black).copy(alpha = 0.1f)
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                    .then(
                        if (useGlassEffect) {
                            Modifier.border(
                                1.dp,
                                (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f),
                                RoundedCornerShape(containerHeight / 2)
                            )
                        } else Modifier
                    )
                    .clickable {
                        lastClicked = PlaybackButtonType.PREVIOUS
                        onPrevious()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = RhythmIcons.SkipPrevious,
                    contentDescription = stringResource(R.string.animatedplaybackcontrols_previous),
                    tint = if (useGlassEffect) {
                        if (isDark) Color.White else Color.Black
                    } else {
                        MaterialTheme.colorScheme.onTertiary
                    },
                    modifier = Modifier.size(prevNextSize * 0.5f)
                )
            }
            
            // Seek back button - always visible if enabled
            if (showSeekButtons) {
                Box(
                    modifier = Modifier
                        .weight(seekBackWeight.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(containerHeight / 2))
                        .background(
                            if (useGlassEffect) {
                                (if (isDark) Color.White else Color.Black).copy(alpha = 0.08f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                        .then(
                            if (useGlassEffect) {
                                Modifier.border(
                                    1.dp,
                                    (if (isDark) Color.White else Color.Black).copy(alpha = 0.10f),
                                    RoundedCornerShape(containerHeight / 2)
                                )
                            } else Modifier
                        )
                        .clickable {
                            lastClicked = PlaybackButtonType.SEEK_BACK
                            onSeekBack()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RhythmIcons.Replay10,
                        contentDescription = stringResource(R.string.expressivecomponents_seek_back),
                        tint = if (useGlassEffect) {
                            (if (isDark) Color.White else Color.Black).copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(seekButtonSize * 0.5f)
                    )
                }
            }
            
            // Play/Pause button - center
            Box(
                modifier = Modifier
                    .weight(playWeight.coerceAtLeast(0.001f))
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveMorphingPlayPauseButton(
                    isPlaying = playPauseVisualState,
                    onClick = {
                        lastClicked = PlaybackButtonType.PLAY_PAUSE
                        onPlayPause()
                    },
                    isExtraSmallWidth = isExtraSmallWidth,
                    isCompactWidth = isCompactWidth,
                    isLoading = isLoading,
                    showSeekButtons = showSeekButtons,
                    cornerRadius = playCorner,
                    useGlassEffect = useGlassEffect,
                    isDark = isDark,
                    iconScale = sizeScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Seek forward button - always visible if enabled
            if (showSeekButtons) {
                Box(
                    modifier = Modifier
                        .weight(seekForwardWeight.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(containerHeight / 2))
                        .background(
                            if (useGlassEffect) {
                                (if (isDark) Color.White else Color.Black).copy(alpha = 0.08f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                        .then(
                            if (useGlassEffect) {
                                Modifier.border(
                                    1.dp,
                                    (if (isDark) Color.White else Color.Black).copy(alpha = 0.10f),
                                    RoundedCornerShape(containerHeight / 2)
                                )
                            } else Modifier
                        )
                        .clickable {
                            lastClicked = PlaybackButtonType.SEEK_FORWARD
                            onSeekForward()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RhythmIcons.Forward10,
                        contentDescription = stringResource(R.string.expressivecomponents_seek_forward),
                        tint = if (useGlassEffect) {
                            (if (isDark) Color.White else Color.Black).copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(seekButtonSize * 0.5f)
                    )
                }
            }
            
            // Next button
            Box(
                modifier = Modifier
                    .weight(nextWeight.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(containerHeight / 2))
                    .background(
                        if (useGlassEffect) {
                            (if (isDark) Color.White else Color.Black).copy(alpha = 0.1f)
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                    .then(
                        if (useGlassEffect) {
                            Modifier.border(
                                1.dp,
                                (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f),
                                RoundedCornerShape(containerHeight / 2)
                            )
                        } else Modifier
                    )
                    .clickable {
                        lastClicked = PlaybackButtonType.NEXT
                        onNext()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = RhythmIcons.SkipNext,
                    contentDescription = stringResource(R.string.onboarding_next),
                    tint = if (useGlassEffect) {
                        if (isDark) Color.White else Color.Black
                    } else {
                        MaterialTheme.colorScheme.onTertiary
                    },
                    modifier = Modifier.size(prevNextSize * 0.5f)
                )
            }
        }
    }
}

/**
 * Morphing Play/Pause button that expands to pill when seek buttons disabled
 */
@Composable
private fun ExpressiveMorphingPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    isExtraSmallWidth: Boolean,
    isCompactWidth: Boolean,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    showSeekButtons: Boolean = true,
    cornerRadius: Dp = 26.dp,
    useGlassEffect: Boolean = false,
    isDark: Boolean = false,
    iconScale: Float = 1f
) {
    // Show PAUSE text after 2 seconds when paused
    var showPauseText by remember { mutableStateOf(false) }
    
    LaunchedEffect(isPlaying) {
        if (!isPlaying && !showSeekButtons) {
            delay(2000)
            showPauseText = true
        } else {
            showPauseText = false
        }
    }
    
    val width by animateDpAsState(
        targetValue = when {
            !showSeekButtons -> when {
                isExtraSmallWidth -> if (showPauseText) 120.dp else if (isPlaying) 110.dp else 120.dp
                isCompactWidth -> if (showPauseText) 140.dp else 130.dp
                else -> if (showPauseText) 150.dp else 140.dp
            }
            else -> when {
                isExtraSmallWidth -> 48.dp
                isCompactWidth -> 54.dp
                else -> 60.dp
            }
        },
        label = "playPauseWidth"
    )
    
    val height = when {
        isExtraSmallWidth -> 48.dp
        isCompactWidth -> 54.dp
        else -> 60.dp
    }
    
    val buttonBg = if (useGlassEffect) (if (isDark) Color.White else Color.Black) else MaterialTheme.colorScheme.primary
    val buttonTint = if (useGlassEffect) (if (isDark) Color.Black else Color.White) else MaterialTheme.colorScheme.onPrimary
    
    if (!showSeekButtons && !isLoading) {
        // Pill button with text when seek buttons disabled
        Box(
            modifier = modifier
                .then(if (modifier == Modifier) Modifier.width(width) else Modifier)
                .fillMaxHeight()
                .clip(RoundedCornerShape(percent = 50))
                .background(buttonBg)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Crossfade(
                    targetState = isPlaying,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "playPauseCrossfade"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) RhythmIcons.Pause else RhythmIcons.Play,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = buttonTint,
                        modifier = Modifier.size((if (isExtraSmallWidth) 18.dp else 24.dp) * iconScale)
                    )
                }
                AnimatedVisibility(
                    visible = !isPlaying || showPauseText,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Text(
                        text = if (isPlaying) "PAUSE" else "PLAY",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = buttonTint,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    } else {
        // Icon button when seek buttons enabled or loading
        Box(
            modifier = modifier
                .then(if (modifier == Modifier) Modifier.size(height) else Modifier.fillMaxWidth())
                .fillMaxHeight()
                .clip(RoundedCornerShape(percent = 50))
                .background(buttonBg)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // When loading, show ONLY the loader, hide everything else
            if (isLoading) {
                val rotation by animateFloatAsState(
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )
                
                Box(
                    modifier = Modifier
                        .size(if (isExtraSmallWidth) 20.dp else 24.dp)
                        .graphicsLayer { rotationZ = rotation }
                ) {
                    M3CircularLoader(
                        modifier = Modifier.fillMaxSize(),
                        color = buttonTint,
                        trackColor = buttonTint.copy(alpha = 0.24f),
                        strokeWidth = 2f
                    )
                }
            } else {
                Crossfade(
                    targetState = isPlaying,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "playPauseCrossfade"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) RhythmIcons.Pause else RhythmIcons.Play,
                        contentDescription = stringResource(R.string.play_pause),
                        tint = buttonTint,
                        modifier = Modifier.size(if (isExtraSmallWidth) 20.dp else 24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Expressive Toggle Button Group for secondary actions
 * Groups Shuffle, Lyrics, and Repeat buttons with unified styling
 * and morphing pill animations
 */
@Composable
fun ExpressiveToggleButtonGroup(
    shuffleEnabled: Boolean,
    lyricsVisible: Boolean,
    repeatMode: Int,
    onToggleShuffle: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleRepeat: () -> Unit,
    showLyrics: Boolean,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    isCompactHeight: Boolean = false,
    isCompactWidth: Boolean = false,
    onLongClickLyrics: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle toggle button
        ExpressiveMorphingToggleButton(
            isActive = shuffleEnabled,
            onClick = onToggleShuffle,
            icon = RhythmIcons.Shuffle,
            label = stringResource(R.string.action_shuffle),
            isDarkTheme = isDarkTheme,
            isCompactHeight = isCompactHeight,
            isCompactWidth = isCompactWidth
        )
        
        // Lyrics toggle button (only if lyrics are enabled)
        if (showLyrics) {
            ExpressiveMorphingToggleButton(
                isActive = lyricsVisible,
                onClick = onToggleLyrics,
                onLongClick = onLongClickLyrics,
                icon = RhythmIcons.Player.Lyrics,
                label = stringResource(R.string.player_chip_lyrics),
                isDarkTheme = isDarkTheme,
                isCompactHeight = isCompactHeight,
                isCompactWidth = isCompactWidth
            )
        }
        
        // Repeat toggle button
        ExpressiveMorphingToggleButton(
            isActive = repeatMode != 0,
            onClick = onToggleRepeat,
            icon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RhythmIcons.RepeatOne
                Player.REPEAT_MODE_ALL -> RhythmIcons.Repeat
                else -> RhythmIcons.Repeat
            },
            label = stringResource(R.string.player_chip_repeat),
            isDarkTheme = isDarkTheme,
            isCompactHeight = isCompactHeight,
            isCompactWidth = isCompactWidth
        )
    }
}

/**
 * Morphing toggle button that expands when inactive to show label
 */
@Composable
private fun ExpressiveMorphingToggleButton(
    isActive: Boolean,
    onClick: () -> Unit,
    icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon,
    label: String,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    isCompactHeight: Boolean = false,
    isCompactWidth: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isPressedManual by remember { mutableStateOf(false) }
    val effectivePressed = if (onLongClick != null) isPressedManual else isPressed
    
    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            if (isDarkTheme) MaterialTheme.colorScheme.inverseSurface 
            else MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "toggleButtonColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isActive) {
            if (isDarkTheme) MaterialTheme.colorScheme.inverseOnSurface 
            else MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "toggleContentColor"
    )
    
    val isCompact = isCompactHeight || isCompactWidth
    
    val width by animateDpAsState(
        targetValue = if (isActive) (if (isCompact) 40.dp else 48.dp) else (if (isCompact) 84.dp else 100.dp),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "toggleButtonWidth"
    )
    
    val toggleHeight = if (isCompact) 40.dp else 48.dp
    
    val scale by animateFloatAsState(
        targetValue = if (effectivePressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "toggleButtonScale"
    )
    
    Surface(
        modifier = modifier
            .width(width)
            .height(toggleHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (onLongClick != null) {
                    Modifier.pointerInput(onClick, onLongClick) {
                        detectTapGestures(
                            onPress = {
                                isPressedManual = true
                                tryAwaitRelease()
                                isPressedManual = false
                            },
                            onTap = { onClick() },
                            onLongPress = { onLongClick() }
                        )
                    }
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                }
            ),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(if (isCompact) 18.dp else 20.dp),
                tint = contentColor
            )
            
            AnimatedVisibility(
                visible = !isActive,
                enter = fadeIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + expandHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
                exit = fadeOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessHigh
                    )
                ) + shrinkHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessHigh
                    )
                )
            ) {
                Row {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = contentColor
                    )
                }
            }
        }
    }
}

// ============================================================================
// ADDITIONAL EXPRESSIVE ENHANCEMENTS
// ============================================================================

/**
 * Expressive elevated player control group with shadow and depth
 * More prominent visual hierarchy for featured player sections
 */
@Composable
fun ExpressiveElevatedPlayerControlGroup(
    isPlaying: Boolean,
    showSeekButtons: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
    isExtraSmallWidth: Boolean = false,
    isCompactWidth: Boolean = false
) {
    // Animated elevation based on playing state
    val elevation by animateDpAsState(
        targetValue = if (isPlaying) 8.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "controlGroupElevation"
    )
    
    val groupWidth by animateDpAsState(
        targetValue = when {
            !isPlaying && !showSeekButtons -> 220.dp
            !isPlaying && showSeekButtons -> 300.dp
            isPlaying && !showSeekButtons -> 200.dp
            else -> 340.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elevatedControlGroupWidth"
    )
    
    Surface(
        modifier = modifier
            .width(groupWidth)
            .height(when {
                isExtraSmallWidth -> 64.dp
                isCompactWidth -> 72.dp
                else -> 80.dp
            }),
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = elevation,
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onPrevious,
                modifier = Modifier.size(when {
                    isExtraSmallWidth -> 44.dp
                    isCompactWidth -> 48.dp
                    else -> 52.dp
                }),
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = RhythmIcons.SkipPrevious,
                    contentDescription = stringResource(R.string.animatedplaybackcontrols_previous),
                    modifier = Modifier.size(when {
                        isExtraSmallWidth -> 22.dp
                        isCompactWidth -> 24.dp
                        else -> 26.dp
                    })
                )
            }
            
            AnimatedVisibility(
                visible = isPlaying && showSeekButtons,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FilledTonalIconButton(
                    onClick = onSeekBack,
                    modifier = Modifier.size(when {
                        isExtraSmallWidth -> 48.dp
                        isCompactWidth -> 52.dp
                        else -> 60.dp
                    }),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = RhythmIcons.Replay10,
                        contentDescription = stringResource(R.string.expressivecomponents_seek_back),
                        modifier = Modifier.size(when {
                            isExtraSmallWidth -> 24.dp
                            isCompactWidth -> 26.dp
                            else -> 30.dp
                        })
                    )
                }
            }
            
            ExpressiveMorphingPlayPauseButton(
                isPlaying = isPlaying,
                onClick = onPlayPause,
                isExtraSmallWidth = isExtraSmallWidth,
                isCompactWidth = isCompactWidth
            )
            
            AnimatedVisibility(
                visible = isPlaying && showSeekButtons,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FilledTonalIconButton(
                    onClick = onSeekForward,
                    modifier = Modifier.size(when {
                        isExtraSmallWidth -> 48.dp
                        isCompactWidth -> 52.dp
                        else -> 60.dp
                    }),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = RhythmIcons.Forward10,
                        contentDescription = stringResource(R.string.expressivecomponents_seek_forward),
                        modifier = Modifier.size(when {
                            isExtraSmallWidth -> 24.dp
                            isCompactWidth -> 26.dp
                            else -> 30.dp
                        })
                    )
                }
            }
            
            FilledTonalIconButton(
                onClick = onNext,
                modifier = Modifier.size(when {
                    isExtraSmallWidth -> 44.dp
                    isCompactWidth -> 48.dp
                    else -> 52.dp
                }),
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = RhythmIcons.SkipNext,
                    contentDescription = stringResource(R.string.onboarding_next),
                    modifier = Modifier.size(when {
                        isExtraSmallWidth -> 22.dp
                        isCompactWidth -> 24.dp
                        else -> 26.dp
                    })
                )
            }
        }
    }
}

/**
 * Expressive compact player controls for mini player or compact layouts
 * Minimalist design with essential controls only
 */
@Composable
fun ExpressiveCompactPlayerControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveFilledTonalIconButton(
            onClick = onPrevious,
            shape = CircleShape
        ) {
            Icon(
                imageVector = RhythmIcons.SkipPrevious,
                contentDescription = stringResource(R.string.animatedplaybackcontrols_previous),
                modifier = Modifier.size(20.dp)
            )
        }
        
        ExpressiveFilledIconButton(
            onClick = onPlayPause,
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (isPlaying) RhythmIcons.Pause else RhythmIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(24.dp)
            )
        }
        
        ExpressiveFilledTonalIconButton(
            onClick = onNext,
            shape = CircleShape
        ) {
            Icon(
                imageVector = RhythmIcons.SkipNext,
                contentDescription = stringResource(R.string.onboarding_next),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Expressive progress indicator with animated wave
 * Shows visual feedback during buffering or loading
 */
@Composable
fun ExpressiveLoadingPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "loadingPulse"
    )
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            M3CircularLoader(
                modifier = Modifier.size(size * 0.7f),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.24f),
                strokeWidth = 3f
            )
        }
    }
}

// ============================================================================
// ADDITIONAL EXPRESSIVE COMPONENTS
// ============================================================================

/**
 * Shimmer Box
 * Animated shimmer effect for loading states using MaterialTheme colors
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest
    
    val shimmerColors = listOf(
        baseColor,
        highlightColor,
        baseColor,
    )

    val transition = rememberInfiniteTransition(label = "expressiveShimmer")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, delayMillis = 300),
        ),
        label = "expressiveShimmerOffset"
    )

    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = shimmerColors,
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset(x = offset, y = offset)
    )

    Box(modifier = modifier.background(brush = brush, shape = RoundedCornerShape(8.dp)))
}

/**
 * Expressive Toggle Segment Button
 * Animated toggle button with text
 */
@Composable
fun ExpressiveToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    text: String
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (active) activeColor else inactiveColor,
        animationSpec = tween(200),
        label = "toggleBgColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (active) activeContentColor else inactiveContentColor,
        animationSpec = tween(200),
        label = "toggleContentColor"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(activeCornerRadius))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Auto-Scrolling Text
 * Displays text with animated marquee effect when it overflows
 * Gracefully shows static text when content fits
 */
@Composable
fun AutoScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    gradientEdgeColor: Color = MaterialTheme.colorScheme.surface,
    gradientWidth: Dp = 24.dp,
    initialDelayMillis: Int = 1500
) {
    var overflow by remember { mutableStateOf(false) }
    var showScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (overflow) {
            delay(initialDelayMillis.toLong())
            showScrolling = true
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (!showScrolling || !overflow) {
            Text(
                text = text,
                style = style,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { res: androidx.compose.ui.text.TextLayoutResult -> 
                    overflow = res.hasVisualOverflow 
                }
            )
        } else {
            // Animated scrolling version
            Text(
                text = text,
                style = style,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        clip = true
                    }
            )
        }
    }
}

/**
 * Progress Bar with Label
 * Professional linear progress indicator with percentage display
 */
@Composable
fun ExpressiveProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true,
    label: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3CircularLoader(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier.size(24.dp),
                color = color,
                trackColor = trackColor,
                strokeWidth = 2f
            )

            if (showPercentage) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * Compact Loading State Indicator
 * Shows loading spinner with optional text message
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null,
    size: Dp = 32.dp,
    strokeWidth: Dp = 2.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        M3CircularLoader(
            modifier = Modifier.size(size),
            color = color,
            trackColor = color.copy(alpha = 0.24f),
            strokeWidth = strokeWidth.value
        )

        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Enhanced Divider with Labels
 * Stylish section divider with optional text label
 */
@Composable
fun ExpressiveSectionDivider(
    modifier: Modifier = Modifier,
    label: String? = null,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    thickness: Dp = 1.dp
) {
    if (label == null) {
        androidx.compose.material3.HorizontalDivider(
            modifier = modifier,
            color = color,
            thickness = thickness
        )
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = color,
                thickness = thickness
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = color,
                thickness = thickness
            )
        }
    }
}

/**
 * Status Badge Component
 * Displays status with icon and color coding
 */
@Composable
fun ExpressiveStatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    icon: ImageVector? = null,
    size: Dp = 24.dp
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color,
        contentColor = textColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Animated Pulsing Container
 * Element that pulses to draw attention
 */
@Composable
fun ExpressivePulsingContainer(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = shape,
        color = color
    ) {
        content()
    }
}

/**
 * Collapsible Card Section
 * Card that expands/collapses with smooth animation
 */
@Composable
fun ExpressiveCollapsibleCard(
    modifier: Modifier = Modifier,
    title: String,
    initialExpanded: Boolean = true,
    shape: Shape = ExpressiveShapes.Large,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    
    val rotationDegrees by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "arrowRotation"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = RhythmIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            rotationZ = rotationDegrees
                        }
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(modifier = Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    }
}

/**
 * Gradient Text Overlay Effect
 * Text with gradient fade at edges for overflow effect
 */
@Composable
fun ExpressiveGradientText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = 1,
    gradientColors: List<Color> = listOf(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.surface
    )
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        softWrap = false
    )
}

/**
 * Empty State Card
 * Displays empty state with icon, title, and optional action button
 */
@Composable
fun ExpressiveEmptyStateCard(
    modifier: Modifier = Modifier,
    icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon,
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(8.dp))
            ExpressiveFilledButton(
                onClick = onAction
            ) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Animated Counter
 * Counts up with smooth animation - useful for stats/labels
 */
@Composable
fun ExpressiveAnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    suffix: String = ""
) {
    val animatedValue by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = 600),
        label = "counterAnimation"
    )

    Text(
        text = "${animatedValue.toInt()}$suffix",
        modifier = modifier,
        style = style,
        fontWeight = FontWeight.Bold
    )
}

/**
 * Tooltip-like Card
 * Helpful text overlay positioned near UI elements
 */
@Composable
fun ExpressiveInfoCard(
    modifier: Modifier = Modifier,
    text: String,
    backgroundColor: Color = MaterialTheme.colorScheme.inverseSurface,
    textColor: Color = MaterialTheme.colorScheme.inverseOnSurface,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = textColor
                )
            }

            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = textColor
            )
        }
    }
}

/**
 * Animated Button with Loading State
 * Button that shows loading spinner and disables interactions during async operations
 */
@Composable
fun ExpressiveAsyncButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    label: String = "Button"
) {
    ExpressiveFilledButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            M3CircularLoader(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2f,
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(label)
    }
}
