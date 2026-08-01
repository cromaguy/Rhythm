package chromahub.rhythm.app.shared.presentation.components.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Arrow position relative to the tooltip body.
 */
enum class RhythmTooltipArrowPosition {
    TopStart, TopCenter, TopEnd,
    BottomStart, BottomCenter, BottomEnd,
    StartCenter, EndCenter
}

/**
 * A custom-styled tooltip with an arrow pointer that can be positioned
 * on any side of the tooltip body.
 *
 * @param text The tooltip text content.
 * @param arrowPosition Where the arrow should point from.
 * @param containerColor Background colour of the tooltip.
 * @param contentColor Text colour inside the tooltip.
 */
@Composable
fun RhythmTooltip(
    text: String,
    modifier: Modifier = Modifier,
    arrowPosition: RhythmTooltipArrowPosition = RhythmTooltipArrowPosition.BottomCenter,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val arrowWidth = 16.dp
    val arrowHeight = 8.dp

    Row(
        modifier = modifier.wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (arrowPosition == RhythmTooltipArrowPosition.StartCenter) {
            TooltipArrowSide(
                color = containerColor,
                width = arrowHeight,
                height = arrowWidth,
                direction = TooltipArrowDirection.Left
            )
        }

        Column(
            horizontalAlignment = when (arrowPosition) {
                RhythmTooltipArrowPosition.TopStart,
                RhythmTooltipArrowPosition.BottomStart -> Alignment.Start
                RhythmTooltipArrowPosition.TopCenter,
                RhythmTooltipArrowPosition.BottomCenter -> Alignment.CenterHorizontally
                RhythmTooltipArrowPosition.TopEnd,
                RhythmTooltipArrowPosition.BottomEnd -> Alignment.End
                else -> Alignment.CenterHorizontally
            }
        ) {
            if (arrowPosition == RhythmTooltipArrowPosition.TopStart ||
                arrowPosition == RhythmTooltipArrowPosition.TopCenter ||
                arrowPosition == RhythmTooltipArrowPosition.TopEnd
            ) {
                TooltipArrow(
                    color = containerColor,
                    width = arrowWidth,
                    height = arrowHeight,
                    isUpward = true,
                    modifier = Modifier.padding(
                        start = if (arrowPosition == RhythmTooltipArrowPosition.TopStart) 16.dp else 0.dp,
                        end = if (arrowPosition == RhythmTooltipArrowPosition.TopEnd) 16.dp else 0.dp
                    )
                )
            }

            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 240.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (arrowPosition == RhythmTooltipArrowPosition.BottomStart ||
                arrowPosition == RhythmTooltipArrowPosition.BottomCenter ||
                arrowPosition == RhythmTooltipArrowPosition.BottomEnd
            ) {
                TooltipArrow(
                    color = containerColor,
                    width = arrowWidth,
                    height = arrowHeight,
                    isUpward = false,
                    modifier = Modifier.padding(
                        start = if (arrowPosition == RhythmTooltipArrowPosition.BottomStart) 16.dp else 0.dp,
                        end = if (arrowPosition == RhythmTooltipArrowPosition.BottomEnd) 16.dp else 0.dp
                    )
                )
            }
        }

        if (arrowPosition == RhythmTooltipArrowPosition.EndCenter) {
            TooltipArrowSide(
                color = containerColor,
                width = arrowHeight,
                height = arrowWidth,
                direction = TooltipArrowDirection.Right
            )
        }
    }
}

private enum class TooltipArrowDirection { Left, Right }

@Composable
private fun TooltipArrowSide(
    color: Color,
    width: Dp,
    height: Dp,
    direction: TooltipArrowDirection,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(width, height)
            .offset(x = if (direction == TooltipArrowDirection.Left) 1.dp else (-1).dp)
    ) {
        val path = Path().apply {
            if (direction == TooltipArrowDirection.Left) {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2f)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
            }
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
private fun TooltipArrow(
    color: Color,
    width: Dp,
    height: Dp,
    isUpward: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(width, height)
            .offset(y = if (isUpward) 1.dp else (-1).dp)
    ) {
        val path = Path().apply {
            if (isUpward) {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
            }
            close()
        }
        drawPath(path, color = color)
    }
}

/**
 * A convenient wrapper that uses M3's [TooltipBox] with [RhythmTooltip].
 *
 * @param tooltipText The tooltip text.
 * @param state Tooltip state controlling visibility.
 * @param arrowPosition Where to place the arrow.
 * @param content The anchor composable that triggers the tooltip on hover/long-press.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmTooltipBox(
    tooltipText: String,
    state: TooltipState,
    modifier: Modifier = Modifier,
    arrowPosition: RhythmTooltipArrowPosition = RhythmTooltipArrowPosition.BottomCenter,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = when (arrowPosition) {
                RhythmTooltipArrowPosition.StartCenter -> TooltipAnchorPosition.Right
                RhythmTooltipArrowPosition.EndCenter -> TooltipAnchorPosition.Left
                RhythmTooltipArrowPosition.TopStart,
                RhythmTooltipArrowPosition.TopCenter,
                RhythmTooltipArrowPosition.TopEnd -> TooltipAnchorPosition.Below
                else -> TooltipAnchorPosition.Above
            },
            spacingBetweenTooltipAndAnchor = 4.dp
        ),
        tooltip = {
            RhythmTooltip(
                text = tooltipText,
                arrowPosition = arrowPosition
            )
        },
        state = state,
        modifier = modifier,
        content = content
    )
}
