/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.bottomsheets
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.data.model.PlaybackLocation
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import androidx.compose.ui.res.stringResource
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShape
import chromahub.rhythm.app.shared.presentation.components.dialogs.AppRestartDialog
import chromahub.rhythm.app.util.AppRestarter
import chromahub.rhythm.app.core.domain.model.StreamingQuality
import chromahub.rhythm.app.shared.presentation.components.player.VolumeSlider
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackBottomSheet(
    locations: List<PlaybackLocation>,
    currentLocation: PlaybackLocation?,
    volume: Float,
    musicViewModel: MusicViewModel,
    onLocationSelect: (PlaybackLocation) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onRefreshDevices: () -> Unit,
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToGoMode: (() -> Unit)? = null,
    onNavigateToEqualizer: (() -> Unit)? = null,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    
    // System volume state
    var systemVolume by remember { mutableFloatStateOf(0.5f) }
    var systemMaxVolume by remember { mutableIntStateOf(15) }
    
    // Collect settings
    val playbackSpeed by musicViewModel.playbackSpeed.collectAsState()
    val playbackPitch by musicViewModel.playbackPitch.collectAsState()
    val streamingQuality by appSettings.streamingQuality.collectAsState()
    val appMode by appSettings.appMode.collectAsState()
    val syncSpeedAndPitch by appSettings.syncSpeedAndPitch.collectAsState()
    val gaplessPlayback by appSettings.gaplessPlayback.collectAsState()
    val useSystemVolume by appSettings.useSystemVolume.collectAsState()
    val stopPlaybackOnZeroVolume by appSettings.stopPlaybackOnZeroVolume.collectAsState()
    val resumeOnDeviceReconnect by appSettings.resumeOnDeviceReconnect.collectAsState()
    val hidePlayedQueueSongs by appSettings.hidePlayedQueueSongs.collectAsState()
    val showPlayedQueueSongs = !hidePlayedQueueSongs
    val crossfadeEnabled by appSettings.crossfade.collectAsState()
    val crossfadeDuration by appSettings.crossfadeDuration.collectAsState()
    val audioNormalization by appSettings.audioNormalization.collectAsState()
    val replayGain by appSettings.replayGain.collectAsState()
    val equalizerEnabled by appSettings.equalizerEnabled.collectAsState()
    val bassBoostEnabled by appSettings.bassBoostEnabled.collectAsState()
    val bassBoostStrength by appSettings.bassBoostStrength.collectAsState()
    val virtualizerEnabled by appSettings.virtualizerEnabled.collectAsState()
    val virtualizerStrength by appSettings.virtualizerStrength.collectAsState()
    val isAudioOffloadActive by appSettings.isAudioOffloadActive.collectAsState()
    val batterySaverEnabled by appSettings.batterySaverEnabled.collectAsState()
    val batterySaverMode by appSettings.batterySaverMode.collectAsState()
    val batterySaverEnableOffload by appSettings.batterySaverEnableOffload.collectAsState()
    val isOffloadEnforced = batterySaverEnabled && (batterySaverMode == "auto" || (batterySaverMode == "manual" && batterySaverEnableOffload))
    
    var showSpeedPitchSheet by remember { mutableStateOf(false) }

    // Quality sheet and restart dialog state
    var showQualitySheet by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartDialogMessage by remember { mutableStateOf("") }

    // Initialize system volume and monitor for changes
    LaunchedEffect(Unit) {
        // Get system volume
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        systemVolume = currentVolume.toFloat() / maxVolume.toFloat()
        systemMaxVolume = maxVolume
    }
    
    // Monitor system volume changes using ContentObserver (no polling)
    LaunchedEffect(Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val volumeObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val cv = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val mv = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val newVol = cv.toFloat() / mv.toFloat()
                if (newVol != systemVolume) {
                    systemVolume = newVol
                    systemMaxVolume = mv
                }
            }
        }
        context.contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI,
            true,
            volumeObserver
        )
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            context.contentResolver.unregisterContentObserver(volumeObserver)
        }
    }

    val lazyListState = rememberLazyListState()

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.WIDE_DIALOG,
        lazyListState = lazyListState,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { 
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.primary
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header - Fixed at top, doesn't scroll
            PlaybackHeader(
                haptics = haptics
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Scrollable content inside AdaptiveSheetScrollContainer
            AdaptiveSheetScrollContainer(
                lazyListState = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) { endPadding ->
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, end = endPadding),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                // Volume + Active Device (merged)
                item {
                    AnimateIn {
                        VolumeAndDeviceCard(
                            volume = volume,
                            systemVolume = systemVolume,
                            systemMaxVolume = systemMaxVolume,
                            appSettings = appSettings,
                            context = context,
                            onVolumeChange = onVolumeChange,
                            onSystemVolumeChange = { newVolume ->
                                systemVolume = newVolume
                            },
                            location = currentLocation,
                            onSwitchDevice = {
                                musicViewModel.showOutputSwitcherDialog()
                            },
                            onRefreshDevices = onRefreshDevices,
                            haptics = haptics
                        )
                    }
                }

                // Place StreamingQualityCard below the Volume control when in STREAMING mode
                if (appMode == "STREAMING") {
                    item {
                        AnimateIn {
                            StreamingQualityCard(
                                selectedQuality = streamingQuality,
                                onOpenQualitySheet = {
                                    showQualitySheet = true
                                },
                                haptics = haptics,
                                context = context,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
                
                // Playback Quick Settings Section
                item {
                    AnimateIn {
                        PlaybackQuickSettingsCard(
                                    appMode = appMode,
                            gaplessPlayback = gaplessPlayback,
                            useSystemVolume = useSystemVolume,
                            stopPlaybackOnZeroVolume = stopPlaybackOnZeroVolume,
                            resumeOnDeviceReconnect = resumeOnDeviceReconnect,
                            showPlayedQueueSongs = showPlayedQueueSongs,
                            crossfadeEnabled = crossfadeEnabled,
                            crossfadeDuration = crossfadeDuration,
                            onGaplessPlaybackChange = {
                                musicViewModel.setGaplessPlayback(it)
                            },
                            onUseSystemVolumeChange = { musicViewModel.setUseSystemVolumeMode(it) },
                            onStopPlaybackOnZeroVolumeChange = { appSettings.setStopPlaybackOnZeroVolume(it) },
                            onResumeOnDeviceReconnectChange = { appSettings.setResumeOnDeviceReconnect(it) },
                            onShowPlayedQueueSongsChange = { appSettings.setHidePlayedQueueSongs(!it) },
                            onCrossfadeEnabledChange = { appSettings.setCrossfade(it) },
                            onCrossfadeDurationChange = { appSettings.setCrossfadeDuration(it) },
                            onNavigateToSettings = onNavigateToSettings,
                            onNavigateToGoMode = onNavigateToGoMode,
                            haptics = haptics,
                            context = context,
                            isAudioOffloadActive = isAudioOffloadActive,
                            isOffloadEnforced = isOffloadEnforced
                        )
                    }
                }
                
                // Audio Effects Section
                item {
                    AnimateIn {
                        AudioEffectsCard(
                            audioNormalization = audioNormalization,
                            replayGain = replayGain,
                            equalizerEnabled = equalizerEnabled,
                            bassBoostEnabled = bassBoostEnabled,
                            bassBoostStrength = bassBoostStrength,
                            virtualizerEnabled = virtualizerEnabled,
                            virtualizerStrength = virtualizerStrength,
                            onAudioNormalizationChange = { appSettings.setAudioNormalization(it) },
                            onReplayGainChange = { appSettings.setReplayGain(it) },
                            onEqualizerEnabledChange = { musicViewModel.setEqualizerEnabled(it) },
                            onBassBoostEnabledChange = { enabled ->
                                appSettings.setBassBoostEnabled(enabled)
                                musicViewModel.setBassBoost(enabled, bassBoostStrength.toShort())
                            },
                            onBassBoostStrengthChange = { strength ->
                                appSettings.setBassBoostStrength(strength)
                                musicViewModel.setBassBoost(bassBoostEnabled, strength.toShort())
                            },
                            onVirtualizerEnabledChange = { enabled ->
                                appSettings.setVirtualizerEnabled(enabled)
                                musicViewModel.setVirtualizer(enabled, virtualizerStrength.toShort())
                            },
                            onVirtualizerStrengthChange = { strength ->
                                appSettings.setVirtualizerStrength(strength)
                                musicViewModel.setVirtualizer(virtualizerEnabled, strength.toShort())
                            },
                            onNavigateToEqualizer = onNavigateToEqualizer,
                            haptics = haptics,
                            context = context,
                            isAudioOffloadActive = isAudioOffloadActive,
                            isOffloadEnforced = isOffloadEnforced
                        )
                    }
                }

                // Playback Speed & Pitch Section
                item {
                    AnimateIn {
                        PlaybackSpeedAndPitchCard(
                            currentSpeed = playbackSpeed,
                            currentPitch = playbackPitch,
                            syncEnabled = syncSpeedAndPitch,
                            onSyncChange = { appSettings.setSyncSpeedAndPitch(it) },
                            onOpenSpeedPitchSheet = { showSpeedPitchSheet = true },
                            haptics = haptics,
                            context = context
                        )
                    }
                }
            }
        }
    }
}

    // Quality selection bottom sheet
    if (showQualitySheet) {
        QualitySelectionBottomSheet(
            selectedQuality = streamingQuality.uppercase(),
            onDismiss = { showQualitySheet = false },
            onSelect = { quality ->
                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                appSettings.setStreamingQuality(quality)
                // Show restart dialog
                restartDialogMessage = "Streaming quality changed. Restart the app to apply the new audio settings."
                showRestartDialog = true
                showQualitySheet = false
            }
        )
    }

    // Restart dialog
    if (showRestartDialog) {
        AppRestartDialog(
            onDismiss = { showRestartDialog = false },
            onRestart = { AppRestarter.restartApp(context) },
            onContinue = { /* continue without restart */ },
            message = restartDialogMessage
        )
    }

    if (showSpeedPitchSheet) {
        PlaybackSpeedAndPitchBottomSheet(
            currentSpeed = playbackSpeed,
            currentPitch = playbackPitch,
            syncEnabled = syncSpeedAndPitch,
            onSyncChange = { appSettings.setSyncSpeedAndPitch(it) },
            onDismiss = { showSpeedPitchSheet = false },
            onSave = { speed, pitch ->
                musicViewModel.setPlaybackSpeed(speed)
                musicViewModel.setPlaybackPitch(pitch)
                showSpeedPitchSheet = false
            },
            onSetDefaultSpeed = { speed ->
                musicViewModel.setDefaultPlaybackSpeed(speed)
            }
        )
    }
}

@Composable
private fun PlaybackHeader(
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = context.getString(R.string.bottomsheet_playback),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape
                    )
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    text = context.getString(R.string.audio_settings),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun VolumeAndDeviceCard(
    volume: Float,
    systemVolume: Float,
    systemMaxVolume: Int,
    appSettings: AppSettings,
    context: Context,
    onVolumeChange: (Float) -> Unit,
    onSystemVolumeChange: (Float) -> Unit,
    location: PlaybackLocation?,
    onSwitchDevice: () -> Unit,
    onRefreshDevices: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    modifier: Modifier = Modifier
) {
    val useSystemVolume by appSettings.useSystemVolume.collectAsState()
    val currentVolume = if (useSystemVolume) systemVolume else volume

    var localVolume by remember { mutableFloatStateOf(currentVolume) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentVolume) {
        if (!isDragging) localVolume = currentVolume
    }

    val setSystemVolume = { newVolume: Float ->
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val targetStep = (newVolume * systemMaxVolume).toInt().coerceIn(0, systemMaxVolume)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "devicePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val cardShape = RoundedCornerShape(
        topStart = 26.dp, topEnd = 20.dp,
        bottomStart = 20.dp, bottomEnd = 30.dp
    )
    val expressiveIconShape = rememberExpressiveShape("COOKIE_7", CircleShape)

    var isRefreshing by remember { mutableStateOf(false) }
    val refreshRotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        finishedListener = { isRefreshing = false },
        label = "refreshRotation"
    )

    val typeDescription = when {
        location?.id?.startsWith("bt_") == true -> "Bluetooth device"
        location?.id == "wired_headset" -> "Wired headphones"
        location?.id == "speaker" -> "Built-in speaker"
        else -> "Audio device"
    }

    val primaryVariant = MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                )
                .padding(18.dp)
        ) {
            // Device section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSwitchDevice() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (location != null) {
                    Surface(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            },
                        shape = expressiveIconShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = getDeviceIcon(location),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = location.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = typeDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryVariant.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = expressiveIconShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = RhythmIcons.Speaker,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.bottomsheet_no_device),
                            style = MaterialTheme.typography.titleMedium,
                            color = primaryVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalIconButton(
                    onClick = {
                        isRefreshing = true
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        onRefreshDevices()
                    },
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 16.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = RhythmIcons.Refresh,
                        contentDescription = stringResource(R.string.content_desc_refresh_devices),
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = refreshRotation }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = onSwitchDevice,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = MaterialSymbolIcon("sync_alt", filled = true),
                            contentDescription = stringResource(R.string.content_desc_switch_device),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Volume section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (useSystemVolume) "System Volume" else "App Volume",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryVariant
                    )
                }

                Text(
                    text = "${(currentVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            VolumeSlider(
                value = localVolume,
                onValueChange = { newVolume ->
                    localVolume = newVolume
                    isDragging = true
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    if (useSystemVolume) setSystemVolume(newVolume) else onVolumeChange(newVolume)
                },
                onValueChangeFinished = if (useSystemVolume) {
                    {
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val cv = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val mv = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        onSystemVolumeChange(cv.toFloat() / mv.toFloat())
                        isDragging = false
                    }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                accentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PlaybackSpeedAndPitchCard(
    currentSpeed: Float,
    currentPitch: Float,
    syncEnabled: Boolean,
    onSyncChange: (Boolean) -> Unit,
    onOpenSpeedPitchSheet: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    context: Context,
    modifier: Modifier = Modifier
) {
    fun formatClean(v: Float): String {
        val s = String.format(java.util.Locale.US, "%.3f", v)
        return s.dropLastWhile { it == '0' }.dropLastWhile { it == '.' }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Material3SettingsGroup(
            title = context.getString(R.string.player_speed_and_pitch),
            containerColor = MaterialTheme.colorScheme.surface,
            items = listOf(
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("sync_alt", filled = true),
                    title = { Text(text = context.getString(R.string.player_sync_speed_pitch)) },
                    description = { Text(text = context.getString(R.string.player_sync_speed_pitch_desc)) },
                    trailingContent = {
                        chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch(
                            checked = syncEnabled,
                            onCheckedChange = {
                                onSyncChange(it)
                            }
                        )
                    },
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onSyncChange(!syncEnabled)
                    }
                ),
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("speed", filled = true),
                    title = { Text(text = context.getString(R.string.player_playback_speed)) },
                    description = { Text(text = context.getString(R.string.player_current_rate, formatClean(currentSpeed))) },
                    trailingContent = {
                        Icon(
                            imageVector = RhythmIcons.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onOpenSpeedPitchSheet()
                    }
                ),
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("graphic_eq", filled = true),
                    title = { Text(text = context.getString(R.string.settings_playback_pitch)) },
                    description = { Text(text = context.getString(R.string.player_current_rate, formatClean(currentPitch))) },
                    trailingContent = {
                        Icon(
                            imageVector = RhythmIcons.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onOpenSpeedPitchSheet()
                    }
                )
            )
        )
    }
}

