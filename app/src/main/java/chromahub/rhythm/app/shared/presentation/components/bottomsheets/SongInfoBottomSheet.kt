/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType


import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.ActionProgressLoader
import chromahub.rhythm.app.shared.presentation.components.common.ContentLoadingIndicator
import chromahub.rhythm.app.shared.presentation.components.player.formatDuration
import chromahub.rhythm.app.shared.presentation.components.common.MarqueeText
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.common.RhythmDetailActionButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmDetailActionButtonFullWidth
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.MediaUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import chromahub.rhythm.app.network.NetworkClient
import chromahub.rhythm.app.network.YTMusicSearchRequest
import chromahub.rhythm.app.network.YTMusicContext
import chromahub.rhythm.app.network.YTMusicClient
import chromahub.rhythm.app.network.extractAlbumImageUrl
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import chromahub.rhythm.app.util.windowScreenWidthDp
import chromahub.rhythm.app.util.windowScreenHeightDp
import chromahub.rhythm.app.ui.theme.MusicDimensions

data class ExtendedSongInfo(
    val fileSize: Long = 0,
    val bitrate: String = "Unknown",
    val sampleRate: String = "Unknown",
    val format: String = "Unknown",
    val composer: String = "",
    val discNumber: Int = 0,
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val filePath: String = "",
    val albumArtist: String = "",
    val year: Int = 0,
    val mimeType: String = "",
    val channels: String = "Unknown",
    val hasLyrics: Boolean = false,
    val genre: String = "",
    val isLossless: Boolean = false,
    val isDolby: Boolean = false,
    val isDTS: Boolean = false,
    val isHiRes: Boolean = false,
    val audioCodec: String = "Unknown",
    val formatName: String = "Unknown",
    val qualityType: String = "Unknown",
    val qualityLabel: String = "Unknown",
    val qualityDescription: String = "",
    val bitDepth: Int = 0,
    val qualityCategory: String = "Unknown"
)

private fun resolveSongInfoArtworkUri(context: android.content.Context, song: Song): Uri? {
    val currentArtworkUri = song.artworkUri

    if (currentArtworkUri != null &&
        !isMediaStoreAlbumArtworkUri(currentArtworkUri) &&
        isUsableArtworkUri(currentArtworkUri)
    ) {
        return currentArtworkUri
    }

    val cachedLossless = MediaUtils.getCachedEmbeddedAlbumArtUri(
        cacheDir = context.cacheDir,
        songUri = song.uri,
        lossless = true
    )
    if (cachedLossless != null) {
        return cachedLossless
    }

    val cachedLossy = MediaUtils.getCachedEmbeddedAlbumArtUri(
        cacheDir = context.cacheDir,
        songUri = song.uri,
        lossless = false
    )
    if (cachedLossy != null) {
        return cachedLossy
    }

    val extractedEmbedded = MediaUtils.extractEmbeddedAlbumArt(
        context = context,
        songUri = song.uri,
        cacheDir = context.cacheDir,
        lossless = false
    )
    if (extractedEmbedded != null) {
        return extractedEmbedded
    }

    return currentArtworkUri
}

private fun isMediaStoreAlbumArtworkUri(uri: Uri): Boolean {
    val value = uri.toString().lowercase()
    return value.startsWith("content://media/") && value.contains("/audio/albumart")
}

