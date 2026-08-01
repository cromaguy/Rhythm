@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings
import chromahub.rhythm.app.features.local.data.repository.MusicRepository



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


data class ArtworkCacheStats(
    val sizeBytes: Long,
    val fileCount: Int
)

const val ARTWORK_CACHE_TRIM_MAX_BYTES = 256L * 1024 * 1024
const val ARTWORK_CACHE_TRIM_MAX_FILES = 1200

fun collectArtworkCacheFiles(cacheDir: File): MutableList<File> {
    val artworkCacheDir = File(cacheDir, "embedded_artwork")
    val currentArtworkFiles = artworkCacheDir
        .listFiles { file -> file.isFile }
        ?.toMutableList()
        ?: mutableListOf()

    val legacyArtworkFiles = cacheDir
        .listFiles { file ->
            file.isFile &&
                (file.name.startsWith("embedded_art_") || file.name.startsWith("embedded_art_lossless_"))
        }
        ?.toList()
        .orEmpty()

    return mutableListOf<File>().apply {
        addAll(currentArtworkFiles)
        addAll(legacyArtworkFiles)
    }
}



fun readArtworkCacheStats(cacheDir: File): ArtworkCacheStats {
    val files = collectArtworkCacheFiles(cacheDir)
    return ArtworkCacheStats(
        sizeBytes = files.sumOf { it.length() },
        fileCount = files.size
    )
}



fun trimArtworkCacheNow(cacheDir: File): ArtworkCacheStats {
    val files = collectArtworkCacheFiles(cacheDir)
    if (files.isEmpty()) {
        return ArtworkCacheStats(sizeBytes = 0L, fileCount = 0)
    }

    var totalSize = files.sumOf { it.length() }
    var fileCount = files.size

    if (totalSize <= ARTWORK_CACHE_TRIM_MAX_BYTES && fileCount <= ARTWORK_CACHE_TRIM_MAX_FILES) {
        return ArtworkCacheStats(sizeBytes = totalSize, fileCount = fileCount)
    }

    files.sortBy { it.lastModified() }

    for (file in files) {
        if (totalSize <= ARTWORK_CACHE_TRIM_MAX_BYTES && fileCount <= ARTWORK_CACHE_TRIM_MAX_FILES) {
            break
        }

        val fileSize = file.length()
        if (file.delete()) {
            totalSize -= fileSize
            fileCount--
        }
    }

    return ArtworkCacheStats(
        sizeBytes = totalSize.coerceAtLeast(0L),
        fileCount = fileCount.coerceAtLeast(0)
    )
}


