/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
package chromahub.rhythm.app.features.local.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.util.ArtistSeparator
import chromahub.rhythm.app.util.NaturalSortComparator

import kotlin.math.abs

import android.widget.Toast
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import kotlin.collections.sortedBy
import kotlin.collections.mutableListOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSortMenuContent
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSongMenuContent
import chromahub.rhythm.app.shared.presentation.components.common.RhythmSortOption
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveScrollBar
import chromahub.rhythm.app.shared.presentation.components.common.songFastScrollLabel
import chromahub.rhythm.app.shared.presentation.components.common.albumFastScrollLabel
import chromahub.rhythm.app.shared.presentation.components.common.artistFastScrollLabel
import chromahub.rhythm.app.shared.presentation.components.common.playlistFastScrollLabel
import android.net.Uri
import android.util.Log
import chromahub.rhythm.app.util.PlaylistImportExportUtils
import chromahub.rhythm.app.util.AppRestarter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import chromahub.rhythm.app.ui.UiConstants
import chromahub.rhythm.app.ui.theme.MusicDimensions
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.model.findAlbumForSong
import chromahub.rhythm.app.shared.data.model.AlbumViewType
import chromahub.rhythm.app.shared.data.model.ArtistViewType
import chromahub.rhythm.app.shared.data.model.PlaylistViewType
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AddToPlaylistBottomSheet
import chromahub.rhythm.app.shared.presentation.components.dialogs.CreatePlaylistDialog
import chromahub.rhythm.app.shared.presentation.components.player.MiniPlayer
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.M3CircularLoader
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveFilledButton
import chromahub.rhythm.app.shared.presentation.theme.ExpressiveMaterialShape
import chromahub.rhythm.app.shared.presentation.theme.rememberExpressiveShape
import chromahub.rhythm.app.shared.presentation.components.dialogs.BulkPlaylistExportDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistImportDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationProgressDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationResultDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.AppRestartDialog
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongInfoBottomSheet

import chromahub.rhythm.app.features.local.presentation.components.settings.LibraryTabOrderBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.BatchEditTagsSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.MultiSelectionBottomSheet
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.M3ImageUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.data.model.ScanPhase
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.zIndex
import chromahub.rhythm.app.shared.presentation.components.player.PlayingEqIcon
import chromahub.rhythm.app.shared.presentation.components.common.ContentLoadingIndicator
import chromahub.rhythm.app.shared.presentation.components.common.DataProcessingLoader
import chromahub.rhythm.app.shared.presentation.components.common.AlphabetBar
import chromahub.rhythm.app.shared.presentation.components.common.ScrollToTopButton
import chromahub.rhythm.app.shared.presentation.components.common.TabAnimation
import chromahub.rhythm.app.util.AudioFormatDetector
import chromahub.rhythm.app.util.AudioQualityDetector
import chromahub.rhythm.app.shared.presentation.components.common.ActionProgressLoader
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.ButtonGroupStyle
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveElevation
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveGroupButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveFilledIconButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import chromahub.rhythm.app.util.windowScreenWidthDp
import chromahub.rhythm.app.util.windowScreenHeightDp

private fun LazyListState.shouldShowScrollbar(): Boolean {
    val layoutInfo = this.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return false
    val totalItems = layoutInfo.totalItemsCount
    if (visibleItems.size < totalItems) return true
    val lastItem = visibleItems.last()
    val lastItemBottom = lastItem.offset + lastItem.size
    return lastItemBottom > layoutInfo.viewportEndOffset - 80
}

private fun LazyGridState.shouldShowScrollbar(): Boolean {
    val layoutInfo = this.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return false
    val totalItems = layoutInfo.totalItemsCount
    if (visibleItems.size < totalItems) return true
    val lastItem = visibleItems.last()
    val lastItemBottom = lastItem.offset.y + lastItem.size.height
    return lastItemBottom > layoutInfo.viewportEndOffset - 120
}

enum class LibraryTab { SONGS, PLAYLISTS, ALBUMS, ARTISTS, EXPLORER }

enum class LibraryPlaylistSortOrder {
    NAME_ASC,
    NAME_DESC,
    DATE_CREATED_ASC,
    DATE_CREATED_DESC,
    SONG_COUNT_ASC,
    SONG_COUNT_DESC
}

/**
 * Main library surface with tabbed browsing, playback actions, and library management controls.
 */
