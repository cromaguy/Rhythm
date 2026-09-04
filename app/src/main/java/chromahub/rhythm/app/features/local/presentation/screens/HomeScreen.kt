/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// Experimental API opt-ins required for:
// - Material3 Carousel APIs (HorizontalCenteredHeroCarousel, HorizontalUncontainedCarousel)
// - ModalBottomSheet, rememberModalBottomSheetState
// - Window Size Class APIs
// These will become stable in future Material3 releases
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)

package chromahub.rhythm.app.features.local.presentation.screens

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.features.streaming.presentation.components.StreamingServiceStateCard
import chromahub.rhythm.app.features.streaming.data.repository.StreamingServiceSession
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum
import chromahub.rhythm.app.features.streaming.domain.model.StreamingArtist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingServiceId
import chromahub.rhythm.app.features.streaming.presentation.model.StreamingServiceOptions
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.util.ArtistSeparator

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGuardCard
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import chromahub.rhythm.app.shared.data.repository.StatsTimeRange
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.ui.theme.festive.FestiveConfig
import chromahub.rhythm.app.ui.theme.festive.FestiveThemeEngine
import chromahub.rhythm.app.shared.data.model.AppSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import chromahub.rhythm.app.util.PerformIfEnabled
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.player.MiniPlayer
import chromahub.rhythm.app.features.local.presentation.components.settings.HomeSectionOrderBottomSheet
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveFilledButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveFilledTonalButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveOutlinedButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveFilledIconButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveFilledTonalIconButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveLargeIconButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveCard
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveElevatedCard
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapes
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.theme.ExpressiveMaterialShape
import chromahub.rhythm.app.shared.presentation.theme.rememberExpressiveShape
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.ActionProgressLoader
import chromahub.rhythm.app.shared.presentation.components.common.NetworkOperationLoader
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveAnimatedCounter

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AddToPlaylistBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongInfoBottomSheet
import chromahub.rhythm.app.shared.presentation.components.AudioQualityBadges
import chromahub.rhythm.app.shared.presentation.components.AudioQualityIcon
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.M3ImageUtils
import chromahub.rhythm.app.shared.presentation.viewmodel.AppVersion
import chromahub.rhythm.app.shared.presentation.components.dialogs.CreatePlaylistDialog
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import java.util.Calendar
import kotlin.random.Random
import androidx.core.text.HtmlCompat
import androidx.compose.ui.res.stringResource
import chromahub.rhythm.app.util.windowScreenWidthDp
import chromahub.rhythm.app.util.windowScreenHeightDp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    musicViewModel: chromahub.rhythm.app.viewmodel.MusicViewModel,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    recentlyPlayed: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlayPause: () -> Unit,
    onPlayerClick: () -> Unit,
    onViewAllSongs: () -> Unit,
    onViewAllAlbums: () -> Unit,
    onViewAllArtists: () -> Unit,
    onSkipNext: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onAddSongToPlaylist: (Song, String) -> Unit = { _, _ -> },
    onNavigateToPlaylist: (String) -> Unit = {},
    onCreatePlaylist: (String) -> Unit = { _ -> },
    onNavigateToStats: () -> Unit = {},
    onNavigateToRhythmGuard: () -> Unit = {},
    onNavigateToArtist: (Artist) -> Unit = {},
    isStreamingMode: Boolean = false,
    streamingViewModel: chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel? = null,
    streamingSongs: List<Song> = emptyList(),
    streamingAlbums: List<Album> = emptyList(),
    streamingArtists: List<Artist> = emptyList(),
    streamingPlaylists: List<chromahub.rhythm.app.shared.data.model.Playlist> = emptyList(),
    streamingRecentlyPlayed: List<Song> = emptyList(),
    streamingServiceName: String = "",
    streamingServiceConnected: Boolean = false,
    streamingIsLoading: Boolean = false,
    streamingError: String? = null,
    onConfigureService: (String) -> Unit = {},
    onSwitchToLocalMode: () -> Unit = {},
    onStreamingNavigateToArtist: (chromahub.rhythm.app.features.streaming.domain.model.StreamingArtist) -> Unit = {},
    onStreamingNavigateToAlbum: (chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum) -> Unit = {},
    onStreamingNavigateToPlaylist: (chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist) -> Unit = {},
    onStreamingPlayQueue: (List<chromahub.rhythm.app.features.streaming.domain.model.StreamingSong>, Int, Boolean) -> Unit = { _, _, _ -> },
    onStreamingShuffleQueue: (List<chromahub.rhythm.app.features.streaming.domain.model.StreamingSong>) -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val isTablet = windowScreenWidthDp() >= 600
    val isLandscapeTablet = isTablet && windowScreenWidthDp() > windowScreenHeightDp()
    val appSettings = remember { AppSettings.getInstance(context) }

    // Home header customization
    val headerDisplayMode by appSettings.homeHeaderDisplayMode.collectAsState()
    val showAppIcon by appSettings.homeShowAppIcon.collectAsState()
    val iconVisibilityMode by appSettings.homeAppIconVisibility.collectAsState()
    val floatingNavigationBar by appSettings.floatingNavigationBar.collectAsState()
    val streamingDisconnected = isStreamingMode && !streamingServiceConnected && !streamingIsLoading

    // State for AddToPlaylist bottom sheet
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var selectedSongForPlaylist by remember { mutableStateOf<Song?>(null) }
    val addToPlaylistSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    // Song info bottom sheet state
    var showSongInfoSheet by remember { mutableStateOf(false) }

    // Home section order bottom sheet state
    var showHomeSectionOrderSheet by remember { mutableStateOf(false) }

    val isLibraryRefreshing by musicViewModel.isLibraryRefreshing.collectAsState()
    var streamingIsRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(streamingIsLoading) {
        if (!streamingIsLoading) streamingIsRefreshing = false
    }

    // Pending write request for metadata editing (Android 11+)
    val pendingWriteRequest by musicViewModel.pendingWriteRequest.collectAsState()

    var pendingMetadataEditCompleteCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    // Write permission launcher for Android 11+ metadata editing
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

    // Select featured content from all albums (enhanced selection)
    val featuredContent = albums

    // Get all unique artists
    val availableArtists = remember(artists) {
        artists.sortedBy { it.name }
    }

    val quickPicks = songs.take(8)
    val topArtists = availableArtists

    // Enhanced filtering for new releases
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val currentYearReleases = remember(albums, currentYear) {
        albums.filter { it.year == currentYear }
            .ifEmpty {
                albums.sortedByDescending { it.year }.take(6)
            }
    }

    // Enhanced recently added songs
    val recentlyAddedSongs = remember(songs) {
        val oneMonthAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis
        songs.filter { it.dateAdded >= oneMonthAgo }
            .sortedByDescending { it.dateAdded }
    }

    // Enhanced recently added albums
    val recentlyAddedAlbums = remember(albums, songs) {
        val oneMonthAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis
        val recentSongIds = songs.filter { it.dateAdded >= oneMonthAgo }.map { it.id }.toSet()
        albums.filter { album ->
            album.songs.any { song -> song.id in recentSongIds }
        }.sortedByDescending { album ->
            album.songs.mapNotNull { song ->
                if (song.id in recentSongIds) song.dateAdded else null
            }.maxOfOrNull { it } ?: 0L
        }
    }



    // Song info bottom sheet
    if (showSongInfoSheet && selectedSongForPlaylist != null) {
        SongInfoBottomSheet(
            song = selectedSongForPlaylist,
            onDismiss = { showSongInfoSheet = false },
            appSettings = AppSettings.getInstance(context),
            isStreamingMode = isStreamingMode,
            onEditSong = { title, artist, album, genre, year, trackNumber, artworkUri, removeArtwork, albumArtist, composer, discNumber, onComplete ->
                pendingMetadataEditCompleteCallback = onComplete
                musicViewModel.saveMetadataChanges(
                    song = selectedSongForPlaylist!!,
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

    if (showAddToPlaylistSheet && selectedSongForPlaylist != null) {
        val playlists by musicViewModel.playlists.collectAsState()

        AddToPlaylistBottomSheet(
            song = selectedSongForPlaylist!!,
            playlists = playlists,
            onDismissRequest = { showAddToPlaylistSheet = false },
            onAddToPlaylist = { playlist ->
                onAddSongToPlaylist(selectedSongForPlaylist!!, playlist.id)
                coroutineScope.launch {
                    addToPlaylistSheetState.hide()
                }.invokeOnCompletion {
                    if (!addToPlaylistSheetState.isVisible) {
                        showAddToPlaylistSheet = false
                    }
                }
            },
            onCreateNewPlaylist = {
                coroutineScope.launch {
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

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { 
                showCreatePlaylistDialog = false
                selectedSongForPlaylist = null
            },
            onConfirm = { name ->
                musicViewModel.createPlaylist(name)
                showCreatePlaylistDialog = false
                selectedSongForPlaylist = null
            },
            song = selectedSongForPlaylist,
            onConfirmWithSong = { name ->
                selectedSongForPlaylist?.let { song ->
                    musicViewModel.createPlaylist(name, listOf(song)) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    musicViewModel.createPlaylist(name)
                }
                showCreatePlaylistDialog = false
                selectedSongForPlaylist = null
            }
        )
    }

    if (showHomeSectionOrderSheet) {
        HomeSectionOrderBottomSheet(
            onDismiss = { showHomeSectionOrderSheet = false },
            appSettings = AppSettings.getInstance(context)
        )
    }

    CollapsibleHeaderScreen(
        title = if (streamingDisconnected) "" else if (isStreamingMode) context.getString(R.string.streaming_integration_title) else context.getString(R.string.home_title),
        headerDisplayMode = headerDisplayMode,
        alwaysCollapsed = streamingDisconnected,
        showAppIcon = showAppIcon,
        iconVisibilityMode = iconVisibilityMode,
        actions = {
            val showReorder = !isLandscapeTablet && !streamingDisconnected
            val showSettings = !isTablet && floatingNavigationBar

            if (showReorder) {
                ExpressiveFilledTonalIconButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        showHomeSectionOrderSheet = true
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.padding(end = if (showSettings) 8.dp else 16.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("reorder", filled = true),
                        contentDescription = context.getString(R.string.cd_reorder_home_sections),
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
            if (showSettings) {
                ExpressiveFilledIconButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        onSettingsClick()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Settings,
                        contentDescription = context.getString(R.string.home_settings_cd),
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
    ) { modifier ->
        if (isStreamingMode) {
            StreamingHomeBody(
                modifier = modifier
                    .fillMaxSize()
                    .padding(bottom = if (currentSong != null) 0.dp else 0.dp),
                streamingViewModel = streamingViewModel,
                songs = streamingSongs,
                albums = streamingAlbums,
                artists = streamingArtists,
                playlists = streamingPlaylists,
                recentlyPlayed = streamingRecentlyPlayed.ifEmpty { recentlyPlayed },
                serviceName = streamingServiceName,
                serviceConnected = streamingServiceConnected,
                isLoading = streamingIsLoading,
                errorMessage = streamingError,
                onConfigureService = onConfigureService,
                onSwitchToLocalMode = onSwitchToLocalMode,
                onNavigateToArtist = onStreamingNavigateToArtist,
                onNavigateToAlbum = onStreamingNavigateToAlbum,
                onNavigateToPlaylist = onStreamingNavigateToPlaylist,
                onPlayQueue = onStreamingPlayQueue,
                onShuffleQueue = onStreamingShuffleQueue,
                onViewAllArtists = onViewAllArtists,
                onNavigateToLibrary = onNavigateToLibrary,
                onSongClick = onSongClick,
                onNavigateToStats = onNavigateToStats,
                onNavigateToRhythmGuard = onNavigateToRhythmGuard,
                musicViewModel = musicViewModel,
                coroutineScope = coroutineScope,
                isRefreshing = streamingIsRefreshing,
                onRefresh = {
                    streamingIsRefreshing = true
                    streamingViewModel?.refreshHome()
                }
            )
        } else {
            ModernScrollableContent(
                modifier = modifier
                    .fillMaxSize()
                    .padding(bottom = if (currentSong != null) 0.dp else 0.dp),
                featuredContent = featuredContent,
                albums = albums,
                topArtists = topArtists,
                newReleases = currentYearReleases,
                recentlyAddedSongs = recentlyAddedSongs,
                recentlyAddedAlbums = recentlyAddedAlbums,
                recentlyPlayed = recentlyPlayed,
                songs = songs,
                onSongClick = onSongClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = { artist: Artist ->
                    onNavigateToArtist(artist)
                },
                onViewAllSongs = onViewAllSongs,
                onViewAllAlbums = onViewAllAlbums,
                onViewAllArtists = onViewAllArtists,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToPlaylist = onNavigateToPlaylist,
                onNavigateToStats = onNavigateToStats,
                onNavigateToRhythmGuard = onNavigateToRhythmGuard,
                musicViewModel = musicViewModel,
                coroutineScope = coroutineScope,
                isRefreshing = isLibraryRefreshing,
                onRefresh = {
                    musicViewModel.refreshLibrary(showMediaScanLoader = false)
                }
            )
        }
    }
}

/**
 * Go-mode (streaming) home body.
 *
 * Renders streaming content through the exact same local Modern* card language:
 * streaming models are converted to local model types by the caller, and play
 * actions are routed back to the streaming view model (which hands off to the
 * shared local player via the playback handler).
 */
@Composable
private fun StreamingHomeBody(
    modifier: Modifier = Modifier,
    streamingViewModel: chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel?,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    playlists: List<chromahub.rhythm.app.shared.data.model.Playlist>,
    recentlyPlayed: List<Song>,
    serviceName: String,
    serviceConnected: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onConfigureService: (String) -> Unit,
    onSwitchToLocalMode: () -> Unit = {},
    onNavigateToArtist: (chromahub.rhythm.app.features.streaming.domain.model.StreamingArtist) -> Unit,
    onNavigateToAlbum: (chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum) -> Unit,
    onNavigateToPlaylist: (chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist) -> Unit,
    onPlayQueue: (List<chromahub.rhythm.app.features.streaming.domain.model.StreamingSong>, Int, Boolean) -> Unit,
    onShuffleQueue: (List<chromahub.rhythm.app.features.streaming.domain.model.StreamingSong>) -> Unit,
    onViewAllArtists: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onSongClick: (Song) -> Unit,
    onNavigateToStats: () -> Unit = {},
    onNavigateToRhythmGuard: () -> Unit = {},
    musicViewModel: chromahub.rhythm.app.viewmodel.MusicViewModel,
    coroutineScope: CoroutineScope,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val widthSizeClass = windowSizeClass.widthSizeClass
    val heightSizeClass = windowSizeClass.heightSizeClass
    val scrollState = rememberScrollState()

    val appSettings = AppSettings.getInstance(context)
    val showDiscoverCarousel by appSettings.homeShowDiscoverCarousel.collectAsState()
    val discoverItemCount by appSettings.homeDiscoverItemCount.collectAsState()
    val discoverShowAlbumName by appSettings.homeDiscoverShowAlbumName.collectAsState()
    val discoverShowArtistName by appSettings.homeDiscoverShowArtistName.collectAsState()
    val discoverShowYear by appSettings.homeDiscoverShowYear.collectAsState()
    val discoverShowPlayButton by appSettings.homeDiscoverShowPlayButton.collectAsState()
    val discoverShowGradient by appSettings.homeDiscoverShowGradient.collectAsState()
    val showRhythmGuardSection by appSettings.streamingHomeShowRhythmGuard.collectAsState()
    val showRhythmStatsSection by appSettings.streamingHomeShowRhythmStats.collectAsState()

    val rhythmGuardMode by appSettings.rhythmGuardMode.collectAsState()
    val rhythmGuardAge by appSettings.rhythmGuardAge.collectAsState()
    val rhythmGuardAlertThresholdMinutes by appSettings.rhythmGuardAlertThresholdMinutes.collectAsState()
    val rhythmGuardTimeoutUntilMs by appSettings.rhythmGuardTimeoutUntilMs.collectAsState()
    val dailyListeningStats by appSettings.dailyListeningStats.collectAsState()
    val persistedSongsPlayed by appSettings.songsPlayed.collectAsState()
    val listeningTimeMs by appSettings.listeningTime.collectAsState()

    val rhythmGuardPolicy = remember(rhythmGuardAge) { appSettings.getRhythmGuardPolicy(rhythmGuardAge) }
    val rhythmGuardRecommendedMinutes = when (rhythmGuardMode) {
        AppSettings.RHYTHM_GUARD_MODE_MANUAL -> rhythmGuardAlertThresholdMinutes
            .takeIf { it > 0 }
            ?: rhythmGuardPolicy.recommendedDailyMinutes
        else -> rhythmGuardPolicy.recommendedDailyMinutes
    }
    val playbackStatsRepository = remember(context) { PlaybackStatsRepository.getInstance(context) }
    var todayListeningMinutes by remember { mutableIntStateOf(0) }

    val currentProgress by musicViewModel.progress.collectAsState()
    val currentDurationMs by musicViewModel.duration.collectAsState()
    val currentIsPlaying by musicViewModel.isPlaying.collectAsState()

    LaunchedEffect(dailyListeningStats, persistedSongsPlayed, listeningTimeMs, currentProgress, currentDurationMs, currentIsPlaying) {
        val todaySummary = runCatching {
            playbackStatsRepository.loadSummary(StatsTimeRange.TODAY)
        }.getOrNull()
        val dbDurationMs = todaySummary?.totalDurationMs ?: 0L
        val activeSessionDurationMs = if (currentIsPlaying && currentDurationMs > 0) {
            (currentProgress * currentDurationMs).toLong()
        } else {
            0L
        }
        val totalMs = dbDurationMs + activeSessionDurationMs
        todayListeningMinutes = (totalMs / 60000L).toInt().coerceAtLeast(0)
    }

    val horizontalPadding = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 20.dp
        WindowWidthSizeClass.Medium -> 48.dp
        WindowWidthSizeClass.Expanded -> 64.dp
        else -> 20.dp
    }
    val sectionSpacing = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 32.dp
            else -> 40.dp
        }
        WindowWidthSizeClass.Medium -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 48.dp
            else -> 56.dp
        }
        WindowWidthSizeClass.Expanded -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 52.dp
            else -> 64.dp
        }
        else -> 40.dp
    }

    val vm = streamingViewModel
    val rawAllSongs = vm?.allSongs?.collectAsState()?.value ?: emptyList()
    val rawRecommendations = vm?.recommendations?.collectAsState()?.value ?: emptyList()
    val rawNewReleases = vm?.newReleases?.collectAsState()?.value ?: emptyList()
    val rawArtists = vm?.followedArtists?.collectAsState()?.value ?: emptyList()
    val rawPlaylists = vm?.savedPlaylists?.collectAsState()?.value ?: emptyList()
    val syncProgress = vm?.syncProgress?.collectAsState()?.value ?: chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingSyncProgress()

    val streamingSongById = remember(rawAllSongs, rawRecommendations, rawNewReleases) {
        (rawAllSongs + rawRecommendations + rawNewReleases.flatMap { it.tracks })
            .distinctBy { it.id }
            .associateBy { it.id }
    }
    val streamingAlbumById = remember(rawNewReleases) { rawNewReleases.associateBy { it.id } }
    val streamingArtistById = remember(rawArtists) { rawArtists.associateBy { it.id } }
    val streamingPlaylistById = remember(rawPlaylists) { rawPlaylists.associateBy { it.id } }

    val hasLoadedHomeContent = vm?.hasLoadedHomeContent?.collectAsState()?.value ?: false
    val hasLoadedLibrary = vm?.hasLoadedLibrary?.collectAsState()?.value ?: false
    LaunchedEffect(vm, serviceConnected, hasLoadedHomeContent) {
        if (serviceConnected && vm != null && !hasLoadedHomeContent) {
            vm.loadHomeContent()
        }
    }
    LaunchedEffect(vm, serviceConnected, hasLoadedLibrary) {
        if (serviceConnected && vm != null && !hasLoadedLibrary) {
            vm.loadLibrary()
        }
    }

    val playQueueFromMapped: (List<Song>, Int, Boolean) -> Unit = { mappedSongs, startIndex, shuffle ->
        val originals = mappedSongs.mapNotNull { streamingSongById[it.id] }
        if (originals.isNotEmpty()) onPlayQueue(originals, startIndex, shuffle)
    }

    val serviceSessions = vm?.serviceSessions?.collectAsState()?.value ?: emptyMap()
    val isAuthenticated = vm?.isAuthenticated?.collectAsState()?.value ?: false

    if (!serviceConnected && !isLoading) {
        StreamingHomeWelcomeContent(
            modifier = modifier.fillMaxSize(),
            serviceSessions = serviceSessions,
            isAuthenticated = isAuthenticated,
            onConfigureService = onConfigureService,
            onSwitchToLocalMode = onSwitchToLocalMode
        )
        return
    }

    val pullToRefreshState = rememberPullToRefreshState()
    val isListAtTop by remember { derivedStateOf { scrollState.value == 0 } }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        enabled = isListAtTop,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {
        if (showDiscoverCarousel && albums.isNotEmpty()) {
            ModernFeaturedSection(
                albums = albums.take(discoverItemCount),
                onAlbumClick = { album ->
                    streamingAlbumById[album.id]?.let(onNavigateToAlbum)
                },
                showAlbumName = discoverShowAlbumName,
                showArtistName = discoverShowArtistName,
                showYear = discoverShowYear,
                showPlayButton = discoverShowPlayButton,
                showGradient = discoverShowGradient,
                widthSizeClass = widthSizeClass,
                heightSizeClass = heightSizeClass,
                onPlayAlbum = { album ->
                    playQueueFromMapped(album.songs, 0, false)
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
        ) {
        if (syncProgress.isSyncing) {
            val syncSubtitle = if (syncProgress.total > 0) {
                context.getString(
                    R.string.streaming_sync_progress_format,
                    syncProgress.current,
                    syncProgress.total,
                    syncProgress.songsCount
                )
            } else if (syncProgress.songsCount > 0) {
                "${syncProgress.songsCount} songs found"
            } else {
                context.getString(R.string.streaming_home_widget_empty_hint)
            }
            StreamingServiceStateCard(
                title = context.getString(R.string.streaming_sync_progress_title, serviceName),
                subtitle = syncSubtitle,
                icon = MaterialSymbolIcon("sync", filled = true),
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                iconTint = MaterialTheme.colorScheme.primary,
                showProgressIndicator = true
            )
        } else {
            when {
                isLoading && songs.isEmpty() -> {
                    StreamingServiceStateCard(
                        title = context.getString(R.string.streaming_home_no_content_title),
                        subtitle = context.getString(R.string.streaming_home_widget_empty_hint),
                        showProgressIndicator = true
                    )
                }

                errorMessage != null && songs.isEmpty() -> {
                    StreamingServiceStateCard(
                        title = errorMessage,
                        subtitle = context.getString(R.string.streaming_home_widget_empty_hint),
                        actionText = context.getString(R.string.streaming_service_setup_reconnect),
                        onAction = { onConfigureService(serviceName) }
                    )
                }
            }
        }

        val hasStreamingContent = songs.isNotEmpty() ||
            albums.isNotEmpty() ||
            artists.isNotEmpty() ||
            playlists.isNotEmpty() ||
            recentlyPlayed.isNotEmpty()

        val isTablet = widthSizeClass != WindowWidthSizeClass.Compact || windowScreenWidthDp() >= 600
        val isLandscapeTablet = isTablet && windowScreenWidthDp() > windowScreenHeightDp()
        val sectionOrder by appSettings.homeSectionOrder.collectAsState()

        @Composable
        fun RenderStreamingSection(sectionId: String) {
            when (sectionId) {
                "RECENTLY_PLAYED" -> {
                    if (recentlyPlayed.isNotEmpty()) {
                        ModernRecentlyPlayedSection(
                            recentlyPlayed = recentlyPlayed,
                            onSongClick = onSongClick,
                            musicViewModel = musicViewModel,
                            coroutineScope = coroutineScope,
                            widthSizeClass = widthSizeClass,
                            heightSizeClass = heightSizeClass
                        )
                    }
                }
                "NEW_RELEASES" -> {
                    if (albums.isNotEmpty()) {
                        Column {
                            ModernSectionTitle(
                                title = context.getString(R.string.home_new_releases),
                                subtitle = context.getString(
                                    R.string.streaming_home_widget_new_releases_subtitle,
                                    serviceName.ifBlank { context.getString(R.string.streaming_not_selected) }
                                ),
                                onPlayAll = {
                                    playQueueFromMapped(albums.flatMap { it.songs }, 0, false)
                                },
                                onShufflePlay = {
                                    onShuffleQueue(albums.flatMap { it.songs }.mapNotNull { streamingSongById[it.id] })
                                }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = albums,
                                    key = { "streaming_album_${it.id}" }
                                ) { album ->
                                    ModernAlbumCard(
                                        album = album,
                                        onClick = { clickedAlbum ->
                                            streamingAlbumById[clickedAlbum.id]?.let { raw ->
                                                onNavigateToAlbum(raw)
                                            }
                                        },
                                        widthSizeClass = widthSizeClass,
                                        heightSizeClass = heightSizeClass,
                                        onPlayClick = { clickedAlbum ->
                                            playQueueFromMapped(clickedAlbum.songs, 0, false)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (playlists.isNotEmpty()) {
                        if (albums.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        Column {
                            ModernSectionTitle(
                                title = context.getString(R.string.settings_tab_playlists),
                                viewAllAction = onNavigateToLibrary
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = playlists,
                                    key = { "streaming_playlist_${it.id}" }
                                ) { playlist ->
                                    StreamingHomePlaylistCard(
                                        playlist = playlist,
                                        onClick = {
                                            streamingPlaylistById[playlist.id]?.let { raw ->
                                                onNavigateToPlaylist(raw)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                "ARTISTS" -> {
                    if (artists.isNotEmpty()) {
                        Column {
                            ModernSectionTitle(
                                title = context.getString(R.string.home_top_artists),
                                subtitle = context.getString(R.string.home_top_artists_subtitle),
                                viewAllAction = onViewAllArtists
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = artists,
                                    key = { "streaming_artist_${it.id}" }
                                ) { artist ->
                                    ModernArtistCard(
                                        artist = artist,
                                        songs = artist.songs,
                                        onClick = {
                                            streamingArtistById[artist.id]?.let { raw ->
                                                onNavigateToArtist(raw)
                                            }
                                        },
                                        widthSizeClass = widthSizeClass,
                                        heightSizeClass = heightSizeClass,
                                        onPlayClick = { clickedArtist ->
                                            playQueueFromMapped(clickedArtist.songs, 0, false)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                "RECOMMENDED" -> {
                    if (songs.isNotEmpty()) {
                        ModernRecommendedSection(
                            recommendedSongs = songs,
                            artists = artists,
                            onSongClick = { song ->
                                val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                playQueueFromMapped(songs, index, false)
                            },
                            onPlayClick = { songsToPlay ->
                                playQueueFromMapped(songsToPlay, 0, false)
                            }
                        )
                    }
                }
                "RHYTHM_GUARD" -> {
                    if (hasStreamingContent && showRhythmGuardSection && rhythmGuardMode != AppSettings.RHYTHM_GUARD_MODE_OFF) {
                        val rhythmGuardTimeoutRemainingMs = (rhythmGuardTimeoutUntilMs - System.currentTimeMillis()).coerceAtLeast(0L)
                        val isRhythmGuardTimeoutActive = rhythmGuardTimeoutRemainingMs > 0L

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ModernSectionTitle(
                                title = stringResource(id = R.string.settings_rhythm_guard),
                                subtitle = stringResource(id = R.string.settings_rhythm_guard_list_desc)
                            )
                            RhythmGuardCard(
                                rhythmGuardMode = rhythmGuardMode,
                                rhythmGuardRecommendedMinutes = rhythmGuardRecommendedMinutes,
                                todayListeningMinutes = todayListeningMinutes,
                                isGuardTimeoutActive = isRhythmGuardTimeoutActive,
                                guardTimeoutRemainingMs = rhythmGuardTimeoutRemainingMs,
                                onCardClick = onNavigateToRhythmGuard
                            )
                        }
                    }
                }
                "STATS" -> {
                    if (hasStreamingContent && showRhythmStatsSection) {
                        ModernListeningStatsSection(onClick = onNavigateToStats)
                    }
                }
            }
        }

        fun isStreamingSectionVisible(sectionId: String): Boolean = when (sectionId) {
            "RECENTLY_PLAYED" -> recentlyPlayed.isNotEmpty()
            "NEW_RELEASES" -> albums.isNotEmpty() || playlists.isNotEmpty()
            "ARTISTS" -> artists.isNotEmpty()
            "RECOMMENDED" -> songs.isNotEmpty()
            "RHYTHM_GUARD" -> hasStreamingContent && showRhythmGuardSection && rhythmGuardMode != AppSettings.RHYTHM_GUARD_MODE_OFF
            "STATS" -> hasStreamingContent && showRhythmStatsSection
            else -> false
        }

        if (isLandscapeTablet) {
            val visibleSections = sectionOrder.filter { it != "DISCOVER" && isStreamingSectionVisible(it) }
            val leftSections = visibleSections.filterIndexed { index, _ -> index % 2 == 0 }
            val rightSections = visibleSections.filterIndexed { index, _ -> index % 2 == 1 }

            if (visibleSections.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                    ) {
                        for (sectionId in leftSections) {
                            RenderStreamingSection(sectionId)
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                    ) {
                        for (sectionId in rightSections) {
                            RenderStreamingSection(sectionId)
                        }
                    }
                }
            }
        } else {
            for (sectionId in sectionOrder) {
                if (sectionId != "DISCOVER" && isStreamingSectionVisible(sectionId)) {
                    RenderStreamingSection(sectionId)
                }
            }
        }

        if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty() && serviceConnected) {
            ModernEmptyState(
                icon = MaterialSymbolIcon("cloud_sync", filled = true),
                title = context.getString(R.string.streaming_home_no_content_title),
                subtitle = context.getString(R.string.streaming_home_no_content_hint)
            )
        }

        Spacer(
            modifier = Modifier.height(24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding())
        )
        }
    }
    }
}

@Composable
private fun StreamingHomeWelcomeContent(
    modifier: Modifier = Modifier,
    serviceSessions: Map<String, StreamingServiceSession>,
    isAuthenticated: Boolean,
    onConfigureService: (String) -> Unit,
    onSwitchToLocalMode: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val appSettings = remember { AppSettings.getInstance(context) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.rhythm_splash_logo),
                        contentDescription = stringResource(R.string.updates_rhythm_logo_cd),
                        modifier = Modifier.size(100.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(id = R.string.common_rhythm),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = stringResource(R.string.splashscreen_go),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Text(
                    text = if (isAuthenticated) {
                        stringResource(R.string.streaming_status_ready_to_connect)
                    } else {
                        stringResource(R.string.streaming_status_not_connected)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        onSwitchToLocalMode()
                    }
                ) {
                    Text(text = stringResource(R.string.streaminghomescreen_leave_go_mode))
                }
            }
        }

        item {
            val settingsItems = remember(serviceSessions) {
                StreamingServiceOptions.defaults.map { service ->
                    val isConnected = serviceSessions[service.id]?.isConnected == true

                    Material3SettingsItem(
                        leadingContent = {
                            val providerIconRes = when (service.id) {
                                StreamingServiceId.SUBSONIC -> R.drawable.ic_subsonic
                                StreamingServiceId.JELLYFIN -> R.drawable.ic_jellyfin
                                else -> null
                            }
                            if (providerIconRes != null) {
                                Image(
                                    painter = painterResource(id = providerIconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        },
                        title = {
                            Text(
                                text = stringResource(id = service.nameRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(id = service.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        appSettings.setStreamingService(service.id)
                                        onConfigureService(service.id)
                                    }
                                ) {
                                    Text(
                                        text = if (isConnected) {
                                            stringResource(id = R.string.streaming_manage)
                                        } else {
                                            stringResource(id = R.string.streaming_connect)
                                        }
                                    )
                                }
                            }
                        },
                        onClick = {
                            appSettings.setStreamingService(service.id)
                            onConfigureService(service.id)
                        }
                    )
                }
            }

            Material3SettingsGroup(items = settingsItems)
        }

        item {
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun StreamingHomePlaylistCard(
    playlist: chromahub.rhythm.app.shared.data.model.Playlist,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    ExpressiveCard(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
            onClick()
        },
        modifier = Modifier
            .width(150.dp)
            .height(196.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = ExpressiveShapes.SquircleLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            M3ImageUtils.TrackImage(
                imageUrl = playlist.artworkUri,
                trackName = playlist.name,
                modifier = Modifier
                    .size(126.dp)
                    .fillMaxWidth(),
                applyExpressiveShape = true
            )

            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            val songCount = playlist.songs.size
            Text(
                text = pluralStringResource(
                    R.plurals.streaming_home_widget_playlist_track_count,
                    songCount,
                    songCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernScrollableContent(
    modifier: Modifier = Modifier,
    featuredContent: List<Album>,
    albums: List<Album>,
    topArtists: List<Artist>,
    newReleases: List<Album>,
    recentlyAddedSongs: List<Song>,
    recentlyAddedAlbums: List<Album>,
    recentlyPlayed: List<Song>,
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onViewAllSongs: () -> Unit,
    onViewAllAlbums: () -> Unit,
    onViewAllArtists: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToRhythmGuard: () -> Unit = {},
    musicViewModel: chromahub.rhythm.app.viewmodel.MusicViewModel,
    coroutineScope: CoroutineScope,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as android.app.Activity)
    val widthSizeClass = windowSizeClass.widthSizeClass
    val heightSizeClass = windowSizeClass.heightSizeClass
    val scrollState = rememberScrollState()
    val allSongs by musicViewModel.filteredSongs.collectAsState()

    // Home Screen Customization Settings
    val appSettings = AppSettings.getInstance(context)
    val sectionOrder by appSettings.homeSectionOrder.collectAsState()
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

    // Rhythm Guard States (pulled up from LazyColumn block)
    val rhythmGuardMode by appSettings.rhythmGuardMode.collectAsState()
    val showRhythmGuardWidget by appSettings.homeShowRhythmGuard.collectAsState()
    val rhythmGuardAge by appSettings.rhythmGuardAge.collectAsState()
    val rhythmGuardAlertThresholdMinutes by appSettings.rhythmGuardAlertThresholdMinutes.collectAsState()
    val rhythmGuardTimeoutUntilMs by appSettings.rhythmGuardTimeoutUntilMs.collectAsState()
    val dailyListeningStats by appSettings.dailyListeningStats.collectAsState()
    val persistedSongsPlayed by appSettings.songsPlayed.collectAsState()
    val listeningTimeMs by appSettings.listeningTime.collectAsState()

    val rhythmGuardPolicy = remember(rhythmGuardAge) { appSettings.getRhythmGuardPolicy(rhythmGuardAge) }
    val rhythmGuardRecommendedMinutes = when (rhythmGuardMode) {
        AppSettings.RHYTHM_GUARD_MODE_MANUAL -> rhythmGuardAlertThresholdMinutes
            .takeIf { it > 0 }
            ?: rhythmGuardPolicy.recommendedDailyMinutes
        else -> rhythmGuardPolicy.recommendedDailyMinutes
    }
    val playbackStatsRepository = remember(context) { PlaybackStatsRepository.getInstance(context) }
    var todayListeningMinutes by remember { mutableIntStateOf(0) }

    val currentProgress by musicViewModel.progress.collectAsState()
    val currentDurationMs by musicViewModel.duration.collectAsState()
    val currentIsPlaying by musicViewModel.isPlaying.collectAsState()

    LaunchedEffect(dailyListeningStats, persistedSongsPlayed, listeningTimeMs, currentProgress, currentDurationMs, currentIsPlaying) {
        val todaySummary = runCatching {
            playbackStatsRepository.loadSummary(StatsTimeRange.TODAY)
        }.getOrNull()
        val dbDurationMs = todaySummary?.totalDurationMs ?: 0L
        val activeSessionDurationMs = if (currentIsPlaying && currentDurationMs > 0) {
            (currentProgress * currentDurationMs).toLong()
        } else {
            0L
        }
        val totalMs = dbDurationMs + activeSessionDurationMs
        todayListeningMinutes = (totalMs / 60000L).toInt().coerceAtLeast(0)
    }

    // Discover widget card content visibility settings
    val discoverShowAlbumName by appSettings.homeDiscoverShowAlbumName.collectAsState()
    val discoverShowArtistName by appSettings.homeDiscoverShowArtistName.collectAsState()
    val discoverShowYear by appSettings.homeDiscoverShowYear.collectAsState()
    val discoverShowPlayButton by appSettings.homeDiscoverShowPlayButton.collectAsState()
    val discoverShowGradient by appSettings.homeDiscoverShowGradient.collectAsState()

    // Enhanced artist computation
    val availableArtists = remember(topArtists) {
        val collaborationSeparators = listOf(
            ", ", ",", " & ", " and ", "&", " feat. ", " featuring ", " ft. ",
            " with ", " x ", " X ", " + ", " vs ", " VS ", " / ", ";", " · "
        )

        val collaborationRegex = collaborationSeparators
            .map { Regex.escape(it) }
            .joinToString("|")
            .toRegex(RegexOption.IGNORE_CASE)

        topArtists.filter { artist ->
            !artist.name.contains(collaborationRegex)
        }
    }

    // Featured albums with stable ID preservation across library & background metadata updates
    var currentFeaturedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }

    // Synchronize currentFeaturedAlbums when library/albums change without reshuffling
    LaunchedEffect(albums, discoverItemCount) {
        if (albums.isEmpty()) {
            currentFeaturedAlbums = emptyList()
            return@LaunchedEffect
        }
        val targetCount = discoverItemCount.coerceAtLeast(1)
        if (currentFeaturedAlbums.isEmpty()) {
            currentFeaturedAlbums = albums.shuffled().take(targetCount)
        } else {
            val albumsById = albums.associateBy { it.id }
            val updated = currentFeaturedAlbums.mapNotNull { albumsById[it.id] }
            if (updated.size < targetCount && albums.size > updated.size) {
                val existingIds = updated.map { it.id }.toSet()
                val remaining = albums.filter { it.id !in existingIds }.shuffled()
                currentFeaturedAlbums = (updated + remaining).take(targetCount)
            } else if (updated.size > targetCount) {
                currentFeaturedAlbums = updated.take(targetCount)
            } else if (updated.isNotEmpty()) {
                currentFeaturedAlbums = updated
            } else {
                currentFeaturedAlbums = albums.shuffled().take(targetCount)
            }
        }
    }

    // Auto-refresh featured content periodically every 45s (stable loop, decoupled from album list emissions)
    val latestAlbums by rememberUpdatedState(albums)
    val latestDiscoverItemCount by rememberUpdatedState(discoverItemCount)
    LaunchedEffect(Unit) {
        while (true) {
            delay(45000)
            val currentList = latestAlbums
            val targetCount = latestDiscoverItemCount.coerceAtLeast(1)
            if (currentList.isNotEmpty()) {
                currentFeaturedAlbums = if (currentList.size > targetCount) {
                    currentList.shuffled().take(targetCount)
                } else {
                    currentList.shuffled()
                }
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val isTablet = widthSizeClass != WindowWidthSizeClass.Compact || windowScreenWidthDp() >= 600
    val isLandscapeTablet = isTablet && windowScreenWidthDp() > windowScreenHeightDp()

    val horizontalPadding = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 20.dp
        WindowWidthSizeClass.Medium -> 48.dp
        WindowWidthSizeClass.Expanded -> 64.dp
        else -> 20.dp
    }
    val sectionSpacing = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 32.dp
            else -> 40.dp
        }
        WindowWidthSizeClass.Medium -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 48.dp
            else -> 56.dp
        }
        WindowWidthSizeClass.Expanded -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 52.dp
            else -> 64.dp
        }
        else -> 40.dp
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        val pullToRefreshState = rememberPullToRefreshState()
        val isListAtTop by remember { derivedStateOf { lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0 } }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            enabled = isListAtTop,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding())
        ) {
        @Composable
        fun RenderLocalSection(sectionId: String) {
            when (sectionId) {
                "RECENTLY_PLAYED" -> {
                    if (showRecentlyPlayed) {
                        ModernRecentlyPlayedSection(
                            recentlyPlayed = recentlyPlayed.take(recentlyPlayedCount),
                            onSongClick = onSongClick,
                            musicViewModel = musicViewModel,
                            coroutineScope = coroutineScope,
                            widthSizeClass = widthSizeClass,
                            heightSizeClass = heightSizeClass
                        )
                    }
                }
                "ARTISTS" -> {
                    if (showArtists) {
                        if (availableArtists.isNotEmpty()) {
                            ModernArtistsSection(
                                artists = availableArtists.take(artistsCount),
                                songs = allSongs,
                                onArtistClick = onArtistClick,
                                onViewAllArtists = onViewAllArtists,
                                widthSizeClass = widthSizeClass,
                                heightSizeClass = heightSizeClass
                            )
                        } else {
                            Column {
                                ModernSectionTitle(
                                    title = context.getString(R.string.home_artists),
                                    subtitle = context.getString(R.string.home_explore_musicians),
                                    viewAllAction = onViewAllArtists
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                ModernEmptyState(
                                    icon = RhythmIcons.ArtistFilled,
                                    title = context.getString(R.string.home_no_artists),
                                    subtitle = context.getString(R.string.home_no_artists_desc),
                                    iconSize = 32.dp
                                )
                            }
                        }
                    }
                }
                "NEW_RELEASES" -> {
                    if (showNewReleases) {
                        Column {
                            ModernSectionTitle(
                                title = context.getString(R.string.home_new_releases),
                                subtitle = context.getString(R.string.home_fresh_music),
                                onPlayAll = {
                                    coroutineScope.launch {
                                        val allNewReleaseSongs = newReleases.flatMap { album ->
                                            musicViewModel.getMusicRepository().getSongsForAlbumLocal(album.id)
                                        }
                                        if (allNewReleaseSongs.isNotEmpty()) {
                                            musicViewModel.playSongs(allNewReleaseSongs)
                                        }
                                    }
                                },
                                onShufflePlay = {
                                    coroutineScope.launch {
                                        val allNewReleaseSongs = newReleases.flatMap { album ->
                                            musicViewModel.getMusicRepository().getSongsForAlbumLocal(album.id)
                                        }
                                        if (allNewReleaseSongs.isNotEmpty()) {
                                            musicViewModel.playShuffled(allNewReleaseSongs)
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            if (newReleases.isNotEmpty()) {
                                val newReleasesListState = rememberLazyListState()
                                LazyRow(
                                    state = newReleasesListState,
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(
                                        items = newReleases.take(newReleasesCount),
                                        key = { "newrelease_${it.id}" },
                                        contentType = { "album" }
                                    ) { album ->
                                        ModernAlbumCard(
                                            album = album,
                                            onClick = { onAlbumClick(album) },
                                            widthSizeClass = widthSizeClass,
                                            heightSizeClass = heightSizeClass
                                        )
                                    }
                                }
                            } else {
                                ModernEmptyState(
                                    icon = MaterialSymbolIcon("new_releases", filled = true),
                                    title = context.getString(R.string.home_no_new_releases),
                                    subtitle = context.getString(R.string.home_no_new_releases_desc),
                                    iconSize = 32.dp
                                )
                            }
                        }
                    }
                }
                "RECENTLY_ADDED" -> {
                    if (showRecentlyAdded) {
                        Column {
                            ModernSectionTitle(
                                title = context.getString(R.string.home_recently_added),
                                subtitle = context.getString(R.string.home_latest_additions),
                                onPlayAll = {
                                    if (recentlyAddedSongs.isNotEmpty()) {
                                        musicViewModel.playSongs(recentlyAddedSongs)
                                    }
                                },
                                onShufflePlay = {
                                    if (recentlyAddedSongs.isNotEmpty()) {
                                        musicViewModel.playShuffled(recentlyAddedSongs)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            if (recentlyAddedAlbums.isNotEmpty()) {
                                val recentlyAddedListState = rememberLazyListState()
                                LazyRow(
                                    state = recentlyAddedListState,
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(
                                        items = recentlyAddedAlbums.take(recentlyAddedCount),
                                        key = { "recentalbum_${it.id}" },
                                        contentType = { "album" }
                                    ) { album ->
                                        ModernAlbumCard(
                                            album = album,
                                            onClick = { onAlbumClick(album) },
                                            widthSizeClass = widthSizeClass,
                                            heightSizeClass = heightSizeClass
                                        )
                                    }
                                }
                            } else {
                                ModernEmptyState(
                                    icon = MaterialSymbolIcon("library_add", filled = true),
                                    title = context.getString(R.string.home_no_recently_added),
                                    subtitle = context.getString(R.string.home_no_recently_added_desc),
                                    iconSize = 32.dp
                                )
                            }
                        }
                    }
                }
                "RECOMMENDED" -> {
                    if (showRecommended) {
                        val favoriteSongsState = musicViewModel.favoriteSongs.collectAsState()
                        val recommendedSongs = remember(recentlyPlayed, songs, recommendedCount, favoriteSongsState.value) {
                            val favoriteIds = favoriteSongsState.value
                            val favoriteSongsList = songs.filter { it.id in favoriteIds }

                            var result = if (recentlyPlayed.isNotEmpty()) {
                                val playedArtists = recentlyPlayed.map { it.artist }.distinct()
                                val playedAlbums = recentlyPlayed.map { it.album }.distinct()

                                songs.filter { song ->
                                    (song.artist in playedArtists || song.album in playedAlbums) &&
                                            !recentlyPlayed.contains(song)
                                }.shuffled()
                            } else {
                                emptyList()
                            }

                            if (result.isEmpty() && recentlyPlayed.isNotEmpty()) {
                                val playedArtists = recentlyPlayed.map { it.artist }.distinct()
                                val playedAlbums = recentlyPlayed.map { it.album }.distinct()
                                result = songs.filter { song ->
                                    song.artist in playedArtists || song.album in playedAlbums
                                }.shuffled()
                            }

                            if (result.isEmpty()) {
                                result = favoriteSongsList.shuffled()
                            }

                            if (result.isEmpty()) {
                                result = songs.shuffled()
                            }

                            result.take(recommendedCount)
                        }

                        ModernRecommendedSection(
                            recommendedSongs = recommendedSongs,
                            artists = availableArtists,
                            onSongClick = onSongClick,
                            onPlayClick = { songsToPlay ->
                                musicViewModel.playSongs(songsToPlay)
                            }
                        )
                    }
                }
                "RHYTHM_GUARD" -> {
                    if (showRhythmGuardWidget && rhythmGuardMode != AppSettings.RHYTHM_GUARD_MODE_OFF) {
                        val rhythmGuardTimeoutRemainingMs = (rhythmGuardTimeoutUntilMs - System.currentTimeMillis()).coerceAtLeast(0L)
                        val isRhythmGuardTimeoutActive = rhythmGuardTimeoutRemainingMs > 0L

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ModernSectionTitle(
                                title = stringResource(id = R.string.settings_rhythm_guard),
                                subtitle = stringResource(id = R.string.settings_rhythm_guard_list_desc)
                            )
                            RhythmGuardCard(
                                rhythmGuardMode = rhythmGuardMode,
                                rhythmGuardRecommendedMinutes = rhythmGuardRecommendedMinutes,
                                todayListeningMinutes = todayListeningMinutes,
                                isGuardTimeoutActive = isRhythmGuardTimeoutActive,
                                guardTimeoutRemainingMs = rhythmGuardTimeoutRemainingMs,
                                onCardClick = onNavigateToRhythmGuard
                            )
                        }
                    }
                }
                "STATS" -> {
                    if (showListeningStats) {
                        ModernListeningStatsSection(onClick = onNavigateToStats)
                    }
                }
            }
        }

        fun isLocalSectionVisible(sectionId: String): Boolean = when (sectionId) {
            "RECENTLY_PLAYED" -> showRecentlyPlayed
            "ARTISTS" -> showArtists
            "NEW_RELEASES" -> showNewReleases
            "RECENTLY_ADDED" -> showRecentlyAdded
            "RECOMMENDED" -> showRecommended
            "RHYTHM_GUARD" -> showRhythmGuardWidget && rhythmGuardMode != AppSettings.RHYTHM_GUARD_MODE_OFF
            "STATS" -> showListeningStats
            else -> false
        }

        if (isLandscapeTablet) {
            if (showDiscoverCarousel && sectionOrder.contains("DISCOVER")) {
                item(key = "section_discover") {
                    if (currentFeaturedAlbums.isNotEmpty()) {
                        ModernFeaturedSection(
                            albums = currentFeaturedAlbums,
                            onAlbumClick = onAlbumClick,
                            showAlbumName = discoverShowAlbumName,
                            showArtistName = discoverShowArtistName,
                            showYear = discoverShowYear,
                            showPlayButton = discoverShowPlayButton,
                            showGradient = discoverShowGradient,
                            widthSizeClass = widthSizeClass,
                            heightSizeClass = heightSizeClass
                        )
                    } else {
                        Box(modifier = Modifier.padding(horizontal = horizontalPadding)) {
                            ModernEmptyState(
                                icon = RhythmIcons.AlbumFilled,
                                title = context.getString(R.string.home_no_featured_albums),
                                subtitle = context.getString(R.string.home_no_featured_albums_desc),
                                iconSize = 32.dp
                            )
                        }
                    }
                }
            }

            val visibleSections = sectionOrder.filter { it != "DISCOVER" && isLocalSectionVisible(it) }
            val leftSections = visibleSections.filterIndexed { index, _ -> index % 2 == 0 }
            val rightSections = visibleSections.filterIndexed { index, _ -> index % 2 == 1 }

            if (visibleSections.isNotEmpty()) {
                item(key = "two_column_home_sections") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                        ) {
                            for (sectionId in leftSections) {
                                RenderLocalSection(sectionId)
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                        ) {
                            for (sectionId in rightSections) {
                                RenderLocalSection(sectionId)
                            }
                        }
                    }
                }
            }
        } else {
            sectionOrder.forEach { sectionId ->
                when (sectionId) {
                    "DISCOVER" -> {
                        if (showDiscoverCarousel) {
                            item(key = "section_discover") {
                                Column {
                                    if (currentFeaturedAlbums.isNotEmpty()) {
                                        ModernFeaturedSection(
                                            albums = currentFeaturedAlbums,
                                            onAlbumClick = onAlbumClick,
                                            showAlbumName = discoverShowAlbumName,
                                            showArtistName = discoverShowArtistName,
                                            showYear = discoverShowYear,
                                            showPlayButton = discoverShowPlayButton,
                                            showGradient = discoverShowGradient,
                                            widthSizeClass = widthSizeClass,
                                            heightSizeClass = heightSizeClass
                                        )
                                    } else {
                                        Box(modifier = Modifier.padding(horizontal = horizontalPadding)) {
                                            ModernEmptyState(
                                                icon = RhythmIcons.AlbumFilled,
                                                title = context.getString(R.string.home_no_featured_albums),
                                                subtitle = context.getString(R.string.home_no_featured_albums_desc),
                                                iconSize = 32.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (isLocalSectionVisible(sectionId)) {
                            item(key = "section_${sectionId.lowercase()}") {
                                Box(modifier = Modifier.padding(horizontal = horizontalPadding)) {
                                    RenderLocalSection(sectionId)
                                }
                            }
                        }
                    }
                }
            }
        }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModernRecentlyPlayedSection(
    recentlyPlayed: List<Song>,
    onSongClick: (Song) -> Unit,
    musicViewModel: chromahub.rhythm.app.viewmodel.MusicViewModel,
    coroutineScope: CoroutineScope,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    heightSizeClass: WindowHeightSizeClass = WindowHeightSizeClass.Medium
) {
    val context = LocalContext.current
    Column {
        ModernSectionTitle(
            title = context.getString(R.string.home_recently_played),
            subtitle = context.getString(R.string.home_recently_played_subtitle),
            onPlayAll = {
                coroutineScope.launch {
                    if (recentlyPlayed.isNotEmpty()) {
                        musicViewModel.playSongs(recentlyPlayed)
                    }
                }
            },
            onShufflePlay = {
                coroutineScope.launch {
                    if (recentlyPlayed.isNotEmpty()) {
                        musicViewModel.playShuffled(recentlyPlayed)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (recentlyPlayed.isNotEmpty()) {
            val isTablet = widthSizeClass != WindowWidthSizeClass.Compact
            if (isTablet) {
                // Grid layout for tablets - better use of space
                val gridColumns = when (widthSizeClass) {
                    WindowWidthSizeClass.Medium -> 2
                    WindowWidthSizeClass.Expanded -> 3
                    else -> 2
                }
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(
                        items = recentlyPlayed,
                        key = { "recentplay_${it.id}" },
                        contentType = { "song" }
                    ) { song ->
                        ModernRecentSongCard(
                            song = song,
                            onClick = { onSongClick(song) },
                            widthSizeClass = widthSizeClass,
                            heightSizeClass = heightSizeClass
                        )
                    }
                }
            } else {
                // Horizontal scroll for phones
                val recentlyPlayedListState = rememberLazyListState()
                LazyRow(
                    state = recentlyPlayedListState,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = recentlyPlayed,
                        key = { "recentplay_${it.id}" },
                        contentType = { "song" }
                    ) { song ->
                        ModernRecentSongCard(
                            song = song,
                            onClick = { onSongClick(song) },
                            widthSizeClass = widthSizeClass,
                            heightSizeClass = heightSizeClass
                        )
                    }
                }
            }
        } else {
            // Empty state for recently played
            ModernEmptyState(
                icon = MaterialSymbolIcon("history", filled = true),
                title = context.getString(R.string.home_no_recent_activity),
                subtitle = context.getString(R.string.home_no_recent_activity_desc),
                iconSize = 32.dp
            )
        }
    }
}

@Composable
private fun ModernRecentSongCard(
    song: Song,
    onClick: () -> Unit,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    heightSizeClass: WindowHeightSizeClass = WindowHeightSizeClass.Medium
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val (cardWidth, cardHeight) = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 160.dp to 70.dp
            else -> 180.dp to 80.dp
        }
        WindowWidthSizeClass.Medium -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> Dp.Unspecified to 90.dp
            else -> Dp.Unspecified to 95.dp
        }
        WindowWidthSizeClass.Expanded -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> Dp.Unspecified to 100.dp
            else -> Dp.Unspecified to 100.dp
        }
        else -> 180.dp to 80.dp
    }

    val cardModifier = if (cardWidth == Dp.Unspecified) {
        Modifier
            .fillMaxWidth()
            .height(cardHeight)
    } else {
        Modifier
            .width(cardWidth)
            .height(cardHeight)
    }

    ExpressiveCard(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
            onClick()
        },
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = ExpressiveShapes.Large
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3ImageUtils.TrackImage(
                imageUrl = song.artworkUri,
                trackName = song.title,
                modifier = Modifier.size(52.dp),
                applyExpressiveShape = true
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = RhythmIcons.Play,
                contentDescription = context.getString(R.string.cd_play),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ModernSectionTitle(
    title: String,
    subtitle: String? = null,
    viewAllAction: (() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    onShufflePlay: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onPlayAll != null || onShufflePlay != null) {
                RhythmGroupedButton(
                    size = RhythmButtonSize.Small,
                    isFillMaxWidth = false,
                    modifier = Modifier.widthIn(max = 120.dp)
                ) {
                    val playAction = onPlayAll
                    val shuffleAction = onShufflePlay
                    if (playAction != null) {
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                playAction()
                            },
                            weight = 1f,
                            isFirst = true,
                            isLast = shuffleAction == null,
                            text = context.getString(R.string.action_play)
                        )
                    }

                    if (shuffleAction != null) {
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                shuffleAction()
                            },
                            weight = 1f,
                            isFirst = playAction == null,
                            isLast = true,
                            icon = RhythmIcons.Shuffle,
                            text = if (playAction == null) context.getString(R.string.cd_shuffle) else null
                        )
                    }
                }
            }

            viewAllAction?.let { action ->
                IconButton(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        action()
                    }
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("arrow_forward", filled = true),
                        contentDescription = context.getString(R.string.ui_view_all),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ModernFeaturedSection(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    showAlbumName: Boolean = true,
    showArtistName: Boolean = true,
    showYear: Boolean = true,
    showPlayButton: Boolean = true,
    showGradient: Boolean = true,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    heightSizeClass: WindowHeightSizeClass = WindowHeightSizeClass.Medium,
    onPlayAlbum: ((Album) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val viewModel = viewModel<chromahub.rhythm.app.viewmodel.MusicViewModel>()

    val screenWidth = windowScreenWidthDp().dp
    val isTablet = widthSizeClass != WindowWidthSizeClass.Compact
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    if (isTablet) {
        val carouselState = rememberCarouselState { albums.size }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (albums.isNotEmpty() && !carouselState.isScrollInProgress) {
                        coroutineScope.launch {
                            carouselState.scrollToItem(carouselState.currentItem.coerceIn(0, albums.lastIndex))
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(albums.size) {
            if (albums.size > 1) {
                while (true) {
                    delay(4500)
                    if (!carouselState.isScrollInProgress) {
                        val nextItem = (carouselState.currentItem + 1) % albums.size
                        carouselState.animateScrollToItem(
                            nextItem,
                            animationSpec = tween(durationMillis = 800)
                        )
                    }
                }
            }
        }

        val carouselHeight = when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 240.dp
            else -> 280.dp
        }
        val preferredItemWidth = when (widthSizeClass) {
            WindowWidthSizeClass.Expanded -> 360.dp
            else -> 280.dp
        }
        val horizontalPadding = when (widthSizeClass) {
            WindowWidthSizeClass.Expanded -> 32.dp
            else -> 16.dp
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 8.dp)
        ) {
            HorizontalMultiBrowseCarousel(
                state = carouselState,
                preferredItemWidth = preferredItemWidth,
                itemSpacing = 16.dp,
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                minSmallItemWidth = 48.dp,
                maxSmallItemWidth = 140.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(carouselHeight)
            ) { page ->
                val album = albums.getOrNull(page) ?: return@HorizontalMultiBrowseCarousel
                Card(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        onAlbumClick(album)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .maskClip(MaterialTheme.shapes.extraLarge),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        M3ImageUtils.AlbumArt(
                            imageUrl = album.artworkUri,
                            albumName = album.title,
                            modifier = Modifier.fillMaxSize(),
                            applyExpressiveShape = false
                        )

                        if (showGradient) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                            ),
                                            startY = 0f,
                                            endY = Float.POSITIVE_INFINITY
                                        )
                                    )
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            if (showAlbumName) {
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (showArtistName) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = album.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (showPlayButton) {
                                    Button(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                            if (onPlayAlbum != null) onPlayAlbum(album) else viewModel.playAlbum(album)
                                        },
                                        shape = RoundedCornerShape(percent = 50),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text(
                                            text = context.getString(R.string.action_play),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (showYear && album.year > 0) {
                                        Text(
                                            text = album.year.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                    album.songs.firstOrNull()?.let { firstSong ->
                                        AudioQualityIcon(song = firstSong)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val carouselState = rememberCarouselState { albums.size }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (albums.isNotEmpty() && !carouselState.isScrollInProgress) {
                        coroutineScope.launch {
                            carouselState.scrollToItem(carouselState.currentItem.coerceIn(0, albums.lastIndex))
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(albums.size) {
            if (albums.size > 1) {
                if (!carouselState.isScrollInProgress) {
                    carouselState.scrollToItem(carouselState.currentItem.coerceIn(0, albums.lastIndex))
                }
                while (true) {
                    delay(4500)
                    if (!carouselState.isScrollInProgress) {
                        val currentItem = carouselState.currentItem
                        val nextItem = (currentItem + 1) % albums.size
                        carouselState.animateScrollToItem(
                            nextItem,
                            animationSpec = tween(durationMillis = 900)
                        )
                    }
                }
            }
        }
        val headerHeight = when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 280.dp
            else -> screenWidth
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
        ) {
            HorizontalUncontainedCarousel(
                state = carouselState,
                itemWidth = screenWidth,
                itemSpacing = 0.dp,
                contentPadding = PaddingValues(0.dp),
                flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state = carouselState),
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val album = albums.getOrNull(page) ?: return@HorizontalUncontainedCarousel

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RectangleShape)
                        .clickable {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onAlbumClick(album)
                        }
                ) {
                    M3ImageUtils.AlbumArt(
                        imageUrl = album.artworkUri,
                        albumName = album.title,
                        modifier = Modifier.fillMaxSize(),
                        applyExpressiveShape = false
                    )

                    if (showGradient) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.background,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                            Color.Transparent,
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                            MaterialTheme.colorScheme.background
                                        ),
                                        startY = 0f,
                                        endY = Float.POSITIVE_INFINITY
                                    )
                                )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        if (showAlbumName) {
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (showArtistName) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = album.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showPlayButton) {
                                Button(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                        if (onPlayAlbum != null) onPlayAlbum(album) else viewModel.playAlbum(album)
                                    },
                                    shape = RoundedCornerShape(percent = 50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Text(
                                        text = context.getString(R.string.action_play),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (showYear && album.year > 0) {
                                    Text(
                                        text = album.year.toString(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                                album.songs.firstOrNull()?.let { firstSong ->
                                    AudioQualityIcon(song = firstSong)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Additional Modern Components

@Composable
private fun ModernArtistsSection(
    artists: List<Artist>,
    songs: List<Song>,
    onArtistClick: (Artist) -> Unit,
    onViewAllArtists: () -> Unit,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    heightSizeClass: WindowHeightSizeClass = WindowHeightSizeClass.Medium
) {
    val context = LocalContext.current
    Column {
        ModernSectionTitle(
            title = context.getString(R.string.home_top_artists),
            subtitle = context.getString(R.string.home_top_artists_subtitle),
            viewAllAction = onViewAllArtists
        )

        Spacer(modifier = Modifier.height(16.dp))

        val artistsListState = rememberLazyListState()
        LazyRow(
            state = artistsListState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = artists,
                key = { "artist_${it.id}" },
                contentType = { "artist" }
            ) { artist ->
                ModernArtistCard(
                    artist = artist,
                    songs = songs,
                    onClick = { onArtistClick(artist) },
                    widthSizeClass = widthSizeClass,
                    heightSizeClass = heightSizeClass
                )
            }
        }
    }
}

@Composable
private fun ModernArtistCard(
    artist: Artist,
    songs: List<Song>,
    onClick: () -> Unit,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    heightSizeClass: WindowHeightSizeClass = WindowHeightSizeClass.Medium,
    onPlayClick: ((Artist) -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel = viewModel<chromahub.rhythm.app.viewmodel.MusicViewModel>()
    val haptic = LocalHapticFeedback.current
    val handleArtistPlayClick = onPlayClick ?: { artistToPlay -> viewModel.playArtist(artistToPlay) }

    val cardSize = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 100.dp
            else -> 120.dp
        }
        WindowWidthSizeClass.Medium -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 110.dp
            else -> 120.dp
        }
        WindowWidthSizeClass.Expanded -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 115.dp
            else -> 125.dp
        }
        else -> 120.dp
    }

    val columnModifier = Modifier
        .width(cardSize)
        .clickable {
            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
            onClick()
        }

    Column(
        modifier = columnModifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(cardSize)) {
            M3ImageUtils.ArtistImage(
                imageUrl = artist.artworkUri,
                artistName = artist.name,
                modifier = Modifier.fillMaxSize()
            )

            ExpressiveFilledIconButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    handleArtistPlayClick(artist)
                    onClick()
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = RhythmIcons.Play,
                    contentDescription = context.getString(R.string.cd_play_artist, artist.name),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ModernAlbumCard(
    album: Album,
    onClick: (Album) -> Unit,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    heightSizeClass: WindowHeightSizeClass = WindowHeightSizeClass.Medium,
    onPlayClick: ((Album) -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel = viewModel<chromahub.rhythm.app.viewmodel.MusicViewModel>()
    val haptic = LocalHapticFeedback.current
    val handlePlayClick = onPlayClick ?: { albumToPlay -> viewModel.playAlbum(albumToPlay) }

    val (cardWidth, cardHeight) = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 140.dp to 210.dp
            else -> 160.dp to 240.dp
        }
        WindowWidthSizeClass.Medium -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 180.dp to 270.dp
            else -> 200.dp to 300.dp
        }
        WindowWidthSizeClass.Expanded -> when (heightSizeClass) {
            WindowHeightSizeClass.Compact -> 200.dp to 300.dp
            else -> 220.dp to 330.dp
        }
        else -> 160.dp to 240.dp
    }

    ExpressiveCard(
        onClick = {
            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
            onClick(album)
        },
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = ExpressiveShapes.SquircleLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(
            when (widthSizeClass) {
                WindowWidthSizeClass.Compact -> 12.dp
                WindowWidthSizeClass.Medium -> 16.dp
                WindowWidthSizeClass.Expanded -> 20.dp
                else -> 12.dp
            }
        )) {
            Box {
                M3ImageUtils.AlbumArt(
                    imageUrl = album.artworkUri,
                    albumName = album.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = rememberExpressiveShapeFor(ExpressiveShapeTarget.ALBUM_ART)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            when (widthSizeClass) {
                                WindowWidthSizeClass.Compact -> 12.dp
                                WindowWidthSizeClass.Medium -> 16.dp
                                WindowWidthSizeClass.Expanded -> 20.dp
                                else -> 12.dp
                            }
                        )
                ) {
                    ExpressiveFilledIconButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            handlePlayClick(album)
                        },
                        modifier = Modifier.size(
                            when (widthSizeClass) {
                                WindowWidthSizeClass.Compact -> 40.dp
                                WindowWidthSizeClass.Medium -> 48.dp
                                WindowWidthSizeClass.Expanded -> 52.dp
                                else -> 40.dp
                            }
                        ),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Play,
                            contentDescription = context.getString(R.string.cd_play_album),
                            modifier = Modifier.size(
                                when (widthSizeClass) {
                                    WindowWidthSizeClass.Compact -> 20.dp
                                    WindowWidthSizeClass.Medium -> 24.dp
                                    WindowWidthSizeClass.Expanded -> 26.dp
                                    else -> 20.dp
                                }
                            )
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        when (widthSizeClass) {
                            WindowWidthSizeClass.Compact -> 12.dp
                            WindowWidthSizeClass.Medium -> 16.dp
                            WindowWidthSizeClass.Expanded -> 20.dp
                            else -> 12.dp
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(
                    when (widthSizeClass) {
                        WindowWidthSizeClass.Compact -> 4.dp
                        WindowWidthSizeClass.Medium -> 6.dp
                        WindowWidthSizeClass.Expanded -> 8.dp
                        else -> 4.dp
                    }
                )
            ) {
                Text(
                    text = album.title,
                    style = when (widthSizeClass) {
                        WindowWidthSizeClass.Compact -> MaterialTheme.typography.titleSmall
                        WindowWidthSizeClass.Medium -> MaterialTheme.typography.titleMedium
                        WindowWidthSizeClass.Expanded -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = when (widthSizeClass) {
                        WindowWidthSizeClass.Compact -> 18.sp
                        WindowWidthSizeClass.Medium -> 22.sp
                        WindowWidthSizeClass.Expanded -> 24.sp
                        else -> 18.sp
                    }
                )

                Text(
                    text = album.artist,
                    style = when (widthSizeClass) {
                        WindowWidthSizeClass.Compact -> MaterialTheme.typography.bodySmall
                        WindowWidthSizeClass.Medium -> MaterialTheme.typography.bodyMedium
                        WindowWidthSizeClass.Expanded -> MaterialTheme.typography.bodyMedium
                        else -> MaterialTheme.typography.bodySmall
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ModernListeningStatsSection(
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = viewModel<chromahub.rhythm.app.viewmodel.MusicViewModel>()
    val songs by viewModel.songs.collectAsState()

    var statsSummary by remember { mutableStateOf<chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository.PlaybackStatsSummary?>(null) }

    LaunchedEffect(songs) {
        statsSummary = viewModel.loadPlaybackStats(chromahub.rhythm.app.shared.data.repository.StatsTimeRange.ALL_TIME)
    }

    val listeningTimeHours = remember(statsSummary) {
        val totalMillis = statsSummary?.totalDurationMs ?: 0L
        val hours = totalMillis / (1000 * 60 * 60)
        if (hours < 1) 0 else hours.toInt()
    }

    val songsPlayed = remember(statsSummary) {
        statsSummary?.totalPlayCount ?: 0
    }

    val uniqueArtistsCount = remember(statsSummary) {
        statsSummary?.uniqueArtists ?: 0
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = context.getString(R.string.home_listening_stats),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = MaterialSymbolIcon("arrow_forward", filled = true),
                    contentDescription = stringResource(R.string.homescreen_view_all_stats),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = RhythmIcons.Player.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp).padding(bottom = 8.dp)
                )
                Text(
                    text = if (listeningTimeHours < 1) "< 1h" else "${listeningTimeHours}h",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.stats_total_listening_time),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f).clickable { onClick() },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Music.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp).padding(bottom = 4.dp)
                    )
                    Text(
                        text = "$songsPlayed",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.homescreen_plays),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f).clickable { onClick() },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Artist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp).padding(bottom = 4.dp)
                    )
                    Text(
                        text = "$uniqueArtistsCount",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = stringResource(R.string.settings_tab_artists),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernRecommendedSection(
    recommendedSongs: List<Song>,
    artists: List<Artist>,
    onSongClick: (Song) -> Unit,
    onPlayClick: (List<Song>) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val artistSeparatorEnabled by appSettings.artistSeparatorEnabled.collectAsState()
    val artistSeparatorDelimiters by appSettings.artistSeparatorDelimiters.collectAsState()
    val effectiveDelimiters = artistSeparatorDelimiters.ifBlank { AppSettings.DEFAULT_ARTIST_SEPARATOR_DELIMITERS }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        ModernSectionTitle(
            title = context.getString(R.string.home_recommended_title),
            subtitle = context.getString(R.string.home_recommended_subtitle)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (recommendedSongs.isNotEmpty()) {
            val firstSong = recommendedSongs.firstOrNull()
            val rawArtist = firstSong?.artist ?: "Unknown Artist"

            val splitNames = remember(rawArtist, effectiveDelimiters, artistSeparatorEnabled) {
                ArtistSeparator.splitArtistNames(
                    artistName = rawArtist,
                    delimiters = effectiveDelimiters,
                    enabled = artistSeparatorEnabled
                )
            }
            val primaryArtistName = splitNames.firstOrNull() ?: rawArtist

            val recommendedArtist = remember(splitNames, primaryArtistName, artists) {
                artists.find { artist ->
                    splitNames.any { splitName -> artist.name.equals(splitName, ignoreCase = true) }
                } ?: artists.find { it.name.equals(primaryArtistName, ignoreCase = true) }
            }

            val artistName = recommendedArtist?.name ?: primaryArtistName
            val artistArtworkUri = recommendedArtist?.artworkUri ?: firstSong?.artworkUri
            val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
            val onCardBgColor = MaterialTheme.colorScheme.onSurface

            val artistNameLength = artistName.length
            val artistNameStyle = when {
                artistNameLength > 20 -> MaterialTheme.typography.titleLarge
                artistNameLength > 12 -> MaterialTheme.typography.headlineMedium
                else -> MaterialTheme.typography.displaySmall
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = cardBgColor
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    RecommendedArtistHeadline(
                        artistName = artistName,
                        style = artistNameStyle,
                        color = onCardBgColor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 20.dp, bottom = 40.dp)
                            .fillMaxHeight(0.72f)
                            .aspectRatio(1f)
                    ) {
                        M3ImageUtils.ArtistImage(
                            imageUrl = artistArtworkUri,
                            artistName = artistName,
                            modifier = Modifier.fillMaxSize(),
                            applyExpressiveShape = true
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((-12).dp)
                        ) {
                            val coverSongs = recommendedSongs.take(4)
                            coverSongs.forEach { song ->
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .border(
                                            width = 1.5.dp,
                                            color = cardBgColor,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    M3ImageUtils.TrackImage(
                                        imageUrl = song.artworkUri,
                                        trackName = song.title,
                                        modifier = Modifier.fillMaxSize(),
                                        applyExpressiveShape = false
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                onPlayClick(recommendedSongs)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.cd_play),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        } else {
            ModernEmptyState(
                icon = MaterialSymbolIcon("tips_and_updates", filled = true),
                title = context.getString(R.string.home_no_recommendations),
                subtitle = context.getString(R.string.home_no_recommendations_desc),
                iconSize = 32.dp
            )
        }
    }
}

@Composable
private fun RecommendedArtistHeadline(
    artistName: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val fullWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }

        val firstLineEnd = remember(artistName, style, fullWidthPx) {
            if (artistName.isBlank() || fullWidthPx <= 0) {
                0
            } else {
                val layout = textMeasurer.measure(
                    text = AnnotatedString(artistName),
                    style = style.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    softWrap = true,
                    constraints = Constraints(maxWidth = fullWidthPx)
                )
                layout.getLineEnd(0, visibleEnd = true).coerceIn(0, artistName.length)
            }
        }

        val headlineFirstLine = remember(artistName, firstLineEnd) {
            artistName.take(firstLineEnd).trimEnd()
        }
        val headlineRemainder = remember(artistName, firstLineEnd) {
            artistName.drop(firstLineEnd).trimStart()
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (headlineFirstLine.isNotBlank()) headlineFirstLine else artistName,
                style = style,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )

            if (headlineRemainder.isNotBlank()) {
                Text(
                    text = headlineRemainder,
                    style = style,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.fillMaxWidth(0.48f),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecommendedSongItem(
    song: Song,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                onClick()
            })
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        M3ImageUtils.TrackImage(
            imageUrl = song.artworkUri,
            trackName = song.title,
            modifier = Modifier.size(52.dp),
            applyExpressiveShape = true
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${song.artist} • ${song.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        ExpressiveFilledIconButton(
            onClick = {
                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                onClick()
            },
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Icon(
                imageVector = RhythmIcons.Play,
                contentDescription = stringResource(R.string.cd_play),
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

@Composable
private fun ModernEmptyState(
    icon: MaterialSymbolIcon,
    title: String,
    subtitle: String,
    iconSize: Dp = 32.dp
) {
    val cookieShape = rememberExpressiveShape(ExpressiveMaterialShape.COOKIE_12)
    ExpressiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = ExpressiveShapes.SquircleLarge
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = cookieShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(iconSize + 16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}