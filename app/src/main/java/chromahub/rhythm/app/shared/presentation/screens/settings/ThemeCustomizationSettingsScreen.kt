/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package chromahub.rhythm.app.shared.presentation.screens.settings

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.groupedBottomSheetItemShape



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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import chromahub.rhythm.app.ui.theme.getFontPreviewStyle
import chromahub.rhythm.app.ui.theme.getFontFamilyByName
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import chromahub.rhythm.app.utils.FontLoader
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
import chromahub.rhythm.app.ui.theme.ColorSchemeOption
import chromahub.rhythm.app.ui.theme.getPresetColorSchemeOptions

// Data classes and enums for theme customization

data class FontOption(
    val name: String,
    val displayName: String,
    val description: String
)

enum class ColorSource(val displayName: String, val description: String, val icon: MaterialSymbolIcon) {
    ALBUM_ART("Art Based", "Extract theme colors from the current album artwork", RhythmIcons.Image),
    MONET("System Colors", "Use Material You colors from your wallpaper", MaterialSymbolIcon("color_lens", filled = true)),
    CUSTOM("Custom Scheme", "Choose from predefined color schemes", RhythmIcons.Palette)
}

fun ColorSource.getDisplayName(context: Context): String {
    return when (this) {
        ColorSource.ALBUM_ART -> context.getString(R.string.color_source_album_art)
        ColorSource.MONET -> context.getString(R.string.color_source_system_colors)
        ColorSource.CUSTOM -> context.getString(R.string.color_source_custom_scheme)
    }
}

fun ColorSource.getDescription(context: Context): String {
    return when (this) {
        ColorSource.ALBUM_ART -> context.getString(R.string.color_source_album_art_desc)
        ColorSource.MONET -> context.getString(R.string.color_source_system_colors_desc)
        ColorSource.CUSTOM -> context.getString(R.string.color_source_custom_scheme_desc)
    }
}

enum class FontSource(val displayName: String, val description: String, val icon: MaterialSymbolIcon) {
    SYSTEM("System Font", "Use the device's default font", MaterialSymbolIcon("phone_android", filled = true)),
    CUSTOM("Custom Font", "Import and use a custom font file", MaterialSymbolIcon("text_fields", filled = true))
}

fun FontSource.getDisplayName(context: Context): String {
    return when (this) {
        FontSource.SYSTEM -> context.getString(R.string.font_source_system_font)
        FontSource.CUSTOM -> context.getString(R.string.font_source_custom_font)
    }
}

fun FontSource.getDescription(context: Context): String {
    return when (this) {
        FontSource.SYSTEM -> context.getString(R.string.font_source_system_font_desc)
        FontSource.CUSTOM -> context.getString(R.string.font_source_custom_font_desc)
    }
}









