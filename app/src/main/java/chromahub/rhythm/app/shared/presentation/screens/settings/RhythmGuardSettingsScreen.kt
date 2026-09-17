/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType



import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import androidx.compose.foundation.layout.PaddingValues
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import chromahub.rhythm.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.BuildConfig
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import chromahub.rhythm.app.shared.data.repository.StatsTimeRange
import chromahub.rhythm.app.util.GsonUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlin.system.exitProcess
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ButtonGroupStyle
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveScrollBar
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveButtonGroup
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveGroupButton
import chromahub.rhythm.app.shared.presentation.components.common.M3CircularLoader
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.common.StyledProgressBar
import chromahub.rhythm.app.shared.presentation.components.common.ProgressStyle
import chromahub.rhythm.app.shared.presentation.components.common.ThumbStyle
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.LicensesBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.UpdateBottomSheet
import chromahub.rhythm.app.ui.utils.LazyListStateSaver
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapeProvider
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapes
import chromahub.rhythm.app.shared.presentation.components.common.buildSplashBackdropShapes
import chromahub.rhythm.app.shared.presentation.components.common.SplashBackgroundOrbs
import chromahub.rhythm.app.shared.presentation.viewmodel.AppUpdaterViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.AppVersion
import chromahub.rhythm.app.ui.theme.getFontPreviewStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import chromahub.rhythm.app.utils.FontLoader
import chromahub.rhythm.app.ui.theme.parseCustomColorScheme
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.core.text.HtmlCompat
import chromahub.rhythm.app.shared.presentation.components.common.M3FourColorCircularLoader
import chromahub.rhythm.app.shared.presentation.components.player.PlayingEqIcon
import chromahub.rhythm.app.shared.presentation.components.dialogs.CreatePlaylistDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.BulkPlaylistExportDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistImportDialog
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShape
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationProgressDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.PlaylistOperationResultDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.AppRestartDialog
import chromahub.rhythm.app.shared.presentation.components.player.PlayerChipOrderBottomSheet
import chromahub.rhythm.app.features.local.presentation.components.settings.HomeSectionOrderBottomSheet
import chromahub.rhythm.app.features.local.presentation.components.settings.LibraryTabOrderBottomSheet
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem

import chromahub.rhythm.app.shared.presentation.screens.settings.TunerSettingRow
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch
import chromahub.rhythm.app.shared.presentation.screens.settings.TunerSettingCard
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingItem
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingGroup


