/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package chromahub.rhythm.app.shared.presentation.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.presentation.screens.equalizer.ViperEngineStatusSheet
import chromahub.rhythm.app.infrastructure.audio.viper.AnalogXConfig
import chromahub.rhythm.app.infrastructure.audio.viper.BassMonoConfig
import chromahub.rhythm.app.infrastructure.audio.viper.ConvolverConfig
import chromahub.rhythm.app.infrastructure.audio.viper.CureCrossfeedConfig
import chromahub.rhythm.app.infrastructure.audio.viper.DiffSurroundConfig
import chromahub.rhythm.app.infrastructure.audio.viper.FieldSurroundConfig
import chromahub.rhythm.app.infrastructure.audio.viper.FireEqualizerConfig
import chromahub.rhythm.app.infrastructure.audio.viper.MasterGateConfig
import chromahub.rhythm.app.infrastructure.audio.viper.PsychoacousticBassConfig
import chromahub.rhythm.app.infrastructure.audio.viper.ReverberationConfig
import chromahub.rhythm.app.infrastructure.audio.viper.TubeSimulatorConfig
import chromahub.rhythm.app.infrastructure.audio.viper.ViperAudioProcessor
import chromahub.rhythm.app.infrastructure.audio.viper.ViperBassConfig
import chromahub.rhythm.app.infrastructure.audio.viper.ViperClarityConfig
import chromahub.rhythm.app.infrastructure.audio.viper.ViperDdcConfig
import chromahub.rhythm.app.infrastructure.audio.viper.ViperOutputDevice
import chromahub.rhythm.app.infrastructure.audio.viper.ViperSettingsRepository
import chromahub.rhythm.app.infrastructure.audio.viper.ViperUiMode
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.HeaderAction
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedMenuContent
import chromahub.rhythm.app.shared.presentation.components.common.RhythmMenuItem
import chromahub.rhythm.app.shared.presentation.components.common.SmallTabAnimation
import chromahub.rhythm.app.shared.presentation.components.common.horizontalEdgeBlend
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class ViperSheet { ENGINE_STATUS }

private data class ViperPreset(
    val key: String,
    @StringRes val titleRes: Int,
    val icon: MaterialSymbolIcon
)

private val viperPresets = listOf(
    ViperPreset("audiophile", R.string.viper_preset_audiophile, MaterialSymbolIcon("diamond", filled = true)),
    ViperPreset("basshead", R.string.viper_preset_basshead, MaterialSymbolIcon("bass_boost", filled = true)),
    ViperPreset("tube", R.string.viper_preset_tube, MaterialSymbolIcon("thermostat", filled = true)),
    ViperPreset("stadium", R.string.viper_preset_stadium, MaterialSymbolIcon("theater_comedy", filled = true))
)

private val clarityModes = listOf("Natural", "Ozone+", "X-HiFi")
private val ddcDevices = listOf(
    "None",
    "Apple AirPods Pro",
    "Sony WH-1000XM4",
    "Sennheiser HD 650",
    "Audio-Technica ATH-M50x"
)
private val ddcDeviceLabels = listOf("None", "AirPods", "Sony XM4", "HD 650", "M50x")
private val convolverPresets = listOf("Studio Master", "Warm Tube", "Analog Tape", "Acoustic Room")
private val bassModes = listOf("Natural", "Pure Bass", "Subwoofer")
private val dynamicSystemDevices = listOf("Earphone", "Hi-End", "EarPods", "Extreme", "Speaker")
private val tubeModels = listOf("6N1J Triode", "12AX7 Warm", "FET Stage")
private val analogXModes = listOf("Subtle", "Moderate", "Warm Vintage")
private val surroundQualities = listOf("Standard", "VHS+")
private val agcStrengths = listOf("Subtle", "Moderate", "Aggressive")
private val eqPresetOptions = listOf("Flat", "Rock", "Acoustic", "Bass", "R&B")
private val eqPresetKeys = listOf("Flat", "Rock", "Acoustic", "Bass Booster", "R&B")

/**
 * ViPER4Android DSP settings.
 *
 * Uses Rhythm's standard Material 3 Expressive settings items and groups.
 */
