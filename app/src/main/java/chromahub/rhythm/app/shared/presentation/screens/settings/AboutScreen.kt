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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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


// SpotifyApiConfigDialog removed - Canvas API has been removed



@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onNavigateToUpdates: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val appUpdaterViewModel: AppUpdaterViewModel = rememberAppUpdaterViewModel()
    var showLicensesSheet by remember { mutableStateOf(false) }

    val openUrl: (String) -> Unit = { url ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    val copyToClipboard: (String, String) -> Unit = { label, text ->
        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied!", Toast.LENGTH_SHORT).show()
    }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_about_title),
        showBackButton = true,
        onBackClick = onBackClick
    ) { modifier ->
        val lazyListState = rememberSaveable(
            saver = LazyListStateSaver
        ) {
            androidx.compose.foundation.lazy.LazyListState()
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            state = lazyListState,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                val appSettings = remember { AppSettings.getInstance(context) }
                val expressiveShapesEnabled by appSettings.expressiveShapesEnabled.collectAsState()
                val expressiveShapeA by appSettings.expressiveShapeSongArt.collectAsState()
                val expressiveShapeB by appSettings.expressiveShapePlayerArt.collectAsState()
                val expressiveShapeC by appSettings.expressiveShapeAlbumArt.collectAsState()
                val expressiveShapeD by appSettings.expressiveShapePlaylistArt.collectAsState()
                val expressiveShapeE by appSettings.expressiveShapeArtistArt.collectAsState()
                val expressiveShapeF by appSettings.expressiveShapePlayerControls.collectAsState()
                val expressiveShapeG by appSettings.expressiveShapeMiniPlayer.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                        if (expressiveShapesEnabled) {
                            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                            val screenWidthDp = configuration.screenWidthDp
                            val screenHeightDp = configuration.screenHeightDp
                            val expressivePreset by appSettings.expressiveShapePreset.collectAsState()
                            val seed = System.nanoTime().toInt()
                            val primaryBackdropColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            val secondaryBackdropColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.82f)
                            val tertiaryBackdropColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                            val neutralBackdropColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

                            val aboutBackdropShapes = remember(
                                seed,
                                expressiveShapeA,
                                expressiveShapeB,
                                expressiveShapeC,
                                expressiveShapeD,
                                expressiveShapeE,
                                expressiveShapeF,
                                expressiveShapeG,
                                expressivePreset
                            ) {
                                buildSplashBackdropShapes(
                                    seed = seed,
                                    shapeIds = listOf(
                                        expressiveShapeA,
                                        expressiveShapeB,
                                        expressiveShapeC,
                                        expressiveShapeD,
                                        expressiveShapeE,
                                        expressiveShapeF,
                                        expressiveShapeG
                                    ),
                                    preset = expressivePreset,
                                    screenWidthDp = screenWidthDp,
                                    screenHeightDp = screenHeightDp,
                                    primaryColor = primaryBackdropColor,
                                    secondaryColor = secondaryBackdropColor,
                                    tertiaryColor = tertiaryBackdropColor,
                                    neutralColor = neutralBackdropColor
                                )
                            }

                            SplashBackgroundOrbs(shapes = aboutBackdropShapes)
                        }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = chromahub.rhythm.app.R.drawable.rhythm_splash_logo),
                                contentDescription = context.getString(R.string.updates_rhythm_logo_cd),
                                modifier = Modifier.size(82.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = context.getString(R.string.app_name),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Text(
                            text = context.getString(R.string.settings_about_music_player),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = stringResource(R.string.onboarding_welcome_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                val appInfo = context.applicationInfo
                val buildType = when {
                    BuildConfig.DEBUG -> "Debug"
                    BuildConfig.IS_NIGHTLY -> "Nightly"
                    BuildConfig.VERSION_NAME.contains("Beta", ignoreCase = true) -> "Beta"
                    else -> "Stable"
                }
                val buildVariant = if (BuildConfig.FLAVOR.isNotBlank()) {
                    "$buildType (${BuildConfig.FLAVOR})"
                } else {
                    buildType
                }
                val detectedAbis = Build.SUPPORTED_ABIS
                    .take(2)
                    .joinToString(separator = ", ")
                    .ifBlank { context.getString(R.string.settings_about_architecture_value) }

                val detailsItems = listOf(
                    Material3SettingsItem(
                        icon = RhythmIcons.Info,
                        title = { Text(context.getString(R.string.settings_about_version_label)) },
                        description = { Text(BuildConfig.VERSION_NAME) },
                        onClick = { copyToClipboard("Version", BuildConfig.VERSION_NAME) },
                        trailingContent = {
                            Icon(
                                imageVector = MaterialSymbolIcon("content_copy"),
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ),
                    Material3SettingsItem(
                        icon = MaterialSymbolIcon("build"),
                        title = { Text(context.getString(R.string.settings_about_build)) },
                        description = { Text("${BuildConfig.VERSION_CODE} • $buildVariant") },
                        onClick = { copyToClipboard("Build Info", "${BuildConfig.VERSION_CODE} • $buildVariant") },
                        trailingContent = {
                            Icon(
                                imageVector = MaterialSymbolIcon("content_copy"),
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ),
                    Material3SettingsItem(
                        icon = MaterialSymbolIcon("developer_mode"),
                        title = { Text(context.getString(R.string.settings_about_target_sdk)) },
                        description = { Text(appInfo.targetSdkVersion.toString()) },
                        onClick = { copyToClipboard("Target SDK", appInfo.targetSdkVersion.toString()) },
                        trailingContent = {
                            Icon(
                                imageVector = MaterialSymbolIcon("content_copy"),
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ),
                    Material3SettingsItem(
                        icon = MaterialSymbolIcon("memory"),
                        title = { Text(context.getString(R.string.settings_about_architecture)) },
                        description = { Text(detectedAbis) },
                        onClick = { copyToClipboard("Architecture", detectedAbis) },
                        trailingContent = {
                            Icon(
                                imageVector = MaterialSymbolIcon("content_copy"),
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    ),
                    Material3SettingsItem(
                        icon = MaterialSymbolIcon("content_copy"),
                        title = { Text("Copy System Info") },
                        description = { Text("Copy all version and hardware details to clipboard") },
                        onClick = {
                            val allInfo = """
                                App: Rhythm
                                Version: ${BuildConfig.VERSION_NAME}
                                Build: ${BuildConfig.VERSION_CODE} ($buildVariant)
                                Target SDK: ${appInfo.targetSdkVersion}
                                Device OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                                Brand/Manufacturer: ${Build.BRAND} / ${Build.MANUFACTURER}
                                Model (Product): ${Build.MODEL} (${Build.PRODUCT})
                                Board/Hardware: ${Build.BOARD} / ${Build.HARDWARE}
                                Architecture (ABIs): ${Build.SUPPORTED_ABIS.joinToString(", ")}
                            """.trimIndent()
                            copyToClipboard("System Info", allInfo)
                        }
                    )
                )

                Material3SettingsGroup(
                    title = context.getString(R.string.settings_about_project_details),
                    items = detailsItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item {
                Text(
                    text = context.getString(R.string.settings_about_credits),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp, top = 18.dp)
                )

                DeveloperCard(
                    name = "Anjishnu Nandi",
                    role = "Lead Developer & Project Architect",
                    githubUsername = "cromaguy",
                    avatarUrl = "https://github.com/cromaguy.png",
                    supportUrl = "https://ko-fi.com/anjishnunandi",
                    openUrl = openUrl
                )
                
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = ExpressiveShapes.Large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = MaterialSymbolIcon("diversity_3", filled = true),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = context.getString(R.string.settings_about_team_chromahub),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = context.getString(R.string.settings_about_team_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            item {
                Text(
                    text = context.getString(R.string.settings_about_community),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp, top = 18.dp)
                )
                
                val maintainerItems = remember(context, haptics) {
                    listOf(
                        createCommunityMemberItem(
                            context = context,
                            haptics = haptics,
                            name = "Izzy",
                            role = "Manages updates on IzzyOnDroid",
                            githubUsername = "IzzySoft",
                            avatarUrl = "https://github.com/IzzySoft.png"
                        ),
                        createCommunityMemberItem(
                            context = context,
                            haptics = haptics,
                            name = "linsui",
                            role = "Manages updates on F-Droid",
                            githubUsername = "linsui",
                            avatarUrl = "https://github.com/linsui.png"
                        ),
                        createCommunityMemberItem(
                            context = context,
                            haptics = haptics,
                            name = "Licaon_Kter",
                            role = "Manages updates on F-Droid",
                            githubUsername = "licaon-kter",
                            avatarUrl = "https://github.com/licaon-kter.png"
                        )
                    )
                }
                Material3SettingsGroup(
                    title = context.getString(R.string.about_community_group_maintainers),
                    items = maintainerItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )

                Spacer(modifier = Modifier.height(12.dp))

                val collaboratorItems = remember(context, haptics) {
                    listOf(
                        createCommunityMemberItem(
                            context = context,
                            haptics = haptics,
                            name = "Christian",
                            role = "Guide & Booming Music's Lead Dev",
                            githubUsername = "mardous",
                            avatarUrl = "https://github.com/mardous.png"
                        ),
                        createCommunityMemberItem(
                            context = context,
                            haptics = haptics,
                            name = "theovilardo",
                            role = "Guide & PixelPlayer's Lead Dev",
                            githubUsername = "theovilardo",
                            avatarUrl = "https://github.com/theovilardo.png"
                        ),
                        createCommunityMemberItem(
                            context = context,
                            haptics = haptics,
                            name = "Nick",
                            role = "Guide & Gramophone's Maintainer",
                            githubUsername = "nift4",
                            avatarUrl = "https://github.com/nift4.png"
                        ),
                        createCommunityMemberItem(
                            context = context,
                            haptics = haptics,
                            name = "vivi",
                            role = "Guide & Vivi Music's Lead Dev",
                            githubUsername = "vivizzz007",
                            avatarUrl = "https://github.com/vivizzz007.png"
                        ),
                        createCommunityMemberItem(
                            context = context,
                            haptics = haptics,
                            name = "Alex",
                            role = "Lyrically API's Lead Dev",
                            githubUsername = "Paxsenix0",
                            avatarUrl = "https://github.com/Paxsenix0.png"
                        )
                    )
                }
                Material3SettingsGroup(
                    title = context.getString(R.string.about_community_group_collaborators),
                    items = collaboratorItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )

                Spacer(modifier = Modifier.height(12.dp))

                val designTestingItems = remember(context, haptics) {
                    listOf(
                        createCommunityMemberItem(
                            context = context,
                            haptics = haptics,
                            name = "itzKane",
                            role = "UI Concept Designer",
                            githubUsername = "soykane",
                            avatarUrl = "https://github.com/soykane.png"
                        ),
                        createCommunityMemberItem(
                            context = context,
                            haptics = haptics,
                            name = "firefly-sylestia",
                            role = "Beta Tester & QA",
                            githubUsername = "firefly-sylestia",
                            avatarUrl = "https://github.com/firefly-sylestia.png"
                        )
                    )
                }
                Material3SettingsGroup(
                    title = context.getString(R.string.about_community_group_design_testing),
                    items = designTestingItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }



            item {
                val actionItems = listOf(
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = RhythmIcons.Download,
                            title = context.getString(R.string.settings_about_check_updates),
                            description = context.getString(R.string.updates_check_again),
                            onClick = {
                                appUpdaterViewModel.checkForUpdates(force = true)
                                onNavigateToUpdates?.invoke()
                            }
                        )
                    ),
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = RhythmIcons.Language,
                            title = context.getString(R.string.settings_about_visit_website),
                            description = "rhythmweb.vercel.app",
                            onClick = { openUrl("https://rhythmweb.vercel.app/") }
                        )
                    ),
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = RhythmIcons.Code,
                            title = context.getString(R.string.settings_about_view_github),
                            description = "github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}",
                            onClick = { openUrl("https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}") }
                        )
                    ),
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = RhythmIcons.BugReport,
                            title = context.getString(R.string.settings_about_report_bug),
                            description = "github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/issues",
                            onClick = { openUrl("https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/issues") }
                        )
                    ),
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = RhythmIcons.Settings,
                            title = context.getString(R.string.settings_about_open_source_libs),
                            description = context.getString(R.string.settings_about_view_dependencies),
                            onClick = { showLicensesSheet = true }
                        )
                    ),
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = MaterialSymbolIcon("chat", filled = true),
                            title = stringResource(R.string.aboutscreen_discord_community),
                            description = "discord.gg/XjPyUYPQYc",
                            onClick = { openUrl("https://discord.gg/XjPyUYPQYc") }
                        )
                    ),
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = MaterialSymbolIcon("send", filled = true),
                            title = stringResource(R.string.cd_telegram_support),
                            description = "t.me/RhythmSupport",
                            onClick = { openUrl("https://t.me/RhythmSupport") }
                        )
                    )
                )

                Material3SettingsGroup(
                    title = context.getString(R.string.settings_about_actions),
                    items = actionItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (showLicensesSheet) {
            LicensesBottomSheet(
                onDismiss = { showLicensesSheet = false }
            )
        }
    }
}


