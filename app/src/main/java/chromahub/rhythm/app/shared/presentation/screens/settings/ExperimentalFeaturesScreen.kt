/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

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
import androidx.compose.ui.unit.TextUnit
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
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.groupedBottomSheetItemShape
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
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
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonSize
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonType
import chromahub.rhythm.app.shared.presentation.components.common.RhythmButtonWeighted
import chromahub.rhythm.app.shared.presentation.components.common.RhythmGroupedButton
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.flow.distinctUntilChanged

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
    LabsSettingsScreen(
        onBackClick = onBackClick,
        onNavigateTo = onNavigateTo,
        onNavigateToGoSettings = onNavigateToGoSettings
    )
}

@Composable
fun LabsSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateTo: (String) -> Unit = {},
    onNavigateToGoSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val appMode by appSettings.appMode.collectAsState()
    val hapticFeedbackEnabled by appSettings.hapticFeedbackEnabled.collectAsState()
    val enableAlbumEditing by appSettings.enableAlbumEditing.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    val forcePlayerCompactMode by appSettings.forcePlayerCompactMode.collectAsState()
    
    val updaterViewModel: AppUpdaterViewModel = viewModel()
    val latestVersion by updaterViewModel.latestVersion.collectAsState()

    var showRestartDialog by remember { mutableStateOf(false) }
    var restartDialogMessage by remember { mutableStateOf("") }

    val audioRoutingMode by appSettings.audioRoutingMode.collectAsState()
    val isBitPerfect = audioRoutingMode == "app"
    var showAudioRoutingDialog by remember { mutableStateOf(false) }

    val playbackPrefs = remember(appSettings) { appSettings.playbackPrefs }
    val uiPrefs = remember(appSettings) { appSettings.uiPrefs }
    val streamingPrefs = remember(appSettings) { appSettings.streamingPrefs }

    val sampleRateCapHz by playbackPrefs.sampleRateCapHz.collectAsState()
    val surroundDownmixEnabled by playbackPrefs.surroundDownmixEnabled.collectAsState()
    val alacDecoderEnabled by playbackPrefs.alacDecoderEnabled.collectAsState()
    val musicHapticsEnabled by playbackPrefs.musicHapticsEnabled.collectAsState()
    val musicHapticsIntensity by playbackPrefs.musicHapticsIntensity.collectAsState()
    val autoMixBpmSync by playbackPrefs.autoMixBpmSyncEnabled.collectAsState()
    val isolatePreview by appSettings.externalPlayerIsolatePreview.collectAsState()
    val unifiedLibraryEnabled by appSettings.unifiedLibraryEnabled.collectAsState()
    val playerAmbientBackdropEnabled by appSettings.playerAmbientBackdropEnabled.collectAsState()
    val agslFluidBackdropEnabled by uiPrefs.agslFluidBackdropEnabled.collectAsState()
    val statusBarTickerEnabled by uiPrefs.statusBarTickerEnabled.collectAsState()
    val stallMonitorEnabled by appSettings.mainThreadStallMonitorEnabled.collectAsState()
    val playlistSyncUri by appSettings.playlistSyncUri.collectAsState()

    val listenBrainzToken by streamingPrefs.listenBrainzToken.collectAsState()
    val libreFmSessionKey by streamingPrefs.libreFmSessionKey.collectAsState()
    val malojaServerUrl by streamingPrefs.malojaServerUrl.collectAsState()
    val malojaToken by streamingPrefs.malojaToken.collectAsState()
    val discordRpcEnabled by streamingPrefs.discordRpcEnabled.collectAsState()
    val discordRpcAppId by streamingPrefs.discordRpcApplicationId.collectAsState()
    val discordSupported = remember {
        chromahub.rhythm.app.infrastructure.network.presence.DiscordPresenceFactory.isSupported()
    }

    val musicViewModel: MusicViewModel = viewModel()
    val labsScope = rememberCoroutineScope()
    var rollbackRefreshCounter by remember { mutableIntStateOf(0) }
    val hasSafetyRollback = remember(rollbackRefreshCounter) {
        musicViewModel.hasSafetyRollbackSnapshot()
    }

    var showSampleRateSheet by remember { mutableStateOf(false) }
    var showListenBrainzSheet by remember { mutableStateOf(false) }
    var showLibreFmSheet by remember { mutableStateOf(false) }
    var showMalojaSheet by remember { mutableStateOf(false) }
    var showDiscordSheet by remember { mutableStateOf(false) }
    var showM3uSyncSheet by remember { mutableStateOf(false) }

    val m3uSyncFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            appSettings.setPlaylistSyncUri(treeUri.toString())
            chromahub.rhythm.app.infrastructure.worker.M3uSyncWorker.enqueue(context)
            Toast.makeText(context, "Playlist M3U8 sync enabled", Toast.LENGTH_SHORT).show()
        }
    }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_labs),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        val settingGroups = buildList {
            add(
                SettingGroup(
                    title = "Audio & Output",
                    items = listOf(
                        SettingItem(
                            icon = RhythmIcons.Devices.Usb,
                            title = context.getString(R.string.settings_audio_routing_mode),
                            description = when (audioRoutingMode) {
                                "app" -> context.getString(R.string.audio_routing_app)
                                "system" -> context.getString(R.string.audio_routing_system)
                                else -> context.getString(R.string.audio_routing_default)
                            },
                            onClick = { showAudioRoutingDialog = true },
                            settingKey = "audioRoutingMode"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("speed", filled = true),
                            title = "Sample Rate Limit",
                            description = when (sampleRateCapHz) {
                                48_000 -> "48 kHz"
                                96_000 -> "96 kHz"
                                192_000 -> "192 kHz"
                                else -> "Native"
                            },
                            onClick = { if (!isBitPerfect) showSampleRateSheet = true },
                            enabled = !isBitPerfect,
                            settingKey = "sample_rate_cap_hz"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("surround_sound", filled = true),
                            title = "Surround Downmix",
                            description = "Convert multichannel audio to stereo",
                            toggleState = surroundDownmixEnabled && !isBitPerfect,
                            onToggleChange = { playbackPrefs.setSurroundDownmixEnabled(it) },
                            enabled = !isBitPerfect,
                            settingKey = "surround_downmix_enabled"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("high_quality", filled = true),
                            title = "ALAC Software Decoder",
                            description = "Decode Apple Lossless tracks in software",
                            toggleState = alacDecoderEnabled,
                            onToggleChange = { playbackPrefs.setAlacDecoderEnabled(it) },
                            settingKey = "alac_decoder_enabled"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("perm_device_information", filled = true),
                            title = context.getString(R.string.settings_category_device_capabilities_title),
                            description = context.getString(R.string.settings_category_device_capabilities_desc),
                            onClick = { onNavigateTo(SettingsRoutes.NATIVE_CAPABILITIES) }
                        )
                    )
                )
            )

            add(
                SettingGroup(
                    title = "Sound Effects & Haptics",
                    items = listOf(
                        SettingItem(
                            icon = MaterialSymbolIcon("developer_board", filled = true),
                            title = context.getString(R.string.settings_viper_title),
                            description = context.getString(R.string.settings_viper_desc),
                            onClick = { onNavigateTo(SettingsRoutes.VIPER) }
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("vibration", filled = true),
                            title = context.getString(R.string.settings_rhythm_haptics),
                            description = when {
                                isBitPerfect -> context.getString(R.string.audio_routing_bit_perfect_disabled_effect)
                                musicHapticsEnabled -> "${context.getString(R.string.status_active)} • ${(musicHapticsIntensity * 100).toInt()}%"
                                else -> context.getString(R.string.settings_rhythm_haptics_desc)
                            },
                            toggleState = musicHapticsEnabled && !isBitPerfect,
                            onToggleChange = { playbackPrefs.setMusicHapticsEnabled(it) },
                            onClick = { if (!isBitPerfect) onNavigateTo(SettingsRoutes.RHYTHM_HAPTICS) },
                            enabled = !isBitPerfect,
                            settingKey = "music_haptics_enabled"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("graphic_eq", filled = true),
                            title = "Smart Transitions",
                            description = "Match tempo and blend smoothly between tracks",
                            toggleState = autoMixBpmSync,
                            onToggleChange = { playbackPrefs.setAutoMixBpmSyncEnabled(it) },
                            settingKey = "automix_bpm_sync_enabled"
                        )
                    )
                )
            )

            add(
                SettingGroup(
                    title = "Library",
                    items = listOf(
                        SettingItem(
                            icon = MaterialSymbolIcon("layers"),
                            title = context.getString(R.string.settings_unified_library),
                            description = context.getString(R.string.settings_unified_library_desc),
                            toggleState = unifiedLibraryEnabled,
                            onToggleChange = { appSettings.setUnifiedLibraryEnabled(it) },
                            settingKey = "unifiedLibraryEnabled"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("edit"),
                            title = context.getString(R.string.settings_enable_album_editing),
                            description = context.getString(R.string.settings_enable_album_editing_desc),
                            toggleState = enableAlbumEditing,
                            onToggleChange = { appSettings.setEnableAlbumEditing(it) },
                            settingKey = "enableAlbumEditing"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("cloud_queue"),
                            title = context.getString(R.string.exp_go_mode),
                            description = context.getString(R.string.exp_go_mode_desc),
                            toggleState = appMode == "STREAMING",
                            onToggleChange = { enabled ->
                                appSettings.setAppMode(if (enabled) "STREAMING" else "LOCAL")
                            },
                            onClick = { onNavigateToGoSettings?.invoke() }
                        )
                    )
                )
            )

            add(
                SettingGroup(
                    title = "Player & Display",
                    items = listOf(
                        SettingItem(
                            icon = MaterialSymbolIcon("water_drop"),
                            title = "Beat-Reactive Backdrop",
                            description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                "Animate player background with the music"
                            } else {
                                "Requires Android 13 or newer"
                            },
                            toggleState = agslFluidBackdropEnabled,
                            onToggleChange = { uiPrefs.setAgslFluidBackdropEnabled(it) },
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && playerAmbientBackdropEnabled,
                            settingKey = "agsl_fluid_backdrop_enabled"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("subtitles"),
                            title = "Status-Bar Lyrics",
                            description = "Show live lyrics in the status bar on supported devices",
                            toggleState = statusBarTickerEnabled,
                            onToggleChange = { uiPrefs.setStatusBarTickerEnabled(it) },
                            settingKey = "status_bar_ticker_enabled"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("smartphone"),
                            title = context.getString(R.string.exp_force_player_compact_mode),
                            description = context.getString(R.string.exp_force_player_compact_mode_desc),
                            toggleState = forcePlayerCompactMode,
                            onToggleChange = { appSettings.setForcePlayerCompactMode(it) }
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("queue_music"),
                            title = context.getString(R.string.external_player_isolate_preview),
                            description = context.getString(R.string.external_player_isolate_preview_desc),
                            toggleState = isolatePreview,
                            onToggleChange = { appSettings.setExternalPlayerIsolatePreview(it) },
                            settingKey = "external_player_isolate_preview"
                        )
                    )
                )
            )

            add(
                SettingGroup(
                    title = "Scrobbling & Presence",
                    items = listOf(
                        SettingItem(
                            icon = MaterialSymbolIcon("graphic_eq"),
                            title = "ListenBrainz",
                            description = if (!listenBrainzToken.isNullOrBlank()) "Connected" else "Tap to add user token",
                            onClick = { showListenBrainzSheet = true },
                            settingKey = "listenbrainz_scrobble"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("radio"),
                            title = "Libre.fm",
                            description = if (!libreFmSessionKey.isNullOrBlank()) "Connected" else "Tap to sign in",
                            onClick = { showLibreFmSheet = true },
                            settingKey = "librefm_scrobble"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("dns"),
                            title = "Maloja",
                            description = if (!malojaServerUrl.isNullOrBlank() && !malojaToken.isNullOrBlank()) "Connected to $malojaServerUrl" else "Tap to set server URL and key",
                            onClick = { showMalojaSheet = true },
                            settingKey = "maloja_scrobble"
                        )
                    ) + if (discordSupported) {
                        listOf(
                            SettingItem(
                                icon = MaterialSymbolIcon("sensors"),
                                title = "Discord Presence",
                                description = when {
                                    discordRpcEnabled && discordRpcAppId.isNotBlank() -> "Active"
                                    discordRpcEnabled -> "Tap to enter Discord App ID"
                                    else -> "Show active track on Discord"
                                },
                                toggleState = discordRpcEnabled,
                                onToggleChange = { checked -> streamingPrefs.setDiscordRpcConfig(checked, discordRpcAppId) },
                                onClick = { showDiscordSheet = true },
                                settingKey = "discord_rpc_enabled"
                            )
                        )
                    } else emptyList()
                )
            )

            add(
                SettingGroup(
                    title = "Backup & Diagnostics",
                    items = listOf(
                        SettingItem(
                            icon = MaterialSymbolIcon("sync"),
                            title = "Auto-Sync Playlists",
                            description = if (playlistSyncUri.isNullOrBlank()) {
                                "Keep playlist files synced with a folder"
                            } else {
                                "Syncing playlists to selected folder"
                            },
                            onClick = { showM3uSyncSheet = true },
                            settingKey = "m3u8_playlist_sync"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("history"),
                            title = "Undo Last Restore",
                            description = if (hasSafetyRollback) {
                                "Revert to state before the last restore"
                            } else {
                                "No previous backup snapshot saved"
                            },
                            onClick = {
                                if (hasSafetyRollback) {
                                    musicViewModel.restoreLastSafetyRollback()
                                    rollbackRefreshCounter++
                                    Toast.makeText(context, "Restored pre-restore safety snapshot", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = hasSafetyRollback,
                            settingKey = "backup_safety_rollback"
                        ),
                        SettingItem(
                            icon = MaterialSymbolIcon("monitor_heart", filled = true),
                            title = "App Freeze Monitor",
                            description = "Save crash logs when the UI freezes",
                            toggleState = stallMonitorEnabled,
                            onToggleChange = { enabled ->
                                appSettings.setMainThreadStallMonitorEnabled(enabled)
                                chromahub.rhythm.app.RhythmApplication.instance.setStallMonitorEnabled(enabled)
                            },
                            settingKey = "main_thread_stall_watchdog"
                        ),
                        SettingItem(
                            MaterialSymbolIcon("running_with_errors"),
                            context.getString(R.string.exp_track_error_checker),
                            context.getString(R.string.exp_track_error_checker_desc),
                            toggleState = appSettings.trackErrorCheckerEnabled.collectAsState().value,
                            onToggleChange = { appSettings.setTrackErrorCheckerEnabled(it) }
                        ),
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
                            RhythmIcons.BugReport,
                            context.getString(R.string.exp_test_crash),
                            context.getString(R.string.exp_test_crash_desc),
                            onClick = { chromahub.rhythm.app.util.CrashReporter.testCrash() }
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
                    toMaterial3SettingsItem(context = context, item = item, hapticFeedback = haptic)
                }

                Material3SettingsGroup(
                    title = group.title,
                    items = materialItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }




            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("lightbulb", filled = true),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = context.getString(R.string.updates_experimental_coming),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
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

    if (showAudioRoutingDialog) {
        AudioRoutingDialog(
            onDismiss = { showAudioRoutingDialog = false },
            appSettings = appSettings,
            context = context,
            haptic = haptic
        )
    }

    if (showSampleRateSheet) {
        SampleRateCapBottomSheet(
            currentSampleRateHz = sampleRateCapHz,
            onSelect = { hz ->
                playbackPrefs.setSampleRateCapHz(hz)
                showSampleRateSheet = false
            },
            onDismiss = { showSampleRateSheet = false },
            haptic = haptic
        )
    }

    if (showM3uSyncSheet) {
        M3uSyncOptionsBottomSheet(
            syncUri = playlistSyncUri,
            onSelectFolder = {
                showM3uSyncSheet = false
                m3uSyncFolderLauncher.launch(null)
            },
            onSyncNow = {
                chromahub.rhythm.app.infrastructure.worker.M3uSyncWorker.enqueue(context)
                Toast.makeText(context, "Playlist M3U8 sync scheduled", Toast.LENGTH_SHORT).show()
                showM3uSyncSheet = false
            },
            onDisable = {
                appSettings.setPlaylistSyncUri(null)
                Toast.makeText(context, "Playlist M3U8 sync disabled", Toast.LENGTH_SHORT).show()
                showM3uSyncSheet = false
            },
            onDismiss = { showM3uSyncSheet = false },
            haptic = haptic
        )
    }

    if (showListenBrainzSheet) {
        ListenBrainzLabsBottomSheet(
            currentToken = listenBrainzToken,
            onSave = { token ->
                streamingPrefs.setListenBrainzToken(token)
                showListenBrainzSheet = false
            },
            onDismiss = { showListenBrainzSheet = false }
        )
    }

    if (showLibreFmSheet) {
        LibreFmLabsBottomSheet(
            hasActiveSession = !libreFmSessionKey.isNullOrBlank(),
            onConnect = { username, password ->
                labsScope.launch {
                    val res = chromahub.rhythm.app.infrastructure.network.scrobble.MultiScrobblerManager
                        .getInstance(context)
                        .authenticateLibreFm(username, password)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (res.isSuccess) "Libre.fm connected" else (res.exceptionOrNull()?.message ?: "Sign-in failed"),
                            Toast.LENGTH_SHORT
                        ).show()
                        if (res.isSuccess) showLibreFmSheet = false
                    }
                }
            },
            onDisconnect = {
                streamingPrefs.setLibreFmSessionKey(null)
                showLibreFmSheet = false
            },
            onDismiss = { showLibreFmSheet = false }
        )
    }

    if (showMalojaSheet) {
        MalojaLabsBottomSheet(
            currentUrl = malojaServerUrl,
            currentToken = malojaToken,
            onSave = { url, token ->
                streamingPrefs.setMalojaConfig(url, token)
                showMalojaSheet = false
            },
            onDismiss = { showMalojaSheet = false }
        )
    }

    if (showDiscordSheet && discordSupported) {
        DiscordRpcLabsBottomSheet(
            enabled = discordRpcEnabled,
            currentAppId = discordRpcAppId,
            onSave = { enabled, appId ->
                streamingPrefs.setDiscordRpcConfig(enabled = enabled && appId.isNotBlank(), applicationId = appId)
                showDiscordSheet = false
            },
            onDismiss = { showDiscordSheet = false }
        )
    }
}

@Composable
private fun SampleRateCapBottomSheet(
    currentSampleRateHz: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    haptic: HapticFeedback
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val options = listOf(
        Triple(0, "Native", "Use device native sample rate"),
        Triple(48_000, "48 kHz", "Standard compatibility limit"),
        Triple(96_000, "96 kHz", "Hi-Res 96 kHz limit"),
        Triple(192_000, "192 kHz", "Studio 192 kHz limit")
    )

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = "Sample Rate Limit",
            subtitle = "Choose maximum output sample rate",
            visible = true
        )
        val scrollState = rememberScrollState()
        AdaptiveSheetScrollContainer(scrollState = scrollState, modifier = Modifier.fillMaxWidth()) { endPadding ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEachIndexed { index, (hz, title, subtitle) ->
                    val isSelected = currentSampleRateHz == hz
                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onSelect(hz)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = groupedBottomSheetItemShape(index, options.size),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("speed", filled = isSelected),
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = RhythmIcons.CheckCircle,
                                    contentDescription = context.getString(R.string.cd_selected),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun M3uSyncOptionsBottomSheet(
    syncUri: String?,
    onSelectFolder: () -> Unit,
    onSyncNow: () -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
    haptic: HapticFeedback
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val hasFolder = !syncUri.isNullOrBlank()
    val optionCount = if (hasFolder) 3 else 1

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = "Auto-Sync Playlists",
            subtitle = if (hasFolder) "Folder selected" else "Pick a folder for playlist files",
            visible = true
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Card(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                    onSelectFolder()
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = groupedBottomSheetItemShape(0, optionCount),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = RhythmIcons.FolderOpen, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (hasFolder) "Change Folder" else "Choose Folder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Pick a folder for .m3u8 playlist sync", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (hasFolder) {
                Card(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        onSyncNow()
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = groupedBottomSheetItemShape(1, optionCount),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = MaterialSymbolIcon("sync", filled = true), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sync Now", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Update playlist files right away", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f))
                        }
                    }
                }
                Card(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                        onDisable()
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = groupedBottomSheetItemShape(2, optionCount),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = MaterialSymbolIcon("link_off"), contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Turn Off Sync", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Stop syncing playlists to this folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.72f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabsStatusBannerCard(
    connected: Boolean,
    connectedText: String,
    disconnectedText: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (connected) RhythmIcons.CheckCircle else MaterialSymbolIcon("info"),
                contentDescription = null,
                tint = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = if (connected) connectedText else disconnectedText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LabsSheetInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: MaterialSymbolIcon,
    index: Int,
    totalCount: Int,
    isSecret: Boolean = false,
    digitsOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done
) {
    var revealed by remember { mutableStateOf(false) }
    val textState = rememberTextFieldState(value)
    LaunchedEffect(value) {
        if (textState.text.toString() != value) {
            textState.setTextAndPlaceCursorAtEnd(value)
        }
    }
    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }
            .distinctUntilChanged()
            .collect { current ->
                val filtered = if (digitsOnly) current.filter { it.isDigit() } else current
                if (digitsOnly && filtered != current) {
                    textState.setTextAndPlaceCursorAtEnd(filtered)
                }
                onValueChange(filtered)
            }
    }
    val decorator = TextFieldDecorator { innerTextField ->
        Box(modifier = Modifier.fillMaxWidth()) {
            if (textState.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
            innerTextField()
        }
    }

    Card(
        shape = groupedBottomSheetItemShape(index, totalCount),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (isSecret) {
                    BasicSecureTextField(
                        state = textState,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                        textObfuscationMode = if (revealed) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                        modifier = Modifier.fillMaxWidth(),
                        decorator = decorator
                    )
                } else {
                    BasicTextField(
                        state = textState,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                        modifier = Modifier.fillMaxWidth(),
                        decorator = decorator
                    )
                }
            }
            if (isSecret && textState.text.isNotEmpty()) {
                IconButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon(if (revealed) "visibility_off" else "visibility"),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (textState.text.isNotEmpty()) {
                IconButton(
                    onClick = { textState.clearText() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenBrainzLabsBottomSheet(
    currentToken: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var tokenInput by remember(currentToken) { mutableStateOf(currentToken.orEmpty()) }
    LaunchedEffect(Unit) { sheetState.expand() }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(title = "ListenBrainz", subtitle = "Scrobble plays using your user token", visible = true)
        val scrollState = rememberScrollState()
        AdaptiveSheetScrollContainer(scrollState = scrollState, modifier = Modifier.fillMaxWidth()) { endPadding ->
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LabsStatusBannerCard(
                    connected = !currentToken.isNullOrBlank(),
                    connectedText = "Connected and scrobbling plays",
                    disconnectedText = "Paste your user token from your ListenBrainz profile"
                )
                LabsSheetInputCard(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = "User Token",
                    placeholder = "Enter ListenBrainz user token",
                    icon = MaterialSymbolIcon("key"),
                    index = 0,
                    totalCount = 1,
                    isSecret = true
                )
                RhythmGroupedButton(modifier = Modifier.fillMaxWidth(), size = RhythmButtonSize.Large) {
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onDismiss()
                        },
                        type = RhythmButtonType.Tonal,
                        size = RhythmButtonSize.Large,
                        text = stringResource(R.string.ui_cancel),
                        isFirst = true,
                        isLast = false
                    )
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM)
                            onSave(tokenInput.trim())
                        },
                        type = RhythmButtonType.Filled,
                        size = RhythmButtonSize.Large,
                        text = if (tokenInput.isBlank() && !currentToken.isNullOrBlank()) "Disconnect" else stringResource(R.string.ui_save),
                        icon = MaterialSymbolIcon("check"),
                        isFirst = false,
                        isLast = true
                    )
                }
            }
        }
    }
}

