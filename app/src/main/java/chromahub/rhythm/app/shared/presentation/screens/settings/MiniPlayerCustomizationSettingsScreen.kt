/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType



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
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
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


// MiniPlayer Customization Screen
@Composable
fun MiniPlayerCustomizationSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptics = LocalHapticFeedback.current

    // MiniPlayer settings
    val miniPlayerThemeId by appSettings.miniPlayerThemeId.collectAsState()
    val isExpressiveActive = miniPlayerThemeId == "EXPRESSIVE"
    val miniPlayerProgressStyle by appSettings.miniPlayerProgressStyle.collectAsState()
    val miniPlayerShowProgress by appSettings.miniPlayerShowProgress.collectAsState()
    val miniPlayerShowArtwork by appSettings.miniPlayerShowArtwork.collectAsState()
    val miniPlayerArtworkSize by appSettings.miniPlayerArtworkSize.collectAsState()
    val miniPlayerCornerRadius by appSettings.miniPlayerCornerRadius.collectAsState()
    val miniPlayerShowTime by appSettings.miniPlayerShowTime.collectAsState()
    val miniPlayerAlwaysShowTablet by appSettings.miniPlayerAlwaysShowTablet.collectAsState()
    val expressiveShapesEnabled by appSettings.expressiveShapesEnabled.collectAsState()

    var showMiniPlayerProgressStyleSheet by remember { mutableStateOf(false) }
    var showMiniPlayerArtworkSizeSheet by remember { mutableStateOf(false) }
    var showMiniPlayerCornerRadiusSheet by remember { mutableStateOf(false) }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_miniplayer),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {

            // MiniPlayer Theme Selection Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.miniplayercustomizationsettingsscreen_miniplayer_theme),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Material3SettingsGroup(
                    items = listOf(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("palette"),
                            title = { Text(stringResource(R.string.miniplayercustomizationsettingsscreen_miniplayer_theme)) },
                            description = {
                                Column {
                                    Text(stringResource(R.string.miniplayercustomizationsettingsscreen_choose_between_rhythm_default))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    ExpressiveButtonGroup(
                                        items = listOf(
                                            stringResource(R.string.theme_rhythm),
                                            stringResource(R.string.theme_expressive)
                                        ),
                                        selectedIndex = if (miniPlayerThemeId == "EXPRESSIVE") 1 else 0,
                                        onItemClick = { index ->
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            if (index == 1) {
                                                appSettings.setMiniPlayerThemeId("EXPRESSIVE")
                                            } else {
                                                appSettings.setMiniPlayerThemeId("MATERIAL")
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

            // Progress Display Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = context.getString(R.string.settings_progress_display),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                val previewStyle = try {
                    ProgressStyle.valueOf(miniPlayerProgressStyle)
                } catch (e: IllegalArgumentException) {
                    ProgressStyle.NORMAL
                }
                if (isExpressiveActive) {
                    Text(
                        text = stringResource(R.string.miniplayercustomizationsettingsscreen_integrated_directly_into_the),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                } else if (miniPlayerShowProgress) {
                    StyledProgressBar(
                        progress = 0.45f,
                        style = previewStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        progressColor = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        height = 4.dp,
                        isPlaying = true
                    )
                } else {
                    Text(
                        text = context.getString(R.string.settings_progress_hidden),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                val progressSettingsItems = buildList {
                    add(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = RhythmIcons.Visibility,
                                title = context.getString(R.string.settings_show_progress),
                                description = if (isExpressiveActive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_show_progress_desc),
                                toggleState = miniPlayerShowProgress,
                                onToggleChange = { appSettings.setMiniPlayerShowProgress(it) },
                                enabled = !isExpressiveActive
                            )
                        )
                    )

                    if (miniPlayerShowProgress && !isExpressiveActive) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = haptics,
                                item = SettingItem(
                                    icon = MaterialSymbolIcon("linear_scale"),
                                    title = stringResource(R.string.settings_miniplayer_progress_style),
                                    description = miniPlayerProgressStyle.lowercase().replaceFirstChar { it.uppercase() },
                                    onClick = { showMiniPlayerProgressStyleSheet = true }
                                )
                            )
                        )
                    }
                }

                Material3SettingsGroup(
                    items = progressSettingsItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Artwork Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = context.getString(R.string.settings_artwork),
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
                                icon = RhythmIcons.Album,
                                title = context.getString(R.string.settings_show_artwork),
                                description = if (isExpressiveActive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_show_artwork_desc),
                                toggleState = miniPlayerShowArtwork,
                                onToggleChange = { appSettings.setMiniPlayerShowArtwork(it) },
                                enabled = !isExpressiveActive
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("photo_size_select_large"),
                                title = stringResource(R.string.settings_miniplayer_artwork_size),
                                description = if (isExpressiveActive) context.getString(R.string.lyrics_settings_not_supported_expressive) else "${miniPlayerArtworkSize}dp",
                                onClick = { showMiniPlayerArtworkSizeSheet = true },
                                enabled = !isExpressiveActive
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = if (expressiveShapesEnabled) {
                                SettingItem(
                                    icon = MaterialSymbolIcon("rounded_corner"),
                                    title = stringResource(R.string.settings_miniplayer_corner_radius),
                                    description = context.getString(R.string.settings_managed_by_expressive_shapes)
                                )
                            } else {
                                SettingItem(
                                    icon = MaterialSymbolIcon("rounded_corner"),
                                    title = stringResource(R.string.settings_miniplayer_corner_radius),
                                    description = context.getString(R.string.settings_value_dp, miniPlayerCornerRadius),
                                    onClick = { showMiniPlayerCornerRadiusSheet = true }
                                )
                            }
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Display Options Section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = context.getString(R.string.settings_display_options),
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
                                icon = MaterialSymbolIcon("timer"),
                                title = context.getString(R.string.settings_show_time),
                                description = if (isExpressiveActive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_show_time_desc),
                                toggleState = miniPlayerShowTime,
                                onToggleChange = { appSettings.setMiniPlayerShowTime(it) },
                                enabled = !isExpressiveActive
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("tablet"),
                                title = context.getString(R.string.settings_tablet_layout),
                                description = if (isExpressiveActive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_tablet_layout_desc),
                                toggleState = miniPlayerAlwaysShowTablet,
                                onToggleChange = { appSettings.setMiniPlayerAlwaysShowTablet(it) },
                                enabled = !isExpressiveActive
                            )
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
                                text = stringResource(R.string.settings_miniplayer_header),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = context.getString(R.string.settings_customize_miniplayer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // MiniPlayer Progress Style Bottom Sheet
    if (showMiniPlayerProgressStyleSheet) {
        ProgressStyleBottomSheet(
            title = stringResource(R.string.settings_miniplayer_progress_style),
            currentStyle = miniPlayerProgressStyle,
            onStyleSelected = { style ->
                appSettings.setMiniPlayerProgressStyle(style)
                showMiniPlayerProgressStyleSheet = false
            },
            onDismiss = { showMiniPlayerProgressStyleSheet = false },
            context = context,
            haptics = haptics
        )
    }

    // MiniPlayer Artwork Size Bottom Sheet
    if (showMiniPlayerArtworkSizeSheet) {
        MiniPlayerArtworkSizeSheet(
            currentSize = miniPlayerArtworkSize,
            onSizeSelected = { size ->
                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                appSettings.setMiniPlayerArtworkSize(size)
                showMiniPlayerArtworkSizeSheet = false
            },
            onDismiss = { showMiniPlayerArtworkSizeSheet = false },
            context = context,
            haptics = haptics
        )
    }

    // MiniPlayer Corner Radius Bottom Sheet
    if (showMiniPlayerCornerRadiusSheet) {
        MiniPlayerCornerRadiusSheet(
            currentRadius = miniPlayerCornerRadius,
            onRadiusSelected = { radius ->
                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                appSettings.setMiniPlayerCornerRadius(radius)
                showMiniPlayerCornerRadiusSheet = false
            },
            onDismiss = { showMiniPlayerCornerRadiusSheet = false },
            context = context,
            haptics = haptics
        )
    }
}
/**
 * Bottom sheet for choosing mini player artwork size (shared with the onboarding tour).
 */
@Composable
fun MiniPlayerArtworkSizeSheet(
    currentSize: Int,
    onSizeSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    context: Context,
    haptics: HapticFeedback
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var tempSize by remember { mutableIntStateOf(currentSize) }

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
            title = stringResource(R.string.settings_miniplayer_artwork_size),
            subtitle = "${tempSize}dp",
            visible = true
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            val sizeSliderState = remember(tempSize) {
                SliderState(
                    value = tempSize.toFloat(),
                    steps = 31,
                    trackRange = 40f..72f
                )
            }
            sizeSliderState.value = tempSize.toFloat()
            Slider(
                state = sizeSliderState,
                onValueChange = { tempSize = it.toInt() },
                onValueChangeFinished = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    onSizeSelected(tempSize)
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Bottom sheet for choosing mini player corner radius (shared with the onboarding tour).
 */
@Composable
fun MiniPlayerCornerRadiusSheet(
    currentRadius: Int,
    onRadiusSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    context: Context,
    haptics: HapticFeedback
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var tempRadius by remember { mutableIntStateOf(currentRadius) }

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
                    steps = 27,
                    trackRange = 0f..28f
                )
            }
            radiusSliderState.value = tempRadius.toFloat()
            Slider(
                state = radiusSliderState,
                onValueChange = { tempRadius = it.toInt() },
                onValueChangeFinished = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    onRadiusSelected(tempRadius)
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
