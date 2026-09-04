/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.navigation

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.animation.AnimatedContent
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.activities.MainActivity
import chromahub.rhythm.app.activities.RhythmGuardTimeoutActivity
import chromahub.rhythm.app.R
import chromahub.rhythm.app.core.domain.model.AppMode
import chromahub.rhythm.app.features.local.presentation.navigation.LocalNavigation
import chromahub.rhythm.app.shared.data.repository.UserPreferencesRepository
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import chromahub.rhythm.app.shared.data.repository.StatsTimeRange
import chromahub.rhythm.app.shared.presentation.components.common.RhythmWavyProgressLoader
import chromahub.rhythm.app.shared.presentation.viewmodel.AppModeViewModel
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.ThemeViewModel
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.util.AudioDeviceManager
import androidx.core.app.NotificationCompat
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import java.util.Locale



/**
 * Main navigation composable that handles switching between Local and Streaming modes.
 * Acts as the root navigation for the entire app.
 */
@Composable
fun RhythmNavigation(
    modifier: Modifier = Modifier,
    musicViewModel: MusicViewModel = viewModel(),
    themeViewModel: ThemeViewModel = viewModel(),
    streamingMusicViewModel: StreamingMusicViewModel = viewModel(),
    navigateToSettingsTrigger: Boolean = false,
    onSettingsNavigationComplete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings.getInstance(context) }
    val appMode by appSettings.appMode.collectAsState()
    
    // Create a NavHostController that can be passed to both local and streaming navigation
    val rootNavController = rememberNavController()
    
    // Navigate to settings when triggered
    LaunchedEffect(navigateToSettingsTrigger) {
        if (navigateToSettingsTrigger) {
            rootNavController.navigate("settings") {
                launchSingleTop = true
            }
            onSettingsNavigationComplete?.invoke()
        }
    }
    
    // Settings navigation callback that works for both modes
    val navigateToSettings: () -> Unit = {
        rootNavController.navigate("settings") {
            launchSingleTop = true
        }
    }
    
    val defaultMiniPlayerPadding = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()

    // Switch between Local and Streaming navigation based on app mode with animated transitions.
    // RhythmGuardWarningHost is mounted at root so warnings appear above all screens and bottom sheets.
    CompositionLocalProvider(LocalMiniPlayerPadding provides defaultMiniPlayerPadding) {
        Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = rootNavController,
            startDestination = "main",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("main") {
                AnimatedContent(
                    targetState = appMode,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(
                            animationSpec = tween(400)
                        ) + slideInHorizontally(
                            initialOffsetX = { if (targetState == "STREAMING") it else -it },
                            animationSpec = tween(500, easing = EaseInOutQuart)
                        ) togetherWith fadeOut(
                            animationSpec = tween(400)
                        ) + slideOutHorizontally(
                            targetOffsetX = { if (targetState == "STREAMING") -it else it },
                            animationSpec = tween(500, easing = EaseInOutQuart)
                        )
                    },
                    label = "appModeTransition"
                ) { mode ->
                    when (mode) {
                        "STREAMING" -> {
                            // Streaming (Go) content is hosted on the shared Home/Library screens
                            LocalNavigation(
                                viewModel = musicViewModel,
                                themeViewModel = themeViewModel,
                                appSettings = appSettings,
                                streamingMusicViewModel = streamingMusicViewModel
                            )
                        }

                        else -> {
                            // Use LocalNavigation for local mode (default)
                            LocalNavigation(
                                viewModel = musicViewModel,
                                themeViewModel = themeViewModel,
                                appSettings = appSettings,
                                streamingMusicViewModel = streamingMusicViewModel
                            )
                        }
                    }
                }
            }

            // Settings screen (shared between both modes)
            composable(
                route = "settings",
                enterTransition = {
                    fadeIn(animationSpec = tween(300)) +
                        slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(350, easing = EaseInOutQuart)
                        )
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(300))
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(300)) +
                        slideOutVertically(
                            targetOffsetY = { it / 4 },
                            animationSpec = tween(350, easing = EaseInOutQuart)
                        )
                }
            ) {
                chromahub.rhythm.app.shared.presentation.screens.settings.SettingsScreenWrapper(
                    onBack = {
                        val popped = rootNavController.popBackStack()
                        if (!popped) {
                            rootNavController.navigate("main") {
                                launchSingleTop = true
                            }
                        }
                    },
                    appSettings = appSettings,
                    navController = rootNavController,
                    musicViewModel = musicViewModel
                )
            }
        }

        RhythmGuardWarningHost(
            appSettings = appSettings,
            musicViewModel = musicViewModel
        )
        }
    }
}

private enum class RhythmGuardWarningType {
    VOLUME,
    EXPOSURE
}

enum class RhythmGuardRiskLevel {
    LOW,
    MODERATE,
    HIGH,
    SEVERE
}

private const val RHYTHM_GUARD_ALERT_CHANNEL_ID = "rhythm_guard_alerts"
private const val RHYTHM_GUARD_TIMER_CHANNEL_ID = "rhythm_guard_timers"
private const val RHYTHM_GUARD_ALERT_NOTIFICATION_ID = 1301
private const val RHYTHM_GUARD_TIMER_NOTIFICATION_ID = 1302

private data class RhythmGuardVolumeWarningDialogState(
    val mode: String,
    val currentVolumePercent: Int,
    val safeThresholdPercent: Int,
    val suggestedVolume: Float,
    val riskLevel: RhythmGuardRiskLevel
)

private data class RhythmGuardBreakDialogState(
    val mode: String,
    val estimatedTodayMinutes: Int,
    val recommendedDailyMinutes: Int,
    val riskLevel: RhythmGuardRiskLevel
)