private fun isUsableArtworkUri(uri: Uri): Boolean {
    return when (uri.scheme) {
        "file", null -> uri.path?.let { File(it).exists() } == true
        else -> true
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongInfoBottomSheet(
    song: Song?,
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    onEditSong: ((title: String, artist: String, album: String, genre: String, year: Int, trackNumber: Int, artworkUri: Uri?, removeArtwork: Boolean, albumArtist: String?, composer: String?, discNumber: Int, onComplete: (Boolean) -> Unit) -> Unit)? = null,
    onShowLyricsEditor: (() -> Unit)? = null,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    isStreamingMode: Boolean = false,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onToggleDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var extendedInfo by remember { mutableStateOf<ExtendedSongInfo?>(null) }
    var isLoadingMetadata by remember { mutableStateOf(true) }
    var isLoadingStats by remember { mutableStateOf(true) }
    var showEditSheet by remember { mutableStateOf(false) }
    
    val isTablet = windowScreenWidthDp() >= 600
    val isLandscapeTablet = isTablet && windowScreenWidthDp() > windowScreenHeightDp()
    
    val useHoursFormat by appSettings.useHoursInTimeFormat.collectAsState()
    
    var showContent by remember { mutableStateOf(true) }
    
    var currentSong by remember(song?.id) { mutableStateOf(song) }
    
    LaunchedEffect(song) {
        if (song != null) {
            currentSong = song
        }
    }
    
    val blacklistedSongs by appSettings.blacklistedSongs.collectAsState()
    val blacklistedFolders by appSettings.blacklistedFolders.collectAsState()
    var isLoadingBlacklist by remember { mutableStateOf(false) }
    var showBlacklistTrackConfirm by remember { mutableStateOf(false) }
    var showBlacklistFolderConfirm by remember { mutableStateOf(false) }
    
    val whitelistedSongs by appSettings.whitelistedSongs.collectAsState()
    val whitelistedFolders by appSettings.whitelistedFolders.collectAsState()
    var isLoadingWhitelist by remember { mutableStateOf(false) }
    
    var songPlaybackStats by remember { mutableStateOf<chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository.SongPlaybackSummary?>(null) }
    
    val songArtShape = rememberExpressiveShapeFor(ExpressiveShapeTarget.SONG_ART)
    
    val isBlacklisted = song?.let { blacklistedSongs.contains(it.id) } ?: false
    
    val isWhitelisted = song?.let { whitelistedSongs.contains(it.id) } ?: false
    
    val folderPath = remember(song?.uri) {
        song?.let { 
            try {
                when (it.uri.scheme) {
                    "content" -> {
                        val projection = arrayOf(MediaStore.Audio.Media.DATA)
                        context.contentResolver.query(it.uri, projection, null, null, null)
                            ?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                                    val filePath = cursor.getString(dataIndex)
                                    File(filePath).parent
                                } else null
                            }
                    }
                    "file" -> File(it.uri.path ?: "").parent
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    val isInBlacklistedFolder = folderPath != null && blacklistedFolders.any { blacklistedPath ->
        folderPath.startsWith(blacklistedPath, ignoreCase = true)
    }
    
    val isInWhitelistedFolder = folderPath != null && whitelistedFolders.any { whitelistedPath ->
        folderPath.startsWith(whitelistedPath, ignoreCase = true)
    }

    if (song == null) {
        onDismiss()
        return
    }

    val displaySong = currentSong ?: song
    var displayArtworkUri by remember(displaySong.id, displaySong.artworkUri) {
        mutableStateOf(displaySong.artworkUri)
    }

    LaunchedEffect(displaySong.id, displaySong.uri, displaySong.artworkUri) {
        displayArtworkUri = withContext(Dispatchers.IO) {
            resolveSongInfoArtworkUri(context, displaySong)
        }
    }

    LaunchedEffect(song.id) {
        isLoadingMetadata = true
        extendedInfo = withContext(Dispatchers.IO) {
            MediaUtils.getExtendedSongInfo(context, song)
        }
        isLoadingMetadata = false
    }
    
    LaunchedEffect(song.id) {
        isLoadingStats = true
        song.let { currentSong ->
            songPlaybackStats = withContext(Dispatchers.IO) {
                chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository.getInstance(context).getSongPlaybackStats(
                    currentSong.id,
                    chromahub.rhythm.app.shared.data.repository.StatsTimeRange.ALL_TIME
                )
            }
        }
        isLoadingStats = false
    }
    
    if (showBlacklistTrackConfirm) {
        AlertDialog(
            onDismissRequest = { showBlacklistTrackConfirm = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isBlacklisted) RhythmIcons.CheckCircle else RhythmIcons.Block,
                        contentDescription = null,
                        tint = if (isBlacklisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isBlacklisted) stringResource(R.string.blacklist_remove_song_title) else stringResource(R.string.blacklist_add_song_title))
                }
            },
            text = {
                Column {
                    Text(
                        if (isBlacklisted) stringResource(R.string.blacklist_remove_song_desc) else stringResource(R.string.blacklist_add_song_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.blacklist_undo_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isLoadingBlacklist = true
                        song.let { songToBlock ->
                            if (isBlacklisted) {
                                appSettings.removeFromBlacklist(songToBlock.id)
                            } else {
                                appSettings.addToBlacklist(songToBlock.id)
                            }
                            isLoadingBlacklist = false
                            val message = if (isBlacklisted) context.getString(R.string.blacklist_song_removed_msg) else context.getString(R.string.blacklist_song_added_msg)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        showBlacklistTrackConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBlacklisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = if (isBlacklisted) RhythmIcons.CheckCircle else RhythmIcons.Block,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isBlacklisted) stringResource(R.string.blacklist_button_remove) else stringResource(R.string.blacklist_button_blacklist))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBlacklistTrackConfirm = false }
                ) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
    
    if (showBlacklistFolderConfirm) {
        AlertDialog(
            onDismissRequest = { showBlacklistFolderConfirm = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isInBlacklistedFolder) RhythmIcons.CheckCircle else MaterialSymbolIcon("folder_off", filled = true),
                        contentDescription = null,
                        tint = if (isInBlacklistedFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isInBlacklistedFolder) stringResource(R.string.blacklist_remove_folder_title) else stringResource(R.string.blacklist_add_folder_title))
                }
            },
            text = {
                Column {
                    folderPath?.let { path ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = path,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        if (isInBlacklistedFolder) stringResource(R.string.blacklist_remove_folder_desc) else stringResource(R.string.blacklist_add_folder_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.blacklist_undo_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isLoadingBlacklist = true
                        folderPath?.let { path ->
                            if (isInBlacklistedFolder) {
                                appSettings.removeFolderFromBlacklist(path)
                            } else {
                                appSettings.addFolderToBlacklist(path)
                            }
                        }
                        isLoadingBlacklist = false
                        val message = if (isInBlacklistedFolder) context.getString(R.string.blacklist_folder_removed_msg) else context.getString(R.string.blacklist_folder_added_msg)
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        showBlacklistFolderConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInBlacklistedFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = if (isInBlacklistedFolder) RhythmIcons.CheckCircle else MaterialSymbolIcon("folder_off", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isInBlacklistedFolder) stringResource(R.string.blacklist_button_remove) else stringResource(R.string.blacklist_button_blacklist))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBlacklistFolderConfirm = false }
                ) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (isLandscapeTablet) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .navigationBarsPadding()
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxHeight(),
                            color = Color.Transparent
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 24.dp, start = 32.dp, end = 32.dp, bottom = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(180.dp),
                                    shape = songArtShape,
                                    shadowElevation = 16.dp,
                                    tonalElevation = 8.dp
                                ) {
                                    Box {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .apply(
                                                    ImageUtils.buildImageRequest(
                                                        displayArtworkUri,
                                                        displaySong.title,
                                                        context.cacheDir,
                                                        M3PlaceholderType.TRACK
                                                    )
                                                )
                                                .build(),
                                            contentDescription = stringResource(R.string.songinfobottomsheet_song_artwork),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = displaySong.title,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = displaySong.artist,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight(),
                            color = Color.Transparent
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 24.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.songinfobottomsheet_details),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Edit button — local files only (hidden for streaming songs)
                                            if (!isStreamingMode) {
                                                onEditSong?.let {
                                                    AdaptiveSheetActionButton(
                                                        onClick = {
                                                            HapticUtils.performHapticFeedback(
                                                                context,
                                                                haptics,
                                                                HapticType.HEAVY
                                                            )
                                                            showEditSheet = true
                                                        },
                                                        icon = RhythmIcons.Edit,
                                                        contentDescription = stringResource(R.string.bottomsheet_timer_edit)
                                                    )
                                                }
                                            } else if (onToggleDownload != null) {
                                                AdaptiveSheetActionButton(
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(
                                                            context,
                                                            haptics,
                                                            HapticType.HEAVY
                                                        )
                                                        onToggleDownload()
                                                    },
                                                    icon = if (isDownloaded) MaterialSymbolIcon("download_done", filled = true) else MaterialSymbolIcon("download"),
                                                    contentDescription = if (isDownloaded) stringResource(R.string.streaming_remove_download) else stringResource(R.string.streaming_download)
                                                )
                                            }

                                            AdaptiveSheetCloseButton(
                                                onClick = onDismiss
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                    shape = RoundedCornerShape(
                                        topStart = 28.dp,
                                        topEnd = 28.dp
                                    ),
                                    tonalElevation = 1.dp
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            top = 8.dp,
                                            bottom = 24.dp,
                                            start = 16.dp,
                                            end = 16.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(24.dp),
                                        userScrollEnabled = true
                                    ) {
                                        item {
                                            SongInfoCard(
                                                song = currentSong ?: song,
                                                extendedInfo = extendedInfo,
                                                useHoursFormat = useHoursFormat,
                                                isLoading = isLoadingMetadata
                                            )
                                        }
                                        item {
                                            RhythmStatsCard(
                                                songPlaybackStats = songPlaybackStats,
                                                useHoursFormat = useHoursFormat,
                                                isLoading = isLoadingStats
                                            )
                                        }
                                        item {
                                            FileInfoCard(
                                                song = currentSong ?: song,
                                                extendedInfo = extendedInfo,
                                                folderPath = folderPath,
                                                isLoading = isLoadingMetadata
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showEditSheet) {
            EditSongSheet(
                song = currentSong ?: song,
                extendedInfo = extendedInfo,
                onDismiss = { showEditSheet = false },
                onSave = { title: String, artist: String, album: String, genre: String, year: Int, trackNumber: Int, artworkUri: Uri?, removeArtwork: Boolean, albumArtist: String?, composer: String?, discNumber: Int, onComplete ->
                    currentSong = currentSong?.copy(
                        title = title,
                        artist = artist,
                        album = album,
                        genre = genre,
                        year = year,
                        trackNumber = trackNumber,
                        artworkUri = when {
                            removeArtwork -> null
                            artworkUri != null -> artworkUri
                            else -> currentSong?.artworkUri
                        },
                        albumArtist = albumArtist,
                        discNumber = discNumber
                    )
                    onEditSong?.invoke(
                        title,
                        artist,
                        album,
                        genre,
                        year,
                        trackNumber,
                        artworkUri,
                        removeArtwork,
                        albumArtist,
                        composer,
                        discNumber
                    ) { success ->
                        onComplete(success)
                        if (success) {
                            showEditSheet = false
                        }
                    }
                },
                onShowLyricsEditor = onShowLyricsEditor,
                songArtShape = songArtShape
            )
        }
    } else {
        val infoListState = rememberLazyListState()

        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.TWO_PANE_DIALOG,
            lazyListState = infoListState,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = { 
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.primary
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // Header (Sticky at top)
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = songArtShape,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .apply(
                                        ImageUtils.buildImageRequest(
                                            displayArtworkUri,
                                            displaySong.title,
                                            context.cacheDir,
                                            M3PlaceholderType.TRACK
                                        )
                                    )
                                    .build(),
                                contentDescription = stringResource(R.string.songinfobottomsheet_song_artwork),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = displaySong.title,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            MarqueeText(
                                text = displaySong.artist,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                gradientEdgeColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                AdaptiveSheetScrollContainer(
                    lazyListState = infoListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) { endPadding ->
                    LazyColumn(
                        state = infoListState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp + endPadding, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
            if (isStreamingMode) {
                if (onToggleDownload != null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RhythmDetailActionButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    onToggleDownload()
                                },
                                height = 48.dp,
                                isFirst = true,
                                isLast = true,
                                type = RhythmButtonType.Tonal,
                                isLoading = isDownloading,
                                icon = if (isDownloaded) {
                                    MaterialSymbolIcon("download_done", filled = true)
                                } else {
                                    MaterialSymbolIcon("download")
                                } as MaterialSymbolIcon?,
                                iconSize = 18.dp,
                                text = when {
                                    isDownloading -> stringResource(R.string.streaming_downloading)
                                    isDownloaded -> stringResource(R.string.streaming_remove_download)
                                    else -> stringResource(R.string.streaming_download)
                                },
                                containerColor = if (isDownloaded)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (isDownloaded)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            } else {
                item {
                    // Actions section - only shown in local mode
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                                                Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (onEditSong != null) {
                                RhythmDetailActionButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        showEditSheet = true
                                    },
                                    height = 48.dp,
                                    isFirst = true,
                                    isLast = false,
                                    type = RhythmButtonType.Tonal,
                                    icon = RhythmIcons.Edit,
                                    iconSize = 16.dp,
                                    text = stringResource(R.string.bottomsheet_timer_edit),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            RhythmDetailActionButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showBlacklistTrackConfirm = true
                                },
                                height = 48.dp,
                                enabled = !isLoadingBlacklist,
                                isFirst = onEditSong == null,
                                isLast = folderPath == null,
                                type = RhythmButtonType.Tonal,
                                isLoading = isLoadingBlacklist,
                                icon = RhythmIcons.Block,
                                iconSize = 16.dp,
                                text = stringResource(R.string.bottomsheet_track),
                                containerColor = if (isBlacklisted)
                                    MaterialTheme.colorScheme.errorContainer
                                else
                                    MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (isBlacklisted)
                                    MaterialTheme.colorScheme.onErrorContainer
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )

                            if (folderPath != null) {
                                RhythmDetailActionButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        showBlacklistFolderConfirm = true
                                    },
                                    height = 48.dp,
                                    enabled = !isLoadingBlacklist,
                                    isFirst = false,
                                    isLast = true,
                                    type = RhythmButtonType.Tonal,
                                    isLoading = isLoadingBlacklist,
                                    icon = MaterialSymbolIcon("folder_off", filled = true),
                                    iconSize = 16.dp,
                                    text = stringResource(R.string.cd_folder),
                                    containerColor = if (isInBlacklistedFolder)
                                        MaterialTheme.colorScheme.errorContainer
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (isInBlacklistedFolder)
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    SongInfoCard(
                        song = displaySong,
                        extendedInfo = extendedInfo,
                        useHoursFormat = useHoursFormat,
                        isLoading = isLoadingMetadata
                    )
                }
            }

            item {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    RhythmStatsCard(
                        songPlaybackStats = songPlaybackStats,
                        useHoursFormat = useHoursFormat,
                        isLoading = isLoadingStats
                    )
                }
            }

            item {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    FileInfoCard(
                        song = displaySong,
                        extendedInfo = extendedInfo,
                        folderPath = folderPath,
                        isLoading = isLoadingMetadata
                    )
                }
            }
            }
        }
    }
        
    if (showEditSheet) {
            EditSongSheet(
                song = currentSong ?: song,
                extendedInfo = extendedInfo,
                onDismiss = { showEditSheet = false },
                onSave = { title, artist, album, genre, year, trackNumber, artworkUri, removeArtwork, albumArtist, composer, discNumber, onComplete ->
                    currentSong = currentSong?.copy(
                        title = title,
                        artist = artist,
                        album = album,
                        genre = genre,
                        year = year,
                        trackNumber = trackNumber,
                        artworkUri = when {
                            removeArtwork -> null
                            artworkUri != null -> artworkUri
                            else -> currentSong?.artworkUri
                        },
                        albumArtist = albumArtist,
                        discNumber = discNumber
                    )
                    onEditSong?.invoke(
                        title,
                        artist,
                        album,
                        genre,
                        year,
                        trackNumber,
                        artworkUri,
                        removeArtwork,
                        albumArtist,
                        composer,
                        discNumber
                    ) { success ->
                        onComplete(success)
                        if (success) {
                            showEditSheet = false
                        }
                    }
                },
                onShowLyricsEditor = onShowLyricsEditor,
                songArtShape = songArtShape
            )
        }
    }
    }
}

