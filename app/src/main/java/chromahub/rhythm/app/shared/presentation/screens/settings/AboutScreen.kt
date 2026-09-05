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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.BuildConfig
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
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveShapes
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
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingGroup
import chromahub.rhythm.app.shared.data.model.AppSettings
import androidx.core.net.toUri



@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onNavigateToUpdates: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val appUpdaterViewModel: AppUpdaterViewModel = rememberAppUpdaterViewModel()
    val appSettings = remember { AppSettings.getInstance(context) }
    var showLicensesSheet by remember { mutableStateOf(false) }

    val openUrl: (String) -> Unit = { url ->
        val intent = Intent(Intent.ACTION_VIEW, (url).toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    val copyToClipboard: (String, String) -> Unit = { label, text ->
        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.about_copied_format, label), Toast.LENGTH_SHORT).show()
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
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

                val detailCards = listOf(
                    ProjectDetailCardData(
                        icon = RhythmIcons.Info,
                        label = context.getString(R.string.settings_about_version_label),
                        value = BuildConfig.VERSION_NAME
                    ),
                    ProjectDetailCardData(
                        icon = MaterialSymbolIcon("build"),
                        label = context.getString(R.string.settings_about_build),
                        value = "${BuildConfig.VERSION_CODE} • $buildVariant"
                    ),
                    ProjectDetailCardData(
                        icon = MaterialSymbolIcon("developer_mode"),
                        label = context.getString(R.string.settings_about_target_sdk),
                        value = appInfo.targetSdkVersion.toString()
                    ),
                    ProjectDetailCardData(
                        icon = MaterialSymbolIcon("memory"),
                        label = context.getString(R.string.settings_about_architecture),
                        value = detectedAbis
                    )
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    detailCards.chunked(2).forEachIndexed { rowIndex, rowCards ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max)
                        ) {
                            rowCards.forEachIndexed { colIndex, card ->
                                ProjectDetailCard(
                                    icon = card.icon,
                                    label = card.label,
                                    value = card.value,
                                    shape = getDetailCardShape(rowIndex * 2 + colIndex, detailCards.size),
                                    onClick = { copyToClipboard(card.label, card.value) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }

                    ProjectDetailCard(
                        icon = MaterialSymbolIcon("content_copy"),
                        label = context.getString(R.string.about_copy_system_info),
                        value = context.getString(R.string.about_copy_system_info_desc),
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                        onClick = {
                            val allInfo = buildString {
                                appendLine("App: Rhythm")
                                appendLine("Version: ${BuildConfig.VERSION_NAME}")
                                appendLine("Build: ${BuildConfig.VERSION_CODE} ($buildVariant)")
                                appendLine("Target SDK: ${appInfo.targetSdkVersion}")
                                appendLine("Device OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                                appendLine("Brand/Manufacturer: ${Build.BRAND} / ${Build.MANUFACTURER}")
                                appendLine("Model (Product): ${Build.MODEL} (${Build.PRODUCT})")
                                appendLine("Board/Hardware: ${Build.BOARD} / ${Build.HARDWARE}")
                                append("Architecture (ABIs): ${Build.SUPPORTED_ABIS.joinToString(", ")}")
                            }
                            copyToClipboard(context.getString(R.string.about_copy_system_info), allInfo)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                    githubUsername = "cromaguy",
                    avatarUrl = "https://github.com/cromaguy.png",
                    supportUrl = "https://ko-fi.com/anjishnunandi",
                    teamTitle = context.getString(R.string.settings_about_team_chromahub),
                    teamDescription = context.getString(R.string.settings_about_team_desc),
                    openUrl = openUrl
                )
            }

            item {
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
                            icon = RhythmIcons.BugReport,
                            title = context.getString(R.string.settings_about_report_bug),
                            description = "github.com/cromaguy/Rhythm/issues",
                            onClick = { openUrl("https://github.com/cromaguy/Rhythm/issues") }
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
                    ),
                    toMaterial3SettingsItem(
                        context = context,
                        hapticFeedback = haptics,
                        item = SettingItem(
                            icon = MaterialSymbolIcon("restart_alt", filled = true),
                            title = stringResource(R.string.about_replay_tour),
                            description = stringResource(R.string.about_replay_tour_desc),
                            onClick = {
                                appSettings.setOnboardingCompleted(false)
                            }
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
    githubUsername: String,
    avatarUrl: String,
    supportUrl: String,
    teamTitle: String,
    teamDescription: String,
    openUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                val fallbackPainter = painterResource(id = R.drawable.ic_music_note)
                val cookieShape = rememberExpressiveShape("COOKIE_12")
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = name,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(cookieShape)
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

                Spacer(modifier = Modifier.height(18.dp))

                ExpressiveButtonGroup(
                    style = ButtonGroupStyle.Tonal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ExpressiveGroupButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            openUrl("https://rhythmweb.vercel.app/")
                        },
                        isStart = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = context.getString(R.string.settings_about_visit_website),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ExpressiveGroupButton(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            openUrl("https://github.com/$githubUsername")
                        },
                        isEnd = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Code,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = context.getString(R.string.settings_about_view_github),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = teamTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = teamDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
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
            val intent = Intent(Intent.ACTION_VIEW, ("https://github.com/$githubUsername").toUri()).apply {
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

private data class ProjectDetailCardData(
    val icon: Any,
    val label: String,
    val value: String
)

private fun getDetailCardShape(index: Int, totalItems: Int): RoundedCornerShape {
    if (totalItems <= 1) {
        return RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    }
    if (totalItems == 2) {
        return if (index == 0) {
            RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        } else {
            RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        }
    }
    return when (index) {
        0 -> RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        else -> RoundedCornerShape(6.dp)
    }
}

@Composable
private fun ProjectDetailCard(
    icon: Any,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp)
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    onClick()
                }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon is ImageVector) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = icon as MaterialSymbolIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
