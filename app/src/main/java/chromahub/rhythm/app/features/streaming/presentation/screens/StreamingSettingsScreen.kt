/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.groupedBottomSheetItemShape

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.core.domain.model.StreamingQuality
import chromahub.rhythm.app.core.utils.NetworkUtils
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch
import chromahub.rhythm.app.features.streaming.presentation.model.StreamingServiceOptions
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType

import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import androidx.compose.foundation.layout.PaddingValues

@Composable
fun StreamingSettingsScreen(
    viewModel: StreamingMusicViewModel,
    onOpenGlobalSettings: () -> Unit,
    onConfigureService: (String) -> Unit,
    onSwitchToLocalMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val appSettings = remember { AppSettings.getInstance(context) }

    val selectedService by appSettings.streamingService.collectAsState()
    val appMode by appSettings.appMode.collectAsState()
    val sessions by viewModel.serviceSessions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val streamingQuality by appSettings.streamingQuality.collectAsState()
    val allowCellularStreaming by appSettings.allowCellularStreaming.collectAsState()
    val offlineMode by appSettings.offlineMode.collectAsState()
    val listeningTimeMs by appSettings.listeningTime.collectAsState()
    val songsPlayed by appSettings.songsPlayed.collectAsState()
    val dailyListeningStats by appSettings.dailyListeningStats.collectAsState()
    val rhythmGuardMode by appSettings.rhythmGuardMode.collectAsState()
    val rhythmGuardAge by appSettings.rhythmGuardAge.collectAsState()
    val rhythmGuardAlertThresholdMinutes by appSettings.rhythmGuardAlertThresholdMinutes.collectAsState()
    val selectedSession = sessions[selectedService]
    val selectedServiceConnected = selectedSession?.isConnected == true

    val rhythmGuardPolicy = remember(rhythmGuardAge) { appSettings.getRhythmGuardPolicy(rhythmGuardAge) }
    val recommendedDailyMinutes = remember(
        rhythmGuardMode,
        rhythmGuardAlertThresholdMinutes,
        rhythmGuardPolicy
    ) {
        if (rhythmGuardMode == AppSettings.RHYTHM_GUARD_MODE_MANUAL) {
            rhythmGuardAlertThresholdMinutes.takeIf { it > 0 } ?: rhythmGuardPolicy.recommendedDailyMinutes
        } else {
            rhythmGuardPolicy.recommendedDailyMinutes
        }
    }
    val todayListeningMinutes = remember(dailyListeningStats, songsPlayed, listeningTimeMs) {
        appSettings.estimateRhythmGuardTodayListeningMinutes(
            dailyListeningStats = dailyListeningStats,
            songsPlayed = songsPlayed,
            listeningTimeMs = listeningTimeMs
        )
    }
    val rhythmGuardModeLabel = remember(rhythmGuardMode) {
        rhythmGuardModeDisplayName(rhythmGuardMode)
    }

    var showServiceSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }

    fun setGoMode(enabled: Boolean) {
        HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
        if (enabled) {
            appSettings.setAppMode("STREAMING")
        } else {
            onSwitchToLocalMode()
        }
    }

    val canStream = remember(allowCellularStreaming, offlineMode) {
        NetworkUtils.canStream(context, allowCellularStreaming) && !offlineMode
    }

    CollapsibleHeaderScreen(
        title = stringResource(id = R.string.streaming_settings_title),
        headerDisplayMode = 1
    ) { contentModifier ->
        LazyColumn(
            modifier = modifier
                .then(contentModifier)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StreamingStatusCard(
                    selectedServiceName = selectedServiceLabel(selectedService, context),
                    isConnected = selectedServiceConnected,
                    isLoading = isLoading,
                    username = selectedSession?.username.orEmpty(),
                    serverUrl = selectedSession?.serverUrl.orEmpty()
                )
            }
            
            if (!canStream) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.streamingsettingsscreen_streaming_restricted),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        offlineMode -> "Offline mode enabled - only cached content available"
                                        !NetworkUtils.canStream(context, allowCellularStreaming) ->
                                            if (NetworkUtils.isCellularConnected(context) && !allowCellularStreaming)
                                                "Cellular streaming disabled - connect to WiFi"
                                            else
                                                "No network connection available"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            item {
                Material3SettingsGroup(
                    title = stringResource(id = R.string.streaming_settings_group_services),
                    items = listOf(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("cloud_queue"),
                            title = { Text(text = stringResource(id = R.string.exp_go_mode)) },
                            description = { Text(text = stringResource(id = R.string.exp_go_mode_desc)) },
                            onClick = {
                                // Set settings host to open the Go Settings pane, then open settings
                                appSettings.setInitialSettingsSubroute(chromahub.rhythm.app.shared.presentation.screens.settings.SettingsRoutes.GO_SETTINGS)
                                onOpenGlobalSettings()
                            }
                        )
                    )
                )
            }

            item {
                Material3SettingsGroup(
                    title = stringResource(id = R.string.streaming_settings_group_wellbeing),
                    items = listOf(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("auto_graph"),
                            title = {
                                Text(text = stringResource(id = R.string.settings_rhythm_stats))
                            },
                            description = {
                                Text(
                                    text = "${stringResource(id = R.string.settings_rhythm_stats_desc)} | ${formatListeningDurationShort(listeningTimeMs)}"
                                )
                            },
                            onClick = onOpenGlobalSettings
                        ),
                        Material3SettingsItem(
                            icon = RhythmIcons.Security,
                            title = {
                                Text(text = stringResource(id = R.string.settings_rhythm_guard))
                            },
                            description = {
                                Text(
                                    text = "$rhythmGuardModeLabel | ${stringResource(id = R.string.streaming_home_guard_exposure, todayListeningMinutes, recommendedDailyMinutes)}"
                                )
                            },
                            onClick = onOpenGlobalSettings
                        )
                    )
                )
            }

            item {
                Material3SettingsGroup(
                    title = stringResource(id = R.string.streaming_settings_group_actions),
                    items = listOf(
                        Material3SettingsItem(
                            icon = RhythmIcons.Settings,
                            title = {
                                Text(text = stringResource(id = R.string.streaming_open_full_settings))
                            },
                            description = {
                                Text(text = stringResource(id = R.string.streaming_open_full_settings_desc))
                            },
                            onClick = onOpenGlobalSettings
                        ),
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("swap_horiz"),
                            title = {
                                Text(text = stringResource(id = R.string.streaming_switch_to_local_mode))
                            },
                            description = {
                                Text(text = stringResource(id = R.string.streaming_switch_to_local_mode_desc))
                            },
                            onClick = onSwitchToLocalMode
                        )
                    )
                )
            }

            item {
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    if (showServiceSheet) {
        ServiceSelectionBottomSheet(
            selectedService = selectedService,
            sessions = sessions,
            onDismiss = { showServiceSheet = false },
            onSelect = { serviceId ->
                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                appSettings.setStreamingService(serviceId)
                showServiceSheet = false
            }
        )
    }

    if (showQualitySheet) {
        QualitySelectionBottomSheet(
            selectedQuality = normalizeStreamingQuality(streamingQuality),
            onDismiss = { showQualitySheet = false },
            onSelect = { quality ->
                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                viewModel.setStreamingQuality(StreamingQuality.valueOf(quality))
                showQualitySheet = false
            }
        )
    }
}

