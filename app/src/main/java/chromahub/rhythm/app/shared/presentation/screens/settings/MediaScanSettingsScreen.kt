/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

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
import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.platform.LocalDensity
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
import chromahub.rhythm.app.shared.data.model.MediaScanMode
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
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveFilterChip
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
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


private data class FormatCategory(
    val titleRes: Int,
    val formats: List<String>
)

private val FORMAT_CATEGORIES = listOf(
    FormatCategory(
        R.string.settings_formats_group_common,
        listOf("mp3", "m4a", "aac", "alac", "flac", "ogg", "opus", "oga", "opa", "wav", "aiff", "aif", "wma")
    ),
    FormatCategory(
        R.string.settings_formats_group_lossless,
        listOf("ape", "wv", "tta", "tak", "dsf", "dff", "dsd")
    ),
    FormatCategory(
        R.string.settings_formats_group_surround,
        listOf("ac3", "ac4", "eac", "eac3", "dts", "dtshd", "dtsx", "truehd")
    ),
    FormatCategory(
        R.string.settings_formats_group_containers,
        listOf("mka", "m4b", "adts", "mp4", "mkv")
    ),
    FormatCategory(
        R.string.settings_formats_group_legacy,
        listOf("mid", "midi", "mhm", "mhm1")
    )
)

private val ALL_KNOWN_FORMATS: List<String> = FORMAT_CATEGORIES.flatMap { it.formats }

