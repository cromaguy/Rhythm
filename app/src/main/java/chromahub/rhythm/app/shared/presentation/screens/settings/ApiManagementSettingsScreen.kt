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
import kotlinx.coroutines.launch


// API Management Screen
@Composable
fun ApiManagementSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val appSettings = AppSettings.getInstance(context)

    // API states
    val integrationsEnabled by appSettings.integrationsEnabled.collectAsState()
    val deezerApiEnabled by appSettings.deezerApiEnabled.collectAsState()
    val lrclibApiEnabled by appSettings.lrclibApiEnabled.collectAsState()
    val betterLyricsApiEnabled by appSettings.betterLyricsApiEnabled.collectAsState()
    val ytMusicApiEnabled by appSettings.ytMusicApiEnabled.collectAsState()
    val lyricallyApiEnabled by appSettings.lyricallyApiEnabled.collectAsState()
    val wikipediaApiEnabled by appSettings.wikipediaApiEnabled.collectAsState()
    val appleCanvasEnabled by appSettings.appleCanvasEnabled.collectAsState()
    val appleCanvasNetworkMode by appSettings.appleCanvasNetworkMode.collectAsState()
    var showCanvasNetworkModeDialog by remember { mutableStateOf(false) }

    val streamingPrefs = remember(appSettings) { appSettings.streamingPrefs }
    val listenBrainzToken by streamingPrefs.listenBrainzToken.collectAsState()
    val libreFmSessionKey by streamingPrefs.libreFmSessionKey.collectAsState()
    val malojaServerUrl by streamingPrefs.malojaServerUrl.collectAsState()
    val malojaToken by streamingPrefs.malojaToken.collectAsState()
    val discordRpcEnabled by streamingPrefs.discordRpcEnabled.collectAsState()
    val discordRpcAppId by streamingPrefs.discordRpcApplicationId.collectAsState()
    val discordSupported = remember {
        chromahub.rhythm.app.infrastructure.network.presence.DiscordPresenceFactory.isSupported()
    }
    val scrobbleScope = rememberCoroutineScope()
    var showListenBrainzDialog by remember { mutableStateOf(false) }
    var showLibreFmDialog by remember { mutableStateOf(false) }
    var showMalojaDialog by remember { mutableStateOf(false) }
    var showDiscordDialog by remember { mutableStateOf(false) }

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_api_management),
        showBackButton = true,
        onBackClick = {
            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
            onBackClick()
        },
        headerContent = {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (integrationsEnabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbolIcon("hub"),
                        contentDescription = null,
                        tint = if (integrationsEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(35.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(if (integrationsEnabled) R.string.status_active else R.string.status_disabled),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TunerAnimatedSwitch(
                        checked = integrationsEnabled,
                        onCheckedChange = { enabled ->
                            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
                            appSettings.setIntegrationsEnabled(enabled)
                        }
                    )
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }


            // API Services
            item {
                Text(
                    text = context.getString(R.string.external_services),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                val apiServiceItems = buildList {
                    if (chromahub.rhythm.app.BuildConfig.ENABLE_DEEZER) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = null,
                                    title = stringResource(R.string.onboarding_integration_deezer),
                                    description = context.getString(R.string.api_deezer_desc),
                                    enabled = integrationsEnabled,
                                    toggleState = deezerApiEnabled,
                                    onToggleChange = { enabled -> appSettings.setDeezerApiEnabled(enabled) }
                                )
                            )
                        )
                    }

                    if (chromahub.rhythm.app.BuildConfig.ENABLE_LRCLIB) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = null,
                                    title = stringResource(R.string.onboarding_integration_lrclib),
                                    description = context.getString(R.string.api_lrclib_desc),
                                    enabled = integrationsEnabled,
                                    toggleState = lrclibApiEnabled,
                                    onToggleChange = { enabled -> appSettings.setLrcLibApiEnabled(enabled) }
                                )
                            )
                        )
                    }

                    if (chromahub.rhythm.app.BuildConfig.ENABLE_BETTERLYRICS) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = null,
                                    title = stringResource(R.string.onboarding_integration_betterlyrics),
                                    description = context.getString(R.string.api_betterlyrics_desc),
                                    enabled = integrationsEnabled,
                                    toggleState = betterLyricsApiEnabled,
                                    onToggleChange = { enabled -> appSettings.setBetterLyricsApiEnabled(enabled) }
                                )
                            )
                        )
                    }

                    if (chromahub.rhythm.app.BuildConfig.ENABLE_LYRICALLY_API) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = null,
                                    title = stringResource(R.string.apimanagementsettingsscreen_lyrically),
                                    description = context.getString(R.string.api_lyrically_desc),
                                    enabled = integrationsEnabled,
                                    toggleState = lyricallyApiEnabled,
                                    onToggleChange = { enabled -> appSettings.setLyricallyApiEnabled(enabled) }
                                )
                            )
                        )
                    }

                    add(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = null,
                                title = context.getString(R.string.api_apple_motion_canvas),
                                description = context.getString(
                                    R.string.api_apple_motion_canvas_desc,
                                    when (appleCanvasNetworkMode) {
                                        chromahub.rhythm.app.shared.data.model.CanvasNetworkMode.WIFI_ONLY -> context.getString(R.string.api_apple_canvas_wifi)
                                        chromahub.rhythm.app.shared.data.model.CanvasNetworkMode.BOTH -> context.getString(R.string.api_apple_canvas_both)
                                    }
                                ),
                                enabled = integrationsEnabled,
                                toggleState = appleCanvasEnabled,
                                onToggleChange = { enabled -> appSettings.setAppleCanvasEnabled(enabled) },
                                onClick = {
                                    if (appleCanvasEnabled) {
                                        HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                        showCanvasNetworkModeDialog = true
                                    }
                                }
                            )
                        )
                    )

                    if (chromahub.rhythm.app.BuildConfig.ENABLE_YOUTUBE_MUSIC) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = null,
                                    title = stringResource(R.string.onboarding_integration_ytmusic),
                                    description = context.getString(R.string.api_ytmusic_desc),
                                    enabled = integrationsEnabled,
                                    toggleState = ytMusicApiEnabled,
                                    onToggleChange = { enabled -> appSettings.setYTMusicApiEnabled(enabled) }
                                )
                            )
                        )
                    }

                    if (chromahub.rhythm.app.BuildConfig.ENABLE_WIKIPEDIA) {
                        add(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = null,
                                    title = stringResource(R.string.onboarding_integration_wikipedia),
                                    description = stringResource(R.string.onboarding_integration_wikipedia_desc),
                                    enabled = integrationsEnabled,
                                    toggleState = wikipediaApiEnabled,
                                    onToggleChange = { enabled -> appSettings.setWikipediaApiEnabled(enabled) }
                                )
                            )
                        )
                    }


                    add(
                        Material3SettingsItem(
                            icon = null,
                            title = { Text(stringResource(R.string.apimanagementsettingsscreen_github)) },
                            description = { Text(stringResource(R.string.apimanagementsettingsscreen_app_updates_and_release)) }
                        )
                    )
                }

                Material3SettingsGroup(
                    items = apiServiceItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Text(
                    text = "Scrobbling Services",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
                Material3SettingsGroup(
                    items = listOf(
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("graphic_eq"),
                                title = "ListenBrainz",
                                description = if (!listenBrainzToken.isNullOrBlank()) "Connected (auto-scrobble + offline queue)" else "Tap to configure user token",
                                onClick = { showListenBrainzDialog = true }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("radio"),
                                title = "Libre.fm",
                                description = if (!libreFmSessionKey.isNullOrBlank()) "Connected (GNU FM session active)" else "Tap to sign in with Libre.fm account",
                                onClick = { showLibreFmDialog = true }
                            )
                        ),
                        toMaterial3SettingsItem(
                            context = context,
                            hapticFeedback = hapticFeedback,
                            item = SettingItem(
                                icon = MaterialSymbolIcon("dns"),
                                title = "Maloja (Self-Hosted)",
                                description = if (!malojaServerUrl.isNullOrBlank() && !malojaToken.isNullOrBlank()) "Connected to $malojaServerUrl" else "Tap to configure Maloja URL & API token",
                                onClick = { showMalojaDialog = true }
                            )
                        )
                    ) + if (discordSupported) {
                        listOf(
                            toMaterial3SettingsItem(
                                context = context,
                                hapticFeedback = hapticFeedback,
                                item = SettingItem(
                                    icon = MaterialSymbolIcon("sensors"),
                                    title = "Discord Rich Presence",
                                    description = when {
                                        discordRpcEnabled && discordRpcAppId.isNotBlank() -> "Enabled (App ID $discordRpcAppId)"
                                        discordRpcEnabled -> "Enabled (Tap to set Discord App ID)"
                                        else -> "Share active track & format to Discord IPC"
                                    },
                                    toggleState = discordRpcEnabled,
                                    onToggleChange = { checked ->
                                        streamingPrefs.setDiscordRpcConfig(checked, discordRpcAppId)
                                    },
                                    onClick = { showDiscordDialog = true }
                                )
                            )
                        )
                    } else {
                        emptyList()
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
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
                                text = context.getString(R.string.api_services),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = context.getString(R.string.external_services_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        if (showCanvasNetworkModeDialog) {
            CanvasNetworkModeDialog(
                onDismiss = { showCanvasNetworkModeDialog = false },
                appSettings = appSettings,
                context = context,
                haptic = hapticFeedback
            )
        }

        if (showListenBrainzDialog) {
            var tokenInput by remember(listenBrainzToken) { mutableStateOf(listenBrainzToken.orEmpty()) }
            AlertDialog(
                onDismissRequest = { showListenBrainzDialog = false },
                title = { Text("ListenBrainz Token") },
                text = {
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("User token (blank to clear)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        streamingPrefs.setListenBrainzToken(tokenInput)
                        showListenBrainzDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showListenBrainzDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showLibreFmDialog) {
            var userInput by remember { mutableStateOf("") }
            var passInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showLibreFmDialog = false },
                title = { Text("Libre.fm Scrobbling") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!libreFmSessionKey.isNullOrBlank()) {
                            Text("Session is currently active. Sign in below to replace it or disconnect.")
                        }
                        OutlinedTextField(
                            value = userInput,
                            onValueChange = { userInput = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = passInput,
                            onValueChange = { passInput = it },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scrobbleScope.launch {
                            val res = chromahub.rhythm.app.infrastructure.network.scrobble.MultiScrobblerManager
                                .getInstance(context)
                                .authenticateLibreFm(userInput, passInput)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    if (res.isSuccess) "Libre.fm connected" else (res.exceptionOrNull()?.message ?: "Sign-in failed"),
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (res.isSuccess) showLibreFmDialog = false
                            }
                        }
                    }) { Text("Connect") }
                },
                dismissButton = {
                    Row {
                        if (!libreFmSessionKey.isNullOrBlank()) {
                            TextButton(onClick = {
                                streamingPrefs.setLibreFmSessionKey(null)
                                showLibreFmDialog = false
                            }) { Text("Disconnect") }
                        }
                        TextButton(onClick = { showLibreFmDialog = false }) { Text("Cancel") }
                    }
                }
            )
        }

        if (showMalojaDialog) {
            var urlInput by remember(malojaServerUrl) { mutableStateOf(malojaServerUrl.orEmpty()) }
            var keyInput by remember(malojaToken) { mutableStateOf(malojaToken.orEmpty()) }
            AlertDialog(
                onDismissRequest = { showMalojaDialog = false },
                title = { Text("Maloja Server") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("Server URL (e.g. https://maloja.local)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            label = { Text("API Key (blank both to clear)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        streamingPrefs.setMalojaConfig(urlInput, keyInput)
                        showMalojaDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showMalojaDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showDiscordDialog && discordSupported) {
            var appIdInput by remember(discordRpcAppId) { mutableStateOf(discordRpcAppId) }
            AlertDialog(
                onDismissRequest = { showDiscordDialog = false },
                title = { Text("Discord Rich Presence") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Enter your numeric Discord Application ID from the Discord Developer Portal to enable local Android IPC presence.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = appIdInput,
                            onValueChange = { ch -> appIdInput = ch.filter { it.isDigit() } },
                            label = { Text("Discord Application ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        streamingPrefs.setDiscordRpcConfig(
                            enabled = appIdInput.isNotBlank(),
                            applicationId = appIdInput
                        )
                        showDiscordDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscordDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}