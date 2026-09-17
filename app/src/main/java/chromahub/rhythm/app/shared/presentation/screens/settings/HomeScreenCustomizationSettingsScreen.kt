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
import androidx.compose.ui.res.pluralStringResource
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenCustomizationSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptic = LocalHapticFeedback.current

    // State for home section order bottom sheet
    var showHomeSectionOrderSheet by remember { mutableStateOf(false) }

    // Collect all home screen settings
    val showRecentlyPlayed by appSettings.homeShowRecentlyPlayed.collectAsState()
    val showDiscoverCarousel by appSettings.homeShowDiscoverCarousel.collectAsState()
    val showArtists by appSettings.homeShowArtists.collectAsState()
    val showNewReleases by appSettings.homeShowNewReleases.collectAsState()
    val showRecentlyAdded by appSettings.homeShowRecentlyAdded.collectAsState()
    val showRecommended by appSettings.homeShowRecommended.collectAsState()
    val showListeningStats by appSettings.homeShowListeningStats.collectAsState()
    val discoverItemCount by appSettings.homeDiscoverItemCount.collectAsState()
    val recentlyPlayedCount by appSettings.homeRecentlyPlayedCount.collectAsState()
    val artistsCount by appSettings.homeArtistsCount.collectAsState()
    val newReleasesCount by appSettings.homeNewReleasesCount.collectAsState()
    val recentlyAddedCount by appSettings.homeRecentlyAddedCount.collectAsState()
    val recommendedCount by appSettings.homeRecommendedCount.collectAsState()

    // Discover Widget visibility settings
    val discoverShowAlbumName by appSettings.homeDiscoverShowAlbumName.collectAsState()
    val discoverShowArtistName by appSettings.homeDiscoverShowArtistName.collectAsState()
    val discoverShowYear by appSettings.homeDiscoverShowYear.collectAsState()
    val discoverShowPlayButton by appSettings.homeDiscoverShowPlayButton.collectAsState()
    val discoverShowGradient by appSettings.homeDiscoverShowGradient.collectAsState()

    // Show bottom sheet if requested
    if (showHomeSectionOrderSheet) {
        HomeSectionOrderBottomSheet(
            onDismiss = { showHomeSectionOrderSheet = false },
            appSettings = appSettings
        )
    }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_home_screen),
        showBackButton = true,
        onBackClick = {
            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
            onBackClick()
        }
    ) { modifier ->
        val lazyListState = rememberSaveable(
            saver = LazyListStateSaver
        ) {
            androidx.compose.foundation.lazy.LazyListState()
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            state = lazyListState,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp)
        ) {
            // ==================== HEADER CUSTOMIZATION ====================
            item(key = "header_customization_header", contentType = "section_header") {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = context.getString(R.string.settings_header_customization),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }
            
            // Consolidated Header Settings Card
            item(key = "header_settings_card", contentType = "settings_card") {
                val collapseBehavior by appSettings.headerCollapseBehavior.collectAsState()
                val displayMode by appSettings.homeHeaderDisplayMode.collectAsState()
                val visibilityMode by appSettings.homeAppIconVisibility.collectAsState()
                
                val displayLabel = when (displayMode) {
                    0 -> context.getString(R.string.option_icon).lowercase()
                    1 -> context.getString(R.string.option_name).lowercase()
                    2 -> context.getString(R.string.option_both).lowercase()
                    else -> context.getString(R.string.common_unknown).lowercase()
                }

                val headerItems = buildList {
                    add(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptic,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("unfold_less", filled = true),
                                title = context.getString(R.string.settings_always_start_collapsed),
                                description = context.getString(R.string.settings_start_collapsed),
                                toggleState = collapseBehavior == 1,
                                onToggleChange = { appSettings.setHeaderCollapseBehavior(if (it) 1 else 0) }
                            )
                        )
                    )

                    add(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptic,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("stars", filled = true),
                                title = context.getString(R.string.settings_header_display),
                                description = context.getString(R.string.settings_choose_header_content)
                            ),
                            description = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = context.getString(R.string.settings_choose_header_content),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    ExpressiveButtonGroup(
                                        items = listOf(
                                            stringResource(R.string.option_icon),
                                            stringResource(R.string.option_name),
                                            stringResource(R.string.option_both)
                                        ),
                                        selectedIndex = displayMode,
                                        onItemClick = { index ->
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            appSettings.setHomeHeaderDisplayMode(index)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        )
                    )

                    if (displayMode != 2) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = haptic,
                                item = SettingItem(
                                    icon = RhythmIcons.Visibility,
                                    title = context.getString(R.string.settings_visibility),
                                    description = context.getString(R.string.settings_visibility_desc, displayLabel)
                                ),
                                description = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = context.getString(R.string.settings_visibility_desc, displayLabel),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        ExpressiveButtonGroup(
                                            items = listOf(
                                                stringResource(R.string.option_always),
                                                stringResource(R.string.option_expanded),
                                                stringResource(R.string.option_collapsed)
                                            ),
                                            selectedIndex = visibilityMode,
                                            onItemClick = { index ->
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                appSettings.setHomeAppIconVisibility(index)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        )
                    }
                }

                Material3SettingsGroup(
                    items = headerItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // ==================== SECTION ORDER & VISIBILITY ====================
            item(key = "section_order_header", contentType = "section_header") {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = context.getString(R.string.settings_section_order_visibility),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }

            item(key = "section_order_button", contentType = "action_button") {
                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptic,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("reorder", filled = true),
                                title = context.getString(R.string.settings_reorder_toggle_sections),
                                description = context.getString(R.string.settings_customize_home_layout),
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    showHomeSectionOrderSheet = true
                                }
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // ==================== WIDGET ITEM COUNTS ====================
            item(key = "widget_counts_settings", contentType = "slider_group") {
                val widgetCountItems = buildList {
                    if (showRecentlyPlayed) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = haptic,
                                item = SettingItem(
                                    icon = MaterialSymbolIcon("history", filled = true),
                                    title = stringResource(R.string.settings_home_recently_played),
                                    description = pluralStringResource(R.plurals.settings_count_songs, recentlyPlayedCount, recentlyPlayedCount)
                                ),
                                description = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "$recentlyPlayedCount songs",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val recentlyPlayedSliderState = remember(recentlyPlayedCount) {
                                            SliderState(
                                                value = recentlyPlayedCount.toFloat(),
                                                steps = 8,
                                                trackRange = 3f..12f
                                            )
                                        }
                                        recentlyPlayedSliderState.value = recentlyPlayedCount.toFloat()
                                        Slider(
                                            state = recentlyPlayedSliderState,
                                            onValueChange = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                appSettings.setHomeRecentlyPlayedCount(it.toInt())
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        )
                    }

                    if (showArtists) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = haptic,
                                item = SettingItem(
                                    icon = MaterialSymbolIcon("people", filled = true),
                                    title = stringResource(R.string.settings_top_artists),
                                    description = pluralStringResource(R.plurals.settings_count_artists, artistsCount, artistsCount)
                                ),
                                description = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "$artistsCount artists",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val artistsSliderState = remember(artistsCount) {
                                            SliderState(
                                                value = artistsCount.toFloat(),
                                                steps = 15,
                                                trackRange = 4f..20f
                                            )
                                        }
                                        artistsSliderState.value = artistsCount.toFloat()
                                        Slider(
                                            state = artistsSliderState,
                                            onValueChange = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                appSettings.setHomeArtistsCount(it.toInt())
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        )
                    }

                    if (showNewReleases) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = haptic,
                                item = SettingItem(
                                    icon = MaterialSymbolIcon("new_releases", filled = true),
                                    title = stringResource(R.string.settings_new_releases),
                                    description = pluralStringResource(R.plurals.settings_count_albums, newReleasesCount, newReleasesCount)
                                ),
                                description = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "$newReleasesCount albums",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val newReleasesSliderState = remember(newReleasesCount) {
                                            SliderState(
                                                value = newReleasesCount.toFloat(),
                                                steps = 15,
                                                trackRange = 4f..20f
                                            )
                                        }
                                        newReleasesSliderState.value = newReleasesCount.toFloat()
                                        Slider(
                                            state = newReleasesSliderState,
                                            onValueChange = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                appSettings.setHomeNewReleasesCount(it.toInt())
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        )
                    }

                    if (showRecentlyAdded) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = haptic,
                                item = SettingItem(
                                    icon = MaterialSymbolIcon("library_add", filled = true),
                                    title = stringResource(R.string.settings_recently_added),
                                    description = pluralStringResource(R.plurals.settings_count_albums, recentlyAddedCount, recentlyAddedCount)
                                ),
                                description = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "$recentlyAddedCount albums",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val recentlyAddedSliderState = remember(recentlyAddedCount) {
                                            SliderState(
                                                value = recentlyAddedCount.toFloat(),
                                                steps = 15,
                                                trackRange = 4f..20f
                                            )
                                        }
                                        recentlyAddedSliderState.value = recentlyAddedCount.toFloat()
                                        Slider(
                                            state = recentlyAddedSliderState,
                                            onValueChange = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                appSettings.setHomeRecentlyAddedCount(it.toInt())
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        )
                    }

                    if (showRecommended) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = haptic,
                                item = SettingItem(
                                    icon = MaterialSymbolIcon("recommend", filled = true),
                                    title = stringResource(R.string.settings_recommended),
                                    description = pluralStringResource(R.plurals.settings_count_songs, recommendedCount, recommendedCount)
                                ),
                                description = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "$recommendedCount songs",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val recommendedSliderState = remember(recommendedCount) {
                                            SliderState(
                                                value = recommendedCount.toFloat(),
                                                steps = 5,
                                                trackRange = 2f..8f
                                            )
                                        }
                                        recommendedSliderState.value = recommendedCount.toFloat()
                                        Slider(
                                            state = recommendedSliderState,
                                            onValueChange = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                appSettings.setHomeRecommendedCount(it.toInt())
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        )
                    }
                }

                if (widgetCountItems.isNotEmpty()) {
                    Column {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = context.getString(R.string.settings_widget_item_counts),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Material3SettingsGroup(
                            items = widgetCountItems,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    }
                }
            }

            // ==================== DISCOVER CAROUSEL SETTINGS ====================
            item(key = "discover_carousel_settings", contentType = "settings_card") {
                AnimatedVisibility(visible = showDiscoverCarousel) {
                    Column {
                        val isDiscoverImmersiveMode = true

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = context.getString(R.string.settings_discover_carousel),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )

                        if (isDiscoverImmersiveMode) {
                            Text(
                                text = stringResource(R.string.homescreencustomizationsettingsscreen_immersive_mode_is_active),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )
                        }

                        val discoverToggleItems = buildList {
                            add(
                            SettingItem(
                                RhythmIcons.AlbumFilled,
                                context.getString(R.string.settings_discover_album_name),
                                context.getString(R.string.settings_discover_album_name_desc),
                                toggleState = discoverShowAlbumName,
                                onToggleChange = { appSettings.setHomeDiscoverShowAlbumName(it) }
                            )
                            )

                            add(
                            SettingItem(
                                RhythmIcons.ArtistFilled,
                                context.getString(R.string.settings_discover_artist_name),
                                context.getString(R.string.settings_discover_artist_name_desc),
                                toggleState = discoverShowArtistName,
                                onToggleChange = { appSettings.setHomeDiscoverShowArtistName(it) }
                            )
                            )

                            add(
                            SettingItem(
                                MaterialSymbolIcon("calendar_today", filled = true),
                                context.getString(R.string.settings_discover_release_year),
                                context.getString(R.string.settings_discover_release_year_desc),
                                toggleState = discoverShowYear,
                                onToggleChange = { appSettings.setHomeDiscoverShowYear(it) }
                            )
                            )

                            add(
                            SettingItem(
                                RhythmIcons.Play,
                                context.getString(R.string.settings_discover_play_button),
                                context.getString(R.string.settings_discover_play_button_desc),
                                toggleState = discoverShowPlayButton,
                                onToggleChange = { appSettings.setHomeDiscoverShowPlayButton(it) }
                            )
                            )

                            add(
                            SettingItem(
                                MaterialSymbolIcon("gradient", filled = true),
                                context.getString(R.string.settings_discover_gradient_overlay),
                                context.getString(R.string.settings_discover_gradient_overlay_desc),
                                toggleState = discoverShowGradient,
                                onToggleChange = { appSettings.setHomeDiscoverShowGradient(it) }
                            )
                            )
                        }

                        val discoverItems = buildList {
                            addAll(
                                discoverToggleItems.map { item ->
                                    toMaterial3SettingsItem(context = context, item = item, hapticFeedback = haptic)
                                }
                            )

                            add(
                                toMaterial3SettingsItem(
                                    context = context,
                                    hapticFeedback = haptic,
                                    item = SettingItem(
                                        icon = MaterialSymbolIcon("view_carousel", filled = true),
                                        title = stringResource(R.string.homescreencustomizationsettingsscreen_album_count),
                                        description = pluralStringResource(R.plurals.settings_count_albums, discoverItemCount, discoverItemCount)
                                    ),
                                    description = {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "$discoverItemCount albums",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            val discoverSliderState = remember(discoverItemCount) {
                                                SliderState(
                                                    value = discoverItemCount.toFloat(),
                                                    steps = 8,
                                                    trackRange = 3f..12f
                                                )
                                            }
                                            discoverSliderState.value = discoverItemCount.toFloat()
                                            Slider(
                                                state = discoverSliderState,
                                                onValueChange = {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                    appSettings.setHomeDiscoverItemCount(it.toInt())
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                )
                            )

                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Material3SettingsGroup(
                            items = discoverItems,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    }
                }
            }

            // Quick Tips Card
            item(key = "tips_card", contentType = "tips") {
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
                                text = stringResource(R.string.settings_quick_tips),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        HomeScreenTipItem(
                            icon = RhythmIcons.Visibility,
                            text = stringResource(R.string.settings_toggle_widgets)
                        )
                        HomeScreenTipItem(
                            icon = MaterialSymbolIcon("speed"),
                            text = stringResource(R.string.settings_disable_unused_sections)
                        )
                        HomeScreenTipItem(
                            icon = RhythmIcons.Album,
                            text = stringResource(R.string.settings_discover_carousel_info)
                        )
                        HomeScreenTipItem(
                            icon = RhythmIcons.TrendingUp,
                            text = stringResource(R.string.settings_statistics_update)
                        )
                    }
                }
            }

            item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}



@Composable
fun HomeScreenTipItem(
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



@Composable
fun CarouselStyleSelector(
    selectedStyle: Int,
    onStyleSelected: (Int) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val styles = listOf(
        Triple(0, context.getString(R.string.carousel_style_default), context.getString(R.string.carousel_style_default_desc)),
        Triple(1, context.getString(R.string.carousel_style_hero), context.getString(R.string.carousel_style_hero_desc))
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                // Icon container with expressive design matching TunerSettingRow
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(34.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 0.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("view_carousel", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.settings_carousel_style),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_carousel_style_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                styles.forEach { (style, title, description) ->
                    val isSelected = selectedStyle == style

                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onStyleSelected(style)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = if (isSelected)
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else
                            null
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = when (style) {
                                    0 -> MaterialSymbolIcon("view_column", filled = true)
                                    else -> MaterialSymbolIcon("center_focus_weak", filled = true)
                                },
                                contentDescription = null,
                                tint = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun HomeSettingsSliderCard(
    icon: ImageVector,
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            val sliderState = remember(valueRange, steps) {
                SliderState(
                    value = value,
                    steps = steps,
                    trackRange = valueRange
                )
            }
            sliderState.value = value

            Slider(
                state = sliderState,
                onValueChange = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onValueChange(it)
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }
    }
}



@Composable
fun HomeSettingsSliderRow(
    icon: ImageVector,
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon container with expressive design matching TunerSettingRow
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(34.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 0.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
        }

        Spacer(modifier = Modifier.height(8.dp))

        val sliderState = remember(valueRange, steps) {
            SliderState(
                value = value,
                steps = steps,
                trackRange = valueRange
            )
        }
        sliderState.value = value

        Slider(
            state = sliderState,
            onValueChange = {
                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                onValueChange(it)
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, end = 8.dp)
        )
    }
}