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

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import chromahub.rhythm.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.BuildConfig
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import chromahub.rhythm.app.shared.data.repository.StatsTimeRange
import chromahub.rhythm.app.util.GsonUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlin.system.exitProcess
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ButtonGroupStyle
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveScrollBar
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveGroupButton
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.common.StyledProgressBar
import chromahub.rhythm.app.shared.presentation.components.common.ProgressStyle
import chromahub.rhythm.app.shared.presentation.components.common.ThumbStyle
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.LicensesBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.UpdateBottomSheet
import chromahub.rhythm.app.ui.utils.LazyListStateSaver
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeProvider
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapes
import chromahub.rhythm.app.shared.presentation.components.common.buildSplashBackdropShapes
import chromahub.rhythm.app.shared.presentation.components.common.SplashBackgroundOrbs
import chromahub.rhythm.app.shared.presentation.viewmodel.AppUpdaterViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.AppVersion
import chromahub.rhythm.app.ui.theme.getFontPreviewStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import chromahub.rhythm.app.utils.FontLoader
import chromahub.rhythm.app.ui.theme.parseCustomColorScheme
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.text.HtmlCompat
import chromahub.rhythm.app.shared.presentation.components.common.M3FourColorCircularLoader
import chromahub.rhythm.app.shared.presentation.components.player.PlayingEqIcon
import chromahub.rhythm.app.shared.presentation.components.dialogs.CreatePlaylistDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.BulkPlaylistExportDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistImportDialog
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShape
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationProgressDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationResultDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.AppRestartDialog
import chromahub.rhythm.app.shared.presentation.components.player.PlayerChipOrderBottomSheet
import chromahub.rhythm.app.features.local.presentation.components.settings.HomeSectionOrderBottomSheet
import chromahub.rhythm.app.features.local.presentation.components.settings.LibraryTabOrderBottomSheet
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem

import chromahub.rhythm.app.shared.presentation.screens.settings.TunerSettingRow
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerSettingCard
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingItem
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingGroup


