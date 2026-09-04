/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import androidx.compose.foundation.layout.PaddingValues
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

@Composable
fun QueueSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val hapticFeedback = LocalHapticFeedback.current

    val shuffleUsesExoplayer by appSettings.shuffleUsesExoplayer.collectAsState()
    val keepShuffleOnSelection by appSettings.keepShuffleOnSelection.collectAsState()
    val autoAddToQueue by appSettings.autoAddToQueue.collectAsState()
    val respectAlbumOnPlay by appSettings.respectAlbumOnPlay.collectAsState()
    val clearQueueOnNewSong by appSettings.clearQueueOnNewSong.collectAsState()
    val hidePlayedQueueSongs by appSettings.hidePlayedQueueSongs.collectAsState()
    val contextQueuePreference by appSettings.contextQueuePreference.collectAsState()
    val contextQueuePersistenceRaw by appSettings.contextQueuePersistence.collectAsState()
    val showAlreadyPlayedSongsInQueue = !hidePlayedQueueSongs
    val effectiveContextQueuePreference = if (contextQueuePreference == "GENRE_FIRST") {
        "GENRE_FIRST"
    } else {
        "ARTIST_FIRST"
    }
    val showQueueDialog by appSettings.showQueueDialog.collectAsState()
    val listQueueActionBehavior by appSettings.listQueueActionBehavior.collectAsState(initial = "replace")
    val queuePersistenceEnabled by appSettings.queuePersistenceEnabled.collectAsState()

    var showListQueueBehaviorDialog by remember { mutableStateOf(false) }
    var showQueueDialogSettingDialog by remember { mutableStateOf(false) }
    var showContextPrefBottomSheet by remember { mutableStateOf(false) }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_queue_title),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        val settingGroups = listOf(
            SettingGroup(
                title = context.getString(R.string.settings_queue_behavior),
                items = buildList {
                    add(
                        SettingItem(
                            MaterialSymbolIcon("album", filled = true),
                            context.getString(R.string.settings_respect_album_on_play),
                            context.getString(R.string.settings_respect_album_on_play_desc),
                            toggleState = respectAlbumOnPlay,
                            onToggleChange = { appSettings.setRespectAlbumOnPlay(it) }
                        )
                    )
                    add(
                        SettingItem(
                            RhythmIcons.Sort,
                            context.getString(R.string.settings_list_queue_action_dialog),
                            when (listQueueActionBehavior) {
                                "ask" -> context.getString(R.string.settings_list_queue_action_ask)
                                "play_next" -> context.getString(R.string.settings_list_queue_action_play_next)
                                "add_to_end" -> context.getString(R.string.settings_list_queue_action_add_to_end)
                                else -> context.getString(R.string.settings_list_queue_action_replace)
                            },
                            onClick = { showListQueueBehaviorDialog = true }
                        )
                    )
                    add(
                        SettingItem(
                            MaterialSymbolIcon("help", filled = true),
                            context.getString(R.string.settings_queue_action_dialog),
                            when {
                                clearQueueOnNewSong -> context.getString(R.string.settings_queue_action_dialog_desc_disabled)
                                showQueueDialog -> context.getString(R.string.settings_queue_action_dialog_desc_ask)
                                else -> context.getString(R.string.settings_queue_action_dialog_desc_always)
                            },
                            onClick = { showQueueDialogSettingDialog = true },
                            enabled = !clearQueueOnNewSong
                        )
                    )
                    add(
                        SettingItem(
                            RhythmIcons.Delete,
                            context.getString(R.string.settings_clear_queue_on_new_song),
                            context.getString(R.string.settings_clear_queue_on_new_song_desc),
                            toggleState = clearQueueOnNewSong,
                            onToggleChange = { appSettings.setClearQueueOnNewSong(it) }
                        )
                    )
                    add(
                        SettingItem(
                            RhythmIcons.Shuffle,
                            context.getString(R.string.settings_use_exoplayer_shuffle),
                            context.getString(R.string.settings_use_exoplayer_shuffle_desc),
                            toggleState = shuffleUsesExoplayer,
                            onToggleChange = { appSettings.setShuffleUsesExoplayer(it) }
                        )
                    )
                    add(
                        SettingItem(
                            RhythmIcons.Shuffle,
                            context.getString(R.string.settings_keep_shuffle_on_selection),
                            context.getString(R.string.settings_keep_shuffle_on_selection_desc),
                            toggleState = keepShuffleOnSelection,
                            onToggleChange = { appSettings.setKeepShuffleOnSelection(it) }
                        )
                    )
                }
            ),
            SettingGroup(
                title = context.getString(R.string.settings_queue_autofill),
                items = buildList {
                    add(
                        SettingItem(
                            RhythmIcons.AddToQueue,
                            context.getString(R.string.settings_auto_queue),
                            context.getString(R.string.settings_auto_queue_desc),
                            toggleState = autoAddToQueue,
                            onToggleChange = { appSettings.setAutoAddToQueue(it) }
                        )
                    )
                    add(
                        SettingItem(
                            RhythmIcons.Tune,
                            context.getString(R.string.settings_context_queue_preference),
                            when (effectiveContextQueuePreference) {
                                "ARTIST_FIRST" -> context.getString(R.string.settings_context_pref_artist_first)
                                else -> context.getString(R.string.settings_context_pref_genre_first)
                            },
                            onClick = { showContextPrefBottomSheet = true },
                            enabled = autoAddToQueue
                        )
                    )
                    add(
                        SettingItem(
                            RhythmIcons.Repeat,
                            context.getString(R.string.settings_context_queue_persistence),
                            context.getString(R.string.settings_context_queue_persistence_desc),
                            data = "context_queue_persistence",
                            enabled = autoAddToQueue
                        )
                    )
                }
            ),
            SettingGroup(
                title = context.getString(R.string.settings_queue_display),
                items = listOf(
                    SettingItem(
                        RhythmIcons.Queue,
                        context.getString(R.string.settings_show_played_queue_songs),
                        context.getString(R.string.settings_show_played_queue_songs_desc),
                        toggleState = showAlreadyPlayedSongsInQueue,
                        onToggleChange = { appSettings.setHidePlayedQueueSongs(!it) }
                    ),
                    SettingItem(
                        MaterialSymbolIcon("history", filled = true),
                        context.getString(R.string.settings_remember_queue),
                        context.getString(R.string.settings_remember_queue_desc),
                        toggleState = queuePersistenceEnabled,
                        onToggleChange = { appSettings.setQueuePersistenceEnabled(it) }
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
                key = { "queue_${it.title}" },
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

                                if (item.data == "context_queue_persistence") {
                                    val persistenceOptions = listOf(
                                        "EPHEMERAL" to context.getString(R.string.settings_context_persistence_ephemeral),
                                        "PERSISTENT" to context.getString(R.string.settings_context_persistence_persistent)
                                    )
                                    val selectedIndex = persistenceOptions
                                        .indexOfFirst { it.first == contextQueuePersistenceRaw }
                                        .coerceAtLeast(0)

                                    Spacer(modifier = Modifier.height(10.dp))
                                    ExpressiveButtonGroup(
                                        items = persistenceOptions.map { it.second },
                                        selectedIndex = selectedIndex,
                                        onItemClick = { index ->
                                            HapticUtils.performHapticFeedback(
                                                context,
                                                hapticFeedback,
                                                HapticType.LIGHT
                                            )
                                            appSettings.setContextQueuePersistence(
                                                persistenceOptions[index].first
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = item.enabled
                                    )
                                }
                            }
                        },
                        trailingContent = if (item.toggleState != null) {
                            {
                                TunerAnimatedSwitch(
                                    checked = item.toggleState,
                                    onCheckedChange = {
                                        item.onToggleChange?.invoke(it)
                                    }
                                )
                            }
                        } else if (item.onClick != null) {
                            {
                                Icon(
                                    imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                                    contentDescription = context.getString(R.string.cd_navigate),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            null
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

            item(key = "queue_bottom_spacer") { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    if (showContextPrefBottomSheet) {
        ContextQueuePreferenceBottomSheet(
            currentPreference = effectiveContextQueuePreference,
            onDismiss = { showContextPrefBottomSheet = false },
            onSelect = { pref ->
                appSettings.setContextQueuePreference(pref)
                showContextPrefBottomSheet = false
            }
        )
    }

    if (showListQueueBehaviorDialog) {
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val listQueueSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { showListQueueBehaviorDialog = false },
            sheetState = listQueueSheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.primary
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            StandardBottomSheetHeader(
                title = context.getString(R.string.list_queue_behavior_title),
                subtitle = context.getString(R.string.list_queue_behavior_desc),
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val options = listOf(
                        "replace" to Triple(
                            context.getString(R.string.list_queue_behavior_replace_title),
                            context.getString(R.string.list_queue_behavior_replace_desc),
                            RhythmIcons.Playlist
                        ),
                        "ask" to Triple(
                            context.getString(R.string.list_queue_behavior_ask_title),
                            context.getString(R.string.list_queue_behavior_ask_desc),
                            MaterialSymbolIcon("help", filled = true)
                        ),
                        "play_next" to Triple(
                            context.getString(R.string.list_queue_behavior_play_next_title),
                            context.getString(R.string.list_queue_behavior_play_next_desc),
                            RhythmIcons.Play
                        ),
                        "add_to_end" to Triple(
                            context.getString(R.string.list_queue_behavior_add_end_title),
                            context.getString(R.string.list_queue_behavior_add_end_desc),
                            RhythmIcons.AddToPlaylist
                        )
                    )

                    options.forEach { (value, option) ->
                        val isSelected = listQueueActionBehavior == value

                        Card(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                scope.launch {
                                    appSettings.setListQueueActionBehavior(value)
                                    showListQueueBehaviorDialog = false
                                }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = option.third,
                                            contentDescription = null,
                                        tint = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.first,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = option.second,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = RhythmIcons.CheckCircle,
                                        contentDescription = context.getString(R.string.ui_selected),
                                        tint = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQueueDialogSettingDialog) {
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val queueSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { showQueueDialogSettingDialog = false },
            sheetState = queueSheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.primary
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            StandardBottomSheetHeader(
                title = context.getString(R.string.queue_action_title),
                subtitle = context.getString(R.string.queue_action_choose),
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            scope.launch {
                                appSettings.setShowQueueDialog(true)
                                showQueueDialogSettingDialog = false
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (showQueueDialog)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (showQueueDialog)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("help", filled = true),
                                        contentDescription = null,
                                        tint = if (showQueueDialog)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = context.getString(R.string.queue_action_ask_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (showQueueDialog)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = context.getString(R.string.queue_action_ask_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (showQueueDialog)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (showQueueDialog) {
                                Icon(
                                    imageVector = RhythmIcons.CheckCircle,
                                    contentDescription = stringResource(R.string.streaming_selected),
                                    tint = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            scope.launch {
                                appSettings.setShowQueueDialog(false)
                                showQueueDialogSettingDialog = false
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (!showQueueDialog)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (!showQueueDialog)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Play,
                                        contentDescription = null,
                                        tint = if (!showQueueDialog)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = context.getString(R.string.queue_action_always_add_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (!showQueueDialog)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = context.getString(R.string.queue_action_always_add_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!showQueueDialog)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!showQueueDialog) {
                                Icon(
                                    imageVector = RhythmIcons.CheckCircle,
                                    contentDescription = stringResource(R.string.streaming_selected),
                                    tint = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
