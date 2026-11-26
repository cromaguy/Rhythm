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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.presentation.screens.equalizer.ViperEngineStatusSheet
import chromahub.rhythm.app.infrastructure.audio.viper.FireEqualizerConfig
import chromahub.rhythm.app.infrastructure.audio.viper.ViperAudioProcessor
import chromahub.rhythm.app.infrastructure.audio.viper.ViperDdcConfig
import chromahub.rhythm.app.infrastructure.audio.viper.ViperMasterConfig
import chromahub.rhythm.app.infrastructure.audio.viper.ViperOutputDevice
import chromahub.rhythm.app.infrastructure.audio.viper.ViperSettingsRepository
import chromahub.rhythm.app.infrastructure.audio.viper.ViperUiMode
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.CookieHorizontalSlider
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.HeaderAction
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedMenuContent
import chromahub.rhythm.app.shared.presentation.components.common.RhythmMenuItem
import chromahub.rhythm.app.shared.presentation.components.common.SmallTabAnimation
import chromahub.rhythm.app.shared.presentation.components.common.horizontalEdgeBlend
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.viper.ViperSegmentedRow
import chromahub.rhythm.app.shared.presentation.components.viper.ViperSliderRow
import chromahub.rhythm.app.shared.presentation.components.viper.formatDb
import chromahub.rhythm.app.shared.presentation.components.viper.formatHz
import chromahub.rhythm.app.shared.presentation.components.viper.formatPercent
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.windowScreenWidthDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class ViperSheet { ENGINE_STATUS, PROFILE_IO }

private data class ViperPreset(
    val key: String,
    @StringRes val titleRes: Int,
    val icon: MaterialSymbolIcon
)

private data class ViperSection(
    @StringRes val titleRes: Int,
    val items: List<Material3SettingsItem>
)

private val viperPresets = listOf(
    ViperPreset("audiophile", R.string.viper_preset_audiophile, MaterialSymbolIcon("diamond", filled = true)),
    ViperPreset("basshead", R.string.viper_preset_basshead, MaterialSymbolIcon("bass_boost", filled = true)),
    ViperPreset("tube", R.string.viper_preset_tube, MaterialSymbolIcon("thermostat", filled = true)),
    ViperPreset("stadium", R.string.viper_preset_stadium, MaterialSymbolIcon("theater_comedy", filled = true))
)

private val outputDeviceLabels = listOf("Speaker", "Headset", "Bluetooth", "USB")
private val uiModeLabels = listOf("Simple", "Moderate", "Expert")
private val eqPresetLabels = listOf("Flat", "Rock", "Acoustic", "Bass", "R&B")
private val eqPresetKeys = listOf("Flat", "Rock", "Acoustic", "Bass Booster", "R&B")
private val convolverPresets = listOf("Studio Master", "Warm Tube", "Analog Tape", "Acoustic Room")
private val ddcDeviceLabels = listOf(
    "None", "AirPods Pro", "AirPods Max", "Sony XM4", "Sony XM5", "HD 650",
    "M50x", "DT 770", "DT 990", "Buds Pro", "Aria", "ZSN Pro X"
)

/**
 * ViPER4Android DSP settings.
 *
 * Uses Rhythm's standard settings items and groups, split into signal-chain sections. The
 * Simple / Moderate / Expert interface mode decides which effects are exposed.
 */
