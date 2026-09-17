/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.common.*
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.SettingsPalettes
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun PlaybackSpeedAndPitchBottomSheet(
    currentSpeed: Float,
    currentPitch: Float,
    syncEnabled: Boolean = false,
    onSyncChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (speed: Float, pitch: Float) -> Unit,
    onSetDefaultSpeed: ((Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    val minVal = 0.25f
    val maxVal = 3.0f

    var selectedSpeed by remember { mutableFloatStateOf(currentSpeed.coerceIn(minVal, maxVal)) }
    var selectedPitch by remember { mutableFloatStateOf(currentPitch.coerceIn(minVal, maxVal)) }

    fun formatClean(value: Float): String {
        val formatted = String.format(Locale.US, "%.3f", value)
        return formatted.dropLastWhile { it == '0' }.dropLastWhile { it == '.' }
    }

    fun formatValueWithX(value: Float): String = "${formatClean(value)}x"

    fun adjustSpeed(delta: Float) {
        val newSpeed = (selectedSpeed + delta).coerceIn(minVal, maxVal)
        val rounded = (Math.round(newSpeed * 1000.0) / 1000.0).toFloat()
        selectedSpeed = rounded
        if (syncEnabled) selectedPitch = rounded
    }

    fun adjustPitch(delta: Float) {
        val newPitch = (selectedPitch + delta).coerceIn(minVal, maxVal)
        val rounded = (Math.round(newPitch * 1000.0) / 1000.0).toFloat()
        selectedPitch = rounded
        if (syncEnabled) selectedSpeed = rounded
    }

    fun dismissAndSave(speed: Float, pitch: Float) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onSave(speed, pitch)
            onDismiss()
        }
    }

    val speedPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
    val pitchPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)

    val isSpeedModified = Math.abs(selectedSpeed - 1.0f) > 0.0001f
    val isPitchModified = Math.abs(selectedPitch - 1.0f) > 0.0001f

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.WIDE_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        val scrollState = rememberScrollState()

        Column(modifier = Modifier.fillMaxWidth()) {
            StandardBottomSheetHeader(
                title = stringResource(R.string.player_speed_and_pitch),
                visible = true
            )

            AdaptiveSheetScrollContainer(
                scrollState = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) { endPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(start = 24.dp, end = 24.dp + endPadding, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val syncDefaultItems = buildList {
                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("sync_alt", filled = true),
                                palette = SettingsPalettes.SkyBlue,
                                title = {
                                    Text(
                                        text = stringResource(R.string.player_sync_speed_pitch),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                description = {
                                    Text(
                                        text = stringResource(R.string.mirror_changes_across_both),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    TunerAnimatedSwitch(
                                        checked = syncEnabled,
                                        onCheckedChange = { enabled ->
                                            onSyncChange(enabled)
                                            if (enabled) selectedPitch = selectedSpeed
                                        }
                                    )
                                },
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    val newSync = !syncEnabled
                                    onSyncChange(newSync)
                                    if (newSync) selectedPitch = selectedSpeed
                                }
                            )
                        )

                        if (onSetDefaultSpeed != null) {
                            add(
                                Material3SettingsItem(
                                    icon = MaterialSymbolIcon("star", filled = true),
                                    palette = SettingsPalettes.Amber,
                                    title = {
                                        Text(
                                            text = stringResource(R.string.set_as_default_speed),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    description = {
                                        Text(
                                            text = stringResource(R.string.current_speed_value, formatValueWithX(selectedSpeed)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        onSetDefaultSpeed.invoke(selectedSpeed)
                                    }
                                )
                            )
                        }
                    }

                    Material3SettingsGroup(
                        items = syncDefaultItems,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeadingRow(
                            label = context.getString(R.string.player_speed_label),
                            value = formatValueWithX(selectedSpeed),
                            isModified = isSpeedModified,
                            tint = MaterialTheme.colorScheme.primary,
                            onReset = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                selectedSpeed = 1.0f
                                if (syncEnabled) selectedPitch = 1.0f
                            }
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExpressiveFilledTonalIconButton(
                                    onClick = {
                                        adjustSpeed(-0.05f)
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("remove", filled = true),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                val speedSliderState = remember(minVal, maxVal) {
                                    SliderState(
                                        value = selectedSpeed,
                                        trackRange = minVal..maxVal
                                    )
                                }
                                speedSliderState.value = selectedSpeed
                                Slider(
                                    state = speedSliderState,
                                    onValueChange = { v ->
                                        val r = (Math.round(v * 1000.0) / 1000.0).toFloat()
                                        selectedSpeed = r
                                        if (syncEnabled) selectedPitch = r
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    onValueChangeFinished = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    }
                                )
                                ExpressiveFilledTonalIconButton(
                                    onClick = {
                                        adjustSpeed(+0.05f)
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("add", filled = true),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            RhythmGroupedButton(
                                modifier = Modifier.fillMaxWidth(),
                                size = RhythmButtonSize.Small
                            ) {
                                listOf(-0.01f, -0.001f, +0.001f, +0.01f).forEachIndexed { idx, step ->
                                    val label = if (step > 0) "+${formatClean(step)}" else formatClean(step)
                                    RhythmButtonWeighted(
                                        onClick = {
                                            adjustSpeed(step)
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        },
                                        weight = 1f,
                                        isFirst = idx == 0,
                                        isLast = idx == 3,
                                        type = RhythmButtonType.Tonal,
                                        text = label
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                speedPresets.forEach { preset ->
                                    val isSelected = selectedSpeed == preset
                                    ExpressiveFilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedSpeed = preset
                                            if (syncEnabled) selectedPitch = preset
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        },
                                        label = {
                                            Text(
                                                text = formatValueWithX(preset),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeadingRow(
                            label = context.getString(R.string.player_pitch_label),
                            value = formatValueWithX(selectedPitch),
                            isModified = isPitchModified,
                            tint = MaterialTheme.colorScheme.secondary,
                            onReset = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                selectedPitch = 1.0f
                                if (syncEnabled) selectedSpeed = 1.0f
                            }
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExpressiveFilledTonalIconButton(
                                    onClick = {
                                        adjustPitch(-0.05f)
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("remove", filled = true),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                val pitchSliderState = remember(minVal, maxVal) {
                                    SliderState(
                                        value = selectedPitch,
                                        trackRange = minVal..maxVal
                                    )
                                }
                                pitchSliderState.value = selectedPitch
                                Slider(
                                    state = pitchSliderState,
                                    onValueChange = { v ->
                                        val r = (Math.round(v * 1000.0) / 1000.0).toFloat()
                                        selectedPitch = r
                                        if (syncEnabled) selectedSpeed = r
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.secondary,
                                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    onValueChangeFinished = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    }
                                )
                                ExpressiveFilledTonalIconButton(
                                    onClick = {
                                        adjustPitch(+0.05f)
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("add", filled = true),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            RhythmGroupedButton(
                                modifier = Modifier.fillMaxWidth(),
                                size = RhythmButtonSize.Small
                            ) {
                                listOf(-0.01f, -0.001f, +0.001f, +0.01f).forEachIndexed { idx, step ->
                                    val label = if (step > 0) "+${formatClean(step)}" else formatClean(step)
                                    RhythmButtonWeighted(
                                        onClick = {
                                            adjustPitch(step)
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        },
                                        weight = 1f,
                                        isFirst = idx == 0,
                                        isLast = idx == 3,
                                        type = RhythmButtonType.Tonal,
                                        text = label,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pitchPresets.forEach { preset ->
                                    val isSelected = selectedPitch == preset
                                    ExpressiveFilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedPitch = preset
                                            if (syncEnabled) selectedSpeed = preset
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        },
                                        label = {
                                            Text(
                                                text = formatValueWithX(preset),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                RhythmGroupedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    size = RhythmButtonSize.Large
                ) {
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            selectedSpeed = 1.0f
                            selectedPitch = 1.0f
                        },
                        weight = 1f,
                        isFirst = true,
                        icon = MaterialSymbolIcon("restart_alt"),
                        text = context.getString(R.string.bottomsheet_reset)
                    )
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            dismissAndSave(selectedSpeed, selectedPitch)
                        },
                        weight = 1f,
                        isLast = true,
                        icon = RhythmIcons.Check,
                        text = context.getString(R.string.ui_apply)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeadingRow(
    label: String,
    value: String,
    isModified: Boolean,
    tint: Color,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpressiveStatusBadge(
                label = value,
                color = if (isModified) tint.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                textColor = if (isModified) tint else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isModified) {
                ExpressiveAssistChip(
                    onClick = onReset,
                    leadingIcon = {
                        Icon(
                            imageVector = MaterialSymbolIcon("restart_alt", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.bottomsheet_reset),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }
    }
}
