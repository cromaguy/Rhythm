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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
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
import chromahub.rhythm.app.infrastructure.audio.viper.ViperSettingsRepository
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.horizontalEdgeBlend
import chromahub.rhythm.app.shared.presentation.components.common.SmallTabAnimation
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
private val convolverPresets = listOf("Studio Master", "Warm Tube", "Analog Tape", "Acoustic Room")

/**
 * ViPER4Android DSP settings.
 *
 * Reachable from Labs, the equalizer overflow menu, and settings search. Mirrors the
 * theme settings screen: the master switch, an equalizer-style preset chip row, effect
 * toggles whose options open inline in the same settings group, and the profile
 * import/export section at the bottom.
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

    var openSheet by remember { mutableStateOf<ViperSheet?>(null) }
    var selectedPreset by remember { mutableStateOf<String?>(null) }
    var showImportField by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
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
        val effectsGroup = SettingGroup(
            title = context.getString(R.string.viper_effects_group),
            items = listOf(
                SettingItem(
                    icon = MaterialSymbolIcon("waves", filled = true),
                    title = context.getString(R.string.viper_clarity_title),
                    description = context.getString(R.string.viper_clarity_desc),
                    enabled = viperConfig.masterEnabled,
                    toggleState = viperConfig.viperClarity.enabled,
                    onToggleChange = { enabled ->
                        viperRepo.updateConfig { it.copy(viperClarity = it.viperClarity.copy(enabled = enabled)) }
                    }
                ),
                SettingItem(
                    icon = MaterialSymbolIcon("headphones", filled = true),
                    title = context.getString(R.string.viper_ddc_title),
                    description = context.getString(R.string.viper_ddc_desc),
                    enabled = viperConfig.masterEnabled,
                    toggleState = viperConfig.viperDdc.enabled,
                    onToggleChange = { enabled ->
                        viperRepo.updateConfig { it.copy(viperDdc = it.viperDdc.copy(enabled = enabled)) }
                    }
                ),
                SettingItem(
                    icon = MaterialSymbolIcon("settings_input_component", filled = true),
                    title = context.getString(R.string.viper_convolver_title),
                    description = context.getString(R.string.viper_convolver_desc),
                    enabled = viperConfig.masterEnabled,
                    toggleState = viperConfig.convolver.enabled,
                    onToggleChange = { enabled ->
                        viperRepo.updateConfig { it.copy(convolver = it.convolver.copy(enabled = enabled)) }
                    }
                )
            )
        )

        val engineGroup = SettingGroup(
            title = context.getString(R.string.viper_engine_group),
            items = listOf(
                SettingItem(
                    icon = MaterialSymbolIcon("insights", filled = true),
                    title = context.getString(R.string.viper_engine_status_title),
                    description = context.getString(R.string.viper_engine_status_subtitle),
                    onClick = { openSheet = ViperSheet.ENGINE_STATUS }
                )
            )
        )

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
                val effectItems = buildList {
                    addAll(effectsGroup.items.map { toMaterial3SettingsItem(context = context, item = it) })

                    if (viperConfig.masterEnabled && viperConfig.viperClarity.enabled) {
                        add(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.viper_clarity_mode)) },
                                description = {
                                    Column {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        ExpressiveButtonGroup(
                                            items = clarityModes,
                                            selectedIndex = viperConfig.viperClarity.mode.coerceIn(0, clarityModes.size - 1),
                                            onItemClick = { index ->
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                viperRepo.updateConfig {
                                                    it.copy(viperClarity = it.viperClarity.copy(mode = index))
                                                }
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
                                                viperRepo.updateConfig {
                                                    it.copy(viperClarity = it.viperClarity.copy(gainDb = value))
                                                }
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    }

                    if (viperConfig.masterEnabled && viperConfig.viperDdc.enabled) {
                        add(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.viper_ddc_headphone)) },
                                description = {
                                    Column {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        ExpressiveButtonGroup(
                                            items = listOf("None", "AirPods", "XM4", "HD 650", "M50x"),
                                            selectedIndex = ddcDevices.indexOf(viperConfig.viperDdc.deviceName)
                                                .let { if (it < 0) 0 else it.coerceAtMost(4) },
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

                    if (viperConfig.masterEnabled && viperConfig.convolver.enabled) {
                        add(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.viper_convolver_preset)) },
                                description = {
                                    Column {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        ExpressiveButtonGroup(
                                            items = convolverPresets,
                                            selectedIndex = convolverPresets.indexOf(viperConfig.convolver.kernelName)
                                                .let { if (it < 0) 0 else it },
                                            onItemClick = { index ->
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                viperRepo.updateConfig {
                                                    it.copy(convolver = it.convolver.copy(kernelName = convolverPresets[index]))
                                                }
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
                                                viperRepo.updateConfig {
                                                    it.copy(convolver = it.convolver.copy(crossChannel = value / 100f))
                                                }
                                            }
                                        )
                                    }
                                }
                            )
                        )
                    }
                }

                Material3SettingsGroup(
                    title = effectsGroup.title,
                    items = effectItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Material3SettingsGroup(
                    title = engineGroup.title,
                    items = engineGroup.items.map { toMaterial3SettingsItem(context = context, item = it) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))

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
                        SettingItem(
                            icon = MaterialSymbolIcon("save", filled = true),
                            title = context.getString(R.string.viper_save_to_file),
                            description = context.getString(R.string.viper_save_to_file_desc),
                            enabled = !isBusy,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                exportToFileLauncher.launch("viper-profile.json")
                            }
                        )
                    )
                    add(
                        SettingItem(
                            icon = MaterialSymbolIcon("file_open", filled = true),
                            title = context.getString(R.string.viper_load_from_file),
                            description = context.getString(R.string.viper_load_from_file_desc),
                            enabled = !isBusy,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                importFromFileLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    )
                    add(
                        SettingItem(
                            icon = MaterialSymbolIcon("content_copy", filled = true),
                            title = context.getString(R.string.viper_export_profile),
                            description = context.getString(R.string.viper_export_profile_desc),
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
                        SettingItem(
                            icon = MaterialSymbolIcon("content_paste", filled = true),
                            title = if (showImportField) {
                                context.getString(R.string.viper_hide_import)
                            } else {
                                context.getString(R.string.viper_import_profile)
                            },
                            description = context.getString(R.string.viper_import_profile_desc),
                            enabled = !isBusy,
                            onClick = { showImportField = !showImportField }
                        )
                    )
                    if (showImportField) {
                        add(
                            SettingItem(
                                icon = MaterialSymbolIcon("check", filled = true),
                                title = context.getString(R.string.viper_apply_imported),
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
                    items = backupItems.map { toMaterial3SettingsItem(context = context, item = it) },
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
}

private fun applyViperPreset(repository: ViperSettingsRepository, key: String) {
    repository.updateConfig { config ->
        when (key) {
            "audiophile" -> config.copy(
                masterEnabled = true,
                fireEqualizer = FireEqualizerConfig(enabled = true, presetName = "Acoustic"),
                viperClarity = ViperClarityConfig(enabled = true, mode = 1, gainDb = 2.5f),
                cureCrossfeed = CureCrossfeedConfig(enabled = true, preset = 1),
                masterGate = MasterGateConfig(limiterEnabled = true)
            )

            "basshead" -> config.copy(
                masterEnabled = true,
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
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
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