@Composable
fun DeveloperCard(
    name: String,
    role: String,
    githubUsername: String,
    avatarUrl: String,
    supportUrl: String,
    openUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = ExpressiveShapes.ExtraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(
            modifier = Modifier
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                val fallbackPainter = painterResource(id = R.drawable.ic_music_note)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = name,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(ExpressiveShapes.SquircleLarge)
                        .background(MaterialTheme.colorScheme.surface),
                    error = fallbackPainter,
                    placeholder = fallbackPainter
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    shape = ExpressiveShapes.Full,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = role,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            openUrl("https://github.com/$githubUsername")
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = RhythmIcons.Link,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "@$githubUsername",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                        openUrl(supportUrl)
                    },
                    shape = ExpressiveShapes.Full,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("local_cafe", filled = true),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.aboutscreen_support_development),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

private fun createCommunityMemberItem(
    context: Context,
    haptics: HapticFeedback,
    name: String,
    role: String,
    githubUsername: String,
    avatarUrl: String
): Material3SettingsItem {
    return Material3SettingsItem(
        leadingContent = {
            val fallbackPainter = painterResource(id = R.drawable.ic_music_note)
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(ExpressiveShapes.SquircleMedium)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                error = fallbackPainter,
                placeholder = fallbackPainter
            )
        },
        title = {
            Text(
                text = name,
                fontWeight = FontWeight.Bold
            )
        },
        description = {
            Text(
                text = role,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Icon(
                imageVector = MaterialSymbolIcon("chevron_right"),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        },
        onClick = {
            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$githubUsername")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    )
}



@Composable
fun FeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



@Composable
fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.End
        )
    }
}



@Composable
fun TechStackItem(
    technology: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = technology,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



@Composable
fun CreditItem(
    name: String,
    role: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = role,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}