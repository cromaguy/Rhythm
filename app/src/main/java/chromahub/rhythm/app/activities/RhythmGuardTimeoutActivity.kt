/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.activities

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.AppSettings
import kotlinx.coroutines.launch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.ui.graphics.Color

import chromahub.rhythm.app.shared.presentation.components.common.RhythmWavyProgressLoader
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import chromahub.rhythm.app.shared.data.repository.StatsTimeRange
import chromahub.rhythm.app.ui.theme.RhythmTheme
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import java.util.Locale
import chromahub.rhythm.app.util.windowScreenWidthDp
import chromahub.rhythm.app.util.windowScreenHeightDp

class RhythmGuardTimeoutActivity : AppCompatActivity() {

    private val appSettings by lazy { AppSettings.getInstance(applicationContext) }

    private fun cancelRhythmGuardTimerNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(RHYTHM_GUARD_TIMER_NOTIFICATION_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val timeoutUntilMs = intent.getLongExtra(EXTRA_TIMEOUT_UNTIL_MS, 0L)
        val timeoutStartedAtMs = intent.getLongExtra(EXTRA_TIMEOUT_STARTED_AT_MS, 0L)
        val launchNow = System.currentTimeMillis()
        val timeoutWasActiveAtLaunch =
            timeoutUntilMs > launchNow || appSettings.rhythmGuardTimeoutUntilMs.value > launchNow

        setContent {
            RhythmTheme {
                RhythmGuardTimeoutScreen(
                    reason = reason,
                    fallbackTimeoutUntilMs = timeoutUntilMs,
                    fallbackTimeoutStartedAtMs = timeoutStartedAtMs,
                    appSettings = appSettings,
                    onCloseScreen = {
                        finish()
                    },
                    onTimeoutFinished = { completedTimeoutUntilMs ->
                        val shouldApplyCooldown = completedTimeoutUntilMs > 0L || timeoutWasActiveAtLaunch
                        if (shouldApplyCooldown) {
                            val cooldownMinutes = appSettings.rhythmGuardPostTimeoutCooldownMinutes.value.coerceIn(1, 60)
                            val cooldownUntil = System.currentTimeMillis() + cooldownMinutes.toLong() * 60_000L
                            
                            lifecycleScope.launch {
                                val statsRepository = PlaybackStatsRepository.getInstance(applicationContext)
                                val todaySummary = runCatching {
                                    statsRepository.loadSummary(StatsTimeRange.TODAY)
                                }.getOrNull()
                                val dbDurationMs = todaySummary?.totalDurationMs ?: 0L
                                val currentMinutes = (dbDurationMs / 60000L).toInt().coerceAtLeast(0)
                                
                                appSettings.setRhythmGuardTimeoutCooldownWithLimit(cooldownUntil, currentMinutes + 15)
                                
                                cancelRhythmGuardTimerNotification()
                                appSettings.clearRhythmGuardListeningTimeout()
                                finish()
                            }
                        } else {
                            cancelRhythmGuardTimerNotification()
                            appSettings.clearRhythmGuardListeningTimeout()
                            finish()
                        }
                    }
                )
            }
        }
    }

