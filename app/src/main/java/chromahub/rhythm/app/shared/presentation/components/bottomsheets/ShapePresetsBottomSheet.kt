/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import kotlinx.coroutines.delay

data class ShapePresetOption(
    val id: String,
    val displayName: String,
    val description: String,
    val icon: MaterialSymbolIcon
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShapePresetsBottomSheet(
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentPreset by appSettings.expressiveShapePreset.collectAsState()
    var showPresetContent by remember { mutableStateOf(false) }

    val presets = remember {
        listOf(
            ShapePresetOption("DEFAULT", "Default", "Gentle expressive shapes for all ages", RhythmIcons.RadioButtonUnchecked),
            ShapePresetOption("FRIENDLY", "Friendly", "Warm and approachable shapes", RhythmIcons.FavoriteFilled),
            ShapePresetOption("CHEERFUL", "Cheerful", "Bright and expressive shapes", MaterialSymbolIcon("wb_sunny")),
            ShapePresetOption("MODERN", "Modern", "Contemporary expressive design", MaterialSymbolIcon("star")),
            ShapePresetOption("PLAYFUL", "Playful", "Fun and expressive shapes", MaterialSymbolIcon("celebration")),
            ShapePresetOption("ORGANIC", "Organic", "Nature-inspired shapes", MaterialSymbolIcon("park")),
            ShapePresetOption("GEOMETRIC", "Geometric", "Clean and modern shapes", RhythmIcons.Category),
            ShapePresetOption("RETRO", "Retro", "Pixelated nostalgic shapes", MaterialSymbolIcon("gamepad")),
            ShapePresetOption("CUSTOM", "Custom", "Your personalized selection", RhythmIcons.Tune)
        )
    }

    LaunchedEffect(sheetState) {
        sheetState.expand()
    }

    val scrollState = rememberScrollState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        scrollState = scrollState,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = stringResource(R.string.expressiveshapessettingsscreen_choose_a_preset),
            subtitle = stringResource(R.string.expressiveshapessettingsscreen_select_a_theme_for),
            visible = true
        )

        AdaptiveSheetScrollContainer(
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth()
        ) { endPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                presets.forEachIndexed { index, preset ->
                    val isSelected = preset.id == currentPreset

                    var isPressed by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.96f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "preset_scale"
                    )

                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "preset_container_color"
                    )

                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            isPressed = true
                            appSettings.applyExpressiveShapePreset(preset.id)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        shape = groupedBottomSheetItemShape(index, presets.size),
                        colors = CardDefaults.cardColors(
                            containerColor = containerColor
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = preset.icon,
                                    contentDescription = preset.displayName,
                                    tint = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column {
                                    Text(
                                        text = getLocalizedShapePresetName(preset.id),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = getLocalizedShapePresetDesc(preset.id),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = RhythmIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun getLocalizedShapePresetName(id: String): String {
    val resId = when (id) {
        "DEFAULT" -> R.string.shape_preset_default
        "FRIENDLY" -> R.string.shape_preset_friendly
        "CHEERFUL" -> R.string.shape_preset_cheerful
        "MODERN" -> R.string.shape_preset_modern
        "PLAYFUL" -> R.string.shape_preset_playful
        "ORGANIC" -> R.string.shape_preset_organic
        "GEOMETRIC" -> R.string.shape_preset_geometric
        "RETRO" -> R.string.shape_preset_retro
        "CUSTOM" -> R.string.shape_preset_custom
        else -> null
    }
    return if (resId != null) stringResource(resId) else id
}

@Composable
fun getLocalizedShapePresetDesc(id: String): String {
    val resId = when (id) {
        "DEFAULT" -> R.string.shape_preset_default_desc
        "FRIENDLY" -> R.string.shape_preset_friendly_desc
        "CHEERFUL" -> R.string.shape_preset_cheerful_desc
        "MODERN" -> R.string.shape_preset_modern_desc
        "PLAYFUL" -> R.string.shape_preset_playful_desc
        "ORGANIC" -> R.string.shape_preset_organic_desc
        "GEOMETRIC" -> R.string.shape_preset_geometric_desc
        "RETRO" -> R.string.shape_preset_retro_desc
        "CUSTOM" -> R.string.shape_preset_custom_desc
        else -> null
    }
    return if (resId != null) stringResource(resId) else ""
}
