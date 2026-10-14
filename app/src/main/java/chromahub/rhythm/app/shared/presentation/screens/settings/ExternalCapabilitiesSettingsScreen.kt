/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.core.device.AudioCodecInfo
import chromahub.rhythm.app.core.device.DeviceCapabilitiesProvider
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveCookieEmptyState
import chromahub.rhythm.app.shared.presentation.components.common.horizontalEdgeBlend
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import java.util.Locale

private data class MimeFilterTab(
    val label: String,
    val key: String?
)

@Composable
fun ExternalCapabilitiesSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateTo: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val haptics = LocalHapticFeedback.current

    val softwareEngineEnabled by appSettings.audioEngineSoftwareEnabled.collectAsState()
    val preferHardware by appSettings.audioEnginePreferHardware.collectAsState()
    val floatOutput by appSettings.audioEngineFloatOutput.collectAsState()
    val disabledDecoders by appSettings.audioEngineDisabledDecoders.collectAsState()

    val allDecoders = remember(context) { DeviceCapabilitiesProvider.getAudioDecoders() }
    val softwareDecoders = remember(allDecoders) { allDecoders.filter { !it.isHardwareAccelerated } }

    var selectedMimeFilter by remember { mutableStateOf<String?>(null) }

    val filterCategories = remember(softwareDecoders) {
        val detectedFormats = softwareDecoders
            .map { getExternalMimeDisplayName(it.mimeType, it.name) }
            .distinct()
            .sorted()

        listOf(MimeFilterTab("All (${softwareDecoders.size})", null)) +
            detectedFormats.map { format ->
                MimeFilterTab(format, format)
            }
    }

    val visibleDecoders = remember(softwareDecoders, selectedMimeFilter) {
        if (selectedMimeFilter == null) {
            softwareDecoders
        } else {
            softwareDecoders.filter {
                getExternalMimeDisplayName(it.mimeType, it.name).equals(selectedMimeFilter, ignoreCase = true)
            }
        }
    }

    CollapsibleHeaderScreen(
        title = stringResource(R.string.settings_category_external_capabilities_title),
        showBackButton = true,
        onBackClick = onBackClick,
        headerContent = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (softwareEngineEnabled) {
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
                        imageVector = MaterialSymbolIcon("extension", filled = true),
                        contentDescription = null,
                        tint = if (softwareEngineEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(35.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(if (softwareEngineEnabled) R.string.status_active else R.string.status_disabled),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TunerAnimatedSwitch(
                        checked = softwareEngineEnabled,
                        onCheckedChange = { enabled ->
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            appSettings.setAudioEngineSoftwareEnabled(enabled)
                        }
                    )
                }
            }
        }
    ) { modifier ->
        if (softwareEngineEnabled) {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()
                ),
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "engine_config_group") {
                    val engineSettingItems = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("extension", filled = true),
                                title = stringResource(R.string.settings_category_decoder_extensions_title),
                                description = stringResource(R.string.settings_category_decoder_extensions_desc),
                                onClick = {
                                    onNavigateTo?.invoke(SettingsRoutes.DECODER_EXTENSIONS)
                                }
                            ),
                            hapticFeedback = haptics
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("memory", filled = true),
                                title = stringResource(R.string.settings_audio_engine_prefer_hw),
                                description = stringResource(R.string.settings_audio_engine_prefer_hw_desc),
                                toggleState = preferHardware,
                                onToggleChange = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    appSettings.setAudioEnginePreferHardware(it)
                                }
                            ),
                            hapticFeedback = haptics
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("graphic_eq", filled = true),
                                title = stringResource(R.string.settings_audio_engine_float_output),
                                description = stringResource(R.string.settings_audio_engine_float_output_desc),
                                toggleState = floatOutput,
                                onToggleChange = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    appSettings.setAudioEngineFloatOutput(it)
                                }
                            ),
                            hapticFeedback = haptics
                        )
                    )

                    Material3SettingsGroup(
                        title = stringResource(R.string.settings_external_caps_engine),
                        items = engineSettingItems,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                }

                item(key = "header_sw_decoders") {
                    Text(
                        text = stringResource(R.string.settings_external_caps_sw_decoders),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp, top = 18.dp)
                    )
                }

                item(key = "filter_chips") {
                    val filterChipsState = rememberLazyListState()
                    LazyRow(
                        state = filterChipsState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalEdgeBlend(lazyListState = filterChipsState, fadeWidth = 14.dp)
                    ) {
                        items(
                            items = filterCategories,
                            key = { it.label }
                        ) { cat ->
                            val isSelected = cat.key == selectedMimeFilter

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    selectedMimeFilter = if (isSelected && cat.key != null) null else cat.key
                                },
                                label = {
                                    Text(
                                        text = cat.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = RhythmIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                item(key = "sw_decoders_group") {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (visibleDecoders.isEmpty()) {
                        ExpressiveCookieEmptyState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            title = stringResource(R.string.settings_external_caps_no_decoders_title),
                            subtitle = stringResource(R.string.settings_external_caps_no_decoders_desc),
                            mainIcon = MaterialSymbolIcon("extension_off"),
                            accentIcon = RhythmIcons.FilterList,
                            cornerIcon = MaterialSymbolIcon("tune"),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        val decoderSettingItems = visibleDecoders.map { decoder ->
                            val isEnabled = !disabledDecoders.contains(decoder.name)
                            val titleFormat = getExternalMimeDisplayName(decoder.mimeType, decoder.name)
                            val subtitleDesc = if (decoder.maxInstances > 0) {
                                "${decoder.name} • Max: ${decoder.maxInstances}"
                            } else {
                                decoder.name
                            }

                            toMaterial3SettingsItem(
                                context = context,
                                item = SettingItem(
                                    icon = null,
                                    title = titleFormat,
                                    description = subtitleDesc,
                                    toggleState = isEnabled,
                                    onToggleChange = { checked ->
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        appSettings.toggleAudioEngineDecoder(decoder.name, checked)
                                    }
                                ),
                                hapticFeedback = haptics
                            )
                        }

                        Material3SettingsGroup(
                            items = decoderSettingItems,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    }
                }

                if (disabledDecoders.isNotEmpty() || !preferHardware || !floatOutput) {
                    item(key = "reset_engine_group") {
                        Spacer(modifier = Modifier.height(10.dp))
                        Material3SettingsGroup(
                            items = listOf(
                                toMaterial3SettingsItem(
                                    context = context,
                                    item = SettingItem(
                                        icon = MaterialSymbolIcon("restart_alt", filled = true),
                                        title = stringResource(R.string.settings_external_caps_reset),
                                        description = stringResource(R.string.settings_external_caps_reset_desc),
                                        onClick = {
                                            appSettings.resetAudioEngineDecoders()
                                            appSettings.setAudioEnginePreferHardware(true)
                                            appSettings.setAudioEngineFloatOutput(true)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.settings_external_caps_reset_toast),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    ),
                                    hapticFeedback = haptics
                                )
                            ),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = LocalMiniPlayerPadding.current.calculateBottomPadding()),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = context.getString(chromahub.rhythm.app.R.string.ui_software_engine_bypassed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = context.getString(chromahub.rhythm.app.R.string.ui_external_software_decoding_overrides_and_float_pcm_pipeline_are_),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun getExternalMimeDisplayName(mime: String, decoderName: String = ""): String {
    val normMime = mime.lowercase(Locale.US)
    val normName = decoderName.lowercase(Locale.US)
    return when {
        normMime.contains("ac4") || normName.contains(".ac4") -> "AC-4"
        normMime.contains("eac3-joc") || normName.contains("eac3_joc") -> "E-AC-3 JOC"
        normMime.contains("eac3") || normName.contains(".eac3") -> "E-AC-3"
        normMime.contains("ac3") || normName.contains(".ac3") -> "AC-3"
        normMime.contains("flac") || normName.contains(".flac") -> "FLAC"
        normMime.contains("opus") || normName.contains(".opus") -> "Opus"
        normMime.contains("mp4a-latm") || normMime.contains("aac") || normName.contains(".aac") -> "AAC"
        normMime.contains("mpeg") || normMime.contains("mp3") || normName.contains(".mp3") -> "MP3"
        normMime.contains("vorbis") || normName.contains(".vorbis") -> "Vorbis"
        normMime.contains("alac") || normName.contains(".alac") -> "ALAC"
        normMime.contains("dts-hd") || normName.contains("dtshd") -> "DTS-HD"
        normMime.contains("dts") || normName.contains(".dts") -> "DTS"
        normMime.contains("truehd") || normName.contains("truehd") -> "Dolby TrueHD"
        normMime.contains("raw") || normName.contains(".raw") -> "PCM RAW"
        normMime.contains("amr-wb") || normName.contains("amrwb") -> "AMR-WB"
        normMime.contains("3gpp") || normName.contains("amrnb") -> "AMR-NB"
        normMime.contains("g711-alaw") || normName.contains("g711.alaw") -> "G.711 A-law"
        normMime.contains("g711-mlaw") || normName.contains("g711.mlaw") -> "G.711 μ-law"
        normMime.contains("gsm") || normName.contains(".gsm") -> "GSM"
        normMime.contains("midi") || normName.contains(".midi") -> "MIDI"
        normMime.contains("wma") || normName.contains(".wma") -> "WMA"
        else -> {
            val fromMime = mime.substringAfterLast("/").substringAfterLast(".")
            fromMime.ifBlank { decoderName.substringAfterLast(".") }.uppercase(Locale.US)
        }
    }
}

@Composable
fun AudioCapabilitiesSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateTo: ((String) -> Unit)? = null
) {
    ExternalCapabilitiesSettingsScreen(onBackClick = onBackClick, onNavigateTo = onNavigateTo)
}

@Composable
fun AudioEngineSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateTo: ((String) -> Unit)? = null
) {
    ExternalCapabilitiesSettingsScreen(onBackClick = onBackClick, onNavigateTo = onNavigateTo)
}