    companion object {
        private const val RHYTHM_GUARD_TIMER_NOTIFICATION_ID = 1302
        private const val EXTRA_REASON = "extra_timeout_reason"
        private const val EXTRA_TIMEOUT_UNTIL_MS = "extra_timeout_until_ms"
        private const val EXTRA_TIMEOUT_STARTED_AT_MS = "extra_timeout_started_at_ms"

        fun start(
            context: Context,
            reason: String,
            timeoutUntilMs: Long,
            timeoutStartedAtMs: Long
        ) {
            val intent = Intent(context, RhythmGuardTimeoutActivity::class.java).apply {
                putExtra(EXTRA_REASON, reason)
                putExtra(EXTRA_TIMEOUT_UNTIL_MS, timeoutUntilMs)
                putExtra(EXTRA_TIMEOUT_STARTED_AT_MS, timeoutStartedAtMs)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun RhythmGuardTimeoutScreen(
    reason: String,
    fallbackTimeoutUntilMs: Long,
    fallbackTimeoutStartedAtMs: Long,
    appSettings: AppSettings,
    onCloseScreen: () -> Unit,
    onTimeoutFinished: (Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val useHoursFormat by appSettings.useHoursInTimeFormat.collectAsState()
    val timeoutUntilMsState by appSettings.rhythmGuardTimeoutUntilMs.collectAsState()
    val timeoutReasonState by appSettings.rhythmGuardTimeoutReason.collectAsState()
    val timeoutStartedAtMsState by appSettings.rhythmGuardTimeoutStartedAtMs.collectAsState()
    val defaultBreakResumeMinutes by appSettings.rhythmGuardBreakResumeMinutes.collectAsState()
    var fallbackConsumed by rememberSaveable { mutableStateOf(false) }
    val timeoutUntilMs = when {
        timeoutUntilMsState > 0L -> {
            fallbackConsumed = true
            timeoutUntilMsState
        }
        !fallbackConsumed && fallbackTimeoutUntilMs > System.currentTimeMillis() -> fallbackTimeoutUntilMs
        else -> 0L
    }
    val timeoutStartedAtMs = if (timeoutStartedAtMsState > 0L && timeoutStartedAtMsState < timeoutUntilMs) {
        timeoutStartedAtMsState
    } else if (fallbackTimeoutStartedAtMs > 0L) {
        fallbackTimeoutStartedAtMs
    } else {
        val fallbackDurationMs = defaultBreakResumeMinutes.coerceIn(1, 180) * 60_000L
        (timeoutUntilMs - fallbackDurationMs).coerceAtLeast(0L)
    }

    var remainingSeconds by remember(timeoutUntilMs) {
        mutableLongStateOf(((timeoutUntilMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L))
    }

    val totalSeconds = ((timeoutUntilMs - timeoutStartedAtMs).coerceAtLeast(1_000L) / 1000f)
    val elapsedSeconds = (totalSeconds - remainingSeconds.toFloat()).coerceAtLeast(0f)
    val progress = (elapsedSeconds / totalSeconds).coerceIn(0f, 1f)

    LaunchedEffect(timeoutUntilMs) {
        if (timeoutUntilMs <= 0L) {
            onTimeoutFinished(0L)
            return@LaunchedEffect
        }

        while (true) {
            val left = ((timeoutUntilMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            remainingSeconds = left
            if (left <= 0L) break
            delay(1000L)
        }

        onTimeoutFinished(timeoutUntilMs)
    }

    // Prevent bypassing the timeout gate via system back.
    BackHandler(enabled = true) {}

    // Responsive sizing
    val isTablet = windowScreenWidthDp() >= 600
    val contentMaxWidth = if (isTablet) 1000.dp else 600.dp
    val startPadding = if (isTablet) 60.dp else 30.dp
    val endPadding = if (isTablet) 60.dp else 30.dp
    val topPadding = if (isTablet) 50.dp else 40.dp
    val bottomPadding = if (isTablet) 50.dp else 32.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        RotatingBackgroundCookies(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))

        // Timeout card container
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = if (isTablet) RoundedCornerShape(32.dp) else androidx.compose.ui.graphics.RectangleShape,
            border = if (isTablet) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null,
            tonalElevation = 0.dp,
            modifier = if (isTablet) {
                Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 750.dp)
                    .fillMaxHeight(0.9f)
            } else {
                Modifier.fillMaxSize()
            }
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
        ) {

            if (isTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = startPadding,
                            end = endPadding,
                            top = topPadding,
                            bottom = bottomPadding
                        ),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left column: Icon (centered, 72.dp), Title, Subtitle, description, branding, and action buttons
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Security,
                                contentDescription = stringResource(R.string.settings_rhythm_guard),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                        
                        Text(
                            text = context.getString(R.string.settings_rhythm_guard_timeout_activity_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val displayReason = if (timeoutReasonState.isNotBlank()) timeoutReasonState else reason
                        Text(
                            text = if (displayReason.isNotBlank()) {
                                displayReason
                            } else {
                                context.getString(R.string.settings_rhythm_guard_timeout_activity_default_reason)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = context.getString(R.string.settings_rhythm_guard_timeout_activity_comic_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.rhythm_splash_logo),
                                    contentDescription = stringResource(R.string.updates_rhythm_logo_cd),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.cd_rhythm_splash),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons moved to left column bottom
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val extendMinutes = 5
                            OutlinedButton(
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    if (timeoutUntilMs > now) {
                                        val updatedUntil = (timeoutUntilMs + extendMinutes.toLong() * 60_000L)
                                            .coerceAtMost(now + 24L * 60L * 60L * 1000L)
                                        val updatedReason = timeoutReasonState.ifBlank { reason }
                                        appSettings.setRhythmGuardListeningTimeout(
                                            untilEpochMs = updatedUntil,
                                            reason = updatedReason,
                                            startedAtEpochMs = timeoutStartedAtMs.takeIf { it > 0L } ?: now
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(32.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = context.getString(
                                        R.string.settings_rhythm_guard_timeout_activity_extend,
                                        extendMinutes
                                    ),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val exitButtonScale = remember { Animatable(1f) }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            exitButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                            exitButtonScale.animateTo(1f, animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessHigh
                                            ))
                                        }
                                        onCloseScreen()
                                    },
                                    modifier = Modifier
                                        .height(56.dp)
                                        .weight(1f)
                                        .graphicsLayer {
                                            scaleX = exitButtonScale.value
                                            scaleY = exitButtonScale.value
                                        },
                                    shape = RoundedCornerShape(32.dp)
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.ExitToApp,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        context.getString(R.string.settings_rhythm_guard_timeout_activity_exit),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                
                                val closeButtonScale = remember { Animatable(1f) }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            closeButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                            closeButtonScale.animateTo(1f, animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessHigh
                                            ))
                                        }
                                        val activity = context as? AppCompatActivity
                                        activity?.finishAffinity()
                                    },
                                    modifier = Modifier
                                        .height(56.dp)
                                        .weight(1f)
                                        .graphicsLayer {
                                            scaleX = closeButtonScale.value
                                            scaleY = closeButtonScale.value
                                        },
                                    shape = RoundedCornerShape(32.dp)
                                ) {
                                    Text(
                                        context.getString(R.string.settings_rhythm_guard_timeout_activity_close_app),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = RhythmIcons.Forward,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Right column: circular progress countdown loader, centered vertically/horizontally
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(176.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            RhythmWavyProgressLoader(
                                progress = progress,
                                modifier = Modifier.fillMaxSize(),
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = formatCountdown(remainingSeconds, useHoursFormat),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                }
            } else {
                // Mobile layout Column
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = startPadding,
                            end = endPadding,
                            top = topPadding,
                            bottom = bottomPadding
                        ),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Timeout icon centered at top
                    AnimatedVisibility(
                        visible = true,
                        enter = scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ) + fadeIn()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Security,
                                contentDescription = stringResource(R.string.settings_rhythm_guard),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }

                    // Left-aligned texts
                    Text(
                        text = context.getString(R.string.settings_rhythm_guard_timeout_activity_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val displayReason = if (timeoutReasonState.isNotBlank()) timeoutReasonState else reason
                    Text(
                        text = if (displayReason.isNotBlank()) {
                            displayReason
                        } else {
                            context.getString(R.string.settings_rhythm_guard_timeout_activity_default_reason)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    Text(
                        text = context.getString(R.string.settings_rhythm_guard_timeout_activity_comic_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // Circular progress with countdown
                    AnimatedVisibility(
                        visible = true,
                        enter = scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(animationSpec = tween(1000))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.size(176.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                RhythmWavyProgressLoader(
                                    progress = progress,
                                    modifier = Modifier.fillMaxSize(),
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.AccessTime,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = formatCountdown(remainingSeconds, useHoursFormat),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // App logo and name at center
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 28.dp)
                    ) {
                        AnimatedVisibility(
                            visible = true,
                            enter = scaleIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(animationSpec = tween(1000))
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.rhythm_splash_logo),
                                    contentDescription = stringResource(R.string.updates_rhythm_logo_cd),
                                    modifier = Modifier.size(80.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(3.dp))

                        AnimatedVisibility(
                            visible = true,
                            enter = scaleIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeIn(animationSpec = tween(800, delayMillis = 200))
                        ) {
                            Text(
                                text = stringResource(R.string.cd_rhythm_splash),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Action buttons at bottom
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val extendMinutes = 5
                        OutlinedButton(
                            onClick = {
                                val now = System.currentTimeMillis()
                                if (timeoutUntilMs > now) {
                                    val updatedUntil = (timeoutUntilMs + extendMinutes.toLong() * 60_000L)
                                        .coerceAtMost(now + 24L * 60L * 60L * 1000L)
                                    val updatedReason = timeoutReasonState.ifBlank { reason }
                                    appSettings.setRhythmGuardListeningTimeout(
                                        untilEpochMs = updatedUntil,
                                        reason = updatedReason,
                                        startedAtEpochMs = timeoutStartedAtMs.takeIf { it > 0L } ?: now
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(32.dp)
                        ) {
                            Icon(
                                imageVector = RhythmIcons.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = context.getString(
                                    R.string.settings_rhythm_guard_timeout_activity_extend,
                                    extendMinutes
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val exitButtonScale = remember { Animatable(1f) }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        exitButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                        exitButtonScale.animateTo(1f, animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessHigh
                                        ))
                                    }
                                    onCloseScreen()
                                },
                                modifier = Modifier
                                    .height(56.dp)
                                    .weight(1f)
                                    .graphicsLayer {
                                        scaleX = exitButtonScale.value
                                        scaleY = exitButtonScale.value
                                    },
                                shape = RoundedCornerShape(32.dp)
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.ExitToApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    context.getString(R.string.settings_rhythm_guard_timeout_activity_exit),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            val closeButtonScale = remember { Animatable(1f) }
                            Button(
                                onClick = {
                                    scope.launch {
                                        closeButtonScale.animateTo(0.92f, animationSpec = tween(100))
                                        closeButtonScale.animateTo(1f, animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessHigh
                                        ))
                                    }
                                    val activity = context as? AppCompatActivity
                                    activity?.finishAffinity()
                                },
                                modifier = Modifier
                                    .height(56.dp)
                                    .weight(1f)
                                    .graphicsLayer {
                                        scaleX = closeButtonScale.value
                                        scaleY = closeButtonScale.value
                                    },
                                shape = RoundedCornerShape(32.dp)
                            ) {
                                Text(
                                    context.getString(R.string.settings_rhythm_guard_timeout_activity_close_app),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = RhythmIcons.Forward,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

        }
    }
}

private fun formatCountdown(seconds: Long, useHoursFormat: Boolean): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    val totalMinutes = safeSeconds / 60L
    val secs = safeSeconds % 60L

    return if (useHoursFormat && totalMinutes >= 60L) {
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", totalMinutes, secs)
    }
}

@Composable
private fun RotatingBackgroundCookies(color: Color) {
    val lowerY = remember { Animatable(-600f) }
    val upperY = remember { Animatable(-1000f) }

    LaunchedEffect(Unit) {
        this.launch {
            lowerY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
        }
        
        upperY.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.LinearEasing)
        )
        
        this.launch {
            lowerY.animateTo(
                targetValue = 40f,
                animationSpec = tween(durationMillis = 80, easing = androidx.compose.animation.core.FastOutLinearInEasing)
            )
            lowerY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        
        this.launch {
            upperY.animateTo(
                targetValue = -60f,
                animationSpec = tween(durationMillis = 120, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
            )
            upperY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }


    val infiniteTransition = rememberInfiniteTransition(label = "cookieRotation")
    val rotationLower by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 50000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lowerRotation"
    )
    val rotationUpper by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "upperRotation"
    )

    val cookie6Shape = rememberExpressiveShape("COOKIE_6")
    val cookie12Shape = rememberExpressiveShape("COOKIE_12")

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(460.dp)
                .graphicsLayer {
                    translationX = -120.dp.toPx()
                    translationY = (140.dp.toPx() + lowerY.value.dp.toPx())
                    rotationZ = rotationLower + 15f
                }
                .clip(cookie6Shape)
                .background(color)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(340.dp)
                .graphicsLayer {
                    translationX = 80.dp.toPx()
                    translationY = (-100.dp.toPx() + upperY.value.dp.toPx())
                    rotationZ = rotationUpper - 20f
                }
                .clip(cookie12Shape)
                .background(color)
        )
    }
}

