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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch

@Composable
fun ReplayGainSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateTo: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptic = LocalHapticFeedback.current

    val replayGain by appSettings.replayGain.collectAsState()
    val replayGainMode by appSettings.replayGainMode.collectAsState()
    val replayGainDrc by appSettings.replayGainDrc.collectAsState()
    val replayGainPreamp by appSettings.replayGainPreamp.collectAsState()
    val replayGainPreampUntagged by appSettings.replayGainPreampUntagged.collectAsState()
    val isAudioOffloadActive by appSettings.isAudioOffloadActive.collectAsState()
    val batterySaverEnabled by appSettings.batterySaverEnabled.collectAsState()
    val batterySaverMode by appSettings.batterySaverMode.collectAsState()
    val batterySaverEnableOffload by appSettings.batterySaverEnableOffload.collectAsState()
    val isOffloadEnforced = batterySaverEnabled && (batterySaverMode == "auto" || (batterySaverMode == "manual" && batterySaverEnableOffload))

    CollapsibleHeaderScreen(
        title = context.getString(R.string.replay_gain),
        showBackButton = true,
        onBackClick = onBackClick,
        headerContent = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (replayGain && !isOffloadEnforced)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surfaceContainer
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
                        imageVector = MaterialSymbolIcon("volume_up"),
                        contentDescription = null,
                        tint = if (replayGain && !isOffloadEnforced) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(35.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isOffloadEnforced) {
                                "Disabled (Lite Mode)"
                            } else {
                                stringResource(if (replayGain) R.string.status_active else R.string.status_disabled)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (isOffloadEnforced) {
                            Text(
                                text = context.getString(R.string.replay_gain_disabled_battery),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (isAudioOffloadActive && !replayGain) {
                            Text(
                                text = context.getString(R.string.replay_gain_offload_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TunerAnimatedSwitch(
                        checked = if (isOffloadEnforced) false else replayGain,
                        onCheckedChange = { enabled ->
                            if (!isOffloadEnforced) {
                                appSettings.setReplayGain(enabled)
                            }
                        },
                        enabled = !isOffloadEnforced
                    )
                }
            }
        }
    ) { modifier ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp)
        ) {
            item(key = "replay_gain_controls") {
                AnimatedVisibility(
                    visible = replayGain,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val settingItems = listOf(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("music_note"),
                            title = { Text(stringResource(R.string.replay_gain_mode_title)) },
                            description = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.replay_gain_mode_desc))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    ExpressiveButtonGroup(
                                        items = listOf(
                                            stringResource(R.string.replay_gain_mode_track),
                                            stringResource(R.string.replay_gain_mode_album)
                                        ),
                                        selectedIndex = if (replayGainMode == 2) 1 else 0,
                                        onItemClick = { index ->
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            appSettings.setReplayGainMode(if (index == 1) 2 else 1)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        ),
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("graphic_eq"),
                            title = { Text(stringResource(R.string.replay_gain_prevent_clipping)) },
                            description = {
                                Text(stringResource(R.string.replay_gain_prevent_clipping_desc))
                            },
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = replayGainDrc,
                                    onCheckedChange = {
                                        appSettings.setReplayGainDrc(it)
                                    }
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                appSettings.setReplayGainDrc(!replayGainDrc)
                            }
                        ),
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("volume_up"),
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.replay_gain_preamp_tagged))
                                    Text(
                                        text = "${replayGainPreamp.toInt()} dB",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            description = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.replay_gain_preamp_tagged_desc))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val preampSliderState = remember(replayGainPreamp) {
                                        SliderState(
                                            value = replayGainPreamp,
                                            steps = 30,
                                            trackRange = -15f..15f
                                        )
                                    }
                                    preampSliderState.value = replayGainPreamp
                                    Slider(
                                        state = preampSliderState,
                                        onValueChange = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            appSettings.setReplayGainPreamp(it)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        ),
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("volume_down"),
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.replay_gain_preamp_untagged))
                                    Text(
                                        text = "${replayGainPreampUntagged.toInt()} dB",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            description = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.replay_gain_preamp_untagged_desc))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val untaggedPreampSliderState = remember(replayGainPreampUntagged) {
                                        SliderState(
                                            value = replayGainPreampUntagged,
                                            steps = 30,
                                            trackRange = -15f..15f
                                        )
                                    }
                                    untaggedPreampSliderState.value = replayGainPreampUntagged
                                    Slider(
                                        state = untaggedPreampSliderState,
                                        onValueChange = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            appSettings.setReplayGainPreampUntagged(it)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        )
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Material3SettingsGroup(
                            title = stringResource(R.string.replay_gain_configuration),
                            items = settingItems,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}
