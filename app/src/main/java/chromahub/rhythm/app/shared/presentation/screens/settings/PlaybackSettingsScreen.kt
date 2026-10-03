/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

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
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaybackSpeedDialog

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
    val skipSilenceEnabled by appSettings.skipSilenceEnabled.collectAsState()
    val repeatModePersistence by appSettings.repeatModePersistence.collectAsState()
    val shuffleModePersistence by appSettings.shuffleModePersistence.collectAsState()
    val keepShuffleOnSelection by appSettings.keepShuffleOnSelection.collectAsState()
    val useHoursInTimeFormat by appSettings.useHoursInTimeFormat.collectAsState()
    val showRemainingTime by appSettings.showRemainingTime.collectAsState()
    val gaplessEnabled by appSettings.gaplessPlayback.collectAsState()
    val crossfadeEnabled by appSettings.crossfade.collectAsState()
    val crossfadeDuration by appSettings.crossfadeDuration.collectAsState()
    val stopPlaybackOnAppClose by appSettings.stopPlaybackOnAppClose.collectAsState()
    val monoAudioEnabled by appSettings.monoAudioEnabled.collectAsState()
    val useSystemVolume by appSettings.useSystemVolume.collectAsState()
    val resumeOnDeviceReconnect by appSettings.resumeOnDeviceReconnect.collectAsState()
    val audioOffloadEnabled by appSettings.audioOffloadEnabled.collectAsState()
    val isAudioOffloadActive by appSettings.isAudioOffloadActive.collectAsState()
    val batterySaverEnabled by appSettings.batterySaverEnabled.collectAsState()
    val batterySaverMode by appSettings.batterySaverMode.collectAsState()
    val batterySaverEnableOffload by appSettings.batterySaverEnableOffload.collectAsState()
    val isOffloadEnforced = batterySaverEnabled && (batterySaverMode == "auto" || (batterySaverMode == "manual" && batterySaverEnableOffload))

    val defaultPlaybackSpeed by appSettings.defaultPlaybackSpeed.collectAsState()
    val useDefaultPlaybackSpeed by appSettings.useDefaultPlaybackSpeed.collectAsState()
    var showDefaultSpeedDialog by remember { mutableStateOf(false) }

    val audioRoutingMode by appSettings.audioRoutingMode.collectAsState()
    val isBitPerfect = audioRoutingMode == "app"

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
                        icon = RhythmIcons.Player.VolumeUp,
                        title = context.getString(R.string.settings_system_volume),
                        description = context.getString(R.string.settings_system_volume_desc),
                        toggleState = useSystemVolume,
                        onToggleChange = { musicViewModel.setUseSystemVolumeMode(it) }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("bluetooth_connected", filled = true),
                        title = context.getString(R.string.settings_resume_on_device_reconnect),
                        description = context.getString(R.string.settings_resume_on_device_reconnect_desc),
                        toggleState = resumeOnDeviceReconnect,
                        onToggleChange = { appSettings.setResumeOnDeviceReconnect(it) }
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.settings_playback_persistence),
                items = listOf(
                    SettingItem(
                        icon = RhythmIcons.Repeat,
                        title = context.getString(R.string.settings_remember_repeat_mode),
                        description = context.getString(R.string.settings_remember_repeat_mode_desc),
                        toggleState = repeatModePersistence,
                        onToggleChange = { appSettings.setRepeatModePersistence(it) }
                    ),
                    SettingItem(
                        icon = RhythmIcons.Shuffle,
                        title = context.getString(R.string.settings_remember_shuffle_mode),
                        description = context.getString(R.string.settings_remember_shuffle_mode_desc),
                        toggleState = shuffleModePersistence,
                        onToggleChange = { appSettings.setShuffleModePersistence(it) }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("shuffle_on", filled = true),
                        title = context.getString(R.string.settings_keep_shuffle_on_selection),
                        description = context.getString(R.string.settings_keep_shuffle_on_selection_desc),
                        toggleState = keepShuffleOnSelection,
                        onToggleChange = { appSettings.setKeepShuffleOnSelection(it) }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("stop_circle", filled = true),
                        title = context.getString(R.string.settings_stop_playback_on_close),
                        description = context.getString(R.string.settings_stop_playback_on_close_desc),
                        toggleState = stopPlaybackOnAppClose,
                        onToggleChange = { appSettings.setStopPlaybackOnAppClose(it) }
                    ),
                    SettingItem(
                        icon = RhythmIcons.Player.Speed,
                        title = context.getString(R.string.use_default_playback_speed),
                        description = context.getString(R.string.use_default_playback_speed_desc),
                        toggleState = useDefaultPlaybackSpeed,
                        onToggleChange = { appSettings.setUseDefaultPlaybackSpeed(it) }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("pace", filled = true),
                        title = context.getString(R.string.default_playback_speed),
                        description = "${String.format(java.util.Locale.US, "%.3f", defaultPlaybackSpeed).dropLastWhile { it == '0' }.dropLastWhile { it == '.' }}x — ${context.getString(R.string.default_playback_speed_desc)}",
                        onClick = { showDefaultSpeedDialog = true }
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.settings_audio_effects),
                items = listOf(
                    SettingItem(
                        icon = MaterialSymbolIcon("graphic_eq", filled = true),
                        title = context.getString(R.string.settings_gapless_playback),
                        description = context.getString(R.string.settings_gapless_playback_desc),
                        toggleState = gaplessEnabled,
                        onToggleChange = { appSettings.setGaplessPlayback(it) }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("hearing", filled = true),
                        title = context.getString(R.string.settings_skip_silence),
                        description = when {
                            isBitPerfect -> context.getString(R.string.audio_routing_bit_perfect_disabled_effect)
                            isOffloadEnforced -> "Disabled under Lite Mode to conserve battery."
                            isAudioOffloadActive && !skipSilenceEnabled -> "${context.getString(R.string.settings_skip_silence_desc)}\n(Enabling will disable hardware Audio Offload)"
                            else -> context.getString(R.string.settings_skip_silence_desc)
                        },
                        toggleState = if (isOffloadEnforced || isAudioOffloadActive || isBitPerfect) false else skipSilenceEnabled,
                        onToggleChange = {
                            if (!isOffloadEnforced && !isAudioOffloadActive && !isBitPerfect) {
                                appSettings.setSkipSilenceEnabled(it)
                            }
                        },
                        enabled = !isOffloadEnforced && !isAudioOffloadActive && !isBitPerfect
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("compare_arrows", filled = true),
                        title = context.getString(R.string.settings_crossfade),
                        description = when {
                            isBitPerfect -> context.getString(R.string.audio_routing_bit_perfect_disabled_effect)
                            isOffloadEnforced -> "Disabled under Lite Mode to conserve battery."
                            crossfadeEnabled -> "${context.getString(R.string.status_active)} • ${String.format(java.util.Locale.US, "%.1f", crossfadeDuration)}s"
                            isAudioOffloadActive -> "${context.getString(R.string.status_disabled)}\n(Enabling will disable hardware Audio Offload)"
                            else -> context.getString(R.string.settings_crossfade_desc)
                        },
                        onClick = { if (!isBitPerfect) onNavigateTo(SettingsRoutes.CROSSFADE) },
                        enabled = !isBitPerfect
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("spatial_audio_off", filled = true),
                        title = context.getString(R.string.settings_mono_audio),
                        description = when {
                            isBitPerfect -> context.getString(R.string.audio_routing_bit_perfect_disabled_effect)
                            isOffloadEnforced -> "Disabled under Lite Mode to conserve battery."
                            isAudioOffloadActive && !monoAudioEnabled -> "${context.getString(R.string.settings_mono_audio_desc)}\n(Enabling will disable hardware Audio Offload)"
                            else -> context.getString(R.string.settings_mono_audio_desc)
                        },
                        toggleState = if (isOffloadEnforced || isBitPerfect) false else monoAudioEnabled,
                        onToggleChange = { if (!isOffloadEnforced && !isBitPerfect) musicViewModel.setMonoAudioEnabled(it) },
                        enabled = !isOffloadEnforced && !isBitPerfect
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("equalizer", filled = true),
                        title = context.getString(R.string.replay_gain),
                        description = when {
                            isBitPerfect -> context.getString(R.string.audio_routing_bit_perfect_disabled_effect)
                            isOffloadEnforced -> "Disabled under Lite Mode to conserve battery."
                            isAudioOffloadActive && !replayGain -> "${context.getString(R.string.replay_gain_desc)}\n(Enabling will disable hardware Audio Offload)"
                            else -> context.getString(R.string.replay_gain_desc)
                        },
                        onClick = { if (!isBitPerfect) onNavigateTo(SettingsRoutes.REPLAY_GAIN) },
                        enabled = !isBitPerfect
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.settings_section_audio_playback),
                items = listOf(
                    SettingItem(
                        icon = MaterialSymbolIcon("bolt", filled = true),
                        title = context.getString(R.string.settingsscreen_audio_offload),
                        description = when {
                            isBitPerfect -> context.getString(R.string.audio_routing_bit_perfect_disabled_effect)
                            isOffloadEnforced -> "Enforced under Lite Mode to conserve battery."
                            else -> context.getString(R.string.settingsscreen_audio_offload_desc)
                        },
                        toggleState = if (isOffloadEnforced) true else if (isBitPerfect) false else audioOffloadEnabled,
                        onToggleChange = { if (!isOffloadEnforced && !isBitPerfect) appSettings.setAudioOffloadEnabled(it) },
                        enabled = !isOffloadEnforced && !isBitPerfect
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.settings_time_display),
                items = listOf(
                    SettingItem(
                        icon = RhythmIcons.AccessTime,
                        title = context.getString(R.string.settings_use_hours),
                        description = if (useHoursInTimeFormat) context.getString(R.string.settings_use_hours_enabled) else context.getString(R.string.settings_use_hours_disabled),
                        toggleState = useHoursInTimeFormat,
                        onToggleChange = { appSettings.setUseHoursInTimeFormat(it) }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("timelapse", filled = true),
                        title = context.getString(R.string.settings_show_remaining_time),
                        description = context.getString(R.string.settings_show_remaining_time_desc),
                        toggleState = showRemainingTime,
                        onToggleChange = { appSettings.setShowRemainingTime(it) }
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
                        description = item.description?.let { desc -> { Text(desc) } },
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

        if (showDefaultSpeedDialog) {
            PlaybackSpeedDialog(
                currentSpeed = defaultPlaybackSpeed,
                syncEnabled = false,
                onDismiss = { showDefaultSpeedDialog = false },
                onSave = { speed ->
                    appSettings.setDefaultPlaybackSpeed(speed)
                    showDefaultSpeedDialog = false
                }
            )
        }
    }
}
