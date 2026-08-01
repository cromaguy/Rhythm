package chromahub.rhythm.app.shared.presentation.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.common.InitializationLoader
import chromahub.rhythm.app.features.local.presentation.screens.onboarding.OnboardingStep
import chromahub.rhythm.app.features.local.presentation.screens.onboarding.PermissionScreenState
import chromahub.rhythm.app.features.local.presentation.screens.OnboardingScreen
import chromahub.rhythm.app.shared.presentation.viewmodel.ThemeViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.AppUpdaterViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.rememberAppUpdaterViewModel
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionHandler(
    onPermissionsGranted: @Composable () -> Unit,
    themeViewModel: ThemeViewModel,
    appSettings: AppSettings,
    isLoading: Boolean, // Pass as parameter
    isInitializingApp: Boolean, // Pass as parameter
    onSetIsLoading: (Boolean) -> Unit, // Callback to update state
    onSetIsInitializingApp: (Boolean) -> Unit, // Callback to update state
    musicViewModel: MusicViewModel = viewModel(),
    updaterViewModel: AppUpdaterViewModel = rememberAppUpdaterViewModel(),
    streamingViewModel: StreamingMusicViewModel = viewModel(),
    showMediaScanLoader: Boolean,
    onShowMediaScanLoaderChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onboardingCompleted by appSettings.onboardingCompleted.collectAsState()
    val initialMediaScanCompleted by appSettings.initialMediaScanCompleted.collectAsState()
    val appMode by appSettings.appMode.collectAsState()
    var permissionScreenState by remember { mutableStateOf<PermissionScreenState>(PermissionScreenState.Loading) }
    var permissionRequestLaunched by remember { mutableStateOf(false) } // New state to track if permission request has been launched
    var continueFullTour by remember { mutableStateOf(false) }
    // Track whether the tour was programmatically reset so that remember(onboardingCompleted)
    // does not flip currentOnboardingStep back to COMPLETE during mid-tour recompositions.
    var tourWasReset by remember { mutableStateOf(false) }

    // Storage permissions based on Android version:
    // - Android 13+ (API 33+): READ_MEDIA_AUDIO (granular media permissions)
    // - Android 12 (API 31-32): READ_EXTERNAL_STORAGE (legacy permission)
    // - Android 11 and below: READ_EXTERNAL_STORAGE
    // Note: READ_MEDIA_IMAGES is optional for album art and NOT required
    val storagePermissions = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            // Android 13, 14, 15, 16+ (API 33+) - granular media permissions
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        }
        else -> {
            // Android 12 and below (API 32 and lower) - legacy storage permission
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    
    // Notification permissions for Android 13+
    val notificationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }
    
    // Bluetooth permissions based on Android version
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ requires BLUETOOTH_CONNECT and BLUETOOTH_SCAN
        listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        // Older versions use BLUETOOTH and BLUETOOTH_ADMIN
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }
    
    // Only request essential permissions that are actually needed
    val essentialPermissions = storagePermissions + bluetoothPermissions + notificationPermissions
    
    // Check if onboarding state is valid - if onboarding is marked complete but permissions are not granted,
    // reset the onboarding state to force a fresh start (except in streaming mode where storage permission isn't required)
    LaunchedEffect(Unit) {
        if (onboardingCompleted && appSettings.appMode.value != "STREAMING") {
            val hasStoragePermissions = storagePermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (!hasStoragePermissions) {
                // Invalid state: onboarding marked complete but permissions missing
                // Reset onboarding to start fresh from WELCOME
                tourWasReset = true
                appSettings.setOnboardingCompleted(false)
                appSettings.setInitialMediaScanCompleted(false)
                // Immediately clear loading so the WELCOME screen can appear instead of
                // getting stuck on the loading spinner indefinitely.
                onSetIsLoading(false)
            }
        }
    }

    // Reactive check: when the user switches from STREAMING → LOCAL mode after the onboarding
    // was already completed in streaming mode, we must verify storage permissions are present.
    // LaunchedEffect(Unit) above only runs once; this covers runtime mode switches.
    LaunchedEffect(appMode) {
        if (appMode != "STREAMING" && onboardingCompleted) {
            val hasStoragePermissions = storagePermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (!hasStoragePermissions) {
                // Permissions are required for local mode but not granted — restart the tour
                // so the user goes through the PERMISSIONS step before reaching the main app.
                tourWasReset = true
                appSettings.setOnboardingCompleted(false)
                appSettings.setInitialMediaScanCompleted(false)
                onSetIsLoading(false)
            }
        }
    }

    // remember(onboardingCompleted) re-runs its lambda ONLY when the key (onboardingCompleted)
    // changes. The initial value is simple: completed → COMPLETE, not completed → WELCOME.
    // tourWasReset is intentionally NOT used here — when setOnboardingCompleted(true) fires at
    // tour completion, the DataStore flow emits true, the key changes, and the lambda runs again.
    // If tourWasReset were still true at that point (it's never auto-cleared), the lambda would
    // return WELCOME instead of COMPLETE, causing a 1-frame WELCOME flash.
    // tourWasReset is only used by the AnimatedVisibility escape hatch below.
    var currentOnboardingStep by remember(onboardingCompleted) {
        mutableStateOf(
            if (onboardingCompleted) OnboardingStep.COMPLETE else OnboardingStep.WELCOME
        )
    }

    fun completeOnboardingNow() {
        tourWasReset = false // Clear reset flag before completing so the remember lambda sees false
        appSettings.setOnboardingCompleted(true)
        currentOnboardingStep = OnboardingStep.COMPLETE
        if (!initialMediaScanCompleted && !musicViewModel.isLibraryRefreshing.value && appSettings.appMode.value != "STREAMING") {
            onShowMediaScanLoaderChange(true)
            musicViewModel.refreshLibrary(showMediaScanLoader = false)
        }
    }
    
    val permissionsState = rememberMultiplePermissionsState(essentialPermissions)
    
    val lifecycleOwner = LocalLifecycleOwner.current

    // Centralized function to evaluate permission status and update onboarding step
    suspend fun evaluatePermissionsAndSetStep() {
        // Check if we have the essential storage permissions (bypass if streaming)
        val hasStoragePermissions = appSettings.appMode.value == "STREAMING" || storagePermissions.all { permission ->
            permissionsState.permissions.find { it.permission == permission }?.status?.isGranted == true
        }

        if (hasStoragePermissions) {
            permissionScreenState = PermissionScreenState.PermissionsGranted
            if (!onboardingCompleted) {
                continueFullTour = false
                currentOnboardingStep = OnboardingStep.RHYTHM_GUARD
            } else {
                currentOnboardingStep = OnboardingStep.COMPLETE
                onSetIsInitializingApp(true) // Start app initialization
                try {
                    val intent = Intent(context, chromahub.rhythm.app.infrastructure.service.MediaPlaybackService::class.java)
                    intent.action = chromahub.rhythm.app.infrastructure.service.MediaPlaybackService.ACTION_INIT_SERVICE
                    // Must use startForegroundService to avoid BackgroundServiceStartNotAllowedException on Android 12+
                    ContextCompat.startForegroundService(context, intent)
                    delay(1000) // Give service time to initialize
                } catch (_: Exception) {
                    // Fallback: service will be started when the activity is fully foregrounded
                }
                onSetIsInitializingApp(false) // End app initialization
            }
            onSetIsLoading(false) // Always set loading to false after evaluation
        } else {
            // Check denied permissions state
            val deniedStoragePermissions = storagePermissions.filter { permission ->
                permissionsState.permissions.find { it.permission == permission }?.status?.isGranted != true
            }

            val shouldShowRationaleForAny = deniedStoragePermissions.any { permission ->
                permissionsState.permissions.find { it.permission == permission }?.status?.shouldShowRationale == true
            }
            
            val allDeniedPermanently = deniedStoragePermissions.isNotEmpty() && deniedStoragePermissions.all { permission ->
                val permissionState = permissionsState.permissions.find { it.permission == permission }
                permissionState?.status?.shouldShowRationale == false && permissionRequestLaunched
            }

            // Determine the correct permission screen state
            if (!permissionRequestLaunched && !onboardingCompleted) {
                // First time asking for permissions
                permissionScreenState = PermissionScreenState.PermissionsRequired
            } else if (shouldShowRationaleForAny) {
                // User denied but we can show rationale
                permissionScreenState = PermissionScreenState.ShowRationale
            } else if (allDeniedPermanently) {
                // User denied permanently
                permissionScreenState = PermissionScreenState.RedirectToSettings
            } else {
                // Default state
                permissionScreenState = PermissionScreenState.PermissionsRequired
            }
            currentOnboardingStep = OnboardingStep.PERMISSIONS
            onSetIsLoading(false) // Always set loading to false after evaluation
        }
    }

    // Effect to evaluate permission state when entering PERMISSIONS step
    // BUT do NOT auto-launch permission request - let user click button
    LaunchedEffect(currentOnboardingStep) {
        if (currentOnboardingStep == OnboardingStep.PERMISSIONS) {
            // Immediately evaluate the current permission state without launching request
            onSetIsLoading(false) // Ensure we're not showing loading initially
            evaluatePermissionsAndSetStep()
        }
    }

    // Effect to re-evaluate permissions after a request or on resume
    LaunchedEffect(permissionsState.allPermissionsGranted, permissionsState.shouldShowRationale) {
        // This effect runs when permission state changes (e.g., after user interacts with dialog)
        // or when the app resumes.
        // Only re-evaluate if we are currently on the permissions step or if onboarding is complete
        // but permissions somehow became ungranted (e.g., user revoked from settings).
        if (currentOnboardingStep == OnboardingStep.PERMISSIONS || (onboardingCompleted && permissionScreenState != PermissionScreenState.PermissionsGranted)) {
            // Don't show loading immediately - evaluate first, then decide
            evaluatePermissionsAndSetStep()
        }
    }

    // Effect to observe lifecycle and re-check permissions on resume
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                super.onResume(owner)
                // When activity resumes, if we are on the permissions step, re-check
                if (currentOnboardingStep == OnboardingStep.PERMISSIONS || (onboardingCompleted && permissionScreenState != PermissionScreenState.PermissionsGranted)) {
                    scope.launch {
                        delay(300) // Small delay to ensure system permission dialogs are fully dismissed
                        // Don't set loading here - just re-evaluate
                        evaluatePermissionsAndSetStep()
                        permissionRequestLaunched = false // Reset for next time
                    }
                }
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Show the main app as soon as onboarding is complete, not initializing
        AnimatedVisibility(
            visible = currentOnboardingStep == OnboardingStep.COMPLETE && !isInitializingApp, // Show app immediately when complete
            enter = fadeIn(animationSpec = tween(1000, easing = androidx.compose.animation.core.EaseOutCubic)) +
                   slideInVertically(
                       initialOffsetY = { it / 3 },
                       animationSpec = tween(1000, easing = androidx.compose.animation.core.EaseOutCubic)
                   )
        ) {
            onPermissionsGranted()
        }


        AnimatedVisibility(
            visible = isInitializingApp || (isLoading && currentOnboardingStep != OnboardingStep.COMPLETE && currentOnboardingStep != OnboardingStep.PERMISSIONS), // Show loading if app initializing, or general loading not on permission screen
            exit = fadeOut(animationSpec = tween(800, easing = androidx.compose.animation.core.EaseInCubic))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                InitializationLoader(
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        AnimatedVisibility(
            // Show onboarding when:
            //  • Not in a loading/initializing state, OR
            //  • The tour was explicitly reset (we always want to show WELCOME in that case,
            //    even if isLoading hasn't fully settled yet).
            visible = (!isLoading && !isInitializingApp && currentOnboardingStep != OnboardingStep.COMPLETE)
                    || (tourWasReset && currentOnboardingStep == OnboardingStep.WELCOME && !isInitializingApp),
            enter = fadeIn(animationSpec = tween(800, easing = androidx.compose.animation.core.EaseOutCubic)) +
                   scaleIn(initialScale = 0.95f, animationSpec = tween(800, easing = androidx.compose.animation.core.EaseOutCubic))
        ) {
            OnboardingScreen(
                currentStep = currentOnboardingStep,
                musicViewModel = musicViewModel,
                updaterViewModel = updaterViewModel,
                streamingViewModel = streamingViewModel,
                onNextStep = {
                    when (currentOnboardingStep) {
                        OnboardingStep.WELCOME -> currentOnboardingStep = OnboardingStep.APP_MODE_CHOICE
                        OnboardingStep.APP_MODE_CHOICE -> {
                            currentOnboardingStep = if (appSettings.appMode.value == "STREAMING") {
                                OnboardingStep.STREAMING_SETUP
                            } else {
                                OnboardingStep.PERMISSIONS
                            }
                        }
                        OnboardingStep.STREAMING_SETUP -> currentOnboardingStep = OnboardingStep.RHYTHM_GUARD
                        OnboardingStep.PERMISSIONS -> {
                            // Handle based on current permission state
                            when (permissionScreenState) {
                                PermissionScreenState.PermissionsGranted -> {
                                    currentOnboardingStep = OnboardingStep.RHYTHM_GUARD
                                }
                                PermissionScreenState.RedirectToSettings -> {
                                    // This is handled by onRequestAgain callback
                                }
                                else -> {
                                    // Launch permission request
                                    onSetIsLoading(true) // Set loading to true when requesting
                                    scope.launch {
                                        try {
                                            permissionsState.launchMultiplePermissionRequest()
                                            permissionRequestLaunched = true // Mark as launched
                                        } catch (e: Exception) {
                                            onSetIsLoading(false)
                                            permissionScreenState = PermissionScreenState.ShowRationale
                                        }
                                    }
                                }
                            }
                        }
                        OnboardingStep.RHYTHM_GUARD -> {
                            currentOnboardingStep = if (appSettings.appMode.value == "STREAMING") {
                                OnboardingStep.UPDATER
                            } else {
                                OnboardingStep.MEDIA_SCAN
                            }
                        }
                        OnboardingStep.MEDIA_SCAN -> {
                            musicViewModel.refreshLibrary(showMediaScanLoader = false)
                            onShowMediaScanLoaderChange(true)
                            currentOnboardingStep = OnboardingStep.UPDATER
                        }
                        OnboardingStep.UPDATER -> currentOnboardingStep = OnboardingStep.FULL_TOUR_PROMPT
                        OnboardingStep.FULL_TOUR_PROMPT -> {
                            currentOnboardingStep = if (continueFullTour) {
                                OnboardingStep.BACKUP_RESTORE
                            } else {
                                OnboardingStep.SETUP_FINISHED
                            }
                        }
                        OnboardingStep.NOTIFICATIONS -> currentOnboardingStep = OnboardingStep.BACKUP_RESTORE
                        OnboardingStep.BACKUP_RESTORE -> currentOnboardingStep = OnboardingStep.AUDIO_PLAYBACK
                        OnboardingStep.AUDIO_PLAYBACK -> currentOnboardingStep = OnboardingStep.THEMING
                        OnboardingStep.THEMING -> currentOnboardingStep = OnboardingStep.PLAYER_THEME_CHOICE
                        OnboardingStep.PLAYER_THEME_CHOICE -> currentOnboardingStep = OnboardingStep.GESTURES
                        OnboardingStep.GESTURES -> currentOnboardingStep = OnboardingStep.WIDGETS
                        OnboardingStep.LIBRARY_SETUP -> currentOnboardingStep = OnboardingStep.WIDGETS
                        OnboardingStep.WIDGETS -> currentOnboardingStep = OnboardingStep.INTEGRATIONS // Move to integrations
                        OnboardingStep.INTEGRATIONS -> currentOnboardingStep = OnboardingStep.RHYTHM_STATS // Move to rhythm stats
                        OnboardingStep.RHYTHM_STATS -> currentOnboardingStep = OnboardingStep.SETUP_FINISHED // Move to setup finished
                        OnboardingStep.SETUP_FINISHED -> {
                            completeOnboardingNow()
                        }
                        OnboardingStep.COMPLETE -> { /* Should not happen */ }
                    }
                },
                onPrevStep = {
                    when (currentOnboardingStep) {
                        OnboardingStep.APP_MODE_CHOICE -> currentOnboardingStep = OnboardingStep.WELCOME
                        OnboardingStep.PERMISSIONS -> currentOnboardingStep = OnboardingStep.APP_MODE_CHOICE
                        OnboardingStep.STREAMING_SETUP -> currentOnboardingStep = OnboardingStep.APP_MODE_CHOICE
                        OnboardingStep.RHYTHM_GUARD -> {
                            if (appSettings.appMode.value == "STREAMING") {
                                currentOnboardingStep = OnboardingStep.STREAMING_SETUP
                            } else {
                                currentOnboardingStep = OnboardingStep.PERMISSIONS
                                scope.launch {
                                    onSetIsLoading(false)
                                    evaluatePermissionsAndSetStep()
                                }
                            }
                        }
                        OnboardingStep.MEDIA_SCAN -> currentOnboardingStep = OnboardingStep.RHYTHM_GUARD
                        OnboardingStep.UPDATER -> {
                            currentOnboardingStep = if (appSettings.appMode.value == "STREAMING") {
                                OnboardingStep.RHYTHM_GUARD
                            } else {
                                OnboardingStep.MEDIA_SCAN
                            }
                        }
                        OnboardingStep.FULL_TOUR_PROMPT -> currentOnboardingStep = OnboardingStep.UPDATER
                        OnboardingStep.NOTIFICATIONS -> currentOnboardingStep = OnboardingStep.FULL_TOUR_PROMPT
                        OnboardingStep.BACKUP_RESTORE -> currentOnboardingStep = OnboardingStep.FULL_TOUR_PROMPT
                        OnboardingStep.AUDIO_PLAYBACK -> currentOnboardingStep = OnboardingStep.BACKUP_RESTORE
                        OnboardingStep.THEMING -> currentOnboardingStep = OnboardingStep.AUDIO_PLAYBACK
                        OnboardingStep.PLAYER_THEME_CHOICE -> currentOnboardingStep = OnboardingStep.THEMING
                        OnboardingStep.GESTURES -> currentOnboardingStep = OnboardingStep.PLAYER_THEME_CHOICE
                        OnboardingStep.LIBRARY_SETUP -> currentOnboardingStep = OnboardingStep.GESTURES
                        OnboardingStep.WIDGETS -> currentOnboardingStep = OnboardingStep.GESTURES
                        OnboardingStep.INTEGRATIONS -> currentOnboardingStep = OnboardingStep.WIDGETS
                        OnboardingStep.RHYTHM_STATS -> currentOnboardingStep = OnboardingStep.INTEGRATIONS
                        OnboardingStep.SETUP_FINISHED -> {
                            currentOnboardingStep = if (continueFullTour) {
                                OnboardingStep.RHYTHM_STATS
                            } else {
                                OnboardingStep.FULL_TOUR_PROMPT
                            }
                        }
                        else -> { /* Should not happen for WELCOME or COMPLETE */ }
                    }
                },
                onContinueFullTour = {
                    continueFullTour = true
                    currentOnboardingStep = OnboardingStep.BACKUP_RESTORE
                },
                onSkipFullTour = {
                    continueFullTour = false
                    completeOnboardingNow()
                },
                onRequestAgain = {
                    // This is for "Open App Settings" or re-requesting permissions
                    // This will be called when the user clicks "Grant Access" or "Open Settings"
                    // in the PermissionContent.
                    onSetIsLoading(true) // Set loading to true when requesting again
                    // The action (launching settings or re-requesting) will be handled inside PermissionContent
                    // based on permissionScreenState.
                    // No need to launchMultiplePermissionRequest here, as it's handled by the button click in PermissionContent
                },
                permissionScreenState = permissionScreenState, // Pass the state
                isParentLoading = isLoading,
                themeViewModel = themeViewModel,
                appSettings = appSettings, // Pass appSettings to OnboardingScreen
                onFinish = {
                    completeOnboardingNow()
                }
            )
        }
    }
}