// Gestures Settings Screen
@Composable
fun GesturesSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptic = LocalHapticFeedback.current

    // Player Gestures
    val gesturePlayerSwipeDismiss by appSettings.gesturePlayerSwipeDismiss.collectAsState()
    val gesturePlayerSwipeTracks by appSettings.gesturePlayerSwipeTracks.collectAsState()
    val gestureArtworkDoubleTap by appSettings.gestureArtworkDoubleTap.collectAsState()
    val gestureArtworkSingleTap by appSettings.gestureArtworkSingleTap.collectAsState()

    // Mini Player Gestures
    val miniPlayerSwipeGestures by appSettings.miniPlayerSwipeGestures.collectAsState()
    val miniPlayerSwipeTracks by appSettings.miniPlayerSwipeTracks.collectAsState()
    val miniPlayerSwipeDismiss by appSettings.miniPlayerSwipeDismiss.collectAsState()

    // Queue Gestures
    val gestureQueueSwipeToRemove by appSettings.gestureQueueSwipeToRemove.collectAsState()

    // Library Gestures
    val gestureLibrarySwipeTabs by appSettings.gestureLibrarySwipeTabs.collectAsState()

    // Lyrics Gestures
    val tapLyricsToFullScreen by appSettings.tapLyricsToFullScreen.collectAsState()
    val tapLyricsToSeek by appSettings.tapLyricsToSeek.collectAsState()
    val autoHideLyricsControls by appSettings.autoHideLyricsControls.collectAsState()

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_gestures),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Full Player Gestures
            item(key = "player_gestures_group") {
                Spacer(modifier = Modifier.height(24.dp))

                val playerGestureItems = listOf(
                    SettingItem(
                        icon = MaterialSymbolIcon("swipe_down", filled = true),
                        title = context.getString(R.string.settings_swipe_down_dismiss),
                        description = context.getString(R.string.settings_swipe_down_dismiss_desc),
                        toggleState = gesturePlayerSwipeDismiss,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setGesturePlayerSwipeDismiss(it)
                        }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("swipe_left", filled = true),
                        title = context.getString(R.string.settings_swipe_artwork_tracks),
                        description = context.getString(R.string.settings_swipe_artwork_tracks_desc),
                        toggleState = gesturePlayerSwipeTracks,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setGesturePlayerSwipeTracks(it)
                        }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("touch_app", filled = true),
                        title = context.getString(R.string.settings_double_tap_artwork),
                        description = context.getString(R.string.settings_double_tap_artwork_desc),
                        toggleState = gestureArtworkDoubleTap,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setGestureArtworkDoubleTap(it)
                        }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("music_note", filled = true),
                        title = context.getString(R.string.settings_tap_artwork_lyrics),
                        description = context.getString(R.string.settings_tap_artwork_lyrics_desc),
                        toggleState = gestureArtworkSingleTap,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setGestureArtworkSingleTap(it)
                        }
                    )
                )

                Material3SettingsGroup(
                    title = context.getString(R.string.settings_full_player),
                    items = playerGestureItems.map { item ->
                        toMaterial3SettingsItem(context = context, item = item, hapticFeedback = haptic)
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Mini Player Gestures
            item(key = "miniplayer_gestures_group") {
                Spacer(modifier = Modifier.height(24.dp))

                val miniPlayerItems = listOf(
                    SettingItem(
                        icon = MaterialSymbolIcon("swipe", filled = true),
                        title = context.getString(R.string.settings_swipe_gestures),
                        description = context.getString(R.string.settings_swipe_gestures_desc),
                        toggleState = miniPlayerSwipeGestures,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setMiniPlayerSwipeGestures(it)
                        }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("fast_forward", filled = true),
                        title = context.getString(R.string.settings_miniplayer_swipe_tracks),
                        description = context.getString(R.string.settings_miniplayer_swipe_tracks_desc),
                        toggleState = miniPlayerSwipeTracks,
                        enabled = miniPlayerSwipeGestures,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setMiniPlayerSwipeTracks(it)
                        }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("swipe_vertical", filled = true),
                        title = context.getString(R.string.settings_miniplayer_swipe_dismiss),
                        description = context.getString(R.string.settings_miniplayer_swipe_dismiss_desc),
                        toggleState = miniPlayerSwipeDismiss,
                        enabled = miniPlayerSwipeGestures,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setMiniPlayerSwipeDismiss(it)
                        }
                    )
                )

                Material3SettingsGroup(
                    title = context.getString(R.string.settings_miniplayer),
                    items = miniPlayerItems.map { item ->
                        toMaterial3SettingsItem(context = context, item = item, hapticFeedback = haptic)
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Queue Gestures
            item(key = "queue_gestures_group") {
                Spacer(modifier = Modifier.height(24.dp))

                val queueItems = listOf(
                    SettingItem(
                        icon = MaterialSymbolIcon("delete_sweep", filled = true),
                        title = context.getString(R.string.settings_queue_swipe_remove),
                        description = context.getString(R.string.settings_queue_swipe_remove_desc),
                        toggleState = gestureQueueSwipeToRemove,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setGestureQueueSwipeToRemove(it)
                        }
                    )
                )

                Material3SettingsGroup(
                    title = context.getString(R.string.settings_queue_gestures),
                    items = queueItems.map { item ->
                        toMaterial3SettingsItem(context = context, item = item, hapticFeedback = haptic)
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Library & Navigation Gestures
            item(key = "library_gestures_group") {
                Spacer(modifier = Modifier.height(24.dp))

                val libraryItems = listOf(
                    SettingItem(
                        icon = MaterialSymbolIcon("tab", filled = true),
                        title = context.getString(R.string.settings_library_swipe_tabs),
                        description = context.getString(R.string.settings_library_swipe_tabs_desc),
                        toggleState = gestureLibrarySwipeTabs,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setGestureLibrarySwipeTabs(it)
                        }
                    )
                )

                Material3SettingsGroup(
                    title = context.getString(R.string.settings_library_gestures),
                    items = libraryItems.map { item ->
                        toMaterial3SettingsItem(context = context, item = item, hapticFeedback = haptic)
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Lyrics Gestures
            item(key = "lyrics_gestures_group") {
                Spacer(modifier = Modifier.height(24.dp))

                val lyricsItems = listOf(
                    SettingItem(
                        icon = MaterialSymbolIcon("fullscreen", filled = true),
                        title = context.getString(R.string.playercustomizationsettingsscreen_tap_lyrics_for_immersive),
                        description = context.getString(R.string.lyrics_settings_open_fullscreen_desc),
                        toggleState = tapLyricsToFullScreen,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setTapLyricsToFullScreen(it)
                        }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("ads_click", filled = true),
                        title = context.getString(R.string.settings_lyrics_tap_seek),
                        description = context.getString(R.string.settings_lyrics_tap_seek_desc),
                        toggleState = tapLyricsToSeek,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setTapLyricsToSeek(it)
                        }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("visibility_off", filled = true),
                        title = context.getString(R.string.lyrics_settings_autohide_controls),
                        description = context.getString(R.string.lyrics_settings_autohide_controls_desc),
                        toggleState = autoHideLyricsControls,
                        onToggleChange = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            appSettings.setAutoHideLyricsControls(it)
                        }
                    )
                )

                Material3SettingsGroup(
                    title = context.getString(R.string.settings_lyrics_gestures),
                    items = lyricsItems.map { item ->
                        toMaterial3SettingsItem(context = context, item = item, hapticFeedback = haptic)
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Tips
            item(key = "gesture_tips") {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.settings_quick_tips),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        GestureTipItem(
                            icon = MaterialSymbolIcon("swipe_vertical"),
                            text = context.getString(R.string.settings_swipe_up_open)
                        )
                        GestureTipItem(
                            icon = MaterialSymbolIcon("swipe_down"),
                            text = context.getString(R.string.settings_swipe_down_dismiss_tip)
                        )
                        GestureTipItem(
                            icon = MaterialSymbolIcon("touch_app"),
                            text = context.getString(R.string.settings_double_tap_artwork_tip)
                        )
                        GestureTipItem(
                            icon = MaterialSymbolIcon("speed"),
                            text = context.getString(R.string.settings_disable_unused_gestures)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}



@Composable
fun GestureTipItem(
    icon: MaterialSymbolIcon,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}