/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import chromahub.rhythm.app.R
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.SliderState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.graphics.Color
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.lyrics.ActiveLyricLine
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.common.AutoScrollingTextOnDemand
import chromahub.rhythm.app.shared.presentation.components.common.ButtonGroupStyle
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveGroupButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressivePlayerControlGroup
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.common.RhythmDetailActionButton
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeTarget
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShapeFor
import chromahub.rhythm.app.shared.presentation.components.common.WaveSlider
import chromahub.rhythm.app.shared.presentation.components.common.M3CircularLoader
import chromahub.rhythm.app.ui.theme.RhythmTheme
import chromahub.rhythm.app.util.MediaUtils
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.ThemeViewModel
import chromahub.rhythm.app.activities.MainActivity
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalHapticFeedback
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.features.local.presentation.navigation.Screen
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingsRoutes
import java.util.Locale

/**
 * Compact bottom sheet activity for playing external audio files.
 * Provides a minimal, focused UI without jumping to the main app.
 */
class ExternalPlaybackActivity : ComponentActivity() {
    private val TAG = "ExternalPlaybackActivity"
    private val musicViewModel: MusicViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()
    private var playbackRequestJob: Job? = null
    private var lastHandledUri: String? = null
    private var openedInRhythm = false

    private fun handleDismiss() {
        val appSettings = chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(this)
        val preview = previewController
        if (preview != null && !appSettings.externalPlayerContinuePlaybackOnClose.value && !openedInRhythm) {
            preview.pause()
        }
        finish()
    }

    private var previewController: chromahub.rhythm.app.infrastructure.service.player.ExternalPreviewPlayerController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val preview = chromahub.rhythm.app.infrastructure.service.player.ExternalPreviewPlayerController(
            context = applicationContext,
            scope = lifecycleScope
        )
        previewController = preview

        handleIntent(intent)

        val appSettings = chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(this)

