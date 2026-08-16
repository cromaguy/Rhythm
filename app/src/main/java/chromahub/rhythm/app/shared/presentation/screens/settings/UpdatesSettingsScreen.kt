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
import androidx.lifecycle.ViewModelStoreOwner
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
import chromahub.rhythm.app.shared.presentation.viewmodel.rememberAppUpdaterViewModel
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
import chromahub.rhythm.app.shared.presentation.components.dialogs.FdroidUpdateWarningDialog
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
fun UpdatesSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val updaterViewModel: AppUpdaterViewModel = rememberAppUpdaterViewModel()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Collect state from ViewModel and AppSettings
    val updatesEnabled by appSettings.updatesEnabled.collectAsState()
    val autoCheckForUpdates by appSettings.autoCheckForUpdates.collectAsState()
    val useSmartUpdatePolling by appSettings.useSmartUpdatePolling.collectAsState()
    val updateChannel by appSettings.updateChannel.collectAsState()
    val updateSource by appSettings.updateSource.collectAsState()
    val updateCheckIntervalHours by appSettings.updateCheckIntervalHours.collectAsState()
    val currentVersion by updaterViewModel.currentVersion.collectAsState()
    val latestVersion by updaterViewModel.latestVersion.collectAsState()
    val isCheckingForUpdates by updaterViewModel.isCheckingForUpdates.collectAsState()
    val updateAvailable by updaterViewModel.updateAvailable.collectAsState()
    val error by updaterViewModel.error.collectAsState()
    val isDownloading by updaterViewModel.isDownloading.collectAsState()
    val downloadProgress by updaterViewModel.downloadProgress.collectAsState()
    val downloadedFile by updaterViewModel.downloadedFile.collectAsState()
    val isExtracting by updaterViewModel.isExtracting.collectAsState()
    val canProceedWithMismatchedDownload by updaterViewModel.canProceedWithMismatchedDownload.collectAsState()

    // Simulation state variables
    var simulateEnabled by remember { mutableStateOf(false) }
    var simIsChecking by remember { mutableStateOf(false) }
    var simUpdateAvailable by remember { mutableStateOf(false) }
    var simIsDownloading by remember { mutableStateOf(false) }
    var simDownloadProgress by remember { mutableStateOf(0f) }
    var simDownloadedFile by remember { mutableStateOf<File?>(null) }
    var simError by remember { mutableStateOf<String?>(null) }

    val simLatestVersion = remember {
        AppVersion(
            versionName = "3.2.0-beta",
            versionCode = 320,
            releaseDate = "2026-05-21",
            whatsNew = listOf(
                "Added a brand new <b>UI Test Sandbox</b> for easy developer updates debugging!",
                "Stunning onboarding-style wavy progress animations for app status updates.",
                "Premium glassmorphic card layouts and dynamic gradient highlights.",
                "Smooth micro-animations and improved layout responsiveness across all screen sizes."
            ),
            knownIssues = listOf(
                "Simulated sandbox mode overrides actual remote check updates."
            ),
            downloadUrl = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases",
            apkAssetName = "rhythm-v3.2.0-beta.apk",
            apkSize = 18454937, // ~17.6 MB
            releaseNotes = "Simulated update notes",
            isPreRelease = true,
            buildNumber = 3200
        )
    }

    val activeIsCheckingForUpdates = if (simulateEnabled) simIsChecking else isCheckingForUpdates
    val activeUpdateAvailable = if (simulateEnabled) simUpdateAvailable else updateAvailable
    val activeError = if (simulateEnabled) simError else error
    val activeIsDownloading = if (simulateEnabled) simIsDownloading else isDownloading
    val activeDownloadProgress = if (simulateEnabled) simDownloadProgress else downloadProgress
    val activeDownloadedFile = if (simulateEnabled) simDownloadedFile else downloadedFile
    val activeIsExtracting = if (simulateEnabled) false else isExtracting
    val activeCanProceedWithMismatchedDownload = !simulateEnabled && canProceedWithMismatchedDownload
    val activeLatestVersion = if (simulateEnabled) {
        if (simUpdateAvailable) simLatestVersion else null
    } else {
        latestVersion
    }
    val activeWhatsNew = activeLatestVersion?.whatsNew ?: emptyList()
    val activeKnownIssues = activeLatestVersion?.knownIssues ?: emptyList()

    // Dialog states
    var showChannelDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showFdroidWarningDialog by remember { mutableStateOf(false) }

    val intervalOptions = listOf(
        1 to context.getString(R.string.settings_interval_every_hour),
        6 to context.getString(R.string.settings_interval_every_6_hours),
        12 to context.getString(R.string.settings_interval_every_12_hours),
        24 to context.getString(R.string.settings_interval_once_a_day),
        168 to context.getString(R.string.settings_interval_once_a_week)
    )
    val updateIntervalLabel = intervalOptions.firstOrNull { (hours, _) ->
        hours == updateCheckIntervalHours
    }?.second ?: context.getString(R.string.settings_check_interval_value, updateCheckIntervalHours)

    // Check for updates when the screen is first shown and updates are enabled
    LaunchedEffect(updatesEnabled) {
        if (updatesEnabled) {
            updaterViewModel.checkForUpdates(force = true)
        }
    }

    // Infinite transition for continuous animations
    val infiniteTransition = rememberInfiniteTransition(label = "update_animations")

    // Rotating icon for downloading state
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Success scale animation
    val successScale = remember { Animatable(0.7f) }
    LaunchedEffect(activeDownloadedFile) {
        if (activeDownloadedFile != null) {
            successScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Determine status components
    val statusIcon = when {
        activeError != null -> RhythmIcons.BugReport
        activeDownloadedFile != null -> RhythmIcons.CheckCircle
        activeIsDownloading -> MaterialSymbolIcon("autorenew", filled = true)
        activeUpdateAvailable -> RhythmIcons.Download
        !updatesEnabled -> MaterialSymbolIcon("update_disabled", filled = true)
        else -> RhythmIcons.SystemUpdate
    }

    val statusColor = when {
        activeError != null -> MaterialTheme.colorScheme.error
        activeDownloadedFile != null -> MaterialTheme.colorScheme.tertiary
        activeIsDownloading -> MaterialTheme.colorScheme.secondary
        activeUpdateAvailable -> MaterialTheme.colorScheme.primary
        !updatesEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.primary
    }

    val statusTitle = when {
        activeError != null -> context.getString(R.string.updates_check_failed)
        !updatesEnabled -> context.getString(R.string.updates_disabled)
        activeIsCheckingForUpdates -> context.getString(R.string.updates_checking)
        activeIsExtracting -> "Extracting Update"
        activeIsDownloading -> context.getString(R.string.updates_downloading)
        activeDownloadedFile != null -> context.getString(R.string.updates_download_complete)
        activeUpdateAvailable -> context.getString(R.string.updates_available)
        !autoCheckForUpdates -> context.getString(R.string.updates_manual_check)
        else -> context.getString(R.string.updates_up_to_date_message)
    }

    val statusDescription = when {
        activeError != null -> activeError
        !updatesEnabled -> context.getString(R.string.updates_disabled_message)
        activeIsCheckingForUpdates -> context.getString(R.string.fetching_latest_version)
        activeIsExtracting -> "Unzipping update file, please wait..."
        activeIsDownloading -> "${((activeLatestVersion?.apkSize ?: 0) * activeDownloadProgress / 100).toLong().let { updaterViewModel.getReadableFileSize(it) }} / ${activeLatestVersion?.let { updaterViewModel.getReadableFileSize(it.apkSize) } ?: ""}"
        activeDownloadedFile != null -> "Version ${activeLatestVersion?.versionName ?: "?"} is ready to install"
        activeUpdateAvailable -> "Version ${activeLatestVersion?.versionName ?: "?"} • ${activeLatestVersion?.let { updaterViewModel.getReadableFileSize(it.apkSize) } ?: ""}"
        !autoCheckForUpdates -> context.getString(R.string.updates_auto_disabled)
        else -> "Rhythm is up to date with the latest features and security updates"
    }

    val headerBlendHeight = 24.dp
    val headerBlendBaseColor = MaterialTheme.colorScheme.surface

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
            ) {
                // Static, non-collapsible Top Bar
                androidx.compose.material3.TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = RhythmIcons.Back,
                                    contentDescription = stringResource(R.string.cd_back),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(25.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerBlendHeight)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    headerBlendBaseColor,
                                    headerBlendBaseColor.copy(alpha = 0.72f),
                                    headerBlendBaseColor.copy(alpha = 0.32f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    ) { paddingValues ->
        val lazyListState = rememberSaveable(
            saver = LazyListStateSaver
        ) {
            androidx.compose.foundation.lazy.LazyListState()
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .offset(y = -headerBlendHeight)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Update Status display (No card bg, onboarding-like placements and wavy loaders)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Centered Icon with no container background (Onboarding style)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeIsDownloading || activeIsExtracting) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(72.dp),
                                color = statusColor
                            )
                        } else {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }

                    // Status Title
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )

                    // Linear Wavy Progress Bar between Title and Description (Onboarding style)
                    if (activeIsCheckingForUpdates) {
                        LinearWavyProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                        )
                    }

                    if (activeIsDownloading || activeIsExtracting) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (activeIsDownloading) {
                                        Text(
                                            text = "${activeDownloadProgress.toInt()}%",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Text(
                                        text = if (activeIsExtracting) "Unzipping..." else context.getString(R.string.onboarding_in_progress),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            if (activeIsDownloading) {
                                LinearWavyProgressIndicator(
                                    progress = { activeDownloadProgress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                )
                            } else {
                                LinearWavyProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }

                    // Status Description
                    Text(
                        text = statusDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (activeError != null) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )

                    // Action buttons (Install, Download, Cancel, Retry, Enable Updates, Check Again)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when {
                            activeError != null -> {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (activeCanProceedWithMismatchedDownload) {
                                                updaterViewModel.resetDownloadState()
                                            } else if (simulateEnabled) {
                                                simError = null
                                            } else {
                                                updaterViewModel.clearError()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (activeCanProceedWithMismatchedDownload)
                                                context.getString(R.string.ui_reset)
                                            else
                                                context.getString(R.string.ui_dismiss),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (activeCanProceedWithMismatchedDownload) {
                                                updaterViewModel.proceedWithMismatchedDownload()
                                            } else if (simulateEnabled) {
                                                simError = null
                                                scope.launch {
                                                    simIsChecking = true
                                                    delay(1500)
                                                    simIsChecking = false
                                                    simUpdateAvailable = true
                                                }
                                            } else {
                                                if (error?.contains("unknown sources", ignoreCase = true) == true ||
                                                    error?.contains("install from unknown", ignoreCase = true) == true) {
                                                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                    }
                                                    try {
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        val fallbackIntent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                                                        context.startActivity(fallbackIntent)
                                                    }
                                                } else {
                                                    updaterViewModel.checkForUpdates(force = true)
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (activeCanProceedWithMismatchedDownload)
                                                MaterialTheme.colorScheme.tertiary
                                            else
                                                MaterialTheme.colorScheme.error
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(
                                            defaultElevation = 4.dp,
                                            pressedElevation = 8.dp
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (activeCanProceedWithMismatchedDownload)
                                                RhythmIcons.CheckCircle
                                            else if (!simulateEnabled && (error?.contains("unknown sources", ignoreCase = true) == true ||
                                                              error?.contains("install from unknown", ignoreCase = true) == true))
                                                RhythmIcons.SettingsFilled else RhythmIcons.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (activeCanProceedWithMismatchedDownload)
                                                context.getString(R.string.bottomsheet_proceed)
                                            else if (!simulateEnabled && (error?.contains("unknown sources", ignoreCase = true) == true ||
                                                     error?.contains("install from unknown", ignoreCase = true) == true))
                                                context.getString(R.string.updates_open_settings)
                                            else
                                                context.getString(R.string.updates_retry),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                }
                            }

                            !updatesEnabled -> {
                                Button(
                                    onClick = {
                                        if (BuildConfig.FLAVOR == "fdroid") {
                                            showFdroidWarningDialog = true
                                        } else {
                                            appSettings.setUpdatesEnabled(true)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = context.getString(R.string.updates_enable_updates),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }

                            activeIsDownloading -> {
                                OutlinedButton(
                                    onClick = {
                                        if (simulateEnabled) {
                                            simIsDownloading = false
                                            simDownloadProgress = 0f
                                            simUpdateAvailable = true
                                        } else {
                                            updaterViewModel.cancelDownload()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Block,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = context.getString(R.string.updates_cancel_download),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }

                            activeDownloadedFile != null -> {
                                Button(
                                    onClick = {
                                        if (simulateEnabled) {
                                            Toast.makeText(context, R.string.updatessettingsscreen_simulating_update_installation_success, Toast.LENGTH_SHORT).show()
                                            simDownloadedFile = null
                                            simUpdateAvailable = false
                                        } else {
                                            updaterViewModel.installDownloadedApk()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .scale(successScale.value),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    shape = RoundedCornerShape(20.dp),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 6.dp,
                                        pressedElevation = 12.dp
                                    )
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = context.getString(R.string.updates_install_update),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }

                            activeUpdateAvailable -> {
                                Button(
                                    onClick = {
                                        if (simulateEnabled) {
                                            scope.launch {
                                                simIsDownloading = true
                                                simDownloadProgress = 0f
                                                while (simDownloadProgress < 100f && simIsDownloading) {
                                                    delay(100)
                                                    simDownloadProgress += 4f
                                                }
                                                if (simIsDownloading) {
                                                    simIsDownloading = false
                                                    simDownloadedFile = File(context.cacheDir, "simulated_update.apk")
                                                }
                                            }
                                        } else {
                                            updaterViewModel.downloadUpdate()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 6.dp,
                                        pressedElevation = 12.dp
                                    )
                                ) {
                                    Icon(
                                        imageVector = RhythmIcons.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = context.getString(R.string.updates_download_update),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }

                            !activeIsCheckingForUpdates -> {
                                if (!autoCheckForUpdates) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (simulateEnabled) {
                                                    scope.launch {
                                                        simIsChecking = true
                                                        simError = null
                                                        delay(1500)
                                                        simIsChecking = false
                                                        simUpdateAvailable = true
                                                    }
                                                } else {
                                                    updaterViewModel.checkForUpdates(force = true)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = RhythmIcons.SearchFilled,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.updates_check_now_action),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { appSettings.setAutoCheckForUpdates(true) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                        ) {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("autorenew", filled = true),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = context.getString(R.string.updates_enable_auto_check),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (simulateEnabled) {
                                                scope.launch {
                                                    simIsChecking = true
                                                    simError = null
                                                    delay(1500)
                                                    simIsChecking = false
                                                    simUpdateAvailable = true
                                                }
                                            } else {
                                                updaterViewModel.checkForUpdates(force = true)
                                            }
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                                    ) {
                                        Icon(
                                            imageVector = RhythmIcons.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = context.getString(R.string.updates_check_again),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Build Info display
            item {
                val isUpdateAvail = activeUpdateAvailable && activeLatestVersion != null
                val displayVersionName = if (isUpdateAvail) activeLatestVersion.versionName else currentVersion.versionName
                val displayReleaseDate = if (isUpdateAvail) activeLatestVersion.releaseDate else currentVersion.releaseDate

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                            val tag = currentVersion.versionName
                            val releaseUrl = if (tag.startsWith("v", ignoreCase = true)) {
                                "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/tag/$displayVersionName"
                            } else {
                                "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/tag/v$displayVersionName"
                            }
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, R.string.updatessettingsscreen_unable_to_open_release, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (displayVersionName.length > 20) {
                            if (isUpdateAvail) "Available Version:\n$displayVersionName" else "Version:\n$displayVersionName"
                        } else {
                            if (isUpdateAvail) "Available Version: V $displayVersionName" else context.getString(R.string.updates_version_prefix, displayVersionName)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isUpdateAvail) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isUpdateAvail) "Released: $displayReleaseDate" else context.getString(R.string.updates_released_prefix, displayReleaseDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. What's New section
            item {
                AnimatedVisibility(
                    visible = updatesEnabled && activeUpdateAvailable && activeLatestVersion != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = context.getString(R.string.updates_whats_new),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp)
                            ) {
                                val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
                                if (activeWhatsNew.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.updatessettingsscreen_refer_to_release_notes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    activeWhatsNew.forEachIndexed { index, change ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .padding(top = 8.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primary,
                                                        CircleShape
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            AndroidView(
                                                modifier = Modifier.fillMaxWidth(),
                                                factory = { ctx ->
                                                    TextView(ctx).apply {
                                                        setTextColor(onSurfaceColor)
                                                    }
                                                },
                                                update = { textView ->
                                                    textView.text = HtmlCompat.fromHtml(change, HtmlCompat.FROM_HTML_MODE_COMPACT)
                                                    textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                                                }
                                            )
                                        }
                                        if (index < activeWhatsNew.size - 1) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Known Issues section
            item {
                AnimatedVisibility(
                    visible = updatesEnabled && activeUpdateAvailable && activeLatestVersion != null && activeKnownIssues.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = context.getString(R.string.updates_known_issues),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp)
                            ) {
                                val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
                                activeKnownIssues.forEachIndexed { index, issue ->
                                    Row(
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .padding(top = 8.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.error,
                                                    CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        AndroidView(
                                            modifier = Modifier.fillMaxWidth(),
                                            factory = { ctx ->
                                                TextView(ctx).apply {
                                                    setTextColor(onSurfaceColor)
                                                }
                                            },
                                            update = { textView ->
                                                textView.text = HtmlCompat.fromHtml(issue, HtmlCompat.FROM_HTML_MODE_COMPACT)
                                                textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                                            }
                                        )
                                    }
                                    if (index < activeKnownIssues.size - 1) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Settings Section below
            item {
                Spacer(modifier = Modifier.height(24.dp))
                val updateSettingsItems = buildList {
                    add(
                        SettingItem(
                            RhythmIcons.SystemUpdate,
                            context.getString(R.string.updates_enable),
                            context.getString(R.string.updates_enable_updates),
                            toggleState = updatesEnabled,
                            onToggleChange = { enabled ->
                                if (enabled) {
                                    if (BuildConfig.FLAVOR == "fdroid") {
                                        showFdroidWarningDialog = true
                                    } else {
                                        appSettings.setUpdatesEnabled(true)
                                    }
                                } else {
                                    appSettings.setUpdatesEnabled(false)
                                }
                            }
                        )
                    )
                    if (updatesEnabled) {
                        add(
                            SettingItem(
                                RhythmIcons.Update,
                                context.getString(R.string.onboarding_periodic_check_title),
                                context.getString(R.string.onboarding_periodic_check_desc),
                                toggleState = autoCheckForUpdates,
                                onToggleChange = { appSettings.setAutoCheckForUpdates(it) }
                            )
                        )
                        add(
                            SettingItem(
                                MaterialSymbolIcon("cloud_sync"),
                                context.getString(R.string.onboarding_smart_polling_title),
                                context.getString(R.string.onboarding_smart_polling_desc),
                                toggleState = useSmartUpdatePolling,
                                onToggleChange = { appSettings.setUseSmartUpdatePolling(it) }
                            )
                        )
                        add(
                            SettingItem(
                                RhythmIcons.Category,
                                context.getString(R.string.updates_channel_title),
                                "${updateChannel.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} - Tap to change",
                                onClick = { showChannelDialog = true }
                            )
                        )
                        add(
                            SettingItem(
                                RhythmIcons.Category,
                                context.getString(R.string.updates_source_title),
                                getUpdateSourceLabel(context, updateSource),
                                onClick = { showSourceDialog = true }
                            )
                        )
                        add(
                            SettingItem(
                                RhythmIcons.AccessTime,
                                context.getString(R.string.updates_check_interval_title),
                                updateIntervalLabel,
                                onClick = { showIntervalDialog = true }
                            )
                        )
                    }
                }

                Material3SettingsGroup(
                    title = context.getString(R.string.updates_settings),
                    items = updateSettingsItems.map { item ->
                        toMaterial3SettingsItem(context = context, item = item, hapticFeedback = haptics)
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // 6. Informational card about smart polling
            item {
                AnimatedVisibility(
                    visible = updatesEnabled && useSmartUpdatePolling,
                    enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = context.getString(R.string.updates_smart_polling),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = context.getString(R.string.updates_smart_polling_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // 7. Developer UI Test Sandbox Card
//            item {
//                Card(
//                    modifier = Modifier.fillMaxWidth(),
//                    shape = RoundedCornerShape(18.dp),
//                    colors = CardDefaults.cardColors(
//                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
//                    ),
//                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
//                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
//                ) {
//                    Column(
//                        modifier = Modifier.padding(16.dp),
//                        verticalArrangement = Arrangement.spacedBy(12.dp)
//                    ) {
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceBetween,
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            Row(
//                                verticalAlignment = Alignment.CenterVertically,
//                                horizontalArrangement = Arrangement.spacedBy(8.dp)
//                            ) {
//                                Icon(
//                                    imageVector = MaterialSymbolIcon("science"),
//                                    contentDescription = null,
//                                    tint = MaterialTheme.colorScheme.secondary,
//                                    modifier = Modifier.size(24.dp)
//                                )
//                                Text(
//                                    text = "UI Test Sandbox",
//                                    style = MaterialTheme.typography.titleMedium,
//                                    fontWeight = FontWeight.Bold,
//                                    color = MaterialTheme.colorScheme.onSecondaryContainer
//                                )
//                            }
//                            TunerAnimatedSwitch(
//                                checked = simulateEnabled,
//                                onCheckedChange = {
//                                    HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
//                                    simulateEnabled = it
//                                    if (!it) {
//                                        // Reset simulated states when disabled
//                                        simIsChecking = false
//                                        simUpdateAvailable = false
//                                        simIsDownloading = false
//                                        simDownloadProgress = 0f
//                                        simDownloadedFile = null
//                                        simError = null
//                                    }
//                                }
//                            )
//                        }
//
//                        Text(
//                            text = "Enable to simulate different update states and progress bars for UI testing.",
//                            style = MaterialTheme.typography.bodySmall,
//                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
//                        )
//
//                        AnimatedVisibility(
//                            visible = simulateEnabled,
//                            enter = fadeIn() + expandVertically(),
//                            exit = fadeOut() + shrinkVertically()
//                        ) {
//                            Column(
//                                verticalArrangement = Arrangement.spacedBy(8.dp)
//                            ) {
//                                HorizontalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
//
//                                Text(
//                                    text = "Simulate State:",
//                                    style = MaterialTheme.typography.titleSmall,
//                                    fontWeight = FontWeight.SemiBold,
//                                    color = MaterialTheme.colorScheme.onSecondaryContainer
//                                )
//
//                                // Row 1 of presets: Checking & Update Available
//                                Row(
//                                    modifier = Modifier.fillMaxWidth(),
//                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                                ) {
//                                    val isCheckingSelected = simIsChecking && !simUpdateAvailable && !simIsDownloading && simDownloadedFile == null && simError == null
//                                    if (isCheckingSelected) {
//                                        Button(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = true
//                                                simUpdateAvailable = false
//                                                simIsDownloading = false
//                                                simDownloadedFile = null
//                                                simError = null
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Checking", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
//                                        }
//                                    } else {
//                                        OutlinedButton(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = true
//                                                simUpdateAvailable = false
//                                                simIsDownloading = false
//                                                simDownloadedFile = null
//                                                simError = null
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Checking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
//                                        }
//                                    }
//
//                                    val isAvailableSelected = !simIsChecking && simUpdateAvailable && !simIsDownloading && simDownloadedFile == null && simError == null
//                                    if (isAvailableSelected) {
//                                        Button(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = false
//                                                simUpdateAvailable = true
//                                                simIsDownloading = false
//                                                simDownloadedFile = null
//                                                simError = null
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Update Available", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
//                                        }
//                                    } else {
//                                        OutlinedButton(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = false
//                                                simUpdateAvailable = true
//                                                simIsDownloading = false
//                                                simDownloadedFile = null
//                                                simError = null
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Update Available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
//                                        }
//                                    }
//                                }
//
//                                // Row 2 of presets: Downloading & Downloaded
//                                Row(
//                                    modifier = Modifier.fillMaxWidth(),
//                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                                ) {
//                                    val isDownloadingSelected = simIsDownloading
//                                    if (isDownloadingSelected) {
//                                        Button(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = false
//                                                simUpdateAvailable = true
//                                                simIsDownloading = true
//                                                simDownloadedFile = null
//                                                simError = null
//
//                                                // Start simulating a progress cycle
//                                                scope.launch {
//                                                    simDownloadProgress = 0f
//                                                    while (simDownloadProgress < 100f && simIsDownloading) {
//                                                        delay(100)
//                                                        simDownloadProgress += 4f
//                                                    }
//                                                    if (simIsDownloading) {
//                                                        simIsDownloading = false
//                                                        simDownloadedFile = File(context.cacheDir, "simulated_update.apk")
//                                                    }
//                                                }
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Downloading", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
//                                        }
//                                    } else {
//                                        OutlinedButton(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = false
//                                                simUpdateAvailable = true
//                                                simIsDownloading = true
//                                                simDownloadedFile = null
//                                                simError = null
//
//                                                // Start simulating a progress cycle
//                                                scope.launch {
//                                                    simDownloadProgress = 0f
//                                                    while (simDownloadProgress < 100f && simIsDownloading) {
//                                                        delay(100)
//                                                        simDownloadProgress += 4f
//                                                    }
//                                                    if (simIsDownloading) {
//                                                        simIsDownloading = false
//                                                        simDownloadedFile = File(context.cacheDir, "simulated_update.apk")
//                                                    }
//                                                }
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Downloading", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
//                                        }
//                                    }
//
//                                    val isDownloadedSelected = simDownloadedFile != null && !simIsDownloading
//                                    if (isDownloadedSelected) {
//                                        Button(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = false
//                                                simUpdateAvailable = true
//                                                simIsDownloading = false
//                                                simDownloadedFile = File(context.cacheDir, "simulated_update.apk")
//                                                simError = null
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Downloaded", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
//                                        }
//                                    } else {
//                                        OutlinedButton(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = false
//                                                simUpdateAvailable = true
//                                                simIsDownloading = false
//                                                simDownloadedFile = File(context.cacheDir, "simulated_update.apk")
//                                                simError = null
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Downloaded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
//                                        }
//                                    }
//                                }
//
//                                // Row 3 of presets: Error & Reset
//                                Row(
//                                    modifier = Modifier.fillMaxWidth(),
//                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                                ) {
//                                    val isErrorSelected = simError != null
//                                    if (isErrorSelected) {
//                                        Button(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = false
//                                                simUpdateAvailable = false
//                                                simIsDownloading = false
//                                                simDownloadedFile = null
//                                                simError = "Simulated network timeout error. Please check connection and try again."
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Error", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
//                                        }
//                                    } else {
//                                        OutlinedButton(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = false
//                                                simUpdateAvailable = false
//                                                simIsDownloading = false
//                                                simDownloadedFile = null
//                                                simError = "Simulated network timeout error. Please check connection and try again."
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
//                                        }
//                                    }
//
//                                    val isResetSelected = !simIsChecking && !simUpdateAvailable && !simIsDownloading && simDownloadedFile == null && simError == null
//                                    if (isResetSelected) {
//                                        Button(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = false
//                                                simUpdateAvailable = false
//                                                simIsDownloading = false
//                                                simDownloadedFile = null
//                                                simError = null
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Reset", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
//                                        }
//                                    } else {
//                                        OutlinedButton(
//                                            onClick = {
//                                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
//                                                simIsChecking = false
//                                                simUpdateAvailable = false
//                                                simIsDownloading = false
//                                                simDownloadedFile = null
//                                                simError = null
//                                            },
//                                            modifier = Modifier.weight(1f).height(36.dp),
//                                            shape = RoundedCornerShape(12.dp),
//                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
//                                            contentPadding = PaddingValues(0.dp)
//                                        ) {
//                                            Text("Reset", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
        }
    }

    // Update Channel Dialog
    if (showChannelDialog) {
        AlertDialog(
            onDismissRequest = { showChannelDialog = false },
            icon = {
                Icon(
                    imageVector = RhythmIcons.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = { Text(context.getString(R.string.updates_channel_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = context.getString(R.string.updates_channel_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val channels = listOf(
                        "stable" to context.getString(R.string.updates_channel_stable_desc),
                        "beta" to context.getString(R.string.updates_channel_beta_desc),
                        "nightly" to context.getString(R.string.updates_channel_nightly_desc)
                    )

                    channels.forEach { (channel, description) ->
                        Card(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                appSettings.setUpdateChannel(channel)
                                showChannelDialog = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (updateChannel == channel)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (updateChannel == channel) {
                                    Icon(
                                        imageVector = RhythmIcons.CheckCircle,
                                        contentDescription = stringResource(R.string.streaming_selected),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showChannelDialog = false }) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.ui_close))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Update Check Interval Dialog
    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            icon = {
                Icon(
                    imageVector = RhythmIcons.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = { Text(context.getString(R.string.updates_check_interval_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = context.getString(R.string.updates_check_frequency),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    intervalOptions.forEach { (hours, label) ->
                        Card(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                appSettings.setUpdateCheckIntervalHours(hours)
                                showIntervalDialog = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (updateCheckIntervalHours == hours)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (updateCheckIntervalHours == hours) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (updateCheckIntervalHours == hours) {
                                    Icon(
                                        imageVector = RhythmIcons.CheckCircle,
                                        contentDescription = stringResource(R.string.streaming_selected),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showIntervalDialog = false }) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.ui_close))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            icon = {
                Icon(
                    imageVector = RhythmIcons.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = { Text(context.getString(R.string.updates_source_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = context.getString(R.string.updates_source_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val sources = listOf(
                        "installed" to getUpdateSourceLabel(context, "installed"),
                        "github" to context.getString(R.string.updates_source_github_desc),
                        "fdroid" to context.getString(R.string.updates_source_fdroid_desc)
                    )

                    sources.forEach { (source, description) ->
                        Card(
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                appSettings.setUpdateSource(source)
                                showSourceDialog = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (updateSource == source)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when (source) {
                                            "installed" -> context.getString(R.string.updates_source_installed)
                                            else -> source.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (updateSource == source) {
                                    Icon(
                                        imageVector = RhythmIcons.CheckCircle,
                                        contentDescription = stringResource(R.string.streaming_selected),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showSourceDialog = false }) {
                    Icon(
                        imageVector = RhythmIcons.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.ui_close))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showFdroidWarningDialog) {
        FdroidUpdateWarningDialog(
            onDismiss = { showFdroidWarningDialog = false },
            onConfirm = {
                appSettings.setUpdatesEnabled(true)
            }
        )
    }
}
