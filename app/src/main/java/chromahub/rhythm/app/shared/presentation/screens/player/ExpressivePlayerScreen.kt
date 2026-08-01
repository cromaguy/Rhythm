package chromahub.rhythm.app.shared.presentation.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.EaseInOut
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.ArtistBottomSheet
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
import chromahub.rhythm.app.util.LrcUtils
import chromahub.rhythm.app.network.CanvasArtwork
import chromahub.rhythm.app.shared.presentation.components.player.CanvasArtworkPlayer
import chromahub.rhythm.app.util.SemanticLyrics
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePlayerScreen(
    song: Song?,
    isPlaying: Boolean,
    isFavorite: Boolean,
    canvasArtwork: CanvasArtwork? = null,
    canvasLoading: Boolean = false,
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
    onShowArtistBottomSheet: () -> Unit,
    onMoreClick: () -> Unit,
    onDeviceClick: () -> Unit,
    onQueueClick: () -> Unit,
    onBack: () -> Unit,
    location: PlaybackLocation?,
    appSettings: AppSettings,
    onOpenFullScreenLyrics: () -> Unit = {},
    swipeToDismissEnabled: Boolean = true,
    expansionFraction: Float = 1f,
    modifier: Modifier = Modifier
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
    val enhancedSeekingEnabled by appSettings.enhancedSeekingEnabled.collectAsState()
    val playerAmbientBackdropEnabled by appSettings.playerAmbientBackdropEnabled.collectAsState()
    val playerAmbientBackdropIntensity by appSettings.playerAmbientBackdropIntensity.collectAsState()
    val playerLyricsTextSize by appSettings.playerLyricsTextSize.collectAsState()
    val showLyricsTranslation by appSettings.showLyricsTranslation.collectAsState()
    val showLyricsRomanization by appSettings.showLyricsRomanization.collectAsState()
    val playerLyricsTransition by appSettings.playerLyricsTransition.collectAsState()
    val tapLyricsToFullScreen by appSettings.tapLyricsToFullScreen.collectAsState()
    val playerLyricsAlignment by appSettings.playerLyricsAlignment.collectAsState()
    val keepScreenOnLyrics by appSettings.keepScreenOnLyrics.collectAsState()

    val onTapLyricsView = if (tapLyricsToFullScreen) onOpenFullScreenLyrics else null
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    val progressValue = progress().coerceIn(0f, 1f)
    val currentTimeMs = (progressValue * (song?.duration ?: 0L)).toLong()
    val lyricsVisible = showLyricsView && showLyrics
    val showBuffering = isMediaLoading || isSeeking

    val isBackdropEnabled = (playerAmbientBackdropEnabled || canvasArtwork?.preferredAnimationUrl != null) && song?.artworkUri != null
    val showCanvasArtBg = isBackdropEnabled && !lyricsVisible && song?.artworkUri != null
    val showDarkBg = showCanvasArtBg || (isBackdropEnabled && lyricsVisible && song?.artworkUri != null)
    val showBg = showDarkBg
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val useLightModeOnDarkBg = lyricsVisible && showDarkBg && !isDarkTheme

    val lyricsTextAlign = when (playerLyricsAlignment) {
        "START" -> TextAlign.Start; "END" -> TextAlign.End; else -> TextAlign.Center
    }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val shouldKeepScreenOn = keepScreenOnLyrics && lyricsVisible
    val activity = context as? android.app.Activity
    DisposableEffect(shouldKeepScreenOn) {
        if (shouldKeepScreenOn && activity != null) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    var showAlbumArt by remember { mutableStateOf(true) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var showBottomButtons by remember { mutableStateOf(true) }

    val localEntranceFraction = if (swipeToDismissEnabled) {
        var animateEntrance by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { animateEntrance = true }
        animateFloatAsState(targetValue = if (animateEntrance) 1f else 0f, animationSpec = spring(stiffness = Spring.StiffnessLow), label = "localEntranceFraction").value
    } else { expansionFraction }

    val line2Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(((localEntranceFraction - 0.1f) / 0.5f).coerceIn(0f, 1f))
    val line3Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(((localEntranceFraction - 0.2f) / 0.5f).coerceIn(0f, 1f))
    val line4Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(((localEntranceFraction - 0.3f) / 0.5f).coerceIn(0f, 1f))
    val line5Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(((localEntranceFraction - 0.4f) / 0.5f).coerceIn(0f, 1f))
    val line6Fraction = androidx.compose.animation.core.FastOutSlowInEasing.transform(((localEntranceFraction - 0.5f) / 0.5f).coerceIn(0f, 1f))

    val line2Alpha = line2Fraction
    val line2TranslationY = with(LocalDensity.current) { 32.dp.toPx() * (1f - line2Fraction) }
    val line3Alpha = line3Fraction
    val line4Alpha = line4Fraction
    val line5Alpha = line5Fraction
    val line6Alpha = line6Fraction

    val artworkClipShape = if (lyricsVisible) RoundedCornerShape(artworkCornerRadius) else playerArtworkShape

    // Extract dominant color from artwork for expressive ambient backdrop surfaces
    val needsDarkSurfaces = showDarkBg && !useLightModeOnDarkBg
    val darkScheme = remember { darkColorScheme() }
    val darkSurfaceHigh = darkScheme.surfaceContainerHigh
    val darkSurface = darkScheme.surfaceContainer
    val artworkColor = produceState<Color?>(null, song?.artworkUri, isBackdropEnabled) {
        if (!isBackdropEnabled || song?.artworkUri == null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                val artworkUri = song!!.artworkUri
                val inputStream = context.contentResolver.openInputStream(artworkUri)
                val opts = BitmapFactory.Options().apply { inSampleSize = 32 }
                val bitmap = BitmapFactory.decodeStream(inputStream, null, opts)
                inputStream?.close()
                if (bitmap != null) {
                    var r = 0L; var g = 0L; var b = 0L
                    val count = bitmap.width * bitmap.height
                    for (x in 0 until bitmap.width) {
                        for (y in 0 until bitmap.height) {
                            val p = bitmap.getPixel(x, y)
                            r += android.graphics.Color.red(p)
                            g += android.graphics.Color.green(p)
                            b += android.graphics.Color.blue(p)
                        }
                    }
                    bitmap.recycle()
                    Color(r.toFloat() / count / 255f, g.toFloat() / count / 255f, b.toFloat() / count / 255f)
                } else null
            } catch (_: Exception) { null }
        }
    }
    val artColor = artworkColor.value

    // Animated colors — derived from artwork when backdrop is active, solid M3 surfaces otherwise
    val ambientAlpha = if (isBackdropEnabled) playerAmbientBackdropIntensity else 1f
    val controlsContainerColor by animateColorAsState(
        targetValue = when {
            needsDarkSurfaces && artColor != null -> Color(
                red = artColor.red * 0.18f + 0.08f,
                green = artColor.green * 0.18f + 0.08f,
                blue = artColor.blue * 0.18f + 0.08f,
                alpha = ambientAlpha
            )
            needsDarkSurfaces -> darkSurfaceHigh.copy(alpha = ambientAlpha)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = ambientAlpha)
        },
        animationSpec = tween(600), label = "controlsContainerColor"
    )
    val outerBoxBgColor by animateColorAsState(
        targetValue = when {
            useLightModeOnDarkBg -> Color.White
            showDarkBg -> Color.Black
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(600), label = "outerBoxBgColor"
    )
    val onSurfaceColor by animateColorAsState(
        targetValue = if (useLightModeOnDarkBg) Color.Black else if (showDarkBg) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(400), label = "onSurfaceColor"
    )
    val onSurfaceVariantColor by animateColorAsState(
        targetValue = if (useLightModeOnDarkBg) Color.Black.copy(alpha = 0.65f) else if (showDarkBg) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(400), label = "onSurfaceVariantColor"
    )
    val surfaceContainerColor by animateColorAsState(
        targetValue = when {
            needsDarkSurfaces && artColor != null -> Color(
                red = artColor.red * 0.14f + 0.05f,
                green = artColor.green * 0.14f + 0.05f,
                blue = artColor.blue * 0.14f + 0.05f,
                alpha = ambientAlpha
            )
            needsDarkSurfaces -> darkSurface.copy(alpha = ambientAlpha)
            else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = ambientAlpha)
        },
        animationSpec = tween(400), label = "surfaceContainerColor"
    )
    val primaryColor by animateColorAsState(
        targetValue = if (useLightModeOnDarkBg) MaterialTheme.colorScheme.primary else if (showDarkBg) Color.White else MaterialTheme.colorScheme.primary,
        animationSpec = tween(400), label = "canvasPrimaryColor"
    )
    val clearArtworkAlpha by animateFloatAsState(
        targetValue = if (lyricsVisible) 0f else 1f, animationSpec = tween(500), label = "clearArtworkAlpha"
    )
    val lyricsOverlayAlpha by animateFloatAsState(
        targetValue = if (lyricsVisible && showDarkBg) 1f else 0f, animationSpec = tween(600), label = "lyricsOverlayAlpha"
    )

    LaunchedEffect(showDarkBg) {
        if (showDarkBg) showAlbumArt = false
        else { delay(400); showAlbumArt = true }
    }

    val configuration = LocalConfiguration.current
    val isCompactWidth = configuration.screenWidthDp < 360
    val isCompactHeight = configuration.screenHeightDp < 640
    val isTablet = configuration.screenWidthDp >= 600
    val isLandscapeTablet = isTablet && configuration.screenWidthDp > configuration.screenHeightDp
    val songTitle = song?.title ?: stringResource(R.string.unknown_track)
    val songArtist = song?.artist ?: stringResource(R.string.unknown_artist)
    val titleLength = songTitle.length
    val titleLetterSpacing = when {
        isCompactWidth || titleLength > 32 -> (-0.6).sp
        titleLength > 24 -> (-1.0).sp
        else -> (-1.5).sp
    }
    val titleTextStyle = when {
        isCompactWidth -> MaterialTheme.typography.headlineSmall
        isCompactHeight -> MaterialTheme.typography.headlineMedium
        titleLength > 32 -> MaterialTheme.typography.headlineSmall
        titleLength > 24 -> MaterialTheme.typography.headlineMedium
        else -> MaterialTheme.typography.displaySmall
    }.copy(fontWeight = FontWeight.Black, letterSpacing = titleLetterSpacing)

    val coroutineScope = rememberCoroutineScope()
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

    var swipeOffsetY by remember { mutableStateOf(0f) }
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
    } else { modifier }    // Debounce artwork transitions during crossfade (prevents back-and-forth animation)
    val debouncedArtworkUri = remember { mutableStateOf(song?.artworkUri) }
    LaunchedEffect(song?.id) {
        delay(250) // Small debounce to filter out crossfade transients
        debouncedArtworkUri.value = song?.artworkUri
    }

    // ====== Unified Background Layer (motion canvas style with smooth track transitions) ======
    val unifiedBackground = @Composable { modifier: Modifier ->
        val currentArtworkUri = debouncedArtworkUri.value
        if (currentArtworkUri != null) {
            val gc = if (useLightModeOnDarkBg) Color.White else Color.Black
            // Smooth crossfade when track artwork changes (1200ms motion canvas style)
            AnimatedContent(
                targetState = currentArtworkUri,
                transitionSpec = { fadeIn(tween(1200)).togetherWith(fadeOut(tween(800))) },
                label = "canvasArtworkTransition"
            ) { artworkUri ->
                if (artworkUri != null) {
                    Box(modifier = modifier.fillMaxSize()) {
                        // Layer 1: Full-screen blurred artwork
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(artworkUri).size(128, 128).build(),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(150.dp)
                        )
                        // Layer 2: Clear artwork with motion canvas gradient mask
                        // Phone: vertical gradient (top to bottom, fades at ~65%)
                        // Tablet landscape: horizontal gradient (left to right, artwork on left)
                        Box(
                            modifier = (if (isLandscapeTablet) Modifier.fillMaxWidth(0.5f).fillMaxHeight()
                                else Modifier.fillMaxWidth().fillMaxHeight(0.58f)).alpha(clearArtworkAlpha)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            val brush = if (isLandscapeTablet) Brush.horizontalGradient(
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
                                CanvasArtworkPlayer(canvasArtwork.animated, canvasArtwork.videoUrl, isPlaying, modifier = Modifier.fillMaxSize())
                            }
                        }
                        // Layer 3: Dynamic overlay for depth
                        Box(modifier = Modifier.fillMaxSize().background(
                            if (isLandscapeTablet) Brush.horizontalGradient(listOf(gc.copy(alpha = 0.03f), gc.copy(alpha = 0.35f)))
                            else Brush.verticalGradient(listOf(gc.copy(alpha = 0.05f), gc.copy(alpha = 0.4f)))))
                        // Layer 4: Lyrics overlay
                        Box(modifier = Modifier.fillMaxSize().alpha(lyricsOverlayAlpha).background(
                            if (isLandscapeTablet) Brush.horizontalGradient(
                                colors = listOf(gc.copy(alpha = 0.65f), gc.copy(alpha = 0.50f), gc.copy(alpha = 0.72f)))
                            else Brush.verticalGradient(
                                colors = listOf(gc.copy(alpha = 0.72f), gc.copy(alpha = 0.52f), gc.copy(alpha = 0.78f)))))
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(outerBoxBgColor)
    ) {
        // Background
        AnimatedVisibility(visible = showBg, enter = fadeIn(tween(600)), exit = fadeOut(tween(600))) {
            unifiedBackground(Modifier.fillMaxSize())
        }

        // Content Column with swipe dismiss
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().then(swipeMinimizeModifier)
        ) {
            // ====== Content area ======
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
                val smallControlSize = if (isPortraitLocal) {
                    val base = (containerMaxWidth * 0.14f)
                    if (isCompactHeight) base.coerceIn(32.dp, 42.dp) else base.coerceIn(36.dp, 56.dp)
                } else { if (isCompactHeight) 36.dp else 48.dp }
                var artworkOffsetX by remember { mutableStateOf(0f) }
                val artworkSwipeThreshold = 140f
                val artworkTranslationX by animateFloatAsState(
                    targetValue = artworkOffsetX.coerceIn(-200f, 200f),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "artworkTranslationX"
                )

                // Use fully qualified AnimatedVisibility to avoid ColumnScope receiver capture
                val artworkContent = @Composable { modifier: Modifier ->
                    androidx.compose.animation.AnimatedVisibility(visible = showAlbumArt || lyricsVisible,
                        enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }, modifier = modifier) {
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = line2Alpha; translationY = line2TranslationY }, contentAlignment = Alignment.Center) {
                            AnimatedContent(targetState = lyricsVisible, transitionSpec = {
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
                            }, label = "lyricsViewTransition", modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { tl ->
                                if (tl) {
                                    RhythmPlayerLyricsPanel(lyrics = lyrics, isLoadingLyrics = isLoadingLyrics,
                                        onlineOnlyLyrics = onlineOnlyLyrics, currentTimeMs = currentTimeMs,
                                        onLyricsSeek = onLyricsSeek, onTapLyricsView = onTapLyricsView,
                                        textSizeMultiplier = playerLyricsTextSize, onRetryLyrics = onRetryLyrics,
                                        onShowLyricsEditor = onShowLyricsEditor, onPickLyricsFile = onPickLyricsFile,
                                        showTranslation = showLyricsTranslation, showRomanization = showLyricsRomanization,
                                        textAlignment = lyricsTextAlign, textColor = onSurfaceColor, subtitleColor = onSurfaceVariantColor,
                                        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = if (isCompactWidth) 16.dp else 24.dp))
                                } else {
                                    Box(Modifier.padding(horizontal = if (isCompactWidth) 12.dp else 24.dp)
                                        .fillMaxSize(if (isTablet && !isLandscapeTablet) 0.55f else if (isCompactHeight) 0.78f else 0.88f).aspectRatio(1f)
                                        .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale; translationX = artworkTranslationX; shape = artworkClipShape; clip = true }
                                        .pointerInput(showLyrics, lyricsVisible) {
                                            detectTapGestures(onDoubleTap = { HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY); onPlayPause() },
                                                onTap = { if (showLyrics) { HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT); onToggleLyrics() } })
                                        }
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragEnd = {
                                                    if (artworkOffsetX < -artworkSwipeThreshold) { HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY); onSkipNext() }
                                                    else if (artworkOffsetX > artworkSwipeThreshold) { HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY); onSkipPrevious() }
                                                    artworkOffsetX = 0f
                                                }, onDragCancel = { artworkOffsetX = 0f },
                                                onDrag = { change, dragAmount -> change.consume(); artworkOffsetX += dragAmount.x })
                                        }) {
                                        M3ImageUtils.M3MediaImage(data = song?.artworkUri, contentDescription = stringResource(R.string.content_desc_album_artwork),
                                            modifier = Modifier.fillMaxSize(), shape = artworkClipShape, type = M3PlaceholderType.TRACK, name = song?.title, expressiveShape = playerArtworkShape)
                                        if (canvasArtwork?.preferredAnimationUrl != null) {
                                            CanvasArtworkPlayer(canvasArtwork.animated, canvasArtwork.videoUrl, isPlaying, modifier = Modifier.fillMaxSize().clip(artworkClipShape))
                                        }
                                        if (canvasLoading && canvasArtwork == null) {
                                            Box(Modifier.align(Alignment.TopEnd).padding(10.dp),
                                                contentAlignment = Alignment.Center) {
                                                M3CircularLoader(modifier = Modifier.size(16.dp), color = primaryColor, strokeWidth = 2.5f)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val controlsContent = @Composable {
                    androidx.compose.animation.AnimatedVisibility(visible = showPlayerControls, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                        Column(Modifier.fillMaxWidth().padding(start = if (isCompactWidth) 12.dp else 24.dp, end = if (isCompactWidth) 12.dp else 24.dp, bottom = if (isCompactHeight) 8.dp else 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.fillMaxWidth().graphicsLayer { alpha = line3Alpha }.padding(bottom = if (isCompactHeight) 8.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                    AutoScrollingTextOnDemand(text = songTitle, style = titleTextStyle.copy(color = onSurfaceColor),
                                        gradientEdgeColor = when { useLightModeOnDarkBg -> Color.White; showDarkBg -> Color.Black; else -> MaterialTheme.colorScheme.surface },
                                        modifier = Modifier.fillMaxWidth().clickable { onSongInfoClick() }, respectGlobalSetting = true)
                                    AutoScrollingTextOnDemand(text = songArtist, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, color = onSurfaceVariantColor),
                                        gradientEdgeColor = when { useLightModeOnDarkBg -> Color.White; showDarkBg -> Color.Black; else -> MaterialTheme.colorScheme.surface },
                                        modifier = Modifier.fillMaxWidth().clickable { onShowArtistBottomSheet() }, respectGlobalSetting = true)
                                }
                                Spacer(Modifier.width(16.dp))

                                RhythmGroupedButton(
                                    size = RhythmButtonSize.Small,
                                    isFillMaxWidth = false,
                                    modifier = Modifier.widthIn(max = 100.dp)
                                ) {
                                    RhythmButtonWeighted(
                                        onClick = onToggleLyrics,
                                        weight = 1f,
                                        isFirst = true,
                                        isLast = false,
                                        containerColor = controlsContainerColor,
                                        contentColor = primaryColor,
                                        icon = RhythmIcons.Player.Lyrics
                                    )
                                    RhythmButtonWeighted(
                                        onClick = onToggleFavorite,
                                        weight = 1f,
                                        isFirst = false,
                                        isLast = true,
                                        containerColor = controlsContainerColor,
                                        contentColor = primaryColor,
                                        icon = if (isFavorite) RhythmIcons.FavoriteFilled else RhythmIcons.Favorite
                                    )
                                }
                            }

                            Surface(shape = RoundedCornerShape(32.dp), color = controlsContainerColor,
                                modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(if (isCompactWidth) 12.dp else 20.dp)) {
                                    Row(Modifier.fillMaxWidth().graphicsLayer { alpha = line4Alpha },
                                        horizontalArrangement = Arrangement.spacedBy(if (isCompactWidth) 8.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RhythmPlayButton(
                                            isPlaying = isPlaying,
                                            showBuffering = showBuffering,
                                            onClick = onPlayPause,
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = ambientAlpha),
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                            size = controlButtonSize,
                                            modifier = Modifier.weight(1f)
                                        )
                                        RhythmControlButton(
                                            onClick = onSkipNext,
                                            shape = playerControlShape,
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = ambientAlpha),
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            size = controlButtonSize
                                        ) {
                                            Icon(RhythmIcons.Player.SkipNext, stringResource(R.string.cd_next_track), Modifier.size(controlButtonSize * 0.45f), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                    Spacer(Modifier.height(if (isCompactHeight) 8.dp else 16.dp))
                                    Row(Modifier.fillMaxWidth().graphicsLayer { alpha = line5Alpha },
                                        horizontalArrangement = Arrangement.spacedBy(if (isCompactWidth) 8.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RhythmControlButton(
                                            onClick = onSkipPrevious,
                                            shape = playerControlShape,
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = ambientAlpha),
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            size = controlButtonSize
                                        ) {
                                            Icon(RhythmIcons.Player.SkipPrevious, stringResource(R.string.cd_previous_track), Modifier.size(controlButtonSize * 0.45f), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                        val canSeek = (song?.duration ?: 0L) > 0L
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                            if (showBuffering) {
                                                M3LinearLoader(modifier = Modifier.fillMaxWidth().height(8.dp), color = primaryColor, trackColor = onSurfaceColor.copy(alpha = 0.18f))
                                            } else if (playerProgressStyle == "WAVY") {
                                                WaveSlider(value = if (isScrubbing && enhancedSeekingEnabled) scrubProgress else progressValue,
                                                    onValueChange = { if (canSeek && enhancedSeekingEnabled) { isScrubbing = true; scrubProgress = it } else if (canSeek) onSeek(it) },
                                                    onValueChangeFinished = { if (canSeek && enhancedSeekingEnabled && isScrubbing) { onSeek(scrubProgress); isScrubbing = false } },
                                                    modifier = Modifier.fillMaxWidth(), enabled = canSeek, isPlaying = isPlaying,
                                                    activeTrackColor = primaryColor, inactiveTrackColor = onSurfaceColor.copy(alpha = 0.2f), thumbColor = primaryColor)
                                            } else {
                                                val ps = try { ProgressStyle.valueOf(playerProgressStyle) } catch (e: IllegalArgumentException) { ProgressStyle.NORMAL }
                                                val ts = try { ThumbStyle.valueOf(playerProgressThumbStyle) } catch (e: IllegalArgumentException) { ThumbStyle.CIRCLE }
                                                Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
                                                    StyledProgressBar(progress = progressValue, style = ps, modifier = Modifier.fillMaxWidth(),
                                                        progressColor = primaryColor, trackColor = onSurfaceColor.copy(alpha = 0.2f),
                                                        height = when (ps) { ProgressStyle.THIN -> 2.dp; ProgressStyle.THICK -> 12.dp; else -> 8.dp },
                                                        isPlaying = isPlaying, showThumb = ts != ThumbStyle.NONE, thumbStyle = ts, thumbSize = 14.dp, waveAmplitudeWhenPlaying = 3.dp, waveLength = 60.dp)
                                                    Slider(value = progressValue, onValueChange = { onSeek(it) }, modifier = Modifier.fillMaxWidth(), enabled = canSeek,
                                                        colors = SliderDefaults.colors(thumbColor = Color.Transparent, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
                                                }
                                            }
                                            Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(currentTimeStr, style = MaterialTheme.typography.labelMedium, color = onSurfaceVariantColor)
                                                Text(totalTimeStr, style = MaterialTheme.typography.labelMedium, color = onSurfaceVariantColor)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val bottomButtonsContent = @Composable {
                    androidx.compose.animation.AnimatedVisibility(visible = showBottomButtons, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
                        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = line6Alpha }
                            .padding(start = if (isCompactWidth) 12.dp else 24.dp, end = if (isCompactWidth) 12.dp else 24.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(if (isCompactHeight) 12.dp else 16.dp))
                            RhythmGroupedButton(
                                size = RhythmButtonSize.Small
                            ) {
                                val deviceIcon = when {
                                    location?.id?.startsWith("bt_") == true -> RhythmIcons.BluetoothFilled
                                    location?.id == "wired_headset" -> RhythmIcons.HeadphonesFilled
                                    location?.id == "speaker" -> RhythmIcons.SpeakerFilled
                                    else -> RhythmIcons.Location
                                }
                                val queueLabel = if (queueTotal > 0) stringResource(R.string.player_queue_format, queuePosition, queueTotal) else stringResource(R.string.player_queue)

                                val deviceName = location?.name ?: "Output"
                                val deviceTextStyle = when {
                                    deviceName.length > 28 -> MaterialTheme.typography.labelLarge
                                    deviceName.length > 18 -> MaterialTheme.typography.titleSmall
                                    else -> MaterialTheme.typography.titleMedium
                                }.copy(fontWeight = FontWeight.Bold)

                                RhythmDetailActionButton(
                                    onClick = onDeviceClick,
                                    weight = 1f,
                                    height = 44.dp,
                                    isFirst = true,
                                    isLast = false,
                                    type = RhythmButtonType.Tonal,
                                    icon = deviceIcon,
                                    iconSize = 20.dp,
                                    text = deviceName,
                                    textStyle = deviceTextStyle,
                                    gradientEdgeColor = surfaceContainerColor,
                                    respectMarqueeGlobalSetting = false,
                                    contentDescription = stringResource(R.string.expressiveplayerscreen_device),
                                    containerColor = surfaceContainerColor,
                                    contentColor = primaryColor
                                )
                                RhythmDetailActionButton(
                                    onClick = onQueueClick,
                                    weight = 1f,
                                    height = 44.dp,
                                    isFirst = false,
                                    isLast = false,
                                    type = RhythmButtonType.Tonal,
                                    icon = RhythmIcons.Queue,
                                    iconSize = 20.dp,
                                    text = queueLabel,
                                    contentDescription = stringResource(R.string.bottomsheet_queue),
                                    containerColor = surfaceContainerColor,
                                    contentColor = primaryColor
                                )
                                RhythmDetailActionButton(
                                    onClick = onMoreClick,
                                    weight = 0.3f,
                                    height = 44.dp,
                                    isFirst = false,
                                    isLast = true,
                                    type = RhythmButtonType.Tonal,
                                    icon = RhythmIcons.More,
                                    iconSize = 22.dp,
                                    contentDescription = stringResource(R.string.expressiveplayerscreen_more),
                                    containerColor = surfaceContainerColor,
                                    contentColor = primaryColor
                                )
                            }
                        }
                    }
                }

                if (isLandscapeTablet) {
                    Row(Modifier.fillMaxSize().navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1.1f).fillMaxHeight().padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box(Modifier.fillMaxSize()) { artworkContent(Modifier.fillMaxSize()) }
                        }
                        Column(Modifier.weight(0.9f).fillMaxHeight().padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box { controlsContent() }
                            Box { bottomButtonsContent() }
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (isTablet && !isLandscapeTablet) Arrangement.Center else Arrangement.Bottom
                    ) {
                        Box(Modifier.weight(1f)) {
                            artworkContent(Modifier.fillMaxSize().padding(bottom = if (isCompactHeight) 12.dp else if (isTablet && !isLandscapeTablet) 32.dp else 24.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isTablet && !isLandscapeTablet) Modifier.padding(horizontal = 48.dp).padding(bottom = 24.dp) else Modifier)
                        ) {
                            controlsContent()
                        }
                        Box(
                            modifier = Modifier
                                .then(if (isTablet && !isLandscapeTablet) Modifier.padding(horizontal = 48.dp) else Modifier)
                        ) {
                            bottomButtonsContent()
                        }
                    }
                }

                // Audio quality icon at top-right (like album screen but positioned in corner)
                if (song != null) {
                    AudioQualityIcon(
                        song = song,
                        iconSize = 40.dp,
                        padding = 6.dp,
                        tint = when {
                            useLightModeOnDarkBg -> Color.Black
                            showDarkBg -> Color.White
                            else -> null
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RhythmPlayerLyricsPanel(
    lyrics: LyricsData?, isLoadingLyrics: Boolean, onlineOnlyLyrics: Boolean,
    currentTimeMs: Long, onLyricsSeek: ((Long) -> Unit)?, onTapLyricsView: (() -> Unit)? = null,
    textSizeMultiplier: Float, onRetryLyrics: () -> Unit, onShowLyricsEditor: () -> Unit, onPickLyricsFile: () -> Unit,
    showTranslation: Boolean, showRomanization: Boolean, textAlignment: TextAlign,
    textColor: Color = MaterialTheme.colorScheme.onSurface, subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hasLyrics = lyrics?.hasLyrics() == true && lyrics.isErrorMessage().not()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            isLoadingLyrics -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
                    ContainedLoadingIndicator()
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
                                onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY); onRetryLyrics() },
                                weight = 1f,
                                isFirst = true,
                                isLast = false,
                                icon = RhythmIcons.Refresh,
                                text = stringResource(R.string.updates_retry)
                            )
                            RhythmButtonWeighted(
                                onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY); onShowLyricsEditor() },
                                weight = 1f,
                                isFirst = false,
                                isLast = false,
                                icon = RhythmIcons.Player.Lyrics,
                                text = stringResource(R.string.button_add)
                            )
                            RhythmButtonWeighted(
                                onClick = { HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY); onPickLyricsFile() },
                                weight = 1f,
                                isFirst = false,
                                isLast = true,
                                icon = MaterialSymbolIcon("file_open", filled = true),
                                text = stringResource(R.string.expressiveplayerscreen_load)
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
                        textAlignment = textAlignment, showTranslation = showTranslation, showRomanization = showRomanization)
                } else {
                    val lyricsText = remember(lyrics) { lyrics.getBestLyrics() ?: "" }
                    val filteredText = remember(lyricsText, showTranslation, showRomanization) { filterPlainLyricsByPreference(lyricsText, showTranslation, showRomanization) }
                    val likelySynced = remember(lyricsText) { Regex("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{0,3})?]").containsMatchIn(lyricsText) }
                    val parsedLyrics by produceState<List<chromahub.rhythm.app.util.LyricLine>?>(if (likelySynced) null else emptyList(), lyricsText, likelySynced) {
                        value = if (!likelySynced) emptyList() else withContext(Dispatchers.Default) { chromahub.rhythm.app.util.LyricsParser.parseLyrics(lyricsText) }
                    }
                    if (parsedLyrics == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            M3CircularLoader(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), strokeWidth = 2f)
                        }
                    } else if (parsedLyrics?.isNotEmpty() == true) {
                        SyncedLyricsView(lyricsText, currentTimeMs, Modifier.fillMaxSize(), parsedLyricsInput = parsedLyrics,
                            onSeek = onLyricsSeek, onTapLyricsView = onTapLyricsView, showTranslation = showTranslation,
                            showRomanization = showRomanization, lyricsSource = lyrics.source, textSizeMultiplier = textSizeMultiplier, textAlignment = textAlignment)
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
    }
}

private fun filterPlainLyricsByPreference(rawLyrics: String, showTranslation: Boolean, showRomanization: Boolean): String {
    if (rawLyrics.isBlank() || (showTranslation && showRomanization)) return rawLyrics
    val filteredLines = mutableListOf<String>()
    var prevNonAscii = false
    rawLyrics.lineSequence().forEach { line ->
        val t = line.trim()
        if (t.isEmpty()) { filteredLines += line; return@forEach }
        val isBracketTrans = t.startsWith("(") && t.endsWith(")") && t.length > 2
        val isBracketRoman = t.startsWith("[") && t.endsWith("]") && t.length > 2
        val hasLetters = t.any { it.isLetterOrDigit() }
        val isAscii = t.all { it.code <= 127 || it.isWhitespace() }
        if ((!showTranslation && isBracketTrans) || (!showRomanization && (isBracketRoman || (hasLetters && isAscii && prevNonAscii)))) return@forEach
        filteredLines += line
        if (!isBracketTrans && !isBracketRoman) prevNonAscii = t.any { it.code > 127 }
    }
    return filteredLines.joinToString("\n")
}
