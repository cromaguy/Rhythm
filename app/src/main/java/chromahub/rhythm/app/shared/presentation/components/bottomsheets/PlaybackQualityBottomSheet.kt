/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.PlaybackQualityInfo
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.MarqueeText
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.common.RhythmDetailActionButton
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.ui.theme.MusicDimensions
import chromahub.rhythm.app.util.AudioQualityDetector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackQualityBottomSheet(
    song: Song,
    playbackQuality: PlaybackQualityInfo?,
    onDismissRequest: () -> Unit,
    onViewSongInfo: (() -> Unit)? = null,
    onRouteAudio: (() -> Unit)? = null,
    sheetState: SheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
) {
    val quality = remember(playbackQuality, song) {
        playbackQuality ?: run {
            val detected = AudioQualityDetector.detectQuality(
                codec = song.codec?.ifBlank { "Audio" } ?: "Audio",
                sampleRateHz = song.sampleRate ?: 44100,
                bitrateKbps = (song.bitrate ?: 0) / 1000,
                channelCount = song.channels ?: 2
            )
            val icon = PlaybackQualityInfo.resolveIconRes(detected.qualityType, detected.qualityLabel)
            PlaybackQualityInfo(
                sourceCodec = song.codec?.ifBlank { "Audio" } ?: "Audio",
                sourceBitrateKbps = (song.bitrate ?: 0) / 1000,
                sourceSampleRateHz = song.sampleRate ?: 0,
                sourceBitDepth = 16,
                sourceChannels = song.channels ?: 2,
                decoderName = "MediaCodec Audio Engine",
                decodedSampleRateHz = song.sampleRate ?: 44100,
                decodedBitDepth = 16,
                decodedChannels = song.channels ?: 2,
                decodedMimeType = "audio/raw",
                decodedBitrateKbps = (song.bitrate ?: 0) / 1000,
                outputDeviceName = "Audio Output",
                outputDeviceType = "Speaker / Headset",
                qualityType = detected.qualityType,
                qualityLabel = detected.qualityLabel,
                qualityDescription = detected.qualityDescription,
                isLossless = detected.isLossless,
                isHiRes = detected.isHiRes,
                isDolby = detected.isDolby,
                isDTS = detected.isDTS,
                qualityCategory = detected.category,
                outputQualityStatus = when {
                    detected.qualityType == AudioQualityDetector.QualityType.DOLBY_ATMOS -> "Spatial Audio • Full object rendering reproduced"
                    detected.isDolby || detected.isDTS || detected.qualityType == AudioQualityDetector.QualityType.MPEG_H_3D -> "Multichannel surround sound • Full quality reproduced"
                    detected.isLossless -> "Full quality output • Matched device capability"
                    else -> "Full source quality reproduced"
                },
                iconResId = icon
            )
        }
    }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.TWO_PANE_DIALOG,
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Icon(
                    painter = painterResource(id = quality.iconResId),
                    contentDescription = quality.qualityLabel,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = quality.qualityLabel.ifBlank { stringResource(R.string.playback_quality_dialog_title) },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val subheading = quality.displaySubheading
                if (subheading.isNotBlank()) {
                    Text(
                        text = subheading,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val decodedStreamItems = buildList {
                add(
                    QualityMetadataItem(
                        label = "Format / Codec",
                        value = quality.sourceCodec,
                        icon = MaterialSymbolIcon("music_note", filled = true)
                    )
                )
                add(
                    QualityMetadataItem(
                        label = stringResource(R.string.playback_quality_sample_rate),
                        value = PlaybackQualityInfo.formatSampleRate(quality.decodedSampleRateHz),
                        icon = MaterialSymbolIcon("graphic_eq", filled = true)
                    )
                )
                add(
                    QualityMetadataItem(
                        label = stringResource(R.string.playback_quality_bit_depth),
                        value = PlaybackQualityInfo.formatBitDepth(quality.decodedBitDepth),
                        icon = MaterialSymbolIcon("layers", filled = true)
                    )
                )
                add(
                    QualityMetadataItem(
                        label = stringResource(R.string.playback_quality_bitrate),
                        value = PlaybackQualityInfo.formatBitrate(quality.decodedBitrateKbps),
                        icon = MaterialSymbolIcon("speed", filled = true)
                    )
                )
                add(
                    QualityMetadataItem(
                        label = stringResource(R.string.playback_quality_channels),
                        value = PlaybackQualityInfo.formatChannels(quality.decodedChannels),
                        icon = MaterialSymbolIcon("surround_sound", filled = true)
                    )
                )
                add(
                    QualityMetadataItem(
                        label = stringResource(R.string.playback_quality_decoder),
                        value = quality.decoderName.ifBlank { "ExoPlayer MediaCodec" },
                        icon = MaterialSymbolIcon("memory", filled = true),
                        isWide = true
                    )
                )
            }

            QualityMetadataGridCard(
                title = stringResource(R.string.playback_quality_decoded_stream),
                icon = MaterialSymbolIcon("graphic_eq", filled = true),
                items = decodedStreamItems,
                tint = MaterialTheme.colorScheme.primary,
                tintContainer = MaterialTheme.colorScheme.primaryContainer
            )

            val outputDeviceIcon: Any = when {
                quality.isBluetooth -> RhythmIcons.BluetoothFilled
                quality.isBitPerfect -> MaterialSymbolIcon("usb", filled = true)
                quality.outputDeviceType.contains("Wired", ignoreCase = true) -> RhythmIcons.HeadphonesFilled
                else -> RhythmIcons.SpeakerFilled
            }

            val outputPipelineItems = buildList {
                add(
                    QualityMetadataItem(
                        label = stringResource(R.string.playback_quality_device),
                        value = quality.outputDeviceName.ifBlank { quality.outputDeviceType },
                        icon = outputDeviceIcon,
                        isWide = true,
                        onClick = onRouteAudio?.let { action ->
                            {
                                onDismissRequest()
                                action()
                            }
                        }
                    )
                )

                if (quality.isBluetooth) {
                    add(
                        QualityMetadataItem(
                            label = "Bluetooth Codec",
                            value = quality.bluetoothCodec ?: "A2DP",
                            icon = RhythmIcons.BluetoothFilled
                        )
                    )
                    val rateText = quality.bluetoothSampleRateHz?.takeIf { it > 0 }?.let {
                        PlaybackQualityInfo.formatSampleRate(it) +
                            (quality.bluetoothBitDepth?.let { d -> " • ${PlaybackQualityInfo.formatBitDepth(d)}" } ?: "")
                    } ?: (quality.bluetoothBitrate ?: "Standard Rate")
                    add(
                        QualityMetadataItem(
                            label = "Transmission Rate",
                            value = rateText,
                            icon = MaterialSymbolIcon("speed", filled = true)
                        )
                    )
                }

                val transmissionValue = when {
                    quality.isBitPerfect -> "Bit-Perfect USB 1:1 Direct Output"
                    quality.isResampled -> quality.resamplingDescription ?: "Resampled by Audio HAL"
                    quality.isBluetooth -> "Bluetooth Transmission (${quality.bluetoothCodec ?: "A2DP"})"
                    else -> "Direct HAL Output Pipeline"
                }
                val isWarning = quality.isResampled || (quality.isBluetooth && quality.bluetoothCodec in listOf("SBC", "AAC"))
                add(
                    QualityMetadataItem(
                        label = stringResource(R.string.playback_quality_resampling),
                        value = transmissionValue,
                        icon = MaterialSymbolIcon(if (isWarning) "info" else "check_circle", filled = true),
                        isWide = true
                    )
                )
            }

            QualityMetadataGridCard(
                title = stringResource(R.string.playback_quality_output_pipeline),
                icon = MaterialSymbolIcon("speaker_group", filled = true),
                items = outputPipelineItems,
                tint = MaterialTheme.colorScheme.secondary,
                tintContainer = MaterialTheme.colorScheme.secondaryContainer
            )

            val appContext = androidx.compose.ui.platform.LocalContext.current
            val appSettings = remember(appContext) { chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(appContext) }
            val sampleRateCapHz by appSettings.playbackPrefs.sampleRateCapHz.collectAsState()
            val surroundDownmix by appSettings.playbackPrefs.surroundDownmixEnabled.collectAsState()
            val audioRoutingMode by appSettings.audioRoutingMode.collectAsState()
            val bitPerfectRouting = audioRoutingMode == "app"
            val equalizerEnabled by appSettings.equalizerEnabled.collectAsState()
            val floatEnabled by appSettings.audioEngineFloatOutput.collectAsState()
            val speed by appSettings.playbackSpeed.collectAsState()
            var showSignalPathDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RhythmDetailActionButton(
                    onClick = { showSignalPathDialog = true },
                    weight = 1f,
                    height = 48.dp,
                    isFirst = true,
                    isLast = onRouteAudio == null && onViewSongInfo == null,
                    type = RhythmButtonType.Tonal,
                    icon = MaterialSymbolIcon("route", filled = true),
                    iconSize = 18.dp,
                    text = "Signal Path",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                if (onRouteAudio != null) {
                    RhythmDetailActionButton(
                        onClick = {
                            onDismissRequest()
                            onRouteAudio()
                        },
                        weight = 1f,
                        height = 48.dp,
                        isFirst = false,
                        isLast = onViewSongInfo == null,
                        type = RhythmButtonType.Tonal,
                        icon = MaterialSymbolIcon("speaker_group", filled = true),
                        iconSize = 18.dp,
                        text = stringResource(R.string.playback_quality_audio_output),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                if (onViewSongInfo != null) {
                    RhythmDetailActionButton(
                        onClick = {
                            onDismissRequest()
                            onViewSongInfo()
                        },
                        weight = 1f,
                        height = 48.dp,
                        isFirst = false,
                        isLast = true,
                        type = RhythmButtonType.Tonal,
                        icon = RhythmIcons.Info,
                        iconSize = 18.dp,
                        text = stringResource(R.string.playback_quality_view_full_metadata),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (showSignalPathDialog) {
                val isUsb = quality.outputDeviceType.contains("USB", ignoreCase = true)
                val viperRepo = remember(appContext) {
                    chromahub.rhythm.app.infrastructure.audio.viper.ViperSettingsRepository.getInstance(appContext.applicationContext)
                }
                val viperConfig by viperRepo.configFlow.collectAsState()
                val streamHealth by chromahub.rhythm.app.infrastructure.service.MediaPlaybackService.streamHealthSnapshot.collectAsState()
                val report = remember(quality, sampleRateCapHz, surroundDownmix, bitPerfectRouting, equalizerEnabled, floatEnabled, speed, viperConfig.masterEnabled, streamHealth) {
                    chromahub.rhythm.app.infrastructure.audio.SignalPathEvaluator.evaluate(
                        chromahub.rhythm.app.infrastructure.audio.SignalPathInput(
                            sourceCodec = quality.sourceCodec,
                            sourceSampleRateHz = quality.sourceSampleRateHz,
                            sourceBitDepth = quality.sourceBitDepth,
                            sourceChannels = quality.sourceChannels,
                            isLosslessSource = quality.isLossless,
                            outputSampleRateHz = quality.decodedSampleRateHz,
                            outputBitDepth = quality.decodedBitDepth,
                            outputDeviceName = quality.outputDeviceName,
                            isUsbDevice = isUsb,
                            bitPerfectMixerActive = quality.isBitPerfect || (isUsb && bitPerfectRouting),
                            bitPerfectRequested = bitPerfectRouting,
                            hasActiveEq = equalizerEnabled,
                            hasActiveViper = viperConfig.masterEnabled,
                            hasActiveOtherDsp = appSettings.bassBoostEnabled.value || appSettings.virtualizerEnabled.value || appSettings.monoAudioEnabled.value,
                            playbackSpeed = speed,
                            sampleRateCapHz = sampleRateCapHz,
                            surroundDownmixActive = surroundDownmix && quality.sourceChannels > 2,
                            floatPipelineActive = floatEnabled,
                            driftPpm = streamHealth.driftPpm,
                            glitchCount = streamHealth.glitchCount
                        )
                    )
                }
                SignalPathBottomSheet(
                    report = report,
                    onDismissRequest = { showSignalPathDialog = false }
                )
            }
        }
    }
}

private data class QualityMetadataItem(
    val label: String,
    val value: String,
    val icon: Any,
    val isWide: Boolean = false,
    val onClick: (() -> Unit)? = null
)

@Composable
private fun QualityMetadataSection(
    title: String,
    icon: MaterialSymbolIcon,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

@Composable
private fun QualityMetadataGridCard(
    title: String,
    icon: MaterialSymbolIcon,
    items: List<QualityMetadataItem>,
    tint: Color = MaterialTheme.colorScheme.primary,
    tintContainer: Color = MaterialTheme.colorScheme.primaryContainer
) {
    val visibleItems = items.filter { it.value.isNotBlank() && !it.value.equals("Unknown", ignoreCase = true) }
    if (visibleItems.isNotEmpty()) {
        QualityMetadataSection(
            title = title,
            icon = icon,
            tint = tint
        ) {
            val regularCount = visibleItems.count { !it.isWide }
            val lastRegularIndex = visibleItems.indexOfLast { !it.isWide }
            val isFullWidth: (Int) -> Boolean = { index ->
                visibleItems[index].isWide ||
                    (index == lastRegularIndex && regularCount % 2 == 1)
            }
            val (cells, totalRows) = computeGridCellInfo(visibleItems.size, isFullWidth)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                userScrollEnabled = false,
                modifier = Modifier.height(
                    MusicDimensions.infoTileHeight * totalRows.coerceAtLeast(1) +
                        4.dp * (totalRows.coerceAtLeast(1) - 1)
                )
            ) {
                itemsIndexed(
                    items = visibleItems,
                    span = { index, _ -> if (isFullWidth(index)) GridItemSpan(2) else GridItemSpan(1) }
                ) { index, item ->
                    val (row, col) = cells[index]
                    QualityInfoGridItem(
                        item = item,
                        shape = connectedGridItemShape(row, col, totalRows),
                        tint = tint,
                        tintContainer = tintContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityInfoGridItem(
    item: QualityMetadataItem,
    shape: RoundedCornerShape,
    tint: Color = MaterialTheme.colorScheme.primary,
    tintContainer: Color = MaterialTheme.colorScheme.primaryContainer
) {
    val tileColor = lerp(
        MaterialTheme.colorScheme.surfaceContainer,
        tintContainer,
        0.35f
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(MusicDimensions.infoTileHeight)
            .clip(shape)
            .then(if (item.onClick != null) Modifier.clickable(onClick = item.onClick) else Modifier),
        shape = shape,
        color = tileColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val icon = item.icon) {
                is MaterialSymbolIcon -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint.copy(alpha = MusicDimensions.infoTileWatermarkAlpha),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(
                            x = MusicDimensions.infoTileBackdropCut,
                            y = MusicDimensions.infoTileBackdropCut
                        )
                        .size(MusicDimensions.infoTileIconSize)
                )
                is Int -> Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = tint.copy(alpha = MusicDimensions.infoTileWatermarkAlpha),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(
                            x = MusicDimensions.infoTileBackdropCut,
                            y = MusicDimensions.infoTileBackdropCut
                        )
                        .size(MusicDimensions.infoTileIconSize)
                )
            }

            MarqueeText(
                text = item.value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                ),
                gradientEdgeColor = tileColor,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = MusicDimensions.infoTileInset,
                        end = MusicDimensions.infoTileInset,
                        bottom = MusicDimensions.infoTileInset
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(
                        top = MusicDimensions.infoTileInset,
                        start = MusicDimensions.infoTileInset,
                        end = MusicDimensions.infoTileInset
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = tint,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.onClick != null) {
                    Icon(
                        imageVector = MaterialSymbolIcon("chevron_right"),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = tint.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun computeGridCellInfo(
    itemCount: Int,
    isFullWidth: (Int) -> Boolean
): Pair<List<Pair<Int, Int>>, Int> {
    val cells = mutableListOf<Pair<Int, Int>>()
    var row = 0
    var col = 0
    for (i in 0 until itemCount) {
        if (isFullWidth(i)) {
            if (col == 1) {
                row++
                col = 0
            }
            cells.add(Pair(row, 2))
            row++
            col = 0
        } else {
            cells.add(Pair(row, col))
            col++
            if (col == 2) {
                col = 0
                row++
            }
        }
    }
    val totalRows = if (col > 0) row + 1 else row
    return Pair(cells, totalRows)
}

private fun connectedGridItemShape(row: Int, col: Int, totalRows: Int): RoundedCornerShape {
    val isFirstRow = row == 0
    val isLastRow = row == totalRows - 1
    return when {
        col == 2 -> when {
            isFirstRow && isLastRow -> RoundedCornerShape(24.dp)
            isFirstRow -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            isLastRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(8.dp)
        }
        col == 0 -> when {
            isFirstRow && isLastRow -> RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
            isFirstRow -> RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            isLastRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
            else -> RoundedCornerShape(8.dp)
        }
        else -> when {
            isFirstRow && isLastRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
            isFirstRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            isLastRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(8.dp)
        }
    }
}
