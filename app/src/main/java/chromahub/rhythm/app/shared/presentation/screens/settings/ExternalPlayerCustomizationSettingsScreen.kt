/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils

@Composable
fun ExternalPlayerCustomizationSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptics = LocalHapticFeedback.current

    val continuePlaybackOnClose by appSettings.externalPlayerContinuePlaybackOnClose.collectAsState()
    val autoCloseOnFinish by appSettings.externalPlayerAutoCloseOnFinish.collectAsState()
    val showLyrics by appSettings.externalPlayerShowLyrics.collectAsState()
    val showAudioDetails by appSettings.externalPlayerShowAudioDetails.collectAsState()
    val showWaveSeeker by appSettings.externalPlayerShowWaveSeeker.collectAsState()
    val hapticsEnabled by appSettings.externalPlayerHapticFeedback.collectAsState()

    CollapsibleHeaderScreen(
        title = stringResource(R.string.settings_external_player_customization),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.external_player_playback_section),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                val playbackItems = listOf(
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = MaterialSymbolIcon("play_circle"),
                            title = stringResource(R.string.external_player_continue_playback),
                            description = stringResource(R.string.external_player_continue_playback_desc),
                            toggleState = continuePlaybackOnClose,
                            onToggleChange = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                appSettings.setExternalPlayerContinuePlaybackOnClose(it)
                            }
                        )
                    ),
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = MaterialSymbolIcon("close"),
                            title = stringResource(R.string.external_player_auto_close),
                            description = stringResource(R.string.external_player_auto_close_desc),
                            toggleState = autoCloseOnFinish,
                            onToggleChange = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                appSettings.setExternalPlayerAutoCloseOnFinish(it)
                            }
                        )
                    )
                )

                Material3SettingsGroup(
                    items = playbackItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.external_player_appearance_section),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                val appearanceItems = listOf(
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = MaterialSymbolIcon("lyrics"),
                            title = stringResource(R.string.external_player_show_lyrics),
                            description = stringResource(R.string.external_player_show_lyrics_desc),
                            toggleState = showLyrics,
                            onToggleChange = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                appSettings.setExternalPlayerShowLyrics(it)
                            }
                        )
                    ),
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = MaterialSymbolIcon("info"),
                            title = stringResource(R.string.external_player_audio_details),
                            description = stringResource(R.string.external_player_audio_details_desc),
                            toggleState = showAudioDetails,
                            onToggleChange = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                appSettings.setExternalPlayerShowAudioDetails(it)
                            }
                        )
                    ),
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = MaterialSymbolIcon("graphic_eq"),
                            title = stringResource(R.string.external_player_wave_seeker),
                            description = stringResource(R.string.external_player_wave_seeker_desc),
                            toggleState = showWaveSeeker,
                            onToggleChange = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                appSettings.setExternalPlayerShowWaveSeeker(it)
                            }
                        )
                    )
                )

                Material3SettingsGroup(
                    items = appearanceItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.external_player_interaction_section),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                val interactionItems = listOf(
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = MaterialSymbolIcon("vibration"),
                            title = stringResource(R.string.external_player_haptics),
                            description = stringResource(R.string.external_player_haptics_desc),
                            toggleState = hapticsEnabled,
                            onToggleChange = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                appSettings.setExternalPlayerHapticFeedback(it)
                            }
                        )
                    )
                )

                Material3SettingsGroup(
                    items = interactionItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }
        }
    }
}
