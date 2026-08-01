package chromahub.rhythm.app.shared.presentation.components.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RhythmButtonSize {
    Small, Medium, Large, ExtraLarge
}

enum class RhythmButtonType {
    Filled, Tonal, Elevated, Outlined, Text
}

data class RhythmToggleOption(
    val text: String? = null,
    val icon: MaterialSymbolIcon? = null,
    val weight: Float = 1f,
    val type: RhythmButtonType? = null,
    val selectedType: RhythmButtonType? = null,
    val isLoading: Boolean = false,
    val loadingProgress: Float? = null,
    val backgroundProgress: Float? = null,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
    val enabled: Boolean = true
)

@Composable
fun RhythmToggleButtonGroup(
    options: List<RhythmToggleOption>,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isMultiSelect: Boolean = false,
    size: RhythmButtonSize = RhythmButtonSize.Medium,
    isInsideContainer: Boolean = false,
    isShowingCheck: Boolean = true,
    isFillMaxWidth: Boolean = true
) {
    val rowHeight = when (size) {
        RhythmButtonSize.Small -> 32.dp
        RhythmButtonSize.Medium -> 40.dp
        RhythmButtonSize.Large -> 48.dp
        RhythmButtonSize.ExtraLarge -> 56.dp
    }

    Row(
        modifier = modifier
            .height(rowHeight)
            .then(if (isFillMaxWidth) Modifier.fillMaxWidth() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = selectedIndices.contains(index)

            val animWeight by animateFloatAsState(
                targetValue = when {
                    isSelected -> option.weight * 1.2f
                    else -> option.weight
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "toggleWeight_$index"
            )

            val halfR = rowHeight / 2
            val smallR = rowHeight * 0.2f
            val startR by animateDpAsState(
                targetValue = if (isSelected || index == 0) halfR else smallR,
                label = "toggleStartR_$index"
            )
            val endR by animateDpAsState(
                targetValue = if (isSelected || index == options.size - 1) halfR else smallR,
                label = "toggleEndR_$index"
            )

            val effectiveType = if (isSelected) {
                option.selectedType ?: RhythmButtonType.Filled
            } else {
                option.type ?: RhythmButtonType.Tonal
            }

            val unselectedBg = if (isInsideContainer) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
            val unselectedContent = if (isInsideContainer) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            RhythmToggleButton(
                onClick = { onToggle(index) },
                modifier = Modifier.weight(animWeight),
                type = effectiveType,
                size = size,
                text = option.text,
                icon = if (isSelected && isMultiSelect && isShowingCheck && option.icon == null)
                    RhythmIcons.Check else option.icon,
                isLoading = option.isLoading,
                loadingProgress = option.loadingProgress,
                backgroundProgress = option.backgroundProgress,
                containerColor = option.containerColor ?: if (isSelected) null else unselectedBg,
                contentColor = option.contentColor ?: if (isSelected) null else unselectedContent,
                enabled = option.enabled,
                selected = isSelected,
                shape = RoundedCornerShape(
                    topStart = startR, bottomStart = startR,
                    topEnd = endR, bottomEnd = endR
                )
            )
        }
    }
}

@Composable
private fun RhythmToggleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: RhythmButtonType = RhythmButtonType.Tonal,
    size: RhythmButtonSize = RhythmButtonSize.Medium,
    text: String? = null,
    icon: MaterialSymbolIcon? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingProgress: Float? = null,
    backgroundProgress: Float? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(50)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()

    var isTapped by remember { mutableStateOf(false) }
    val visualActive = isPressed || isTapped

    val height = when (size) {
        RhythmButtonSize.Small -> 32.dp
        RhythmButtonSize.Medium -> 40.dp
        RhythmButtonSize.Large -> 48.dp
        RhythmButtonSize.ExtraLarge -> 56.dp
    }
    val iconSize = when (size) {
        RhythmButtonSize.Small -> 14.dp
        RhythmButtonSize.Medium -> 16.dp
        RhythmButtonSize.Large -> 18.dp
        RhythmButtonSize.ExtraLarge -> 20.dp
    }
    val textStyle = when (size) {
        RhythmButtonSize.Small -> MaterialTheme.typography.labelSmall
        RhythmButtonSize.Medium -> MaterialTheme.typography.labelMedium
        RhythmButtonSize.Large -> MaterialTheme.typography.labelLarge
        RhythmButtonSize.ExtraLarge -> MaterialTheme.typography.titleMedium
    }
    val horizontalPad = when (size) {
        RhythmButtonSize.Small -> 8.dp
        RhythmButtonSize.Medium -> 12.dp
        RhythmButtonSize.Large -> 16.dp
        RhythmButtonSize.ExtraLarge -> 20.dp
    }

    val targetBg = containerColor ?: when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
        type == RhythmButtonType.Filled -> MaterialTheme.colorScheme.primary
        type == RhythmButtonType.Tonal -> MaterialTheme.colorScheme.secondaryContainer
        type == RhythmButtonType.Elevated -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> Color.Transparent
    }
    val animatedBg by animateColorAsState(targetValue = targetBg, label = "toggleBg")

    val targetContent = contentColor ?: when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        type == RhythmButtonType.Filled -> MaterialTheme.colorScheme.onPrimary
        type == RhythmButtonType.Tonal -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.primary
    }
    val animatedContent by animateColorAsState(targetValue = targetContent, label = "toggleContent")

    val animScale by animateFloatAsState(
        targetValue = if (visualActive && enabled) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "toggleScale"
    )

    Surface(
        onClick = {
            scope.launch {
                isTapped = true
                delay(100)
                isTapped = false
            }
            onClick()
        },
        modifier = modifier.height(height),
        enabled = enabled && !isLoading,
        shape = shape,
        color = animatedBg,
        contentColor = animatedContent,
        tonalElevation = if (type == RhythmButtonType.Elevated) 2.dp else 0.dp,
        border = if (type == RhythmButtonType.Outlined)
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = horizontalPad)
                .graphicsLayer {
                    scaleX = animScale
                    scaleY = animScale
                },
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    modifier = Modifier.size(iconSize)
                )
                if (text != null) Spacer(Modifier.width(4.dp))
            }
            if (text != null) {
                Text(
                    text = text,
                    style = textStyle,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RhythmGroupedButton(
    modifier: Modifier = Modifier,
    size: RhythmButtonSize = RhythmButtonSize.Large,
    isFillMaxWidth: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val spacing = when (size) {
        RhythmButtonSize.Small -> 2.dp
        else -> 4.dp
    }
    Row(
        modifier = modifier
            .then(if (isFillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .animateContentSize(
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun RowScope.RhythmButtonWeighted(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    type: RhythmButtonType = RhythmButtonType.Filled,
    size: RhythmButtonSize = RhythmButtonSize.Large,
    text: String? = null,
    icon: MaterialSymbolIcon? = null,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    selected: Boolean = false,
    isFirst: Boolean = true,
    isLast: Boolean = true,
    squareInnerCorners: Boolean = false,
    height: Dp? = null,
    content: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var isTapped by remember { mutableStateOf(false) }
    val visualActive = isPressed || isTapped

    val animWeight by animateFloatAsState(
        targetValue = when {
            visualActive -> weight * 1.25f
            selected -> weight * 1.1f
            else -> weight
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "wButtonWeight"
    )

    val resolvedHeight = height ?: when (size) {
        RhythmButtonSize.Small -> 32.dp
        RhythmButtonSize.Medium -> 40.dp
        RhythmButtonSize.Large -> 48.dp
        RhythmButtonSize.ExtraLarge -> 64.dp
    }
    val halfR = resolvedHeight / 2
    val innerR = resolvedHeight * 0.25f

    val animCorner by animateDpAsState(
        targetValue = if (visualActive) resolvedHeight * 0.3f else halfR,
        label = "wButtonCorner"
    )

    val innerCorner = if (squareInnerCorners) 0.dp else innerR
    val shape = RoundedCornerShape(
        topStart = if (isFirst) animCorner else innerCorner,
        bottomStart = if (isFirst) animCorner else innerCorner,
        topEnd = if (isLast) animCorner else innerCorner,
        bottomEnd = if (isLast) animCorner else innerCorner
    )

    val bg = containerColor ?: when (type) {
        RhythmButtonType.Filled -> MaterialTheme.colorScheme.primary
        RhythmButtonType.Tonal -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val resolvedContentColor = contentColor ?: when (type) {
        RhythmButtonType.Filled -> MaterialTheme.colorScheme.onPrimary
        RhythmButtonType.Tonal -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = {
            scope.launch {
                isTapped = true
                delay(100)
                isTapped = false
            }
            onClick()
        },
        modifier = modifier
            .weight(animWeight)
            .height(resolvedHeight),
        enabled = enabled,
        shape = shape,
        color = bg,
        contentColor = resolvedContentColor,
        interactionSource = interactionSource
    ) {
        if (content != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                content()
            }
        } else {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(
                                when (size) {
                                    RhythmButtonSize.Small -> 14.dp
                                    RhythmButtonSize.Medium -> 16.dp
                                    RhythmButtonSize.Large -> 18.dp
                                    RhythmButtonSize.ExtraLarge -> 20.dp
                                }
                            )
                        )
                        if (text != null) Spacer(Modifier.width(6.dp))
                    }
                    if (text != null) {
                        Text(
                            text = text,
                            style = when (size) {
                                RhythmButtonSize.Small -> MaterialTheme.typography.labelSmall
                                RhythmButtonSize.Medium -> MaterialTheme.typography.labelMedium
                                RhythmButtonSize.Large -> MaterialTheme.typography.labelLarge
                                RhythmButtonSize.ExtraLarge -> MaterialTheme.typography.titleMedium
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.RhythmDetailActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    type: RhythmButtonType = RhythmButtonType.Filled,
    height: Dp = 52.dp,
    text: String? = null,
    icon: MaterialSymbolIcon? = null,
    enabled: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    iconSize: Dp = 20.dp,
    fontWeight: FontWeight = FontWeight.Bold,
    contentDescription: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    containerColor: Color? = null,
    contentColor: Color? = null,
    isLoading: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    gradientEdgeColor: Color? = null,
    respectMarqueeGlobalSetting: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var isTapped by remember { mutableStateOf(false) }
    val visualActive = isPressed || isTapped

    val animWeight by animateFloatAsState(
        targetValue = if (visualActive) weight * 1.25f else weight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "detailActionWeight"
    )

    val halfR = height / 2
    val pressedR = height * 0.3f
    val animCorner by animateDpAsState(
        targetValue = if (visualActive) pressedR else halfR,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "detailActionCorner"
    )

    val innerCorner = pressedR
    val shape = RoundedCornerShape(
        topStart = if (isFirst) animCorner else innerCorner,
        bottomStart = if (isFirst) animCorner else innerCorner,
        topEnd = if (isLast) animCorner else innerCorner,
        bottomEnd = if (isLast) animCorner else innerCorner
    )

    val resolvedContainer = containerColor ?: if (type == RhythmButtonType.Filled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val resolvedContent = contentColor ?: if (type == RhythmButtonType.Filled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val colors = if (type == RhythmButtonType.Filled) {
        ButtonDefaults.buttonColors(
            containerColor = resolvedContainer,
            contentColor = resolvedContent
        )
    } else {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = resolvedContainer,
            contentColor = resolvedContent
        )
    }

    Button(
        onClick = {
            scope.launch {
                isTapped = true
                delay(100)
                isTapped = false
            }
            onClick()
        },
        modifier = modifier
            .weight(animWeight)
            .height(height),
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        enabled = enabled,
        interactionSource = interactionSource
    ) {
        if (isLoading) {
            ActionProgressLoader(
                size = iconSize,
                color = resolvedContent
            )
            if (text != null) Spacer(modifier = Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
            if (text != null) Spacer(modifier = Modifier.width(8.dp))
        }
        if (text != null) {
            if (gradientEdgeColor != null) {
                AutoScrollingTextOnDemand(
                    text = text,
                    style = textStyle.copy(fontWeight = fontWeight),
                    gradientEdgeColor = gradientEdgeColor,
                    textAlign = TextAlign.Start,
                    respectGlobalSetting = respectMarqueeGlobalSetting
                )
            } else {
                Text(
                    text = text,
                    style = textStyle,
                    fontWeight = fontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RhythmDetailActionButtonFullWidth(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: RhythmButtonType = RhythmButtonType.Filled,
    height: Dp = 48.dp,
    text: String? = null,
    icon: MaterialSymbolIcon? = null,
    enabled: Boolean = true,
    iconSize: Dp = 20.dp,
    fontWeight: FontWeight = FontWeight.Bold,
    contentDescription: String? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = 12.dp),
    containerColor: Color? = null,
    contentColor: Color? = null,
    isLoading: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    gradientEdgeColor: Color? = null,
    respectMarqueeGlobalSetting: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    var isTapped by remember { mutableStateOf(false) }
    val visualActive = isPressed || isTapped

    val halfR = height / 2
    val animCorner by animateDpAsState(
        targetValue = if (visualActive) height * 0.3f else halfR,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "detailActionCorner"
    )

    val shape = RoundedCornerShape(animCorner)
    val resolvedContainer = containerColor ?: if (type == RhythmButtonType.Filled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val resolvedContent = contentColor ?: if (type == RhythmButtonType.Filled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val colors = if (type == RhythmButtonType.Filled) {
        ButtonDefaults.buttonColors(
            containerColor = resolvedContainer,
            contentColor = resolvedContent
        )
    } else {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = resolvedContainer,
            contentColor = resolvedContent
        )
    }

    Button(
        onClick = {
            scope.launch {
                isTapped = true
                delay(100)
                isTapped = false
            }
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        enabled = enabled,
        interactionSource = interactionSource
    ) {
        if (isLoading) {
            ActionProgressLoader(
                size = iconSize,
                color = resolvedContent
            )
            if (text != null) Spacer(modifier = Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
            if (text != null) Spacer(modifier = Modifier.width(8.dp))
        }
        if (text != null) {
            if (gradientEdgeColor != null) {
                AutoScrollingTextOnDemand(
                    text = text,
                    style = textStyle.copy(fontWeight = fontWeight),
                    gradientEdgeColor = gradientEdgeColor,
                    textAlign = TextAlign.Start,
                    respectGlobalSetting = respectMarqueeGlobalSetting
                )
            } else {
                Text(
                    text = text,
                    style = textStyle,
                    fontWeight = fontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
