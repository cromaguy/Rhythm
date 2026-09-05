/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.player

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import chromahub.rhythm.app.shared.presentation.components.lyrics.FullScreenLyricsView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavController
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ArtistChooserBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ExtraControlBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AddToPlaylistBottomSheet

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.PlaybackBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.QueueBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongInfoBottomSheet
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaybackPitchDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaybackSpeedDialog
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.PlaybackSpeedAndPitchBottomSheet
import chromahub.rhythm.app.shared.presentation.components.player.ExpressiveBottomButtonsOrderBottomSheet
import chromahub.rhythm.app.shared.presentation.components.player.SleepTimerBottomSheetNew
import chromahub.rhythm.app.shared.presentation.components.lyrics.LyricsEditorBottomSheet
import chromahub.rhythm.app.shared.presentation.components.player.formatDuration
import chromahub.rhythm.app.features.local.presentation.navigation.Screen
import chromahub.rhythm.app.features.local.presentation.screens.LibraryTab
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.findAlbumForSong
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.shared.data.model.PlaybackLocation
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import chromahub.rhythm.app.network.AppleMusicCanvasProvider
import chromahub.rhythm.app.network.CanvasArtwork
import chromahub.rhythm.app.shared.data.model.CanvasNetworkMode
import chromahub.rhythm.app.core.utils.NetworkUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    song: Song?,
    isPlaying: Boolean,
    progress: () -> Float,
    location: PlaybackLocation?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit,
    onLocationClick: () -> Unit,
    onQueueClick: () -> Unit,
    appSettings: AppSettings,
    musicViewModel: MusicViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
    queuePosition: Int = 1,
    queueTotal: Int = 1,
    onLyricsSeek: ((Long) -> Unit)? = null,
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
    swipeToDismissEnabled: Boolean = true,
    expansionFraction: Float = 1f
) {
    val playerThemeId by appSettings.playerThemeId.collectAsState()
    var showFullScreenLyrics by remember { mutableStateOf(false) }

    BackHandler(enabled = showFullScreenLyrics || expansionFraction > 0.5f) {
        if (showFullScreenLyrics) {
            showFullScreenLyrics = false
        } else {
            onBack()
        }
    }

    val context = LocalContext.current
    val lyricsTimeOffset by musicViewModel.lyricsTimeOffset.collectAsState()
    var showLyricsEditorDialog by remember { mutableStateOf(false) }

    val appleCanvasEnabled by appSettings.appleCanvasEnabled.collectAsState()
    val appleCanvasNetworkMode by appSettings.appleCanvasNetworkMode.collectAsState()
    var canvasArtwork by remember(song?.id) { mutableStateOf<CanvasArtwork?>(null) }
    var canvasLoading by remember(song?.id) { mutableStateOf(false) }

    LaunchedEffect(song?.id, appleCanvasEnabled, appleCanvasNetworkMode) {
        // Reset immediately so stale canvas from previous track is gone
        canvasArtwork = null
        canvasLoading = false

        if (song != null && appleCanvasEnabled) {
            val hasNetwork = if (appleCanvasNetworkMode == CanvasNetworkMode.WIFI_ONLY) {
                NetworkUtils.isWifiConnected(context)
            } else {
                NetworkUtils.isNetworkAvailable(context)
            }
            if (hasNetwork) {
                canvasLoading = true
                val result = withContext(Dispatchers.IO) {
                    AppleMusicCanvasProvider.getBySongArtist(
                        song = song.title,
                        artist = song.artist,
                        album = song.album
                    )
                }
                canvasLoading = false
                canvasArtwork = result
            }
        }
    }


    val lyricsWritePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            musicViewModel.completeLyricsWriteAfterPermission(
                onSuccess = { },
                onError = { errorMessage ->
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            )
        } else {
            musicViewModel.cancelPendingLyricsWrite()
            Toast.makeText(context, R.string.materialplayerscreen_permission_denied_could_not, Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (playerThemeId != "MATERIAL") {
        val haptic = LocalHapticFeedback.current
        val useHoursFormat by appSettings.useHoursInTimeFormat.collectAsState()
        val showRemainingTime by appSettings.showRemainingTime.collectAsState()
        val progressValue = progress().coerceIn(0f, 1f)
        val vmDurationMs by musicViewModel.duration.collectAsState()
        val totalTimeMs = song?.duration?.takeIf { it > 0 } ?: vmDurationMs.takeIf { it > 0 } ?: 0L
        val currentTimeMs = (progressValue * totalTimeMs).toLong()
        
        val currentSeconds = currentTimeMs / 1000
        val totalSeconds = totalTimeMs / 1000
        val remainingSeconds = (totalSeconds - currentSeconds).coerceAtLeast(0L)
        
        val currentTimeStr = remember(currentSeconds, useHoursFormat) {
            formatDuration(currentSeconds * 1000, useHoursFormat)
        }
        val totalTimeFormatted = remember(totalTimeMs, useHoursFormat) {
            formatDuration(totalSeconds * 1000, useHoursFormat)
        }
        val totalTimeStr = if (showRemainingTime) {
            remember(remainingSeconds, useHoursFormat) {
                "-" + formatDuration(remainingSeconds * 1000, useHoursFormat)
            }
        } else {
            totalTimeFormatted
        }

        var showQueueSheet by remember { mutableStateOf(false) }
        var showSongInfoSheet by remember { mutableStateOf(false) }
        var showMoreSheet by remember { mutableStateOf(false) }
        var showExpressiveBottomButtonsSheet by remember { mutableStateOf(false) }
        var showDeviceOutputSheet by remember { mutableStateOf(false) }
        var showAddToPlaylistSheetInternal by remember { mutableStateOf(false) }
        var showPlaybackSpeedDialog by remember { mutableStateOf(false) }
        var showPlaybackPitchDialog by remember { mutableStateOf(false) }
        var showSleepTimerBottomSheet by remember { mutableStateOf(false) }
        var showAlbumSheet by remember { mutableStateOf(false) }
        var selectedAlbum by remember { mutableStateOf<Album?>(null) }
        var selectedSongForPlaylist by remember { mutableStateOf<Song?>(null) }
        var showLyricsView by remember { mutableStateOf(false) }
        var showArtistChooserSheet by remember { mutableStateOf(false) }
        var candidateArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
        var pendingMetadataEditCompleteCallback by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

        val playbackSpeed by musicViewModel.playbackSpeed.collectAsState()
        val playbackPitch by musicViewModel.playbackPitch.collectAsState()
        val sleepTimerActive by musicViewModel.sleepTimerActive.collectAsState()
        val sleepTimerRemainingSeconds by musicViewModel.sleepTimerRemainingSeconds.collectAsState()
        val equalizerEnabled by musicViewModel.equalizerEnabled.collectAsState()
        val hiddenChips by appSettings.hiddenPlayerChips.collectAsState()
        val syncSpeedAndPitch by appSettings.syncSpeedAndPitch.collectAsState()
        val playerMergeControlsToBottom by appSettings.playerMergeControlsToBottom.collectAsState()
        val artistSeparatorEnabled by appSettings.artistSeparatorEnabled.collectAsState()
        val artistSeparatorDelimiters by appSettings.artistSeparatorDelimiters.collectAsState()

        val splitArtistNames: (String) -> List<String> = remember(artistSeparatorDelimiters, artistSeparatorEnabled) {
            { artistName ->
                chromahub.rhythm.app.util.ArtistSeparator.splitArtistNames(
                    artistName = artistName,
                    delimiters = artistSeparatorDelimiters,
                    enabled = artistSeparatorEnabled
                )
            }
        }

        fun resolveAlbumForSong(currentSong: Song): Album? {
            return albums.findAlbumForSong(currentSong)
        }

        fun resolveArtistForSong(currentSong: Song): Artist? {
            val artistNames = splitArtistNames(currentSong.artist)

            val matched = artistNames.firstNotNullOfOrNull { name ->
                artists.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }
            if (matched != null) return matched

            val fallbackName = artistNames.firstOrNull()?.trim()
            return if (fallbackName != null) {
                Artist(id = fallbackName, name = fallbackName)
            } else {
                null
            }
        }
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

        val queueSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        val deviceOutputSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        val addToPlaylistSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        val albumBottomSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        val currentSongAlbumForSheet = remember(song, albums, songs) {
            song?.let { currentSong ->
                resolveAlbumForSong(currentSong)
            }
        }
        val currentSongArtistForSheet = remember(song, artists) {
            song?.let { currentSong ->
                resolveArtistForSong(currentSong)
            }
        }

        ExpressivePlayerScreen(
            song = song,
            isPlaying = isPlaying,
            isFavorite = isFavorite,
            progress = { progressValue },
            currentTimeStr = currentTimeStr,
            totalTimeStr = totalTimeStr,
            onTotalTimeClick = { appSettings.setShowRemainingTime(!showRemainingTime) },
            queuePosition = queuePosition,
            queueTotal = queueTotal,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            showLyricsView = showLyricsView,
            showLyrics = showLyrics,
            lyrics = lyrics,
            isLoadingLyrics = isLoadingLyrics,
            onlineOnlyLyrics = onlineOnlyLyrics,
            onLyricsSeek = onLyricsSeek,
            onRetryLyrics = onRetryLyrics,
            onShowLyricsEditor = { showLyricsEditorDialog = true },
            onPickLyricsFile = onPickLyricsFile,
            onNavigateToLyricsSettings = {
                try {
                    navController.navigate(Screen.TunerLyrics.route) {
                        popUpTo(Screen.Player.route) {
                            inclusive = true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerScreen", "Failed to navigate to lyrics settings", e)
                }
            },
            isMediaLoading = isMediaLoading,
            isSeeking = isSeeking,
            onPlayPause = onPlayPause,
            onSeek = onSeek,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext,
            onToggleFavorite = onToggleFavorite,
            onToggleShuffle = onToggleShuffle,
            onToggleRepeat = onToggleRepeat,
            onToggleLyrics = { showLyricsView = !showLyricsView },
            onSongInfoClick = { showSongInfoSheet = true },
            onOpenFullScreenLyrics = { showFullScreenLyrics = true },
            onShowAlbumBottomSheet = {
                song?.let { currentSong ->
                    val album = resolveAlbumForSong(currentSong)
                    if (album != null) {
                        if (isStreamingMode) {
                            navController.navigate("streaming_album/${android.net.Uri.encode(album.id)}?albumName=${android.net.Uri.encode(album.title)}")
                        } else {
                            navController.navigate(Screen.AlbumDetail.createRoute(album.id, album.title))
                        }
                    } else {
                        if (isStreamingMode) {
                            // Build a proper legacy encoded ID so the repository can search the server
                            val serviceId = currentSong.id.substringBefore("::", "JELLYFIN")
                            val streamingFallbackId = currentSong.albumId.takeIf { it.isNotBlank() }
                                ?: "$serviceId::album::${currentSong.artist}::${currentSong.album}"
                            navController.navigate("streaming_album/${android.net.Uri.encode(streamingFallbackId)}?albumName=${android.net.Uri.encode(currentSong.album)}")
                        } else {
                            val fallbackAlbumId = currentSong.albumId.takeIf { it.isNotBlank() } ?: "unknown_" + currentSong.album
                            navController.navigate(Screen.AlbumDetail.createRoute(fallbackAlbumId, currentSong.album))
                        }
                    }
                }
            },
            onShowArtist = {
                song?.let { currentSong ->
                    val artistNames = splitArtistNames(currentSong.artist)

                    if (artistNames.size <= 1) {
                        currentSongArtistForSheet?.let { artist ->
                            navController.navigate(Screen.ArtistDetail.createRoute(artist.name))
                        }
                    } else {
                        val resolvedCandidates = artistNames.map { name ->
                            artists.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
                                ?: Artist(id = name.trim(), name = name.trim())
                        }
                        candidateArtists = resolvedCandidates
                        showArtistChooserSheet = true
                    }
                }
            },
            onMoreClick = {
                showSongInfoSheet = false
                showMoreSheet = true
            },
            onDeviceClick = { showDeviceOutputSheet = true },
            onQueueClick = { showQueueSheet = true },
            onPlaybackSpeed = { showPlaybackSpeedDialog = true },
            onPlaybackPitch = { showPlaybackPitchDialog = true },
            onEqualizer = {
                try {
                    navController.navigate(Screen.Equalizer.route) {
                        popUpTo(Screen.Player.route) {
                            inclusive = true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerScreen", "Failed to navigate to equalizer", e)
                }
            },
            onSleepTimer = { showSleepTimerBottomSheet = true },
            onAddToPlaylist = { showAddToPlaylistSheetInternal = true },
            onShareFile = {
                song?.let { currentSong ->
                    try {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, currentSong.uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${currentSong.title}"))
                    } catch (_: Exception) {
                        Toast.makeText(context, R.string.materialplayerscreen_unable_to_share_file, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onBack = onBack,
            location = location,
            appSettings = appSettings,
            musicViewModel = musicViewModel,
            isStreamingMode = isStreamingMode,
            canvasArtwork = if (showFullScreenLyrics) null else canvasArtwork,
            canvasLoading = if (showFullScreenLyrics) false else canvasLoading,
            swipeToDismissEnabled = swipeToDismissEnabled,
            expansionFraction = expansionFraction,
            modifier = modifier
        )

        if (showDeviceOutputSheet) {
            LaunchedEffect(showDeviceOutputSheet) {
                if (showDeviceOutputSheet) {
                    onRefreshDevices()
                }
            }

            PlaybackBottomSheet(
                locations = locations,
                currentLocation = location,
            volume = volume,
            musicViewModel = musicViewModel,
            onLocationSelect = {
                onLocationSelect(it)
                showDeviceOutputSheet = false
            },
            onVolumeChange = onVolumeChange,
            onRefreshDevices = onRefreshDevices,
                onDismiss = {
                    showDeviceOutputSheet = false
                    onStopDeviceMonitoring()
                },
                appSettings = appSettings,
                onNavigateToSettings = {
                    showDeviceOutputSheet = false
                    try {
                        navController.navigate(Screen.TunerPlayback.route) {
                            popUpTo(Screen.Player.route) {
                                inclusive = true
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerScreen", "Failed to navigate to playback settings", e)
                    }
                },
                onNavigateToGoMode = null,
                onNavigateToEqualizer = {
                    showDeviceOutputSheet = false
                    try {
                        navController.navigate(Screen.Equalizer.route) {
                            popUpTo(Screen.Player.route) {
                                inclusive = true
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerScreen", "Failed to navigate to equalizer", e)
                    }
                },
                sheetState = deviceOutputSheetState
            )
        }

        if (showQueueSheet && song != null) {
            QueueBottomSheet(
                currentSong = song,
                queue = queue,
                currentQueueIndex = queuePosition - 1,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                onSongClick = { selectedSong ->
                    onSongClick(selectedSong)
                    showQueueSheet = false
                },
                onSongClickAtIndex = { index ->
                    onSongClickAtIndex(index)
                    showQueueSheet = false
                },
                onDismiss = { showQueueSheet = false },
                onRemoveSongAtIndex = onRemoveFromQueueAtIndex,
                onMoveQueueItem = onMoveQueueItem,
                onAddSongsClick = {
                    showQueueSheet = false
                    onNavigateToLibrary(LibraryTab.SONGS)
                },
                onClearQueue = {
                    onClearQueue()
                },
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                sheetState = queueSheetState
            )
        }

        if (showSongInfoSheet && song != null) {
            SongInfoBottomSheet(
                song = song,
                onDismiss = { showSongInfoSheet = false },
                appSettings = appSettings,
                isStreamingMode = isStreamingMode,
                onEditSong = { title, artist, album, genre, year, trackNumber, artworkUri, removeArtwork, albumArtist, composer, discNumber, onComplete ->
                    pendingMetadataEditCompleteCallback = onComplete
                    try {
                        musicViewModel.saveMetadataChanges(
                            song = song,
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
                        pendingMetadataEditCompleteCallback?.invoke(false)
                        pendingMetadataEditCompleteCallback = null
                        android.util.Log.w("PlayerScreen", "Metadata update failed for song: ${song.title}", e)
                    }
                },
                onShowLyricsEditor = { showLyricsEditorDialog = true }
            )
        }

        if (showMoreSheet) {
            val moreSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
            val haptic = LocalHapticFeedback.current

            ExtraControlBottomSheet(
                onDismiss = { showMoreSheet = false },
                sheetState = moreSheetState,
                hiddenChips = hiddenChips,
                equalizerEnabled = equalizerEnabled,
                sleepTimerActive = sleepTimerActive,
                sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                lyrics = lyrics,
                onAddToPlaylist = { showAddToPlaylistSheetInternal = true },
                onEditControls = { showExpressiveBottomButtonsSheet = true },
                onPlaybackSpeed = { showPlaybackSpeedDialog = true },
                onPlaybackPitch = { showPlaybackPitchDialog = true },
                onEqualizer = {
                    try {
                        navController.navigate(Screen.Equalizer.route) {
                            popUpTo(Screen.Player.route) {
                                inclusive = true
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerScreen", "Failed to navigate to equalizer", e)
                    }
                },
                onSleepTimer = { showSleepTimerBottomSheet = true },
                onLyricsEditor = { showLyricsEditorDialog = true },
                onAlbum = {
                    song?.let { currentSong ->
                        val album = resolveAlbumForSong(currentSong)
                        if (album != null) {
                            if (isStreamingMode) {
                                navController.navigate("streaming_album/${android.net.Uri.encode(album.id)}?albumName=${android.net.Uri.encode(album.title)}")
                            } else {
                                navController.navigate(Screen.AlbumDetail.createRoute(album.id, album.title))
                            }
                        } else {
                            if (isStreamingMode) {
                                // Build a proper legacy encoded ID so the repository can search the server
                                val serviceId = currentSong.id.substringBefore("::", "JELLYFIN")
                                val streamingFallbackId = currentSong.albumId.takeIf { it.isNotBlank() }
                                    ?: "$serviceId::album::${currentSong.artist}::${currentSong.album}"
                                navController.navigate("streaming_album/${android.net.Uri.encode(streamingFallbackId)}?albumName=${android.net.Uri.encode(currentSong.album)}")
                            } else {
                                val fallbackAlbumId = currentSong.albumId.takeIf { it.isNotBlank() } ?: "unknown_" + currentSong.album
                                navController.navigate(Screen.AlbumDetail.createRoute(fallbackAlbumId, currentSong.album))
                            }
                        }
                    }
                },
                onArtist = {
                    song?.let { currentSong ->
                        val artistNames = splitArtistNames(currentSong.artist)

                        if (artistNames.size <= 1) {
                            currentSongArtistForSheet?.let { artist ->
                                navController.navigate(Screen.ArtistDetail.createRoute(artist.name))
                            }
                        } else {
                            candidateArtists = artistNames.map { name ->
                                artists.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
                                    ?: Artist(id = name.trim(), name = name.trim())
                            }
                            showArtistChooserSheet = true
                        }
                    }
                },
                onSongInfo = { showSongInfoSheet = true },
                onShareFile = {
                    song?.let { currentSong ->
                        try {
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, currentSong.uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${currentSong.title}"))
                        } catch (_: Exception) {
                            Toast.makeText(context, R.string.materialplayerscreen_unable_to_share_file, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                haptic = haptic,
                isExtraSmallWidth = false,
                isCompactWidth = false
            )
        }

        if (showExpressiveBottomButtonsSheet) {
            ExpressiveBottomButtonsOrderBottomSheet(
                onDismiss = { showExpressiveBottomButtonsSheet = false },
                appSettings = appSettings,
                haptics = haptic,
                initialModeIndex = if (playerMergeControlsToBottom) 1 else 0
            )
        }

        if (showAddToPlaylistSheetInternal && song != null) {
            AddToPlaylistBottomSheet(
                song = selectedSongForPlaylist ?: song,
                playlists = playlists,
                onDismissRequest = {
                    showAddToPlaylistSheetInternal = false
                    selectedSongForPlaylist = null
                },
                onAddToPlaylist = { playlist ->
                    onAddSongToPlaylist(selectedSongForPlaylist ?: song, playlist.id)
                    showAddToPlaylistSheetInternal = false
                    selectedSongForPlaylist = null
                },
                onCreateNewPlaylist = {
                    onShowCreatePlaylistDialog(selectedSongForPlaylist ?: song)
                },
                sheetState = addToPlaylistSheetState
            )
        }

        if (showPlaybackSpeedDialog || showPlaybackPitchDialog) {
            PlaybackSpeedAndPitchBottomSheet(
                currentSpeed = playbackSpeed,
                currentPitch = playbackPitch,
                syncEnabled = syncSpeedAndPitch,
                onSyncChange = { appSettings.setSyncSpeedAndPitch(it) },
                onDismiss = {
                    showPlaybackSpeedDialog = false
                    showPlaybackPitchDialog = false
                },
                onSave = { speed, pitch ->
                    musicViewModel.setPlaybackSpeed(speed)
                    musicViewModel.setPlaybackPitch(pitch)
                    showPlaybackSpeedDialog = false
                    showPlaybackPitchDialog = false
                },
                onSetDefaultSpeed = { speed ->
                    musicViewModel.setDefaultPlaybackSpeed(speed)
                }
            )
        }

        if (showSleepTimerBottomSheet) {
            SleepTimerBottomSheetNew(
                onDismiss = { showSleepTimerBottomSheet = false },
                currentSong = song,
                isPlaying = isPlaying,
                musicViewModel = musicViewModel
            )
        }



        if (showArtistChooserSheet) {
            ArtistChooserBottomSheet(
                candidateArtists = candidateArtists,
                onDismiss = { showArtistChooserSheet = false },
                onArtistSelected = { artist ->
                    showArtistChooserSheet = false
                    navController.navigate(Screen.ArtistDetail.createRoute(artist.name))
                },
                haptic = haptic
            )
        }
    } else {
        MaterialPlayerScreen(
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
            onBack = onBack,
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
            onToggleShuffle = onToggleShuffle,
            onToggleRepeat = onToggleRepeat,
            onToggleFavorite = onToggleFavorite,
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
            onOpenFullScreenLyrics = { showFullScreenLyrics = true },
            canvasArtwork = if (showFullScreenLyrics) null else canvasArtwork,
            canvasLoading = if (showFullScreenLyrics) false else canvasLoading,
            swipeToDismissEnabled = swipeToDismissEnabled,
            expansionFraction = expansionFraction,
            modifier = modifier
        )
    }

    AnimatedVisibility(
        visible = showFullScreenLyrics,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        val progressValue = progress().coerceIn(0f, 1f)
        val vmDurationMs by musicViewModel.duration.collectAsState()
        val totalTimeMs = song?.duration?.takeIf { it > 0 } ?: vmDurationMs.takeIf { it > 0 } ?: 0L
        val currentTimeMs = (progressValue * totalTimeMs).toLong()

        FullScreenLyricsView(
            song = song,
            isPlaying = isPlaying,
            currentTimeMs = currentTimeMs,
            lyrics = lyrics,
            isLoadingLyrics = isLoadingLyrics,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onSeek = onSeek,
            onLyricsSeek = onLyricsSeek,
            onRetryLyrics = onRetryLyrics,
            onClose = { showFullScreenLyrics = false },
            onShowLyricsEditor = { showLyricsEditorDialog = true },
            onNavigateToLyricsSettings = {
                try {
                    navController.navigate(Screen.TunerLyrics.route) {
                        popUpTo(Screen.Player.route) {
                            inclusive = true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerScreen", "Failed to navigate to lyrics settings", e)
                }
            },
            canvasArtwork = canvasArtwork,
            canvasLoading = canvasLoading,
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showLyricsEditorDialog) {
        LyricsEditorBottomSheet(
            lyricsData = lyrics,
            songTitle = song?.title ?: stringResource(R.string.common_unknown),
            initialTimeOffset = lyricsTimeOffset,
            song = song,
            isStreamingMode = isStreamingMode,
            onDismiss = { showLyricsEditorDialog = false },
            onSave = { editedLyrics, timeOffset, format ->
                musicViewModel.saveEditedLyrics(editedLyrics, timeOffset, format)
            },
            onRefresh = {
                musicViewModel.clearLyricsCacheAndRefetch()
            },
            onEmbedInFile = { editedLyrics ->
                musicViewModel.embedLyricsInFile(
                    lyrics = editedLyrics,
                    onPermissionRequired = { pendingRequest ->
                        try {
                            val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(
                                pendingRequest.intentSender
                            ).build()
                            lyricsWritePermissionLauncher.launch(intentSenderRequest)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Failed to request permission: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            musicViewModel.cancelPendingLyricsWrite()
                        }
                    }
                )
            }
        )
    }
}
}
