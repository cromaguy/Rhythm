package chromahub.rhythm.app.shared.presentation.components.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RhythmTapTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    text: String? = null,
    content: @Composable RowScope.() -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var isTapped by remember { mutableStateOf(false) }
    val visualActive = isPressed || isTapped

    val animScale by animateFloatAsState(
        targetValue = if (visualActive && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tapTonalScale"
    )

    val animElevation by animateDpAsState(
        targetValue = if (visualActive && enabled) 1.dp else 3.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "tapTonalElev"
    )

    Surface(
        onClick = {
            if (enabled) {
                scope.launch {
                    isTapped = true
                    delay(120)
                    isTapped = false
                }
                onClick()
            }
        },
        modifier = modifier.graphicsLayer { scaleX = animScale; scaleY = animScale },
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = colors.containerColor,
        contentColor = colors.contentColor,
        tonalElevation = animElevation,
        shadowElevation = animElevation,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(8.dp))
            }
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            content()
        }
    }
}

@Composable
fun RhythmControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 56.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isTapped by remember { mutableStateOf(false) }
    val visualActive = isPressed || isTapped
    val animScale by animateFloatAsState(
        targetValue = if (visualActive && enabled) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ctrlBtnScale"
    )

    val animCorner by animateDpAsState(
        targetValue = if (visualActive && enabled) 16.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "ctrlBtnCorner"
    )

    val pressedBg = lerp(containerColor, Color.Black, 0.18f)
    val animBg by animateColorAsState(
        targetValue = if (visualActive && enabled) pressedBg else containerColor,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "ctrlBtnBg"
    )

    val resolvedShape = if (shape is RoundedCornerShape) {
        RoundedCornerShape(if (visualActive && enabled) (size / 2 - animCorner) else size / 2)
    } else {
        shape
    }

    Surface(
        onClick = {
            if (enabled) {
                scope.launch {
                    isTapped = true
                    delay(120)
                    isTapped = false
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        },
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
            },
        shape = resolvedShape,
        color = animBg,
        contentColor = contentColor,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun RhythmPlayButton(
    isPlaying: Boolean,
    showBuffering: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isTapped by remember { mutableStateOf(false) }
    val visualActive = isPressed || isTapped

    val animCorner by animateDpAsState(
        targetValue = if (visualActive) 18.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "playBtnCorner"
    )

    Surface(
        onClick = {
            scope.launch {
                isTapped = true
                delay(120)
                isTapped = false
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier.height(size),
        shape = RoundedCornerShape(size / 2 - animCorner),
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (showBuffering) {
                PlaybackBufferingLoader(Modifier.size(40.dp), contentColor)
            } else {
                Text(
                    text = if (isPlaying) "Pause" else "Play",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun RhythmCompactButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var isTapped by remember { mutableStateOf(false) }
    val visualActive = isPressed || isTapped
    val pressedBg = lerp(containerColor, Color.Black, 0.18f)

    val animScale by animateFloatAsState(
        targetValue = if (visualActive && enabled) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "compactBtnScale"
    )

    val animBg by animateColorAsState(
        targetValue = if (visualActive && enabled) pressedBg else containerColor,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "compactBtnBg"
    )

    val animCorner by animateDpAsState(
        targetValue = if (visualActive && enabled) 10.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "compactBtnCorner"
    )

    Surface(
        onClick = {
            if (enabled) {
                scope.launch {
                    isTapped = true
                    delay(120)
                    isTapped = false
                }
                onClick()
            }
        },
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
            },
        shape = RoundedCornerShape(size / 2 - animCorner),
        color = animBg,
        contentColor = contentColor,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun RhythmPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonHeight: Dp = 44.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var isTapped by remember { mutableStateOf(false) }
    val visualActive = isPressed || isTapped
    val pressedBg = lerp(containerColor, Color.Black, 0.18f)

    val animScale by animateFloatAsState(
        targetValue = if (visualActive && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pillBtnScale"
    )

    val animBg by animateColorAsState(
        targetValue = if (visualActive && enabled) pressedBg else containerColor,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillBtnBg"
    )

    Surface(
        onClick = {
            if (enabled) {
                scope.launch {
                    isTapped = true
                    delay(120)
                    isTapped = false
                }
                onClick()
            }
        },
        modifier = modifier
            .height(buttonHeight)
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
            },
        shape = RoundedCornerShape(buttonHeight / 2),
        color = animBg,
        contentColor = contentColor,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