@Composable
fun RhythmGuardSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptic = LocalHapticFeedback.current
    val useHoursFormat by appSettings.useHoursInTimeFormat.collectAsState()

    val auraMode by appSettings.rhythmGuardMode.collectAsState()
    val auraAge by appSettings.rhythmGuardAge.collectAsState()
    val manualWarningsEnabled by appSettings.rhythmGuardManualWarningsEnabled.collectAsState()
    val manualVolumeThreshold by appSettings.rhythmGuardManualVolumeThreshold.collectAsState()
    val alertThresholdMinutes by appSettings.rhythmGuardAlertThresholdMinutes.collectAsState()
    val warningTimeoutMinutes by appSettings.rhythmGuardWarningTimeoutMinutes.collectAsState()
    val postTimeoutCooldownMinutes by appSettings.rhythmGuardPostTimeoutCooldownMinutes.collectAsState()
    val breakResumeMinutes by appSettings.rhythmGuardBreakResumeMinutes.collectAsState()
    val timeoutUntilMs by appSettings.rhythmGuardTimeoutUntilMs.collectAsState()
    val timeoutStartedAtMs by appSettings.rhythmGuardTimeoutStartedAtMs.collectAsState()
    val timeoutCooldownUntilMs by appSettings.rhythmGuardTimeoutCooldownUntilMs.collectAsState()

    val dailyListeningStats by appSettings.dailyListeningStats.collectAsState()

    val stopPlaybackOnZeroVolume by appSettings.stopPlaybackOnZeroVolume.collectAsState()
    val rhythmGuardApplyVolumeLimitOnSpeaker by appSettings.rhythmGuardApplyVolumeLimitOnSpeaker.collectAsState()

    val currentSystemVolume = rememberSystemMusicVolumeFraction(context)
    val playbackStatsRepository = remember(context) { PlaybackStatsRepository.getInstance(context) }

    var todayExposureMs by remember { mutableLongStateOf(0L) }
    var weeklyAverageSessions by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(dailyListeningStats) {
        val todaySummary = runCatching {
            playbackStatsRepository.loadSummary(StatsTimeRange.TODAY)
        }.getOrNull()
        val weekSummary = runCatching {
            playbackStatsRepository.loadSummary(StatsTimeRange.WEEK)
        }.getOrNull()

        todayExposureMs = todaySummary?.totalDurationMs ?: 0L
        weeklyAverageSessions = weekSummary?.averageSessionsPerDay ?: 0f
    }

    val nowEpochMs = System.currentTimeMillis()
    val isRhythmGuardEnabled = auraMode != AppSettings.RHYTHM_GUARD_MODE_OFF
    val todayExposureMinutes = (todayExposureMs / 60000L).toInt().coerceAtLeast(0)

    val activePolicy = remember(auraAge) { appSettings.getRhythmGuardPolicy(auraAge) }
    val policyTable = remember { appSettings.getRhythmGuardPolicyBands() }
    val recommendedVolumeThreshold = activePolicy.maxVolumeThreshold
    val recommendedDailyMinutes = activePolicy.recommendedDailyMinutes

    val effectiveExposureLimitMinutes = if (auraMode == AppSettings.RHYTHM_GUARD_MODE_AUTO) {
        recommendedDailyMinutes
    } else if (alertThresholdMinutes > 0) {
        alertThresholdMinutes
    } else {
        recommendedDailyMinutes
    }
    val totalExposureMinutes = (todayExposureMs / 60000L).toInt().coerceAtLeast(0)
    val isExposureLimitExceeded = isRhythmGuardEnabled && totalExposureMinutes > effectiveExposureLimitMinutes

    val currentVolumePercent = (currentSystemVolume * 100f).toInt().coerceIn(0, 100)
    val manualThresholdPercent = (manualVolumeThreshold * 100f).toInt().coerceIn(0, 100)
    val recommendedThresholdPercent = (recommendedVolumeThreshold * 100f).toInt().coerceIn(0, 100)

    val formattedTotalExposure = remember(todayExposureMs, useHoursFormat) {
        rhythmGuardFormatDurationFromMillis(todayExposureMs, useHoursFormat)
    }
    val formattedDailyTarget = remember(effectiveExposureLimitMinutes, useHoursFormat) {
        rhythmGuardFormatDurationFromMinutes(effectiveExposureLimitMinutes, useHoursFormat)
    }
    val formattedTimeout = remember(warningTimeoutMinutes, useHoursFormat) {
        rhythmGuardFormatDurationFromMinutes(warningTimeoutMinutes, useHoursFormat)
    }
    val formattedPostTimeoutCooldown = remember(postTimeoutCooldownMinutes, useHoursFormat) {
        rhythmGuardFormatDurationFromMinutes(postTimeoutCooldownMinutes, useHoursFormat)
    }
    val formattedResumeInterval = remember(breakResumeMinutes, useHoursFormat) {
        rhythmGuardFormatDurationFromMinutes(breakResumeMinutes, useHoursFormat)
    }
    val activeVolumeThreshold = if (auraMode == AppSettings.RHYTHM_GUARD_MODE_AUTO) {
        recommendedVolumeThreshold
    } else {
        manualVolumeThreshold
    }
    val activeThresholdPercent = if (auraMode == AppSettings.RHYTHM_GUARD_MODE_AUTO) {
        recommendedThresholdPercent
    } else {
        manualThresholdPercent
    }
    val isTimeoutActive = isRhythmGuardEnabled && timeoutUntilMs > nowEpochMs
    val isCooldownActive = isRhythmGuardEnabled && !isTimeoutActive && timeoutCooldownUntilMs > nowEpochMs
    val timeoutRemainingSeconds = ((timeoutUntilMs - nowEpochMs) / 1000L).coerceAtLeast(0L)
    val cooldownRemainingSeconds = ((timeoutCooldownUntilMs - nowEpochMs) / 1000L).coerceAtLeast(0L)

    val timeoutStartFallbackMs = timeoutUntilMs - breakResumeMinutes.coerceIn(1, 180).toLong() * 60_000L
    val resolvedTimeoutStartMs = timeoutStartedAtMs
        .takeIf { it > 0L && it < timeoutUntilMs }
        ?: timeoutStartFallbackMs
    val timeoutTotalMs = (timeoutUntilMs - resolvedTimeoutStartMs).coerceAtLeast(1_000L)
    val timeoutElapsedMs = (timeoutTotalMs - (timeoutUntilMs - nowEpochMs).coerceAtLeast(0L))
        .coerceIn(0L, timeoutTotalMs)
    val timeoutProgress = (timeoutElapsedMs.toFloat() / timeoutTotalMs.toFloat()).coerceIn(0f, 1f)

    val cooldownTotalMs = postTimeoutCooldownMinutes.coerceIn(1, 60).toLong() * 60_000L
    val cooldownElapsedMs = (cooldownTotalMs - (timeoutCooldownUntilMs - nowEpochMs).coerceAtLeast(0L))
        .coerceIn(0L, cooldownTotalMs)
    val cooldownProgress = (cooldownElapsedMs.toFloat() / cooldownTotalMs.toFloat()).coerceIn(0f, 1f)

    val showVolumeWarning = isRhythmGuardEnabled &&
        auraMode == AppSettings.RHYTHM_GUARD_MODE_MANUAL &&
        manualWarningsEnabled &&
        currentSystemVolume > manualVolumeThreshold
    val showExposureWarning = isRhythmGuardEnabled &&
        auraMode == AppSettings.RHYTHM_GUARD_MODE_MANUAL &&
        manualWarningsEnabled &&
        totalExposureMinutes > effectiveExposureLimitMinutes
    val showWarningCard = showVolumeWarning || showExposureWarning || isExposureLimitExceeded
    val safetySnapshot = remember(
        isRhythmGuardEnabled,
        auraMode,
        manualWarningsEnabled,
        currentSystemVolume,
        activeVolumeThreshold,
        totalExposureMinutes,
        effectiveExposureLimitMinutes,
        weeklyAverageSessions,
        isTimeoutActive,
        isCooldownActive,
        cooldownProgress
    ) {
        rhythmGuardCalculateSafetySnapshot(
            isEnabled = isRhythmGuardEnabled,
            isManualMode = auraMode == AppSettings.RHYTHM_GUARD_MODE_MANUAL,
            manualWarningsEnabled = manualWarningsEnabled,
            currentVolumeFraction = currentSystemVolume,
            safeVolumeThresholdFraction = activeVolumeThreshold,
            exposureMinutes = totalExposureMinutes,
            exposureLimitMinutes = effectiveExposureLimitMinutes,
            weeklyAverageSessions = weeklyAverageSessions,
            timeoutActive = isTimeoutActive,
            cooldownActive = isCooldownActive,
            cooldownProgress = cooldownProgress
        )
    }
    val healthRiskScore = safetySnapshot.riskScore
    val overallHealthLevel = when {
        !isRhythmGuardEnabled -> RhythmGuardOverallHealthLevel.OFF
        isTimeoutActive -> RhythmGuardOverallHealthLevel.TIMEOUT
        isCooldownActive -> RhythmGuardOverallHealthLevel.COOLDOWN
        healthRiskScore < 0.40f -> RhythmGuardOverallHealthLevel.GOOD
        healthRiskScore < 0.72f -> RhythmGuardOverallHealthLevel.FAIR
        else -> RhythmGuardOverallHealthLevel.RISK
    }
    val overallHealthProgress = when {
        !isRhythmGuardEnabled -> 0f
        isTimeoutActive -> timeoutProgress
        isCooldownActive -> cooldownProgress
        else -> safetySnapshot.safetyProgress
    }
    val guardStatusText = when {
        !isRhythmGuardEnabled -> context.getString(R.string.settings_rhythm_guard_state_inactive)
        isTimeoutActive -> context.getString(R.string.settings_rhythm_guard_state_timeout_active)
        isCooldownActive -> context.getString(R.string.settings_rhythm_guard_state_cooldown_active)
        else -> context.getString(R.string.settings_rhythm_guard_state_active)
    }
    val guardStatusDetail = when {
        isTimeoutActive -> context.getString(
            R.string.settings_rhythm_guard_state_timeout_remaining,
            rhythmGuardFormatCountdownFromSeconds(timeoutRemainingSeconds, useHoursFormat)
        )
        isCooldownActive -> context.getString(
            R.string.settings_rhythm_guard_state_cooldown_remaining,
            rhythmGuardFormatCountdownFromSeconds(cooldownRemainingSeconds, useHoursFormat)
        )
        else -> null
    }
    val activeManualPreset = remember(
        manualVolumeThreshold,
        alertThresholdMinutes,
        warningTimeoutMinutes,
        postTimeoutCooldownMinutes,
        breakResumeMinutes,
        manualWarningsEnabled
    ) {
        rhythmGuardResolveProtectionPreset(
            manualVolumeThreshold = manualVolumeThreshold,
            alertThresholdMinutes = alertThresholdMinutes,
            warningTimeoutMinutes = warningTimeoutMinutes,
            postTimeoutCooldownMinutes = postTimeoutCooldownMinutes,
            breakResumeMinutes = breakResumeMinutes,
            manualWarningsEnabled = manualWarningsEnabled
        )
    }
    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_rhythm_guard),
        showBackButton = true,
        onBackClick = onBackClick,
        headerContent = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isRhythmGuardEnabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Security,
                            contentDescription = null,
                            tint = if (isRhythmGuardEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(35.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(if (isRhythmGuardEnabled) R.string.status_active else R.string.status_disabled),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isRhythmGuardEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (isTimeoutActive) {
                                Text(
                                    text = stringResource(id = R.string.settings_rhythm_guard_locked_during_break),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        TunerAnimatedSwitch(
                            checked = isRhythmGuardEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val restoredMode = if (auraMode == AppSettings.RHYTHM_GUARD_MODE_MANUAL) {
                                        AppSettings.RHYTHM_GUARD_MODE_MANUAL
                                    } else {
                                        AppSettings.RHYTHM_GUARD_MODE_AUTO
                                    }
                                    appSettings.setRhythmGuardMode(restoredMode)
                                } else {
                                    appSettings.setRhythmGuardMode(AppSettings.RHYTHM_GUARD_MODE_OFF)
                                }
                            },
                            enabled = !isTimeoutActive
                        )
                    }

                    // Smoothly expand the mode selector when active
                    AnimatedVisibility(
                        visible = isRhythmGuardEnabled,
                        enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut() + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            ExpressiveButtonGroup(
                                items = listOf(
                                    context.getString(R.string.settings_rhythm_guard_mode_auto),
                                    context.getString(R.string.settings_rhythm_guard_mode_manual)
                                ),
                                selectedIndex = if (auraMode == AppSettings.RHYTHM_GUARD_MODE_MANUAL) 1 else 0,
                                onItemClick = { index ->
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    when (index) {
                                        0 -> appSettings.setRhythmGuardMode(AppSettings.RHYTHM_GUARD_MODE_AUTO)
                                        else -> appSettings.setRhythmGuardMode(AppSettings.RHYTHM_GUARD_MODE_MANUAL)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    ) { modifier ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(14.dp))
            }

            item {
                RhythmGuardHeroCard(
                    level = overallHealthLevel,
                    overallProgress = overallHealthProgress,
                    statusText = guardStatusText,
                    statusDetail = guardStatusDetail,
                    isExceeded = isExposureLimitExceeded,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                RhythmGuardTrendsRow(
                    exposureValue = formattedTotalExposure,
                    exposureSubtitle = formattedDailyTarget,
                    exposureProgress = (totalExposureMinutes / maxOf(effectiveExposureLimitMinutes, 1).toFloat()).coerceIn(0f, 1f),
                    exposureWarning = showExposureWarning,
                    volumeValue = context.getString(R.string.settings_value_percent, currentVolumePercent),
                    volumeSubtitle = context.getString(R.string.settings_value_percent, activeThresholdPercent),
                    volumeProgress = (currentSystemVolume / maxOf(activeVolumeThreshold, 0.01f)).coerceIn(0f, 1f),
                    volumeWarning = showVolumeWarning,
                    isLast = !isRhythmGuardEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isRhythmGuardEnabled) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = context.getString(
                                            R.string.settings_rhythm_guard_age_label,
                                            auraAge
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.settings_rhythm_guard_age_desc,
                                            recommendedDailyMinutes,
                                            recommendedDailyMinutes,
                                            recommendedThresholdPercent
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            val auraAgeSliderState = remember(auraAge) {
                                SliderState(
                                    value = auraAge.toFloat(),
                                    steps = 71,
                                    trackRange = 8f..80f
                                )
                            }
                            auraAgeSliderState.value = auraAge.toFloat()
                            Slider(
                                state = auraAgeSliderState,
                                onValueChange = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                    appSettings.setRhythmGuardAge(it.toInt())
                                }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    val outputSettingItems = listOf(
                        SettingItem(
                            RhythmIcons.Speaker,
                            context.getString(R.string.settings_rhythm_guard_device_controls_speaker_limit_title),
                            context.getString(R.string.settings_rhythm_guard_device_controls_speaker_limit_desc),
                            toggleState = rhythmGuardApplyVolumeLimitOnSpeaker,
                            onToggleChange = {
                                appSettings.setRhythmGuardApplyVolumeLimitOnSpeaker(
                                    it
                                )
                            }
                        )
                    )

                    val materialItems = outputSettingItems.map { item ->
                        toMaterial3SettingsItem(
                            context = context,
                            item = item,
                            hapticFeedback = haptic
                        )
                    }

                    Material3SettingsGroup(
                        title = context.getString(R.string.settings_rhythm_guard_device_controls_title),
                        items = materialItems,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                }

                if (auraMode == AppSettings.RHYTHM_GUARD_MODE_MANUAL) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = context.getString(R.string.settings_rhythm_guard_alert_controls_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                // Threshold control
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = context.getString(
                                            R.string.settings_rhythm_guard_alert_threshold_title,
                                            if (alertThresholdMinutes > 0) {
                                                rhythmGuardFormatDurationFromMinutes(
                                                    alertThresholdMinutes,
                                                    useHoursFormat
                                                )
                                            } else {
                                                context.getString(R.string.settings_rhythm_guard_alert_threshold_policy_default)
                                            }
                                        ),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(-1, 60, 90, 120).forEach { option ->
                                            val isSelected = alertThresholdMinutes == option
                                            val cornerRadius by animateDpAsState(
                                                targetValue = if (isSelected) 24.dp else 12.dp,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                label = "alertThresholdCornerRadius"
                                            )
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    appSettings.setRhythmGuardAlertThresholdMinutes(
                                                        option
                                                    )
                                                },
                                                leadingIcon = if (isSelected) {
                                                    {
                                                        Icon(
                                                            imageVector = RhythmIcons.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                                        )
                                                    }
                                                } else null,
                                                shape = RoundedCornerShape(cornerRadius),
                                                label = {
                                                    Text(
                                                        if (option > 0) {
                                                            rhythmGuardFormatDurationFromMinutes(
                                                                option,
                                                                useHoursFormat
                                                            )
                                                        } else {
                                                            context.getString(R.string.settings_rhythm_guard_alert_threshold_policy_default)
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    val alertThresholdSliderState = remember(alertThresholdMinutes) {
                                        SliderState(
                                            value = maxOf(alertThresholdMinutes, 15).toFloat(),
                                            steps = 344,
                                            trackRange = 15f..360f
                                        )
                                    }
                                    alertThresholdSliderState.value = maxOf(alertThresholdMinutes, 15).toFloat()
                                    Slider(
                                        state = alertThresholdSliderState,
                                        onValueChange = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            appSettings.setRhythmGuardAlertThresholdMinutes(
                                                it.toInt()
                                            )
                                        }
                                    )
                                }

                                // Timeout control
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = context.getString(
                                            R.string.settings_rhythm_guard_alert_timeout_title,
                                            formattedTimeout
                                        ),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(2, 5, 10, 15).forEach { option ->
                                            val isSelected = warningTimeoutMinutes == option
                                            val cornerRadius by animateDpAsState(
                                                targetValue = if (isSelected) 24.dp else 12.dp,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                label = "warningTimeoutCornerRadius"
                                            )
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    appSettings.setRhythmGuardWarningTimeoutMinutes(
                                                        option
                                                    )
                                                },
                                                leadingIcon = if (isSelected) {
                                                    {
                                                        Icon(
                                                            imageVector = RhythmIcons.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                                        )
                                                    }
                                                } else null,
                                                shape = RoundedCornerShape(cornerRadius),
                                                label = {
                                                    Text(
                                                        rhythmGuardFormatDurationFromMinutes(
                                                            option,
                                                            useHoursFormat
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    val warningTimeoutSliderState = remember(warningTimeoutMinutes) {
                                        SliderState(
                                            value = warningTimeoutMinutes.toFloat(),
                                            steps = 28,
                                            trackRange = 1f..30f
                                        )
                                    }
                                    warningTimeoutSliderState.value = warningTimeoutMinutes.toFloat()
                                    Slider(
                                        state = warningTimeoutSliderState,
                                        onValueChange = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            appSettings.setRhythmGuardWarningTimeoutMinutes(
                                                it.toInt()
                                            )
                                        }
                                    )
                                }

                                // Post-timeout cooldown control
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = context.getString(
                                            R.string.settings_rhythm_guard_post_timeout_cooldown_title,
                                            formattedPostTimeoutCooldown
                                        ),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(3, 5, 10, 15).forEach { option ->
                                            val isSelected = postTimeoutCooldownMinutes == option
                                            val cornerRadius by animateDpAsState(
                                                targetValue = if (isSelected) 24.dp else 12.dp,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                label = "postTimeoutCooldownCornerRadius"
                                            )
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    appSettings.setRhythmGuardPostTimeoutCooldownMinutes(
                                                        option
                                                    )
                                                },
                                                leadingIcon = if (isSelected) {
                                                    {
                                                        Icon(
                                                            imageVector = RhythmIcons.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                                        )
                                                    }
                                                } else null,
                                                shape = RoundedCornerShape(cornerRadius),
                                                label = {
                                                    Text(
                                                        rhythmGuardFormatDurationFromMinutes(
                                                            option,
                                                            useHoursFormat
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    val postTimeoutCooldownSliderState = remember(postTimeoutCooldownMinutes) {
                                        SliderState(
                                            value = postTimeoutCooldownMinutes.toFloat(),
                                            steps = 28,
                                            trackRange = 1f..30f
                                        )
                                    }
                                    postTimeoutCooldownSliderState.value = postTimeoutCooldownMinutes.toFloat()
                                    Slider(
                                        state = postTimeoutCooldownSliderState,
                                        onValueChange = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            appSettings.setRhythmGuardPostTimeoutCooldownMinutes(
                                                it.toInt()
                                            )
                                        }
                                    )
                                }

                                // Break interval control
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = context.getString(
                                            R.string.settings_rhythm_guard_break_resume_default_title,
                                            formattedResumeInterval
                                        ),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(10, 15, 30, 60).forEach { option ->
                                            val isSelected = breakResumeMinutes == option
                                            val cornerRadius by animateDpAsState(
                                                targetValue = if (isSelected) 24.dp else 12.dp,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                label = "breakResumeCornerRadius"
                                            )
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    appSettings.setRhythmGuardBreakResumeMinutes(
                                                        option
                                                    )
                                                },
                                                leadingIcon = if (isSelected) {
                                                    {
                                                        Icon(
                                                            imageVector = RhythmIcons.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                                        )
                                                    }
                                                } else null,
                                                shape = RoundedCornerShape(cornerRadius),
                                                label = {
                                                    Text(
                                                        rhythmGuardFormatDurationFromMinutes(
                                                            option,
                                                            useHoursFormat
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    val breakResumeSliderState = remember(breakResumeMinutes) {
                                        SliderState(
                                            value = breakResumeMinutes.toFloat(),
                                            steps = 118,
                                            trackRange = 1f..120f
                                        )
                                    }
                                    breakResumeSliderState.value = breakResumeMinutes.toFloat()
                                    Slider(
                                        state = breakResumeSliderState,
                                        onValueChange = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            appSettings.setRhythmGuardBreakResumeMinutes(
                                                it.toInt()
                                            )
                                        }
                                    )
                                }

                                // Manual protection presets (quick multi-setting tunes)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = context.getString(R.string.settings_rhythm_guard_protection_presets_title),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = context.getString(R.string.settings_rhythm_guard_protection_presets_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            RhythmGuardProtectionPreset.GENTLE,
                                            RhythmGuardProtectionPreset.BALANCED,
                                            RhythmGuardProtectionPreset.STRICT
                                        ).forEach { preset ->
                                            val isSelected = activeManualPreset == preset
                                            val cornerRadius by animateDpAsState(
                                                targetValue = if (isSelected) 24.dp else 12.dp,
                                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                label = "presetCornerRadius"
                                            )
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    val values = rhythmGuardPresetValues(preset)
                                                    appSettings.setRhythmGuardManualWarningsEnabled(
                                                        true
                                                    )
                                                    appSettings.setRhythmGuardManualVolumeThreshold(
                                                        values.volumeThreshold
                                                    )
                                                    appSettings.setRhythmGuardAlertThresholdMinutes(
                                                        values.alertThresholdMinutes
                                                    )
                                                    appSettings.setRhythmGuardWarningTimeoutMinutes(
                                                        values.warningTimeoutMinutes
                                                    )
                                                    appSettings.setRhythmGuardPostTimeoutCooldownMinutes(
                                                        values.postTimeoutCooldownMinutes
                                                    )
                                                    appSettings.setRhythmGuardBreakResumeMinutes(
                                                        values.breakResumeMinutes
                                                    )
                                                },
                                                leadingIcon = if (isSelected) {
                                                    {
                                                        Icon(
                                                            imageVector = RhythmIcons.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                                        )
                                                    }
                                                } else null,
                                                shape = RoundedCornerShape(cornerRadius),
                                                label = {
                                                    Text(
                                                        text = when (preset) {
                                                            RhythmGuardProtectionPreset.GENTLE -> context.getString(
                                                                R.string.settings_rhythm_guard_protection_preset_gentle
                                                            )
 
                                                            RhythmGuardProtectionPreset.BALANCED -> context.getString(
                                                                R.string.settings_rhythm_guard_protection_preset_balanced
                                                            )
 
                                                            RhythmGuardProtectionPreset.STRICT -> context.getString(
                                                                R.string.settings_rhythm_guard_protection_preset_strict
                                                            )
 
                                                            RhythmGuardProtectionPreset.CUSTOM -> context.getString(
                                                                R.string.settings_rhythm_guard_protection_preset_custom
                                                            )
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (auraMode == AppSettings.RHYTHM_GUARD_MODE_AUTO) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        val policyItems = policyTable.map { band ->
                            val isCurrentBand = auraAge in band.minAge..band.maxAge
                            Material3SettingsItem(
                                icon = RhythmIcons.Security,
                                title = {
                                    Text(
                                        text = context.getString(
                                            R.string.settings_rhythm_guard_auto_policy_band,
                                            band.minAge,
                                            band.maxAge
                                        ),
                                        fontWeight = if (isCurrentBand) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                description = {
                                    Text(
                                        text = context.getString(
                                            R.string.settings_rhythm_guard_auto_policy_value,
                                            (band.maxVolumeThreshold * 100f).toInt(),
                                            band.recommendedDailyMinutes
                                        ),
                                        color = if (isCurrentBand) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                },
                                isHighlighted = isCurrentBand
                            )
                        }
                        Material3SettingsGroup(
                            title = context.getString(R.string.settings_rhythm_guard_auto_policy_table_title),
                            items = policyItems,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    }
                }

                if (auraMode == AppSettings.RHYTHM_GUARD_MODE_MANUAL) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        val manualSettingItems = listOf(
                            SettingItem(
                                RhythmIcons.Warning,
                                context.getString(R.string.settings_rhythm_guard_manual_warning_toggle),
                                context.getString(R.string.settings_rhythm_guard_manual_warning_toggle_desc),
                                toggleState = manualWarningsEnabled,
                                onToggleChange = {
                                    appSettings.setRhythmGuardManualWarningsEnabled(
                                        it
                                    )
                                }
                            ),
                            SettingItem(
                                RhythmIcons.Stop,
                                context.getString(R.string.settings_stop_playback_on_zero_volume),
                                context.getString(R.string.settings_stop_playback_on_zero_volume_desc),
                                toggleState = stopPlaybackOnZeroVolume,
                                onToggleChange = { appSettings.setStopPlaybackOnZeroVolume(it) }
                            )
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = context.getString(
                                            R.string.settings_rhythm_guard_manual_threshold_title,
                                            manualThresholdPercent
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = context.getString(R.string.settings_rhythm_guard_manual_threshold_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val manualVolumeThresholdSliderState = remember(manualVolumeThreshold) {
                                        SliderState(
                                            value = manualVolumeThreshold,
                                            trackRange = 0.40f..0.95f
                                        )
                                    }
                                    manualVolumeThresholdSliderState.value = manualVolumeThreshold
                                    Slider(
                                        state = manualVolumeThresholdSliderState,
                                        onValueChange = {
                                            appSettings.setRhythmGuardManualVolumeThreshold(
                                                it
                                            )
                                        },
                                        onValueChangeFinished = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        }
                                    )
                                }
                            }

                            val materialItems = manualSettingItems.map { item ->
                                toMaterial3SettingsItem(
                                    context = context,
                                    item = item,
                                    hapticFeedback = haptic
                                )
                            }

                            Material3SettingsGroup(
                                title = context.getString(R.string.settings_rhythm_guard_manual_controls_title),
                                items = materialItems,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                itemShape = RoundedCornerShape(8.dp),
                                lastItemShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                            )
                        }
                    }
                }

                if (showWarningCard) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = context.getString(R.string.settings_rhythm_guard_warning_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = when {
                                        showExposureWarning || isExposureLimitExceeded -> context.getString(
                                            R.string.settings_rhythm_guard_warning_daily_exposure,
                                            formattedTotalExposure,
                                            formattedDailyTarget
                                        )

                                        else -> context.getString(
                                            R.string.settings_rhythm_guard_warning_high_volume,
                                            activeThresholdPercent
                                        )
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}



fun rhythmGuardWeeklyAverageSessions(stats: Map<String, Long>): Float {
    if (stats.isEmpty()) return 0f
    val recentDays = stats.toList()
        .sortedByDescending { it.first }
        .take(7)
        .map { it.second }

    if (recentDays.isEmpty()) return 0f
    return recentDays.average().toFloat()
}



internal data class RhythmGuardSafetySnapshot(
    val riskScore: Float,
    val safetyProgress: Float
)

internal fun rhythmGuardCalculateSafetySnapshot(
    isEnabled: Boolean,
    isManualMode: Boolean,
    manualWarningsEnabled: Boolean,
    currentVolumeFraction: Float,
    safeVolumeThresholdFraction: Float,
    exposureMinutes: Int,
    exposureLimitMinutes: Int,
    weeklyAverageSessions: Float,
    timeoutActive: Boolean,
    cooldownActive: Boolean,
    cooldownProgress: Float
): RhythmGuardSafetySnapshot {
    if (!isEnabled) {
        return RhythmGuardSafetySnapshot(riskScore = 0f, safetyProgress = 0f)
    }

    if (timeoutActive) {
        return RhythmGuardSafetySnapshot(riskScore = 1f, safetyProgress = 0f)
    }

    val safeVolumeThreshold = rhythmGuardSanitizeFloat(safeVolumeThresholdFraction, fallback = 0.01f)
        .coerceIn(0.01f, 1f)
    val safeCurrentVolume = rhythmGuardSanitizeFloat(currentVolumeFraction)
    val safeExposureMinutes = exposureMinutes.coerceAtLeast(0)
    val safeExposureLimit = exposureLimitMinutes.coerceAtLeast(1)
    val safeWeeklySessions = rhythmGuardSanitizeFloat(weeklyAverageSessions)

    val volumeRatio = safeCurrentVolume / safeVolumeThreshold
    val exposureRatio = safeExposureMinutes.toFloat() / safeExposureLimit.toFloat()
    val sessionRatio = safeWeeklySessions / 8f

    val volumePressure = rhythmGuardNormalizePressure(volumeRatio)
    val exposurePressure = rhythmGuardNormalizePressure(exposureRatio)
    val sessionPressure = rhythmGuardNormalizePressure(sessionRatio)

    var riskScore = (
        (volumePressure * 0.40f) +
            (exposurePressure * 0.42f) +
            (sessionPressure * 0.18f)
        ).coerceIn(0f, 1f)

    if (isManualMode && !manualWarningsEnabled) {
        riskScore = (riskScore + 0.07f).coerceIn(0f, 1f)
    }

    if (cooldownActive) {
        val recoveryProgress = rhythmGuardSanitizeFloat(cooldownProgress).coerceIn(0f, 1f)
        val decay = 0.82f - (recoveryProgress * 0.30f)
        riskScore = (riskScore * decay).coerceIn(0.18f, 0.70f)
    }

    val safetyProgress = (1f - riskScore).coerceIn(0f, 1f)
    return RhythmGuardSafetySnapshot(riskScore = riskScore, safetyProgress = safetyProgress)
}



fun rhythmGuardSanitizeFloat(value: Float, fallback: Float = 0f): Float {
    return if (value.isFinite()) value.coerceAtLeast(0f) else fallback
}



fun rhythmGuardNormalizePressure(ratio: Float): Float {
    val safeRatio = rhythmGuardSanitizeFloat(ratio)
    return when {
        safeRatio <= 0f -> 0f
        safeRatio <= 0.6f -> (safeRatio / 0.6f) * 0.32f
        safeRatio <= 1f -> 0.32f + ((safeRatio - 0.6f) / 0.4f) * 0.24f
        safeRatio <= 1.5f -> 0.56f + ((safeRatio - 1f) / 0.5f) * 0.29f
        safeRatio <= 2f -> 0.85f + ((safeRatio - 1.5f) / 0.5f) * 0.10f
        else -> 0.95f + ((safeRatio - 2f) / 2f) * 0.05f
    }.coerceIn(0f, 1f)
}



@Composable
fun rememberSystemMusicVolumeFraction(context: Context): Float {
    var systemVolume by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

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



fun rhythmGuardFormatDurationFromMinutes(minutes: Int, useHoursFormat: Boolean = true): String {
    val safeMinutes = minutes.coerceAtLeast(0)
    if (!useHoursFormat) {
        return "${safeMinutes}m"
    }
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



fun rhythmGuardFormatDurationFromMillis(durationMs: Long, useHoursFormat: Boolean = true): String {
    return rhythmGuardFormatDurationFromMinutes((durationMs / 60000L).toInt(), useHoursFormat)
}



fun rhythmGuardFormatCountdownFromSeconds(seconds: Long, useHoursFormat: Boolean = true): String {
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











enum class RhythmGuardOverallHealthLevel {
    OFF,
    GOOD,
    FAIR,
    RISK,
    COOLDOWN,
    TIMEOUT
}



enum class RhythmGuardProtectionPreset {
    GENTLE,
    BALANCED,
    STRICT,
    CUSTOM
}



data class RhythmGuardProtectionPresetValues(
    val volumeThreshold: Float,
    val alertThresholdMinutes: Int,
    val warningTimeoutMinutes: Int,
    val postTimeoutCooldownMinutes: Int,
    val breakResumeMinutes: Int
)

fun rhythmGuardPresetValues(preset: RhythmGuardProtectionPreset): RhythmGuardProtectionPresetValues {
    return when (preset) {
        RhythmGuardProtectionPreset.GENTLE -> RhythmGuardProtectionPresetValues(
            volumeThreshold = 0.80f,
            alertThresholdMinutes = 120,
            warningTimeoutMinutes = 10,
            postTimeoutCooldownMinutes = 5,
            breakResumeMinutes = 10
        )
        RhythmGuardProtectionPreset.BALANCED -> RhythmGuardProtectionPresetValues(
            volumeThreshold = 0.68f,
            alertThresholdMinutes = 90,
            warningTimeoutMinutes = 5,
            postTimeoutCooldownMinutes = 10,
            breakResumeMinutes = 15
        )
        RhythmGuardProtectionPreset.STRICT -> RhythmGuardProtectionPresetValues(
            volumeThreshold = 0.58f,
            alertThresholdMinutes = 60,
            warningTimeoutMinutes = 3,
            postTimeoutCooldownMinutes = 15,
            breakResumeMinutes = 20
        )
        RhythmGuardProtectionPreset.CUSTOM -> RhythmGuardProtectionPresetValues(
            volumeThreshold = 0.68f,
            alertThresholdMinutes = 90,
            warningTimeoutMinutes = 5,
            postTimeoutCooldownMinutes = 10,
            breakResumeMinutes = 15
        )
    }
}



fun rhythmGuardResolveProtectionPreset(
    manualVolumeThreshold: Float,
    alertThresholdMinutes: Int,
    warningTimeoutMinutes: Int,
    postTimeoutCooldownMinutes: Int,
    breakResumeMinutes: Int,
    manualWarningsEnabled: Boolean
): RhythmGuardProtectionPreset {
    if (!manualWarningsEnabled) {
        return RhythmGuardProtectionPreset.CUSTOM
    }

    return when {
        rhythmGuardMatchesPreset(
            manualVolumeThreshold,
            alertThresholdMinutes,
            warningTimeoutMinutes,
            postTimeoutCooldownMinutes,
            breakResumeMinutes,
            rhythmGuardPresetValues(RhythmGuardProtectionPreset.GENTLE)
        ) -> RhythmGuardProtectionPreset.GENTLE

        rhythmGuardMatchesPreset(
            manualVolumeThreshold,
            alertThresholdMinutes,
            warningTimeoutMinutes,
            postTimeoutCooldownMinutes,
            breakResumeMinutes,
            rhythmGuardPresetValues(RhythmGuardProtectionPreset.BALANCED)
        ) -> RhythmGuardProtectionPreset.BALANCED

        rhythmGuardMatchesPreset(
            manualVolumeThreshold,
            alertThresholdMinutes,
            warningTimeoutMinutes,
            postTimeoutCooldownMinutes,
            breakResumeMinutes,
            rhythmGuardPresetValues(RhythmGuardProtectionPreset.STRICT)
        ) -> RhythmGuardProtectionPreset.STRICT

        else -> RhythmGuardProtectionPreset.CUSTOM
    }
}



fun rhythmGuardMatchesPreset(
    manualVolumeThreshold: Float,
    alertThresholdMinutes: Int,
    warningTimeoutMinutes: Int,
    postTimeoutCooldownMinutes: Int,
    breakResumeMinutes: Int,
    preset: RhythmGuardProtectionPresetValues
): Boolean {
    return kotlin.math.abs(manualVolumeThreshold - preset.volumeThreshold) <= 0.015f &&
        alertThresholdMinutes == preset.alertThresholdMinutes &&
        warningTimeoutMinutes == preset.warningTimeoutMinutes &&
        postTimeoutCooldownMinutes == preset.postTimeoutCooldownMinutes &&
        breakResumeMinutes == preset.breakResumeMinutes
}







data class BackupRestoreResultState(
    val title: String,
    val message: String,
    val isError: Boolean,
    val requiresRestart: Boolean
)

@Composable
fun BackupRestoreSectionPickerBottomSheet(
    title: String,
    subtitle: String,
    confirmLabel: String,
    confirmIcon: MaterialSymbolIcon,
    sections: AppSettings.BackupRestoreSections,
    isProcessing: Boolean,
    onSectionsChange: (AppSettings.BackupRestoreSections) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (AppSettings.BackupRestoreSections) -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val selectedSectionCount = listOf(
        sections.includeGeneralSettings,
        sections.includeLibraryData,
        sections.includeStatsAndRhythmGuard
    ).count { it }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        StandardBottomSheetHeader(
            title = title,
            subtitle = subtitle,
            visible = true
        )

        val scrollState = rememberScrollState()

        AdaptiveSheetScrollContainer(
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth()
        ) { endPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(end = endPadding)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = RhythmIcons.Tune,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = stringResource(R.string.rhythmguardsettingsscreen_choose_sections),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = pluralStringResource(R.plurals.backup_restore_sections_enabled, selectedSectionCount, selectedSectionCount, 3),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        onSectionsChange(
                                            sections.copy(
                                                includeGeneralSettings = true,
                                                includeLibraryData = true,
                                                includeStatsAndRhythmGuard = true
                                            )
                                        )
                                    },
                                    enabled = !isProcessing,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text(stringResource(R.string.autoeqprofileselector_all), style = MaterialTheme.typography.labelLarge)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        onSectionsChange(
                                            sections.copy(
                                                includeGeneralSettings = false,
                                                includeLibraryData = false,
                                                includeStatsAndRhythmGuard = false
                                            )
                                        )
                                    },
                                    enabled = !isProcessing,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text(stringResource(R.string.rhythmguardsettingsscreen_none), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val pickerItems = listOf(
                        Material3SettingsItem(
                            icon = RhythmIcons.Settings,
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.rhythmguardsettingsscreen_general_settings),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = stringResource(R.string.backup_restore_section_core),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            description = {
                                Text(stringResource(R.string.backup_restore_section_core_desc))
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    TunerAnimatedSwitch(
                                        checked = sections.includeGeneralSettings,
                                        onCheckedChange = { onSectionsChange(sections.copy(includeGeneralSettings = it)) }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (sections.includeGeneralSettings) stringResource(R.string.backup_restore_status_included) else stringResource(R.string.backup_restore_status_excluded),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                onSectionsChange(sections.copy(includeGeneralSettings = !sections.includeGeneralSettings))
                            }
                        ),
                        Material3SettingsItem(
                            icon = RhythmIcons.Library,
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.rhythmguardsettingsscreen_library_data),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = stringResource(R.string.backup_restore_section_collection),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            description = {
                                Text(stringResource(R.string.backup_restore_section_collection_desc))
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    TunerAnimatedSwitch(
                                        checked = sections.includeLibraryData,
                                        onCheckedChange = { onSectionsChange(sections.copy(includeLibraryData = it)) }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (sections.includeLibraryData) stringResource(R.string.backup_restore_status_included) else stringResource(R.string.backup_restore_status_excluded),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                onSectionsChange(sections.copy(includeLibraryData = !sections.includeLibraryData))
                            }
                        ),
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("auto_graph"),
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.rhythmguardsettingsscreen_stats_rhythm_guard),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = stringResource(R.string.backup_restore_section_insight),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            description = {
                                Text(stringResource(R.string.backup_restore_section_insight_desc))
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    TunerAnimatedSwitch(
                                        checked = sections.includeStatsAndRhythmGuard,
                                        onCheckedChange = { onSectionsChange(sections.copy(includeStatsAndRhythmGuard = it)) }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (sections.includeStatsAndRhythmGuard) stringResource(R.string.backup_restore_status_included) else stringResource(R.string.backup_restore_status_excluded),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                onSectionsChange(sections.copy(includeStatsAndRhythmGuard = !sections.includeStatsAndRhythmGuard))
                            }
                        )
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Material3SettingsGroup(
                            items = pickerItems,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            ExpressiveButtonGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                style = ButtonGroupStyle.Tonal
            ) {
                ExpressiveGroupButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing,
                    isStart = true
                ) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(context.getString(R.string.ui_cancel))
                }

                ExpressiveGroupButton(
                    onClick = { onConfirm(sections) },
                    modifier = Modifier.weight(1f),
                    enabled = sections.hasAtLeastOneSectionSelected && !isProcessing,
                    isEnd = true
                ) {
                    if (isProcessing) {
                        M3CircularLoader(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.5f,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = confirmIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(confirmLabel)
                    }
                }
            }
        }
    }

// ============ Guard dashboard components ============

@Composable
fun RhythmGuardHeroCard(
    level: RhythmGuardOverallHealthLevel,
    overallProgress: Float,
    statusText: String,
    statusDetail: String?,
    isExceeded: Boolean,
    modifier: Modifier = Modifier
) {
    val isEnabled = level != RhythmGuardOverallHealthLevel.OFF
    val heroColor = when (level) {
        RhythmGuardOverallHealthLevel.GOOD -> MaterialTheme.colorScheme.primary
        RhythmGuardOverallHealthLevel.FAIR -> MaterialTheme.colorScheme.tertiary
        RhythmGuardOverallHealthLevel.RISK, RhythmGuardOverallHealthLevel.TIMEOUT -> MaterialTheme.colorScheme.error
        RhythmGuardOverallHealthLevel.COOLDOWN -> MaterialTheme.colorScheme.secondary
        RhythmGuardOverallHealthLevel.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.rhythmguardsettingsscreen_status),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            val statusLine = if (isExceeded) {
                stringResource(R.string.settings_rhythm_guard_hero_limit_exceeded)
            } else {
                statusText
            }
            Text(
                text = statusLine,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isExceeded) MaterialTheme.colorScheme.error else heroColor,
                textAlign = TextAlign.Center
            )
            if (!statusDetail.isNullOrBlank()) {
                Text(
                    text = statusDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = overallProgress.coerceIn(0f, 1f),
                animationSpec = spring(dampingRatio = 0.4f, stiffness = 50f),
                label = "GuardHeroProgress"
            )

            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = if (isExceeded) MaterialTheme.colorScheme.error else heroColor,
                trackColor = (if (isExceeded) MaterialTheme.colorScheme.error else heroColor).copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
fun RhythmGuardTrendsRow(
    exposureValue: String,
    exposureSubtitle: String,
    exposureProgress: Float,
    exposureWarning: Boolean,
    volumeValue: String,
    volumeSubtitle: String,
    volumeProgress: Float,
    volumeWarning: Boolean,
    modifier: Modifier = Modifier,
    isLast: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RhythmGuardTrendCard(
            title = stringResource(R.string.settings_rhythm_guard_snapshot_exposure_title),
            value = exposureValue,
            subtitle = stringResource(R.string.settings_rhythm_guard_hero_exposure_of, exposureSubtitle),
            progress = exposureProgress,
            isWarning = exposureWarning,
            shape = if (isLast) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 8.dp)
            } else {
                RoundedCornerShape(8.dp)
            },
            modifier = Modifier.weight(1f)
        )
        RhythmGuardTrendCard(
            title = stringResource(R.string.settings_rhythm_guard_snapshot_volume_title),
            value = volumeValue,
            subtitle = stringResource(R.string.settings_rhythm_guard_hero_volume_limit, volumeSubtitle),
            progress = volumeProgress,
            isWarning = volumeWarning,
            shape = if (isLast) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 24.dp)
            } else {
                RoundedCornerShape(8.dp)
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RhythmGuardTrendCard(
    title: String,
    value: String,
    subtitle: String,
    progress: Float,
    isWarning: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val tint = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
        label = "GuardTrendProgress"
    )

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = tint,
                trackColor = tint.copy(alpha = 0.12f)
            )
        }
    }
}

@Composable
private fun RhythmGuardDigitTicker(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = ""
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        text.forEachIndexed { index, char ->
            val key = "${prefix}_${text.length - index}"
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    if (targetState.isDigit() && initialState.isDigit()) {
                        if (targetState > initialState) {
                            (slideInVertically { it / 2 } + fadeIn()).togetherWith(slideOutVertically { -it / 2 } + fadeOut())
                        } else {
                            (slideInVertically { -it / 2 } + fadeIn()).togetherWith(slideOutVertically { it / 2 } + fadeOut())
                        }
                    } else {
                        fadeIn().togetherWith(fadeOut())
                    }
                },
                label = "GuardDigitTicker_$key",
                contentAlignment = Alignment.BottomStart
            ) { targetChar ->
                Text(
                    text = targetChar.toString(),
                    style = style.copy(
                        letterSpacing = (-2).sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Bottom,
                            trim = LineHeightStyle.Trim.Both
                        )
                    ),
                    fontWeight = FontWeight.Bold,
                    color = color,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun RhythmGuardTickerUnit(unit: String) {
    Text(
        text = unit,
        style = MaterialTheme.typography.displayMedium.copy(
            letterSpacing = (-2).sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Bottom,
                trim = LineHeightStyle.Trim.Both
            )
        ),
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}