@Composable
fun ViperSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isTablet = windowScreenWidthDp() >= 600

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

    val profileSheetScrollState = rememberScrollState()

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
                } catch (_: Exception) {
                    Toast.makeText(context, context.getString(R.string.viper_profile_invalid_format), Toast.LENGTH_SHORT).show()
                } finally {
                    isBusy = false
                }
            }
        }
    }

    val customIrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                val parsed = withContext(Dispatchers.IO) {
                    chromahub.rhythm.app.infrastructure.audio.viper.dsp.WavIrParser.loadFromUri(context, uri)
                }
                if (parsed != null) {
                    viperRepo.updateConfig {
                        it.copy(
                            convolver = it.convolver.copy(
                                enabled = true,
                                irUri = uri.toString(),
                                kernelName = chromahub.rhythm.app.infrastructure.audio.viper.dsp.FastConvolver.PRESET_CUSTOM_IR
                            )
                        )
                    }
                    Toast.makeText(context, "Loaded custom IR (${parsed.left.size} taps @ ${parsed.sampleRate} Hz)", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Unsupported or corrupted WAV/IRS impulse file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val masterEnabled = viperConfig.masterEnabled
    val uiMode = viperConfig.uiMode
    val onUpdate: ((ViperMasterConfig) -> ViperMasterConfig) -> Unit = { transform ->
        viperRepo.updateConfig(transform)
    }

    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showContent = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "viperContentAlpha"
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (showContent) 0f else 30f,
        animationSpec = tween(durationMillis = 450),
        label = "viperContentOffset"
    )

    val presetsContent: @Composable () -> Unit = {
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

    val outputItems = listOf(
        Material3SettingsItem(
            icon = MaterialSymbolIcon("device_hub"),
            title = { Text(stringResource(R.string.viper_output_device_title)) },
            description = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExpressiveButtonGroup(
                        items = outputDeviceLabels,
                        selectedIndex = when (viperConfig.activeOutputDevice) {
                            ViperOutputDevice.SPEAKER -> 0
                            ViperOutputDevice.HEADSET -> 1
                            ViperOutputDevice.BLUETOOTH -> 2
                            ViperOutputDevice.USB -> 3
                        },
                        onItemClick = { index ->
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            viperRepo.switchDeviceProfile(
                                when (index) {
                                    1 -> ViperOutputDevice.HEADSET
                                    2 -> ViperOutputDevice.BLUETOOTH
                                    3 -> ViperOutputDevice.USB
                                    else -> ViperOutputDevice.SPEAKER
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        ),
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
        ),
        Material3SettingsItem(
            icon = MaterialSymbolIcon("tune"),
            title = { Text(stringResource(R.string.viper_ui_complexity)) },
            description = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExpressiveButtonGroup(
                        items = uiModeLabels,
                        selectedIndex = when (uiMode) {
                            ViperUiMode.SIMPLE -> 0
                            ViperUiMode.MODERATE -> 1
                            ViperUiMode.EXPERT -> 2
                        },
                        onItemClick = { index ->
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            viperRepo.setUiMode(
                                when (index) {
                                    0 -> ViperUiMode.SIMPLE
                                    2 -> ViperUiMode.EXPERT
                                    else -> ViperUiMode.MODERATE
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    )

    val fireEqualizer = viperConfig.fireEqualizer
    val toneItems = buildList {
        add(
            viperEffectItem(
                icon = MaterialSymbolIcon("equalizer", filled = true),
                title = "FireEqualizer",
                description = "10-band graphic tone equalizer",
                effectEnabled = fireEqualizer.enabled,
                available = masterEnabled,
                onToggle = { enabled ->
                    onUpdate { it.copy(fireEqualizer = it.fireEqualizer.copy(enabled = enabled)) }
                }
            )
        )
        if (masterEnabled && fireEqualizer.enabled) {
            val currentPreset = if (fireEqualizer.presetName == "Bass") "Bass Booster" else fireEqualizer.presetName
            add(
                viperParamItem(title = "Equalizer preset") {
                    ExpressiveButtonGroup(
                        items = eqPresetLabels,
                        selectedIndex = if (fireEqualizer.presetName == "Custom") {
                            -1
                        } else {
                            eqPresetKeys.indexOf(currentPreset).coerceAtLeast(0)
                        },
                        onItemClick = { index ->
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            val name = eqPresetKeys[index]
                            onUpdate {
                                it.copy(
                                    fireEqualizer = it.fireEqualizer.copy(
                                        presetName = name,
                                        bands = FireEqualizerConfig.BUILTIN_PRESETS[name] ?: List(10) { 0.0f }
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
            add(
                viperParamItem(title = "Tone frequencies") {
                    val frequencies = FireEqualizerConfig.FREQUENCIES_10
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (index in 0 until minOf(frequencies.size, fireEqualizer.bands.size)) {
                            val label = if (frequencies[index] >= 1000) {
                                "${frequencies[index] / 1000}k"
                            } else {
                                "${frequencies[index]}"
                            }
                            val bandColor = when (index) {
                                0, 1 -> MaterialTheme.colorScheme.secondary
                                in 2..7 -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                            val bandThumbColor = when (index) {
                                0, 1 -> MaterialTheme.colorScheme.onSecondary
                                in 2..7 -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onTertiary
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(48.dp)
                                )
                                CookieHorizontalSlider(
                                    value = fireEqualizer.bands[index],
                                    onValueChange = { level ->
                                        val updated = fireEqualizer.bands.toMutableList()
                                        updated[index] = level
                                        onUpdate {
                                            it.copy(
                                                fireEqualizer = it.fireEqualizer.copy(bands = updated, presetName = "Custom")
                                            )
                                        }
                                    },
                                    valueRange = -12.0f..12.0f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp),
                                    activeTrackColor = bandColor,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    thumbColor = bandThumbColor
                                )
                                Text(
                                    text = formatDb(fireEqualizer.bands[index]),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = bandColor,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(56.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    }

    val viperBass = viperConfig.viperBass
    val bassItems = buildList {
        add(
            viperEffectItem(
                icon = MaterialSymbolIcon("speaker", filled = true),
                title = "ViPER Bass",
                description = "Natural, pure and subwoofer low-end",
                effectEnabled = viperBass.enabled,
                available = masterEnabled,
                onToggle = { enabled ->
                    onUpdate { it.copy(viperBass = it.viperBass.copy(enabled = enabled)) }
                }
            )
        )
        if (masterEnabled && viperBass.enabled) {
            add(
                viperParamItem(title = "Bass tuning") {
                    ViperSegmentedRow(
                        label = "Bass mode",
                        options = listOf("Natural", "Pure Bass", "Subwoofer"),
                        selectedIndex = viperBass.mode.coerceIn(0, 2),
                        onSelectIndex = { mode ->
                            onUpdate { it.copy(viperBass = it.viperBass.copy(mode = mode)) }
                        }
                    )
                    ViperSliderRow(
                        label = "Bass frequency",
                        value = viperBass.frequencyHz.toFloat(),
                        valueRange = 30.0f..200.0f,
                        valueFormatted = formatHz(viperBass.frequencyHz),
                        onValueChange = { value ->
                            onUpdate { it.copy(viperBass = it.viperBass.copy(frequencyHz = value.toInt())) }
                        }
                    )
                    ViperSliderRow(
                        label = "Bass gain",
                        value = viperBass.gainDb,
                        valueRange = 0.0f..18.0f,
                        valueFormatted = formatDb(viperBass.gainDb),
                        onValueChange = { value ->
                            onUpdate { it.copy(viperBass = it.viperBass.copy(gainDb = value)) }
                        }
                    )
                }
            )
        }

        if (uiMode != ViperUiMode.SIMPLE) {
            val dynamicSystem = viperConfig.dynamicSystem
            add(
                viperEffectItem(
                    icon = MaterialSymbolIcon("graphic_eq", filled = true),
                    title = "Dynamic System",
                    description = "Acoustic transducer & driver emulation",
                    effectEnabled = dynamicSystem.enabled,
                    available = masterEnabled,
                    onToggle = { enabled ->
                        onUpdate { it.copy(dynamicSystem = it.dynamicSystem.copy(enabled = enabled)) }
                    }
                )
            )
            if (masterEnabled && dynamicSystem.enabled) {
                add(
                    viperParamItem(title = "Transducer") {
                        ViperSegmentedRow(
                            label = "Transducer preset",
                            options = listOf("Earphone", "Hi-End", "EarPods", "Extreme", "Speaker"),
                            selectedIndex = dynamicSystem.device.coerceIn(0, 4),
                            onSelectIndex = { device ->
                                onUpdate { it.copy(dynamicSystem = it.dynamicSystem.copy(device = device)) }
                            }
                        )
                        ViperSliderRow(
                            label = "Effect strength",
                            value = dynamicSystem.strength * 100f,
                            valueRange = 0.0f..100.0f,
                            valueFormatted = formatPercent(dynamicSystem.strength),
                            onValueChange = { value ->
                                onUpdate { it.copy(dynamicSystem = it.dynamicSystem.copy(strength = value / 100f)) }
                            }
                        )
                    }
                )
            }
        }

        if (uiMode == ViperUiMode.EXPERT) {
            val bassMono = viperConfig.bassMono
            add(
                viperEffectItem(
                    icon = MaterialSymbolIcon("music_note", filled = true),
                    title = "Bass Mono",
                    description = "Center low-end frequencies for punch",
                    effectEnabled = bassMono.enabled,
                    available = masterEnabled,
                    onToggle = { enabled ->
                        onUpdate { it.copy(bassMono = it.bassMono.copy(enabled = enabled)) }
                    }
                )
            )
            if (masterEnabled && bassMono.enabled) {
                add(
                    viperParamItem(title = "Bass mono cutoff") {
                        ViperSliderRow(
                            label = "Cutoff frequency",
                            value = bassMono.frequencyHz.toFloat(),
                            valueRange = 40.0f..200.0f,
                            valueFormatted = formatHz(bassMono.frequencyHz),
                            onValueChange = { value ->
                                onUpdate { it.copy(bassMono = it.bassMono.copy(frequencyHz = value.toInt())) }
                            }
                        )
                    }
                )
            }

            val psychoacoustic = viperConfig.psychoacousticBass
            add(
                viperEffectItem(
                    icon = MaterialSymbolIcon("psychology", filled = true),
                    title = "Psychoacoustic Bass",
                    description = "Missing fundamental harmonic generator",
                    effectEnabled = psychoacoustic.enabled,
                    available = masterEnabled,
                    onToggle = { enabled ->
                        onUpdate { it.copy(psychoacousticBass = it.psychoacousticBass.copy(enabled = enabled)) }
                    }
                )
            )
            if (masterEnabled && psychoacoustic.enabled) {
                add(
                    viperParamItem(title = "Harmonic generator") {
                        ViperSliderRow(
                            label = "Bass cutoff",
                            value = psychoacoustic.cutoffHz.toFloat(),
                            valueRange = 40.0f..140.0f,
                            valueFormatted = formatHz(psychoacoustic.cutoffHz),
                            onValueChange = { value ->
                                onUpdate { it.copy(psychoacousticBass = it.psychoacousticBass.copy(cutoffHz = value.toInt())) }
                            }
                        )
                        ViperSliderRow(
                            label = "Harmonic intensity",
                            value = psychoacoustic.intensity * 100f,
                            valueRange = 0.0f..100.0f,
                            valueFormatted = formatPercent(psychoacoustic.intensity),
                            onValueChange = { value ->
                                onUpdate { it.copy(psychoacousticBass = it.psychoacousticBass.copy(intensity = value / 100f)) }
                            }
                        )
                    }
                )
            }
        }
    }

    val clarity = viperConfig.viperClarity
    val clarityItems = buildList {
        add(
            viperEffectItem(
                icon = MaterialSymbolIcon("waves", filled = true),
                title = stringResource(R.string.viper_clarity_title),
                description = stringResource(R.string.viper_clarity_desc),
                effectEnabled = clarity.enabled,
                available = masterEnabled,
                onToggle = { enabled ->
                    onUpdate { it.copy(viperClarity = it.viperClarity.copy(enabled = enabled)) }
                }
            )
        )
        if (masterEnabled && clarity.enabled) {
            add(
                viperParamItem(title = "Clarity tuning") {
                    ViperSegmentedRow(
                        label = stringResource(R.string.viper_clarity_mode),
                        options = listOf("Natural", "Ozone+", "X-HiFi"),
                        selectedIndex = clarity.mode.coerceIn(0, 2),
                        onSelectIndex = { mode ->
                            onUpdate { it.copy(viperClarity = it.viperClarity.copy(mode = mode)) }
                        }
                    )
                    ViperSliderRow(
                        label = stringResource(R.string.viper_clarity_gain),
                        value = clarity.gainDb,
                        valueRange = 0.0f..12.0f,
                        valueFormatted = formatDb(clarity.gainDb),
                        onValueChange = { value ->
                            onUpdate { it.copy(viperClarity = it.viperClarity.copy(gainDb = value)) }
                        }
                    )
                }
            )
        }

        if (uiMode != ViperUiMode.SIMPLE) {
            val tube = viperConfig.tubeSimulator
            add(
                viperEffectItem(
                    icon = MaterialSymbolIcon("candlestick_chart", filled = true),
                    title = "Tube Simulator (6N1J)",
                    description = "Warm vacuum tube triode saturation",
                    effectEnabled = tube.enabled,
                    available = masterEnabled,
                    onToggle = { enabled ->
                        onUpdate { it.copy(tubeSimulator = it.tubeSimulator.copy(enabled = enabled)) }
                    }
                )
            )
            if (masterEnabled && tube.enabled) {
                add(
                    viperParamItem(title = "Tube character") {
                        ViperSegmentedRow(
                            label = "Tube model",
                            options = listOf("6N1J Triode", "12AX7 Warm", "FET Stage"),
                            selectedIndex = tube.model.coerceIn(0, 2),
                            onSelectIndex = { model ->
                                onUpdate { it.copy(tubeSimulator = it.tubeSimulator.copy(model = model)) }
                            }
                        )
                        ViperSliderRow(
                            label = "Drive amount",
                            value = tube.drive * 100f,
                            valueRange = 0.0f..100.0f,
                            valueFormatted = formatPercent(tube.drive),
                            onValueChange = { value ->
                                onUpdate { it.copy(tubeSimulator = it.tubeSimulator.copy(drive = value / 100f)) }
                            }
                        )
                        ViperSliderRow(
                            label = "Wet / dry mix",
                            value = tube.mix * 100f,
                            valueRange = 0.0f..100.0f,
                            valueFormatted = formatPercent(tube.mix),
                            onValueChange = { value ->
                                onUpdate { it.copy(tubeSimulator = it.tubeSimulator.copy(mix = value / 100f)) }
                            }
                        )
                    }
                )
            }

            val spectrum = viperConfig.spectrumExtension
            add(
                viperEffectItem(
                    icon = MaterialSymbolIcon("insights", filled = true),
                    title = "Spectrum Extension",
                    description = "High-frequency harmonic reconstructor",
                    effectEnabled = spectrum.enabled,
                    available = masterEnabled,
                    onToggle = { enabled ->
                        onUpdate { it.copy(spectrumExtension = it.spectrumExtension.copy(enabled = enabled)) }
                    }
                )
            )
            if (masterEnabled && spectrum.enabled) {
                add(
                    viperParamItem(title = "Extension shaping") {
                        ViperSliderRow(
                            label = "Extension strength",
                            value = spectrum.strength * 100f,
                            valueRange = 0.0f..100.0f,
                            valueFormatted = formatPercent(spectrum.strength),
                            onValueChange = { value ->
                                onUpdate { it.copy(spectrumExtension = it.spectrumExtension.copy(strength = value / 100f)) }
                            }
                        )
                        ViperSliderRow(
                            label = "Exciter amount",
                            value = spectrum.exciter * 100f,
                            valueRange = 0.0f..100.0f,
                            valueFormatted = formatPercent(spectrum.exciter),
                            onValueChange = { value ->
                                onUpdate { it.copy(spectrumExtension = it.spectrumExtension.copy(exciter = value / 100f)) }
                            }
                        )
                    }
                )
            }
        }

        if (uiMode == ViperUiMode.EXPERT) {
            val analogX = viperConfig.analogX
            add(
                viperEffectItem(
                    icon = MaterialSymbolIcon("memory", filled = true),
                    title = "AnalogX",
                    description = "Class-A discrete analog amplifier emulation",
                    effectEnabled = analogX.enabled,
                    available = masterEnabled,
                    onToggle = { enabled ->
                        onUpdate { it.copy(analogX = it.analogX.copy(enabled = enabled)) }
                    }
                )
            )
            if (masterEnabled && analogX.enabled) {
                add(
                    viperParamItem(title = "Warmth level") {
                        ViperSegmentedRow(
                            label = "Warmth",
                            options = listOf("Subtle", "Moderate", "Warm Vintage"),
                            selectedIndex = analogX.mode.coerceIn(0, 2),
                            onSelectIndex = { mode ->
                                onUpdate { it.copy(analogX = it.analogX.copy(mode = mode)) }
                            }
                        )
                    }
                )
            }
        }
    }

    val correctionItems = buildList {
        val ddc = viperConfig.viperDdc
        add(
            viperEffectItem(
                icon = MaterialSymbolIcon("headphones", filled = true),
                title = stringResource(R.string.viper_ddc_title),
                description = stringResource(R.string.viper_ddc_desc),
                effectEnabled = ddc.enabled,
                available = masterEnabled,
                onToggle = { enabled ->
                    onUpdate { it.copy(viperDdc = it.viperDdc.copy(enabled = enabled)) }
                }
            )
        )
        if (masterEnabled && ddc.enabled) {
            add(
                Material3SettingsItem(
                    title = { Text(stringResource(R.string.viper_ddc_headphone)) },
                    description = {
                        Column {
                            ViperSegmentedRow(
                                label = stringResource(R.string.viper_ddc_headphone),
                                options = ddcDeviceLabels,
                                selectedIndex = ViperDdcConfig.BUILTIN_DEVICES
                                    .indexOf(ddc.deviceName)
                                    .coerceIn(0, ddcDeviceLabels.lastIndex),
                                onSelectIndex = { index ->
                                    val device = ViperDdcConfig.BUILTIN_DEVICES
                                        .getOrElse(index) { "None" }
                                    onUpdate { it.copy(viperDdc = it.viperDdc.copy(deviceName = device)) }
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.viper_ddc_applied, ddc.deviceName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            )
        }
    }

    val soundstageItems = buildList {
        val fieldSurround = viperConfig.fieldSurround
        add(
            viperEffectItem(
                icon = MaterialSymbolIcon("surround_sound", filled = true),
                title = "Field Surround",
                description = "Multi-dimensional soundstage widening",
                effectEnabled = fieldSurround.enabled,
                available = masterEnabled,
                onToggle = { enabled ->
                    onUpdate { it.copy(fieldSurround = it.fieldSurround.copy(enabled = enabled)) }
                }
            )
        )
        if (masterEnabled && fieldSurround.enabled) {
            add(
                viperParamItem(title = "Soundstage") {
                    ViperSliderRow(
                        label = "Soundstage widening",
                        value = fieldSurround.widening * 100f,
                        valueRange = 0.0f..100.0f,
                        valueFormatted = formatPercent(fieldSurround.widening),
                        onValueChange = { value ->
                            onUpdate { it.copy(fieldSurround = it.fieldSurround.copy(widening = value / 100f)) }
                        }
                    )
                    ViperSliderRow(
                        label = "Mid-image focus",
                        value = fieldSurround.midImage,
                        valueRange = 0.5f..2.0f,
                        valueFormatted = String.format(Locale.US, "%.1fx", fieldSurround.midImage),
                        onValueChange = { value ->
                            onUpdate { it.copy(fieldSurround = it.fieldSurround.copy(midImage = value)) }
                        }
                    )
                }
            )
        }

        val convolver = viperConfig.convolver
        add(
            viperEffectItem(
                icon = MaterialSymbolIcon("settings_input_component", filled = true),
                title = stringResource(R.string.viper_convolver_title),
                description = stringResource(R.string.viper_convolver_desc),
                effectEnabled = convolver.enabled,
                available = masterEnabled,
                onToggle = { enabled ->
                    onUpdate { it.copy(convolver = it.convolver.copy(enabled = enabled)) }
                }
            )
        )
        if (masterEnabled && convolver.enabled) {
            add(
                viperParamItem(title = stringResource(R.string.viper_convolver_preset)) {
                    ExpressiveButtonGroup(
                        items = convolverPresets,
                        selectedIndex = convolverPresets.indexOf(convolver.kernelName).coerceAtLeast(0),
                        onItemClick = { index ->
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            onUpdate {
                                it.copy(
                                    convolver = it.convolver.copy(
                                        kernelName = convolverPresets[index],
                                        irUri = null
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { customIrLauncher.launch(arrayOf("audio/*", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (!convolver.irUri.isNullOrBlank()) "Change Custom IR (.wav/.irs)" else "Load Custom IR (.wav/.irs)",
                                maxLines = 1
                            )
                        }
                        if (!convolver.irUri.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = {
                                    onUpdate {
                                        it.copy(
                                            convolver = it.convolver.copy(
                                                kernelName = convolverPresets.first(),
                                                irUri = null
                                            )
                                        )
                                    }
                                }
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    ViperSliderRow(
                        label = "Wet Mix",
                        value = convolver.mixPercent * 100f,
                        valueRange = 0.0f..100.0f,
                        valueFormatted = formatPercent(convolver.mixPercent),
                        onValueChange = { value ->
                            onUpdate { it.copy(convolver = it.convolver.copy(mixPercent = value / 100f)) }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ViperSliderRow(
                        label = stringResource(R.string.viper_convolver_cross_channel),
                        value = convolver.crossChannel * 100f,
                        valueRange = 0.0f..100.0f,
                        valueFormatted = formatPercent(convolver.crossChannel),
                        onValueChange = { value ->
                            onUpdate { it.copy(convolver = it.convolver.copy(crossChannel = value / 100f)) }
                        }
                    )
                }
            )
        }

        val reverberation = viperConfig.reverberation
        add(
            viperEffectItem(
                icon = MaterialSymbolIcon("blur_on", filled = true),
                title = "Reverberation",
                description = "Algorithmic acoustic reverb",
                effectEnabled = reverberation.enabled,
                available = masterEnabled,
                onToggle = { enabled ->
                    onUpdate { it.copy(reverberation = it.reverberation.copy(enabled = enabled)) }
                }
            )
        )
        if (masterEnabled && reverberation.enabled) {
            add(
                viperParamItem(title = "Reverb parameters") {
                    ViperSliderRow(
                        label = "Room size",
                        value = reverberation.roomSize * 100f,
                        valueRange = 0.0f..100.0f,
                        valueFormatted = formatPercent(reverberation.roomSize),
                        onValueChange = { value ->
                            onUpdate { it.copy(reverberation = it.reverberation.copy(roomSize = value / 100f)) }
                        }
                    )
                    ViperSliderRow(
                        label = "Wet signal",
                        value = reverberation.wet * 100f,
                        valueRange = 0.0f..50.0f,
                        valueFormatted = formatPercent(reverberation.wet),
                        onValueChange = { value ->
                            onUpdate { it.copy(reverberation = it.reverberation.copy(wet = value / 100f)) }
                        }
                    )
                }
            )
        }

        val headphoneSurround = viperConfig.headphoneSurround
        add(
            viperEffectItem(
                icon = MaterialSymbolIcon("headphones", filled = true),
                title = "Headphone Surround+",
                description = "Binaural headphone spatial virtualizer",
                effectEnabled = headphoneSurround.enabled,
                available = masterEnabled,
                onToggle = { enabled ->
                    onUpdate { it.copy(headphoneSurround = it.headphoneSurround.copy(enabled = enabled)) }
                }
            )
        )
        if (masterEnabled && headphoneSurround.enabled) {
            add(
                viperParamItem(title = "Surround quality") {
                    ViperSegmentedRow(
                        label = "Quality",
                        options = listOf("Standard", "VHS+"),
                        selectedIndex = headphoneSurround.quality.coerceIn(0, 1),
                        onSelectIndex = { quality ->
                            onUpdate { it.copy(headphoneSurround = it.headphoneSurround.copy(quality = quality)) }
                        }
                    )
                }
            )
        }

        if (uiMode == ViperUiMode.EXPERT) {
            val diffSurround = viperConfig.diffSurround
            add(
                viperEffectItem(
                    icon = MaterialSymbolIcon("spatial_audio", filled = true),
                    title = "Differential Surround",
                    description = "Haas-effect phase delay",
                    effectEnabled = diffSurround.enabled,
                    available = masterEnabled,
                    onToggle = { enabled ->
                        onUpdate { it.copy(diffSurround = it.diffSurround.copy(enabled = enabled)) }
                    }
                )
            )
            if (masterEnabled && diffSurround.enabled) {
                add(
                    viperParamItem(title = "Haas delay") {
                        ViperSliderRow(
                            label = "Surround delay",
                            value = diffSurround.delayMs,
                            valueRange = 1.0f..20.0f,
                            valueFormatted = String.format(Locale.US, "%.1f ms", diffSurround.delayMs),
                            onValueChange = { value ->
                                onUpdate { it.copy(diffSurround = it.diffSurround.copy(delayMs = value)) }
                            }
                        )
                        ViperSliderRow(
                            label = "Wet / dry mix",
                            value = diffSurround.wetDryMix * 100f,
                            valueRange = 0.0f..100.0f,
                            valueFormatted = formatPercent(diffSurround.wetDryMix),
                            onValueChange = { value ->
                                onUpdate { it.copy(diffSurround = it.diffSurround.copy(wetDryMix = value / 100f)) }
                            }
                        )
                    }
                )
            }
        }
    }

    val dynamicsItems = buildList {
        val playbackGain = viperConfig.playbackGain
        add(
            viperEffectItem(
                icon = MaterialSymbolIcon("volume_up", filled = true),
                title = "AGC (Playback Gain)",
                description = "Dynamic auto gain leveler",
                effectEnabled = playbackGain.enabled,
                available = masterEnabled,
                onToggle = { enabled ->
                    onUpdate { it.copy(playbackGain = it.playbackGain.copy(enabled = enabled)) }
                }
            )
        )
        if (masterEnabled && playbackGain.enabled) {
            add(
                viperParamItem(title = "Gain leveling") {
                    ViperSegmentedRow(
                        label = "Strength",
                        options = listOf("Subtle", "Moderate", "Aggressive"),
                        selectedIndex = (playbackGain.strength - 1).coerceIn(0, 2),
                        onSelectIndex = { index ->
                            onUpdate { it.copy(playbackGain = it.playbackGain.copy(strength = index + 1)) }
                        }
                    )
                    ViperSliderRow(
                        label = "Max gain",
                        value = playbackGain.maxGainDb,
                        valueRange = 0.0f..18.0f,
                        valueFormatted = formatDb(playbackGain.maxGainDb),
                        onValueChange = { value ->
                            onUpdate { it.copy(playbackGain = it.playbackGain.copy(maxGainDb = value)) }
                        }
                    )
                    ViperSliderRow(
                        label = "Output threshold",
                        value = playbackGain.outputThresholdDb,
                        valueRange = -6.0f..0.0f,
                        valueFormatted = formatDb(playbackGain.outputThresholdDb),
                        onValueChange = { value ->
                            onUpdate { it.copy(playbackGain = it.playbackGain.copy(outputThresholdDb = value)) }
                        }
                    )
                }
            )
        }

        if (uiMode == ViperUiMode.EXPERT) {
            val fet = viperConfig.fetCompressor
            add(
                viperEffectItem(
                    icon = MaterialSymbolIcon("compress", filled = true),
                    title = "FET Compressor",
                    description = "Vintage fast analog dynamics & punch",
                    effectEnabled = fet.enabled,
                    available = masterEnabled,
                    onToggle = { enabled ->
                        onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(enabled = enabled)) }
                    }
                )
            )
            if (masterEnabled && fet.enabled) {
                add(
                    viperParamItem(title = "Compressor") {
                        ViperSliderRow(
                            label = "Threshold",
                            value = fet.thresholdDb,
                            valueRange = -40.0f..0.0f,
                            valueFormatted = formatDb(fet.thresholdDb),
                            onValueChange = { value ->
                                onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(thresholdDb = value)) }
                            }
                        )
                        ViperSliderRow(
                            label = "Ratio",
                            value = fet.ratio,
                            valueRange = 1.0f..10.0f,
                            valueFormatted = String.format(Locale.US, "%.1f:1", fet.ratio),
                            onValueChange = { value ->
                                onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(ratio = value)) }
                            }
                        )
                        ViperSliderRow(
                            label = "Attack time",
                            value = fet.attackMs,
                            valueRange = 0.5f..50.0f,
                            valueFormatted = String.format(Locale.US, "%.1f ms", fet.attackMs),
                            onValueChange = { value ->
                                onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(attackMs = value)) }
                            }
                        )
                        ViperSliderRow(
                            label = "Release time",
                            value = fet.releaseMs,
                            valueRange = 10.0f..400.0f,
                            valueFormatted = String.format(Locale.US, "%.0f ms", fet.releaseMs),
                            onValueChange = { value ->
                                onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(releaseMs = value)) }
                            }
                        )
                        ViperSliderRow(
                            label = "Makeup gain",
                            value = fet.makeupGainDb,
                            valueRange = 0.0f..12.0f,
                            valueFormatted = formatDb(fet.makeupGainDb),
                            onValueChange = { value ->
                                onUpdate { it.copy(fetCompressor = it.fetCompressor.copy(makeupGainDb = value)) }
                            }
                        )
                    }
                )
            }
        }
    }

    val masterGate = viperConfig.masterGate
    val limiterItems = buildList {
        add(
            viperEffectItem(
                icon = MaterialSymbolIcon("tune", filled = true),
                title = stringResource(R.string.viper_master_gate_title),
                description = "Output gain, balance & peak protection",
                effectEnabled = masterGate.limiterEnabled,
                available = masterEnabled,
                onToggle = { enabled ->
                    onUpdate { it.copy(masterGate = it.masterGate.copy(limiterEnabled = enabled)) }
                }
            )
        )
        if (masterEnabled && masterGate.limiterEnabled) {
            add(
                viperParamItem(title = "Output volume & pan") {
                    ViperSliderRow(
                        label = "Output volume",
                        value = masterGate.outputVolumeDb,
                        valueRange = -20.0f..6.0f,
                        valueFormatted = formatDb(masterGate.outputVolumeDb),
                        onValueChange = { value ->
                            onUpdate { it.copy(masterGate = it.masterGate.copy(outputVolumeDb = value)) }
                        }
                    )
                    ViperSliderRow(
                        label = "Channel pan (L / R)",
                        value = masterGate.channelPan,
                        valueRange = -1.0f..1.0f,
                        valueFormatted = when {
                            masterGate.channelPan < -0.05f -> "L ${(-masterGate.channelPan * 100).toInt()}%"
                            masterGate.channelPan > 0.05f -> "R ${(masterGate.channelPan * 100).toInt()}%"
                            else -> "Center"
                        },
                        onValueChange = { value ->
                            onUpdate { it.copy(masterGate = it.masterGate.copy(channelPan = value)) }
                        }
                    )
                }
            )
            add(
                viperParamItem(title = "Peak protection") {
                    ViperSliderRow(
                        label = "Limiter threshold",
                        value = masterGate.limiterThresholdDb,
                        valueRange = -6.0f..0.0f,
                        valueFormatted = formatDb(masterGate.limiterThresholdDb),
                        onValueChange = { value ->
                            onUpdate { it.copy(masterGate = it.masterGate.copy(limiterThresholdDb = value)) }
                        }
                    )
                }
            )
        }
    }

    val sections = buildList {
        add(ViperSection(R.string.viper_section_output, outputItems))
        add(ViperSection(R.string.viper_section_tone, toneItems))
        add(ViperSection(R.string.viper_section_bass, bassItems))
        add(ViperSection(R.string.viper_section_clarity, clarityItems))
        if (uiMode != ViperUiMode.SIMPLE) {
            add(ViperSection(R.string.viper_section_correction, correctionItems))
            add(ViperSection(R.string.viper_section_soundstage, soundstageItems))
            add(ViperSection(R.string.viper_section_dynamics, dynamicsItems))
        }
        add(ViperSection(R.string.viper_section_limiter, limiterItems))
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
                            title = stringResource(R.string.viper_backup_sharing),
                            icon = MaterialSymbolIcon("save", filled = true),
                            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                showMenu = false
                                openSheet = ViperSheet.PROFILE_IO
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
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    openSheet = ViperSheet.ENGINE_STATUS
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (masterEnabled) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
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
                        tint = if (masterEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(35.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = if (masterEnabled) {
                            stringResource(R.string.common_active)
                        } else {
                            stringResource(R.string.common_disabled)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TunerAnimatedSwitch(
                        checked = masterEnabled,
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
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset
                },
            contentPadding = PaddingValues(
                start = if (isTablet) 28.dp else 20.dp,
                end = if (isTablet) 28.dp else 20.dp,
                top = 20.dp,
                bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "presets") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.viper_profiles_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    presetsContent()
                }
            }

            if (isTablet) {
                item(key = "tablet_sections") {
                    val midpoint = (sections.size + 1) / 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            sections.take(midpoint).forEach { ViperSectionGroup(it) }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            sections.drop(midpoint).forEach { ViperSectionGroup(it) }
                        }
                    }
                }
            } else {
                sections.forEach { section ->
                    item(key = section.titleRes) {
                        ViperSectionGroup(section)
                    }
                }
            }
        }
    }

    if (openSheet == ViperSheet.ENGINE_STATUS) {
        val telemetry by ViperAudioProcessor.globalTelemetry.collectAsState()
        ViperEngineStatusSheet(
            telemetry = telemetry,
            isMasterEnabled = masterEnabled,
            onDismiss = { openSheet = null }
        )
    }

    if (openSheet == ViperSheet.PROFILE_IO) {
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

        RhythmAdaptiveModalSheet(
            onDismissRequest = {
                openSheet = null
                showImportField = false
            },
            scrollState = profileSheetScrollState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                StandardBottomSheetHeader(
                    title = stringResource(R.string.viper_backup_sharing),
                    subtitle = stringResource(R.string.viper_profiles_subtitle),
                    visible = true
                )

                AdaptiveSheetScrollContainer(
                    scrollState = profileSheetScrollState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) { endPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(profileSheetScrollState)
                            .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp)
                    ) {
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
                                    .height(120.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Material3SettingsGroup(
                            items = backupItems,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
            }
        }
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

@Composable
private fun ViperSectionGroup(section: ViperSection) {
    Material3SettingsGroup(
        title = stringResource(section.titleRes),
        items = section.items,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}

@Composable
private fun viperEffectItem(
    icon: Any,
    title: String,
    description: String,
    effectEnabled: Boolean,
    available: Boolean,
    onToggle: (Boolean) -> Unit
): Material3SettingsItem = Material3SettingsItem(
    icon = icon,
    title = { Text(title) },
    description = { Text(description) },
    enabled = available,
    trailingContent = {
        TunerAnimatedSwitch(
            checked = effectEnabled,
            onCheckedChange = onToggle,
            enabled = available
        )
    },
    onClick = { if (available) onToggle(!effectEnabled) }
)

@Composable
private fun viperParamItem(
    title: String,
    content: @Composable () -> Unit
): Material3SettingsItem = Material3SettingsItem(
    title = { Text(title) },
    description = { Column { content() } }
)

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
                viperClarity = config.viperClarity.copy(enabled = true, mode = 1, gainDb = 2.5f),
                cureCrossfeed = config.cureCrossfeed.copy(enabled = true, preset = 1),
                masterGate = config.masterGate.copy(limiterEnabled = true)
            )

            "basshead" -> config.copy(
                masterEnabled = true,
                fireEqualizer = FireEqualizerConfig(
                    enabled = true,
                    presetName = "Bass Booster",
                    bands = FireEqualizerConfig.BUILTIN_PRESETS["Bass Booster"] ?: List(10) { 0.0f }
                ),
                viperBass = config.viperBass.copy(enabled = true, mode = 2, frequencyHz = 55, gainDb = 8.5f),
                psychoacousticBass = config.psychoacousticBass.copy(enabled = true, cutoffHz = 75, intensity = 0.6f),
                bassMono = config.bassMono.copy(enabled = true, frequencyHz = 85),
                masterGate = config.masterGate.copy(limiterEnabled = true)
            )

            "tube" -> config.copy(
                masterEnabled = true,
                tubeSimulator = config.tubeSimulator.copy(enabled = true, model = 0, drive = 0.6f, mix = 0.45f),
                analogX = config.analogX.copy(enabled = true, mode = 2),
                convolver = config.convolver.copy(enabled = true, kernelName = "Warm Tube"),
                masterGate = config.masterGate.copy(limiterEnabled = true)
            )

            "stadium" -> config.copy(
                masterEnabled = true,
                fieldSurround = config.fieldSurround.copy(enabled = true, widening = 0.65f),
                diffSurround = config.diffSurround.copy(enabled = true, delayMs = 9.0f, wetDryMix = 0.45f),
                reverberation = config.reverberation.copy(enabled = true, roomSize = 0.55f, wet = 0.22f),
                masterGate = config.masterGate.copy(limiterEnabled = true)
            )

            else -> config
        }
    }
}