// ✅ REDESIGNED Media Scan Screen with improved UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScanSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val appSettings = AppSettings.getInstance(context)
    val musicViewModel: MusicViewModel = viewModel()

    // Get all songs and filtered items
    val allSongs by musicViewModel.songs.collectAsState()
    val filteredSongs by musicViewModel.filteredSongs.collectAsState()
    val blacklistedSongs by appSettings.blacklistedSongs.collectAsState()
    val blacklistedFolders by appSettings.blacklistedFolders.collectAsState()
    val whitelistedSongs by appSettings.whitelistedSongs.collectAsState()
    val whitelistedFolders by appSettings.whitelistedFolders.collectAsState()

    // Get current media scan mode from settings
    val mediaScanMode by appSettings.mediaScanMode.collectAsState()
    val includeHiddenWhitelistedMedia by appSettings.includeHiddenWhitelistedMedia.collectAsState()
    val allowedFormats by appSettings.allowedFormats.collectAsState()
    val minimumDuration by appSettings.minimumDuration.collectAsState()

    val enabledKnownCount = allowedFormats.count { it in ALL_KNOWN_FORMATS }

    fun updateAllowedFormats(newFormats: Set<String>) {
        if (newFormats.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.settings_formats_at_least_one),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        appSettings.setAllowedFormats(newFormats)
    }

    // Mode state
    var currentMode by remember {
        mutableStateOf(mediaScanMode)
    }

    // Bottom sheet states
    var showSongsBottomSheet by remember { mutableStateOf(false) }
    var showFoldersBottomSheet by remember { mutableStateOf(false) }
    var showFormatsBottomSheet by remember { mutableStateOf(false) }
    var showDurationBottomSheet by remember { mutableStateOf(false) }

    // File picker launcher for folder selection
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val docId = DocumentsContract.getTreeDocumentId(uri)
                    val split = docId.split(":")

                    if (split.size >= 2) {
                        val storageType = split[0] // e.g., "primary", "home", or specific SD card ID
                        val relativePath = split[1] // e.g., "Music/MyFolder"

                        // Build the full path based on storage type
                        val fullPath = when (storageType) {
                            "primary" -> "/storage/emulated/0/$relativePath"
                            "home" -> "/storage/emulated/0/$relativePath"
                            else -> {
                                // For SD cards or other storage, try to construct path
                                // This is a best-effort approach
                                if (storageType.contains("-")) {
                                    // SD card UUID format
                                    "/storage/$storageType/$relativePath"
                                } else {
                                    // Fallback to emulated storage
                                    "/storage/emulated/0/$relativePath"
                                }
                            }
                        }

                        if (currentMode == MediaScanMode.BLACKLIST) {
                            appSettings.addFolderToBlacklist(fullPath)
                        } else {
                            appSettings.addFolderToWhitelist(fullPath)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MediaScanSettingsScreen", "Error parsing folder path", e)
                }
            }
        }
    }

    // Computed values OUTSIDE LazyColumn
    val filteredSongDetails = remember(allSongs, blacklistedSongs, whitelistedSongs, currentMode) {
        when (currentMode) {
            MediaScanMode.BLACKLIST ->
                allSongs.filter { song -> blacklistedSongs.contains(song.id) }
            MediaScanMode.WHITELIST ->
                allSongs.filter { song -> whitelistedSongs.contains(song.id) }
        }
    }

    val filteredFoldersList = remember(blacklistedFolders, whitelistedFolders, currentMode) {
        when (currentMode) {
            MediaScanMode.BLACKLIST -> blacklistedFolders
            MediaScanMode.WHITELIST -> whitelistedFolders
        }
    }

    val detectedMusicFolders = remember(allSongs) {
        allSongs.mapNotNull { song ->
            val path = song.path ?: if (song.uri.scheme == "file") song.uri.path else null
            path?.let { p ->
                try {
                    File(p).parent
                } catch (e: Exception) {
                    null
                }
            }
        }.distinct().sorted()
    }

    val suggestedFolders = remember(detectedMusicFolders, filteredFoldersList) {
        val currentSet = filteredFoldersList.toSet()
        detectedMusicFolders.filter { folder ->
            !currentSet.contains(folder)
        }
    }

    val settingGroups = listOf(
        SettingGroup(
            title = context.getString(R.string.settings_mode_selection),
            items = listOf(
                SettingItem(
                    RhythmIcons.Block,
                    context.getString(R.string.settings_blacklist_mode),
                    context.getString(R.string.settings_blacklist_mode_desc),
                    toggleState = currentMode == MediaScanMode.BLACKLIST,
                    onToggleChange = { enabled ->
                        if (enabled) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            currentMode = MediaScanMode.BLACKLIST
                            appSettings.setMediaScanMode(MediaScanMode.BLACKLIST)
                        }
                    }
                ),
                SettingItem(
                    RhythmIcons.CheckCircle,
                    context.getString(R.string.settings_whitelist_mode),
                    context.getString(R.string.settings_whitelist_mode_desc),
                    toggleState = currentMode == MediaScanMode.WHITELIST,
                    onToggleChange = { enabled ->
                        if (enabled) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            currentMode = MediaScanMode.WHITELIST
                            appSettings.setMediaScanMode(MediaScanMode.WHITELIST)
                            if (whitelistedFolders.isEmpty() && whitelistedSongs.isEmpty()) {
                                try {
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                    folderPickerLauncher.launch(intent)
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, context.getString(R.string.error_no_document_app), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )
            )
        ),
        SettingGroup(
            title = context.getString(R.string.settings_song_management),
            items = listOf(
                SettingItem(
                    RhythmIcons.Queue,
                    context.getString(R.string.settings_manage_songs),
                    context.getString(R.string.settings_manage_songs_desc, filteredSongDetails.size, if (currentMode == MediaScanMode.BLACKLIST) context.getString(R.string.settings_blocked) else context.getString(R.string.settings_whitelisted)),
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        showSongsBottomSheet = true
                    }
                ),
                SettingItem(
                    MaterialSymbolIcon("clear"),
                    context.getString(R.string.settings_clear_all_songs),
                    context.getString(R.string.settings_clear_all_songs_desc, if (currentMode == MediaScanMode.BLACKLIST) context.getString(R.string.settings_blocked) else context.getString(R.string.settings_whitelisted)),
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        if (currentMode == MediaScanMode.BLACKLIST) {
                            appSettings.clearBlacklist()
                        } else {
                            appSettings.clearWhitelist()
                        }
                    }
                )
            )
        ),
        SettingGroup(
            title = context.getString(R.string.settings_folder_management),
            items = listOf(
                SettingItem(
                    RhythmIcons.Folder,
                    context.getString(R.string.settings_manage_folders),
                    context.getString(R.string.settings_manage_folders_desc, filteredFoldersList.size, if (currentMode == MediaScanMode.BLACKLIST) context.getString(R.string.settings_blocked) else context.getString(R.string.settings_whitelisted)),
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        showFoldersBottomSheet = true
                    }
                ),
                SettingItem(
                    RhythmIcons.Add,
                    context.getString(R.string.settings_add_folder),
                    context.getString(R.string.settings_add_folder_desc, if (currentMode == MediaScanMode.BLACKLIST) context.getString(R.string.settings_block) else context.getString(R.string.settings_whitelist)),
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        try {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            folderPickerLauncher.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, context.getString(R.string.error_no_document_app), Toast.LENGTH_LONG).show()
                        }
                    }
                ),
                SettingItem(
                    MaterialSymbolIcon("clear"),
                    context.getString(R.string.settings_clear_all_folders),
                    context.getString(R.string.settings_clear_all_folders_desc, if (currentMode == MediaScanMode.BLACKLIST) context.getString(R.string.settings_blocked) else context.getString(R.string.settings_whitelisted)),
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        if (currentMode == MediaScanMode.BLACKLIST) {
                            blacklistedFolders.forEach { folder ->
                                appSettings.removeFolderFromBlacklist(folder)
                            }
                        } else {
                            whitelistedFolders.forEach { folder ->
                                appSettings.removeFolderFromWhitelist(folder)
                            }
                        }
                    }
                )
            )
        )
    )

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_media_scan),
        showBackButton = true,
        onBackClick = onBackClick
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main overview content
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // Mode Selection with ExpressiveButtonGroup
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = context.getString(R.string.settings_mode_selection),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            ExpressiveButtonGroup(
                                items = listOf(
                                    context.getString(R.string.settings_blacklist_mode),
                                    context.getString(R.string.settings_whitelist_mode)
                                ),
                                selectedIndex = if (currentMode == MediaScanMode.BLACKLIST) 0 else 1,
                                onItemClick = { index ->
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    currentMode = if (index == 0) {
                                        appSettings.setMediaScanMode(MediaScanMode.BLACKLIST)
                                        MediaScanMode.BLACKLIST
                                    } else {
                                        appSettings.setMediaScanMode(MediaScanMode.WHITELIST)
                                        if (whitelistedFolders.isEmpty() && whitelistedSongs.isEmpty()) {
                                            try {
                                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                                folderPickerLauncher.launch(intent)
                                            } catch (e: ActivityNotFoundException) {
                                                Toast.makeText(context, context.getString(R.string.error_no_document_app), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        MediaScanMode.WHITELIST
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = if (currentMode == MediaScanMode.BLACKLIST)
                                    context.getString(R.string.settings_blacklist_mode_desc)
                                else
                                    context.getString(R.string.settings_whitelist_mode_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                val scanBehaviorItems = listOf(
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptic,
                        item = SettingItem(
                            icon = RhythmIcons.Visibility,
                            title = context.getString(R.string.settings_include_hidden_whitelisted_media),
                            description = context.getString(R.string.settings_include_hidden_whitelisted_media_desc),
                            toggleState = includeHiddenWhitelistedMedia,
                            onToggleChange = { appSettings.setIncludeHiddenWhitelistedMedia(it) }
                        )
                    ),
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptic,
                        item = SettingItem(
                            icon = RhythmIcons.Player.Timer,
                            title = context.getString(R.string.settings_min_duration),
                            description = formatMinimumDuration(context, minimumDuration),
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                showDurationBottomSheet = true
                            }
                        )
                    )
                )

                Material3SettingsGroup(
                    title = context.getString(R.string.settings_scan_behavior),
                    items = scanBehaviorItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                val formatsItems = listOf(
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptic,
                        item = SettingItem(
                            icon = RhythmIcons.MusicNote,
                            title = context.getString(R.string.settings_allowed_formats),
                            description = context.getString(
                                R.string.settings_allowed_formats_desc,
                                enabledKnownCount,
                                ALL_KNOWN_FORMATS.size
                            ),
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                showFormatsBottomSheet = true
                            }
                        )
                    )
                )

                Material3SettingsGroup(
                    title = context.getString(R.string.settings_audio_formats),
                    items = formatsItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            settingGroups.drop(1).forEach { group ->
                item {
                    val materialItems = group.items.map { item ->
                        toMaterial3SettingsItem(
                            context = context,
                            item = item,
                            hapticFeedback = haptic
                        )
                    }

                    Material3SettingsGroup(
                        title = group.title,
                        items = materialItems,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                }
            }

            // Quick Tips Card
            item {
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

                        MediaScanTipItem(
                            icon = RhythmIcons.Block,
                            text = context.getString(R.string.settings_quick_tip_blacklist)
                        )
                        MediaScanTipItem(
                            icon = RhythmIcons.CheckCircle,
                            text = context.getString(R.string.settings_quick_tip_whitelist)
                        )
                        MediaScanTipItem(
                            icon = RhythmIcons.Folder,
                            text = context.getString(R.string.settings_quick_tip_folder)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Songs bottom sheet
    if (showSongsBottomSheet) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { showSongsBottomSheet = false },
            sheetState = sheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.primary
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            StandardBottomSheetHeader(
                title = context.getString(R.string.settings_manage_songs),
                subtitle = if (currentMode == MediaScanMode.BLACKLIST) context.getString(R.string.settings_blocked_songs) else context.getString(R.string.settings_whitelisted_songs),
                visible = true
            )

            val songsScrollState = rememberScrollState()

            AdaptiveSheetScrollContainer(
                scrollState = songsScrollState,
                modifier = Modifier.fillMaxWidth()
            ) { endPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(songsScrollState)
                        .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp)
                ) {
                    // Stats cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (currentMode == MediaScanMode.BLACKLIST) RhythmIcons.Block else RhythmIcons.CheckCircle,
                                    contentDescription = null,
                                    tint = if (currentMode == MediaScanMode.BLACKLIST)
                                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${filteredSongDetails.size}",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (currentMode == MediaScanMode.BLACKLIST) context.getString(R.string.settings_blocked) else context.getString(R.string.settings_whitelisted),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${allSongs.size}",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = context.getString(R.string.settings_total_songs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Songs list
                    filteredSongDetails.forEach { song ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (currentMode == MediaScanMode.BLACKLIST)
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.MusicNote,
                                        contentDescription = null,
                                        tint = if (currentMode == MediaScanMode.BLACKLIST)
                                            MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                FilledIconButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        if (currentMode == MediaScanMode.BLACKLIST) {
                                            appSettings.removeFromBlacklist(song.id)
                                        } else {
                                            appSettings.removeFromWhitelist(song.id)
                                        }
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (currentMode == MediaScanMode.BLACKLIST)
                                            MaterialTheme.colorScheme.errorContainer
                                        else MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Close,
                                        contentDescription = stringResource(R.string.content_desc_remove),
                                        tint = if (currentMode == MediaScanMode.BLACKLIST)
                                            MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Clear button at bottom
                    if (filteredSongDetails.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                if (currentMode == MediaScanMode.BLACKLIST) {
                                    appSettings.clearBlacklist()
                                } else {
                                    appSettings.clearWhitelist()
                                }
                                showSongsBottomSheet = false
                            },
                            border = BorderStroke(2.dp, if (currentMode == MediaScanMode.BLACKLIST)
                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("delete_sweep", filled = true),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(context.getString(R.string.settings_clear_all_button, if (currentMode == MediaScanMode.BLACKLIST) context.getString(R.string.settings_blocked) else context.getString(R.string.settings_whitelisted)))
                        }
                    }
                }
            }
        }
    }

    // Folders bottom sheet
    if (showFoldersBottomSheet) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { showFoldersBottomSheet = false },
            sheetState = sheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.primary
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            StandardBottomSheetHeader(
                title = context.getString(R.string.settings_manage_folders),
                subtitle = if (currentMode == MediaScanMode.BLACKLIST) context.getString(R.string.settings_blocked_folders) else context.getString(R.string.settings_whitelisted_folders),
                visible = true
            )

            val foldersScrollState = rememberScrollState()

            AdaptiveSheetScrollContainer(
                scrollState = foldersScrollState,
                modifier = Modifier.fillMaxWidth()
            ) { endPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(foldersScrollState)
                        .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp)
                ) {
                    // Stats card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (currentMode == MediaScanMode.BLACKLIST) MaterialSymbolIcon("folder_off") else RhythmIcons.Folder,
                                contentDescription = null,
                                tint = if (currentMode == MediaScanMode.BLACKLIST)
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${filteredFoldersList.size}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (currentMode == MediaScanMode.BLACKLIST) context.getString(R.string.settings_blocked_folders) else context.getString(R.string.settings_whitelisted_folders),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Folders list
                    if (filteredFoldersList.isNotEmpty()) {
                        Text(
                            text = if (currentMode == MediaScanMode.BLACKLIST) "Currently Blocked Folders" else "Currently Whitelisted Folders",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        filteredFoldersList.forEach { folder ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (currentMode == MediaScanMode.BLACKLIST)
                                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Folder,
                                            contentDescription = null,
                                            tint = if (currentMode == MediaScanMode.BLACKLIST)
                                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = File(folder).name.ifEmpty { context.getString(R.string.settings_root) },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = folder,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    FilledIconButton(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            if (currentMode == MediaScanMode.BLACKLIST) {
                                                appSettings.removeFolderFromBlacklist(folder)
                                            } else {
                                                appSettings.removeFolderFromWhitelist(folder)
                                            }
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = if (currentMode == MediaScanMode.BLACKLIST)
                                                MaterialTheme.colorScheme.errorContainer
                                            else MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Close,
                                            contentDescription = context.getString(R.string.cd_remove),
                                            tint = if (currentMode == MediaScanMode.BLACKLIST)
                                                MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (suggestedFolders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = context.getString(R.string.media_suggested_folders),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        suggestedFolders.forEach { folder ->
                            Card(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    if (currentMode == MediaScanMode.BLACKLIST) {
                                        appSettings.addFolderToBlacklist(folder)
                                    } else {
                                        appSettings.addFolderToWhitelist(folder)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbolIcon("create_new_folder"),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = File(folder).name.ifEmpty { folder },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = folder,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = RhythmIcons.Add,
                                        contentDescription = context.getString(R.string.settings_add_folder),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Action buttons at bottom
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                try {
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                    folderPickerLauncher.launch(intent)
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, context.getString(R.string.error_no_document_app), Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(context.getString(R.string.settings_add_folder_button))
                        }

                        if (filteredFoldersList.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                    if (currentMode == MediaScanMode.BLACKLIST) {
                                        blacklistedFolders.forEach { folder ->
                                            appSettings.removeFolderFromBlacklist(folder)
                                        }
                                    } else {
                                        whitelistedFolders.forEach { folder ->
                                            appSettings.removeFolderFromWhitelist(folder)
                                        }
                                    }
                                    showFoldersBottomSheet = false
                                },
                                border = BorderStroke(2.dp, if (currentMode == MediaScanMode.BLACKLIST)
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("delete_sweep", filled = true),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(context.getString(R.string.settings_clear_all_button_short))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormatsBottomSheet) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

        LaunchedEffect(Unit) {
            sheetState.expand()
        }

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { showFormatsBottomSheet = false },
            sheetState = sheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.primary
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            StandardBottomSheetHeader(
                title = context.getString(R.string.settings_allowed_formats),
                subtitle = context.getString(
                    R.string.settings_allowed_formats_desc,
                    enabledKnownCount,
                    ALL_KNOWN_FORMATS.size
                ),
                visible = true
            )

            val formatsScrollState = rememberScrollState()

            AdaptiveSheetScrollContainer(
                scrollState = formatsScrollState,
                modifier = Modifier.fillMaxWidth()
            ) { endPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(formatsScrollState)
                        .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp)
                ) {
                    FORMAT_CATEGORIES.forEach { category ->
                        Text(
                            text = context.getString(category.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp, top = 12.dp)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            category.formats.forEach { format ->
                                val selected = allowedFormats.contains(format)
                                ExpressiveFilterChip(
                                    selected = selected,
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        updateAllowedFormats(
                                            if (selected) allowedFormats - format else allowedFormats + format
                                        )
                                    },
                                    leadingIcon = if (selected) ({
                                        Icon(
                                            imageVector = RhythmIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }) else null,
                                    label = {
                                        Text(
                                            text = format.uppercase(),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = context.getString(R.string.settings_quick_tips),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = context.getString(R.string.settings_allowed_formats_open_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = context.getString(R.string.settings_formats_video_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDurationBottomSheet) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )

        var currentSeconds by remember(minimumDuration) {
            mutableFloatStateOf((minimumDuration / 1000L).toFloat().coerceIn(0f, 300f))
        }

        LaunchedEffect(Unit) {
            sheetState.expand()
        }

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { showDurationBottomSheet = false },
            sheetState = sheetState,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.primary
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            StandardBottomSheetHeader(
                title = context.getString(R.string.settings_min_duration),
                subtitle = if (currentSeconds > 0f) "Filtering < ${formatMinimumDuration(context, (currentSeconds * 1000L).toLong())}" else context.getString(R.string.settings_min_duration_none),
                visible = true
            )

            val durationScrollState = rememberScrollState()

            AdaptiveSheetScrollContainer(
                scrollState = durationScrollState,
                modifier = Modifier.fillMaxWidth()
            ) { endPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(durationScrollState)
                        .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 12.dp)
                ) {
                        // ── Grouped Cards: Slider (Top) + Preset Chips (Bottom) ───
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Top Card: Slider
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Slider(
                                        value = currentSeconds,
                                        onValueChange = { value ->
                                            val stepped = kotlin.math.round(value / 5f) * 5f
                                            if (stepped != currentSeconds) {
                                                currentSeconds = stepped
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            }
                                        },
                                        valueRange = 0f..300f,
                                        steps = 59, // 0 to 300 in steps of 5
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "0s (All)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "2m 30s",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "5m",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Bottom Card: Preset buttons using RhythmGroupedButton
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val row1 = listOf(
                                        0L to "0s",
                                        5_000L to "5s",
                                        10_000L to "10s",
                                        15_000L to "15s"
                                    )
                                    val row2 = listOf(
                                        30_000L to "30s",
                                        60_000L to "1m",
                                        120_000L to "2m",
                                        300_000L to "5m"
                                    )

                                    RhythmGroupedButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        size = RhythmButtonSize.Medium
                                    ) {
                                        row1.forEachIndexed { index, (durationMs, label) ->
                                            val isSelected = (currentSeconds * 1000L).toLong() == durationMs
                                            RhythmButtonWeighted(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                    currentSeconds = (durationMs / 1000L).toFloat()
                                                },
                                                weight = 1f,
                                                isFirst = index == 0,
                                                isLast = index == row1.lastIndex,
                                                selected = isSelected,
                                                type = if (isSelected) RhythmButtonType.Filled else RhythmButtonType.Tonal,
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                text = label
                                            )
                                        }
                                    }

                                    RhythmGroupedButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        size = RhythmButtonSize.Medium
                                    ) {
                                        row2.forEachIndexed { index, (durationMs, label) ->
                                            val isSelected = (currentSeconds * 1000L).toLong() == durationMs
                                            RhythmButtonWeighted(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                    currentSeconds = (durationMs / 1000L).toFloat()
                                                },
                                                weight = 1f,
                                                isFirst = index == 0,
                                                isLast = index == row2.lastIndex,
                                                selected = isSelected,
                                                type = if (isSelected) RhythmButtonType.Filled else RhythmButtonType.Tonal,
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                text = label
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Tip Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = context.getString(R.string.settings_min_duration_tip),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                // Apply + Reset buttons pinned at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp, top = 8.dp)
                ) {
                    RhythmGroupedButton(
                        modifier = Modifier.fillMaxWidth(),
                        size = RhythmButtonSize.Large
                    ) {
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                currentSeconds = 0f
                            },
                            weight = 1f,
                            isFirst = true,
                            icon = MaterialSymbolIcon("restart_alt"),
                            text = context.getString(R.string.bottomsheet_reset)
                        )
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                val newDuration = (currentSeconds * 1000L).toLong()
                                val changed = appSettings.minimumDuration.value != newDuration
                                appSettings.setMinimumDuration(newDuration)
                                if (changed) {
                                    musicViewModel.refreshLibrary(showMediaScanLoader = false)
                                }
                                showDurationBottomSheet = false
                            },
                            weight = 1f,
                            isLast = true,
                            icon = RhythmIcons.Check,
                            text = context.getString(R.string.ui_apply)
                        )
                    }
                }
            }
        }
    }

private fun formatMinimumDuration(context: Context, durationMs: Long): String {
    if (durationMs <= 0L) {
        return context.getString(R.string.settings_min_duration_none)
    }
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val remainingSeconds = totalSeconds % 60L

    return when {
        minutes > 0 && remainingSeconds > 0 -> context.getString(R.string.settings_min_duration_minutes, minutes, remainingSeconds)
        minutes > 0 -> context.getString(R.string.settings_min_duration_minutes_only, minutes)
        else -> context.getString(R.string.settings_min_duration_seconds, remainingSeconds)
    }
}

@Composable
fun MediaScanTipItem(
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