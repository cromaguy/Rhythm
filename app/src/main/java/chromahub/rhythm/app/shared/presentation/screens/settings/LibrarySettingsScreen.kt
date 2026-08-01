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
import chromahub.rhythm.app.shared.data.model.ArtistArtworkSource
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ArtistArtworkSourceBottomSheet
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
import androidx.compose.ui.unit.dp
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


@Composable
fun LibrarySettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val enableRatingSystem by appSettings.enableRatingSystem.collectAsState()
    val libraryCombineDiscs by appSettings.libraryCombineDiscs.collectAsState()
    val showLibraryBottomBarAlways by appSettings.showLibraryBottomBarAlways.collectAsState()
    val preferSongArtwork by appSettings.preferSongArtwork.collectAsState()
    val losslessArtwork by appSettings.losslessArtwork.collectAsState()
    val albumBottomSheetGradientBlur by appSettings.albumBottomSheetGradientBlur.collectAsState()
    val albumHideAbout by appSettings.albumHideAbout.collectAsState()
    val lyricallyApiEnabled by appSettings.lyricallyApiEnabled.collectAsState()
    val autoFetchArtwork by appSettings.autoFetchArtwork.collectAsState()
    val artistArtworkSource by appSettings.artistArtworkSource.collectAsState()

    var showLibraryTabOrderBottomSheet by remember { mutableStateOf(false) }
    var showArtistArtworkSourceBottomSheet by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartRequiresArtworkRescan by remember { mutableStateOf(false) }
    var restartDialogMessage by remember {
        mutableStateOf(context.getString(R.string.settings_song_artwork_restart_required))
    }

    val artistArtworkSourceSubtitle = when (artistArtworkSource) {
        ArtistArtworkSource.PREFER_LOCAL_THEN_API -> stringResource(R.string.settings_artist_artwork_source_prefer_local)
        ArtistArtworkSource.LOCAL_ONLY -> stringResource(R.string.settings_artist_artwork_source_local_only)
        ArtistArtworkSource.API_ONLY -> stringResource(R.string.settings_artist_artwork_source_api_only)
        ArtistArtworkSource.DISABLED -> stringResource(R.string.settings_artist_artwork_source_disabled)
    }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_library_settings),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        val settingGroups = listOf(
            SettingGroup(
                title = context.getString(R.string.settings_library_group_organization),
                items = listOf(
                    SettingItem(
                        MaterialSymbolIcon("star"),
                        context.getString(R.string.settings_song_ratings),
                        context.getString(R.string.settings_song_ratings_desc),
                        toggleState = enableRatingSystem,
                        onToggleChange = { appSettings.setEnableRatingSystem(it) }
                    ),
                    SettingItem(
                        MaterialSymbolIcon("reorder"),
                        context.getString(R.string.settings_library_tab_order),
                        context.getString(R.string.settings_library_tab_order_desc),
                        onClick = { showLibraryTabOrderBottomSheet = true }
                    ),
                    SettingItem(
                        RhythmIcons.Album,
                        context.getString(R.string.settings_library_combine_discs),
                        context.getString(R.string.settings_library_combine_discs_desc),
                        toggleState = libraryCombineDiscs,
                        onToggleChange = { appSettings.setLibraryCombineDiscs(it) }
                    ),
                    SettingItem(
                        MaterialSymbolIcon("view_agenda"),
                        context.getString(R.string.settings_show_library_bottom_bar_always),
                        context.getString(R.string.settings_show_library_bottom_bar_always_desc),
                        toggleState = showLibraryBottomBarAlways,
                        onToggleChange = { appSettings.setShowLibraryBottomBarAlways(it) }
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.settings_library_group_artwork),
                items = listOf(
                    SettingItem(
                        RhythmIcons.Album,
                        context.getString(R.string.settings_ignore_mediastore_covers),
                        context.getString(R.string.settings_ignore_mediastore_covers_desc),
                        toggleState = preferSongArtwork,
                        onToggleChange = {
                            if (it != preferSongArtwork) {
                                appSettings.setPreferSongArtwork(it)
                                restartRequiresArtworkRescan = true
                                restartDialogMessage = context.getString(R.string.settings_song_artwork_restart_required)
                                showRestartDialog = true
                            }
                        }
                    ),
                    SettingItem(
                        RhythmIcons.MusicNote,
                        context.getString(R.string.settings_lossless_artwork),
                        context.getString(R.string.settings_lossless_artwork_desc),
                        toggleState = losslessArtwork,
                        onToggleChange = {
                            if (it != losslessArtwork) {
                                appSettings.setLosslessArtwork(it)
                                restartRequiresArtworkRescan = true
                                restartDialogMessage = context.getString(R.string.settings_song_artwork_restart_required)
                                showRestartDialog = true
                            }
                        }
                    ),
                    SettingItem(
                        MaterialSymbolIcon("lens_blur"),
                        context.getString(R.string.settings_album_bottom_sheet_gradient_blur),
                        context.getString(R.string.settings_album_bottom_sheet_gradient_blur_desc),
                        toggleState = albumBottomSheetGradientBlur,
                        onToggleChange = { appSettings.setAlbumBottomSheetGradientBlur(it) }
                    ),
                    SettingItem(
                        MaterialSymbolIcon("info"),
                        context.getString(R.string.settings_album_hide_about),
                        context.getString(R.string.settings_album_hide_about_desc),
                        toggleState = albumHideAbout,
                        onToggleChange = { appSettings.setAlbumHideAbout(it) }
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("cloud_download"),
                        title = stringResource(R.string.librarysettingsscreen_autofetch_artwork),
                        description = "Automatically search online APIs for missing cover artwork on startup",
                        toggleState = autoFetchArtwork && lyricallyApiEnabled,
                        onToggleChange = { enabled -> appSettings.setAutoFetchArtwork(enabled) },
                        enabled = lyricallyApiEnabled
                    ),
                    SettingItem(
                        icon = MaterialSymbolIcon("portrait"),
                        title = stringResource(R.string.settings_artist_artwork_source),
                        description = artistArtworkSourceSubtitle,
                        onClick = { showArtistArtworkSourceBottomSheet = true }
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
            items(settingGroups, key = { "libsettings_${it.title}" }) { group ->
                Spacer(modifier = Modifier.height(24.dp))

                val materialItems = group.items.map { item ->
                    toMaterial3SettingsItem(context = context, item = item)
                }

                Material3SettingsGroup(
                    title = group.title,
                    items = materialItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    if (showLibraryTabOrderBottomSheet) {
        LibraryTabOrderBottomSheet(
            onDismiss = { showLibraryTabOrderBottomSheet = false },
            appSettings = appSettings,
            haptics = haptics
        )
    }

    if (showArtistArtworkSourceBottomSheet) {
        ArtistArtworkSourceBottomSheet(
            onDismiss = { showArtistArtworkSourceBottomSheet = false },
            appSettings = appSettings
        )
    }

    if (showRestartDialog) {
        AppRestartDialog(
            onDismiss = {
                showRestartDialog = false
                restartRequiresArtworkRescan = false
            },
            onRestart = {
                val shouldRefreshLibrary = restartRequiresArtworkRescan
                showRestartDialog = false
                restartRequiresArtworkRescan = false
                scope.launch {
                    if (shouldRefreshLibrary) {
                        try {
                            chromahub.rhythm.app.util.CacheManager.clearAllCache(context, null)
                            appSettings.requestFullMediaRescanOnNextLaunch(reason = "library_artwork_settings_restart")
                        } catch (e: Exception) {
                            Log.e("CacheManagement", "Error clearing cache before artwork settings restart", e)
                        }
                    }
                    chromahub.rhythm.app.util.AppRestarter.restartApp(context)
                }
            },
            onContinue = {
                showRestartDialog = false
                restartRequiresArtworkRescan = false
            },
            message = restartDialogMessage
        )
    }
}