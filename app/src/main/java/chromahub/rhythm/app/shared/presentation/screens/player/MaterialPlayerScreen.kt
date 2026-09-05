/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.player

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.core.net.toUri
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.PlaybackLocation
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.network.CanvasArtwork
import chromahub.rhythm.app.shared.presentation.components.player.CanvasArtworkPlayer
import chromahub.rhythm.app.shared.presentation.components.common.WaveSlider
import chromahub.rhythm.app.shared.presentation.components.common.StyledProgressBar
import chromahub.rhythm.app.shared.presentation.components.common.ProgressStyle
import chromahub.rhythm.app.shared.presentation.components.common.ThumbStyle
import chromahub.rhythm.app.shared.presentation.components.common.M3CircularLoader
import chromahub.rhythm.app.shared.presentation.components.common.M3LinearLoader
import chromahub.rhythm.app.shared.presentation.components.common.FixedHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.AutoScrollingTextOnDemand
import chromahub.rhythm.app.shared.presentation.components.player.PlayingEqIcon
import chromahub.rhythm.app.shared.presentation.components.common.ShimmerBox
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveGroupButton
import chromahub.rhythm.app.shared.presentation.components.common.ButtonGroupStyle
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.ui.theme.PlayerButtonColor
import chromahub.rhythm.app.util.ImageUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.LyricsFileUtils
import chromahub.rhythm.app.util.LrcUtils
import chromahub.rhythm.app.util.SemanticLyrics
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import chromahub.rhythm.app.shared.presentation.components.common.NetworkOperationLoader
import android.view.animation.OvershootInterpolator
import chromahub.rhythm.app.shared.presentation.components.player.SleepTimerBottomSheetNew
import chromahub.rhythm.app.shared.presentation.components.lyrics.SyncedLyricsView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import chromahub.rhythm.app.shared.presentation.components.player.formatDuration
import java.util.concurrent.TimeUnit
import chromahub.rhythm.app.shared.presentation.components.common.PlaybackBufferingLoader
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ArtistChooserBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.QueueBottomSheet
import chromahub.rhythm.app.features.local.presentation.screens.LibraryTab
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AddToPlaylistBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.PlaybackBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongInfoBottomSheet

import chromahub.rhythm.app.shared.presentation.components.player.PlayerChipOrderBottomSheet
import chromahub.rhythm.app.shared.presentation.components.lyrics.LyricsEditorBottomSheet
import chromahub.rhythm.app.shared.presentation.components.AudioQualityBadges
import chromahub.rhythm.app.util.MediaUtils
import chromahub.rhythm.app.util.windowScreenHeightDp
import chromahub.rhythm.app.util.windowScreenWidthDp
import java.util.Locale
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.findAlbumForSong
import chromahub.rhythm.app.features.local.presentation.navigation.Screen
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.components.lyrics.WordByWordLyricsView
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ExtraControlBottomSheet
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaybackSpeedDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaybackPitchDialog
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.PlaybackSpeedAndPitchBottomSheet
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource

// Experimental API opt-ins required for:
// - Material3 ModalBottomSheet and related APIs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialPlayerScreen(
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
    appSettings: chromahub.rhythm.app.shared.data.model.AppSettings,
    musicViewModel: chromahub.rhythm.app.viewmodel.MusicViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
    canvasArtwork: CanvasArtwork? = null,
    canvasLoading: Boolean = false,
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
    lyrics: chromahub.rhythm.app.shared.data.model.LyricsData? = null,
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
    onCreatePlaylist: (String) -> Unit = { _ -> },
    onShowCreatePlaylistDialog: (Song?) -> Unit = {},
    onClearQueue: () -> Unit = {},
    isMediaLoading: Boolean = false,
    isSeeking: Boolean = false,
    onShowAlbumBottomSheet: () -> Unit = {},
    // Album and artist data for bottom sheets
    songs: List<Song> = emptyList(),
    albums: List<Album> = emptyList(),
    artists: List<Artist> = emptyList(),
    onPlayAlbumSongs: (List<Song>) -> Unit = {},
    onShuffleAlbumSongs: (List<Song>) -> Unit = {},
    onPlayArtistSongs: (List<Song>) -> Unit = {},
    onShuffleArtistSongs: (List<Song>) -> Unit = {},
    isStreamingMode: Boolean = false,
    onOpenFullScreenLyrics: () -> Unit = {},
    swipeToDismissEnabled: Boolean = true,
    expansionFraction: Float = 1f
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isDarkTheme = isSystemInDarkTheme()
    
    // Use surfaceContainer as background color for player screen
    val playerBackgroundColor = BottomSheetDefaults.ContainerColor

    // Get AppSettings for volume control setting
    val appSettingsInstance = appSettings
    val useSystemVolume by appSettingsInstance.useSystemVolume.collectAsState()
    val stopPlaybackOnZeroVolume by appSettingsInstance.stopPlaybackOnZeroVolume.collectAsState()
    val appMode by appSettingsInstance.appMode.collectAsState()
    val groupByAlbumArtist by appSettingsInstance.groupByAlbumArtist.collectAsState()
    val artistSeparatorEnabled by appSettingsInstance.artistSeparatorEnabled.collectAsState()
    val artistSeparatorDelimiters by appSettingsInstance.artistSeparatorDelimiters.collectAsState()
    val useHoursFormat by appSettingsInstance.useHoursInTimeFormat.collectAsState()
    val showRemainingTime by appSettingsInstance.showRemainingTime.collectAsState()
    
    // Player customization settings
    val playerShowGradientOverlay by appSettingsInstance.playerShowGradientOverlay.collectAsState()
    val playerLyricsTransition by appSettingsInstance.playerLyricsTransition.collectAsState()
    val playerLyricsTextSize by appSettingsInstance.playerLyricsTextSize.collectAsState()
    val playerLyricsAlignment by appSettingsInstance.playerLyricsAlignment.collectAsState()
    val playerShowArtBelowLyrics by appSettingsInstance.playerShowArtBelowLyrics.collectAsState()
    val playerShowSeekButtons by appSettingsInstance.playerShowSeekButtons.collectAsState()
    val playerTextAlignment by appSettingsInstance.playerTextAlignment.collectAsState()
    val playerShowSongInfoOnArtwork by appSettingsInstance.playerShowSongInfoOnArtwork.collectAsState()
    val playerArtworkCornerRadius by appSettingsInstance.playerArtworkCornerRadius.collectAsState()
    val playerShowAudioQualityBadges by appSettingsInstance.playerShowAudioQualityBadges.collectAsState()
    val tapLyricsToFullScreen by appSettingsInstance.tapLyricsToFullScreen.collectAsState()
    val onTapLyricsView = if (tapLyricsToFullScreen) onOpenFullScreenLyrics else null
    
    // Expressive shape for player artwork display
    val playerArtworkShape = rememberExpressiveShapeFor(
        ExpressiveShapeTarget.PLAYER_ART,
        RoundedCornerShape(28.dp)
    )
    
    // Progress bar customization settings
    val playerProgressStyle by appSettingsInstance.playerProgressStyle.collectAsState()
    val playerProgressThumbStyle by appSettingsInstance.playerProgressThumbStyle.collectAsState()
    val playerProgressThumbRotate by appSettingsInstance.playerProgressThumbRotate.collectAsState()
    
    // Enhanced seeking settings
    val enhancedSeekingEnabled by appSettingsInstance.enhancedSeekingEnabled.collectAsState()
    val showLyricsTranslation by appSettingsInstance.showLyricsTranslation.collectAsState()
    val showLyricsRomanization by appSettingsInstance.showLyricsRomanization.collectAsState()
    val keepScreenOnLyrics by appSettingsInstance.keepScreenOnLyrics.collectAsState()
    val forcePlayerCompactMode by appSettingsInstance.forcePlayerCompactMode.collectAsState()
    
    // Enhanced seeking state - shows preview during scrubbing
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    val resolvedDurationMs by musicViewModel.duration.collectAsState()
    
    // Gesture settings
    val gesturePlayerSwipeDismiss by appSettingsInstance.gesturePlayerSwipeDismiss.collectAsState()
    val gesturePlayerSwipeTracks by appSettingsInstance.gesturePlayerSwipeTracks.collectAsState()
    val gestureArtworkDoubleTap by appSettingsInstance.gestureArtworkDoubleTap.collectAsState()

    // Helper function to split artist names
    val splitArtistNames: (String) -> List<String> = remember(artistSeparatorDelimiters, artistSeparatorEnabled) {
        { artistName ->
            chromahub.rhythm.app.util.ArtistSeparator.splitArtistNames(
                artistName = artistName,
                delimiters = artistSeparatorDelimiters,
                enabled = artistSeparatorEnabled
            )
        }
    }

    val resolveAlbumForSong: (Song) -> Album? = remember(albums) {
        { targetSong ->
            albums.findAlbumForSong(targetSong)
        }
    }

    // System volume state
    var systemVolume by remember { mutableFloatStateOf(0.5f) }
    var wasPausedByZeroVolume by remember { mutableStateOf(false) }

    // Monitor system volume changes using ContentObserver instead of polling
    LaunchedEffect(useSystemVolume) {
        if (useSystemVolume) {
            // Safety net: ensure app (ExoPlayer) volume is full when system volume mode is active.
            // setUseSystemVolumeMode() already does this, but guard against cold-start or direct
            // AppSettings toggle paths that bypass the ViewModel.
            musicViewModel.setVolume(1f)

            val audioManager =
                context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            // Get initial volume
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            systemVolume = if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 0f

            // Use ContentObserver to react to volume changes without polling
            val volumeObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    val cv = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    val mv = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    systemVolume = if (mv > 0) cv.toFloat() / mv.toFloat() else 0f
                }
            }
            context.contentResolver.registerContentObserver(
                android.provider.Settings.System.CONTENT_URI,
                true,
                volumeObserver
            )

            // Unregister when effect leaves composition
            try { kotlinx.coroutines.awaitCancellation() } finally {
                context.contentResolver.unregisterContentObserver(volumeObserver)
            }
        }
    }


    // Auto-resume: when system volume rises back above 0 after a zero-volume pause, resume playback.
    // NOTE: Pausing + dialog trigger are now handled in MediaPlaybackService.volumeChangeReceiver
    //       (via checkAndPauseOnZeroSystemVolume) so they work from any screen, not just the player.
    LaunchedEffect(systemVolume, isPlaying) {
        if (useSystemVolume && stopPlaybackOnZeroVolume) {
            if (systemVolume > 0f && !isPlaying && wasPausedByZeroVolume) {
                onPlayPause()
                wasPausedByZeroVolume = false
            } else if (systemVolume == 0f && isPlaying) {
                // Service will pause; track that we were playing so auto-resume can fire
                wasPausedByZeroVolume = true
            }
        }
    }

    // Calculate screen dimensions
    val screenHeight = with(density) { windowScreenHeightDp().dp.toPx() }
    val screenWidth = with(density) { windowScreenWidthDp().dp.toPx() }

    // Enhanced screen size detection for better responsiveness
    val isCompactHeight = forcePlayerCompactMode || windowScreenHeightDp() < 600
    val isLargeHeight = windowScreenHeightDp() > 800
    val isTablet = windowScreenWidthDp() >= 600 // Tablet detection
    val isLandscapeTablet = isTablet && windowScreenWidthDp() > windowScreenHeightDp()
    val isExtraSmallWidth = windowScreenWidthDp() < 360 // Extra small width (< 360dp)
    val isCompactWidth = forcePlayerCompactMode || windowScreenWidthDp() < 400 // Compact width (< 400dp)
    val isMidWidth = windowScreenWidthDp() in 400..499 // Mid-range width (400-499dp)
    
    // Bottom sheet states
    var showSleepTimerBottomSheet by remember { mutableStateOf(false) }
    var showLyricsEditorDialog by remember { mutableStateOf(false) }
    var showPlaybackSpeedDialog by remember { mutableStateOf(false) }
    var showPlaybackPitchDialog by remember { mutableStateOf(false) }
    var showChipOrderBottomSheet by remember { mutableStateOf(false) }
    
    // Sleep timer state from ViewModel
    val sleepTimerActive by musicViewModel.sleepTimerActive.collectAsState()
    val sleepTimerRemainingSeconds by musicViewModel.sleepTimerRemainingSeconds.collectAsState()
    
    // Playback speed/pitch state from ViewModel
    val playbackSpeed by musicViewModel.playbackSpeed.collectAsState()
    val playbackPitch by musicViewModel.playbackPitch.collectAsState()
    
    // Equalizer state from ViewModel
    val equalizerEnabled by musicViewModel.equalizerEnabled.collectAsState()
    
    // Chip visibility state
    var showChips by remember { mutableStateOf(false) }
    
    val shareSong: () -> Unit = {
        song?.let { currentSong ->
            try {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, currentSong.uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${currentSong.title}"))
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, R.string.materialplayerscreen_unable_to_share_file, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Collect chip order and hidden chips from settings
    val chipOrder by appSettings.playerChipOrder.collectAsState()
    val hiddenChips by appSettings.hiddenPlayerChips.collectAsState()
    
    // Filter out hidden chips
    val visibleChips = remember(chipOrder, hiddenChips) {
        chipOrder.filter { !hiddenChips.contains(it) }
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
    
    // Write permission launcher for Android 11+ lyrics embedding
    val lyricsWritePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            musicViewModel.completeLyricsWriteAfterPermission(
                onSuccess = {
                    // Toast already shown by ViewModel
                },
                onError = { errorMessage ->
                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                }
            )
        } else {
            musicViewModel.cancelPendingLyricsWrite()
            Toast.makeText(context, R.string.materialplayerscreen_permission_denied_could_not, Toast.LENGTH_LONG).show()
        }
    }
    
    // File picker launcher for loading lyrics directly
    val loadLyricsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        LyricsFileUtils.loadLyricsFromUri(context, selectedUri)
                    }

                    if (result.lyrics != null) {
                        musicViewModel.saveEditedLyrics(result.lyrics)
                        showLyricsEditorDialog = true
                        Toast.makeText(context, R.string.lyrics_loaded_success, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            result.errorMessage ?: "Error loading lyrics file",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("MaterialPlayerScreen", "Error loading lyrics file", e)
                    Toast.makeText(context, context.getString(R.string.error_loading_lyrics, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Allow lyrics files that may be exposed with generic or custom document MIME types.
    val lyricsOpenMimeTypes = arrayOf(
        "text/plain",
        "text/*",
        "text/x-lrc",
        "application/x-lrc",
        "application/octet-stream",
        "*/*"
    )

    // Dynamic sizing based on screen dimensions - used for artwork responsiveness
    val albumArtFraction = when {
        isCompactHeight -> 0.78f
        isExtraSmallWidth -> 0.90f
        isCompactWidth -> 0.92f
        isLargeHeight -> 1.0f
        else -> 0.95f
    }

    // Toggle between album art and lyrics with improved state management
    // Made lyrics view state persistent across song changes
    var showLyricsView by remember { mutableStateOf(false) }
    var isLyricsContentVisible by remember { mutableStateOf(false) }
    var isSongInfoVisible by remember { mutableStateOf(true) }

    // Reset lyrics view when lyrics are disabled
    LaunchedEffect(showLyrics) {
        if (!showLyrics && showLyricsView) {
            showLyricsView = false
            isLyricsContentVisible = false
            isSongInfoVisible = true  // Ensure song info is shown when lyrics are disabled
        }
    }

    val autoFetchArtwork by appSettings.autoFetchArtwork.collectAsState()
    val artworkValidation = rememberArtworkValidation(song?.artworkUri, context)
    var isAutoFetchingMissingArtwork by remember { mutableStateOf(false) }
    var fetchedAutoArtworkUriStr by remember { mutableStateOf<String?>(null) }
    var showAutoFetchEmbedDialog by remember { mutableStateOf(false) }
    var pendingAutoFetchSong by remember { mutableStateOf<Song?>(null) }
    val autoFetchPromptedSongIds = remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastNoArtworkToastTime by remember { mutableLongStateOf(0L) }

    // Auto-fetch in both modes, but only after validation confirms the song has no
    // artwork (null = still checking). Each song is prompted at most once per session.
    LaunchedEffect(song?.id, autoFetchArtwork, artworkValidation) {
        val currentSong = song
        val alreadyPrompted = currentSong != null && currentSong.id in autoFetchPromptedSongIds.value
        if (autoFetchArtwork && currentSong != null && artworkValidation == false && !isAutoFetchingMissingArtwork && !alreadyPrompted) {
            autoFetchPromptedSongIds.value = autoFetchPromptedSongIds.value + currentSong.id
            isAutoFetchingMissingArtwork = true
            musicViewModel.autoFetchArtworkForSong(currentSong) { success, uriStr ->
                isAutoFetchingMissingArtwork = false
                if (success && uriStr != null) {
                    if (isStreamingMode) {
                        // Go mode: no file to embed — apply to the in-memory song (session-only).
                        if (song.id == currentSong.id) {
                            val artUri = uriStr.toUri()
                            musicViewModel.updateCurrentSongMetadata(currentSong.copy(artworkUri = artUri))
                            Toast.makeText(
                                context,
                                context.getString(R.string.expressiveplayerscreen_artwork_applied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        pendingAutoFetchSong = currentSong
                        fetchedAutoArtworkUriStr = uriStr
                        showAutoFetchEmbedDialog = true
                    }
                } else {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastNoArtworkToastTime > 5000) {
                        lastNoArtworkToastTime = now
                        Toast.makeText(
                            context,
                            context.getString(R.string.expressiveplayerscreen_no_artwork_found),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    // Reset song info when song changes
    LaunchedEffect(song?.id) {
        if (song != null) {
            // Don't reset lyrics view state - let user maintain their preference
            // Only reset song info visibility if lyrics are currently showing
            if (!showLyricsView) {
                isSongInfoVisible = true
            }
        }
    }

    // Improved state management for smooth, non-overlapping transitions
    LaunchedEffect(showLyricsView, showLyrics) {
        if (showLyrics && showLyricsView) {
            // Transitioning to lyrics view
            isSongInfoVisible = false // Hide song info immediately
            delay(200) // Wait for song info to fade out completely
            isLyricsContentVisible = true // Then show lyrics content
        } else {
            // Transitioning back to song info view
            isLyricsContentVisible = false // Hide lyrics immediately
            delay(300) // Wait for lyrics to fade out completely
            isSongInfoVisible = true // Then show song info
        }
    }

    // Keep screen awake while lyrics are visible
    val shouldKeepScreenOn = keepScreenOnLyrics && isLyricsContentVisible
    val activity = context as? android.app.Activity
    DisposableEffect(shouldKeepScreenOn) {
        if (shouldKeepScreenOn && activity != null) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Bottom sheet states
    val queueSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val addToPlaylistSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val deviceOutputSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var showQueueSheet by remember { mutableStateOf(false) }
    var showDeviceOutputSheet by remember { mutableStateOf(false) }
    var showSongInfoSheet by remember { mutableStateOf(false) }
    var candidateArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var showArtistChooserSheet by remember { mutableStateOf(false) }

    val openArtistForSong: (Song) -> Unit = { currentSong ->
        val artistNames = splitArtistNames(currentSong.artist)

        if (artistNames.size <= 1) {
            val artistName = artistNames.firstOrNull()?.trim() ?: currentSong.artist.trim()
            navController.navigate(Screen.ArtistDetail.createRoute(artistName))
        } else {
            candidateArtists = artistNames.map { name ->
                artists.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
                    ?: Artist(id = name.trim(), name = name.trim())
            }
            showArtistChooserSheet = true
        }
    }
    
    val navigateToAlbum: (String, String) -> Unit = { id, title ->
        if (isStreamingMode) {
            // Only pop the player if it is actually still in the back stack.
            // If predictive-back is mid-gesture the entry may already be gone,
            // and calling popBackStack on a missing entry causes an
            // "Cannot transition entry that is not in the back stack" crash.
            val playerInStack = try {
                navController.getBackStackEntry(Screen.Player.route)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
            if (playerInStack) {
                navController.popBackStack(Screen.Player.route, inclusive = true)
            }
            try {
                navController.navigate("streaming_album/${android.net.Uri.encode(id)}?albumName=${android.net.Uri.encode(title)}") {
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                Log.e("MaterialPlayerScreen", "Failed to navigate to streaming album", e)
            }
        } else {
            try {
                navController.navigate(Screen.AlbumDetail.createRoute(id, title))
            } catch (e: Exception) {
                Log.e("MaterialPlayerScreen", "Failed to navigate to album detail", e)
            }
        }
    }
    var showCompactChipsSheet by remember { mutableStateOf(false) }
    var selectedSongForPlaylist by remember { mutableStateOf<Song?>(null) }
    var showAddToPlaylistSheetInternal by remember { mutableStateOf(false) }

    // AutoEQ Suggestion Dialog state
    var showAutoEQSuggestion by remember { mutableStateOf(false) }
    var detectedDevice by remember { mutableStateOf<chromahub.rhythm.app.shared.data.model.UserAudioDevice?>(null) }
    var showDeviceConfig by remember { mutableStateOf(false) }

    // State for showing loader in play/pause button
    var showLoaderInPlayPauseButton by remember { mutableStateOf(false) }
    
    // Show loader only when media is loading or seeking
    LaunchedEffect(isMediaLoading, isSeeking) {
        showLoaderInPlayPauseButton = isMediaLoading || isSeeking
    }

    // Entry animation states - staggered like LyricsEditorBottomSheet
    var showHeader by remember { mutableStateOf(true) }
    var showAlbumArt by remember { mutableStateOf(true) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var showBottomButtons by remember { mutableStateOf(true) }

    val localEntranceFraction = if (swipeToDismissEnabled) {
        var animateEntrance by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            animateEntrance = true
        }
        animateFloatAsState(
            targetValue = if (animateEntrance) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "localEntranceFraction"
        ).value
    } else {
        expansionFraction
    }

    val line1Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(((localEntranceFraction - 0.0f) / 0.5f).coerceIn(0f, 1f))
    val line2Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(((localEntranceFraction - 0.1f) / 0.5f).coerceIn(0f, 1f))
    val line3Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(((localEntranceFraction - 0.2f) / 0.5f).coerceIn(0f, 1f))
    val line4Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(((localEntranceFraction - 0.3f) / 0.5f).coerceIn(0f, 1f))
    val line5Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(((localEntranceFraction - 0.4f) / 0.5f).coerceIn(0f, 1f))
    val line6Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(((localEntranceFraction - 0.5f) / 0.5f).coerceIn(0f, 1f))

    val line1Alpha = line1Fraction
    val line1TranslationY = with(LocalDensity.current) { -16.dp.toPx() * (1f - line1Fraction) }

    val line2Alpha = line2Fraction
    val line2TranslationY = with(LocalDensity.current) { 32.dp.toPx() * (1f - line2Fraction) }

    val line3Alpha = line3Fraction
    val line4Alpha = line4Fraction
    val line5Alpha = line5Fraction
    val line6Alpha = line6Fraction
    
    // Swipe to dismiss gesture state - enhanced for mini player-like transition
    var swipeOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isSwipeDismissing by remember { mutableStateOf(false) }
    val dismissTargetOffset = screenHeight * 1.18f
    
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffsetY,
        animationSpec = if (isDragging) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        } else if (isSwipeDismissing) {
            tween(durationMillis = 220, easing = EaseInOut)
        } else {
            spring(
                dampingRatio = 0.82f,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "swipeOffset"
    )
    
    // Calculate swipe-based transformations for mini-player-like effect (moved to graphicsLayer to prevent recomposition loops)
    val swipeDismissThreshold = screenHeight * 0.16f
    
    // Derived states for visibility to prevent recomposition loops
    val showSwipeIndicator by remember { derivedStateOf { swipeOffsetY > 16f } }
    val isPastThreshold by remember { derivedStateOf { swipeOffsetY > swipeDismissThreshold } }

    // Calculate current and total time
    val totalTimeMs = song?.duration?.takeIf { it > 0 } ?: resolvedDurationMs.takeIf { it > 0 } ?: 0L
    val progressValue = progress().coerceIn(0f, 1f)
    val currentTimeMs = (totalTimeMs * progressValue).toLong()
    val canSeek = totalTimeMs > 0
    
    // Calculate scrub preview time when enhanced seeking is active
    val scrubTimeMs = (totalTimeMs * scrubProgress).toLong()

    // Format current and total time directly to ensure perfect sync
    val currentSeconds = currentTimeMs / 1000
    val totalSeconds = totalTimeMs / 1000
    val remainingSeconds = (totalSeconds - currentSeconds).coerceAtLeast(0L)

    val currentTimeFormatted = remember(currentSeconds, useHoursFormat) {
        formatDuration(currentSeconds * 1000, useHoursFormat)
    }
    val totalTimeFormatted = if (showRemainingTime) {
        remember(remainingSeconds, useHoursFormat) {
            "-" + formatDuration(remainingSeconds * 1000, useHoursFormat)
        }
    } else {
        remember(totalTimeMs, useHoursFormat) {
            formatDuration(totalSeconds * 1000, useHoursFormat)
        }
    }
    val scrubTimeFormatted = formatDuration(scrubTimeMs, useHoursFormat)

    // Track previous queue position for slide direction
    var previousQueuePosition by remember { mutableIntStateOf(queuePosition) }
    var slideDirection by remember { mutableIntStateOf(0) } // -1 = left (previous), 0 = none, 1 = right (next)
    
    // Enhanced track change animation with slide direction
    LaunchedEffect(song?.id) {
        if (song != null) {
            // Determine slide direction based on queue position change
            slideDirection = when {
                queuePosition > previousQueuePosition -> 1  // Going to next track - slide from right
                queuePosition < previousQueuePosition -> -1 // Going to previous track - slide from left
                else -> 1 // Default to next direction
            }
            previousQueuePosition = queuePosition
            
            // Reset animation when song changes with quick exit
            showAlbumArt = false
            delay(80) // Brief pause for exit animation
            showAlbumArt = true
        }
        // Don't reset lyrics view - preserve user's preference
    }

    // Start device monitoring when player screen is shown and stop when closed
    LaunchedEffect(Unit) {
        onRefreshDevices()
    }
    
    // Device detection and AutoEQ suggestion
    LaunchedEffect(location) {
        if (location != null && location.id != "speaker") {
            // Try to match with saved user devices
            val matchedDevice = musicViewModel.findMatchingUserDevice(location.name)
            val activeDevice = musicViewModel.getActiveAudioDevice()
            
            if (matchedDevice != null && musicViewModel.shouldShowAutoEQSuggestion(matchedDevice.id)) {
                // Check if this device is already active with profile applied
                val isAlreadyActive = activeDevice?.id == matchedDevice.id && 
                                     matchedDevice.autoEQProfileName != null
                
                // Show popup only if:
                // 1. Device has NO preset configured (needs configuration), OR
                // 2. Device has preset but is NOT currently active (needs to be applied)
                if (!isAlreadyActive) {
                    detectedDevice = matchedDevice
                    showAutoEQSuggestion = true
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopDeviceMonitoring()
        }
    }

    val albumScale by animateFloatAsState(
        targetValue = if (showAlbumArt) {
            if (showLyricsView) 0.98f else 1f  // Slightly smaller when showing lyrics
        } else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "albumScale"
    )

    val albumAlpha by animateFloatAsState(
        targetValue = if (showAlbumArt) {
            when {
                showLyricsView && isLyricsContentVisible -> 0.95f  // Slightly dimmed when lyrics are showing
                !isSongInfoVisible && !isLyricsContentVisible -> 0.8f  // Dimmed during transition
                else -> 1f
            }
        } else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "albumAlpha"
    )
    
    // Slide offset for track change animation
    val albumSlideOffset by animateFloatAsState(
        targetValue = if (showAlbumArt) 0f else (slideDirection * 60f), // Slide based on direction
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "albumSlideOffset"
    )

    // Find which playlist the current song belongs to
    val songPlaylist = remember(song, playlists) {
        if (song == null) null
        else playlists.find { playlist ->
            playlist.songs.any { it.id == song.id }
        }
    }

    // Show bottom sheets if needed
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
            onRemoveSongAtIndex = { indexToRemove ->
                onRemoveFromQueueAtIndex(indexToRemove)
            },
            onMoveQueueItem = { fromIndex, toIndex ->
                onMoveQueueItem(fromIndex, toIndex)
            },
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

    if ((showAddToPlaylistSheet || showAddToPlaylistSheetInternal) && (selectedSongForPlaylist ?: song) != null) {
        AddToPlaylistBottomSheet(
            song = selectedSongForPlaylist ?: song!!,
            playlists = playlists,
            onDismissRequest = {
                if (showAddToPlaylistSheetInternal) {
                    showAddToPlaylistSheetInternal = false
                    selectedSongForPlaylist = null
                } else {
                    onAddToPlaylistSheetDismiss()
                }
            },
            onAddToPlaylist = { playlist ->
                onAddSongToPlaylist(selectedSongForPlaylist ?: song!!, playlist.id)
                if (showAddToPlaylistSheetInternal) {
                    showAddToPlaylistSheetInternal = false
                    selectedSongForPlaylist = null
                } else {
                    onAddToPlaylistSheetDismiss()
                }
            },
            onCreateNewPlaylist = {
                val songForDialog = selectedSongForPlaylist ?: song
                if (showAddToPlaylistSheetInternal) {
                    showAddToPlaylistSheetInternal = false
                    selectedSongForPlaylist = null
                } else {
                    onAddToPlaylistSheetDismiss()
                }
                onShowCreatePlaylistDialog(songForDialog)
            },
            sheetState = addToPlaylistSheetState
        )
    }

    // Device Output Bottom Sheet
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
                    navController.navigate(Screen.TunerPlayback.route)
                } catch (e: Exception) {
                    Log.e("MaterialPlayerScreen", "Failed to navigate to playback settings", e)
                }
            },
            onNavigateToGoMode = if (appMode == "STREAMING") {
                {
                    showDeviceOutputSheet = false
                    try {
                        navController.navigate("streaming_go_settings")
                    } catch (e: Exception) {
                        Log.e("MaterialPlayerScreen", "Failed to navigate to go settings", e)
                    }
                }
            } else {
                null
            },
            onNavigateToEqualizer = {
                showDeviceOutputSheet = false
                try {
                    navController.navigate(Screen.Equalizer.route)
                } catch (e: Exception) {
                    Log.e("MaterialPlayerScreen", "Failed to navigate to equalizer", e)
                }
            },
            sheetState = deviceOutputSheetState
        )
    }

    // Song Info Bottom Sheet
    if (showSongInfoSheet && song != null) {
        SongInfoBottomSheet(
            song = song,
            onDismiss = { showSongInfoSheet = false },
            appSettings = appSettings,
            isStreamingMode = isStreamingMode,
            onEditSong = { title, artist, album, genre, year, trackNumber, artworkUri, removeArtwork, albumArtist, composer, discNumber, onComplete ->
                pendingMetadataEditCompleteCallback = onComplete
                try {
                    // Use the ViewModel's new metadata saving function
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
                            } else {
                                // Don't show error here - permission request will be triggered
                            }
                            pendingMetadataEditCompleteCallback?.invoke(true)
                            pendingMetadataEditCompleteCallback = null
                        },
                        onError = { errorMessage ->
                            // Show detailed error message
                            Toast.makeText(
                                context, 
                                errorMessage, 
                                Toast.LENGTH_LONG
                            ).show()
                            pendingMetadataEditCompleteCallback?.invoke(false)
                            pendingMetadataEditCompleteCallback = null
                        },
                        onPermissionRequired = { pendingRequest ->
                            // Launch the system permission dialog for Android 11+
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
                } catch (e: Exception) {
                    // Show generic error message for unexpected exceptions
                    Toast.makeText(
                        context, 
                        "Unexpected error: ${e.message}", 
                        Toast.LENGTH_LONG
                    ).show()
                    pendingMetadataEditCompleteCallback?.invoke(false)
                    pendingMetadataEditCompleteCallback = null
                    
                    // Log additional debug info
                    android.util.Log.w("MaterialPlayerScreen", "Metadata update failed for song: ${song.title}", e)
                }
            },
            onShowLyricsEditor = { showLyricsEditorDialog = true }
        )
    }


    if (showArtistChooserSheet && candidateArtists.isNotEmpty()) {
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

    // Compact Mode Filter Chips Bottom Sheet
    if (showCompactChipsSheet) {
        val compactChipsSheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        
        ExtraControlBottomSheet(
            onDismiss = { showCompactChipsSheet = false },
            sheetState = compactChipsSheetState,
            hiddenChips = hiddenChips,
            equalizerEnabled = equalizerEnabled,
            sleepTimerActive = sleepTimerActive,
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            lyrics = lyrics,
            onAddToPlaylist = onAddToPlaylist,
            onPlaybackSpeed = { showPlaybackSpeedDialog = true },
            onPlaybackPitch = { showPlaybackPitchDialog = true },
            onEqualizer = { navController.navigate(Screen.Equalizer.route) },
            onSleepTimer = { showSleepTimerBottomSheet = true },
            onLyricsEditor = { showLyricsEditorDialog = true },
            onAlbum = {
                song?.let { currentSong ->
                    val albumForSong = resolveAlbumForSong(currentSong)
                    if (albumForSong != null) {
                        navigateToAlbum(albumForSong.id, albumForSong.title)
                    } else {
                        val fallbackAlbumId = currentSong.albumId.takeIf { it.isNotBlank() }
                            ?: if (isStreamingMode) {
                                val serviceId = currentSong.id.substringBefore("::", "JELLYFIN")
                                "$serviceId::album::${currentSong.artist}::${currentSong.album}"
                            } else {
                                "unknown_${currentSong.album}"
                            }
                        navigateToAlbum(fallbackAlbumId, currentSong.album)
                    }
                }
            },
            onArtist = {
                song?.let { currentSong ->
                    openArtistForSong(currentSong)
                }
            },
            onSongInfo = { showSongInfoSheet = true },
            onShareFile = shareSong,
            haptic = haptic,
            isExtraSmallWidth = isExtraSmallWidth,
            isCompactWidth = isCompactWidth
        )
    }

    FixedHeaderScreen(
        title = stringResource(R.string.settings_player),
        showBackButton = true,
        onBackClick = {
            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
            onBack()
        },
        modifier = if (swipeToDismissEnabled) {
            Modifier
                .graphicsLayer {
                    val swipeProgress = (animatedSwipeOffset / screenHeight).coerceIn(0f, 1f)
                    val swipeScale = 1f - (swipeProgress * 0.10f)
                    val swipeAlpha = 1f - (swipeProgress * 0.42f)
                    val swipeCornerRadius = (swipeProgress * 26f).dp

                    // Apply swipe transitions to the whole scaffold so header and content dismiss together.
                    alpha = swipeAlpha
                    translationY = animatedSwipeOffset
                    scaleX = swipeScale
                    scaleY = swipeScale

                    // Add subtle corner radius animation (simulating mini player collapse)
                    clip = true
                    shape = RoundedCornerShape(
                        topStart = swipeCornerRadius,
                        topEnd = swipeCornerRadius,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                }
                .pointerInput(gesturePlayerSwipeDismiss) {
                    if (gesturePlayerSwipeDismiss) {
                        var velocityTracker = 0f

                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                isSwipeDismissing = false
                                velocityTracker = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                // Only allow downward swipes
                                if (dragAmount > 0) {
                                    change.consume()
                                    val currentSwipeProgress = (swipeOffsetY / screenHeight).coerceIn(0f, 1f)
                                    val dragResistance = (1f - (currentSwipeProgress * 0.35f)).coerceAtLeast(0.55f)
                                    swipeOffsetY = (swipeOffsetY + dragAmount * dragResistance).coerceIn(0f, dismissTargetOffset)
                                    velocityTracker = (dragAmount * 0.55f) + (velocityTracker * 0.45f)
                                }
                            },
                            onDragEnd = {
                                isDragging = false

                                val fastSwipeThreshold = 900f
                                val shouldDismiss = swipeOffsetY > swipeDismissThreshold ||
                                    (velocityTracker > fastSwipeThreshold && swipeOffsetY > screenHeight * 0.03f)

                                if (shouldDismiss) {
                                    HapticUtils.performHapticFeedback(
                                        context,
                                        haptic,
                                        HapticType.HEAVY
                                    )
                                    scope.launch {
                                        isSwipeDismissing = true
                                        swipeOffsetY = dismissTargetOffset
                                        delay(240)
                                        onBack()
                                    }
                                } else {
                                    isSwipeDismissing = false
                                    swipeOffsetY = 0f
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                isSwipeDismissing = false
                                swipeOffsetY = 0f
                            }
                        )
                    }
                }
        } else {
            Modifier
        },
        containerColor = playerBackgroundColor, // Use surfaceContainer for player screen header
        actions = {
            Row(
                modifier = Modifier.graphicsLayer {
                    alpha = line1Alpha
                    translationY = line1TranslationY
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Song info display in actions
                if (song != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .padding(
                            end = if (isExtraSmallWidth) 2.dp else if (isCompactWidth) 4.dp else 8.dp
                        )
                        .width(
                            when {
                                isExtraSmallWidth -> 100.dp
                                isCompactWidth -> 120.dp
                                else -> 180.dp
                            }
                        )
                ) {
                    // Playlist name on top
                    if (songPlaylist != null) {
                        AutoScrollingTextOnDemand(
                            text = songPlaylist.name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = if (isExtraSmallWidth) 10.sp else 11.sp
                            ),
                            gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = true
                        )
                    }
                    // Album name below
                    if (!song.album.isNullOrBlank()) {
                        AutoScrollingTextOnDemand(
                            text = song.album,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = if (isExtraSmallWidth) 9.sp else 10.sp
                            ),
                            gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainer,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = true
                        )
                    }
                }
            }
            // Song info button
            FilledTonalIconButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    showSongInfoSheet = true
                },
                modifier = Modifier.size(
                    if (isExtraSmallWidth) 36.dp else 40.dp
                ),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = RhythmIcons.Info,
                    contentDescription = stringResource(R.string.action_song_info),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            }
        }
    ) { modifier ->
        // Use Box as the root container to allow absolute positioning
        Box(
            modifier = modifier
                .fillMaxSize()
        ) {
            // Enhanced swipe indicator with progress feedback
            AnimatedVisibility(
                visible = showSwipeIndicator,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isPastThreshold) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        },
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = stringResource(if (isPastThreshold) R.string.player_release_to_close else R.string.player_swipe_down_to_close),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isPastThreshold) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isPastThreshold) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    
                    // Progress bar showing swipe progress
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((swipeOffsetY / swipeDismissThreshold).coerceIn(0f, 1f))
                                .background(
                                    if (isPastThreshold) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    }
                                )
                        )
                    }
                }
            }

            // Define Song Info Content for reuse
            val songInfoContent: @Composable () -> Unit = {
                if (song != null) {
                    Column(
                        horizontalAlignment = when(playerTextAlignment) {
                            "START" -> Alignment.Start
                            "END" -> Alignment.End
                            else -> Alignment.CenterHorizontally
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = line3Alpha
                            }
                            .padding(
                                horizontal = when {
                                    isExtraSmallWidth -> 12.dp
                                    isCompactWidth -> 16.dp
                                    else -> 24.dp
                                }
                            )
                    ) {
                        // Song title
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.15.sp,
                                fontSize = when {
                                    isExtraSmallWidth -> 18.sp
                                    isCompactHeight -> 20.sp
                                    else -> 28.sp
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = when(playerTextAlignment) {
                                "START" -> TextAlign.Start
                                "END" -> TextAlign.End
                                else -> TextAlign.Center
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(if (isExtraSmallWidth) 4.dp else 8.dp))
                        
                        // Artist name only (clickable)
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.4.sp,
                                fontSize = when {
                                    isExtraSmallWidth -> 12.sp
                                    isCompactHeight -> 13.sp
                                    else -> 16.sp
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = when(playerTextAlignment) {
                                "START" -> TextAlign.Start
                                "END" -> TextAlign.End
                                else -> TextAlign.Center
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    HapticUtils.performHapticFeedback(
                                        context,
                                        haptic,
                                        HapticType.HEAVY
                                    )
                                    song?.let { openArtistForSong(it) }
                                }
                        )
                        
                        // Audio quality badges for tablets
                        if (playerShowAudioQualityBadges) {
                            Spacer(modifier = Modifier.height(12.dp))
                            AudioQualityBadges(
                                song = song,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Define content blocks for adaptive layout
            val artworkContent: @Composable () -> Unit = {
                // Main content column - optimized spacing to reduce vertical padding
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = when {
                                isExtraSmallWidth -> 4.dp // Extra small width: minimal padding
                                isCompactWidth -> 6.dp     // Compact width: reduced padding
                                else -> 12.dp              // Normal width: standard padding
                            },
                            vertical = when {
                                isExtraSmallWidth && isCompactHeight -> 4.dp
                                isCompactWidth && isCompactHeight -> 6.dp
                                !isTablet && isCompactHeight -> 8.dp
                                !isTablet -> 12.dp
                                isCompactHeight -> 2.dp
                                else -> 4.dp
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (isCompactHeight) Arrangement.Center else Arrangement.SpaceEvenly
                ) {
                    // Optimized dynamic spacing - increased for mobile to give better visual separation
                    val contentSpacing = when {
                        isTablet -> if (isCompactHeight) 1.dp else 2.dp
                        isExtraSmallWidth -> 3.dp  // Extra small: minimal spacing
                        isCompactHeight -> 5.dp    // Compact: reduced spacing
                        isLargeHeight -> 8.dp      // Better spacing on mobile large height  
                        else -> 8.dp               // Better spacing on mobile regular
                    }

                    // Album artwork or lyrics view with smooth transitions
                    // Album artwork with optimized padding and improved click handling
                    AnimatedVisibility(
                        visible = showAlbumArt,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // State for artwork swipe gestures
                        var artworkOffsetX by remember { mutableFloatStateOf(0f) }
                        val artworkSwipeThreshold = 150f
                        
                        val artworkTranslationX by animateFloatAsState(
                            targetValue = artworkOffsetX.coerceIn(-200f, 200f),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "artworkTranslationX"
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(if (isLandscapeTablet) 1.0f else if (isTablet) 0.55f else albumArtFraction) // Responsive size based on screen dimensions
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    val currentSwipeProgress = (animatedSwipeOffset / screenHeight).coerceIn(0f, 1f)
                                    // Album art scales and shrinks during swipe (mini-player effect)
                                    val combinedScale = albumScale * (1f - currentSwipeProgress * 0.2f)
                                    scaleX = combinedScale
                                    scaleY = combinedScale
                                    alpha = albumAlpha * (1f - currentSwipeProgress * 0.3f) * line2Alpha
                                    
                                    // Move upward slightly as if collapsing to mini player position
                                    translationY = -currentSwipeProgress * 100f + line2TranslationY
                                    
                                    // Apply horizontal translation for track swipe and track change animation
                                    translationX = artworkTranslationX + albumSlideOffset
                                }
                                // Swipe gestures for changing tracks on artwork
                                .pointerInput(gesturePlayerSwipeTracks, gestureArtworkDoubleTap) {
                                    if (gesturePlayerSwipeTracks || gestureArtworkDoubleTap) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (gestureArtworkDoubleTap) {
                                                    // Double tap to play/pause
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                    onPlayPause()
                                                }
                                            },
                                            onTap = {
                                                // Single tap - toggle lyrics if available
                                                if (showLyrics && !isLyricsContentVisible && isSongInfoVisible) {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                    showLyricsView = !showLyricsView
                                                } else if (showLyrics && isLyricsContentVisible && !isSongInfoVisible) {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                    showLyricsView = !showLyricsView
                                                }
                                            }
                                        )
                                    }
                                }
                                .pointerInput(gesturePlayerSwipeTracks) {
                                    if (gesturePlayerSwipeTracks) {
                                        detectDragGestures(
                                            onDragStart = { },
                                            onDragEnd = {
                                                if (artworkOffsetX < -artworkSwipeThreshold) {
                                                    // Swipe left - next track
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                    onSkipNext()
                                                } else if (artworkOffsetX > artworkSwipeThreshold) {
                                                    // Swipe right - previous track
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                    onSkipPrevious()
                                                }
                                                artworkOffsetX = 0f
                                            },
                                            onDragCancel = {
                                                artworkOffsetX = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                artworkOffsetX += dragAmount.x
                                            }
                                        )
                                    }
                                },
                            contentAlignment = Alignment.TopCenter // Align content to the center
                        ) {
                            // Render album art (hidden during lyrics view unless playerShowArtBelowLyrics is on)
                            if (!showLyricsView || playerShowArtBelowLyrics) {
                            if (song != null) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Album art content with enhanced loading state
                                    if (song.artworkUri != null) {
                                        var imageLoaded by remember { mutableStateOf(false) }
                                        // Reset loading state when song changes
                                        LaunchedEffect(song.id) { imageLoaded = false }
                                        
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            // Show shimmer only while loading
                                            if (!imageLoaded) {
                                                ShimmerBox(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(playerArtworkShape)
                                                )
                                            }
                                            
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(song.artworkUri)
                                                    .placeholder(chromahub.rhythm.app.R.drawable.rhythm_logo)
                                                    .error(chromahub.rhythm.app.R.drawable.rhythm_logo)
                                                    .build(),
                                                contentDescription = stringResource(R.string.album_artwork_description, song.title),
                                                contentScale = ContentScale.Crop,
                                                onSuccess = { imageLoaded = true },
                                                onError = { imageLoaded = true },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(playerArtworkShape)
                                            )

                                            if (canvasArtwork?.preferredAnimationUrl != null) {
                                                CanvasArtworkPlayer(
                                                    primaryUrl = canvasArtwork.animated,
                                                    fallbackUrl = canvasArtwork.videoUrl,
                                                    isPlaying = isPlaying,
                                                    alwaysPlay = true,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(playerArtworkShape)
                                                )
                                            }

                                            // Canvas loading badge — shown while API is fetching
                                            if (canvasLoading && canvasArtwork == null) {
                                                androidx.compose.foundation.layout.Box(
                                                    modifier = Modifier
                                                        .align(androidx.compose.ui.Alignment.TopEnd)
                                                        .padding(10.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
                                                            RoundedCornerShape(50)
                                                        )
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    contentAlignment = androidx.compose.ui.Alignment.Center
                                                ) {
                                                    Row(
                                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        M3CircularLoader(
                                                            modifier = Modifier.size(12.dp),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            strokeWidth = 2f
                                                        )
                                                        Text(
                                                            text = stringResource(R.string.materialplayerscreen_canvas),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }

                                            // Art overlay: bottom-heavy vertical gradient
                                            if (playerShowGradientOverlay && !isTablet) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(playerArtworkShape)
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    BottomSheetDefaults.ContainerColor.copy(alpha = 0.6f),
                                                                    BottomSheetDefaults.ContainerColor.copy(alpha = 0.9f),
                                                                    BottomSheetDefaults.ContainerColor.copy(alpha = 1.0f)
                                                                )
                                                            )
                                                        )
                                                )
                                            }
                                        }
                                    } else {
                                        // Fallback to Rhythm logo if artwork is null
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(playerArtworkShape)
                                                .background(
                                                    Brush.radialGradient(
                                                        colors = listOf(
                                                            BottomSheetDefaults.ContainerColor.copy(
                                                                alpha = 0.3f
                                                            ),
                                                            BottomSheetDefaults.ContainerColor
                                                        ),
                                                        radius = 400f
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = chromahub.rhythm.app.R.drawable.rhythm_logo),
                                                contentDescription = stringResource(R.string.album_artwork_description, song.title),
                                                tint = MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.7f
                                                ),
                                                modifier = Modifier.size(120.dp)
                                            )

                                            // Art overlay: Bottom-heavy gradient for fallback logo (no artwork)
                                            if (playerShowGradientOverlay && !isTablet) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                0.0f to Color.Transparent,
                                                                0.35f to Color.Transparent,
                                                                1.0f to BottomSheetDefaults.ContainerColor.copy(alpha = 0.85f)
                                                            )
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Default placeholder if no song with Rhythm logo
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(if (isCompactHeight) 20.dp else 28.dp))
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    BottomSheetDefaults.ContainerColor.copy(alpha = 0.3f),
                                                    BottomSheetDefaults.ContainerColor
                                                ),
                                                radius = 400f
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = chromahub.rhythm.app.R.drawable.rhythm_logo),
                                        contentDescription = stringResource(R.string.cd_no_song_playing),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        modifier = Modifier.size(120.dp)
                                    )
                                }
                            }
                            } // end art visibility condition

                            Column(
                                modifier = Modifier.fillMaxSize(), // This Column will fill the Box
                                horizontalAlignment = Alignment.CenterHorizontally, // Center its children horizontally
                                verticalArrangement = Arrangement.Bottom // Align content to the bottom
                            ) {
                // Song info overlay on album art with improved animations
                                AnimatedVisibility(
                                    visible = song != null && isSongInfoVisible && !showLyricsView && playerShowSongInfoOnArtwork && !isTablet,
                                    enter = fadeIn(
                                        animationSpec = tween(
                                            durationMillis = 300,
                                            easing = EaseInOut
                                        )
                                    ) +
                                            slideInVertically(
                                                animationSpec = tween(
                                                    durationMillis = 300,
                                                    easing = EaseInOut
                                                )
                                            ) { it / 3 },
                                    exit = fadeOut(
                                        animationSpec = tween(
                                            durationMillis = 200,
                                            easing = EaseInOut
                                        )
                                    ) +
                                            slideOutVertically(
                                                animationSpec = tween(
                                                    durationMillis = 200,
                                                    easing = EaseInOut
                                                )
                                            ) { it / 3 }
                                ) {
                                    if (song != null) {
                                        Column(
                                            horizontalAlignment = when(playerTextAlignment) {
                                                "START" -> Alignment.Start
                                                "END" -> Alignment.End
                                                else -> Alignment.CenterHorizontally
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    horizontal = when {
                                                        isExtraSmallWidth -> 8.dp
                                                        isCompactWidth -> 12.dp
                                                        else -> 16.dp
                                                    },
                                                    vertical = if (isExtraSmallWidth) 12.dp else 16.dp
                                                )
                                        ) {
                                            // Track title with alignment support
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = when(playerTextAlignment) {
                                                    "START" -> Alignment.CenterStart
                                                    "END" -> Alignment.CenterEnd
                                                    else -> Alignment.Center
                                                }
                                            ) {
                                                AutoScrollingTextOnDemand(
                                                    text = song.title,
                                                    style = MaterialTheme.typography.headlineMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.15.sp,
                                                        fontSize = when {
                                                            isExtraSmallWidth -> 16.sp
                                                            isCompactHeight -> 20.sp
                                                            else -> 28.sp
                                                        },
                                                        color = if (!playerShowGradientOverlay) MaterialTheme.colorScheme.primary else Color.Unspecified
                                                    ),
                                                    gradientEdgeColor = MaterialTheme.colorScheme.background,
                                                    modifier = Modifier.padding(horizontal = 2.dp),
                                                    enabled = true
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(if (isExtraSmallWidth) 1.dp else if (isCompactHeight) 2.dp else 4.dp))

                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = when(playerTextAlignment) {
                                                    "START" -> Alignment.CenterStart
                                                    "END" -> Alignment.CenterEnd
                                                    else -> Alignment.Center
                                                }
                                            ) {
                                                AutoScrollingTextOnDemand(
                                                    text = buildString {
                                                        append(song.artist)
                                                    },
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Medium,
                                                        letterSpacing = 0.4.sp,
                                                        fontSize = when {
                                                            isExtraSmallWidth -> 11.sp
                                                            isCompactHeight -> 13.sp
                                                            else -> 16.sp
                                                        },
                                                        color = if (!playerShowGradientOverlay) MaterialTheme.colorScheme.primary else Color.Unspecified
                                                    ),
                                                    gradientEdgeColor = MaterialTheme.colorScheme.background,
                                                    modifier = Modifier.padding(horizontal = 2.dp),
                                                    enabled = true
                                                )
                                            }
                                            
                                            // Audio quality badges
                                            if (playerShowAudioQualityBadges) {
                                                Spacer(modifier = Modifier.height(if (isExtraSmallWidth) 4.dp else 8.dp))
                                                AudioQualityBadges(
                                                    song = song,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }

                                // Lyrics overlay view with transition based on playerLyricsTransition setting
                                val lyricsTextAlign = when (playerLyricsAlignment) {
                                    "START" -> TextAlign.Start
                                    "END" -> TextAlign.End
                                    else -> TextAlign.Center
                                }
                                val lyricsEnterTransition = when (playerLyricsTransition) {
                                    1 -> fadeIn(tween(400, easing = EaseInOut)) // Fade
                                    2 -> fadeIn(tween(350, easing = EaseInOut)) + scaleIn(tween(350, easing = EaseInOut), initialScale = 0.92f) // Scale
                                    3 -> fadeIn(tween(350, easing = EaseInOut)) + slideInVertically(tween(350, easing = EaseInOut)) { it / 2 } // Slide horizontal (repurposed as slide up from bottom)
                                    else -> fadeIn(tween(350, easing = EaseInOut)) + slideInVertically(tween(350, easing = EaseInOut)) { -it / 2 } // 0 = SlideVertical (from top)
                                }
                                val lyricsExitTransition = when (playerLyricsTransition) {
                                    1 -> fadeOut(tween(300, easing = EaseInOut))
                                    2 -> fadeOut(tween(250, easing = EaseInOut)) + scaleOut(tween(250, easing = EaseInOut), targetScale = 0.92f)
                                    3 -> fadeOut(tween(250, easing = EaseInOut)) + slideOutVertically(tween(250, easing = EaseInOut)) { it / 2 }
                                    else -> fadeOut(tween(250, easing = EaseInOut)) + slideOutVertically(tween(250, easing = EaseInOut)) { -it / 2 }
                                }
                                AnimatedVisibility(
                                    visible = isLyricsContentVisible && showLyrics && showLyricsView,
                                    enter = lyricsEnterTransition,
                                    exit = lyricsExitTransition,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        // Lyrics overlay: Fixed gradient
                                        // Horizontal gradient for depth
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            BottomSheetDefaults.ContainerColor.copy(alpha = 0.4f),
                                                            Color.Transparent,
                                                            Color.Transparent,
                                                            BottomSheetDefaults.ContainerColor.copy(alpha = 0.4f)
                                                        )
                                                    )
                                                )
                                        )
                                        // Semi-transparent background for text readability
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            BottomSheetDefaults.ContainerColor.copy(alpha = 0.50f),
                                                            BottomSheetDefaults.ContainerColor.copy(alpha = 0.60f),
                                                            BottomSheetDefaults.ContainerColor.copy(alpha = 0.75f)
                                                        )
                                                    ),
                                                    shape = RoundedCornerShape(0.dp)
                                                )
                                        )
                                        // Subtle radial overlay for text contrast
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = Brush.radialGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            BottomSheetDefaults.ContainerColor.copy(alpha = 0.10f)
                                                        ),
                                                        radius = 500f
                                                    ),
                                                    shape = RoundedCornerShape(0.dp)
                                                )
                                        )

                                        // Content area with lyrics - optimized padding (from original lyrics view)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize() // Use full artwork length
                                                .padding(
                                                    when {
                                                        isCompactHeight -> 12.dp
                                                        isLargeHeight -> 20.dp
                                                        else -> 16.dp
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when {
                                                isLoadingLyrics -> {
                                                    // Show shape loader with background in the lyrics view area
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 32.dp)
                                                    ) {
                                                        androidx.compose.material3.ContainedLoadingIndicator()
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                        Text(
                                                            text = context.getString(R.string.player_loading_lyrics),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.7f
                                                            ),
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }

                                                lyrics == null ||
                                                        !lyrics.hasLyrics() ||
                                                        lyrics.isErrorMessage() -> {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            Icon(
                                                                imageVector = RhythmIcons.MusicNote,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurface.copy(
                                                                    alpha = 0.8f
                                                                ),
                                                                modifier = Modifier.size(48.dp)
                                                            )
                                                            Spacer(modifier = Modifier.height(16.dp))
                                                            Text(
                                                                text = stringResource(if (onlineOnlyLyrics) R.string.lyrics_currently_no_lyrics else R.string.lyrics_no_lyrics),
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                                    alpha = 0.8f
                                                                ),
                                                                textAlign = TextAlign.Center
                                                            )

                                                            // Show action buttons when not loading
                                                            if (!isLoadingLyrics) {
                                                                Spacer(modifier = Modifier.height(16.dp))

                                                                // Action buttons with expressive button group
                                                                ExpressiveButtonGroup(
                                                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                                                    style = ButtonGroupStyle.Tonal
                                                                ) {
                                                                    // Retry button
                                                                    ExpressiveGroupButton(
                                                                        onClick = {
                                                                            HapticUtils.performHapticFeedback(
                                                                                context,
                                                                                haptic,
                                                                                HapticType.HEAVY
                                                                            )
                                                                            onRetryLyrics()
                                                                        },
                                                                        isStart = true,
                                                                        isEnd = false
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = RhythmIcons.Refresh,
                                                                            contentDescription = null,
                                                                            modifier = Modifier.size(18.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Text(stringResource(R.string.updates_retry))
                                                                    }

                                                                    // Lyrics Editor button
                                                                    ExpressiveGroupButton(
                                                                        onClick = {
                                                                            HapticUtils.performHapticFeedback(
                                                                                context,
                                                                                haptic,
                                                                                HapticType.HEAVY
                                                                            )
                                                                            showLyricsEditorDialog = true
                                                                        },
                                                                        isStart = false,
                                                                        isEnd = false
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = MaterialSymbolIcon("lyrics", filled = true),
                                                                            contentDescription = null,
                                                                            modifier = Modifier.size(18.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Text(stringResource(R.string.lyrics_editor_short))
                                                                    }

                                                                    // Lyrics Settings button
                                                                    ExpressiveGroupButton(
                                                                        onClick = {
                                                                            HapticUtils.performHapticFeedback(
                                                                                context,
                                                                                haptic,
                                                                                HapticType.HEAVY
                                                                            )
                                                                            try {
                                                                                navController.navigate(Screen.TunerLyrics.route) {
                                                                                    popUpTo(Screen.Player.route) {
                                                                                        inclusive = true
                                                                                    }
                                                                                }
                                                                            } catch (e: Exception) {
                                                                                android.util.Log.e("MaterialPlayerScreen", "Failed to navigate to lyrics settings", e)
                                                                            }
                                                                        },
                                                                        isStart = false,
                                                                        isEnd = true
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = MaterialSymbolIcon("settings", filled = true),
                                                                            contentDescription = null,
                                                                            modifier = Modifier.size(18.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Text(stringResource(R.string.lyrics_settings_short))
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                else -> {
                                                    // Check for word-by-word lyrics first (highest quality)
                                                    val translationAutoWord by appSettings.translationAutoWord.collectAsState()
                                                    val wordByWordLyrics = remember(lyrics, translationAutoWord) {
                                                        lyrics.getWordByWordLyricsOrNull() ?: run {
                                                            if (translationAutoWord && lyrics.syncedLyrics != null) {
                                                                try {
                                                                    val options = LrcUtils.LrcParserOptions(
                                                                        trim = true, multiLine = true, errorText = null, autoWordSync = true
                                                                    )
                                                                    val parsed = LrcUtils.parseLyrics(
                                                                        lyrics.syncedLyrics, audioMimeType = null,
                                                                        parserOptions = options, format = LrcUtils.LyricFormat.LRC
                                                                    )
                                                                    if (parsed is SemanticLyrics.SyncedLyrics) {
                                                                        LrcUtils.convertSemanticLyricsToWordByWord(parsed)
                                                                    } else null
                                                                } catch (e: Exception) {
                                                                    null
                                                                }
                                                            } else null
                                                        }
                                                    }
                                                    
                                                    if (wordByWordLyrics != null) {
                                                        // Use WordByWordLyricsView for Rhythm word-by-word lyrics
                                                        WordByWordLyricsView(
                                                            wordByWordLyrics = wordByWordLyrics,
                                                            currentPlaybackTime = currentTimeMs,
                                                            modifier = Modifier.fillMaxSize(),
                                                            onSeek = onLyricsSeek,
                                                            onTapLyricsView = onTapLyricsView,
                                                            lyricsSource = lyrics.source,
                                                            textSizeMultiplier = playerLyricsTextSize,
                                                            textAlignment = lyricsTextAlign,
                                                            showTranslation = showLyricsTranslation,
                                                            showRomanization = showLyricsRomanization
                                                        )
                                                    } else {
                                                        // Fall back to line-by-line synced or plain lyrics
                                                        val lyricsText = remember(lyrics) {
                                                            lyrics.getBestLyrics() ?: ""
                                                        }
                                                        val filteredPlainLyricsText = remember(
                                                            lyricsText,
                                                            showLyricsTranslation,
                                                            showLyricsRomanization
                                                        ) {
                                                            filterPlainLyricsByPreference(
                                                                rawLyrics = lyricsText,
                                                                showTranslation = showLyricsTranslation,
                                                                showRomanization = showLyricsRomanization
                                                            )
                                                        }

                                                        val likelySyncedLyrics = remember(lyricsText) {
                                                            Regex("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{0,3})?]")
                                                                .containsMatchIn(lyricsText)
                                                        }

                                                        val parsedLyrics by produceState<List<chromahub.rhythm.app.util.LyricLine>?>(
                                                            initialValue = if (likelySyncedLyrics) null else emptyList(),
                                                            key1 = lyricsText,
                                                            key2 = likelySyncedLyrics
                                                        ) {
                                                            value = if (!likelySyncedLyrics) {
                                                                emptyList()
                                                            } else {
                                                                withContext(Dispatchers.Default) {
                                                                    chromahub.rhythm.app.util.LyricsParser.parseLyrics(
                                                                        lyricsText
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        if (parsedLyrics == null) {
                                                            Box(
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                M3CircularLoader(
                                                                    modifier = Modifier.size(28.dp),
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                                    strokeWidth = 2f
                                                                )
                                                            }
                                                        } else if (!parsedLyrics.isNullOrEmpty()) {
                                                            // Use SyncedLyricsView for line-by-line synchronized lyrics
                                                            SyncedLyricsView(
                                                                lyrics = lyricsText,
                                                                currentPlaybackTime = currentTimeMs,
                                                                modifier = Modifier.fillMaxSize(),
                                                                parsedLyricsInput = parsedLyrics,
                                                                onSeek = onLyricsSeek,
                                                                onTapLyricsView = onTapLyricsView,
                                                                showTranslation = showLyricsTranslation,
                                                                showRomanization = showLyricsRomanization,
                                                                lyricsSource = lyrics.source,
                                                                textSizeMultiplier = playerLyricsTextSize,
                                                                textAlignment = lyricsTextAlign
                                                            )
                                                        } else {
                                                            // Fallback to plain text lyrics if not synchronized
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .verticalScroll(rememberScrollState()),
                                                                horizontalAlignment = when (playerLyricsAlignment) {
                                                                    "START" -> Alignment.Start
                                                                    "END" -> Alignment.End
                                                                    else -> Alignment.CenterHorizontally
                                                                }
                                                            ) {
                                                                Text(
                                                                    text = filteredPlainLyricsText,
                                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * playerLyricsTextSize,
                                                                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.6f * playerLyricsTextSize,
                                                                        fontWeight = FontWeight.Medium,
                                                                        letterSpacing = 0.5.sp
                                                                    ),
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    textAlign = lyricsTextAlign,
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(horizontal = 8.dp)
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
                        }
                    }

                    // Song info below artwork for tablets (only in portrait mode)
                    if (isTablet && !isLandscapeTablet && song != null && isSongInfoVisible && !showLyricsView) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(
                            horizontalAlignment = when(playerTextAlignment) {
                                "START" -> Alignment.Start
                                "END" -> Alignment.End
                                else -> Alignment.CenterHorizontally
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            // Song title
                            AutoScrollingTextOnDemand(
                                text = song.title,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.15.sp,
                                    fontSize = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                gradientEdgeColor = MaterialTheme.colorScheme.background,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = true
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Artist name (clickable)
                            AutoScrollingTextOnDemand(
                                text = buildString {
                                    append(song.artist)
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                gradientEdgeColor = MaterialTheme.colorScheme.background,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        HapticUtils.performHapticFeedback(
                                            context,
                                            haptic,
                                            HapticType.HEAVY
                                        )
                                        song?.let { openArtistForSong(it) }
                                    },
                                enabled = true
                            )
                            
                            // Audio quality badges for tablets
                            if (playerShowAudioQualityBadges) {
                                Spacer(modifier = Modifier.height(8.dp))
                                AudioQualityBadges(
                                    song = song,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }


                    // Spacer to push content up and allow bottom controls to be anchored
                    Spacer(modifier = Modifier.height(if (isTablet) 18.dp else 12.dp))

                } // End of main content area that gets pushed up
            }

            val controlsContent: @Composable () -> Unit = {
                // Bottom controls container - anchored to the bottom
                AnimatedVisibility(
                    visible = showPlayerControls,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                // Fade out controls as we swipe down (mini-player doesn't show full controls)
                                val swipeProgress = (animatedSwipeOffset / screenHeight).coerceIn(0f, 1f)
                                alpha = 1f - (swipeProgress * 1.2f)
                                translationY = swipeProgress * 50f
                            }
                    ) {
                        // Show progress bar section only if song has duration
                        if (totalTimeMs > 0) {
                            // Add spacing above progress bar on tablet
                            if (isTablet) {
                                Spacer(modifier = Modifier.height(28.dp))
                            }
                            
                            // Progress bar and time indicators - combined into a single row with pill-shaped time indicators
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = line4Alpha
                                    }
                                    .padding(
                                        horizontal = when {
                                            isExtraSmallWidth -> 8.dp
                                            isCompactWidth -> 12.dp
                                            isTablet -> 20.dp
                                            else -> 16.dp
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Current time pill (shows scrub preview when enhanced seeking)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isScrubbing && enhancedSeekingEnabled)
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (isScrubbing && enhancedSeekingEnabled) scrubTimeFormatted else currentTimeFormatted,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isScrubbing && enhancedSeekingEnabled) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = if (isExtraSmallWidth) 10.sp else 12.sp
                                        ),
                                        color = if (isScrubbing && enhancedSeekingEnabled)
                                            MaterialTheme.colorScheme.onSecondary
                                        else
                                            MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(
                                            horizontal = if (isExtraSmallWidth) 6.dp else 8.dp, 
                                            vertical = if (isExtraSmallWidth) 2.dp else 4.dp
                                        )
                                    )
                                }

                                // Customizable progress slider based on user setting
                                if (showLoaderInPlayPauseButton) {
                                    M3LinearLoader(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                            .height(8.dp),
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                                    )
                                                                } else if (playerProgressStyle == "WAVY") {
                                    // WaveSlider: proper animated waves + morphing thumb + play/pause reaction
                                    WaveSlider(
                                        value = if (isScrubbing && enhancedSeekingEnabled) scrubProgress else progress(),
                                        onValueChange = { newValue ->
                                            if (canSeek && enhancedSeekingEnabled) {
                                                isScrubbing = true
                                                scrubProgress = newValue
                                            } else if (canSeek) {
                                                onSeek(newValue)
                                            }
                                        },
                                        onValueChangeFinished = {
                                            if (canSeek && enhancedSeekingEnabled && isScrubbing) {
                                                onSeek(scrubProgress)
                                                isScrubbing = false
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp),
                                        enabled = canSeek,
                                        isPlaying = isPlaying,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                                } else {
                                    // Other styled progress bars
                                    val progressStyle = try {
                                        ProgressStyle.valueOf(playerProgressStyle)
                                    } catch (e: IllegalArgumentException) {
                                        ProgressStyle.NORMAL
                                    }
                                    
                                    val thumbStyle = ThumbStyle.fromStorage(playerProgressThumbStyle)
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                            .height(56.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Use a clickable slider overlay for seeking
                                        StyledProgressBar(
                                            progress = progress(),
                                            style = progressStyle,
                                            modifier = Modifier.fillMaxWidth(),
                                            progressColor = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                            height = when (progressStyle) {
                                                ProgressStyle.THIN -> 2.dp
                                                ProgressStyle.THICK -> 12.dp
                                                else -> 8.dp
                                            },
                                            isPlaying = isPlaying,
                                            showThumb = thumbStyle != ThumbStyle.NONE,
                                            thumbStyle = thumbStyle,
                                            thumbSize = 14.dp,
                                            rotateThumbWhenPlaying = playerProgressThumbRotate,
                                            waveAmplitudeWhenPlaying = 3.dp,
                                            waveLength = 60.dp // Longer wavelength = fewer waves for Player screen
                                        )
                                        
                                        // Enhanced seeking preview indicator
                                        if (isScrubbing && enhancedSeekingEnabled) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction = scrubProgress)
                                                    .height(
                                                        when (progressStyle) {
                                                            ProgressStyle.THIN -> 4.dp
                                                            ProgressStyle.THICK -> 14.dp
                                                            else -> 10.dp
                                                        }
                                                    )
                                                    .background(
                                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .align(Alignment.CenterStart)
                                            )
                                        }
                                        
                                        // Invisible slider for seeking - overlays the progress bar
                                        androidx.compose.material3.Slider(
                                            value = if (isScrubbing && enhancedSeekingEnabled) scrubProgress else progress(),
                                            onValueChange = { newValue ->
                                                if (canSeek && enhancedSeekingEnabled) {
                                                    isScrubbing = true
                                                    scrubProgress = newValue
                                                } else if (canSeek) {
                                                    onSeek(newValue)
                                                }
                                            },
                                            onValueChangeFinished = {
                                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                if (canSeek && enhancedSeekingEnabled && isScrubbing) {
                                                    onSeek(scrubProgress)
                                                    isScrubbing = false
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = canSeek,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color.Transparent,
                                                activeTrackColor = Color.Transparent,
                                                inactiveTrackColor = Color.Transparent
                                            )
                                        )
                                    }
                                }

                                // Total time pill
                                Surface(
                                    onClick = { appSettingsInstance.setShowRemainingTime(!showRemainingTime) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = totalTimeFormatted,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = if (isExtraSmallWidth) 10.sp else 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(
                                            horizontal = if (isExtraSmallWidth) 6.dp else 8.dp, 
                                            vertical = if (isExtraSmallWidth) 2.dp else 4.dp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(if (isTablet) 20.dp else if (isCompactHeight) 6.dp else 12.dp))
                            
                            // Add spacing below progress bar on both views
                            Spacer(modifier = Modifier.height(if (isTablet) 12.dp else if (isCompactHeight) 4.dp else 8.dp))
                        }

                        // Main player controls with Expressive Material 3 button group
                        // Full width container with same padding as toggle buttons
                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = line5Alpha
                            }
                        ) {
                            chromahub.rhythm.app.shared.presentation.components.common.ExpressivePlayerControlGroup(
                            isPlaying = isPlaying && !showLoaderInPlayPauseButton,
                            showSeekButtons = playerShowSeekButtons && canSeek,
                            onPrevious = {
                                HapticUtils.performHapticFeedback(
                                    context,
                                    haptic,
                                    HapticType.LIGHT
                                )
                                onSkipPrevious()
                            },
                            onPlayPause = {
                                HapticUtils.performHapticFeedback(
                                    context,
                                    haptic,
                                    HapticType.HEAVY
                                )
                                onPlayPause()
                            },
                            onNext = {
                                HapticUtils.performHapticFeedback(
                                    context,
                                    haptic,
                                    HapticType.HEAVY
                                )
                                onSkipNext()
                            },
                            onSeekBack = {
                                if (canSeek) {
                                    HapticUtils.performHapticFeedback(
                                        context,
                                        haptic,
                                        HapticType.HEAVY
                                    )
                                    onSeek(
                                        ((progress() * totalTimeMs).toLong() - 10000).coerceAtLeast(0L)
                                            .toFloat() / totalTimeMs
                                    )
                                }
                            },
                            onSeekForward = {
                                if (canSeek) {
                                    HapticUtils.performHapticFeedback(
                                        context,
                                        haptic,
                                        HapticType.HEAVY
                                    )
                                    onSeek(
                                        ((progress() * totalTimeMs).toLong() + 10000).coerceAtMost(totalTimeMs)
                                            .toFloat() / totalTimeMs
                                    )
                                }
                            },
                            isExtraSmallWidth = isExtraSmallWidth,
                            isCompactWidth = isCompactWidth,
                            isCompactHeight = isCompactHeight,
                            isLoading = showLoaderInPlayPauseButton,
                            modifier = Modifier.padding(
                                horizontal = when {
                                    isExtraSmallWidth -> 10.dp
                                    isCompactWidth -> 16.dp
                                    isTablet -> 32.dp
                                    else -> 20.dp
                                }
                            )
                        )
                        }

                        Spacer(modifier = Modifier.height(if (isTablet) 28.dp else if (isCompactHeight) 12.dp else if (isExtraSmallWidth) 20.dp else 28.dp))

                        // Secondary action buttons with Expressive Toggle Button Group
                        Box(
                            modifier = Modifier.graphicsLayer {
                                alpha = line6Alpha
                            }
                        ) {
                        chromahub.rhythm.app.shared.presentation.components.common.ExpressiveToggleButtonGroup(
                            shuffleEnabled = isShuffleEnabled,
                            lyricsVisible = showLyricsView,
                            repeatMode = repeatMode,
                            onToggleShuffle = {
                                HapticUtils.performHapticFeedback(
                                    context,
                                    haptic,
                                    HapticType.HEAVY
                                )
                                onToggleShuffle()
                            },
                            onToggleLyrics = {
                                HapticUtils.performHapticFeedback(
                                    context,
                                    haptic,
                                    HapticType.HEAVY
                                )
                                if (!isLyricsContentVisible && isSongInfoVisible) {
                                    showLyricsView = !showLyricsView
                                } else if (isLyricsContentVisible && !isSongInfoVisible) {
                                    showLyricsView = !showLyricsView
                                }
                            },
                            onToggleRepeat = {
                                HapticUtils.performHapticFeedback(
                                    context,
                                    haptic,
                                    HapticType.HEAVY
                                )
                                onToggleRepeat()
                            },
                            onLongClickLyrics = onOpenFullScreenLyrics,
                            showLyrics = showLyrics,
                            modifier = Modifier.padding(
                                horizontal = when {
                                    isExtraSmallWidth -> 10.dp
                                    isCompactWidth -> 16.dp
                                    isTablet -> 32.dp
                                    else -> 20.dp
                                }
                            ),
                            isDarkTheme = isDarkTheme,
                            isCompactHeight = isCompactHeight,
                            isCompactWidth = isCompactWidth
                        )
                        }

                        Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else if (isExtraSmallWidth) 12.dp else 20.dp))

                        // Arrow button to show chips or chips themselves (hidden in compact mode)
                        if (!isCompactWidth) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = when {
                                            isExtraSmallWidth -> 12.dp
                                            else -> 24.dp
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                // Up arrow button (shown when chips are hidden)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !showChips,
                                enter = fadeIn(
                                    animationSpec = tween(
                                        300,
                                        delayMillis = 200
                                    )
                                ) + scaleIn(animationSpec = tween(300, delayMillis = 200)),
                                exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                                    animationSpec = tween(
                                        200
                                    )
                                )
                            ) {
                                IconButton(
                                    onClick = {
                                        HapticUtils.performHapticFeedback(
                                            context,
                                            haptic,
                                            HapticType.HEAVY
                                        )
                                        showChips = true
                                    },
                                    modifier = Modifier
                                        .width(when {
                                            isExtraSmallWidth -> 160.dp
                                            isCompactWidth -> 190.dp
                                            else -> 226.dp
                                        })
                                        .height(26.dp)
                                        .background(
                                            color = BottomSheetDefaults.ContainerColor,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("keyboard_arrow_up"),
                                        contentDescription = stringResource(R.string.cd_show_actions),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Action chips (shown when chips are visible)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showChips,
                                enter = slideInVertically(
                                    animationSpec = tween(400, easing = EaseInOut),
                                    initialOffsetY = { it / 2 }
                                ) + fadeIn(animationSpec = tween(400)),
                                exit = slideOutVertically(
                                    animationSpec = tween(300, easing = EaseInOut),
                                    targetOffsetY = { it / 2 }
                                ) + fadeOut(animationSpec = tween(300))
                            ) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        if (isExtraSmallWidth) 4.dp else 8.dp
                                    ),
                                    contentPadding = PaddingValues(
                                        horizontal = if (isExtraSmallWidth) 4.dp else 8.dp
                                    )
                                ) {
                                    // Add to Playlist chip (always first, not reorderable)
                                    item {
                                        var isPressed by remember { mutableStateOf(false) }
                                        val scale by animateFloatAsState(
                                            targetValue = if (isPressed) 0.95f else 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            ),
                                            label = "addToPlaylistScale"
                                        )
                                        AssistChip(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(
                                                    context,
                                                    haptic,
                                                    HapticType.HEAVY
                                                )
                                                onAddToPlaylist()
                                            },
                                            label = {
                                                Text(
                                                    "Add to",
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontSize = if (isExtraSmallWidth) 11.sp else 12.sp
                                                    )
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = RhythmIcons.AddToPlaylist,
                                                    contentDescription = stringResource(R.string.content_desc_add_to_playlist),
                                                    modifier = Modifier.size(if (isExtraSmallWidth) 14.dp else 18.dp)
                                                )
                                            },
                                            modifier = Modifier
                                                .height(if (isExtraSmallWidth) 28.dp else 32.dp)
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                }
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onPress = {
                                                            isPressed = true
                                                            try {
                                                                awaitRelease()
                                                            } finally {
                                                                isPressed = false
                                                            }
                                                        }
                                                    )
                                                },
                                            shape = RoundedCornerShape(if (isExtraSmallWidth) 12.dp else 16.dp),
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            border = null // Removed border
                                        )
                                    }

                                    // Dynamic reorderable chips based on visible chips
                                    items(
                                        items = visibleChips,
                                        key = { it }
                                    ) { chipId ->
                                        when (chipId) {
                                            "FAVORITE" -> {
                                                val containerColor by animateColorAsState(
                                                    targetValue = if (isFavorite) Color.Red.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surfaceVariant,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "favoriteChipContainerColor"
                                                )
                                                val labelColor by animateColorAsState(
                                                    targetValue = if (isFavorite) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "favoriteChipLabelColor"
                                                )
                                                val iconColor by animateColorAsState(
                                                    targetValue = if (isFavorite) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "favoriteChipIconColor"
                                                )
                                                val scale by animateFloatAsState(
                                                    targetValue = if (isFavorite) 1.05f else 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "favoriteChipScale"
                                                )

                                                FilterChip(
                                                    selected = isFavorite,
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(
                                                            context,
                                                            haptic,
                                                            HapticType.HEAVY
                                                        )
                                                        onToggleFavorite()
                                                    },
                                                    label = {
                                                        Text(
                                                            "Like",
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontSize = if (isExtraSmallWidth) 11.sp else 12.sp
                                                            )
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = if (isFavorite) MaterialSymbolIcon("thumb_down", filled = true) else MaterialSymbolIcon("thumb_up", filled = true),
                                                            contentDescription = stringResource(R.string.cd_toggle_favorite),
                                                            modifier = Modifier.size(if (isExtraSmallWidth) 14.dp else 16.dp)
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .height(if (isExtraSmallWidth) 28.dp else 32.dp)
                                                        .graphicsLayer {
                                                            scaleX = scale
                                                            scaleY = scale
                                                        },
                                                    shape = RoundedCornerShape(if (isExtraSmallWidth) 12.dp else 16.dp),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        containerColor = containerColor,
                                                        labelColor = labelColor,
                                                        iconColor = iconColor,
                                                        selectedContainerColor = containerColor,
                                                        selectedLabelColor = labelColor,
                                                        selectedLeadingIconColor = iconColor
                                                    ),
                                                    border = null
                                                )
                                            }
                                            "SPEED" -> {
                                                val containerColor by animateColorAsState(
                                                    targetValue = if (playbackSpeed != 1.0f)
                                                        MaterialTheme.colorScheme.tertiaryContainer
                                                    else
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "speedChipContainerColor"
                                                )
                                                val labelColor by animateColorAsState(
                                                    targetValue = if (playbackSpeed != 1.0f)
                                                        MaterialTheme.colorScheme.onTertiaryContainer
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "speedChipLabelColor"
                                                )
                                                val iconColor by animateColorAsState(
                                                    targetValue = if (playbackSpeed != 1.0f)
                                                        MaterialTheme.colorScheme.onTertiaryContainer
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "speedChipIconColor"
                                                )

                                                AssistChip(
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(
                                                            context,
                                                            haptic,
                                                            HapticType.HEAVY
                                                        )
                                                        showPlaybackSpeedDialog = true
                                                    },
                                                    label = {
                                                        Text(
                                                            if (playbackSpeed != 1.0f)
                                                                "${String.format(Locale.US, "%.2f", playbackSpeed)}x"
                                                            else
                                                                "Speed",
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontSize = if (isExtraSmallWidth) 11.sp else 12.sp
                                                            )
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = MaterialSymbolIcon("speed", filled = true),
                                                            contentDescription = stringResource(R.string.player_chip_speed),
                                                            modifier = Modifier.size(if (isExtraSmallWidth) 14.dp else 16.dp)
                                                        )
                                                    },
                                                    modifier = Modifier.height(if (isExtraSmallWidth) 28.dp else 32.dp),
                                                    shape = RoundedCornerShape(if (isExtraSmallWidth) 12.dp else 16.dp),
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = containerColor,
                                                        labelColor = labelColor,
                                                        leadingIconContentColor = iconColor
                                                    ),
                                                    border = null
                                                )
                                            }
                                            "PITCH" -> {
                                                val containerColor by animateColorAsState(
                                                    targetValue = if (playbackPitch != 1.0f)
                                                        MaterialTheme.colorScheme.tertiaryContainer
                                                    else
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "pitchChipContainerColor"
                                                )
                                                val labelColor by animateColorAsState(
                                                    targetValue = if (playbackPitch != 1.0f)
                                                        MaterialTheme.colorScheme.onTertiaryContainer
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "pitchChipLabelColor"
                                                )
                                                val iconColor by animateColorAsState(
                                                    targetValue = if (playbackPitch != 1.0f)
                                                        MaterialTheme.colorScheme.onTertiaryContainer
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "pitchChipIconColor"
                                                )

                                                AssistChip(
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(
                                                            context,
                                                            haptic,
                                                            HapticType.HEAVY
                                                        )
                                                        showPlaybackPitchDialog = true
                                                    },
                                                    label = {
                                                        Text(
                                                            if (playbackPitch != 1.0f)
                                                                "${String.format(Locale.US, "%.2f", playbackPitch)}x"
                                                            else
                                                                "Pitch",
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontSize = if (isExtraSmallWidth) 11.sp else 12.sp
                                                            )
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = MaterialSymbolIcon("graphic_eq", filled = true),
                                                            contentDescription = stringResource(R.string.settings_playback_pitch),
                                                            modifier = Modifier.size(if (isExtraSmallWidth) 14.dp else 16.dp)
                                                        )
                                                    },
                                                    modifier = Modifier.height(if (isExtraSmallWidth) 28.dp else 32.dp),
                                                    shape = RoundedCornerShape(if (isExtraSmallWidth) 12.dp else 16.dp),
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = containerColor,
                                                        labelColor = labelColor,
                                                        leadingIconContentColor = iconColor
                                                    ),
                                                    border = null
                                                )
                                            }
                                            "EQUALIZER" -> {
                                                var isPressed by remember { mutableStateOf(false) }
                                                val scale by animateFloatAsState(
                                                    targetValue = if (isPressed) 0.95f else 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "equalizerChipScale"
                                                )
                                                AssistChip(
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(
                                                            context,
                                                            haptic,
                                                            HapticType.HEAVY
                                                        )
                                                        navController.navigate(Screen.Equalizer.route)
                                                    },
                                                    label = {
                                                        Text(
                                                            if (equalizerEnabled) "EQ ON" else "EQ OFF",
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontSize = if (isExtraSmallWidth) 11.sp else 12.sp
                                                            ),
                                                            fontWeight = if (equalizerEnabled) FontWeight.SemiBold else FontWeight.Normal
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = if (equalizerEnabled) MaterialSymbolIcon("graphic_eq") else MaterialSymbolIcon("graphic_eq"),
                                                            contentDescription = if (equalizerEnabled) "Equalizer enabled" else "Equalizer disabled",
                                                            modifier = Modifier.size(if (isExtraSmallWidth) 14.dp else 16.dp),
                                                            tint = if (equalizerEnabled)
                                                                MaterialTheme.colorScheme.primary
                                                            else
                                                                MaterialTheme.colorScheme.onSurface.copy(
                                                                    alpha = 0.6f
                                                                )
                                                        )
                                                    },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = if (equalizerEnabled)
                                                            MaterialTheme.colorScheme.primaryContainer.copy(
                                                                alpha = 0.8f
                                                            )
                                                        else
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                                alpha = 0.7f
                                                            ),
                                                        labelColor = if (equalizerEnabled)
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        else
                                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.8f
                                                            )
                                                    ),
                                                    modifier = Modifier
                                                        .height(if (isExtraSmallWidth) 28.dp else 32.dp)
                                                        .graphicsLayer {
                                                            scaleX = scale
                                                            scaleY = scale
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectTapGestures(
                                                                onPress = {
                                                                    isPressed = true
                                                                    try {
                                                                        awaitRelease()
                                                                    } finally {
                                                                        isPressed = false
                                                                    }
                                                                }
                                                            )
                                                        },
                                                    shape = RoundedCornerShape(if (isExtraSmallWidth) 12.dp else 16.dp),
                                                    border = null
                                                )
                                            }
                                            "SLEEP_TIMER" -> {
                                                var isPressed by remember { mutableStateOf(false) }
                                                val scale by animateFloatAsState(
                                                    targetValue = if (isPressed) 0.95f else 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "sleepTimerChipScale"
                                                )

                                                val timerText = if (sleepTimerActive) {
                                                    val minutes = sleepTimerRemainingSeconds / 60
                                                    val seconds = sleepTimerRemainingSeconds % 60
                                                    "${minutes}:${seconds.toString().padStart(2, '0')}"
                                                } else {
                                                    "Timer"
                                                }

                                                val chipColors = if (sleepTimerActive) {
                                                    AssistChipDefaults.assistChipColors(
                                                        containerColor = MaterialTheme.colorScheme.primary.copy(
                                                            alpha = 0.12f
                                                        ),
                                                        labelColor = MaterialTheme.colorScheme.primary,
                                                        leadingIconContentColor = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    AssistChipDefaults.assistChipColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                AssistChip(
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(
                                                            context,
                                                            haptic,
                                                            HapticType.HEAVY
                                                        )
                                                        showSleepTimerBottomSheet = true
                                                    },
                                                    label = {
                                                        Text(
                                                            text = timerText,
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontSize = if (isExtraSmallWidth) 11.sp else 12.sp
                                                            )
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = if (sleepTimerActive) RhythmIcons.AccessTime else RhythmIcons.AccessTime,
                                                            contentDescription = if (sleepTimerActive) "Active sleep timer" else "Set sleep timer",
                                                            modifier = Modifier.size(if (isExtraSmallWidth) 14.dp else 16.dp)
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .height(if (isExtraSmallWidth) 28.dp else 32.dp)
                                                        .graphicsLayer {
                                                            scaleX = scale
                                                            scaleY = scale
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectTapGestures(
                                                                onPress = {
                                                                    isPressed = true
                                                                    try {
                                                                        awaitRelease()
                                                                    } finally {
                                                                        isPressed = false
                                                                    }
                                                                }
                                                            )
                                                        },
                                                    shape = RoundedCornerShape(if (isExtraSmallWidth) 12.dp else 16.dp),
                                                    colors = chipColors,
                                                    border = null
                                                )
                                            }
                                            "LYRICS" -> {
                                                var isPressed by remember { mutableStateOf(false) }
                                                val scale by animateFloatAsState(
                                                    targetValue = if (isPressed) 0.95f else 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "lyricsEditChipScale"
                                                )

                                                val hasLyrics =
                                                    lyrics?.getBestLyrics()?.isNotEmpty() == true
                                                // Use same colors as "Add to" chip - surfaceVariant for consistency
                                                val chipColors = AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                AssistChip(
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(
                                                            context,
                                                            haptic,
                                                            HapticType.HEAVY
                                                        )
                                                        showLyricsEditorDialog = true
                                                    },
                                                    label = {
                                                        Text(
                                                            text = if (hasLyrics) "Edit Lyrics" else "Add Lyrics",
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontSize = if (isExtraSmallWidth) 11.sp else 12.sp
                                                            )
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = if (hasLyrics) RhythmIcons.Edit else MaterialSymbolIcon("lyrics", filled = true),
                                                            contentDescription = if (hasLyrics) "Edit lyrics" else "Add lyrics",
                                                            modifier = Modifier.size(if (isExtraSmallWidth) 14.dp else 16.dp)
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .height(if (isExtraSmallWidth) 28.dp else 32.dp)
                                                        .graphicsLayer {
                                                            scaleX = scale
                                                            scaleY = scale
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectTapGestures(
                                                                onPress = {
                                                                    isPressed = true
                                                                    try {
                                                                        awaitRelease()
                                                                    } finally {
                                                                        isPressed = false
                                                                    }
                                                                }
                                                            )
                                                        },
                                                    shape = RoundedCornerShape(if (isExtraSmallWidth) 12.dp else 16.dp),
                                                    colors = chipColors,
                                                    border = null
                                                )
                                            }
                                            "ALBUM" -> {
                                                var isPressed by remember { mutableStateOf(false) }
                                                val scale by animateFloatAsState(
                                                    targetValue = if (isPressed) 0.95f else 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "albumChipScale"
                                                )
                                                AssistChip(
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(
                                                            context,
                                                            haptic,
                                                            HapticType.HEAVY
                                                        )
                                                        // Find the album for the current song and show bottom sheet
                                                        song?.let { currentSong ->
                                                            val albumForSong = resolveAlbumForSong(currentSong)
                                                            if (albumForSong != null) {
                                                                navigateToAlbum(albumForSong.id, albumForSong.title)
                                                            } else {
                                                                val fallbackAlbumId = currentSong.albumId.takeIf { it.isNotBlank() }
                                                                    ?: if (isStreamingMode) {
                                                                        val serviceId = currentSong.id.substringBefore("::", "JELLYFIN")
                                                                        "$serviceId::album::${currentSong.artist}::${currentSong.album}"
                                                                    } else {
                                                                        "unknown_${currentSong.album}"
                                                                    }
                                                                navigateToAlbum(fallbackAlbumId, currentSong.album)
                                                            }
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            "Album",
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontSize = if (isExtraSmallWidth) 11.sp else 12.sp
                                                            )
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = RhythmIcons.Music.Album,
                                                            contentDescription = stringResource(R.string.cd_show_album),
                                                            modifier = Modifier.size(if (isExtraSmallWidth) 14.dp else 16.dp)
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .height(if (isExtraSmallWidth) 28.dp else 32.dp)
                                                        .graphicsLayer {
                                                            scaleX = scale
                                                            scaleY = scale
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectTapGestures(
                                                                onPress = {
                                                                    isPressed = true
                                                                    try {
                                                                        awaitRelease()
                                                                    } finally {
                                                                        isPressed = false
                                                                    }
                                                                }
                                                            )
                                                        },
                                                    shape = RoundedCornerShape(if (isExtraSmallWidth) 12.dp else 16.dp),
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    border = null
                                                )
                                            }
                                            "ARTIST" -> {
                                                var isPressed by remember { mutableStateOf(false) }
                                                val scale by animateFloatAsState(
                                                    targetValue = if (isPressed) 0.95f else 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                    ),
                                                    label = "artistChipScale"
                                                )
                                                AssistChip(
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(
                                                            context,
                                                            haptic,
                                                            HapticType.HEAVY
                                                        )
                                                        song?.let { currentSong ->
                                                            openArtistForSong(currentSong)
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            "Artist",
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontSize = if (isExtraSmallWidth) 11.sp else 12.sp
                                                            )
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = RhythmIcons.Music.Artist,
                                                            contentDescription = stringResource(R.string.cd_show_artist),
                                                            modifier = Modifier.size(if (isExtraSmallWidth) 14.dp else 16.dp)
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .height(if (isExtraSmallWidth) 28.dp else 32.dp)
                                                        .graphicsLayer {
                                                            scaleX = scale
                                                            scaleY = scale
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectTapGestures(
                                                                onPress = {
                                                                    isPressed = true
                                                                    try {
                                                                        awaitRelease()
                                                                    } finally {
                                                                        isPressed = false
                                                                    }
                                                                }
                                                            )
                                                        },
                                                    shape = RoundedCornerShape(if (isExtraSmallWidth) 12.dp else 16.dp),
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    border = null
                                                )
                                            }
                                            "SHARE" -> {
                                                var isPressed by remember { mutableStateOf(false) }
                                                val scale by animateFloatAsState(
                                                    targetValue = if (isPressed) 0.95f else 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessLow
                                                     ),
                                                     label = "shareChipScale"
                                                )
                                                AssistChip(
                                                    onClick = {
                                                        HapticUtils.performHapticFeedback(
                                                            context,
                                                            haptic,
                                                            HapticType.HEAVY
                                                        )
                                                        shareSong()
                                                    },
                                                    label = {
                                                        Text(
                                                            "Share",
                                                            style = MaterialTheme.typography.labelLarge.copy(
                                                                fontSize = if (isExtraSmallWidth) 11.sp else 12.sp
                                                            )
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = RhythmIcons.Share,
                                                            contentDescription = stringResource(R.string.materialplayerscreen_share_song_cd),
                                                            modifier = Modifier.size(if (isExtraSmallWidth) 14.dp else 16.dp)
                                                        )
                                                    },
                                                    modifier = Modifier
                                                        .height(if (isExtraSmallWidth) 28.dp else 32.dp)
                                                        .graphicsLayer {
                                                            scaleX = scale
                                                            scaleY = scale
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectTapGestures(
                                                                onPress = {
                                                                    isPressed = true
                                                                    try {
                                                                        awaitRelease()
                                                                    } finally {
                                                                        isPressed = false
                                                                    }
                                                                }
                                                            )
                                                        },
                                                    shape = RoundedCornerShape(if (isExtraSmallWidth) 12.dp else 16.dp),
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    border = null
                                                )
                                            }
                                        }
                                    }

                                    // Edit chip for reordering (always last, not reorderable)
                                    item {
                                        var isPressed by remember { mutableStateOf(false) }
                                        val scale by animateFloatAsState(
                                            targetValue = if (isPressed) 0.95f else 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            ),
                                            label = "editChipScale"
                                        )
                                        AssistChip(
                                            onClick = {
                                                HapticUtils.performHapticFeedback(
                                                    context,
                                                    haptic,
                                                    HapticType.HEAVY
                                                )
                                                showChipOrderBottomSheet = true
                                            },
                                            label = {
                                                Text(
                                                    "Edit",
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontSize = if (isExtraSmallWidth) 11.sp else 12.sp
                                                    )
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = RhythmIcons.Edit,
                                                    contentDescription = stringResource(R.string.cd_reorder_chips),
                                                    modifier = Modifier.size(if (isExtraSmallWidth) 14.dp else 16.dp)
                                                )
                                            },
                                            modifier = Modifier
                                                .height(if (isExtraSmallWidth) 28.dp else 32.dp)
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                }
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onPress = {
                                                            isPressed = true
                                                            try {
                                                                awaitRelease()
                                                            } finally {
                                                                isPressed = false
                                                            }
                                                        }
                                                    )
                                                },
                                            shape = RoundedCornerShape(if (isExtraSmallWidth) 12.dp else 16.dp),
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            border = null
                                        )
                                    }

                                }
                            }
                            Spacer(modifier = Modifier.height(if (isTablet) 32.dp else 32.dp))
                        }
                    } else {
                        // Compact mode: no extra button needed — arrow button between bottom controls handles this
                    }
                        Spacer(modifier = Modifier.height(if (isTablet) 12.dp else 22.dp))
                    }
                }
            }

            val bottomButtonsContent: @Composable () -> Unit = {
                    // Bottom buttons - optimized responsive design with reduced padding
                    AnimatedVisibility(
                        visible = showBottomButtons,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = line6Alpha
                                }
                                .padding(
                                    start = when {
                                        isExtraSmallWidth -> 4.dp
                                        isCompactWidth -> 8.dp
                                        else -> 12.dp
                                    },
                                    end = when {
                                        isExtraSmallWidth -> 4.dp
                                        isCompactWidth -> 8.dp
                                        else -> 12.dp
                                    },
                                    top = if (isTablet) 8.dp else if (isCompactHeight) 2.dp else 0.dp,
                                    bottom = 24.dp
                                ),
                            horizontalArrangement = Arrangement.spacedBy(
                                if (isExtraSmallWidth) 4.dp else if (isCompactWidth) 6.dp else 8.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Device Output button - expressive style
                            Surface(
                                onClick = {
                                    HapticUtils.performHapticFeedback(
                                        context,
                                        haptic,
                                        HapticType.HEAVY
                                    )
                                    showDeviceOutputSheet = true
                                },
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                tonalElevation = 0.dp,
                                modifier = if (isCompactWidth) {
                                    Modifier
                                        .height(if (isCompactHeight) 48.dp else 56.dp)
                                        .weight(1f)
                                } else {
                                    Modifier.weight(1f)
                                }
                            ) {
                                if (isCompactWidth) {
                                    // Compact: vertical icon + label layout
                                    val icon = when {
                                        location?.id?.startsWith("bt_") == true -> RhythmIcons.BluetoothFilled
                                        location?.id == "wired_headset" -> RhythmIcons.HeadphonesFilled
                                        location?.id == "speaker" -> RhythmIcons.SpeakerFilled
                                        else -> RhythmIcons.Location
                                    }
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(if (isCompactHeight) 28.dp else 34.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondary,
                                                    modifier = Modifier.size(if (isCompactHeight) 16.dp else 18.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = if (isCompactHeight) 8.dp else 12.dp,
                                            horizontal = 12.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    // Icon with background
                                    val icon = when {
                                        location?.id?.startsWith("bt_") == true -> RhythmIcons.BluetoothFilled
                                        location?.id == "wired_headset" -> RhythmIcons.HeadphonesFilled
                                        location?.id == "speaker" -> RhythmIcons.SpeakerFilled
                                        else -> RhythmIcons.Location
                                    }
                                    
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(if (isCompactHeight) 36.dp else 40.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondary,
                                                modifier = Modifier.size(if (isCompactHeight) 20.dp else 24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = location?.name ?: "Device Output",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val displayVolume = if (useSystemVolume) systemVolume else volume
                                        val volumeText = if (useSystemVolume) "System" else "App"
                                        Text(
                                            text = "${(displayVolume * 100).toInt()}% $volumeText",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    Icon(
                                        imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                } // end else (non-compact)
                            }

                            // Arrow button - positioned between buttons in compact mode
                            if (isCompactWidth) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !showChips,
                                    enter = fadeIn(
                                        animationSpec = tween(
                                            300,
                                            delayMillis = 200
                                        )
                                    ) + scaleIn(animationSpec = tween(300, delayMillis = 200)),
                                    exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                                        animationSpec = tween(
                                            200
                                        )
                                    )
                                ) {
                                    Surface(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(
                                                context,
                                                haptic,
                                                HapticType.HEAVY
                                            )
                                            showCompactChipsSheet = true
                                        },
                                        shape = RoundedCornerShape(28.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        modifier = Modifier
                                            .width(if (isExtraSmallWidth) 48.dp else 56.dp)
                                            .height(if (isCompactHeight) 48.dp else 56.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("keyboard_arrow_up"),
                                                contentDescription = stringResource(R.string.cd_show_actions),
                                                
                                                modifier = Modifier.size(if (isCompactHeight) 20.dp else 22.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Queue button - expressive style
                            Surface(
                                onClick = {
                                    HapticUtils.performHapticFeedback(
                                        context,
                                        haptic,
                                        HapticType.HEAVY
                                    )
                                    if (song != null) {
                                        showQueueSheet = true
                                    } else {
                                        onQueueClick()
                                    }
                                },
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                tonalElevation = 0.dp,
                                modifier = if (isCompactWidth) {
                                    Modifier
                                        .height(if (isCompactHeight) 48.dp else 56.dp)
                                        .weight(1f)
                                } else {
                                    Modifier.weight(1f)
                                }
                            ) {
                                if (isCompactWidth) {
                                    // Compact: vertical icon + label layout
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(if (isCompactHeight) 28.dp else 34.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Icon(
                                                    imageVector = RhythmIcons.Queue,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSecondary,
                                                    modifier = Modifier.size(if (isCompactHeight) 16.dp else 18.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = if (isCompactHeight) 8.dp else 12.dp,
                                            horizontal = 12.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    // Icon with background
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(if (isCompactHeight) 36.dp else 40.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = RhythmIcons.Queue,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondary,
                                                modifier = Modifier.size(if (isCompactHeight) 20.dp else 24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Text(
                                            text = context.getString(R.string.player_queue),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$queuePosition of $queueTotal",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    Icon(
                                        imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                } // end else (non-compact)
                            }
                        }  // Close Row for buttons
                    }  // Close AnimatedVisibility
            }
            
            // Adaptive Layout Logic
            if (isLandscapeTablet) {
                Row(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side - Reduced size on tablet
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(0.85f)) {
                            artworkContent()
                        }
                    }
                    // Right Side
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.height(32.dp))
                        songInfoContent()
                        controlsContent()
                        bottomButtonsContent()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top artwork and controls (expands to fill available space)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            artworkContent()
                        }
                        controlsContent()
                    }
                    // Bottom buttons (always pinned to bottom)
                    bottomButtonsContent()
                }
            }
        }
    }
    
    if (showPlaybackSpeedDialog || showPlaybackPitchDialog) {
        val syncSpeedAndPitch by appSettings.syncSpeedAndPitch.collectAsState()
        val playbackPitch by musicViewModel.playbackPitch.collectAsState()
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
    
    if (showChipOrderBottomSheet) {
        PlayerChipOrderBottomSheet(
            onDismiss = { showChipOrderBottomSheet = false },
            appSettings = appSettings,
            haptics = haptic
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
    
    // Lyrics Editor Bottom Sheet
    if (showLyricsEditorDialog) {
        LyricsEditorBottomSheet(
            lyricsData = lyrics,
            songTitle = song?.title ?: stringResource(R.string.common_unknown),
            initialTimeOffset = musicViewModel.lyricsTimeOffset.collectAsState().value,
            song = song,
            isStreamingMode = isStreamingMode,
            onDismiss = { showLyricsEditorDialog = false },
            onSave = { editedLyrics, timeOffset, format ->
                // Save lyrics to cache and update current lyrics immediately with offset
                musicViewModel.saveEditedLyrics(editedLyrics, timeOffset, format)
            },
            onRefresh = {
                // Clear cache and refetch lyrics from source priority
                musicViewModel.clearLyricsCacheAndRefetch()
            },
            onEmbedInFile = { editedLyrics ->
                // Embed lyrics into the audio file's metadata with permission handling
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
    
    // AutoEQ Suggestion Dialog
    if (showAutoEQSuggestion && detectedDevice != null) {
        val equalizerEnabled by appSettings.equalizerEnabled.collectAsState()
        val autoEQProfiles by musicViewModel.autoEQProfiles.collectAsState()
        
        chromahub.rhythm.app.shared.presentation.components.dialogs.AutoEQSuggestionDialog(
            deviceName = location?.name ?: detectedDevice!!.name,
            savedDevice = detectedDevice!!,
            equalizerEnabled = equalizerEnabled,
            onApplyProfile = {
                // Apply the AutoEQ profile
                val profile = autoEQProfiles
                    .find { it.name == detectedDevice!!.autoEQProfileName }
                
                if (profile != null) {
                    musicViewModel.applyAutoEQProfile(profile)
                    musicViewModel.setActiveAudioDevice(detectedDevice!!)
                    android.widget.Toast.makeText(
                        context,
                        "Applied ${profile.name} profile",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                showAutoEQSuggestion = false
            },
            onDismiss = {
                showAutoEQSuggestion = false
            },
            onDontAskAgain = {
                musicViewModel.dismissAutoEQSuggestion(detectedDevice!!.id)
                showAutoEQSuggestion = false
            },
            onConfigureDevice = {
                showAutoEQSuggestion = false
                showDeviceConfig = true
            }
        )
    }
    
    // Device Configuration Dialog
    if (showDeviceConfig) {
        chromahub.rhythm.app.shared.presentation.components.bottomsheets.DeviceConfigurationBottomSheet(
            musicViewModel = musicViewModel,
            onDismiss = { showDeviceConfig = false }
        )
    }

    if (showAutoFetchEmbedDialog) {
        val dialogSong = pendingAutoFetchSong ?: song
        AlertDialog(
            onDismissRequest = {
                showAutoFetchEmbedDialog = false
                pendingAutoFetchSong = null
            },
            icon = {
                Icon(
                    imageVector = MaterialSymbolIcon("cloud_download", filled = true),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.expressiveplayerscreen_artwork_auto_fetched),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.expressiveplayerscreen_artwork_auto_fetched_msg, dialogSong?.title ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            showAutoFetchEmbedDialog = false
                            pendingAutoFetchSong = null
                            dialogSong?.let { currentSong ->
                                val artUri = fetchedAutoArtworkUriStr?.let { it.toUri() }
                                musicViewModel.saveMetadataChanges(
                                    song = currentSong,
                                    title = currentSong.title,
                                    artist = currentSong.artist,
                                    album = currentSong.album,
                                    genre = currentSong.genre ?: "",
                                    year = currentSong.year,
                                    trackNumber = currentSong.trackNumber,
                                    artworkUri = artUri,
                                    onSuccess = { fileWritten ->
                                        if (fileWritten) {
                                            Toast.makeText(context, context.getString(R.string.expressiveplayerscreen_artwork_embedded_toast), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.expressiveplayerscreen_artwork_applied_toast), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    },
                                    onPermissionRequired = { pendingRequest ->
                                        try {
                                            val intentSenderRequest = IntentSenderRequest.Builder(
                                                pendingRequest.intentSender
                                            ).build()
                                            writePermissionLauncher.launch(intentSenderRequest)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.failed_to_request_permission, e.message ?: ""),
                                                Toast.LENGTH_LONG
                                            ).show()
                                            musicViewModel.cancelPendingMetadataWrite()
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("cloud_download", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.expressiveplayerscreen_embed_in_file))
                    }

                    OutlinedButton(
                        onClick = {
                            showAutoFetchEmbedDialog = false
                            pendingAutoFetchSong = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("library_music", filled = true),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.expressiveplayerscreen_keep_library_only))
                    }
                }
            },
            dismissButton = {}
        )
    }
}

private fun filterPlainLyricsByPreference(
    rawLyrics: String,
    showTranslation: Boolean,
    showRomanization: Boolean
): String {
    if (rawLyrics.isBlank()) return rawLyrics
    if (showTranslation && showRomanization) return rawLyrics

    val filteredLines = mutableListOf<String>()
    var previousMainLineWasNonAscii = false

    rawLyrics.lineSequence().forEach { line ->
        val trimmed = line.trim()

        if (trimmed.isEmpty()) {
            filteredLines += line
            return@forEach
        }

        val isBracketTranslation = trimmed.startsWith("(") && trimmed.endsWith(")") && trimmed.length > 2
        val isBracketRomanization = trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2
        val hasLetters = trimmed.any { it.isLetter() }
        val isLatin = chromahub.rhythm.app.util.LyricsParser.isLatinBased(trimmed)
        val inferredRomanization = hasLetters && isLatin && previousMainLineWasNonAscii

        val shouldHide =
            (!showTranslation && isBracketTranslation) ||
                (!showRomanization && (isBracketRomanization || inferredRomanization))

        if (shouldHide) {
            return@forEach
        }

        filteredLines += line

        if (!isBracketTranslation && !isBracketRomanization && !inferredRomanization) {
            previousMainLineWasNonAscii = chromahub.rhythm.app.util.LyricsParser.hasNonLatinScript(trimmed)
        }
    }

    return filteredLines.joinToString("\n")
}
