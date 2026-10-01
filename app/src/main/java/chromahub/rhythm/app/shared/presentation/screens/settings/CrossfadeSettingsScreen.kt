/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import java.util.Locale

@Composable
fun CrossfadeSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateTo: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptic = LocalHapticFeedback.current

    val crossfade by appSettings.crossfade.collectAsState()
    val crossfadeDuration by appSettings.crossfadeDuration.collectAsState()
    val crossfadeRepeatOne by appSettings.crossfadeRepeatOne.collectAsState()
    val crossfadeOnSkip by appSettings.crossfadeOnSkip.collectAsState()
    val isAudioOffloadActive by appSettings.isAudioOffloadActive.collectAsState()
    val batterySaverEnabled by appSettings.batterySaverEnabled.collectAsState()
    val batterySaverMode by appSettings.batterySaverMode.collectAsState()
    val batterySaverEnableOffload by appSettings.batterySaverEnableOffload.collectAsState()
    val isOffloadEnforced = batterySaverEnabled && (batterySaverMode == "auto" || (batterySaverMode == "manual" && batterySaverEnableOffload))
    val audioRoutingMode by appSettings.audioRoutingMode.collectAsState()
    val isBitPerfect = audioRoutingMode == "app"

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_crossfade),
        showBackButton = true,
        onBackClick = onBackClick,
        headerContent = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (crossfade && !isOffloadEnforced && !isBitPerfect) {
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
                        imageVector = MaterialSymbolIcon("compare_arrows", filled = true),
                        contentDescription = null,
                        tint = if (crossfade && !isOffloadEnforced && !isBitPerfect) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(35.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isBitPerfect -> "Disabled (Bit-Perfect)"
                                isOffloadEnforced -> "Disabled (Lite Mode)"
                                else -> stringResource(if (crossfade) R.string.status_active else R.string.status_disabled)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (isBitPerfect) {
                            Text(
                                text = stringResource(R.string.audio_routing_bit_perfect_disabled_effect),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (isOffloadEnforced) {
                            Text(
                                text = "Disabled under Lite Mode to conserve battery.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (isAudioOffloadActive && !crossfade) {
                            Text(
                                text = stringResource(R.string.replay_gain_offload_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TunerAnimatedSwitch(
                        checked = if (isOffloadEnforced || isBitPerfect) false else crossfade,
                        onCheckedChange = { enabled ->
                            if (!isOffloadEnforced && !isBitPerfect) {
                                appSettings.setCrossfade(enabled)
                            }
                        },
                        enabled = !isOffloadEnforced && !isBitPerfect
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
            item(key = "crossfade_controls") {
                AnimatedVisibility(
                    visible = crossfade && !isOffloadEnforced && !isBitPerfect,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val settingItems = listOf(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("timer", filled = true),
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.settings_crossfade_duration))
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", crossfadeDuration)}s",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            description = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.settings_crossfade_duration_help))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val crossfadeSliderState = remember(crossfadeDuration) {
                                        SliderState(
                                            value = crossfadeDuration,
                                            steps = 22,
                                            trackRange = 0.5f..12f
                                        )
                                    }
                                    crossfadeSliderState.value = crossfadeDuration
                                    Slider(
                                        state = crossfadeSliderState,
                                        onValueChange = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            appSettings.setCrossfadeDuration(it)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_crossfade_min),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_crossfade_max),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        ),
                        Material3SettingsItem(
                            icon = RhythmIcons.Player.RepeatOne,
                            title = { Text(stringResource(R.string.settings_crossfade_repeat_one)) },
                            description = { Text(stringResource(R.string.settings_crossfade_repeat_one_desc)) },
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = crossfadeRepeatOne,
                                    onCheckedChange = { appSettings.setCrossfadeRepeatOne(it) }
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                appSettings.setCrossfadeRepeatOne(!crossfadeRepeatOne)
                            }
                        ),
                        Material3SettingsItem(
                            icon = RhythmIcons.Player.SkipNext,
                            title = { Text(stringResource(R.string.settings_crossfade_on_skip)) },
                            description = { Text(stringResource(R.string.settings_crossfade_on_skip_desc)) },
                            trailingContent = {
                                TunerAnimatedSwitch(
                                    checked = crossfadeOnSkip,
                                    onCheckedChange = { appSettings.setCrossfadeOnSkip(it) }
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                appSettings.setCrossfadeOnSkip(!crossfadeOnSkip)
                            }
                        )
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Material3SettingsGroup(
                            title = stringResource(R.string.settings_crossfade_configuration),
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
