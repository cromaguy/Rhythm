/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.groupedBottomSheetItemShape
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.ui.utils.LazyListStateSaver
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils

@Composable
fun NotificationsSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val hapticFeedback = LocalHapticFeedback.current

    // Preference states
    val useCustomNotification by appSettings.useCustomNotification.collectAsState()
    val showCodecNotifications by appSettings.showCodecNotifications.collectAsState()
    val libraryOperationsNotificationsEnabled by appSettings.libraryOperationsNotificationsEnabled.collectAsState()
    val sleepTimerNotificationsEnabled by appSettings.sleepTimerNotificationsEnabled.collectAsState()
    val updateNotificationsEnabled by appSettings.updateNotificationsEnabled.collectAsState()
    val rhythmGuardAlertNotificationsEnabled by appSettings.rhythmGuardAlertNotificationsEnabled.collectAsState()
    val rhythmGuardTimerNotificationsEnabled by appSettings.rhythmGuardTimerNotificationsEnabled.collectAsState()
    val rhythmPulseNotificationsEnabled by appSettings.rhythmPulseNotificationsEnabled.collectAsState()
    val rhythmPulseNotificationIntervalHours by appSettings.rhythmPulseNotificationIntervalHours.collectAsState()
    val broadcastStatusEnabled by appSettings.broadcastStatusEnabled.collectAsState()
    val bluetoothLyricsEnabled by appSettings.bluetoothLyricsEnabled.collectAsState()

    var showPulseIntervalDialog by remember { mutableStateOf(false) }

    // System notification permission check
    var isPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val pulseIntervalLabel = when (rhythmPulseNotificationIntervalHours) {
        6 -> context.getString(R.string.settings_interval_every_6_hours)
        12 -> context.getString(R.string.settings_interval_every_12_hours)
        24 -> context.getString(R.string.settings_interval_once_a_day)
        48 -> context.getString(R.string.settings_interval_every_48_hours)
        72 -> context.getString(R.string.settings_interval_every_72_hours)
        else -> pluralStringResource(R.plurals.settings_check_interval_value, rhythmPulseNotificationIntervalHours, rhythmPulseNotificationIntervalHours)
    }

    fun openSystemNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }

    CollapsibleHeaderScreen(
        title = stringResource(R.string.settings_notifications),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        val settingGroups = listOf(
            SettingGroup(
                title = stringResource(R.string.settings_notifications_library_group),
                items = listOf(
                    SettingItem(
                        RhythmIcons.Library,
                        stringResource(R.string.settings_library_operations_notifications),
                        stringResource(R.string.settings_library_operations_notifications_desc),
                        toggleState = libraryOperationsNotificationsEnabled,
                        onToggleChange = { appSettings.setLibraryOperationsNotificationsEnabled(it) }
                    )
                )
            ),
            SettingGroup(
                title = stringResource(R.string.sleep_timer),
                items = listOf(
                    SettingItem(
                        RhythmIcons.AccessTime,
                        stringResource(R.string.notifications_sleep_timer_status),
                        stringResource(R.string.notifications_sleep_timer_status_desc),
                        toggleState = sleepTimerNotificationsEnabled,
                        onToggleChange = { appSettings.setSleepTimerNotificationsEnabled(it) }
                    )
                )
            ),
            SettingGroup(
                title = stringResource(R.string.settings_notifications_updates_group),
                items = listOf(
                    SettingItem(
                        RhythmIcons.Update,
                        stringResource(R.string.settings_notifications_updates_group),
                        stringResource(R.string.settings_update_notifications_merged_desc),
                        toggleState = updateNotificationsEnabled,
                        onToggleChange = {
                            appSettings.setUpdateNotificationsEnabled(it)
                            appSettings.setUpdateStatusNotificationsEnabled(it)
                        }
                    )
                )
            ),
            SettingGroup(
                title = stringResource(R.string.settings_notifications_rhythm_guard_group),
                items = listOf(
                    SettingItem(
                        RhythmIcons.Warning,
                        stringResource(R.string.settings_rhythm_guard_alert_notifications),
                        stringResource(R.string.settings_rhythm_guard_alert_notifications_desc),
                        toggleState = rhythmGuardAlertNotificationsEnabled,
                        onToggleChange = { appSettings.setRhythmGuardAlertNotificationsEnabled(it) }
                    ),
                    SettingItem(
                        RhythmIcons.AccessTime,
                        stringResource(R.string.settings_rhythm_guard_timer_notifications),
                        stringResource(R.string.settings_rhythm_guard_timer_notifications_desc),
                        toggleState = rhythmGuardTimerNotificationsEnabled,
                        onToggleChange = { appSettings.setRhythmGuardTimerNotificationsEnabled(it) }
                    )
                )
            ),
            SettingGroup(
                title = stringResource(R.string.settings_notifications_rhythm_pulse_group),
                items = listOf(
                    SettingItem(
                        MaterialSymbolIcon("celebration"),
                        stringResource(R.string.settings_rhythm_pulse_notifications),
                        stringResource(R.string.settings_rhythm_pulse_notifications_desc),
                        toggleState = rhythmPulseNotificationsEnabled,
                        onToggleChange = { appSettings.setRhythmPulseNotificationsEnabled(it) }
                    ),
                    SettingItem(
                        RhythmIcons.AccessTime,
                        stringResource(R.string.settings_rhythm_pulse_interval),
                        pulseIntervalLabel,
                        onClick = { showPulseIntervalDialog = true },
                        enabled = rhythmPulseNotificationsEnabled
                    )
                )
            ),
            SettingGroup(
                title = stringResource(R.string.settings_notifications_system_group),
                items = listOf(
                    SettingItem(
                        icon = MaterialSymbolIcon("wifi"),
                        title = stringResource(R.string.broadcast_status_enabled),
                        description = stringResource(R.string.broadcast_status_desc),
                        toggleState = broadcastStatusEnabled,
                        onToggleChange = { appSettings.setBroadcastStatusEnabled(it) }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("lyrics"),
                        title = stringResource(R.string.bluetooth_lyrics_enabled),
                        description = stringResource(R.string.bluetooth_lyrics_desc),
                        toggleState = bluetoothLyricsEnabled,
                        onToggleChange = {
                            appSettings.setBluetoothLyricsEnabled(it)
                            if (it && !broadcastStatusEnabled) {
                                appSettings.setBroadcastStatusEnabled(true)
                            }
                        }
                    ),
                    SettingItem(
                        RhythmIcons.Settings,
                        stringResource(R.string.settings_system_notification_channels),
                        stringResource(R.string.settings_system_notification_channels_desc),
                        onClick = { openSystemNotificationSettings() }
                    )
                )
            )
        )

        val lazyListState = rememberSaveable(saver = LazyListStateSaver) {
            androidx.compose.foundation.lazy.LazyListState()
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            state = lazyListState,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp)
        ) {
            // System Notification Permission Warning Banner (if permission missing)
            if (!isPermissionGranted) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = stringResource(R.string.settings_notification_permission_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_notification_permission_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                            Button(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
                                    openSystemNotificationSettings()
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_notification_permission_grant),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Setting Groups
            items(
                items = settingGroups,
                key = { "setting_${it.title}" },
                contentType = { "settingGroup" }
            ) { group ->
                Spacer(modifier = Modifier.height(20.dp))

                val materialItems = group.items.map { item ->
                    toMaterial3SettingsItem(
                        context = context,
                        item = item,
                        hapticFeedback = hapticFeedback
                    )
                }

                Material3SettingsGroup(
                    title = group.title,
                    items = materialItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }
        }
    }

    if (showPulseIntervalDialog) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { showPulseIntervalDialog = false },
            sheetState = sheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
            },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            StandardBottomSheetHeader(
                title = stringResource(R.string.settings_rhythm_pulse_interval),
                subtitle = stringResource(R.string.settings_rhythm_pulse_notifications_desc),
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
                    val intervals = listOf(
                        6 to stringResource(R.string.settings_interval_every_6_hours),
                        12 to stringResource(R.string.settings_interval_every_12_hours),
                        24 to stringResource(R.string.settings_interval_once_a_day),
                        48 to stringResource(R.string.settings_interval_every_48_hours),
                        72 to stringResource(R.string.settings_interval_every_72_hours)
                    )

                    intervals.forEachIndexed { index, (hours, label) ->
                        val isSelected = rhythmPulseNotificationIntervalHours == hours

                        Card(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                appSettings.setRhythmPulseNotificationIntervalHours(hours)
                                showPulseIntervalDialog = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = groupedBottomSheetItemShape(index, intervals.size),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.AccessTime,
                                    contentDescription = null,
                                    tint = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(if (isSelected) 30.dp else 26.dp)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isSelected) {
                                    Icon(
                                        imageVector = RhythmIcons.CheckCircle,
                                        contentDescription = stringResource(R.string.ui_selected),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
}
