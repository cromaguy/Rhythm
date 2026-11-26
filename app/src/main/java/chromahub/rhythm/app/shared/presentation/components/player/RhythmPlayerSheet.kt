/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.player

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import chromahub.rhythm.app.util.windowScreenWidthDp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.launch
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chromahub.rhythm.app.features.local.presentation.navigation.Screen
import chromahub.rhythm.app.features.local.presentation.screens.LibraryTab
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.data.model.*
import chromahub.rhythm.app.shared.presentation.screens.player.PlayerScreen
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmPlayerSheet(
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onMiniPlayerDismiss: () -> Unit,
    song: Song?,
    isPlaying: Boolean,
    progress: () -> Float,
    location: PlaybackLocation?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    appSettings: AppSettings,
    musicViewModel: MusicViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
    queuePosition: Int = 1,
    queueTotal: Int = 1,
    onLyricsSeek: ((Long) -> Unit)? = null,
    onLocationClick: () -> Unit = {},
    onQueueClick: () -> Unit = {},
    locations: List<PlaybackLocation> = emptyList(),
    onLocationSelect: (PlaybackLocation) -> Unit = {},
    volume: Float = 0.7f,
    isMuted: Boolean = false,
    onVolumeChange: (Float) -> Unit = {},
    onToggleMute: () -> Unit = {},
    onMaxVolume: () -> Unit = {},
    onRefreshDevices: () -> Unit = {},
    onStopDeviceMonitoring: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    isShuffleEnabled: Boolean = false,
    repeatMode: Int = 0,
    isFavorite: Boolean = false,
    showLyrics: Boolean = true,
    onlineOnlyLyrics: Boolean = false,
    lyrics: LyricsData? = null,
    isLoadingLyrics: Boolean = false,
    onRetryLyrics: () -> Unit = {},
    onEditLyrics: (String) -> Unit = {},
    onPickLyricsFile: () -> Unit = {},
    onSaveLyrics: (String, String) -> Unit = { _, _ -> },
    playlists: List<Playlist> = emptyList(),
    queue: List<Song> = emptyList(),
    onSongClick: (Song) -> Unit = {},
    onSongClickAtIndex: (Int) -> Unit = { _ -> },
    onRemoveFromQueueAtIndex: (Int) -> Unit = { _ -> },
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onAddSongsToQueue: () -> Unit = {},
    onNavigateToLibrary: (LibraryTab) -> Unit = {},
    showAddToPlaylistSheet: Boolean = false,
    onAddToPlaylistSheetDismiss: () -> Unit = {},
    onAddSongToPlaylist: (Song, String) -> Unit = { _, _ -> },
    onCreatePlaylist: (String) -> Unit = {},
    onShowCreatePlaylistDialog: (Song?) -> Unit = {},
    onClearQueue: () -> Unit = {},
    isMediaLoading: Boolean = false,
    isSeeking: Boolean = false,
    onShowAlbumBottomSheet: () -> Unit = {},
    songs: List<Song> = emptyList(),
    albums: List<Album> = emptyList(),
    artists: List<Artist> = emptyList(),
    onPlayAlbumSongs: (List<Song>) -> Unit = {},
    onShuffleAlbumSongs: (List<Song>) -> Unit = {},
    onPlayArtistSongs: (List<Song>) -> Unit = {},
    onShuffleArtistSongs: (List<Song>) -> Unit = {},
    isStreamingMode: Boolean = false,
    miniPlayerBottomOffset: Dp = 8.dp
) {
    if (song == null) return

    val density = LocalDensity.current
    val miniPlayerThemeId by appSettings.miniPlayerThemeId.collectAsState()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        
        val miniPlayerHeight = if (miniPlayerThemeId == "EXPRESSIVE") 84.dp else 110.dp
        
        val collapsedOffset = screenHeightPx - with(density) { (miniPlayerBottomOffset + miniPlayerHeight).toPx() }
        val expandedOffset = 0f

        val targetOffset = if (isExpanded) expandedOffset else collapsedOffset
        val motionConfig = MiniPlayerMotion.resolveConfig(miniPlayerThemeId)
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        val offsetAnim = remember {
            androidx.compose.animation.core.Animatable(if (isExpanded) expandedOffset else collapsedOffset)
        }
        var isDraggingSheet by remember { mutableStateOf(false) }

        LaunchedEffect(isExpanded, collapsedOffset, isDraggingSheet) {
            if (!isDraggingSheet) {
                offsetAnim.animateTo(
                    targetValue = targetOffset,
                    animationSpec = motionConfig.spatialSpring
                )
            }
        }

        val expansionFractionProvider: () -> Float = remember(collapsedOffset) {
            {
                val span = collapsedOffset.coerceAtLeast(1f)
                ((collapsedOffset - offsetAnim.value) / span).coerceIn(0f, 1f)
            }
        }

        val maxCornerRadius = 32.dp
        val sheetShape = remember(maxCornerRadius, expansionFractionProvider) {
            object : androidx.compose.ui.graphics.Shape {
                override fun createOutline(
                    size: androidx.compose.ui.geometry.Size,
                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                    density: androidx.compose.ui.unit.Density
                ): androidx.compose.ui.graphics.Outline {
                    val exp = expansionFractionProvider()
                    val cornerBlend = FullPlayerCompositionPolicy.cornerBlendProgress(exp)
                    val smoothBlend = cornerBlend * cornerBlend
                    val topPx = with(density) { (maxCornerRadius * (1f - smoothBlend)).toPx() }
                    val bottomPx = with(density) { (28.dp * (1f - exp)).toPx() }
                    return androidx.compose.ui.graphics.Outline.Rounded(
                        androidx.compose.ui.geometry.RoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height,
                            topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(topPx, topPx),
                            topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(topPx, topPx),
                            bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(bottomPx, bottomPx),
                            bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(bottomPx, bottomPx)
                        )
                    )
                }
            }
        }

        val dragModifier = Modifier.draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { delta ->
                isDraggingSheet = true
                val next = (offsetAnim.value + delta).coerceIn(expandedOffset, screenHeightPx)
                scope.launch { offsetAnim.snapTo(next) }
            },
            onDragStopped = { velocity ->
                val currentOffset = offsetAnim.value
                isDraggingSheet = false
                if (isExpanded) {
                    if (currentOffset > collapsedOffset * 0.3f || velocity > 1000f) {
                        onCollapse()
                    } else {
                        scope.launch { offsetAnim.animateTo(targetOffset, motionConfig.spatialSpring) }
                    }
                } else {
                    if (currentOffset > collapsedOffset + with(density) { 40.dp.toPx() } || velocity > 1000f) {
                        onMiniPlayerDismiss()
                    } else if (currentOffset < collapsedOffset * 0.7f || velocity < -1000f) {
                        onExpand()
                    } else {
                        scope.launch { offsetAnim.animateTo(targetOffset, motionConfig.spatialSpring) }
                    }
                }
            }
        )

        val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
        val isTablet = windowScreenWidthDp() >= 600

        // Threshold-only derived states: recomposition occurs only when crossing 0.15f/0.25f/visibility.
        val isBelowQuarterExpansion by remember(collapsedOffset) {
            androidx.compose.runtime.derivedStateOf {
                expansionFractionProvider() < FullPlayerCompositionPolicy.MINI_FADE_END_FRACTION
            }
        }
        val isMiniClickable by remember(collapsedOffset) {
            androidx.compose.runtime.derivedStateOf {
                expansionFractionProvider() < 0.15f && !isDraggingSheet
            }
        }
        val isTabletCompact by remember(isTablet, collapsedOffset) {
            androidx.compose.runtime.derivedStateOf {
                isTablet && expansionFractionProvider() < 0.15f
            }
        }
        var hasExpandedOnce by remember { mutableStateOf(isExpanded) }
        LaunchedEffect(isExpanded) {
            if (isExpanded) hasExpandedOnce = true
        }
        LaunchedEffect(Unit) {
            if (!hasExpandedOnce) {
                kotlinx.coroutines.delay(250)
                hasExpandedOnce = true
            }
        }
        val shouldRenderExpandedPlayer by remember(isExpanded, hasExpandedOnce, collapsedOffset) {
            androidx.compose.runtime.derivedStateOf {
                FullPlayerCompositionPolicy.shouldComposeExpandedContent(
                    expansionFraction = expansionFractionProvider(),
                    isExpanded = isExpanded,
                    wasPreviouslyExpanded = hasExpandedOnce
                )
            }
        }
        val miniBottomOffsetPx = with(density) { miniPlayerBottomOffset.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetAnim.value.roundToInt()) }
        ) {
            val baseHeightModifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val exp = expansionFractionProvider()
                    val bottomEdgePx = screenHeightPx - miniBottomOffsetPx * (1f - exp)
                    val heightPx = (bottomEdgePx - offsetAnim.value).coerceAtLeast(0f).roundToInt()
                    val cappedHeight = heightPx.coerceIn(constraints.minHeight, constraints.maxHeight)
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = cappedHeight, maxHeight = cappedHeight)
                    )
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                }

            val sheetContainerModifier = if (isTabletCompact) {
                baseHeightModifier
            } else {
                baseHeightModifier
                    .graphicsLayer {
                        clip = true
                        shape = sheetShape
                    }
                    .drawBehind {
                        val bgAlpha = if (isExpanded) 1f
                        else if (isDraggingSheet) expansionFractionProvider()
                        else 0f
                        if (bgAlpha > 0f) {
                            drawRect(color = surfaceContainerColor.copy(alpha = bgAlpha))
                        }
                    }
                    .then(dragModifier)
            }

            Box(
                modifier = sheetContainerModifier
            ) {
                val miniPlayerContainerModifier = if (isTabletCompact) {
                    Modifier
                        .fillMaxWidth()
                        .height(miniPlayerHeight)
                        .zIndex(if (isBelowQuarterExpansion) 1f else 0f)
                        .graphicsLayer {
                            alpha = FullPlayerCompositionPolicy.miniPlayerAlpha(expansionFractionProvider())
                        }
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(miniPlayerHeight)
                        .zIndex(if (isBelowQuarterExpansion) 1f else 0f)
                        .graphicsLayer {
                            alpha = FullPlayerCompositionPolicy.miniPlayerAlpha(expansionFractionProvider())
                        }
                        .clickable(enabled = isMiniClickable) {
                            onExpand()
                        }
                }

                Box(
                    modifier = miniPlayerContainerModifier
                ) {
                    MiniPlayer(
                        song = song,
                        isPlaying = isPlaying,
                        progress = progress,
                        onPlayPause = onPlayPause,
                        onPlayerClick = onExpand,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onDismiss = onMiniPlayerDismiss,
                        isMediaLoading = isMediaLoading,
                        verticalDragEnabled = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .zIndex(if (!isBelowQuarterExpansion) 1f else 0f)
                        .layout { measurable, constraints ->
                            val fullHeight = screenHeightPx.roundToInt().coerceAtLeast(1)
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = constraints.maxWidth,
                                    maxWidth = constraints.maxWidth,
                                    minHeight = fullHeight,
                                    maxHeight = fullHeight
                                )
                            )
                            val layoutHeight = constraints.maxHeight.coerceIn(0, fullHeight)
                            layout(placeable.width, layoutHeight) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                        .graphicsLayer {
                            alpha = FullPlayerCompositionPolicy.expandedPlayerAlpha(expansionFractionProvider())
                        }
                ) {
                    if (shouldRenderExpandedPlayer) {
                        WithoutPlatformOverscroll {
                            PlayerScreen(
                        song = song,
                        isPlaying = isPlaying,
                        progress = progress,
                        location = location,
                        queuePosition = queuePosition,
                        queueTotal = queueTotal,
                        onPlayPause = onPlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onSeek = onSeek,
                        onLyricsSeek = onLyricsSeek,
                        onBack = onCollapse,
                        onLocationClick = onLocationClick,
                        onQueueClick = onQueueClick,
                        locations = locations,
                        onLocationSelect = onLocationSelect,
                        volume = volume,
                        isMuted = isMuted,
                        onVolumeChange = onVolumeChange,
                        onToggleMute = onToggleMute,
                        onMaxVolume = onMaxVolume,
                        onRefreshDevices = onRefreshDevices,
                        onStopDeviceMonitoring = onStopDeviceMonitoring,
                        onToggleShuffle = { onToggleShuffle() },
                        onToggleRepeat = { onToggleRepeat() },
                        onToggleFavorite = { onToggleFavorite() },
                        onAddToPlaylist = onAddToPlaylist,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        isFavorite = isFavorite,
                        showLyrics = showLyrics,
                        onlineOnlyLyrics = onlineOnlyLyrics,
                        lyrics = lyrics,
                        isLoadingLyrics = isLoadingLyrics,
                        onRetryLyrics = onRetryLyrics,
                        onEditLyrics = onEditLyrics,
                        onPickLyricsFile = onPickLyricsFile,
                        onSaveLyrics = onSaveLyrics,
                        playlists = playlists,
                        queue = queue,
                        onSongClick = onSongClick,
                        onSongClickAtIndex = onSongClickAtIndex,
                        onRemoveFromQueueAtIndex = onRemoveFromQueueAtIndex,
                        onMoveQueueItem = onMoveQueueItem,
                        onAddSongsToQueue = onAddSongsToQueue,
                        onNavigateToLibrary = onNavigateToLibrary,
                        showAddToPlaylistSheet = showAddToPlaylistSheet,
                        onAddToPlaylistSheetDismiss = onAddToPlaylistSheetDismiss,
                        onAddSongToPlaylist = onAddSongToPlaylist,
                        onCreatePlaylist = onCreatePlaylist,
                        onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
                        onClearQueue = onClearQueue,
                        isMediaLoading = isMediaLoading,
                        isSeeking = isSeeking,
                        onShowAlbumBottomSheet = onShowAlbumBottomSheet,
                        songs = songs,
                        albums = albums,
                        artists = artists,
                        onPlayAlbumSongs = onPlayAlbumSongs,
                        onShuffleAlbumSongs = onShuffleAlbumSongs,
                        onPlayArtistSongs = onPlayArtistSongs,
                        onShuffleArtistSongs = onShuffleArtistSongs,
                        appSettings = appSettings,
                        musicViewModel = musicViewModel,
                        navController = navController,
                        isStreamingMode = isStreamingMode,
                                swipeToDismissEnabled = false,
                                expansionFractionProvider = expansionFractionProvider
                            )
                        }
                    }
                }
            }
        }
    }
}
