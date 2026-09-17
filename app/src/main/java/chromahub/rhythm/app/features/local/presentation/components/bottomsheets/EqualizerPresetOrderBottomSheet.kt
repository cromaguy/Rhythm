/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package chromahub.rhythm.app.features.local.presentation.components.bottomsheets

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.data.model.EqualizerPresetType
import chromahub.rhythm.app.shared.data.model.UnifiedEqualizerPreset
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.common.DragDropLazyColumn
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import kotlinx.coroutines.launch

private fun groupedBottomSheetItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    if (totalCount <= 1) return RoundedCornerShape(24.dp)
    return when (index) {
        0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        totalCount - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

@Composable
fun EqualizerPresetOrderBottomSheet(
    onDismiss: () -> Unit,
    musicViewModel: MusicViewModel
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val appSettings = musicViewModel.appSettings

    val presetOrder by appSettings.equalizerPresetOrder.collectAsState()
    val hiddenPresets by appSettings.hiddenEqualizerPresets.collectAsState()
    val customPresets by appSettings.customEqualizerPresets.collectAsState()
    val pinnedAutoEQ by appSettings.pinnedAutoEQProfiles.collectAsState()
    val currentAutoEQ by appSettings.autoEQProfile.collectAsState()
    val userDevicesJson by appSettings.userAudioDevices.collectAsState()
    val userDevices = remember(userDevicesJson) {
        chromahub.rhythm.app.shared.data.model.UserAudioDevice.fromJson(userDevicesJson)
    }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    // Build the master list of all known presets
    val builtInNames = remember { appSettings.defaultEqualizerPresetOrder }

    val allAutoEQNames = remember(pinnedAutoEQ, currentAutoEQ, userDevices) {
        val names = mutableListOf<String>()
        names.addAll(pinnedAutoEQ)
        if (currentAutoEQ.isNotBlank() && currentAutoEQ != "None" && currentAutoEQ !in names) {
            names.add(currentAutoEQ)
        }
        userDevices.forEach { device ->
            val profile = device.autoEQProfileName
            if (!profile.isNullOrBlank() && profile != "None" && profile !in names) {
                names.add(profile)
            }
        }
        names.distinct()
    }
    val allAutoEQKeys = remember(allAutoEQNames) {
        allAutoEQNames.map { "AutoEQ: $it" }
    }

    fun getPresetInfo(key: String): UnifiedEqualizerPreset {
        return when {
            key.startsWith("AutoEQ: ") -> {
                val profileName = key.removePrefix("AutoEQ: ")
                UnifiedEqualizerPreset(
                    id = key,
                    name = profileName,
                    type = EqualizerPresetType.AUTO_EQ,
                    bands = List(10) { 0f },
                    isDeletable = true
                )
            }
            customPresets.any { it.name == key } -> {
                val custom = customPresets.first { it.name == key }
                UnifiedEqualizerPreset(
                    id = custom.id,
                    name = custom.name,
                    type = EqualizerPresetType.CUSTOM,
                    bands = custom.bands,
                    isDeletable = true
                )
            }
            else -> {
                UnifiedEqualizerPreset(
                    id = key,
                    name = key,
                    type = EqualizerPresetType.BUILT_IN,
                    bands = List(10) { 0f },
                    isDeletable = false
                )
            }
        }
    }

    var reorderableList by remember(presetOrder, customPresets, allAutoEQKeys) {
        val allKeys = mutableListOf<String>()
        val isDefaultOrder = presetOrder == builtInNames || presetOrder.isEmpty()
        if (isDefaultOrder) {
            allKeys.addAll(allAutoEQKeys)
            customPresets.forEach { if (it.name !in allKeys) allKeys.add(it.name) }
            builtInNames.forEach { if (it !in allKeys) allKeys.add(it) }
        } else {
            presetOrder.forEach { key ->
                if (key in builtInNames || customPresets.any { it.name == key } || key in allAutoEQKeys) {
                    allKeys.add(key)
                }
            }
            val missingAutoEQ = allAutoEQKeys.filter { it !in allKeys }
            allKeys.addAll(0, missingAutoEQ)
            customPresets.forEach { if (it.name !in allKeys) allKeys.add(it.name) }
            builtInNames.forEach { if (it !in allKeys) allKeys.add(it) }
        }
        mutableStateOf<List<String>>(allKeys)
    }

    var hiddenPresetsSet by remember(hiddenPresets) {
        mutableStateOf(hiddenPresets.toSet())
    }

    var presetToDelete by remember { mutableStateOf<UnifiedEqualizerPreset?>(null) }

    val lazyListState = rememberLazyListState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        lazyListState = lazyListState,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = stringResource(R.string.eq_preset_order_title),
            subtitle = stringResource(R.string.eq_preset_order_desc),
            visible = true
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            AdaptiveSheetScrollContainer(
                lazyListState = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { endPadding ->
                DragDropLazyColumn(
                    items = reorderableList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp + endPadding),
                    lazyListState = lazyListState,
                    onMove = { fromIndex, toIndex ->
                        val newList = reorderableList.toMutableList()
                        val item = newList.removeAt(fromIndex)
                        newList.add(toIndex, item)
                        reorderableList = newList
                    },
                    itemKey = { it }
                ) { key, isDragging, index ->
                    val info = getPresetInfo(key)
                    val totalPresets = reorderableList.size

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDragging)
                                MaterialTheme.colorScheme.secondaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = groupedBottomSheetItemShape(index, totalPresets)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Position indicator
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                // Preset icon
                                val icon = when (info.type) {
                                    EqualizerPresetType.AUTO_EQ -> RhythmIcons.HeadphonesFilled
                                    EqualizerPresetType.CUSTOM -> MaterialSymbolIcon("tune", filled = true)
                                    EqualizerPresetType.BUILT_IN -> when (info.name) {
                                        "Flat" -> MaterialSymbolIcon("linear_scale", filled = true)
                                        "Rock" -> RhythmIcons.MusicNote
                                        "Pop" -> MaterialSymbolIcon("star", filled = true)
                                        "Jazz" -> MaterialSymbolIcon("piano", filled = true)
                                        "Classical" -> RhythmIcons.Library
                                        "Electronic", "Hip Hop" -> MaterialSymbolIcon("graphic_eq", filled = true)
                                        "Vocal" -> MaterialSymbolIcon("record_voice_over", filled = true)
                                        "Bass Boost" -> RhythmIcons.SpeakerFilled
                                        "Treble Boost" -> MaterialSymbolIcon("waves", filled = true)
                                        "V-Shape" -> MaterialSymbolIcon("show_chart", filled = true)
                                        "Harman" -> RhythmIcons.HeadphonesFilled
                                        else -> RhythmIcons.Equalizer
                                    }
                                }

                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    val displayName = if (info.type == EqualizerPresetType.BUILT_IN) {
                                        chromahub.rhythm.app.features.local.presentation.screens.getLocalizedPresetName(info.name)
                                    } else {
                                        info.name
                                    }
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val typeLabel = when (info.type) {
                                        EqualizerPresetType.BUILT_IN -> stringResource(R.string.eq_preset_type_builtin)
                                        EqualizerPresetType.CUSTOM -> stringResource(R.string.eq_preset_type_custom)
                                        EqualizerPresetType.AUTO_EQ -> stringResource(R.string.eq_preset_type_autoeq)
                                    }

                                    Text(
                                        text = typeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Actions: Delete (if custom), Visibility, and Drag handle
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (info.isDeletable) {
                                    IconButton(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                            presetToDelete = info
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Delete,
                                            contentDescription = stringResource(R.string.eq_preset_delete_title),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                val isHidden = hiddenPresetsSet.contains(key)
                                val visibleCount = reorderableList.count { !hiddenPresetsSet.contains(it) }

                                IconButton(
                                    onClick = {
                                        if (!isHidden && visibleCount <= 1) {
                                            Toast.makeText(context, R.string.eq_preset_at_least_one, Toast.LENGTH_SHORT).show()
                                            return@IconButton
                                        }
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        hiddenPresetsSet = if (isHidden) {
                                            hiddenPresetsSet - key
                                        } else {
                                            hiddenPresetsSet + key
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isHidden) RhythmIcons.VisibilityOff else RhythmIcons.Visibility,
                                        contentDescription = if (isHidden) "Show preset" else "Hide preset",
                                        tint = if (isHidden)
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        else
                                            MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Icon(
                                    imageVector = RhythmIcons.DragHandle,
                                    contentDescription = stringResource(R.string.drag_to_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Sticky bottom actions footer
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                RhythmGroupedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    size = RhythmButtonSize.Large
                ) {
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            musicViewModel.resetEqualizerPresetOrder()
                            musicViewModel.setHiddenEqualizerPresets(emptySet())
                            reorderableList = allAutoEQKeys + customPresets.map { it.name } + builtInNames
                            hiddenPresetsSet = emptySet()
                            Toast.makeText(context, R.string.eq_preset_order_reset, Toast.LENGTH_SHORT).show()
                        },
                        weight = 1f,
                        isFirst = true,
                        icon = MaterialSymbolIcon("restart_alt"),
                        text = context.getString(R.string.bottomsheet_reset)
                    )

                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            musicViewModel.setEqualizerPresetOrder(reorderableList)
                            musicViewModel.setHiddenEqualizerPresets(hiddenPresetsSet)
                            Toast.makeText(context, R.string.eq_preset_order_saved, Toast.LENGTH_SHORT).show()
                            scope.launch {
                                sheetState.hide()
                            }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    onDismiss()
                                }
                            }
                        },
                        weight = 1f,
                        isLast = true,
                        icon = RhythmIcons.Check,
                        text = context.getString(R.string.bottomsheet_save)
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    presetToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            icon = {
                Icon(
                    imageVector = RhythmIcons.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(stringResource(R.string.eq_preset_delete_title)) },
            text = { Text(stringResource(R.string.eq_preset_delete_confirm, preset.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        val keyToRemove = if (preset.type == EqualizerPresetType.AUTO_EQ) {
                            musicViewModel.deleteAutoEQProfile(preset.name)
                            "AutoEQ: ${preset.name}"
                        } else {
                            musicViewModel.deleteCustomEqualizerPreset(preset.id)
                            preset.name
                        }
                        reorderableList = reorderableList.filter { it != keyToRemove && it != preset.name }
                        hiddenPresetsSet = hiddenPresetsSet - keyToRemove - preset.name
                        presetToDelete = null
                        Toast.makeText(context, R.string.eq_preset_deleted, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.button_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { presetToDelete = null }) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}