private fun computeGridCellInfo(
    itemCount: Int,
    isFullWidth: (Int) -> Boolean
): Pair<List<Pair<Int, Int>>, Int> {
    val cells = mutableListOf<Pair<Int, Int>>()
    var row = 0
    var col = 0
    for (i in 0 until itemCount) {
        if (isFullWidth(i)) {
            cells.add(Pair(row, 2))
            row++
            col = 0
        } else {
            cells.add(Pair(row, col))
            col++
            if (col == 2) {
                col = 0
                row++
            }
        }
    }
    val totalRows = if (col > 0) row + 1 else row
    return Pair(cells, totalRows)
}

private fun connectedGridItemShape(row: Int, col: Int, totalRows: Int): RoundedCornerShape {
    val isFirstRow = row == 0
    val isLastRow = row == totalRows - 1
    return when {
        col == 2 -> when {
            isFirstRow && isLastRow -> RoundedCornerShape(24.dp)
            isFirstRow -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            isLastRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(8.dp)
        }
        col == 0 -> when {
            isFirstRow && isLastRow -> RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
            isFirstRow -> RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            isLastRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
            else -> RoundedCornerShape(8.dp)
        }
        else -> when {
            isFirstRow && isLastRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
            isFirstRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            isLastRow -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(8.dp)
        }
    }
}

