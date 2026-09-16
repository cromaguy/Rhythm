/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.components.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils

@Composable
fun SaveCustomPresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    bandLevels: List<Float>,
    existingPresetNames: List<String> = emptyList(),
    initialName: String = ""
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var presetName by remember { mutableStateOf(initialName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun validateAndSave() {
        val trimmed = presetName.trim()
        when {
            trimmed.isEmpty() -> {
                errorMessage = context.getString(R.string.eq_preset_name_empty)
            }
            existingPresetNames.any { it.equals(trimmed, ignoreCase = true) && !it.equals(initialName, ignoreCase = true) } -> {
                errorMessage = context.getString(R.string.eq_preset_name_exists)
            }
            else -> {
                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                onSave(trimmed)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = RhythmIcons.Equalizer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.eq_save_custom_preset),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.eq_save_custom_preset_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Mini visual EQ preview curve
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val secondaryColor = MaterialTheme.colorScheme.secondary
                    val tertiaryColor = MaterialTheme.colorScheme.tertiary
                    val outlineColor = MaterialTheme.colorScheme.outline

                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        val width = size.width
                        val height = size.height
                        val count = bandLevels.size.coerceAtLeast(1)
                        val bandWidth = width / count

                        // Center guideline
                        drawLine(
                            color = outlineColor.copy(alpha = 0.2f),
                            start = Offset(0f, height / 2),
                            end = Offset(width, height / 2),
                            strokeWidth = 1.dp.toPx()
                        )

                        val points = bandLevels.mapIndexed { index, level ->
                            val x = (index + 0.5f) * bandWidth
                            val normalizedLevel = ((level + 15f) / 30f).coerceIn(0f, 1f)
                            val y = height * (1f - normalizedLevel)
                            Offset(x, y)
                        }

                        if (points.size > 1) {
                            val curvePath = Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (i in 1 until points.size) {
                                    val p0 = points[i - 1]
                                    val p1 = points[i]
                                    val controlX = (p0.x + p1.x) / 2
                                    quadraticTo(controlX, p0.y, p1.x, p1.y)
                                }
                            }

                            drawPath(
                                path = curvePath,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(secondaryColor, primaryColor, tertiaryColor)
                                ),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        points.forEach { point ->
                            drawCircle(
                                color = primaryColor,
                                radius = 3.5.dp.toPx(),
                                center = point
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = presetName,
                    onValueChange = {
                        presetName = it
                        if (errorMessage != null) errorMessage = null
                    },
                    label = { Text(stringResource(R.string.eq_preset_name_hint)) },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg ->
                        { Text(text = msg, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { validateAndSave() },
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    imageVector = RhythmIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.bottomsheet_save))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    imageVector = RhythmIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ui_cancel))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}
