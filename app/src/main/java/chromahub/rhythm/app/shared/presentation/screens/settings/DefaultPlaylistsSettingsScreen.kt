/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.R
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils

@Composable
fun DefaultPlaylistsSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val appSettings = AppSettings.getInstance(context)
    val musicViewModel: MusicViewModel = viewModel()
    
    val defaultPlaylistsEnabled by appSettings.defaultPlaylistsEnabled.collectAsState()
    val showLikedInPlaylists by appSettings.showLikedInPlaylists.collectAsState()
    val smartPlaylistRecentlyAdded by appSettings.smartPlaylistRecentlyAdded.collectAsState()
    val smartPlaylistMostPlayed by appSettings.smartPlaylistMostPlayed.collectAsState()
    val smartPlaylistOnRepeat by appSettings.smartPlaylistOnRepeat.collectAsState()
    val smartPlaylistForgottenFavorites by appSettings.smartPlaylistForgottenFavorites.collectAsState()
    val smartPlaylistRecentlyPlayed by appSettings.smartPlaylistRecentlyPlayed.collectAsState()

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_default_playlists),
        showBackButton = true,
        onBackClick = {
            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
            onBackClick()
        },
        headerContent = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (defaultPlaylistsEnabled)
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
                        imageVector = RhythmIcons.Library,
                        contentDescription = null,
                        tint = if (defaultPlaylistsEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(35.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(if (defaultPlaylistsEnabled) R.string.status_active else R.string.status_disabled),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TunerAnimatedSwitch(
                        checked = defaultPlaylistsEnabled,
                        onCheckedChange = { enabled ->
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            musicViewModel.setDefaultPlaylistsEnabled(enabled)
                        }
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "smart_playlists_content") {
                AnimatedVisibility(
                    visible = defaultPlaylistsEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Material3SettingsGroup(
                            title = context.getString(R.string.settings_smart_playlists),
                            items = listOf(
                                toMaterial3SettingsItem(
                                    context = context,
                                    item = SettingItem(
                                        icon = null,
                                        title = context.getString(R.string.settings_show_liked_in_playlists),
                                        description = context.getString(R.string.settings_show_liked_in_playlists_desc),
                                        toggleState = showLikedInPlaylists,
                                        onToggleChange = { enabled ->
                                            musicViewModel.setShowLikedInPlaylists(enabled)
                                        }
                                    ),
                                    hapticFeedback = haptic
                                ),
                                toMaterial3SettingsItem(
                                    context = context,
                                    item = SettingItem(
                                        icon = null,
                                        title = context.getString(R.string.settings_smart_playlist_recently_added),
                                        description = context.getString(R.string.settings_smart_playlist_recently_added_desc),
                                        toggleState = smartPlaylistRecentlyAdded,
                                        onToggleChange = { enabled ->
                                            musicViewModel.setSmartPlaylistRecentlyAdded(enabled)
                                        }
                                    ),
                                    hapticFeedback = haptic
                                ),
                                toMaterial3SettingsItem(
                                    context = context,
                                    item = SettingItem(
                                        icon = null,
                                        title = context.getString(R.string.settings_smart_playlist_most_played),
                                        description = context.getString(R.string.settings_smart_playlist_most_played_desc),
                                        toggleState = smartPlaylistMostPlayed,
                                        onToggleChange = { enabled ->
                                            musicViewModel.setSmartPlaylistMostPlayed(enabled)
                                        }
                                    ),
                                    hapticFeedback = haptic
                                ),
                                toMaterial3SettingsItem(
                                    context = context,
                                    item = SettingItem(
                                        icon = null,
                                        title = context.getString(R.string.settings_smart_playlist_on_repeat),
                                        description = context.getString(R.string.settings_smart_playlist_on_repeat_desc),
                                        toggleState = smartPlaylistOnRepeat,
                                        onToggleChange = { enabled ->
                                            musicViewModel.setSmartPlaylistOnRepeat(enabled)
                                        }
                                    ),
                                    hapticFeedback = haptic
                                ),
                                toMaterial3SettingsItem(
                                    context = context,
                                    item = SettingItem(
                                        icon = null,
                                        title = context.getString(R.string.settings_smart_playlist_forgotten_favorites),
                                        description = context.getString(R.string.settings_smart_playlist_forgotten_favorites_desc),
                                        toggleState = smartPlaylistForgottenFavorites,
                                        onToggleChange = { enabled ->
                                            musicViewModel.setSmartPlaylistForgottenFavorites(enabled)
                                        }
                                    ),
                                    hapticFeedback = haptic
                                ),
                                toMaterial3SettingsItem(
                                    context = context,
                                    item = SettingItem(
                                        icon = null,
                                        title = context.getString(R.string.settings_smart_playlist_recently_played),
                                        description = context.getString(R.string.settings_smart_playlist_recently_played_desc),
                                        toggleState = smartPlaylistRecentlyPlayed,
                                        onToggleChange = { enabled ->
                                            musicViewModel.setSmartPlaylistRecentlyPlayed(enabled)
                                        }
                                    ),
                                    hapticFeedback = haptic
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}
