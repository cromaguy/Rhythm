/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.activities

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.navigation.RhythmNavigation
import chromahub.rhythm.app.ui.theme.RhythmTheme
import chromahub.rhythm.app.ui.theme.festive.FestiveOverlayFromSettings
import chromahub.rhythm.app.shared.presentation.viewmodel.ThemeViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.AppUpdaterViewModel
import chromahub.rhythm.app.util.CrashReporter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.animation.Crossfade
import androidx.compose.ui.text.font.FontWeight
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import chromahub.rhythm.app.shared.data.model.ScanPhase
import chromahub.rhythm.app.shared.presentation.components.common.RhythmWavyProgressLoader
import chromahub.rhythm.app.shared.presentation.components.icons.Icon as RhythmIcon
import kotlin.math.abs
import android.provider.Settings
import chromahub.rhythm.app.util.ServiceStartUtils
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import chromahub.rhythm.app.shared.data.model.AppSettings
import java.util.Locale
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import chromahub.rhythm.app.shared.presentation.components.common.M3LinearLoader
import chromahub.rhythm.app.shared.presentation.components.common.M3FourColorCircularLoader
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType // Import HapticFeedbackType
import androidx.compose.material3.ButtonDefaults
import chromahub.rhythm.app.shared.presentation.components.common.InitializationLoader
import chromahub.rhythm.app.shared.presentation.components.PermissionHandler
import chromahub.rhythm.app.shared.presentation.components.dialogs.BetaProgramPopup
import chromahub.rhythm.app.shared.presentation.components.dialogs.TrackCorruptionDialog
import chromahub.rhythm.app.features.local.presentation.screens.OnboardingScreen
import chromahub.rhythm.app.features.local.presentation.screens.onboarding.OnboardingStep
import chromahub.rhythm.app.features.local.presentation.screens.onboarding.PermissionScreenState
import androidx.core.content.pm.ShortcutManagerCompat
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingSyncStage
import chromahub.rhythm.app.core.domain.model.SourceType

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val musicViewModel: MusicViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()
    private val appUpdaterViewModel: AppUpdaterViewModel by viewModels() // Inject AppUpdaterViewModel
    private val streamingMusicViewModel: StreamingMusicViewModel by viewModels()
    private lateinit var appSettings: AppSettings // Declare AppSettings
    
    companion object {
        const val DISPLAY_AUDIO_EFFECT_CONTROL_PANEL_REQUEST = 1002
        const val EXTRA_OPEN_PLAYER = "OPEN_PLAYER"
        const val EXTRA_OPEN_QUEUE = "OPEN_QUEUE"
    }
    
    // Track coroutine jobs to prevent memory leaks
    private val lifecycleScopeJobs = mutableListOf<kotlinx.coroutines.Job>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appSettings = AppSettings.getInstance(applicationContext) // AppSettings already initialized in Application
        
        // We'll delay intent handling until after initialization
        val startupIntent = intent
        
        setContent {
            val useSystemTheme by themeViewModel.useSystemTheme.collectAsState()
            val darkMode by themeViewModel.darkMode.collectAsState()
            val amoledTheme by appSettings.amoledTheme.collectAsState()
            val useDynamicColors by themeViewModel.useDynamicColors.collectAsState()
            val customColorScheme by appSettings.customColorScheme.collectAsState()
            val customFont by appSettings.customFont.collectAsState()
            val fontSource by appSettings.fontSource.collectAsState()
            val customFontPath by appSettings.customFontPath.collectAsState()
            val colorSource by appSettings.colorSource.collectAsState()
            val extractedAlbumColors by appSettings.extractedAlbumColors.collectAsState()
            val appMode by appSettings.appMode.collectAsState()
            
            // Determine the theme based on settings
            val isDarkTheme = if (useSystemTheme) {
                // Use system default
                androidx.compose.foundation.isSystemInDarkTheme()
            } else {
                // Use app setting
                darkMode
            }
            
            RhythmTheme(
                darkTheme = isDarkTheme,
                amoledTheme = amoledTheme && isDarkTheme,
                // Use dynamic colors (Monet) when system theme is enabled
                dynamicColor = useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                customColorScheme = customColorScheme,
                customFont = customFont,
                fontSource = fontSource,
                customFontPath = customFontPath,
                colorSource = colorSource,
                extractedAlbumColorsJson = extractedAlbumColors
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FestiveOverlayFromSettings {
                        // Show the initialization loader first, then transition to the app
                        val hasShownBetaPopup by appSettings.hasShownBetaPopup.collectAsState()
                        var showBetaPopup by remember { mutableStateOf(false) }
                        val currentAppVersion by appUpdaterViewModel.currentVersion.collectAsState() // Observe current version
                        val updateChannel by appUpdaterViewModel.updateChannel.collectAsState() // Observe update channel
                        var showMediaScanLoader by rememberSaveable { mutableStateOf(false) }

                    // State for permission handling and app initialization.
                    // rememberSaveable so these survive configuration changes (e.g. system theme toggle)
                    // which recreate the Activity but must not re-show the loader or re-enter loading.
                    var shouldShowSettingsRedirect by remember { mutableStateOf(false) }
                    var isLoading by rememberSaveable { mutableStateOf(true) }
                    var isInitializingApp by rememberSaveable { mutableStateOf(false) }
                    val lastCrashLog by appSettings.lastCrashLog.collectAsState() // Observe last crash log

                    val pendingDeleteRequest by musicViewModel.pendingDeleteRequest.collectAsState()
                    val deletePermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartIntentSenderForResult()
                    ) { result ->
                        if (result.resultCode == RESULT_OK) {
                            val pending = musicViewModel.pendingDeleteRequest.value
                            if (pending != null) {
                                musicViewModel.completeSongDeletion(pending.song)
                            }
                        } else {
                            musicViewModel.cancelPendingDelete()
                        }
                    }

                    LaunchedEffect(pendingDeleteRequest) {
                        pendingDeleteRequest?.let { pending ->
                            try {
                                val intentSenderRequest = IntentSenderRequest.Builder(
                                    pending.intentSender
                                ).build()
                                deletePermissionLauncher.launch(intentSenderRequest)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Failed to launch delete permission request", e)
                                musicViewModel.cancelPendingDelete()
                            }
                        }
                    }

                    // Runs once the music library has finished initializing.
                    var hasHandledStartupIntents by rememberSaveable { mutableStateOf(false) }
                    fun onInitializationComplete() {
                        isLoading = false // Stop initial loading after initialization

                        // Show beta popup if it hasn't been shown before AND the current version is a pre-release
                        if (!hasShownBetaPopup && currentAppVersion.isPreRelease) {
                            showBetaPopup = true
                        }

                        // Check for previous crash logs
                        lastCrashLog?.let {
                            // CrashActivity is now responsible for showing the dialog
                        }
                        
                        // Handle startup intents once, after the library is ready.
                        if (!hasHandledStartupIntents) {
                            hasHandledStartupIntents = true
                            val shouldHandleStartupIntent = startupIntent?.let {
                                (it.action == Intent.ACTION_VIEW && it.data != null) ||
                                    it.action == "chromahub.rhythm.app.action.SHORTCUT_PLAY_PAUSE" ||
                                    it.action == "chromahub.rhythm.app.action.SHORTCUT_SKIP_NEXT" ||
                                    it.action == "chromahub.rhythm.app.action.SHORTCUT_SKIP_PREVIOUS" ||
                                    it.getBooleanExtra(EXTRA_OPEN_PLAYER, false) ||
                                    it.getBooleanExtra(EXTRA_OPEN_QUEUE, false)
                            } == true

                            if (shouldHandleStartupIntent) {
                                // Small delay to ensure view models are ready, then handle intent
                                val startupIntentJob = lifecycleScope.launch {
                                    kotlinx.coroutines.delay(500)
                                    handleIntent(startupIntent)
                                }
                                lifecycleScopeJobs.add(startupIntentJob)
                            }
                        }
                    }

                    // Show the initialization loader until the music library is ready.
                    val isInitialized by musicViewModel.isInitialized.collectAsState()
                    LaunchedEffect(isInitialized) {
                        if (isInitialized) {
                            onInitializationComplete()
                        }
                    }
                    val scanProgress by musicViewModel.scanProgress.collectAsState()
                    val isStreamingMode = appMode == "STREAMING"
                    val streamingSyncProgress by streamingMusicViewModel.syncProgress.collectAsState()
                    val streamingCurrentService by streamingMusicViewModel.currentService.collectAsState()
                    val streamingServiceName = remember(streamingCurrentService) {
                        streamingMusicViewModel.getSourceTypeName(streamingCurrentService)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = isInitialized,
                            enter = fadeIn(animationSpec = tween(1000, easing = androidx.compose.animation.core.EaseOutCubic)) + 
                                   scaleIn(initialScale = 0.92f, animationSpec = tween(1000, easing = androidx.compose.animation.core.EaseOutCubic)),
                        ) {
                            PermissionHandler(
                                onPermissionsGranted = {
                                    // RhythmNavigation handles mode switching between Local and Streaming
                                    RhythmNavigation(
                                        musicViewModel = musicViewModel,
                                        themeViewModel = themeViewModel,
                                        streamingMusicViewModel = streamingMusicViewModel
                                    )
                                },
                                themeViewModel = themeViewModel,
                                appSettings = appSettings,
                                isLoading = isLoading,
                                isInitializingApp = isInitializingApp,
                                onSetIsLoading = { isLoading = it },
                                onSetIsInitializingApp = { isInitializingApp = it },
                                musicViewModel = musicViewModel,
                                streamingViewModel = streamingMusicViewModel,
                                showMediaScanLoader = showMediaScanLoader,
                                onShowMediaScanLoaderChange = { showMediaScanLoader = it }
                            )
                        }
                        
                        AnimatedVisibility(
                            visible = !isInitialized,
                            exit = fadeOut(animationSpec = tween(600, easing = androidx.compose.animation.core.EaseInCubic))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {

                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    InitializationLoader(modifier = Modifier.size(64.dp))

                                    Text(
                                        text = stringResource(R.string.mainactivity_preparing),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                    )
                                }

                                // App logo, name and tagline at the bottom (matches splash branding)
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 96.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.rhythm_splash_logo),
                                            contentDescription = stringResource(R.string.cd_rhythm_logo),
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Text(
                                            text = stringResource(
                                                if (appMode == "STREAMING") R.string.streaming_integration_title else R.string.common_rhythm
                                            ),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = stringResource(R.string.splash_tagline),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )

                                    val preparingScanText = remember(isStreamingMode, scanProgress, streamingSyncProgress, streamingServiceName) {
                                         if (isStreamingMode) {
                                             when (streamingSyncProgress.stage) {
                                                 StreamingSyncStage.Syncing -> {
                                                     if (streamingSyncProgress.total > 0) {
                                                         "Syncing $streamingServiceName: ${streamingSyncProgress.current} / ${streamingSyncProgress.total}"
                                                     } else if (streamingSyncProgress.songsCount > 0) {
                                                         "Syncing $streamingServiceName: ${streamingSyncProgress.songsCount} tracks"
                                                     } else {
                                                         "Connecting to $streamingServiceName…"
                                                     }
                                                 }
                                                 StreamingSyncStage.Error -> "Sync error"
                                                 StreamingSyncStage.Complete, StreamingSyncStage.Idle -> null
                                             }
                                         } else {
                                             when (scanProgress.stage) {
                                                 is ScanPhase.Songs -> if (scanProgress.total > 0) "Scanning media: ${scanProgress.current} / ${scanProgress.total}" else "Scanning media…"
                                                 is ScanPhase.Incremental -> if (scanProgress.total > 0) "Checking new files: ${scanProgress.current} / ${scanProgress.total}" else "Checking for new music…"
                                                 is ScanPhase.SavingDb -> "Saving database…"
                                                 is ScanPhase.Error -> "Scan error"
                                                 is ScanPhase.PermissionDenied -> "Permission required"
                                                 is ScanPhase.Complete, is ScanPhase.Idle -> null
                                             }
                                         }
                                     }

                                    if (!preparingScanText.isNullOrBlank()) {
                                        Text(
                                            text = preparingScanText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }

                        // Beta Program Popup
                        BetaProgramPopup(
                            showDialog = showBetaPopup,
                            onDismiss = {
                                showBetaPopup = false
                                appSettings.setHasShownBetaPopup(true)
                            }
                        )

                        // Track Corruption Popup
                        val showCorruptionDialog by musicViewModel.showCorruptionDialog.collectAsState()
                        val corruptedTrackName by musicViewModel.corruptedTrackName.collectAsState()
                        val corruptedTrackMessage by musicViewModel.corruptedTrackMessage.collectAsState()

                        if (showCorruptionDialog) {
                            TrackCorruptionDialog(
                                onDismiss = { musicViewModel.dismissCorruptionDialog() },
                                onSkip = { musicViewModel.skipToNext() },
                                trackName = corruptedTrackName,
                                errorMessage = corruptedTrackMessage
                            )
                        }

                        // Show media scan loader as a small floating chip with swipe-to-dismiss at the top Center
                        val coroutineScope = rememberCoroutineScope()
                        val swipeOffsetX = remember { Animatable(0f) }
                        val swipeOffsetY = remember { Animatable(0f) }
                        val density = LocalDensity.current
                        val swipeThresholdPx = with(density) { 80.dp.toPx() }

                        var exitTransition by remember {
                            mutableStateOf(fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -it }))
                        }

                        LaunchedEffect(showMediaScanLoader) {
                            if (showMediaScanLoader) {
                                swipeOffsetX.snapTo(0f)
                                swipeOffsetY.snapTo(0f)
                                exitTransition = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { -it })
                            }
                        }

                        var isScanBubbleDismissedManually by remember { mutableStateOf(false) }

                        LaunchedEffect(isStreamingMode, scanProgress.stage, streamingSyncProgress.stage) {
                            if (isStreamingMode) {
                                when (streamingSyncProgress.stage) {
                                    StreamingSyncStage.Syncing -> {
                                        if (!isScanBubbleDismissedManually) {
                                            showMediaScanLoader = true
                                        }
                                    }
                                    StreamingSyncStage.Complete -> {
                                        if (showMediaScanLoader) {
                                            delay(2000)
                                            showMediaScanLoader = false
                                        }
                                        isScanBubbleDismissedManually = false
                                    }
                                    StreamingSyncStage.Error -> {
                                        if (showMediaScanLoader) {
                                            delay(3000)
                                            showMediaScanLoader = false
                                        }
                                        isScanBubbleDismissedManually = false
                                    }
                                    StreamingSyncStage.Idle -> {
                                        showMediaScanLoader = false
                                        isScanBubbleDismissedManually = false
                                    }
                                }
                            } else {
                                val stage = scanProgress.stage
                                val isScanning = stage !is ScanPhase.Idle &&
                                                 stage !is ScanPhase.Complete &&
                                                 stage !is ScanPhase.Error &&
                                                 stage !is ScanPhase.PermissionDenied

                                if (isScanning) {
                                    if (!isScanBubbleDismissedManually) {
                                        showMediaScanLoader = true
                                    }
                                } else if (stage is ScanPhase.Complete) {
                                    if (showMediaScanLoader) {
                                        delay(2000)
                                        showMediaScanLoader = false
                                        appSettings.setInitialMediaScanCompleted(true)
                                    }
                                    isScanBubbleDismissedManually = false
                                } else {
                                    showMediaScanLoader = false
                                    isScanBubbleDismissedManually = false
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showMediaScanLoader,
                            enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { -it }),
                            exit = exitTransition,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 16.dp)
                        ) {
                            val scanProgressValue: Float? = remember(isStreamingMode, scanProgress, streamingSyncProgress) {
                                if (isStreamingMode) {
                                    when (streamingSyncProgress.stage) {
                                        StreamingSyncStage.Idle -> null
                                        StreamingSyncStage.Syncing -> {
                                            if (streamingSyncProgress.total > 0) {
                                                (streamingSyncProgress.current.toFloat() / streamingSyncProgress.total.toFloat()).coerceIn(0.05f, 0.95f)
                                            } else {
                                                null
                                            }
                                        }
                                        StreamingSyncStage.Complete -> 1.0f
                                        StreamingSyncStage.Error -> null
                                    }
                                } else {
                                    when (scanProgress.stage) {
                                        is ScanPhase.Idle -> null
                                        is ScanPhase.Songs -> {
                                            if (scanProgress.total > 0) {
                                                (scanProgress.current.toFloat() / scanProgress.total.toFloat()).coerceIn(0f, 0.85f)
                                            } else {
                                                null
                                            }
                                        }
                                        is ScanPhase.Incremental -> {
                                            if (scanProgress.total > 0) {
                                                0.5f + (scanProgress.current.toFloat() / scanProgress.total.toFloat() * 0.35f)
                                            } else {
                                                null
                                            }
                                        }
                                        is ScanPhase.SavingDb -> null
                                        is ScanPhase.Complete -> 1.0f
                                        else -> null
                                    }
                                }
                            }

                            val scanLabelText = remember(isStreamingMode, scanProgress.stage, streamingSyncProgress.stage, streamingServiceName) {
                                if (isStreamingMode) {
                                    when (streamingSyncProgress.stage) {
                                        StreamingSyncStage.Syncing -> "Syncing $streamingServiceName"
                                        StreamingSyncStage.Complete -> "$streamingServiceName Updated"
                                        StreamingSyncStage.Error -> "Sync Error"
                                        StreamingSyncStage.Idle -> streamingServiceName
                                    }
                                } else {
                                    when (scanProgress.stage) {
                                        is ScanPhase.Songs -> "Scanning Music Library"
                                        is ScanPhase.Incremental, is ScanPhase.SavingDb -> "Updating Music Library"
                                        is ScanPhase.Complete -> "Music Library Updated"
                                        is ScanPhase.Error -> "Scan Error"
                                        is ScanPhase.PermissionDenied -> "Permission Required"
                                        else -> "Music Library"
                                    }
                                }
                            }

                            val scanStatusText = remember(isStreamingMode, scanProgress, streamingSyncProgress) {
                                if (isStreamingMode) {
                                    when (streamingSyncProgress.stage) {
                                        StreamingSyncStage.Idle -> "Initializing..."
                                        StreamingSyncStage.Syncing -> {
                                            if (streamingSyncProgress.total > 0) {
                                                if (streamingSyncProgress.songsCount > 0) {
                                                    "${streamingSyncProgress.current} of ${streamingSyncProgress.total} albums (${streamingSyncProgress.songsCount} songs)"
                                                } else {
                                                    "${streamingSyncProgress.current} of ${streamingSyncProgress.total} albums"
                                                }
                                            } else if (streamingSyncProgress.songsCount > 0) {
                                                "${streamingSyncProgress.songsCount} songs synced"
                                            } else {
                                                "Fetching library..."
                                            }
                                        }
                                        StreamingSyncStage.Complete -> {
                                            if (streamingSyncProgress.songsCount > 0) {
                                                "${streamingSyncProgress.songsCount} songs up to date"
                                            } else {
                                                "Up to date"
                                            }
                                        }
                                        StreamingSyncStage.Error -> "Failed to sync library"
                                    }
                                } else {
                                    when (scanProgress.stage) {
                                        is ScanPhase.Idle -> "Initializing..."
                                        is ScanPhase.Songs, is ScanPhase.Incremental -> "${scanProgress.current} of ${scanProgress.total} tracks"
                                        is ScanPhase.SavingDb -> "Saving changes..."
                                        is ScanPhase.Complete -> "Up to date"
                                        is ScanPhase.Error -> "Failed to scan files"
                                        is ScanPhase.PermissionDenied -> "Storage permission required"
                                    }
                                }
                            }

                            val swipeFraction = remember(swipeOffsetX.value, swipeOffsetY.value) {
                                val maxDist = swipeThresholdPx * 1.5f
                                val dist = maxOf(abs(swipeOffsetX.value), abs(swipeOffsetY.value))
                                (dist / maxDist).coerceIn(0f, 1f)
                            }
                            val chipAlpha = (1f - swipeFraction).coerceIn(0f, 1f)
                            val chipScale = (1f - swipeFraction * 0.1f).coerceIn(0.9f, 1f)

                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .graphicsLayer {
                                        translationX = swipeOffsetX.value
                                        translationY = swipeOffsetY.value
                                        alpha = chipAlpha
                                        scaleX = chipScale
                                        scaleY = chipScale
                                    }
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                val x = swipeOffsetX.value
                                                val y = swipeOffsetY.value
                                                if (y < -swipeThresholdPx) {
                                                    coroutineScope.launch {
                                                        exitTransition = fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { -it })
                                                        swipeOffsetY.animateTo(-500f, tween(200))
                                                        isScanBubbleDismissedManually = true
                                                        showMediaScanLoader = false
                                                        if (!isStreamingMode) {
                                                            appSettings.setInitialMediaScanCompleted(true)
                                                        }
                                                    }
                                                } else if (abs(x) > swipeThresholdPx) {
                                                    coroutineScope.launch {
                                                        if (x > 0) {
                                                            exitTransition = fadeOut(animationSpec = tween(200)) + slideOutHorizontally(targetOffsetX = { it })
                                                        } else {
                                                            exitTransition = fadeOut(animationSpec = tween(200)) + slideOutHorizontally(targetOffsetX = { -it })
                                                        }
                                                        val targetX = if (x > 0) 1000f else -1000f
                                                        swipeOffsetX.animateTo(targetX, tween(200))
                                                        isScanBubbleDismissedManually = true
                                                        showMediaScanLoader = false
                                                        if (!isStreamingMode) {
                                                            appSettings.setInitialMediaScanCompleted(true)
                                                        }
                                                    }
                                                } else {
                                                    coroutineScope.launch {
                                                        launch { swipeOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                                        launch { swipeOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                coroutineScope.launch {
                                                    launch { swipeOffsetX.animateTo(0f, spring()) }
                                                    launch { swipeOffsetY.animateTo(0f, spring()) }
                                                }
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            coroutineScope.launch {
                                                swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount.x)
                                                swipeOffsetY.snapTo((swipeOffsetY.value + dragAmount.y).coerceAtMost(50f))
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 4.dp,
                                shadowElevation = 8.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(34.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RhythmWavyProgressLoader(
                                            progress = scanProgressValue,
                                            modifier = Modifier.fillMaxSize(),
                                            indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = scanLabelText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                        Text(
                                            text = scanStatusText,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle the new intent
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        Log.d(TAG, "Handling intent: ${intent.action}, data: ${intent.data}")
        
        try {
            val shouldOpenPlayer = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)

            // OPEN_PLAYER/OPEN_QUEUE are navigation hints, not content intents.
            if (shouldOpenPlayer) {
                ShortcutManagerCompat.reportShortcutUsed(this, "shortcut_open_player")
                Log.d(TAG, "Opening player from external shortcut intent")
                // The player should automatically show since the song is already playing
                // No additional action needed as the navigation will handle it
                return
            }
            
            when (intent.action) {
                "chromahub.rhythm.app.action.SHORTCUT_PLAY_PAUSE" -> {
                    ShortcutManagerCompat.reportShortcutUsed(this, "shortcut_play_pause")
                    Log.d(TAG, "Received Play/Pause shortcut action")
                    val playPauseIntent = Intent(this, chromahub.rhythm.app.infrastructure.service.MediaPlaybackService::class.java).apply {
                        action = chromahub.rhythm.app.infrastructure.service.MediaPlaybackService.ACTION_PLAY_PAUSE
                    }
                    try {
                        androidx.core.content.ContextCompat.startForegroundService(this, playPauseIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start service for play/pause shortcut", e)
                    }
                }
                "chromahub.rhythm.app.action.SHORTCUT_SKIP_NEXT" -> {
                    ShortcutManagerCompat.reportShortcutUsed(this, "shortcut_next")
                    Log.d(TAG, "Received Skip Next shortcut action")
                    val nextIntent = Intent(this, chromahub.rhythm.app.infrastructure.service.MediaPlaybackService::class.java).apply {
                        action = chromahub.rhythm.app.infrastructure.service.MediaPlaybackService.ACTION_SKIP_NEXT
                    }
                    try {
                        androidx.core.content.ContextCompat.startForegroundService(this, nextIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start service for next shortcut", e)
                    }
                }
                "chromahub.rhythm.app.action.SHORTCUT_SKIP_PREVIOUS" -> {
                    ShortcutManagerCompat.reportShortcutUsed(this, "shortcut_previous")
                    Log.d(TAG, "Received Skip Previous shortcut action")
                    val prevIntent = Intent(this, chromahub.rhythm.app.infrastructure.service.MediaPlaybackService::class.java).apply {
                        action = chromahub.rhythm.app.infrastructure.service.MediaPlaybackService.ACTION_SKIP_PREVIOUS
                    }
                    try {
                        androidx.core.content.ContextCompat.startForegroundService(this, prevIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start service for previous shortcut", e)
                    }
                }
                Intent.ACTION_VIEW -> {
                    // Handle external audio file with validation
                    intent.data?.let { uri ->
                        Log.d(TAG, "Received ACTION_VIEW intent with URI: $uri")
                        
                        if (isValidAndSafeUri(uri)) {
                            handleExternalAudioFile(uri)
                        } else {
                            Log.w(TAG, "Invalid or unsafe URI rejected: $uri")
                            val errorMsg = when {
                                uri.scheme == null -> "Invalid file format"
                                uri.scheme !in listOf("content", "file", "android.resource") -> "Unsupported file location type"
                                else -> "Cannot access file location"
                            }
                            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        Log.w(TAG, "ACTION_VIEW intent received without data")
                    }
                }
                else -> {
                    Log.d(TAG, "Unhandled intent action: ${intent.action}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling intent", e)
            Toast.makeText(this, R.string.mainactivity_error_opening_file, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun isValidAndSafeUri(uri: Uri): Boolean {
        return try {
            // Validate URI scheme - be more permissive
            val scheme = uri.scheme?.lowercase()
            if (scheme != "content" && scheme != "file" && scheme != "android.resource") {
                Log.w(TAG, "Unsupported URI scheme: $scheme for URI: $uri")
                return false
            }
            
            // For content URIs, be more permissive with authorities
            if (scheme == "content") {
                val authority = uri.authority
                Log.d(TAG, "Content URI authority: $authority")
                
                // Allow more authorities, including third-party file managers
                val suspiciousAuthorities = setOf(
                    "com.malicious.app",
                    "suspicious.authority"
                )
                
                if (authority != null && suspiciousAuthorities.any { authority.contains(it) }) {
                    Log.w(TAG, "Potentially malicious content authority: $authority")
                    return false
                }
            }
            
            // Validate file path for file URIs - be more permissive
            if (scheme == "file") {
                val path = uri.path
                if (path == null) {
                    Log.w(TAG, "File URI with null path")
                    return false
                }
                
                try {
                    // Check for path traversal attempts but be less strict
                    val file = java.io.File(path)
                    if (!file.exists()) {
                        Log.w(TAG, "File does not exist: $path")
                        return false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking file existence: $path", e)
                    return false
                }
            }
            
            // Try to access the URI to verify it exists and is readable
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    // URI is accessible, try reading a few bytes to ensure it's valid
                    val buffer = ByteArray(8)
                    inputStream.read(buffer)
                    Log.d(TAG, "URI is accessible and readable: $uri")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot read from URI: $uri", e)
                return false
            }
            
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Security exception accessing URI: $uri", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error validating URI: $uri", e)
            false
        }
    }
    
    private fun handleExternalAudioFile(uri: Uri) {
        Log.d(TAG, "Handling external audio file: $uri")
        
        // Validate URI and check if it's an audio file
        if (!isValidAudioUri(uri)) {
            Log.e(TAG, "Invalid or unsupported audio file: $uri")
            Toast.makeText(applicationContext, R.string.mainactivity_unsupported_file_format, Toast.LENGTH_SHORT).show()
            return
        }
        
        val mimeType = MediaUtils.getMimeType(applicationContext, uri)
        Log.d(TAG, "File is recognized as audio with mime type: $mimeType")
        
        // Extract metadata from the audio file with proper error handling
        val job = lifecycleScope.launch {
            try {
                // Start the service with proper initialization waiting
                val serviceStarted = startMediaServiceAndWait()
                if (!serviceStarted) {
                    Log.e(TAG, "Failed to start media service")
                    Toast.makeText(applicationContext, R.string.mainactivity_failed_to_initialize_media, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Extract metadata on a background thread
                val song = withContext(Dispatchers.IO) {
                    MediaUtils.extractMetadataFromUri(applicationContext, uri)
                }
                
                Log.d(TAG, "Extracted song metadata: ${song.title} by ${song.artist} from ${song.album}")
                
                // Ensure service connection with timeout
                val serviceConnected = waitForServiceConnection(timeoutMs = 5000)
                if (!serviceConnected) {
                    Log.w(TAG, "Service connection timeout, attempting fallback")
                    fallbackPlayExternalFile(uri)
                    return@launch
                }
                
                // Play the external file
                musicViewModel.playExternalAudioFile(song)
                
                // Verify playback started with timeout
                val playbackStarted = waitForPlaybackStart(timeoutMs = 3000)
                if (!playbackStarted) {
                    Log.w(TAG, "Playback didn't start, using fallback method")
                    fallbackPlayExternalFile(uri)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing external audio file", e)
                val errorMessage = when (e) {
                    is SecurityException -> "Permission denied accessing file"
                    is IllegalArgumentException -> "Invalid audio file format"
                    else -> "Error playing audio file: ${e.message}"
                }
                Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
        
        // Track the job for cleanup
        lifecycleScopeJobs.add(job)
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
                    uriStr.endsWith(".dff", ignoreCase = true)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating URI: $uri", e)
            false
        }
    }
    
    private suspend fun startMediaServiceAndWait(): Boolean {
        return try {
            val serviceIntent = Intent(this, chromahub.rhythm.app.infrastructure.service.MediaPlaybackService::class.java)
            serviceIntent.action = chromahub.rhythm.app.infrastructure.service.MediaPlaybackService.ACTION_INIT_SERVICE

            val started = ServiceStartUtils.startServiceSafely(
                context = this,
                intent = serviceIntent,
                logTag = TAG,
                reason = "main_activity_init_service"
            )
            if (!started) {
                return false
            }
            
            // Wait for service to be ready
            var attempts = 0
            while (attempts < 10 && !musicViewModel.isServiceConnected()) {
                delay(100)
                attempts++
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start media service", e)
            false
        }
    }
    
    private suspend fun waitForServiceConnection(timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        var lastConnectAttemptMs = 0L
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (musicViewModel.isServiceConnected()) {
                return true
            }
            val now = System.currentTimeMillis()
            if (!musicViewModel.isServiceConnected() && now - lastConnectAttemptMs >= 1000L) {
                musicViewModel.connectToMediaService()
                lastConnectAttemptMs = now
            }
            delay(100)
        }
        return false
    }
    
    private suspend fun waitForPlaybackStart(timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        delay(500) // Initial delay to let playback initialize
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (musicViewModel.isPlaying()) {
                return true
            }
            delay(100)
        }
        return false
    }
    
    private suspend fun fallbackPlayExternalFile(uri: Uri) {
        try {
            Log.d(TAG, "Using direct service intent as fallback")
            val playIntent = Intent(applicationContext, chromahub.rhythm.app.infrastructure.service.MediaPlaybackService::class.java)
            playIntent.action = chromahub.rhythm.app.infrastructure.service.MediaPlaybackService.ACTION_PLAY_EXTERNAL_FILE
            playIntent.data = uri

            val started = ServiceStartUtils.startServiceSafely(
                context = applicationContext,
                intent = playIntent,
                logTag = TAG,
                reason = "main_activity_fallback_external_play"
            )
            if (!started) {
                Toast.makeText(applicationContext, R.string.externalplaybackactivity_failed_to_play_audio, Toast.LENGTH_SHORT).show()
                return
            }
            
            // Give fallback some time to start
            delay(1000)
            if (!musicViewModel.isPlaying()) {
                Toast.makeText(applicationContext, R.string.mainactivity_unable_to_play_audio, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback playback also failed", e)
            Toast.makeText(applicationContext, R.string.externalplaybackactivity_failed_to_play_audio, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "MainActivity onDestroy - cleaning up resources")
        
        // Cancel all tracked coroutine jobs to prevent memory leaks
        lifecycleScopeJobs.forEach { job ->
            if (job.isActive) {
                job.cancel()
                Log.d(TAG, "Cancelled pending job: $job")
            }
        }
        lifecycleScopeJobs.clear()
        
        // Auto-trim cache if usage exceeds 90% of max.
        // Run in a standalone CoroutineScope since lifecycleScope is cancelled when super.onDestroy() runs.
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val maxSize = appSettings.maxCacheSize.value
                chromahub.rhythm.app.util.CacheManager.autoTrimCache(applicationContext, maxSize)
            } catch (e: Exception) {
                Log.e(TAG, "Error during cache auto-trim on app destroy", e)
            }
        }
        
        super.onDestroy()
    }
    
    // Helper function to get step name for accessibility
    private fun getStepName(step: OnboardingStep): String {
        return when (step) {
            OnboardingStep.WELCOME -> "Welcome"
            OnboardingStep.APP_MODE_CHOICE -> "App Mode Choice"
            OnboardingStep.STREAMING_SETUP -> "Streaming Setup"
            OnboardingStep.PERMISSIONS -> "Permissions"
            OnboardingStep.RHYTHM_GUARD -> "Rhythm Guard"
            OnboardingStep.UPDATER -> "Updates"
            OnboardingStep.FULL_TOUR_PROMPT -> "Full Tour Choice"
            OnboardingStep.NOTIFICATIONS -> "Notifications"
            OnboardingStep.BACKUP_RESTORE -> "Backup & Restore"
            OnboardingStep.AUDIO_PLAYBACK -> "Audio & Playback"
            OnboardingStep.THEMING -> "Theming"
            OnboardingStep.PLAYER_THEME_CHOICE -> "Player Themes"
            OnboardingStep.GESTURES -> "Gestures"
            OnboardingStep.LIBRARY_SETUP -> "Library Setup"
            OnboardingStep.MEDIA_SCAN -> "Media Scan"
            OnboardingStep.WIDGETS -> "Widgets"
            OnboardingStep.INTEGRATIONS -> "Integrations"
            OnboardingStep.RHYTHM_STATS -> "Rhythm Stats"
            OnboardingStep.SETUP_FINISHED -> "Setup Finished"
            OnboardingStep.COMPLETE -> "Complete"
        }
}}
