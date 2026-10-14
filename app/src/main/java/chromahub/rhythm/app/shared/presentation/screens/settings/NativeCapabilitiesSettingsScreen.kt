/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.core.device.AudioOutputCategory
import chromahub.rhythm.app.core.device.DeviceCapabilitiesProvider
import chromahub.rhythm.app.core.device.HiFiCapabilityChecker
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveCookieEmptyState
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import java.util.Locale
import kotlin.math.roundToInt

data class NativeGridItem(
    val label: String,
    val value: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val isHighlighted: Boolean = false,
    val onClick: (() -> Unit)? = null
)

@Composable
fun NativeCapabilitiesSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val copyToClipboard: (String, String) -> Unit = { label, text ->
        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.about_copied_format, label), Toast.LENGTH_SHORT).show()
    }

    val systemSummary = remember(context) { DeviceCapabilitiesProvider.getSystemSummary(context) }
    val capabilities = remember(context) { DeviceCapabilitiesProvider.getCapabilities(context) }
    val isFloatSupported = remember { HiFiCapabilityChecker.isSupported() }
    val allDecoders = remember(context) { DeviceCapabilitiesProvider.getAudioDecoders() }
    val hardwareDecoders = remember(allDecoders) { allDecoders.filter { it.isHardwareAccelerated } }

    val storageStats = remember {
        runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            total to free
        }.getOrDefault(0L to 0L)
    }
    val totalStorageBytes = storageStats.first
    val freeStorageBytes = storageStats.second

    val displayMetrics = context.resources.displayMetrics
    val refreshRate = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.display.refreshRate }.getOrDefault(60f)
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay?.refreshRate ?: 60f
            }.getOrDefault(60f)
        }
    }

    val hardwareItems = remember(systemSummary, totalStorageBytes, freeStorageBytes, refreshRate) {
        listOf(
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_device_model),
                value = "${systemSummary.brand} ${systemSummary.model}",
                subtitle = context.getString(R.string.native_manufacturer, Build.MANUFACTURER),
                onClick = { copyToClipboard("Device", "${systemSummary.brand} ${systemSummary.model}") }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_soc_hardware),
                value = Build.HARDWARE.ifBlank { "Unknown" },
                subtitle = context.getString(R.string.native_board, Build.BOARD),
                onClick = { copyToClipboard("Hardware", "${Build.HARDWARE} (${Build.BOARD})") }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_cpu_architecture),
                value = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown",
                subtitle = Build.SUPPORTED_ABIS.drop(1).take(2).joinToString(", ").ifBlank { null },
                onClick = { copyToClipboard("Architecture", Build.SUPPORTED_ABIS.joinToString(", ")) }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_platform_release),
                value = "Android ${Build.VERSION.RELEASE}",
                subtitle = context.getString(R.string.native_api_level, Build.VERSION.SDK_INT),
                onClick = { copyToClipboard("Android Version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})") }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_system_ram),
                value = formatNativeSize(systemSummary.totalRamBytes),
                subtitle = context.getString(R.string.native_available, formatNativeSize(systemSummary.availableRamBytes)),
                onClick = { copyToClipboard("RAM", "${formatNativeSize(systemSummary.totalRamBytes)} (${formatNativeSize(systemSummary.availableRamBytes)} Available)") }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_internal_storage),
                value = formatNativeSize(totalStorageBytes),
                subtitle = "${formatNativeSize(freeStorageBytes)} Free",
                onClick = { copyToClipboard("Storage", "${formatNativeSize(totalStorageBytes)} (${formatNativeSize(freeStorageBytes)} Free)") }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_display_screen),
                value = "${displayMetrics.widthPixels} × ${displayMetrics.heightPixels}",
                subtitle = "${refreshRate.roundToInt()} Hz • ${displayMetrics.densityDpi} DPI",
                onClick = { copyToClipboard("Display", "${displayMetrics.widthPixels}x${displayMetrics.heightPixels} @ ${refreshRate.roundToInt()}Hz") }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_security_patch),
                value = Build.VERSION.SECURITY_PATCH.ifBlank { context.getString(R.string.native_current) },
                subtitle = "Build ID: ${Build.ID}",
                onClick = { copyToClipboard("Security Patch", Build.VERSION.SECURITY_PATCH) }
            )
        )
    }

    val pipelineItems = remember(capabilities, isFloatSupported) {
        val isOffloadSupported = capabilities.offloadSupportedFormats.isNotEmpty()
        listOf(
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_native_hal_rate),
                value = "${capabilities.nativeSampleRate} Hz",
                badge = "HAL",
                isHighlighted = true,
                subtitle = context.getString(chromahub.rhythm.app.R.string.ui_hardware_clock_rate),
                onClick = { copyToClipboard("Native HAL Rate", "${capabilities.nativeSampleRate} Hz") }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_buffer_frame_burst),
                value = "${capabilities.framesPerBuffer} frames",
                badge = context.getString(chromahub.rhythm.app.R.string.ui_low_latency),
                subtitle = context.getString(chromahub.rhythm.app.R.string.ui_kernel_burst_quantum),
                onClick = { copyToClipboard("Native Buffer Size", "${capabilities.framesPerBuffer} frames") }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_dsp_audio_offload),
                value = if (isOffloadSupported) context.getString(R.string.native_formats_supported, capabilities.offloadSupportedFormats.size) else context.getString(R.string.native_unsupported),
                badge = if (isOffloadSupported) "HW DSP" else context.getString(R.string.native_disabled),
                isHighlighted = isOffloadSupported,
                subtitle = if (isOffloadSupported) capabilities.offloadSupportedFormats.joinToString(", ") else "Direct DAC power saving",
                onClick = { copyToClipboard("DSP Offload", if (isOffloadSupported) capabilities.offloadSupportedFormats.joinToString(", ") else context.getString(R.string.native_unsupported)) }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_pro_audio_stack),
                value = if (capabilities.isProAudioSupported) context.getString(R.string.native_supported) else context.getString(R.string.native_unsupported),
                badge = context.getString(chromahub.rhythm.app.R.string.ui_feature),
                isHighlighted = capabilities.isProAudioSupported,
                subtitle = context.getString(chromahub.rhythm.app.R.string.ui_pro_level_audio_routing),
                onClick = { copyToClipboard("Pro Audio", if (capabilities.isProAudioSupported) context.getString(R.string.native_supported) else context.getString(R.string.native_unsupported)) }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_fast_track_audio),
                value = if (capabilities.isLowLatencySupported) context.getString(R.string.native_supported) else context.getString(R.string.native_unsupported),
                badge = context.getString(chromahub.rhythm.app.R.string.ui_fast_track),
                isHighlighted = capabilities.isLowLatencySupported,
                subtitle = context.getString(chromahub.rhythm.app.R.string.ui_direct_mixer_bypass),
                onClick = { copyToClipboard("Low Latency", if (capabilities.isLowLatencySupported) context.getString(R.string.native_supported) else context.getString(R.string.native_unsupported)) }
            ),
            NativeGridItem(
                label = context.getString(chromahub.rhythm.app.R.string.ui_32_bit_float_pcm),
                value = if (isFloatSupported) context.getString(R.string.native_supported) else context.getString(R.string.native_unsupported),
                badge = "IEEE 754",
                isHighlighted = isFloatSupported,
                subtitle = context.getString(chromahub.rhythm.app.R.string.ui_full_dynamic_precision),
                onClick = { copyToClipboard("32-Bit Float PCM", if (isFloatSupported) context.getString(R.string.native_supported) else context.getString(R.string.native_unsupported)) }
            )
        )
    }

    val endpointItems = remember(capabilities.outputRoutes) {
        if (capabilities.outputRoutes.isNotEmpty()) {
            capabilities.outputRoutes.map { route ->
                NativeGridItem(
                    label = route.name,
                    value = route.category.name,
                    badge = route.category.name.uppercase(Locale.US),
                    isHighlighted = route.category == AudioOutputCategory.BuiltIn || route.category == AudioOutputCategory.Bluetooth || route.category == AudioOutputCategory.Usb,
                    subtitle = "Route ID: ${route.id}",
                    onClick = { copyToClipboard("Audio Route", "${route.name} (${route.category})") }
                )
            }
        } else {
            listOf(
                NativeGridItem(
                    label = context.getString(chromahub.rhythm.app.R.string.ui_built_in_speaker),
                    value = "Available",
                    badge = context.getString(chromahub.rhythm.app.R.string.ui_speaker),
                    isHighlighted = true,
                    subtitle = context.getString(chromahub.rhythm.app.R.string.ui_device_internal_speaker)
                ),
                NativeGridItem(
                    label = context.getString(chromahub.rhythm.app.R.string.ui_audio_headset),
                    value = "Ready",
                    badge = context.getString(chromahub.rhythm.app.R.string.ui_wired),
                    subtitle = context.getString(chromahub.rhythm.app.R.string.ui_3_5mm_usb_type_c_jack)
                )
            )
        }
    }

    val hwDecoderItems = remember(hardwareDecoders) {
        hardwareDecoders.map { dec ->
            NativeGridItem(
                label = dec.name,
                value = getNativeMimeDisplayName(dec.mimeType),
                badge = context.getString(chromahub.rhythm.app.R.string.ui_hardware),
                isHighlighted = true,
                subtitle = "Max instances: ${dec.maxInstances}",
                onClick = { copyToClipboard("Hardware Decoder", "${dec.name} (${dec.mimeType})") }
            )
        }
    }

    CollapsibleHeaderScreen(
        title = stringResource(R.string.settings_category_native_capabilities_title),
        showBackButton = true,
        onBackClick = onBackClick
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "header_soc") {
                NativeSectionHeader(title = stringResource(R.string.settings_native_caps_soc))
            }
            item(key = "grid_soc") {
                NativeConnectedGrid(items = hardwareItems)
            }

            item(key = "header_pipeline") {
                NativeSectionHeader(title = stringResource(R.string.settings_native_caps_pipeline))
            }
            item(key = "grid_pipeline") {
                NativeConnectedGrid(items = pipelineItems)
            }

            item(key = "header_endpoints") {
                NativeSectionHeader(title = stringResource(R.string.settings_native_caps_endpoints))
            }
            item(key = "grid_endpoints") {
                NativeConnectedGrid(items = endpointItems)
            }

            item(key = "header_hw_decoders") {
                NativeSectionHeader(title = stringResource(R.string.settings_native_caps_hw_decoders))
            }
            if (hwDecoderItems.isEmpty()) {
                item(key = "empty_hw_decoders") {
                    ExpressiveCookieEmptyState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        title = stringResource(R.string.settings_native_caps_no_decoders_title),
                        subtitle = stringResource(R.string.settings_native_caps_no_decoders_desc),
                        mainIcon = MaterialSymbolIcon("memory"),
                        accentIcon = MaterialSymbolIcon("audio_file"),
                        cornerIcon = RhythmIcons.Info,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                item(key = "grid_hw_decoders") {
                    NativeConnectedGrid(items = hwDecoderItems)
                }
            }
        }
    }
}

