package chromahub.rhythm.app.shared.presentation.screens.settings

import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
//import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import chromahub.rhythm.app.shared.presentation.navigation.RhythmGuardRiskLevel
import chromahub.rhythm.app.shared.presentation.navigation.rhythmGuardResolveRiskLevel
import androidx.compose.ui.draw.clip
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import chromahub.rhythm.app.R
import chromahub.rhythm.app.BuildConfig
import chromahub.rhythm.app.shared.presentation.components.common.CollapsibleHeaderScreen
import chromahub.rhythm.app.ui.utils.LazyListStateSaver
import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.features.local.presentation.components.settings.LanguageSwitcherDialog
import chromahub.rhythm.app.shared.presentation.components.dialogs.FdroidUpdateWarningDialog
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import chromahub.rhythm.app.ui.theme.RhythmTheme
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.shared.presentation.components.player.SleepTimerBottomSheetNew
import chromahub.rhythm.app.features.local.presentation.navigation.Screen
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import androidx.compose.ui.res.stringResource

// Define routes for navigation
object SettingsRoutes {
    const val NOTIFICATIONS = "notifications_settings"
    const val EXPERIMENTAL_FEATURES = "experimental_features_settings"
    const val ABOUT = "about_screen"
    const val UPDATES = "updates_screen"
    const val MEDIA_SCAN = "media_scan_settings"
    const val ARTIST_SEPARATORS = "artist_separators_settings"
    const val PLAYLISTS = "playlist_settings"
    const val API_MANAGEMENT = "api_management_settings"
    const val CACHE_MANAGEMENT = "cache_management_settings"
    const val BACKUP_RESTORE = "backup_restore_settings"
    const val LIBRARY_TAB_ORDER = "library_tab_order_settings"
    const val THEME_CUSTOMIZATION = "theme_customization_settings"
    const val PLAYER_CUSTOMIZATION = "player_customization_settings"
    const val MINIPLAYER_CUSTOMIZATION = "miniplayer_customization_settings"
    const val EQUALIZER = "equalizer_settings"
    const val SLEEP_TIMER = "sleep_timer_settings"
    const val CRASH_LOG_HISTORY = "crash_log_history_settings"
    const val QUEUE = "queue_settings"
    const val PLAYBACK = "playback_settings"
    const val LYRICS = "lyrics_settings"
    const val WIDGET = "widget_settings"
    const val HOME_SCREEN = "home_screen_settings"
    const val GESTURES = "gestures_settings"
    const val RHYTHM_STATS = "rhythm_stats"
    const val EXPRESSIVE_SHAPES = "expressive_shapes_settings"
    const val LIBRARY_SETTINGS = "library_settings"
    const val RHYTHM_GUARD = "rhythm_guard_settings"
    @Deprecated("Use RHYTHM_GUARD")
    const val RHYTHM_AURA = RHYTHM_GUARD
    const val GO_SETTINGS = "go_settings"
    const val BATTERY_SAVER = "battery_saver_settings"
    const val REPLAY_GAIN = "replay_gain_settings"
}

data class SettingItem(
    val icon: MaterialSymbolIcon,
    val title: String,
    val description: String? = null,
    val onClick: (() -> Unit)? = null,
    val toggleState: Boolean? = null,
    val onToggleChange: ((Boolean) -> Unit)? = null,
    val data: Any? = null,
    val enabled: Boolean = true
)

