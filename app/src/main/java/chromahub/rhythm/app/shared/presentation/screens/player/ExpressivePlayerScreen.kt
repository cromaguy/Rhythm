/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.core.view.WindowCompat
import androidx.core.graphics.get
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ExtraControlBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AddToPlaylistBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.PlaybackBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.QueueBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SongInfoBottomSheet
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaybackPitchDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaybackSpeedDialog
import chromahub.rhythm.app.shared.presentation.components.player.SleepTimerBottomSheetNew
import chromahub.rhythm.app.shared.presentation.components.lyrics.LyricsEditorBottomSheet
import chromahub.rhythm.app.shared.presentation.components.lyrics.SyncedLyricsView
import chromahub.rhythm.app.shared.presentation.components.lyrics.WordByWordLyricsView
import chromahub.rhythm.app.shared.presentation.components.player.formatDuration
import chromahub.rhythm.app.features.local.presentation.navigation.Screen
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.shared.data.model.PlaybackLocation
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.presentation.components.common.AnimatedDigitTickerText
import chromahub.rhythm.app.shared.presentation.components.common.AutoScrollingTextOnDemand
import chromahub.rhythm.app.shared.presentation.components.common.ButtonGroupStyle
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.M3LinearLoader
import chromahub.rhythm.app.shared.presentation.components.common.M3PlaceholderType
import chromahub.rhythm.app.shared.presentation.components.common.PlaybackBufferingLoader
import chromahub.rhythm.app.shared.presentation.components.common.ProgressStyle
import chromahub.rhythm.app.shared.presentation.components.common.StyledProgressBar
import chromahub.rhythm.app.shared.presentation.components.common.ThumbStyle
import chromahub.rhythm.app.shared.presentation.components.common.WaveSlider
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.common.M3CircularLoader
import chromahub.rhythm.app.shared.presentation.components.common.RhythmControlButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmDetailActionButton
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.common.RhythmPlayButton
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.AudioQualityIcon
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.M3ImageUtils
import chromahub.rhythm.app.util.ColorExtractor
import chromahub.rhythm.app.util.DevicePosture
import chromahub.rhythm.app.util.rememberDevicePosture
import com.google.android.material.color.utilities.Hct
import chromahub.rhythm.app.util.LrcUtils
import chromahub.rhythm.app.network.CanvasArtwork
import chromahub.rhythm.app.shared.presentation.components.player.CanvasArtworkPlayer
import chromahub.rhythm.app.util.SemanticLyrics
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.EaseInOutSine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.core.net.toUri
import chromahub.rhythm.app.util.windowScreenWidthDp
import chromahub.rhythm.app.util.windowScreenHeightDp

internal val artworkValidationCache = java.util.concurrent.ConcurrentHashMap<android.net.Uri, Boolean>()
internal val accentSchemeCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Color, Color>>()

/**
 * Decode a bitmap from a song's artwork URI.
 *
 * Local songs expose content:// URIs that only ContentResolver can open; streaming
 * (Go-mode) songs expose http(s):// artwork URLs so those are fetched through Coil.
 *
 * We want at least `maxSize` pixels on the longest dimension so the downstream
 * ColorExtractor.QuantizerCelebi has enough pixels to produce meaningful clusters.
 */
