/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings

import android.os.Build
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
import chromahub.rhythm.app.infrastructure.audio.haptics.MusicHapticsMode
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import kotlin.math.roundToInt

@Composable
fun RhythmHapticsSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateTo: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val playbackPrefs = remember(appSettings) { appSettings.playbackPrefs }
    val haptic = LocalHapticFeedback.current

    val enabled by playbackPrefs.musicHapticsEnabled.collectAsState()
    val modeName by playbackPrefs.musicHapticsMode.collectAsState()
    val intensity by playbackPrefs.musicHapticsIntensity.collectAsState()
    val muteOnExternal by playbackPrefs.musicHapticsMuteOnExternal.collectAsState()
    val useSystemGenerator by playbackPrefs.musicHapticsUseSystemGenerator.collectAsState()
    val audioRoutingMode by appSettings.audioRoutingMode.collectAsState()
    val isBitPerfect = audioRoutingMode == "app"

    val selectedMode = remember(modeName) {
        MusicHapticsMode.entries.firstOrNull { it.name == modeName } ?: MusicHapticsMode.BASS_AND_BEATS
    }
    val modeEntries = listOf(
        MusicHapticsMode.BASS_AND_BEATS to stringResource(R.string.rhythm_haptics_mode_bass_beats),
        MusicHapticsMode.VOCALS_ONLY to stringResource(R.string.rhythm_haptics_mode_vocals),
        MusicHapticsMode.WHOLE_SONG to stringResource(R.string.rhythm_haptics_mode_whole_song)
    )

    CollapsibleHeaderScreen(
        title = stringResource(R.string.settings_rhythm_haptics),
        showBackButton = true,
        onBackClick = onBackClick,
        headerContent = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled && !isBitPerfect) {
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
                        imageVector = MaterialSymbolIcon("vibration", filled = true),
                        contentDescription = null,
                        tint = if (enabled && !isBitPerfect) {
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
                                else -> stringResource(if (enabled) R.string.status_active else R.string.status_disabled)
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
                        }
                    }
                    TunerAnimatedSwitch(
                        checked = if (isBitPerfect) false else enabled,
                        onCheckedChange = { checked ->
                            if (!isBitPerfect) {
                                playbackPrefs.setMusicHapticsEnabled(checked)
                            }
                        },
                        enabled = !isBitPerfect
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
            item(key = "rhythm_haptics_controls") {
                AnimatedVisibility(
                    visible = enabled && !isBitPerfect,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val strengthPercent = (intensity * 100f).roundToInt().coerceIn(10, 100)
                    val settingItems = buildList {
                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("tune", filled = true),
                                title = { Text(stringResource(R.string.settings_rhythm_haptics_mode)) },
                                description = {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.settings_rhythm_haptics_mode_desc))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        ExpressiveButtonGroup(
                                            items = modeEntries.map { it.second },
                                            selectedIndex = modeEntries.indexOfFirst { it.first == selectedMode }.coerceAtLeast(0),
                                            onItemClick = { index ->
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                playbackPrefs.setMusicHapticsMode(modeEntries[index].first.name)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        )
                        add(
                            Material3SettingsItem(
                                icon = MaterialSymbolIcon("bolt", filled = true),
                                title = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(stringResource(R.string.settings_rhythm_haptics_strength))
                                        Text(
                                            text = "$strengthPercent%",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                description = {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.settings_rhythm_haptics_strength_desc))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val strengthSliderState = remember(intensity) {
                                            SliderState(
                                                value = intensity.coerceIn(0.1f, 1.0f),
                                                steps = 8,
                                                trackRange = 0.1f..1.0f
                                            )
                                        }
                                        strengthSliderState.value = intensity.coerceIn(0.1f, 1.0f)
                                        Slider(
                                            state = strengthSliderState,
                                            onValueChange = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                playbackPrefs.setMusicHapticsIntensity(it)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = stringResource(R.string.settings_rhythm_haptics_min),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = stringResource(R.string.settings_rhythm_haptics_max),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            )
                        )
                        add(
                            Material3SettingsItem(
                                icon = RhythmIcons.Devices.Usb,
                                title = { Text(stringResource(R.string.settings_rhythm_haptics_mute_external)) },
                                description = { Text(stringResource(R.string.settings_rhythm_haptics_mute_external_desc)) },
                                trailingContent = {
                                    TunerAnimatedSwitch(
                                        checked = muteOnExternal,
                                        onCheckedChange = { playbackPrefs.setMusicHapticsMuteOnExternal(it) }
                                    )
                                },
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    playbackPrefs.setMusicHapticsMuteOnExternal(!muteOnExternal)
                                }
                            )
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            add(
                                Material3SettingsItem(
                                    icon = MaterialSymbolIcon("graphic_eq", filled = true),
                                    title = { Text(stringResource(R.string.settings_rhythm_haptics_system_generator)) },
                                    description = { Text(stringResource(R.string.settings_rhythm_haptics_system_generator_desc)) },
                                    trailingContent = {
                                        TunerAnimatedSwitch(
                                            checked = useSystemGenerator,
                                            onCheckedChange = { playbackPrefs.setMusicHapticsUseSystemGenerator(it) }
                                        )
                                    },
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        playbackPrefs.setMusicHapticsUseSystemGenerator(!useSystemGenerator)
                                    }
                                )
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Material3SettingsGroup(
                            title = stringResource(R.string.settings_rhythm_haptics_config),
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
