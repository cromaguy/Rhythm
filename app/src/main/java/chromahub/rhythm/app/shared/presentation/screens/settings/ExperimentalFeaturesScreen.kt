@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings



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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import chromahub.rhythm.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.BuildConfig
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import chromahub.rhythm.app.shared.data.repository.StatsTimeRange
import chromahub.rhythm.app.util.BluetoothLyricsFormatter
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
fun ExperimentalFeaturesScreen(
    onBackClick: () -> Unit,
    onNavigateTo: (String) -> Unit = {},
    onNavigateToGoSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val appMode by appSettings.appMode.collectAsState()
    val hapticFeedbackEnabled by appSettings.hapticFeedbackEnabled.collectAsState()
    val enableAlbumEditing by appSettings.enableAlbumEditing.collectAsState()
    val skipSilenceEnabled by appSettings.skipSilenceEnabled.collectAsState()
    val replayGain by appSettings.replayGain.collectAsState()
    val audioRoutingMode by appSettings.audioRoutingMode.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    // Third-party integrations states
    val broadcastStatusEnabled by appSettings.broadcastStatusEnabled.collectAsState()
    val bluetoothLyricsEnabled by appSettings.bluetoothLyricsEnabled.collectAsState()
    val bluetoothLyricsLegacyCarModeEnabled by appSettings.bluetoothLyricsLegacyCarModeEnabled.collectAsState()
    val bluetoothLyricsOffsetMs by appSettings.bluetoothLyricsOffsetMs.collectAsState()
    val bluetoothLyricsMaxChunkChars by appSettings.bluetoothLyricsMaxChunkChars.collectAsState()
    val bluetoothLyricsScrollCharsPerSecond by appSettings.bluetoothLyricsScrollCharsPerSecond.collectAsState()
    val bluetoothLyricsMinChunkHoldMs by appSettings.bluetoothLyricsMinChunkHoldMs.collectAsState()
    
    val forcePlayerCompactMode by appSettings.forcePlayerCompactMode.collectAsState()
    val useExperimentalPlayerUi by appSettings.useExperimentalPlayerUi.collectAsState()
    
    val updaterViewModel: AppUpdaterViewModel = viewModel()
    val latestVersion by updaterViewModel.latestVersion.collectAsState()

    var showRestartDialog by remember { mutableStateOf(false) }
    var restartDialogMessage by remember { mutableStateOf("") }
    var showBluetoothLyricsTuningDialog by remember { mutableStateOf(false) }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_experimental),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        val settingGroups = buildList {
            add(
                SettingGroup(
                    title = context.getString(R.string.settings_audio_effects),
                    items = listOf(
                        SettingItem(
                            MaterialSymbolIcon("hearing"),
                            context.getString(R.string.settings_skip_silence),
                            context.getString(R.string.settings_skip_silence_desc),
                            toggleState = skipSilenceEnabled,
                            onToggleChange = {
                                appSettings.setSkipSilenceEnabled(it)
                            }
                        ),
                        SettingItem(
                            MaterialSymbolIcon("volume_up"),
                            context.getString(R.string.replay_gain),
                            context.getString(R.string.replay_gain_desc),
                            onClick = { onNavigateTo(SettingsRoutes.REPLAY_GAIN) }
                        )
                    )
                )
            )


            add(
                SettingGroup(
                    title = context.getString(R.string.settings_metadata_editing),
                    items = listOf(
                        SettingItem(
                            MaterialSymbolIcon("edit"),
                            context.getString(R.string.settings_enable_album_editing),
                            context.getString(R.string.settings_enable_album_editing_desc),
                            toggleState = enableAlbumEditing,
                            onToggleChange = { appSettings.setEnableAlbumEditing(it) }
                        )
                    )
                )
            )
            
            // Developer/Debugging features group
            add(
                SettingGroup(
                    title = context.getString(R.string.exp_developer_debugging),
                    items = listOf(
                        SettingItem(
                            RhythmIcons.Code,
                            context.getString(R.string.exp_codec_monitoring),
                            context.getString(R.string.exp_codec_monitoring_desc),
                            toggleState = appSettings.codecMonitoringEnabled.collectAsState().value,
                            onToggleChange = { appSettings.setCodecMonitoringEnabled(it) }
                        ),
                        SettingItem(
                            RhythmIcons.Headphones,
                            context.getString(R.string.exp_audio_device_logging),
                            context.getString(R.string.exp_audio_device_logging_desc),
                            toggleState = appSettings.audioDeviceLoggingEnabled.collectAsState().value,
                            onToggleChange = { appSettings.setAudioDeviceLoggingEnabled(it) }
                        ),
                        SettingItem(
                            MaterialSymbolIcon("restart_alt"),
                            context.getString(R.string.exp_launch_onboarding),
                            context.getString(R.string.exp_launch_onboarding_desc),
                            onClick = { appSettings.setOnboardingCompleted(false) }
                        ),
                        SettingItem(
                            RhythmIcons.BugReport,
                            context.getString(R.string.exp_test_crash),
                            context.getString(R.string.exp_test_crash_desc),
                            onClick = { chromahub.rhythm.app.util.CrashReporter.testCrash() }
                        ),
                        SettingItem(
                            MaterialSymbolIcon("smartphone"),
                            context.getString(R.string.exp_force_player_compact_mode),
                            context.getString(R.string.exp_force_player_compact_mode_desc),
                            toggleState = forcePlayerCompactMode,
                            onToggleChange = { appSettings.setForcePlayerCompactMode(it) }
                        ),
                        SettingItem(
                            MaterialSymbolIcon("cloud_queue"),
                            context.getString(R.string.exp_go_mode),
                            context.getString(R.string.exp_go_mode_desc),
                            toggleState = appMode == "STREAMING",
                            onToggleChange = { enabled ->
                                appSettings.setAppMode(if (enabled) "STREAMING" else "LOCAL")
                            },
                            onClick = { onNavigateToGoSettings?.invoke() }
                        )
                    )
                )
            )
            
            // Third-Party Integrations group
            add(
                SettingGroup(
                    title = context.getString(R.string.exp_third_party_integrations),
                    items = listOf(
                        SettingItem(
                            MaterialSymbolIcon("wifi"),
                            context.getString(R.string.broadcast_status_enabled),
                            context.getString(R.string.broadcast_status_desc),
                            toggleState = broadcastStatusEnabled,
                            onToggleChange = { appSettings.setBroadcastStatusEnabled(it) }
                        ),
                        SettingItem(
                            MaterialSymbolIcon("lyrics"),
                            context.getString(R.string.bluetooth_lyrics_enabled),
                            context.getString(R.string.bluetooth_lyrics_desc),
                            toggleState = bluetoothLyricsEnabled,
                            onToggleChange = {
                                appSettings.setBluetoothLyricsEnabled(it)
                                if (it && !broadcastStatusEnabled) {
                                    appSettings.setBroadcastStatusEnabled(true)
                                }
                            }
                        ),
                        SettingItem(
                            MaterialSymbolIcon("directions_car"),
                            context.getString(R.string.bluetooth_lyrics_legacy_car_mode),
                            context.getString(R.string.bluetooth_lyrics_legacy_car_mode_desc),
                            enabled = bluetoothLyricsEnabled,
                            toggleState = bluetoothLyricsLegacyCarModeEnabled,
                            onToggleChange = { appSettings.setBluetoothLyricsLegacyCarModeEnabled(it) }
                        ),
                        SettingItem(
                            MaterialSymbolIcon("sync_alt"),
                            context.getString(R.string.bluetooth_lyrics_tuning_title),
                            context.getString(
                                R.string.bluetooth_lyrics_tuning_summary,
                                formatBluetoothLyricsOffsetValue(bluetoothLyricsOffsetMs),
                                bluetoothLyricsMaxChunkChars,
                                bluetoothLyricsScrollCharsPerSecond
                            ),
                            enabled = bluetoothLyricsEnabled,
                            onClick = { showBluetoothLyricsTuningDialog = true }
                        )
                    )
                )
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp)
        ) {
            items(settingGroups, key = { "setting_${it.title}_${settingGroups.indexOf(it)}" }) { group ->
                Spacer(modifier = Modifier.height(24.dp))

                val materialItems = group.items.map { item ->
                    toMaterial3SettingsItem(context = context, item = item)
                }

                Material3SettingsGroup(
                    title = group.title,
                    items = materialItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }



            item(key = "experimental_audio_routing_section") {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = context.getString(R.string.settings_audio_routing),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(34.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                tonalElevation = 0.dp
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Headphones,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = context.getString(R.string.settings_dac_usb_audio),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = context.getString(R.string.settings_dac_usb_audio_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        ExpressiveButtonGroup(
                            items = listOf(context.getString(R.string.settings_audio_routing_default), context.getString(R.string.settings_audio_routing_app), context.getString(R.string.settings_audio_routing_system)),
                            selectedIndex = when (audioRoutingMode) {
                                "default" -> 0
                                "app" -> 1
                                "system" -> 2
                                else -> 0
                            },
                            onItemClick = { index ->
                                when (index) {
                                    0 -> {
                                        appSettings.setAudioRoutingMode("default")
                                        showRestartDialog = true
                                        restartDialogMessage = "Audio routing changed to Default. Restart the app to apply the changes."
                                    }
                                    1 -> {
                                        appSettings.setAudioRoutingMode("app")
                                        showRestartDialog = true
                                        restartDialogMessage = "Audio routing changed to App mode. Restart the app to apply the changes."
                                    }
                                    2 -> {
                                        appSettings.setAudioRoutingMode("system")
                                        showRestartDialog = true
                                        restartDialogMessage = "Audio routing changed to System. Restart the app to apply the changes."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = when (audioRoutingMode) {
                                "app" -> context.getString(R.string.settings_audio_routing_app_desc)
                                "system" -> context.getString(R.string.settings_audio_routing_system_desc)
                                else -> context.getString(R.string.settings_audio_routing_default_desc)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("science"),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = context.getString(R.string.updates_experimental_coming),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showRestartDialog) {
        AppRestartDialog(
            onDismiss = { showRestartDialog = false },
            onRestart = {
                showRestartDialog = false
                chromahub.rhythm.app.util.AppRestarter.restartApp(context)
            },
            onContinue = {
                showRestartDialog = false
            },
            message = restartDialogMessage
        )
    }

    if (showBluetoothLyricsTuningDialog) {
        BluetoothLyricsTuningDialog(
            currentOffsetMs = bluetoothLyricsOffsetMs,
            currentMaxChunkChars = bluetoothLyricsMaxChunkChars,
            currentScrollCharsPerSecond = bluetoothLyricsScrollCharsPerSecond,
            currentMinChunkHoldMs = bluetoothLyricsMinChunkHoldMs,
            onApply = { offsetMs, maxChunkChars, scrollCharsPerSecond, minChunkHoldMs ->
                appSettings.setBluetoothLyricsOffsetMs(offsetMs)
                appSettings.setBluetoothLyricsMaxChunkChars(maxChunkChars)
                appSettings.setBluetoothLyricsScrollCharsPerSecond(scrollCharsPerSecond)
                appSettings.setBluetoothLyricsMinChunkHoldMs(minChunkHoldMs)
            },
            onDismiss = { showBluetoothLyricsTuningDialog = false }
        )
    }

    // Show update bottomsheet - removed, now handled globally in LocalNavigation
}


@Composable
private fun BluetoothLyricsTuningDialog(
    currentOffsetMs: Int,
    currentMaxChunkChars: Int,
    currentScrollCharsPerSecond: Int,
    currentMinChunkHoldMs: Int,
    onApply: (Int, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var draftOffsetMs by remember(currentOffsetMs) { mutableIntStateOf(currentOffsetMs) }
    var draftMaxChunkChars by remember(currentMaxChunkChars) { mutableIntStateOf(currentMaxChunkChars) }
    var draftScrollCharsPerSecond by remember(currentScrollCharsPerSecond) { mutableIntStateOf(currentScrollCharsPerSecond) }
    var draftMinChunkHoldMs by remember(currentMinChunkHoldMs) { mutableIntStateOf(currentMinChunkHoldMs) }
    val minOffset = AppSettings.BLUETOOTH_LYRICS_OFFSET_MIN_MS
    val maxOffset = AppSettings.BLUETOOTH_LYRICS_OFFSET_MAX_MS
    val presets = listOf(-1000, -500, -200, 0, 200, 500, 1000)
    val tuningPresets = listOf(
        BluetoothLyricsTuningPreset(
            label = stringResource(R.string.bluetooth_lyrics_preset_fast),
            maxChunkChars = 22,
            scrollCharsPerSecond = 0,
            minChunkHoldMs = 700
        ),
        BluetoothLyricsTuningPreset(
            label = stringResource(R.string.bluetooth_lyrics_preset_short),
            maxChunkChars = 26,
            scrollCharsPerSecond = 1,
            minChunkHoldMs = 900
        ),
        BluetoothLyricsTuningPreset(
            label = stringResource(R.string.bluetooth_lyrics_preset_scroll),
            maxChunkChars = 38,
            scrollCharsPerSecond = 6,
            minChunkHoldMs = 1200
        )
    )

    fun resetDrafts() {
        draftOffsetMs = 0
        draftMaxChunkChars = BluetoothLyricsFormatter.DEFAULT_MAX_CHUNK_CHARS
        draftScrollCharsPerSecond = BluetoothLyricsFormatter.DEFAULT_SCROLL_CHARS_PER_SECOND
        draftMinChunkHoldMs = BluetoothLyricsFormatter.DEFAULT_MIN_CHUNK_HOLD_MS
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = MaterialSymbolIcon("sync_alt"),
                contentDescription = null
            )
        },
        title = { Text(stringResource(R.string.bluetooth_lyrics_tuning_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.bluetooth_lyrics_tuning_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                BluetoothLyricsGuidance()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.bluetooth_lyrics_presets_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tuningPresets.forEach { preset ->
                            FilterChip(
                                selected = draftMaxChunkChars == preset.maxChunkChars &&
                                    draftScrollCharsPerSecond == preset.scrollCharsPerSecond &&
                                    draftMinChunkHoldMs == preset.minChunkHoldMs,
                                onClick = {
                                    draftMaxChunkChars = preset.maxChunkChars
                                    draftScrollCharsPerSecond = preset.scrollCharsPerSecond
                                    draftMinChunkHoldMs = preset.minChunkHoldMs
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                }

                BluetoothLyricsTuningSlider(
                    title = stringResource(R.string.bluetooth_lyrics_offset_title),
                    description = stringResource(R.string.bluetooth_lyrics_offset_help),
                    valueLabel = formatBluetoothLyricsOffsetValue(draftOffsetMs),
                    value = draftOffsetMs,
                    valueRange = minOffset..maxOffset,
                    step = AppSettings.BLUETOOTH_LYRICS_OFFSET_STEP_MS,
                    onValueChange = { draftOffsetMs = it }
                )

                BluetoothLyricsTuningSlider(
                    title = stringResource(R.string.bluetooth_lyrics_max_chunk_title),
                    description = stringResource(R.string.bluetooth_lyrics_max_chunk_help),
                    valueLabel = stringResource(
                        R.string.bluetooth_lyrics_max_chunk_value,
                        draftMaxChunkChars
                    ),
                    value = draftMaxChunkChars,
                    valueRange = BluetoothLyricsFormatter.MIN_MAX_CHUNK_CHARS..
                        BluetoothLyricsFormatter.MAX_MAX_CHUNK_CHARS,
                    step = 1,
                    onValueChange = { draftMaxChunkChars = BluetoothLyricsFormatter.coerceMaxChunkChars(it) }
                )

                BluetoothLyricsTuningSlider(
                    title = stringResource(R.string.bluetooth_lyrics_scroll_title),
                    description = stringResource(R.string.bluetooth_lyrics_scroll_help),
                    valueLabel = stringResource(
                        R.string.bluetooth_lyrics_scroll_value,
                        draftScrollCharsPerSecond
                    ),
                    value = draftScrollCharsPerSecond,
                    valueRange = BluetoothLyricsFormatter.MIN_SCROLL_CHARS_PER_SECOND..
                        BluetoothLyricsFormatter.MAX_SCROLL_CHARS_PER_SECOND,
                    step = 1,
                    onValueChange = {
                        draftScrollCharsPerSecond = BluetoothLyricsFormatter.coerceScrollCharsPerSecond(it)
                    }
                )

                BluetoothLyricsTuningSlider(
                    title = stringResource(R.string.bluetooth_lyrics_min_chunk_hold_title),
                    description = stringResource(R.string.bluetooth_lyrics_min_chunk_hold_help),
                    valueLabel = formatBluetoothLyricsHoldValue(draftMinChunkHoldMs),
                    value = draftMinChunkHoldMs,
                    valueRange = BluetoothLyricsFormatter.MIN_MIN_CHUNK_HOLD_MS..
                        BluetoothLyricsFormatter.MAX_MIN_CHUNK_HOLD_MS,
                    step = 100,
                    onValueChange = { draftMinChunkHoldMs = BluetoothLyricsFormatter.coerceMinChunkHoldMs(it) }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = draftOffsetMs == preset,
                            onClick = { draftOffsetMs = preset },
                            label = { Text(formatBluetoothLyricsOffsetValue(preset)) }
                        )
                    }
                }

                TextButton(onClick = ::resetDrafts) {
                    Text(stringResource(R.string.ui_reset))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        draftOffsetMs,
                        draftMaxChunkChars,
                        draftScrollCharsPerSecond,
                        draftMinChunkHoldMs
                    )
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.ui_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_cancel))
            }
        }
    )
}

private data class BluetoothLyricsTuningPreset(
    val label: String,
    val maxChunkChars: Int,
    val scrollCharsPerSecond: Int,
    val minChunkHoldMs: Int
)

@Composable
private fun BluetoothLyricsGuidance() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.bluetooth_lyrics_how_to_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.bluetooth_lyrics_how_to_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BluetoothLyricsTuningSlider(
    title: String,
    description: String,
    valueLabel: String,
    value: Int,
    valueRange: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit
) {
    val safeStep = step.coerceAtLeast(1)
    val sliderSteps = ((valueRange.last - valueRange.first) / safeStep - 1).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { rawValue ->
                onValueChange(roundBluetoothLyricsSliderValue(rawValue, valueRange, safeStep))
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = sliderSteps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun roundBluetoothLyricsSliderValue(
    rawValue: Float,
    valueRange: IntRange,
    step: Int
): Int {
    val min = valueRange.first
    val roundedSteps = kotlin.math.round((rawValue - min) / step).toInt()
    return (min + roundedSteps * step).coerceIn(valueRange.first, valueRange.last)
}

private fun formatBluetoothLyricsOffsetValue(offsetMs: Int): String {
    return when {
        offsetMs > 0 -> "+${offsetMs}ms"
        else -> "${offsetMs}ms"
    }
}

private fun formatBluetoothLyricsHoldValue(holdMs: Int): String {
    return if (holdMs >= 1000 && holdMs % 1000 == 0) {
        "${holdMs / 1000}s"
    } else {
        "${holdMs}ms"
    }
}


fun getUpdateSourceLabel(context: Context, source: String): String {
    return when (source.lowercase()) {
        "github" -> context.getString(R.string.updates_source_github_label)
        "fdroid" -> context.getString(R.string.updates_source_fdroid_label)
        else -> context.getString(R.string.updates_source_installed_label)
    }
}



/**
 * Toggle card for individual decoration elements
 */
@Composable
fun DecorationToggleCard(
    title: String,
    description: String,
    icon: MaterialSymbolIcon,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon with background
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isEnabled)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            // Toggle switch
            TunerAnimatedSwitch(
                checked = isEnabled,
                onCheckedChange = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    onToggle(it)
                }
            )
        }
    }
}



fun getFestivalDisplayName(festivalType: String): String {
    return when (festivalType) {
        "CHRISTMAS" -> "Christmas"
        "NEW_YEAR" -> "New Year"
        "NONE" -> "None"
        "CUSTOM" -> "Custom"
        else -> "Not selected"
    }
}
