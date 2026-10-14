/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
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

private data class DecoderCodecCapability(
    val formatName: String,
    val requiredExtensionId: String,
    val extensionName: String,
    val category: String,
    val description: String
)

private val EXTENSION_CODECS = listOf(
    DecoderCodecCapability(
        formatName = "AC-4 (Dolby AC-4)",
        requiredExtensionId = "ffmpeg",
        extensionName = "FFmpeg Extension",
        category = "Dolby & Atmos",
        description = "Next-generation broadcast & immersive audio"
    ),
    DecoderCodecCapability(
        formatName = "E-AC-3 / EAC3-JOC (Dolby Atmos)",
        requiredExtensionId = "ffmpeg",
        extensionName = "FFmpeg Extension",
        category = "Dolby & Atmos",
        description = "Enhanced AC-3 with Joint Object Coding"
    ),
    DecoderCodecCapability(
        formatName = "AC-3 (Dolby Digital)",
        requiredExtensionId = "ffmpeg",
        extensionName = "FFmpeg Extension",
        category = "Dolby & Atmos",
        description = "Standard 5.1 multichannel audio"
    ),
    DecoderCodecCapability(
        formatName = "WMA / WMA-Pro / Lossless",
        requiredExtensionId = "ffmpeg",
        extensionName = "FFmpeg Extension",
        category = "Lossless & Hi-Res",
        description = "Windows Media Audio codec family"
    ),
    DecoderCodecCapability(
        formatName = "ALAC (Apple Lossless)",
        requiredExtensionId = "ffmpeg",
        extensionName = "FFmpeg Extension",
        category = "Lossless & Hi-Res",
        description = "High-resolution Apple Lossless Audio Codec"
    ),
    DecoderCodecCapability(
        formatName = "Standard MIDI (.mid, .midi)",
        requiredExtensionId = "midi",
        extensionName = "MIDI Synthesizer",
        category = "MIDI",
        description = "Synthesizer for standard MIDI sequence files"
    ),
    DecoderCodecCapability(
        formatName = "DTS / DTS-HD Master Audio",
        requiredExtensionId = "ffmpeg",
        extensionName = "FFmpeg Extension",
        category = "Dolby & Atmos",
        description = "High-definition surround cinema audio"
    ),
    DecoderCodecCapability(
        formatName = "Dolby TrueHD",
        requiredExtensionId = "ffmpeg",
        extensionName = "FFmpeg Extension",
        category = "Dolby & Atmos",
        description = "Bit-for-bit lossless multichannel audio"
    )
)