@Composable
fun ViperSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val viperRepo = remember { ViperSettingsRepository.getInstance(context) }
    val viperConfig by viperRepo.configFlow.collectAsState()

    var openSheet by rememberSaveable { mutableStateOf<ViperSheet?>(null) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var selectedPreset by rememberSaveable { mutableStateOf<String?>(null) }
    var showImportField by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }

    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    val exportToFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                try {
                    val json = withContext(Dispatchers.IO) { viperRepo.exportProfileJson() }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(json.toByteArray())
                            stream.flush()
                        } ?: throw IllegalStateException("Unable to open the selected file")
                    }
                    Toast.makeText(context, context.getString(R.string.viper_profile_saved), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.viper_profile_save_failed), Toast.LENGTH_SHORT).show()
                } finally {
                    isBusy = false
                }
            }
        }
    }

    val importFromFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }
                    val imported = withContext(Dispatchers.IO) {
                        !json.isNullOrBlank() && viperRepo.importProfileJson(json)
                    }
                    Toast.makeText(
                        context,
                        context.getString(
                            if (imported) R.string.viper_profile_imported_success else R.string.viper_profile_invalid_format
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.viper_profile_invalid_format), Toast.LENGTH_SHORT).show()
                } finally {
                    isBusy = false
                }
            }
        }
    }

    CollapsibleHeaderScreen(
        title = stringResource(R.string.settings_viper_title),
        showBackButton = true,
        onBackClick = onBackClick,
        headerActions = listOf(
            HeaderAction(
                icon = MaterialSymbolIcon("more_vert", filled = true),
                contentDescription = stringResource(R.string.content_desc_more_options),
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    showMenu = true
                }
            )
        ),
        actions = {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .widthIn(min = 200.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                RhythmGroupedMenuContent(
                    items = listOf(
                        RhythmMenuItem(
                            title = stringResource(R.string.viper_engine_status_title),
                            icon = MaterialSymbolIcon("insights", filled = true),
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = {
                                showMenu = false
                                openSheet = ViperSheet.ENGINE_STATUS
                            }
                        ),
                        RhythmMenuItem(
                            title = stringResource(R.string.bottomsheet_reset),
                            icon = MaterialSymbolIcon("restart_alt", filled = true),
                            iconContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            iconTint = MaterialTheme.colorScheme.onErrorContainer,
                            isDestructive = true,
                            onClick = {
                                showMenu = false
                                showResetDialog = true
                            }
                        )
                    )
                )
            }
        },
        headerContent = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (viperConfig.masterEnabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("developer_board", filled = true),
                        contentDescription = null,
                        tint = if (viperConfig.masterEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(35.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (viperConfig.masterEnabled) {
                            stringResource(R.string.common_active)
                        } else {
                            stringResource(R.string.common_disabled)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TunerAnimatedSwitch(
                        checked = viperConfig.masterEnabled,
                        onCheckedChange = { isChecked ->
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            viperRepo.setMasterEnabled(isChecked)
                        }
                    )
                }
            }
        }
    ) { modifier ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.viper_profiles_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                val presetRowState = rememberLazyListState()
                LazyRow(
                    state = presetRowState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                    modifier = Modifier.horizontalEdgeBlend(lazyListState = presetRowState, fadeWidth = 14.dp)
                ) {
                    itemsIndexed(viperPresets, key = { _, preset -> preset.key }) { index, preset ->
                        val isSelected = selectedPreset == preset.key
                        SmallTabAnimation(
                            index = index,
                            selectedIndex = viperPresets.indexOfFirst { it.key == selectedPreset },
                            title = stringResource(preset.titleRes),
                            selectedColor = MaterialTheme.colorScheme.primaryContainer,
                            onSelectedColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                applyViperPreset(viperRepo, preset.key)
                                selectedPreset = preset.key
                            },
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = preset.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(preset.titleRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val targetItems = buildList {
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("device_hub"),
                            title = { Text(stringResource(R.string.viper_output_device_title)) },
                            description = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ExpressiveButtonGroup(
                                        items = listOf("Speaker", "Headset", "Bluetooth", "USB"),
                                        selectedIndex = when (viperConfig.activeOutputDevice) {
                                            ViperOutputDevice.SPEAKER -> 0
                                            ViperOutputDevice.HEADSET -> 1
                                            ViperOutputDevice.BLUETOOTH -> 2
                                            ViperOutputDevice.USB -> 3
                                        },
                                        onItemClick = { index ->
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                            val device = when (index) {
                                                1 -> ViperOutputDevice.HEADSET
                                                2 -> ViperOutputDevice.BLUETOOTH
                                                3 -> ViperOutputDevice.USB
                                                else -> ViperOutputDevice.SPEAKER
                                            }
                                            viperRepo.switchDeviceProfile(device)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        )
                    )
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("sync_alt"),
                            title = { Text(stringResource(R.string.viper_auto_device_profile)) },
                            description = { Text(stringResource(R.string.viper_auto_device_profile_desc)) },
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = viperConfig.autoDeviceProfileEnabled,
                                    onCheckedChange = { checked ->
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        viperRepo.updateConfig { it.copy(autoDeviceProfileEnabled = checked) }
                                    }
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                viperRepo.updateConfig { it.copy(autoDeviceProfileEnabled = !it.autoDeviceProfileEnabled) }
                            }
                        )
                    )
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("tune"),
                            title = { Text(stringResource(R.string.viper_ui_complexity)) },
                            description = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ExpressiveButtonGroup(
                                        items = listOf("Simple", "Moderate", "Expert"),
                                        selectedIndex = when (viperConfig.uiMode) {
                                            ViperUiMode.SIMPLE -> 0
                                            ViperUiMode.MODERATE -> 1
                                            ViperUiMode.EXPERT -> 2
                                        },
                                        onItemClick = { index ->
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                            val mode = when (index) {
                                                0 -> ViperUiMode.SIMPLE
                                                2 -> ViperUiMode.EXPERT
                                                else -> ViperUiMode.MODERATE
                                            }
                                            viperRepo.setUiMode(mode)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        )
                    )
                }
                Material3SettingsGroup(
                    title = stringResource(R.string.viper_output_device_title),
                    items = targetItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val masterItems = buildList {
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("tune", filled = true),
                            title = { Text("Master Limiter") },
                            description = { Text("Peak protection & soft-knee anti-clipping") },
                            enabled = viperConfig.masterEnabled,
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = viperConfig.masterGate.limiterEnabled,
                                    onCheckedChange = { en ->
                                        viperRepo.updateConfig { it.copy(masterGate = it.masterGate.copy(limiterEnabled = en)) }
                                    },
                                    enabled = viperConfig.masterEnabled
                                )
                            },
                            onClick = {
                                if (viperConfig.masterEnabled) {
                                    viperRepo.updateConfig { it.copy(masterGate = it.masterGate.copy(limiterEnabled = !it.masterGate.limiterEnabled)) }
                                }
                            }
                        )
                    )
                    if (viperConfig.masterEnabled && viperConfig.masterGate.limiterEnabled) {
                        add(
                            Material3SettingsItem(
                                title = { Text("Limiter Threshold") },
                                description = {
                                    ViperSliderRow(
                                        label = "Ceiling Level",
                                        valueText = String.format(Locale.US, "%.1f dB", viperConfig.masterGate.limiterThresholdDb),
                                        value = viperConfig.masterGate.limiterThresholdDb,
                                        valueRange = -6.0f..0.0f,
                                        onValueChange = { v ->
                                            viperRepo.updateConfig { it.copy(masterGate = it.masterGate.copy(limiterThresholdDb = v)) }
                                        }
                                    )
                                }
                            )
                        )
                    }
                    if (viperConfig.masterEnabled) {
                        add(
                            Material3SettingsItem(
                                title = { Text("Output Volume & Pan") },
                                description = {
                                    Column {
                                        ViperSliderRow(
                                            label = "Output Volume",
                                            valueText = String.format(Locale.US, "%+.1f dB", viperConfig.masterGate.outputVolumeDb),
                                            value = viperConfig.masterGate.outputVolumeDb,
                                            valueRange = -20.0f..6.0f,
                                            onValueChange = { v ->
                                                viperRepo.updateConfig { it.copy(masterGate = it.masterGate.copy(outputVolumeDb = v)) }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ViperSliderRow(
                                            label = "Channel Pan (L / R)",
                                            valueText = when {
                                                viperConfig.masterGate.channelPan < -0.05f -> String.format(Locale.US, "L %d%%", (-viperConfig.masterGate.channelPan * 100).toInt())
                                                viperConfig.masterGate.channelPan > 0.05f -> String.format(Locale.US, "R %d%%", (viperConfig.masterGate.channelPan * 100).toInt())
                                                else -> "Center"
                                            },
                                            value = viperConfig.masterGate.channelPan,
                                            valueRange = -1.0f..1.0f,
                                            onValueChange = { v ->
                                                viperRepo.updateConfig { it.copy(masterGate = it.masterGate.copy(channelPan = v)) }
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    }
                }
                Material3SettingsGroup(
                    title = "Master Control & Limiter",
                    items = masterItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val eqItems = buildList {
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("equalizer", filled = true),
                            title = { Text("FireEqualizer") },
                            description = { Text("10-Band graphic tone equalizer") },
                            enabled = viperConfig.masterEnabled,
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = viperConfig.fireEqualizer.enabled,
                                    onCheckedChange = { en ->
                                        viperRepo.updateConfig { it.copy(fireEqualizer = it.fireEqualizer.copy(enabled = en)) }
                                    },
                                    enabled = viperConfig.masterEnabled
                                )
                            },
                            onClick = {
                                if (viperConfig.masterEnabled) {
                                    viperRepo.updateConfig { it.copy(fireEqualizer = it.fireEqualizer.copy(enabled = !it.fireEqualizer.enabled)) }
                                }
                            }
                        )
                    )
                    if (viperConfig.masterEnabled && viperConfig.fireEqualizer.enabled) {
                        val currentPresetName = if (viperConfig.fireEqualizer.presetName == "Bass") "Bass Booster" else viperConfig.fireEqualizer.presetName
                        val presetIdx = eqPresetKeys.indexOf(currentPresetName).coerceAtLeast(0)
                        add(
                            Material3SettingsItem(
                                title = { Text("Equalizer Preset") },
                                description = {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ExpressiveButtonGroup(
                                            items = eqPresetOptions,
                                            selectedIndex = presetIdx,
                                            onItemClick = { idx ->
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                val pName = eqPresetKeys[idx]
                                                val bands = FireEqualizerConfig.BUILTIN_PRESETS[pName] ?: List(10) { 0.0f }
                                                viperRepo.updateConfig {
                                                    it.copy(fireEqualizer = it.fireEqualizer.copy(presetName = pName, bands = bands))
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        )
                        add(
                            Material3SettingsItem(
                                title = { Text("Tone Frequencies") },
                                description = {
                                    Column {
                                        val freqs = FireEqualizerConfig.FREQUENCIES_10
                                        for (i in 0 until minOf(10, viperConfig.fireEqualizer.bands.size)) {
                                            val freqLabel = if (freqs[i] >= 1000) "${freqs[i] / 1000} kHz" else "${freqs[i]} Hz"
                                            ViperSliderRow(
                                                label = freqLabel,
                                                valueText = String.format(Locale.US, "%+.1f dB", viperConfig.fireEqualizer.bands[i]),
                                                value = viperConfig.fireEqualizer.bands[i],
                                                valueRange = -12.0f..12.0f,
                                                onValueChange = { newLevel ->
                                                    val updatedBands = viperConfig.fireEqualizer.bands.toMutableList()
                                                    updatedBands[i] = newLevel
                                                    viperRepo.updateConfig {
                                                        it.copy(fireEqualizer = it.fireEqualizer.copy(bands = updatedBands, presetName = "Custom"))
                                                    }
                                                }
                                            )
                                            if (i < 9) Spacer(modifier = Modifier.height(4.dp))
                                        }
                                    }
                                }
                            )
                        )
                    }
                }
                Material3SettingsGroup(
                    title = "Tone Equalizer",
                    items = eqItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val bassItems = buildList {
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("speaker", filled = true),
                            title = { Text("ViPER Bass") },
                            description = { Text("Natural, Pure & Subwoofer low-end") },
                            enabled = viperConfig.masterEnabled,
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = viperConfig.viperBass.enabled,
                                    onCheckedChange = { en ->
                                        viperRepo.updateConfig { it.copy(viperBass = it.viperBass.copy(enabled = en)) }
                                    },
                                    enabled = viperConfig.masterEnabled
                                )
                            },
                            onClick = {
                                if (viperConfig.masterEnabled) {
                                    viperRepo.updateConfig { it.copy(viperBass = it.viperBass.copy(enabled = !it.viperBass.enabled)) }
                                }
                            }
                        )
                    )
                    if (viperConfig.masterEnabled && viperConfig.viperBass.enabled) {
                        add(
                            Material3SettingsItem(
                                title = { Text("Bass Configuration") },
                                description = {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ExpressiveButtonGroup(
                                            items = bassModes,
                                            selectedIndex = viperConfig.viperBass.mode.coerceIn(0, 2),
                                            onItemClick = { idx ->
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                viperRepo.updateConfig { it.copy(viperBass = it.viperBass.copy(mode = idx)) }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        ViperSliderRow(
                                            label = "Bass Frequency",
                                            valueText = String.format(Locale.US, "%d Hz", viperConfig.viperBass.frequencyHz),
                                            value = viperConfig.viperBass.frequencyHz.toFloat(),
                                            valueRange = 40.0f..160.0f,
                                            onValueChange = { v ->
                                                viperRepo.updateConfig { it.copy(viperBass = it.viperBass.copy(frequencyHz = v.toInt())) }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ViperSliderRow(
                                            label = "Bass Gain",
                                            valueText = String.format(Locale.US, "+%.1f dB", viperConfig.viperBass.gainDb),
                                            value = viperConfig.viperBass.gainDb,
                                            valueRange = 0.0f..18.0f,
                                            onValueChange = { v ->
                                                viperRepo.updateConfig { it.copy(viperBass = it.viperBass.copy(gainDb = v)) }
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    }

                    if (viperConfig.uiMode != ViperUiMode.SIMPLE) {
                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("graphic_eq", filled = true),
                                title = { Text("Dynamic System") },
                                description = { Text("Acoustic transducer & driver emulation") },
                                enabled = viperConfig.masterEnabled,
                                trailingContent = {
                                    TunerAnimatedSwitch(
                                        checked = viperConfig.dynamicSystem.enabled,
                                        onCheckedChange = { en ->
                                            viperRepo.updateConfig { it.copy(dynamicSystem = it.dynamicSystem.copy(enabled = en)) }
                                        },
                                        enabled = viperConfig.masterEnabled
                                    )
                                },
                                onClick = {
                                    if (viperConfig.masterEnabled) {
                                        viperRepo.updateConfig { it.copy(dynamicSystem = it.dynamicSystem.copy(enabled = !it.dynamicSystem.enabled)) }
                                    }
                                }
                            )
                        )
                        if (viperConfig.masterEnabled && viperConfig.dynamicSystem.enabled) {
                            add(
                                Material3SettingsItem(
                                    title = { Text("Transducer Parameters") },
                                    description = {
                                        Column {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ExpressiveButtonGroup(
                                                items = dynamicSystemDevices,
                                                selectedIndex = viperConfig.dynamicSystem.device.coerceIn(0, 4),
                                                onItemClick = { idx ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                    viperRepo.updateConfig { it.copy(dynamicSystem = it.dynamicSystem.copy(device = idx)) }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            ViperSliderRow(
                                                label = "Effect Strength",
                                                valueText = String.format(Locale.US, "%d%%", (viperConfig.dynamicSystem.strength * 100).toInt()),
                                                value = viperConfig.dynamicSystem.strength * 100f,
                                                valueRange = 0.0f..100.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(dynamicSystem = it.dynamicSystem.copy(strength = v / 100f)) }
                                                }
                                            )
                                        }
                                    }
                                )
                            )
                        }
                    }

                    if (viperConfig.uiMode == ViperUiMode.EXPERT) {
                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("music_note", filled = true),
                                title = { Text("Bass Mono") },
                                description = { Text("Center low frequencies to prevent phase cancellation") },
                                enabled = viperConfig.masterEnabled,
                                trailingContent = {
                                    TunerAnimatedSwitch(
                                        checked = viperConfig.bassMono.enabled,
                                        onCheckedChange = { en ->
                                            viperRepo.updateConfig { it.copy(bassMono = it.bassMono.copy(enabled = en)) }
                                        },
                                        enabled = viperConfig.masterEnabled
                                    )
                                },
                                onClick = {
                                    if (viperConfig.masterEnabled) {
                                        viperRepo.updateConfig { it.copy(bassMono = it.bassMono.copy(enabled = !it.bassMono.enabled)) }
                                    }
                                }
                            )
                        )
                        if (viperConfig.masterEnabled && viperConfig.bassMono.enabled) {
                            add(
                                Material3SettingsItem(
                                    title = { Text("Bass Mono Cutoff") },
                                    description = {
                                        ViperSliderRow(
                                            label = "Cutoff Frequency",
                                            valueText = String.format(Locale.US, "%d Hz", viperConfig.bassMono.frequencyHz),
                                            value = viperConfig.bassMono.frequencyHz.toFloat(),
                                            valueRange = 40.0f..200.0f,
                                            onValueChange = { v ->
                                                viperRepo.updateConfig { it.copy(bassMono = it.bassMono.copy(frequencyHz = v.toInt())) }
                                            }
                                        )
                                    }
                                )
                            )
                        }

                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("psychology", filled = true),
                                title = { Text("Psychoacoustic Bass") },
                                description = { Text("Missing fundamental harmonic generator") },
                                enabled = viperConfig.masterEnabled,
                                trailingContent = {
                                    TunerAnimatedSwitch(
                                        checked = viperConfig.psychoacousticBass.enabled,
                                        onCheckedChange = { en ->
                                            viperRepo.updateConfig { it.copy(psychoacousticBass = it.psychoacousticBass.copy(enabled = en)) }
                                        },
                                        enabled = viperConfig.masterEnabled
                                    )
                                },
                                onClick = {
                                    if (viperConfig.masterEnabled) {
                                        viperRepo.updateConfig { it.copy(psychoacousticBass = it.psychoacousticBass.copy(enabled = !it.psychoacousticBass.enabled)) }
                                    }
                                }
                            )
                        )
                        if (viperConfig.masterEnabled && viperConfig.psychoacousticBass.enabled) {
                            add(
                                Material3SettingsItem(
                                    title = { Text("Harmonic Parameters") },
                                    description = {
                                        Column {
                                            ViperSliderRow(
                                                label = "Bass Cutoff",
                                                valueText = String.format(Locale.US, "%d Hz", viperConfig.psychoacousticBass.cutoffHz),
                                                value = viperConfig.psychoacousticBass.cutoffHz.toFloat(),
                                                valueRange = 40.0f..140.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(psychoacousticBass = it.psychoacousticBass.copy(cutoffHz = v.toInt())) }
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ViperSliderRow(
                                                label = "Harmonic Intensity",
                                                valueText = String.format(Locale.US, "%d%%", (viperConfig.psychoacousticBass.intensity * 100).toInt()),
                                                value = viperConfig.psychoacousticBass.intensity * 100f,
                                                valueRange = 0.0f..100.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(psychoacousticBass = it.psychoacousticBass.copy(intensity = v / 100f)) }
                                                }
                                            )
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
                Material3SettingsGroup(
                    title = "Bass & Low-End",
                    items = bassItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val clarityItems = buildList {
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("waves", filled = true),
                            title = { Text(stringResource(R.string.viper_clarity_title)) },
                            description = { Text(stringResource(R.string.viper_clarity_desc)) },
                            enabled = viperConfig.masterEnabled,
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = viperConfig.viperClarity.enabled,
                                    onCheckedChange = { en ->
                                        viperRepo.updateConfig { it.copy(viperClarity = it.viperClarity.copy(enabled = en)) }
                                    },
                                    enabled = viperConfig.masterEnabled
                                )
                            },
                            onClick = {
                                if (viperConfig.masterEnabled) {
                                    viperRepo.updateConfig { it.copy(viperClarity = it.viperClarity.copy(enabled = !it.viperClarity.enabled)) }
                                }
                            }
                        )
                    )
                    if (viperConfig.masterEnabled && viperConfig.viperClarity.enabled) {
                        add(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.viper_clarity_mode)) },
                                description = {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ExpressiveButtonGroup(
                                            items = clarityModes,
                                            selectedIndex = viperConfig.viperClarity.mode.coerceIn(0, clarityModes.size - 1),
                                            onItemClick = { index ->
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                viperRepo.updateConfig { it.copy(viperClarity = it.viperClarity.copy(mode = index)) }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        ViperSliderRow(
                                            label = stringResource(R.string.viper_clarity_gain),
                                            valueText = String.format(Locale.US, "+%.1f dB", viperConfig.viperClarity.gainDb),
                                            value = viperConfig.viperClarity.gainDb,
                                            valueRange = 0f..12f,
                                            onValueChange = { value ->
                                                viperRepo.updateConfig { it.copy(viperClarity = it.viperClarity.copy(gainDb = value)) }
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    }

                    if (viperConfig.uiMode != ViperUiMode.SIMPLE) {
                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("candlestick_chart", filled = true),
                                title = { Text("Tube Simulator (6N1J)") },
                                description = { Text("Warm vacuum tube triode saturation") },
                                enabled = viperConfig.masterEnabled,
                                trailingContent = {
                                    TunerAnimatedSwitch(
                                        checked = viperConfig.tubeSimulator.enabled,
                                        onCheckedChange = { en ->
                                            viperRepo.updateConfig { it.copy(tubeSimulator = it.tubeSimulator.copy(enabled = en)) }
                                        },
                                        enabled = viperConfig.masterEnabled
                                    )
                                },
                                onClick = {
                                    if (viperConfig.masterEnabled) {
                                        viperRepo.updateConfig { it.copy(tubeSimulator = it.tubeSimulator.copy(enabled = !it.tubeSimulator.enabled)) }
                                    }
                                }
                            )
                        )
                        if (viperConfig.masterEnabled && viperConfig.tubeSimulator.enabled) {
                            add(
                                Material3SettingsItem(
                                    title = { Text("Tube Model & Saturation") },
                                    description = {
                                        Column {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ExpressiveButtonGroup(
                                                items = tubeModels,
                                                selectedIndex = viperConfig.tubeSimulator.model.coerceIn(0, 2),
                                                onItemClick = { idx ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                    viperRepo.updateConfig { it.copy(tubeSimulator = it.tubeSimulator.copy(model = idx)) }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            ViperSliderRow(
                                                label = "Drive Amount",
                                                valueText = String.format(Locale.US, "%d%%", (viperConfig.tubeSimulator.drive * 100).toInt()),
                                                value = viperConfig.tubeSimulator.drive * 100f,
                                                valueRange = 0.0f..100.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(tubeSimulator = it.tubeSimulator.copy(drive = v / 100f)) }
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ViperSliderRow(
                                                label = "Wet / Dry Mix",
                                                valueText = String.format(Locale.US, "%d%%", (viperConfig.tubeSimulator.mix * 100).toInt()),
                                                value = viperConfig.tubeSimulator.mix * 100f,
                                                valueRange = 0.0f..100.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(tubeSimulator = it.tubeSimulator.copy(mix = v / 100f)) }
                                                }
                                            )
                                        }
                                    }
                                )
                            )
                        }

                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("insights", filled = true),
                                title = { Text("Spectrum Extension") },
                                description = { Text("High-frequency harmonic reconstructor") },
                                enabled = viperConfig.masterEnabled,
                                trailingContent = {
                                    TunerAnimatedSwitch(
                                        checked = viperConfig.spectrumExtension.enabled,
                                        onCheckedChange = { en ->
                                            viperRepo.updateConfig { it.copy(spectrumExtension = it.spectrumExtension.copy(enabled = en)) }
                                        },
                                        enabled = viperConfig.masterEnabled
                                    )
                                },
                                onClick = {
                                    if (viperConfig.masterEnabled) {
                                        viperRepo.updateConfig { it.copy(spectrumExtension = it.spectrumExtension.copy(enabled = !it.spectrumExtension.enabled)) }
                                    }
                                }
                            )
                        )
                        if (viperConfig.masterEnabled && viperConfig.spectrumExtension.enabled) {
                            add(
                                Material3SettingsItem(
                                    title = { Text("Exciter Parameters") },
                                    description = {
                                        Column {
                                            ViperSliderRow(
                                                label = "Extension Strength",
                                                valueText = String.format(Locale.US, "%d%%", (viperConfig.spectrumExtension.strength * 100).toInt()),
                                                value = viperConfig.spectrumExtension.strength * 100f,
                                                valueRange = 0.0f..100.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(spectrumExtension = it.spectrumExtension.copy(strength = v / 100f)) }
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ViperSliderRow(
                                                label = "Exciter Amount",
                                                valueText = String.format(Locale.US, "%d%%", (viperConfig.spectrumExtension.exciter * 100).toInt()),
                                                value = viperConfig.spectrumExtension.exciter * 100f,
                                                valueRange = 0.0f..100.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(spectrumExtension = it.spectrumExtension.copy(exciter = v / 100f)) }
                                                }
                                            )
                                        }
                                    }
                                )
                            )
                        }
                    }

                    if (viperConfig.uiMode == ViperUiMode.EXPERT) {
                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("memory", filled = true),
                                title = { Text("AnalogX") },
                                description = { Text("Class-A discrete analog amplifier emulation") },
                                enabled = viperConfig.masterEnabled,
                                trailingContent = {
                                    TunerAnimatedSwitch(
                                        checked = viperConfig.analogX.enabled,
                                        onCheckedChange = { en ->
                                            viperRepo.updateConfig { it.copy(analogX = it.analogX.copy(enabled = en)) }
                                        },
                                        enabled = viperConfig.masterEnabled
                                    )
                                },
                                onClick = {
                                    if (viperConfig.masterEnabled) {
                                        viperRepo.updateConfig { it.copy(analogX = it.analogX.copy(enabled = !it.analogX.enabled)) }
                                    }
                                }
                            )
                        )
                        if (viperConfig.masterEnabled && viperConfig.analogX.enabled) {
                            add(
                                Material3SettingsItem(
                                    title = { Text("Warmth Level") },
                                    description = {
                                        Column {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ExpressiveButtonGroup(
                                                items = analogXModes,
                                                selectedIndex = viperConfig.analogX.mode.coerceIn(0, 2),
                                                onItemClick = { idx ->
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                    viperRepo.updateConfig { it.copy(analogX = it.analogX.copy(mode = idx)) }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
                Material3SettingsGroup(
                    title = "Clarity & Harmonics",
                    items = clarityItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val ddcItems = buildList {
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("headphones", filled = true),
                            title = { Text(stringResource(R.string.viper_ddc_title)) },
                            description = { Text(stringResource(R.string.viper_ddc_desc)) },
                            enabled = viperConfig.masterEnabled,
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = viperConfig.viperDdc.enabled,
                                    onCheckedChange = { en ->
                                        viperRepo.updateConfig { it.copy(viperDdc = it.viperDdc.copy(enabled = en)) }
                                    },
                                    enabled = viperConfig.masterEnabled
                                )
                            },
                            onClick = {
                                if (viperConfig.masterEnabled) {
                                    viperRepo.updateConfig { it.copy(viperDdc = it.viperDdc.copy(enabled = !it.viperDdc.enabled)) }
                                }
                            }
                        )
                    )
                    if (viperConfig.masterEnabled && viperConfig.viperDdc.enabled) {
                        val selectedDdcIdx = ddcDevices.indexOf(viperConfig.viperDdc.deviceName).let { if (it < 0) 0 else it.coerceAtMost(4) }
                        add(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.viper_ddc_headphone)) },
                                description = {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ExpressiveButtonGroup(
                                            items = ddcDeviceLabels,
                                            selectedIndex = selectedDdcIdx,
                                            onItemClick = { index ->
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                viperRepo.updateConfig {
                                                    it.copy(viperDdc = it.viperDdc.copy(deviceName = ddcDevices[index]))
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.viper_ddc_applied, viperConfig.viperDdc.deviceName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        )
                    }
                }
                Material3SettingsGroup(
                    title = "Headphone Correction",
                    items = ddcItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val spatialItems = buildList {
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("surround_sound", filled = true),
                            title = { Text("Field Surround") },
                            description = { Text("Multi-dimensional soundstage widening") },
                            enabled = viperConfig.masterEnabled,
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = viperConfig.fieldSurround.enabled,
                                    onCheckedChange = { en ->
                                        viperRepo.updateConfig { it.copy(fieldSurround = it.fieldSurround.copy(enabled = en)) }
                                    },
                                    enabled = viperConfig.masterEnabled
                                )
                            },
                            onClick = {
                                if (viperConfig.masterEnabled) {
                                    viperRepo.updateConfig { it.copy(fieldSurround = it.fieldSurround.copy(enabled = !it.fieldSurround.enabled)) }
                                }
                            }
                        )
                    )
                    if (viperConfig.masterEnabled && viperConfig.fieldSurround.enabled) {
                        add(
                            Material3SettingsItem(
                                title = { Text("Field Surround Parameters") },
                                description = {
                                    Column {
                                        ViperSliderRow(
                                            label = "Soundstage Widening",
                                            valueText = String.format(Locale.US, "%d%%", (viperConfig.fieldSurround.widening * 100).toInt()),
                                            value = viperConfig.fieldSurround.widening * 100f,
                                            valueRange = 0.0f..100.0f,
                                            onValueChange = { v ->
                                                viperRepo.updateConfig { it.copy(fieldSurround = it.fieldSurround.copy(widening = v / 100f)) }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ViperSliderRow(
                                            label = "Mid-Image Focus",
                                            valueText = String.format(Locale.US, "%.1fx", viperConfig.fieldSurround.midImage),
                                            value = viperConfig.fieldSurround.midImage,
                                            valueRange = 0.5f..2.0f,
                                            onValueChange = { v ->
                                                viperRepo.updateConfig { it.copy(fieldSurround = it.fieldSurround.copy(midImage = v)) }
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    }

                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("settings_input_component", filled = true),
                            title = { Text(stringResource(R.string.viper_convolver_title)) },
                            description = { Text(stringResource(R.string.viper_convolver_desc)) },
                            enabled = viperConfig.masterEnabled,
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = viperConfig.convolver.enabled,
                                    onCheckedChange = { en ->
                                        viperRepo.updateConfig { it.copy(convolver = it.convolver.copy(enabled = en)) }
                                    },
                                    enabled = viperConfig.masterEnabled
                                )
                            },
                            onClick = {
                                if (viperConfig.masterEnabled) {
                                    viperRepo.updateConfig { it.copy(convolver = it.convolver.copy(enabled = !it.convolver.enabled)) }
                                }
                            }
                        )
                    )
                    if (viperConfig.masterEnabled && viperConfig.convolver.enabled) {
                        val convIdx = convolverPresets.indexOf(viperConfig.convolver.kernelName).let { if (it < 0) 0 else it }
                        add(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.viper_convolver_preset)) },
                                description = {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ExpressiveButtonGroup(
                                            items = convolverPresets,
                                            selectedIndex = convIdx,
                                            onItemClick = { index ->
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                viperRepo.updateConfig { it.copy(convolver = it.convolver.copy(kernelName = convolverPresets[index])) }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        ViperSliderRow(
                                            label = stringResource(R.string.viper_convolver_cross_channel),
                                            valueText = String.format(Locale.US, "%d%%", (viperConfig.convolver.crossChannel * 100).toInt()),
                                            value = viperConfig.convolver.crossChannel * 100f,
                                            valueRange = 0f..100f,
                                            onValueChange = { value ->
                                                viperRepo.updateConfig { it.copy(convolver = it.convolver.copy(crossChannel = value / 100f)) }
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    }

                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("blur_on", filled = true),
                            title = { Text("Reverberation") },
                            description = { Text("Algorithmic acoustic reverb") },
                            enabled = viperConfig.masterEnabled,
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = viperConfig.reverberation.enabled,
                                    onCheckedChange = { en ->
                                        viperRepo.updateConfig { it.copy(reverberation = it.reverberation.copy(enabled = en)) }
                                    },
                                    enabled = viperConfig.masterEnabled
                                )
                            },
                            onClick = {
                                if (viperConfig.masterEnabled) {
                                    viperRepo.updateConfig { it.copy(reverberation = it.reverberation.copy(enabled = !it.reverberation.enabled)) }
                                }
                            }
                        )
                    )
                    if (viperConfig.masterEnabled && viperConfig.reverberation.enabled) {
                        add(
                            Material3SettingsItem(
                                title = { Text("Reverb Parameters") },
                                description = {
                                    Column {
                                        ViperSliderRow(
                                            label = "Room Size",
                                            valueText = String.format(Locale.US, "%d%%", (viperConfig.reverberation.roomSize * 100).toInt()),
                                            value = viperConfig.reverberation.roomSize * 100f,
                                            valueRange = 0.0f..100.0f,
                                            onValueChange = { v ->
                                                viperRepo.updateConfig { it.copy(reverberation = it.reverberation.copy(roomSize = v / 100f)) }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ViperSliderRow(
                                            label = "Wet Signal",
                                            valueText = String.format(Locale.US, "%d%%", (viperConfig.reverberation.wet * 100).toInt()),
                                            value = viperConfig.reverberation.wet * 100f,
                                            valueRange = 0.0f..50.0f,
                                            onValueChange = { v ->
                                                viperRepo.updateConfig { it.copy(reverberation = it.reverberation.copy(wet = v / 100f)) }
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    }

                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("headphones", filled = true),
                            title = { Text("Headphone Surround+") },
                            description = { Text("Binaural headphone spatial virtualizer") },
                            enabled = viperConfig.masterEnabled,
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = viperConfig.headphoneSurround.enabled,
                                    onCheckedChange = { en ->
                                        viperRepo.updateConfig { it.copy(headphoneSurround = it.headphoneSurround.copy(enabled = en)) }
                                    },
                                    enabled = viperConfig.masterEnabled
                                )
                            },
                            onClick = {
                                if (viperConfig.masterEnabled) {
                                    viperRepo.updateConfig { it.copy(headphoneSurround = it.headphoneSurround.copy(enabled = !it.headphoneSurround.enabled)) }
                                }
                            }
                        )
                    )
                    if (viperConfig.masterEnabled && viperConfig.headphoneSurround.enabled) {
                        add(
                            Material3SettingsItem(
                                title = { Text("Surround Quality") },
                                description = {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ExpressiveButtonGroup(
                                            items = surroundQualities,
                                            selectedIndex = viperConfig.headphoneSurround.quality.coerceIn(0, 1),
                                            onItemClick = { idx ->
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                viperRepo.updateConfig { it.copy(headphoneSurround = it.headphoneSurround.copy(quality = idx)) }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        )
                    }

                    if (viperConfig.uiMode == ViperUiMode.EXPERT) {
                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("spatial_audio", filled = true),
                                title = { Text("Differential Surround") },
                                description = { Text("Haas-effect phase delay") },
                                enabled = viperConfig.masterEnabled,
                                trailingContent = {
                                    TunerAnimatedSwitch(
                                        checked = viperConfig.diffSurround.enabled,
                                        onCheckedChange = { en ->
                                            viperRepo.updateConfig { it.copy(diffSurround = it.diffSurround.copy(enabled = en)) }
                                        },
                                        enabled = viperConfig.masterEnabled
                                    )
                                },
                                onClick = {
                                    if (viperConfig.masterEnabled) {
                                        viperRepo.updateConfig { it.copy(diffSurround = it.diffSurround.copy(enabled = !it.diffSurround.enabled)) }
                                    }
                                }
                            )
                        )
                        if (viperConfig.masterEnabled && viperConfig.diffSurround.enabled) {
                            add(
                                Material3SettingsItem(
                                    title = { Text("Haas Delay Parameters") },
                                    description = {
                                        Column {
                                            ViperSliderRow(
                                                label = "Surround Delay",
                                                valueText = String.format(Locale.US, "%.1f ms", viperConfig.diffSurround.delayMs),
                                                value = viperConfig.diffSurround.delayMs,
                                                valueRange = 1.0f..20.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(diffSurround = it.diffSurround.copy(delayMs = v)) }
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ViperSliderRow(
                                                label = "Wet / Dry Mix",
                                                valueText = String.format(Locale.US, "%d%%", (viperConfig.diffSurround.wetDryMix * 100).toInt()),
                                                value = viperConfig.diffSurround.wetDryMix * 100f,
                                                valueRange = 0.0f..100.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(diffSurround = it.diffSurround.copy(wetDryMix = v / 100f)) }
                                                }
                                            )
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
                Material3SettingsGroup(
                    title = "Soundstage & Spatial",
                    items = spatialItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                val dynItems = buildList {
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("volume_up", filled = true),
                            title = { Text("AGC (Playback Gain)") },
                            description = { Text("Dynamic auto gain leveler") },
                            enabled = viperConfig.masterEnabled,
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = viperConfig.playbackGain.enabled,
                                    onCheckedChange = { en ->
                                        viperRepo.updateConfig { it.copy(playbackGain = it.playbackGain.copy(enabled = en)) }
                                    },
                                    enabled = viperConfig.masterEnabled
                                )
                            },
                            onClick = {
                                if (viperConfig.masterEnabled) {
                                    viperRepo.updateConfig { it.copy(playbackGain = it.playbackGain.copy(enabled = !it.playbackGain.enabled)) }
                                }
                            }
                        )
                    )
                    if (viperConfig.masterEnabled && viperConfig.playbackGain.enabled) {
                        add(
                            Material3SettingsItem(
                                title = { Text("AGC Configuration") },
                                description = {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ExpressiveButtonGroup(
                                            items = agcStrengths,
                                            selectedIndex = (viperConfig.playbackGain.strength - 1).coerceIn(0, 2),
                                            onItemClick = { idx ->
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                viperRepo.updateConfig { it.copy(playbackGain = it.playbackGain.copy(strength = idx + 1)) }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        ViperSliderRow(
                                            label = "Max Gain",
                                            valueText = String.format(Locale.US, "+%.1f dB", viperConfig.playbackGain.maxGainDb),
                                            value = viperConfig.playbackGain.maxGainDb,
                                            valueRange = 0.0f..18.0f,
                                            onValueChange = { v ->
                                                viperRepo.updateConfig { it.copy(playbackGain = it.playbackGain.copy(maxGainDb = v)) }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ViperSliderRow(
                                            label = "Output Threshold",
                                            valueText = String.format(Locale.US, "%.1f dB", viperConfig.playbackGain.outputThresholdDb),
                                            value = viperConfig.playbackGain.outputThresholdDb,
                                            valueRange = -6.0f..0.0f,
                                            onValueChange = { v ->
                                                viperRepo.updateConfig { it.copy(playbackGain = it.playbackGain.copy(outputThresholdDb = v)) }
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    }

                    if (viperConfig.uiMode == ViperUiMode.EXPERT) {
                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("compress", filled = true),
                                title = { Text("FET Compressor") },
                                description = { Text("Vintage fast analog dynamics & punch") },
                                enabled = viperConfig.masterEnabled,
                                trailingContent = {
                                    TunerAnimatedSwitch(
                                        checked = viperConfig.fetCompressor.enabled,
                                        onCheckedChange = { en ->
                                            viperRepo.updateConfig { it.copy(fetCompressor = it.fetCompressor.copy(enabled = en)) }
                                        },
                                        enabled = viperConfig.masterEnabled
                                    )
                                },
                                onClick = {
                                    if (viperConfig.masterEnabled) {
                                        viperRepo.updateConfig { it.copy(fetCompressor = it.fetCompressor.copy(enabled = !it.fetCompressor.enabled)) }
                                    }
                                }
                            )
                        )
                        if (viperConfig.masterEnabled && viperConfig.fetCompressor.enabled) {
                            add(
                                Material3SettingsItem(
                                    title = { Text("Compressor Parameters") },
                                    description = {
                                        Column {
                                            ViperSliderRow(
                                                label = "Threshold",
                                                valueText = String.format(Locale.US, "%.1f dB", viperConfig.fetCompressor.thresholdDb),
                                                value = viperConfig.fetCompressor.thresholdDb,
                                                valueRange = -40.0f..0.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(fetCompressor = it.fetCompressor.copy(thresholdDb = v)) }
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ViperSliderRow(
                                                label = "Ratio",
                                                valueText = String.format(Locale.US, "%.1f:1", viperConfig.fetCompressor.ratio),
                                                value = viperConfig.fetCompressor.ratio,
                                                valueRange = 1.0f..10.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(fetCompressor = it.fetCompressor.copy(ratio = v)) }
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ViperSliderRow(
                                                label = "Attack Time",
                                                valueText = String.format(Locale.US, "%.1f ms", viperConfig.fetCompressor.attackMs),
                                                value = viperConfig.fetCompressor.attackMs,
                                                valueRange = 0.5f..50.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(fetCompressor = it.fetCompressor.copy(attackMs = v)) }
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ViperSliderRow(
                                                label = "Release Time",
                                                valueText = String.format(Locale.US, "%.0f ms", viperConfig.fetCompressor.releaseMs),
                                                value = viperConfig.fetCompressor.releaseMs,
                                                valueRange = 10.0f..400.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(fetCompressor = it.fetCompressor.copy(releaseMs = v)) }
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            ViperSliderRow(
                                                label = "Makeup Gain",
                                                valueText = String.format(Locale.US, "+%.1f dB", viperConfig.fetCompressor.makeupGainDb),
                                                value = viperConfig.fetCompressor.makeupGainDb,
                                                valueRange = 0.0f..12.0f,
                                                onValueChange = { v ->
                                                    viperRepo.updateConfig { it.copy(fetCompressor = it.fetCompressor.copy(makeupGainDb = v)) }
                                                }
                                            )
                                        }
                                    }
                                )
                            )
                        }
                    }
                }
                Material3SettingsGroup(
                    title = "Dynamics & Loudness",
                    items = dynItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                if (isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                if (showImportField) {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text(stringResource(R.string.viper_paste_json)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                val backupItems = buildList {
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("save", filled = true),
                            title = { Text(context.getString(R.string.viper_save_to_file)) },
                            description = { Text(context.getString(R.string.viper_save_to_file_desc)) },
                            enabled = !isBusy,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                exportToFileLauncher.launch("viper-profile.json")
                            }
                        )
                    )
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("file_open", filled = true),
                            title = { Text(context.getString(R.string.viper_load_from_file)) },
                            description = { Text(context.getString(R.string.viper_load_from_file_desc)) },
                            enabled = !isBusy,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                importFromFileLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    )
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("content_copy", filled = true),
                            title = { Text(context.getString(R.string.viper_export_profile)) },
                            description = { Text(context.getString(R.string.viper_export_profile_desc)) },
                            enabled = !isBusy,
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    val json = withContext(Dispatchers.IO) { viperRepo.exportProfileJson() }
                                    clipboard.setPrimaryClip(ClipData.newPlainText("ViPER Profile", json))
                                    Toast.makeText(context, context.getString(R.string.viper_profile_copied), Toast.LENGTH_SHORT).show()
                                    isBusy = false
                                }
                            }
                        )
                    )
                    add(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("content_paste", filled = true),
                            title = {
                                Text(
                                    if (showImportField) {
                                        context.getString(R.string.viper_hide_import)
                                    } else {
                                        context.getString(R.string.viper_import_profile)
                                    }
                                )
                            },
                            description = { Text(context.getString(R.string.viper_import_profile_desc)) },
                            enabled = !isBusy,
                            onClick = { showImportField = !showImportField }
                        )
                    )
                    if (showImportField) {
                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("check", filled = true),
                                title = { Text(context.getString(R.string.viper_apply_imported)) },
                                enabled = !isBusy && importText.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        isBusy = true
                                        val imported = withContext(Dispatchers.IO) {
                                            viperRepo.importProfileJson(importText)
                                        }
                                        isBusy = false
                                        if (imported) {
                                            Toast.makeText(context, context.getString(R.string.viper_profile_imported_success), Toast.LENGTH_SHORT).show()
                                            importText = ""
                                            showImportField = false
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.viper_profile_invalid_format), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        )
                    }
                }

                Material3SettingsGroup(
                    title = context.getString(R.string.viper_backup_sharing),
                    items = backupItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }
        }
    }

    if (openSheet == ViperSheet.ENGINE_STATUS) {
        val telemetry by ViperAudioProcessor.globalTelemetry.collectAsState()
        ViperEngineStatusSheet(
            telemetry = telemetry,
            isMasterEnabled = viperConfig.masterEnabled,
            onDismiss = { openSheet = null }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Icon(
                    imageVector = MaterialSymbolIcon("restart_alt", filled = true),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.bottomsheet_reset),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.viper_reset_confirm_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        viperRepo.resetConfig()
                        selectedPreset = null
                        Toast.makeText(
                            context,
                            context.getString(R.string.viper_reset_done),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("restart_alt", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bottomsheet_reset))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetDialog = false },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("close", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}

private fun applyViperPreset(repository: ViperSettingsRepository, key: String) {
    repository.updateConfig { config ->
        when (key) {
            "audiophile" -> config.copy(
                masterEnabled = true,
                fireEqualizer = FireEqualizerConfig(
                    enabled = true,
                    presetName = "Acoustic",
                    bands = FireEqualizerConfig.BUILTIN_PRESETS["Acoustic"] ?: List(10) { 0.0f }
                ),
                viperClarity = ViperClarityConfig(enabled = true, mode = 1, gainDb = 2.5f),
                cureCrossfeed = CureCrossfeedConfig(enabled = true, preset = 1),
                masterGate = MasterGateConfig(limiterEnabled = true)
            )

            "basshead" -> config.copy(
                masterEnabled = true,
                fireEqualizer = FireEqualizerConfig(
                    enabled = true,
                    presetName = "Bass Booster",
                    bands = FireEqualizerConfig.BUILTIN_PRESETS["Bass Booster"] ?: List(10) { 0.0f }
                ),
                viperBass = ViperBassConfig(enabled = true, mode = 2, frequencyHz = 55, gainDb = 8.5f),
                psychoacousticBass = PsychoacousticBassConfig(enabled = true, cutoffHz = 75, intensity = 0.6f),
                bassMono = BassMonoConfig(enabled = true, frequencyHz = 85),
                masterGate = MasterGateConfig(limiterEnabled = true)
            )

            "tube" -> config.copy(
                masterEnabled = true,
                tubeSimulator = TubeSimulatorConfig(enabled = true, model = 0, drive = 0.6f, mix = 0.45f),
                analogX = AnalogXConfig(enabled = true, mode = 2),
                convolver = ConvolverConfig(enabled = true, kernelName = "Warm Tube"),
                masterGate = MasterGateConfig(limiterEnabled = true)
            )

            "stadium" -> config.copy(
                masterEnabled = true,
                fieldSurround = FieldSurroundConfig(enabled = true, widening = 0.65f),
                diffSurround = DiffSurroundConfig(enabled = true, delayMs = 9.0f, wetDryMix = 0.45f),
                reverberation = ReverberationConfig(enabled = true, roomSize = 0.55f, wet = 0.22f),
                masterGate = MasterGateConfig(limiterEnabled = true)
            )

            else -> config
        }
    }
}

@Composable
private fun ViperSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        val sliderState = remember(valueRange) {
            SliderState(
                value = value,
                trackRange = valueRange
            )
        }
        sliderState.value = value
        Slider(
            state = sliderState,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