@Composable
private fun RhythmGuardWarningHost(
    appSettings: AppSettings,
    musicViewModel: MusicViewModel
) {
    val context = LocalContext.current
    val playbackStatsRepository = remember(context) { PlaybackStatsRepository.getInstance(context) }

    var timeoutActivityLaunched by rememberSaveable { mutableStateOf(false) }

    val timeoutUntilMs by appSettings.rhythmGuardTimeoutUntilMs.collectAsState()
    val timeoutReason by appSettings.rhythmGuardTimeoutReason.collectAsState()
    val timeoutStartedAtMsState by appSettings.rhythmGuardTimeoutStartedAtMs.collectAsState()

    LaunchedEffect(timeoutUntilMs) {
        val now = System.currentTimeMillis()
        if (timeoutUntilMs > now) {
            // Dismiss zero-volume dialog if a Rhythm Guard timeout starts (lock screen takes over)
            if (musicViewModel.showZeroVolumePauseDialog.value) {
                musicViewModel.dismissZeroVolumePauseDialog()
            }
            if (!timeoutActivityLaunched) {
                timeoutActivityLaunched = true
                val reason = timeoutReason.ifBlank {
                    context.getString(R.string.settings_rhythm_guard_timeout_activity_default_reason)
                }
                RhythmGuardTimeoutActivity.start(
                    context = context,
                    reason = reason,
                    timeoutUntilMs = timeoutUntilMs,
                    timeoutStartedAtMs = timeoutStartedAtMsState
                )
            }
        } else {
            timeoutActivityLaunched = false
        }
    }

    // Register a BroadcastReceiver to detect zero system-volume pauses/resumes triggered by the service.
    // This works from any screen (not just the player screen) because the receiver lives at the
    // root composition level for the lifetime of RhythmGuardWarningHost.
    DisposableEffect(Unit) {
        val zeroVolumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    chromahub.rhythm.app.infrastructure.service.MediaPlaybackService.ACTION_ZERO_VOLUME_PAUSE -> {
                        musicViewModel.triggerZeroVolumePauseDialog()
                    }
                    chromahub.rhythm.app.infrastructure.service.MediaPlaybackService.ACTION_ZERO_VOLUME_RESUME -> {
                        musicViewModel.dismissZeroVolumePauseDialog()
                    }
                }
            }
        }
        val zeroVolumeFilter = IntentFilter().apply {
            addAction(chromahub.rhythm.app.infrastructure.service.MediaPlaybackService.ACTION_ZERO_VOLUME_PAUSE)
            addAction(chromahub.rhythm.app.infrastructure.service.MediaPlaybackService.ACTION_ZERO_VOLUME_RESUME)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            zeroVolumeReceiver,
            zeroVolumeFilter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(zeroVolumeReceiver)
        }
    }

    val auraMode by appSettings.rhythmGuardMode.collectAsState()
    val auraAge by appSettings.rhythmGuardAge.collectAsState()
    val manualWarningsEnabled by appSettings.rhythmGuardManualWarningsEnabled.collectAsState()
    val alertNotificationsEnabled by appSettings.rhythmGuardAlertNotificationsEnabled.collectAsState()
    val timerNotificationsEnabled by appSettings.rhythmGuardTimerNotificationsEnabled.collectAsState()
    val manualVolumeThreshold by appSettings.rhythmGuardManualVolumeThreshold.collectAsState()
    val applyVolumeLimitOnSpeaker by appSettings.rhythmGuardApplyVolumeLimitOnSpeaker.collectAsState()
    val configuredAlertThresholdMinutes by appSettings.rhythmGuardAlertThresholdMinutes.collectAsState()
    val warningTimeoutMinutes by appSettings.rhythmGuardWarningTimeoutMinutes.collectAsState()
    val postTimeoutCooldownMinutes by appSettings.rhythmGuardPostTimeoutCooldownMinutes.collectAsState()
    val configuredBreakResumeMinutes by appSettings.rhythmGuardBreakResumeMinutes.collectAsState()
    val timeoutCooldownUntilMs by appSettings.rhythmGuardTimeoutCooldownUntilMs.collectAsState()
    val nextAllowedLimit by appSettings.rhythmGuardNextAllowedLimitMinutes.collectAsState()
    val dailyListeningStats by appSettings.dailyListeningStats.collectAsState()
    val songsPlayed by appSettings.songsPlayed.collectAsState()
    val listeningTime by appSettings.listeningTime.collectAsState()

    val currentSong by musicViewModel.currentSong.collectAsState()
    val currentProgress by musicViewModel.progress.collectAsState()
    val currentDurationMs by musicViewModel.duration.collectAsState()
    val currentIsPlaying by musicViewModel.isPlaying.collectAsState()
    val currentPlaybackDevice by musicViewModel.currentDevice.collectAsState()
    val currentSystemVolume = rememberSystemMusicVolumeFraction(context)
    var todayExposureMinutes by remember { mutableIntStateOf(0) }

    val isSpeakerOutput = currentPlaybackDevice?.id == AudioDeviceManager.DEVICE_SPEAKER
    val shouldApplyVolumeLimitForCurrentOutput = applyVolumeLimitOnSpeaker || !isSpeakerOutput

    val activePolicy = remember(auraAge) { appSettings.getRhythmGuardPolicy(auraAge) }
    val effectiveExposureLimitMinutes = if (auraMode == AppSettings.RHYTHM_GUARD_MODE_AUTO) {
        activePolicy.recommendedDailyMinutes
    } else if (configuredAlertThresholdMinutes > 0) {
        configuredAlertThresholdMinutes
    } else {
        activePolicy.recommendedDailyMinutes
    }
    val activeLimit = if (nextAllowedLimit > effectiveExposureLimitMinutes) {
        nextAllowedLimit
    } else {
        effectiveExposureLimitMinutes
    }
    val activeThreshold = if (auraMode == AppSettings.RHYTHM_GUARD_MODE_AUTO) {
        activePolicy.maxVolumeThreshold
    } else {
        manualVolumeThreshold
    }
    val activeThresholdPercent = (activeThreshold * 100f).toInt().coerceIn(0, 100)
    val currentVolumePercent = (currentSystemVolume * 100f).toInt().coerceIn(0, 100)
    val isListeningTimeoutActive = timeoutUntilMs > System.currentTimeMillis() ||
            appSettings.rhythmGuardTimeoutUntilMs.value > System.currentTimeMillis()
    val isPostTimeoutCooldownActive = timeoutCooldownUntilMs > System.currentTimeMillis() ||
            appSettings.rhythmGuardTimeoutCooldownUntilMs.value > System.currentTimeMillis()

    val rulesEnabled = auraMode != AppSettings.RHYTHM_GUARD_MODE_OFF && currentSong != null
    val volumeWarningEnabled = when (auraMode) {
        AppSettings.RHYTHM_GUARD_MODE_AUTO -> false
        AppSettings.RHYTHM_GUARD_MODE_MANUAL -> manualWarningsEnabled
        else -> false
    }
    val exposureWarningEnabled = when (auraMode) {
        AppSettings.RHYTHM_GUARD_MODE_AUTO -> true
        AppSettings.RHYTHM_GUARD_MODE_MANUAL -> manualWarningsEnabled
        else -> false
    }
    val needsVolumeWarning =
        rulesEnabled &&
            !isListeningTimeoutActive &&
            volumeWarningEnabled &&
            shouldApplyVolumeLimitForCurrentOutput &&
            currentSystemVolume > activeThreshold
    val needsExposureWarning =
        rulesEnabled &&
            !isListeningTimeoutActive &&
            !isPostTimeoutCooldownActive &&
            exposureWarningEnabled &&
            todayExposureMinutes > activeLimit

    val firstBreakSeen by appSettings.rhythmGuardFirstBreakSeen.collectAsState()
    var showFirstBreakDialogState by remember { mutableStateOf<RhythmGuardBreakDialogState?>(null) }

    var volumeDialogState by remember { mutableStateOf<RhythmGuardVolumeWarningDialogState?>(null) }
    var breakDialogState by remember { mutableStateOf<RhythmGuardBreakDialogState?>(null) }
    var lastWarningType by remember { mutableStateOf<RhythmGuardWarningType?>(null) }
    var lastWarningAt by remember { mutableLongStateOf(0L) }
    var lastWarningVolumePercent by remember { mutableIntStateOf(-1) }
    var lastWarningExposureMinutes by remember { mutableIntStateOf(-1) }
    var breakResumeMinutes by remember(configuredBreakResumeMinutes) {
        mutableIntStateOf(configuredBreakResumeMinutes.coerceIn(1, 180))
    }
    var breakDelayMinutes by remember(warningTimeoutMinutes) {
        mutableIntStateOf(warningTimeoutMinutes.coerceIn(1, 180))
    }
    var resumeCountdownSeconds by remember { mutableLongStateOf(0L) }
    var timeoutStartedAtMs by remember { mutableLongStateOf(0L) }
    var lastTimeoutTriggeredExposureMinutes by remember { mutableIntStateOf(-1) }
    var lastAutoClampAtMs by remember { mutableLongStateOf(0L) }
    var lastAutoClampThresholdPercent by remember { mutableIntStateOf(-1) }
    var pendingBreakStartAtMs by remember { mutableLongStateOf(0L) }
    var pendingBreakStartCountdownSeconds by remember { mutableLongStateOf(0L) }
    var pendingBreakDurationMinutes by remember {
        mutableIntStateOf(configuredBreakResumeMinutes.coerceIn(1, 180))
    }
    var bubbleHorizontalPos by rememberSaveable { mutableIntStateOf(2) } // 0: Left, 1: Middle, 2: Right (Default: 2)
    var isBubbleCollapsed by rememberSaveable { mutableStateOf(false) }
    var rawDragXDp by remember { mutableFloatStateOf(0f) }

    val formattedTodayExposure = remember(todayExposureMinutes) {
        rhythmGuardFormatDurationFromMinutes(todayExposureMinutes)
    }
    val formattedExposureLimit = remember(effectiveExposureLimitMinutes) {
        rhythmGuardFormatDurationFromMinutes(effectiveExposureLimitMinutes)
    }

    val startTimeoutBreak: (Int) -> Unit = { requestedMinutes ->
        musicViewModel.pauseForRhythmGuardTimeout(reason = "manual break start")
        val now = System.currentTimeMillis()
        val safeBreakMinutes = requestedMinutes.coerceIn(1, 180)
        val nextTimeoutUntil = now + safeBreakMinutes.toLong() * 60_000L

        timeoutStartedAtMs = now
        lastTimeoutTriggeredExposureMinutes = todayExposureMinutes
        appSettings.setRhythmGuardListeningTimeout(
            untilEpochMs = nextTimeoutUntil,
            reason = context.getString(R.string.settings_rhythm_guard_timeout_reason_manual),
            startedAtEpochMs = now
        )
        appSettings.setRhythmGuardBreakResumeMinutes(safeBreakMinutes)
        breakResumeMinutes = safeBreakMinutes
        pendingBreakStartAtMs = 0L
        pendingBreakStartCountdownSeconds = 0L
        breakDialogState = null

        if (timerNotificationsEnabled) {
            showRhythmGuardTimerNotification(
                context = context,
                title = context.getString(R.string.settings_rhythm_guard_notification_timer_active_title),
                text = context.getString(
                    R.string.settings_rhythm_guard_notification_timer_active_text,
                    rhythmGuardFormatCountdown(safeBreakMinutes.toLong() * 60L)
                ),
                remainingSeconds = safeBreakMinutes.toLong() * 60L,
                totalSeconds = safeBreakMinutes.toLong() * 60L
            )
        }
    }

    val delayBreakReminder: (Int) -> Unit = { requestedMinutes ->
        val now = System.currentTimeMillis()
        val safeDelayMinutes = requestedMinutes.coerceIn(1, 180)
        val delayMs = safeDelayMinutes.toLong() * 60_000L

        pendingBreakDurationMinutes = breakResumeMinutes.coerceIn(1, 180)
        pendingBreakStartAtMs = now + delayMs
        pendingBreakStartCountdownSeconds = (delayMs / 1000L).coerceAtLeast(0L)
        breakDelayMinutes = safeDelayMinutes
        breakDialogState = null

        if (timerNotificationsEnabled) {
            showRhythmGuardTimerNotification(
                context = context,
                title = context.getString(R.string.settings_rhythm_guard_notification_timer_scheduled_title),
                text = context.getString(
                    R.string.settings_rhythm_guard_notification_timer_scheduled_text,
                    rhythmGuardFormatCountdown(safeDelayMinutes.toLong() * 60L)
                ),
                remainingSeconds = safeDelayMinutes.toLong() * 60L,
                totalSeconds = safeDelayMinutes.toLong() * 60L
            )
        }
    }

    LaunchedEffect(alertNotificationsEnabled, timerNotificationsEnabled) {
        if (!alertNotificationsEnabled) {
            cancelRhythmGuardAlertNotification(context)
        }
        if (!timerNotificationsEnabled) {
            cancelRhythmGuardTimerNotification(context)
        }
    }

    LaunchedEffect(dailyListeningStats, songsPlayed, listeningTime, currentProgress, currentDurationMs, currentIsPlaying) {
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
        todayExposureMinutes = (totalMs / 60000L).toInt().coerceAtLeast(0)
    }

    LaunchedEffect(auraMode, manualWarningsEnabled, currentSong) {
        if (auraMode == AppSettings.RHYTHM_GUARD_MODE_OFF) {
            volumeDialogState = null
            breakDialogState = null
            lastWarningType = null
            appSettings.clearRhythmGuardListeningTimeout()
            appSettings.clearRhythmGuardTimeoutCooldown()
            resumeCountdownSeconds = 0L
            timeoutStartedAtMs = 0L
            pendingBreakStartAtMs = 0L
            pendingBreakStartCountdownSeconds = 0L
            lastWarningVolumePercent = -1
            lastWarningExposureMinutes = -1
            lastTimeoutTriggeredExposureMinutes = -1
            lastAutoClampAtMs = 0L
            lastAutoClampThresholdPercent = -1
            cancelRhythmGuardAlertNotification(context)
            cancelRhythmGuardTimerNotification(context)
        }
        if (currentSong == null) {
            volumeDialogState = null
            breakDialogState = null
            pendingBreakStartAtMs = 0L
            pendingBreakStartCountdownSeconds = 0L
            cancelRhythmGuardAlertNotification(context)
            cancelRhythmGuardTimerNotification(context)
        }
        if (auraMode == AppSettings.RHYTHM_GUARD_MODE_MANUAL && !manualWarningsEnabled) {
            volumeDialogState = null
            breakDialogState = null
        }
    }

    LaunchedEffect(
        timeoutUntilMs,
        timeoutStartedAtMsState,
        configuredBreakResumeMinutes,
        postTimeoutCooldownMinutes,
        auraMode,
        todayExposureMinutes
    ) {
        val now = System.currentTimeMillis()
        if (timeoutUntilMs <= now) {
            cancelRhythmGuardTimerNotification(context)
            if (timeoutUntilMs > 0L) {
                val cooldownUntil = now + postTimeoutCooldownMinutes.coerceIn(1, 60) * 60_000L
                appSettings.setRhythmGuardTimeoutCooldownWithLimit(cooldownUntil, todayExposureMinutes + 15)
                appSettings.clearRhythmGuardListeningTimeout()
                musicViewModel.resumePlaybackAfterRhythmGuardTimeoutIfNeeded(
                    source = "timeout effect immediate-expired"
                )
            }
            resumeCountdownSeconds = 0L
            timeoutStartedAtMs = 0L
            return@LaunchedEffect
        }

        musicViewModel.pauseForRhythmGuardTimeout(reason = "timeout countdown active")
        if (timeoutStartedAtMsState > 0L && timeoutStartedAtMsState < timeoutUntilMs) {
            timeoutStartedAtMs = timeoutStartedAtMsState
        } else if (timeoutStartedAtMs <= 0L || timeoutStartedAtMs >= timeoutUntilMs) {
            timeoutStartedAtMs = timeoutUntilMs - configuredBreakResumeMinutes.coerceIn(1, 180) * 60_000L
        }

        val totalTimeoutSeconds = ((timeoutUntilMs - timeoutStartedAtMs) / 1000L)
            .coerceAtLeast(1L)

        while (true) {
            val remainingSeconds = ((timeoutUntilMs - System.currentTimeMillis()) / 1000L)
                .coerceAtLeast(0L)
            resumeCountdownSeconds = remainingSeconds
            if (timerNotificationsEnabled) {
                showRhythmGuardTimerNotification(
                    context = context,
                    title = context.getString(R.string.settings_rhythm_guard_notification_timer_active_title),
                    text = context.getString(
                        R.string.settings_rhythm_guard_notification_timer_active_text,
                        rhythmGuardFormatCountdown(remainingSeconds)
                    ),
                    remainingSeconds = remainingSeconds,
                    totalSeconds = totalTimeoutSeconds
                )
            }
            if (remainingSeconds <= 0L) break
            delay(1000)
        }

        val timeoutExpired = timeoutUntilMs <= System.currentTimeMillis()
        if (timeoutExpired) {
            val cooldownUntil = System.currentTimeMillis() + postTimeoutCooldownMinutes.coerceIn(1, 60) * 60_000L
            appSettings.setRhythmGuardTimeoutCooldownWithLimit(cooldownUntil, todayExposureMinutes + 15)
            appSettings.clearRhythmGuardListeningTimeout()
            musicViewModel.resumePlaybackAfterRhythmGuardTimeoutIfNeeded(
                source = "timeout effect countdown-finished"
            )
            cancelRhythmGuardTimerNotification(context)
        }
        resumeCountdownSeconds = 0L
        timeoutStartedAtMs = 0L
    }

    LaunchedEffect(pendingBreakStartAtMs, pendingBreakDurationMinutes, timeoutUntilMs) {
        if (pendingBreakStartAtMs <= 0L || timeoutUntilMs > System.currentTimeMillis()) {
            pendingBreakStartCountdownSeconds = 0L
            return@LaunchedEffect
        }

        val totalDelaySeconds = pendingBreakStartCountdownSeconds.coerceAtLeast(1L)

        while (true) {
            val now = System.currentTimeMillis()
            val remainingSeconds = ((pendingBreakStartAtMs - now) / 1000L).coerceAtLeast(0L)
            pendingBreakStartCountdownSeconds = remainingSeconds
            if (timerNotificationsEnabled) {
                showRhythmGuardTimerNotification(
                    context = context,
                    title = context.getString(R.string.settings_rhythm_guard_notification_timer_scheduled_title),
                    text = context.getString(
                        R.string.settings_rhythm_guard_notification_timer_scheduled_text,
                        rhythmGuardFormatCountdown(remainingSeconds)
                    ),
                    remainingSeconds = remainingSeconds,
                    totalSeconds = totalDelaySeconds
                )
            }
            if (remainingSeconds <= 0L) break
            delay(1000L)
        }

        val shouldStartTimeout = pendingBreakStartAtMs > 0L && timeoutUntilMs <= System.currentTimeMillis()
        pendingBreakStartAtMs = 0L
        pendingBreakStartCountdownSeconds = 0L

        if (shouldStartTimeout) {
            startTimeoutBreak(pendingBreakDurationMinutes)
        }
    }

    LaunchedEffect(
        rulesEnabled,
        auraMode,
        currentSystemVolume,
        activeThreshold,
        activeThresholdPercent,
        auraAge,
        shouldApplyVolumeLimitForCurrentOutput
    ) {
        if (!rulesEnabled || auraMode != AppSettings.RHYTHM_GUARD_MODE_AUTO) {
            return@LaunchedEffect
        }

        if (!shouldApplyVolumeLimitForCurrentOutput) {
            lastAutoClampThresholdPercent = -1
            return@LaunchedEffect
        }

        val volumeOvershoot = currentSystemVolume - activeThreshold
        if (volumeOvershoot > 0.01f) {
            val now = System.currentTimeMillis()
            val thresholdChanged = lastAutoClampThresholdPercent != activeThresholdPercent
            val largeOvershoot = volumeOvershoot > 0.03f
            val cooldownElapsed = now - lastAutoClampAtMs >= 1_500L

            if (!thresholdChanged && !largeOvershoot && !cooldownElapsed) {
                return@LaunchedEffect
            }

            setSystemMusicVolumeFraction(context, activeThreshold)
            musicViewModel.setVolume(activeThreshold)
            lastAutoClampAtMs = now
            lastAutoClampThresholdPercent = activeThresholdPercent
        }
    }

    LaunchedEffect(
        rulesEnabled,
        needsVolumeWarning,
        needsExposureWarning,
        auraMode,
        currentVolumePercent,
        activeThresholdPercent,
        todayExposureMinutes,
        effectiveExposureLimitMinutes,
        warningTimeoutMinutes,
        alertNotificationsEnabled,
        timerNotificationsEnabled
    ) {
        if (!rulesEnabled) {
            volumeDialogState = null
            breakDialogState = null
            showFirstBreakDialogState = null
            cancelRhythmGuardAlertNotification(context)
            if (auraMode == AppSettings.RHYTHM_GUARD_MODE_OFF) {
                appSettings.clearRhythmGuardListeningTimeout()
            }
            return@LaunchedEffect
        }

        if (isListeningTimeoutActive) {
            volumeDialogState = null
            breakDialogState = null
            showFirstBreakDialogState = null
            cancelRhythmGuardAlertNotification(context)
            return@LaunchedEffect
        }

        if (!needsVolumeWarning && lastWarningType == RhythmGuardWarningType.VOLUME) {
            volumeDialogState = null
            cancelRhythmGuardAlertNotification(context)
        }

        val warningType = when {
            needsVolumeWarning -> RhythmGuardWarningType.VOLUME
            needsExposureWarning -> RhythmGuardWarningType.EXPOSURE
            else -> null
        }

        if (warningType == null) {
            volumeDialogState = null
            breakDialogState = null
            showFirstBreakDialogState = null
            cancelRhythmGuardAlertNotification(context)
            return@LaunchedEffect
        }

        volumeDialogState?.let { existing ->
            if (!needsVolumeWarning || existing.mode != auraMode) {
                volumeDialogState = null
            }
        }
        breakDialogState?.let { existing ->
            if (!needsExposureWarning || existing.mode != auraMode) {
                breakDialogState = null
            }
        }

        val now = System.currentTimeMillis()
        val warningCooldownMs = warningTimeoutMinutes.coerceIn(1, 60) * 60 * 1000L
        val cooldownElapsed = now - lastWarningAt > warningCooldownMs
        val warningTypeChanged = warningType != lastWarningType
        val riskStepIncreased = when (warningType) {
            RhythmGuardWarningType.VOLUME -> currentVolumePercent >= (lastWarningVolumePercent + 5)
            RhythmGuardWarningType.EXPOSURE -> todayExposureMinutes >= (lastWarningExposureMinutes + 15)
        }
        val exposureGrowthSinceLastTimeout = if (lastTimeoutTriggeredExposureMinutes >= 0) {
            todayExposureMinutes - lastTimeoutTriggeredExposureMinutes
        } else {
            Int.MAX_VALUE
        }
        val exposureCooldownEligible = cooldownElapsed && exposureGrowthSinceLastTimeout >= 10
        val canShow = when (warningType) {
            RhythmGuardWarningType.VOLUME -> warningTypeChanged || cooldownElapsed || riskStepIncreased
            RhythmGuardWarningType.EXPOSURE -> {
                val switchedFromVolume = warningTypeChanged && lastWarningType == RhythmGuardWarningType.VOLUME
                val canEscalateFromVolume = riskStepIncreased || exposureCooldownEligible
                if (switchedFromVolume && !canEscalateFromVolume) {
                    false
                } else {
                    warningTypeChanged || riskStepIncreased || exposureCooldownEligible
                }
            }
        }

        if (canShow && volumeDialogState == null && breakDialogState == null) {
            when (warningType) {
                RhythmGuardWarningType.VOLUME -> {
                    val riskLevel = rhythmGuardResolveRiskLevel(
                        currentVolumePercent = currentVolumePercent,
                        safeThresholdPercent = activeThresholdPercent,
                        exposureMinutes = todayExposureMinutes,
                        exposureLimitMinutes = effectiveExposureLimitMinutes
                    )
                    volumeDialogState = RhythmGuardVolumeWarningDialogState(
                        mode = auraMode,
                        currentVolumePercent = currentVolumePercent,
                        safeThresholdPercent = activeThresholdPercent,
                        suggestedVolume = activeThreshold,
                        riskLevel = riskLevel
                    )

                    if (alertNotificationsEnabled) {
                        showRhythmGuardAlertNotification(
                            context = context,
                            title = context.getString(R.string.settings_rhythm_guard_notification_alert_title),
                            text = context.getString(
                                R.string.settings_rhythm_guard_warning_dialog_volume_message,
                                currentVolumePercent,
                                activeThresholdPercent
                            ),
                            riskLevel = riskLevel
                        )
                    }
                }
                RhythmGuardWarningType.EXPOSURE -> {
                    val riskLevel = rhythmGuardResolveRiskLevel(
                        currentVolumePercent = currentVolumePercent,
                        safeThresholdPercent = activeThresholdPercent,
                        exposureMinutes = todayExposureMinutes,
                        exposureLimitMinutes = effectiveExposureLimitMinutes
                    )

                    if (!firstBreakSeen) {
                        showFirstBreakDialogState = RhythmGuardBreakDialogState(
                            mode = auraMode,
                            estimatedTodayMinutes = todayExposureMinutes,
                            recommendedDailyMinutes = effectiveExposureLimitMinutes,
                            riskLevel = riskLevel
                        )
                    } else {
                        if (auraMode == AppSettings.RHYTHM_GUARD_MODE_AUTO) {
                            val now = System.currentTimeMillis()
                            val safeBreakMinutes = configuredBreakResumeMinutes.coerceIn(1, 180)
                            val timeoutEnd = now + safeBreakMinutes * 60_000L
                            val reason = context.getString(
                                R.string.settings_rhythm_guard_timeout_reason_auto,
                                formattedTodayExposure,
                                formattedExposureLimit
                            )
                            timeoutStartedAtMs = now
                            lastTimeoutTriggeredExposureMinutes = todayExposureMinutes
                            appSettings.setRhythmGuardBreakResumeMinutes(safeBreakMinutes)
                            appSettings.setRhythmGuardListeningTimeout(
                                untilEpochMs = timeoutEnd,
                                reason = reason,
                                startedAtEpochMs = now
                            )
                            musicViewModel.pauseForRhythmGuardTimeout(reason = "auto break start")
                            RhythmGuardTimeoutActivity.start(
                                context = context,
                                reason = reason,
                                timeoutUntilMs = timeoutEnd,
                                timeoutStartedAtMs = now
                            )

                            if (alertNotificationsEnabled) {
                                showRhythmGuardAlertNotification(
                                    context = context,
                                    title = context.getString(R.string.settings_rhythm_guard_notification_alert_title),
                                    text = context.getString(
                                        R.string.settings_rhythm_guard_break_dialog_message,
                                        formattedTodayExposure,
                                        formattedExposureLimit
                                    ),
                                    riskLevel = riskLevel
                                )
                            }

                            if (timerNotificationsEnabled) {
                                showRhythmGuardTimerNotification(
                                    context = context,
                                    title = context.getString(R.string.settings_rhythm_guard_notification_timer_active_title),
                                    text = context.getString(
                                        R.string.settings_rhythm_guard_notification_timer_active_text,
                                        rhythmGuardFormatCountdown(safeBreakMinutes.toLong() * 60L)
                                    ),
                                    remainingSeconds = safeBreakMinutes.toLong() * 60L,
                                    totalSeconds = safeBreakMinutes.toLong() * 60L
                                )
                            }
                        } else {
                            breakDialogState = RhythmGuardBreakDialogState(
                                mode = auraMode,
                                estimatedTodayMinutes = todayExposureMinutes,
                                recommendedDailyMinutes = effectiveExposureLimitMinutes,
                                riskLevel = riskLevel
                            )

                            if (alertNotificationsEnabled) {
                                showRhythmGuardAlertNotification(
                                    context = context,
                                    title = context.getString(R.string.settings_rhythm_guard_notification_alert_title),
                                    text = context.getString(
                                        R.string.settings_rhythm_guard_break_dialog_message,
                                        formattedTodayExposure,
                                        formattedExposureLimit
                                    ),
                                    riskLevel = riskLevel
                                )
                            }
                        }
                    }
                }
            }
            lastWarningType = warningType
            lastWarningAt = now
            lastWarningVolumePercent = currentVolumePercent
            lastWarningExposureMinutes = todayExposureMinutes
        }
    }

    val showZeroVolumeDialog by musicViewModel.showZeroVolumePauseDialog.collectAsState()
    val useSystemVolumeForDialog by appSettings.useSystemVolume.collectAsState()
    if (showZeroVolumeDialog) {
        AlertDialog(
            onDismissRequest = { musicViewModel.dismissZeroVolumePauseDialog() },
            icon = {
                Icon(
                    imageVector = RhythmIcons.VolumeOff,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = context.getString(R.string.settings_stop_playback_on_zero_volume),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = context.getString(R.string.playback_paused_zero_volume),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Restore volume to a safe audible level (30%)
                        val restoreVolume = 0.30f
                        if (useSystemVolumeForDialog) {
                            setSystemMusicVolumeFraction(context, restoreVolume)
                        }
                        musicViewModel.setVolume(restoreVolume)
                        musicViewModel.dismissZeroVolumePauseDialog()
                    }
                ) {
                    Icon(
                        imageVector = RhythmIcons.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = context.getString(R.string.zero_volume_raise_volume))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { musicViewModel.dismissZeroVolumePauseDialog() }
                ) {
                    Text(text = context.getString(R.string.action_ok))
                }
            }
        )
    }


    val volumeState = volumeDialogState
    if (volumeState != null) {
        AlertDialog(
            onDismissRequest = { volumeDialogState = null },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            iconContentColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            textContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = {
                Icon(
                    imageVector = RhythmIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = context.getString(R.string.settings_rhythm_guard_warning_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = context.getString(
                            R.string.settings_rhythm_guard_warning_dialog_volume_message,
                            volumeState.currentVolumePercent,
                            volumeState.safeThresholdPercent
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = context.getString(
                            R.string.settings_rhythm_guard_risk_level_label,
                            rhythmGuardRiskLabel(context, volumeState.riskLevel)
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                    Text(
                        text = context.getString(
                            R.string.settings_rhythm_guard_risk_action_label,
                            rhythmGuardRiskAction(context, volumeState.riskLevel)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        setSystemMusicVolumeFraction(context, volumeState.suggestedVolume)
                        musicViewModel.setVolume(volumeState.suggestedVolume)
                        appSettings.setAudioNormalization(true)
                        appSettings.setReplayGain(false)
                        if (auraMode == AppSettings.RHYTHM_GUARD_MODE_AUTO) {
                            appSettings.applyRhythmGuardAutoProfileForAge(auraAge)
                        }
                        volumeDialogState = null
                    }
                ) {
                    Icon(
                        imageVector = RhythmIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.settings_rhythm_guard_warning_dialog_apply))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { volumeDialogState = null }) {
                    Icon(
                        imageVector = RhythmIcons.SettingsFilled,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.settings_rhythm_guard_warning_dialog_dismiss))
                }
            }
        )
    }

    val firstBreakState = showFirstBreakDialogState
    if (firstBreakState != null) {
        AlertDialog(
            onDismissRequest = {
                // Do not dismiss on outside click
            },
            icon = {
                Icon(
                    imageVector = RhythmIcons.Security,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = context.getString(R.string.settings_rhythm_guard_first_break_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = context.getString(R.string.settings_rhythm_guard_first_break_dialog_message),
                    style = MaterialTheme.typography.bodyMedium
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
                            appSettings.setRhythmGuardFirstBreakSeen(true)
                            showFirstBreakDialogState = null
                            if (firstBreakState.mode == AppSettings.RHYTHM_GUARD_MODE_AUTO) {
                                val now = System.currentTimeMillis()
                                val safeBreakMinutes = configuredBreakResumeMinutes.coerceIn(1, 180)
                                val timeoutEnd = now + safeBreakMinutes * 60_000L
                                val reason = context.getString(
                                    R.string.settings_rhythm_guard_timeout_reason_auto,
                                    formattedTodayExposure,
                                    formattedExposureLimit
                                )
                                timeoutStartedAtMs = now
                                lastTimeoutTriggeredExposureMinutes = todayExposureMinutes
                                appSettings.setRhythmGuardBreakResumeMinutes(safeBreakMinutes)
                                appSettings.setRhythmGuardListeningTimeout(
                                    untilEpochMs = timeoutEnd,
                                    reason = reason,
                                    startedAtEpochMs = now
                                )
                                musicViewModel.pauseForRhythmGuardTimeout(reason = "auto break start")
                                RhythmGuardTimeoutActivity.start(
                                    context = context,
                                    reason = reason,
                                    timeoutUntilMs = timeoutEnd,
                                    timeoutStartedAtMs = now
                                )
                            } else {
                                breakDialogState = firstBreakState
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(text = context.getString(R.string.settings_rhythm_guard_first_break_dialog_start))
                    }

                    OutlinedButton(
                        onClick = {
                            appSettings.setRhythmGuardMode(AppSettings.RHYTHM_GUARD_MODE_OFF)
                            appSettings.setRhythmGuardFirstBreakSeen(true)
                            showFirstBreakDialogState = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(text = context.getString(R.string.settings_rhythm_guard_first_break_dialog_turn_off))
                    }
                }
            },
            dismissButton = {}
        )
    }

    val breakState = breakDialogState
    if (breakState != null) {
        var showBreakDurationPicker by remember { mutableStateOf(false) }
        var showDelayDurationPicker by remember { mutableStateOf(false) }

        val formattedBreakDuration = rhythmGuardFormatDurationFromMinutes(breakResumeMinutes.coerceIn(1, 180))

        AlertDialog(
            onDismissRequest = {
                showBreakDurationPicker = false
                showDelayDurationPicker = false
                breakDialogState = null
            },
            icon = {
                Icon(
                    imageVector = RhythmIcons.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = context.getString(R.string.settings_rhythm_guard_break_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = context.getString(
                            R.string.settings_rhythm_guard_break_dialog_message,
                            formattedTodayExposure,
                            formattedExposureLimit
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = context.getString(
                            R.string.settings_rhythm_guard_risk_level_label,
                            rhythmGuardRiskLabel(context, breakState.riskLevel)
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = context.getString(
                            R.string.settings_rhythm_guard_risk_action_label,
                            rhythmGuardRiskAction(context, breakState.riskLevel)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = context.getString(R.string.settings_rhythm_guard_break_dialog_schedule_title),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = formattedBreakDuration,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = { showBreakDurationPicker = true }) {
                                Icon(
                                    imageVector = RhythmIcons.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(context.getString(R.string.settings_rhythm_guard_break_dialog_pick_time))
                            }
                        }
                    }
                }
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
                        onClick = { startTimeoutBreak(breakResumeMinutes) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.settings_rhythm_guard_break_dialog_pause_only))
                    }

                    OutlinedButton(
                        onClick = { showDelayDurationPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = RhythmIcons.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.settings_rhythm_guard_break_dialog_pause_close))
                    }
                }
            },
            dismissButton = {}
        )

        if (showBreakDurationPicker) {
            RhythmGuardTimePickerDialog(
                title = context.getString(R.string.settings_rhythm_guard_break_dialog_picker_extend_title),
                initialMinutes = breakResumeMinutes,
                onDismiss = { showBreakDurationPicker = false },
                onConfirm = { selectedMinutes ->
                    breakResumeMinutes = selectedMinutes.coerceIn(1, 180)
                    showBreakDurationPicker = false
                }
            )
        }

        if (showDelayDurationPicker) {
            RhythmGuardTimePickerDialog(
                title = context.getString(R.string.settings_rhythm_guard_break_dialog_picker_delay_title),
                initialMinutes = breakDelayMinutes,
                onDismiss = { showDelayDurationPicker = false },
                onConfirm = { selectedMinutes ->
                    val safeDelayMinutes = selectedMinutes.coerceIn(1, 180)
                    breakDelayMinutes = safeDelayMinutes
                    showDelayDurationPicker = false
                    delayBreakReminder(safeDelayMinutes)
                }
            )
        }
    }

    val nowEpochMs = System.currentTimeMillis()
    val isTimeoutBubbleActive = timeoutUntilMs > nowEpochMs && resumeCountdownSeconds > 0L
    val isPendingStartBubbleActive =
        pendingBreakStartAtMs > nowEpochMs && pendingBreakStartCountdownSeconds in 1L..60L
    val bubbleVisible = isTimeoutBubbleActive || isPendingStartBubbleActive

    val timeoutTotalSeconds = ((timeoutUntilMs - timeoutStartedAtMs).coerceAtLeast(1_000L) / 1000f)
    val timeoutElapsedSeconds = (timeoutTotalSeconds - resumeCountdownSeconds.toFloat()).coerceAtLeast(0f)
    val timeoutProgress = (timeoutElapsedSeconds / timeoutTotalSeconds).coerceIn(0f, 1f)
    val pendingStartProgress = ((60f - pendingBreakStartCountdownSeconds.toFloat()) / 60f).coerceIn(0f, 1f)
    val bubbleCountdownSeconds = if (isTimeoutBubbleActive) {
        resumeCountdownSeconds
    } else {
        pendingBreakStartCountdownSeconds
    }
    val bubbleProgress = if (isTimeoutBubbleActive) {
        timeoutProgress
    } else {
        pendingStartProgress
    }
    val bubbleLabel = if (isTimeoutBubbleActive) {
        if (timeoutReason.isNotBlank()) timeoutReason else context.getString(
            R.string.settings_rhythm_guard_resume_countdown_label
        )
    } else if (isPendingStartBubbleActive) {
        context.getString(R.string.settings_rhythm_guard_delay_break_countdown_label)
    } else {
        ""
    }
    val bubbleValueResId = if (isTimeoutBubbleActive) {
        R.string.settings_rhythm_guard_resume_countdown_value
    } else if (isPendingStartBubbleActive) {
        R.string.settings_rhythm_guard_start_countdown_value
    } else {
        R.string.settings_rhythm_guard_resume_countdown_value
    }
    val density = androidx.compose.ui.platform.LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        val scope = rememberCoroutineScope()

        val maxHeightDp = maxHeight
        val collapsedYDp = 12.dp
        val expandedYDp = (maxHeightDp * 0.35f).coerceAtLeast(150.dp)

        val collapsedYPx = with(density) { collapsedYDp.toPx() }
        val expandedYPx = with(density) { expandedYDp.toPx() }

        val animatableY = remember { Animatable(expandedYPx) }

        LaunchedEffect(isBubbleCollapsed, expandedYPx) {
            animatableY.animateTo(
                targetValue = if (isBubbleCollapsed) collapsedYPx else expandedYPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        AnimatedVisibility(
            visible = bubbleVisible,
            enter = fadeIn(animationSpec = tween(220)) + scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut(animationSpec = tween(200)) + scaleOut(animationSpec = tween(180))
        ) {
            RhythmGuardResumeCountdownBubble(
                countdownText = rhythmGuardFormatCountdown(bubbleCountdownSeconds),
                progress = bubbleProgress,
                label = bubbleLabel,
                countdownValueResId = bubbleValueResId,
                isCollapsed = isBubbleCollapsed,
                modifier = Modifier
                    .align(
                        when (bubbleHorizontalPos) {
                            0 -> Alignment.TopStart
                            1 -> Alignment.TopCenter
                            else -> Alignment.TopEnd
                        }
                    )
                    .offset { IntOffset(rawDragXDp.dp.roundToPx(), animatableY.value.toDp().roundToPx()) }
                    .pointerInput(timeoutReason, timeoutUntilMs, timeoutStartedAtMs, isTimeoutBubbleActive, isBubbleCollapsed) {
                        detectTapGestures(onTap = {
                            if (isBubbleCollapsed) {
                                isBubbleCollapsed = false
                            } else {
                                if (isTimeoutBubbleActive) {
                                    RhythmGuardTimeoutActivity.start(
                                        context = context,
                                        reason = timeoutReason.ifBlank {
                                            context.getString(R.string.settings_rhythm_guard_timeout_activity_default_reason)
                                        },
                                        timeoutUntilMs = timeoutUntilMs,
                                        timeoutStartedAtMs = timeoutStartedAtMs
                                    )
                                }
                            }
                        })
                    }
                    .pointerInput(bubbleHorizontalPos, collapsedYPx, expandedYPx) {
                        detectDragGestures(
                            onDragEnd = {
                                bubbleHorizontalPos = when (bubbleHorizontalPos) {
                                    0 -> { // Currently Left
                                        if (rawDragXDp > 180f) 2
                                        else if (rawDragXDp > 60f) 1
                                        else 0
                                    }
                                    1 -> { // Currently Middle
                                        if (rawDragXDp > 60f) 2
                                        else if (rawDragXDp < -60f) 0
                                        else 1
                                    }
                                    else -> { // Currently Right
                                        if (rawDragXDp < -180f) 0
                                        else if (rawDragXDp < -60f) 1
                                        else 2
                                    }
                                }
                                rawDragXDp = 0f

                                val midPoint = (collapsedYPx + expandedYPx) / 2f
                                isBubbleCollapsed = animatableY.value < midPoint
                            },
                            onDragCancel = {
                                rawDragXDp = 0f
                                scope.launch {
                                    animatableY.animateTo(
                                        targetValue = if (isBubbleCollapsed) collapsedYPx else expandedYPx,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val deltaXDp = dragAmount.x / density.density
                            rawDragXDp += deltaXDp

                            val newY = (animatableY.value + dragAmount.y).coerceIn(collapsedYPx, expandedYPx)
                            scope.launch {
                                animatableY.snapTo(newY)
                            }
                        }
                    }
            )
        }
    }
}

fun rhythmGuardResolveRiskLevel(
    currentVolumePercent: Int,
    safeThresholdPercent: Int,
    exposureMinutes: Int,
    exposureLimitMinutes: Int
): RhythmGuardRiskLevel {
    val safeVolume = safeThresholdPercent.coerceAtLeast(1)
    val safeExposure = exposureLimitMinutes.coerceAtLeast(1)

    val volumeRatio = currentVolumePercent.toFloat() / safeVolume.toFloat()
    val exposureRatio = exposureMinutes.toFloat() / safeExposure.toFloat()
    val combinedRatio = maxOf(volumeRatio, exposureRatio, (volumeRatio + exposureRatio) / 2f)

    return when {
        combinedRatio >= 1.50f -> RhythmGuardRiskLevel.SEVERE
        combinedRatio >= 1.25f -> RhythmGuardRiskLevel.HIGH
        combinedRatio >= 1.00f -> RhythmGuardRiskLevel.MODERATE
        else -> RhythmGuardRiskLevel.LOW
    }
}

private fun rhythmGuardRiskLabel(
    context: android.content.Context,
    level: RhythmGuardRiskLevel
): String {
    val labelResId = when (level) {
        RhythmGuardRiskLevel.LOW -> R.string.settings_rhythm_guard_risk_level_low
        RhythmGuardRiskLevel.MODERATE -> R.string.settings_rhythm_guard_risk_level_moderate
        RhythmGuardRiskLevel.HIGH -> R.string.settings_rhythm_guard_risk_level_high
        RhythmGuardRiskLevel.SEVERE -> R.string.settings_rhythm_guard_risk_level_severe
    }
    return context.getString(labelResId)
}

private fun rhythmGuardRiskAction(
    context: android.content.Context,
    level: RhythmGuardRiskLevel
): String {
    val actionResId = when (level) {
        RhythmGuardRiskLevel.LOW -> R.string.settings_rhythm_guard_risk_action_low
        RhythmGuardRiskLevel.MODERATE -> R.string.settings_rhythm_guard_risk_action_moderate
        RhythmGuardRiskLevel.HIGH -> R.string.settings_rhythm_guard_risk_action_high
        RhythmGuardRiskLevel.SEVERE -> R.string.settings_rhythm_guard_risk_action_severe
    }
    return context.getString(actionResId)
}

private fun rhythmGuardFormatDurationFromMinutes(minutes: Int): String {
    val safeMinutes = minutes.coerceAtLeast(0)
    val days = safeMinutes / (24 * 60)
    val hours = (safeMinutes % (24 * 60)) / 60
    val mins = safeMinutes % 60

    return when {
        days > 0 && hours > 0 && mins > 0 -> "${days}d ${hours}h ${mins}m"
        days > 0 && hours > 0 -> "${days}d ${hours}h"
        days > 0 && mins > 0 -> "${days}d ${mins}m"
        days > 0 -> "${days}d"
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}

private fun rhythmGuardFormatCountdown(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val secs = safeSeconds % 60

    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, secs)
    }
}

private fun showRhythmGuardAlertNotification(
    context: android.content.Context,
    title: String,
    text: String,
    riskLevel: RhythmGuardRiskLevel
) {
    val notificationManager =
        context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureRhythmGuardNotificationChannels(context, notificationManager)

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        8101,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val priority = when (riskLevel) {
        RhythmGuardRiskLevel.SEVERE,
        RhythmGuardRiskLevel.HIGH -> NotificationCompat.PRIORITY_HIGH
        RhythmGuardRiskLevel.MODERATE -> NotificationCompat.PRIORITY_DEFAULT
        RhythmGuardRiskLevel.LOW -> NotificationCompat.PRIORITY_LOW
    }

    val notification = NotificationCompat.Builder(context, RHYTHM_GUARD_ALERT_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(priority)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(pendingIntent)
        .build()

    notificationManager.notify(RHYTHM_GUARD_ALERT_NOTIFICATION_ID, notification)
}

private fun showRhythmGuardTimerNotification(
    context: android.content.Context,
    title: String,
    text: String,
    remainingSeconds: Long,
    totalSeconds: Long
) {
    val notificationManager =
        context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureRhythmGuardNotificationChannels(context, notificationManager)

    val safeTotal = totalSeconds.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val safeRemaining = remainingSeconds.coerceIn(0L, safeTotal.toLong()).toInt()
    val completed = (safeTotal - safeRemaining).coerceIn(0, safeTotal)

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        8102,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, RHYTHM_GUARD_TIMER_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(
            NotificationCompat.BigTextStyle().bigText(
                "$text\n${context.getString(R.string.settings_rhythm_guard_notification_tap_open)}"
            )
        )
        .setProgress(safeTotal, completed, false)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(pendingIntent)
        .build()

    notificationManager.notify(RHYTHM_GUARD_TIMER_NOTIFICATION_ID, notification)
}

private fun cancelRhythmGuardTimerNotification(context: android.content.Context) {
    val notificationManager =
        context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(RHYTHM_GUARD_TIMER_NOTIFICATION_ID)
}

private fun cancelRhythmGuardAlertNotification(context: android.content.Context) {
    val notificationManager =
        context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(RHYTHM_GUARD_ALERT_NOTIFICATION_ID)
}

private fun ensureRhythmGuardNotificationChannels(
    context: android.content.Context,
    notificationManager: NotificationManager
) {

    val alertChannel = NotificationChannel(
        RHYTHM_GUARD_ALERT_CHANNEL_ID,
        context.getString(R.string.service_rhythm_guard_alerts),
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = context.getString(R.string.service_rhythm_guard_alerts_desc)
        enableVibration(true)
    }

    val timerChannel = NotificationChannel(
        RHYTHM_GUARD_TIMER_CHANNEL_ID,
        context.getString(R.string.service_rhythm_guard_timers),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = context.getString(R.string.service_rhythm_guard_timers_desc)
        enableVibration(false)
        setShowBadge(false)
    }

    notificationManager.createNotificationChannel(alertChannel)
    notificationManager.createNotificationChannel(timerChannel)
}

@Composable
private fun RhythmGuardResumeCountdownBubble(
    countdownText: String,
    progress: Float,
    label: String,
    countdownValueResId: Int,
    isCollapsed: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progressValue = progress.coerceIn(0f, 1f)

    val transition = updateTransition(targetState = isCollapsed, label = "bubbleCollapseTransition")

    val shapeCorner by transition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
        label = "shapeCorner"
    ) { collapsed ->
        if (collapsed) 16.dp else 22.dp
    }

    val horizontalPadding by transition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
        label = "horizontalPadding"
    ) { collapsed ->
        if (collapsed) 10.dp else 14.dp
    }

    val verticalPadding by transition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
        label = "verticalPadding"
    ) { collapsed ->
        if (collapsed) 8.dp else 10.dp
    }

    val iconSize by transition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
        label = "iconSize"
    ) { collapsed ->
        if (collapsed) 24.dp else 34.dp
    }

    val innerIconSize by transition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
        label = "innerIconSize"
    ) { collapsed ->
        if (collapsed) 12.dp else 16.dp
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(shapeCorner),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(iconSize),
                contentAlignment = Alignment.Center
            ) {
                RhythmWavyProgressLoader(
                    progress = progressValue,
                    modifier = Modifier.fillMaxSize(),
                    indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = RhythmIcons.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(innerIconSize)
                    )
                }
            }

            if (!isCollapsed) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Text(
                        text = context.getString(countdownValueResId, countdownText),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                Text(
                    text = countdownText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RhythmGuardTimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val context = LocalContext.current
    val safeInitialMinutes = initialMinutes.coerceIn(1, 180)
    val timePickerState = rememberTimePickerState(
        initialHour = safeInitialMinutes / 60,
        initialMinute = safeInitialMinutes % 60,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            TimePicker(state = timePickerState)
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedMinutes = (timePickerState.hour * 60 + timePickerState.minute)
                        .coerceAtLeast(1)
                    onConfirm(selectedMinutes)
                }
            ) {
                Text(context.getString(R.string.bottomsheet_timer_set))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(context.getString(R.string.bottomsheet_cancel))
            }
        }
    )
}