@Composable
private fun LibreFmLabsBottomSheet(
    hasActiveSession: Boolean,
    onConnect: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var userInput by remember { mutableStateOf("") }
    var passInput by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { sheetState.expand() }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = "Libre.fm",
            subtitle = if (hasActiveSession) "Account connected" else "Sign in to scrobble your plays",
            visible = true
        )
        val scrollState = rememberScrollState()
        AdaptiveSheetScrollContainer(scrollState = scrollState, modifier = Modifier.fillMaxWidth()) { endPadding ->
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LabsStatusBannerCard(
                    connected = hasActiveSession,
                    connectedText = "Connected and scrobbling plays",
                    disconnectedText = "Sign in to connect your Libre.fm account"
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabsSheetInputCard(
                        value = userInput,
                        onValueChange = { userInput = it },
                        label = "Username",
                        placeholder = "Enter Libre.fm username",
                        icon = MaterialSymbolIcon("person"),
                        index = 0,
                        totalCount = 2,
                        imeAction = ImeAction.Next
                    )
                    LabsSheetInputCard(
                        value = passInput,
                        onValueChange = { passInput = it },
                        label = "Password",
                        placeholder = "Enter password",
                        icon = MaterialSymbolIcon("lock"),
                        index = 1,
                        totalCount = 2,
                        isSecret = true,
                        keyboardType = KeyboardType.Password
                    )
                }
                RhythmGroupedButton(modifier = Modifier.fillMaxWidth(), size = RhythmButtonSize.Large) {
                    if (hasActiveSession) {
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM)
                                onDisconnect()
                            },
                            type = RhythmButtonType.Tonal,
                            size = RhythmButtonSize.Large,
                            text = "Disconnect",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            isFirst = true,
                            isLast = false
                        )
                    } else {
                        RhythmButtonWeighted(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                onDismiss()
                            },
                            type = RhythmButtonType.Tonal,
                            size = RhythmButtonSize.Large,
                            text = stringResource(R.string.ui_cancel),
                            isFirst = true,
                            isLast = false
                        )
                    }
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM)
                            onConnect(userInput.trim(), passInput)
                        },
                        type = RhythmButtonType.Filled,
                        size = RhythmButtonSize.Large,
                        text = "Connect",
                        icon = MaterialSymbolIcon("login"),
                        enabled = userInput.isNotBlank() && passInput.isNotBlank(),
                        isFirst = false,
                        isLast = true
                    )
                }
            }
        }
    }
}

