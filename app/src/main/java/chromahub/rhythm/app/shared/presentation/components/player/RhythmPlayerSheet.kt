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
import androidx.compose.ui.graphics.graphicsLayer
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
        
        var dragOffset by remember { mutableFloatStateOf(0f) }
        
        val collapsedOffset = screenHeightPx - with(density) { (miniPlayerBottomOffset + miniPlayerHeight).toPx() }
        val expandedOffset = 0f
        
        val targetOffset = if (isExpanded) expandedOffset else collapsedOffset
        
        val motionConfig = MiniPlayerMotion.resolveConfig(miniPlayerThemeId)

        val animatedOffset by animateFloatAsState(
            targetValue = targetOffset + dragOffset,
            animationSpec = motionConfig.slowSpatialSpring,
            label = "sheetOffset"
        )
        
        val expansionFraction = ((collapsedOffset - animatedOffset) / collapsedOffset).coerceIn(0f, 1f)
        
        val maxCornerRadius = 32.dp
        val cornerBlendProgress = ((expansionFraction - 0.75f) / 0.25f).coerceIn(0f, 1f)
        val smoothBlend = cornerBlendProgress * cornerBlendProgress
        val topCornerSize = maxCornerRadius * (1f - smoothBlend)
        val bottomCornerSize = 28.dp * (1f - expansionFraction)
        val sheetShape = RoundedCornerShape(
            topStart = topCornerSize,
            topEnd = topCornerSize,
            bottomStart = bottomCornerSize,
            bottomEnd = bottomCornerSize
        )
        
        val bottomEdgePx = screenHeightPx - with(density) { miniPlayerBottomOffset.toPx() } * (1f - expansionFraction)
        val sheetHeightPx = (bottomEdgePx - animatedOffset).coerceAtLeast(0f)
        val sheetHeightDp = with(density) { sheetHeightPx.toDp() }

        val dragModifier = Modifier.draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { delta ->
                dragOffset = (dragOffset + delta).coerceIn(
                    minimumValue = expandedOffset - targetOffset,
                    maximumValue = screenHeightPx - targetOffset
                )
            },
            onDragStopped = { velocity ->
                val currentOffset = targetOffset + dragOffset
                if (isExpanded) {
                    if (currentOffset > collapsedOffset * 0.3f || velocity > 1000f) {
                        onCollapse()
                    }
                } else {
                    if (currentOffset > collapsedOffset + with(density) { 40.dp.toPx() } || velocity > 1000f) {
                        onMiniPlayerDismiss()
                    } else if (currentOffset < collapsedOffset * 0.7f || velocity < -1000f) {
                        onExpand()
                    }
                }
                dragOffset = 0f
            }
        )

        val sheetBackgroundAlpha = if (isExpanded) {
            1f
        } else if (dragOffset != 0f) {
            ((collapsedOffset - animatedOffset) / collapsedOffset).coerceIn(0f, 1f)
        } else {
            0f
        }
        val sheetBackgroundColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = sheetBackgroundAlpha)

        val isTablet = windowScreenWidthDp() >= 600

        val miniPlayerAlpha = (1f - (expansionFraction / 0.25f)).coerceIn(0f, 1f)
        val expandedPlayerAlpha = ((expansionFraction - 0.20f) / 0.80f).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, animatedOffset.roundToInt()) }
        ) {
            val sheetContainerModifier = if (isTablet && expansionFraction < 0.15f) {
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeightDp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(sheetHeightDp)
                    .clip(sheetShape)
                    .background(sheetBackgroundColor, sheetShape)
                    .then(dragModifier)
            }

            Box(
                modifier = sheetContainerModifier
            ) {
                val miniPlayerContainerModifier = if (isTablet && expansionFraction < 0.15f) {
                    Modifier
                        .fillMaxWidth()
                        .height(miniPlayerHeight)
                        .zIndex(if (expansionFraction < 0.25f) 1f else 0f)
                        .graphicsLayer { alpha = miniPlayerAlpha }
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(miniPlayerHeight)
                        .zIndex(if (expansionFraction < 0.25f) 1f else 0f)
                        .graphicsLayer { alpha = miniPlayerAlpha }
                        .clip(sheetShape)
                        .clickable(enabled = expansionFraction < 0.15f && dragOffset == 0f) {
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
                        .fillMaxSize()
                        .zIndex(if (expansionFraction >= 0.25f) 1f else 0f)
                        .graphicsLayer { alpha = expandedPlayerAlpha }
                        .clip(sheetShape)
                ) {
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
                        expansionFraction = expansionFraction
                    )
                }
            }
        }
    }
}