@Composable
private fun NativeSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 2.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun NativeConnectedGrid(
    items: List<NativeGridItem>,
    columns: Int = 2
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items.chunked(columns).forEachIndexed { rowIndex, rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
            ) {
                rowItems.forEachIndexed { colIndex, item ->
                    val index = rowIndex * columns + colIndex
                    NativeConnectedCard(
                        item = item,
                        shape = getNativeConnectedShape(index, items.size, columns),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                if (rowItems.size < columns) {
                    Spacer(modifier = Modifier.weight((columns - rowItems.size).toFloat()))
                }
            }
        }
    }
}

@Composable
private fun NativeConnectedCard(
    item: NativeGridItem,
    shape: RoundedCornerShape,
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
                .then(
                    if (item.onClick != null) {
                        Modifier.clickable {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            item.onClick.invoke()
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.badge != null) {
                    Surface(
                        color = if (item.isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = item.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun getNativeConnectedShape(index: Int, totalItems: Int, columns: Int = 2): RoundedCornerShape {
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

private fun formatNativeSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown"
    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        val mb = bytes.toDouble() / (1024 * 1024)
        String.format(Locale.US, "%.0f MB", mb)
    }
}

private fun getNativeMimeDisplayName(mime: String): String {
    return when {
        mime.contains("flac", ignoreCase = true) -> "FLAC"
        mime.contains("opus", ignoreCase = true) -> "Opus"
        mime.contains("mp4a-latm", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC"
        mime.contains("mp3", ignoreCase = true) || mime.contains("mpeg", ignoreCase = true) -> "MP3"
        mime.contains("vorbis", ignoreCase = true) -> "Vorbis"
        mime.contains("raw", ignoreCase = true) -> "PCM RAW"
        else -> mime.substringAfterLast("/").substringAfterLast(".").uppercase()
    }
}

@Composable
fun DeviceCapabilitiesSettingsScreen(
    onBackClick: () -> Unit
) {
    NativeCapabilitiesSettingsScreen(onBackClick = onBackClick)
}