        setContent {
            val useSystemTheme by themeViewModel.useSystemTheme.collectAsState()
            val darkMode by themeViewModel.darkMode.collectAsState()
            val useDynamicColors by themeViewModel.useDynamicColors.collectAsState()
            val customFont by appSettings.customFont.collectAsState()
            val fontSource by appSettings.fontSource.collectAsState()
            val customFontPath by appSettings.customFontPath.collectAsState()
            val amoledTheme by appSettings.amoledTheme.collectAsState()
            val customColorScheme by appSettings.customColorScheme.collectAsState()
            val colorSource by appSettings.colorSource.collectAsState()
            val extractedAlbumColors by appSettings.extractedAlbumColors.collectAsState()
            val expressiveColors by appSettings.expressiveColors.collectAsState()
            val themeIntensity by appSettings.themeIntensity.collectAsState()
            val isDarkTheme = if (useSystemTheme) isSystemInDarkTheme() else darkMode

            RhythmTheme(
                darkTheme = isDarkTheme,
                amoledTheme = amoledTheme && isDarkTheme,
                dynamicColor = useDynamicColors,
                customColorScheme = customColorScheme,
                customFont = customFont,
                fontSource = fontSource,
                customFontPath = customFontPath,
                colorSource = colorSource,
                extractedAlbumColorsJson = extractedAlbumColors,
                expressiveColors = expressiveColors,
                themeIntensity = themeIntensity
            ) {
                ExternalPlaybackBottomSheet(
                    previewController = preview,
                    onDismiss = { handleDismiss() },
                    onOpenFullPlayer = {
                        val handoff = preview.createOpenInAppHandoff()
                        openedInRhythm = true
                        preview.pause()
                        val handoffSong = handoff.song
                        if (handoff.shouldHandOffToMainService && handoffSong != null) {
                            musicViewModel.connectToMediaService()
                            musicViewModel.playExternalAudioFile(handoffSong, handoff.startPositionMs)
                        }
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                        }
                        startActivity(intent)
                        finish()
                    },
                    onOpenSettings = {
                        appSettings.setInitialSettingsSubroute(SettingsRoutes.EXTERNAL_PLAYER_CUSTOMIZATION)
                        appSettings.requestTopLevelRoute("settings")
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            putExtra(MainActivity.EXTRA_OPEN_SETTINGS_SUBROUTE, SettingsRoutes.EXTERNAL_PLAYER_CUSTOMIZATION)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        playbackRequestJob?.cancel()
        val appSettings = chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(this)
        val preview = previewController
        if (preview != null) {
            val closeHandoff = preview.createCloseHandoff(
                continueOnClose = appSettings.externalPlayerContinuePlaybackOnClose.value,
                alreadyOpenedInRhythm = openedInRhythm
            )
            val handoffSong = closeHandoff.song
            if (closeHandoff.shouldHandOffToMainService && handoffSong != null) {
                musicViewModel.connectToMediaService()
                musicViewModel.playExternalAudioFile(handoffSong, closeHandoff.startPositionMs)
            }
            preview.release()
            previewController = null
        }
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
        Log.d(TAG, "handleIntent called, action=${intent.action}")
        if (intent.action != Intent.ACTION_VIEW) {
            Log.w(TAG, "Unsupported intent action for external playback: ${intent.action}")
            return
        }

        val uri = intent.data ?: run {
            Log.e(TAG, "Intent data is null")
            Toast.makeText(this, R.string.externalplaybackactivity_unable_to_open_audio, Toast.LENGTH_SHORT).show()
            return
        }

        if (!isValidAudioUri(uri)) {
            Log.w(TAG, "Rejected non-audio or unreadable URI: $uri")
            Toast.makeText(this, R.string.externalplaybackactivity_unsupported_or_unreadable_audio, Toast.LENGTH_SHORT).show()
            return
        }

        val uriKey = uri.toString()
        if (playbackRequestJob?.isActive == true && lastHandledUri == uriKey) {
            Log.d(TAG, "Duplicate playback request ignored for URI: $uri")
            return
        }

        lastHandledUri = uriKey
        playbackRequestJob?.cancel()
        playbackRequestJob = lifecycleScope.launch {
            playExternalFile(uri)
        }
    }

    private fun isValidAudioUri(uri: Uri): Boolean {
        return try {
            val mimeType = MediaUtils.getMimeType(applicationContext, uri)
            mimeType?.startsWith("audio/") == true ||
                mimeType?.contains("matroska", ignoreCase = true) == true ||
                uri.toString().let { uriStr ->
                    uriStr.endsWith(".mp3", ignoreCase = true) ||
                        uriStr.endsWith(".m4a", ignoreCase = true) ||
                        uriStr.endsWith(".mp4", ignoreCase = true) ||
                        uriStr.endsWith(".alac", ignoreCase = true) ||
                        uriStr.endsWith(".wav", ignoreCase = true) ||
                        uriStr.endsWith(".ogg", ignoreCase = true) ||
                        uriStr.endsWith(".flac", ignoreCase = true) ||
                        uriStr.endsWith(".aac", ignoreCase = true) ||
                        uriStr.endsWith(".opus", ignoreCase = true) ||
                        uriStr.endsWith(".opa", ignoreCase = true) ||
                        uriStr.endsWith(".wma", ignoreCase = true) ||
                        uriStr.endsWith(".mkv", ignoreCase = true) ||
                        uriStr.endsWith(".mka", ignoreCase = true) ||
                        uriStr.endsWith(".ac3", ignoreCase = true) ||
                        uriStr.endsWith(".eac", ignoreCase = true) ||
                        uriStr.endsWith(".eac3", ignoreCase = true) ||
                        uriStr.endsWith(".ac4", ignoreCase = true) ||
                        uriStr.endsWith(".mhm", ignoreCase = true) ||
                        uriStr.endsWith(".mhm1", ignoreCase = true) ||
                        uriStr.endsWith(".oga", ignoreCase = true) ||
                        uriStr.endsWith(".mid", ignoreCase = true) ||
                        uriStr.endsWith(".midi", ignoreCase = true) ||
                        uriStr.endsWith(".adts", ignoreCase = true) ||
                        uriStr.endsWith(".m4b", ignoreCase = true) ||
                        uriStr.endsWith(".ape", ignoreCase = true) ||
                        uriStr.endsWith(".wv", ignoreCase = true) ||
                        uriStr.endsWith(".tta", ignoreCase = true) ||
                        uriStr.endsWith(".tak", ignoreCase = true) ||
                        uriStr.endsWith(".aiff", ignoreCase = true) ||
                        uriStr.endsWith(".aif", ignoreCase = true) ||
                        uriStr.endsWith(".dsf", ignoreCase = true) ||
                        uriStr.endsWith(".dff", ignoreCase = true) ||
                        uriStr.endsWith(".dsd", ignoreCase = true) ||
                        uriStr.endsWith(".dts", ignoreCase = true) ||
                        uriStr.endsWith(".dtshd", ignoreCase = true) ||
                        uriStr.endsWith(".dtsx", ignoreCase = true) ||
                        uriStr.endsWith(".truehd", ignoreCase = true) ||
                        uriStr.endsWith(".thd", ignoreCase = true) ||
                        uriStr.endsWith(".mlp", ignoreCase = true)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating external URI: $uri", e)
            false
        }
    }

    private suspend fun playExternalFile(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "playExternalFile: Starting, URI=$uri")

            // Check if file exists in MediaStore
            val existingSong = MediaUtils.findSongInMediaStore(this@ExternalPlaybackActivity, uri)

            val targetSong = if (existingSong != null) {
                Log.d(TAG, "Found existing song in MediaStore: ${existingSong.title}")
                existingSong
            } else {
                Log.d(TAG, "Extracting metadata from external file")
                MediaUtils.extractMetadataFromUri(this@ExternalPlaybackActivity, uri)
            }

            Log.d(TAG, "Resolved external song: title=${targetSong.title}, artist=${targetSong.artist}, uri=${targetSong.uri}")

            withContext(Dispatchers.Main) {
                val preview = previewController
                if (preview != null) {
                    preview.loadSong(
                        song = targetSong,
                        fetchLyrics = { musicViewModel.fetchLyricsForPreviewSong(it) },
                        autoPlay = true
                    )
                    delay(500)
                    Log.d(
                        TAG,
                        "After isolated preview start - isPlaying=${preview.isPlaying.value}, currentSong=${preview.currentSong.value?.title}"
                    )
                } else {
                    Log.w(TAG, "Preview controller unavailable; falling back to MusicViewModel")
                    musicViewModel.connectToMediaService()
                    musicViewModel.playExternalAudioFile(targetSong)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing external file", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ExternalPlaybackActivity, R.string.externalplaybackactivity_failed_to_play_audio, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalPlaybackBottomSheet(
    previewController: chromahub.rhythm.app.infrastructure.service.player.ExternalPreviewPlayerController,
    onDismiss: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val appSettings = remember { chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context) }
    val haptics = LocalHapticFeedback.current
    val hapticSetting by appSettings.externalPlayerHapticFeedback.collectAsState()
    val globalHaptics by appSettings.isHapticEnabled.collectAsState()
    val hapticEnabled = hapticSetting && globalHaptics
    val showLyrics by appSettings.externalPlayerShowLyrics.collectAsState()
    val showAudioDetails by appSettings.externalPlayerShowAudioDetails.collectAsState()
    val showWaveSeeker by appSettings.externalPlayerShowWaveSeeker.collectAsState()
    val autoCloseOnFinish by appSettings.externalPlayerAutoCloseOnFinish.collectAsState()

    val currentSong by previewController.currentSong.collectAsState()
    val isPlaying by previewController.isPlaying.collectAsState()
    val isBuffering by previewController.isBuffering.collectAsState()
    val isSeeking by previewController.isSeeking.collectAsState()
    val progress by previewController.progress.collectAsState()
    val lyrics by previewController.currentLyrics.collectAsState()
    val lyricsTimeOffset = 0L

    var showContent by remember { mutableStateOf(false) }
    var awaitingSong by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val scope = rememberCoroutineScope()
    var isDismissing by remember { mutableStateOf(false) }

    val dismissSmoothly: () -> Unit = {
        if (!isDismissing) {
            isDismissing = true
            scope.launch {
                try {
                    sheetState.hide()
                } finally {
                    onDismiss()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    LaunchedEffect(progress, isScrubbing) {
        if (!isScrubbing) {
            scrubProgress = progress.coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(progress, isPlaying, autoCloseOnFinish) {
        if (autoCloseOnFinish && progress >= 0.995f && !isPlaying && !isScrubbing) {
            dismissSmoothly()
        }
    }

    LaunchedEffect(currentSong, isBuffering, isSeeking) {
        if (currentSong != null) {
            awaitingSong = false
            return@LaunchedEffect
        }

        if (!awaitingSong && !isBuffering && !isSeeking) {
            // Avoid dismissing on transient nulls during controller/service churn.
            delay(1200)
            if (currentSong == null && !isBuffering && !isSeeking) {
                dismissSmoothly()
            }
        }
    }
    RhythmAdaptiveModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                                0.4f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                                0.7f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                                1f to MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.rhythm_splash_logo),
                        contentDescription = stringResource(R.string.updates_rhythm_logo_cd),
                        modifier = Modifier.size(50.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.cd_rhythm_splash),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveButtonGroup(
                        style = ButtonGroupStyle.Tonal
                    ) {
                        ExpressiveGroupButton(
                            onClick = {
                                if (hapticEnabled) {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                }
                                onOpenSettings()
                            },
                            isStart = true,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(
                                icon = MaterialSymbolIcon("settings", filled = true),
                                contentDescription = stringResource(R.string.settings_external_player_customization),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        ExpressiveGroupButton(
                            onClick = {
                                val song = currentSong ?: return@ExpressiveGroupButton
                                if (hapticEnabled) {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.MEDIUM)
                                }
                                try {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "audio/*"
                                        putExtra(Intent.EXTRA_STREAM, song.uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, song.title))
                                } catch (e: Exception) {
                                    Toast.makeText(context, R.string.materialplayerscreen_unable_to_share_file, Toast.LENGTH_SHORT).show()
                                }
                            },
                            isEnd = true,
                            enabled = currentSong != null,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(
                                icon = MaterialSymbolIcon("share", filled = true),
                                contentDescription = stringResource(R.string.action_share),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                if (currentSong == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        M3CircularLoader(modifier = Modifier.size(48.dp), strokeWidth = 4f)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        val artworkShape = rememberExpressiveShapeFor(
                            target = ExpressiveShapeTarget.PLAYER_ART,
                            fallbackShape = RoundedCornerShape(24.dp)
                        )
                        val artUri = currentSong?.artworkUri
                        if (artUri != null) {
                            AsyncImage(
                                model = artUri,
                                contentDescription = currentSong?.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(artworkShape)
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(artworkShape),
                                shape = artworkShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_music_note),
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            AutoScrollingTextOnDemand(
                                text = currentSong?.title ?: "Unknown Title",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            AutoScrollingTextOnDemand(
                                text = currentSong?.artist ?: "Unknown Artist",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            if (showAudioDetails) {
                                Spacer(modifier = Modifier.height(4.dp))
                                AutoScrollingTextOnDemand(
                                    text = currentSong?.album ?: "Unknown album",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    ),
                                    gradientEdgeColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }
                    }
                }

                if (showLyrics) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ActiveLyricLine(
                            lyrics = lyrics,
                            playbackTimeMs = (
                                (currentSong?.duration ?: 0L) * (if (isScrubbing) scrubProgress else progress)
                                ).toLong() + lyricsTimeOffset,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                currentSong?.let { song ->
                        val displayedProgress = if (isScrubbing) scrubProgress else progress

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            if (showWaveSeeker) {
                                WaveSlider(
                                    value = displayedProgress,
                                    onValueChange = { newProgress ->
                                        isScrubbing = true
                                        scrubProgress = newProgress.coerceIn(0f, 1f)
                                        if (hapticEnabled) {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        }
                                    },
                                    onValueChangeFinished = {
                                        val targetPositionMs = (song.duration * scrubProgress).toLong()
                                        val currentPositionMs =
                                            (song.duration * progress.coerceIn(0f, 1f)).toLong()
                                        if (abs(targetPositionMs - currentPositionMs) >= 750L) {
                                            previewController.seekTo(targetPositionMs)
                                        }
                                        if (hapticEnabled) {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        }
                                        isScrubbing = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    isPlaying = isPlaying
                                )
                            } else {
                                val seekSliderState = remember { SliderState(value = displayedProgress) }
                                seekSliderState.value = displayedProgress
                                Slider(
                                    state = seekSliderState,
                                    onValueChange = { newProgress ->
                                        isScrubbing = true
                                        scrubProgress = newProgress.coerceIn(0f, 1f)
                                        if (hapticEnabled) {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        }
                                    },
                                    onValueChangeFinished = {
                                        val targetPositionMs = (song.duration * scrubProgress).toLong()
                                        val currentPositionMs =
                                            (song.duration * progress.coerceIn(0f, 1f)).toLong()
                                        if (abs(targetPositionMs - currentPositionMs) >= 750L) {
                                            previewController.seekTo(targetPositionMs)
                                        }
                                        if (hapticEnabled) {
                                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                        }
                                        isScrubbing = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime((song.duration * displayedProgress).toLong()),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = formatTime(song.duration),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                }

                Spacer(modifier = Modifier.height(28.dp))

                ExpressivePlayerControlGroup(
                    isPlaying = isPlaying,
                    showSeekButtons = false,
                    onPrevious = {
                        if (hapticEnabled) {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        }
                        previewController.restartOrZero()
                    },
                    onPlayPause = {
                        if (hapticEnabled) {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        }
                        if (!isBuffering) {
                            previewController.togglePlayPause()
                        }
                    },
                    onNext = {
                        if (hapticEnabled) {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        }
                        previewController.restartOrZero()
                    },
                    onSeekBack = {},
                    onSeekForward = {},
                    isExtraSmallWidth = false,
                    isCompactWidth = false,
                    isCompactHeight = false,
                    isLoading = isBuffering,
                    showContainer = false,
                    sizeScale = 1.15f,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RhythmDetailActionButton(
                        onClick = {
                            if (hapticEnabled) {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.MEDIUM)
                            }
                            onOpenFullPlayer()
                        },
                        weight = 1f,
                        height = 52.dp,
                        isFirst = true,
                        isLast = false,
                        type = RhythmButtonType.Tonal,
                        icon = MaterialSymbolIcon("open_in_new", filled = true),
                        iconSize = 18.dp,
                        text = stringResource(R.string.externalplaybackactivity_open),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    RhythmDetailActionButton(
                        onClick = {
                            if (hapticEnabled) {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            }
                            dismissSmoothly()
                        },
                        weight = 1f,
                        height = 52.dp,
                        isFirst = false,
                        isLast = true,
                        type = RhythmButtonType.Tonal,
                        icon = MaterialSymbolIcon("close", filled = true),
                        iconSize = 18.dp,
                        text = stringResource(R.string.dialog_close),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}