@Composable
private fun StreamingStatusCard(
    selectedServiceName: String,
    isConnected: Boolean,
    isLoading: Boolean,
    username: String,
    serverUrl: String
) {
    val badgeText = when {
        isLoading -> stringResource(id = R.string.streaming_status_badge_refreshing)
        isConnected -> stringResource(id = R.string.streaming_status_badge_connected)
        else -> stringResource(id = R.string.streaming_status_badge_pending)
    }

    val badgeContainerColor = when {
        isLoading -> MaterialTheme.colorScheme.primaryContainer
        isConnected -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val badgeContentColor = when {
        isLoading -> MaterialTheme.colorScheme.onPrimaryContainer
        isConnected -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MaterialSymbolIcon("cloud_queue"),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.streaming_status_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            id = R.string.streaming_status_selected_service,
                            selectedServiceName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = badgeContainerColor,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeContentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (isConnected) {
                if (username.isNotBlank()) {
                    Text(
                        text = stringResource(
                            id = R.string.streaming_service_setup_connected_as,
                            username
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (serverUrl.isNotBlank()) {
                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = stringResource(id = R.string.streaming_status_connect_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServiceSelectionBottomSheet(
    selectedService: String,
    sessions: Map<String, chromahub.rhythm.app.features.streaming.data.repository.StreamingServiceSession>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = stringResource(id = R.string.streaming_settings_preferred_service),
            subtitle = stringResource(id = R.string.streaming_settings_service_sheet_desc),
            visible = true
        )

        val scrollState = rememberScrollState()

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
                StreamingServiceOptions.defaults.forEachIndexed { index, option ->
                    val isSelected = selectedService == option.id
                    val isConnected = sessions[option.id]?.isConnected == true

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = groupedBottomSheetItemShape(index, StreamingServiceOptions.defaults.size),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ),
                        onClick = { onSelect(option.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("cloud_queue"),
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(if (isSelected) 30.dp else 26.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = option.nameRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = if (isConnected) {
                                        stringResource(id = R.string.streaming_status_connected)
                                    } else {
                                        stringResource(id = R.string.streaming_status_not_connected)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = RhythmIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(20.dp)
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
private fun QualitySelectionBottomSheet(
    selectedQuality: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = stringResource(id = R.string.streaming_settings_quality),
            subtitle = stringResource(id = R.string.streaming_settings_quality_sheet_desc),
            visible = true
        )

        val scrollState = rememberScrollState()

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
                streamingQualityOptions.forEachIndexed { index, option ->
                    val isSelected = selectedQuality == option.value

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = groupedBottomSheetItemShape(index, streamingQualityOptions.size),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ),
                        onClick = { onSelect(option.value) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("high_quality"),
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(if (isSelected) 30.dp else 26.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = option.titleRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = stringResource(id = option.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = RhythmIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun selectedServiceLabel(selectedService: String, context: Context): String {
    val matching = StreamingServiceOptions.defaults.firstOrNull { it.id == selectedService }
    return matching?.let { context.getString(it.nameRes) } ?: context.getString(R.string.streaming_not_selected)
}

private fun normalizeStreamingQuality(rawValue: String): String {
    val normalized = rawValue.uppercase()
    return if (streamingQualityOptions.any { it.value == normalized }) normalized else "HIGH"
}

private fun streamingQualityLabel(quality: String, context: Context): String {
    return when (normalizeStreamingQuality(quality)) {
        "LOW" -> context.getString(R.string.streaming_quality_low)
        "NORMAL" -> context.getString(R.string.streaming_quality_normal)
        "HIGH" -> context.getString(R.string.streaming_quality_high)
        "LOSSLESS" -> context.getString(R.string.streaming_quality_lossless)
        else -> quality
    }
}

private fun rhythmGuardModeDisplayName(mode: String): String {
    return when (mode) {
        AppSettings.RHYTHM_GUARD_MODE_AUTO -> "Adaptive Guard"
        AppSettings.RHYTHM_GUARD_MODE_MANUAL -> "Manual Guard"
        else -> "Guard Off"
    }
}

private fun formatListeningDurationShort(durationMs: Long): String {
    if (durationMs <= 0L) return "0m"

    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L

    return when {
        hours <= 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

private data class StreamingQualityOption(
    val value: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int
)

private val streamingQualityOptions = listOf(
    StreamingQualityOption(
        value = "LOW",
        titleRes = R.string.streaming_quality_low,
        descriptionRes = R.string.streaming_quality_low_desc
    ),
    StreamingQualityOption(
        value = "NORMAL",
        titleRes = R.string.streaming_quality_normal,
        descriptionRes = R.string.streaming_quality_normal_desc
    ),
    StreamingQualityOption(
        value = "HIGH",
        titleRes = R.string.streaming_quality_high,
        descriptionRes = R.string.streaming_quality_high_desc
    ),
    StreamingQualityOption(
        value = "LOSSLESS",
        titleRes = R.string.streaming_quality_lossless,
        descriptionRes = R.string.streaming_quality_lossless_desc
    )
)
