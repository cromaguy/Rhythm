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
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.LyricsApiPriorityBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.LrcRenameBehaviorBottomSheet
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


// Lyrics Settings Screen
@Composable
fun LyricsSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val hapticFeedback = LocalHapticFeedback.current

    val lyricsSourcePreference by appSettings.lyricsSourcePreference.collectAsState()
    val bluetoothLyricsEnabled by appSettings.bluetoothLyricsEnabled.collectAsState()
    val broadcastStatusEnabled by appSettings.broadcastStatusEnabled.collectAsState()
    var showPriorityBottomSheet by remember { mutableStateOf(false) }
    var showLrcRenameBottomSheet by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            scope.launch {
                val success = appSettings.exportLyricsPreferencesToCsv(context, it)
                if (success) {
                    Toast.makeText(context, context.getString(R.string.lyrics_exported_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.lyrics_export_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                val success = appSettings.importLyricsPreferencesFromCsv(context, it)
                if (success) {
                    Toast.makeText(context, context.getString(R.string.lyrics_imported_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.lyrics_import_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val showLyrics by appSettings.showLyrics.collectAsState()
    val tapLyricsToFullScreen by appSettings.tapLyricsToFullScreen.collectAsState()
    val tapLyricsToSeek by appSettings.tapLyricsToSeek.collectAsState()
    val keepScreenOnLyrics by appSettings.keepScreenOnLyrics.collectAsState()
    val autoHideLyricsControls by appSettings.autoHideLyricsControls.collectAsState()
    val showLyricsBackgroundArtwork by appSettings.showLyricsBackgroundArtwork.collectAsState()
    val playerShowArtBelowLyrics by appSettings.playerShowArtBelowLyrics.collectAsState()
    val playerLyricsTransition by appSettings.playerLyricsTransition.collectAsState()
    val playerLyricsTextSize by appSettings.playerLyricsTextSize.collectAsState()
    val playerLyricsAlignment by appSettings.playerLyricsAlignment.collectAsState()
    val playerThemeId by appSettings.playerThemeId.collectAsState()
    val isExpressiveActive = playerThemeId != "MATERIAL"

    val lyricBold by appSettings.lyricBold.collectAsState()
    val trimLyrics by appSettings.trimLyrics.collectAsState()
    val lyricNoAnimation by appSettings.lyricNoAnimation.collectAsState()
    val translationAutoWord by appSettings.translationAutoWord.collectAsState()
    val showLyricsTranslation by appSettings.showLyricsTranslation.collectAsState()
    val showLyricsRomanization by appSettings.showLyricsRomanization.collectAsState()

    CollapsibleHeaderScreen(
        title = context.getString(R.string.lyrics_settings_title),
        showBackButton = true,
        onBackClick = {
            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
            onBackClick()
        }
    ) { modifier ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 1. General & Behavior Settings
            item {
                Text(
                    text = context.getString(R.string.lyrics_settings_general),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                Material3SettingsGroup(
                    items = buildList {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = MaterialSymbolIcon("lyrics", filled = true),
                                    title = context.getString(R.string.settings_show_lyrics),
                                    description = context.getString(R.string.settings_show_lyrics_desc),
                                    toggleState = showLyrics,
                                    onToggleChange = { appSettings.setShowLyrics(it) }
                                )
                            )
                        )
                        if (showLyrics) {
                            add(
                                toMaterial3SettingsItem(
                                    context = context,
                                    hapticFeedback = hapticFeedback,
                                    item = SettingItem(
                                        icon = MaterialSymbolIcon("fullscreen", filled = true),
                                        title = stringResource(R.string.playercustomizationsettingsscreen_tap_lyrics_for_immersive),
                                        description = context.getString(R.string.lyrics_settings_open_fullscreen_desc),
                                        toggleState = tapLyricsToFullScreen,
                                        onToggleChange = { appSettings.setTapLyricsToFullScreen(it) }
                                    )
                                )
                            )
                            add(
                                toMaterial3SettingsItem(
                                    context = context,
                                    hapticFeedback = hapticFeedback,
                                    item = SettingItem(
                                        icon = MaterialSymbolIcon("ads_click"),
                                        title = stringResource(R.string.settings_lyrics_tap_seek),
                                        description = stringResource(R.string.settings_lyrics_tap_seek_desc),
                                        toggleState = tapLyricsToSeek,
                                        onToggleChange = { appSettings.setTapLyricsToSeek(it) }
                                    )
                                )
                            )
                            add(
                                toMaterial3SettingsItem(
                                    context = context,
                                    hapticFeedback = hapticFeedback,
                                    item = SettingItem(
                                        icon = MaterialSymbolIcon("blur_on"),
                                        title = stringResource(R.string.settings_show_lyrics_background_artwork),
                                        description = stringResource(R.string.settings_show_lyrics_background_artwork_desc),
                                        toggleState = showLyricsBackgroundArtwork,
                                        onToggleChange = { appSettings.setShowLyricsBackgroundArtwork(it) }
                                    )
                                )
                            )
                            add(
                                toMaterial3SettingsItem(
                                    context = context,
                                    hapticFeedback = hapticFeedback,
                                    item = SettingItem(
                                        icon = MaterialSymbolIcon("translate"),
                                        title = context.getString(R.string.lyrics_show_translation),
                                        description = context.getString(R.string.lyrics_show_translation_desc),
                                        toggleState = showLyricsTranslation,
                                        onToggleChange = { appSettings.setShowLyricsTranslation(it) }
                                    )
                                )
                            )
                            add(
                                toMaterial3SettingsItem(
                                    context = context,
                                    hapticFeedback = hapticFeedback,
                                    item = SettingItem(
                                        icon = MaterialSymbolIcon("subtitles"),
                                        title = context.getString(R.string.lyrics_show_romanization),
                                        description = context.getString(R.string.lyrics_show_romanization_desc),
                                        toggleState = showLyricsRomanization,
                                        onToggleChange = { appSettings.setShowLyricsRomanization(it) }
                                    )
                                )
                            )
                            add(
                                toMaterial3SettingsItem(
                                    context = context,
                                    hapticFeedback = hapticFeedback,
                                    item = SettingItem(
                                        icon = MaterialSymbolIcon("light_mode", filled = true),
                                        title = context.getString(R.string.settings_keep_screen_on_lyrics),
                                        description = context.getString(R.string.settings_keep_screen_on_lyrics_desc),
                                        toggleState = keepScreenOnLyrics,
                                        onToggleChange = { appSettings.setKeepScreenOnLyrics(it) }
                                    )
                                )
                            )
                            add(
                                toMaterial3SettingsItem(
                                    context = context,
                                    hapticFeedback = hapticFeedback,
                                    item = SettingItem(
                                        icon = MaterialSymbolIcon("visibility_off"),
                                        title = context.getString(R.string.lyrics_settings_autohide_controls),
                                        description = context.getString(R.string.lyrics_settings_autohide_controls_desc),
                                        toggleState = autoHideLyricsControls,
                                        onToggleChange = { appSettings.setAutoHideLyricsControls(it) }
                                    )
                                )
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }
            // 2. Lyrics Source Priority
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = context.getString(R.string.lyrics_source_priority_title),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                val sourceOptions = listOf<Pair<chromahub.rhythm.app.shared.data.model.LyricsSourcePreference, Triple<String, String, MaterialSymbolIcon>>>(
                    chromahub.rhythm.app.shared.data.model.LyricsSourcePreference.EMBEDDED_FIRST to Triple(
                        context.getString(R.string.lyrics_settings_embedded_first),
                        context.getString(R.string.lyrics_settings_embedded_first_desc),
                        RhythmIcons.MusicNote
                    ),
                    chromahub.rhythm.app.shared.data.model.LyricsSourcePreference.API_FIRST to Triple(
                        context.getString(R.string.lyrics_settings_online_first),
                        context.getString(R.string.lyrics_settings_online_first_desc),
                        MaterialSymbolIcon("cloud_queue")
                    ),
                    chromahub.rhythm.app.shared.data.model.LyricsSourcePreference.LOCAL_FIRST to Triple(
                        context.getString(R.string.lyrics_settings_local_first),
                        context.getString(R.string.lyrics_settings_local_first_desc),
                        RhythmIcons.Storage
                    )
                )

                Material3SettingsGroup(
                    items = sourceOptions.map { (preference, info) ->
                        val (title, description, icon) = info
                        val isSelected = lyricsSourcePreference == preference
                        
                        Material3SettingsItem(
                            icon = icon,
                            title = { Text(title) },
                            description = { Text(description) },
                            isHighlighted = isSelected, 
                            trailingContent = {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null 
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(
                                    context,
                                    hapticFeedback,
                                    HapticType.LIGHT
                                )
                                appSettings.setLyricsSourcePreference(preference)
                            }
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // 3. Online APIs
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.lyricssourcesettingsscreen_online_api_options),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                val apiPriority by appSettings.lyricsApiPriority.collectAsState()
                val apiFallback by appSettings.lyricsApiFallbackRetry.collectAsState()

                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("lyrics"),
                                title = stringResource(R.string.lyricssourcesettingsscreen_lyrics_api_priority),
                                description = when (apiPriority) {
                                    chromahub.rhythm.app.shared.data.model.LyricsApiPriority.BETTERLYRICS_FIRST -> context.getString(R.string.lyrics_settings_betterlyrics_first)
                                    chromahub.rhythm.app.shared.data.model.LyricsApiPriority.LYRICALLY_FIRST -> context.getString(R.string.lyrics_settings_lyrically_first)
                                    chromahub.rhythm.app.shared.data.model.LyricsApiPriority.LRCLIB_FIRST -> context.getString(R.string.lyrics_settings_lrclib_first)
                                },
                                onClick = {
                                    showPriorityBottomSheet = true
                                }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("compare_arrows"),
                                title = stringResource(R.string.lyricssourcesettingsscreen_retry_using_fallbacks),
                                description = context.getString(R.string.lyrics_settings_fallback_apis_desc),
                                toggleState = apiFallback,
                                onToggleChange = { appSettings.setLyricsApiFallbackRetry(it) }
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // 4. Styling & Typography Customization (Only active if lyrics enabled)
            if (showLyrics) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = context.getString(R.string.lyrics_settings_display_styling),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )

                    val stylingItems = buildList {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = MaterialSymbolIcon("format_size"),
                                    title = context.getString(R.string.settings_lyrics_text_size),
                                    description = context.getString(R.string.lyrics_settings_size_percentage, (playerLyricsTextSize * 100).toInt())
                                ),
                                description = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = context.getString(R.string.lyrics_settings_size_percentage, (playerLyricsTextSize * 100).toInt()),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Slider(
                                            value = playerLyricsTextSize,
                                            onValueChange = { appSettings.setPlayerLyricsTextSize(it) },                                                valueRange = 0.5f..2.0f,
                                                modifier = Modifier.fillMaxWidth(),
                                                onValueChangeFinished = {
                                                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                                }
                                            )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(context.getString(R.string.lyrics_size_min), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(context.getString(R.string.lyrics_size_max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            )
                        )
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = MaterialSymbolIcon("format_align_center"),
                                    title = stringResource(R.string.settings_lyrics_alignment),
                                    description = context.getString(R.string.settings_lyrics_alignment_desc)
                                ),
                                description = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = context.getString(R.string.settings_lyrics_alignment_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        ExpressiveButtonGroup(
                                            items = listOf(
                                                context.getString(R.string.alignment_left),
                                                context.getString(R.string.alignment_center),
                                                context.getString(R.string.alignment_right)
                                            ),
                                            selectedIndex = when (playerLyricsAlignment) {
                                                "START" -> 0
                                                "END" -> 2
                                                else -> 1
                                            },
                                            onItemClick = { index ->
                                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                                appSettings.setPlayerLyricsAlignment(when (index) {
                                                    0 -> "START"
                                                    2 -> "END"
                                                    else -> "CENTER"
                                                })
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        )
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = MaterialSymbolIcon("animation"),
                                    title = context.getString(R.string.settings_lyrics_transition),
                                    description = context.getString(R.string.lyrics_settings_switch_animation_desc)
                                ),
                                description = {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.settings_lyrics_transition_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        ExpressiveButtonGroup(
                                            items = listOf(
                                                context.getString(R.string.animation_slide),
                                                context.getString(R.string.animation_fade),
                                                context.getString(R.string.animation_scale),
                                                context.getString(R.string.animation_up)
                                            ),
                                            selectedIndex = playerLyricsTransition,
                                            onItemClick = { index ->
                                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                                appSettings.setPlayerLyricsTransition(index)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            )
                        )
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = RhythmIcons.Image,
                                    title = context.getString(R.string.settings_show_art_below_lyrics),
                                    description = if (isExpressiveActive) context.getString(R.string.lyrics_settings_not_supported_expressive) else context.getString(R.string.settings_show_art_below_lyrics_desc),
                                    toggleState = playerShowArtBelowLyrics,
                                    onToggleChange = { appSettings.setPlayerShowArtBelowLyrics(it) },
                                    enabled = !isExpressiveActive
                                )
                            )
                        )
                    }

                    Material3SettingsGroup(
                        items = stylingItems,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                }
            }

            // 5. Customization, Formatting & Behaviors
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = context.getString(R.string.lyrics_settings_formatting_behaviors),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("format_bold"),
                                title = context.getString(R.string.lyrics_settings_bold_text),
                                description = context.getString(R.string.lyrics_settings_bold_text_desc),
                                toggleState = lyricBold,
                                onToggleChange = { appSettings.setLyricBold(it) }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("content_cut"),
                                title = context.getString(R.string.lyrics_settings_trim_lyrics),
                                description = context.getString(R.string.lyrics_settings_trim_lyrics_desc),
                                toggleState = trimLyrics,
                                onToggleChange = { appSettings.setTrimLyrics(it) }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("motion_photos_off"),
                                title = context.getString(R.string.lyrics_settings_disable_animations),
                                description = context.getString(R.string.lyrics_settings_disable_animations_desc),
                                toggleState = lyricNoAnimation,
                                onToggleChange = { appSettings.setLyricNoAnimation(it) }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("translate"),
                                title = context.getString(R.string.lyrics_settings_word_by_word_translation),
                                description = context.getString(R.string.lyrics_settings_word_by_word_translation_desc),
                                toggleState = translationAutoWord,
                                onToggleChange = { appSettings.setTranslationAutoWord(it) }
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Lyrics File & Data Management
            item {
                val lrcRenameBehavior by appSettings.lrcRenameBehavior.collectAsState()
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = context.getString(R.string.lyrics_file_data_management),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("drive_file_rename_outline"),
                                title = context.getString(R.string.lyrics_lrc_rename_behavior),
                                description = when (lrcRenameBehavior) {
                                    "always" -> context.getString(R.string.lyrics_rename_always)
                                    "never" -> context.getString(R.string.lyrics_rename_never)
                                    else -> context.getString(R.string.lyrics_rename_ask)
                                },
                                onClick = {
                                    showLrcRenameBottomSheet = true
                                }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("upload"),
                                title = context.getString(R.string.lyrics_export_preferences),
                                description = context.getString(R.string.lyrics_export_preferences_desc),
                                onClick = {
                                    exportCsvLauncher.launch("rhythm_lyrics_preferences.csv")
                                }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("download"),
                                title = context.getString(R.string.lyrics_import_preferences),
                                description = context.getString(R.string.lyrics_import_preferences_desc),
                                onClick = {
                                    importCsvLauncher.launch("*/*")
                                }
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Notification Lyrics
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.bluetooth_lyrics_enabled),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("lyrics"),
                                title = stringResource(R.string.bluetooth_lyrics_enabled),
                                description = stringResource(R.string.bluetooth_lyrics_desc),
                                toggleState = bluetoothLyricsEnabled,
                                onToggleChange = {
                                    appSettings.setBluetoothLyricsEnabled(it)
                                    if (it && !broadcastStatusEnabled) {
                                        appSettings.setBroadcastStatusEnabled(true)
                                    }
                                }
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Info Card
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
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
                                text = stringResource(R.string.settings_about_lyrics_sources),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.lyricssourcesettingsscreen_embedded_lyrics_are_stored) +
                                    stringResource(R.string.lyrics_settings_info_bullets),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (showPriorityBottomSheet) {
        LyricsApiPriorityBottomSheet(
            onDismiss = { showPriorityBottomSheet = false },
            appSettings = appSettings
        )
    }

    if (showLrcRenameBottomSheet) {
        LrcRenameBehaviorBottomSheet(
            onDismiss = { showLrcRenameBottomSheet = false },
            appSettings = appSettings
        )
    }
}

