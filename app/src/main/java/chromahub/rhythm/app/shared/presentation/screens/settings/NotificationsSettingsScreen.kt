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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
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
    val streamingNotificationsEnabled by appSettings.streamingNotificationsEnabled.collectAsState()
    val updateNotificationsEnabled by appSettings.updateNotificationsEnabled.collectAsState()
    val rhythmGuardAlertNotificationsEnabled by appSettings.rhythmGuardAlertNotificationsEnabled.collectAsState()
    val rhythmGuardTimerNotificationsEnabled by appSettings.rhythmGuardTimerNotificationsEnabled.collectAsState()
    val rhythmPulseNotificationsEnabled by appSettings.rhythmPulseNotificationsEnabled.collectAsState()
    val rhythmPulseNotificationIntervalHours by appSettings.rhythmPulseNotificationIntervalHours.collectAsState()

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
        else -> context.getString(R.string.settings_check_interval_value, rhythmPulseNotificationIntervalHours)
    }

    fun openSystemNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
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
                title = "Sleep Timer",
                items = listOf(
                    SettingItem(
                        RhythmIcons.AccessTime,
                        "Sleep Timer Status",
                        "Show notification countdown and controls when sleep timer is active",
                        toggleState = sleepTimerNotificationsEnabled,
                        onToggleChange = { appSettings.setSleepTimerNotificationsEnabled(it) }
                    )
                )
            ),
            SettingGroup(
                title = "Cloud & Streaming",
                items = listOf(
                    SettingItem(
                        MaterialSymbolIcon("cloud"),
                        "Cloud Sync Alerts",
                        "Show notifications for streaming service connection and sync progress",
                        toggleState = streamingNotificationsEnabled,
                        onToggleChange = { appSettings.setStreamingNotificationsEnabled(it) }
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
        AlertDialog(
            onDismissRequest = { showPulseIntervalDialog = false },
            icon = {
                Icon(
                    imageVector = RhythmIcons.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(stringResource(R.string.settings_rhythm_pulse_interval_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val intervals = listOf(
                        6 to stringResource(R.string.settings_interval_every_6_hours),
                        12 to stringResource(R.string.settings_interval_every_12_hours),
                        24 to stringResource(R.string.settings_interval_once_a_day),
                        48 to stringResource(R.string.settings_interval_every_48_hours),
                        72 to stringResource(R.string.settings_interval_every_72_hours)
                    )

                    intervals.forEach { (hours, label) ->
                        Card(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                appSettings.setRhythmPulseNotificationIntervalHours(hours)
                                showPulseIntervalDialog = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (rhythmPulseNotificationIntervalHours == hours)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (rhythmPulseNotificationIntervalHours == hours) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (rhythmPulseNotificationIntervalHours == hours) {
                                    Icon(
                                        imageVector = RhythmIcons.CheckCircle,
                                        contentDescription = stringResource(R.string.ui_selected),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showPulseIntervalDialog = false }) {
                    Text(stringResource(R.string.ui_close))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}
