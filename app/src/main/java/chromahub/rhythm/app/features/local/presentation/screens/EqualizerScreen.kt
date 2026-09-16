/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens

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
import android.provider.Settings
import android.media.audiofx.AudioEffect
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import chromahub.rhythm.app.BuildConfig
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.GsonUtils
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.EqualizerUtils
import chromahub.rhythm.app.util.windowScreenWidthDp
import chromahub.rhythm.app.util.AudioDeviceManager
import chromahub.rhythm.app.activities.MainActivity
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlin.system.exitProcess
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.shared.presentation.components.common.ArcProgressSlider
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.common.StyledProgressBar
import chromahub.rhythm.app.shared.presentation.components.common.ProgressStyle
import chromahub.rhythm.app.shared.presentation.components.common.ThumbStyle
import chromahub.rhythm.app.shared.presentation.components.common.CookieHorizontalSlider
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.LicensesBottomSheet
import chromahub.rhythm.app.ui.utils.LazyListStateSaver
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.AppUpdaterViewModel
import chromahub.rhythm.app.ui.theme.getFontPreviewStyle
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.EaseInBack
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.lerp
import chromahub.rhythm.app.features.local.presentation.screens.LibraryTab
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import chromahub.rhythm.app.R
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AddToPlaylistBottomSheet
import chromahub.rhythm.app.features.local.presentation.screens.AddToPlaylistScreen
import chromahub.rhythm.app.shared.presentation.components.dialogs.CreatePlaylistDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.QueueActionDialog
import chromahub.rhythm.app.shared.presentation.components.player.MiniPlayer
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons.Delete
import chromahub.rhythm.app.features.local.presentation.screens.LibraryScreen
import chromahub.rhythm.app.shared.presentation.components.common.SmallTabAnimation
import chromahub.rhythm.app.shared.presentation.screens.player.PlayerScreen
import chromahub.rhythm.app.features.local.presentation.screens.PlaylistDetailScreen
import chromahub.rhythm.app.shared.presentation.screens.settings.SettingsScreenWrapper
import chromahub.rhythm.app.shared.presentation.screens.settings.*
import chromahub.rhythm.app.shared.presentation.components.MediaScanLoader
import chromahub.rhythm.app.shared.presentation.viewmodel.ThemeViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AutoEQPresetPickerBottomSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.DeviceConfigurationBottomSheet
import chromahub.rhythm.app.features.local.presentation.components.dialogs.SaveCustomPresetDialog
import chromahub.rhythm.app.features.local.presentation.components.bottomsheets.EqualizerPresetOrderBottomSheet
import chromahub.rhythm.app.shared.presentation.components.common.rhythmMarquee
import chromahub.rhythm.app.shared.data.model.AutoEQProfile
import chromahub.rhythm.app.shared.data.model.CustomEqualizerPreset
import chromahub.rhythm.app.shared.data.model.EqualizerPresetType
import chromahub.rhythm.app.shared.data.model.UnifiedEqualizerPreset
import androidx.compose.ui.res.stringResource
import java.util.Locale

data class EqualizerPreset(
    val name: String,
    val icon: MaterialSymbolIcon,
    val bands: List<Float>,
    val type: EqualizerPresetType = EqualizerPresetType.BUILT_IN,
    val id: String = name,
    val brand: String? = null
)

@Composable
fun TunerAnimatedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    chromahub.rhythm.app.shared.presentation.screens.settings.TunerAnimatedSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}