data class SettingGroup(
    val title: String,
    val items: List<SettingItem>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateTo: (String) -> Unit, // Add navigation callback
    scrollState: LazyListState? = null, // Optional scroll state parameter
    isTablet: Boolean = false // Whether this is displayed on a tablet
) {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    
    // Collect states for toggles
    val updatesEnabled by appSettings.updatesEnabled.collectAsState()
    val hapticFeedbackEnabled by appSettings.hapticFeedbackEnabled.collectAsState()
    val defaultScreen by appSettings.defaultScreen.collectAsState()
    val showAlphabetBar by appSettings.showAlphabetBar.collectAsState()
    val showScrollToTop by appSettings.showScrollToTop.collectAsState()
    val appMode by appSettings.appMode.collectAsState()
    val rhythmGuardMode by appSettings.rhythmGuardMode.collectAsState()
    val showSettingsSuggestions by appSettings.showSettingsSuggestions.collectAsState()
    val showKeyboardOnSearchOpen by appSettings.showKeyboardOnSearchOpen.collectAsState()
    val currentAppLocale = context.resources.configuration.locales[0]
    val currentAppLanguage = currentAppLocale.getDisplayName(currentAppLocale)
    
    var showDefaultScreenDialog by remember { mutableStateOf(false) }
    var showLanguageSwitcher by remember { mutableStateOf(false) }
    var showFdroidWarningDialog by remember { mutableStateOf(false) }
    
    // Search state
    var searchQuery by remember { mutableStateOf("") }
    val searchIndex = remember(context) { buildSettingsSearchIndex(context) }
    val searchResults = remember(searchQuery, searchIndex) { 
        searchSettings(searchQuery, searchIndex) 
    }
    val isSearchActive = searchQuery.isNotEmpty()

    CollapsibleHeaderScreen(
        title = context.getString(R.string.settings_title),
        showBackButton = !isTablet,
        onBackClick = {
            if (isSearchActive) {
                searchQuery = ""
            } else {
                HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.HEAVY)
                onBackClick()
            }
        }
        ,
        headerContent = {
            // Settings search moved into header
            SettingsSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .padding(horizontal = if (isTablet) 32.dp else 24.dp)
                    .padding(top = 8.dp, bottom = 8.dp)
            )
        }
    ) { modifier ->
        val settingGroups = listOf(
            // 1. Look & Feel
            SettingGroup(
                title = context.getString(R.string.settings_section_appearance),
                items = buildList {
                    add(SettingItem(RhythmIcons.Palette, context.getString(R.string.settings_theme_customization), context.getString(R.string.settings_theme_customization_desc), onClick = { onNavigateTo(SettingsRoutes.THEME_CUSTOMIZATION) }))
                    add(SettingItem(MaterialSymbolIcon("interests"), context.getString(R.string.settings_shapes), context.getString(R.string.settings_shapes_desc), onClick = { onNavigateTo(SettingsRoutes.EXPRESSIVE_SHAPES) }))
                    add(SettingItem(RhythmIcons.MusicNote, context.getString(R.string.settings_player_customization), context.getString(R.string.settings_player_customization_desc), onClick = { onNavigateTo(SettingsRoutes.PLAYER_CUSTOMIZATION) }))
                    add(SettingItem(RhythmIcons.PlayCircle, context.getString(R.string.settings_miniplayer_customization), context.getString(R.string.settings_miniplayer_customization_desc), onClick = { onNavigateTo(SettingsRoutes.MINIPLAYER_CUSTOMIZATION) }))
                }
            ),
            // 2. Home & Widgets - only show in LOCAL mode
            if (appMode == "LOCAL") SettingGroup(
                title = context.getString(R.string.settings_section_home_widgets),
                items = listOf(
                    SettingItem(RhythmIcons.Home, context.getString(R.string.settings_home_customization), context.getString(R.string.settings_home_customization_desc), onClick = { onNavigateTo(SettingsRoutes.HOME_SCREEN) }),
                    SettingItem(MaterialSymbolIcon("widgets"), context.getString(R.string.settings_widget), context.getString(R.string.settings_widget_desc), onClick = { onNavigateTo(SettingsRoutes.WIDGET) })
                )
            ) else null,
            // 3. Navigation & Controls
            SettingGroup(
                title = context.getString(R.string.settings_section_user_interface),
                items = buildList {
                    add(SettingItem(
                        RhythmIcons.Home,
                        context.getString(R.string.settings_default_screen),
                        if (defaultScreen == "library") context.getString(R.string.library) else context.getString(R.string.home),
                        onClick = { showDefaultScreenDialog = true }
                    ))
                    add(SettingItem(
                        RhythmIcons.Public,
                        context.getString(R.string.settings_language),
                        currentAppLanguage,
                        onClick = { showLanguageSwitcher = true }
                    ))
                    if (appMode == "LOCAL") {
                        //add(SettingItem(MaterialSymbolIcon("reorder"), context.getString(R.string.settings_library_tab_order), context.getString(R.string.settings_library_tab_order_desc), onClick = { onNavigateTo(SettingsRoutes.LIBRARY_TAB_ORDER) }))
                    }
                    add(SettingItem(
                        MaterialSymbolIcon("touch_app"), 
                        context.getString(R.string.settings_haptic_feedback), 
                        context.getString(R.string.settings_haptic_feedback_desc), 
                        toggleState = hapticFeedbackEnabled,
                        onToggleChange = { appSettings.setHapticFeedbackEnabled(it) }
                    ))
                    add(SettingItem(
                        MaterialSymbolIcon("gesture"),
                        context.getString(R.string.settings_gestures),
                        context.getString(R.string.settings_gestures_desc),
                        onClick = { onNavigateTo(SettingsRoutes.GESTURES) }
                    ))
                    add(SettingItem(
                        RhythmIcons.Search,
                        context.getString(R.string.settings_show_keyboard_on_search_open),
                        context.getString(R.string.settings_show_keyboard_on_search_open_desc),
                        toggleState = showKeyboardOnSearchOpen,
                        onToggleChange = { appSettings.setShowKeyboardOnSearchOpen(it) }
                    ))
                    add(SettingItem(
                        MaterialSymbolIcon("lightbulb"),
                        context.getString(R.string.settings_suggestions),
                        context.getString(R.string.settings_suggestions_desc),
                        toggleState = showSettingsSuggestions,
                        onToggleChange = { appSettings.setShowSettingsSuggestions(it) }
                    ))
                }
            ),
            // 4. Queue & Playback
            SettingGroup(
                title = context.getString(R.string.settings_section_queue_playback),
                items = buildList {
                    add(SettingItem(RhythmIcons.Queue, context.getString(R.string.settings_queue), context.getString(R.string.settings_queue_desc), onClick = { onNavigateTo(SettingsRoutes.QUEUE) }))
                    add(SettingItem(RhythmIcons.Play, context.getString(R.string.settings_playback), context.getString(R.string.settings_playback_desc), onClick = { onNavigateTo(SettingsRoutes.PLAYBACK) }))
                    // Sleep Timer is available in both LOCAL and STREAMING modes
                    add(SettingItem(RhythmIcons.AccessTime, context.getString(R.string.sleep_timer), context.getString(R.string.sleep_timer_set_control), onClick = { onNavigateTo(SettingsRoutes.SLEEP_TIMER) }))
                }
            ),
            // 5. Audio & Lyrics
            SettingGroup(
                title = context.getString(R.string.settings_section_audio_lyrics),
                items = buildList {
                    // Equalizer is available in both LOCAL and STREAMING modes
                    add(SettingItem(RhythmIcons.Equalizer, context.getString(R.string.settings_equalizer_title), context.getString(R.string.settings_equalizer_desc), onClick = { onNavigateTo(SettingsRoutes.EQUALIZER) }))
                    add(SettingItem(
                        icon = MaterialSymbolIcon("lyrics"),
                        title = context.getString(R.string.settings_lyrics_source),
                        description = context.getString(R.string.playback_lyrics_priority_desc),
                        onClick = { onNavigateTo(SettingsRoutes.LYRICS) }
                    ))
                    add(SettingItem(
                        icon = MaterialSymbolIcon("speed"),
                        title = stringResource(R.string.performancesettingsscreen_performance),
                        description = context.getString(R.string.settings_performance_desc_optimized),
                        onClick = { onNavigateTo(SettingsRoutes.BATTERY_SAVER) }
                    ))
                }
            ),
            // 6. Library & Media - only show in LOCAL mode
            if (appMode == "LOCAL") SettingGroup(
                title = context.getString(R.string.settings_section_library_content),
                items = listOf(
                    SettingItem(RhythmIcons.Folder, context.getString(R.string.settings_media_scan_title), context.getString(R.string.settings_media_scan_desc), onClick = { onNavigateTo(SettingsRoutes.MEDIA_SCAN) }),
                    SettingItem(RhythmIcons.Artist, context.getString(R.string.settings_artist_parsing), context.getString(R.string.settings_artist_parsing_desc), onClick = { onNavigateTo(SettingsRoutes.ARTIST_SEPARATORS) }),
                    SettingItem(MaterialSymbolIcon("playlist_add_check_circle"), context.getString(R.string.settings_playlists_title), context.getString(R.string.settings_playlists_desc), onClick = { onNavigateTo(SettingsRoutes.PLAYLISTS) }),
                    SettingItem(RhythmIcons.Library, context.getString(R.string.settings_library_settings), context.getString(R.string.settings_library_settings_desc), onClick = { onNavigateTo(SettingsRoutes.LIBRARY_SETTINGS) })
                )
            ) else null,
            // 6. Notifications & Services
            SettingGroup(
                title = context.getString(R.string.settings_section_notifications_services),
                items = buildList {
                    add(SettingItem(RhythmIcons.Notifications, context.getString(R.string.settings_notifications), context.getString(R.string.settings_notifications_desc), onClick = { onNavigateTo(SettingsRoutes.NOTIFICATIONS) }))
                    // API Management/Integrations is available in both LOCAL and STREAMING modes
                    add(SettingItem(MaterialSymbolIcon("api"), context.getString(R.string.settings_api_management), context.getString(R.string.settings_api_management_desc), onClick = { onNavigateTo(SettingsRoutes.API_MANAGEMENT) }))
                }
            ),
            // 7. Data & Storage - split into shared and local-only items
            SettingGroup(
                title = context.getString(R.string.settings_section_storage_data),
                items = buildList {
                    // Listening Stats and Rhythm Guard are shared across LOCAL and STREAMING modes
                    add(SettingItem(MaterialSymbolIcon("auto_graph"), context.getString(R.string.settings_rhythm_stats), context.getString(R.string.settings_rhythm_stats_desc), onClick = { onNavigateTo(SettingsRoutes.RHYTHM_STATS) }))
                    add(SettingItem(RhythmIcons.Security, context.getString(R.string.settings_rhythm_guard), context.getString(R.string.settings_rhythm_guard_list_desc), onClick = { onNavigateTo(SettingsRoutes.RHYTHM_GUARD) }))
                    // Cache and Backup are LOCAL-only
                    if (appMode == "LOCAL") {
                        add(SettingItem(RhythmIcons.Storage, context.getString(R.string.settings_cache_management_title), context.getString(R.string.settings_cache_management_desc), onClick = { onNavigateTo(SettingsRoutes.CACHE_MANAGEMENT) }))
                        add(SettingItem(MaterialSymbolIcon("backup"), context.getString(R.string.settings_backup_restore_title), context.getString(R.string.settings_backup_restore_desc), onClick = { onNavigateTo(SettingsRoutes.BACKUP_RESTORE) }))
                    }
                }
            ),
            // 8. Updates & Info
            SettingGroup(
                title = context.getString(R.string.settings_section_updates_info),
                items = listOf(
                    SettingItem(
                        RhythmIcons.Update,
                        context.getString(R.string.settings_updates_title),
                        context.getString(R.string.settings_updates_desc),
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
                        },
                        onClick = { onNavigateTo(SettingsRoutes.UPDATES) }
                    ),
                    SettingItem(RhythmIcons.Info, context.getString(R.string.settings_about_title), context.getString(R.string.settings_about_desc), onClick = { onNavigateTo(SettingsRoutes.ABOUT) })
                )
            ),
            // 9. Advanced
            SettingGroup(
                title = context.getString(R.string.settings_section_advanced),
                items = listOf(
                    SettingItem(RhythmIcons.BugReport, context.getString(R.string.settings_crash_log_history), context.getString(R.string.settings_crash_log_history_desc), onClick = { onNavigateTo(SettingsRoutes.CRASH_LOG_HISTORY) }),
                    SettingItem(MaterialSymbolIcon("science"), context.getString(R.string.settings_experimental_features), context.getString(R.string.settings_experimental_features_desc), onClick = { onNavigateTo(SettingsRoutes.EXPERIMENTAL_FEATURES) })
                )
            )
        ).filterNotNull() // Filter out null groups (for streaming mode)

        val lazyListState = scrollState ?: rememberSaveable(
            saver = LazyListStateSaver
        ) {
            LazyListState()
        }
        
        // Main content
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Show search results or normal settings
            if (isSearchActive) {
                SettingsSearchResults(
                    results = searchResults,
                    onResultClick = { result ->
                        searchQuery = "" // Clear search
                        if (result.route != null) {
                            onNavigateTo(result.route)
                        }
                    },
                    modifier = Modifier.padding(horizontal = if (isTablet) 32.dp else 24.dp)
                )
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isTablet) 32.dp else 24.dp),
                    contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding())
                ) {
                    item {
                        if (showSettingsSuggestions) {
                            SettingsTipsRow(
                                onNavigateTo = onNavigateTo,
                                rhythmGuardMode = rhythmGuardMode,
                                appMode = appMode,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                    }

                    itemsIndexed(settingGroups, key = { _, group -> "setting_${group.title}" }) { index, group ->
                        Spacer(
                            modifier = Modifier.height(
                                if (index == 0 && showSettingsSuggestions) 10.dp else 28.dp
                            )
                        )

                        val materialItems = group.items.map { item ->
                            Material3SettingsItem(
                                icon = item.icon,
                                title = { Text(item.title) },
                                description = item.description?.let { descriptionText ->
                                    { Text(descriptionText) }
                                },
                                trailingContent = when {
                                    item.toggleState != null && item.onClick != null -> {
                                        {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .width(1.dp)
                                                        .height(20.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.3f
                                                            )
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                AnimatedSwitch(
                                                    checked = item.toggleState,
                                                    onCheckedChange = {
                                                        HapticUtils.performHapticFeedback(
                                                            context,
                                                            hapticFeedback,
                                                            HapticType.LIGHT
                                                        )
                                                        item.onToggleChange?.invoke(it)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    item.toggleState != null -> {
                                        {
                                            AnimatedSwitch(
                                                checked = item.toggleState,
                                                onCheckedChange = {
                                                    HapticUtils.performHapticFeedback(
                                                        context,
                                                        hapticFeedback,
                                                        HapticType.LIGHT
                                                    )
                                                    item.onToggleChange?.invoke(it)
                                                }
                                            )
                                        }
                                    }

                                    item.onClick != null -> {
                                        {
                                            Icon(
                                                imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    else -> null
                                },
                                isHighlighted = item.toggleState == true,
                                enabled = item.enabled,
                                onClick = when {
                                    item.onClick != null -> {
                                        {
                                            HapticUtils.performHapticFeedback(
                                                context,
                                                hapticFeedback,
                                                HapticType.HEAVY
                                            )
                                            item.onClick.invoke()
                                        }
                                    }

                                    item.toggleState != null && item.onToggleChange != null -> {
                                        {
                                            HapticUtils.performHapticFeedback(
                                                context,
                                                hapticFeedback,
                                                HapticType.LIGHT
                                            )
                                            item.onToggleChange.invoke(!item.toggleState)
                                        }
                                    }

                                    else -> null
                                }
                            )
                        }

                        Material3SettingsGroup(
                            title = group.title,
                            items = materialItems,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    }
                }
            } // End of else branch for search
        } // End of Column
        
        // Default screen selection bottom sheet
        if (showDefaultScreenDialog) {
            val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
            
            ModalBottomSheet(
                onDismissRequest = { showDefaultScreenDialog = false },
                sheetState = sheetState,
                dragHandle = { 
                    BottomSheetDefaults.DragHandle(
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp, vertical = 16.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = context.getString(R.string.settings_default_screen),
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
                                    text = context.getString(R.string.settings_default_screen_desc),
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    // Home option
                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                            appSettings.setDefaultScreen("home")
                            showDefaultScreenDialog = false
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (defaultScreen == "home") 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Home,
                                contentDescription = null,
                                tint = if (defaultScreen == "home") 
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = context.getString(R.string.common_home),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (defaultScreen == "home") 
                                        MaterialTheme.colorScheme.onPrimaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = context.getString(R.string.settings_home_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (defaultScreen == "home") 
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (defaultScreen == "home") {
                                Icon(
                                    imageVector = RhythmIcons.CheckCircle,
                                    contentDescription = stringResource(R.string.streaming_selected),
                                    
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    // Library option
                    Card(
                        onClick = {
                            HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                            appSettings.setDefaultScreen("library")
                            showDefaultScreenDialog = false
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (defaultScreen == "library") 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = RhythmIcons.Library,
                                contentDescription = null,
                                tint = if (defaultScreen == "library")
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = context.getString(R.string.common_library),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (defaultScreen == "library") 
                                        MaterialTheme.colorScheme.onPrimaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = context.getString(R.string.settings_library_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (defaultScreen == "library") 
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (defaultScreen == "library") {
                                Icon(
                                    imageVector = RhythmIcons.CheckCircle,
                                    contentDescription = stringResource(R.string.streaming_selected),
                                    
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
        
        // Language switcher dialog
        if (showLanguageSwitcher) {
            LanguageSwitcherDialog(
                onDismiss = { showLanguageSwitcher = false }
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
        
        // App Mode selection dialog
    }
}

@Composable
fun SettingRow(item: SettingItem) {
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val appSettings = AppSettings.getInstance(context)
    val hapticFeedbackEnabled by appSettings.hapticFeedbackEnabled.collectAsState()
    
    // Animation states
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "setting_scale"
    )
    
    val iconBackgroundColor by animateColorAsState(
        targetValue = when {
            item.toggleState == true -> MaterialTheme.colorScheme.primaryContainer
            isPressed -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_bg_color"
    )
    
    val iconTintColor by animateColorAsState(
        targetValue = when {
            item.toggleState == true -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_tint_color"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container with expressive design
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(24.dp),
            color = iconBackgroundColor,
            tonalElevation = if (item.toggleState == true) 2.dp else 0.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    modifier = Modifier.size(24.dp),
                    tint = iconTintColor
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (item.onClick != null && item.toggleState == null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                isPressed = true
                                HapticUtils.performHapticFeedback(
                                    context,
                                    hapticFeedback,
                                    HapticType.HEAVY
                                )
                                item.onClick()
                            }
                        )
                    } else if (item.onClick != null && item.toggleState != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                isPressed = true
                                HapticUtils.performHapticFeedback(
                                    context,
                                    hapticFeedback,
                                    HapticType.HEAVY
                                )
                                item.onClick()
                            }
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            item.description?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }
        }

        if (item.toggleState != null && item.onClick != null) {
            Icon(
                imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                contentDescription = stringResource(R.string.cd_navigate),
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            AnimatedSwitch(
                checked = item.toggleState,
                onCheckedChange = {
                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                    item.onToggleChange?.invoke(it)
                }
            )
        } else if (item.toggleState != null) {
            AnimatedSwitch(
                checked = item.toggleState,
                onCheckedChange = {
                    HapticUtils.performHapticFeedback(context, hapticFeedback, HapticType.LIGHT)
                    item.onToggleChange?.invoke(it)
                }
            )
        } else if (item.onClick != null) {
            Icon(
                imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                contentDescription = stringResource(R.string.cd_navigate),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    RhythmTheme {
        SettingsScreen(onBackClick = {}, onNavigateTo = {})
    }
}

// Wrapper function for navigation
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsScreenWrapper(
    onBack: () -> Unit,
    appSettings: chromahub.rhythm.app.shared.data.model.AppSettings,
    navController: androidx.navigation.NavController,
    musicViewModel: MusicViewModel
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var currentRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var showSleepTimerBottomSheet by rememberSaveable { mutableStateOf(false) }
    val currentSong by musicViewModel.currentSong.collectAsState()
    val isPlaying by musicViewModel.isPlaying.collectAsState()
    val appMode by appSettings.appMode.collectAsState()

    // Hoist the main settings scroll state to persist across navigation
    val mainSettingsScrollState = rememberSaveable(
        saver = LazyListStateSaver
    ) {
        LazyListState()
    }

    // Handle back navigation - if we're in a subsettings screen, go back to main screen
    val handleBack = {
        if (currentRoute != null) {
            currentRoute = null
        } else {
            onBack()
        }
    }

    // Consume any initial subroute requested by external callers (e.g., open settings -> specific pane)
    LaunchedEffect(Unit) {
        val pending = appSettings.consumeInitialSettingsSubroute()
        if (!pending.isNullOrBlank()) {
            currentRoute = pending
        }
    }

    // Handle system back gestures when in subsettings
    BackHandler(enabled = currentRoute != null) {
        handleBack()
    }

    val onNavigateToSubsetting = { route: String ->
        if (route == SettingsRoutes.RHYTHM_STATS) {
            val localStatsRoute = Screen.RhythmStats.route
            val streamingStatsRoute = "streaming_rhythm_stats"
            when {
                navController.graph.findNode(streamingStatsRoute) != null -> navController.navigate(streamingStatsRoute)
                navController.graph.findNode(localStatsRoute) != null -> navController.navigate(localStatsRoute)
                appMode == "STREAMING" -> {
                    appSettings.setInitialStreamingRoute(streamingStatsRoute)
                    if (!navController.popBackStack()) {
                        safeNavigateToMain(navController)
                    }
                }
                else -> {
                    appSettings.setInitialStreamingRoute(localStatsRoute)
                    if (!navController.popBackStack()) {
                        safeNavigateToMain(navController)
                    }
                }
            }
        } else if (route == SettingsRoutes.EQUALIZER) {
            navController.navigate(Screen.Equalizer.route)
        } else if (route == SettingsRoutes.SLEEP_TIMER) {
            showSleepTimerBottomSheet = true
        } else {
            currentRoute = route
        }
    }

    if (isTablet) {
        // Tablet layout: Master-detail with settings always visible on left
        Row(modifier = Modifier.fillMaxSize()) {
            // Master pane - always visible settings list
            Surface(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight(),
            ) {
                SettingsScreen(
                    onBackClick = handleBack,
                    onNavigateTo = onNavigateToSubsetting,
                    scrollState = mainSettingsScrollState,
                    isTablet = true
                )
            }

            // Divider

            // Detail pane - subsettings or placeholder
            Surface(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                AnimatedContent(
                    targetState = currentRoute,
                    transitionSpec = {
                        if (targetState != null) {
                            // Slide in from right for subsettings
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(
                                    durationMillis = 400,
                                    easing = androidx.compose.animation.core.EaseOutCubic
                                )
                            ) + fadeIn(
                                animationSpec = tween(
                                    durationMillis = 350,
                                    delayMillis = 50
                                )
                            ) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { -it / 4 },
                                animationSpec = tween(
                                    durationMillis = 350,
                                    easing = androidx.compose.animation.core.EaseInCubic
                                )
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = 250)
                            )
                        } else {
                            // Slide in from left when going back to placeholder
                            slideInHorizontally(
                                initialOffsetX = { -it / 4 },
                                animationSpec = tween(
                                    durationMillis = 400,
                                    easing = androidx.compose.animation.core.EaseOutCubic
                                )
                            ) + fadeIn(
                                animationSpec = tween(
                                    durationMillis = 350,
                                    delayMillis = 50
                                )
                            ) togetherWith
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(
                                    durationMillis = 350,
                                    easing = androidx.compose.animation.core.EaseInCubic
                                )
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = 250)
                            )
                        }
                    },
                    label = "tablet_detail_navigation",
                    contentKey = { it ?: "placeholder" }
                ) { route ->
                    when (route) {
                        SettingsRoutes.NOTIFICATIONS -> NotificationsSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.PLAYLISTS -> PlaylistsSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.MEDIA_SCAN -> MediaScanSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.ARTIST_SEPARATORS -> ArtistSeparatorsSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.ABOUT -> chromahub.rhythm.app.shared.presentation.screens.settings.AboutScreen(
                            onBackClick = { currentRoute = null },
                            onNavigateToUpdates = { currentRoute = SettingsRoutes.UPDATES }
                        )
                        SettingsRoutes.UPDATES -> UpdatesSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.EXPERIMENTAL_FEATURES -> ExperimentalFeaturesScreen(
                            onBackClick = { currentRoute = null },
                            onNavigateTo = { currentRoute = it },
                            onNavigateToGoSettings = { currentRoute = SettingsRoutes.GO_SETTINGS }
                        )
                        SettingsRoutes.GO_SETTINGS -> chromahub.rhythm.app.features.streaming.presentation.screens.GoSettingsScreen(
                            onBackClick = { currentRoute = null },
                            onConfigureCurrentProvider = { serviceId ->
                                appSettings.setInitialStreamingRoute("streaming_service_setup/$serviceId")
                                appSettings.setAppMode("STREAMING")
                                if (!navController.popBackStack()) {
                                    safeNavigateToMain(navController)
                                }
                            }
                        )
                        SettingsRoutes.API_MANAGEMENT -> ApiManagementSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.CACHE_MANAGEMENT -> CacheManagementSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.BACKUP_RESTORE -> BackupRestoreSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.LIBRARY_TAB_ORDER -> LibraryTabOrderSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.THEME_CUSTOMIZATION -> ThemeCustomizationSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.PLAYER_CUSTOMIZATION -> PlayerCustomizationSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.MINIPLAYER_CUSTOMIZATION -> MiniPlayerCustomizationSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.CRASH_LOG_HISTORY -> CrashLogHistorySettingsScreen(onBackClick = { currentRoute = null }, appSettings = appSettings)
                        SettingsRoutes.QUEUE -> QueueSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.PLAYBACK -> PlaybackSettingsScreen(
                            onBackClick = { currentRoute = null },
                            onNavigateTo = { currentRoute = it }
                        )
                        SettingsRoutes.REPLAY_GAIN -> ReplayGainSettingsScreen(
                            onBackClick = { currentRoute = null },
                            onNavigateTo = { currentRoute = it }
                        )
                        SettingsRoutes.LYRICS -> LyricsSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.WIDGET -> WidgetSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.HOME_SCREEN -> HomeScreenCustomizationSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.GESTURES -> GesturesSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.EXPRESSIVE_SHAPES -> ExpressiveShapesSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.LIBRARY_SETTINGS -> LibrarySettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.RHYTHM_GUARD -> RhythmGuardSettingsScreen(onBackClick = { currentRoute = null })
                        SettingsRoutes.BATTERY_SAVER -> PerformanceSettingsScreen(onBackClick = { currentRoute = null })
                        else -> PlaceholderSettingsScreen()
                    }
                }
            }
        }
    } else {
        // Phone layout: Traditional navigation with AnimatedContent
        AnimatedContent(
            targetState = currentRoute,
            transitionSpec = {
                if (targetState != null) {
                    // Enhanced slide in from right when navigating to a screen
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = androidx.compose.animation.core.EaseOutCubic
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
                            easing = androidx.compose.animation.core.EaseOutCubic
                        )
                    ) togetherWith
                    slideOutHorizontally(
                        targetOffsetX = { -it / 4 },
                        animationSpec = tween(
                            durationMillis = 350,
                            easing = androidx.compose.animation.core.EaseInCubic
                        )
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 250)
                    ) + scaleOut(
                        targetScale = 0.95f,
                        animationSpec = tween(
                            durationMillis = 350,
                            easing = androidx.compose.animation.core.EaseInCubic
                        )
                    )
                } else {
                    // Enhanced slide in from left when going back
                    slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = androidx.compose.animation.core.EaseOutCubic
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
                            easing = androidx.compose.animation.core.EaseOutCubic
                        )
                    ) togetherWith
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(
                            durationMillis = 350,
                            easing = androidx.compose.animation.core.EaseInCubic
                        )
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 250)
                    ) + scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(
                            durationMillis = 350,
                            easing = androidx.compose.animation.core.EaseInCubic
                        )
                    )
                }
            },
            label = "settings_navigation",
            contentKey = { it ?: "main_settings" }
        ) { route ->
            when (route) {
                SettingsRoutes.NOTIFICATIONS -> NotificationsSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.PLAYLISTS -> PlaylistsSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.MEDIA_SCAN -> MediaScanSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.ARTIST_SEPARATORS -> ArtistSeparatorsSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.ABOUT -> chromahub.rhythm.app.shared.presentation.screens.settings.AboutScreen(
                    onBackClick = { currentRoute = null },
                    onNavigateToUpdates = { currentRoute = SettingsRoutes.UPDATES }
                )
                SettingsRoutes.UPDATES -> UpdatesSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.EXPERIMENTAL_FEATURES -> ExperimentalFeaturesScreen(
                    onBackClick = { currentRoute = null },
                    onNavigateTo = { currentRoute = it },
                    onNavigateToGoSettings = { currentRoute = SettingsRoutes.GO_SETTINGS }
                )
                SettingsRoutes.GO_SETTINGS -> chromahub.rhythm.app.features.streaming.presentation.screens.GoSettingsScreen(
                    onBackClick = { currentRoute = null },
                    onConfigureCurrentProvider = { serviceId ->
                        appSettings.setInitialStreamingRoute("streaming_service_setup/$serviceId")
                        appSettings.setAppMode("STREAMING")
                        if (!navController.popBackStack()) {
                            safeNavigateToMain(navController)
                        }
                    }
                )
                SettingsRoutes.API_MANAGEMENT -> ApiManagementSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.CACHE_MANAGEMENT -> CacheManagementSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.BACKUP_RESTORE -> BackupRestoreSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.LIBRARY_TAB_ORDER -> LibraryTabOrderSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.THEME_CUSTOMIZATION -> ThemeCustomizationSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.PLAYER_CUSTOMIZATION -> PlayerCustomizationSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.MINIPLAYER_CUSTOMIZATION -> MiniPlayerCustomizationSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.CRASH_LOG_HISTORY -> CrashLogHistorySettingsScreen(onBackClick = { currentRoute = null }, appSettings = appSettings)
                SettingsRoutes.QUEUE -> QueueSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.PLAYBACK -> PlaybackSettingsScreen(
                    onBackClick = { currentRoute = null },
                    onNavigateTo = { currentRoute = it }
                )
                SettingsRoutes.REPLAY_GAIN -> ReplayGainSettingsScreen(
                    onBackClick = { currentRoute = null },
                    onNavigateTo = { currentRoute = it }
                )
                SettingsRoutes.LYRICS -> LyricsSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.WIDGET -> WidgetSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.HOME_SCREEN -> HomeScreenCustomizationSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.GESTURES -> GesturesSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.EXPRESSIVE_SHAPES -> ExpressiveShapesSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.LIBRARY_SETTINGS -> LibrarySettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.RHYTHM_GUARD -> RhythmGuardSettingsScreen(onBackClick = { currentRoute = null })
                SettingsRoutes.BATTERY_SAVER -> PerformanceSettingsScreen(onBackClick = { currentRoute = null })
                else -> SettingsScreen(
                    onBackClick = handleBack,
                    onNavigateTo = onNavigateToSubsetting,
                    scrollState = mainSettingsScrollState
                )
            }
        }
    }

    if (showSleepTimerBottomSheet) {
        SleepTimerBottomSheetNew(
            onDismiss = { showSleepTimerBottomSheet = false },
            currentSong = currentSong,
            isPlaying = isPlaying,
            musicViewModel = musicViewModel
        )
    }
}

private fun safeNavigateToMain(navController: androidx.navigation.NavController) {
    if (navController.graph.findNode("main") != null) {
        navController.navigate("main") { launchSingleTop = true }
    } else {
        val startDest = navController.graph.startDestinationRoute
        if (startDest != null) {
            navController.navigate(startDest) {
                popUpTo(startDest) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

@Composable
fun AnimatedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    TunerAnimatedSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}

data class SettingsTipData(
    val id: String,
    val icon: MaterialSymbolIcon,
    val title: String,
    val text: String,
    val route: String? = null,
    val isPrimary: Boolean = false,
    val riskLevel: RhythmGuardRiskLevel? = null,
    val fillProgress: Float? = null
)

@Composable
fun SettingsTipsRow(
    onNavigateTo: (String) -> Unit,
    rhythmGuardMode: String,
    appMode: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var dismissedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    
    // Playback stats
    var todayExposureMinutes by remember { mutableStateOf(0) }
    var currentRiskLevel by remember { mutableStateOf(RhythmGuardRiskLevel.LOW) }
    
    val appSettings = remember { chromahub.rhythm.app.shared.data.model.AppSettings.getInstance(context) }
    val limitMinutes by appSettings.rhythmGuardAlertThresholdMinutes.collectAsState()
    val manualVolumeFloat by appSettings.rhythmGuardManualVolumeThreshold.collectAsState()
    val autoBackupEnabled by appSettings.autoBackupEnabled.collectAsState()
    val updatesEnabled by appSettings.updatesEnabled.collectAsState()
    val miniPlayerShowProgress by appSettings.miniPlayerShowProgress.collectAsState()
    val playerShowSeekButtons by appSettings.playerShowSeekButtons.collectAsState()
    val gesturePlayerSwipeTracks by appSettings.gesturePlayerSwipeTracks.collectAsState()
    val enableRatingSystem by appSettings.enableRatingSystem.collectAsState()

    LaunchedEffect(limitMinutes, manualVolumeFloat) {
        val statsRepo = chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository.getInstance(context)
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        
        while (true) {
            val stats = statsRepo.loadSummary(chromahub.rhythm.app.shared.data.repository.StatsTimeRange.TODAY)
            todayExposureMinutes = (stats.totalDurationMs / 60000).toInt()
            
            val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val currentVolumePercent = if (maxVol > 0) ((currentVol.toFloat() / maxVol) * 100).toInt() else 0
            
            val safeLimit = if (limitMinutes > 0) limitMinutes else 90
            val safeThreshold = (manualVolumeFloat * 100).toInt().coerceAtLeast(1)
            
            currentRiskLevel = chromahub.rhythm.app.shared.presentation.navigation.rhythmGuardResolveRiskLevel(
                currentVolumePercent = currentVolumePercent, 
                safeThresholdPercent = safeThreshold,
                exposureMinutes = todayExposureMinutes,
                exposureLimitMinutes = safeLimit
            )
            kotlinx.coroutines.delay(2000)
        }
    }
    
    // Generate a fixed seed when the view enters to keep shuffle stable during recompositions
    val shuffleSeed = rememberSaveable { kotlin.random.Random.nextInt() }
    
    val tips = remember(
        rhythmGuardMode,
        appMode,
        dismissedIds,
        todayExposureMinutes,
        currentRiskLevel,
        autoBackupEnabled,
        updatesEnabled,
        miniPlayerShowProgress,
        playerShowSeekButtons,
        gesturePlayerSwipeTracks,
        enableRatingSystem
    ) {
        val random = kotlin.random.Random(shuffleSeed)
        val isLocalMode = appMode == "LOCAL"
        val hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val dayMomentLabel = when (hourOfDay) {
            in 5..11 -> context.getString(R.string.moment_morning)
            in 12..16 -> context.getString(R.string.moment_afternoon)
            in 17..21 -> context.getString(R.string.moment_evening)
            else -> context.getString(R.string.moment_night)
        }
        val listeningPulseLabel = when {
            todayExposureMinutes < 20 -> context.getString(R.string.pulse_fresh)
            todayExposureMinutes < 60 -> context.getString(R.string.pulse_steady)
            todayExposureMinutes < 120 -> context.getString(R.string.pulse_heavy)
            else -> context.getString(R.string.pulse_marathon)
        }

        buildList {
            if (isLocalMode && "rhythm_guard" !in dismissedIds) {
                val desc = when (rhythmGuardMode) {
                    "OFF" -> "${context.getString(R.string.settings_tip_rhythm_guard_off)} ${listeningPulseLabel.lowercase()} ${dayMomentLabel}."
                    "MANUAL" -> "${context.getString(R.string.settings_tip_rhythm_guard_manual)} ${todayExposureMinutes} min played today."
                    else -> "${context.getString(R.string.settings_tip_rhythm_guard_auto)} ${todayExposureMinutes} min tracked ${dayMomentLabel}."
                }
                
                val progress = (todayExposureMinutes.toFloat() / 90f).coerceIn(0.05f, 1f)

                add(
                    SettingsTipData(
                        id = "rhythm_guard",
                        icon = RhythmIcons.Security,
                        title = context.getString(R.string.settings_rhythm_guard),
                        text = desc,
                        route = SettingsRoutes.RHYTHM_GUARD,
                        isPrimary = true,
                        riskLevel = if (rhythmGuardMode == "OFF") null else currentRiskLevel,
                        fillProgress = if (rhythmGuardMode == "OFF") null else progress
                    )
                )
            }
            if ("theme" !in dismissedIds) {
                val descs = listOf(
                    context.getString(R.string.settings_tip_theme),
                    context.getString(R.string.settings_tip_theme_desc_1),
                    context.getString(R.string.settings_tip_theme_desc_2)
                )
                add(
                    SettingsTipData(
                        id = "theme",
                        icon = RhythmIcons.Palette,
                        title = context.getString(R.string.settingsscreen_personalization),
                        text = descs.random(random),
                        route = SettingsRoutes.THEME_CUSTOMIZATION
                    )
                )
            }
            if ("gestures" !in dismissedIds) {
                val descs = listOf(
                    context.getString(R.string.settings_tip_gestures),
                    context.getString(R.string.settings_tip_gestures_swipe),
                    context.getString(R.string.settings_tip_gestures_artwork)
                )
                add(
                    SettingsTipData(
                        id = "gestures",
                        icon = MaterialSymbolIcon("gesture"),
                        title = context.getString(R.string.settings_gestures),
                        text = descs.random(random),
                        route = SettingsRoutes.GESTURES
                    )
                )
            }
            if (isLocalMode && "media_scan" !in dismissedIds) {
                val descs = listOf(
                    context.getString(R.string.settings_tip_media_scan),
                    context.getString(R.string.settings_tip_media_scan_desc_1),
                    context.getString(R.string.settings_tip_media_scan_desc_2)
                )
                add(
                    SettingsTipData(
                        id = "media_scan",
                        icon = RhythmIcons.Folder,
                        title = context.getString(R.string.settingsscreen_library_focus),
                        text = descs.random(random),
                        route = SettingsRoutes.MEDIA_SCAN
                    )
                )
            }
            if (isLocalMode && "sleep_timer" !in dismissedIds) {
                val descs = listOf(
                    context.getString(R.string.settings_tip_sleep_timer_desc_1),
                    context.getString(R.string.settings_tip_sleep_timer_desc_2)
                )
                add(
                    SettingsTipData(
                        id = "sleep_timer",
                        icon = RhythmIcons.AccessTime,
                        title = context.getString(R.string.settings_sleep_timer_search),
                        text = descs.random(random),
                        route = SettingsRoutes.SLEEP_TIMER
                    )
                )
            }
            if (isLocalMode && "equalizer" !in dismissedIds) {
                val descs = listOf(
                    context.getString(R.string.settings_tip_equalizer_desc_1),
                    context.getString(R.string.settings_tip_equalizer_desc_2)
                )
                add(
                    SettingsTipData(
                        id = "equalizer",
                        icon = RhythmIcons.Equalizer,
                        title = context.getString(R.string.settingsscreen_audio_equalizer),
                        text = descs.random(random),
                        route = SettingsRoutes.EQUALIZER
                    )
                )
            }
            if (isLocalMode && "backup_restore" !in dismissedIds) {
                val descs = if (autoBackupEnabled) {
                    listOf(
                        context.getString(R.string.settings_tip_backup_active_desc_1),
                        context.getString(R.string.settings_tip_backup_active_desc_2)
                    )
                } else {
                    listOf(
                        context.getString(R.string.settings_tip_backup_inactive_desc_1),
                        context.getString(R.string.settings_tip_backup_inactive_desc_2)
                    )
                }
                add(
                    SettingsTipData(
                        id = "backup_restore",
                        icon = MaterialSymbolIcon("backup"),
                        title = context.getString(R.string.settings_backup_restore),
                        text = descs.random(random),
                        route = SettingsRoutes.BACKUP_RESTORE
                    )
                )
            }
            if ("updates" !in dismissedIds) {
                val descs = if (updatesEnabled) {
                    listOf(
                        context.getString(R.string.settings_tip_updates_active_desc_1),
                        context.getString(R.string.settings_tip_updates_active_desc_2)
                    )
                } else {
                    listOf(
                        context.getString(R.string.settings_tip_updates_inactive_desc_1),
                        context.getString(R.string.settings_tip_updates_inactive_desc_2)
                    )
                }
                add(
                    SettingsTipData(
                        id = "updates",
                        icon = RhythmIcons.Update,
                        title = context.getString(R.string.cd_app_updates),
                        text = descs.random(random),
                        route = SettingsRoutes.UPDATES
                    )
                )
            }
            if ("queue_playback" !in dismissedIds) {
                val descs = if (gesturePlayerSwipeTracks) {
                    listOf(
                        context.getString(R.string.settings_tip_queue_active_desc_1),
                        context.getString(R.string.settings_tip_queue_active_desc_2)
                    )
                } else {
                    listOf(
                        context.getString(R.string.settings_tip_queue_inactive_desc_1),
                        context.getString(R.string.settings_tip_queue_inactive_desc_2)
                    )
                }
                add(
                    SettingsTipData(
                        id = "queue_playback",
                        icon = RhythmIcons.Queue,
                        title = context.getString(R.string.settings_queue_title),
                        text = descs.random(random),
                        route = SettingsRoutes.QUEUE
                    )
                )
            }
            if ("player_controls" !in dismissedIds) {
                val descs = if (playerShowSeekButtons) {
                    listOf(
                        context.getString(R.string.settings_tip_controls_active_desc_1),
                        context.getString(R.string.settings_tip_controls_active_desc_2)
                    )
                } else {
                    listOf(
                        context.getString(R.string.settings_tip_controls_inactive_desc_1),
                        context.getString(R.string.settings_tip_controls_inactive_desc_2)
                    )
                }
                add(
                    SettingsTipData(
                        id = "player_controls",
                        icon = RhythmIcons.MusicNote,
                        title = context.getString(R.string.settings_shapes_player_controls),
                        text = descs.random(random),
                        route = SettingsRoutes.PLAYER_CUSTOMIZATION
                    )
                )
            }
            if ("miniplayer" !in dismissedIds) {
                val descs = if (miniPlayerShowProgress) {
                    listOf(
                        context.getString(R.string.settings_tip_miniplayer_active_desc_1),
                        context.getString(R.string.settings_tip_miniplayer_active_desc_2)
                    )
                } else {
                    listOf(
                        context.getString(R.string.settings_tip_miniplayer_inactive_desc_1),
                        context.getString(R.string.settings_tip_miniplayer_inactive_desc_2)
                    )
                }
                add(
                    SettingsTipData(
                        id = "miniplayer",
                        icon = MaterialSymbolIcon("play_circle_filled"),
                        title = context.getString(R.string.settings_shapes_mini_player),
                        text = descs.random(random),
                        route = SettingsRoutes.MINIPLAYER_CUSTOMIZATION
                    )
                )
            }
            if (isLocalMode && "library_settings" !in dismissedIds) {
                val descs = if (enableRatingSystem) {
                    listOf(
                        context.getString(R.string.settings_tip_library_active_desc_1),
                        context.getString(R.string.settings_tip_library_active_desc_2)
                    )
                } else {
                    listOf(
                        context.getString(R.string.settings_tip_library_inactive_desc_1),
                        context.getString(R.string.settings_tip_library_inactive_desc_2)
                    )
                }
                add(
                    SettingsTipData(
                        id = "library_settings",
                        icon = RhythmIcons.Library,
                        title = context.getString(R.string.settingsscreen_library_settings),
                        text = descs.random(random),
                        route = SettingsRoutes.LIBRARY_SETTINGS
                    )
                )
            }
        }.shuffled(random)
    }

    if (tips.isNotEmpty()) {
        LazyRow(
            modifier = modifier,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 0.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
        ) {
            items(tips, key = { it.id }) { tip ->
                SettingsTipCard(
                    tip = tip,
                    onDismiss = { dismissedIds = dismissedIds + tip.id },
                    onClick = { tip.route?.let { onNavigateTo(it) } }
                )
            }
        }
    }
}

@Composable
fun SettingsTipCard(
    tip: SettingsTipData,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    val isPrimary = tip.isPrimary
    val containerColor = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (isPrimary) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val iconColor = if (isPrimary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    // Use health status directly for icon background if available, looks much cleaner than partial filling!
    val indicatorColor = if (tip.riskLevel != null) {
        when (tip.riskLevel) {
            RhythmGuardRiskLevel.LOW -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
            RhythmGuardRiskLevel.MODERATE -> androidx.compose.ui.graphics.Color(0xFFFF9800)
            RhythmGuardRiskLevel.HIGH -> androidx.compose.ui.graphics.Color(0xFFFF5722)
            RhythmGuardRiskLevel.SEVERE -> MaterialTheme.colorScheme.error
        }
    } else null

    Card(
        modifier = Modifier
            .width(320.dp)
            .height(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(if (isPrimary) 24.dp else 20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = tip.icon,
                        contentDescription = null,
                        tint = indicatorColor ?: iconColor,
                        modifier = Modifier.size(30.dp)
                    )
                    
                    androidx.compose.material3.IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isPrimary) 0.55f else 0.44f))
                    ) {
                        Icon(
                            imageVector = RhythmIcons.Close,
                            contentDescription = stringResource(R.string.onboarding_dismiss),
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = tip.title,
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = tip.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.85f),
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