@Composable
fun LibraryScreen(
    songs: List<Song>,
    albums: List<Album>,
    playlists: List<Playlist>,
    artists: List<Artist>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onPlayPause: () -> Unit,
    onPlayerClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onAddPlaylist: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumShufflePlay: (Album) -> Unit = { _ -> },
    onPlayQueue: (List<Song>) -> Unit = { _ -> },
    onPlayQueueFromIndex: (List<Song>, Int) -> Unit = { _, _ -> },
    onShuffleQueue: (List<Song>) -> Unit = { _ -> },
    onAlbumBottomSheetClick: (Album) -> Unit = { _ -> },
    onSort: () -> Unit = {},
    onRefreshClick: () -> Unit,
    onAddSongToPlaylist: (Song, String) -> Unit = { _, _ -> },
    onCreatePlaylist: (String) -> Unit = { _ -> },
    sortOrder: MusicViewModel.SortOrder = MusicViewModel.SortOrder.TITLE_ASC,
    onSkipNext: () -> Unit = {},
    onAddToQueue: (Song) -> Unit,
    initialTab: LibraryTab = LibraryTab.SONGS,
    musicViewModel: MusicViewModel,
    onExportAllPlaylists: ((PlaylistImportExportUtils.PlaylistExportFormat, Boolean, Uri?, (Result<String>) -> Unit) -> Unit)? = null,
    onImportPlaylist: ((Uri, (Result<String>) -> Unit, (() -> Unit)?) -> Unit)? = null,
    onRestartApp: (() -> Unit)? = null,
    onNavigateToArtist: (Artist) -> Unit = {},
    isStreamingMode: Boolean = false,
    streamingServiceName: String = "",
    streamingServiceConnected: Boolean = true,
    streamingIsLoading: Boolean = false,
    streamingError: String? = null,
    onConfigureService: (String) -> Unit = {},
    onStreamingPlayNext: ((Song) -> Unit)? = null,
    onStreamingAddToQueue: ((Song) -> Unit)? = null,
    onStreamingToggleFavorite: ((Song) -> Unit)? = null,
    onStreamingSetFavorite: ((Song, Boolean) -> Unit)? = null,
    streamingFavoriteSongIds: Set<String> = emptySet(),
    streamingDownloadedSongIds: Set<String> = emptySet(),
    streamingDownloadingSongIds: Set<String> = emptySet(),
    onStreamingToggleDownload: ((Song) -> Unit)? = null
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val tabOrder by appSettings.libraryTabOrder.collectAsState()
    val hiddenTabs by appSettings.hiddenLibraryTabs.collectAsState()
    val showLibraryBottomBarAlways by appSettings.showLibraryBottomBarAlways.collectAsState()

    // Show Album Artists tab by default in Go mode, hidden in local mode
    val effectiveHiddenTabs = remember(hiddenTabs, isStreamingMode) {
        if (isStreamingMode) hiddenTabs - "ALBUM_ARTISTS" else hiddenTabs
    }
    
    val allowedStreamingTabs = setOf("SONGS", "LIKED", "PLAYLISTS", "ALBUMS", "ARTISTS", "ALBUM_ARTISTS")
    val tabs = remember(tabOrder, effectiveHiddenTabs, isStreamingMode) {
        tabOrder
            .filter { !effectiveHiddenTabs.contains(it) && (!isStreamingMode || it in allowedStreamingTabs) }
            .map { tabId ->
                when (tabId) {
                    "SONGS" -> context.getString(R.string.settings_tab_songs)
                    "LIKED" -> context.getString(R.string.settings_tab_liked)
                    "PLAYLISTS" -> context.getString(R.string.settings_tab_playlists)
                    "ALBUMS" -> context.getString(R.string.settings_tab_albums)
                    "ARTISTS" -> context.getString(R.string.settings_tab_artists)
                    "ALBUM_ARTISTS" -> context.getString(R.string.settings_tab_album_artists)
                    "DATES" -> context.getString(R.string.settings_tab_dates)
                    "EXPLORER" -> context.getString(R.string.settings_tab_explorer)
                    else -> tabId
                }
            }
    }
    
    val visibleTabIds = remember(tabOrder, effectiveHiddenTabs, isStreamingMode) {
        tabOrder.filter {
            !effectiveHiddenTabs.contains(it) && (!isStreamingMode || it in allowedStreamingTabs)
        }
    }
    
    val initialTabIndex = remember(visibleTabIds, initialTab) {
        val tabId = initialTab.name
        visibleTabIds.indexOf(tabId).takeIf { it >= 0 } ?: 0
    }
    
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(initialTabIndex) }
    var expandedHeaderHeight by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = selectedTabIndex) { tabs.size }
    val tabRowState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    
    var previousVisibleTabIds by remember { mutableStateOf(visibleTabIds) }
    
    LaunchedEffect(tabs.size, visibleTabIds) {
        val hasTabsChanged = previousVisibleTabIds != visibleTabIds
        
        if (hasTabsChanged) {
            selectedTabIndex = 0
            pagerState.scrollToPage(0)
            tabRowState.animateScrollToItem(0)
            previousVisibleTabIds = visibleTabIds
        } else if (selectedTabIndex >= tabs.size) {
            selectedTabIndex = 0
            pagerState.scrollToPage(0)
        }
    }
    

    
    LaunchedEffect(selectedTabIndex) {
        tabRowState.animateScrollToItem(selectedTabIndex)
    }
    
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }

    var showSongInfoSheet by remember { mutableStateOf(false) }
    var showBulkExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showOperationProgress by remember { mutableStateOf(false) }
    var operationInProgress by remember { mutableStateOf("") }
    var operationResult by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    
    val pendingWriteRequest by musicViewModel.pendingWriteRequest.collectAsState()
    
    var pendingMetadataEditCompleteCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            if (musicViewModel.pendingBatchWriteRequest.value != null) {
                musicViewModel.completeBatchMetadataWriteAfterPermission(
                    onSuccess = {
                        Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully, Toast.LENGTH_SHORT).show()
                    },
                    onError = { errorMessage ->
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                )
            } else {
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
            }
        } else {
            if (musicViewModel.pendingBatchWriteRequest.value != null) {
                musicViewModel.cancelPendingBatchMetadataWrite()
            } else {
                musicViewModel.cancelPendingMetadataWrite()
                pendingMetadataEditCompleteCallback?.invoke(false)
                pendingMetadataEditCompleteCallback = null
            }
            Toast.makeText(context, R.string.localnavigation_permission_denied_changes_saved, Toast.LENGTH_LONG).show()
        }
    }
    
    var operationProgressText by remember { mutableStateOf("") }
    var operationError by remember { mutableStateOf<String?>(null) }
    var showExportResultDialog by remember { mutableStateOf(false) }
    var exportResultsData by remember { mutableStateOf<List<Pair<String, Boolean>>?>(null) }
    var showImportResultDialog by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    
    var explorerReloadTrigger by remember { mutableIntStateOf(0) }
    var explorerPath by rememberSaveable { mutableStateOf<String?>(null) }
    var explorerFolderSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var songsToAddToPlaylist by remember { mutableStateOf<List<Song>>(emptyList()) }
    val addToPlaylistSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    
    val multiSelectionState = remember { chromahub.rhythm.app.features.local.presentation.viewmodel.MultiSelectionStateHolder() }
    val selectedSongs by multiSelectionState.selectedSongs.collectAsState()
    val isSelectionMode by multiSelectionState.isSelectionMode.collectAsState()
    val selectedSongIds by multiSelectionState.selectedSongIds.collectAsState()
    var showMultiSelectionSheet by remember { mutableStateOf(false) }
    var showBatchEditSheet by remember { mutableStateOf(false) }
    
    val onSongLongPress: (Song) -> Unit = remember(multiSelectionState) {
        { song -> multiSelectionState.toggleSelection(song) }
    }
    
    val onSongSelectionToggle: (Song) -> Unit = remember(multiSelectionState) {
        { song -> multiSelectionState.toggleSelection(song) }
    }
    
    val localFavoriteSongs by musicViewModel.favoriteSongs.collectAsState()
    val favoriteSongs = if (isStreamingMode) streamingFavoriteSongIds else localFavoriteSongs
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    // The Favorites filter chip was replaced by the dedicated Liked tab; reset stale saved state.
    LaunchedEffect(Unit) {
        if (selectedCategory == "❤️ Favorites") selectedCategory = "All"
    }
    val breadcrumbScrollState = rememberLazyListState()

    val sortedSongs = remember(songs, sortOrder) {
        when (sortOrder) {
            MusicViewModel.SortOrder.TITLE_ASC -> songs.sortedBy { it.title.lowercase() }
            MusicViewModel.SortOrder.TITLE_DESC -> songs.sortedByDescending { it.title.lowercase() }
            MusicViewModel.SortOrder.ARTIST_ASC -> songs.sortedBy { it.artist.lowercase() }
            MusicViewModel.SortOrder.ARTIST_DESC -> songs.sortedByDescending { it.artist.lowercase() }
            MusicViewModel.SortOrder.ALBUM_ASC -> songs.sortedBy { it.album.lowercase() }
            MusicViewModel.SortOrder.ALBUM_DESC -> songs.sortedByDescending { it.album.lowercase() }
            MusicViewModel.SortOrder.YEAR_ASC -> songs.sortedBy { it.year }
            MusicViewModel.SortOrder.YEAR_DESC -> songs.sortedByDescending { it.year }
            MusicViewModel.SortOrder.DATE_ADDED_ASC -> songs.sortedBy { it.dateAdded }
            MusicViewModel.SortOrder.DATE_ADDED_DESC -> songs.sortedByDescending { it.dateAdded }
            MusicViewModel.SortOrder.DATE_MODIFIED_ASC -> songs.sortedBy { it.dateModified }
            MusicViewModel.SortOrder.DATE_MODIFIED_DESC -> songs.sortedByDescending { it.dateModified }
        }
    }
    
    val preparedSongs = remember(sortedSongs) {
        sortedSongs.distinctBy { "${it.id}_${it.uri}" }
    }

    val categories = remember(preparedSongs, streamingDownloadedSongIds) {
        calculateSongCategories(
            preparedSongs,
            hasDownloadedSongs = streamingDownloadedSongIds.isNotEmpty() || (isStreamingMode && preparedSongs.any { it.id in streamingDownloadedSongIds })
        )
    }

    val filteredSongs = remember(preparedSongs, selectedCategory, streamingDownloadedSongIds) {
        filterSongsByCategory(preparedSongs, selectedCategory, streamingDownloadedSongIds)
    }

    val likedSongs = remember(preparedSongs, favoriteSongs) {
        preparedSongs.filter { it.id in favoriteSongs }
    }

    val sortedAlbums = remember(albums, sortOrder) {
        when (sortOrder) {
            MusicViewModel.SortOrder.TITLE_ASC -> albums.sortedBy { it.title.lowercase() }
            MusicViewModel.SortOrder.TITLE_DESC -> albums.sortedByDescending { it.title.lowercase() }
            MusicViewModel.SortOrder.ARTIST_ASC -> albums.sortedBy { it.artist.lowercase() }
            MusicViewModel.SortOrder.ARTIST_DESC -> albums.sortedByDescending { it.artist.lowercase() }
            MusicViewModel.SortOrder.ALBUM_ASC -> albums.sortedBy { it.title.lowercase() }
            MusicViewModel.SortOrder.ALBUM_DESC -> albums.sortedByDescending { it.title.lowercase() }
            MusicViewModel.SortOrder.YEAR_ASC -> albums.sortedBy { it.year }
            MusicViewModel.SortOrder.YEAR_DESC -> albums.sortedByDescending { it.year }
            MusicViewModel.SortOrder.DATE_ADDED_ASC -> albums.sortedBy { it.songs.minOfOrNull { s -> s.dateAdded } ?: 0L }
            MusicViewModel.SortOrder.DATE_ADDED_DESC -> albums.sortedByDescending { it.songs.minOfOrNull { s -> s.dateAdded } ?: 0L }
            MusicViewModel.SortOrder.DATE_MODIFIED_ASC -> albums.sortedBy { it.dateModified }
            MusicViewModel.SortOrder.DATE_MODIFIED_DESC -> albums.sortedByDescending { it.dateModified }
        }
    }

    var artistSortOption by rememberSaveable { mutableStateOf(ArtistSortOption.NAME_ASC) }
    val sortedArtists = remember(artists, artistSortOption) {
        val baseList = artists.distinctBy { it.id }
        when (artistSortOption) {
            ArtistSortOption.NAME_ASC -> baseList.sortedBy { it.name.lowercase() }
            ArtistSortOption.NAME_DESC -> baseList.sortedByDescending { it.name.lowercase() }
            ArtistSortOption.TRACK_COUNT_DESC -> baseList.sortedByDescending { it.numberOfTracks }
            ArtistSortOption.ALBUM_COUNT_DESC -> baseList.sortedByDescending { it.numberOfAlbums }
        }
    }

    val artistSeparatorEnabled by appSettings.artistSeparatorEnabled.collectAsState()
    val artistSeparatorDelimiters by appSettings.artistSeparatorDelimiters.collectAsState()
    val albumArtists = remember(preparedSongs, artists, artistSeparatorEnabled, artistSeparatorDelimiters) {
        val delimitersStr = if (artistSeparatorEnabled) artistSeparatorDelimiters else ""
        val artistSongsMap = java.util.HashMap<String, MutableList<Song>>()
        val artistAlbumsMap = java.util.HashMap<String, java.util.HashSet<String>>()
        val artistNameMap = java.util.HashMap<String, String>()
        for (song in preparedSongs) {
            val albumArtistName = song.albumArtist?.trim().orEmpty()
            val artistField = if (albumArtistName.isNotBlank() && !albumArtistName.equals("<unknown>", ignoreCase = true)) albumArtistName else song.artist
            val names = ArtistSeparator.splitArtistNames(artistField, delimitersStr, artistSeparatorEnabled)
            for (name in names) {
                if (name.equals("<unknown>", ignoreCase = true)) continue
                val key = name.lowercase()
                artistSongsMap.getOrPut(key) { mutableListOf() }.add(song)
                artistAlbumsMap.getOrPut(key) { java.util.HashSet() }.add(song.album.trim().lowercase())
                if (!artistNameMap.containsKey(key)) {
                    artistNameMap[key] = name
                }
            }
        }
        val artistByName = artists.associateBy { it.name.lowercase() }
        artistSongsMap.map { (key, songsOfArtist) ->
            val existing = artistByName[key]
            Artist(
                id = existing?.id ?: "albumartist:$key",
                name = artistNameMap[key] ?: songsOfArtist.first().artist,
                artworkUri = existing?.artworkUri,
                songs = songsOfArtist,
                numberOfTracks = songsOfArtist.size,
                numberOfAlbums = artistAlbumsMap[key]?.size ?: 0
            )
        }
    }
    val sortedAlbumArtists = remember(albumArtists, artistSortOption) {
        val baseList = albumArtists.distinctBy { it.id }
        when (artistSortOption) {
            ArtistSortOption.NAME_ASC -> baseList.sortedBy { it.name.lowercase() }
            ArtistSortOption.NAME_DESC -> baseList.sortedByDescending { it.name.lowercase() }
            ArtistSortOption.TRACK_COUNT_DESC -> baseList.sortedByDescending { it.numberOfTracks }
            ArtistSortOption.ALBUM_COUNT_DESC -> baseList.sortedByDescending { it.numberOfAlbums }
        }
    }
    
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val globalCollapseBehavior by appSettings.headerCollapseBehavior.collectAsState()
    val shouldStartCollapsed = globalCollapseBehavior == 1
    
    LaunchedEffect(shouldStartCollapsed) {
        if (shouldStartCollapsed) {
            topAppBarState.heightOffset = topAppBarState.heightOffsetLimit
        }
    }
    
    var showPlaylistFabMenu by remember { mutableStateOf(false) }

    BackHandler(showPlaylistFabMenu) {
        showPlaylistFabMenu = false
    }

    val onCreatePlaylistFromFab: () -> Unit = {
        showCreatePlaylistDialog = true
    }

    val onImportPlaylistFromFab: (() -> Unit)? = if (onImportPlaylist != null) {
        {
            showImportDialog = true
        }
    } else null

    val onExportPlaylistsFromFab: (() -> Unit)? = if (onExportAllPlaylists != null) {
        {
            showBulkExportDialog = true
        }
    } else null

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(selectedTabIndex)
        }
    }
    


    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && selectedTabIndex != pagerState.currentPage) {
            selectedTabIndex = pagerState.currentPage
            tabRowState.animateScrollToItem(pagerState.currentPage)
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { 
                showCreatePlaylistDialog = false
                songsToAddToPlaylist = emptyList()
            },
            onConfirm = { name ->
                if (songsToAddToPlaylist.isEmpty()) {
                    onCreatePlaylist(name)
                } else {
                    musicViewModel.createPlaylist(name, songsToAddToPlaylist)
                    songsToAddToPlaylist = emptyList()
                }
                showCreatePlaylistDialog = false
            }
        )
    }
    
    val currentSelectedSong = selectedSong
    if (showSongInfoSheet && currentSelectedSong != null) {
        val displaySong = songs.find { it.id == currentSelectedSong.id } ?: currentSelectedSong
        
        SongInfoBottomSheet(
            song = displaySong,
            onDismiss = { showSongInfoSheet = false },
            appSettings = appSettings,
            isStreamingMode = isStreamingMode,
            isDownloaded = streamingDownloadedSongIds.contains(displaySong.id),
            isDownloading = streamingDownloadingSongIds.contains(displaySong.id),
            onToggleDownload = if (isStreamingMode) ({
                onStreamingToggleDownload?.invoke(displaySong)
            }) else null,
            onEditSong = { title, artist, album, genre, year, trackNumber, artworkUri, removeArtwork, albumArtist, composer, discNumber, onComplete ->
                pendingMetadataEditCompleteCallback = onComplete
                musicViewModel.saveMetadataChanges(
                    song = displaySong,
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
                            Toast.makeText(
                                context,
                                "Failed to request permission: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            musicViewModel.cancelPendingMetadataWrite()
                            pendingMetadataEditCompleteCallback?.invoke(false)
                            pendingMetadataEditCompleteCallback = null
                        }
                    }
                )
            }
        )
    }
    
    if (showAddToPlaylistSheet && songsToAddToPlaylist.isNotEmpty()) {
        AddToPlaylistBottomSheet(
            song = songsToAddToPlaylist.first(),
            playlists = playlists,
            onDismissRequest = { 
                showAddToPlaylistSheet = false
                songsToAddToPlaylist = emptyList()
            },
            onAddToPlaylist = { playlist ->
                if (songsToAddToPlaylist.size == 1) {
                    onAddSongToPlaylist(songsToAddToPlaylist.first(), playlist.id)
                } else {
                    val (successCount, playlistName) = musicViewModel.addSongsToPlaylist(songsToAddToPlaylist, playlist.id)
                    Toast.makeText(
                        context,
                        "Added $successCount songs to $playlistName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                scope.launch {
                    addToPlaylistSheetState.hide()
                }.invokeOnCompletion {
                    if (!addToPlaylistSheetState.isVisible) {
                        showAddToPlaylistSheet = false
                        songsToAddToPlaylist = emptyList()
                    }
                }
            },
            onCreateNewPlaylist = {
                scope.launch {
                    addToPlaylistSheetState.hide()
                }.invokeOnCompletion {
                    if (!addToPlaylistSheetState.isVisible) {
                        showAddToPlaylistSheet = false
                        showCreatePlaylistDialog = true
                    }
                }
            },
            sheetState = addToPlaylistSheetState
        )
    }

    
    
    val isLibraryRefreshing by musicViewModel.isLibraryRefreshing.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    val songsListState = rememberLazyListState()
    val likedSongsListState = rememberLazyListState()
    val datesListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()
    val playlistsGridState = rememberLazyGridState()
    val albumsListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()
    val artistsListState = rememberLazyListState()
    val artistsGridState = rememberLazyGridState()
    val albumArtistsListState = rememberLazyListState()
    val albumArtistsGridState = rememberLazyGridState()
    val explorerListState = rememberLazyListState()

    val playlistViewType by appSettings.playlistViewType.collectAsState()
    val albumViewType by appSettings.albumViewType.collectAsState()
    val artistViewType by appSettings.artistViewType.collectAsState()



    val isListAtTop by remember(
        selectedTabIndex, visibleTabIds, playlistViewType, albumViewType, artistViewType
    ) {
        derivedStateOf {
            when (visibleTabIds.getOrNull(selectedTabIndex)) {
                "SONGS" -> songsListState.firstVisibleItemIndex == 0 && songsListState.firstVisibleItemScrollOffset == 0
                "LIKED" -> likedSongsListState.firstVisibleItemIndex == 0 && likedSongsListState.firstVisibleItemScrollOffset == 0
                "DATES" -> datesListState.firstVisibleItemIndex == 0 && datesListState.firstVisibleItemScrollOffset == 0
                "PLAYLISTS" -> {
                    if (playlistViewType == PlaylistViewType.GRID) {
                        playlistsGridState.firstVisibleItemIndex == 0 && playlistsGridState.firstVisibleItemScrollOffset == 0
                    } else {
                        playlistsListState.firstVisibleItemIndex == 0 && playlistsListState.firstVisibleItemScrollOffset == 0
                    }
                }
                "ALBUMS" -> {
                    if (albumViewType == AlbumViewType.GRID) {
                        albumsGridState.firstVisibleItemIndex == 0 && albumsGridState.firstVisibleItemScrollOffset == 0
                    } else {
                        albumsListState.firstVisibleItemIndex == 0 && albumsListState.firstVisibleItemScrollOffset == 0
                    }
                }
                "ARTISTS" -> {
                    if (artistViewType == ArtistViewType.GRID) {
                        artistsGridState.firstVisibleItemIndex == 0 && artistsGridState.firstVisibleItemScrollOffset == 0
                    } else {
                        artistsListState.firstVisibleItemIndex == 0 && artistsListState.firstVisibleItemScrollOffset == 0
                    }
                }
                "ALBUM_ARTISTS" -> {
                    if (artistViewType == ArtistViewType.GRID) {
                        albumArtistsGridState.firstVisibleItemIndex == 0 && albumArtistsGridState.firstVisibleItemScrollOffset == 0
                    } else {
                        albumArtistsListState.firstVisibleItemIndex == 0 && albumArtistsListState.firstVisibleItemScrollOffset == 0
                    }
                }
                "EXPLORER" -> explorerListState.firstVisibleItemIndex == 0 && explorerListState.firstVisibleItemScrollOffset == 0
                else -> true
            }
        }
    }
    val isTabletLayout = windowScreenWidthDp() >= 600
    val baseLibraryBottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
    val fabBottomPadding = if (isTabletLayout) {
        (baseLibraryBottomPadding + 12.dp).coerceAtLeast(12.dp)
    } else {
        (baseLibraryBottomPadding - 4.dp).coerceAtLeast(0.dp)
    }
    val libraryBottomOverlayPadding = baseLibraryBottomPadding
    val adjustedSongsBottomPadding = baseLibraryBottomPadding

    
    LaunchedEffect(isLibraryRefreshing) {
        isRefreshing = isLibraryRefreshing
    }
    
    BackHandler(enabled = isSelectionMode) {
        multiSelectionState.clearSelection()
    }
    
    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            favoriteSongIds = favoriteSongs.toSet(),
            onDismiss = {
                showMultiSelectionSheet = false
                multiSelectionState.clearSelection()
            },
            onPlayAll = {
                onPlayQueue(selectedSongs)
                multiSelectionState.clearSelection()
            },
            onAddToQueue = {
                selectedSongs.forEach { song -> onAddToQueue(song) }
                multiSelectionState.clearSelection()
            },
            onPlayNext = if (isStreamingMode) null else {
                {
                    selectedSongs.reversed().forEach { song -> musicViewModel.playNext(song) }
                    multiSelectionState.clearSelection()
                }
            },
            onAddToPlaylist = {
                songsToAddToPlaylist = selectedSongs
                showMultiSelectionSheet = false
                showAddToPlaylistSheet = true
            },
            onToggleLikeAll = if (isStreamingMode) ({ shouldLike ->
                selectedSongs.forEach { song ->
                    val isLiked = streamingFavoriteSongIds.contains(song.id)
                    if (shouldLike != isLiked) onStreamingSetFavorite?.invoke(song, shouldLike)
                }
            }) else { shouldLike ->
                selectedSongs.forEach { song ->
                    val isFavorited = favoriteSongs.contains(song.id)
                    if (shouldLike != isFavorited) {
                        musicViewModel.toggleFavorite(song)
                    }
                }
            },
            onAddToBlacklist = if (isStreamingMode) null else {
                {
                    selectedSongs.forEach { song ->
                        appSettings.addToBlacklist(song.id)
                    }
                }
            },
            onBatchEditTags = if (isStreamingMode) null else {
                {
                    showMultiSelectionSheet = false
                    showBatchEditSheet = true
                }
            }
        )
    }

    if (showBatchEditSheet && selectedSongs.isNotEmpty()) {
        BatchEditTagsSheet(
            selectedSongs = selectedSongs,
            onDismiss = {
                showBatchEditSheet = false
                multiSelectionState.clearSelection()
            },
            onSave = { artist, album, genre, year, artworkUri, removeArtwork, onProgress, onComplete ->
                musicViewModel.batchEditMetadata(
                    songs = selectedSongs,
                    artist = artist,
                    album = album,
                    genre = genre,
                    year = year,
                    artworkUri = artworkUri,
                    removeArtwork = removeArtwork,
                    onProgress = onProgress,
                    onComplete = onComplete,
                    onPermissionRequired = { pendingRequest ->
                        try {
                            val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                pendingRequest.intentSender
                            ).build()
                            writePermissionLauncher.launch(intentSenderRequest)
                        } catch (e: Exception) {
                            Log.e("LibraryScreen", "Failed to launch batch write request permission", e)
                        }
                    }
                )
            }
        )
    }

    val activeTabIdOuter = visibleTabIds.getOrNull(pagerState.currentPage) ?: ""
    val bottomBarSongs = remember(activeTabIdOuter, filteredSongs, likedSongs, sortedAlbums, sortedArtists, sortedAlbumArtists, explorerFolderSongs) {
        when (activeTabIdOuter) {
            "SONGS" -> filteredSongs
            "DATES" -> filteredSongs
            "LIKED" -> likedSongs
            "ALBUMS" -> sortedAlbums.flatMap { it.songs }
            "ARTISTS" -> sortedArtists.flatMap { it.songs }
            "ALBUM_ARTISTS" -> sortedAlbumArtists.flatMap { it.songs }
            "EXPLORER" -> explorerFolderSongs
            else -> emptyList()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    if (scrollBehavior.state.heightOffset == 0f || expandedHeaderHeight == 0) {
                        expandedHeaderHeight = coordinates.size.height - scrollBehavior.state.heightOffset.toInt()
                    }
                }
            ) {
                Spacer(modifier = Modifier.height(5.dp))
                
                LargeTopAppBar(
                navigationIcon = { },
                title = {
                    val collapsedFraction = scrollBehavior.state.collapsedFraction
                    val fontSize = (24 + (32 - 24) * (1 - collapsedFraction)).sp

                    Text(
                        text = if (isStreamingMode && streamingServiceConnected && streamingServiceName.isNotBlank()) {
                            "$streamingServiceName ${context.getString(R.string.library_title)}"
                        } else {
                            context.getString(R.string.library_title)
                        },
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSize
                        ),
                        modifier = Modifier.padding(start = 14.dp)
                    )
                },
                actions = {
                    val showShuffle = !showLibraryBottomBarAlways && !isSelectionMode && bottomBarSongs.isNotEmpty() && activeTabIdOuter != "ARTISTS" && activeTabIdOuter != "ALBUM_ARTISTS" && activeTabIdOuter != "ALBUMS"

                    AnimatedVisibility(
                        visible = showShuffle,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    onShuffleQueue(bottomBarSongs)
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Shuffle,
                                    contentDescription = stringResource(R.string.cd_shuffle),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }

                    when (visibleTabIds.getOrNull(selectedTabIndex)) {
                        "ALBUMS" -> {
                            val buttonScale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "albumToggleScale"
                            )
                            
                            FilledTonalIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    val newViewType = if (albumViewType == AlbumViewType.LIST) AlbumViewType.GRID else AlbumViewType.LIST
                                    appSettings.setAlbumViewType(newViewType)
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(42.dp)
                                    .graphicsLayer {
                                        scaleX = buttonScale
                                        scaleY = buttonScale
                                    }
                            ) {
                                Icon(
                                    imageVector = if (albumViewType == AlbumViewType.LIST) RhythmIcons.GridView else MaterialSymbolIcon("view_list", filled = true),
                                    contentDescription = if (albumViewType == AlbumViewType.LIST) "Switch to Grid View" else "Switch to List View",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        "ARTISTS" -> {
                            val buttonScale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "artistToggleScale"
                            )
                            
                            FilledTonalIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    val newViewType = if (artistViewType == ArtistViewType.LIST) ArtistViewType.GRID else ArtistViewType.LIST
                                    appSettings.setArtistViewType(newViewType)
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(42.dp)
                                    .graphicsLayer {
                                        scaleX = buttonScale
                                        scaleY = buttonScale
                                    }
                            ) {
                                Icon(
                                    imageVector = if (artistViewType == ArtistViewType.LIST) RhythmIcons.GridView else MaterialSymbolIcon("view_list", filled = true),
                                    contentDescription = if (artistViewType == ArtistViewType.LIST) "Switch to Grid View" else "Switch to List View",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        "ALBUM_ARTISTS" -> {
                            val buttonScale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "albumArtistToggleScale"
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    val newViewType = if (artistViewType == ArtistViewType.LIST) ArtistViewType.GRID else ArtistViewType.LIST
                                    appSettings.setArtistViewType(newViewType)
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(42.dp)
                                    .graphicsLayer {
                                        scaleX = buttonScale
                                        scaleY = buttonScale
                                    }
                            ) {
                                Icon(
                                    imageVector = if (artistViewType == ArtistViewType.LIST) RhythmIcons.GridView else MaterialSymbolIcon("view_list", filled = true),
                                    contentDescription = if (artistViewType == ArtistViewType.LIST) "Switch to Grid View" else "Switch to List View",
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        "PLAYLISTS" -> {
                            val buttonScale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "playlistToggleScale"
                            )
                            
                            FilledTonalIconButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    val newViewType = if (playlistViewType == PlaylistViewType.LIST) PlaylistViewType.GRID else PlaylistViewType.LIST
                                    appSettings.setPlaylistViewType(newViewType)
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(42.dp)
                                    .graphicsLayer {
                                        scaleX = buttonScale
                                        scaleY = buttonScale
                                    }
                            ) {
                                Icon(
                                    imageVector = if (playlistViewType == PlaylistViewType.LIST) RhythmIcons.GridView else MaterialSymbolIcon("view_list", filled = true),
                                    contentDescription = if (playlistViewType == PlaylistViewType.LIST) "Switch to Grid View" else "Switch to List View",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        

                    }
                    
                    val currentTabId = visibleTabIds.getOrNull(selectedTabIndex)
                    if (currentTabId == "SONGS" || currentTabId == "ALBUMS" || currentTabId == "DATES") {
                        var showSortMenu by remember { mutableStateOf(false) }
                        var pendingSortOrder by remember { mutableStateOf<MusicViewModel.SortOrder?>(null) }
                        
                        LaunchedEffect(sortOrder) {
                            pendingSortOrder = null
                        }
                        
                        Box {
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
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .graphicsLayer {
                                scaleX = sortButtonScale
                                scaleY = sortButtonScale
                            }
                        ) {
                            val sortIcon = when (sortOrder) {
                                MusicViewModel.SortOrder.TITLE_ASC, MusicViewModel.SortOrder.TITLE_DESC -> RhythmIcons.SortByAlpha
                                MusicViewModel.SortOrder.ARTIST_ASC, MusicViewModel.SortOrder.ARTIST_DESC -> RhythmIcons.ArtistFilled
                                MusicViewModel.SortOrder.ALBUM_ASC, MusicViewModel.SortOrder.ALBUM_DESC -> RhythmIcons.AlbumFilled
                                MusicViewModel.SortOrder.YEAR_ASC, MusicViewModel.SortOrder.YEAR_DESC -> MaterialSymbolIcon("calendar_month", filled = true)
                                MusicViewModel.SortOrder.DATE_ADDED_ASC, MusicViewModel.SortOrder.DATE_ADDED_DESC -> RhythmIcons.DateRange
                                MusicViewModel.SortOrder.DATE_MODIFIED_ASC, MusicViewModel.SortOrder.DATE_MODIFIED_DESC -> MaterialSymbolIcon("edit_calendar", filled = true)
                            }
                            Icon(
                                imageVector = sortIcon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            val sortText = when (sortOrder) {
                                MusicViewModel.SortOrder.TITLE_ASC, MusicViewModel.SortOrder.TITLE_DESC -> context.getString(R.string.library_sort_title)
                                MusicViewModel.SortOrder.ARTIST_ASC, MusicViewModel.SortOrder.ARTIST_DESC -> context.getString(R.string.library_sort_artist)
                                MusicViewModel.SortOrder.ALBUM_ASC, MusicViewModel.SortOrder.ALBUM_DESC -> context.getString(R.string.library_sort_album)
                                MusicViewModel.SortOrder.YEAR_ASC, MusicViewModel.SortOrder.YEAR_DESC -> context.getString(R.string.metadata_year)
                                MusicViewModel.SortOrder.DATE_ADDED_ASC, MusicViewModel.SortOrder.DATE_ADDED_DESC -> context.getString(R.string.library_sort_date_added)
                                MusicViewModel.SortOrder.DATE_MODIFIED_ASC, MusicViewModel.SortOrder.DATE_MODIFIED_DESC -> context.getString(R.string.library_sort_date_modified)
                            }

                            Text(
                                text = sortText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            val sortArrowIcon = when (sortOrder) {
                                MusicViewModel.SortOrder.TITLE_ASC, MusicViewModel.SortOrder.ARTIST_ASC, MusicViewModel.SortOrder.ALBUM_ASC, MusicViewModel.SortOrder.YEAR_ASC, MusicViewModel.SortOrder.DATE_ADDED_ASC, MusicViewModel.SortOrder.DATE_MODIFIED_ASC -> RhythmIcons.ArrowUpward
                                MusicViewModel.SortOrder.TITLE_DESC, MusicViewModel.SortOrder.ARTIST_DESC, MusicViewModel.SortOrder.ALBUM_DESC, MusicViewModel.SortOrder.YEAR_DESC, MusicViewModel.SortOrder.DATE_ADDED_DESC, MusicViewModel.SortOrder.DATE_MODIFIED_DESC -> RhythmIcons.ArrowDownward
                            }
                            
                            Icon(
                                imageVector = sortArrowIcon,
                                contentDescription = if (sortOrder.name.endsWith("_ASC")) context.getString(R.string.library_sort_ascending) else context.getString(R.string.library_sort_descending),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .widthIn(min = 250.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(8.dp)
                        ) {
                            val activeSortOrder = pendingSortOrder ?: sortOrder
                            val currentKey = when (activeSortOrder) {
                                MusicViewModel.SortOrder.TITLE_ASC, MusicViewModel.SortOrder.TITLE_DESC -> "TITLE"
                                MusicViewModel.SortOrder.ARTIST_ASC, MusicViewModel.SortOrder.ARTIST_DESC -> "ARTIST"
                                MusicViewModel.SortOrder.ALBUM_ASC, MusicViewModel.SortOrder.ALBUM_DESC -> "ALBUM"
                                MusicViewModel.SortOrder.YEAR_ASC, MusicViewModel.SortOrder.YEAR_DESC -> "YEAR"
                                MusicViewModel.SortOrder.DATE_ADDED_ASC, MusicViewModel.SortOrder.DATE_ADDED_DESC -> "DATE_ADDED"
                                MusicViewModel.SortOrder.DATE_MODIFIED_ASC, MusicViewModel.SortOrder.DATE_MODIFIED_DESC -> "DATE_MODIFIED"
                            }
                            val isAscending = when (activeSortOrder) {
                                MusicViewModel.SortOrder.TITLE_ASC, MusicViewModel.SortOrder.ARTIST_ASC, MusicViewModel.SortOrder.ALBUM_ASC, MusicViewModel.SortOrder.YEAR_ASC, MusicViewModel.SortOrder.DATE_ADDED_ASC, MusicViewModel.SortOrder.DATE_MODIFIED_ASC -> true
                                else -> false
                            }
                            
                            fun getSortOrder(key: String, asc: Boolean): MusicViewModel.SortOrder {
                                return when (key) {
                                    "TITLE" -> if (asc) MusicViewModel.SortOrder.TITLE_ASC else MusicViewModel.SortOrder.TITLE_DESC
                                    "ARTIST" -> if (asc) MusicViewModel.SortOrder.ARTIST_ASC else MusicViewModel.SortOrder.ARTIST_DESC
                                    "ALBUM" -> if (asc) MusicViewModel.SortOrder.ALBUM_ASC else MusicViewModel.SortOrder.ALBUM_DESC
                                    "YEAR" -> if (asc) MusicViewModel.SortOrder.YEAR_ASC else MusicViewModel.SortOrder.YEAR_DESC
                                    "DATE_ADDED" -> if (asc) MusicViewModel.SortOrder.DATE_ADDED_ASC else MusicViewModel.SortOrder.DATE_ADDED_DESC
                                    "DATE_MODIFIED" -> if (asc) MusicViewModel.SortOrder.DATE_MODIFIED_ASC else MusicViewModel.SortOrder.DATE_MODIFIED_DESC
                                    else -> MusicViewModel.SortOrder.TITLE_ASC
                                }
                            }
                            
                            val sortOptions = listOf(
                                RhythmSortOption("TITLE", context.getString(R.string.library_sort_title), RhythmIcons.SortByAlpha),
                                RhythmSortOption("ARTIST", context.getString(R.string.library_sort_artist), RhythmIcons.ArtistFilled),
                                RhythmSortOption("ALBUM", context.getString(R.string.library_sort_album), RhythmIcons.AlbumFilled),
                                RhythmSortOption("YEAR", context.getString(R.string.metadata_year), RhythmIcons.CalendarMonth),
                                RhythmSortOption("DATE_ADDED", context.getString(R.string.library_sort_date_added), RhythmIcons.DateRange),
                                RhythmSortOption("DATE_MODIFIED", context.getString(R.string.library_sort_date_modified), MaterialSymbolIcon("edit_calendar", filled = true))
                            ).filter { option ->
                                !(currentTabId == "ALBUMS" && option.key == "ALBUM")
                            }
                            
                            RhythmSortMenuContent(
                                selectedKey = currentKey,
                                isAscending = isAscending,
                                options = sortOptions,
                                onKeySelected = { key ->
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    val newOrder = getSortOrder(key, isAscending)
                                    pendingSortOrder = newOrder
                                    showSortMenu = false
                                    if (sortOrder != newOrder) {
                                        musicViewModel.setSortOrder(newOrder)
                                    }
                                },
                                onDirectionToggled = { asc ->
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    val newOrder = getSortOrder(currentKey, asc)
                                    pendingSortOrder = newOrder
                                    showSortMenu = false
                                    if (sortOrder != newOrder) {
                                        musicViewModel.setSortOrder(newOrder)
                                    }
                                }
                            )
                        }
                    }
                }
                    
                    if (currentTabId == "PLAYLISTS") {
                        val playlistSortOrderString by appSettings.playlistSortOrder.collectAsState()
                        val playlistSortOrder = try {
                            LibraryPlaylistSortOrder.valueOf(playlistSortOrderString)
                        } catch (e: Exception) {
                            LibraryPlaylistSortOrder.NAME_ASC
                        }
                        var showPlaylistSortMenu by remember { mutableStateOf(false) }
                        
                        Box {
                            val sortButtonScale by animateFloatAsState(
                                targetValue = if (showPlaylistSortMenu) 0.95f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "playlistSortButtonScale"
                            )
                            
                            FilledTonalButton(
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showPlaylistSortMenu = true
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .graphicsLayer {
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

                                val sortText = when (playlistSortOrder) {
                                    LibraryPlaylistSortOrder.NAME_ASC, LibraryPlaylistSortOrder.NAME_DESC -> context.getString(R.string.sort_name)
                                    LibraryPlaylistSortOrder.DATE_CREATED_ASC, LibraryPlaylistSortOrder.DATE_CREATED_DESC -> context.getString(R.string.sort_date_created)
                                    LibraryPlaylistSortOrder.SONG_COUNT_ASC, LibraryPlaylistSortOrder.SONG_COUNT_DESC -> context.getString(R.string.sort_song_count)
                                }

                                Text(
                                    text = sortText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Spacer(modifier = Modifier.width(4.dp))
                                
                                val sortArrowIcon = if (playlistSortOrder.name.endsWith("_ASC")) RhythmIcons.ArrowUpward else RhythmIcons.ArrowDownward
                                
                                Icon(
                                    imageVector = sortArrowIcon,
                                    contentDescription = if (playlistSortOrder.name.endsWith("_ASC")) context.getString(R.string.library_sort_ascending) else context.getString(R.string.library_sort_descending),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                             DropdownMenu(
                                 expanded = showPlaylistSortMenu,
                                 onDismissRequest = { showPlaylistSortMenu = false },
                                 shape = RoundedCornerShape(20.dp),
                                 modifier = Modifier
                                     .widthIn(min = 250.dp)
                                     .background(MaterialTheme.colorScheme.surfaceContainer)
                                     .padding(8.dp)
                             ) {
                                 val currentKey = when (playlistSortOrder) {
                                     LibraryPlaylistSortOrder.NAME_ASC, LibraryPlaylistSortOrder.NAME_DESC -> "NAME"
                                     LibraryPlaylistSortOrder.DATE_CREATED_ASC, LibraryPlaylistSortOrder.DATE_CREATED_DESC -> "DATE_CREATED"
                                     LibraryPlaylistSortOrder.SONG_COUNT_ASC, LibraryPlaylistSortOrder.SONG_COUNT_DESC -> "SONG_COUNT"
                                 }
                                 val isAscending = when (playlistSortOrder) {
                                     LibraryPlaylistSortOrder.NAME_ASC, LibraryPlaylistSortOrder.DATE_CREATED_ASC, LibraryPlaylistSortOrder.SONG_COUNT_ASC -> true
                                     else -> false
                                 }
                                 
                                 fun getPlaylistSortOrder(key: String, asc: Boolean): LibraryPlaylistSortOrder {
                                     return when (key) {
                                         "NAME" -> if (asc) LibraryPlaylistSortOrder.NAME_ASC else LibraryPlaylistSortOrder.NAME_DESC
                                         "DATE_CREATED" -> if (asc) LibraryPlaylistSortOrder.DATE_CREATED_ASC else LibraryPlaylistSortOrder.DATE_CREATED_DESC
                                         "SONG_COUNT" -> if (asc) LibraryPlaylistSortOrder.SONG_COUNT_ASC else LibraryPlaylistSortOrder.SONG_COUNT_DESC
                                         else -> LibraryPlaylistSortOrder.NAME_ASC
                                     }
                                 }
                                 
                                 val playlistSortOptions = listOf(
                                     RhythmSortOption("NAME", context.getString(R.string.sort_name), RhythmIcons.SortByAlpha),
                                     RhythmSortOption("DATE_CREATED", context.getString(R.string.sort_date_created), RhythmIcons.DateRange),
                                     RhythmSortOption("SONG_COUNT", context.getString(R.string.sort_song_count), RhythmIcons.MusicNote)
                                 )
                                 
                                 RhythmSortMenuContent(
                                     selectedKey = currentKey,
                                     isAscending = isAscending,
                                     options = playlistSortOptions,
                                     onKeySelected = { key ->
                                         HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                         val newOrder = getPlaylistSortOrder(key, isAscending)
                                         showPlaylistSortMenu = false
                                         if (playlistSortOrder != newOrder) {
                                             appSettings.setPlaylistSortOrder(newOrder.name)
                                         }
                                     },
                                     onDirectionToggled = { asc ->
                                         HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                         val newOrder = getPlaylistSortOrder(currentKey, asc)
                                         showPlaylistSortMenu = false
                                         if (playlistSortOrder != newOrder) {
                                             appSettings.setPlaylistSortOrder(newOrder.name)
                                         }
                                     }
                                 )
                             }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                scrollBehavior = scrollBehavior,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            }
        },
        bottomBar = {},
        floatingActionButton = {
            if (visibleTabIds.getOrNull(selectedTabIndex) == "PLAYLISTS") {
                val playlistsScrollingUp = if (playlistViewType == PlaylistViewType.GRID) playlistsGridState.isScrollingUp() else playlistsListState.isScrollingUp()
                val playlistsScrollInProgress = if (playlistViewType == PlaylistViewType.GRID) playlistsGridState.isScrollInProgress else playlistsListState.isScrollInProgress
                val showPlaylistFab = !playlistsScrollInProgress || playlistsScrollingUp
                PlaylistFabMenu(
                    visible = showPlaylistFab,
                    expanded = showPlaylistFabMenu,
                    onExpandedChange = { showPlaylistFabMenu = it },
                    onCreatePlaylist = onCreatePlaylistFromFab,
                    onImportPlaylist = onImportPlaylistFromFab,
                    onExportPlaylists = onExportPlaylistsFromFab,
                    bottomPadding = fabBottomPadding,
                    haptics = haptics
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                LazyRow(
                    state = tabRowState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(
                        count = tabs.size,
                        key = { index -> tabOrder.getOrNull(index) ?: "tab_$index" }
                    ) { index ->
                        val isSelected = selectedTabIndex == index
                        
                        TabAnimation(
                            index = index,
                            selectedIndex = selectedTabIndex,
                            title = tabs[index],
                            selectedColor = MaterialTheme.colorScheme.primary,
                            onSelectedColor = MaterialTheme.colorScheme.onPrimary,
                            unselectedColor = MaterialTheme.colorScheme.surfaceContainer,
                            onUnselectedColor = MaterialTheme.colorScheme.onSurface,
                            onClick = {
                                selectedTabIndex = index
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                    tabRowState.animateScrollToItem(index)
                                }
                            },
                            modifier = Modifier.padding(all = 2.dp),
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val currentTabId = visibleTabIds.getOrNull(index)
                                    Icon(
                                        imageVector = when (currentTabId) {
                                            "SONGS" -> RhythmIcons.HeadphonesFilled
                                            "LIKED" -> RhythmIcons.FavoriteFilled
                                            "PLAYLISTS" -> RhythmIcons.PlaylistFilled
                                            "ALBUMS" -> RhythmIcons.Music.Album
                                            "ARTISTS" -> RhythmIcons.Artist
                                            "ALBUM_ARTISTS" -> MaterialSymbolIcon("person_pin")
                                            "DATES" -> RhythmIcons.CalendarMonth
                                            "EXPLORER" -> RhythmIcons.Folder
                                            else -> RhythmIcons.HeadphonesFilled
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = tabs[index],
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        )
                    }
                    
                    item {
                        var showLibraryTabOrderSheet by remember { mutableStateOf(false) }

                        TabAnimation(
                            index = tabs.size,
                            selectedIndex = -1,
                            title = stringResource(R.string.bottomsheet_timer_edit),
                            selectedColor = MaterialTheme.colorScheme.secondaryContainer,
                            onSelectedColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unselectedColor = MaterialTheme.colorScheme.surfaceContainer,
                            onUnselectedColor = MaterialTheme.colorScheme.onSurface,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                showLibraryTabOrderSheet = true
                            },
                            modifier = Modifier.padding(all = 2.dp),
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Edit,
                                        contentDescription = stringResource(R.string.cd_reorder_tabs),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.bottomsheet_timer_edit),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        )

                        if (showLibraryTabOrderSheet) {
                            LibraryTabOrderBottomSheet(
                                onDismiss = { showLibraryTabOrderSheet = false },
                                appSettings = appSettings,
                                haptics = haptics
                            )
                        }
                    }
                }
            }
            
            val isBackgroundProcessing by musicViewModel.isBackgroundProcessing.collectAsState()
            val isMediaScanning by musicViewModel.isMediaScanning.collectAsState()
            val isGenreDetectionRunning by musicViewModel.isGenreDetectionRunning.collectAsState()
            val isFetchingArtwork by musicViewModel.isFetchingArtwork.collectAsState()
            val isExtractingMetadata by musicViewModel.isExtractingMetadata.collectAsState()
            
            AnimatedVisibility(
                visible = isBackgroundProcessing,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp)
                ) {
                    androidx.compose.material3.LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        trackColor = Color.Transparent
                    )
                    
                }
            }
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 10.dp, top = 0.dp, end = 10.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                shadowElevation = 0.dp
            ) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        if (visibleTabIds.getOrNull(selectedTabIndex) == "EXPLORER") {
                            explorerReloadTrigger++
                        } else {
                            onRefreshClick()
                        }
                    },
                    state = pullToRefreshState,
                    enabled = !isSelectionMode && isListAtTop,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val currentTabId = visibleTabIds.getOrNull(selectedTabIndex)
                        
                        AnimatedVisibility(
                            visible = currentTabId == "SONGS" || (currentTabId == "EXPLORER" && explorerPath != null),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                            ) {
                                if (currentTabId == "SONGS") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(
                                            items = categories,
                                            key = { it }
                                        ) { category ->
                                            val isSelected = selectedCategory == category
                                            
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                                    selectedCategory = category
                                                },
                                                label = {
                                                    Text(
                                                        text = when (category) {
                                                            "All" -> context.getString(R.string.library_category_all)
                                                            "Downloaded" -> stringResource(R.string.streaming_downloaded)
                                                            "Short (< 3 min)" -> context.getString(R.string.library_category_short)
                                                            "Medium (3-5 min)" -> context.getString(R.string.library_category_medium)
                                                            "Long (> 5 min)" -> context.getString(R.string.library_category_long)
                                                            "Hi-Res Lossless" -> "Hi-Res Lossless"
                                                            "Lossless" -> "Lossless"
                                                            "Dolby" -> "Dolby"
                                                            "Mono" -> context.getString(R.string.library_category_mono)
                                                            "Stereo" -> context.getString(R.string.library_category_stereo)
                                                            "High Quality" -> "High Quality"
                                                            "Standard" -> "Standard"
                                                            else -> category
                                                        },
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                },
                                                leadingIcon = if (isSelected) {
                                                    {
                                                        Icon(
                                                            imageVector = RhythmIcons.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                                        )
                                                    }
                                                } else null,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    }
                                } else if (currentTabId == "EXPLORER" && explorerPath != null) {
                                    ExplorerBreadcrumb(
                                        path = explorerPath!!,
                                        onNavigateTo = { newPath ->
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            explorerPath = newPath
                                        },
                                        onGoHome = {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                            explorerPath = null
                                        },
                                        scrollState = breadcrumbScrollState
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).clipToBounds()) {
                            val streamingContentEmpty = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
                            when {
                                isStreamingMode && streamingContentEmpty && (!streamingServiceConnected || streamingError != null) -> {
                                    EmptyState(
                                        message = context.getString(R.string.streaming_home_selected_service_unavailable),
                                        icon = RhythmIcons.Connectivity.WifiOff,
                                        subtitle = streamingError?.takeIf { it.isNotBlank() }
                                            ?: context.getString(
                                                R.string.streaming_home_connect_selected_service,
                                                streamingServiceName.ifBlank { context.getString(R.string.streaming_not_selected) }
                                            ),
                                        actionLabel = context.getString(R.string.streaming_service_setup_reconnect),
                                        onRefresh = { onConfigureService(streamingServiceName) }
                                    )
                                }
                                isStreamingMode && streamingContentEmpty && streamingIsLoading -> {
                                    EmptyState(
                                        message = context.getString(R.string.streaming_library_syncing),
                                        icon = MaterialSymbolIcon("sync"),
                                        subtitle = context.getString(R.string.streaming_home_no_content_hint),
                                        actionLabel = null,
                                        onRefresh = null
                                    )
                                }
                                else -> HorizontalPager(
                                    state = pagerState,
                                    contentPadding = PaddingValues(0.dp),
                                    pageSpacing = 0.dp,
                                    modifier = Modifier.fillMaxSize()
                                ) { page ->
                                when (visibleTabIds.getOrNull(page)) {
                                    "SONGS" -> {
                                        SingleCardSongsContent(
                                        songs = filteredSongs,
                                        paginatedSongs = musicViewModel.paginatedSongs,
                                        listState = songsListState,
                                        albums = albums,
                                        artists = artists,
                                        onSongClick = onSongClick,
                                        onAddToPlaylist = { song ->
                                            songsToAddToPlaylist = listOf(song)
                                            showAddToPlaylistSheet = true
                                        },
                                        onAddToQueue = { song ->
                                            if (isStreamingMode) onStreamingAddToQueue?.invoke(song) else onAddToQueue(song)
                                        },
                                        onPlayNext = { song ->
                                            if (isStreamingMode) onStreamingPlayNext?.invoke(song) else musicViewModel.playNext(song)
                                        },
                                        onToggleFavorite = if (isStreamingMode) onStreamingToggleFavorite else { song ->
                                            musicViewModel.toggleFavorite(song)
                                        },
                                        favoriteSongs = favoriteSongs,
                                        onGoToArtist = onArtistClick,
                                        onGoToAlbum = onAlbumClick,
                                        onShowSongInfo = { song ->
                                            selectedSong = song
                                            showSongInfoSheet = true
                                        },
                                        onAddToBlacklist = if (isStreamingMode) null else { song ->
                                            appSettings.addToBlacklist(song.id)
                                        },
                                        onDeleteSong = if (isStreamingMode) null else { song ->
                                            musicViewModel.deleteSong(song)
                                        },
                                        onPlayQueue = onPlayQueue,
                                            onPlayQueueFromIndex = onPlayQueueFromIndex,
                                            onShuffleQueue = onShuffleQueue,
                                            currentSong = currentSong,
                                            isPlaying = isPlaying,
                                            haptics = haptics,
                                            isSelectionMode = isSelectionMode,
                                            selectedSongIds = selectedSongIds,
                                            multiSelectionState = multiSelectionState,
                                            onSongLongPress = onSongLongPress,
                                            onSongSelectionToggle = onSongSelectionToggle,
                                            onShowMultiSelectionSheet = { showMultiSelectionSheet = true },
                                            onRefreshClick = onRefreshClick,
                                            bottomPadding = adjustedSongsBottomPadding,
                                            sortOrder = sortOrder,
                                            isStreamingMode = isStreamingMode,
                                            streamingDownloadedSongIds = streamingDownloadedSongIds,
                                            streamingDownloadingSongIds = streamingDownloadingSongIds,
                                            onStreamingToggleDownload = onStreamingToggleDownload
                                        )
                                    }
                                    "LIKED" -> SingleCardSongsContent(
                                        songs = likedSongs,
                                        paginatedSongs = musicViewModel.paginatedSongs,
                                        listState = likedSongsListState,
                                        albums = albums,
                                        artists = artists,
                                        onSongClick = onSongClick,
                                        onAddToPlaylist = { song ->
                                            songsToAddToPlaylist = listOf(song)
                                            showAddToPlaylistSheet = true
                                        },
                                        onAddToQueue = { song ->
                                            if (isStreamingMode) onStreamingAddToQueue?.invoke(song) else onAddToQueue(song)
                                        },
                                        onPlayNext = { song ->
                                            if (isStreamingMode) onStreamingPlayNext?.invoke(song) else musicViewModel.playNext(song)
                                        },
                                        onToggleFavorite = if (isStreamingMode) onStreamingToggleFavorite else { song ->
                                            musicViewModel.toggleFavorite(song)
                                        },
                                        favoriteSongs = favoriteSongs,
                                        onGoToArtist = onArtistClick,
                                        onGoToAlbum = onAlbumClick,
                                        onShowSongInfo = { song ->
                                            selectedSong = song
                                            showSongInfoSheet = true
                                        },
                                        onAddToBlacklist = if (isStreamingMode) null else { song ->
                                            appSettings.addToBlacklist(song.id)
                                        },
                                        onDeleteSong = if (isStreamingMode) null else { song ->
                                            musicViewModel.deleteSong(song)
                                        },
                                        onPlayQueue = onPlayQueue,
                                            onPlayQueueFromIndex = onPlayQueueFromIndex,
                                            onShuffleQueue = onShuffleQueue,
                                            currentSong = currentSong,
                                            isPlaying = isPlaying,
                                            haptics = haptics,
                                            isSelectionMode = isSelectionMode,
                                            selectedSongIds = selectedSongIds,
                                            multiSelectionState = multiSelectionState,
                                            onSongLongPress = onSongLongPress,
                                            onSongSelectionToggle = onSongSelectionToggle,
                                            onShowMultiSelectionSheet = { showMultiSelectionSheet = true },
                                            onRefreshClick = onRefreshClick,
                                            bottomPadding = adjustedSongsBottomPadding,
                                            sortOrder = sortOrder,
                                            emptyMessage = context.getString(R.string.library_no_liked_songs),
                                            emptySubtitle = context.getString(R.string.library_no_liked_songs_desc),
                                            showEmptyRefresh = false,
                                            isStreamingMode = isStreamingMode,
                                            streamingDownloadedSongIds = streamingDownloadedSongIds,
                                            streamingDownloadingSongIds = streamingDownloadingSongIds,
                                            onStreamingToggleDownload = onStreamingToggleDownload
                                        )
                                    "PLAYLISTS" -> SingleCardPlaylistsContent(
                                        playlists = playlists,
                                        onPlaylistClick = onPlaylistClick,
                                        listState = playlistsListState,
                                        gridState = playlistsGridState,
                                        haptics = haptics,
                                        onCreatePlaylist = { showCreatePlaylistDialog = true },
                                        onImportPlaylist = if (isStreamingMode) null else ({ showImportDialog = true }),
                                        onExportPlaylists = if (isStreamingMode) null else ({ showBulkExportDialog = true }),
                                        appSettings = appSettings,
                                        onRefreshClick = onRefreshClick,
                                        bottomPadding = baseLibraryBottomPadding
                                    )
                                    "ALBUMS" -> {
                                        SingleCardAlbumsContent(
                                            albums = sortedAlbums,
                                            onAlbumClick = onAlbumClick,
                                            listState = albumsListState,
                                            gridState = albumsGridState,
                                            onSongClick = onSongClick,
                                            onAlbumBottomSheetClick = onAlbumBottomSheetClick,
                                            haptics = haptics,
                                            appSettings = appSettings,
                                            onPlayQueue = onPlayQueue,
                                            onShuffleQueue = onShuffleQueue,
                                            onRefreshClick = onRefreshClick,
                                            bottomPadding = adjustedSongsBottomPadding,
                                            sortOrder = sortOrder
                                        )
                                    }
                                    "ARTISTS" -> SingleCardArtistsContent(
                                        artists = artists,
                                        onArtistClick = { artist ->
                                            onNavigateToArtist(artist)
                                        },
                                        listState = artistsListState,
                                        gridState = artistsGridState,
                                        haptics = haptics,
                                        onPlayQueue = onPlayQueue,
                                        onShuffleQueue = onShuffleQueue,
                                        onRefreshClick = onRefreshClick,
                                        bottomPadding = baseLibraryBottomPadding,
                                        initialSortOption = artistSortOption,
                                        onSortOptionChange = { artistSortOption = it }
                                    )
                                    "ALBUM_ARTISTS" -> SingleCardArtistsContent(
                                        artists = sortedAlbumArtists,
                                        onArtistClick = { artist ->
                                            onNavigateToArtist(artist)
                                        },
                                        listState = albumArtistsListState,
                                        gridState = albumArtistsGridState,
                                        haptics = haptics,
                                        onPlayQueue = onPlayQueue,
                                        onShuffleQueue = onShuffleQueue,
                                        onRefreshClick = onRefreshClick,
                                        bottomPadding = baseLibraryBottomPadding,
                                        initialSortOption = artistSortOption,
                                        onSortOptionChange = { artistSortOption = it }
                                    )
                                    "DATES" -> YearGroupedSongsContent(
                                    songs = filteredSongs,
                                    albums = albums,
                                    listState = datesListState,
                                    onSongClick = onSongClick,
                                    onAddToPlaylist = { song ->
                                        songsToAddToPlaylist = listOf(song)
                                        showAddToPlaylistSheet = true
                                    },
                                    onAddToQueue = onAddToQueue,
                                    onPlayNext = { song -> musicViewModel.playNext(song) },
                                    onToggleFavorite = { song -> musicViewModel.toggleFavorite(song) },
                                    favoriteSongs = musicViewModel.favoriteSongs.collectAsState().value,
                                    onGoToArtist = onArtistClick,
                                    onGoToAlbum = onAlbumClick,
                                    onShowSongInfo = { song ->
                                        selectedSong = song
                                        showSongInfoSheet = true
                                    },
                                    onAddToBlacklist = { song ->
                                        appSettings.addToBlacklist(song.id)
                                    },
                                    onDeleteSong = { musicViewModel.deleteSong(it) },
                                    onPlayQueue = onPlayQueue,
                                        onPlayQueueFromIndex = onPlayQueueFromIndex,
                                        onShuffleQueue = onShuffleQueue,
                                        currentSong = currentSong,
                                        isPlaying = isPlaying,
                                        haptics = haptics,
                                        isSelectionMode = isSelectionMode,
                                        selectedSongIds = selectedSongIds,
                                        multiSelectionState = multiSelectionState,
                                        onSongLongPress = onSongLongPress,
                                        onSongSelectionToggle = onSongSelectionToggle,
                                        onShowMultiSelectionSheet = { showMultiSelectionSheet = true },
                                        onRefreshClick = onRefreshClick,
                                        bottomPadding = adjustedSongsBottomPadding,
                                        sortOrder = sortOrder,
                                        isStreamingMode = isStreamingMode,
                                        streamingDownloadedSongIds = streamingDownloadedSongIds,
                                        streamingDownloadingSongIds = streamingDownloadingSongIds,
                                        onStreamingToggleDownload = onStreamingToggleDownload
                                    )
                                    "EXPLORER" -> SingleCardExplorerContent(
                                        songs = songs,
                                        onSongClick = onSongClick,
                                        listState = explorerListState,
                                        onAddToPlaylist = { song ->
                                            songsToAddToPlaylist = listOf(song)
                                            showAddToPlaylistSheet = true
                                        },
                                        onAddToQueue = onAddToQueue,
                                        onShowSongInfo = { song ->
                                            selectedSong = song
                                            showSongInfoSheet = true
                                        },
                                        onPlayQueue = onPlayQueue,
                                        onPlayQueueFromIndex = onPlayQueueFromIndex,
                                        onShuffleQueue = onShuffleQueue,
                                        haptics = haptics,
                                        appSettings = appSettings,
                                        reloadTrigger = explorerReloadTrigger,
                                        onCreatePlaylist = onCreatePlaylist,
                                        musicViewModel = musicViewModel,
                                        currentSong = currentSong,
                                        isPlaying = isPlaying,
                                        currentPath = explorerPath,
                                        onPathChanged = { explorerPath = it },
                                        onFolderSongsChanged = { explorerFolderSongs = it },
                                        bottomPadding = adjustedSongsBottomPadding,
                                        isSelectionMode = isSelectionMode,
                                        selectedSongIds = selectedSongIds,
                                        onSongLongPress = onSongLongPress,
                                        onSongSelectionToggle = onSongSelectionToggle,
                                        multiSelectionState = multiSelectionState
                                    )
                                }
                            }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.background,
                                                MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                                MaterialTheme.colorScheme.background.copy(alpha = 0.32f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .zIndex(5f)
                            )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isMediaScanning,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                            modifier = with(this@Box) {
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                            }
                        ) {
                            LibraryScanProgressBanner(musicViewModel = musicViewModel)
                        }



                        val activeTabId = visibleTabIds.getOrNull(pagerState.currentPage) ?: ""
                        val hasContent = when (activeTabId) {
                            "SONGS" -> songs.isNotEmpty()
                            "LIKED" -> likedSongs.isNotEmpty()
                            "ALBUMS" -> albums.isNotEmpty()
                            "ARTISTS" -> false
                            "ALBUM_ARTISTS" -> albumArtists.isNotEmpty()
                            "DATES" -> songs.isNotEmpty()
                            "EXPLORER" -> explorerPath != null || explorerFolderSongs.isNotEmpty()
                            else -> false
                        }
                        val songsScrollingUp = songsListState.isScrollingUp()
                        val likedSongsScrollingUp = likedSongsListState.isScrollingUp()
                        val albumsListScrollingUp = albumsListState.isScrollingUp()
                        val albumsGridScrollingUp = albumsGridState.isScrollingUp()
                        val explorerScrollingUp = explorerListState.isScrollingUp()
                        val songsScrollInProgress = songsListState.isScrollInProgress
                        val likedSongsScrollInProgress = likedSongsListState.isScrollInProgress
                        val albumsListScrollInProgress = albumsListState.isScrollInProgress
                        val albumsGridScrollInProgress = albumsGridState.isScrollInProgress
                        val explorerScrollInProgress = explorerListState.isScrollInProgress
                        val shouldShowBottomBar = if (isSelectionMode) {
                            hasContent
                        } else {
                            showLibraryBottomBarAlways && hasContent && when (activeTabId) {
                                "SONGS" -> !songsScrollInProgress || songsScrollingUp
                                "LIKED" -> !likedSongsScrollInProgress || likedSongsScrollingUp
                                "DATES" -> !songsScrollInProgress || songsScrollingUp
                                "ALBUMS" -> {
                                    val isScrollingUp = if (albumViewType == AlbumViewType.GRID) albumsGridScrollingUp else albumsListScrollingUp
                                    val isScrolling = if (albumViewType == AlbumViewType.GRID) albumsGridScrollInProgress else albumsListScrollInProgress
                                    !isScrolling || isScrollingUp
                                }
                                "EXPLORER" -> !explorerScrollInProgress || explorerScrollingUp
                                else -> true
                            }
                        }
                        
                            val locateScope = rememberCoroutineScope()
                            val showLocateButton = !shouldShowBottomBar && !isListAtTop

                            androidx.compose.animation.AnimatedVisibility(
                                visible = showLocateButton,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 20.dp, bottom = fabBottomPadding)
                                    .zIndex(10f)
                            ) {
                                FloatingActionButton(
                                    onClick = {
                                            locateScope.launch {
                                                when (activeTabId) {
                                                    "SONGS" -> {
                                                        val idx = filteredSongs.indexOfFirst { it.id == currentSong?.id }
                                                        songsListState.animateScrollToItem(if (idx >= 0) idx else 0)
                                                    }
                                                    "LIKED" -> {
                                                        val idx = likedSongs.indexOfFirst { it.id == currentSong?.id }
                                                        likedSongsListState.animateScrollToItem(if (idx >= 0) idx else 0)
                                                    }
                                                    "DATES" -> datesListState.animateScrollToItem(0)
                                                    "PLAYLISTS" -> {
                                                        if (playlistViewType == PlaylistViewType.GRID) playlistsGridState.animateScrollToItem(0)
                                                        else playlistsListState.animateScrollToItem(0)
                                                    }
                                                    "ALBUMS" -> {
                                                        if (albumViewType == AlbumViewType.GRID) albumsGridState.animateScrollToItem(0)
                                                        else albumsListState.animateScrollToItem(0)
                                                    }
                                                    "ARTISTS" -> {
                                                        if (artistViewType == ArtistViewType.GRID) artistsGridState.animateScrollToItem(0)
                                                        else artistsListState.animateScrollToItem(0)
                                                    }
                                                    "ALBUM_ARTISTS" -> {
                                                        if (artistViewType == ArtistViewType.GRID) albumArtistsGridState.animateScrollToItem(0)
                                                        else albumArtistsListState.animateScrollToItem(0)
                                                    }
                                                    "EXPLORER" -> explorerListState.animateScrollToItem(0)
                                                }
                                        }
                                    },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    val locateIcon = if (activeTabId == "SONGS") {
                                        MaterialSymbolIcon("my_location", filled = true)
                                    } else {
                                        RhythmIcons.ArrowUpward
                                    }
                                    val locateDesc = if (activeTabId == "SONGS") "Locate current song" else "Scroll to top"
                                    Icon(
                                        imageVector = locateIcon,
                                        contentDescription = locateDesc,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            LibraryBottomBar(
                                isVisible = shouldShowBottomBar,
                            activeTab = activeTabId,
                            songs = bottomBarSongs,
                            isSelectionMode = isSelectionMode,
                            selectedSongsCount = selectedSongs.size,
                            explorerPath = explorerPath,
                            onSelectToggle = {
                                if (bottomBarSongs.isNotEmpty()) {
                                    multiSelectionState.toggleSelection(bottomBarSongs.first())
                                }
                            },
                            onCancelSelection = {
                                multiSelectionState.clearSelection()
                            },
                            onPlayAll = {
                                if (bottomBarSongs.isNotEmpty()) onPlayQueue(bottomBarSongs)
                            },
                            onShuffle = {
                                if (bottomBarSongs.isNotEmpty()) onShuffleQueue(bottomBarSongs)
                            },
                            onPlaySelected = {
                                if (selectedSongs.isNotEmpty()) {
                                    onPlayQueueFromIndex(selectedSongs, 0)
                                    multiSelectionState.clearSelection()
                                }
                            },
                            onMoreActions = {
                                showMultiSelectionSheet = true
                            },
                            onBack = {
                                explorerPath?.let { path ->
                                    explorerPath = getParentPath(path)
                                }
                            },
                            modifier = with(this@Box) {
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .zIndex(10f)
                            }
                        )
                    }
                }
            }
        }
    }
    }
    
    if (showBulkExportDialog && onExportAllPlaylists != null) {
        BulkPlaylistExportDialog(
            playlistCount = playlists.size,
            onDismiss = { 
                showBulkExportDialog = false
                operationError = null
            },
            onExport = { format, includeDefault ->
                showBulkExportDialog = false
                showOperationProgress = true
                operationProgressText = context.getString(R.string.exporting_playlists)
                
                onExportAllPlaylists(format, includeDefault, null) { result ->
                    showOperationProgress = false
                    result.fold(
                        onSuccess = { message ->
                        },
                        onFailure = { error ->
                            operationError = error.message ?: "Export failed"
                        }
                    )
                }
            },
            onExportToCustomLocation = { format, includeDefault, directoryUri ->
                showBulkExportDialog = false
                showOperationProgress = true
                operationProgressText = context.getString(R.string.exporting_to_location)
                
                onExportAllPlaylists(format, includeDefault, directoryUri) { result ->
                    showOperationProgress = false
                    result.fold(
                        onSuccess = { message ->
                        },
                        onFailure = { error ->
                            operationError = error.message ?: "Export failed"
                        }
                    )
                }
            }
        )
    }
    
    if (showImportDialog && onImportPlaylist != null) {
        PlaylistImportDialog(
            onDismiss = { 
                showImportDialog = false
                operationError = null
            },
            onImport = { uri, onResult, onRestartRequired ->
                showImportDialog = false
                showOperationProgress = true
                operationProgressText = context.getString(R.string.importing_playlist)
                onImportPlaylist(uri, { result ->
                    showOperationProgress = false
                    result.fold(
                        onSuccess = { message ->
                            operationResult = Pair(message, true)
                            showRestartDialog = true
                        },
                        onFailure = { error ->
                            operationError = error.message ?: "Import failed"
                        }
                    )
                    onResult(result)
                }, onRestartRequired)
            }
        )
    }

    if (showRestartDialog && onRestartApp != null) {
        AppRestartDialog(
            onDismiss = { showRestartDialog = false },
            onRestart = {
                showRestartDialog = false
                onRestartApp()
            },
            onContinue = {
                showRestartDialog = false
            }
        )
    }

    if (showOperationProgress) {
        PlaylistOperationProgressDialog(
            operation = operationProgressText,
            onDismiss = {
                showOperationProgress = false
                operationProgressText = ""
            }
        )
    }
    
    if (operationError != null) {
        AlertDialog(
            onDismissRequest = { operationError = null },
            icon = {
                Icon(
                    imageVector = MaterialSymbolIcon("error", filled = true),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = { Text(stringResource(R.string.updates_status_error)) },
            text = { Text(operationError!!) },
            confirmButton = {
                Button(onClick = { operationError = null }) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_ok))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showImportResultDialog && importResult != null) {
        AlertDialog(
            onDismissRequest = { showImportResultDialog = false; importResult = null },
            icon = {
                Icon(
                    imageVector = MaterialSymbolIcon("restart_alt", filled = true),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = { Text(stringResource(R.string.import_complete_title)) },
            text = {
                val (count, message) = importResult!!
                Text(pluralStringResource(R.plurals.playlist_import_success, count, count, message))
            },
            confirmButton = {
                Button(onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    showImportResultDialog = false
                    importResult = null
                    AppRestarter.restartApp(context)
                }) {
                    Icon(
                        imageVector = MaterialSymbolIcon("restart_alt", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.crash_restart_app))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    showImportResultDialog = false
                    importResult = null
                }) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bottomsheet_lyrics_later))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun SingleCardSongsContent(
    songs: List<Song>,
    paginatedSongs: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Song>>? = null,
    listState: LazyListState = rememberLazyListState(),
    albums: List<Album> = emptyList(),
    artists: List<Artist> = emptyList(),
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onToggleFavorite: ((Song) -> Unit)? = null,
    favoriteSongs: Set<String> = emptySet(),
    onGoToArtist: (Artist) -> Unit = {},
    onGoToAlbum: (Album) -> Unit = {},
    onShowSongInfo: (Song) -> Unit,
    onAddToBlacklist: ((Song) -> Unit)? = null,
    onDeleteSong: ((Song) -> Unit)? = null,
    onPlayQueue: (List<Song>) -> Unit = { _ -> },
    onPlayQueueFromIndex: (List<Song>, Int) -> Unit = { _, _ -> },
    onShuffleQueue: (List<Song>) -> Unit = { _ -> },
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    multiSelectionState: chromahub.rhythm.app.features.local.presentation.viewmodel.MultiSelectionStateHolder? = null,
    onSongLongPress: (Song) -> Unit = {},
    onSongSelectionToggle: (Song) -> Unit = {},
    onShowMultiSelectionSheet: () -> Unit = {},
    onRefreshClick: (() -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    songMenuContent: (@Composable (song: Song, dismissMenu: () -> Unit) -> Unit)? = null,
    sortOrder: MusicViewModel.SortOrder = MusicViewModel.SortOrder.TITLE_ASC,
    emptyMessage: String? = null,
    emptySubtitle: String? = null,
    showEmptyRefresh: Boolean = true,
    isStreamingMode: Boolean = false,
    streamingDownloadedSongIds: Set<String> = emptySet(),
    streamingDownloadingSongIds: Set<String> = emptySet(),
    onStreamingToggleDownload: ((Song) -> Unit)? = null
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val groupByAlbumArtist by appSettings.groupByAlbumArtist.collectAsState()
    
    val selectedSongs = multiSelectionState?.selectedSongs?.collectAsState()?.value ?: emptyList()
    
    val isLoading = false
    val preparedSongs = remember(songs) {
        songs.distinctBy { "${it.id}_${it.uri}" }
    }
    
    val splitArtistNames: (String) -> List<String> = remember {
        { artistName ->
            val libAppSettings = AppSettings.getInstance(context)
            chromahub.rhythm.app.util.ArtistSeparator.splitArtistNames(
                artistName = artistName,
                delimiters = libAppSettings.artistSeparatorDelimiters.value,
                enabled = libAppSettings.artistSeparatorEnabled.value
            )
        }
    }
    
    val audioQualityCache = remember { mutableMapOf<String, AudioQualityDetector.AudioQuality>() }
    
    suspend fun getAudioQuality(song: Song): AudioQualityDetector.AudioQuality {
        audioQualityCache[song.id]?.let { return it }
        
        return withContext(Dispatchers.IO) {
            try {
                val formatInfo = AudioFormatDetector.detectFormat(context, song.uri, song)

                val songBitrate = song.bitrate ?: 0
                val songSampleRate = song.sampleRate ?: 0
                val songChannels = song.channels ?: 0
                
                val bitrateKbps = if (songBitrate > 0) {
                    songBitrate / 1000
                } else if (formatInfo.bitrateKbps > 0) {
                    formatInfo.bitrateKbps
                } else {
                    0
                }
                
                val sampleRateHz = if (songSampleRate > 0) {
                    songSampleRate
                } else if (formatInfo.sampleRateHz > 0) {
                    formatInfo.sampleRateHz
                } else {
                    0
                }
                
                val channelCount = if (songChannels > 0) {
                    songChannels
                } else if (formatInfo.channelCount > 0) {
                    formatInfo.channelCount
                } else {
                    2
                }
                
                val codec = formatInfo.codec.ifEmpty { song.codec ?: "Unknown" }
                val bitDepth = formatInfo.bitDepth
                
                val quality = AudioQualityDetector.detectQuality(
                    codec = codec,
                    sampleRateHz = sampleRateHz,
                    bitrateKbps = bitrateKbps,
                    bitDepth = bitDepth,
                    channelCount = channelCount
                )
                
                audioQualityCache[song.id] = quality
                quality
            } catch (e: Exception) {
                android.util.Log.w("SongsTab", "Error detecting audio quality for ${song.title}: ${e.message}")
                AudioQualityDetector.AudioQuality(
                    qualityType = AudioQualityDetector.QualityType.UNKNOWN,
                    isLossless = false,
                    isDolby = false,
                    isDTS = false,
                    isHiRes = false,
                    qualityLabel = "Unknown",
                    qualityDescription = "Quality could not be determined",
                    bitDepthEstimate = 0,
                    category = "Unknown"
                )
            }
        }
    }
    
    if (isLoading && preparedSongs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ContentLoadingIndicator(
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = context.getString(R.string.library_loading_songs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (preparedSongs.isEmpty()) {
        EmptyState(
            message = emptyMessage ?: context.getString(R.string.library_no_songs),
            subtitle = emptySubtitle ?: context.getString(R.string.library_start_collection),
            icon = RhythmIcons.Music.Song,
            onRefresh = if (showEmptyRefresh) onRefreshClick else null
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            val canScroll by remember(listState) {
                derivedStateOf { listState.shouldShowScrollbar() }
            }
            val animatedEndPadding by animateDpAsState(
                targetValue = if (canScroll) 36.dp else 16.dp,
                animationSpec = tween(durationMillis = 200)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = animatedEndPadding,
                    top = 16.dp,
                    bottom = bottomPadding + 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(
                    items = preparedSongs,
                    key = { _, song -> "song_${song.id}_${song.uri}" },
                    contentType = { _, _ -> "song" }
                ) { index, song ->
                    AnimateIn(modifier = Modifier.animateItem()) {
                        val isSelected = selectedSongIds.contains(song.id)
                        val selectionIndex = multiSelectionState?.getSelectionIndex(song.id)
                        
                        LibrarySongItemWrapper(
                            song = song,
                            onClick = {
                                if (isSelectionMode) {
                                    onSongSelectionToggle(song)
                                } else {
                                    val songIndex = preparedSongs.indexOf(song)
                                    if (songIndex >= 0) {
                                        onPlayQueueFromIndex(preparedSongs, songIndex)
                                    } else {
                                        onSongClick(song)
                                    }
                                }
                            },
                            onMoreClick = { onAddToPlaylist(song) },
                            onAddToQueue = { onAddToQueue(song) },
                            onPlayNext = { onPlayNext(song) },
                            onToggleFavorite = onToggleFavorite?.let { fn -> { fn(song) } },
                            isFavorite = favoriteSongs.contains(song.id),
                            onGoToArtist = { 
                                val artist = if (groupByAlbumArtist) {
                                    val explicitAlbumArtist = song.albumArtist?.trim().orEmpty()
                                    val songArtistNames = if (explicitAlbumArtist.isNotBlank() && !explicitAlbumArtist.equals("<unknown>", ignoreCase = true)) {
                                        splitArtistNames(explicitAlbumArtist)
                                    } else {
                                        splitArtistNames(song.artist)
                                    }
                                    songArtistNames.firstNotNullOfOrNull { name ->
                                        artists.find { it.name.equals(name, ignoreCase = true) }
                                    }
                                } else {
                                    val songArtistNames = splitArtistNames(song.artist)
                                    songArtistNames.firstNotNullOfOrNull { name ->
                                        artists.find { it.name.equals(name, ignoreCase = true) }
                                    }
                                }
                                artist?.let { onGoToArtist(it) }
                            },
                            onGoToAlbum = {
                                val album = albums.findAlbumForSong(song)
                                album?.let { onGoToAlbum(it) }
                            },
                            onShowSongInfo = { onShowSongInfo(song) },
                            onAddToBlacklist = onAddToBlacklist?.let { fn -> { fn(song) } },
                            onDeleteSong = onDeleteSong?.let { fn -> { fn(song) } },
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            haptics = haptics,
                            itemShape = groupedLibraryItemShape(index, preparedSongs.size),
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            selectionIndex = selectionIndex,
                            onLongPress = { onSongLongPress(song) },
                            customMenuContent = songMenuContent?.let { menuBuilder ->
                                { dismissMenu -> menuBuilder(song, dismissMenu) }
                            },
                            isDownloaded = isStreamingMode && streamingDownloadedSongIds.contains(song.id),
                            isDownloading = isStreamingMode && streamingDownloadingSongIds.contains(song.id),
                            onToggleDownload = if (isStreamingMode) ({ onStreamingToggleDownload?.invoke(song) }) else null
                        )
                    }
                }
            }

            val songFastScrollLabelProvider = remember(preparedSongs, sortOrder) {
                { index: Int ->
                    songFastScrollLabel(
                        song = preparedSongs.getOrNull(index),
                        sortOrder = sortOrder
                    )
                }
            }

            ExpressiveScrollBar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding + 16.dp),
                listState = listState,
                visible = canScroll,
                dragLabelProvider = songFastScrollLabelProvider
            )
        }
    }
}

@Composable
fun SingleCardPlaylistsContent(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onCreatePlaylist: (() -> Unit)? = null,
    onImportPlaylist: (() -> Unit)? = null,
    onExportPlaylists: (() -> Unit)? = null,
    appSettings: AppSettings,
    onRefreshClick: (() -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val playlistViewType by appSettings.playlistViewType.collectAsState()
    val playlistSortOrderString by appSettings.playlistSortOrder.collectAsState()
    val playlistSortOrder = try {
        LibraryPlaylistSortOrder.valueOf(playlistSortOrderString)
    } catch (e: Exception) {
        LibraryPlaylistSortOrder.NAME_ASC
    }
    
    var isLoading by remember(playlists, playlistSortOrder) { mutableStateOf(true) }
    var preparedPlaylists by remember(playlists, playlistSortOrder) { mutableStateOf<List<Playlist>>(emptyList()) }
    
    LaunchedEffect(playlists, playlistSortOrder) {
        preparedPlaylists = withContext(Dispatchers.Default) {
            val baseList = playlists.distinctBy { it.id }
            when (playlistSortOrder) {
                LibraryPlaylistSortOrder.NAME_ASC -> baseList.sortedBy { it.name.lowercase() }
                LibraryPlaylistSortOrder.NAME_DESC -> baseList.sortedByDescending { it.name.lowercase() }
                LibraryPlaylistSortOrder.DATE_CREATED_ASC -> baseList.sortedBy { it.dateCreated }
                LibraryPlaylistSortOrder.DATE_CREATED_DESC -> baseList.sortedByDescending { it.dateCreated }
                LibraryPlaylistSortOrder.SONG_COUNT_ASC -> baseList.sortedBy { it.songs.size }
                LibraryPlaylistSortOrder.SONG_COUNT_DESC -> baseList.sortedByDescending { it.songs.size }
            }
        }
        isLoading = false
    }
    
    if (isLoading && preparedPlaylists.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ContentLoadingIndicator(
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = context.getString(R.string.library_loading_playlists),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (preparedPlaylists.isEmpty()) {
        EmptyState(
            message = context.getString(R.string.library_no_playlists_yet),
            subtitle = context.getString(R.string.library_no_playlists_yet_desc),
            icon = RhythmIcons.Music.Playlist,
            onRefresh = onRefreshClick
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            val canScroll by remember(listState, gridState, playlistViewType) {
                derivedStateOf {
                    if (playlistViewType == PlaylistViewType.GRID) gridState.shouldShowScrollbar()
                    else listState.shouldShowScrollbar()
                }
            }
            val animatedEndPadding by animateDpAsState(
                targetValue = if (canScroll) 36.dp else 16.dp,
                animationSpec = tween(durationMillis = 200)
            )

            val playlistFastScrollLabelProvider = remember(preparedPlaylists, playlistSortOrder) {
                { index: Int ->
                    playlistFastScrollLabel(
                        playlist = preparedPlaylists.getOrNull(index),
                        sortOrder = playlistSortOrder
                    )
                }
            }

            if (playlistViewType == PlaylistViewType.GRID) {
                val gridWidthDp = windowScreenWidthDp()
                val columnsCount = remember(gridWidthDp) {
                    val cols = (gridWidthDp - 32 + 12) / (160 + 12)
                    maxOf(cols, 1)
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = animatedEndPadding,
                        top = 16.dp,
                        bottom = bottomPadding + 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = preparedPlaylists,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "playlist" }
                    ) { index, playlist ->
                        AnimateIn(modifier = Modifier.animateItem()) {
                            val shape = getLibraryResponsiveGridItemShape(index, preparedPlaylists.size, columnsCount)
                            PlaylistGridItem(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                haptics = haptics,
                                shape = shape
                            )
                        }
                    }
                }

                ExpressiveScrollBar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding + 16.dp),
                    gridState = gridState,
                    visible = canScroll,
                    dragLabelProvider = playlistFastScrollLabelProvider
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = animatedEndPadding,
                        top = 16.dp,
                        bottom = bottomPadding + 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {

                    itemsIndexed(
                        items = preparedPlaylists,
                        key = { _, playlist -> playlist.id },
                        contentType = { _, _ -> "playlist" }
                    ) { index, playlist ->
                        AnimateIn(modifier = Modifier.animateItem()) {
                            PlaylistItem(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                haptics = haptics,
                                itemShape = groupedLibraryItemShape(index, preparedPlaylists.size)
                            )
                        }
                    }
                }

                ExpressiveScrollBar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding + 16.dp),
                    listState = listState,
                    visible = canScroll,
                    dragLabelProvider = playlistFastScrollLabelProvider
                )
            }
        }
    }
}

@Composable
fun SingleCardAlbumsContent(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    onSongClick: (Song) -> Unit,
    onAlbumBottomSheetClick: (Album) -> Unit = {},
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    appSettings: AppSettings,
    onPlayQueue: (List<Song>) -> Unit = { _ -> },
    onShuffleQueue: (List<Song>) -> Unit = { _ -> },
    onRefreshClick: (() -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    sortOrder: MusicViewModel.SortOrder = MusicViewModel.SortOrder.TITLE_ASC
) {
    val context = LocalContext.current
    val albumViewType by appSettings.albumViewType.collectAsState()
    
    var isLoading by remember { mutableStateOf(true) }
    var preparedAlbums by remember { mutableStateOf(albums) }
    
    LaunchedEffect(albums) {
        isLoading = true
        preparedAlbums = withContext(Dispatchers.Default) {
            albums.distinctBy { it.id }
        }
        isLoading = false
    }
    
    if (isLoading && preparedAlbums.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ContentLoadingIndicator(
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = context.getString(R.string.library_loading_albums),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (preparedAlbums.isEmpty()) {
        EmptyState(
            message = context.getString(R.string.library_no_albums_yet),
            subtitle = context.getString(R.string.library_no_albums_yet_desc),
            icon = RhythmIcons.Music.Album,
            onRefresh = onRefreshClick
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            val canScroll by remember(listState, gridState, albumViewType) {
                derivedStateOf {
                    if (albumViewType == AlbumViewType.GRID) gridState.shouldShowScrollbar()
                    else listState.shouldShowScrollbar()
                }
            }
            val animatedEndPadding by animateDpAsState(
                targetValue = if (canScroll) 36.dp else 16.dp,
                animationSpec = tween(durationMillis = 200)
            )

            val albumFastScrollLabelProvider = remember(preparedAlbums, sortOrder) {
                { index: Int ->
                    albumFastScrollLabel(
                        album = preparedAlbums.getOrNull(index),
                        sortOrder = sortOrder
                    )
                }
            }

            if (albumViewType == AlbumViewType.GRID) {
                val gridWidthDp = windowScreenWidthDp()
                val columnsCount = remember(gridWidthDp) {
                    val cols = (gridWidthDp - 32 + 12) / (160 + 12)
                    maxOf(cols, 1)
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = animatedEndPadding,
                        top = 16.dp,
                        bottom = bottomPadding + 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = preparedAlbums,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "album" }
                    ) { index, album ->
                        AnimateIn(modifier = Modifier.animateItem()) {
                            val shape = getLibraryResponsiveGridItemShape(index, preparedAlbums.size, columnsCount)
                            AlbumGridItem(
                                album = album,
                                onClick = { onAlbumBottomSheetClick(album) },
                                onPlayClick = { onAlbumClick(album) },
                                haptics = haptics,
                                shape = shape
                            )
                        }
                    }
                }

                ExpressiveScrollBar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding + 16.dp),
                    gridState = gridState,
                    visible = canScroll,
                    dragLabelProvider = albumFastScrollLabelProvider
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = animatedEndPadding,
                        top = 16.dp,
                        bottom = bottomPadding + 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {

                    itemsIndexed(
                        items = preparedAlbums,
                        key = { _, album -> album.id },
                        contentType = { _, _ -> "album" }
                    ) { index, album ->
                        AnimateIn(modifier = Modifier.animateItem()) {
                            LibraryAlbumItem(
                                album = album,
                                onClick = { onAlbumBottomSheetClick(album) },
                                onPlayClick = { onAlbumClick(album) },
                                haptics = haptics,
                                itemShape = groupedLibraryItemShape(index, preparedAlbums.size)
                            )
                        }
                    }
                }

                ExpressiveScrollBar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding + 16.dp),
                    listState = listState,
                    visible = canScroll,
                    dragLabelProvider = albumFastScrollLabelProvider
                )
            }
        }
    }
}


@Composable
@Deprecated("Use SingleCardPlaylistsContent instead")
fun PlaylistsTab(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    if (playlists.isEmpty()) {
        EmptyState(
            message = context.getString(R.string.library_no_playlists_yet),
            subtitle = context.getString(R.string.library_no_playlists_yet_desc),
            icon = RhythmIcons.Music.Playlist
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = rememberExpressiveShapeFor(ExpressiveShapeTarget.PLAYER_CONTROLS),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = RhythmIcons.PlaylistFilled,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = context.getString(R.string.library_your_playlists),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "${playlists.size} ${if (playlists.size == 1) "playlist" else "playlists"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Surface(
                        modifier = Modifier
                            .height(2.dp)
                            .width(60.dp),
                        shape = RoundedCornerShape(1.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                    ) {}
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(
                        items = playlists,
                        key = { it.id }
                    ) { playlist ->
                        AnimateIn {
                            PlaylistItem(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                haptics = haptics
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumsTab(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumBottomSheetClick: (Album) -> Unit = {},
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val albumViewType by appSettings.albumViewType.collectAsState()

    if (albums.isEmpty()) {
        EmptyState(
            message = context.getString(R.string.library_no_albums_yet),
            subtitle = context.getString(R.string.library_no_albums_yet_desc),
            icon = RhythmIcons.Music.Album
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = rememberExpressiveShapeFor(ExpressiveShapeTarget.PLAYER_CONTROLS),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = RhythmIcons.Music.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = context.getString(R.string.library_your_albums),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${albums.size} ${if (albums.size == 1) "album" else "albums"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    FilledIconButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            val newViewType = if (albumViewType == AlbumViewType.LIST) AlbumViewType.GRID else AlbumViewType.LIST
                            appSettings.setAlbumViewType(newViewType)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (albumViewType == AlbumViewType.LIST) RhythmIcons.AppsGrid else RhythmIcons.List,
                            contentDescription = stringResource(R.string.cd_toggle_view_type),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        modifier = Modifier
                            .height(2.dp)
                            .width(60.dp),
                        shape = RoundedCornerShape(1.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                    ) {}
                }
            }

            val uniqueAlbums = remember(albums) { albums.distinctBy { it.id } }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (albumViewType == AlbumViewType.GRID) {
                    AlbumsGrid(
                        albums = uniqueAlbums,
                        onAlbumClick = { album ->
                            onAlbumBottomSheetClick(album)
                        },
                        onAlbumPlay = onAlbumClick,
                        onSongClick = onSongClick,
                        haptics = haptics
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(
                            items = uniqueAlbums,
                            key = { it.id }
                        ) { album ->
                            AnimateIn {
                                LibraryAlbumItem(
                                    album = album,
                                    onClick = { onAlbumBottomSheetClick(album) },
                                    onPlayClick = {
                                        onAlbumClick(album)
                                    },
                                    haptics = haptics
                                )
                            }
                        }
                    }
                }
            }
        }
    }
                    }


@Composable
fun LibrarySongItem(
    song: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit = {},
    onToggleFavorite: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onGoToArtist: () -> Unit = {},
    onGoToAlbum: () -> Unit = {},
    onShowSongInfo: () -> Unit,
    onAddToBlacklist: (() -> Unit)? = null,
    onDeleteSong: (() -> Unit)? = null,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    customMenuContent: (@Composable (dismissMenu: () -> Unit) -> Unit)? = null,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onToggleDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showDropdown by remember { mutableStateOf(false) }
    val isCurrentSong = currentSong?.id == song.id

    val titleColor by animateColorAsState(
        targetValue = if (isCurrentSong && !isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "titleColor"
    )
    val supportingColor by animateColorAsState(
        targetValue = if (isCurrentSong && !isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "supportingColor"
    )

    val moreButtonContainerColor by animateColorAsState(
        targetValue = if (isCurrentSong && !isSelected)
            MaterialTheme.colorScheme.onPrimary
        else
            MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(300),
        label = "moreButtonContainerColor"
    )
    val moreButtonContentColor by animateColorAsState(
        targetValue = if (isCurrentSong && !isSelected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onPrimaryContainer,
        animationSpec = tween(300),
        label = "moreButtonContentColor"
    )

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "selectionScaleAnimation"
    )

    val containerColorForSelection by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isCurrentSong -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 300),
        label = "containerColorAnimation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp)
        ) {
            Surface(
                shape = rememberExpressiveShapeFor(
                    ExpressiveShapeTarget.SONG_ART,
                    fallbackShape = MaterialTheme.shapes.large
                ),
                modifier = Modifier.size(60.dp),
                border = if (isCurrentSong && !isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimary) else null
            ) {
                M3ImageUtils.TrackImage(
                    imageUrl = song.artworkUri,
                    trackName = song.title,
                    modifier = Modifier.fillMaxSize(),
                    applyExpressiveShape = false
                )
            }
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            shape = rememberExpressiveShapeFor(
                                ExpressiveShapeTarget.SONG_ART,
                                fallbackShape = MaterialTheme.shapes.large
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectionIndex != null && selectionIndex >= 0) {
                        Text(
                            text = "${selectionIndex + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = RhythmIcons.CheckCircle,
                            contentDescription = stringResource(R.string.streaming_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else if (isCurrentSong && isPlaying) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .offset(x = 4.dp, y = 4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingEqIcon(
                            modifier = Modifier.size(width = 12.dp, height = 10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            isPlaying = isPlaying,
                            bars = 3
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isDownloaded) {
                    Icon(
                        imageVector = MaterialSymbolIcon("download_for_offline", filled = true),
                        contentDescription = stringResource(R.string.streaming_downloaded),
                        tint = if (isCurrentSong && !isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                } else if (isDownloading) {
                    val infiniteTransition = rememberInfiniteTransition(label = "downloadIconTransition")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "downloadIconRotation"
                    )
                    Icon(
                        imageVector = MaterialSymbolIcon("sync"),
                        contentDescription = stringResource(R.string.streaming_downloading),
                        tint = if (isCurrentSong && !isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = rotation }
                    )
                }
                Text(
                    text = buildString {
                        append(song.artist)
                        append(" • ")
                        append(song.album)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = supportingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!isSelectionMode) {
            Box {
                FilledIconButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        showDropdown = true
                    },
                    modifier = Modifier
                        .width(32.dp)
                        .height(44.dp),
                    shape = RoundedCornerShape(50),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = moreButtonContainerColor,
                        contentColor = moreButtonContentColor
                    )
                ) {
                    Icon(
                        imageVector = RhythmIcons.More,
                        contentDescription = stringResource(R.string.content_desc_more_options),
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        showDropdown = false
                    },
                    modifier = Modifier
                        .widthIn(min = 220.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (customMenuContent != null) {
                        customMenuContent {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            showDropdown = false
                        }
                    } else {
                        RhythmSongMenuContent(
                            song = song,
                            onPlayNext = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                showDropdown = false
                                onPlayNext()
                            },
                            onAddToQueue = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                showDropdown = false
                                onAddToQueue()
                            },
                            isFavorite = isFavorite,
                            onToggleFavorite = onToggleFavorite?.let { action ->
                                {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showDropdown = false
                                    action()
                                }
                            },
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            onToggleDownload = onToggleDownload?.let { action ->
                                {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showDropdown = false
                                    action()
                                }
                            },
                            onAddToPlaylist = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                showDropdown = false
                                onMoreClick()
                            },
                            onShowSongInfo = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                showDropdown = false
                                onShowSongInfo()
                            },
                            onAddToBlacklist = onAddToBlacklist?.let { action ->
                                {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showDropdown = false
                                    action()
                                }
                            },
                            onDeleteSong = onDeleteSong?.let { action ->
                                {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    showDropdown = false
                                    action()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibrarySongItemWrapper(
    song: Song,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit = {},
    onToggleFavorite: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onGoToArtist: () -> Unit = {},
    onGoToAlbum: () -> Unit = {},
    onShowSongInfo: () -> Unit,
    onAddToBlacklist: (() -> Unit)? = null,
    onDeleteSong: (() -> Unit)? = null,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    itemShape: RoundedCornerShape = RoundedCornerShape(20.dp),
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    customMenuContent: (@Composable (dismissMenu: () -> Unit) -> Unit)? = null,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onToggleDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isCurrentSong = currentSong?.id == song.id
    
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "selectionScaleAnimation"
    )
    
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            isCurrentSong -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(300),
        label = "containerColor"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
            .graphicsLayer {
                scaleX = selectionScale
                scaleY = selectionScale
            }
            .combinedClickable(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onClick()
                },
                onLongClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onLongPress()
                }
            ),
        shape = itemShape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isDownloading) {
                val infiniteTransition = rememberInfiniteTransition(label = "downloadFlowTransition")
                val flowOffset by infiniteTransition.animateFloat(
                    initialValue = -0.5f,
                    targetValue = 1.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "downloadFlowOffset"
                )
                val primaryColor = MaterialTheme.colorScheme.primary
                val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithCache {
                            val brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    primaryContainerColor.copy(alpha = 0.35f),
                                    primaryColor.copy(alpha = 0.25f),
                                    primaryContainerColor.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                startX = size.width * (flowOffset - 0.4f),
                                endX = size.width * (flowOffset + 0.4f)
                            )
                            onDrawBehind {
                                drawRect(brush)
                            }
                        }
                )
            }

            LibrarySongItem(
                song = song,
                onClick = {},
                onMoreClick = onMoreClick,
                onAddToQueue = onAddToQueue,
                onPlayNext = onPlayNext,
                onToggleFavorite = onToggleFavorite,
                isFavorite = isFavorite,
                onGoToArtist = onGoToArtist,
                onGoToAlbum = onGoToAlbum,
                onShowSongInfo = onShowSongInfo,
                onAddToBlacklist = onAddToBlacklist,
                onDeleteSong = onDeleteSong,
                currentSong = currentSong,
                isPlaying = isPlaying,
                haptics = haptics,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                selectionIndex = selectionIndex,
                onLongPress = onLongPress,
                customMenuContent = customMenuContent,
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                onToggleDownload = onToggleDownload
            )

            if (isDownloading) {
                androidx.compose.material3.LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    itemShape: RoundedCornerShape = RoundedCornerShape(20.dp),
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    
    val albumArts = remember(playlist.id, playlist.songs.size) {
        playlist.songs
            .distinctBy { it.albumId }
            .take(4)
    }
    
    Surface(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 2.dp),
        shape = itemShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = rememberExpressiveShapeFor(
                    ExpressiveShapeTarget.PLAYLIST_ART,
                    fallbackShape = RoundedCornerShape(16.dp)
                ),
                tonalElevation = 0.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (playlist.artworkUri != null) {
                        M3ImageUtils.PlaylistImage(
                            imageUrl = playlist.artworkUri,
                            playlistName = playlist.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (albumArts.isNotEmpty()) {
                        PlaylistArtCollage(
                            songs = albumArts,
                            playlistName = playlist.name
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = RhythmIcons.PlaylistFilled,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${playlist.songs.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (playlist.songs.isNotEmpty()) {
                        val totalDurationMs = playlist.songs.sumOf { it.duration }
                        val totalMinutes = (totalDurationMs / (1000 * 60)).toInt()
                        val durationText = if (totalMinutes >= 60) {
                            val hours = totalMinutes / 60
                            val minutes = totalMinutes % 60
                            "${hours}h ${minutes}m"
                        } else {
                            "${totalMinutes}m"
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = durationText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 0.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RhythmIcons.Forward,
                        contentDescription = stringResource(R.string.cd_open_playlist),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

internal fun groupedLibraryItemShape(index: Int, totalCount: Int): RoundedCornerShape {
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

@Composable
fun PlaylistArtCollage(
    songs: List<Song>,
    playlistName: String
) {
    when (songs.size) {
        1 -> {
            M3ImageUtils.AlbumArt(
                imageUrl = songs[0].artworkUri,
                albumName = songs[0].album,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                applyExpressiveShape = false
            )
        }
        2 -> {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()) {
                    M3ImageUtils.AlbumArt(
                        imageUrl = songs[0].artworkUri,
                        albumName = songs[0].album,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(0.dp),
                        applyExpressiveShape = false
                    )
                }
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()) {
                    M3ImageUtils.AlbumArt(
                        imageUrl = songs[1].artworkUri,
                        albumName = songs[1].album,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(0.dp),
                        applyExpressiveShape = false
                    )
                }
            }
        }
        3 -> {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()) {
                    M3ImageUtils.AlbumArt(
                        imageUrl = songs[0].artworkUri,
                        albumName = songs[0].album,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(0.dp),
                        applyExpressiveShape = false
                    )
                }
                Column(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Box(modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()) {
                        M3ImageUtils.AlbumArt(
                            imageUrl = songs[1].artworkUri,
                            albumName = songs[1].album,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(0.dp),
                            applyExpressiveShape = false
                        )
                    }
                    Box(modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()) {
                        M3ImageUtils.AlbumArt(
                            imageUrl = songs[2].artworkUri,
                            albumName = songs[2].album,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(0.dp),
                            applyExpressiveShape = false
                        )
                    }
                }
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    Box(modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()) {
                        M3ImageUtils.AlbumArt(
                            imageUrl = songs[0].artworkUri,
                            albumName = songs[0].album,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(0.dp),
                            applyExpressiveShape = false
                        )
                    }
                    Box(modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()) {
                        M3ImageUtils.AlbumArt(
                            imageUrl = songs[1].artworkUri,
                            albumName = songs[1].album,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(0.dp),
                            applyExpressiveShape = false
                        )
                    }
                }
                Row(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    Box(modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()) {
                        M3ImageUtils.AlbumArt(
                            imageUrl = songs[2].artworkUri,
                            albumName = songs[2].album,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(0.dp),
                            applyExpressiveShape = false
                        )
                    }
                    Box(modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()) {
                        M3ImageUtils.AlbumArt(
                            imageUrl = songs[3].artworkUri,
                            albumName = songs[3].album,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(0.dp),
                            applyExpressiveShape = false
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryAlbumItem(
    album: Album,
    onClick: () -> Unit,
    onPlayClick: () -> Unit = {},
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    itemShape: RoundedCornerShape = RoundedCornerShape(20.dp),
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val artworkShape = rememberExpressiveShapeFor(
        ExpressiveShapeTarget.ALBUM_ART,
        fallbackShape = RoundedCornerShape(18.dp)
    )
    
    Surface(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 2.dp),
        shape = itemShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = artworkShape,
                tonalElevation = 0.dp,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (album.artworkUri != null) Color.Transparent
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (album.artworkUri != null) {
                        M3ImageUtils.AlbumArt(
                            imageUrl = album.artworkUri,
                            albumName = album.title,
                            modifier = Modifier.fillMaxSize(),
                            shape = artworkShape
                        )
                    } else {
                        Icon(
                            imageVector = RhythmIcons.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(18.dp))
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${album.numberOfSongs} Songs",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (album.year > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${album.year}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
            
            FilledIconButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onPlayClick()
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = RhythmIcons.Play,
                    contentDescription = stringResource(R.string.content_desc_play_album),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    icon: MaterialSymbolIcon,
    subtitle: String? = null,
    actionLabel: String? = null,
    onRefresh: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val animatedScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 100f
        ),
        label = "iconScale"
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 500,
            delayMillis = 100
        ),
        label = "alphaAnimation"
    )
    val cookieShape = rememberExpressiveShape(ExpressiveMaterialShape.COOKIE_12)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                    alpha = animatedAlpha
                },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 32.dp)
            ) {
                Surface(
                    shape = cookieShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = subtitle ?: context.getString(R.string.library_start_collection),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
                )

                if (onRefresh != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    ExpressiveFilledButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            onRefresh()
                        },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(actionLabel ?: context.getString(R.string.cd_refresh))
                    }
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

private fun getLibraryResponsiveGridItemShape(index: Int, totalItems: Int, columnsCount: Int): RoundedCornerShape {
    if (totalItems <= 1) return RoundedCornerShape(24.dp)
    val totalRows = (totalItems + columnsCount - 1) / columnsCount
    val r = index / columnsCount
    val c = index % columnsCount
    val isTopRow = r == 0
    val isBottomRow = r == totalRows - 1
    val isLeftColumn = c == 0
    val isRightColumn = c == columnsCount - 1 || index == totalItems - 1
    val topStart = if (isTopRow && isLeftColumn) 24.dp else 8.dp
    val topEnd = if (isTopRow && isRightColumn) 24.dp else 8.dp
    val bottomStart = if (isBottomRow && isLeftColumn) 24.dp else 8.dp
    val bottomEnd = if (isBottomRow && isRightColumn) 24.dp else 8.dp
    return RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomStart = bottomStart, bottomEnd = bottomEnd)
}

@Composable
fun AlbumsGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onAlbumPlay: (Album) -> Unit,
    onSongClick: (Song) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val uniqueAlbums = remember(albums) { albums.distinctBy { it.id } }
    val gridWidthDp = windowScreenWidthDp()
    val columnsCount = remember(gridWidthDp) {
        val cols = (gridWidthDp - 32 + 12) / (160 + 12)
        maxOf(cols, 1)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 16.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(
            items = uniqueAlbums,
            key = { _, item -> item.id }
        ) { index, album ->
            AnimateIn {
                val shape = getLibraryResponsiveGridItemShape(index, uniqueAlbums.size, columnsCount)
                AlbumGridItem(
                    album = album,
                    onClick = { onAlbumClick(album) },
                    onPlayClick = { onAlbumPlay(album) },
                    haptics = haptics,
                    shape = shape
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    shape: Shape = RoundedCornerShape(20.dp)
) {
    val context = LocalContext.current
    
    Card(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = rememberExpressiveShapeFor(
                    ExpressiveShapeTarget.PLAYLIST_ART,
                    fallbackShape = RoundedCornerShape(16.dp)
                ),
                tonalElevation = 0.dp,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (playlist.songs.isNotEmpty()) {
                        PlaylistArtCollage(
                            songs = playlist.songs,
                            playlistName = playlist.name
                        )
                    } else {
                        Icon(
                            imageVector = RhythmIcons.PlaylistFilled,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = RhythmIcons.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "${playlist.songs.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit = {},
    shape: Shape = RoundedCornerShape(20.dp)
) {
    val context = LocalContext.current
    
    Card(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = rememberExpressiveShapeFor(
                        ExpressiveShapeTarget.ALBUM_ART,
                        fallbackShape = RoundedCornerShape(16.dp)
                    ),
                    tonalElevation = 0.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (album.artworkUri != null) Color.Transparent
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (album.artworkUri != null) {
                            M3ImageUtils.AlbumArt(
                                imageUrl = album.artworkUri,
                                albumName = album.title,
                                modifier = Modifier.fillMaxSize(),
                                applyExpressiveShape = false
                            )
                        } else {
                            Icon(
                                imageVector = RhythmIcons.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "${album.numberOfSongs}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (album.year > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "${album.year}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                FilledIconButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        onPlayClick()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Play,
                        contentDescription = stringResource(R.string.content_desc_play_album),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SingleCardArtistsContent(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onPlayQueue: (List<Song>) -> Unit = { _ -> },
    onShuffleQueue: (List<Song>) -> Unit = { _ -> },
    onRefreshClick: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    initialSortOption: ArtistSortOption = ArtistSortOption.NAME_ASC,
    onSortOptionChange: (ArtistSortOption) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = viewModel<chromahub.rhythm.app.viewmodel.MusicViewModel>()
    val appSettings = remember { AppSettings.getInstance(context) }
    
    val artistViewType by appSettings.artistViewType.collectAsState()
    
    var selectedCategory by remember { mutableStateOf("All") }
    var currentSortOption by remember(initialSortOption) { mutableStateOf(initialSortOption) }
    var showSortOptions by remember { mutableStateOf(false) }
    
    var isLoading by remember { mutableStateOf(true) }
    var sortedArtists by remember { mutableStateOf(artists) }
    
    val categories = remember(artists) {
        listOf("All")
    }
    
    LaunchedEffect(currentSortOption) {
        onSortOptionChange(currentSortOption)
    }
    
    LaunchedEffect(artists, currentSortOption) {
        isLoading = true
        sortedArtists = withContext(Dispatchers.Default) {
            val baseList = artists.distinctBy { it.id }
            when (currentSortOption) {
                ArtistSortOption.NAME_ASC -> baseList.sortedBy { it.name.lowercase() }
                ArtistSortOption.NAME_DESC -> baseList.sortedByDescending { it.name.lowercase() }
                ArtistSortOption.TRACK_COUNT_DESC -> baseList.sortedByDescending { it.numberOfTracks }
                ArtistSortOption.ALBUM_COUNT_DESC -> baseList.sortedByDescending { it.numberOfAlbums }
            }
        }
        isLoading = false
    }
    
    val isGridView = artistViewType == ArtistViewType.GRID
    
    if (isLoading && sortedArtists.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ContentLoadingIndicator(
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = context.getString(R.string.library_loading_artists),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    
    if (sortedArtists.isEmpty()) {
        EmptyState(
            message = context.getString(R.string.library_no_artists_yet),
            subtitle = context.getString(R.string.library_no_artists_yet_desc),
            icon = RhythmIcons.Artist,
            onRefresh = onRefreshClick
        )
        return
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        val canScroll by remember(listState, gridState, isGridView) {
            derivedStateOf {
                if (isGridView) gridState.shouldShowScrollbar()
                else listState.shouldShowScrollbar()
            }
        }
        val animatedEndPadding by animateDpAsState(
            targetValue = if (canScroll) 36.dp else 16.dp,
            animationSpec = tween(durationMillis = 200)
        )

        val artistFastScrollLabelProvider = remember(sortedArtists, currentSortOption) {
            { index: Int ->
                artistFastScrollLabel(
                    artist = sortedArtists.getOrNull(index),
                    sortOrder = currentSortOption
                )
            }
        }

        if (isGridView) {
            val gridWidthDp = windowScreenWidthDp()
            val columnsCount = remember(gridWidthDp) {
                val cols = (gridWidthDp - 32 + 12) / (160 + 12)
                maxOf(cols, 1)
            }
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = animatedEndPadding,
                    top = 16.dp,
                    bottom = bottomPadding + 80.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (sortedArtists.isNotEmpty()) {
                    itemsIndexed(
                        items = sortedArtists,
                        key = { _, item -> "gridartist_${item.id}" },
                        contentType = { _, _ -> "artist" }
                    ) { index, artist ->
                        AnimateIn(modifier = Modifier.animateItem()) {
                            val shape = getLibraryResponsiveGridItemShape(index, sortedArtists.size, columnsCount)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = shape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArtistGridCard(
                                    artist = artist,
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        onArtistClick(artist)
                                    },
                                    onPlayClick = {
                                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                        viewModel.playArtist(artist)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            ExpressiveScrollBar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding + 16.dp),
                gridState = gridState,
                visible = canScroll,
                dragLabelProvider = artistFastScrollLabelProvider
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = animatedEndPadding,
                    top = 16.dp,
                    bottom = bottomPadding + 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                
                if (sortedArtists.isNotEmpty()) {
                    itemsIndexed(
                        items = sortedArtists,
                        key = { _, artist -> "listartist_${artist.id}" },
                        contentType = { _, _ -> "artist" }
                    ) { index, artist ->
                        AnimateIn(modifier = Modifier.animateItem()) {
                            ArtistListCard(
                                artist = artist,
                                onClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    onArtistClick(artist)
                                },
                                onPlayClick = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                    viewModel.playArtist(artist)
                                },
                                itemShape = groupedLibraryItemShape(index, sortedArtists.size)
                            )
                        }
                    }
                }
            }

            ExpressiveScrollBar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding + 16.dp),
                listState = listState,
                visible = canScroll,
                dragLabelProvider = artistFastScrollLabelProvider
            )
        }
    }

    if (showSortOptions) {
        RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { showSortOptions = false },
            sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
            dragHandle = { 
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.primary
                )
            },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = context.getString(R.string.library_sort_artists),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                ArtistSortOption.entries.forEach { sortOption ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                HapticUtils.performHapticFeedback(
                                    context,
                                    haptics,
                                    HapticType.HEAVY
                                )
                                currentSortOption = sortOption
                                showSortOptions = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sortOption.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (currentSortOption == sortOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (currentSortOption == sortOption) {
                            Icon(
                                imageVector = RhythmIcons.Check,
                                contentDescription = stringResource(R.string.streaming_selected),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
@Composable
private fun ArtistSectionHeader(
    artistCount: Int,
    artists: List<Artist> = emptyList(),
    applyOuterHorizontalPadding: Boolean = true,
    onPlayAll: () -> Unit = {},
    onShuffleAll: () -> Unit = {},
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback? = null
) {
    val context = LocalContext.current

    ExpressiveSectionHeader(

/**
 * Shared expressive header used across library sections for a title, count badge, and trailing actions.
 */
        title = context.getString(R.string.library_your_artists),

/**
 * Artist-specific wrapper around the shared section header with shuffle actions when artists are available.
 */
        countText = "$artistCount ${if (artistCount == 1) "artist" else "artists"}",
        icon = RhythmIcons.Artist,
        countIcon = RhythmIcons.ArtistFilled,
        modifier = if (!applyOuterHorizontalPadding) Modifier.padding(horizontal = 0.dp) else Modifier
    ) {
        if (artists.isNotEmpty() && haptics != null) {
            ExpressiveFilledIconButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    onShuffleAll()
                },
                modifier = Modifier.size(56.dp),
                shape = ExpressiveShapes.SquircleMedium,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(
                    imageVector = RhythmIcons.Shuffle,
                    contentDescription = stringResource(R.string.libraryscreen_shuffle_artists),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

enum class ArtistSortOption(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    TRACK_COUNT_DESC("Songs (High to Low)"),
    ALBUM_COUNT_DESC("Albums (High to Low)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistGridCard(
    artist: Artist,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val artworkShape = rememberExpressiveShapeFor(ExpressiveShapeTarget.ARTIST_ART)
    
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                tonalElevation = 0.dp,
                color = Color.Transparent
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Image area - clipped to the expressive shape
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(
                                rememberExpressiveShapeFor(
                                    ExpressiveShapeTarget.ARTIST_ART,
                                    fallbackShape = RoundedCornerShape(16.dp)
                                )
                            )
                            .background(
                                if (artist.artworkUri != null) Color.Transparent
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        M3ImageUtils.ArtistImage(
                            imageUrl = artist.artworkUri,
                            artistName = artist.name,
                            modifier = Modifier.fillMaxSize(),
                            applyExpressiveShape = false
                        )
                    }
                    
                    // Play button - NOT clipped and no shadow
                    Surface(
                        onClick = onPlayClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Play,
                                contentDescription = stringResource(R.string.play_artist, artist.name),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "${artist.numberOfTracks}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    if (artist.numberOfAlbums > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Album,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "${artist.numberOfAlbums}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistListCard(
    artist: Artist,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemShape: RoundedCornerShape = RoundedCornerShape(20.dp),
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 2.dp),
        shape = itemShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3ImageUtils.ArtistImage(
                imageUrl = artist.artworkUri,
                artistName = artist.name,
                modifier = Modifier
                    .size(68.dp)
            )

            Spacer(modifier = Modifier.width(18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${artist.numberOfTracks}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    if (artist.numberOfAlbums > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Album,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${artist.numberOfAlbums} Albums",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            FilledIconButton(
                onClick = onPlayClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = RhythmIcons.Play,
                    contentDescription = stringResource(R.string.play_artist, artist.name),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


@Composable
fun PlaylistFabMenuContent(
    onCreatePlaylist: () -> Unit,
    onImportPlaylist: (() -> Unit)?,
    onExportPlaylists: (() -> Unit)?,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .widthIn(max = 200.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        if (onExportPlaylists != null) {
            FloatingActionButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    scope.launch {
                        onExportPlaylists()
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = MaterialSymbolIcon("file_upload"),
                    contentDescription = stringResource(R.string.cd_export_playlists),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (onImportPlaylist != null) {
            FloatingActionButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    scope.launch {
                        onImportPlaylist()
                    }
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = RhythmIcons.Actions.Download,
                    contentDescription = stringResource(R.string.cd_import_playlist),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        FloatingActionButton(
            onClick = {
                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                scope.launch {
                    onCreatePlaylist()
                }
            },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = RhythmIcons.Add,
                contentDescription = stringResource(R.string.cd_create_playlist),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PlaylistFabMenu(
    visible: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportPlaylist: (() -> Unit)?,
    onExportPlaylists: (() -> Unit)?,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    val menuItems = remember(onCreatePlaylist, onImportPlaylist, onExportPlaylists) {
        listOfNotNull(
            Triple("New playlist", RhythmIcons.Add, onCreatePlaylist),
            onImportPlaylist?.let {
                Triple("Import playlist", RhythmIcons.Actions.Download, it)
            },
            onExportPlaylists?.let {
                Triple("Export playlists", MaterialSymbolIcon("file_upload"), it)
            }
        )
    }

    FloatingActionButtonMenu(
        modifier = modifier.padding(bottom = bottomPadding + 8.dp),
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                modifier = Modifier
                    .semantics {
                        traversalIndex = -1f
                        stateDescription = if (expanded) "Expanded" else "Collapsed"
                    }
                    .animateFloatingActionButton(
                        visible = visible || expanded,
                        alignment = Alignment.BottomEnd
                    ),
                checked = expanded,
                onCheckedChange = onExpandedChange
            ) {
                val imageVector by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) {
                            RhythmIcons.Close
                        } else {
                            RhythmIcons.Add
                        }
                    }
                }
                Icon(
                    imageVector = imageVector,
                    contentDescription = if (expanded) "Close playlist menu" else "Open playlist menu",
                    modifier = Modifier
                        .size(24.dp)
                        .animateIcon({ checkedProgress })
                )
            }
        }
    ) {
        menuItems.forEachIndexed { index, item ->
            FloatingActionButtonMenuItem(
                modifier = Modifier.semantics {
                    isTraversalGroup = true
                    if (index == menuItems.lastIndex) {
                        customActions = listOf(
                            CustomAccessibilityAction(
                                label = "Close menu",
                                action = {
                                    onExpandedChange(false)
                                    true
                                }
                            )
                        )
                    }
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                    item.third.invoke()
                    onExpandedChange(false)
                },
                icon = {
                    Icon(
                        imageVector = item.second,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = { Text(text = item.first) }
            )
        }
    }
}


@Composable
fun FabMenuItem(
    label: String,
    icon: MaterialSymbolIcon,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    modifier: Modifier = Modifier,
    animationDelay: Int = 0
) {
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pressedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh),
        label = "pressedScale_$label"
    )

    val entranceScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "entranceScale_$label"
    )

    val entranceAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 300,
            delayMillis = animationDelay
        ),
        label = "entranceAlpha_$label"
    )

    Card(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
            isPressed = true
            onClick()
            scope.launch {
                kotlinx.coroutines.delay(100)
                isPressed = false
            }
        },
        shape = RoundedCornerShape(50.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 8.dp
        ),
        modifier = modifier
            .graphicsLayer {
                scaleX = entranceScale * pressedScale
                scaleY = entranceScale * pressedScale
                alpha = entranceAlpha
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        awaitRelease()
                        isPressed = false
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun BottomFloatingButtonGroup(
    modifier: Modifier = Modifier,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isPlayAllLoading by remember { mutableStateOf(false) }
    var isShuffleLoading by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (!isPlayAllLoading && !isShuffleLoading) {
                        isPlayAllLoading = true
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        scope.launch {
                            try {
                                onPlayAll()
                            } finally {
                                kotlinx.coroutines.delay(500)
                                isPlayAllLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                enabled = !isPlayAllLoading && !isShuffleLoading
            ) {
                if (isPlayAllLoading) {
                    ActionProgressLoader(
                        size = 20.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = RhythmIcons.Play,
                        contentDescription = stringResource(R.string.action_play_all),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = context.getString(R.string.library_play_all),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            ExpressiveFilledIconButton(
                onClick = {
                    if (!isPlayAllLoading && !isShuffleLoading) {
                        isShuffleLoading = true
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        scope.launch {
                            try {
                                onShuffle()
                            } finally {
                                kotlinx.coroutines.delay(500)
                                isShuffleLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier.size(52.dp),
                shape = ExpressiveShapes.SquircleMedium,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                enabled = !isPlayAllLoading && !isShuffleLoading
            ) {
                if (isShuffleLoading) {
                    ActionProgressLoader(
                        size = 24.dp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else {
                    Icon(
                        imageVector = RhythmIcons.Shuffle,
                        contentDescription = stringResource(R.string.cd_shuffle),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSectionHeader(
    title: String,
    countText: String,
    icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon,
    modifier: Modifier = Modifier,
    countIcon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    actionContent: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = rememberExpressiveShapeFor(
                ExpressiveShapeTarget.PLAYER_CONTROLS,
                fallbackShape = ExpressiveShapes.SquircleLarge
            ),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = ExpressiveElevation.Level2
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (countIcon != null) {
                        Icon(
                            imageVector = countIcon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actionContent
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSelectionHeader(
    selectedCount: Int,
    totalCount: Int,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    actionContent: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top= 12.dp, bottom = 8.dp),
        shape = ExpressiveShapes.Large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ExpressiveFilledIconButton(
                    onClick = onClearSelection,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        contentColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(imageVector = RhythmIcons.Close, contentDescription = stringResource(R.string.libraryscreen_clear_selection))
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pluralStringResource(R.plurals.library_selected_count_format, selectedCount, selectedCount),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
                    )
                    Text(
                        text = pluralStringResource(R.plurals.library_from_tracks_format, totalCount, totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Box(modifier = Modifier.padding(top = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actionContent
                )
            }
        }
    }
}

private class ScrollDirectionTracker(var previousIndex: Int = 0, var previousOffset: Int = 0)

@Composable
private fun LazyListState.isScrollingUp(): Boolean {
    val tracker = remember(this) { ScrollDirectionTracker(firstVisibleItemIndex, firstVisibleItemScrollOffset) }
    return remember {
        derivedStateOf {
            val currentIndex = firstVisibleItemIndex
            val currentOffset = firstVisibleItemScrollOffset
            val result = if (currentIndex != tracker.previousIndex) {
                currentIndex < tracker.previousIndex
            } else {
                currentOffset < tracker.previousOffset
            }
            tracker.previousIndex = currentIndex
            tracker.previousOffset = currentOffset
            result
        }
    }.value
}

@Composable
private fun LazyGridState.isScrollingUp(): Boolean {
    val tracker = remember(this) { ScrollDirectionTracker(firstVisibleItemIndex, firstVisibleItemScrollOffset) }
    return remember {
        derivedStateOf {
            val currentIndex = firstVisibleItemIndex
            val currentOffset = firstVisibleItemScrollOffset
            val result = if (currentIndex != tracker.previousIndex) {
                currentIndex < tracker.previousIndex
            } else {
                currentOffset < tracker.previousOffset
            }
            tracker.previousIndex = currentIndex
            tracker.previousOffset = currentOffset
            result
        }
    }.value
}

private enum class BottomBarButtonType { NONE, LEFT, CENTER, RIGHT }

@Composable
private fun LibraryBottomBar(
    isVisible: Boolean,
    activeTab: String,
    songs: List<Song>,
    isSelectionMode: Boolean,
    selectedSongsCount: Int,
    explorerPath: String?,
    onSelectToggle: () -> Unit,
    onCancelSelection: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlaySelected: () -> Unit,
    onMoreActions: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    var lastClickedButton by remember { mutableStateOf<BottomBarButtonType?>(null) }
    LaunchedEffect(lastClickedButton) {
        if (lastClickedButton != null && lastClickedButton != BottomBarButtonType.NONE) {
            delay(220L)
            lastClickedButton = BottomBarButtonType.NONE
        }
    }

    val leftScale by animateFloatAsState(
        targetValue = if (lastClickedButton == BottomBarButtonType.LEFT) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "leftScale"
    )
    val centerScale by animateFloatAsState(
        targetValue = if (lastClickedButton == BottomBarButtonType.CENTER) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "centerScale"
    )
    val rightScale by animateFloatAsState(
        targetValue = if (lastClickedButton == BottomBarButtonType.RIGHT) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "rightScale"
    )

    val centerWeightTarget = when (lastClickedButton) {
        BottomBarButtonType.CENTER -> 1.25f
        BottomBarButtonType.RIGHT -> 0.75f
        else -> 1f
    }
    val rightWeightTarget = when (lastClickedButton) {
        BottomBarButtonType.RIGHT -> 1.25f
        BottomBarButtonType.CENTER -> 0.75f
        else -> 1f
    }

    val centerWeight by animateFloatAsState(
        targetValue = centerWeightTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "centerWeight"
    )
    val rightWeight by animateFloatAsState(
        targetValue = rightWeightTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rightWeight"
    )

    val centerCornerTarget = if (lastClickedButton == BottomBarButtonType.CENTER) 14.dp else 24.dp
    val rightCornerTarget = if (lastClickedButton == BottomBarButtonType.RIGHT) 14.dp else 24.dp

    val centerCorner by animateDpAsState(
        targetValue = centerCornerTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "centerCorner"
    )
    val rightCorner by animateDpAsState(
        targetValue = rightCornerTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rightCorner"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it * 2 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { it * 2 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        val isTablet = windowScreenWidthDp() >= 600
        val baseBottomPadding = LocalMiniPlayerPadding.current.calculateBottomPadding()
        val bottomBarContext = LocalContext.current
        val localAppSettings = remember { AppSettings.getInstance(bottomBarContext) }
        val bottomPadding = if (isTablet) {
            12.dp
        } else {
            (baseBottomPadding - 4.dp).coerceAtLeast(0.dp)
        }
        Surface(
            modifier = if (isTablet) {
                Modifier
                    .width(440.dp)
                    .padding(bottom = bottomPadding)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = bottomPadding)
            },
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val showLeftButton = isSelectionMode || activeTab == "SONGS" || (activeTab == "EXPLORER" && explorerPath != null)
                
                AnimatedVisibility(
                    visible = showLeftButton,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            lastClickedButton = BottomBarButtonType.LEFT
                            if (isSelectionMode) {
                                onCancelSelection()
                            } else if (activeTab == "EXPLORER") {
                                onBack()
                            } else {
                                onSelectToggle()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = leftScale
                                scaleY = leftScale
                            },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        AnimatedContent(
                            targetState = isSelectionMode,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                            },
                            label = "leftButtonIcon"
                        ) { selectionMode ->
                            if (selectionMode) {
                                Icon(
                                    imageVector = RhythmIcons.Close,
                                    contentDescription = context.getString(R.string.library_bottom_bar_cancel_selection),
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (activeTab == "EXPLORER") RhythmIcons.Back else MaterialSymbolIcon("check_box"),
                                    contentDescription = if (activeTab == "EXPLORER") context.getString(R.string.library_bottom_bar_back) else context.getString(R.string.library_bottom_bar_select_songs),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                val centerButtonWeightTarget = if (isSelectionMode) 1f else centerWeightTarget
                val animatedCenterWeight by animateFloatAsState(
                    targetValue = centerButtonWeightTarget,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "animatedCenterWeight"
                )

                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        lastClickedButton = BottomBarButtonType.CENTER
                        if (isSelectionMode) {
                            onPlaySelected()
                        } else {
                            onPlayAll()
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .weight(animatedCenterWeight)
                        .graphicsLayer {
                            scaleX = centerScale
                            scaleY = centerScale
                        },
                    shape = RoundedCornerShape(centerCorner),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    AnimatedContent(
                        targetState = isSelectionMode,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(150, delayMillis = 75)) +
                                    scaleIn(initialScale = 0.8f, animationSpec = tween(150, delayMillis = 75)))
                                .togetherWith(fadeOut(animationSpec = tween(75)))
                        },
                        label = "centerButtonContent"
                    ) { selectionMode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Play,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectionMode) context.getString(R.string.library_play_selected, selectedSongsCount) else context.getString(R.string.library_play_all),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                val rightButtonWeightTarget = if (isSelectionMode) 0.001f else rightWeightTarget
                val animatedRightWeight by animateFloatAsState(
                    targetValue = rightButtonWeightTarget,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "animatedRightWeight"
                )

                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        lastClickedButton = BottomBarButtonType.RIGHT
                        if (isSelectionMode) {
                            onMoreActions()
                        } else {
                            onShuffle()
                        }
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .then(
                            if (isSelectionMode) {
                                Modifier.width(48.dp)
                            } else {
                                Modifier.weight(animatedRightWeight.coerceAtLeast(0.001f))
                            }
                        )
                        .graphicsLayer {
                            scaleX = rightScale
                            scaleY = rightScale
                        },
                    shape = RoundedCornerShape(if (isSelectionMode) 24.dp else rightCorner),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = if (isSelectionMode) PaddingValues(0.dp) else PaddingValues(horizontal = 16.dp)
                ) {
                    AnimatedContent(
                        targetState = isSelectionMode,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(150, delayMillis = 75)) +
                                    scaleIn(initialScale = 0.8f, animationSpec = tween(150, delayMillis = 75)))
                                .togetherWith(fadeOut(animationSpec = tween(75)))
                        },
                        label = "rightButtonContent"
                    ) { selectionMode ->
                        if (selectionMode) {
                            Icon(
                                imageVector = RhythmIcons.More,
                                contentDescription = context.getString(R.string.library_bottom_bar_more_actions),
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_shuffle),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

fun isLosslessAudio(song: Song): Boolean {
    val codec = song.codec?.uppercase() ?: ""

    if (codec.isNotEmpty()) {
        val isLossyCodec = codec.contains("MP3") || codec.contains("AAC") ||
                          codec.contains("OGG") || codec.contains("OPUS") ||
                          codec.contains("VORBIS") || codec.contains("AC-3") ||
                          codec.contains("AC3") || codec.contains("E-AC-3") ||
                          codec.contains("EAC3") || codec.contains("MP2") ||
                          codec.contains("AMR") ||
                          (codec.contains("WMA") && !codec.contains("LOSSLESS"))

        if (isLossyCodec) return false

        val isLosslessCodec = codec in listOf("ALAC", "FLAC", "PCM", "WAV", "APE", "DSD", "TRUEHD", "DOLBY ATMOS", "DTS-HD MA", "DTS:X", "AIFF", "MIDI", "WV", "TAK", "TTA") ||
                             codec.contains("LOSSLESS", ignoreCase = true) ||
                             codec.contains("APPLE LOSSLESS", ignoreCase = true)

        if (isLosslessCodec) return true
    }

    val uri = song.uri.toString()
    val isLosslessExtension = uri.endsWith(".flac", ignoreCase = true) ||
                              uri.endsWith(".wav", ignoreCase = true) ||
                              uri.endsWith(".alac", ignoreCase = true) ||
                              uri.endsWith(".ape", ignoreCase = true) ||
                              uri.endsWith(".aiff", ignoreCase = true) ||
                              uri.endsWith(".aif", ignoreCase = true) ||
                              uri.endsWith(".dsd", ignoreCase = true) ||
                              uri.endsWith(".wv", ignoreCase = true) ||
                              uri.endsWith(".tta", ignoreCase = true) ||
                              uri.endsWith(".tak", ignoreCase = true) ||
                              uri.endsWith(".mid", ignoreCase = true) ||
                              uri.endsWith(".midi", ignoreCase = true) ||
                              uri.endsWith(".dsf", ignoreCase = true) ||
                              uri.endsWith(".dff", ignoreCase = true)

    if (isLosslessExtension) return true

    return false
}

fun isHiResLossless(song: Song): Boolean {
    if (!isLosslessAudio(song)) {
        return false
    }

    val sampleRate = song.sampleRate ?: 0
    val bitrate = song.bitrate ?: 0
    val channels = song.channels ?: 2

    if (sampleRate < 48000) {
        return false
    }

    if (sampleRate >= 88200) {
        return true
    }

    if (bitrate > 0 && sampleRate > 0 && channels > 0) {
        val bitrateKbps = bitrate / 1000
        val calculatedBitDepth = (bitrateKbps * 1000) / (sampleRate * channels)
        if (calculatedBitDepth >= 18) {
            return true
        }
    }

    if (bitrate >= 2000000 && sampleRate >= 48000) {
        return true
    }

    return false
}

fun isRegularLossless(song: Song): Boolean {
    val lossless = isLosslessAudio(song)
    if (!lossless) return false
    
    val hiRes = isHiResLossless(song)
    if (hiRes) {
        return false
    }
    
    return true
}

fun isDolbyOrSurround(song: Song): Boolean {
    val codec = song.codec?.uppercase() ?: ""
    return (song.channels ?: 2) > 2 ||
           codec.contains("AC-3") ||
           codec.contains("E-AC-3") ||
           codec.contains("DOLBY") ||
           codec.contains("TRUEHD") ||
           codec.contains("ATMOS") ||
           codec.contains("DTS")
}

fun isDSD(song: Song): Boolean {
    val codec = song.codec?.uppercase() ?: ""
    if (codec.contains("DSD")) return true
    val uri = song.uri.toString()
    return uri.endsWith(".dsd", ignoreCase = true) ||
           uri.endsWith(".dsf", ignoreCase = true) ||
           uri.endsWith(".dff", ignoreCase = true)
}

fun isDTS(song: Song): Boolean {
    val codec = song.codec?.uppercase() ?: ""
    return codec.contains("DTS") &&
           !codec.contains("AC-3") &&
           !codec.contains("DOLBY") &&
           !codec.contains("TRUEHD") &&
           !codec.contains("ATMOS")
}

fun isDolbyAtmos(song: Song): Boolean {
    val codec = song.codec?.uppercase() ?: ""
    return codec.contains("ATMOS") || codec.contains("TRUEHD")
}

fun isDolbyDigital(song: Song): Boolean {
    val codec = song.codec?.uppercase() ?: ""
    return (codec.contains("AC-3") || codec.contains("AC3")) &&
           !codec.contains("ATMOS") &&
           !codec.contains("TRUEHD") &&
           !codec.contains("E-AC-3") &&
           !codec.contains("EAC3")
}

fun isDolbyDigitalPlus(song: Song): Boolean {
    val codec = song.codec?.uppercase() ?: ""
    return (codec.contains("E-AC-3") || codec.contains("EAC3")) &&
           !codec.contains("ATMOS") &&
           !codec.contains("TRUEHD")
}

fun isLossyAudio(song: Song): Boolean {
    val codec = song.codec?.uppercase() ?: ""
    if (codec.isEmpty()) return false
    val lossyCodecs = listOf("MP3", "AAC", "OGG", "OPUS", "VORBIS", "WMA")
    val isLossyCodec = lossyCodecs.any { codec.contains(it) } &&
                       !codec.contains("LOSSLESS")
    if (isLossyCodec) return true
    if (codec.contains("AC-3") || codec.contains("E-AC-3") || codec.contains("AC3") || codec.contains("EAC3") || codec.contains("AC4") || codec.contains("AC-4")) return true
    if (codec == "MP2" || codec.contains("AMR")) return true
    return false
}

fun isCDQuality(song: Song): Boolean {
    if (!isLosslessAudio(song)) return false
    if (isDolbyOrSurround(song)) return false
    if (isDSD(song)) return false
    val sampleRate = song.sampleRate ?: 0
    val channels = song.channels ?: 2
    if (sampleRate == 0) {
        val codec = song.codec?.uppercase() ?: ""
        return codec in listOf("ALAC", "FLAC", "PCM", "WAV", "AIFF")
    }
    if (sampleRate > 48000) return false
    if (channels > 2) return false
    val bitrate = song.bitrate ?: 0
    if (bitrate > 0) {
        val bitrateKbps = bitrate / 1000
        val estimatedBitDepth = if (sampleRate > 0 && channels > 0)
            (bitrateKbps * 1000) / (sampleRate * channels) else 0
        if (estimatedBitDepth >= 20) return false
    }
    return true
}

fun isStudioMaster(song: Song): Boolean {
    if (!isLosslessAudio(song)) return false
    if (isDolbyOrSurround(song)) return false
    if (isDSD(song)) return false
    val sampleRate = song.sampleRate ?: 0
    val bitrate = song.bitrate ?: 0
    val channels = song.channels ?: 2
    if (sampleRate >= 192000 && channels > 0) return true
    if (bitrate > 0 && sampleRate > 0 && channels > 0) {
        val bitrateKbps = bitrate / 1000
        val estimatedBitDepth = (bitrateKbps * 1000) / (sampleRate * channels)
        if (estimatedBitDepth >= 22 && sampleRate >= 96000) return true
    }
    return false
}

fun calculateSongCategories(
    preparedSongs: List<Song>,
    hasDownloadedSongs: Boolean = false
): List<String> {
    val allCategories = mutableListOf("All")

    if (hasDownloadedSongs) {
        allCategories.add("Downloaded")
    }

    if (preparedSongs.any { isStudioMaster(it) }) {
        allCategories.add("Studio Master")
    }

    if (preparedSongs.any { isHiResLossless(it) && !isDolbyOrSurround(it) && !isDSD(it) && !isStudioMaster(it) }) {
        allCategories.add("Hi-Res Lossless")
    }

    if (preparedSongs.any { isCDQuality(it) }) {
        allCategories.add("CD Quality")
    }

    if (preparedSongs.any { isRegularLossless(it) && !isDolbyOrSurround(it) && !isDSD(it) && !isCDQuality(it) && !isStudioMaster(it) }) {
        allCategories.add("Lossless")
    }

    if (preparedSongs.any { isDSD(it) }) {
        allCategories.add("DSD")
    }

    if (preparedSongs.any { isDolbyAtmos(it) }) {
        allCategories.add("Dolby Atmos")
    }

    if (preparedSongs.any { isDolbyDigitalPlus(it) }) {
        allCategories.add("Dolby Digital Plus")
    }

    if (preparedSongs.any { isDolbyDigital(it) }) {
        allCategories.add("Dolby Digital")
    }

    if (preparedSongs.any { isDTS(it) }) {
        allCategories.add("DTS")
    }

    if (preparedSongs.any { isDolbyOrSurround(it) }) {
        allCategories.add("Dolby / Surround")
    }

    if (preparedSongs.any { (it.channels ?: 2) == 1 }) {
        allCategories.add("Mono")
    }

    if (preparedSongs.any { isLossyAudio(it) }) {
        allCategories.add("Lossy")
    }

    if (preparedSongs.any { song ->
        val bitrate = song.bitrate ?: 0
        bitrate >= 320000 && !isLosslessAudio(song) && !isDolbyOrSurround(song)
    }) {
        allCategories.add("High Quality")
    }

    if (preparedSongs.any { song ->
        val bitrate = song.bitrate ?: 0
        bitrate in 128000..319999 && !isLosslessAudio(song) && !isDolbyOrSurround(song)
    }) {
        allCategories.add("Standard")
    }

    if (preparedSongs.any { it.duration < 3 * 60 * 1000 }) {
        allCategories.add("Short (< 3 min)")
    }

    if (preparedSongs.any { it.duration in (3 * 60 * 1000)..(5 * 60 * 1000) }) {
        allCategories.add("Medium (3-5 min)")
    }

    if (preparedSongs.any { it.duration > 5 * 60 * 1000 }) {
        allCategories.add("Long (> 5 min)")
    }

    return allCategories
}

fun filterSongsByCategory(
    preparedSongs: List<Song>,
    selectedCategory: String,
    downloadedSongIds: Set<String> = emptySet()
): List<Song> {
    return when (selectedCategory) {
        "All" -> preparedSongs
        "Downloaded" -> preparedSongs.filter { it.id in downloadedSongIds }

        "Short (< 3 min)" -> preparedSongs.filter { it.duration < 3 * 60 * 1000 }
        "Medium (3-5 min)" -> preparedSongs.filter { it.duration in (3 * 60 * 1000)..(5 * 60 * 1000) }
        "Long (> 5 min)" -> preparedSongs.filter { it.duration > 5 * 60 * 1000 }

        "Studio Master" -> preparedSongs.filter { isStudioMaster(it) }
        "Hi-Res Lossless" -> preparedSongs.filter { isHiResLossless(it) && !isDolbyOrSurround(it) && !isDSD(it) && !isStudioMaster(it) }
        "CD Quality" -> preparedSongs.filter { isCDQuality(it) }
        "Lossless" -> preparedSongs.filter { isRegularLossless(it) && !isDolbyOrSurround(it) && !isDSD(it) && !isCDQuality(it) && !isStudioMaster(it) }
        "Lossy" -> preparedSongs.filter { isLossyAudio(it) }
        "DSD" -> preparedSongs.filter { isDSD(it) }
        "Dolby Atmos" -> preparedSongs.filter { isDolbyAtmos(it) }
        "Dolby Digital Plus" -> preparedSongs.filter { isDolbyDigitalPlus(it) }
        "Dolby Digital" -> preparedSongs.filter { isDolbyDigital(it) }
        "DTS" -> preparedSongs.filter { isDTS(it) }
        "Dolby / Surround" -> preparedSongs.filter { isDolbyOrSurround(it) }
        "Stereo" -> preparedSongs.filter { (it.channels ?: 2) == 2 && !isDolbyOrSurround(it) }
        "Mono" -> preparedSongs.filter { (it.channels ?: 2) == 1 }

        "High Quality" -> preparedSongs.filter { song ->
            val bitrate = song.bitrate ?: 0
            bitrate >= 320000 && !isLosslessAudio(song) && !isDolbyOrSurround(song)
        }

        "Standard" -> preparedSongs.filter { song ->
            val bitrate = song.bitrate ?: 0
            bitrate in 128000..319999 && !isLosslessAudio(song) && !isDolbyOrSurround(song)
        }

        else -> preparedSongs
    }
}

@Composable
fun YearGroupedSongsContent(
    songs: List<Song>,
    albums: List<Album> = emptyList(),
    listState: LazyListState = rememberLazyListState(),
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onToggleFavorite: (Song) -> Unit = {},
    favoriteSongs: Set<String> = emptySet(),
    onGoToArtist: (Artist) -> Unit = {},
    onGoToAlbum: (Album) -> Unit = {},
    onShowSongInfo: (Song) -> Unit,
    onAddToBlacklist: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit = {},
    onPlayQueue: (List<Song>) -> Unit = { _ -> },
    onPlayQueueFromIndex: (List<Song>, Int) -> Unit = { _, _ -> },
    onShuffleQueue: (List<Song>) -> Unit = { _ -> },
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    multiSelectionState: chromahub.rhythm.app.features.local.presentation.viewmodel.MultiSelectionStateHolder? = null,
    onSongLongPress: (Song) -> Unit = {},
    onSongSelectionToggle: (Song) -> Unit = {},
    onShowMultiSelectionSheet: () -> Unit = {},
    onRefreshClick: (() -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    sortOrder: MusicViewModel.SortOrder = MusicViewModel.SortOrder.TITLE_ASC,
    isStreamingMode: Boolean = false,
    streamingDownloadedSongIds: Set<String> = emptySet(),
    streamingDownloadingSongIds: Set<String> = emptySet(),
    onStreamingToggleDownload: ((Song) -> Unit)? = null
) {
    val context = LocalContext.current

    val songsByYear = remember(songs, sortOrder) {
        val groups = songs.groupBy { song ->
            if (song.year > 0) song.year else null
        }
        val sortedYears = groups.keys.sortedByDescending { it ?: Int.MAX_VALUE }
        sortedYears.map { year ->
            val yearSongs = groups[year] ?: emptyList()
            val sortedSongs = when (sortOrder) {
                MusicViewModel.SortOrder.TITLE_ASC -> yearSongs.sortedWith(NaturalSortComparator.comparator<Song> { it.title })
                MusicViewModel.SortOrder.TITLE_DESC -> yearSongs.sortedWith(NaturalSortComparator.comparator<Song> { it.title }.reversed())
                MusicViewModel.SortOrder.ARTIST_ASC -> yearSongs.sortedWith(NaturalSortComparator.comparator<Song> { it.artist })
                MusicViewModel.SortOrder.ARTIST_DESC -> yearSongs.sortedWith(NaturalSortComparator.comparator<Song> { it.artist }.reversed())
                MusicViewModel.SortOrder.DATE_ADDED_DESC -> yearSongs.sortedByDescending { it.dateAdded }
                MusicViewModel.SortOrder.DATE_ADDED_ASC -> yearSongs.sortedBy { it.dateAdded }
                MusicViewModel.SortOrder.DATE_MODIFIED_DESC -> yearSongs.sortedByDescending { it.dateModified }
                MusicViewModel.SortOrder.DATE_MODIFIED_ASC -> yearSongs.sortedBy { it.dateModified }
                else -> yearSongs
            }
            val label = year?.toString() ?: context.getString(R.string.library_unknown_year)
            label to sortedSongs
        }
    }

    if (songsByYear.isEmpty()) {
        EmptyState(
            message = context.getString(R.string.library_no_dated_songs),
            subtitle = context.getString(R.string.library_no_dated_songs_desc),
            icon = RhythmIcons.CalendarMonth,
            onRefresh = onRefreshClick
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val canScroll by remember(listState) {
            derivedStateOf { listState.shouldShowScrollbar() }
        }
        val animatedEndPadding by animateDpAsState(
            targetValue = if (canScroll) 36.dp else 16.dp,
            animationSpec = tween(durationMillis = 200)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = animatedEndPadding,
                top = 16.dp,
                bottom = bottomPadding + 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            songsByYear.forEach { (yearLabel, yearSongs) ->
                item(key = "header_$yearLabel", contentType = "yearHeader") {
                    AnimateIn(modifier = Modifier.animateItem()) {
                        Text(
                            text = "$yearLabel (${yearSongs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                }
                itemsIndexed(
                    items = yearSongs,
                    key = { _, song -> "datesong_${song.id}" },
                    contentType = { _, _ -> "song" }
                ) { index, song ->
                    AnimateIn(modifier = Modifier.animateItem()) {
                        val isSelected = selectedSongIds.contains(song.id)
                        val selectionIndex = multiSelectionState?.getSelectionIndex(song.id)

                        LibrarySongItemWrapper(
                            song = song,
                            onClick = {
                                if (isSelectionMode) {
                                    onSongSelectionToggle(song)
                                } else {
                                    val songIndex = yearSongs.indexOf(song)
                                    if (songIndex >= 0) {
                                        onPlayQueueFromIndex(yearSongs, songIndex)
                                    } else {
                                        onSongClick(song)
                                    }
                                }
                            },
                            onMoreClick = { onAddToPlaylist(song) },
                            onAddToQueue = { onAddToQueue(song) },
                            onPlayNext = { onPlayNext(song) },
                            onToggleFavorite = { onToggleFavorite(song) },
                            isFavorite = favoriteSongs.contains(song.id),
                            onGoToArtist = { onGoToArtist(Artist(id = "", name = song.artist)) },
                            onGoToAlbum = {
                                albums.findAlbumForSong(song)?.let { onGoToAlbum(it) }
                            },
                            onShowSongInfo = { onShowSongInfo(song) },
                            onAddToBlacklist = { onAddToBlacklist(song) },
                            onDeleteSong = { onDeleteSong(song) },
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            haptics = haptics,
                            itemShape = groupedLibraryItemShape(index, yearSongs.size),
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            selectionIndex = selectionIndex,
                            onLongPress = { onSongLongPress(song) },
                            isDownloaded = isStreamingMode && streamingDownloadedSongIds.contains(song.id),
                            isDownloading = isStreamingMode && streamingDownloadingSongIds.contains(song.id),
                            onToggleDownload = if (isStreamingMode) ({ onStreamingToggleDownload?.invoke(song) }) else null
                        )
                    }
                }
            }
        }

        val dateFastScrollLabelProvider = remember(songsByYear) {
            { index: Int -> songsByYear.getOrNull(index)?.first ?: "" }
        }

        ExpressiveScrollBar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding + 16.dp),
            listState = listState,
            visible = canScroll,
            dragLabelProvider = dateFastScrollLabelProvider
        )
    }
}

@Composable
fun LibraryScanProgressBanner(
    musicViewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val scanProgress by musicViewModel.scanProgress.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "scanIconRotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )
            
            Icon(
                imageVector = RhythmIcons.Refresh,
                contentDescription = stringResource(R.string.settings_scanning),
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (scanProgress.stage) {
                        is ScanPhase.Songs -> context.getString(R.string.library_scan_songs)
                        is ScanPhase.Incremental -> context.getString(R.string.library_scan_media)
                        is ScanPhase.SavingDb -> context.getString(R.string.library_scan_media)
                        else -> context.getString(R.string.library_scan_media)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (scanProgress.total > 0) {
                    Text(
                        text = "${scanProgress.current} / ${scanProgress.total}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            
            M3CircularLoader(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                strokeWidth = 2f
            )
        }
    }
}