/**
 * Equalizer Screen - Advanced Audio Equalization
 * Full-featured equalizer with presets, frequency bands, and audio effects
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    navController: NavController,
    viewModel: MusicViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isTablet = windowScreenWidthDp() >= 600

    // Collect states from settings
    val equalizerEnabledState by viewModel.equalizerEnabled.collectAsState()
    val equalizerPresetState by viewModel.equalizerPreset.collectAsState()
    val equalizerBandLevelsState by viewModel.equalizerBandLevels.collectAsState()
    val bassBoostEnabledState by viewModel.bassBoostEnabled.collectAsState()
    val bassBoostStrengthState by viewModel.bassBoostStrength.collectAsState()
    val virtualizerEnabledState by viewModel.virtualizerEnabled.collectAsState()
    val virtualizerStrengthState by viewModel.virtualizerStrength.collectAsState()
    val spatializationStatus by viewModel.spatializationStatus.collectAsState()
    val isSpatializationAvailable by viewModel.isSpatializationAvailable.collectAsState()
    val isBassBoostAvailableState by viewModel.isBassBoostAvailable.collectAsState()

    val appSettings = remember { AppSettings.getInstance(context) }
    val isAudioOffloadActive by appSettings.isAudioOffloadActive.collectAsState()
    val batterySaverEnabled by appSettings.batterySaverEnabled.collectAsState()
    val batterySaverMode by appSettings.batterySaverMode.collectAsState()
    val batterySaverEnableOffload by appSettings.batterySaverEnableOffload.collectAsState()
    val isOffloadEnforced = batterySaverEnabled && (batterySaverMode == "auto" || (batterySaverMode == "manual" && batterySaverEnableOffload))

    // Update spatialization and bass boost availability when screen is shown
    LaunchedEffect(Unit) {
        viewModel.updateSpatializationStatus()
        viewModel.updateBassBoostAvailability()
    }

    // Local mutable states for UI
    var isEqualizerEnabled by remember(equalizerEnabledState) { mutableStateOf(equalizerEnabledState) }
    var selectedPreset by remember(equalizerPresetState) { mutableStateOf(equalizerPresetState) }
    var bandLevels by remember(equalizerBandLevelsState) {
        mutableStateOf(
            equalizerBandLevelsState.split(",").mapNotNull { it.toFloatOrNull() }.let { levels ->
                when {
                    levels.size == 10 -> levels
                    levels.size == 5 -> List(10) { if (it < 5) levels[it] else 0f }
                    else -> List(10) { 0f }
                }
            }
        )
    }
    var isBassBoostEnabled by remember(bassBoostEnabledState) { mutableStateOf(bassBoostEnabledState) }
    var bassBoostStrength by remember(bassBoostStrengthState) { mutableFloatStateOf(bassBoostStrengthState.toFloat()) }
    var isVirtualizerEnabled by remember(virtualizerEnabledState) { mutableStateOf(virtualizerEnabledState) }
    var virtualizerStrength by remember(virtualizerStrengthState) { mutableFloatStateOf(virtualizerStrengthState.toFloat()) }

    // Preset definitions & collectors
    val customPresets by viewModel.customEqualizerPresets.collectAsState()
    val presetOrder by viewModel.equalizerPresetOrder.collectAsState()
    val hiddenPresets by viewModel.hiddenEqualizerPresets.collectAsState()
    val pinnedAutoEQ by viewModel.pinnedAutoEQProfiles.collectAsState()
    val autoEQProfiles by viewModel.autoEQProfiles.collectAsState()
    val currentAutoEQProfile by viewModel.appSettings.autoEQProfile.collectAsState()
    val userDevicesJson by viewModel.appSettings.userAudioDevices.collectAsState()
    val userDevices = remember(userDevicesJson) {
        chromahub.rhythm.app.shared.data.model.UserAudioDevice.fromJson(userDevicesJson)
    }

    LaunchedEffect(Unit) {
        if (autoEQProfiles.isEmpty()) {
            viewModel.loadAutoEQProfiles()
        }
    }

    val builtInPresets = remember {
        listOf(
            EqualizerPreset("Flat", MaterialSymbolIcon("linear_scale", filled = true), listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
            EqualizerPreset("Rock", RhythmIcons.MusicNote, listOf(4.5f, 3.8f, 2.5f, 0.5f, -1.5f, -0.8f, 2.2f, 3.5f, 5.5f, 4.0f)),
            EqualizerPreset("Pop", MaterialSymbolIcon("star", filled = true), listOf(-1.5f, 0.5f, 2.8f, 4.2f, 3.5f, 2.0f, 0.5f, 1.5f, 2.5f, 1.0f)),
            EqualizerPreset("Jazz", MaterialSymbolIcon("piano", filled = true), listOf(3.5f, 2.8f, 1.5f, 0.5f, -1.5f, -0.5f, 1.8f, 2.5f, 4.0f, 3.0f)),
            EqualizerPreset("Classical", RhythmIcons.Library, listOf(3.0f, 1.5f, -0.5f, -1.5f, -2.0f, -1.5f, 0.5f, 2.5f, 3.5f, 2.5f)),
            EqualizerPreset("Electronic", MaterialSymbolIcon("graphic_eq", filled = true), listOf(5.5f, 4.8f, 3.5f, 1.5f, 0.5f, 0f, 2.5f, 4.5f, 5.0f, 4.5f)),
            EqualizerPreset("Hip Hop", MaterialSymbolIcon("graphic_eq", filled = true), listOf(6.5f, 5.5f, 3.5f, 1.5f, -0.5f, -0.8f, 1.8f, 3.5f, 4.5f, 3.5f)),
            EqualizerPreset("Vocal", MaterialSymbolIcon("record_voice_over", filled = true), listOf(-1.0f, 0.5f, 1.5f, 2.8f, 4.5f, 5.0f, 4.0f, 2.5f, 1.5f, 0.5f)),
            EqualizerPreset("Bass Boost", RhythmIcons.SpeakerFilled, listOf(6.0f, 5.0f, 3.5f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f)),
            EqualizerPreset("Treble Boost", MaterialSymbolIcon("waves", filled = true), listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 1.5f, 3.0f, 5.0f, 6.0f)),
            EqualizerPreset("V-Shape", MaterialSymbolIcon("show_chart", filled = true), listOf(5.5f, 4.0f, 1.5f, -1.0f, -2.5f, -2.5f, -0.5f, 2.0f, 4.5f, 5.5f)),
            EqualizerPreset("Harman", RhythmIcons.HeadphonesFilled, listOf(3.5f, 2.0f, 0.5f, -1.0f, 0f, 0.5f, 1.5f, 2.0f, 2.5f, 1.0f))
        )
    }

    val allAvailablePresets = remember(builtInPresets, customPresets, pinnedAutoEQ, currentAutoEQProfile, autoEQProfiles, userDevices) {
        val list = mutableListOf<EqualizerPreset>()

        // 1. AutoEQ profiles (First by default)
        pinnedAutoEQ.forEach { pinnedName ->
            val profile = autoEQProfiles.find { it.name.equals(pinnedName, ignoreCase = true) }
            val bands = profile?.bands ?: List(10) { 0f }
            list.add(
                EqualizerPreset(
                    name = "AutoEQ: $pinnedName",
                    icon = RhythmIcons.HeadphonesFilled,
                    bands = bands,
                    type = EqualizerPresetType.AUTO_EQ,
                    id = "AutoEQ: $pinnedName",
                    brand = profile?.brand
                )
            )
        }

        if (currentAutoEQProfile.isNotBlank() && currentAutoEQProfile != "None") {
            val activeKey = "AutoEQ: $currentAutoEQProfile"
            if (list.none { it.name == activeKey }) {
                val profile = autoEQProfiles.find { it.name.equals(currentAutoEQProfile, ignoreCase = true) }
                val bands = profile?.bands ?: List(10) { 0f }
                list.add(
                    EqualizerPreset(
                        name = activeKey,
                        icon = RhythmIcons.HeadphonesFilled,
                        bands = bands,
                        type = EqualizerPresetType.AUTO_EQ,
                        id = activeKey,
                        brand = profile?.brand
                    )
                )
            }
        }

        userDevices.forEach { device ->
            val profileName = device.autoEQProfileName
            if (!profileName.isNullOrBlank() && profileName != "None") {
                val deviceKey = "AutoEQ: $profileName"
                if (list.none { it.name == deviceKey }) {
                    val profile = autoEQProfiles.find { it.name.equals(profileName, ignoreCase = true) }
                    val bands = profile?.bands ?: List(10) { 0f }
                    list.add(
                        EqualizerPreset(
                            name = deviceKey,
                            icon = RhythmIcons.HeadphonesFilled,
                            bands = bands,
                            type = EqualizerPresetType.AUTO_EQ,
                            id = deviceKey,
                            brand = profile?.brand
                        )
                    )
                }
            }
        }

        // 2. Custom presets
        customPresets.forEach { custom ->
            list.add(
                EqualizerPreset(
                    name = custom.name,
                    icon = MaterialSymbolIcon("tune", filled = true),
                    bands = custom.bands,
                    type = EqualizerPresetType.CUSTOM,
                    id = custom.id
                )
            )
        }

        // 3. Built-in presets
        list.addAll(builtInPresets)

        list
    }

    val displayPresets = remember(allAvailablePresets, presetOrder, hiddenPresets, selectedPreset) {
        val isDefaultOrder = presetOrder == viewModel.appSettings.defaultEqualizerPresetOrder || presetOrder.isEmpty()
        val sorted = if (isDefaultOrder) {
            allAvailablePresets
        } else {
            allAvailablePresets.sortedBy { preset ->
                val idx = presetOrder.indexOf(preset.name)
                if (idx >= 0) {
                    idx
                } else if (preset.type == EqualizerPresetType.AUTO_EQ) {
                    -100 + allAvailablePresets.indexOf(preset)
                } else {
                    1000 + allAvailablePresets.indexOf(preset)
                }
            }
        }
        sorted.filter { preset ->
            preset.name !in hiddenPresets || preset.name == selectedPreset
        }
    }

    val frequencyLabels = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")

    // Functions
    fun applyPreset(preset: EqualizerPreset) {
        HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
        selectedPreset = preset.name
        bandLevels = preset.bands

        // Save to settings
        viewModel.appSettings.setEqualizerPreset(preset.name)
        viewModel.appSettings.setEqualizerBandLevels(preset.bands.joinToString(","))

        if (preset.type == EqualizerPresetType.AUTO_EQ) {
            val autoEQName = preset.name.removePrefix("AutoEQ: ")
            viewModel.appSettings.setAutoEQProfile(autoEQName)
        } else {
            viewModel.appSettings.setAutoEQProfile("")
        }

        // Apply to service
        viewModel.applyEqualizerPreset(preset.name, preset.bands)
    }

    fun updateBandLevel(band: Int, level: Float) {
        val newLevels = bandLevels.toMutableList()
        newLevels[band] = level
        bandLevels = newLevels
        selectedPreset = "Custom"

        // Save to settings
        viewModel.appSettings.setEqualizerBandLevels(newLevels.joinToString(","))
        viewModel.appSettings.setEqualizerPreset("Custom")
        viewModel.appSettings.setAutoEQProfile("")

        // Apply to service
        val levelShort = (level * 100).toInt().toShort()
        viewModel.setEqualizerBandLevel(band.toShort(), levelShort)
    }

    // Dialog and Sheet States
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }
    var showAutoEQSelector by remember { mutableStateOf(false) }
    var showDeviceConfigSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val isAutoEQPresetActive = remember(selectedPreset, currentAutoEQProfile, allAvailablePresets) {
        val activePreset = allAvailablePresets.find { it.name == selectedPreset }
        activePreset?.type == EqualizerPresetType.AUTO_EQ ||
        selectedPreset.startsWith("AutoEQ:", ignoreCase = true) ||
        (currentAutoEQProfile.isNotBlank() && currentAutoEQProfile != "None" && selectedPreset == currentAutoEQProfile)
    }

    val isManualCustom = remember(selectedPreset, allAvailablePresets) {
        selectedPreset == "Custom" || allAvailablePresets.none { it.name == selectedPreset }
    }

    // Screen entrance animation
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50)
        showContent = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "contentAlpha"
    )

    val contentOffset by animateFloatAsState(
        targetValue = if (showContent) 0f else 30f,
        animationSpec = tween(durationMillis = 450),
        label = "contentOffset"
    )

    CollapsibleHeaderScreen(
        title = if (isAutoEQPresetActive) "AutoEQ" else stringResource(R.string.player_chip_equalizer),
        showBackButton = true,
        onBackClick = { navController.popBackStack() },
        actions = {
            // More Options Button - styled symmetrically to match back button
            IconButton(
                onClick = {
                    HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                    showMenu = true
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RhythmIcons.More,
                        contentDescription = stringResource(R.string.content_desc_more_options),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // Dropdown Menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                val menuItems = listOf(
                    Triple(
                        stringResource(R.string.eq_reorder_presets),
                        MaterialSymbolIcon("sort", filled = true),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) to MaterialTheme.colorScheme.onSecondaryContainer
                    ) to {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        showMenu = false
                        showReorderSheet = true
                    },
                    Triple(
                        stringResource(R.string.equalizerscreen_autoeq_profiles),
                        MaterialSymbolIcon("headphones", filled = true),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) to MaterialTheme.colorScheme.onPrimaryContainer
                    ) to {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        showMenu = false
                        showAutoEQSelector = true
                    },
                    Triple(
                        stringResource(R.string.eq_manage_all_devices),
                        MaterialSymbolIcon("tune", filled = true),
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f) to MaterialTheme.colorScheme.onTertiaryContainer
                    ) to {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        showMenu = false
                        showDeviceConfigSheet = true
                    },
                    Triple(
                        stringResource(R.string.eq_system_equalizer),
                        RhythmIcons.Equalizer,
                        MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                    ) to {
                        HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                        showMenu = false
                        val activity = context as? Activity
                        viewModel.openSystemEqualizer(activity, MainActivity.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL_REQUEST)
                    }
                )

                val outerRadius = 16.dp
                val innerRadius = 4.dp
                val itemSpacing = 3.dp

                Column(
                    modifier = Modifier
                        .widthIn(min = 220.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    menuItems.forEachIndexed { index, (itemData, onClick) ->
                        val (title, icon, colors) = itemData
                        val (bgColor, tintColor) = colors

                        val itemShape = when {
                            menuItems.size == 1 -> RoundedCornerShape(outerRadius)
                            index == 0 -> RoundedCornerShape(
                                topStart = outerRadius, topEnd = outerRadius,
                                bottomStart = innerRadius, bottomEnd = innerRadius
                            )
                            index == menuItems.size - 1 -> RoundedCornerShape(
                                topStart = innerRadius, topEnd = innerRadius,
                                bottomStart = outerRadius, bottomEnd = outerRadius
                            )
                            else -> RoundedCornerShape(innerRadius)
                        }

                        Surface(
                            onClick = onClick,
                            shape = itemShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(28.dp),
                                    shape = CircleShape,
                                    color = bgColor
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = tintColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        headerContent = {
            // Equalizer Enable/Disable Card 
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isEqualizerEnabled)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(28.dp), 
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isTablet) 28.dp else 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp) 
                ) {
                    Icon(
                        imageVector = RhythmIcons.Equalizer,
                        contentDescription = null,
                        tint = if (isEqualizerEnabled && !isOffloadEnforced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(35.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when {
                                    isOffloadEnforced -> "Disabled (Lite Mode)"
                                    isEqualizerEnabled -> stringResource(R.string.common_active)
                                    else -> stringResource(R.string.common_disabled)
                                },
                                style = MaterialTheme.typography.titleLarge, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isOffloadEnforced) {
                            Text(
                                text = stringResource(R.string.replay_gain_disabled_battery),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (isAudioOffloadActive && !isEqualizerEnabled) {
                            Text(
                                text = stringResource(R.string.replay_gain_offload_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TunerAnimatedSwitch(
                        checked = if (isOffloadEnforced) false else isEqualizerEnabled,
                        onCheckedChange = { enabled ->
                            if (!isOffloadEnforced) {
                                isEqualizerEnabled = enabled
                                viewModel.setEqualizerEnabled(enabled)
                            }
                        },
                        enabled = !isOffloadEnforced
                    )
                }
            }
        }
    ) { modifier ->
        val lazyListState = rememberSaveable(
            saver = LazyListStateSaver
        ) {
            androidx.compose.foundation.lazy.LazyListState()
        }

        val presetRowState = rememberLazyListState()
        var presetToScrollTo by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(isManualCustom) {
            if (isManualCustom) {
                presetRowState.animateScrollToItem(0)
            }
        }

        LaunchedEffect(presetToScrollTo, displayPresets) {
            val target = presetToScrollTo ?: return@LaunchedEffect
            val targetIndex = displayPresets.indexOfFirst {
                it.name.equals(target, ignoreCase = true) ||
                it.name.equals("AutoEQ: $target", ignoreCase = true) ||
                it.name.removePrefix("AutoEQ: ").equals(target, ignoreCase = true)
            }
            if (targetIndex >= 0) {
                val scrollIndex = if (isManualCustom) targetIndex + 1 else targetIndex
                presetRowState.animateScrollToItem(scrollIndex)
                presetToScrollTo = null
            }
        }

        val presetsContent: @Composable () -> Unit = {
            LazyRow(
                state = presetRowState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                if (isManualCustom) {
                    item(key = "custom_preset") {
                        SmallTabAnimation(
                            index = -1,
                            selectedIndex = -1,
                            title = stringResource(R.string.eq_preset_type_custom),
                            selectedColor = MaterialTheme.colorScheme.primaryContainer,
                            onSelectedColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                showSavePresetDialog = true
                            },
                            onLongClick = { showReorderSheet = true },
                            content = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbolIcon("tune", filled = true),
                                        contentDescription = stringResource(R.string.eq_preset_type_custom),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.eq_preset_type_custom),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        )
                    }
                }

                itemsIndexed(displayPresets, key = { _, it -> "preset_${it.id}" }) { index, preset ->
                    val isSelected = selectedPreset == preset.name
                    val selectedIndex = displayPresets.indexOfFirst { it.name == selectedPreset }

                    val displayName = when (preset.type) {
                        EqualizerPresetType.BUILT_IN -> getLocalizedPresetName(preset.name)
                        EqualizerPresetType.AUTO_EQ -> preset.name.removePrefix("AutoEQ: ")
                        EqualizerPresetType.CUSTOM -> preset.name
                    }

                    SmallTabAnimation(
                        index = index,
                        selectedIndex = selectedIndex,
                        title = displayName,
                        selectedColor = MaterialTheme.colorScheme.primaryContainer,
                        onSelectedColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { applyPreset(preset) },
                        onLongClick = { showReorderSheet = true },
                        content = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = preset.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )

                                if (preset.type == EqualizerPresetType.CUSTOM) {
                                    Surface(
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(start = 2.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.eq_preset_type_custom),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                item(key = "reorder_presets") {
                    SmallTabAnimation(
                        index = -3,
                        selectedIndex = -2,
                        title = stringResource(R.string.eq_reorder),
                        selectedColor = MaterialTheme.colorScheme.secondaryContainer,
                        onSelectedColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        unselectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                            showReorderSheet = true
                        },
                        content = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbolIcon("sort", filled = true),
                                    contentDescription = stringResource(R.string.eq_reorder),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.eq_reorder),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    )
                }
            }
        }

        val frequencyBandsContent: @Composable () -> Unit = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = context.getString(R.string.frequency_bands),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val secondaryColor = MaterialTheme.colorScheme.secondary
                        val tertiaryColor = MaterialTheme.colorScheme.tertiary
                        val outlineColor = MaterialTheme.colorScheme.outline

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val bandWidth = width / bandLevels.size

                                drawLine(
                                    color = outlineColor.copy(alpha = 0.3f),
                                    start = Offset(0f, height / 2),
                                    end = Offset(width, height / 2),
                                    strokeWidth = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                )

                                val points = bandLevels.mapIndexed { index, level ->
                                    val x = (index + 0.5f) * bandWidth
                                    val normalizedLevel = (level + 15f) / 30f
                                    val y = height * (1f - normalizedLevel)
                                    Offset(x, y)
                                }

                                if (points.size > 1) {
                                    val filledPath = Path().apply {
                                        moveTo(0f, height / 2)
                                        lineTo(points[0].x, points[0].y)
                                        for (i in 1 until points.size) {
                                            val p0 = points[i - 1]
                                            val p1 = points[i]
                                            val controlX = (p0.x + p1.x) / 2
                                            quadraticTo(controlX, p0.y, p1.x, p1.y)
                                        }
                                        lineTo(width, height / 2)
                                        close()
                                    }

                                    drawPath(
                                        path = filledPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                primaryColor.copy(alpha = 0.3f),
                                                Color.Transparent
                                            )
                                        )
                                    )

                                    val curvePath = Path().apply {
                                        moveTo(points[0].x, points[0].y)
                                        for (i in 1 until points.size) {
                                            val p0 = points[i - 1]
                                            val p1 = points[i]
                                            val controlX = (p0.x + p1.x) / 2
                                            quadraticTo(controlX, p0.y, p1.x, p1.y)
                                        }
                                    }

                                    drawPath(
                                        path = curvePath,
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(secondaryColor, primaryColor, tertiaryColor)
                                        ),
                                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }

                                points.forEachIndexed { index, point ->
                                    val pointColor = when (index) {
                                        0, 1 -> secondaryColor
                                        2, 3, 4, 5, 6, 7 -> primaryColor
                                        else -> tertiaryColor
                                    }
                                    drawCircle(
                                        color = pointColor,
                                        radius = 5.dp.toPx(),
                                        center = point
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val secondaryColor = MaterialTheme.colorScheme.secondary
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val tertiaryColor = MaterialTheme.colorScheme.tertiary

                    bandLevels.forEachIndexed { index, level ->
                        val bandColor = when (index) {
                            0, 1 -> secondaryColor
                            2, 3, 4, 5, 6, 7 -> primaryColor
                            else -> tertiaryColor
                        }
                        val bandThumbColor = when (index) {
                            0, 1 -> MaterialTheme.colorScheme.onSecondary
                            2, 3, 4, 5, 6, 7 -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.onTertiary
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.width(48.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = frequencyLabels[index],
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            CookieHorizontalSlider(
                                value = level,
                                onValueChange = { roundedLevel ->
                                    updateBandLevel(index, roundedLevel)
                                },
                                valueRange = -15f..15f,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                activeTrackColor = bandColor,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                thumbColor = bandThumbColor
                            )

                            Text(
                                text = if (level > 0) "+${String.format(Locale.ROOT, "%.1f", level)}" else String.format(Locale.ROOT, "%.1f", level),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = bandColor,
                                modifier = Modifier.width(40.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        val audioEffectsContent: @Composable (Boolean) -> Unit = { stackInColumn ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = context.getString(R.string.audio_effects),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp)
                )

                val bassBoostCard: @Composable (Modifier) -> Unit = { cardModifier ->
                    ExpressiveEffectCard(
                        title = stringResource(R.string.settings_bass_boost),
                        icon = RhythmIcons.SpeakerFilled,
                        value = bassBoostStrength,
                        valueRange = 0f..1000f,
                        isEnabled = if (isOffloadEnforced) false else (isBassBoostEnabled && isBassBoostAvailableState),
                        isAvailable = if (isOffloadEnforced) false else isBassBoostAvailableState,
                        activeColor = MaterialTheme.colorScheme.secondary,
                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        onActiveContainerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onValueChange = { strength ->
                            if (!isOffloadEnforced) {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                bassBoostStrength = strength
                                viewModel.setBassBoost(true, strength.toInt().toShort())
                            }
                        },
                        onEnabledChange = { enabled ->
                            if (!isOffloadEnforced && isBassBoostAvailableState) {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                isBassBoostEnabled = enabled
                                viewModel.setBassBoost(enabled, bassBoostStrength.toInt().toShort())
                            }
                        },
                        statusText = when {
                            isOffloadEnforced -> "Disabled (Lite Mode)"
                            !isBassBoostAvailableState -> stringResource(R.string.common_unavailable)
                            isBassBoostEnabled -> stringResource(R.string.common_active)
                            isAudioOffloadActive -> "Enhance Lows\n(will disable offload)"
                            else -> stringResource(R.string.eq_enhance_lows)
                        },
                        modifier = cardModifier
                    )
                }

                val virtualizerCard: @Composable (Modifier) -> Unit = { cardModifier ->
                    ExpressiveEffectCard(
                        title = stringResource(R.string.virtualizer),
                        icon = RhythmIcons.HeadphonesFilled,
                        value = virtualizerStrength,
                        valueRange = 0f..1000f,
                        isEnabled = if (isOffloadEnforced) false else (isVirtualizerEnabled && isSpatializationAvailable),
                        isAvailable = if (isOffloadEnforced) false else isSpatializationAvailable,
                        activeColor = MaterialTheme.colorScheme.tertiary,
                        activeContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        onActiveContainerColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onValueChange = { strength ->
                            if (!isOffloadEnforced) {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.LIGHT)
                                virtualizerStrength = strength
                                viewModel.setVirtualizer(true, strength.toInt().toShort())
                            }
                        },
                        onEnabledChange = { enabled ->
                            if (!isOffloadEnforced && isSpatializationAvailable) {
                                HapticUtils.performHapticFeedback(context, haptics, HapticType.HEAVY)
                                isVirtualizerEnabled = enabled
                                viewModel.setVirtualizer(enabled, virtualizerStrength.toInt().toShort())
                            }
                        },
                        statusText = when {
                            isOffloadEnforced -> "Disabled (Lite Mode)"
                            !isSpatializationAvailable -> stringResource(R.string.eq_mono_only)
                            isVirtualizerEnabled -> stringResource(R.string.common_active)
                            isAudioOffloadActive -> "Widen Sound\n(will disable offload)"
                            else -> stringResource(R.string.eq_widen_sound)
                        },
                        modifier = cardModifier
                    )
                }

                if (stackInColumn) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        bassBoostCard(Modifier.fillMaxWidth())
                        virtualizerCard(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        bassBoostCard(Modifier.weight(1f))
                        virtualizerCard(Modifier.weight(1f))
                    }
                }
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset
                },
            contentPadding = PaddingValues(
                start = if (isTablet) 28.dp else 20.dp,
                end = if (isTablet) 28.dp else 20.dp,
                top = 16.dp,
                bottom = (LocalMiniPlayerPadding.current.calculateBottomPadding() + 24.dp).coerceAtLeast(120.dp)
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (isTablet) {
                item {
                    if (isEqualizerEnabled) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            presetsContent()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    audioEffectsContent(windowScreenWidthDp() < 900)
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    frequencyBandsContent()
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                audioEffectsContent(false)
                            }
                        }
                    }
                }
            } else {
                item {
                    AnimatedVisibility(
                        visible = isEqualizerEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        presetsContent()
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = isEqualizerEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            frequencyBandsContent()
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                item {
                    audioEffectsContent(false)
                }
            }
        }

        if (showSavePresetDialog) {
            SaveCustomPresetDialog(
                onDismiss = { showSavePresetDialog = false },
                onSave = { name ->
                    val newPreset = viewModel.saveCustomEqualizerPreset(name, bandLevels)
                    selectedPreset = newPreset.name
                    viewModel.appSettings.setEqualizerPreset(newPreset.name)
                    viewModel.appSettings.setEqualizerBandLevels(newPreset.bands.joinToString(","))
                    viewModel.applyEqualizerPreset(newPreset.name, newPreset.bands)
                    showSavePresetDialog = false
                    presetToScrollTo = newPreset.name
                    Toast.makeText(
                        context,
                        context.getString(R.string.eq_preset_saved_success, name),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                bandLevels = bandLevels,
                existingPresetNames = displayPresets.map { it.name }
            )
        }

        if (showReorderSheet) {
            EqualizerPresetOrderBottomSheet(
                onDismiss = { showReorderSheet = false },
                musicViewModel = viewModel
            )
        }

        if (showAutoEQSelector) {
            AutoEQPresetPickerBottomSheet(
                currentProfileName = currentAutoEQProfile,
                onDismissRequest = { showAutoEQSelector = false },
                onProfileSelected = { profile: AutoEQProfile ->
                    viewModel.applyAutoEQProfile(profile)
                    if (profile.name.isNotBlank() && !profile.name.equals("None", ignoreCase = true)) {
                        viewModel.pinAutoEQProfile(profile.name)
                        presetToScrollTo = "AutoEQ: ${profile.name}"
                    }
                    showAutoEQSelector = false
                }
            )
        }

        if (showDeviceConfigSheet) {
            DeviceConfigurationBottomSheet(
                onDismiss = { showDeviceConfigSheet = false },
                musicViewModel = viewModel
            )
        }
    }
}

@Composable
private fun ExpressiveEffectCard(
    title: String,
    icon: MaterialSymbolIcon,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    isEnabled: Boolean,
    isAvailable: Boolean,
    activeColor: Color,
    activeContainerColor: Color,
    onActiveContainerColor: Color,
    onValueChange: (Float) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    statusText: String,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isEnabled) activeContainerColor else MaterialTheme.colorScheme.surfaceContainer,
        label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isEnabled) onActiveContainerColor else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "contentColor"
    )

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                TunerAnimatedSwitch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange,
                    enabled = isAvailable,
                    modifier = Modifier.scale(0.85f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(110.dp)
            ) {
                ArcProgressSlider(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxSize(),
                    enabled = isEnabled,
                    valueRange = valueRange,
                    activeTrackColor = if (isEnabled) activeColor else MaterialTheme.colorScheme.outlineVariant,
                    inactiveTrackColor = if (isEnabled) activeColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                    thumbColor = if (isEnabled) activeColor else MaterialTheme.colorScheme.outline,
                    waveAmplitude = 2.dp
                )

                val percentage = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start) * 100).toInt()
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun getLocalizedPresetName(name: String): String {
    return when (name) {
        "Flat" -> stringResource(R.string.eq_preset_flat)
        "Rock" -> stringResource(R.string.eq_preset_rock)
        "Pop" -> stringResource(R.string.eq_preset_pop)
        "Jazz" -> stringResource(R.string.eq_preset_jazz)
        "Classical" -> stringResource(R.string.eq_preset_classical)
        "Electronic" -> stringResource(R.string.eq_preset_electronic)
        "Hip Hop" -> stringResource(R.string.eq_preset_hiphop)
        "Vocal" -> stringResource(R.string.eq_preset_vocal)
        "Bass Boost" -> stringResource(R.string.eq_preset_bass_boost)
        "Treble Boost" -> stringResource(R.string.eq_preset_treble_boost)
        "V-Shape" -> stringResource(R.string.eq_preset_v_shape)
        "Harman" -> stringResource(R.string.eq_preset_harman)
        "Custom" -> stringResource(R.string.eq_preset_custom)
        else -> name
    }
}