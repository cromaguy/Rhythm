/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.settings

import chromahub.rhythm.app.shared.presentation.components.bottomsheets.AdaptiveSheetScrollContainer
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.RhythmAdaptiveModalSheet
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.SheetAdaptiveType
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.StandardBottomSheetHeader
import chromahub.rhythm.app.shared.presentation.components.bottomsheets.groupedBottomSheetItemShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import androidx.compose.foundation.layout.PaddingValues
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.infrastructure.widget.glance.GlanceWidgetUpdater
import chromahub.rhythm.app.infrastructure.widget.glance.RhythmWidgetReceiver
import chromahub.rhythm.app.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    onBackClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    
    // Collect widget settings
    val showArtist by appSettings.widgetShowArtist.collectAsState()
    val showAlbum by appSettings.widgetShowAlbum.collectAsState()
    val showFavoriteButton by appSettings.widgetShowFavoriteButton.collectAsState()
    val cornerRadius by appSettings.widgetCornerRadius.collectAsState()
    val widgetTheme by appSettings.widgetTheme.collectAsState()
    val cookieBottomLeft by appSettings.widgetCookieBottomLeft.collectAsState()
    val cookieBottomRight by appSettings.widgetCookieBottomRight.collectAsState()
    val statsRange by appSettings.widgetStatsRange.collectAsState()
    val statsGem by appSettings.widgetStatsGem.collectAsState()
    
    var showCornerRadiusSheet by remember { mutableStateOf(false) }
    var showWidgetThemeSheet by remember { mutableStateOf(false) }
    var showCookieLeftSheet by remember { mutableStateOf(false) }
    var showCookieRightSheet by remember { mutableStateOf(false) }
    var showStatsRangeSheet by remember { mutableStateOf(false) }
    var showStatsGemSheet by remember { mutableStateOf(false) }

    fun buildToggleSettingsItem(
        icon: MaterialSymbolIcon,
        title: String,
        description: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ): Material3SettingsItem {
        return Material3SettingsItem(
            icon = icon,
            title = { Text(title) },
            description = { Text(description) },
            trailingContent = {
                TunerAnimatedSwitch(
                    checked = checked,
                    onCheckedChange = {
                        onToggle(it)
                    }
                )
            },
            onClick = {
                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                onToggle(!checked)
            }
        )
    }
    
    CollapsibleHeaderScreen(
        title = stringResource(R.string.widgetsettingsscreen_widget_settings),
        showBackButton = true,
        onBackClick = {
            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
            onBackClick()
        }
    ) { modifier ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp)
        ) {

            
            // Display Options
            item {
                Spacer(modifier = Modifier.height(24.dp))
                val displayItems = listOf(
                    buildToggleSettingsItem(
                        icon = RhythmIcons.Artist,
                        title = stringResource(R.string.onboarding_widget_artist),
                        description = stringResource(R.string.widget_show_artist_desc),
                        checked = showArtist,
                        onToggle = {
                            appSettings.setWidgetShowArtist(it)
                            updateAllWidgets(context)
                        }
                    ),
                    buildToggleSettingsItem(
                        icon = RhythmIcons.Album,
                        title = stringResource(R.string.onboarding_widget_album),
                        description = stringResource(R.string.widget_show_album_desc),
                        checked = showAlbum,
                        onToggle = {
                            appSettings.setWidgetShowAlbum(it)
                            updateAllWidgets(context)
                        }
                    ),
                    buildToggleSettingsItem(
                        icon = RhythmIcons.FavoriteFilled,
                        title = stringResource(R.string.widgetsettingsscreen_show_favorite_button),
                        description = stringResource(R.string.widget_show_favorite_button_desc),
                        checked = showFavoriteButton,
                        onToggle = {
                            appSettings.setWidgetShowFavoriteButton(it)
                            updateAllWidgets(context)
                        }
                    )
                )

                Material3SettingsGroup(
                    title = stringResource(R.string.settings_display_options),
                    items = displayItems,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }
            
            // Appearance Options
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Material3SettingsGroup(
                    title = stringResource(R.string.widget_appearance),
                    items = listOf(
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("rounded_corner"),
                            title = { Text(stringResource(R.string.settings_miniplayer_corner_radius)) },
                            description = { Text(stringResource(R.string.widget_settings_radius_desc, cornerRadius)) },
                            trailingContent = {
                                Icon(
                                    imageVector = RhythmIcons.Forward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                showCornerRadiusSheet = true
                            }
                        ),
                        Material3SettingsItem(
                            icon = MaterialSymbolIcon("palette"),
                            title = { Text(stringResource(R.string.widgetsettingsscreen_widget_theme)) },
                            description = {
                                val themeName = when (widgetTheme) {
                                    1 -> stringResource(R.string.widget_theme_solid_dark)
                                    2 -> stringResource(R.string.widget_theme_translucent_dark)
                                    3 -> stringResource(R.string.widget_theme_solid_purple)
                                    else -> stringResource(R.string.widget_theme_dynamic)
                                }
                                Text(stringResource(R.string.widget_theme_glance_suffix, themeName))
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = RhythmIcons.Forward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                showWidgetThemeSheet = true
                            }
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }
            
            // Rhythm Cookie Widget — corner action customization
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Material3SettingsGroup(
                    title = stringResource(R.string.widget_cookie_section_title),
                    items = listOf(
                        Material3SettingsItem(
                            icon = cookieActionIcon(cookieBottomLeft, isLeft = true),
                            title = { Text(stringResource(R.string.widget_cookie_bottom_left)) },
                            description = { Text(cookieActionLabel(cookieBottomLeft)) },
                            trailingContent = {
                                Icon(
                                    imageVector = RhythmIcons.Forward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                showCookieLeftSheet = true
                            }
                        ),
                        Material3SettingsItem(
                            icon = cookieActionIcon(cookieBottomRight, isLeft = false),
                            title = { Text(stringResource(R.string.widget_cookie_bottom_right)) },
                            description = { Text(cookieActionLabel(cookieBottomRight)) },
                            trailingContent = {
                                Icon(
                                    imageVector = RhythmIcons.Forward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                showCookieRightSheet = true
                            }
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            }

            // Rhythm Stats Widget — hero range + gem content customization
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Material3SettingsGroup(
                    title = stringResource(R.string.widget_stats_section_title),
                    items = listOf(
                        Material3SettingsItem(
                            icon = statsRangeIcon(statsRange),
                            title = { Text(stringResource(R.string.widget_stats_time_range)) },
                            description = { Text(statsRangeLabel(statsRange)) },
                            trailingContent = {
                                Icon(
                                    imageVector = RhythmIcons.Forward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                showStatsRangeSheet = true
                            }
                        ),
                        Material3SettingsItem(
                            icon = statsGemIcon(statsGem),
                            title = { Text(stringResource(R.string.widget_stats_gem)) },
                            description = { Text(statsGemLabel(statsGem)) },
                            trailingContent = {
                                Icon(
                                    imageVector = RhythmIcons.Forward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                                showStatsGemSheet = true
                            }
                        )
                    ),
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
                                text = stringResource(R.string.onboarding_widgets_tips_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        WidgetTipItem(
                            icon = MaterialSymbolIcon("cookie"),
                            text = stringResource(R.string.widget_tip_cookie_corners)
                        )
                        WidgetTipItem(
                            icon = MaterialSymbolIcon("auto_graph"),
                            text = stringResource(R.string.widget_tip_stats_widget)
                        )
                        WidgetTipItem(
                            icon = MaterialSymbolIcon("touch_app"),
                            text = stringResource(R.string.widget_tip_controls)
                        )
                        WidgetTipItem(
                            icon = MaterialSymbolIcon("grid_on"),
                            text = stringResource(R.string.widget_tip_resize_settings)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        
        // Corner Radius Slider Sheet
        if (showCornerRadiusSheet) {
            WidgetCornerRadiusSheet(
                currentRadius = cornerRadius,
                onDismiss = { showCornerRadiusSheet = false },
                appSettings = appSettings
            )
        }
        
        // Widget Theme Selection Sheet
        if (showWidgetThemeSheet) {
            WidgetThemeSheet(
                currentTheme = widgetTheme,
                onDismiss = { showWidgetThemeSheet = false },
                appSettings = appSettings
            )
        }
        
        // Rhythm Cookie — Bottom Left Action Sheet
        if (showCookieLeftSheet) {
            ActionPickerSheet(
                title = stringResource(R.string.widget_cookie_bottom_left),
                selectedValue = cookieBottomLeft,
                options = listOf(
                    PickerOption(0, stringResource(R.string.widget_cookie_action_skip), stringResource(R.string.widget_cookie_action_skip_left_desc), MaterialSymbolIcon("skip_previous")),
                    PickerOption(1, stringResource(R.string.widget_cookie_action_shuffle), stringResource(R.string.widget_cookie_action_shuffle_desc), MaterialSymbolIcon("shuffle")),
                    PickerOption(2, stringResource(R.string.widget_cookie_action_repeat), stringResource(R.string.widget_cookie_action_repeat_desc), MaterialSymbolIcon("repeat")),
                    PickerOption(3, stringResource(R.string.widget_cookie_action_favorite), stringResource(R.string.widget_cookie_action_favorite_desc), MaterialSymbolIcon("favorite")),
                    PickerOption(4, stringResource(R.string.widget_cookie_action_none), stringResource(R.string.widget_cookie_action_none_desc), MaterialSymbolIcon("block"))
                ),
                onDismiss = { showCookieLeftSheet = false },
                onSelect = { value ->
                    appSettings.setWidgetCookieBottomLeft(value)
                    updateAllWidgets(context)
                    showCookieLeftSheet = false
                }
            )
        }
        
        // Rhythm Cookie — Bottom Right Action Sheet
        if (showCookieRightSheet) {
            ActionPickerSheet(
                title = stringResource(R.string.widget_cookie_bottom_right),
                selectedValue = cookieBottomRight,
                options = listOf(
                    PickerOption(0, stringResource(R.string.widget_cookie_action_skip), stringResource(R.string.widget_cookie_action_skip_right_desc), MaterialSymbolIcon("skip_next")),
                    PickerOption(1, stringResource(R.string.widget_cookie_action_shuffle), stringResource(R.string.widget_cookie_action_shuffle_desc), MaterialSymbolIcon("shuffle")),
                    PickerOption(2, stringResource(R.string.widget_cookie_action_repeat), stringResource(R.string.widget_cookie_action_repeat_desc), MaterialSymbolIcon("repeat")),
                    PickerOption(3, stringResource(R.string.widget_cookie_action_favorite), stringResource(R.string.widget_cookie_action_favorite_desc), MaterialSymbolIcon("favorite")),
                    PickerOption(4, stringResource(R.string.widget_cookie_action_none), stringResource(R.string.widget_cookie_action_none_desc), MaterialSymbolIcon("block"))
                ),
                onDismiss = { showCookieRightSheet = false },
                onSelect = { value ->
                    appSettings.setWidgetCookieBottomRight(value)
                    updateAllWidgets(context)
                    showCookieRightSheet = false
                }
            )
        }
        
        // Rhythm Stats — Time Range Sheet
        if (showStatsRangeSheet) {
            ActionPickerSheet(
                title = stringResource(R.string.widget_stats_time_range),
                selectedValue = statsRange,
                options = listOf(
                    PickerOption(0, stringResource(R.string.widget_stats_range_all_time), stringResource(R.string.widget_stats_range_all_time_desc), MaterialSymbolIcon("all_inclusive")),
                    PickerOption(1, stringResource(R.string.widget_stats_range_today), stringResource(R.string.widget_stats_range_today_desc), MaterialSymbolIcon("today")),
                    PickerOption(2, stringResource(R.string.widget_stats_range_week), stringResource(R.string.widget_stats_range_week_desc), MaterialSymbolIcon("date_range")),
                    PickerOption(3, stringResource(R.string.widget_stats_range_month), stringResource(R.string.widget_stats_range_month_desc), MaterialSymbolIcon("calendar_month"))
                ),
                onDismiss = { showStatsRangeSheet = false },
                onSelect = { value ->
                    appSettings.setWidgetStatsRange(value)
                    updateAllWidgets(context)
                    showStatsRangeSheet = false
                }
            )
        }
        
        // Rhythm Stats — Gem Content Sheet
        if (showStatsGemSheet) {
            ActionPickerSheet(
                title = stringResource(R.string.widget_stats_gem),
                selectedValue = statsGem,
                options = listOf(
                    PickerOption(0, stringResource(R.string.widget_stats_gem_longest_streak), stringResource(R.string.widget_stats_gem_longest_streak_desc), MaterialSymbolIcon("workspace_premium")),
                    PickerOption(1, stringResource(R.string.widget_stats_gem_current_streak), stringResource(R.string.widget_stats_gem_current_streak_desc), MaterialSymbolIcon("local_fire_department")),
                    PickerOption(2, stringResource(R.string.widget_stats_gem_active_days), stringResource(R.string.widget_stats_gem_active_days_desc), MaterialSymbolIcon("event_available")),
                    PickerOption(3, stringResource(R.string.widget_stats_gem_sessions), stringResource(R.string.widget_stats_gem_sessions_desc), MaterialSymbolIcon("history"))
                ),
                onDismiss = { showStatsGemSheet = false },
                onSelect = { value ->
                    appSettings.setWidgetStatsGem(value)
                    updateAllWidgets(context)
                    showStatsGemSheet = false
                }
            )
        }
    }
}

@Composable
fun cookieActionLabel(value: Int): String {
    return when (value) {
        1 -> stringResource(R.string.widget_cookie_action_shuffle)
        2 -> stringResource(R.string.widget_cookie_action_repeat)
        3 -> stringResource(R.string.widget_cookie_action_favorite)
        4 -> stringResource(R.string.widget_cookie_action_none)
        else -> stringResource(R.string.widget_cookie_action_skip)
    }
}

fun cookieActionIcon(value: Int, isLeft: Boolean): MaterialSymbolIcon {
    return when (value) {
        1 -> MaterialSymbolIcon("shuffle")
        2 -> MaterialSymbolIcon("repeat")
        3 -> MaterialSymbolIcon("favorite")
        4 -> MaterialSymbolIcon("block")
        else -> if (isLeft) MaterialSymbolIcon("skip_previous") else MaterialSymbolIcon("skip_next")
    }
}

fun statsRangeIcon(value: Int): MaterialSymbolIcon {
    return when (value) {
        1 -> MaterialSymbolIcon("today")
        2 -> MaterialSymbolIcon("date_range")
        3 -> MaterialSymbolIcon("calendar_month")
        else -> MaterialSymbolIcon("all_inclusive")
    }
}

fun statsGemIcon(value: Int): MaterialSymbolIcon {
    return when (value) {
        1 -> MaterialSymbolIcon("local_fire_department")
        2 -> MaterialSymbolIcon("event_available")
        3 -> MaterialSymbolIcon("history")
        else -> MaterialSymbolIcon("workspace_premium")
    }
}

@Composable
fun statsRangeLabel(value: Int): String {
    return when (value) {
        1 -> stringResource(R.string.widget_stats_range_today)
        2 -> stringResource(R.string.widget_stats_range_week)
        3 -> stringResource(R.string.widget_stats_range_month)
        else -> stringResource(R.string.widget_stats_range_all_time)
    }
}

@Composable
fun statsGemLabel(value: Int): String {
    return when (value) {
        1 -> stringResource(R.string.widget_stats_gem_current_streak)
        2 -> stringResource(R.string.widget_stats_gem_active_days)
        3 -> stringResource(R.string.widget_stats_gem_sessions)
        else -> stringResource(R.string.widget_stats_gem_longest_streak)
    }
}

/**
 * A selectable option inside [ActionPickerSheet].
 */
data class PickerOption(
    val value: Int,
    val name: String,
    val desc: String,
    val icon: MaterialSymbolIcon
)

/**
 * Reusable bottom sheet with a list of option cards, each with its own icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPickerSheet(
    title: String,
    selectedValue: Int,
    options: List<PickerOption>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    
    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = title,
            subtitle = "",
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
                    .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = selectedValue == option.value
                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                            onSelect(option.value)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = groupedBottomSheetItemShape(index, options.size),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(if (isSelected) 30.dp else 26.dp)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = option.desc,
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
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetTipItem(
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

fun updateAllWidgets(context: Context) {
    // Update Glance widgets via broadcast
    val glanceIntent = android.content.Intent(context, RhythmWidgetReceiver::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    }
    val glanceIds = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, RhythmWidgetReceiver::class.java))
    glanceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, glanceIds)
    context.sendBroadcast(glanceIntent)
    GlanceWidgetUpdater.forceUpdateAll(context)
}
/**
 * Bottom sheet for choosing the widget corner radius (shared with the onboarding tour).
 */
@Composable
fun WidgetCornerRadiusSheet(
    currentRadius: Int,
    onDismiss: () -> Unit,
    appSettings: AppSettings
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var tempRadius by remember { mutableIntStateOf(currentRadius) }

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = stringResource(R.string.settings_miniplayer_corner_radius),
            subtitle = stringResource(R.string.unit_dp, tempRadius),
            visible = true
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Slider(
                value = tempRadius.toFloat(),
                onValueChange = { tempRadius = it.toInt() },
                onValueChangeFinished = {
                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                    appSettings.setWidgetCornerRadius(tempRadius)
                    updateAllWidgets(context)
                },
                valueRange = 0f..60f,
                steps = 59,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = RhythmIcons.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.widgetsettingsscreen_applies_to_glance_widgets),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Bottom sheet for choosing the widget theme (shared with the onboarding tour).
 */
@Composable
fun WidgetThemeSheet(
    currentTheme: Int,
    onDismiss: () -> Unit,
    appSettings: AppSettings
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    val themes = listOf(
        Triple(0, stringResource(R.string.widget_theme_dynamic_system), stringResource(R.string.widget_theme_dynamic_desc)),
        Triple(1, stringResource(R.string.widget_theme_solid_dark), stringResource(R.string.widget_theme_solid_dark_desc)),
        Triple(2, stringResource(R.string.widget_theme_translucent_dark), stringResource(R.string.widget_theme_translucent_dark_desc)),
        Triple(3, stringResource(R.string.widget_theme_solid_purple_signature), stringResource(R.string.widget_theme_solid_purple_desc))
    )

    RhythmAdaptiveModalSheet(
        adaptiveType = SheetAdaptiveType.COMPACT_DIALOG,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary)
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
    ) {
        StandardBottomSheetHeader(
            title = stringResource(R.string.widgetsettingsscreen_widget_theme),
            subtitle = stringResource(R.string.widgetsettingsscreen_personalize_home_screen_widgets),
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
                    .padding(start = 24.dp, end = 24.dp + endPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                themes.forEachIndexed { index, (value, name, desc) ->
                    val isSelected = currentTheme == value
                    val icon = when (value) {
                        1 -> MaterialSymbolIcon("dark_mode", filled = true)
                        2 -> MaterialSymbolIcon("opacity")
                        3 -> MaterialSymbolIcon("palette")
                        else -> MaterialSymbolIcon("auto_awesome")
                    }

                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                            appSettings.setWidgetTheme(value)
                            updateAllWidgets(context)
                            onDismiss()
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = groupedBottomSheetItemShape(index, themes.size),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(if (isSelected) 30.dp else 26.dp)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = desc,
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
                    }
                }
            }
        }
    }
}
