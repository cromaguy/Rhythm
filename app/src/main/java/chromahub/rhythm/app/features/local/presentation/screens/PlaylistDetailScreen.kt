package chromahub.rhythm.app.features.local.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.content.Context
import androidx.compose.ui.focus.FocusRequester
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingsSearchBar
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSortMenuContent
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSortOption
import chromahub.rhythm.app.shared.presentation.components.common.RhythmDetailActionButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmDetailActionButtonFullWidth
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveScrollBar
import chromahub.rhythm.app.shared.presentation.components.common.playlistDetailFastScrollLabel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongPickerBottomSheet
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.player.MiniPlayer
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons.Search
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.ui.UiConstants
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistExportDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistImportDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationProgressDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationResultDialog
import chromahub.rhythm.app.util.PlaylistImportExportUtils
import android.net.Uri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.M3ImageUtils
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.DragDropLazyColumn
import chromahub.rhythm.app.shared.presentation.components.player.formatDuration
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.PlaylistSongOptionsBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongInfoBottomSheet
import kotlinx.coroutines.delay // Import delay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.collectAsState
import chromahub.rhythm.app.shared.presentation.components.player.PlayingEqIcon
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.HorizontalDivider
import androidx.room.util.copy
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.res.stringResource

// Playlist sort order enum
enum class PlaylistSortOrder {
    TITLE_ASC, TITLE_DESC,
    ARTIST_ASC, ARTIST_DESC,
    ALBUM_ASC, ALBUM_DESC,
    DURATION_ASC, DURATION_DESC,
    DATE_ADDED_ASC, DATE_ADDED_DESC
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onPlayerClick: () -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit = {},
    onSongClick: (Song) -> Unit,
    onPlaySongFromPlaylist: ((Song, List<Song>) -> Unit)? = null,
    onBack: () -> Unit,
    onRemoveSong: (Song, String) -> Unit = { _, _ -> },
    onRenamePlaylist: (String) -> Unit = {},
    onDeletePlaylist: () -> Unit = {},
    onAddSongsToPlaylist: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onExportPlaylist: ((PlaylistImportExportUtils.PlaylistExportFormat) -> Unit)? = null,
    onExportPlaylistToCustomLocation: ((PlaylistImportExportUtils.PlaylistExportFormat, Uri) -> Unit)? = null,
    onImportPlaylist: ((Uri, (Result<String>) -> Unit, (() -> Unit)?) -> Unit)? = null,
    onReorderSongs: ((Int, Int) -> Unit)? = null,
    onUpdatePlaylistSongs: ((List<Song>) -> Unit)? = null,
    isStreamingPlaylist: Boolean = false,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onToggleFavorite: (Song) -> Unit = {},
    onGoToAlbum: (Song) -> Unit = {},
    onGoToArtist: (Song) -> Unit = {},
    onShare: (Song) -> Unit = {},
    musicViewModel: MusicViewModel = viewModel()
) {
    // Screen size detection for responsive UI
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val isExtraSmallWidth = screenWidthDp < 360
    val isCompactWidth = screenWidthDp < 400
    val isMidWidth = screenWidthDp in 400..499
    val isTablet = screenWidthDp >= 600
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isLandscapeTablet = isTablet && isLandscape
    val isCompactHeight = screenHeightDp < 600
    val isLargeHeight = screenHeightDp > 800

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showOperationProgress by remember { mutableStateOf(false) }
    var operationInProgress by remember { mutableStateOf("") }
    var operationResult by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var newPlaylistName by remember { mutableStateOf(playlist.name) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var showQueueOptionsDialog by remember { mutableStateOf(false) }
    var selectedSongForQueue by remember { mutableStateOf<Song?>(null) }
    var isReorderMode by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    // Song options bottom sheet
    var showSongOptionsSheet by remember { mutableStateOf(false) }
    var selectedSongForOptions by remember { mutableStateOf<Song?>(null) }
    var selectedSongForPlaylistAdd by remember { mutableStateOf<Song?>(null) }
    var showPlaylistSelector by remember { mutableStateOf(false) }
    var selectedSongForInfo by remember { mutableStateOf<Song?>(null) }
    var showSongInfo by remember { mutableStateOf(false) }
    
    // Multi-select mode state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<String>()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    // Song picker sheet state
    val coroutineScope = rememberCoroutineScope()
    val allSongs by musicViewModel.filteredSongs.collectAsState()
    var showSongPicker by remember { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val appSettings = remember { chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context) }
    var pendingMetadataEditCompleteCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            musicViewModel.completeMetadataWriteAfterPermission(
                onSuccess = {
                    Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully, Toast.LENGTH_SHORT).show()
                    pendingMetadataEditCompleteCallback?.invoke(true)
                    pendingMetadataEditCompleteCallback = null
                },
                onError = { errorMessage ->
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    pendingMetadataEditCompleteCallback?.invoke(false)
                    pendingMetadataEditCompleteCallback = null
                }
            )
        } else {
            musicViewModel.cancelPendingMetadataWrite()
            pendingMetadataEditCompleteCallback?.invoke(false)
            pendingMetadataEditCompleteCallback = null
            Toast.makeText(context, R.string.localnavigation_permission_denied_changes_saved, Toast.LENGTH_LONG).show()
        }
    }
    val playlistClickBehavior by appSettings.playlistClickBehavior.collectAsState(initial = "ask")
    val useHoursFormat by appSettings.useHoursInTimeFormat.collectAsState()
    val canEditPlaylist = !isStreamingPlaylist
    
    // Track current sort order for playlist - persisted via AppSettings
    val persistedSortOrder by appSettings.playlistDetailSortOrder.collectAsState()
    var currentPlaylistSort by remember(persistedSortOrder) {
        mutableStateOf(
            try { PlaylistSortOrder.valueOf(persistedSortOrder) }
            catch (_: Exception) { PlaylistSortOrder.TITLE_ASC }
        )
    }
    

    if (showQueueOptionsDialog && selectedSongForQueue != null) {
        AlertDialog(
            onDismissRequest = { 
                showQueueOptionsDialog = false
                selectedSongForQueue = null
            },
            icon = {
                Icon(
                    imageVector = RhythmIcons.Queue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { 
                Text(
                    "Play from Playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Song info
                    Text(
                        selectedSongForQueue!!.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${selectedSongForQueue!!.artist} • ${playlist.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Option 1: Load Playlist & Play
                    Surface(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            onPlaySongFromPlaylist?.invoke(selectedSongForQueue!!, playlist.songs)
                            showQueueOptionsDialog = false
                            selectedSongForQueue = null
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = RhythmIcons.Queue,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Load Playlist & Play",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Replace queue with playlist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    
                    // Option 2: Play This Song Only
                    Surface(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            onSongClick(selectedSongForQueue!!)
                            showQueueOptionsDialog = false
                            selectedSongForQueue = null
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = RhythmIcons.Play,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Play This Song Only",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Don't change the queue",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        showQueueOptionsDialog = false
                        selectedSongForQueue = null
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
    
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            icon = {
                Icon(
                    imageVector = RhythmIcons.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(context.getString(R.string.playlist_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(context.getString(R.string.playlist_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onRenamePlaylist(newPlaylistName)
                        showRenameDialog = false
                    }
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("save", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_save))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    showRenameDialog = false
                }) {
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

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = MaterialSymbolIcon("delete_forever", filled = true),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(context.getString(R.string.playlist_delete_title)) },
            text = { Text(context.getString(R.string.dialog_delete_playlist_message, playlist.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onDeletePlaylist()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("delete_forever", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.button_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    showDeleteDialog = false
                }) {
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

    // Bulk delete dialog
    if (showBulkDeleteDialog && selectedSongs.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = MaterialSymbolIcon("delete_sweep", filled = true),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(stringResource(R.string.playlist_remove_songs_count, selectedSongs.size)) },
            text = { Text(stringResource(R.string.playlist_remove_songs_confirm, selectedSongs.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        // Remove selected songs
                        selectedSongs.forEach { songId ->
                            playlist.songs.find { it.id == songId }?.let { song ->
                                onRemoveSong(song, context.getString(R.string.playlist_removed_from_playlist, song.title))
                            }
                        }
                        selectedSongs = emptySet()
                        isMultiSelectMode = false
                        showBulkDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("delete_sweep", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.content_desc_remove))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    showBulkDeleteDialog = false
                }) {
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

    // Export dialog
    if (showExportDialog && (onExportPlaylist != null || onExportPlaylistToCustomLocation != null)) {
        PlaylistExportDialog(
            playlistName = playlist.name,
            onDismiss = { showExportDialog = false },
            onExport = { format ->
                operationInProgress = "Exporting"
                showOperationProgress = true
                onExportPlaylist?.invoke(format)
                showExportDialog = false
            },
            onExportToCustomLocation = { format, directoryUri ->
                operationInProgress = "Exporting"
                showOperationProgress = true
                onExportPlaylistToCustomLocation?.invoke(format, directoryUri)
                showExportDialog = false
            }
        )
    }
    
    // Import dialog
    if (showImportDialog && onImportPlaylist != null) {
        PlaylistImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { uri, onResult, onRestartRequired ->
                operationInProgress = "Importing"
                showOperationProgress = true
                onImportPlaylist(uri, onResult, onRestartRequired)
                showImportDialog = false
            }
        )
    }
    
    // Operation progress dialog
    if (showOperationProgress) {
        PlaylistOperationProgressDialog(
            operation = operationInProgress,
            onDismiss = { /* Cannot dismiss during operation */ }
        )
    }
    
    // Operation result dialog
    operationResult?.let { (message, isError) ->
        PlaylistOperationResultDialog(
            title = if (isError) context.getString(R.string.playlist_operation_failed) else context.getString(R.string.playlist_operation_complete),
            message = message,
            isError = isError,
            onDismiss = { operationResult = null }
        )
    }
    
    // Song Options Bottom Sheet - matching search screen
    if (showSongOptionsSheet && selectedSongForOptions != null) {
        PlaylistSongOptionsBottomSheet(
            song = selectedSongForOptions!!,
            onDismiss = { showSongOptionsSheet = false },
            onShare = {
                onShare(selectedSongForOptions!!)
                showSongOptionsSheet = false
            },
            onRemoveFromPlaylist = {
                onRemoveSong(selectedSongForOptions!!, context.getString(R.string.playlist_removed_from_playlist, selectedSongForOptions!!.title))
                showSongOptionsSheet = false
            },
            onPlayNext = {
                onPlayNext(selectedSongForOptions!!)
                showSongOptionsSheet = false
                Toast.makeText(context, context.getString(R.string.will_play_next, selectedSongForOptions!!.title), Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = {
                onAddToQueue(selectedSongForOptions!!)
                showSongOptionsSheet = false
                Toast.makeText(context, context.getString(R.string.added_to_queue, selectedSongForOptions!!.title), Toast.LENGTH_SHORT).show()
            },
            onAddToPlaylist = {
                onAddToPlaylist(selectedSongForOptions!!)
                showSongOptionsSheet = false
            },
            onShowSongInfo = {
                selectedSongForInfo = selectedSongForOptions
                showSongInfo = true
                showSongOptionsSheet = false
            },
            onGoToAlbum = {
                onGoToAlbum(selectedSongForOptions!!)
                showSongOptionsSheet = false
            },
            onGoToArtist = {
                onGoToArtist(selectedSongForOptions!!)
                showSongOptionsSheet = false
            },
            showRemoveFromPlaylist = canEditPlaylist,
            haptics = haptics
        )
    }

    val availableSongs = remember(allSongs, playlist.songs) {
        val playlistSongIds = playlist.songs.map { it.id }.toSet()
        allSongs.filter { song -> song.id !in playlistSongIds }
    }

    if (showSongPicker) {
        SongPickerBottomSheet(
            targetPlaylist = playlist,
            availableSongs = availableSongs,
            onDismissRequest = { showSongPicker = false },
            onAddSongsToPlaylist = { songs ->
                operationInProgress = "Adding"
                showOperationProgress = true
                val (successCount, playlistName) = musicViewModel.addSongsToPlaylist(songs, playlist.id)
                showOperationProgress = false
                val message = when {
                    successCount == 0 -> "No songs added - they may already be in the playlist"
                    successCount == songs.size -> "Added $successCount songs to $playlistName"
                    else -> "Added $successCount of ${songs.size} songs to $playlistName"
                }
                operationResult = Pair(message, false)
                showSongPicker = false
            },
            sheetState = sheetState
        )
    }

    // Song Info Bottom Sheet
    if (showSongInfo && selectedSongForInfo != null) {
        SongInfoBottomSheet(
            song = selectedSongForInfo,
            onDismiss = {
                showSongInfo = false
                selectedSongForInfo = null
            },
            appSettings = appSettings,
            isStreamingMode = isStreamingPlaylist,
            onEditSong = { title, artist, album, genre, year, trackNumber, artworkUri, removeArtwork, albumArtist, composer, discNumber, onComplete ->
                pendingMetadataEditCompleteCallback = onComplete
                try {
                    musicViewModel.saveMetadataChanges(
                        song = selectedSongForInfo!!,
                        title = title,
                        artist = artist,
                        album = album,
                        genre = genre,
                        year = year,
                        trackNumber = trackNumber,
                        artworkUri = artworkUri,
                        removeArtwork = removeArtwork,
                        albumArtist = albumArtist,
                        composer = composer,
                        discNumber = discNumber,
                        onSuccess = { fileWriteSucceeded ->
                            if (fileWriteSucceeded) {
                                Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully_to, Toast.LENGTH_SHORT).show()
                            }
                            pendingMetadataEditCompleteCallback?.invoke(true)
                            pendingMetadataEditCompleteCallback = null
                        },
                        onError = { errorMessage ->
                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            pendingMetadataEditCompleteCallback?.invoke(false)
                            pendingMetadataEditCompleteCallback = null
                        },
                        onPermissionRequired = { pendingRequest ->
                            try {
                                val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                    pendingRequest.intentSender
                                ).build()
                                writePermissionLauncher.launch(intentSenderRequest)
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.failed_to_request_permission, e.message ?: ""), Toast.LENGTH_LONG).show()
                                musicViewModel.cancelPendingMetadataWrite()
                                pendingMetadataEditCompleteCallback?.invoke(false)
                                pendingMetadataEditCompleteCallback = null
                            }
                        }
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.unexpected_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                    android.util.Log.w("PlaylistDetailScreen", "Metadata update failed for song: ${selectedSongForInfo!!.title}", e)
                    pendingMetadataEditCompleteCallback?.invoke(false)
                    pendingMetadataEditCompleteCallback = null
                }
            },
            onShowLyricsEditor = { }
        )
    }

    CollapsibleHeaderScreen(
        title = playlist.name,
        showBackButton = true,
        onBackClick = {
            if (showSearchBar) {
                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                showSearchBar = false
                searchQuery = ""
            } else {
                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                onBack()
            }
        },
        actions = {
            // Sort button (only show if sorting is available)
            val isDefault = playlist.id == "1" || playlist.id == "2" || playlist.id == "3"
            if (isDefault || (onUpdatePlaylistSongs != null && playlist.songs.size > 1)) {
                val sortButtonScale by animateFloatAsState(
                    targetValue = if (showSortMenu) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "sortButtonScale"
                )
                
                FilledTonalButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        showSortMenu = true
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.graphicsLayer {
                        scaleX = sortButtonScale
                        scaleY = sortButtonScale
                    }
                ) {
                    Icon(
                        imageVector = RhythmIcons.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sort order text
                    val sortText = when (currentPlaylistSort) {
                        PlaylistSortOrder.TITLE_ASC, PlaylistSortOrder.TITLE_DESC -> "Title"
                        PlaylistSortOrder.ARTIST_ASC, PlaylistSortOrder.ARTIST_DESC -> "Artist"
                        PlaylistSortOrder.ALBUM_ASC, PlaylistSortOrder.ALBUM_DESC -> "Album"
                        PlaylistSortOrder.DURATION_ASC, PlaylistSortOrder.DURATION_DESC -> "Duration"
                        PlaylistSortOrder.DATE_ADDED_ASC, PlaylistSortOrder.DATE_ADDED_DESC -> "Date Added"
                    }

                    Text(
                        text = sortText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    val sortArrowIcon = when (currentPlaylistSort) {
                        PlaylistSortOrder.TITLE_ASC, PlaylistSortOrder.ARTIST_ASC, PlaylistSortOrder.ALBUM_ASC, 
                        PlaylistSortOrder.DURATION_ASC, PlaylistSortOrder.DATE_ADDED_ASC -> RhythmIcons.ArrowUpward
                        else -> RhythmIcons.ArrowDownward
                    }
                    
                    Icon(
                        imageVector = sortArrowIcon,
                        contentDescription = if (currentPlaylistSort.name.endsWith("_ASC")) "Ascending" else "Descending",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            if (canEditPlaylist) {
                FilledIconButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        showMenu = true
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = RhythmIcons.More,
                        contentDescription = context.getString(R.string.playlist_more_options),
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .widthIn(min = 220.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(5.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    // Reorder songs option
                    if (isDefault || (onReorderSongs != null && playlist.songs.isNotEmpty())) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isReorderMode) context.getString(R.string.playlist_done_reordering) else context.getString(R.string.playlist_reorder_songs),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isReorderMode) RhythmIcons.Check else MaterialSymbolIcon("reorder"),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    showMenu = false
                                    isReorderMode = !isReorderMode
                                    // Exit multi-select mode when entering reorder mode
                                    if (isReorderMode) {
                                        isMultiSelectMode = false
                                        selectedSongs = emptySet()
                                    }
                                }
                            )
                        }
                    }
                    
                    // Select songs option (multi-select mode)
                    if (playlist.songs.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isMultiSelectMode) "Cancel selection" else "Select songs",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isMultiSelectMode) RhythmIcons.Close else MaterialSymbolIcon("check_box"),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    showMenu = false
                                    isMultiSelectMode = !isMultiSelectMode
                                    // Exit reorder mode when entering multi-select mode
                                    if (isMultiSelectMode) {
                                        isReorderMode = false
                                    } else {
                                        selectedSongs = emptySet()
                                    }
                                }
                            )
                        }
                    }
                    
                    // Export playlist option
                    if (!isDefault && (onExportPlaylist != null || onExportPlaylistToCustomLocation != null)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Export playlist",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbolIcon("file_upload"),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    showMenu = false
                                    showExportDialog = true
                                }
                            )
                        }
                    }
                    
                    // Import playlist option
                    if (!isDefault && onImportPlaylist != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Import playlist",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Actions.Download,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    showMenu = false
                                    showImportDialog = true
                                }
                            )
                        }
                    }
                    
                    // Rename playlist option
                    if (!isDefault) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Rename playlist",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    showMenu = false
                                    newPlaylistName = playlist.name
                                    showRenameDialog = true
                                }
                            )
                        }
                    }
                    
                    // Delete playlist option
                    if (!isDefault) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete playlist",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    showMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
                
                // Sort menu dropdown
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .widthIn(min = 250.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(8.dp)
                ) {
                    val currentKey = when (currentPlaylistSort) {
                        PlaylistSortOrder.TITLE_ASC, PlaylistSortOrder.TITLE_DESC -> "TITLE"
                        PlaylistSortOrder.ARTIST_ASC, PlaylistSortOrder.ARTIST_DESC -> "ARTIST"
                        PlaylistSortOrder.ALBUM_ASC, PlaylistSortOrder.ALBUM_DESC -> "ALBUM"
                        PlaylistSortOrder.DURATION_ASC, PlaylistSortOrder.DURATION_DESC -> "DURATION"
                        PlaylistSortOrder.DATE_ADDED_ASC, PlaylistSortOrder.DATE_ADDED_DESC -> "DATE_ADDED"
                    }
                    val isAscending = when (currentPlaylistSort) {
                        PlaylistSortOrder.TITLE_ASC, PlaylistSortOrder.ARTIST_ASC, PlaylistSortOrder.ALBUM_ASC, PlaylistSortOrder.DURATION_ASC, PlaylistSortOrder.DATE_ADDED_ASC -> true
                        else -> false
                    }
                    
                    fun getPlaylistDetailSortOrder(key: String, asc: Boolean): PlaylistSortOrder {
                        return when (key) {
                            "TITLE" -> if (asc) PlaylistSortOrder.TITLE_ASC else PlaylistSortOrder.TITLE_DESC
                            "ARTIST" -> if (asc) PlaylistSortOrder.ARTIST_ASC else PlaylistSortOrder.ARTIST_DESC
                            "ALBUM" -> if (asc) PlaylistSortOrder.ALBUM_ASC else PlaylistSortOrder.ALBUM_DESC
                            "DURATION" -> if (asc) PlaylistSortOrder.DURATION_ASC else PlaylistSortOrder.DURATION_DESC
                            "DATE_ADDED" -> if (asc) PlaylistSortOrder.DATE_ADDED_ASC else PlaylistSortOrder.DATE_ADDED_DESC
                            else -> PlaylistSortOrder.TITLE_ASC
                        }
                    }
                    
                    val sortOptions = listOf(
                        RhythmSortOption("TITLE", context.getString(R.string.library_sort_title), RhythmIcons.SortByAlpha),
                        RhythmSortOption("ARTIST", context.getString(R.string.library_sort_artist), RhythmIcons.ArtistFilled),
                        RhythmSortOption("ALBUM", context.getString(R.string.library_sort_album), RhythmIcons.Music.Album),
                        RhythmSortOption("DURATION", context.getString(R.string.sort_duration), MaterialSymbolIcon("timer", filled = true)),
                        RhythmSortOption("DATE_ADDED", context.getString(R.string.library_sort_date_added), RhythmIcons.DateRange)
                    )
                    
                    RhythmSortMenuContent(
                        selectedKey = currentKey,
                        isAscending = isAscending,
                        options = sortOptions,
                        onKeySelected = { key ->
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            val newOrder = getPlaylistDetailSortOrder(key, isAscending)
                            currentPlaylistSort = newOrder
                            appSettings.setPlaylistDetailSortOrder(newOrder.name)
                            showSortMenu = false
                            val sortedSongs = when (newOrder) {
                                PlaylistSortOrder.TITLE_ASC -> playlist.songs.sortedBy { it.title.lowercase() }
                                PlaylistSortOrder.TITLE_DESC -> playlist.songs.sortedByDescending { it.title.lowercase() }
                                PlaylistSortOrder.ARTIST_ASC -> playlist.songs.sortedBy { it.artist.lowercase() }
                                PlaylistSortOrder.ARTIST_DESC -> playlist.songs.sortedByDescending { it.artist.lowercase() }
                                PlaylistSortOrder.ALBUM_ASC -> playlist.songs.sortedBy { it.album.lowercase() }
                                PlaylistSortOrder.ALBUM_DESC -> playlist.songs.sortedByDescending { it.album.lowercase() }
                                PlaylistSortOrder.DURATION_ASC -> playlist.songs.sortedBy { it.duration }
                                PlaylistSortOrder.DURATION_DESC -> playlist.songs.sortedByDescending { it.duration }
                                PlaylistSortOrder.DATE_ADDED_ASC -> playlist.songs.sortedBy { it.dateAdded }
                                PlaylistSortOrder.DATE_ADDED_DESC -> playlist.songs.sortedByDescending { it.dateAdded }
                            }
                            onUpdatePlaylistSongs?.invoke(sortedSongs)
                        },
                        onDirectionToggled = { asc ->
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            val newOrder = getPlaylistDetailSortOrder(currentKey, asc)
                            currentPlaylistSort = newOrder
                            appSettings.setPlaylistDetailSortOrder(newOrder.name)
                            showSortMenu = false
                            val sortedSongs = when (newOrder) {
                                PlaylistSortOrder.TITLE_ASC -> playlist.songs.sortedBy { it.title.lowercase() }
                                PlaylistSortOrder.TITLE_DESC -> playlist.songs.sortedByDescending { it.title.lowercase() }
                                PlaylistSortOrder.ARTIST_ASC -> playlist.songs.sortedBy { it.artist.lowercase() }
                                PlaylistSortOrder.ARTIST_DESC -> playlist.songs.sortedByDescending { it.artist.lowercase() }
                                PlaylistSortOrder.ALBUM_ASC -> playlist.songs.sortedBy { it.album.lowercase() }
                                PlaylistSortOrder.ALBUM_DESC -> playlist.songs.sortedByDescending { it.album.lowercase() }
                                PlaylistSortOrder.DURATION_ASC -> playlist.songs.sortedBy { it.duration }
                                PlaylistSortOrder.DURATION_DESC -> playlist.songs.sortedByDescending { it.duration }
                                PlaylistSortOrder.DATE_ADDED_ASC -> playlist.songs.sortedBy { it.dateAdded }
                                PlaylistSortOrder.DATE_ADDED_DESC -> playlist.songs.sortedByDescending { it.dateAdded }
                            }
                            onUpdatePlaylistSongs?.invoke(sortedSongs)
                        }
                    )
                }
            }
        },
        headerContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp)
                    .graphicsLayer { 
                        shadowElevation = 0f
                        clip = false
                    }
            ) {
                AnimatedVisibility(
                    visible = showSearchBar && !isTablet,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 300)
                    ),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 200)
                    )
                ) {
                    SettingsSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        focusRequester = searchFocusRequester,
                        hint = "Find a track in this playlist"
                    )
                }
                
                // Button Group for Play All and Shuffle - Sticky (hidden when empty)
                AnimatedVisibility(
                    visible = playlist.songs.isNotEmpty() && !isTablet,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 300)
                    ),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 200)
                    )
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Play All Button - Equal sizing with text
                    RhythmDetailActionButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            onPlayAll()
                        },
                        isFirst = true,
                        isLast = false,
                        icon = RhythmIcons.Play,
                        text = "Play All",
                        fontWeight = FontWeight.Bold
                    )
                    
                    RhythmDetailActionButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            onShufflePlay()
                        },
                        type = RhythmButtonType.Tonal,
                        isFirst = false,
                        isLast = true,
                        icon = RhythmIcons.Shuffle,
                        text = stringResource(R.string.action_shuffle),
                        fontWeight = FontWeight.Medium
                    )
                }
                }
            }
        }
    ) { modifier ->
        if (isLandscapeTablet) {
            // Tablet split-view layout: Left side (art + controls), Right side (song list)
            Box(modifier = modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Playlist Art and Controls
                Column(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 48.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val context = LocalContext.current

                    // Playlist artwork
                    val playlistArtSize = 180.dp
                    Surface(
                        modifier = Modifier.size(playlistArtSize),
                        shape = rememberExpressiveShapeFor(
                            ExpressiveShapeTarget.PLAYLIST_ART,
                            fallbackShape = RoundedCornerShape(32.dp)
                        ),
                        tonalElevation = 8.dp,
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (playlist.artworkUri != null) {
                                M3ImageUtils.PlaylistImage(
                                    imageUrl = playlist.artworkUri,
                                    playlistName = playlist.name,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(32.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.PlaylistFilled,
                                        contentDescription = null,
                                        modifier = Modifier.size(90.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Playlist info
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Action buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (playlist.songs.isNotEmpty()) {
                                // Play All button
                                RhythmDetailActionButtonFullWidth(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        onPlayAll()
                                    },
                                    icon = RhythmIcons.Play,
                                    text = "Play All",
                                    fontWeight = FontWeight.Normal
                                )

                                // Shuffle button
                                RhythmDetailActionButtonFullWidth(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        onShufflePlay()
                                    },
                                    type = RhythmButtonType.Tonal,
                                    icon = RhythmIcons.Shuffle,
                                    iconSize = 24.dp,
                                    text = stringResource(R.string.action_shuffle),
                                    fontWeight = FontWeight.Normal,
                                    contentDescription = stringResource(R.string.content_desc_shuffle_play)
                                )
                            }

                            // Add Songs button
                            FilledTonalButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    showSongPicker = true
                                },
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Add Songs",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            // Search button
                            FilledTonalButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    showSearchBar = !showSearchBar
                                    if (!showSearchBar) {
                                        searchQuery = ""
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (showSearchBar)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = if (showSearchBar)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (showSearchBar) "Searching" else "Search",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }

                // Right Column: Song List with Search
                Box(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    val filteredSongs = remember(playlist.songs, searchQuery) {
                        if (searchQuery.isBlank()) {
                            playlist.songs
                        } else {
                            playlist.songs.filter { song ->
                                song.title.contains(searchQuery, ignoreCase = true) ||
                                        song.artist.contains(searchQuery, ignoreCase = true) ||
                                        song.album.contains(searchQuery, ignoreCase = true)
                            }
                        }
                    }

                    val filteredSongsWithIndices = remember(playlist.songs, searchQuery) {
                        playlist.songs.mapIndexedNotNull { sourceIndex, song ->
                            val matches = searchQuery.isBlank() ||
                                song.title.contains(searchQuery, ignoreCase = true) ||
                                song.artist.contains(searchQuery, ignoreCase = true) ||
                                song.album.contains(searchQuery, ignoreCase = true)
                            if (matches) sourceIndex to song else null
                        }
                    }

                    val listState = rememberLazyListState()

                    LaunchedEffect(showSearchBar) {
                        if (showSearchBar) {
                            listState.animateScrollToItem(0)
                        }
                    }

                    val canScroll by remember(listState) { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            end = if (canScroll && !isReorderMode) 28.dp else 0.dp,
                            top = 16.dp,
                            bottom = 20.dp
                        )
                    ) {
                        // Search field for tablet
                        item {
                            Column {
                            AnimatedVisibility(
                                visible = showSearchBar,
                                enter = expandVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeIn(
                                    animationSpec = tween(durationMillis = 300)
                                ),
                                exit = shrinkVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeOut(
                                    animationSpec = tween(durationMillis = 200)
                                )
                            ) {
                                SettingsSearchBar(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                    focusRequester = searchFocusRequester,
                                    hint = "Find a track in this playlist"
                                )
                            }
                        }
                    }

                        // Song count and total time header
                        if (filteredSongs.isNotEmpty()) {
                            item {
                                val totalDurationMs = filteredSongs.sumOf { it.duration }
                                val durationSeconds = totalDurationMs / 1000
                                val hours = durationSeconds / 3600
                                val minutes = (durationSeconds % 3600) / 60
                                val timeText = when {
                                    hours > 0 && minutes > 0 -> "$hours hr $minutes mins"
                                    hours > 0 -> "$hours hr"
                                    else -> "$minutes mins"
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (filteredSongs.size == 1) "1 song • $timeText" else "${filteredSongs.size} songs • $timeText",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }

                        // Empty state
                        if (filteredSongs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillParentMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        modifier = Modifier.size(80.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        tonalElevation = 4.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = RhythmIcons.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = if (searchQuery.isNotEmpty()) context.getString(R.string.nav_no_matching_songs) else context.getString(R.string.playlist_no_songs_yet),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                }
                            }
                        } else {
                            // Song items
                            if (isReorderMode && filteredSongsWithIndices.isNotEmpty()) {
                                item(key = "playlist_reorder_drag_tablet") {
                                    val reorderListState = rememberLazyListState()
                                    DragDropLazyColumn(
                                        items = filteredSongsWithIndices,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillParentMaxHeight(),
                                        lazyListState = reorderListState,
                                        onMove = { fromIndex, toIndex ->
                                            val actualFromIndex = filteredSongsWithIndices[fromIndex].first
                                            val actualToIndex = filteredSongsWithIndices[toIndex].first
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                            onReorderSongs?.invoke(actualFromIndex, actualToIndex)
                                        },
                                        itemKey = { item -> "${item.first}_${item.second.id}" }
                                    ) { indexedSong, isDragging, displayIndex ->
                                        val song = indexedSong.second
                                        PlaylistSongItem(
                                            song = song,
                                            onClick = { },
                                            onRemove = { message -> onRemoveSong(song, message) },
                                            currentSong = currentSong,
                                            isPlaying = isPlaying,
                                            useHoursFormat = useHoursFormat,
                                            isReorderMode = true,
                                            isDragging = isDragging,
                                            index = displayIndex,
                                            totalCount = filteredSongsWithIndices.size,
                                            onMoveUp = null,
                                            onMoveDown = null,
                                            isMultiSelectMode = false,
                                            isSelected = false,
                                            onMoreClick = null
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(filteredSongs, key = { index, song -> "${song.id}-$index" }) { index, song ->
                                    AnimateIn {
                                        PlaylistSongItem(
                                            song = song,
                                            onClick = {
                                                if (isMultiSelectMode) {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                    selectedSongs = if (selectedSongs.contains(song.id)) {
                                                        selectedSongs - song.id
                                                    } else {
                                                        selectedSongs + song.id
                                                    }
                                                    return@PlaylistSongItem
                                                }
                                                if (isReorderMode) {
                                                    return@PlaylistSongItem
                                                }
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                when (playlistClickBehavior) {
                                                    "play_all" -> {
                                                        onPlaySongFromPlaylist?.invoke(song, playlist.songs) ?: onSongClick(song)
                                                    }
                                                    "play_one" -> {
                                                        onSongClick(song)
                                                    }
                                                    else -> {
                                                        selectedSongForQueue = song
                                                        showQueueOptionsDialog = true
                                                    }
                                                }
                                            },
                                            onRemove = { message -> onRemoveSong(song, message) },
                                            currentSong = currentSong,
                                            isPlaying = isPlaying,
                                            useHoursFormat = useHoursFormat,
                                            isReorderMode = isReorderMode,
                                            index = index,
                                            totalCount = filteredSongs.size,
                                            onMoveUp = if (isReorderMode && index > 0) {
                                                {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                    onReorderSongs?.invoke(index, index - 1)
                                                }
                                            } else null,
                                            onMoveDown = if (isReorderMode && index < filteredSongs.size - 1) {
                                                {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                    onReorderSongs?.invoke(index, index + 1)
                                                }
                                            } else null,
                                            isMultiSelectMode = isMultiSelectMode,
                                            isSelected = selectedSongs.contains(song.id),
                                            onMoreClick = {
                                                selectedSongForOptions = song
                                                showSongOptionsSheet = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    if (!isReorderMode && filteredSongs.isNotEmpty()) {
                        val playlistDetailFastScrollLabelProvider = remember(filteredSongs, currentPlaylistSort) {
                            { index: Int ->
                                playlistDetailFastScrollLabel(
                                    song = filteredSongs.getOrNull(index),
                                    sortOrder = currentPlaylistSort
                                )
                            }
                        }
                        ExpressiveScrollBar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 16.dp, bottom = 20.dp),
                            listState = listState,
                            dragLabelProvider = playlistDetailFastScrollLabelProvider
                        )
                    }
                }
            }
            
            // Floating action pill (tablet) - always visible above miniplayer
            if (playlist.songs.isNotEmpty() && !isTablet) {
                // The NavHost already applies LocalMiniPlayerPadding to its content,
                // and CollapsibleHeaderScreen's Scaffold handles nav bar insets.
                // The pill's parent Box bottom edge already sits above the mini player.
                // We only need a small gap so it doesn't touch the very bottom.
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = LocalMiniPlayerPadding.current.calculateBottomPadding()),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isStreamingPlaylist) {
                            var searchPressed by remember { mutableStateOf(false) }
                            val searchScale by animateFloatAsState(
                                targetValue = if (searchPressed) 0.96f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "searchScale"
                            )
                            LaunchedEffect(searchPressed) {
                                if (searchPressed) {
                                    delay(100)
                                    searchPressed = false
                                }
                            }
                            Button(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showSearchBar = !showSearchBar
                                    if (!showSearchBar) {
                                        searchQuery = ""
                                    }
                                    searchPressed = true
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .graphicsLayer {
                                        scaleX = searchScale
                                        scaleY = searchScale
                                    },
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (showSearchBar)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = if (showSearchBar)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            ) {
                                Icon(
                                    imageVector = if (showSearchBar) RhythmIcons.Close else RhythmIcons.Search,
                                    contentDescription = if (showSearchBar) "Close search" else "Search",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (showSearchBar) "Searching" else "Search",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // Left side - Search button
                            var searchPressed by remember { mutableStateOf(false) }
                            val searchScale by animateFloatAsState(
                                targetValue = if (searchPressed) 0.92f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "searchScale"
                            )
                            LaunchedEffect(searchPressed) {
                                if (searchPressed) {
                                    delay(100)
                                    searchPressed = false
                                }
                            }
                            FilledIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showSearchBar = !showSearchBar
                                    if (!showSearchBar) {
                                        searchQuery = ""
                                    }
                                    searchPressed = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer {
                                        scaleX = searchScale
                                        scaleY = searchScale
                                    },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (showSearchBar)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = if (showSearchBar)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = if (showSearchBar) RhythmIcons.Close else RhythmIcons.Search,
                                    contentDescription = if (showSearchBar) "Close search" else "Search",
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Center - Add Songs button (Primary)
                            var addPressed by remember { mutableStateOf(false) }
                            val addScale by animateFloatAsState(
                                targetValue = if (addPressed) 0.94f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "addScale"
                            )
                            LaunchedEffect(addPressed) {
                                if (addPressed) {
                                    delay(120)
                                    addPressed = false
                                }
                            }
                            Button(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    showSongPicker = true
                                    addPressed = true
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .widthIn(min = 120.dp)
                                    .graphicsLayer {
                                        scaleX = addScale
                                        scaleY = addScale
                                    },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.button_add),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Right side - Select button
                            var selectPressed by remember { mutableStateOf(false) }
                            val selectScale by animateFloatAsState(
                                targetValue = if (selectPressed) 0.92f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "selectScale"
                            )
                            LaunchedEffect(selectPressed) {
                                if (selectPressed) {
                                    delay(100)
                                    selectPressed = false
                                }
                            }
                            FilledIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    isMultiSelectMode = !isMultiSelectMode
                                    if (isMultiSelectMode) {
                                        isReorderMode = false
                                    } else {
                                        selectedSongs = emptySet()
                                    }
                                    selectPressed = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer {
                                        scaleX = selectScale
                                        scaleY = selectScale
                                    },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isMultiSelectMode)
                                        MaterialTheme.colorScheme.errorContainer
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (isMultiSelectMode)
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = if (isMultiSelectMode) RhythmIcons.Close else MaterialSymbolIcon("check_box"),
                                    contentDescription = if (isMultiSelectMode) "Cancel selection" else "Select songs",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
            }
        } else {
            // Phone/Compact layout: Original vertical layout
            Box(modifier = modifier.fillMaxSize()) {
            val filteredSongs = remember(playlist.songs, searchQuery) {
                if (searchQuery.isBlank()) {
                    playlist.songs
                } else {
                    playlist.songs.filter { song ->
                        song.title.contains(searchQuery, ignoreCase = true) ||
                                song.artist.contains(searchQuery, ignoreCase = true) ||
                                song.album.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            val filteredSongsWithIndices = remember(playlist.songs, searchQuery) {
                playlist.songs.mapIndexedNotNull { sourceIndex, song ->
                    val matches = searchQuery.isBlank() ||
                        song.title.contains(searchQuery, ignoreCase = true) ||
                        song.artist.contains(searchQuery, ignoreCase = true) ||
                        song.album.contains(searchQuery, ignoreCase = true)
                    if (matches) sourceIndex to song else null
                }
            }

            val listState = rememberLazyListState()

            LaunchedEffect(showSearchBar) {
                if (showSearchBar) {
                    listState.animateScrollToItem(0) // Scroll to the top to show the search bar
                }
            }

            val canScroll by remember(listState) { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }

            // LazyColumn - placed first so sticky header appears on top
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        end = if (canScroll && !isReorderMode) 28.dp else 16.dp
                    ),
                contentPadding = PaddingValues(
//                    top = if (playlist.songs.isNotEmpty()) 90.dp else 16.dp,
                    bottom = (LocalMiniPlayerPadding.current.calculateBottomPadding() + 20.dp).coerceAtLeast(120.dp)
                )
            ) {

                // Song count and total time header
                if (filteredSongs.isNotEmpty()) {
                    item {
                        val totalDurationMs = filteredSongs.sumOf { it.duration }
                        val durationSeconds = totalDurationMs / 1000
                        val hours = durationSeconds / 3600
                        val minutes = (durationSeconds % 3600) / 60
                        val timeText = when {
                            hours > 0 && minutes > 0 -> "$hours hr $minutes mins"
                            hours > 0 -> "$hours hr"
                            else -> "$minutes mins"
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (filteredSongs.size == 1) "1 song • $timeText" else "${filteredSongs.size} songs • $timeText",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Songs list
                if (filteredSongs.isEmpty()) {
                    item { // Enhanced empty state with better visual design
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Empty state icon
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 4.dp
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Empty state text
                            Text(
                                text = if (searchQuery.isNotEmpty()) context.getString(R.string.nav_no_matching_songs) else context.getString(R.string.playlist_no_songs_yet),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (searchQuery.isNotEmpty()) "Try a different search query" else "Start building your playlist by adding some songs",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Call-to-action button
                            Button(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    showSongPicker = true
                                },
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Add Songs",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        }
                    }
                } else {
                    // Multi-select mode banner
                    if (isMultiSelectMode) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                color = Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Select All button
                                        TextButton(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                if (selectedSongs.size == filteredSongs.size) {
                                                    selectedSongs = emptySet()
                                                } else {
                                                    selectedSongs = filteredSongs.map { it.id }.toSet()
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selectedSongs.size == filteredSongs.size) MaterialSymbolIcon("check_box") else MaterialSymbolIcon("check_box_outline_blank"),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                if (selectedSongs.size == filteredSongs.size) "${selectedSongs.size} selected" else "${selectedSongs.size} selected"
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {

                                        // Delete Selected button
                                        if (selectedSongs.isNotEmpty()) {
                                            Button(
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                    showBulkDeleteDialog = true
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = MaterialSymbolIcon("delete_sweep"),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.content_desc_remove))
                                            }
                                        }
                                        // Done button
                                        Button(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                                isMultiSelectMode = false
                                                selectedSongs = emptySet()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = RhythmIcons.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.ui_done))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Reorder mode banner
                    if (isReorderMode) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                color = Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = MaterialSymbolIcon("reorder"),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.playlist_reorder_songs_title),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // Done button
                                    Button(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            isReorderMode = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.ui_done))
                                    }
                                }
                            }
                        }
                    }
                    
                    if (isReorderMode && filteredSongsWithIndices.isNotEmpty()) {
                        item(key = "playlist_reorder_drag_phone") {
                            val reorderListState = rememberLazyListState()
                            DragDropLazyColumn(
                                items = filteredSongsWithIndices,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillParentMaxHeight(),
                                lazyListState = reorderListState,
                                onMove = { fromIndex, toIndex ->
                                    val actualFromIndex = filteredSongsWithIndices[fromIndex].first
                                    val actualToIndex = filteredSongsWithIndices[toIndex].first
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    onReorderSongs?.invoke(actualFromIndex, actualToIndex)
                                },
                                itemKey = { item -> "${item.first}_${item.second.id}" }
                            ) { indexedSong, isDragging, displayIndex ->
                                val song = indexedSong.second
                                PlaylistSongItem(
                                    song = song,
                                    onClick = { },
                                    onRemove = { message -> onRemoveSong(song, message) },
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    useHoursFormat = useHoursFormat,
                                    isReorderMode = true,
                                    isDragging = isDragging,
                                    index = displayIndex,
                                    totalCount = filteredSongsWithIndices.size,
                                    onMoveUp = null,
                                    onMoveDown = null,
                                    isMultiSelectMode = false,
                                    isSelected = false,
                                    onMoreClick = null
                                )
                            }
                        }
                    } else {
                        itemsIndexed(filteredSongs, key = { index, song -> "${song.id}-$index" }) { index, song ->
                            AnimateIn {
                                PlaylistSongItem(
                                    song = song,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            // Toggle selection
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                            selectedSongs = if (selectedSongs.contains(song.id)) {
                                                selectedSongs - song.id
                                            } else {
                                                selectedSongs + song.id
                                            }
                                            return@PlaylistSongItem
                                        }
                                        if (isReorderMode) {
                                            // Don't play in reorder mode
                                            return@PlaylistSongItem
                                        }
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        when (playlistClickBehavior) {
                                            "play_all" -> {
                                                // Load entire playlist and play from selected song
                                                onPlaySongFromPlaylist?.invoke(song, playlist.songs) ?: onSongClick(song)
                                            }
                                            "play_one" -> {
                                                // Play only this song
                                                onSongClick(song)
                                            }
                                            else -> {
                                                // "ask" - Show dialog
                                                selectedSongForQueue = song
                                                showQueueOptionsDialog = true
                                            }
                                        }
                                    },
                                    onRemove = { message -> onRemoveSong(song, message) },
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    useHoursFormat = useHoursFormat,
                                    isReorderMode = isReorderMode,
                                    index = index,
                                    totalCount = filteredSongs.size,
                                    onMoveUp = if (isReorderMode && index > 0) {
                                        {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                            onReorderSongs?.invoke(index, index - 1)
                                        }
                                    } else null,
                                    onMoveDown = if (isReorderMode && index < filteredSongs.size - 1) {
                                        {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                            onReorderSongs?.invoke(index, index + 1)
                                        }
                                    } else null,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isSelected = selectedSongs.contains(song.id),
                                    onMoreClick = {
                                        selectedSongForOptions = song
                                        showSongOptionsSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
                item { // Extra bottom space for mini player
                    Spacer(modifier = Modifier.height(16.dp)) // Simple spacing
                }
            }
            
            // Floating action pill (phone) - always visible, sits above the miniplayer
            if (playlist.songs.isNotEmpty()) {
                // The NavHost already applies LocalMiniPlayerPadding to its content,
                // and CollapsibleHeaderScreen's Scaffold handles nav bar insets.
                // The pill's parent Box bottom edge already sits correctly above the mini player.
                // We only need a small visual gap from the bottom edge.
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = LocalMiniPlayerPadding.current.calculateBottomPadding()),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isStreamingPlaylist) {
                            var searchPressed by remember { mutableStateOf(false) }
                            val searchScale by animateFloatAsState(
                                targetValue = if (searchPressed) 0.96f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "searchScale"
                            )
                            LaunchedEffect(searchPressed) {
                                if (searchPressed) {
                                    delay(120)
                                    searchPressed = false
                                }
                            }
                            Button(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showSearchBar = !showSearchBar
                                    if (!showSearchBar) {
                                        searchQuery = ""
                                    }
                                    searchPressed = true
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .graphicsLayer {
                                        scaleX = searchScale
                                        scaleY = searchScale
                                    },
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (showSearchBar)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = if (showSearchBar)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            ) {
                                Icon(
                                    imageVector = if (showSearchBar) RhythmIcons.Close else RhythmIcons.Search,
                                    contentDescription = if (showSearchBar) "Close search" else "Search",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (showSearchBar) "Searching" else "Search",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // Left: Select/Remove button
                            var removePressed by remember { mutableStateOf(false) }
                            val removeScale by animateFloatAsState(
                                targetValue = if (removePressed) 0.88f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "removeScale"
                            )
                            LaunchedEffect(removePressed) {
                                if (removePressed) {
                                    delay(120)
                                    removePressed = false
                                }
                            }
                            FilledIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    isMultiSelectMode = !isMultiSelectMode
                                    if (isMultiSelectMode) {
                                        isReorderMode = false
                                    } else {
                                        selectedSongs = emptySet()
                                    }
                                    removePressed = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer {
                                        scaleX = removeScale
                                        scaleY = removeScale
                                    },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isMultiSelectMode)
                                        MaterialTheme.colorScheme.errorContainer
                                    else
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (isMultiSelectMode)
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = if (isMultiSelectMode) RhythmIcons.Close else MaterialSymbolIcon("delete_sweep"),
                                    contentDescription = if (isMultiSelectMode) "Cancel" else "Remove",
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Center: Add button (Primary pill)
                            var addPressed by remember { mutableStateOf(false) }
                            val addScale by animateFloatAsState(
                                targetValue = if (addPressed) 0.94f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "addScale"
                            )
                            LaunchedEffect(addPressed) {
                                if (addPressed) {
                                    delay(120)
                                    addPressed = false
                                }
                            }
                            Button(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    showSongPicker = true
                                    addPressed = true
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .widthIn(min = 130.dp)
                                    .graphicsLayer {
                                        scaleX = addScale
                                        scaleY = addScale
                                    },
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.content_desc_add_songs),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Right: Search button
                            var searchPressed by remember { mutableStateOf(false) }
                            val searchScale by animateFloatAsState(
                                targetValue = if (searchPressed) 0.88f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "searchScale"
                            )
                            LaunchedEffect(searchPressed) {
                                if (searchPressed) {
                                    delay(120)
                                    searchPressed = false
                                }
                            }
                            FilledIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showSearchBar = !showSearchBar
                                    if (!showSearchBar) {
                                        searchQuery = ""
                                    }
                                    searchPressed = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer {
                                        scaleX = searchScale
                                        scaleY = searchScale
                                    },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (showSearchBar)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = if (showSearchBar)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = if (showSearchBar) RhythmIcons.Close else RhythmIcons.Search,
                                    contentDescription = if (showSearchBar) "Close search" else "Search",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            if (!isReorderMode && filteredSongs.isNotEmpty()) {
                val playlistDetailFastScrollLabelProvider = remember(filteredSongs, currentPlaylistSort) {
                    { index: Int ->
                        playlistDetailFastScrollLabel(
                            song = filteredSongs.getOrNull(index),
                            sortOrder = currentPlaylistSort
                        )
                    }
                }
                ExpressiveScrollBar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            end = 4.dp,
                            top = 16.dp,
                            bottom = (LocalMiniPlayerPadding.current.calculateBottomPadding() + 20.dp).coerceAtLeast(120.dp)
                        ),
                    listState = listState,
                    dragLabelProvider = playlistDetailFastScrollLabelProvider
                )
            }
            }
        }
    }
}

@Composable
private fun AnimateIn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 50),
        label = "alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Box(
        modifier = modifier.graphicsLayer(
            alpha = alpha,
            scaleX = scale,
            scaleY = scale
        )
    ) {
        content()
    }
}

@Composable
fun PlaylistSongItem(
    song: Song,
    onClick: () -> Unit,
    onRemove: ((String) -> Unit)? = null,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    useHoursFormat: Boolean = false,
    isReorderMode: Boolean = false,
    isDragging: Boolean = false,
    index: Int = 0,
    totalCount: Int = 0,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onMoreClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showRemoveDialog by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current // Capture haptics here
    val isCurrentSong = currentSong?.id == song.id
    
    // Animated colors for current song
    val titleColor by animateColorAsState(
        targetValue = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "titleColor"
    )
    val artistColor by animateColorAsState(
        targetValue = if (isCurrentSong) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        animationSpec = tween(300),
        label = "artistColor"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isCurrentSong) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        animationSpec = tween(300),
        label = "containerColor"
    )
    
    // Remove confirmation dialog (only show if onRemove is provided)
    if (showRemoveDialog && onRemove != null) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            icon = {
                Icon(
                    imageVector = MaterialSymbolIcon("remove_circle_outline", filled = true),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(stringResource(R.string.playlist_remove_song)) },
            text = { Text(stringResource(R.string.playlist_remove_song_confirm, song.title)) },
            confirmButton = {
                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT) // Use captured haptics
                        onRemove(context.getString(R.string.playlist_removed_from_playlist, song.title))
                        showRemoveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("remove_circle_outline", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.content_desc_remove))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT) // Use captured haptics
                    showRemoveDialog = false
                }) {
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
    
    // Update container color for selection
    val selectionContainerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) 
                      else if (isCurrentSong) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) 
                      else MaterialTheme.colorScheme.surface,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "selectionContainerColor"
    )
    
    // Add scale animation for reorder and select modes
    val itemScale by animateFloatAsState(
        targetValue = if (isDragging) 1.005f else if (isReorderMode || isMultiSelectMode) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "itemScale"
    )
    
    // Keep drag state visually flat (no lift/shadow effect while dragging)
    val itemElevation by animateDpAsState(
        targetValue = if (isDragging) 0.dp else if (isReorderMode) 1.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "itemElevation"
    )
    
    Surface(
        onClick = onClick,
        color = selectionContainerColor,
        shape = groupedPlaylistDetailItemShape(index, totalCount),
        tonalElevation = itemElevation,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox for multi-select mode
            AnimatedVisibility(
                visible = isMultiSelectMode,
                enter = expandHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = shrinkHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut()
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            
            // Enhanced album art with expressive shape support
            Box {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = rememberExpressiveShapeFor(
                        ExpressiveShapeTarget.SONG_ART,
                        fallbackShape = RoundedCornerShape(12.dp)
                    ),
                    tonalElevation = 4.dp,
                    border = if (isCurrentSong) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null
                ) {
                    M3ImageUtils.TrackImage(
                        imageUrl = song.artworkUri,
                        trackName = song.title,
                        modifier = Modifier.fillMaxSize(),
                        applyExpressiveShape = false
                    )
                }
                if (isCurrentSong && isPlaying) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .offset(x = 4.dp, y = 4.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 0.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayingEqIcon(
                                modifier = Modifier.size(width = 12.dp, height = 10.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                isPlaying = isPlaying,
                                bars = 3
                            )
                        }
                    }
                }
            }
            
            // Enhanced song info with better typography
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = titleColor
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = artistColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (song.album.isNotEmpty() && song.album != song.artist) {
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Duration display (hide in reorder mode to make room for buttons)
            if (song.duration > 0 && !isReorderMode) {
                Text(
                    text = formatDuration(song.duration, useHoursFormat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            
            // Show reorder buttons, remove button, or 3-dot menu depending on mode
            if (isReorderMode) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDragging) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    tonalElevation = if (isDragging) 0.dp else 1.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = RhythmIcons.DragHandle,
                            contentDescription = stringResource(R.string.content_desc_drag_reorder),
                            tint = if (isDragging) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else if (isMultiSelectMode && onRemove != null) {
                // Remove button only shown in multi-select mode
                FilledIconButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        showRemoveDialog = true
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Remove,
                        contentDescription = stringResource(R.string.cd_remove_from_playlist),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                // 3-dot menu button matching SearchSongItem style
                FilledIconButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onMoreClick?.invoke()
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = RhythmIcons.More,
                        contentDescription = stringResource(R.string.content_desc_more_options),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun groupedPlaylistDetailItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 6.dp,
            bottomEnd = 6.dp
        )
        index == totalCount - 1 -> RoundedCornerShape(
            topStart = 6.dp,
            topEnd = 6.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )
        else -> RoundedCornerShape(6.dp)
    }
}
