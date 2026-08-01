@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings


import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import androidx.compose.foundation.layout.PaddingValues
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel

@Composable
fun PlaybackSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateTo: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val hapticFeedback = LocalHapticFeedback.current
    val musicViewModel: MusicViewModel = viewModel()

    val replayGain by appSettings.replayGain.collectAsState()
    val repeatModePersistence by appSettings.repeatModePersistence.collectAsState()
    val shuffleModePersistence by appSettings.shuffleModePersistence.collectAsState()
    val useHoursInTimeFormat by appSettings.useHoursInTimeFormat.collectAsState()
    val gaplessEnabled by appSettings.gaplessPlayback.collectAsState()
    val crossfadeEnabled by appSettings.crossfade.collectAsState()
    val crossfadeDuration by appSettings.crossfadeDuration.collectAsState()
    val crossfadeRepeatOne by appSettings.crossfadeRepeatOne.collectAsState()
    val crossfadeOnSkip by appSettings.crossfadeOnSkip.collectAsState()
    val stopPlaybackOnAppClose by appSettings.stopPlaybackOnAppClose.collectAsState()
    val useSystemVolume by appSettings.useSystemVolume.collectAsState()
    val resumeOnDeviceReconnect by appSettings.resumeOnDeviceReconnect.collectAsState()
    val audioOffloadEnabled by appSettings.audioOffloadEnabled.collectAsState()
    val isAudioOffloadActive by appSettings.isAudioOffloadActive.collectAsState()
    val batterySaverEnabled by appSettings.batterySaverEnabled.collectAsState()
    val batterySaverMode by appSettings.batterySaverMode.collectAsState()
    val batterySaverEnableOffload by appSettings.batterySaverEnableOffload.collectAsState()
    val isOffloadEnforced = batterySaverEnabled && (batterySaverMode == "auto" || (batterySaverMode == "manual" && batterySaverEnableOffload))

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_playback_title),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        val settingGroups = listOf(
            SettingGroup(
                title = context.getString(R.string.settings_section_volume_device),
                items = listOf(
                    SettingItem(
                        RhythmIcons.Player.VolumeUp,
                        context.getString(R.string.settings_system_volume),
                        context.getString(R.string.settings_system_volume_desc),
                        toggleState = useSystemVolume,
                        onToggleChange = { musicViewModel.setUseSystemVolumeMode(it) }
                    ),
                    SettingItem(
                        RhythmIcons.Devices.Bluetooth,
                        context.getString(R.string.settings_resume_on_device_reconnect),
                        context.getString(R.string.settings_resume_on_device_reconnect_desc),
                        toggleState = resumeOnDeviceReconnect,
                        onToggleChange = { appSettings.setResumeOnDeviceReconnect(it) }
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.settings_playback_persistence),
                items = listOf(
                    SettingItem(
                        RhythmIcons.Repeat,
                        context.getString(R.string.settings_remember_repeat_mode),
                        context.getString(R.string.settings_remember_repeat_mode_desc),
                        toggleState = repeatModePersistence,
                        onToggleChange = { appSettings.setRepeatModePersistence(it) }
                    ),
                    SettingItem(
                        RhythmIcons.Shuffle,
                        context.getString(R.string.settings_remember_shuffle_mode),
                        context.getString(R.string.settings_remember_shuffle_mode_desc),
                        toggleState = shuffleModePersistence,
                        onToggleChange = { appSettings.setShuffleModePersistence(it) }
                    ),
                    SettingItem(
                        RhythmIcons.Stop,
                        context.getString(R.string.settings_stop_playback_on_close),
                        context.getString(R.string.settings_stop_playback_on_close_desc),
                        toggleState = stopPlaybackOnAppClose,
                        onToggleChange = { appSettings.setStopPlaybackOnAppClose(it) }
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.settings_audio_effects),
                items = listOf(
                    SettingItem(
                        MaterialSymbolIcon("graphic_eq"),
                        context.getString(R.string.settings_gapless_playback),
                        context.getString(R.string.settings_gapless_playback_desc),
                        toggleState = gaplessEnabled,
                        onToggleChange = { appSettings.setGaplessPlayback(it) }
                    ),
                    SettingItem(
                        RhythmIcons.Tune,
                        context.getString(R.string.settings_crossfade),
                        when {
                            isOffloadEnforced -> "Disabled under Lite Mode to conserve battery."
                            isAudioOffloadActive && !crossfadeEnabled -> "${context.getString(R.string.settings_crossfade_desc)}\n(Enabling will disable hardware Audio Offload)"
                            else -> context.getString(R.string.settings_crossfade_desc)
                        },
                        toggleState = if (isOffloadEnforced) false else crossfadeEnabled,
                        onToggleChange = { if (!isOffloadEnforced) appSettings.setCrossfade(it) },
                        enabled = !isOffloadEnforced,
                        data = if (crossfadeEnabled && !isOffloadEnforced) crossfadeDuration else null
                    ),
                    SettingItem(
                        RhythmIcons.Repeat,
                        context.getString(R.string.settings_crossfade_repeat_one),
                        context.getString(R.string.settings_crossfade_repeat_one_desc),
                        toggleState = if (isOffloadEnforced) false else crossfadeRepeatOne,
                        onToggleChange = { if (!isOffloadEnforced) appSettings.setCrossfadeRepeatOne(it) },
                        enabled = crossfadeEnabled && !isOffloadEnforced
                    ),
                    SettingItem(
                        MaterialSymbolIcon("skip_next"),
                        context.getString(R.string.settings_crossfade_on_skip),
                        context.getString(R.string.settings_crossfade_on_skip_desc),
                        toggleState = if (isOffloadEnforced) false else crossfadeOnSkip,
                        onToggleChange = { if (!isOffloadEnforced) appSettings.setCrossfadeOnSkip(it) },
                        enabled = crossfadeEnabled && !isOffloadEnforced
                    ),
                    SettingItem(
                        MaterialSymbolIcon("volume_up"),
                        context.getString(R.string.replay_gain),
                        when {
                            isOffloadEnforced -> "Disabled under Lite Mode to conserve battery."
                            isAudioOffloadActive && !replayGain -> "${context.getString(R.string.replay_gain_desc)}\n(Enabling will disable hardware Audio Offload)"
                            else -> context.getString(R.string.replay_gain_desc)
                        },
                        onClick = { onNavigateTo(SettingsRoutes.REPLAY_GAIN) }
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.settings_section_audio_playback),
                items = listOf(
                    SettingItem(
                        MaterialSymbolIcon("bolt"),
                        context.getString(R.string.settingsscreen_audio_offload),
                        if (isOffloadEnforced) "Enforced under Lite Mode to conserve battery." else context.getString(R.string.settingsscreen_audio_offload_desc),
                        toggleState = if (isOffloadEnforced) true else audioOffloadEnabled,
                        onToggleChange = { if (!isOffloadEnforced) appSettings.setAudioOffloadEnabled(it) },
                        enabled = !isOffloadEnforced
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.settings_time_display),
                items = listOf(
                    SettingItem(
                        RhythmIcons.AccessTime,
                        context.getString(R.string.settings_use_hours),
                        if (useHoursInTimeFormat) context.getString(R.string.settings_use_hours_enabled) else context.getString(R.string.settings_use_hours_disabled),
                        toggleState = useHoursInTimeFormat,
                        onToggleChange = { appSettings.setUseHoursInTimeFormat(it) }
                    )
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
            items(
                items = settingGroups,
                key = { "playback_${it.title}" },
                contentType = { "settingGroup" }
            ) { group ->
                Spacer(modifier = Modifier.height(24.dp))

                val materialItems = group.items.map { item ->
                    Material3SettingsItem(
                        icon = item.icon,
                        title = { Text(item.title) },
                        description = {
                            Column {
                                item.description?.let { desc -> Text(desc) }

                                if (item.data is Float && item.toggleState == true) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = context.getString(R.string.settings_crossfade_duration),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = context.getString(R.string.settings_crossfade_duration_desc, crossfadeDuration),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Slider(
                                        value = crossfadeDuration,
                                        onValueChange = { appSettings.setCrossfadeDuration(it) },
                                        valueRange = 0.5f..12f,
                                        steps = 22,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = context.getString(R.string.settings_crossfade_min),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = context.getString(R.string.settings_crossfade_max),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        trailingContent = when {
                            item.toggleState != null && item.onClick != null -> {
                                {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                                            contentDescription = context.getString(R.string.cd_navigate),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .height(20.dp)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        TunerAnimatedSwitch(
                                            checked = item.toggleState,
                                            onCheckedChange = {
                                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                                item.onToggleChange?.invoke(it)
                                            }
                                        )
                                    }
                                }
                            }
                            item.toggleState != null -> {
                                {
                                    TunerAnimatedSwitch(
                                        checked = item.toggleState,
                                        onCheckedChange = {
                                            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                            item.onToggleChange?.invoke(it)
                                        }
                                    )
                                }
                            }
                            item.onClick != null -> {
                                {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                                        contentDescription = context.getString(R.string.cd_navigate),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            else -> null
                        },
                        isHighlighted = item.toggleState == true,
                        enabled = item.enabled,
                        onClick = when {
                            item.onClick != null -> {
                                {
                                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
                                    item.onClick.invoke()
                                }
                            }

                            item.toggleState != null && item.onToggleChange != null -> {
                                {
                                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                    item.onToggleChange.invoke(!item.toggleState)
                                }
                            }

                            else -> null
                        }
                    )
                }

                Material3SettingsGroup(
                    title = group.title,
                    items = materialItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item(key = "playback_bottom_spacer") { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}