private suspend fun loadArtworkBitmap(context: android.content.Context, uri: android.net.Uri, maxSize: Int = 512): android.graphics.Bitmap? {
    return try {
        val isRemote = uri.scheme == "http" || uri.scheme == "https"
        if (isRemote) {
            val request = ImageRequest.Builder(context)
                .data(uri.toString())
                .memoryCacheKey(uri.toString())
                .size(maxSize)
                .crossfade(false)
                .allowHardware(false)
                .build()
            val result = coil.Coil.imageLoader(context).execute(request)
            val bitmapDrawable = result.drawable as? android.graphics.drawable.BitmapDrawable
            if (bitmapDrawable != null) {
                return bitmapDrawable.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
            }
            val bytes = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: run {
                    val url = java.net.URL(uri.toString())
                    val peekOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    val conn = url.openConnection()
                    conn.connectTimeout = 3000
                    conn.readTimeout = 5000
                    conn.getInputStream().use { BitmapFactory.decodeStream(it, null, peekOpts) }
                    val sample = calculateInSampleSize(peekOpts.outWidth, peekOpts.outHeight, maxSize)
                    val conn2 = url.openConnection()
                    conn2.connectTimeout = 3000
                    conn2.readTimeout = 5000
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    conn2.getInputStream().use { BitmapFactory.decodeStream(it, null, opts) }
                }
            bytes
        } else {
            val peekOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, peekOpts)
            }
            val srcW = peekOpts.outWidth
            val srcH = peekOpts.outHeight
            val sample = calculateInSampleSize(srcW, srcH, maxSize)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Calculate a power-of-two [inSampleSize] so the decoded bitmap fits within [maxSize]
 * on its longest dimension. On zero / missing dimensions returns 1 (no downscale).
 */
private fun calculateInSampleSize(srcW: Int, srcH: Int, maxSize: Int): Int {
    if (srcW <= 0 || srcH <= 0 || maxSize <= 0) return 1
    val longest = maxOf(srcW, srcH)
    var sample = 1
    while (longest / (sample * 2) >= maxSize) sample *= 2
    return sample
}

@Composable
internal fun rememberArtworkValidation(uri: android.net.Uri?, context: android.content.Context): Boolean? {
    if (uri == null || uri == android.net.Uri.EMPTY) return false
    val str = uri.toString().trim()
    if (str.isEmpty() || str == "null" || str == "content://media/external/audio/albumart/0") return false
    // Generated letter placeholders are not real artwork — treat as missing so auto-fetch can run
    if (uri.scheme == "file" && uri.lastPathSegment?.startsWith("placeholder_") == true) return false
    if (str.startsWith("http://") || str.startsWith("https://")) return true

    artworkValidationCache[uri]?.let { return it }

    // null = still validating; false = confirmed no artwork; true = artwork exists
    val isValidState = remember(uri) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(uri) {
        val valid = withContext(Dispatchers.IO) {
            try {
                // Use inJustDecodeBounds — the fastest way to confirm an image
                // exists at the URI without decoding any pixels.
                val opts = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, opts)
                }
                opts.outWidth > 0 && opts.outHeight > 0
            } catch (_: Exception) {
                false
            }
        }
        artworkValidationCache[uri] = valid
        isValidState.value = valid
    }

    return isValidState.value
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("RestrictedApi")
@Composable
fun ExpressivePlayerScreen(
    song: Song?,
    isPlaying: Boolean,
    isFavorite: Boolean,
    progress: () -> Float,
    currentTimeStr: String,
    totalTimeStr: String,
    queuePosition: Int,
    queueTotal: Int,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    showLyricsView: Boolean,
    showLyrics: Boolean,
    lyrics: LyricsData?,
    isLoadingLyrics: Boolean,
    onlineOnlyLyrics: Boolean,
    onLyricsSeek: ((Long) -> Unit)?,
    onRetryLyrics: () -> Unit,
    onShowLyricsEditor: () -> Unit,
    onPickLyricsFile: () -> Unit,
    isMediaLoading: Boolean,
    isSeeking: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleLyrics: () -> Unit,
    onSongInfoClick: () -> Unit,
    onShowAlbumBottomSheet: () -> Unit,
    onShowArtist: () -> Unit,
    onMoreClick: () -> Unit,
    onDeviceClick: () -> Unit,
    onQueueClick: () -> Unit,
    onBack: () -> Unit,
    location: PlaybackLocation?,
    appSettings: AppSettings,
    modifier: Modifier = Modifier,
    canvasArtwork: CanvasArtwork? = null,
    canvasLoading: Boolean = false,
    onTotalTimeClick: () -> Unit = {},
    musicViewModel: MusicViewModel? = null,
    onOpenFullScreenLyrics: () -> Unit = {},
    isStreamingMode: Boolean = false,
    swipeToDismissEnabled: Boolean = true,
    expansionFraction: Float = 1f,
    onNavigateToLyricsSettings: (() -> Unit)? = null,
    onPlaybackSpeed: () -> Unit = {},
    onPlaybackPitch: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    onSleepTimer: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onShareFile: () -> Unit = {}
) {
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ArtworkScale"
    )

    val artworkCornerRadius by animateDpAsState(
        targetValue = if (isPlaying) 32.dp else 48.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ArtworkCornerRadius"
    )

    val playerArtworkShape = rememberExpressiveShapeFor(ExpressiveShapeTarget.PLAYER_ART, RoundedCornerShape(artworkCornerRadius))
    val playerControlShape = rememberExpressiveShapeFor(ExpressiveShapeTarget.PLAYER_CONTROLS, CircleShape)

    val playerProgressStyle by appSettings.playerProgressStyle.collectAsState()
    val playerProgressThumbStyle by appSettings.playerProgressThumbStyle.collectAsState()
    val playerProgressThumbRotate by appSettings.playerProgressThumbRotate.collectAsState()
    val enhancedSeekingEnabled by appSettings.enhancedSeekingEnabled.collectAsState()
    val playerAmbientBackdropEnabled by appSettings.playerAmbientBackdropEnabled.collectAsState()
    val playerAmbientBackdropIntensity by appSettings.playerAmbientBackdropIntensity.collectAsState()
    val playerAmbientInfiniteZoom by appSettings.playerAmbientInfiniteZoom.collectAsState()
    val playerAccentBackgroundEnabled by appSettings.playerAccentBackgroundEnabled.collectAsState()
    val playerMergeControlsToBottom by appSettings.playerMergeControlsToBottom.collectAsState()
    val playerShowAudioQualityBadges by appSettings.playerShowAudioQualityBadges.collectAsState()
    val expressiveBottomButtonsNormal by appSettings.expressiveBottomButtonsNormal.collectAsState()
    val expressiveHiddenBottomButtonsNormal by appSettings.expressiveHiddenBottomButtonsNormal.collectAsState()
    val expressiveBottomButtonsMerge by appSettings.expressiveBottomButtonsMerge.collectAsState()
    val expressiveHiddenBottomButtonsMerge by appSettings.expressiveHiddenBottomButtonsMerge.collectAsState()
    val playerLyricsTextSize by appSettings.playerLyricsTextSize.collectAsState()
    val showLyricsTranslation by appSettings.showLyricsTranslation.collectAsState()
    val showLyricsRomanization by appSettings.showLyricsRomanization.collectAsState()
    val playerLyricsTransition by appSettings.playerLyricsTransition.collectAsState()
    val tapLyricsToFullScreen by appSettings.tapLyricsToFullScreen.collectAsState()
    val autoHideLyricsControls by appSettings.autoHideLyricsControls.collectAsState()
    val playerLyricsAlignment by appSettings.playerLyricsAlignment.collectAsState()
    val keepScreenOnLyrics by appSettings.keepScreenOnLyrics.collectAsState()
    val useExactArtworkColors by appSettings.useExactArtworkColors.collectAsState()
    val gesturePlayerSwipeTracks by appSettings.gesturePlayerSwipeTracks.collectAsState()
    val gestureArtworkDoubleTap by appSettings.gestureArtworkDoubleTap.collectAsState()
    val gestureArtworkSingleTap by appSettings.gestureArtworkSingleTap.collectAsState()

    val postureState by rememberDevicePosture()
    val isFlexMode = postureState is DevicePosture.TableTop

    val context = LocalContext.current

    // Write permission launcher for Android 11+ metadata editing (e.g. auto-fetched artwork embedding)
    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            if (musicViewModel?.pendingBatchWriteRequest?.value != null) {
                musicViewModel.completeBatchMetadataWriteAfterPermission(
                    onSuccess = {
                        Toast.makeText(context, R.string.localnavigation_metadata_saved_successfully, Toast.LENGTH_SHORT).show()
                    },
                    onError = { errorMessage ->
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                )
            } else {
                musicViewModel?.completeMetadataWriteAfterPermission(
                    onSuccess = {
                        Toast.makeText(context, R.string.expressiveplayerscreen_artwork_embedded_toast, Toast.LENGTH_SHORT).show()
                    },
                    onError = { errorMessage ->
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                )
            }
        } else {
            if (musicViewModel?.pendingBatchWriteRequest?.value != null) {
                musicViewModel.cancelPendingBatchMetadataWrite()
            } else {
                musicViewModel?.cancelPendingMetadataWrite()
            }
            Toast.makeText(context, R.string.localnavigation_permission_denied_changes_saved, Toast.LENGTH_LONG).show()
        }
    }

    var lyricsControlsVisible by remember { mutableStateOf(true) }
    var lastLyricsInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun showLyricsControls() {
        lyricsControlsVisible = true
        lastLyricsInteractionTime = System.currentTimeMillis()
    }

    fun toggleLyricsControls() {
        lyricsControlsVisible = !lyricsControlsVisible
        if (lyricsControlsVisible) {
            lastLyricsInteractionTime = System.currentTimeMillis()
        }
    }

    val lyricsVisible = showLyricsView && showLyrics
    val lyricsNestedScrollConnection = remember(lyricsVisible, autoHideLyricsControls) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (lyricsVisible) {
                    showLyricsControls()
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    // Do not launch full screen lyrics view when on expressive player; tapping toggles/shows controls
    val onTapLyricsView: () -> Unit = {
        toggleLyricsControls()
    }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    val progressValue = progress().coerceIn(0f, 1f)
    val resolvedDurationMs = musicViewModel?.duration?.collectAsState()?.value ?: 0L
    val totalTimeMs = song?.duration?.takeIf { it > 0 } ?: resolvedDurationMs.takeIf { it > 0 } ?: 0L
    val currentTimeMs = (progressValue * totalTimeMs).toLong()
    val showBuffering = isMediaLoading || isSeeking

    LaunchedEffect(lyricsVisible, lyricsControlsVisible, lastLyricsInteractionTime, autoHideLyricsControls) {
        if (lyricsVisible && autoHideLyricsControls && lyricsControlsVisible) {
            delay(5000L) // Auto hide after 5 seconds of inactivity
            lyricsControlsVisible = false
        } else if (!lyricsVisible) {
            lyricsControlsVisible = true
        }
    }

    LaunchedEffect(expansionFraction >= 0.5f, lyricsVisible) {
        if (expansionFraction >= 0.5f || !lyricsVisible) {
            showLyricsControls()
        }
    }

    val debouncedSong = remember { mutableStateOf(song) }
    var previousQueuePosition by remember { mutableIntStateOf(queuePosition) }
    var debouncedQueuePosition by remember { mutableIntStateOf(queuePosition) }
    var queueDirection by remember { mutableIntStateOf(1) }
    LaunchedEffect(song?.id, song?.artworkUri) {
        delay(250)
        val trackChanged = debouncedSong.value?.id != song?.id
        // Also refresh when the same track's artwork is updated (e.g. Go-mode auto-fetch)
        val artworkChanged = !trackChanged && debouncedSong.value?.artworkUri != song?.artworkUri
        if (trackChanged || artworkChanged) {
            if (trackChanged) {
                queueDirection = if (queueTotal > 1) {
                    val fwd = ((queuePosition - previousQueuePosition) % queueTotal + queueTotal) % queueTotal
                    val bwd = queueTotal - fwd
                    if (fwd <= bwd) 1 else -1
                } else if (queuePosition > previousQueuePosition) 1 else -1
                previousQueuePosition = queuePosition
                debouncedQueuePosition = queuePosition
            }
            debouncedSong.value = song
        }
    }

    val haptic = LocalHapticFeedback.current

    val artworkValidation = rememberArtworkValidation(debouncedSong.value?.artworkUri, context)
    val hasValidArtwork = artworkValidation == true
    val isBackdropEnabled = playerAmbientBackdropEnabled
    val showCanvasArtBg = isBackdropEnabled && hasValidArtwork && !lyricsVisible
    val showDarkBg = isBackdropEnabled
    val showBg = true
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Normal (non-ambient) mode palette: strict black & white, theme-aware
    val monoBg = if (isDarkTheme) Color(0xFF000000) else Color(0xFFFFFFFF)
    val monoSurface = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val monoFg = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFF000000)
    val monoVariant = monoFg.copy(alpha = 0.72f)
    val useAccentBackground = !isBackdropEnabled && playerAccentBackgroundEnabled
    val currentArtworkUri = song?.artworkUri
    val accentArtScheme = produceState<Pair<Color, Color>?>(
        initialValue = currentArtworkUri?.let { uri -> accentSchemeCache["${uri}_${isDarkTheme}_${useExactArtworkColors}"] },
        currentArtworkUri,
        useAccentBackground,
        isDarkTheme,
        useExactArtworkColors
    ) {
        if (!useAccentBackground || currentArtworkUri == null) return@produceState
        val cacheKey = "${currentArtworkUri}_${isDarkTheme}_${useExactArtworkColors}"
        accentSchemeCache[cacheKey]?.let { cached ->
            value = cached
            return@produceState
        }
        val scheme = withContext(Dispatchers.IO) {
            try {
                val bitmap = loadArtworkBitmap(context, currentArtworkUri, 128)
                val extracted = if (bitmap != null) {
                    ColorExtractor.extractColorsFromBitmap(bitmap)
                } else null
                val seedArgb = extracted?.seedColor?.takeIf { it != 0 }
                    ?: if (isDarkTheme) extracted?.darkPrimary else extracted?.primary
                if (seedArgb == null) null
                else {
                    val sourceHct = Hct.fromInt(seedArgb)
                    val isMonochrome = extracted?.isMonochrome == true ||
                        sourceHct.chroma <= 8.0 ||
                        ColorExtractor.isArgbNearGrayscale(seedArgb)

                    if (isMonochrome) {
                        val bg = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFE5E5E5)
                        val fg = if (isDarkTheme) Color.White else Color.Black
                        bg to fg
                    } else {
                        val schemeType = if (useExactArtworkColors) "CONTENT" else if (sourceHct.chroma > 18.0) "VIBRANT" else "TONAL_SPOT"
                        val dynamicScheme = ColorExtractor.createDynamicScheme(sourceHct, schemeType, isDarkTheme)
                        dynamicScheme.primary to dynamicScheme.onPrimary
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
        if (scheme != null) {
            accentSchemeCache[cacheKey] = scheme
            value = scheme
        }
    }
    val accentBg = accentArtScheme.value?.first ?: MaterialTheme.colorScheme.primary
    val accentFg = accentArtScheme.value?.second ?: MaterialTheme.colorScheme.onPrimary
    val accentGlass = accentFg.copy(alpha = 0.15f)
    val accentGlassStrong = accentFg.copy(alpha = 0.28f)
    val lyricsTextAlign = when (playerLyricsAlignment) {
        "START" -> TextAlign.Start; "END" -> TextAlign.End; else -> TextAlign.Center
    }

    val shouldKeepScreenOn = keepScreenOnLyrics && lyricsVisible
    val activity = context as? android.app.Activity
    DisposableEffect(shouldKeepScreenOn) {
        if (shouldKeepScreenOn && activity != null) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    
    val showPlayerControls = if (lyricsVisible) {
        !autoHideLyricsControls || lyricsControlsVisible
    } else {
        true
    }
    val showBottomButtons = showPlayerControls

    // Slow, smooth staggered entrance: a long linear drive so the cascade is even, with
    // per-element FastOutSlowIn motion so each piece decelerates gently into place.
    val entranceSpec = remember { tween<Float>(durationMillis = 900, easing = LinearEasing) }
    val localEntranceFraction = if (swipeToDismissEnabled) {
        var animateEntrance by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { animateEntrance = true }
        animateFloatAsState(targetValue = if (animateEntrance) 1f else 0f, animationSpec = entranceSpec, label = "localEntranceFraction").value
    } else {
        // Sheet path: the player stays in composition from app start (the sheet hosts it
        // permanently), so an entrance keyed to composition time would play long before the
        // sheet opens. Start the slide-up once the sheet is more than half open so there is
        // only a brief beat before it begins, then let the slow motion play out over the
        // fully-expanded sheet. The latch re-arms only after a full collapse (< 0.5) so
        // lightly nudging the expanded sheet doesn't reset and replay the entrance.
        var entranceStarted by remember { mutableStateOf(false) }
        LaunchedEffect(expansionFraction >= 0.5f) {
            if (expansionFraction >= 0.5f) {
                delay(120)
                entranceStarted = true
            } else {
                entranceStarted = false
            }
        }
        val entranceValue by animateFloatAsState(
            targetValue = if (entranceStarted) 1f else 0f,
            animationSpec = entranceSpec,
            label = "localEntranceFraction"
        )
        entranceValue
    }

    val line3Raw = ((localEntranceFraction - 0.00f) / 0.45f).coerceIn(0f, 1f)
    val line2Raw = ((localEntranceFraction - 0.08f) / 0.45f).coerceIn(0f, 1f)
    val line4Raw = ((localEntranceFraction - 0.22f) / 0.48f).coerceIn(0f, 1f)
    val line6Raw = ((localEntranceFraction - 0.46f) / 0.54f).coerceIn(0f, 1f)

    val line2Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(line2Raw)
    val line3Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(line3Raw)
    val line4Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(line4Raw)
    val line6Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(line6Raw)

    val entrancePopEasing = androidx.compose.animation.core.CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val popScale: (Float) -> Float = { eased ->
        0.88f + 0.12f * eased.coerceIn(0f, 1f) + 0.45f * (eased - 1f).coerceAtLeast(0f)
    }
    val line2Pop = popScale(entrancePopEasing.transform(line2Raw))
    val line3Pop = popScale(entrancePopEasing.transform(line3Raw))
    val line4Pop = popScale(entrancePopEasing.transform(line4Raw))
    val line6Pop = popScale(entrancePopEasing.transform(line6Raw))

    val line2Alpha = line2Fraction
    val line2TranslationY = with(LocalDensity.current) { 32.dp.toPx() * (1f - line2Fraction) }
    val line3Alpha = line3Fraction
    val line3TranslationY = with(LocalDensity.current) { 44.dp.toPx() * (1f - line3Fraction) }
    val showAlbumArt = !(showDarkBg && hasValidArtwork)
    val line4Alpha = line4Fraction
    val line4TranslationY = with(LocalDensity.current) { 40.dp.toPx() * (1f - line4Fraction) }
    val line6Alpha = line6Fraction
    val line6TranslationY = with(LocalDensity.current) { 48.dp.toPx() * (1f - line6Fraction) }

    val artworkClipShape = playerArtworkShape

    val needsDarkSurfaces = showDarkBg
    val darkScheme = remember { darkColorScheme() }
    val darkSurfaceHigh = darkScheme.surfaceContainerHigh
    val darkSurface = darkScheme.surfaceContainer
    val currentTrack = debouncedSong.value
    val artworkColor = produceState<Color?>(null, currentTrack?.artworkUri, isBackdropEnabled) {
        val artworkUri = currentTrack?.artworkUri ?: return@produceState
        if (!isBackdropEnabled) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                val bitmap = loadArtworkBitmap(context, artworkUri, 256)
                if (bitmap != null) {
                    var r = 0L; var g = 0L; var b = 0L
                    val count = bitmap.width * bitmap.height
                    for (x in 0 until bitmap.width) {
                        for (y in 0 until bitmap.height) {
                            val p = bitmap[x, y]
                            r += android.graphics.Color.red(p)
                            g += android.graphics.Color.green(p)
                            b += android.graphics.Color.blue(p)
                        }
                    }
                    Color(r.toFloat() / count / 255f, g.toFloat() / count / 255f, b.toFloat() / count / 255f)
                } else null
            } catch (_: Exception) { null }
        }
    }
    val defaultSongColor = remember(currentTrack?.title, currentTrack?.artist) {
        if (currentTrack != null) {
            val seed = kotlin.math.abs(currentTrack.title.hashCode() * 31 + currentTrack.artist.hashCode())
            val hue = (seed % 360).toFloat()
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.70f, 0.60f)))
        } else null
    }
    val artColor = artworkColor.value ?: defaultSongColor
    // Ambient contrast handling: maintain visibility against backdrop
    val ambientTextColor = if ((artColor?.luminance() ?: 0f) > 0.65f) Color.Black else Color.White

    val isPlayerActive = if (swipeToDismissEnabled) true else expansionFraction > 0.5f
    val isStatusLight = when {
        useAccentBackground -> accentBg.luminance() > 0.5f
        isBackdropEnabled -> (artColor?.luminance() ?: 0f) > 0.65f
        else -> !isDarkTheme
    }

    DisposableEffect(activity, isPlayerActive, isStatusLight, isDarkTheme) {
        val window = activity?.window
        val decorView = window?.decorView
        if (window != null && decorView != null && isPlayerActive) {
            val insetsController = WindowCompat.getInsetsController(window, decorView)
            insetsController.isAppearanceLightStatusBars = isStatusLight
        }
        onDispose {
            if (window != null && decorView != null) {
                val insetsController = WindowCompat.getInsetsController(window, decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
            }
        }
    }

    val ambientAlpha = if (isBackdropEnabled) playerAmbientBackdropIntensity else 1f
    val ambientPlayContainer = Color.White
    val ambientPlayContent = Color.Black
    val ambientControlContainer = Color.White.copy(alpha = 0.18f)
    val ambientControlContent = Color.White
    val controlsContainerColor by animateColorAsState(
        targetValue = when {
            useAccentBackground -> accentGlass
            needsDarkSurfaces && artColor != null -> Color(
                red = artColor.red * 0.18f + 0.08f,
                green = artColor.green * 0.18f + 0.08f,
                blue = artColor.blue * 0.18f + 0.08f,
                alpha = ambientAlpha
            )
            needsDarkSurfaces -> darkSurfaceHigh.copy(alpha = ambientAlpha)
            else -> monoBg
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing), label = "controlsContainerColor"
    )
    val outerBoxBgColor by animateColorAsState(
        targetValue = when {
            useAccentBackground -> accentBg
            showDarkBg && hasValidArtwork -> Color.Black
            showDarkBg && !hasValidArtwork -> Color.Transparent
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing), label = "outerBoxBgColor"
    )
    val lyricsScrimColor by animateColorAsState(
        targetValue = if (useAccentBackground) accentBg else MaterialTheme.colorScheme.surface,
        animationSpec = tween(400, easing = FastOutSlowInEasing), label = "lyricsScrimColor"
    )
    val onSurfaceColor by animateColorAsState(
        targetValue = if (useAccentBackground) accentFg else if (isBackdropEnabled) ambientTextColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(400), label = "onSurfaceColor"
    )
    val onSurfaceVariantColor by animateColorAsState(
        targetValue = if (useAccentBackground) accentFg.copy(alpha = 0.8f) else if (isBackdropEnabled) ambientTextColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(400), label = "onSurfaceVariantColor"
    )
    val surfaceContainerColor by animateColorAsState(
        targetValue = when {
            useAccentBackground -> accentGlass
            needsDarkSurfaces && artColor != null -> Color(
                red = artColor.red * 0.14f + 0.05f,
                green = artColor.green * 0.14f + 0.05f,
                blue = artColor.blue * 0.14f + 0.05f,
                alpha = ambientAlpha
            )
            needsDarkSurfaces -> darkSurface.copy(alpha = ambientAlpha)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(400), label = "surfaceContainerColor"
    )
    val primaryColor by animateColorAsState(
        targetValue = if (useAccentBackground) accentFg else if (isBackdropEnabled) Color.White else MaterialTheme.colorScheme.primary,
        animationSpec = tween(400), label = "canvasPrimaryColor"
    )
    val isCompactWidth = windowScreenWidthDp() < 360
    val isCompactHeight = windowScreenHeightDp() < 640
    val isTablet = windowScreenWidthDp() >= 600
    val isLandscapeTablet = isTablet && windowScreenWidthDp() > windowScreenHeightDp()
    val isTabletImmersiveLyrics = isTablet && lyricsVisible && !showPlayerControls

    val clearArtworkAlpha by animateFloatAsState(
        targetValue = if (lyricsVisible && !isTabletImmersiveLyrics) 0f else 1f,
        animationSpec = tween(500),
        label = "clearArtworkAlpha"
    )
    val lyricsOverlayAlpha by animateFloatAsState(
        targetValue = if (lyricsVisible && showDarkBg && !isTabletImmersiveLyrics) 1f else 0f,
        animationSpec = tween(600),
        label = "lyricsOverlayAlpha"
    )
    val coroutineScope = rememberCoroutineScope()
    val screenHeightPx = with(LocalDensity.current) { windowScreenHeightDp().dp.toPx() }

    var swipeOffsetY by remember { mutableFloatStateOf(0f) }
    var isDraggingSwipe by remember { mutableStateOf(false) }
    var isSwipeMinimizing by remember { mutableStateOf(false) }
    val swipeDismissThreshold = screenHeightPx * 0.16f
    val swipeDismissTarget = screenHeightPx * 1.05f

    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffsetY,
        animationSpec = when {
            isDraggingSwipe -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            isSwipeMinimizing -> tween(durationMillis = 160, easing = EaseInOut)
            else -> spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessLow)
        },
        label = "rhythmPlayerSwipeOffset"
    )

    val swipeCornerRadius by animateFloatAsState(
        targetValue = if (isDraggingSwipe || isSwipeMinimizing) 64f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "swipeCornerRadius"
    )
    val clampedSwipeCornerRadius = swipeCornerRadius.coerceAtLeast(0f)

    val swipeMinimizeModifier = if (swipeToDismissEnabled) {
        modifier
            .graphicsLayer {
                val swipeProgress = (animatedSwipeOffset / screenHeightPx).coerceIn(0f, 1f)
                translationY = animatedSwipeOffset
                val scaleTarget = 1f - (swipeProgress * 0.15f)
                scaleX = scaleTarget; scaleY = scaleTarget
                alpha = (1f - (swipeProgress * 1.5f)).coerceIn(0f, 1f)
                clip = true; shape = RoundedCornerShape(clampedSwipeCornerRadius.dp)
            }
            .pointerInput(screenHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = { isDraggingSwipe = true; isSwipeMinimizing = false },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0f) {
                            change.consume()
                            val p = (swipeOffsetY / screenHeightPx).coerceIn(0f, 1f)
                            swipeOffsetY = (swipeOffsetY + dragAmount * (1f - p * 0.5f).coerceAtLeast(0.4f)).coerceIn(0f, swipeDismissTarget)
                        }
                    },
                    onDragEnd = {
                        isDraggingSwipe = false
                        if (swipeOffsetY > swipeDismissThreshold) {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            isSwipeMinimizing = true; swipeOffsetY = swipeDismissTarget
                            coroutineScope.launch { delay(180); onBack(); isSwipeMinimizing = false; swipeOffsetY = 0f }
                        } else { isSwipeMinimizing = false; swipeOffsetY = 0f }
                    },
                    onDragCancel = { isDraggingSwipe = false; isSwipeMinimizing = false; swipeOffsetY = 0f }
                )
            }
    } else { modifier }

    val autoFetchArtwork by appSettings.autoFetchArtwork.collectAsState()
    var isAutoFetchingMissingArtwork by remember { mutableStateOf(false) }
    var fetchedAutoArtworkUriStr by remember { mutableStateOf<String?>(null) }
    var showAutoFetchEmbedDialog by remember { mutableStateOf(false) }
    var pendingAutoFetchSong by remember { mutableStateOf<Song?>(null) }
    val autoFetchPromptedSongIds = remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastNoArtworkToastTime by remember { mutableLongStateOf(0L) }

    // Auto-fetch in both modes, but only after validation confirms the song has no
    // artwork (null = still checking). Each song is prompted at most once per session.
    LaunchedEffect(debouncedSong.value?.id, autoFetchArtwork, artworkValidation) {
        val currentSong = debouncedSong.value
        val alreadyPrompted = currentSong != null && currentSong.id in autoFetchPromptedSongIds.value
        if (autoFetchArtwork && currentSong != null && artworkValidation == false && !isAutoFetchingMissingArtwork && !alreadyPrompted) {
            autoFetchPromptedSongIds.value = autoFetchPromptedSongIds.value + currentSong.id
            isAutoFetchingMissingArtwork = true
            musicViewModel?.autoFetchArtworkForSong(currentSong) { success, uriStr ->
                isAutoFetchingMissingArtwork = false
                if (success && uriStr != null) {
                    if (isStreamingMode) {
                        // Go mode: no file to embed — apply to the in-memory song (session-only).
                        if (debouncedSong.value?.id == currentSong.id) {
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

    if (showAutoFetchEmbedDialog) {
        val dialogSong = pendingAutoFetchSong ?: debouncedSong.value
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
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                            showAutoFetchEmbedDialog = false
                            pendingAutoFetchSong = null
                            dialogSong?.let { currentSong ->
                                val artUri = fetchedAutoArtworkUriStr?.let { (it).toUri() }
                                musicViewModel?.saveMetadataChanges(
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
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM)
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

    val unifiedBackground = @Composable { modifier: Modifier ->
        val currentArtworkUri = debouncedSong.value?.artworkUri
        val gc = Color.Black
        // Crossfade the artwork↔gradient switch to prevent the gradient from flashing
        // momentarily when skipping to a song whose artwork is not yet validated.
        AnimatedContent(
            targetState = hasValidArtwork,
            transitionSpec = { fadeIn(tween(800)).togetherWith(fadeOut(tween(600))) },
            label = "artworkValidTransition"
        ) { validArtwork ->
            if (!isBackdropEnabled) {
                // Normal (non-ambient) mode: themed surface or accent background
                Box(modifier = modifier.fillMaxSize().background(outerBoxBgColor))
            } else if (validArtwork) {
                // Smooth crossfade when track artwork changes (1200ms motion canvas style)
                AnimatedContent(
                    targetState = currentArtworkUri,
                    transitionSpec = { fadeIn(tween(1200)).togetherWith(fadeOut(tween(800))) },
                    label = "canvasArtworkTransition"
                ) { artworkUri ->
                    Box(modifier = modifier.fillMaxSize()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(artworkUri).size(128, 128).build(),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(150.dp)
                        )
                        val useHorizontalBackdrop = isLandscapeTablet || isTabletImmersiveLyrics
                        Box(
                            modifier = (if (useHorizontalBackdrop) Modifier.fillMaxWidth(0.5f).fillMaxHeight()
                                else Modifier.fillMaxWidth().fillMaxHeight(0.58f)).alpha(clearArtworkAlpha)
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                val brush = if (useHorizontalBackdrop) Brush.horizontalGradient(
                                    0.00f to gc, 0.55f to gc, 0.70f to gc.copy(alpha = 0.65f),
                                    0.85f to gc.copy(alpha = 0.25f), 1.00f to Color.Transparent
                                ) else Brush.verticalGradient(
                                    0.00f to gc, 0.65f to gc, 0.80f to gc.copy(alpha = 0.65f),
                                    0.90f to gc.copy(alpha = 0.25f), 1.00f to Color.Transparent
                                )
                                drawRect(brush = brush, blendMode = BlendMode.DstIn)
                            }
                        ) {
                            AsyncImage(model = ImageRequest.Builder(context).data(artworkUri).crossfade(150)
                                .memoryCacheKey(artworkUri.toString()).diskCacheKey(artworkUri.toString()).build(),
                                contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                            if (canvasArtwork?.preferredAnimationUrl != null) {
                                CanvasArtworkPlayer(primaryUrl = canvasArtwork.animated, fallbackUrl = canvasArtwork.videoUrl, isPlaying = isPlaying, alwaysPlay = true, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize().background(
                            if (useHorizontalBackdrop) Brush.horizontalGradient(listOf(gc.copy(alpha = 0.03f), gc.copy(alpha = 0.35f)))
                            else Brush.verticalGradient(listOf(gc.copy(alpha = 0.05f), gc.copy(alpha = 0.4f)))))
                        Box(modifier = Modifier.fillMaxSize().alpha(lyricsOverlayAlpha).background(
                            if (useHorizontalBackdrop) Brush.horizontalGradient(
                                colors = listOf(gc.copy(alpha = 0.65f), gc.copy(alpha = 0.50f), gc.copy(alpha = 0.72f)))
                            else Brush.verticalGradient(
                                colors = listOf(gc.copy(alpha = 0.72f), gc.copy(alpha = 0.52f), gc.copy(alpha = 0.78f)))))
                    }
                }
            } else {
                val fallbackColor = defaultSongColor ?: MaterialTheme.colorScheme.primaryContainer
                val c1 = fallbackColor
                val c2 = Color(
                    red = (c1.red * 0.60f + 0.12f).coerceIn(0f, 1f),
                    green = (c1.green * 0.60f + 0.12f).coerceIn(0f, 1f),
                    blue = (c1.blue * 0.60f + 0.12f).coerceIn(0f, 1f)
                )
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(colors = listOf(c1, c2, Color(0xFF141416))))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(lyricsOverlayAlpha)
                            .background(
                                if (isLandscapeTablet) Brush.horizontalGradient(
                                    colors = listOf(gc.copy(alpha = 0.65f), gc.copy(alpha = 0.50f), gc.copy(alpha = 0.72f))
                                ) else Brush.verticalGradient(
                                    colors = listOf(gc.copy(alpha = 0.72f), gc.copy(alpha = 0.52f), gc.copy(alpha = 0.78f))
                                )
                            )
                    )
                }
            }
        }
    }

    val infiniteZoomTransition = rememberInfiniteTransition(label = "ambientInfiniteZoomTransition")
    val ambientInfiniteScale by infiniteZoomTransition.animateFloat(
        initialValue = 1.00f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientInfiniteScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(outerBoxBgColor)
            .nestedScroll(lyricsNestedScrollConnection)
            .pointerInput(autoHideLyricsControls, lyricsVisible) {
                if (autoHideLyricsControls && lyricsVisible) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            if (lyricsVisible) {
                                showLyricsControls()
                            }
                        }
                    }
                }
            }
    ) {
        AnimatedVisibility(visible = showBg, enter = fadeIn(tween(600)), exit = fadeOut(tween(600))) {
            val entranceZoom = 1f + 0.04f * (1f - localEntranceFraction)
            val continuousZoom = if (isBackdropEnabled && playerAmbientInfiniteZoom) ambientInfiniteScale else 1f
            val bgZoom = entranceZoom * continuousZoom
            unifiedBackground(Modifier.fillMaxSize().graphicsLayer { scaleX = bgZoom; scaleY = bgZoom })
        }

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().then(swipeMinimizeModifier)
        ) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 0.dp)
            ) {
                val containerMaxWidth = maxWidth
                val containerMaxHeight = maxHeight
                val isPortraitLocal = containerMaxHeight > containerMaxWidth

                val controlButtonSize = if (isPortraitLocal) {
                    val base = (containerMaxWidth * 0.18f)
                    if (isCompactHeight) base.coerceIn(44.dp, 52.dp) else base.coerceIn(44.dp, 72.dp)
                } else { if (isCompactHeight) 44.dp else 64.dp }
                var artworkOffsetX by remember { mutableFloatStateOf(0f) }
                val artworkSwipeThreshold = 140f
                val artworkTranslationX by animateFloatAsState(
                    targetValue = artworkOffsetX.coerceIn(-200f, 200f),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "artworkTranslationX"
                )

                val artworkView = @Composable { modifier: Modifier, isSmall: Boolean ->
                    Box(
                        modifier = modifier,
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .padding(horizontal = if (isCompactWidth) 12.dp else 24.dp)
                                .fillMaxSize(if (isSmall) 0.75f else if (isTablet && !isLandscapeTablet) 0.55f else if (isCompactHeight) 0.78f else 0.88f)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = artworkScale
                                    scaleY = artworkScale
                                    translationX = artworkTranslationX
                                    shape = artworkClipShape
                                    clip = true
                                }
                                .pointerInput(showLyrics, lyricsVisible, gestureArtworkDoubleTap, gestureArtworkSingleTap) {
                                    if (gestureArtworkDoubleTap || (gestureArtworkSingleTap && showLyrics)) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (gestureArtworkDoubleTap) {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                    onPlayPause()
                                                }
                                            },
                                            onTap = {
                                                if (gestureArtworkSingleTap && showLyrics) {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                    onToggleLyrics()
                                                }
                                            }
                                        )
                                    }
                                }
                                .pointerInput(gesturePlayerSwipeTracks) {
                                    if (gesturePlayerSwipeTracks) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                if (artworkOffsetX < -artworkSwipeThreshold) {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                    onSkipNext()
                                                } else if (artworkOffsetX > artworkSwipeThreshold) {
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                                                    onSkipPrevious()
                                                }
                                                artworkOffsetX = 0f
                                            },
                                            onDragCancel = { artworkOffsetX = 0f },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                artworkOffsetX += dragAmount.x
                                            }
                                        )
                                    }
                                }
                        ) {
                            val currentSongArt = debouncedSong.value?.artworkUri

                            AnimatedContent(
                                targetState = hasValidArtwork,
                                transitionSpec = { fadeIn(tween(800)).togetherWith(fadeOut(tween(600))) },
                                label = "artworkIconTransition",
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) { validArtwork ->
                                if (validArtwork) {
                                    M3ImageUtils.M3MediaImage(
                                        data = currentSongArt,
                                        contentDescription = stringResource(R.string.content_desc_album_artwork),
                                        modifier = Modifier.fillMaxSize(),
                                        shape = artworkClipShape,
                                        type = M3PlaceholderType.TRACK,
                                        name = debouncedSong.value?.title,
                                        expressiveShape = playerArtworkShape
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.MusicNote,
                                            contentDescription = null,
                                            modifier = Modifier.size(if (isSmall) 96.dp else 144.dp),
                                            tint = when {
                                                showDarkBg -> Color.White.copy(alpha = 0.85f)
                                                useAccentBackground -> accentFg
                                                else -> monoFg
                                            }
                                        )
                                    }
                                }
                            }

                            if (canvasArtwork?.preferredAnimationUrl != null) {
                                CanvasArtworkPlayer(
                                    primaryUrl = canvasArtwork.animated,
                                    fallbackUrl = canvasArtwork.videoUrl,
                                    isPlaying = isPlaying,
                                    alwaysPlay = true,
                                    modifier = Modifier.fillMaxSize().clip(artworkClipShape)
                                )
                            }
                            if (canvasLoading && canvasArtwork == null) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    M3CircularLoader(
                                        modifier = Modifier.size(16.dp),
                                        color = primaryColor,
                                        strokeWidth = 2.5f
                                    )
                                }
                            }
                        }
                    }
                }

                val lyricsView = @Composable { modifier: Modifier ->
                    RhythmPlayerLyricsPanel(
                        lyrics = lyrics,
                        isLoadingLyrics = isLoadingLyrics,
                        onlineOnlyLyrics = onlineOnlyLyrics,
                        currentTimeMs = currentTimeMs,
                        onLyricsSeek = onLyricsSeek,
                        onTapLyricsView = onTapLyricsView,
                        textSizeMultiplier = playerLyricsTextSize,
                        onRetryLyrics = onRetryLyrics,
                        onShowLyricsEditor = onShowLyricsEditor,
                        onPickLyricsFile = onPickLyricsFile,
                        showTranslation = showLyricsTranslation,
                        showRomanization = showLyricsRomanization,
                        textAlignment = lyricsTextAlign,
                        textColor = onSurfaceColor,
                        subtitleColor = onSurfaceVariantColor,
                        activeColor = if (isBackdropEnabled) ambientTextColor else primaryColor,
                        buttonContainerColor = if (useAccentBackground) accentFg else null,
                        buttonContentColor = if (useAccentBackground) accentBg else null,
                        fadeColor = if (isBackdropEnabled) null else lyricsScrimColor,
                        onNavigateToLyricsSettings = onNavigateToLyricsSettings,
                        modifier = modifier
                    )
                }

                // Use fully qualified AnimatedVisibility to avoid ColumnScope receiver capture
                val artworkContent = @Composable { modifier: Modifier ->
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showAlbumArt || lyricsVisible,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = modifier
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = line2Alpha
                                    translationY = line2TranslationY
                                    scaleX = line2Pop
                                    scaleY = line2Pop
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = lyricsVisible,
                                transitionSpec = {
                                    val e = when (playerLyricsTransition) {
                                        1 -> fadeIn(tween(400, easing = EaseInOut))
                                        2 -> fadeIn(tween(350, easing = EaseInOut)) + scaleIn(tween(350, easing = EaseInOut), initialScale = 0.92f)
                                        3 -> fadeIn(tween(350, easing = EaseInOut)) + slideInVertically(tween(350, easing = EaseInOut)) { it / 2 }
                                        else -> fadeIn(tween(350, easing = EaseInOut)) + slideInVertically(tween(350, easing = EaseInOut)) { -it / 2 }
                                    }
                                    val x = when (playerLyricsTransition) {
                                        1 -> fadeOut(tween(300, easing = EaseInOut))
                                        2 -> fadeOut(tween(250, easing = EaseInOut)) + scaleOut(tween(250, easing = EaseInOut), targetScale = 0.92f)
                                        3 -> fadeOut(tween(250, easing = EaseInOut)) + slideOutVertically(tween(250, easing = EaseInOut)) { it / 2 }
                                        else -> fadeOut(tween(250, easing = EaseInOut)) + slideOutVertically(tween(250, easing = EaseInOut)) { -it / 2 }
                                    }
                                    e togetherWith x
                                },
                                label = "lyricsViewTransition",
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) { tl ->
                                if (tl) {
                                    lyricsView(
                                        Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight()
                                            .padding(horizontal = if (isCompactWidth) 16.dp else 24.dp)
                                    )
                                } else {
                                    artworkView(Modifier.fillMaxSize(), false)
                                }
                            }
                        }
                    }
                }

                val controlsContent = @Composable {
                    androidx.compose.animation.AnimatedVisibility(visible = showPlayerControls, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                        Column(Modifier.fillMaxWidth().padding(start = if (isCompactWidth) 12.dp else 24.dp, end = if (isCompactWidth) 12.dp else 24.dp, bottom = if (isCompactHeight) 8.dp else 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.fillMaxWidth().graphicsLayer { alpha = line3Alpha; translationY = line3TranslationY; scaleX = line3Pop; scaleY = line3Pop }.padding(bottom = if (isCompactHeight) 16.dp else 28.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                    // Track title & artist slide horizontally based on queue direction.
                                    // Driven by the debounced song so they stay stable during crossfade,
                                    // with FastOutSlowIn deceleration so the slide lands smoothly.
                                    AnimatedContent(
                                        targetState = debouncedSong.value,
                                        transitionSpec = {
                                            val direction = queueDirection
                                            (slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth * direction } +
                                                fadeIn(tween(380, easing = FastOutSlowInEasing))).togetherWith(
                                                slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth * direction } +
                                                    fadeOut(tween(320))
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().clipToBounds(),
                                        contentAlignment = Alignment.CenterStart,
                                        label = "songInfoSlideTransition"
                                    ) { targetSong ->
                                        val targetTitle = targetSong?.title ?: stringResource(R.string.unknown_track)
                                        val targetArtist = targetSong?.artist ?: stringResource(R.string.unknown_artist)
                                        val targetTitleLength = targetTitle.length
                                        val targetLetterSpacing = when {
                                            isCompactWidth || targetTitleLength > 32 -> (-0.6).sp
                                            targetTitleLength > 24 -> (-1.0).sp
                                            else -> (-1.5).sp
                                        }
                                        val targetTextStyle = when {
                                            isCompactWidth -> MaterialTheme.typography.headlineSmall
                                            isCompactHeight -> MaterialTheme.typography.headlineMedium
                                            targetTitleLength > 32 -> MaterialTheme.typography.headlineSmall
                                            targetTitleLength > 24 -> MaterialTheme.typography.headlineMedium
                                            else -> MaterialTheme.typography.displaySmall
                                        }.copy(fontWeight = FontWeight.Black, letterSpacing = targetLetterSpacing)
                                        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = if (playerMergeControlsToBottom) Alignment.CenterHorizontally else Alignment.Start) {
                                            AutoScrollingTextOnDemand(text = targetTitle, style = targetTextStyle.copy(color = onSurfaceColor),
                                                gradientEdgeColor = when { showDarkBg -> Color.Black; else -> outerBoxBgColor },
                                                modifier = Modifier.fillMaxWidth().clickable { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onSongInfoClick() }, respectGlobalSetting = true,
                                                textAlign = if (playerMergeControlsToBottom) TextAlign.Center else TextAlign.Start)
                                            AutoScrollingTextOnDemand(text = targetArtist, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, color = onSurfaceVariantColor),
                                                gradientEdgeColor = when { showDarkBg -> Color.Black; else -> outerBoxBgColor },
                                                modifier = Modifier.fillMaxWidth().clickable { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onShowArtist() }, respectGlobalSetting = true,
                                                textAlign = if (playerMergeControlsToBottom) TextAlign.Center else TextAlign.Start)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(16.dp))

                                if (!playerMergeControlsToBottom) {
                                    RhythmGroupedButton(
                                        size = RhythmButtonSize.Small,
                                        isFillMaxWidth = false,
                                        modifier = Modifier.widthIn(max = 100.dp)
                                    ) {
                                        RhythmButtonWeighted(
                                            onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onToggleLyrics() },
                                            weight = 1f,
                                            isFirst = true,
                                            isLast = false,
                                            containerColor = controlsContainerColor,
                                            contentColor = when { needsDarkSurfaces -> ambientControlContent; useAccentBackground -> accentFg; else -> monoFg },
                                            icon = RhythmIcons.Player.Lyrics
                                        )
                                        RhythmButtonWeighted(
                                            onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onToggleFavorite() },
                                            weight = 1f,
                                            isFirst = false,
                                            isLast = true,
                                            containerColor = controlsContainerColor,
                                            contentColor = when { needsDarkSurfaces -> ambientControlContent; useAccentBackground -> accentFg; else -> monoFg },
                                            icon = if (isFavorite) MaterialSymbolIcon("thumb_down", filled = true) else MaterialSymbolIcon("thumb_up", filled = true)
                                        )
                                    }
                                }
                            }

                            // The whole controls card (play controls + progress) slides up as one unit.
                            // In accent-color mode the card is dropped and the controls spread directly.
                            val controlsCardContent = @Composable {
                                val controlsPadding = if (useAccentBackground) {
                                    PaddingValues(start = if (isCompactWidth) 12.dp else 20.dp, end = if (isCompactWidth) 12.dp else 20.dp, top = 8.dp)
                                } else {
                                    PaddingValues(if (isCompactWidth) 12.dp else 20.dp)
                                }
                                Column(Modifier.padding(controlsPadding)) {
                                    Row(Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(if (isCompactWidth) 8.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RhythmPlayButton(
                                            isPlaying = isPlaying,
                                            showBuffering = showBuffering,
                                            onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY); onPlayPause() },
                                            containerColor = when {
                                                needsDarkSurfaces -> ambientPlayContainer
                                                useAccentBackground -> accentFg
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            contentColor = when {
                                                needsDarkSurfaces -> ambientPlayContent
                                                useAccentBackground -> accentBg
                                                else -> MaterialTheme.colorScheme.onPrimary
                                            },
                                            size = controlButtonSize,
                                            modifier = Modifier.weight(1f)
                                        )
                                        RhythmControlButton(
                                            onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onSkipNext() },
                                            shape = playerControlShape,
                                            containerColor = when {
                                                needsDarkSurfaces -> ambientControlContainer
                                                useAccentBackground -> accentGlassStrong
                                                else -> MaterialTheme.colorScheme.secondaryContainer
                                            },
                                            contentColor = when {
                                                needsDarkSurfaces -> ambientControlContent
                                                useAccentBackground -> accentFg
                                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                                            },
                                            size = controlButtonSize
                                        ) {
                                            Icon(RhythmIcons.Player.SkipNext, stringResource(R.string.cd_next_track), Modifier.size(controlButtonSize * 0.45f),
                                                tint = when {
                                                    needsDarkSurfaces -> ambientControlContent
                                                    useAccentBackground -> accentFg
                                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                })
                                        }
                                    }
                                    Spacer(Modifier.height(if (isCompactHeight) 8.dp else 16.dp))
                                    Row(Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(if (isCompactWidth) 8.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RhythmControlButton(
                                            onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onSkipPrevious() },
                                            shape = playerControlShape,
                                            containerColor = when {
                                                needsDarkSurfaces -> ambientControlContainer
                                                useAccentBackground -> accentGlassStrong
                                                else -> MaterialTheme.colorScheme.secondaryContainer
                                            },
                                            contentColor = when {
                                                needsDarkSurfaces -> ambientControlContent
                                                useAccentBackground -> accentFg
                                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                                            },
                                            size = controlButtonSize
                                        ) {
                                            Icon(RhythmIcons.Player.SkipPrevious, stringResource(R.string.cd_previous_track), Modifier.size(controlButtonSize * 0.45f),
                                                tint = when {
                                                    needsDarkSurfaces -> ambientControlContent
                                                    useAccentBackground -> accentFg
                                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                })
                                        }
                                        val canSeek = totalTimeMs > 0L
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                            if (showBuffering) {
                                                M3LinearLoader(modifier = Modifier.fillMaxWidth().height(8.dp), color = primaryColor, trackColor = onSurfaceColor.copy(alpha = 0.18f))
                                            } else if (playerProgressStyle == "WAVY") {
                                                WaveSlider(value = if (isScrubbing && enhancedSeekingEnabled) scrubProgress else progressValue,
                                                    onValueChange = { if (canSeek && enhancedSeekingEnabled) { isScrubbing = true; scrubProgress = it } else if (canSeek) onSeek(it) },
                                                    onValueChangeFinished = { if (canSeek && enhancedSeekingEnabled && isScrubbing) { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onSeek(scrubProgress); isScrubbing = false } },
                                                    modifier = Modifier.fillMaxWidth(), enabled = canSeek, isPlaying = isPlaying,
                                                    activeTrackColor = primaryColor, inactiveTrackColor = onSurfaceColor.copy(alpha = 0.2f), thumbColor = primaryColor)
                                            } else {
                                                val ps = try { ProgressStyle.valueOf(playerProgressStyle) } catch (e: IllegalArgumentException) { ProgressStyle.NORMAL }
                                                val ts = ThumbStyle.fromStorage(playerProgressThumbStyle)
                                                Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
                                                    StyledProgressBar(progress = progressValue, style = ps, modifier = Modifier.fillMaxWidth(),
                                                        progressColor = primaryColor, trackColor = onSurfaceColor.copy(alpha = 0.2f),
                                                        height = when (ps) { ProgressStyle.THIN -> 2.dp; ProgressStyle.THICK -> 12.dp; else -> 8.dp },
                                                        isPlaying = isPlaying, showThumb = ts != ThumbStyle.NONE, thumbStyle = ts, thumbSize = 14.dp, rotateThumbWhenPlaying = playerProgressThumbRotate, waveAmplitudeWhenPlaying = 3.dp, waveLength = 60.dp)
                                                    Slider(value = progressValue, onValueChange = { onSeek(it) }, modifier = Modifier.fillMaxWidth(), enabled = canSeek,
                                                        onValueChangeFinished = { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT) },
                                                        colors = SliderDefaults.colors(thumbColor = Color.Transparent, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(currentTimeStr, style = MaterialTheme.typography.labelMedium, color = onSurfaceVariantColor)
                                                Text(
                                                    text = totalTimeStr,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = onSurfaceVariantColor,
                                                    modifier = Modifier
                                                        .clickable { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onTotalTimeClick() }
                                                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (useAccentBackground) {
                                Box(Modifier.fillMaxWidth().graphicsLayer { alpha = line4Alpha; translationY = line4TranslationY; scaleX = line4Pop; scaleY = line4Pop }) {
                                    controlsCardContent()
                                }
                            } else {
                                Surface(shape = RoundedCornerShape(32.dp), color = controlsContainerColor,
                                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = line4Alpha; translationY = line4TranslationY; scaleX = line4Pop; scaleY = line4Pop }) {
                                    controlsCardContent()
                                }
                            }
                        }
                    }
                }

                val bottomButtonsContent = @Composable {
                    androidx.compose.animation.AnimatedVisibility(visible = showBottomButtons, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = line6Alpha; translationY = line6TranslationY; scaleX = line6Pop; scaleY = line6Pop }
                            .padding(start = if (isCompactWidth) 12.dp else 24.dp, end = if (isCompactWidth) 12.dp else 24.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(if (isCompactHeight) 12.dp else 16.dp))
                            val deviceIcon = when {
                                location?.id?.startsWith("bt_") == true -> RhythmIcons.BluetoothFilled
                                location?.id == "wired_headset" -> RhythmIcons.HeadphonesFilled
                                location?.id == "speaker" -> RhythmIcons.SpeakerFilled
                                else -> RhythmIcons.Location
                            }

                            val defaultContentColor = when {
                                needsDarkSurfaces -> ambientControlContent
                                useAccentBackground -> accentFg
                                else -> monoFg
                            }

                            val activeButtons = remember(
                                playerMergeControlsToBottom,
                                expressiveBottomButtonsMerge,
                                expressiveHiddenBottomButtonsMerge,
                                expressiveBottomButtonsNormal,
                                expressiveHiddenBottomButtonsNormal
                            ) {
                                if (playerMergeControlsToBottom) {
                                    val filtered = expressiveBottomButtonsMerge.filter { !expressiveHiddenBottomButtonsMerge.contains(it) }
                                    if (filtered.isEmpty()) appSettings.defaultExpressiveBottomButtonsMerge else filtered
                                } else {
                                    val filtered = expressiveBottomButtonsNormal.filter { !expressiveHiddenBottomButtonsNormal.contains(it) }
                                    if (filtered.isEmpty()) appSettings.defaultExpressiveBottomButtonsNormal else filtered
                                }
                            }

                            val isCompactButtons = playerMergeControlsToBottom || activeButtons.size > 3

                            if (isCompactButtons) {
                                val pillMaxWidth = (activeButtons.size * 56 + 24).dp.coerceIn(200.dp, 400.dp)
                                RhythmGroupedButton(
                                    size = RhythmButtonSize.Small,
                                    isFillMaxWidth = false,
                                    modifier = Modifier.widthIn(max = pillMaxWidth)
                                ) {
                                    activeButtons.forEachIndexed { index, buttonId ->
                                        val isFirst = index == 0
                                        val isLast = index == activeButtons.size - 1
                                        when (buttonId) {
                                            "LYRICS" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onToggleLyrics() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Player.Lyrics,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.expressiveplayerscreen_lyrics),
                                                    containerColor = if (showLyricsView) primaryColor.copy(alpha = 0.35f) else controlsContainerColor,
                                                    contentColor = if (showLyricsView) primaryColor else defaultContentColor
                                                )
                                            }
                                            "FAVORITE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onToggleFavorite() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = if (isFavorite) MaterialSymbolIcon("thumb_down", filled = true) else MaterialSymbolIcon("thumb_up", filled = true),
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.expressiveplayerscreen_favorite),
                                                    containerColor = if (isFavorite) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else controlsContainerColor,
                                                    contentColor = if (isFavorite) MaterialTheme.colorScheme.error else defaultContentColor
                                                )
                                            }
                                            "DEVICE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onDeviceClick() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = deviceIcon,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.expressiveplayerscreen_device),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "QUEUE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onQueueClick() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Queue,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    textContent = {
                                                        if (queueTotal > 1) {
                                                            Box(
                                                                modifier = Modifier.size(18.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.22f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = debouncedQueuePosition.coerceIn(1, queueTotal).toString(),
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                    fontSize = 9.sp,
                                                                    color = primaryColor
                                                                )
                                                            }
                                                        }
                                                    },
                                                    contentDescription = stringResource(R.string.bottomsheet_queue),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "MORE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onMoreClick() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.More,
                                                    iconSize = 22.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.expressiveplayerscreen_more),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "SHUFFLE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onToggleShuffle() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Player.Shuffle,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.action_shuffle),
                                                    containerColor = if (isShuffleEnabled) primaryColor.copy(alpha = 0.35f) else controlsContainerColor,
                                                    contentColor = if (isShuffleEnabled) primaryColor else defaultContentColor
                                                )
                                            }
                                            "REPEAT" -> {
                                                val repeatIcon = when (repeatMode) {
                                                    2 -> RhythmIcons.Player.RepeatOne
                                                    1 -> RhythmIcons.Player.Repeat
                                                    else -> RhythmIcons.Player.Repeat
                                                }
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onToggleRepeat() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = repeatIcon,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.player_chip_repeat),
                                                    containerColor = if (repeatMode != 0) primaryColor.copy(alpha = 0.35f) else controlsContainerColor,
                                                    contentColor = if (repeatMode != 0) primaryColor else defaultContentColor
                                                )
                                            }
                                            "EQUALIZER" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onEqualizer() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = MaterialSymbolIcon("graphic_eq", filled = true),
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.equalizer),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "SPEED" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onPlaybackSpeed() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = MaterialSymbolIcon("tune", filled = true),
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.player_chip_speed),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "SLEEP_TIMER" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onSleepTimer() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.AccessTime,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.sleep_timer),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "ADD_TO_PLAYLIST" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onAddToPlaylist() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.AddToPlaylist,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.bottomsheet_add_to_playlist),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "ALBUM" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onShowAlbumBottomSheet() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Music.Album,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.player_chip_album),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "ARTIST" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onShowArtist() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Music.Artist,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.player_chip_artist),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "SONG_INFO" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onSongInfoClick() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Info,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.action_song_info),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "SHARE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onShareFile() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Share,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    contentDescription = stringResource(R.string.extrasheet_share_file),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                RhythmGroupedButton(
                                    size = RhythmButtonSize.Small
                                ) {
                                    val deviceName = location?.name ?: "Output"
                                    val deviceTextStyle = when {
                                        deviceName.length > 28 -> MaterialTheme.typography.labelLarge
                                        deviceName.length > 18 -> MaterialTheme.typography.titleSmall
                                        else -> MaterialTheme.typography.titleMedium
                                    }.copy(fontWeight = FontWeight.Bold)

                                    activeButtons.forEachIndexed { index, buttonId ->
                                        val isFirst = index == 0
                                        val isLast = index == activeButtons.size - 1
                                        when (buttonId) {
                                            "DEVICE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onDeviceClick() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = deviceIcon,
                                                    iconSize = 20.dp,
                                                    text = deviceName,
                                                    textStyle = deviceTextStyle,
                                                    gradientEdgeColor = when { needsDarkSurfaces -> Color.Black; useAccentBackground -> accentFg; else -> monoBg },
                                                    respectMarqueeGlobalSetting = false,
                                                    contentDescription = stringResource(R.string.expressiveplayerscreen_device),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "QUEUE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onQueueClick() },
                                                    weight = 1f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Queue,
                                                    iconSize = 20.dp,
                                                    text = null,
                                                    textContent = {
                                                        val queueText = if (queueTotal > 0) stringResource(R.string.player_queue_format, debouncedQueuePosition.coerceIn(1, queueTotal), queueTotal) else stringResource(R.string.player_queue)
                                                        AnimatedDigitTickerText(
                                                            text = queueText,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = defaultContentColor,
                                                            fontWeight = FontWeight.Bold,
                                                            prefix = "queueCounter"
                                                        )
                                                    },
                                                    contentDescription = stringResource(R.string.bottomsheet_queue),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "MORE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onMoreClick() },
                                                    weight = 0.35f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.More,
                                                    iconSize = 22.dp,
                                                    contentDescription = stringResource(R.string.expressiveplayerscreen_more),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "LYRICS" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onToggleLyrics() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Player.Lyrics,
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.expressiveplayerscreen_lyrics),
                                                    containerColor = if (showLyricsView) primaryColor.copy(alpha = 0.35f) else controlsContainerColor,
                                                    contentColor = if (showLyricsView) primaryColor else defaultContentColor
                                                )
                                            }
                                            "FAVORITE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onToggleFavorite() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = if (isFavorite) MaterialSymbolIcon("thumb_down", filled = true) else MaterialSymbolIcon("thumb_up", filled = true),
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.expressiveplayerscreen_favorite),
                                                    containerColor = if (isFavorite) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else controlsContainerColor,
                                                    contentColor = if (isFavorite) MaterialTheme.colorScheme.error else defaultContentColor
                                                )
                                            }
                                            "SHUFFLE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onToggleShuffle() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Player.Shuffle,
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.action_shuffle),
                                                    containerColor = if (isShuffleEnabled) primaryColor.copy(alpha = 0.35f) else controlsContainerColor,
                                                    contentColor = if (isShuffleEnabled) primaryColor else defaultContentColor
                                                )
                                            }
                                            "REPEAT" -> {
                                                val repeatIcon = when (repeatMode) {
                                                    2 -> RhythmIcons.Player.RepeatOne
                                                    1 -> RhythmIcons.Player.Repeat
                                                    else -> RhythmIcons.Player.Repeat
                                                }
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onToggleRepeat() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = repeatIcon,
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.player_chip_repeat),
                                                    containerColor = if (repeatMode != 0) primaryColor.copy(alpha = 0.35f) else controlsContainerColor,
                                                    contentColor = if (repeatMode != 0) primaryColor else defaultContentColor
                                                )
                                            }
                                            "EQUALIZER" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onEqualizer() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = MaterialSymbolIcon("graphic_eq", filled = true),
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.equalizer),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "SPEED" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onPlaybackSpeed() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = MaterialSymbolIcon("tune", filled = true),
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.player_chip_speed),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "SLEEP_TIMER" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onSleepTimer() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.AccessTime,
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.sleep_timer),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "ADD_TO_PLAYLIST" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onAddToPlaylist() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.AddToPlaylist,
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.bottomsheet_add_to_playlist),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "ALBUM" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onShowAlbumBottomSheet() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Music.Album,
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.player_chip_album),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "ARTIST" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onShowArtist() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Music.Artist,
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.player_chip_artist),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "SONG_INFO" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onSongInfoClick() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Info,
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.action_song_info),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                            "SHARE" -> {
                                                RhythmDetailActionButton(
                                                    onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onShareFile() },
                                                    weight = 0.6f,
                                                    height = 44.dp,
                                                    isFirst = isFirst,
                                                    isLast = isLast,
                                                    type = RhythmButtonType.Tonal,
                                                    icon = RhythmIcons.Share,
                                                    iconSize = 20.dp,
                                                    contentDescription = stringResource(R.string.extrasheet_share_file),
                                                    containerColor = controlsContainerColor,
                                                    contentColor = defaultContentColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isFlexMode) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Upper half (top screen): Artwork / Lyrics / Visualizer
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            artworkContent(Modifier.fillMaxSize())
                        }

                        // Lower half (bottom screen): Ergonomic Playback Controls
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            controlsContent()
                            Spacer(Modifier.height(8.dp))
                            bottomButtonsContent()
                        }
                    }
                } else if (isLandscapeTablet) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AnimatedContent(
                                targetState = lyricsVisible && showPlayerControls,
                                transitionSpec = {
                                    if (targetState) {
                                        (slideInHorizontally(tween(400, easing = EaseInOut)) { it } + fadeIn(tween(350, easing = EaseInOut))) togetherWith
                                            (slideOutHorizontally(tween(350, easing = EaseInOut)) { -it } + fadeOut(tween(250, easing = EaseInOut)))
                                    } else {
                                        (slideInHorizontally(tween(400, easing = EaseInOut)) { -it } + fadeIn(tween(350, easing = EaseInOut))) togetherWith
                                            (slideOutHorizontally(tween(350, easing = EaseInOut)) { it } + fadeOut(tween(250, easing = EaseInOut)))
                                    }
                                },
                                label = "tabletLandscapeLeftTransition"
                            ) { showLyricsOnLeft ->
                                if (showLyricsOnLeft) {
                                    lyricsView(Modifier.fillMaxSize().padding(horizontal = 24.dp))
                                } else if (showAlbumArt) {
                                    Box(Modifier.fillMaxSize()) {
                                        artworkView(Modifier.fillMaxSize(), false)
                                    }
                                }
                            }
                        }
                        Column(
                            Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AnimatedContent(
                                targetState = lyricsVisible && !showPlayerControls,
                                transitionSpec = {
                                    if (targetState) {
                                        (slideInHorizontally(tween(400, easing = EaseInOut)) { it } + fadeIn(tween(350, easing = EaseInOut))) togetherWith
                                            (slideOutHorizontally(tween(350, easing = EaseInOut)) { it } + fadeOut(tween(250, easing = EaseInOut)))
                                    } else {
                                        (slideInHorizontally(tween(350, easing = EaseInOut)) { it } + fadeIn(tween(300, easing = EaseInOut))) togetherWith
                                            (slideOutHorizontally(tween(300, easing = EaseInOut)) { it } + fadeOut(tween(200, easing = EaseInOut)))
                                    }
                                },
                                label = "tabletLandscapeRightTransition"
                            ) { showLyricsOnRight ->
                                if (showLyricsOnRight) {
                                    lyricsView(Modifier.fillMaxSize().padding(horizontal = 24.dp))
                                } else {
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box { controlsContent() }
                                        Box { bottomButtonsContent() }
                                    }
                                }
                            }
                        }
                    }
                } else if (isTablet) {
                    AnimatedContent(
                        targetState = lyricsVisible && !showPlayerControls,
                        transitionSpec = {
                            fadeIn(tween(350, easing = EaseInOut)) togetherWith fadeOut(tween(250, easing = EaseInOut))
                        },
                        label = "tabletPortraitLayoutTransition"
                    ) { isImmersive ->
                        if (isImmersive) {
                            if (showAlbumArt) {
                                Row(Modifier.fillMaxSize().navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        Modifier.weight(0.9f).fillMaxHeight().padding(horizontal = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(Modifier.fillMaxSize()) {
                                            artworkView(Modifier.fillMaxSize(), true)
                                        }
                                    }
                                    Column(
                                        Modifier.weight(1.1f).fillMaxHeight().padding(horizontal = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        lyricsView(Modifier.fillMaxSize().padding(horizontal = 16.dp))
                                    }
                                }
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 32.dp, vertical = 24.dp)
                                ) {
                                    lyricsView(Modifier.fillMaxSize())
                                }
                            }
                        } else {
                            Column(
                                Modifier.fillMaxSize().navigationBarsPadding(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(Modifier.weight(1f)) {
                                    AnimatedContent(
                                        targetState = lyricsVisible,
                                        transitionSpec = {
                                            if (targetState) {
                                                (slideInHorizontally(tween(400, easing = EaseInOut)) { it } + fadeIn(tween(350, easing = EaseInOut))) togetherWith
                                                    (slideOutHorizontally(tween(350, easing = EaseInOut)) { -it } + fadeOut(tween(250, easing = EaseInOut)))
                                            } else {
                                                (slideInHorizontally(tween(400, easing = EaseInOut)) { -it } + fadeIn(tween(350, easing = EaseInOut))) togetherWith
                                                    (slideOutHorizontally(tween(350, easing = EaseInOut)) { it } + fadeOut(tween(250, easing = EaseInOut)))
                                            }
                                        },
                                        label = "tabletPortraitTopContent"
                                    ) { isLyrics ->
                                        if (isLyrics) {
                                            lyricsView(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 16.dp))
                                        } else if (showAlbumArt) {
                                            artworkView(Modifier.fillMaxSize().padding(bottom = 32.dp), false)
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp)
                                        .padding(bottom = 24.dp)
                                ) {
                                    controlsContent()
                                }
                                Box(
                                    modifier = Modifier.padding(horizontal = 48.dp)
                                ) {
                                    bottomButtonsContent()
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(Modifier.weight(1f)) {
                            artworkContent(Modifier.fillMaxSize().padding(bottom = if (isCompactHeight) 12.dp else 24.dp))
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            controlsContent()
                        }
                        Box {
                            bottomButtonsContent()
                        }
                    }
                }

                // Audio quality icon at top-right — fades in once the (debounced) track settles
                // and auto-hides after 5 seconds
                if (playerShowAudioQualityBadges) {
                    debouncedSong.value?.let { displaySong ->
                        AudioQualityIcon(
                            song = displaySong,
                            iconSize = 40.dp,
                            padding = 6.dp,
                            autoHideAfterMs = 5000,
                            tint = when {
                                showDarkBg -> Color.White
                                useAccentBackground -> accentFg
                                else -> null
                            },
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RhythmPlayerLyricsPanel(
    lyrics: LyricsData?,
    isLoadingLyrics: Boolean,
    onlineOnlyLyrics: Boolean,
    currentTimeMs: Long,
    onLyricsSeek: ((Long) -> Unit)?,
    textSizeMultiplier: Float,
    onRetryLyrics: () -> Unit,
    onShowLyricsEditor: () -> Unit,
    onPickLyricsFile: () -> Unit,
    showTranslation: Boolean,
    showRomanization: Boolean,
    textAlignment: TextAlign,
    modifier: Modifier = Modifier,
    onTapLyricsView: (() -> Unit)? = null,
    onNavigateToLyricsSettings: (() -> Unit)? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    buttonContainerColor: Color? = null,
    buttonContentColor: Color? = null,
    fadeColor: Color? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hasLyrics = lyrics?.hasLyrics() == true && lyrics.isErrorMessage().not()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            isLoadingLyrics -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
                    M3CircularLoader(modifier = Modifier.size(48.dp), color = activeColor, trackColor = activeColor.copy(alpha = 0.15f), strokeWidth = 4f)
                    Spacer(Modifier.height(16.dp))
                    Text(context.getString(R.string.player_loading_lyrics), style = MaterialTheme.typography.bodyMedium, color = textColor.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
            }
            !hasLyrics -> {
                val message = if (onlineOnlyLyrics) stringResource(R.string.lyrics_currently_no_lyrics) else stringResource(R.string.lyrics_no_lyrics)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(RhythmIcons.MusicNote, null, Modifier.size(48.dp), tint = textColor.copy(alpha = 0.8f))
                    Spacer(Modifier.height(16.dp))
                    Text(message, style = MaterialTheme.typography.bodyLarge, color = textColor.copy(alpha = 0.8f), textAlign = textAlignment)
                    if (!isLoadingLyrics) {
                        Spacer(Modifier.height(16.dp))
                        RhythmGroupedButton(
                            size = RhythmButtonSize.Small
                        ) {
                            RhythmButtonWeighted(
                                onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onRetryLyrics() },
                                weight = 1f,
                                isFirst = true,
                                isLast = false,
                                icon = RhythmIcons.Refresh,
                                text = stringResource(R.string.updates_retry),
                                containerColor = buttonContainerColor,
                                contentColor = buttonContentColor
                            )
                            RhythmButtonWeighted(
                                onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onShowLyricsEditor() },
                                weight = 1f,
                                isFirst = false,
                                isLast = false,
                                icon = RhythmIcons.Player.Lyrics,
                                text = stringResource(R.string.lyrics_editor_short),
                                containerColor = buttonContainerColor,
                                contentColor = buttonContentColor
                            )
                            RhythmButtonWeighted(
                                onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM); onNavigateToLyricsSettings?.invoke() },
                                weight = 1f,
                                isFirst = false,
                                isLast = true,
                                icon = MaterialSymbolIcon("settings", filled = true),
                                text = stringResource(R.string.lyrics_settings_short),
                                containerColor = buttonContainerColor,
                                contentColor = buttonContentColor
                            )
                        }
                    }
                }
            }
            else -> {
                val localAppSettings = remember { chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context) }
                val translationAutoWord by localAppSettings.translationAutoWord.collectAsState()
                val wordByWordLyrics = remember(lyrics, translationAutoWord) {
                    lyrics.getWordByWordLyricsOrNull() ?: run {
                        if (translationAutoWord && lyrics.syncedLyrics != null) try {
                            val o = LrcUtils.LrcParserOptions(true, true, null, true)
                            val p = LrcUtils.parseLyrics(lyrics.syncedLyrics, null, o, LrcUtils.LyricFormat.LRC)
                            if (p is SemanticLyrics.SyncedLyrics) LrcUtils.convertSemanticLyricsToWordByWord(p) else null
                        } catch (e: Exception) { null } else null
                    }
                }

                if (wordByWordLyrics != null) {
                    WordByWordLyricsView(wordByWordLyrics, currentTimeMs, Modifier.fillMaxSize(), onSeek = onLyricsSeek,
                        onTapLyricsView = onTapLyricsView, lyricsSource = lyrics.source, textSizeMultiplier = textSizeMultiplier,
                        textAlignment = textAlignment, showTranslation = showTranslation, showRomanization = showRomanization,
                        textColor = textColor, activeColor = activeColor, subtitleColor = subtitleColor)
                } else {
                    val lyricsText = remember(lyrics) { lyrics.getBestLyrics() ?: "" }
                    val filteredText = remember(lyricsText, showTranslation, showRomanization) { filterPlainLyricsByPreference(lyricsText, showTranslation, showRomanization) }
                    val likelySynced = remember(lyricsText) { Regex("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{0,3})?]").containsMatchIn(lyricsText) }
                    val parsedLyrics by produceState<List<chromahub.rhythm.app.util.LyricLine>?>(if (likelySynced) null else emptyList(), lyricsText, likelySynced) {
                        value = if (!likelySynced) emptyList() else withContext(Dispatchers.Default) { chromahub.rhythm.app.util.LyricsParser.parseLyrics(lyricsText) }
                    }
                    if (parsedLyrics == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            M3CircularLoader(modifier = Modifier.size(28.dp), color = activeColor, trackColor = activeColor.copy(alpha = 0.2f), strokeWidth = 2f)
                        }
                    } else if (parsedLyrics?.isNotEmpty() == true) {
                        SyncedLyricsView(lyricsText, currentTimeMs, Modifier.fillMaxSize(), parsedLyricsInput = parsedLyrics,
                            onSeek = onLyricsSeek, onTapLyricsView = onTapLyricsView, showTranslation = showTranslation,
                            showRomanization = showRomanization, lyricsSource = lyrics.source, textSizeMultiplier = textSizeMultiplier, textAlignment = textAlignment,
                            textColor = textColor, activeColor = activeColor, subtitleColor = subtitleColor)
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                            horizontalAlignment = when (textAlignment) { TextAlign.Start -> Alignment.Start; TextAlign.End -> Alignment.End; else -> Alignment.CenterHorizontally }) {
                            Text(filteredText, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * textSizeMultiplier,
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.6f * textSizeMultiplier, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
                                color = textColor, textAlign = textAlignment, modifier = Modifier.fillMaxWidth())
                            if (!lyrics.source.isNullOrBlank()) {
                                Spacer(Modifier.height(24.dp))
                                Text(stringResource(R.string.lyrics_source_attribution, lyrics.source), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp),
                                    color = subtitleColor.copy(alpha = 0.6f), textAlign = textAlignment, modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp))
                            }
                        }
                    }
                }
            }
        }
        if (fadeColor != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.14f)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(fadeColor, fadeColor.copy(alpha = 0f))))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.14f)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(fadeColor.copy(alpha = 0f), fadeColor)))
            )
        }
    }
}

private fun filterPlainLyricsByPreference(rawLyrics: String, showTranslation: Boolean, showRomanization: Boolean): String {
    if (rawLyrics.isBlank() || (showTranslation && showRomanization)) return rawLyrics
    val filteredLines = mutableListOf<String>()
    var prevNonLatin = false
    rawLyrics.lineSequence().forEach { line ->
        val t = line.trim()
        if (t.isEmpty()) { filteredLines += line; return@forEach }
        val isBracketTrans = t.startsWith("(") && t.endsWith(")") && t.length > 2
        val isBracketRoman = t.startsWith("[") && t.endsWith("]") && t.length > 2
        val hasLetters = t.any { it.isLetter() }
        val isLatin = chromahub.rhythm.app.util.LyricsParser.isLatinBased(t)
        if ((!showTranslation && isBracketTrans) || (!showRomanization && (isBracketRoman || (hasLetters && isLatin && prevNonLatin)))) return@forEach
        filteredLines += line
        if (!isBracketTrans && !isBracketRoman) prevNonLatin = chromahub.rhythm.app.util.LyricsParser.hasNonLatinScript(t)
    }
    return filteredLines.joinToString("\n")
}