@Composable
fun DecoderExtensionsSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val haptics = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    val extensionsMasterEnabled by appSettings.decoderExtensionsEnabled.collectAsState()
    val disabledExtensions by appSettings.disabledDecoderExtensions.collectAsState()

    var selectedFilter by remember { mutableStateOf<String?>(null) }
    val filterOptions = listOf("All", "Engines", "Dolby & Atmos", "Lossless & Hi-Res", "MIDI")

    fun copyToClipboard(label: String, value: String) {
        clipboardManager.setText(AnnotatedString("$label: $value"))
        Toast.makeText(context, context.getString(R.string.about_copied_format, label), Toast.LENGTH_SHORT).show()
    }

    val visibleCodecs = remember(selectedFilter) {
        when (selectedFilter) {
            null, "All" -> EXTENSION_CODECS
            "Engines" -> emptyList()
            else -> EXTENSION_CODECS.filter { it.category.equals(selectedFilter, ignoreCase = true) }
        }
    }

    val showEngines = selectedFilter == null || selectedFilter == "All" || selectedFilter == "Engines" || selectedFilter == "MIDI"

    CollapsibleHeaderScreen(
        title = stringResource(R.string.settings_category_decoder_extensions_title),
        showBackButton = true,
        onBackClick = onBackClick,
        headerContent = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (extensionsMasterEnabled) {
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
                        tint = if (extensionsMasterEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(35.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(if (extensionsMasterEnabled) R.string.status_active else R.string.status_disabled),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TunerAnimatedSwitch(
                        checked = extensionsMasterEnabled,
                        onCheckedChange = { isChecked ->
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            appSettings.setDecoderExtensionsEnabled(isChecked)
                        }
                    )
                }
            }
        }
    ) { modifier ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()
            ),
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                        items = filterOptions,
                        key = { it }
                    ) { option ->
                        val isSelected = (selectedFilter == null && option == "All") || selectedFilter == option

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                selectedFilter = if (isSelected && option != "All") null else if (option == "All") null else option
                            },
                            label = {
                                Text(
                                    text = when (option) {
                                        "All" -> context.getString(R.string.library_category_all)
                                        "Engines" -> context.getString(R.string.decoder_filter_engines)
                                        "Dolby & Atmos" -> context.getString(R.string.decoder_filter_dolby)
                                        "Lossless & Hi-Res" -> context.getString(R.string.decoder_filter_lossless)
                                        else -> option
                                    },
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

            if (showEngines) {
                item(key = "header_engines") {
                    Text(
                        text = stringResource(R.string.settings_decoder_extensions_engines_section),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp, top = 16.dp, bottom = 4.dp)
                    )
                }

                item(key = "engines_group") {
                    val ffmpegActive = extensionsMasterEnabled && !disabledExtensions.contains("ffmpeg")
                    val midiActive = extensionsMasterEnabled && !disabledExtensions.contains("midi")
                    val flacActive = extensionsMasterEnabled && !disabledExtensions.contains("flac")
                    val opusActive = extensionsMasterEnabled && !disabledExtensions.contains("opus")

                    val engineItems = buildList {
                        if (selectedFilter != "MIDI") {
                            add(
                                SettingItem(
                                    icon = null,
                                    title = context.getString(chromahub.rhythm.app.R.string.ui_media3_ffmpeg_decoder),
                                    description = context.getString(chromahub.rhythm.app.R.string.ui_bundled_jellyfin_media3_extension_v1_9_0_1_software_decoding_for),
                                    toggleState = ffmpegActive,
                                    onToggleChange = { isChecked ->
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        appSettings.toggleDecoderExtension("ffmpeg", isChecked)
                                    }
                                )
                            )
                        }

                        add(
                            SettingItem(
                                icon = null,
                                title = context.getString(chromahub.rhythm.app.R.string.ui_media3_midi_synthesizer),
                                description = context.getString(chromahub.rhythm.app.R.string.ui_bundled_media3_midi_extension_v1_5_0_synthesizes_standard_midi_s),
                                toggleState = midiActive,
                                onToggleChange = { isChecked ->
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    appSettings.toggleDecoderExtension("midi", isChecked)
                                }
                            )
                        )

                        if (selectedFilter != "MIDI") {
                            add(
                                SettingItem(
                                    icon = null,
                                    title = context.getString(chromahub.rhythm.app.R.string.ui_native_flac_extension),
                                    description = context.getString(chromahub.rhythm.app.R.string.ui_bundled_libflac_architecture_fallback_for_non_standard_24_32_bit),
                                    toggleState = flacActive,
                                    onToggleChange = { isChecked ->
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        appSettings.toggleDecoderExtension("flac", isChecked)
                                    }
                                )
                            )
                            add(
                                SettingItem(
                                    icon = null,
                                    title = context.getString(chromahub.rhythm.app.R.string.ui_native_opus_extension),
                                    description = context.getString(chromahub.rhythm.app.R.string.ui_direct_c_libopus_fallback_for_ultra_low_latency_ogg_webm_streams),
                                    toggleState = opusActive,
                                    onToggleChange = { isChecked ->
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        appSettings.toggleDecoderExtension("opus", isChecked)
                                    }
                                )
                            )
                        }
                    }

                    Material3SettingsGroup(
                        items = engineItems.map { toMaterial3SettingsItem(context, it, haptics) },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                }
            }

            if (visibleCodecs.isNotEmpty()) {
                item(key = "header_codecs") {
                    Text(
                        text = stringResource(R.string.settings_decoder_extensions_codecs_section),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp, top = 16.dp, bottom = 4.dp)
                    )
                }

                item(key = "codecs_grid") {
                    val columns = 2
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        visibleCodecs.chunked(columns).forEachIndexed { rowIndex, rowCodecs ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Max)
                            ) {
                                rowCodecs.forEachIndexed { colIndex, codec ->
                                    val index = rowIndex * columns + colIndex
                                    val isSupported = extensionsMasterEnabled && !disabledExtensions.contains(codec.requiredExtensionId)
                                    val badge = if (isSupported) {
                                        if (codec.requiredExtensionId == "ffmpeg") "FFMPEG" else "MIDI"
                                    } else {
                                        context.getString(R.string.native_unsupported)
                                    }
                                    val value = if (isSupported) context.getString(R.string.native_supported) else stringResource(R.string.settings_decoder_extensions_not_supported_exoplayer)
                                    val subtitle = if (isSupported) {
                                        if (codec.requiredExtensionId == "ffmpeg") {
                                            stringResource(R.string.settings_decoder_extensions_supported_ffmpeg)
                                        } else {
                                            stringResource(R.string.settings_decoder_extensions_supported_midi)
                                        }
                                    } else {
                                        context.getString(R.string.decoder_requires_extension, codec.extensionName)
                                    }

                                    DecoderConnectedCard(
                                        label = codec.formatName,
                                        value = value,
                                        subtitle = subtitle,
                                        badge = badge,
                                        isHighlighted = isSupported,
                                        shape = getDecoderConnectedShape(index, visibleCodecs.size, columns),
                                        onClick = {
                                            copyToClipboard(codec.formatName, "$value ($badge) • $subtitle")
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                                if (rowCodecs.size < columns) {
                                    Spacer(modifier = Modifier.weight((columns - rowCodecs.size).toFloat()))
                                }
                            }
                        }
                    }
                }
            } else if (!showEngines) {
                item(key = "empty_state") {
                    ExpressiveCookieEmptyState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        title = stringResource(R.string.settings_decoder_extensions_empty_title),
                        subtitle = stringResource(R.string.settings_decoder_extensions_empty_desc),
                        mainIcon = MaterialSymbolIcon("extension_off"),
                        accentIcon = MaterialSymbolIcon("build"),
                        cornerIcon = RhythmIcons.Search,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            item(key = "reset_group") {
                Spacer(modifier = Modifier.height(8.dp))
                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("restart_alt", filled = true),
                                title = stringResource(R.string.settings_decoder_extensions_reset),
                                description = stringResource(R.string.settings_decoder_extensions_reset_desc),
                                onClick = {
                                    appSettings.setDecoderExtensionsEnabled(true)
                                    appSettings.resetDecoderExtensions()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_decoder_extensions_reset_toast),
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
}

@Composable
private fun DecoderConnectedCard(
    label: String,
    value: String,
    subtitle: String,
    badge: String,
    isHighlighted: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    onClick()
                }
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    color = if (isHighlighted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHighlighted) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isHighlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun getDecoderConnectedShape(index: Int, totalItems: Int, columns: Int = 2): RoundedCornerShape {
    if (totalItems <= 1) {
        return RoundedCornerShape(20.dp)
    }
    val row = index / columns
    val col = index % columns
    val totalRows = (totalItems + columns - 1) / columns
    val isFirstRow = row == 0
    val isLastRow = row == totalRows - 1
    val isLeftEdge = col == 0
    val isRightEdge = (col == columns - 1) || (index == totalItems - 1)

    val topStart = if (isFirstRow && isLeftEdge) 20.dp else 6.dp
    val topEnd = if (isFirstRow && isRightEdge) 20.dp else 6.dp
    val bottomStart = if (isLastRow && isLeftEdge) 20.dp else 6.dp
    val bottomEnd = if (isLastRow && isRightEdge) 20.dp else 6.dp

    return RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd
    )
}

@Composable
fun FallbackEnginesSettingsScreen(
    onBackClick: () -> Unit
) {
    DecoderExtensionsSettingsScreen(onBackClick = onBackClick)
}