@Composable
fun CacheManagementSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val musicViewModel: MusicViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Collect states
    val maxCacheSize by appSettings.maxCacheSize.collectAsState()


    // Local states
    var currentCacheSize by remember { mutableStateOf(0L) }
    var isCalculatingSize by remember { mutableStateOf(false) }
    var isClearingCache by remember { mutableStateOf(false) }
    var showCacheSizeDialog by remember { mutableStateOf(false) }
    var showClearCacheSuccess by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartDialogMessage by remember { mutableStateOf("") }
    var cacheDetails by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var isRebuildingRoom by remember { mutableStateOf(false) }
    var roomSongCount by remember { mutableStateOf(-1) }

    val refreshCacheStats: suspend () -> Unit = {
        isCalculatingSize = true
        try {
            val (totalCacheSize, detailedCacheStats) = kotlinx.coroutines.withContext(
                kotlinx.coroutines.Dispatchers.IO
            ) {
                Pair(
                    chromahub.rhythm.app.util.CacheManager.getCacheSize(context),
                    chromahub.rhythm.app.util.CacheManager.getDetailedCacheSize(context)
                )
            }
            currentCacheSize = totalCacheSize
            cacheDetails = detailedCacheStats
        } catch (e: Exception) {
            Log.e("CacheManagement", "Error calculating cache size", e)
        } finally {
            isCalculatingSize = false
        }
    }

    // Calculate cache size when the screen opens
    LaunchedEffect(Unit) {
        refreshCacheStats()
    }

    // Calculate storage backend stats
    LaunchedEffect(Unit) {
        try {
            val repo = musicViewModel.getMusicRepository()
            roomSongCount = try {
                repo.getRoomSongCount()
            } catch (_: Exception) { -1 }
        } catch (e: Exception) {
            Log.e("CacheManagement", "Error calculating storage stats", e)
        }
    }

    // Show cache size dialog
    if (showCacheSizeDialog) {
        CacheSizeDialog(
            currentSize = maxCacheSize,
            onDismiss = { showCacheSizeDialog = false },
            onSave = { size ->
                appSettings.setMaxCacheSize(size)
                showCacheSizeDialog = false
            }
        )
    }

    if (showRestartDialog) {
        AppRestartDialog(
            onDismiss = { showRestartDialog = false },
            onRestart = {
                showRestartDialog = false
                chromahub.rhythm.app.util.AppRestarter.restartApp(context)
            },
            onContinue = {
                showRestartDialog = false
            },
            message = restartDialogMessage
        )
    }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_cache),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (isCalculatingSize) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        // Hero Stat
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chromahub.rhythm.app.util.CacheManager.formatBytes(currentCacheSize),
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Text(
                                text = context.getString(R.string.cache_total_size) + " / ${String.format(Locale.getDefault(), "%.1f", maxCacheSize / (1024f * 1024f))} MB",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Segmented Timeline Bar
                        val totalLimit = maxCacheSize.toFloat().coerceAtLeast(1f)
                        val remainingSpace = (totalLimit - currentCacheSize.toFloat()).coerceAtLeast(0f)
                        val cacheEntries = cacheDetails.entries.toList()
                        
                        val segmentColors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.error
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (cacheEntries.isNotEmpty()) {
                                cacheEntries.forEachIndexed { index, entry ->
                                    val weight = (entry.value.toFloat() / totalLimit).coerceAtLeast(0.02f)
                                    Surface(
                                        shape = when (index) {
                                            0 -> RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                                            else -> RoundedCornerShape(4.dp)
                                        },
                                        color = segmentColors[index % segmentColors.size],
                                        modifier = Modifier
                                            .weight(weight)
                                            .fillMaxHeight()
                                    ) {}
                                }
                                
                                // Free space segment
                                if (remainingSpace > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .weight((remainingSpace / totalLimit).coerceAtLeast(0.05f))
                                            .fillMaxHeight()
                                    ) {}
                                }
                            } else {
                                // Fallback empty state
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxSize()
                                ) {}
                            }
                        }

                        // Detailed Breakdown Settings Group
                        if (cacheEntries.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = context.getString(R.string.cache_current_status),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                val breakdownItems = cacheEntries.mapIndexed { index, entry ->
                                    val progress = (entry.value.toFloat() / maxCacheSize.toFloat()).coerceIn(0f, 1f)
                                    val accentColor = segmentColors[index % segmentColors.size]

                                    Material3SettingsItem(
                                        leadingContent = {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("folder", filled = true),
                                                contentDescription = null,
                                                tint = accentColor
                                            )
                                        },
                                        title = {
                                            Text(
                                                text = entry.key,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        description = {
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp, bottom = 4.dp)
                                                    .height(6.dp)
                                                    .clip(CircleShape),
                                                color = accentColor,
                                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                            )
                                        },
                                        trailingContent = {
                                            Text(
                                                text = chromahub.rhythm.app.util.CacheManager.formatBytes(entry.value),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    )
                                }

                                Material3SettingsGroup(
                                    items = breakdownItems,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            }
                        }
                    }
                }
            }

            // Cache Settings
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = context.getString(R.string.settings_cache_settings),
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
                                icon = MaterialSymbolIcon("data_usage", filled = true),
                                title = context.getString(R.string.cache_max_size),
                                description = "${String.format("%.1f", maxCacheSize / (1024f * 1024f))} MB",
                                onClick = { showCacheSizeDialog = true }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("auto_delete", filled = true),
                                title = context.getString(R.string.cache_auto_trim),
                                description = context.getString(R.string.cache_auto_trim_desc)
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Cache Actions
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = context.getString(R.string.settings_cache_actions),
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
                                icon = RhythmIcons.MusicNote,
                                title = context.getString(R.string.settings_clear_lyrics_cache),
                                description = context.getString(R.string.settings_clear_lyrics_cache_desc),
                                onClick = {
                                    scope.launch {
                                        try {
                                            isClearingCache = true
                                            musicViewModel.clearLyricsCacheAndRefetch()
                                            refreshCacheStats()
                                            Toast.makeText(context, context.getString(R.string.settings_lyrics_cache_cleared), Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("CacheManagement", "Error clearing lyrics cache", e)
                                            Toast.makeText(context, context.getString(R.string.settings_lyrics_cache_clear_failed), Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isClearingCache = false
                                        }
                                    }
                                }
                            )
                        ),
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("delete_sweep", filled = true),
                            title = { Text(context.getString(R.string.settings_clear_all_cache)) },
                            description = { Text(context.getString(R.string.settings_clear_all_cache_desc)) },
                            trailingContent = {
                                if (isClearingCache) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                                        contentDescription = context.getString(R.string.cd_navigate),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                scope.launch {
                                    try {
                                        isClearingCache = true
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            trimArtworkCacheNow(context.cacheDir)
                                        }
                                        chromahub.rhythm.app.util.CacheManager.clearAllCache(context, null)
                                        musicViewModel.getMusicRepository().clearInMemoryCaches()
                                        musicViewModel.getMusicRepository().clearSongCacheData()
                                        refreshCacheStats()
                                        showClearCacheSuccess = true
                                        restartDialogMessage = context.getString(R.string.settings_cache_restart_required)
                                        showRestartDialog = true
                                        Toast.makeText(context, context.getString(R.string.settings_all_cache_cleared), Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Log.e("CacheManagement", "Error clearing cache", e)
                                        Toast.makeText(context, context.getString(R.string.settings_cache_clear_failed), Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isClearingCache = false
                                    }
                                }
                            }
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Library Storage section
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = context.getString(R.string.settings_cache_backend),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Material3SettingsGroup(
                    items = listOf(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("table_chart", filled = true),
                            title = { Text(context.getString(R.string.settings_storage_room_stats)) },
                            description = {
                                Text(
                                    if (roomSongCount >= 0) {
                                        context.getString(R.string.settings_storage_song_count, roomSongCount)
                                    } else {
                                        context.getString(R.string.settings_storage_not_available)
                                    }
                                )
                            }
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = haptics,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("sync", filled = true),
                                title = context.getString(R.string.settings_storage_rebuild_room),
                                description = context.getString(R.string.settings_storage_rebuild_room_desc),
                                onClick = {
                                    if (!isRebuildingRoom) {
                                        scope.launch {
                                            isRebuildingRoom = true
                                            try {
                                                musicViewModel.getMusicRepository().clearSongCacheData()
                                                musicViewModel.refreshLibrary()
                                                roomSongCount = musicViewModel.getMusicRepository().getRoomSongCount()
                                                Toast.makeText(context, context.getString(R.string.settings_storage_rebuild_success), Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Log.e("CacheManagement", "Error rebuilding Room DB", e)
                                                Toast.makeText(context, context.getString(R.string.settings_storage_rebuild_failed), Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isRebuildingRoom = false
                                                restartDialogMessage = context.getString(R.string.settings_storage_rebuild_room_desc)
                                                showRestartDialog = true
                                            }
                                        }
                                    }
                                }
                            )
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )

                if (isRebuildingRoom) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            // Information section
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Info,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.cache_about),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        listOf(
                            context.getString(R.string.settings_cache_info_1),
                            context.getString(R.string.settings_cache_info_2),
                            context.getString(R.string.settings_cache_info_3),
                            context.getString(R.string.settings_cache_info_4)
                        ).forEach { info ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("fiber_manual_record", filled = true),
                                    contentDescription = null,
                                    modifier = Modifier.size(8.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = info,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}