@Composable
private fun MalojaLabsBottomSheet(
    currentUrl: String?,
    currentToken: String?,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl.orEmpty()) }
    var keyInput by remember(currentToken) { mutableStateOf(currentToken.orEmpty()) }
    LaunchedEffect(Unit) { sheetState.expand() }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(title = "Maloja", subtitle = "Connect your self-hosted scrobble server", visible = true)
        val scrollState = rememberScrollState()
        AdaptiveSheetScrollContainer(scrollState = scrollState, modifier = Modifier.fillMaxWidth()) { endPadding ->
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val isConnected = !currentUrl.isNullOrBlank() && !currentToken.isNullOrBlank()
                LabsStatusBannerCard(
                    connected = isConnected,
                    connectedText = "Connected to $currentUrl",
                    disconnectedText = "Enter your server URL and API key"
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabsSheetInputCard(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = "Server URL",
                        placeholder = "https://maloja.local",
                        icon = MaterialSymbolIcon("dns"),
                        index = 0,
                        totalCount = 2,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    )
                    LabsSheetInputCard(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = "API Key",
                        placeholder = "Enter Maloja API token",
                        icon = MaterialSymbolIcon("key"),
                        index = 1,
                        totalCount = 2,
                        isSecret = true
                    )
                }
                RhythmGroupedButton(modifier = Modifier.fillMaxWidth(), size = RhythmButtonSize.Large) {
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onDismiss()
                        },
                        type = RhythmButtonType.Tonal,
                        size = RhythmButtonSize.Large,
                        text = stringResource(R.string.ui_cancel),
                        isFirst = true,
                        isLast = false
                    )
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM)
                            onSave(urlInput.trim(), keyInput.trim())
                        },
                        type = RhythmButtonType.Filled,
                        size = RhythmButtonSize.Large,
                        text = if (urlInput.isBlank() && keyInput.isBlank() && isConnected) "Clear" else stringResource(R.string.ui_save),
                        icon = MaterialSymbolIcon("check"),
                        isFirst = false,
                        isLast = true
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscordRpcLabsBottomSheet(
    enabled: Boolean,
    currentAppId: String,
    onSave: (Boolean, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var appIdInput by remember(currentAppId) { mutableStateOf(currentAppId) }
    var localEnabled by remember(enabled) { mutableStateOf(enabled) }
    LaunchedEffect(Unit) { sheetState.expand() }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(title = "Discord Presence", subtitle = "Show what you're playing on Discord", visible = true)
        val scrollState = rememberScrollState()
        AdaptiveSheetScrollContainer(scrollState = scrollState, modifier = Modifier.fillMaxWidth()) { endPadding ->
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LabsStatusBannerCard(
                    connected = enabled && currentAppId.isNotBlank(),
                    connectedText = "Discord Presence active (App ID $currentAppId)",
                    disconnectedText = "Requires Discord installed with a numeric App ID"
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            localEnabled = !localEnabled
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = groupedBottomSheetItemShape(0, 2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MaterialSymbolIcon("sensors", filled = localEnabled),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Show Playing Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Share active song with Discord", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TunerAnimatedSwitch(checked = localEnabled, onCheckedChange = { localEnabled = it })
                        }
                    }
                    LabsSheetInputCard(
                        value = appIdInput,
                        onValueChange = { appIdInput = it },
                        label = "Discord Application ID",
                        placeholder = "Numeric App ID",
                        icon = MaterialSymbolIcon("pin"),
                        index = 1,
                        totalCount = 2,
                        digitsOnly = true,
                        keyboardType = KeyboardType.Number
                    )
                }
                RhythmGroupedButton(modifier = Modifier.fillMaxWidth(), size = RhythmButtonSize.Large) {
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onDismiss()
                        },
                        type = RhythmButtonType.Tonal,
                        size = RhythmButtonSize.Large,
                        text = stringResource(R.string.ui_cancel),
                        isFirst = true,
                        isLast = false
                    )
                    RhythmButtonWeighted(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.MEDIUM)
                            onSave(localEnabled, appIdInput)
                        },
                        type = RhythmButtonType.Filled,
                        size = RhythmButtonSize.Large,
                        text = stringResource(R.string.ui_save),
                        icon = MaterialSymbolIcon("check"),
                        isFirst = false,
                        isLast = true
                    )
                }
            }
        }
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