@Composable
private fun MetadataSection(
    title: String,
    icon: MaterialSymbolIcon,
    isLoading: Boolean,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center
            ) {
                ContentLoadingIndicator()
            }
        } else {
            content()
        }
    }
}

@Composable
private fun MetadataGridCard(
    title: String,
    icon: MaterialSymbolIcon,
    items: List<MetadataItem>,
    isLoading: Boolean,
    tint: Color = MaterialTheme.colorScheme.primary,
    tintContainer: Color = MaterialTheme.colorScheme.primaryContainer
) {
    val visibleItems = items.filter { it.value.isNotBlank() && !it.value.equals("Unknown", ignoreCase = true) }
    if (isLoading || visibleItems.isNotEmpty()) {
        MetadataSection(
            title = title,
            icon = icon,
            isLoading = isLoading,
            tint = tint
        ) {
            if (visibleItems.isNotEmpty()) {
                val regularCount = visibleItems.count { !it.isWide }
                val lastRegularIndex = visibleItems.indexOfLast { !it.isWide }
                val isFullWidth: (Int) -> Boolean = { index ->
                    visibleItems[index].isWide ||
                        (index == lastRegularIndex && regularCount % 2 == 1)
                }
                val (cells, totalRows) = computeGridCellInfo(visibleItems.size, isFullWidth)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    userScrollEnabled = false,
                    modifier = Modifier.height(
                        MusicDimensions.infoTileHeight * totalRows.coerceAtLeast(1) +
                            4.dp * (totalRows.coerceAtLeast(1) - 1)
                    )
                ) {
                    itemsIndexed(
                        items = visibleItems,
                        span = { index, _ -> if (isFullWidth(index)) GridItemSpan(2) else GridItemSpan(1) }
                    ) { index, item ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = 500,
                                    delayMillis = 400 + (index * 100)
                                )
                            ) + slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 500,
                                    delayMillis = 400 + (index * 100)
                                ),
                                initialOffsetY = { it / 5 }
                            )
                        ) {
                            val (row, col) = cells[index]
                            InfoGridItem(
                                item = item,
                                shape = connectedGridItemShape(row, col, totalRows),
                                tint = tint,
                                tintContainer = tintContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongInfoCard(
    song: Song,
    extendedInfo: ExtendedSongInfo?,
    useHoursFormat: Boolean = false,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    val songInfoItems = buildList {
        add(MetadataItem(context.getString(R.string.metadata_duration), formatDuration(song.duration, useHoursFormat), RhythmIcons.AccessTime))

        val rawTrack = song.trackNumber
        val trackNum = if (rawTrack >= 1000) rawTrack % 1000 else if (rawTrack > 0) rawTrack else 0
        val discNum = if (rawTrack >= 1000) rawTrack / 1000 else ((extendedInfo?.discNumber ?: 0).takeIf { it > 0 } ?: song.discNumber.takeIf { it > 0 } ?: 0)
        if (discNum > 0) {
            add(MetadataItem(context.getString(R.string.metadata_disc), discNum.toString(), RhythmIcons.AlbumFilled))
        }
        if (trackNum > 0) {
            add(MetadataItem(context.getString(R.string.metadata_track), trackNum.toString(), RhythmIcons.FormatListNumbered))
        }

        val yearValue = if (song.year > 0) song.year else extendedInfo?.year ?: 0
        if (yearValue > 0) {
            add(MetadataItem(context.getString(R.string.metadata_year), yearValue.toString(), RhythmIcons.DateRange))
        }

        val genreValue = if (!song.genre.isNullOrEmpty()) song.genre else extendedInfo?.genre
        if (!genreValue.isNullOrEmpty()) {
            add(MetadataItem(context.getString(R.string.metadata_genre), genreValue.trim(), RhythmIcons.Category))
        }

        if (!song.album.isNullOrEmpty()) {
            add(MetadataItem(context.getString(R.string.metadata_album), song.album, RhythmIcons.AlbumFilled, isWide = true))
        }

        extendedInfo?.let { info ->
            if (info.composer.isNotEmpty() && info.composer != song.artist) {
                add(MetadataItem(context.getString(R.string.metadata_composer), info.composer, MaterialSymbolIcon("edit_note", filled = true), isWide = true))
            }
            if (info.albumArtist.isNotEmpty() && info.albumArtist != song.artist) {
                add(MetadataItem(context.getString(R.string.metadata_album_artist), info.albumArtist, RhythmIcons.ArtistFilled, isWide = true))
            }
        }
    }

    MetadataGridCard(
        title = context.getString(R.string.cd_song_info),
        icon = RhythmIcons.Info,
        items = songInfoItems,
        isLoading = isLoading,
        tint = MaterialTheme.colorScheme.primary,
        tintContainer = MaterialTheme.colorScheme.primaryContainer
    )
}

@Composable
private fun RhythmStatsCard(
    songPlaybackStats: chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository.SongPlaybackSummary?,
    useHoursFormat: Boolean = false,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    val rhythmStatsItems = buildList {
        songPlaybackStats?.let { stats ->
            if (stats.playCount > 0) {
                add(MetadataItem(context.getString(R.string.metadata_play_count), stats.playCount.toString(), RhythmIcons.Play))
            }
            if (stats.totalDurationMs > 0) {
                add(MetadataItem(context.getString(R.string.metadata_total_played), formatDuration(stats.totalDurationMs, useHoursFormat), RhythmIcons.AccessTime))
            }
        }
    }

    MetadataGridCard(
        title = context.getString(R.string.rhythm_stats),
        icon = RhythmIcons.BarChart,
        items = rhythmStatsItems,
        isLoading = isLoading,
        tint = MaterialTheme.colorScheme.secondary,
        tintContainer = MaterialTheme.colorScheme.secondaryContainer
    )
}

@Composable
private fun FileInfoCard(
    song: Song,
    extendedInfo: ExtendedSongInfo?,
    folderPath: String?,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    val fileInfoItems = buildList {
        extendedInfo?.let { info ->
            if (info.qualityLabel != "Unknown" && info.qualityLabel.isNotEmpty()) {
                val qualityIcon: Any = when {
                    info.isDolby -> R.drawable.ic_dolby
                    info.isDTS -> R.drawable.ic_dts
                    info.isLossless -> {
                        if (info.qualityLabel.contains("Hi-Res", ignoreCase = true) || info.isHiRes) {
                            R.drawable.ic_high_res
                        } else {
                            R.drawable.ic_cd
                        }
                    }
                    info.isHiRes -> R.drawable.ic_high_res
                    else -> MaterialSymbolIcon("graphic_eq", filled = true)
                }
                val localizedLabel = info.qualityLabel
                add(MetadataItem(context.getString(R.string.metadata_quality), localizedLabel, qualityIcon))
            }

            if (info.qualityLabel == "Unknown") {
                if (info.isLossless) {
                    add(MetadataItem(context.getString(R.string.metadata_quality), "Lossless", R.drawable.ic_cd))
                }
                if (info.isDolby) {
                    add(MetadataItem(context.getString(R.string.metadata_audio_tech), "Dolby", R.drawable.ic_dolby))
                }
                if (info.isDTS) {
                    add(MetadataItem(context.getString(R.string.metadata_audio_tech), "DTS", R.drawable.ic_dts))
                }
                if (info.isHiRes && !info.isLossless) {
                    add(MetadataItem(context.getString(R.string.metadata_quality), "Hi-Res", R.drawable.ic_high_res))
                }
            }

            if (info.bitDepth > 0) {
                add(MetadataItem(context.getString(R.string.metadata_bit_depth), "${info.bitDepth}-bit", MaterialSymbolIcon("high_quality", filled = true)))
            }
            if (info.bitrate != "Unknown") {
                add(MetadataItem(context.getString(R.string.metadata_bitrate), info.bitrate, MaterialSymbolIcon("graphic_eq", filled = true)))
            }
            if (info.sampleRate != "Unknown") {
                add(MetadataItem(context.getString(R.string.metadata_sample_rate), info.sampleRate, RhythmIcons.Tune))
            }
            if (info.channels != "Unknown") {
                add(MetadataItem(context.getString(R.string.metadata_channels), info.channels, MaterialSymbolIcon("settings_input_component", filled = true)))
            }
            if (info.formatName != "Unknown") {
                add(MetadataItem(context.getString(R.string.metadata_format), info.formatName, RhythmIcons.MusicNote))
            } else if (info.format != "Unknown") {
                add(MetadataItem(context.getString(R.string.metadata_format), info.format, RhythmIcons.MusicNote))
            }

            folderPath?.let {
                add(MetadataItem(context.getString(R.string.metadata_location), it, RhythmIcons.FolderOpen, isWide = true))
            }

            if (info.hasLyrics) {
                add(MetadataItem(context.getString(R.string.metadata_lyrics), context.getString(R.string.metadata_lyrics_available), MaterialSymbolIcon("lyrics", filled = true)))
            }
            if (info.mimeType.isNotEmpty()) {
                add(MetadataItem(context.getString(R.string.metadata_mime_type), info.mimeType.substringAfter("/").uppercase(), RhythmIcons.Code))
            }

            if (info.dateAdded > 0) {
                add(MetadataItem(context.getString(R.string.metadata_date_added), formatDate(context, info.dateAdded), RhythmIcons.Add))
            }
            if (info.dateModified > 0 && info.dateModified != info.dateAdded) {
                add(MetadataItem(context.getString(R.string.metadata_modified), formatDate(context, info.dateModified), RhythmIcons.Update))
            }
        }
    }

    MetadataGridCard(
        title = context.getString(R.string.file_info),
        icon = RhythmIcons.Folder,
        items = fileInfoItems,
        isLoading = isLoading,
        tint = MaterialTheme.colorScheme.tertiary,
        tintContainer = MaterialTheme.colorScheme.tertiaryContainer
    )
}

@Composable
private fun InfoGridItem(
    item: MetadataItem,
    shape: RoundedCornerShape,
    tint: Color = MaterialTheme.colorScheme.primary,
    tintContainer: Color = MaterialTheme.colorScheme.primaryContainer
) {
    val tileColor = lerp(
        MaterialTheme.colorScheme.surfaceContainer,
        tintContainer,
        0.35f
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(MusicDimensions.infoTileHeight)
            .clip(shape),
        shape = shape,
        color = tileColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val icon = item.icon) {
                is MaterialSymbolIcon -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint.copy(alpha = MusicDimensions.infoTileWatermarkAlpha),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(
                            x = MusicDimensions.infoTileBackdropCut,
                            y = MusicDimensions.infoTileBackdropCut
                        )
                        .size(MusicDimensions.infoTileIconSize)
                )
                is Int -> Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = tint.copy(alpha = MusicDimensions.infoTileWatermarkAlpha),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(
                            x = MusicDimensions.infoTileBackdropCut,
                            y = MusicDimensions.infoTileBackdropCut
                        )
                        .size(MusicDimensions.infoTileIconSize)
                )
            }

            MarqueeText(
                text = item.value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                ),
                gradientEdgeColor = tileColor,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = MusicDimensions.infoTileInset,
                        end = MusicDimensions.infoTileInset,
                        bottom = MusicDimensions.infoTileInset
                    )
            )

            Text(
                text = item.label,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = tint,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = MusicDimensions.infoTileInset, start = MusicDimensions.infoTileInset)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSongSheet(
    song: Song,
    extendedInfo: ExtendedSongInfo?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        trackNumber: Int,
        artworkUri: Uri?,
        removeArtwork: Boolean,
        albumArtist: String?,
        composer: String?,
        discNumber: Int,
        onComplete: (success: Boolean) -> Unit
    ) -> Unit,
    onShowLyricsEditor: (() -> Unit)? = null,
    songArtShape: androidx.compose.ui.graphics.Shape
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    
    val isTablet = windowScreenWidthDp() >= 600
    val isLandscapeTablet = isTablet && windowScreenWidthDp() > windowScreenHeightDp()
    
    val initYear = if (song.year > 0) song.year else (extendedInfo?.year ?: 0)
    val initTrackRaw = song.trackNumber
    val initTrack = if (initTrackRaw >= 1000) initTrackRaw % 1000 else initTrackRaw
    val initDisc = if (initTrackRaw >= 1000) initTrackRaw / 1000 else if (song.discNumber > 0) song.discNumber else (extendedInfo?.discNumber ?: 1)

    val originalTitle by remember(song.id) { mutableStateOf(song.title) }
    val originalArtist by remember(song.id) { mutableStateOf(song.artist) }
    val originalAlbum by remember(song.id) { mutableStateOf(song.album) }
    val originalGenre by remember(song.id) { mutableStateOf(song.genre ?: extendedInfo?.genre ?: "") }
    val originalYear by remember(song.id) { mutableStateOf(if (initYear > 0) initYear.toString() else "") }
    val originalTrackNumber by remember(song.id) { mutableStateOf(if (initTrack > 0) initTrack.toString() else "") }
    val originalAlbumArtist by remember(song.id) { mutableStateOf(song.albumArtist ?: extendedInfo?.albumArtist ?: "") }
    val originalComposer by remember(song.id) { mutableStateOf(extendedInfo?.composer ?: "") }
    val originalDiscNumber by remember(song.id) { mutableStateOf(if (initDisc > 0) initDisc.toString() else "1") }
    
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var album by remember(song.id) { mutableStateOf(song.album) }
    var genre by remember(song.id) { mutableStateOf(song.genre ?: extendedInfo?.genre ?: "") }
    var year by remember(song.id) { mutableStateOf(if (initYear > 0) initYear.toString() else "") }
    var trackNumber by remember(song.id) { mutableStateOf(if (initTrack > 0) initTrack.toString() else "") }
    var albumArtist by remember(song.id) { mutableStateOf(song.albumArtist ?: extendedInfo?.albumArtist ?: "") }
    var composer by remember(song.id) { mutableStateOf(extendedInfo?.composer ?: "") }
    var discNumber by remember(song.id) { mutableStateOf(if (initDisc > 0) initDisc.toString() else "1") }
    var selectedImageUri by remember(song.id) { mutableStateOf<Uri?>(null) }
    var removeArtwork by remember(song.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    var showWarningDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isFetchingOnlineArt by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var showContent by remember { mutableStateOf(true) }
    var resolvedSongArtworkUri by remember(song.id, song.artworkUri, song.uri) {
        mutableStateOf(song.artworkUri)
    }

    LaunchedEffect(song.id, song.artworkUri, song.uri) {
        resolvedSongArtworkUri = withContext(Dispatchers.IO) {
            resolveSongInfoArtworkUri(context, song)
        }
    }
    
    val resetToOriginal = {
        title = originalTitle
        artist = originalArtist
        album = originalAlbum
        genre = originalGenre
        year = originalYear
        trackNumber = originalTrackNumber
        albumArtist = originalAlbumArtist
        composer = originalComposer
        discNumber = originalDiscNumber
        selectedImageUri = null
        removeArtwork = false
        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
    }
    
    val proceedWithSave = { 
        val yearInt = year.toIntOrNull() ?: 0
        val trackInt = trackNumber.toIntOrNull() ?: 0
        val discInt = discNumber.toIntOrNull() ?: 1
        
        isSaving = true
            onSave(
            title.trim(),
            artist.trim(),
            album.trim(),
            genre.trim(),
            yearInt,
            trackInt,
            selectedImageUri,
            removeArtwork,
            albumArtist.trim(),
            composer.trim(),
            discInt
        ) { success ->
            isSaving = false
            if (success) {
                onDismiss()
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            proceedWithSave()
        } else {
            isSaving = false
            Toast.makeText(
                context, 
                "Storage permission is required to edit audio file metadata", 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            proceedWithSave()
        } else {
            isSaving = false
            Toast.makeText(
                context,
                "Media permissions are required to edit audio file metadata",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
        if (uri != null) {
            removeArtwork = false
        }
    }

    val artworkPreviewUri = when {
        removeArtwork -> null
        selectedImageUri != null -> selectedImageUri
        else -> resolvedSongArtworkUri
    }
    var hasArtworkPreview by remember(artworkPreviewUri, removeArtwork, selectedImageUri) {
        mutableStateOf(selectedImageUri != null)
    }

    LaunchedEffect(artworkPreviewUri, removeArtwork, selectedImageUri) {
        hasArtworkPreview = when {
            removeArtwork -> false
            selectedImageUri != null -> true
            artworkPreviewUri == null -> false
            else -> withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(artworkPreviewUri)?.use { stream ->
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, bounds)
                        bounds.outWidth > 0 && bounds.outHeight > 0
                    } ?: false
                }.getOrDefault(false)
            }
        }
    }
    
    fun handleSave() {
        if (isSaving) return
        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
        showWarningDialog = true
    }
    
    fun proceedAfterWarning() {
        isSaving = true
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ - Check if audio permission is already granted
                val hasAudioPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                
                if (hasAudioPermission) {
                    proceedWithSave()
                } else {
                    // Request only audio permission (images are optional)
                    multiplePermissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_MEDIA_AUDIO
                        )
                    )
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11-12 - Use scoped storage (no special permissions needed for MediaStore)
                proceedWithSave()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10 - Scoped storage but may need some permissions
                proceedWithSave()
            }
            else -> {
                // Android 9 and below - Request write permission
                val hasWritePermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                
                if (hasWritePermission) {
                    proceedWithSave()
                } else {
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    if (isLandscapeTablet) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxHeight(),
                            color = Color.Transparent
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 24.dp, start = 32.dp, end = 16.dp, bottom = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(220.dp)
                                        .clip(songArtShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .apply(
                                                ImageUtils.buildImageRequest(
                                                    artworkPreviewUri,
                                                    song.title,
                                                    context.cacheDir,
                                                    M3PlaceholderType.TRACK
                                                )
                                            )
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = stringResource(R.string.content_desc_album_artwork),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    IconButton(
                                        onClick = {
                                            imagePickerLauncher.launch("image/*")
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(44.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
                                                shape = CircleShape
                                            ),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Image,
                                            contentDescription = stringResource(R.string.songinfobottomsheet_change_artwork),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                RhythmDetailActionButtonFullWidth(
                                    onClick = {
                                        selectedImageUri = null
                                        removeArtwork = true
                                    },
                                    enabled = hasArtworkPreview,
                                    height = 48.dp,
                                    type = RhythmButtonType.Tonal,
                                    icon = RhythmIcons.Delete,
                                    iconSize = 18.dp,
                                    text = stringResource(R.string.songinfobottomsheet_remove_artwork),
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight(),
                            color = Color.Transparent
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 24.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = context.getString(R.string.edit_metadata),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )

                                        AdaptiveSheetCloseButton(
                                            onClick = onDismiss
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    tonalElevation = 1.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = title,
                                            onValueChange = { title = it },
                                            label = { Text(stringResource(R.string.bottomsheet_title)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = RhythmIcons.MusicNote,
                                                    contentDescription = null
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = artist,
                                            onValueChange = { artist = it },
                                            label = { Text(stringResource(R.string.player_chip_artist)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = RhythmIcons.ArtistFilled,
                                                    contentDescription = null
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = album,
                                            onValueChange = { album = it },
                                            label = { Text(stringResource(R.string.player_chip_album)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = RhythmIcons.AlbumFilled,
                                                    contentDescription = null
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = albumArtist,
                                            onValueChange = { albumArtist = it },
                                            label = { Text(stringResource(R.string.metadata_album_artist)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = RhythmIcons.ArtistFilled,
                                                    contentDescription = null
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = composer,
                                            onValueChange = { composer = it },
                                            label = { Text(stringResource(R.string.metadata_composer)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = RhythmIcons.Edit,
                                                    contentDescription = null
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = genre,
                                            onValueChange = { genre = it },
                                            label = { Text(stringResource(R.string.bottomsheet_genre)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = RhythmIcons.Category,
                                                    contentDescription = null
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            singleLine = true
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = year,
                                                onValueChange = { year = it },
                                                label = { Text(stringResource(R.string.bottomsheet_year)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = RhythmIcons.DateRange,
                                                        contentDescription = null
                                                    )
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(16.dp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )

                                            OutlinedTextField(
                                                value = trackNumber,
                                                onValueChange = { trackNumber = it },
                                                label = { Text(stringResource(R.string.bottomsheet_track)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = RhythmIcons.FormatListNumbered,
                                                        contentDescription = null
                                                    )
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(16.dp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )

                                            OutlinedTextField(
                                                value = discNumber,
                                                onValueChange = { discNumber = it },
                                                label = { Text(stringResource(R.string.metadata_disc)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = RhythmIcons.FormatListNumbered,
                                                        contentDescription = null
                                                    )
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(16.dp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        AnimatedVisibility(visible = isSaving) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                LinearWavyProgressIndicator(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = stringResource(R.string.metadata_saving),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    resetToOriginal()
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Icon(
                                                    imageVector = MaterialSymbolIcon("restart_alt", filled = true),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(stringResource(R.string.ui_reset))
                                            }

                                            OutlinedButton(
                                                onClick = onDismiss,
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Close,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(stringResource(R.string.ui_cancel))
                                            }

                                            Button(
                                                onClick = { handleSave() },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(16.dp),
                                                enabled = title.isNotBlank() && artist.isNotBlank() && !isSaving
                                            ) {
                                                Icon(
                                                    imageVector = MaterialSymbolIcon("save", filled = true),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(stringResource(R.string.ui_save))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                if (NetworkClient.isYTMusicApiEnabled()) {
                                    Button(
                                        onClick = {
                                            isFetchingOnlineArt = true
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val apiService = NetworkClient.ytmusicApiService
                                                    if (apiService != null) {
                                                        val searchQuery = "${title.trim()} ${artist.trim()}"
                                                        val searchRequest = YTMusicSearchRequest(
                                                            context = YTMusicContext(YTMusicClient()),
                                                            query = searchQuery,
                                                            params = "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D"
                                                        )
                                                        val response = apiService.search(request = searchRequest)
                                                        if (response.isSuccessful) {
                                                            val imageUrl = response.body()?.extractAlbumImageUrl()
                                                            if (!imageUrl.isNullOrEmpty()) {
                                                                val okRequest = okhttp3.Request.Builder().url(imageUrl).build()
                                                                val okResponse = NetworkClient.genericHttpClient.newCall(okRequest).execute()
                                                                if (okResponse.isSuccessful) {
                                                                    val bytes = okResponse.body.bytes()
                                                                    val tempFile = File(context.cacheDir, "temp_artwork_fetched_${song.id}.jpg")
                                                                    tempFile.writeBytes(bytes)
                                                                    withContext(Dispatchers.Main) {
                                                                        selectedImageUri = Uri.fromFile(tempFile)
                                                                        removeArtwork = false
                                                                        Toast.makeText(context, R.string.songinfobottomsheet_artwork_fetched_successfully_click, Toast.LENGTH_SHORT).show()
                                                                    }
                                                                } else {
                                                                    withContext(Dispatchers.Main) {
                                                                        Toast.makeText(context, R.string.songinfobottomsheet_failed_to_download_artwork_1, Toast.LENGTH_SHORT).show()
                                                                    }
                                                                }
                                                            } else {
                                                                withContext(Dispatchers.Main) {
                                                                    Toast.makeText(context, R.string.songinfobottomsheet_no_artwork_found_for, Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        } else {
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, R.string.songinfobottomsheet_online_search_failed, Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    } else {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, R.string.songinfobottomsheet_online_api_service_unavailable, Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, context.getString(R.string.error_fetching_artwork, e.message ?: ""), Toast.LENGTH_LONG).show()
                                                    }
                                                } finally {
                                                    withContext(Dispatchers.Main) {
                                                        isFetchingOnlineArt = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isFetchingOnlineArt && title.isNotBlank() && artist.isNotBlank(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    ) {
                                        if (isFetchingOnlineArt) {
                                            ActionProgressLoader(
                                                size = 18.dp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.songinfobottomsheet_fetching))
                                        } else {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("cloud_download", filled = true),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.songinfobottomsheet_fetch_online_art))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showWarningDialog) {
            AlertDialog(
                onDismissRequest = { showWarningDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.bottomsheet_irreversible))
                    }
                },
                text = {
                    Column {
                        Text(
                            "The changes you're about to make will permanently modify the audio file's metadata.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This action cannot be undone. Make sure you have a backup if needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showWarningDialog = false
                            proceedAfterWarning()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.bottomsheet_proceed))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { 
                            showWarningDialog = false
                            isSaving = false
                        }
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_cancel))
                    }
                },
                shape = RoundedCornerShape(24.dp)
            )
        }
    } else {
        RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.TWO_PANE_DIALOG,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            StandardBottomSheetHeader(
                title = context.getString(R.string.edit_metadata),
                subtitle = stringResource(R.string.songinfobottomsheet_update_artwork_and_tags),
                visible = true
            )
            
            val editScrollState = rememberScrollState()

            AdaptiveSheetScrollContainer(
                scrollState = editScrollState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { endPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = endPadding)
                        .verticalScroll(editScrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(196.dp)
                                    .clip(songArtShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .apply(
                                            ImageUtils.buildImageRequest(
                                                artworkPreviewUri,
                                                song.title,
                                                context.cacheDir,
                                                M3PlaceholderType.TRACK
                                            )
                                        )
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = stringResource(R.string.content_desc_album_artwork),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                IconButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(40.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Image,
                                        contentDescription = stringResource(R.string.songinfobottomsheet_change_artwork),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                                                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RhythmDetailActionButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    height = 48.dp,
                                    isFirst = true,
                                    isLast = !hasArtworkPreview,
                                    type = RhythmButtonType.Filled,
                                    icon = RhythmIcons.Image,
                                    iconSize = 18.dp,
                                    text = if (selectedImageUri != null) "Change" else "Select"
                                )

                                if (hasArtworkPreview) {
                                    RhythmDetailActionButton(
                                        onClick = {
                                            selectedImageUri = null
                                            removeArtwork = true
                                        },
                                        height = 48.dp,
                                        isFirst = false,
                                        isLast = true,
                                        type = RhythmButtonType.Tonal,
                                        icon = RhythmIcons.Delete,
                                        iconSize = 18.dp,
                                        text = stringResource(R.string.content_desc_remove),
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            if (NetworkClient.isYTMusicApiEnabled()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        isFetchingOnlineArt = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val apiService = NetworkClient.ytmusicApiService
                                                if (apiService != null) {
                                                    val searchQuery = "${title.trim()} ${artist.trim()}"
                                                    val searchRequest = YTMusicSearchRequest(
                                                        context = YTMusicContext(YTMusicClient()),
                                                        query = searchQuery,
                                                        params = "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D"
                                                    )
                                                    val response = apiService.search(request = searchRequest)
                                                    if (response.isSuccessful) {
                                                        val imageUrl = response.body()?.extractAlbumImageUrl()
                                                        if (!imageUrl.isNullOrEmpty()) {
                                                            val okRequest = okhttp3.Request.Builder().url(imageUrl).build()
                                                            val okResponse = NetworkClient.genericHttpClient.newCall(okRequest).execute()
                                                            if (okResponse.isSuccessful) {
                                                                val bytes = okResponse.body.bytes()
                                                                val tempFile = File(context.cacheDir, "temp_artwork_fetched_${song.id}.jpg")
                                                                tempFile.writeBytes(bytes)
                                                                withContext(Dispatchers.Main) {
                                                                    selectedImageUri = Uri.fromFile(tempFile)
                                                                    removeArtwork = false
                                                                    Toast.makeText(context, R.string.songinfobottomsheet_artwork_fetched_successfully_click, Toast.LENGTH_SHORT).show()
                                                                }
                                                            } else {
                                                                withContext(Dispatchers.Main) {
                                                                    Toast.makeText(context, R.string.songinfobottomsheet_failed_to_download_artwork_1, Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                        } else {
                                                            withContext(Dispatchers.Main) {
                                                                    Toast.makeText(context, R.string.songinfobottomsheet_no_artwork_found_for, Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    } else {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, R.string.songinfobottomsheet_online_search_failed, Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, R.string.songinfobottomsheet_online_api_service_unavailable, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, context.getString(R.string.error_fetching_artwork, e.message ?: ""), Toast.LENGTH_LONG).show()
                                                }
                                            } finally {
                                                withContext(Dispatchers.Main) {
                                                    isFetchingOnlineArt = false
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isFetchingOnlineArt && title.isNotBlank() && artist.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    if (isFetchingOnlineArt) {
                                        ActionProgressLoader(
                                            size = 18.dp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.songinfobottomsheet_fetching_artwork))
                                    } else {
                                        Icon(
                                            imageVector = MaterialSymbolIcon("cloud_download", filled = true),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.songinfobottomsheet_fetch_online_art))
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = context.getString(R.string.song_info_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text(stringResource(R.string.bottomsheet_title)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = RhythmIcons.MusicNote,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = artist,
                                onValueChange = { artist = it },
                                label = { Text(stringResource(R.string.player_chip_artist)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = RhythmIcons.ArtistFilled,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = album,
                                onValueChange = { album = it },
                                label = { Text(stringResource(R.string.player_chip_album)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = RhythmIcons.AlbumFilled,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = albumArtist,
                                onValueChange = { albumArtist = it },
                                label = { Text(stringResource(R.string.metadata_album_artist)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = RhythmIcons.ArtistFilled,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = composer,
                                onValueChange = { composer = it },
                                label = { Text(stringResource(R.string.metadata_composer)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = RhythmIcons.Edit,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = genre,
                                onValueChange = { genre = it },
                                label = { Text(stringResource(R.string.bottomsheet_genre)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = RhythmIcons.Category,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = year,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() } && input.length <= 4) {
                                            year = input
                                        }
                                    },
                                    label = { Text(stringResource(R.string.bottomsheet_year)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = RhythmIcons.DateRange,
                                            contentDescription = null
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                OutlinedTextField(
                                    value = trackNumber,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() } && input.length <= 3) {
                                            trackNumber = input
                                        }
                                    },
                                    label = { Text(stringResource(R.string.bottomsheet_track)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = RhythmIcons.FormatListNumbered,
                                            contentDescription = null
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                OutlinedTextField(
                                    value = discNumber,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() } && input.length <= 3) {
                                            discNumber = input
                                        }
                                    },
                                    label = { Text(stringResource(R.string.metadata_disc)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = RhythmIcons.FormatListNumbered,
                                            contentDescription = null
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isSaving) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.metadata_saving),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            RhythmGroupedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                size = RhythmButtonSize.Large
            ) {
                RhythmButtonWeighted(
                    onClick = {
                        HapticUtils.performHapticFeedback(
                            context,
                            haptics,
                            HapticType.HEAVY
                        )
                        resetToOriginal()
                    },
                    weight = 1f,
                    isFirst = true,
                    enabled = !isSaving,
                    icon = MaterialSymbolIcon("restart_alt", filled = true),
                    text = stringResource(R.string.ui_reset)
                )

                RhythmButtonWeighted(
                    onClick = { handleSave() },
                    weight = 1f,
                    isLast = true,
                    enabled = title.isNotBlank() && artist.isNotBlank() && !isSaving,
                    icon = MaterialSymbolIcon("save", filled = true),
                    text = stringResource(R.string.ui_save)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    
    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RhythmIcons.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bottomsheet_irreversible))
                }
            },
            text = {
                Column {
                    Text(
                        "The changes you're about to make will permanently modify the audio file's metadata.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This action cannot be undone. Make sure you have a backup if needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWarningDialog = false
                        proceedAfterWarning()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = RhythmIcons.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bottomsheet_proceed))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { 
                        showWarningDialog = false
                        isSaving = false  // Reset saving state when user cancels
                    }
                ) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
    }
}

data class MetadataItem(
    val label: String,
    val value: String,
    val icon: Any,
    val isWide: Boolean = false
)

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1 -> String.format(Locale.ROOT, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.ROOT, "%.2f MB", mb)
        kb >= 1 -> String.format(Locale.ROOT, "%.2f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatDate(context: android.content.Context, timestamp: Long): String {
    val normalizedTimestamp = if (timestamp in 1..99_999_999_999L) timestamp * 1000L else timestamp
    return if (normalizedTimestamp > 0) {
        val date = java.util.Date(normalizedTimestamp)
        val formatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        formatter.format(date)
    } else {
        context.getString(R.string.common_unknown)
    }
}