@Composable
private fun rememberSystemMusicVolumeFraction(context: android.content.Context): Float {
    var systemVolume by remember { mutableFloatStateOf(0f) }

    androidx.compose.runtime.DisposableEffect(context) {
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager

        fun refreshVolume() {
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            systemVolume = if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 0f
        }

        refreshVolume()

        val observer = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) {
                refreshVolume()
            }
        }

        context.contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI,
            true,
            observer
        )

        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    return systemVolume
}

private fun setSystemMusicVolumeFraction(context: android.content.Context, fraction: Float) {
    val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        ?: return
    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
    if (maxVolume <= 0) return
    val targetVolume = (fraction.coerceIn(0f, 1f) * maxVolume).toInt().coerceIn(0, maxVolume)
    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
}

/**
 * Mode selector screen that allows users to switch between Local and Streaming modes.
 * Can be shown as a full screen or as a bottom sheet.
 */
@Composable
fun ModeSelectorScreen(
    currentMode: AppMode,
    onModeSelected: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.rhythmnavigation_choose_your_experience),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.rhythmnavigation_switch_between_local_music),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Local Mode Card
            ModeCard(
                title = stringResource(R.string.rhythmnavigation_local),
                description = stringResource(R.string.rhythmnavigation_local_desc),
                icon = if (currentMode == AppMode.LOCAL) RhythmIcons.MusicNote else RhythmIcons.MusicNote,
                isSelected = currentMode == AppMode.LOCAL,
                onClick = { onModeSelected(AppMode.LOCAL) },
                modifier = Modifier.weight(1f)
            )
            
            // Streaming Mode Card
            ModeCard(
                title = stringResource(R.string.rhythmnavigation_streaming),
                description = stringResource(R.string.rhythmnavigation_streaming_desc),
                icon = if (currentMode == AppMode.STREAMING) MaterialSymbolIcon("cloud_queue", filled = true) else MaterialSymbolIcon("cloud_queue"),
                isSelected = currentMode == AppMode.STREAMING,
                onClick = { onModeSelected(AppMode.STREAMING) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Card component for displaying a mode option in the mode selector.
 */
@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                modifier = Modifier.size(64.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier.size(32.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Selection indicator
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = stringResource(R.string.bottomsheet_active_device),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact mode switcher that can be placed in a navigation bar or header.
 */
@Composable
fun CompactModeSwitcher(
    currentMode: AppMode,
    onModeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onModeToggle),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Local indicator
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (currentMode == AppMode.LOCAL) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.padding(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RhythmIcons.MusicNote,
                        contentDescription = stringResource(R.string.rhythmnavigation_local),
                        modifier = Modifier.size(16.dp),
                        tint = if (currentMode == AppMode.LOCAL) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (currentMode == AppMode.LOCAL) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.rhythmnavigation_local),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            
            // Streaming indicator
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (currentMode == AppMode.STREAMING) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.padding(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("cloud_queue"),
                        contentDescription = stringResource(R.string.rhythmnavigation_streaming),
                        modifier = Modifier.size(16.dp),
                        tint = if (currentMode == AppMode.STREAMING) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (currentMode == AppMode.STREAMING) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.rhythmnavigation_stream),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