@Composable
private fun PlaybackQuickSettingsCard(
    appMode: String,
    gaplessPlayback: Boolean,
    useSystemVolume: Boolean,
    stopPlaybackOnZeroVolume: Boolean,
    resumeOnDeviceReconnect: Boolean,
    showPlayedQueueSongs: Boolean,
    crossfadeEnabled: Boolean,
    crossfadeDuration: Float,
    onGaplessPlaybackChange: (Boolean) -> Unit,
    onUseSystemVolumeChange: (Boolean) -> Unit,
    onStopPlaybackOnZeroVolumeChange: (Boolean) -> Unit,
    onResumeOnDeviceReconnectChange: (Boolean) -> Unit,
    onShowPlayedQueueSongsChange: (Boolean) -> Unit,
    onCrossfadeEnabledChange: (Boolean) -> Unit,
    onCrossfadeDurationChange: (Float) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    context: Context,
    modifier: Modifier = Modifier,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToGoMode: (() -> Unit)? = null,
    isAudioOffloadActive: Boolean = false,
    isOffloadEnforced: Boolean = false
) {
    val quickSettingsItems = buildList {
        add(
            Material3SettingsItem(
                icon = RhythmIcons.VolumeUp,
                title = { Text(text = context.getString(R.string.playback_use_system_volume)) },
                description = { Text(text = context.getString(R.string.playback_use_system_volume_desc)) },
                trailingContent = {
                    AnimatedAudioSwitch(
                        checked = useSystemVolume,
                        onCheckedChange = {
                            onUseSystemVolumeChange(it)
                        }
                    )
                },
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    onUseSystemVolumeChange(!useSystemVolume)
                }
            )
        )
        add(
            Material3SettingsItem(
                icon = RhythmIcons.VolumeOff,
                title = { Text(text = context.getString(R.string.settings_stop_playback_on_zero_volume)) },
                description = { Text(text = context.getString(R.string.settings_stop_playback_on_zero_volume_desc)) },
                trailingContent = {
                    AnimatedAudioSwitch(
                        checked = stopPlaybackOnZeroVolume,
                        onCheckedChange = {
                            onStopPlaybackOnZeroVolumeChange(it)
                        }
                    )
                },
                scope = chromahub.rhythm.app.shared.presentation.components.SettingScope.BOTH,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    onStopPlaybackOnZeroVolumeChange(!stopPlaybackOnZeroVolume)
                }
            )
        )
        add(
            Material3SettingsItem(
                icon = RhythmIcons.Bluetooth,
                title = { Text(text = context.getString(R.string.settings_resume_on_device_reconnect)) },
                description = { Text(text = context.getString(R.string.settings_resume_on_device_reconnect_desc)) },
                trailingContent = {
                    AnimatedAudioSwitch(
                        checked = resumeOnDeviceReconnect,
                        onCheckedChange = {
                            onResumeOnDeviceReconnectChange(it)
                        }
                    )
                },
                scope = chromahub.rhythm.app.shared.presentation.components.SettingScope.BOTH,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    onResumeOnDeviceReconnectChange(!resumeOnDeviceReconnect)
                }
            )
        )
        add(
            Material3SettingsItem(
                icon = RhythmIcons.Repeat,
                title = { Text(text = context.getString(R.string.settings_gapless_playback)) },
                description = { Text(text = context.getString(R.string.settings_gapless_playback_desc)) },
                trailingContent = {
                    AnimatedAudioSwitch(
                        checked = gaplessPlayback,
                        onCheckedChange = {
                            onGaplessPlaybackChange(it)
                        }
                    )
                },
                scope = chromahub.rhythm.app.shared.presentation.components.SettingScope.BOTH,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    onGaplessPlaybackChange(!gaplessPlayback)
                }
            )
        )
        add(
            Material3SettingsItem(
                icon = RhythmIcons.List,
                title = { Text(text = context.getString(R.string.settings_show_played_queue_songs)) },
                description = { Text(text = context.getString(R.string.settings_show_played_queue_songs_desc)) },
                trailingContent = {
                    AnimatedAudioSwitch(
                        checked = showPlayedQueueSongs,
                        onCheckedChange = {
                            onShowPlayedQueueSongsChange(it)
                        }
                    )
                },
                scope = chromahub.rhythm.app.shared.presentation.components.SettingScope.BOTH,
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    onShowPlayedQueueSongsChange(!showPlayedQueueSongs)
                }
            )
        )
        add(
            Material3SettingsItem(
                icon = RhythmIcons.Tune,
                title = { Text(text = context.getString(R.string.settings_crossfade)) },
                description = {
                    val baseDesc = context.getString(R.string.settings_crossfade_desc)
                    val fullDesc = when {
                        isOffloadEnforced -> "Disabled under Lite Mode to conserve battery."
                        isAudioOffloadActive && !crossfadeEnabled -> "$baseDesc\n(Enabling will disable hardware Audio Offload)"
                        else -> baseDesc
                    }
                    Text(text = fullDesc)
                },
                trailingContent = {
                    AnimatedAudioSwitch(
                        checked = if (isOffloadEnforced) false else crossfadeEnabled,
                        onCheckedChange = {
                            if (!isOffloadEnforced) {
                                onCrossfadeEnabledChange(it)
                            }
                        },
                        enabled = !isOffloadEnforced
                    )
                },
                scope = chromahub.rhythm.app.shared.presentation.components.SettingScope.BOTH,
                enabled = !isOffloadEnforced,
                onClick = {
                    if (!isOffloadEnforced) {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onCrossfadeEnabledChange(!crossfadeEnabled)
                    }
                }
            )
        )

        if (crossfadeEnabled && !isOffloadEnforced) {
            add(
                Material3SettingsItem(
                    icon = RhythmIcons.Tune,
                    title = { Text(text = context.getString(R.string.settings_crossfade_duration)) },
                    description = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = context.getString(R.string.settings_crossfade_duration_desc, crossfadeDuration),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = crossfadeDuration,
                                onValueChange = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                    onCrossfadeDurationChange(it)
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                )
            )
        }

        onNavigateToSettings?.let { navigateToSettings ->
            add(
                Material3SettingsItem(
                    icon = RhythmIcons.Settings,
                    title = {
                        Text(
                            text = if (appMode == "STREAMING") {
                                "Go Mode"
                            } else {
                                context.getString(R.string.settings_queue_playback_title)
                            }
                        )
                    },
                    description = {
                        Text(
                            text = if (appMode == "STREAMING") {
                                "Open Go settings for provider and streaming controls"
                            } else {
                                context.getString(R.string.settings_queue_playback_desc)
                            }
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = RhythmIcons.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        if (appMode == "STREAMING") {
                            if (onNavigateToGoMode != null) {
                                onNavigateToGoMode.invoke()
                            } else {
                                navigateToSettings.invoke()
                            }
                        } else {
                            navigateToSettings.invoke()
                        }
                    }
                    ,scope = chromahub.rhythm.app.shared.presentation.components.SettingScope.BOTH
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Material3SettingsGroup(
            title = context.getString(R.string.playback_settings),
            items = quickSettingsItems,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun StreamingQualityCard(
    selectedQuality: String,
    onOpenQualitySheet: () -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    context: Context,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Material3SettingsGroup(
            title = context.getString(R.string.streaming_settings_quality),
            items = listOf(
                Material3SettingsItem(
                    icon = MaterialSymbolIcon("high_quality", filled = true),
                    title = { Text(text = context.getString(R.string.streaming_settings_quality)) },
                    description = { Text(text = selectedQuality) },
                    trailingContent = {
                        Icon(
                            imageVector = RhythmIcons.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        onOpenQualitySheet()
                    }
                )
            ),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun PlaybackPitchCard(
    currentPitch: Float,
    onPitchChange: (Float) -> Unit,
    syncEnabled: Boolean,
    onSyncChange: (Boolean) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    context: Context,
    modifier: Modifier = Modifier
) {
    var selectedPitch by remember(currentPitch) { mutableFloatStateOf(currentPitch) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MaterialSymbolIcon("graphic_eq", filled = true),
                    contentDescription = stringResource(R.string.settings_playback_pitch),
                    
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = context.getString(R.string.player_pitch_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Current pitch display
                Text(
                    text = "${String.format(Locale.ROOT, "%.2f", selectedPitch)}x",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sync toggle — expressive design matching settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(34.dp),
                        color = if (syncEnabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("sync_alt", filled = true),
                                contentDescription = null,
                                tint = if (syncEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = context.getString(R.string.sync_with_speed),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (syncEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch(
                    checked = syncEnabled,
                    onCheckedChange = {
                        onSyncChange(it)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Slider with labels
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.playbackbottomsheet_str_025x),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.playbackbottomsheet_str_30x),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Slider(
                    value = selectedPitch,
                    onValueChange = { newValue ->
                        selectedPitch = newValue
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    },
                    onValueChangeFinished = {
                        onPitchChange(selectedPitch)
                    },
                    valueRange = 0.25f..3.0f,
                    steps = 54,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Quick preset buttons
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(listOf(0.5f, 0.75f, 0.8f, 0.9f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)) { presetPitch ->
                    AssistChip(
                        onClick = {
                            selectedPitch = presetPitch
                            onPitchChange(presetPitch)
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        },
                        label = {
                            Text(
                                text = "${String.format(Locale.ROOT, "%.2f", presetPitch)}x",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selectedPitch == presetPitch)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (selectedPitch == presetPitch)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedAudioSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier
    )
}


@Composable
private fun getDeviceIcon(location: PlaybackLocation) = when {
    location.id.startsWith("bt_") -> RhythmIcons.BluetoothFilled
    location.id == "wired_headset" -> RhythmIcons.HeadphonesFilled
    location.id == "speaker" -> RhythmIcons.SpeakerFilled
    else -> RhythmIcons.Speaker
}

@Composable
private fun AnimateIn(
    modifier: Modifier = Modifier,
    delay: Int = 50,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
    }
}

@Composable
private fun AudioEffectsCard(
    audioNormalization: Boolean,
    replayGain: Boolean,
    equalizerEnabled: Boolean,
    bassBoostEnabled: Boolean,
    bassBoostStrength: Int,
    virtualizerEnabled: Boolean,
    virtualizerStrength: Int,
    onAudioNormalizationChange: (Boolean) -> Unit,
    onReplayGainChange: (Boolean) -> Unit,
    onEqualizerEnabledChange: (Boolean) -> Unit,
    onBassBoostEnabledChange: (Boolean) -> Unit,
    onBassBoostStrengthChange: (Int) -> Unit,
    onVirtualizerEnabledChange: (Boolean) -> Unit,
    onVirtualizerStrengthChange: (Int) -> Unit,
    onNavigateToEqualizer: (() -> Unit)? = null,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    context: Context,
    isAudioOffloadActive: Boolean = false,
    isOffloadEnforced: Boolean = false
) {
    val audioEffectItems = buildList {
        add(
            Material3SettingsItem(
                icon = RhythmIcons.Equalizer,
                title = { Text(text = context.getString(R.string.equalizer)) },
                description = {
                    val baseDesc = context.getString(R.string.settings_equalizer_desc)
                    val fullDesc = when {
                        isOffloadEnforced -> "Disabled under Lite Mode to conserve battery."
                        isAudioOffloadActive && !equalizerEnabled -> "$baseDesc\n(Enabling will disable hardware Audio Offload)"
                        else -> baseDesc
                    }
                    Text(text = fullDesc)
                },
                trailingContent = {
                    AnimatedAudioSwitch(
                        checked = if (isOffloadEnforced) false else equalizerEnabled,
                        onCheckedChange = {
                            if (!isOffloadEnforced) {
                                onEqualizerEnabledChange(it)
                            }
                        },
                        enabled = !isOffloadEnforced
                    )
                },
                enabled = !isOffloadEnforced,
                onClick = {
                    if (!isOffloadEnforced) {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onEqualizerEnabledChange(!equalizerEnabled)
                    }
                }
            )
        )
        add(
            Material3SettingsItem(
                icon = RhythmIcons.VolumeUp,
                title = { Text(text = context.getString(R.string.bass_boost)) },
                description = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val baseDesc = context.getString(R.string.bass_boost_desc)
                        val fullDesc = when {
                            isOffloadEnforced -> "Disabled under Lite Mode to conserve battery."
                            isAudioOffloadActive && !bassBoostEnabled -> "$baseDesc\n(Enabling will disable hardware Audio Offload)"
                            else -> baseDesc
                        }
                        Text(text = fullDesc)
                        if (bassBoostEnabled && !isOffloadEnforced) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${context.getString(R.string.strength)} ${bassBoostStrength / 10}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Slider(
                                value = bassBoostStrength.toFloat(),
                                onValueChange = { onBassBoostStrengthChange(it.toInt()) },
                                valueRange = 0f..1000f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                onValueChangeFinished = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                }
                            )
                        }
                    }
                },
                trailingContent = {
                    AnimatedAudioSwitch(
                        checked = if (isOffloadEnforced) false else bassBoostEnabled,
                        onCheckedChange = {
                            if (!isOffloadEnforced) {
                                onBassBoostEnabledChange(it)
                            }
                        },
                        enabled = !isOffloadEnforced
                    )
                },
                enabled = !isOffloadEnforced,
                onClick = {
                    if (!isOffloadEnforced) {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onBassBoostEnabledChange(!bassBoostEnabled)
                    }
                }
            )
        )
        add(
            Material3SettingsItem(
                icon = RhythmIcons.Headphones,
                title = { Text(text = context.getString(R.string.virtualizer)) },
                description = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val baseDesc = context.getString(R.string.virtualizer_desc)
                        val fullDesc = when {
                            isOffloadEnforced -> "Disabled under Lite Mode to conserve battery."
                            isAudioOffloadActive && !virtualizerEnabled -> "$baseDesc\n(Enabling will disable hardware Audio Offload)"
                            else -> baseDesc
                        }
                        Text(text = fullDesc)
                        if (virtualizerEnabled && !isOffloadEnforced) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${context.getString(R.string.strength)} ${virtualizerStrength / 10}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Slider(
                                value = virtualizerStrength.toFloat(),
                                onValueChange = { onVirtualizerStrengthChange(it.toInt()) },
                                valueRange = 0f..1000f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                onValueChangeFinished = {
                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                }
                            )
                        }
                    }
                },
                trailingContent = {
                    AnimatedAudioSwitch(
                        checked = if (isOffloadEnforced) false else virtualizerEnabled,
                        onCheckedChange = {
                            if (!isOffloadEnforced) {
                                onVirtualizerEnabledChange(it)
                            }
                        },
                        enabled = !isOffloadEnforced
                    )
                },
                enabled = !isOffloadEnforced,
                onClick = {
                    if (!isOffloadEnforced) {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        onVirtualizerEnabledChange(!virtualizerEnabled)
                    }
                }
            )
        )

        onNavigateToEqualizer?.let { navigateToEqualizer ->
            add(
                Material3SettingsItem(
                    icon = RhythmIcons.Equalizer,
                    title = { Text(text = context.getString(R.string.open_equalizer_settings)) },
                    description = { Text(text = context.getString(R.string.settings_equalizer_desc)) },
                    trailingContent = {
                        Icon(
                            imageVector = RhythmIcons.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        navigateToEqualizer.invoke()
                    }
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Material3SettingsGroup(
            title = if (isAudioOffloadActive && !bassBoostEnabled && !virtualizerEnabled && !replayGain && !equalizerEnabled) {
                "${context.getString(R.string.audio_effects)} (Audio Offload Active)"
            } else {
                context.getString(R.string.audio_effects)
            },
            items = audioEffectItems,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualitySelectionBottomSheet(
    selectedQuality: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.WIDE_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = stringResource(id = chromahub.rhythm.app.R.string.streaming_settings_quality),
            subtitle = stringResource(id = chromahub.rhythm.app.R.string.streaming_settings_quality_sheet_desc),
            visible = true
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {

            Spacer(modifier = Modifier.height(8.dp))

            val streamingQualityOptions = listOf(
                Pair("LOW", chromahub.rhythm.app.R.string.streaming_quality_low),
                Pair("NORMAL", chromahub.rhythm.app.R.string.streaming_quality_normal),
                Pair("HIGH", chromahub.rhythm.app.R.string.streaming_quality_high),
                Pair("LOSSLESS", chromahub.rhythm.app.R.string.streaming_quality_lossless)
            )

            streamingQualityOptions.forEach { option ->
                val isSelected = selectedQuality == option.first

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                    onClick = { onSelect(option.first) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("high_quality"),
                            contentDescription = null,
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = option.second),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = RhythmIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

