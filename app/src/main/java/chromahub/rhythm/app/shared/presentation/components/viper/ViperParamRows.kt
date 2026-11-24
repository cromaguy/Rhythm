/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.viper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import java.util.Locale

internal fun formatDb(value: Float): String = String.format(Locale.US, "%+.1f dB", value)

internal fun formatPercent(value: Float): String =
    String.format(Locale.US, "%d%%", (value * 100f).toInt())

internal fun formatHz(value: Int): String = String.format(Locale.US, "%d Hz", value)

internal fun viperContentColorFor(color: Color): Color =
    if (color.luminance() > 0.5f) Color.Black else Color.White

/**
 * Labelled slider used inside a ViPER settings item.
 */
@Composable
fun ViperSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatted: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
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
                color = if (enabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val sliderState = remember(valueRange) {
            SliderState(value = clamped, trackRange = valueRange)
        }
        sliderState.value = clamped
        Slider(
            state = sliderState,
            onValueChange = onValueChange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = activeColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Labelled single-choice row used inside a ViPER settings item.
 *
 * Up to three options share one row; longer lists wrap onto additional rows so labels stay
 * readable on narrow screens.
 */
@Composable
fun ViperSegmentedRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val select: (Int) -> Unit = { index ->
        if (enabled) {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
            onSelectIndex(index)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        val optionRows = if (options.size <= 3) listOf(options) else options.chunked(3)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var chunkStart = 0
            optionRows.forEach { chunk ->
                ExpressiveButtonGroup(
                    items = chunk,
                    selectedIndex = if (selectedIndex in chunkStart until chunkStart + chunk.size) {
                        selectedIndex - chunkStart
                    } else {
                        -1
                    },
                    onItemClick = { index -> select(chunkStart + index) },
                    modifier = Modifier.fillMaxWidth(),
                    buttonHeight = 44.dp,
                    selectedButtonColor = activeColor,
                    unselectedButtonColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedContentColor = viperContentColorFor(activeColor),
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    showCheckIcon = false,
                    enabled = enabled
                )
                chunkStart += chunk.size
            }
        }
    }
}