@Composable
fun ThemeCustomizationSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val haptic = LocalHapticFeedback.current

    // Theme states
    val useSystemTheme by appSettings.useSystemTheme.collectAsState()
    val darkMode by appSettings.darkMode.collectAsState()
    val amoledTheme by appSettings.amoledTheme.collectAsState()
    val useDynamicColors by appSettings.useDynamicColors.collectAsState()
    val customColorScheme by appSettings.customColorScheme.collectAsState()
    val colorSource by appSettings.colorSource.collectAsState()
    val extractedAlbumColors by appSettings.extractedAlbumColors.collectAsState()
    val useExactArtworkColors by appSettings.useExactArtworkColors.collectAsState()
    val floatingNavigationBar by appSettings.floatingNavigationBar.collectAsState()

    // Font states
    val fontSource by appSettings.fontSource.collectAsState()
    val customFontPath by appSettings.customFontPath.collectAsState()
    val customFontFamily by appSettings.customFontFamily.collectAsState()

    // Color source state - initialize based on saved setting
    var selectedColorSource by remember(colorSource) {
        mutableStateOf(
            when (colorSource) {
                "ALBUM_ART" -> ColorSource.ALBUM_ART
                "MONET" -> ColorSource.MONET
                "CUSTOM" -> ColorSource.CUSTOM
                else -> ColorSource.CUSTOM
            }
        )
    }

    // Font source state - initialize based on saved setting
    var selectedFontSource by remember(fontSource) {
        mutableStateOf(
            when (fontSource) {
                "CUSTOM" -> FontSource.CUSTOM
                "SYSTEM" -> FontSource.SYSTEM
                else -> FontSource.SYSTEM
            }
        )
    }

    // Font file picker launcher
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Copy font to internal storage
            val fontPath = FontLoader.copyFontToInternalStorage(context, it)
            if (fontPath != null) {
                // Validate that the font can be loaded
                val testFont = FontLoader.loadCustomFont(context, fontPath)
                if (testFont != null) {
                    // Save to settings
                    appSettings.setCustomFontPath(fontPath)
                    appSettings.setFontSource("CUSTOM")

                    // Extract and save font name
                    val fontName = FontLoader.getFontFileName(fontPath) ?: "Custom Font"
                    appSettings.setCustomFontFamily(fontName)

                    // Show success feedback
                    HapticUtils.performHapticFeedback(context, haptic, HapticType.HEAVY)
                    Toast.makeText(context, context.getString(R.string.theme_font_imported), Toast.LENGTH_SHORT).show()
                } else {
                    // Font file copied but can't be loaded
                    HapticUtils.performHapticFeedback(context, haptic, HapticFeedbackType.Reject)
                    Toast.makeText(context, context.getString(R.string.theme_font_invalid), Toast.LENGTH_SHORT).show()
                }
            } else {
                // Failed to copy font file
                HapticUtils.performHapticFeedback(context, haptic, HapticFeedbackType.Reject)
                Toast.makeText(context, context.getString(R.string.theme_font_import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Color schemes - dynamic Material 3 preset schemes
    val isCurrentDarkTheme = if (useSystemTheme) isSystemInDarkTheme() else darkMode
    val colorSchemes = remember(context, isCurrentDarkTheme) {
        getPresetColorSchemeOptions(context, isCurrentDarkTheme)
    }

    // Font options - matching bottomsheet
    val fontOptions = remember(context) {
        listOf(
            FontOption(
                name = "Geom",
                displayName = "Geom",
                description = context.getString(R.string.font_geom_desc)
            ),
            FontOption(
                name = "System",
                displayName = context.getString(R.string.font_system_title),
                description = context.getString(R.string.font_system_desc)
            ),
            FontOption(
                name = "Slate",
                displayName = "Slate",
                description = context.getString(R.string.font_slate_desc)
            ),
            FontOption(
                name = "Inter",
                displayName = "Inter",
                description = context.getString(R.string.font_inter_desc)
            ),
            FontOption(
                name = "JetBrains",
                displayName = "JetBrains Mono",
                description = context.getString(R.string.font_jetbrains_desc)
            ),
            FontOption(
                name = "Quicksand",
                displayName = "Quicksand",
                description = context.getString(R.string.font_quicksand_desc)
            )
        )
    }
    val currentFont by appSettings.customFont.collectAsState()

    // Dialog states
    var showColorSourceDialog by remember { mutableStateOf(false) }
    var showFontSourceDialog by remember { mutableStateOf(false) }
    var showFontSelectionDialog by remember { mutableStateOf(false) }
    var navigateToExpressiveShapes by remember { mutableStateOf(false) }
    
    // Restart dialog states
    var showRestartDialog by remember { mutableStateOf(false) }
    var restartDialogMessage by remember { mutableStateOf("") }
    
    // Festive theme states
    val festiveThemeEnabled by appSettings.festiveThemeEnabled.collectAsState()
    val festiveThemeAutoDetect by appSettings.festiveThemeAutoDetect.collectAsState()
    val festiveThemeType by appSettings.festiveThemeType.collectAsState()
    val festiveThemeIntensity by appSettings.festiveThemeIntensity.collectAsState()
    val festiveSnowflakeSize by appSettings.festiveSnowflakeSize.collectAsState()
    val festiveSnowflakeArea by appSettings.festiveSnowflakeArea.collectAsState()
    val festiveShowTopLights by appSettings.festiveShowTopLights.collectAsState()
    val festiveShowSideGarland by appSettings.festiveShowSideGarland.collectAsState()
    val festiveShowBottomSnow by appSettings.festiveShowBottomSnow.collectAsState()
    val festiveShowSnowfall by appSettings.festiveShowSnowfall.collectAsState()
    var showFestivalSelectionDialog by remember { mutableStateOf(false) }
    
    // Handle navigation to Expressive Shapes screen with proper animation
    AnimatedContent(
        targetState = navigateToExpressiveShapes,
        transitionSpec = {
            if (targetState) {
                // Slide in from right when navigating to Expressive Shapes
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = EaseOutCubic
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 350,
                        delayMillis = 50
                    )
                ) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = EaseOutCubic
                    )
                ) togetherWith
                slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = tween(
                        durationMillis = 350,
                        easing = EaseInCubic
                    )
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 250)
                ) + scaleOut(
                    targetScale = 0.95f,
                    animationSpec = tween(
                        durationMillis = 350,
                        easing = EaseInCubic
                    )
                )
            } else {
                // Slide in from left when going back to Theme
                slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = EaseOutCubic
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 350,
                        delayMillis = 50
                    )
                ) + scaleIn(
                    initialScale = 0.95f,
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = EaseOutCubic
                    )
                ) togetherWith
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(
                        durationMillis = 350,
                        easing = EaseInCubic
                    )
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 250)
                ) + scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(
                        durationMillis = 350,
                        easing = EaseInCubic
                    )
                )
            }
        },
        label = "theme_to_shapes_navigation",
        contentKey = { it }
    ) { isNavigatingToShapes ->
        if (isNavigatingToShapes) {
            ExpressiveShapesSettingsScreen(onBackClick = { navigateToExpressiveShapes = false })
        } else {
            CollapsibleHeaderScreen(
                title = context.getString(R.string.settings_theme),
                showBackButton = true,
                onBackClick = onBackClick
            ) { modifier ->
        val settingGroups = listOf(
            SettingGroup(
                title = context.getString(R.string.settings_display_mode),
                items = listOf(
                    // Display Mode Button Group
                    SettingItem(
                        RhythmIcons.Settings,
                        context.getString(R.string.settings_theme_mode),
                        context.getString(R.string.settings_theme_mode_desc),
                        onClick = {
                        }
                    ),
                    // AMOLED Theme - always in list, rendered conditionally via AnimatedVisibility
                    SettingItem(
                        RhythmIcons.DarkMode,
                        context.getString(R.string.settings_amoled_theme),
                        context.getString(R.string.settings_amoled_theme_desc),
                        toggleState = amoledTheme,
                        onToggleChange = { appSettings.setAmoledTheme(it) }
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.settings_color_customization),
                items = buildList {
                    add(
                        SettingItem(
                            RhythmIcons.Palette,
                            context.getString(R.string.settings_color_source),
                            when (selectedColorSource) {
                                ColorSource.ALBUM_ART -> context.getString(R.string.settings_color_source_album)
                                ColorSource.MONET -> context.getString(R.string.settings_color_source_monet)
                                ColorSource.CUSTOM -> context.getString(R.string.settings_color_source_custom, customColorScheme)
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                showColorSourceDialog = true
                            }
                        )
                    )
                    if (selectedColorSource == ColorSource.ALBUM_ART) {
                        add(
                            SettingItem(
                                RhythmIcons.Palette,
                                "Use Exact Artwork Colors",
                                "Use exact background and text colors from artwork",
                                toggleState = useExactArtworkColors,
                                onToggleChange = { appSettings.setUseExactArtworkColors(it) }
                            )
                        )
                    }
                }
            ),
            SettingGroup(
                title = context.getString(R.string.settings_font_customization),
                items = listOf(
                    SettingItem(
                        MaterialSymbolIcon("text_fields"),
                        context.getString(R.string.settings_font_source),
                        when (selectedFontSource) {
                            FontSource.SYSTEM -> context.getString(R.string.settings_font_source_system, currentFont)
                            FontSource.CUSTOM -> context.getString(
                                R.string.settings_font_source_custom,
                                customFontFamily
                            )
                        },
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            showFontSourceDialog = true
                        }
                    ),
                    SettingItem(
                        MaterialSymbolIcon("text_fields"),
                        context.getString(R.string.settings_font_selection),
                        if (selectedFontSource == FontSource.SYSTEM)
                            context.getString(R.string.settings_font_selection_desc)
                        else
                            context.getString(R.string.settings_system_font_only),
                        onClick = {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            showFontSelectionDialog = true
                        }
                    ),
                    SettingItem(
                        MaterialSymbolIcon("file_upload"),
                        context.getString(R.string.settings_import_custom_font),
                        if (customFontPath != null)
                            context.getString(R.string.settings_font_imported_name, customFontFamily)
                        else
                            context.getString(R.string.settings_import_font_desc),
                        onClick = {
                            HapticUtils.performHapticFeedback(
                                context,
                                haptic,
                                HapticType.HEAVY
                            )
                            fontPickerLauncher.launch("font/*")
                        }
                    )
                )
            ),

            SettingGroup(
                title = context.getString(R.string.settings_floating_navigation),
                items = listOf(
                    SettingItem(
                        MaterialSymbolIcon("dock_to_left"),
                        context.getString(R.string.settings_floating_navigation),
                        context.getString(R.string.settings_floating_navigation_desc),
                        toggleState = floatingNavigationBar,
                        onToggleChange = { appSettings.setFloatingNavigationBar(it) }
                    )
                )
            ),

            SettingGroup(
                title = context.getString(R.string.settings_festive_themes),
                items = buildList {
                    add(
                        SettingItem(
                            MaterialSymbolIcon("celebration"),
                            context.getString(R.string.settings_enable_festive),
                            context.getString(R.string.settings_enable_festive_desc),
                            toggleState = festiveThemeEnabled,
                            onToggleChange = { appSettings.setFestiveThemeEnabled(it) }
                        )
                    )
                    if (festiveThemeEnabled) {
                        add(
                            SettingItem(
                                MaterialSymbolIcon("event_available"),
                                context.getString(R.string.settings_auto_detect_holidays),
                                context.getString(R.string.settings_auto_detect_holidays_desc),
                                toggleState = festiveThemeAutoDetect,
                                onToggleChange = { appSettings.setFestiveThemeAutoDetect(it) }
                            )
                        )
                        if (!festiveThemeAutoDetect) {
                            add(
                                SettingItem(
                                    RhythmIcons.AutoAwesome,
                                    context.getString(R.string.settings_select_festival),
                                    getFestivalDisplayName(festiveThemeType),
                                    onClick = {
                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                        showFestivalSelectionDialog = true
                                    }
                                )
                            )
                        }
                    }
                }
            )
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp)
        ) {
            items(settingGroups, key = { "setting_${it.title}_${settingGroups.indexOf(it)}" }) { group ->
                Spacer(modifier = Modifier.height(24.dp))

                val materialItems = when (group.title) {
                    context.getString(R.string.settings_display_mode) -> {
                        buildList {
                            add(
                                Material3SettingsItem(
                                    icon = RhythmIcons.Settings,
                                    title = { Text(context.getString(R.string.settings_theme_mode)) },
                                    description = {
                                        Column {
                                            Text(context.getString(R.string.settings_theme_mode_desc))
                                            Spacer(modifier = Modifier.height(12.dp))
                                            ExpressiveButtonGroup(
                                                items = listOf(
                                                    context.getString(R.string.settings_theme_system),
                                                    context.getString(R.string.settings_theme_light),
                                                    context.getString(R.string.settings_theme_dark)
                                                ),
                                                selectedIndex = when {
                                                    useSystemTheme -> 0
                                                    !darkMode -> 1
                                                    else -> 2
                                                },
                                                onItemClick = { index ->
                                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                    when (index) {
                                                        0 -> {
                                                            appSettings.setUseSystemTheme(true)
                                                        }

                                                        1 -> {
                                                            appSettings.setUseSystemTheme(false)
                                                            appSettings.setDarkMode(false)
                                                        }

                                                        2 -> {
                                                            appSettings.setUseSystemTheme(false)
                                                            appSettings.setDarkMode(true)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                )
                            )

                            if (!useSystemTheme && darkMode && group.items.size > 1) {
                                add(
                                    toMaterial3SettingsItem(
                                        context = context,
                                        item = group.items[1],
                                        hapticFeedback = haptic
                                    )
                                )
                            }
                        }
                    }

                    context.getString(R.string.settings_color_customization) -> {
                        buildList {
                            add(
                                toMaterial3SettingsItem(
                                    context = context,
                                    item = group.items[0],
                                    hapticFeedback = haptic
                                )
                            )
                            // Inline color scheme picker
                            add(
                                Material3SettingsItem(
                                    icon = MaterialSymbolIcon("color_lens"),
                                    title = { Text(context.getString(R.string.settings_color_schemes)) },
                                    description = {
                                        Column {
                                            if (selectedColorSource == ColorSource.CUSTOM) {
                                                Text(
                                                    text = context.getString(R.string.settings_color_schemes_desc),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(bottom = 12.dp)
                                                )
                                                ColorSchemePaletteRow(
                                                    schemes = colorSchemes,
                                                    currentScheme = customColorScheme,
                                                    onSchemeSelected = { scheme ->
                                                        HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                                        appSettings.setCustomColorScheme(scheme)
                                                    }
                                                )
                                            } else {
                                                Text(
                                                    text = context.getString(R.string.settings_custom_only),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                )
                            )
                            group.items.drop(1).forEach { item ->
                                add(
                                    toMaterial3SettingsItem(
                                        context = context,
                                        item = item,
                                        hapticFeedback = haptic
                                    )
                                )
                            }
                        }
                    }

                    else -> {
                        group.items.map { item ->
                            toMaterial3SettingsItem(context = context, item = item, hapticFeedback = haptic)
                        }
                    }
                }

                Material3SettingsGroup(
                    title = group.title,
                    items = materialItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Tips Card
            item {
                Spacer(modifier = Modifier.height(24.dp))
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
                                text = context.getString(R.string.theme_good_to_know),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        ThemeTipItem(
                            icon = RhythmIcons.Palette,
                            text = context.getString(R.string.theme_tip_album_art)
                        )
                        ThemeTipItem(
                            icon = MaterialSymbolIcon("wallpaper", filled = true),
                            text = context.getString(R.string.theme_tip_material_you)
                        )
                        ThemeTipItem(
                            icon = MaterialSymbolIcon("font_download", filled = true),
                            text = context.getString(R.string.theme_tip_custom_fonts)
                        )
                    }
                }
            }
        }
        }  // End of CollapsibleHeaderScreen else block
        }  // End of AnimatedContent
    }

    // App Restart Dialog for theme changes
    if (showRestartDialog) {
        AppRestartDialog(
            onDismiss = { showRestartDialog = false },
            onRestart = {
                showRestartDialog = false
                chromahub.rhythm.app.util.AppRestarter.restartApp(context)
            },
            onContinue = {
                showRestartDialog = false
                // Continue without restart
            },
            message = restartDialogMessage
        )
    }

    // Dialogs
    ColorSourceDialog(
        showDialog = showColorSourceDialog,
        onDismiss = { showColorSourceDialog = false },
        selectedColorSource = selectedColorSource,
        onColorSourceSelected = { selectedColorSource = it },
        appSettings = appSettings,
        context = context,
        haptic = haptic
    )

    FontSourceDialog(
        showDialog = showFontSourceDialog,
        onDismiss = { showFontSourceDialog = false },
        selectedFontSource = selectedFontSource,
        onFontSourceSelected = { selectedFontSource = it },
        appSettings = appSettings,
        customFontPath = customFontPath,
        context = context,
        haptic = haptic,
        onShowRestartDialog = { message ->
            showRestartDialog = true
            restartDialogMessage = message
        }
    )

    FontSelectionBottomSheet(
        showDialog = showFontSelectionDialog,
        onDismiss = { showFontSelectionDialog = false },
        fontOptions = fontOptions,
        currentFont = currentFont,
        selectedFontSource = selectedFontSource,
        onFontSelected = { selectedFont ->
            appSettings.setCustomFont(selectedFont)
            showFontSelectionDialog = false
            showRestartDialog = true
            restartDialogMessage = "Font changed. Restart the app to apply the new font."
        },
        appSettings = appSettings,
        context = context,
        haptic = haptic
    )

    // Festival Selection Dialog with Intensity Controls
    if (showFestivalSelectionDialog) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        
        RhythmAdaptiveModalSheet(
            adaptiveType = SheetAdaptiveType.AUTO_DIALOG,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            onDismissRequest = { showFestivalSelectionDialog = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            StandardBottomSheetHeader(
                title = context.getString(R.string.theme_festive_settings),
                subtitle = context.getString(R.string.settings_choose_festive_theme),
                visible = true
            )

            val festivalListState = rememberLazyListState()

            AdaptiveSheetScrollContainer(
                lazyListState = festivalListState,
                modifier = Modifier.fillMaxWidth()
            ) { endPadding ->
                LazyColumn(
                    state = festivalListState,
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp + endPadding,
                        bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = context.getString(R.string.settings_select_festival),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            val festivals = listOf(
                                "CHRISTMAS" to context.getString(R.string.settings_festival_christmas),
                                "NEW_YEAR" to context.getString(R.string.settings_festival_new_year)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                festivals.forEachIndexed { index, (id, name) ->
                                    val isSelected = id == festiveThemeType
                                    Card(
                                        onClick = {
                                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                            appSettings.setFestiveThemeType(id)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = groupedBottomSheetItemShape(index, festivals.size),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected)
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            else
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = RhythmIcons.CheckCircle,
                                                    contentDescription = context.getString(R.string.ui_selected),
                                                    tint = MaterialTheme.colorScheme.primaryContainer,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = context.getString(R.string.settings_decoration_intensity),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = context.getString(R.string.settings_intensity),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${(festiveThemeIntensity * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Slider(
                                value = festiveThemeIntensity,
                                onValueChange = { appSettings.setFestiveThemeIntensity(it) },
                                valueRange = 0.1f..1f,
                                modifier = Modifier.fillMaxWidth(),
                                onValueChangeFinished = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = context.getString(R.string.settings_snowflake_size),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${(festiveSnowflakeSize * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Slider(
                                value = festiveSnowflakeSize,
                                onValueChange = { appSettings.setFestiveSnowflakeSize(it) },
                                valueRange = 0.5f..2.0f,
                                modifier = Modifier.fillMaxWidth(),
                                onValueChangeFinished = {
                                    HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = context.getString(R.string.settings_snowflake_display_area),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = festiveSnowflakeArea == "FULL_SCREEN",
                                    onClick = { appSettings.setFestiveSnowflakeArea("FULL_SCREEN") },
                                    label = { Text(context.getString(R.string.settings_area_full)) },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = festiveSnowflakeArea == "HEADER_ONLY",
                                    onClick = { appSettings.setFestiveSnowflakeArea("HEADER_ONLY") },
                                    label = { Text(context.getString(R.string.settings_area_top_third)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = context.getString(R.string.settings_decoration_elements),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DecorationToggleCard(
                                    title = context.getString(R.string.settings_snowfall),
                                    description = context.getString(R.string.settings_snowfall_desc),
                                    icon = MaterialSymbolIcon("ac_unit", filled = true),
                                    isEnabled = festiveShowSnowfall,
                                    onToggle = { appSettings.setFestiveShowSnowfall(it) }
                                )
                                DecorationToggleCard(
                                    title = context.getString(R.string.settings_top_lights),
                                    description = context.getString(R.string.settings_top_lights_desc),
                                    icon = MaterialSymbolIcon("lightbulb", filled = true),
                                    isEnabled = festiveShowTopLights,
                                    onToggle = { appSettings.setFestiveShowTopLights(it) }
                                )
                                DecorationToggleCard(
                                    title = context.getString(R.string.settings_side_garland),
                                    description = context.getString(R.string.settings_side_garland_desc),
                                    icon = MaterialSymbolIcon("park", filled = true),
                                    isEnabled = festiveShowSideGarland,
                                    onToggle = { appSettings.setFestiveShowSideGarland(it) }
                                )
                                DecorationToggleCard(
                                    title = context.getString(R.string.settings_snow_pile),
                                    description = context.getString(R.string.settings_snow_pile_desc),
                                    icon = MaterialSymbolIcon("terrain", filled = true),
                                    isEnabled = festiveShowBottomSnow,
                                    onToggle = { appSettings.setFestiveShowBottomSnow(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ColorSchemePaletteRow(
    schemes: List<ColorSchemeOption>,
    currentScheme: String,
    onSchemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(schemes, key = { it.name }) { option ->
            ColorSchemeCircle(
                option = option,
                isSelected = currentScheme == option.name,
                onClick = { onSchemeSelected(option.name) }
            )
        }
    }
}

@Composable
fun ColorSchemeCircle(
    option: ColorSchemeOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clickScale = remember { Animatable(1f) }

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "colorSchemeScale"
    )

    val ringAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "colorSchemeRingAlpha"
    )

    val cornerSize by animateDpAsState(
        targetValue = if (isSelected) 24.dp else 35.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "colorSchemeCorner"
    )

    val ringCornerSize by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 35.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "colorSchemeRingCorner"
    )

    val circleCorner by animateDpAsState(
        targetValue = if (isSelected) 14.dp else 24.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "colorSchemeCircleCorner"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = {
                    scope.launch {
                        clickScale.animateTo(0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        clickScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                    onClick()
                }
            )
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(70.dp)
                .graphicsLayer {
                    scaleX = clickScale.value
                    scaleY = clickScale.value
                }
                .clip(RoundedCornerShape(cornerSize))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (ringAlpha > 0.01f) {
                    val stroke = 2.5.dp.toPx()
                    val ringPad = 5.dp.toPx()
                    val ringSize = size.width - 2 * ringPad
                    val ringCorner = ringCornerSize.toPx()
                    drawRoundRect(
                        color = option.primaryColor.copy(alpha = ringAlpha),
                        style = Stroke(width = stroke),
                        cornerRadius = CornerRadius(ringCorner, ringCorner),
                        topLeft = Offset(ringPad, ringPad),
                        size = Size(ringSize, ringSize)
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                val cr = circleCorner.toPx()
                val w = size.width
                val h = size.height
                val halfW = w / 2f
                val halfH = h / 2f
                val topPath = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, w, halfH, CornerRadius(cr), CornerRadius(cr), CornerRadius.Zero, CornerRadius.Zero))
                }
                drawPath(topPath, color = option.primaryColor)
                val blPath = Path().apply {
                    addRoundRect(RoundRect(0f, halfH, halfW, h, CornerRadius.Zero, CornerRadius.Zero, CornerRadius.Zero, CornerRadius(cr)))
                }
                drawPath(blPath, color = option.secondaryColor)
                val brPath = Path().apply {
                    addRoundRect(RoundRect(halfW, halfH, w, h, CornerRadius.Zero, CornerRadius.Zero, CornerRadius(cr), CornerRadius.Zero))
                }
                drawPath(brPath, color = option.tertiaryColor)
            }

            if (isSelected) {
                Icon(
                    imageVector = RhythmIcons.Check,
                    contentDescription = stringResource(R.string.streaming_selected),
                    tint = if (option.primaryColor.luminance() > 0.5f) {
                        Color.Black.copy(alpha = 0.72f)
                    } else {
                        Color.White
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            alpha = ringAlpha
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = option.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}








@Composable
fun FontCard(
    option: FontOption,
    isSelected: Boolean,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isSelected) {
                    Icon(
                        imageVector = RhythmIcons.CheckCircle,
                        contentDescription = stringResource(R.string.streaming_selected),
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font preview text
            Surface(
                color = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(12.dp),
                border = if (!isSelected)
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                else
                    null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.settings_quick_brown_fox),
                    fontFamily = getFontFamilyByName(option.name),
                    style = getFontPreviewStyle(option.name),
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}



@Composable
fun ThemeTipItem(
    icon: MaterialSymbolIcon,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}