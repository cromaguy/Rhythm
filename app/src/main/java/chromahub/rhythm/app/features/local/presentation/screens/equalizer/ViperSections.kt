/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.infrastructure.audio.viper.*
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import java.util.Locale

@Composable
fun ViperSectionCard(
    title: String,
    subtitle: String,
    icon: Any,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    activeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val containerColor by animateColorAsState(
        targetValue = if (enabled) activeContainerColor else MaterialTheme.colorScheme.surfaceContainer,
        label = "containerColor"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        isExpanded = !isExpanded
                    }
            ) {
                when (icon) {
                    is MaterialSymbolIcon -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    is androidx.compose.ui.graphics.vector.ImageVector -> androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = { isChecked ->
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        onEnabledChange(isChecked)
                        if (isChecked && !isExpanded) isExpanded = true
                    },
                    modifier = Modifier.scale(0.85f)
                )

                val arrowRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "arrowRotation"
                )
                Icon(
                    imageVector = MaterialSymbolIcon("expand_more", filled = true),
                    contentDescription = "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(arrowRotation)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun ViperSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatted: String,
    onValueChange: (Float) -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueFormatted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = activeColor
            )
        }
        Slider(
            value = value,
            onValueChange = { newVal ->
                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                onValueChange(newVal)
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = activeColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ViperSegmentedRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = selectedIndex == index
                OutlinedButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onSelectIndex(index)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) activeColor.copy(alpha = 0.2f) else Color.Transparent,
                        contentColor = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isSelected) activeColor else MaterialTheme.colorScheme.outlineVariant
                        )
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
