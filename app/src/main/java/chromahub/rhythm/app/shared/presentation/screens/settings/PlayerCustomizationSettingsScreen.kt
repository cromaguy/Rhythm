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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import chromahub.rhythm.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
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
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.LyricsApiPriorityBottomSheet
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
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationProgressDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationResultDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.AppRestartDialog
import chromahub.rhythm.app.shared.presentation.components.player.PlayerChipOrderBottomSheet
import chromahub.rhythm.app.shared.presentation.components.player.ExpressiveBottomButtonsOrderBottomSheet
import chromahub.rhythm.app.features.local.presentation.components.settings.HomeSectionOrderBottomSheet
import chromahub.rhythm.app.features.local.presentation.components.settings.LibraryTabOrderBottomSheet
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem

import chromahub.rhythm.app.shared.presentation.screens.settings.TunerSettingRow
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerSettingCard
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingItem
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingGroup


// Player Customization Screen
@Composable
fun PlayerCustomizationSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptics = LocalHapticFeedback.current

    // State variables
    val playerThemeId by appSettings.playerThemeId.collectAsState()
    val isExpressiveActive = playerThemeId != "MATERIAL"
    val playerShowGradientOverlay by appSettings.playerShowGradientOverlay.collectAsState()
    val playerShowSeekButtons by appSettings.playerShowSeekButtons.collectAsState()
    val playerTextAlignment by appSettings.playerTextAlignment.collectAsState()
    val playerShowSongInfoOnArtwork by appSettings.playerShowSongInfoOnArtwork.collectAsState()
    val playerArtworkCornerRadius by appSettings.playerArtworkCornerRadius.collectAsState()
    val playerShowAudioQualityBadges by appSettings.playerShowAudioQualityBadges.collectAsState()
    val expressiveShapesEnabled by appSettings.expressiveShapesEnabled.collectAsState()
    val playerAmbientBackdropEnabled by appSettings.playerAmbientBackdropEnabled.collectAsState()
    val playerAmbientBackdropIntensity by appSettings.playerAmbientBackdropIntensity.collectAsState()
    val playerAmbientInfiniteZoom by appSettings.playerAmbientInfiniteZoom.collectAsState()
    val playerAccentBackgroundEnabled by appSettings.playerAccentBackgroundEnabled.collectAsState()
    val playerMergeControlsToBottom by appSettings.playerMergeControlsToBottom.collectAsState()
    val playerGlassIntensity by appSettings.playerGlassIntensity.collectAsState()

    // Progress bar settings
    val playerProgressStyle by appSettings.playerProgressStyle.collectAsState()
    val playerProgressThumbStyle by appSettings.playerProgressThumbStyle.collectAsState()
    val playerProgressThumbRotate by appSettings.playerProgressThumbRotate.collectAsState()

    var showChipOrderBottomSheet by remember { mutableStateOf(false) }
    var showExpressiveBottomButtonsSheet by remember { mutableStateOf(false) }
    var showTextAlignmentSheet by remember { mutableStateOf(false) }
    var showCornerRadiusSheet by remember { mutableStateOf(false) }
    var showPlayerProgressStyleSheet by remember { mutableStateOf(false) }
    var showPlayerThumbStyleSheet by remember { mutableStateOf(false) }
    var showAmbientIntensitySheet by remember { mutableStateOf(false) }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_player),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {

            // Playback Theme Selection Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.playercustomizationsettingsscreen_player_theme),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Material3SettingsGroup(
                    items = listOf(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("palette"),
                            title = { Text(stringResource(R.string.playercustomizationsettingsscreen_playback_theme)) },
                            description = {
                                Column {
                                    Text(stringResource(R.string.miniplayercustomizationsettingsscreen_choose_between_rhythm_default))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    ExpressiveButtonGroup(
                                        items = listOf(
                                            "Rhythm",
                                            "Expressive"
                                        ),
                                        selectedIndex = if (playerThemeId == "MATERIAL") 0 else 1,
                                        onItemClick = { index ->
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            if (index == 1) {
                                                appSettings.setPlayerThemeId("EXPRESSIVE")
                                            } else {
                                                appSettings.setPlayerThemeId("MATERIAL")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Player Controls Section — only shown for Rhythm (Material) theme; Expressive has no chips
            if (!isExpressiveActive) item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = context.getString(R.string.settings_player_controls),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("reorder"),
                                title = context.getString(R.string.settings_chip_order),
                                description = context.getString(R.string.settings_chip_order_desc),
                                onClick = { showChipOrderBottomSheet = true }
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }


            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.settings_display_options),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("gradient"),
                                title = context.getString(R.string.settings_artwork_overlay),
                                description = if (isExpressiveActive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_artwork_overlay_desc),
                                toggleState = playerShowGradientOverlay,
                                onToggleChange = { appSettings.setPlayerShowGradientOverlay(it) },
                                enabled = !isExpressiveActive
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = RhythmIcons.Info,
                                title = context.getString(R.string.settings_song_info_artwork),
                                description = if (isExpressiveActive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_song_info_artwork_desc),
                                toggleState = playerShowSongInfoOnArtwork,
                                onToggleChange = { appSettings.setPlayerShowSongInfoOnArtwork(it) },
                                enabled = !isExpressiveActive
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("high_quality"),
                                title = context.getString(R.string.settings_audio_quality_badges),
                                description = context.getString(R.string.settings_audio_quality_badges_desc),
                                toggleState = playerShowAudioQualityBadges,
                                onToggleChange = { appSettings.setPlayerShowAudioQualityBadges(it) }
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            if (isExpressiveActive) item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.settings_expressive_player),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("blur_on"),
                                title = context.getString(R.string.player_ambient_backdrop),
                                description = context.getString(R.string.player_ambient_desc),
                                toggleState = playerAmbientBackdropEnabled,
                                onToggleChange = { appSettings.setPlayerAmbientBackdropEnabled(it) }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("zoom_out_map"),
                                title = context.getString(R.string.player_ambient_motion_zoom),
                                description = context.getString(R.string.player_ambient_motion_zoom_desc),
                                toggleState = playerAmbientInfiniteZoom,
                                onToggleChange = { appSettings.setPlayerAmbientInfiniteZoom(it) },
                                enabled = playerAmbientBackdropEnabled
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("colorize"),
                                title = context.getString(R.string.player_accent_background),
                                description = context.getString(R.string.player_accent_background_desc),
                                toggleState = playerAccentBackgroundEnabled,
                                onToggleChange = { appSettings.setPlayerAccentBackgroundEnabled(it) }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("merge_type"),
                                title = context.getString(R.string.player_merge_controls),
                                description = context.getString(R.string.player_merge_controls_desc),
                                toggleState = playerMergeControlsToBottom,
                                onToggleChange = { appSettings.setPlayerMergeControlsToBottom(it) }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("reorder"),
                                title = context.getString(R.string.settings_expressive_bottom_buttons),
                                description = context.getString(R.string.settings_expressive_bottom_buttons_desc),
                                onClick = { showExpressiveBottomButtonsSheet = true }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("opacity"),
                                title = context.getString(R.string.player_ambient_intensity),
                                description = if (playerAmbientBackdropEnabled) context.getString(R.string.settings_value_percent, (playerAmbientBackdropIntensity * 100).toInt()) else context.getString(R.string.player_ambient_intensity_requires_ambient),
                                onClick = { if (playerAmbientBackdropEnabled) showAmbientIntensitySheet = true },
                                enabled = playerAmbientBackdropEnabled
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = context.getString(R.string.settings_layout_options),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = RhythmIcons.Forward10,
                                title = context.getString(R.string.settings_seek_buttons),
                                description = if (isExpressiveActive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_seek_buttons_desc),
                                toggleState = playerShowSeekButtons,
                                onToggleChange = { appSettings.setPlayerShowSeekButtons(it) },
                                enabled = !isExpressiveActive
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("format_align_center"),
                                title = context.getString(R.string.settings_text_alignment),
                                description = if (isExpressiveActive) context.getString(R.string.lyrics_settings_not_supported_expressive) else when(playerTextAlignment) {
                                    "START" -> context.getString(R.string.settings_left_aligned)
                                    "END" -> context.getString(R.string.settings_right_aligned)
                                    else -> context.getString(R.string.settings_center_aligned)
                                },
                                onClick = { showTextAlignmentSheet = true },
                                enabled = !isExpressiveActive
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Progress Bar Style Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.settings_progress_display),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                val previewStyle = try {
                    ProgressStyle.valueOf(playerProgressStyle)
                } catch (e: IllegalArgumentException) {
                    ProgressStyle.WAVY
                }
                val previewThumbStyle = ThumbStyle.fromStorage(playerProgressThumbStyle)
                StyledProgressBar(
                    progress = 0.65f,
                    style = previewStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    progressColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    height = 8.dp,
                    isPlaying = true,
                    showThumb = previewThumbStyle != ThumbStyle.NONE,
                    thumbStyle = previewThumbStyle,
                    thumbSize = 14.dp,
                    rotateThumbWhenPlaying = playerProgressThumbRotate
                )

                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("linear_scale"),
                                title = stringResource(R.string.settings_miniplayer_progress_style),
                                description = playerProgressStyle.lowercase().replaceFirstChar { it.uppercase() },
                                onClick = { showPlayerProgressStyleSheet = true }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("touch_app"),
                                title = context.getString(R.string.settings_thumb_style),
                                description = playerProgressThumbStyle.lowercase().replaceFirstChar { it.uppercase() },
                                onClick = { showPlayerThumbStyleSheet = true }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("rotate_right"),
                                title = context.getString(R.string.settings_thumb_rotate),
                                description = context.getString(R.string.settings_thumb_rotate_desc),
                                toggleState = playerProgressThumbRotate,
                                onToggleChange = { appSettings.setPlayerProgressThumbRotate(it) }
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Artwork Customization Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.settings_artwork),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = if (isExpressiveActive) {
                                SettingItem(
                                    icon = MaterialSymbolIcon("rounded_corner"),
                                    title = stringResource(R.string.settings_miniplayer_corner_radius),
                                    description = context.getString(R.string.lyrics_settings_not_supported_expressive),
                                    enabled = false
                                )
                            } else if (expressiveShapesEnabled) {
                                SettingItem(
                                    icon = MaterialSymbolIcon("rounded_corner"),
                                    title = stringResource(R.string.settings_miniplayer_corner_radius),
                                    description = context.getString(R.string.settings_managed_by_expressive_shapes),
                                    enabled = false
                                )
                            } else {
                                SettingItem(
                                    icon = MaterialSymbolIcon("rounded_corner"),
                                    title = stringResource(R.string.settings_miniplayer_corner_radius),
                                    description = context.getString(R.string.settings_value_dp, playerArtworkCornerRadius),
                                    onClick = { showCornerRadiusSheet = true }
                                )
                            }
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Description Card
            item {
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
                                imageVector = RhythmIcons.Info,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.settings_player_screen),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        PlayerTipItem(
                            icon = MaterialSymbolIcon("swipe_down"),
                            text = context.getString(R.string.settings_swipe_down_dismiss_tip)
                        )
                        PlayerTipItem(
                            icon = MaterialSymbolIcon("touch_app"),
                            text = context.getString(R.string.settings_double_tap_artwork_tip)
                        )
                        PlayerTipItem(
                            icon = MaterialSymbolIcon("speed"),
                            text = context.getString(R.string.settings_disable_unused_gestures)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showChipOrderBottomSheet) {
        PlayerChipOrderBottomSheet(
            onDismiss = { showChipOrderBottomSheet = false },
            appSettings = appSettings,
            haptics = haptics
        )
    }

    if (showExpressiveBottomButtonsSheet) {
        ExpressiveBottomButtonsOrderBottomSheet(
            onDismiss = { showExpressiveBottomButtonsSheet = false },
            appSettings = appSettings,
            haptics = haptics,
            initialModeIndex = if (playerMergeControlsToBottom) 1 else 0
        )
    }

    if (showTextAlignmentSheet) {
        PlayerTextAlignmentBottomSheet(
            currentAlignment = playerTextAlignment,
            onAlignmentSelected = { value ->
                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                appSettings.setPlayerTextAlignment(value)
                showTextAlignmentSheet = false
            },
            onDismiss = { showTextAlignmentSheet = false },
            context = context,
            haptics = haptics
        )
    }

    // Player Progress Style Bottom Sheet
    if (showPlayerProgressStyleSheet) {
        ProgressStyleBottomSheet(
            title = stringResource(R.string.settings_miniplayer_progress_style),
            currentStyle = playerProgressStyle,
            onStyleSelected = { style ->
                appSettings.setPlayerProgressStyle(style)
                showPlayerProgressStyleSheet = false
            },
            onDismiss = { showPlayerProgressStyleSheet = false },
            context = context,
            haptics = haptics
        )
    }

    // Player Thumb Style Bottom Sheet
    if (showPlayerThumbStyleSheet) {
        ThumbStyleBottomSheet(
            title = stringResource(R.string.settings_thumb_style),
            currentStyle = playerProgressThumbStyle,
            onStyleSelected = { style ->
                appSettings.setPlayerProgressThumbStyle(style)
                showPlayerThumbStyleSheet = false
            },
            onDismiss = { showPlayerThumbStyleSheet = false },
            context = context,
            haptics = haptics
        )
    }

    // Corner Radius Bottom Sheet
    if (showCornerRadiusSheet) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        var tempRadius by remember { mutableIntStateOf(playerArtworkCornerRadius) }

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
            onDismissRequest = { showCornerRadiusSheet = false },
            sheetState = sheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
            },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
        ) {
            StandardBottomSheetHeader(
                title = stringResource(R.string.settings_miniplayer_corner_radius),
                subtitle = "${tempRadius}dp",
                visible = true
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                val radiusSliderState = remember(tempRadius) {
                    SliderState(
                        value = tempRadius.toFloat(),
                        steps = 39,
                        trackRange = 0f..40f
                    )
                }
                radiusSliderState.value = tempRadius.toFloat()
                Slider(
                    state = radiusSliderState,
                    onValueChange = { tempRadius = it.toInt() },
                    onValueChangeFinished = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        appSettings.setPlayerArtworkCornerRadius(tempRadius)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Info card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(R.string.settings_adjust_artwork_corners),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Ambient Intensity Bottom Sheet
    if (showAmbientIntensitySheet) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        var tempIntensity by remember { mutableFloatStateOf(playerAmbientBackdropIntensity) }

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
            onDismissRequest = { showAmbientIntensitySheet = false },
            sheetState = sheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
            },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
        ) {
            StandardBottomSheetHeader(
                title = context.getString(R.string.player_ambient_intensity),
                subtitle = "${(tempIntensity * 100).toInt()}%",
                visible = true
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                val intensitySliderState = remember(tempIntensity) {
                    SliderState(
                        value = tempIntensity,
                        steps = 39,
                        trackRange = 0.0f..1.0f
                    )
                }
                intensitySliderState.value = tempIntensity
                Slider(
                    state = intensitySliderState,
                    onValueChange = { tempIntensity = it },
                    onValueChangeFinished = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        appSettings.setPlayerAmbientBackdropIntensity(tempIntensity)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "50%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "100%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Info card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(R.string.player_ambient_intensity_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}



@Composable
fun SettingRow(
    icon: MaterialSymbolIcon,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    toggleState: Boolean? = null,
    onToggleChange: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onClick()
                }
                else Modifier
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                .padding(8.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (toggleState != null && onToggleChange != null) {
            TunerAnimatedSwitch(
                checked = toggleState,
                onCheckedChange = {
                    onToggleChange(it)
                }
            )
        } else if (onClick != null) {
            Icon(
                imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                contentDescription = stringResource(R.string.cd_navigate),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



@Composable
fun PlayerTipItem(
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



/**
 * Bottom sheet for selecting progress bar style with visual previews
 */
@Composable
fun ProgressStyleBottomSheet(
    title: String,
    currentStyle: String,
    onStyleSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    context: Context,
    haptics: HapticFeedback
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    val progressStyles = listOf(
        ProgressStyleOption("NORMAL", "Normal", MaterialSymbolIcon("linear_scale"), "Standard progress bar"),
        ProgressStyleOption("WAVY", "Wavy", MaterialSymbolIcon("graphic_eq"), "Animated wavy line"),
        ProgressStyleOption("ROUNDED", "Rounded", MaterialSymbolIcon("rounded_corner"), "Pill-shaped progress"),
        ProgressStyleOption("THIN", "Thin", RhythmIcons.Remove, "Thin elegant line"),
        ProgressStyleOption("THICK", "Thick", RhythmIcons.DragHandle, "Bold thick bar"),
        ProgressStyleOption("GRADIENT", "Gradient", MaterialSymbolIcon("gradient"), "Multi-color gradient"),
        ProgressStyleOption("SEGMENTED", "Segmented", MaterialSymbolIcon("more_horiz"), "Segmented blocks"),
        ProgressStyleOption("DOTS", "Dots", MaterialSymbolIcon("fiber_manual_record"), "Dot indicators")
    )

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = title,
            subtitle = "",
            visible = true
        )

        val gridState = rememberLazyGridState()

        AdaptiveSheetScrollContainer(
            gridState = gridState,
            modifier = Modifier.fillMaxWidth()
        ) { endPadding ->
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp + endPadding, top = 8.dp, bottom = 24.dp)
            ) {
                items(progressStyles) { styleOption ->
                    val isSelected = currentStyle == styleOption.id
                    val progressStyleEnum = try {
                        ProgressStyle.valueOf(styleOption.id)
                    } catch (e: IllegalArgumentException) {
                        ProgressStyle.NORMAL
                    }

                    Card(
                        modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            onStyleSelected(styleOption.id)
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Preview of the style
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                                    .height(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                StyledProgressBar(
                                    progress = 0.6f,
                                    style = progressStyleEnum,
                                    modifier = Modifier.fillMaxWidth(),
                                    progressColor = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    trackColor = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    height = 6.dp,
                                    isPlaying = true
                                )
                            }

                            // Style name — bottom-left, larger, clipped at the card shape (no ellipsis)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                            ) {
                                Text(
                                    text = styleOption.label,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 0.dp,
                                                topEnd = 20.dp,
                                                bottomEnd = 20.dp,
                                                bottomStart = 0.dp
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



/**
 * Data class for progress style options
 */
data class ProgressStyleOption(
    val id: String,
    val label: String,
    val icon: MaterialSymbolIcon,
    val description: String
)

/**
 * Data class for thumb style options
 */
data class ThumbStyleOption(
    val id: String,
    val label: String,
    val icon: MaterialSymbolIcon,
    val description: String
)

/**
 * Bottom sheet for selecting thumb style
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThumbStyleBottomSheet(
    title: String,
    currentStyle: String,
    onStyleSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    context: Context,
    haptics: HapticFeedback
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    val thumbStyles = listOf(
        ThumbStyleOption("NONE", "None", RhythmIcons.VisibilityOff, "No thumb indicator"),
        ThumbStyleOption("DEFAULT", "Default", MaterialSymbolIcon("fiber_manual_record"), "Official M3 slider thumb"),
        ThumbStyleOption("CIRCLE", "Circle", MaterialSymbolIcon("circle"), "M3 circle"),
        ThumbStyleOption("SQUARE", "Square", MaterialSymbolIcon("crop_square"), "M3 rounded square"),
        ThumbStyleOption("PILL", "Pill", MaterialSymbolIcon("rounded_corner"), "M3 pill"),
        ThumbStyleOption("DIAMOND", "Diamond", MaterialSymbolIcon("diamond"), "M3 diamond"),
        ThumbStyleOption("FLOWER", "Flower", MaterialSymbolIcon("local_florist"), "M3 flower"),
        ThumbStyleOption("HEART", "Heart", MaterialSymbolIcon("favorite"), "M3 heart"),
        ThumbStyleOption("COOKIE", "Cookie", MaterialSymbolIcon("cookie"), "M3 6-sided cookie"),
        ThumbStyleOption("PUFFY", "Puffy", MaterialSymbolIcon("cloud"), "M3 puffy")
    )

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = title,
            subtitle = "",
            visible = true
        )

        val gridState = rememberLazyGridState()

        AdaptiveSheetScrollContainer(
            gridState = gridState,
            modifier = Modifier.fillMaxWidth()
        ) { endPadding ->
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp + endPadding, top = 8.dp, bottom = 24.dp)
            ) {
                items(thumbStyles) { styleOption ->
                    val isSelected = currentStyle == styleOption.id
                    val thumbStyleEnum = ThumbStyle.fromStorage(styleOption.id)

                    Card(
                        modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            onStyleSelected(styleOption.id)
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Preview of the thumb style
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                                    .height(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                StyledProgressBar(
                                    progress = 0.6f,
                                    style = ProgressStyle.NORMAL,
                                    modifier = Modifier.fillMaxWidth(),
                                    progressColor = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    trackColor = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                    height = 6.dp,
                                    isPlaying = true,
                                    showThumb = thumbStyleEnum != ThumbStyle.NONE,
                                    thumbStyle = thumbStyleEnum,
                                    thumbSize = 12.dp
                                )
                            }

                            // Style name — bottom-left, larger, clipped at the card shape (no ellipsis)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                            ) {
                                Text(
                                    text = styleOption.label,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 0.dp,
                                                topEnd = 20.dp,
                                                bottomEnd = 20.dp,
                                                bottomStart = 0.dp
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
/**
 * Bottom sheet for choosing player text alignment (shared with the onboarding tour).
 */
@Composable
fun PlayerTextAlignmentBottomSheet(
    currentAlignment: String,
    onAlignmentSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    context: Context,
    haptics: HapticFeedback
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = stringResource(R.string.settings_player_text_alignment),
            subtitle = "",
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
                    .padding(start = 24.dp, end = 24.dp + endPadding, top = 8.dp, bottom = 24.dp)
            ) {
                listOf(
                    Triple("START", "Left", MaterialSymbolIcon("align_horizontal_left", filled = true)),
                    Triple("CENTER", "Center", MaterialSymbolIcon("format_align_center")),
                    Triple("END", "Right", MaterialSymbolIcon("align_horizontal_right", filled = true))
                ).forEach { (value, label, icon) ->
                    val isSelected = currentAlignment == value
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            onAlignmentSelected(value)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (isSelected) {
                                Icon(
                                    imageVector = RhythmIcons.Check,
                                    contentDescription = stringResource(R.string.streaming_selected),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
