/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.screens.settings


import chromahub.rhythm.app.ui.LocalMiniPlayerPadding
import androidx.compose.foundation.layout.PaddingValues
import chromahub.rhythm.app.shared.presentation.components.icons.RhythmIcons
import chromahub.rhythm.app.shared.presentation.components.icons.MaterialSymbolIcon
import chromahub.rhythm.app.shared.presentation.components.icons.Icon

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chromahub.rhythm.app.R
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsGroup
import chromahub.rhythm.app.shared.presentation.components.Material3SettingsItem
import chromahub.rhythm.app.util.HapticUtils
import chromahub.rhythm.app.util.HapticType
import chromahub.rhythm.app.util.safeGetQuantityString
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextAlign
import chromahub.rhythm.app.shared.presentation.components.common.ExpressiveCookieEmptyState
import chromahub.rhythm.app.shared.presentation.components.common.rememberExpressiveShape

/**
 * Represents a searchable setting item with its metadata for search indexing
 */
data class SearchableSettingItem(
    val id: String,
    val title: String,
    val description: String,
    val keywords: List<String>,
    val icon: MaterialSymbolIcon,
    val route: String?, // null means it's in the main settings screen
    val parentScreen: String, // e.g., "Settings", "Theme", "Player", etc.
    val settingKey: String? = null // for highlighting specific setting
)

/**
 * Builds the complete search index for all settings in the app
 */
fun buildSettingsSearchIndex(context: Context): List<SearchableSettingItem> {
    return buildList {
        // ======================== MAIN SETTINGS SCREEN ========================
        
        // Look & Feel Section
        add(SearchableSettingItem(
            id = "theme_customization",
            title = context.getString(R.string.settings_theme_customization),
            description = context.getString(R.string.settings_theme_customization_desc),
            keywords = listOf("theme", "color", "appearance", "dark mode", "light mode", "colors", "customize", "style"),
            icon = RhythmIcons.Palette,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = context.getString(R.string.settings_section_appearance)
        ))
        add(SearchableSettingItem(
            id = "expressive_shapes_nav",
            title = context.getString(R.string.settings_shapes),
            description = context.getString(R.string.settings_shapes_desc),
            keywords = listOf("shapes", "expressive", "custom", "corners", "rounded", "design"),
            icon = RhythmIcons.Palette,
            route = SettingsRoutes.EXPRESSIVE_SHAPES,
            parentScreen = context.getString(R.string.settings_section_appearance)
        ))
        add(SearchableSettingItem(
            id = "player_customization",
            title = context.getString(R.string.settings_player_customization),
            description = context.getString(R.string.settings_player_customization_desc),
            keywords = listOf("player", "now playing", "full player", "music player", "controls", "artwork"),
            icon = RhythmIcons.MusicNote,
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = context.getString(R.string.settings_section_appearance)
        ))
        add(SearchableSettingItem(
            id = "miniplayer_customization",
            title = context.getString(R.string.settings_miniplayer_customization),
            description = context.getString(R.string.settings_miniplayer_customization_desc),
            keywords = listOf("miniplayer", "mini player", "compact player", "bottom bar", "progress"),
            icon = MaterialSymbolIcon("play_circle_filled"),
            route = SettingsRoutes.MINIPLAYER_CUSTOMIZATION,
            parentScreen = context.getString(R.string.settings_section_appearance)
        ))
        add(SearchableSettingItem(
            id = "album_bottom_sheet_blur",
            title = context.getString(R.string.settings_album_bottom_sheet_gradient_blur),
            description = context.getString(R.string.settings_album_bottom_sheet_gradient_blur_desc),
            keywords = listOf("album", "bottom sheet", "gradient", "blur", "effect", "background"),
            icon = MaterialSymbolIcon("lens_blur"),
            route = SettingsRoutes.LIBRARY_SETTINGS,
            parentScreen = context.getString(R.string.settings_library_settings),
            settingKey = "albumBottomSheetGradientBlur"
        ))
        add(SearchableSettingItem(
            id = "album_hide_about",
            title = context.getString(R.string.settings_album_hide_about),
            description = context.getString(R.string.settings_album_hide_about_desc),
            keywords = listOf("album", "about", "description", "album about", "info"),
            icon = MaterialSymbolIcon("info"),
            route = SettingsRoutes.LIBRARY_SETTINGS,
            parentScreen = context.getString(R.string.settings_library_settings),
            settingKey = "albumHideAbout"
        ))

        
        // Home & Widgets Section
        add(SearchableSettingItem(
            id = "home_customization",
            title = context.getString(R.string.settings_home_customization),
            description = context.getString(R.string.settings_home_customization_desc),
            keywords = listOf("home", "screen", "layout", "sections", "customize", "greeting", "carousel", "discover"),
            icon = RhythmIcons.Home,
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = context.getString(R.string.settings_section_home_widgets)
        ))
        add(SearchableSettingItem(
            id = "widget_settings",
            title = context.getString(R.string.settings_widget),
            description = context.getString(R.string.settings_widget_desc),
            keywords = listOf("widget", "home screen", "launcher", "music widget", "album art"),
            icon = MaterialSymbolIcon("widgets"),
            route = SettingsRoutes.WIDGET,
            parentScreen = context.getString(R.string.settings_section_home_widgets)
        ))
        add(SearchableSettingItem(
            id = "widget_theme",
            title = context.getString(R.string.widgetsettingsscreen_widget_theme),
            description = context.getString(R.string.settings_widget_theme_desc),
            keywords = listOf("widget theme", "dynamic", "solid", "translucent", "dark", "purple", "style"),
            icon = MaterialSymbolIcon("widgets"),
            route = SettingsRoutes.WIDGET,
            parentScreen = context.getString(R.string.settings_widget),
            settingKey = "widgetTheme"
        ))
        add(SearchableSettingItem(
            id = "widget_show_album",
            title = context.getString(R.string.onboarding_widget_album),
            description = context.getString(R.string.widget_show_album_desc),
            keywords = listOf("widget", "album", "name", "title", "show"),
            icon = MaterialSymbolIcon("album"),
            route = SettingsRoutes.WIDGET,
            parentScreen = context.getString(R.string.settings_widget),
            settingKey = "widgetShowAlbum"
        ))
        add(SearchableSettingItem(
            id = "widget_show_artist",
            title = context.getString(R.string.onboarding_widget_artist),
            description = context.getString(R.string.widget_show_artist_desc),
            keywords = listOf("widget", "artist", "name", "show", "title"),
            icon = RhythmIcons.Artist,
            route = SettingsRoutes.WIDGET,
            parentScreen = context.getString(R.string.settings_widget),
            settingKey = "widgetShowArtist"
        ))
        add(SearchableSettingItem(
            id = "widget_favorite_button",
            title = context.getString(R.string.widgetsettingsscreen_show_favorite_button),
            description = context.getString(R.string.widget_show_favorite_button_desc),
            keywords = listOf("widget", "favorite", "like", "heart", "button"),
            icon = MaterialSymbolIcon("favorite"),
            route = SettingsRoutes.WIDGET,
            parentScreen = context.getString(R.string.settings_widget),
            settingKey = "widgetShowFavoriteButton"
        ))
        add(SearchableSettingItem(
            id = "widget_corner_radius",
            title = context.getString(R.string.settings_miniplayer_corner_radius),
            description = context.getString(R.string.widget_settings_radius_desc),
            keywords = listOf("widget", "corner", "radius", "rounded", "shape"),
            icon = MaterialSymbolIcon("rounded_corner"),
            route = SettingsRoutes.WIDGET,
            parentScreen = context.getString(R.string.settings_widget),
            settingKey = "widgetCornerRadius"
        ))
        add(SearchableSettingItem(
            id = "widget_cookie_bottom_left",
            title = context.getString(R.string.widget_cookie_bottom_left),
            description = context.getString(R.string.widget_cookie_section_title),
            keywords = listOf("widget", "cookie", "bottom", "left", "action", "shuffle", "repeat", "favorite"),
            icon = MaterialSymbolIcon("widgets"),
            route = SettingsRoutes.WIDGET,
            parentScreen = context.getString(R.string.settings_widget),
            settingKey = "widgetCookieBottomLeft"
        ))
        add(SearchableSettingItem(
            id = "widget_cookie_bottom_right",
            title = context.getString(R.string.widget_cookie_bottom_right),
            description = context.getString(R.string.widget_cookie_section_title),
            keywords = listOf("widget", "cookie", "bottom", "right", "action", "shuffle", "repeat", "favorite"),
            icon = MaterialSymbolIcon("widgets"),
            route = SettingsRoutes.WIDGET,
            parentScreen = context.getString(R.string.settings_widget),
            settingKey = "widgetCookieBottomRight"
        ))
        add(SearchableSettingItem(
            id = "widget_stats_range",
            title = context.getString(R.string.widget_stats_time_range),
            description = context.getString(R.string.widget_stats_section_title),
            keywords = listOf("widget", "stats", "range", "time", "today", "week", "month"),
            icon = MaterialSymbolIcon("calendar_today"),
            route = SettingsRoutes.WIDGET,
            parentScreen = context.getString(R.string.settings_widget),
            settingKey = "widgetStatsRange"
        ))
        add(SearchableSettingItem(
            id = "widget_stats_gem",
            title = context.getString(R.string.widget_stats_gem),
            description = context.getString(R.string.widget_stats_section_title),
            keywords = listOf("widget", "stats", "gem", "streak", "days", "sessions"),
            icon = MaterialSymbolIcon("auto_graph"),
            route = SettingsRoutes.WIDGET,
            parentScreen = context.getString(R.string.settings_widget),
            settingKey = "widgetStatsGem"
        ))
        
        // Navigation & Interaction Section
        add(SearchableSettingItem(
            id = "default_screen",
            title = context.getString(R.string.settings_default_screen),
            description = context.getString(R.string.settings_default_screen_desc),
            keywords = listOf("default", "screen", "start", "launch", "home", "library", "startup"),
            icon = RhythmIcons.Home,
            route = null,
            parentScreen = context.getString(R.string.settings_section_user_interface),
            settingKey = "defaultScreen"
        ))
        add(SearchableSettingItem(
            id = "language",
            title = context.getString(R.string.settings_language),
            description = context.getString(R.string.settings_language_desc),
            keywords = listOf("language", "locale", "translation", "english", "spanish", "french", "german", "hindi", "chinese", "japanese", "korean"),
            icon = RhythmIcons.Info,
            route = null,
            parentScreen = context.getString(R.string.settings_section_user_interface),
            settingKey = "language"
        ))
        add(SearchableSettingItem(
            id = "haptic_feedback",
            title = context.getString(R.string.settings_haptic_feedback),
            description = context.getString(R.string.settings_haptic_feedback_desc),
            keywords = listOf("haptic", "vibration", "feedback", "touch", "vibrate"),
            icon = MaterialSymbolIcon("touch_app"),
            route = null,
            parentScreen = context.getString(R.string.settings_section_user_interface),
            settingKey = "hapticFeedback"
        ))
        add(SearchableSettingItem(
            id = "settings_suggestions",
            title = context.getString(R.string.settingssearch_settings_suggestions),
            description = context.getString(R.string.settings_search_suggestions_desc),
            keywords = listOf("suggestions", "tips", "recommendations", "contextual", "settings"),
            icon = RhythmIcons.AutoAwesome,
            route = null,
            parentScreen = context.getString(R.string.settings_section_user_interface),
            settingKey = "showSettingsSuggestions"
        ))
        add(SearchableSettingItem(
            id = "gestures",
            title = context.getString(R.string.settings_gestures),
            description = context.getString(R.string.settings_gestures_desc),
            keywords = listOf("gestures", "swipe", "touch", "double tap", "navigation"),
            icon = MaterialSymbolIcon("gesture"),
            route = SettingsRoutes.GESTURES,
            parentScreen = context.getString(R.string.settings_section_user_interface)
        ))
        add(SearchableSettingItem(
            id = "auto_focus_search",
            title = context.getString(R.string.settings_show_keyboard_on_search_open),
            description = context.getString(R.string.settings_show_keyboard_on_search_open_desc),
            keywords = listOf(
                "search",
                "keyboard",
                "focus",
                "auto focus",
                "search screen",
                "open keyboard",
                "auto keyboard"
            ),
            icon = RhythmIcons.Search,
            route = null,
            parentScreen = context.getString(R.string.settings_section_user_interface),
            settingKey = "showKeyboardOnSearchOpen"
        ))
        
        // Audio & Playback Section
        add(SearchableSettingItem(
            id = "system_volume",
            title = context.getString(R.string.settings_system_volume),
            description = context.getString(R.string.settings_system_volume_desc),
            keywords = listOf("volume", "system volume", "audio", "sound", "media volume"),
            icon = RhythmIcons.Player.VolumeUp,
            route = SettingsRoutes.PLAYBACK,
            parentScreen = context.getString(R.string.settings_section_queue_playback),
            settingKey = "useSystemVolume"
        ))
        add(SearchableSettingItem(
            id = "resume_on_device_reconnect",
            title = context.getString(R.string.settings_resume_on_device_reconnect),
            description = context.getString(R.string.settings_resume_on_device_reconnect_desc),
            keywords = listOf("resume", "device", "reconnect", "bluetooth", "headphones", "audio device", "playback"),
            icon = RhythmIcons.Devices.Bluetooth,
            route = SettingsRoutes.PLAYBACK,
            parentScreen = context.getString(R.string.settings_section_queue_playback),
            settingKey = "resumeOnDeviceReconnect"
        ))
        add(SearchableSettingItem(
            id = "show_lyrics",
            title = context.getString(R.string.settings_show_lyrics),
            description = context.getString(R.string.settings_show_lyrics_desc),
            keywords = listOf("lyrics", "show", "display", "text", "song words"),
            icon = MaterialSymbolIcon("lyrics"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = context.getString(R.string.settings_player_customization),
            settingKey = "showLyrics"
        ))
        add(SearchableSettingItem(
            id = "lyrics_source",
            title = context.getString(R.string.lyrics_source_priority),
            description = context.getString(R.string.playback_lyrics_priority_desc),
            keywords = listOf("lyrics", "synced lyrics", "lrc", "subtitle", "song text", "karaoke", "source", "priority"),
            icon = MaterialSymbolIcon("lyrics"),
            route = SettingsRoutes.LYRICS,
            parentScreen = context.getString(R.string.settings_lyrics_source),
            settingKey = "lyricsSource"
        ))
        add(SearchableSettingItem(
            id = "lyrics_api_priority",
            title = context.getString(R.string.lyricssourcesettingsscreen_lyrics_api_priority),
            description = context.getString(R.string.lyrics_api_priority_desc),
            keywords = listOf("lyrics", "api", "priority", "lyrically", "lrclib", "online"),
            icon = MaterialSymbolIcon("lyrics"),
            route = SettingsRoutes.LYRICS,
            parentScreen = context.getString(R.string.settings_lyrics_source),
            settingKey = "lyricsApiPriority"
        ))
        add(SearchableSettingItem(
            id = "lyrics_api_fallback",
            title = context.getString(R.string.lyricssourcesettingsscreen_retry_using_fallbacks),
            description = context.getString(R.string.lyrics_api_fallback_desc),
            keywords = listOf("lyrics", "fallback", "retry", "api", "online"),
            icon = MaterialSymbolIcon("compare_arrows"),
            route = SettingsRoutes.LYRICS,
            parentScreen = context.getString(R.string.settings_lyrics_source),
            settingKey = "lyricsApiFallbackRetry"
        ))
        add(SearchableSettingItem(
            id = "lyrics_lrc_rename_behavior",
            title = context.getString(R.string.lyrics_lrc_rename_behavior),
            description = context.getString(R.string.lyrics_lrc_rename_behavior_desc),
            keywords = listOf("lyrics", "lrc", "rename", "behavior", "ask", "always", "never", "tag", "file"),
            icon = MaterialSymbolIcon("drive_file_rename_outline"),
            route = SettingsRoutes.LYRICS,
            parentScreen = context.getString(R.string.settings_lyrics_source),
            settingKey = "lrcRenameBehavior"
        ))
        add(SearchableSettingItem(
            id = "queue_settings",
            title = context.getString(R.string.settings_queue_title),
            description = context.getString(R.string.settings_queue_desc),
            keywords = listOf("queue", "shuffle", "auto queue", "playlist", "clear", "remember"),
            icon = RhythmIcons.Queue,
            route = SettingsRoutes.QUEUE,
            parentScreen = context.getString(R.string.settings_section_queue_playback)
        ))
        add(SearchableSettingItem(
            id = "playback_settings",
            title = context.getString(R.string.settings_playback_title),
            description = context.getString(R.string.settings_playback_desc),
            keywords = listOf("playback", "repeat", "shuffle", "stop", "gapless", "crossfade", "hours", "duration"),
            icon = RhythmIcons.Play,
            route = SettingsRoutes.PLAYBACK,
            parentScreen = context.getString(R.string.settings_section_queue_playback)
        ))
        add(SearchableSettingItem(
            id = "equalizer",
            title = context.getString(R.string.settings_equalizer_title),
            description = context.getString(R.string.settings_equalizer_desc),
            keywords = listOf("equalizer", "eq", "audio", "bass", "treble", "sound", "effects", "audio enhancement"),
            icon = RhythmIcons.Equalizer,
            route = SettingsRoutes.EQUALIZER,
            parentScreen = context.getString(R.string.settings_section_audio_lyrics)
        ))
        add(SearchableSettingItem(
            id = "battery_saver",
            title = context.getString(R.string.performancesettingsscreen_performance),
            description = context.getString(R.string.performancesettingsscreen_performance_desc),
            keywords = listOf("battery", "power", "saver", "offload", "haptics", "marquee", "optimize"),
            icon = MaterialSymbolIcon("battery_charging_full"),
            route = SettingsRoutes.BATTERY_SAVER,
            parentScreen = context.getString(R.string.settings_section_audio_lyrics)
        ))
        add(SearchableSettingItem(
            id = "audio_offload",
            title = context.getString(R.string.settingsscreen_audio_offload),
            description = context.getString(R.string.settingsscreen_audio_offload_desc),
            keywords = listOf("audio", "offload", "hardware", "dsp", "decode", "battery", "power"),
            icon = MaterialSymbolIcon("bolt"),
            route = SettingsRoutes.PLAYBACK,
            parentScreen = context.getString(R.string.settings_playback_title)
        ))
        add(SearchableSettingItem(
            id = "mono_audio",
            title = context.getString(R.string.settings_mono_audio),
            description = context.getString(R.string.settings_mono_audio_desc),
            keywords = listOf("mono", "mono audio", "downmix", "stereo", "single earpiece", "earpiece", "one ear", "center", "audio"),
            icon = MaterialSymbolIcon("graphic_eq"),
            route = SettingsRoutes.PLAYBACK,
            parentScreen = context.getString(R.string.settings_playback_title),
            settingKey = "monoAudioEnabled"
        ))
        add(SearchableSettingItem(
            id = "battery_saver_disable_haptics",
            title = context.getString(R.string.performancesettingsscreen_disable_haptics),
            description = context.getString(R.string.performancesettingsscreen_disable_haptics_desc),
            keywords = listOf("battery", "performance", "disable haptics", "vibration", "vibrate", "feedback"),
            icon = MaterialSymbolIcon("touch_app"),
            route = SettingsRoutes.BATTERY_SAVER,
            parentScreen = context.getString(R.string.performancesettingsscreen_performance),
            settingKey = "batterySaverDisableHaptics"
        ))
        add(SearchableSettingItem(
            id = "battery_saver_enable_offload",
            title = context.getString(R.string.performancesettingsscreen_enable_audio_offload),
            description = context.getString(R.string.performancesettingsscreen_enable_audio_offload_desc),
            keywords = listOf("battery", "performance", "audio offload", "dsp", "hardware decoding"),
            icon = MaterialSymbolIcon("bolt"),
            route = SettingsRoutes.BATTERY_SAVER,
            parentScreen = context.getString(R.string.performancesettingsscreen_performance),
            settingKey = "batterySaverEnableOffload"
        ))
        add(SearchableSettingItem(
            id = "battery_saver_disable_marquee",
            title = context.getString(R.string.performancesettingsscreen_disable_text_marquee),
            description = context.getString(R.string.performancesettingsscreen_disable_text_marquee_desc),
            keywords = listOf("battery", "performance", "disable marquee", "slide animation", "marquee"),
            icon = MaterialSymbolIcon("slideshow"),
            route = SettingsRoutes.BATTERY_SAVER,
            parentScreen = context.getString(R.string.performancesettingsscreen_performance),
            settingKey = "batterySaverDisableMarquee"
        ))
        add(SearchableSettingItem(
            id = "battery_saver_disable_lossless_artwork",
            title = context.getString(R.string.performancesettingsscreen_disable_lossless_artwork),
            description = context.getString(R.string.performancesettingsscreen_disable_lossless_artwork_desc),
            keywords = listOf("battery", "performance", "disable lossless artwork", "artwork quality", "compressed art"),
            icon = RhythmIcons.Image,
            route = SettingsRoutes.BATTERY_SAVER,
            parentScreen = context.getString(R.string.performancesettingsscreen_performance),
            settingKey = "batterySaverDisableLosslessArtwork"
        ))
        add(SearchableSettingItem(
            id = "battery_saver_disable_auto_fetch_artwork",
            title = context.getString(R.string.performancesettingsscreen_disable_auto_fetch_artwork),
            description = context.getString(R.string.performancesettingsscreen_disable_auto_fetch_artwork_desc),
            keywords = listOf("battery", "performance", "disable auto fetch artwork", "network load", "lag"),
            icon = MaterialSymbolIcon("cloud_off"),
            route = SettingsRoutes.BATTERY_SAVER,
            parentScreen = context.getString(R.string.performancesettingsscreen_performance),
            settingKey = "batterySaverDisableAutoFetchArtwork"
        ))

        
        // Library & Media Section
        add(SearchableSettingItem(
            id = "media_scan",
            title = context.getString(R.string.settings_media_scan_title),
            description = context.getString(R.string.settings_media_scan_desc),
            keywords = listOf("media", "scan", "folder", "exclude", "include", "library", "music folder", "directory"),
            icon = RhythmIcons.Folder,
            route = SettingsRoutes.MEDIA_SCAN,
            parentScreen = context.getString(R.string.settings_section_library_content)
        ))
        add(SearchableSettingItem(
            id = "media_scan_hidden_whitelist",
            title = context.getString(R.string.settings_include_hidden_whitelisted_media),
            description = context.getString(R.string.settings_include_hidden_whitelisted_media_desc),
            keywords = listOf("hidden", "nomedia", "whitelist", "scan behavior", "folders", "media scan"),
            icon = RhythmIcons.Visibility,
            route = SettingsRoutes.MEDIA_SCAN,
            parentScreen = context.getString(R.string.settings_section_library_content),
            settingKey = "includeHiddenWhitelistedMedia"
        ))
        add(SearchableSettingItem(
            id = "media_scan_mode",
            title = context.getString(R.string.settings_whitelist_mode),
            description = context.getString(R.string.settings_whitelist_mode_desc),
            keywords = listOf("scan mode", "whitelist", "blacklist", "allowlist", "blocklist", "media scan", "folder filter"),
            icon = MaterialSymbolIcon("filter_list"),
            route = SettingsRoutes.MEDIA_SCAN,
            parentScreen = context.getString(R.string.settings_media_scan_title),
            settingKey = "mediaScanMode"
        ))
        add(SearchableSettingItem(
            id = "media_manage_folders",
            title = context.getString(R.string.settings_manage_folders),
            description = context.getString(R.string.settings_manage_folders_desc),
            keywords = listOf("folders", "manage", "whitelisted folders", "blocked folders", "media scan"),
            icon = MaterialSymbolIcon("folder_open"),
            route = SettingsRoutes.MEDIA_SCAN,
            parentScreen = context.getString(R.string.settings_media_scan_title)
        ))
        add(SearchableSettingItem(
            id = "media_add_folder",
            title = context.getString(R.string.settings_add_folder),
            description = context.getString(R.string.settings_add_folder_desc),
            keywords = listOf("add folder", "browse", "pick folder", "music folder", "include"),
            icon = MaterialSymbolIcon("add"),
            route = SettingsRoutes.MEDIA_SCAN,
            parentScreen = context.getString(R.string.settings_media_scan_title)
        ))
        add(SearchableSettingItem(
            id = "media_manage_songs",
            title = context.getString(R.string.settings_manage_songs),
            description = context.getString(R.string.settings_manage_songs_desc),
            keywords = listOf("songs", "manage", "blocked songs", "hidden songs", "media scan"),
            icon = MaterialSymbolIcon("library_music"),
            route = SettingsRoutes.MEDIA_SCAN,
            parentScreen = context.getString(R.string.settings_media_scan_title)
        ))
        add(SearchableSettingItem(
            id = "media_clear_all_songs",
            title = context.getString(R.string.settings_clear_all_songs),
            description = context.getString(R.string.settings_clear_all_songs_desc, context.getString(R.string.settings_blocked)),
            keywords = listOf("clear", "all songs", "remove songs", "blocked songs", "whitelisted songs", "reset", "media scan"),
            icon = MaterialSymbolIcon("delete_sweep"),
            route = SettingsRoutes.MEDIA_SCAN,
            parentScreen = context.getString(R.string.settings_media_scan_title)
        ))
        add(SearchableSettingItem(
            id = "media_clear_all_folders",
            title = context.getString(R.string.settings_clear_all_folders),
            description = context.getString(R.string.settings_clear_all_folders_desc, context.getString(R.string.settings_blocked)),
            keywords = listOf("clear", "all folders", "remove folders", "blocked folders", "whitelisted folders", "reset", "media scan"),
            icon = MaterialSymbolIcon("delete_sweep"),
            route = SettingsRoutes.MEDIA_SCAN,
            parentScreen = context.getString(R.string.settings_media_scan_title)
        ))
        add(SearchableSettingItem(
            id = "media_allowed_formats",
            title = context.getString(R.string.settings_allowed_formats),
            description = context.getString(R.string.settings_allowed_formats_open_desc),
            keywords = listOf(
                "formats", "audio formats", "file types", "extensions", "codec", "mp3", "flac", "m4a",
                "aac", "ogg", "opus", "wav", "alac", "ape", "wv", "dsd", "dts", "mp4", "mkv",
                "allowed", "scan", "include formats", "exclude formats"
            ),
            icon = RhythmIcons.MusicNote,
            route = SettingsRoutes.MEDIA_SCAN,
            parentScreen = context.getString(R.string.settings_media_scan_title),
            settingKey = "allowedFormats"
        ))
        add(SearchableSettingItem(
            id = "media_minimum_duration",
            title = context.getString(R.string.settings_min_duration),
            description = context.getString(R.string.settings_min_duration_desc),
            keywords = listOf(
                "duration", "minimum duration", "filter", "short tracks", "opera", "ringtones",
                "length", "song duration", "media scan", "filter duration", "scan filter"
            ),
            icon = RhythmIcons.Player.Timer,
            route = SettingsRoutes.MEDIA_SCAN,
            parentScreen = context.getString(R.string.settings_media_scan_title),
            settingKey = "minimumDuration"
        ))
        add(SearchableSettingItem(
            id = "artist_parsing",
            title = context.getString(R.string.settings_artist_parsing),
            description = context.getString(R.string.settings_artist_parsing_desc),
            keywords = listOf("artist", "parsing", "separator", "featuring", "collaboration", "split", "feat"),
            icon = RhythmIcons.Artist,
            route = SettingsRoutes.ARTIST_SEPARATORS,
            parentScreen = context.getString(R.string.settings_section_library_content)
        ))
        add(SearchableSettingItem(
            id = "artist_separation",
            title = context.getString(R.string.artist_enable_separation),
            description = context.getString(R.string.artist_enable_separation_desc),
            keywords = listOf("artist", "separation", "split", "feat", "featuring", "collaboration", "parsing"),
            icon = RhythmIcons.Artist,
            route = SettingsRoutes.ARTIST_SEPARATORS,
            parentScreen = context.getString(R.string.settings_artist_parsing),
            settingKey = "artistSeparatorEnabled"
        ))
        add(SearchableSettingItem(
            id = "artist_multi_parsing",
            title = context.getString(R.string.artist_multi_parsing),
            description = context.getString(R.string.settings_about_multi_artist),
            keywords = listOf("artist", "multi", "multiple artists", "delimiters", "ampersand", "comma", "parsing"),
            icon = RhythmIcons.Artist,
            route = SettingsRoutes.ARTIST_SEPARATORS,
            parentScreen = context.getString(R.string.settings_artist_parsing)
        ))
        add(SearchableSettingItem(
            id = "playlists",
            title = context.getString(R.string.settings_playlists_title),
            description = context.getString(R.string.settings_playlists_desc),
            keywords = listOf("playlist", "m3u", "import", "export", "manage", "collection"),
            icon = MaterialSymbolIcon("playlist_add_check_circle"),
            route = SettingsRoutes.PLAYLISTS,
            parentScreen = context.getString(R.string.settings_section_library_content)
        ))
        add(SearchableSettingItem(
            id = "default_playlists_enabled",
            title = context.getString(R.string.settings_enable_default_playlists),
            description = context.getString(R.string.settings_enable_default_playlists_desc),
            keywords = listOf("default playlists", "recently added", "most played", "auto playlist"),
            icon = RhythmIcons.Library,
            route = SettingsRoutes.PLAYLISTS,
            parentScreen = context.getString(R.string.settings_playlists_title),
            settingKey = "defaultPlaylistsEnabled"
        ))
        add(SearchableSettingItem(
            id = "playlists_create",
            title = context.getString(R.string.settings_create_new_playlist),
            description = context.getString(R.string.settings_create_new_playlist_desc),
            keywords = listOf("playlist", "create", "new", "add"),
            icon = MaterialSymbolIcon("playlist_add"),
            route = SettingsRoutes.PLAYLISTS,
            parentScreen = context.getString(R.string.settings_playlists_title)
        ))
        add(SearchableSettingItem(
            id = "playlists_export",
            title = context.getString(R.string.settings_export_all_playlists),
            description = context.getString(R.string.settings_export_all_playlists_desc),
            keywords = listOf("playlist", "export", "m3u", "save", "backup"),
            icon = MaterialSymbolIcon("upload"),
            route = SettingsRoutes.PLAYLISTS,
            parentScreen = context.getString(R.string.settings_playlists_title)
        ))
        add(SearchableSettingItem(
            id = "playlists_import",
            title = context.getString(R.string.settings_import_playlists),
            description = context.getString(R.string.settings_import_playlists_desc),
            keywords = listOf("playlist", "import", "m3u", "load", "restore"),
            icon = MaterialSymbolIcon("download"),
            route = SettingsRoutes.PLAYLISTS,
            parentScreen = context.getString(R.string.settings_playlists_title)
        ))
        add(SearchableSettingItem(
            id = "playlists_cleanup_empty",
            title = context.getString(R.string.settings_cleanup_empty_playlists),
            description = context.resources.safeGetQuantityString(R.plurals.settings_cleanup_empty_playlists_desc, 1, 1),
            keywords = listOf("playlist", "cleanup", "empty", "remove", "delete"),
            icon = MaterialSymbolIcon("delete_sweep"),
            route = SettingsRoutes.PLAYLISTS,
            parentScreen = context.getString(R.string.settings_playlists_title)
        ))
        add(SearchableSettingItem(
            id = "library_settings",
            title = context.getString(R.string.settings_library_settings),
            description = context.getString(R.string.settings_library_settings_desc),
            keywords = listOf("library", "settings", "song ratings", "artwork", "album artist", "cover", "blur", "gradient"),
            icon = RhythmIcons.Library,
            route = SettingsRoutes.LIBRARY_SETTINGS,
            parentScreen = context.getString(R.string.settings_section_library_content)
        ))
        add(SearchableSettingItem(
            id = "library_tab_order",
            title = context.getString(R.string.settings_library_tab_order),
            description = context.getString(R.string.settings_library_tab_order_desc),
            keywords = listOf("library", "tab", "order", "reorder", "visibility", "hide tab", "show tab"),
            icon = MaterialSymbolIcon("reorder"),
            route = SettingsRoutes.LIBRARY_SETTINGS,
            parentScreen = context.getString(R.string.settings_library_settings),
            settingKey = "libraryTabOrder"
        ))
        add(SearchableSettingItem(
            id = "library_combine_discs",
            title = context.getString(R.string.settings_library_combine_discs),
            description = context.getString(R.string.settings_library_combine_discs_desc),
            keywords = listOf("disc", "multi-disc", "combine", "album", "sorting", "track list"),
            icon = RhythmIcons.Album,
            route = SettingsRoutes.LIBRARY_SETTINGS,
            parentScreen = context.getString(R.string.settings_library_settings),
            settingKey = "libraryCombineDiscs"
        ))
        add(SearchableSettingItem(
            id = "show_library_bottom_bar_always",
            title = context.getString(R.string.settings_show_library_bottom_bar_always),
            description = context.getString(R.string.settings_show_library_bottom_bar_always_desc),
            keywords = listOf("bottom bar", "library", "visible", "visibility", "always", "hide", "show"),
            icon = MaterialSymbolIcon("view_agenda"),
            route = SettingsRoutes.LIBRARY_SETTINGS,
            parentScreen = context.getString(R.string.settings_library_settings),
            settingKey = "showLibraryBottomBarAlways"
        ))
        add(SearchableSettingItem(
            id = "auto_fetch_artwork",
            title = context.getString(R.string.librarysettingsscreen_autofetch_artwork),
            description = context.getString(R.string.library_auto_fetch_artwork_desc),
            keywords = listOf("auto fetch", "fetch artwork", "online artwork", "missing cover", "embed artwork", "auto fetch artwork", "track artwork", "startup"),
            icon = MaterialSymbolIcon("cloud_download"),
            route = SettingsRoutes.LIBRARY_SETTINGS,
            parentScreen = context.getString(R.string.settings_library_settings),
            settingKey = "autoFetchArtwork"
        ))
        add(SearchableSettingItem(
            id = "prefer_song_artwork",
            title = context.getString(R.string.settings_ignore_mediastore_covers),
            description = context.getString(R.string.settings_ignore_mediastore_covers_desc),
            keywords = listOf("song art", "album art", "artwork", "cover", "mediastore", "embedded", "prefer song"),
            icon = RhythmIcons.Album,
            route = SettingsRoutes.LIBRARY_SETTINGS,
            parentScreen = context.getString(R.string.settings_library_settings),
            settingKey = "preferSongArtwork"
        ))
        add(SearchableSettingItem(
            id = "artist_artwork_source",
            title = context.getString(R.string.settings_artist_artwork_source),
            description = context.getString(R.string.settings_artist_artwork_source_desc),
            keywords = listOf("artist image", "artist art", "artist photo", "artist cover", "deezer", "local image", "artist.jpg", "band.jpg", "api", "artwork source"),
            icon = RhythmIcons.Artist,
            route = SettingsRoutes.LIBRARY_SETTINGS,
            parentScreen = context.getString(R.string.settings_library_settings),
            settingKey = "artistArtworkSource"
        ))

        
        // Notifications & Services Section
        add(SearchableSettingItem(
            id = "notifications",
            title = context.getString(R.string.settings_notifications),
            description = context.getString(R.string.settings_notifications_desc),
            keywords = listOf("notification", "alert", "media control", "playback notification", "status bar"),
            icon = RhythmIcons.Notifications,
            route = SettingsRoutes.NOTIFICATIONS,
            parentScreen = context.getString(R.string.settings_section_notifications_services)
        ))
        add(SearchableSettingItem(
            id = "api_management",
            title = context.getString(R.string.settings_api_management),
            description = context.getString(R.string.settings_api_management_desc),
            keywords = listOf("api", "spotify", "integration", "services"),
            icon = MaterialSymbolIcon("api"),
            route = SettingsRoutes.API_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_section_notifications_services)
        ))
        add(SearchableSettingItem(
            id = "api_deezer",
            title = context.getString(R.string.onboarding_integration_deezer),
            description = context.getString(R.string.settings_search_api_deezer_desc),
            keywords = listOf("deezer", "api", "integration", "artwork", "artist images", "online"),
            icon = MaterialSymbolIcon("cloud"),
            route = SettingsRoutes.API_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_api_management),
            settingKey = "deezerApiEnabled"
        ))
        add(SearchableSettingItem(
            id = "api_lrclib",
            title = context.getString(R.string.onboarding_integration_lrclib),
            description = context.getString(R.string.settings_search_api_lrclib_desc),
            keywords = listOf("lrclib", "api", "lyrics", "synced", "lrc"),
            icon = MaterialSymbolIcon("lyrics"),
            route = SettingsRoutes.API_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_api_management),
            settingKey = "lrcLibApiEnabled"
        ))
        add(SearchableSettingItem(
            id = "api_wikipedia",
            title = context.getString(R.string.onboarding_integration_wikipedia),
            description = context.getString(R.string.onboarding_integration_wikipedia_desc),
            keywords = listOf("wikipedia", "api", "artist info", "biography", "online"),
            icon = MaterialSymbolIcon("public"),
            route = SettingsRoutes.API_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_api_management),
            settingKey = "wikipediaApiEnabled"
        ))
        add(SearchableSettingItem(
            id = "api_ytmusic",
            title = context.getString(R.string.onboarding_integration_ytmusic),
            description = context.getString(R.string.settings_search_api_ytmusic_desc),
            keywords = listOf("youtube music", "ytmusic", "api", "search", "integration"),
            icon = MaterialSymbolIcon("music_video"),
            route = SettingsRoutes.API_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_api_management),
            settingKey = "yTMusicApiEnabled"
        ))
        add(SearchableSettingItem(
            id = "api_lyrically",
            title = context.getString(R.string.apimanagementsettingsscreen_lyrically),
            description = context.getString(R.string.settings_search_api_lyrically_desc),
            keywords = listOf("lyrically", "api", "lyrics", "online", "fetch"),
            icon = MaterialSymbolIcon("lyrics"),
            route = SettingsRoutes.API_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_api_management),
            settingKey = "lyricallyApiEnabled"
        ))
        add(SearchableSettingItem(
            id = "api_github",
            title = context.getString(R.string.apimanagementsettingsscreen_github),
            description = context.getString(R.string.api_github_desc),
            keywords = listOf("github", "api", "releases", "integration", "source"),
            icon = MaterialSymbolIcon("code"),
            route = SettingsRoutes.API_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_api_management)
        ))
        add(SearchableSettingItem(
            id = "api_app_updates",
            title = context.getString(R.string.apimanagementsettingsscreen_app_updates_and_release),
            description = context.getString(R.string.api_app_updates_desc),
            keywords = listOf("updates", "release", "app updates", "changelog", "version"),
            icon = MaterialSymbolIcon("system_update"),
            route = SettingsRoutes.API_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_api_management)
        ))
        
        // Data & Storage Section
        add(SearchableSettingItem(
            id = "cache_management",
            title = context.getString(R.string.settings_cache_management_title),
            description = context.getString(R.string.settings_cache_management_desc),
            keywords = listOf("cache", "storage", "clear", "delete", "memory", "disk space", "images", "album art"),
            icon = RhythmIcons.Storage,
            route = SettingsRoutes.CACHE_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_section_storage_data)
        ))
        add(SearchableSettingItem(
            id = "artwork_cache_size",
            title = context.getString(R.string.settings_artwork_cache_size),
            description = context.getString(R.string.cache_current_status),
            keywords = listOf("artwork cache", "embedded art", "song art cache", "cache size", "album art files", "image cache", "trim artwork cache", "clear all cache"),
            icon = RhythmIcons.Storage,
            route = SettingsRoutes.CACHE_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_cache_management_title)
        ))
        add(SearchableSettingItem(
            id = "cache_max_size",
            title = context.getString(R.string.cache_max_size),
            description = context.getString(R.string.settings_cache_max_size_desc),
            keywords = listOf("cache", "max size", "limit", "storage", "mb", "gb"),
            icon = MaterialSymbolIcon("sd_storage"),
            route = SettingsRoutes.CACHE_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_cache_management_title),
            settingKey = "maxCacheSize"
        ))
        add(SearchableSettingItem(
            id = "cache_auto_trim",
            title = context.getString(R.string.cache_auto_trim),
            description = context.getString(R.string.cache_auto_trim_desc),
            keywords = listOf("cache", "auto trim", "automatic", "cleanup", "free space"),
            icon = MaterialSymbolIcon("auto_delete"),
            route = SettingsRoutes.CACHE_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_cache_management_title)
        ))
        add(SearchableSettingItem(
            id = "cache_clear_all",
            title = context.getString(R.string.settings_clear_all_cache),
            description = context.getString(R.string.settings_clear_all_cache_desc),
            keywords = listOf("clear", "cache", "delete", "free space", "wipe"),
            icon = MaterialSymbolIcon("delete_sweep"),
            route = SettingsRoutes.CACHE_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_cache_management_title)
        ))
        add(SearchableSettingItem(
            id = "cache_clear_lyrics",
            title = context.getString(R.string.settings_clear_lyrics_cache),
            description = context.getString(R.string.settings_clear_lyrics_cache_desc),
            keywords = listOf("clear", "lyrics cache", "delete", "cached lyrics"),
            icon = MaterialSymbolIcon("lyrics"),
            route = SettingsRoutes.CACHE_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_cache_management_title)
        ))
        add(SearchableSettingItem(
            id = "cache_rebuild_storage",
            title = context.getString(R.string.settings_storage_rebuild_room),
            description = context.getString(R.string.settings_storage_rebuild_room_desc),
            keywords = listOf("rebuild", "storage", "database", "room", "recreate", "fix"),
            icon = MaterialSymbolIcon("dns"),
            route = SettingsRoutes.CACHE_MANAGEMENT,
            parentScreen = context.getString(R.string.settings_cache_management_title)
        ))
        add(SearchableSettingItem(
            id = "backup_restore",
            title = context.getString(R.string.settings_backup_restore_title),
            description = context.getString(R.string.settings_backup_restore_desc),
            keywords = listOf("backup", "restore", "export", "import", "settings", "playlists", "data"),
            icon = MaterialSymbolIcon("backup"),
            route = SettingsRoutes.BACKUP_RESTORE,
            parentScreen = context.getString(R.string.settings_section_storage_data)
        ))
        add(SearchableSettingItem(
            id = "backup_create",
            title = context.getString(R.string.settings_create_backup),
            description = context.getString(R.string.settings_create_backup_desc),
            keywords = listOf("backup", "create", "save", "export", "snapshot"),
            icon = MaterialSymbolIcon("backup"),
            route = SettingsRoutes.BACKUP_RESTORE,
            parentScreen = context.getString(R.string.settings_backup_restore_title)
        ))
        add(SearchableSettingItem(
            id = "backup_restore_clipboard",
            title = context.getString(R.string.settings_restore_clipboard),
            description = context.getString(R.string.settings_restore_clipboard_desc),
            keywords = listOf("restore", "clipboard", "paste", "import", "recover"),
            icon = MaterialSymbolIcon("content_paste"),
            route = SettingsRoutes.BACKUP_RESTORE,
            parentScreen = context.getString(R.string.settings_backup_restore_title)
        ))
        add(SearchableSettingItem(
            id = "backup_restore_file",
            title = context.getString(R.string.settings_restore_file),
            description = context.getString(R.string.settings_restore_file_desc),
            keywords = listOf("restore", "file", "open", "import", "recover"),
            icon = MaterialSymbolIcon("file_open"),
            route = SettingsRoutes.BACKUP_RESTORE,
            parentScreen = context.getString(R.string.settings_backup_restore_title)
        ))
        add(SearchableSettingItem(
            id = "backup_auto_backup",
            title = context.getString(R.string.settings_auto_backup),
            description = context.getString(R.string.settings_auto_backup_desc),
            keywords = listOf("auto backup", "automatic", "schedule", "periodic"),
            icon = MaterialSymbolIcon("autorenew"),
            route = SettingsRoutes.BACKUP_RESTORE,
            parentScreen = context.getString(R.string.settings_backup_restore_title),
            settingKey = "autoBackupEnabled"
        ))
        add(SearchableSettingItem(
            id = "rhythm_stats",
            title = context.getString(R.string.settings_rhythm_stats),
            description = context.getString(R.string.settings_rhythm_stats_desc),
            keywords = listOf("stats", "statistics", "listening", "history", "play count", "most played", "analytics"),
            icon = MaterialSymbolIcon("auto_graph"),
            route = SettingsRoutes.RHYTHM_STATS,
            parentScreen = context.getString(R.string.settings_section_storage_data)
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard",
            title = context.getString(R.string.settings_rhythm_guard),
            description = context.getString(R.string.settings_rhythm_guard_list_desc),
            keywords = listOf("aura", "ear health", "hearing", "safe listening", "auto mode", "manual mode", "volume warning"),
            icon = RhythmIcons.Security,
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_section_storage_data)
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_mode",
            title = context.getString(R.string.settings_rhythm_guard_mode_title),
            description = context.getString(R.string.settings_rhythm_guard_mode_desc),
            keywords = listOf("auto", "manual", "off", "mode", "listening health mode"),
            icon = RhythmIcons.Security,
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "rhythmGuardMode"
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_enable",
            title = context.getString(R.string.settings_rhythm_guard_enable_search_title),
            description = context.getString(R.string.settings_rhythm_guard_enable_search_desc),
            keywords = listOf("enable", "disable", "on", "off", "protection switch", "guard toggle"),
            icon = RhythmIcons.Security,
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "rhythmGuardMode"
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_age",
            title = context.getString(R.string.settings_rhythm_guard_age_search_title),
            description = context.getString(R.string.settings_rhythm_guard_age_search_desc),
            keywords = listOf("age", "hearing profile", "safe volume", "daily limit", "ear health"),
            icon = RhythmIcons.Artist,
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "rhythmGuardAge"
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_manual_warnings",
            title = context.getString(R.string.settings_rhythm_guard_manual_warning_toggle),
            description = context.getString(R.string.settings_rhythm_guard_manual_warning_toggle_desc),
            keywords = listOf("warning", "manual", "volume warning", "risk warning", "health warning"),
            icon = RhythmIcons.Warning,
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "rhythmGuardManualWarningsEnabled"
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_threshold",
            title = context.getString(R.string.settings_rhythm_guard_manual_threshold_search_title),
            description = context.getString(R.string.settings_rhythm_guard_manual_threshold_search_desc),
            keywords = listOf("threshold", "safe volume", "manual threshold", "volume limit", "ear safety"),
            icon = MaterialSymbolIcon("graphic_eq"),
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "rhythmGuardManualVolumeThreshold"
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_alert_threshold",
            title = context.getString(R.string.settings_rhythm_guard_alert_threshold_search_title),
            description = context.getString(R.string.settings_rhythm_guard_alert_threshold_search_desc),
            keywords = listOf("exposure", "alert threshold", "daily limit", "minutes", "safety alert"),
            icon = MaterialSymbolIcon("timer"),
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "rhythmGuardAlertThresholdMinutes"
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_warning_timeout",
            title = context.getString(R.string.settings_rhythm_guard_warning_timeout_search_title),
            description = context.getString(R.string.settings_rhythm_guard_warning_timeout_search_desc),
            keywords = listOf("cooldown", "alert timeout", "repeat warning", "warning interval"),
            icon = RhythmIcons.AccessTime,
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "rhythmGuardWarningTimeoutMinutes"
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_post_timeout_cooldown",
            title = context.getString(R.string.settings_rhythm_guard_post_timeout_cooldown_search_title),
            description = context.getString(R.string.settings_rhythm_guard_post_timeout_cooldown_search_desc),
            keywords = listOf("post-timeout", "recovery cooldown", "timeout cooldown", "break cooldown"),
            icon = RhythmIcons.AccessTime,
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "rhythmGuardPostTimeoutCooldownMinutes"
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_protection_presets",
            title = context.getString(R.string.settings_rhythm_guard_protection_presets_search_title),
            description = context.getString(R.string.settings_rhythm_guard_protection_presets_search_desc),
            keywords = listOf("preset", "strict", "balanced", "gentle", "quick setup", "guard profile"),
            icon = RhythmIcons.Security,
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "rhythmGuardProtectionPreset"
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_break_resume",
            title = context.getString(R.string.settings_rhythm_guard_break_resume_search_title),
            description = context.getString(R.string.settings_rhythm_guard_break_resume_search_desc),
            keywords = listOf("break", "resume", "timeout length", "scheduled break", "pause duration"),
            icon = RhythmIcons.AccessTime,
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "rhythmGuardBreakResumeMinutes"
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_zero_volume",
            title = context.getString(R.string.settings_stop_playback_on_zero_volume),
            description = context.getString(R.string.settings_stop_playback_on_zero_volume_desc),
            keywords = listOf("zero volume", "pause on zero", "mute protection", "auto pause"),
            icon = RhythmIcons.Stop,
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "stopPlaybackOnZeroVolume"
        ))
        add(SearchableSettingItem(
            id = "rhythm_guard_speaker_limit",
            title = context.getString(R.string.settings_rhythm_guard_device_controls_speaker_limit_title),
            description = context.getString(R.string.settings_rhythm_guard_device_controls_speaker_limit_desc),
            keywords = listOf("speaker limit", "speaker volume", "speaker", "volume limit", "hearing safety"),
            icon = RhythmIcons.Speaker,
            route = SettingsRoutes.RHYTHM_GUARD,
            parentScreen = context.getString(R.string.settings_rhythm_guard),
            settingKey = "rhythmGuardApplyVolumeLimitOnSpeaker"
        ))
        
        // Updates & Info Section
        add(SearchableSettingItem(
            id = "updates",
            title = context.getString(R.string.settings_updates_title),
            description = context.getString(R.string.settings_updates_desc),
            keywords = listOf("update", "check update", "new version", "download", "changelog", "auto update"),
            icon = RhythmIcons.Update,
            route = SettingsRoutes.UPDATES,
            parentScreen = context.getString(R.string.settings_section_updates_info),
            settingKey = "updatesEnabled"
        ))
        add(SearchableSettingItem(
            id = "updates_interval",
            title = context.getString(R.string.updates_check_interval_title),
            description = context.getString(R.string.onboarding_check_interval_desc),
            keywords = listOf("update interval", "check frequency", "hourly", "daily", "weekly", "polling schedule"),
            icon = RhythmIcons.AccessTime,
            route = SettingsRoutes.UPDATES,
            parentScreen = context.getString(R.string.settings_updates_title),
            settingKey = "updateCheckIntervalHours"
        ))
        add(SearchableSettingItem(
            id = "updates_channel",
            title = context.getString(R.string.updates_channel_title),
            description = context.getString(R.string.updates_channel_desc),
            keywords = listOf("update channel", "stable", "beta", "nightly", "release channel"),
            icon = MaterialSymbolIcon("autorenew"),
            route = SettingsRoutes.UPDATES,
            parentScreen = context.getString(R.string.settings_updates_title),
            settingKey = "updateChannel"
        ))
        add(SearchableSettingItem(
            id = "updates_enable",
            title = context.getString(R.string.updates_enable_updates),
            description = context.getString(R.string.settings_updates_enable_desc),
            keywords = listOf("update", "enable", "disable", "toggle", "switch"),
            icon = RhythmIcons.Update,
            route = SettingsRoutes.UPDATES,
            parentScreen = context.getString(R.string.settings_updates_title),
            settingKey = "updatesEnabled"
        ))
        add(SearchableSettingItem(
            id = "updates_auto_check",
            title = context.getString(R.string.updates_enable_auto_check),
            description = context.getString(R.string.settings_updates_auto_check_desc),
            keywords = listOf("update", "auto check", "automatic", "background"),
            icon = MaterialSymbolIcon("sync"),
            route = SettingsRoutes.UPDATES,
            parentScreen = context.getString(R.string.settings_updates_title),
            settingKey = "autoCheckForUpdates"
        ))
        add(SearchableSettingItem(
            id = "updates_manual_check",
            title = context.getString(R.string.updates_manual_check),
            description = context.getString(R.string.settings_updates_manual_check_desc),
            keywords = listOf("update", "check now", "manual", "refresh"),
            icon = MaterialSymbolIcon("refresh"),
            route = SettingsRoutes.UPDATES,
            parentScreen = context.getString(R.string.settings_updates_title)
        ))
        add(SearchableSettingItem(
            id = "updates_source",
            title = context.getString(R.string.updates_source_title),
            description = context.getString(R.string.updates_source_desc),
            keywords = listOf("update source", "github", "fdroid", "installed", "apk", "download source"),
            icon = MaterialSymbolIcon("cloud_download"),
            route = SettingsRoutes.UPDATES,
            parentScreen = context.getString(R.string.settings_updates_title),
            settingKey = "updateSource"
        ))
        add(SearchableSettingItem(
            id = "about",
            title = context.getString(R.string.settings_about_title),
            description = context.getString(R.string.settings_about_desc),
            keywords = listOf("about", "version", "app info", "credits", "developer", "github", "license"),
            icon = RhythmIcons.Info,
            route = SettingsRoutes.ABOUT,
            parentScreen = context.getString(R.string.settings_section_updates_info)
        ))
        
        // Advanced Section
        add(SearchableSettingItem(
            id = "crash_log_history",
            title = context.getString(R.string.settings_crash_log_history),
            description = context.getString(R.string.settings_crash_log_history_desc),
            keywords = listOf("crash", "log", "error", "bug", "debug", "report", "history"),
            icon = RhythmIcons.BugReport,
            route = SettingsRoutes.CRASH_LOG_HISTORY,
            parentScreen = context.getString(R.string.settings_section_advanced)
        ))
        add(SearchableSettingItem(
            id = "labs",
            title = context.getString(R.string.settings_labs),
            description = context.getString(R.string.settings_labs_desc),
            keywords = listOf("labs", "experimental", "beta", "testing", "new features", "developer", "debug", "advanced"),
            icon = MaterialSymbolIcon("science"),
            route = SettingsRoutes.LABS,
            parentScreen = context.getString(R.string.settings_section_advanced)
        ))
        add(SearchableSettingItem(
            id = "experimental_go_mode",
            title = context.getString(R.string.exp_go_mode),
            description = context.getString(R.string.exp_go_mode_desc),
            keywords = listOf("go mode", "rhythm go", "streaming mode", "streaming navigation", "integration"),
            icon = MaterialSymbolIcon("cloud_queue"),
            route = SettingsRoutes.LABS,
            parentScreen = context.getString(R.string.settings_labs)
        ))
        add(SearchableSettingItem(
            id = "go_preferred_service",
            title = context.getString(R.string.streaming_settings_preferred_service),
            description = context.getString(R.string.streaming_settings_service_sheet_desc),
            keywords = listOf("preferred service", "subsonic", "jellyfin", "streaming provider", "go settings"),
            icon = MaterialSymbolIcon("cloud_queue"),
            route = SettingsRoutes.GO_SETTINGS,
            parentScreen = context.getString(R.string.exp_go_mode)
        ))
        add(SearchableSettingItem(
            id = "go_streaming_quality",
            title = context.getString(R.string.streaming_settings_quality),
            description = context.getString(R.string.streaming_settings_quality_sheet_desc),
            keywords = listOf("streaming quality", "bitrate", "audio quality", "go settings", "data usage"),
            icon = MaterialSymbolIcon("speed"),
            route = SettingsRoutes.GO_SETTINGS,
            parentScreen = context.getString(R.string.exp_go_mode)
        ))
        add(SearchableSettingItem(
            id = "go_cellular_streaming",
            title = context.getString(R.string.exp_cellular_streaming),
            description = context.getString(R.string.gosettingsscreen_enable_streaming_over_mobile),
            keywords = listOf("cellular", "mobile data", "streaming over cellular", "mobile network", "data usage", "go settings"),
            icon = MaterialSymbolIcon("signal_cellular_alt"),
            route = SettingsRoutes.GO_SETTINGS,
            parentScreen = context.getString(R.string.exp_go_mode)
        ))
        
        // ======================== THEME CUSTOMIZATION SCREEN ========================
        add(SearchableSettingItem(
            id = "theme_follow_system",
            title = context.getString(R.string.settings_theme_follow_system),
            description = context.getString(R.string.settings_theme_follow_system_desc),
            keywords = listOf("system theme", "auto", "automatic", "follow system", "dark light"),
            icon = RhythmIcons.Settings,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme"
        ))
        add(SearchableSettingItem(
            id = "theme_dark_mode",
            title = context.getString(R.string.settings_theme_dark_mode),
            description = context.getString(R.string.settings_theme_dark_mode_desc),
            keywords = listOf("dark mode", "dark theme", "night mode", "black theme"),
            icon = RhythmIcons.DarkMode,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "darkMode"
        ))
        add(SearchableSettingItem(
            id = "theme_amoled_theme",
            title = context.getString(R.string.settings_amoled_theme),
            description = context.getString(R.string.settings_amoled_theme_desc),
            keywords = listOf("amoled", "pure black", "pitch black", "oled", "battery saver", "dark mode"),
            icon = RhythmIcons.DarkMode,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "amoledTheme"
        ))
        add(SearchableSettingItem(
            id = "theme_color_source",
            title = context.getString(R.string.settings_theme_color_source),
            description = context.getString(R.string.settings_theme_color_source_desc),
            keywords = listOf("color source", "album art colors", "monet", "material you", "dynamic colors", "custom colors"),
            icon = RhythmIcons.Palette,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme"
        ))
        add(SearchableSettingItem(
            id = "theme_use_exact_artwork_colors",
            title = context.getString(R.string.settings_use_exact_artwork_colors),
            description = context.getString(R.string.settings_use_exact_artwork_colors_desc),
            keywords = listOf("exact", "artwork colors", "album art colors", "dynamic theme", "dynamic background"),
            icon = RhythmIcons.Palette,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "useExactArtworkColors"
        ))
        add(SearchableSettingItem(
            id = "theme_color_schemes",
            title = context.getString(R.string.settings_theme_color_schemes),
            description = context.getString(R.string.settings_theme_color_schemes_desc),
            keywords = listOf("color scheme", "palette", "preset", "default purple", "warm sunset", "cool ocean", "forest green", "rose pink"),
            icon = MaterialSymbolIcon("color_lens"),
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme"
        ))
        add(SearchableSettingItem(
            id = "theme_font_source",
            title = context.getString(R.string.settings_theme_font_source),
            description = context.getString(R.string.settings_theme_font_source_desc),
            keywords = listOf("font", "typography", "text style", "font family", "custom font"),
            icon = MaterialSymbolIcon("text_fields"),
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme"
        ))
        add(SearchableSettingItem(
            id = "theme_import_font",
            title = context.getString(R.string.settings_theme_import_font),
            description = context.getString(R.string.settings_theme_import_font_desc),
            keywords = listOf("import font", "custom font", "ttf", "otf", "font file"),
            icon = MaterialSymbolIcon("file_upload"),
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme"
        ))
        add(SearchableSettingItem(
            id = "theme_font_selection",
            title = context.getString(R.string.settings_font_selection),
            description = context.getString(R.string.settings_font_selection_desc),
            keywords = listOf("font", "choose font", "font family", "typography", "custom font", "system font"),
            icon = MaterialSymbolIcon("text_fields"),
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "customFont"
        ))
        add(SearchableSettingItem(
            id = "theme_festive_enabled",
            title = context.getString(R.string.settings_enable_festive),
            description = context.getString(R.string.settings_enable_festive_desc),
            keywords = listOf("festive", "holiday", "christmas", "new year", "decorations", "theme"),
            icon = MaterialSymbolIcon("celebration"),
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "festiveThemeEnabled"
        ))
        add(SearchableSettingItem(
            id = "theme_festive_auto_detect",
            title = context.getString(R.string.settings_auto_detect_holidays),
            description = context.getString(R.string.settings_auto_detect_holidays_desc),
            keywords = listOf("auto detect", "holiday", "seasonal", "automatic", "festive"),
            icon = RhythmIcons.AutoAwesome,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "festiveThemeAutoDetect"
        ))
        add(SearchableSettingItem(
            id = "theme_festival_type",
            title = context.getString(R.string.settings_select_festival),
            description = context.getString(R.string.settings_choose_festive_theme),
            keywords = listOf("festival", "christmas", "new year", "holiday theme", "festive type"),
            icon = MaterialSymbolIcon("celebration"),
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "festiveThemeType"
        ))
        add(SearchableSettingItem(
            id = "theme_festive_intensity",
            title = context.getString(R.string.settings_decoration_intensity),
            description = context.getString(R.string.settings_adjust_festive_decorations),
            keywords = listOf("intensity", "decoration", "festive", "amount", "strength"),
            icon = MaterialSymbolIcon("linear_scale"),
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "festiveThemeIntensity"
        ))
        add(SearchableSettingItem(
            id = "theme_festive_snowflake_size",
            title = context.getString(R.string.settings_snowflake_size),
            description = context.getString(R.string.settings_adjust_snowflake_size),
            keywords = listOf("snowflake", "size", "snow", "festive", "particle size"),
            icon = MaterialSymbolIcon("linear_scale"),
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "festiveSnowflakeSize"
        ))
        add(SearchableSettingItem(
            id = "theme_festive_snowflake_area",
            title = context.getString(R.string.settings_snowflake_display_area),
            description = context.getString(R.string.settings_toggle_decoration_elements),
            keywords = listOf("snowflake area", "full", "sides", "top", "coverage", "festive"),
            icon = MaterialSymbolIcon("graphic_eq"),
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "festiveSnowflakeArea"
        ))
        add(SearchableSettingItem(
            id = "theme_festive_snowfall",
            title = context.getString(R.string.settings_snowfall),
            description = context.getString(R.string.settings_snowfall_desc),
            keywords = listOf("snowfall", "snow", "animation", "festive", "decoration"),
            icon = RhythmIcons.Visibility,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "festiveShowSnowfall"
        ))
        add(SearchableSettingItem(
            id = "theme_festive_top_lights",
            title = context.getString(R.string.settings_top_lights),
            description = context.getString(R.string.settings_top_lights_desc),
            keywords = listOf("top lights", "lights", "christmas lights", "festive", "decoration"),
            icon = RhythmIcons.Visibility,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "festiveShowTopLights"
        ))
        add(SearchableSettingItem(
            id = "theme_festive_side_garland",
            title = context.getString(R.string.settings_side_garland),
            description = context.getString(R.string.settings_side_garland_desc),
            keywords = listOf("side garland", "garland", "ornaments", "festive", "decoration"),
            icon = RhythmIcons.Visibility,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "festiveShowSideGarland"
        ))
        add(SearchableSettingItem(
            id = "theme_festive_snow_pile",
            title = context.getString(R.string.settings_snow_pile),
            description = context.getString(R.string.settings_snow_pile_desc),
            keywords = listOf("snow pile", "bottom snow", "festive", "decoration"),
            icon = RhythmIcons.Visibility,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "festiveShowBottomSnow"
        ))
        add(SearchableSettingItem(
            id = "theme_floating_navigation",
            title = context.getString(R.string.settings_floating_navigation),
            description = context.getString(R.string.settings_floating_navigation_desc),
            keywords = listOf("floating", "docked", "navigation", "navigation bar", "bottom bar", "rail", "dock", "float"),
            icon = MaterialSymbolIcon("dock_to_left"),
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = "Theme",
            settingKey = "floatingNavigationBar"
        ))
        
        // ======================== PLAYER CUSTOMIZATION SCREEN ========================
        add(SearchableSettingItem(
            id = "player_chip_order",
            title = context.getString(R.string.settings_player_chip_order),
            description = context.getString(R.string.settings_player_chip_order_desc),
            keywords = listOf("chip", "button order", "action chips", "player buttons", "reorder", "visibility"),
            icon = MaterialSymbolIcon("reorder"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player"
        ))
        add(SearchableSettingItem(
            id = "player_show_lyrics",
            title = context.getString(R.string.settings_show_lyrics_player),
            description = context.getString(R.string.settings_show_lyrics_player_desc),
            keywords = listOf("lyrics", "synced lyrics", "karaoke", "text", "song words"),
            icon = MaterialSymbolIcon("lyrics", filled = true),
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics"
        ))
        add(SearchableSettingItem(
            id = "lyrics_show_translation",
            title = context.getString(R.string.settings_lyrics_show_translation),
            description = context.getString(R.string.settings_lyrics_show_translation_desc),
            keywords = listOf("lyrics", "translation", "translate", "multi-language", "subtitle"),
            icon = RhythmIcons.Info,
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "showLyricsTranslation"
        ))
        add(SearchableSettingItem(
            id = "lyrics_show_romanization",
            title = context.getString(R.string.settings_lyrics_show_romanization),
            description = context.getString(R.string.settings_lyrics_show_romanization_desc),
            keywords = listOf("lyrics", "romanization", "romaji", "pinyin", "transliteration"),
            icon = MaterialSymbolIcon("text_fields"),
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "showLyricsRomanization"
        ))
        add(SearchableSettingItem(
            id = "keep_screen_on_lyrics",
            title = context.getString(R.string.settings_keep_screen_on_lyrics),
            description = context.getString(R.string.settings_keep_screen_on_lyrics_desc),
            keywords = listOf("screen", "awake", "wake", "lyrics", "screen on", "display", "timeout", "dim"),
            icon = RhythmIcons.Visibility,
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "keepScreenOnLyrics"
        ))
        add(SearchableSettingItem(
            id = "embed_lyrics_in_file",
            title = context.getString(R.string.settings_embed_lyrics_in_file),
            description = context.getString(R.string.settings_embed_lyrics_in_file_desc),
            keywords = listOf("embed", "lyrics", "file", "metadata", "write", "save", "tag", "id3"),
            icon = RhythmIcons.MusicNote,
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics"
        ))
        add(SearchableSettingItem(
            id = "lyrics_alignment",
            title = context.getString(R.string.settings_lyrics_alignment),
            description = context.getString(R.string.settings_lyrics_alignment_desc),
            keywords = listOf("lyrics", "alignment", "left", "center", "right", "text position"),
            icon = MaterialSymbolIcon("format_align_center"),
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "playerLyricsAlignment"
        ))
        add(SearchableSettingItem(
            id = "lyrics_text_size",
            title = context.getString(R.string.settings_lyrics_text_size),
            description = context.getString(R.string.lyrics_settings_size_percentage),
            keywords = listOf("lyrics", "text size", "font size", "percentage", "bigger", "smaller"),
            icon = MaterialSymbolIcon("format_size"),
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "playerLyricsTextSize"
        ))
        add(SearchableSettingItem(
            id = "lyrics_bold_text",
            title = context.getString(R.string.lyrics_settings_bold_text),
            description = context.getString(R.string.lyrics_settings_bold_text_desc),
            keywords = listOf("lyrics", "bold", "text", "weight", "font"),
            icon = MaterialSymbolIcon("format_bold"),
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "lyricBold"
        ))
        add(SearchableSettingItem(
            id = "lyrics_trim",
            title = context.getString(R.string.lyrics_settings_trim_lyrics),
            description = context.getString(R.string.lyrics_settings_trim_lyrics_desc),
            keywords = listOf("lyrics", "trim", "clean", "white space", "gaps"),
            icon = MaterialSymbolIcon("content_cut"),
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "trimLyrics"
        ))
        add(SearchableSettingItem(
            id = "lyrics_disable_animations",
            title = context.getString(R.string.lyrics_settings_disable_animations),
            description = context.getString(R.string.lyrics_settings_disable_animations_desc),
            keywords = listOf("lyrics", "animations", "disable", "smooth", "transitions"),
            icon = MaterialSymbolIcon("animation"),
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "lyricNoAnimation"
        ))
        add(SearchableSettingItem(
            id = "lyrics_autohide_controls",
            title = context.getString(R.string.lyrics_settings_autohide_controls),
            description = context.getString(R.string.lyrics_settings_autohide_controls_desc),
            keywords = listOf("lyrics", "auto hide", "controls", "immersive", "fullscreen"),
            icon = MaterialSymbolIcon("visibility_off"),
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "autoHideLyricsControls"
        ))
        add(SearchableSettingItem(
            id = "lyrics_word_by_word_translation",
            title = context.getString(R.string.lyrics_settings_word_by_word_translation),
            description = context.getString(R.string.lyrics_settings_word_by_word_translation_desc),
            keywords = listOf("lyrics", "translation", "word by word", "wordwise", "translate"),
            icon = MaterialSymbolIcon("translate"),
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "translationAutoWord"
        ))
        add(SearchableSettingItem(
            id = "lyrics_background_artwork",
            title = context.getString(R.string.settings_show_lyrics_background_artwork),
            description = context.getString(R.string.settings_show_lyrics_background_artwork_desc),
            keywords = listOf("lyrics", "background", "artwork", "blur", "ambient"),
            icon = MaterialSymbolIcon("wallpaper"),
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "showLyricsBackgroundArtwork"
        ))
        add(SearchableSettingItem(
            id = "lyrics_art_below",
            title = context.getString(R.string.settings_show_art_below_lyrics),
            description = context.getString(R.string.settings_show_art_below_lyrics_desc),
            keywords = listOf("lyrics", "artwork", "below", "cover art", "display"),
            icon = RhythmIcons.Album,
            route = SettingsRoutes.LYRICS,
            parentScreen = "Lyrics",
            settingKey = "playerShowArtBelowLyrics"
        ))
        add(SearchableSettingItem(
            id = "lossless_artwork",
            title = context.getString(R.string.settings_lossless_artwork),
            description = context.getString(R.string.settings_lossless_artwork_desc),
            keywords = listOf("lossless", "artwork", "png", "quality", "album art", "image", "uncompressed", "high quality"),
            icon = MaterialSymbolIcon("high_quality"),
            route = SettingsRoutes.LIBRARY_SETTINGS,
            parentScreen = context.getString(R.string.settings_library_settings),
            settingKey = "losslessArtwork"
        ))
        add(SearchableSettingItem(
            id = "player_gradient",
            title = context.getString(R.string.settings_player_gradient),
            description = context.getString(R.string.settings_player_gradient_desc),
            keywords = listOf("gradient", "overlay", "artwork gradient", "background"),
            icon = MaterialSymbolIcon("gradient"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player"
        ))
        add(SearchableSettingItem(
            id = "player_seek_buttons",
            title = context.getString(R.string.settings_player_seek_buttons),
            description = context.getString(R.string.settings_player_seek_buttons_desc),
            keywords = listOf("seek", "skip", "forward", "backward", "10 seconds", "rewind"),
            icon = RhythmIcons.Forward10,
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player"
        ))
        add(SearchableSettingItem(
            id = "player_text_alignment",
            title = context.getString(R.string.settings_player_text_alignment),
            description = context.getString(R.string.settings_player_text_alignment_desc),
            keywords = listOf("text", "alignment", "left", "center", "right", "title position"),
            icon = MaterialSymbolIcon("format_align_center"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player"
        ))
        add(SearchableSettingItem(
            id = "player_progress_style",
            title = context.getString(R.string.settings_player_progress_style),
            description = context.getString(R.string.settings_player_progress_style_desc),
            keywords = listOf("progress bar", "seekbar", "style", "wavy", "dotted", "dashed", "glowing"),
            icon = MaterialSymbolIcon("linear_scale"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player"
        ))
        add(SearchableSettingItem(
            id = "player_artwork_radius",
            title = context.getString(R.string.settings_player_artwork_radius),
            description = context.getString(R.string.settings_player_artwork_radius_desc),
            keywords = listOf("artwork", "corner", "radius", "rounded", "square", "album art shape"),
            icon = MaterialSymbolIcon("rounded_corner"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player"
        ))
        add(SearchableSettingItem(
            id = "player_quality_badges",
            title = context.getString(R.string.settings_player_quality_badges),
            description = context.getString(R.string.settings_player_quality_badges_desc),
            keywords = listOf("quality", "badge", "codec", "bitrate", "flac", "mp3", "audio format"),
            icon = MaterialSymbolIcon("high_quality"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player"
        ))
        add(SearchableSettingItem(
            id = "player_thumb_style",
            title = context.getString(R.string.settings_thumb_style),
            description = context.getString(R.string.settings_player_thumb_style_desc),
            keywords = listOf("thumb", "progress thumb", "seekbar handle", "slider knob", "style", "progress"),
            icon = MaterialSymbolIcon("touch_app"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player",
            settingKey = "playerProgressThumbStyle"
        ))
        add(SearchableSettingItem(
            id = "player_thumb_rotate",
            title = context.getString(R.string.settings_thumb_rotate),
            description = context.getString(R.string.settings_thumb_rotate_desc),
            keywords = listOf("thumb", "rotate", "spin", "animation", "playing", "progress"),
            icon = MaterialSymbolIcon("rotate_right"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player",
            settingKey = "playerProgressThumbRotate"
        ))
        add(SearchableSettingItem(
            id = "player_song_info_artwork",
            title = context.getString(R.string.settings_song_info_artwork),
            description = context.getString(R.string.settings_song_info_artwork_desc),
            keywords = listOf("song info", "title on artwork", "artist overlay", "artwork text", "overlay"),
            icon = RhythmIcons.Info,
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player",
            settingKey = "playerShowSongInfoOnArtwork"
        ))
        add(SearchableSettingItem(
            id = "player_artwork_overlay",
            title = context.getString(R.string.settings_artwork_overlay),
            description = context.getString(R.string.settings_artwork_overlay_desc),
            keywords = listOf("overlay", "gradient overlay", "artwork effect", "shade", "player"),
            icon = MaterialSymbolIcon("gradient"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player",
            settingKey = "playerShowGradientOverlay"
        ))
        add(SearchableSettingItem(
            id = "player_ambient_backdrop",
            title = context.getString(R.string.player_ambient_backdrop),
            description = context.getString(R.string.player_ambient_desc),
            keywords = listOf("ambient", "backdrop", "blur", "artwork background", "player background", "glass"),
            icon = MaterialSymbolIcon("blur_on"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player",
            settingKey = "playerAmbientBackdropEnabled"
        ))
        add(SearchableSettingItem(
            id = "player_ambient_motion_zoom",
            title = context.getString(R.string.player_ambient_motion_zoom),
            description = context.getString(R.string.player_ambient_motion_zoom_desc),
            keywords = listOf("ambient", "motion", "zoom", "infinite zoom", "ken burns", "breathing", "animation"),
            icon = MaterialSymbolIcon("zoom_out_map"),
            route = SettingsRoutes.PLAYER_CUSTOMIZATION,
            parentScreen = "Player",
            settingKey = "playerAmbientInfiniteZoom"
        ))
        
        // ======================== MINIPLAYER CUSTOMIZATION SCREEN ========================
        add(SearchableSettingItem(
            id = "miniplayer_show_progress",
            title = context.getString(R.string.settings_miniplayer_show_progress),
            description = context.getString(R.string.settings_miniplayer_show_progress_desc),
            keywords = listOf("miniplayer progress", "progress bar", "indicator", "mini player"),
            icon = RhythmIcons.Visibility,
            route = SettingsRoutes.MINIPLAYER_CUSTOMIZATION,
            parentScreen = "MiniPlayer"
        ))
        add(SearchableSettingItem(
            id = "miniplayer_progress_style",
            title = context.getString(R.string.settings_miniplayer_progress_style),
            description = context.getString(R.string.settings_miniplayer_progress_style_desc),
            keywords = listOf("progress style", "miniplayer", "wavy", "dotted", "normal"),
            icon = MaterialSymbolIcon("linear_scale"),
            route = SettingsRoutes.MINIPLAYER_CUSTOMIZATION,
            parentScreen = "MiniPlayer"
        ))
        add(SearchableSettingItem(
            id = "miniplayer_show_artwork",
            title = context.getString(R.string.settings_miniplayer_show_artwork),
            description = context.getString(R.string.settings_miniplayer_show_artwork_desc),
            keywords = listOf("artwork", "album art", "cover", "image", "miniplayer"),
            icon = RhythmIcons.Album,
            route = SettingsRoutes.MINIPLAYER_CUSTOMIZATION,
            parentScreen = "MiniPlayer"
        ))
        add(SearchableSettingItem(
            id = "miniplayer_artwork_size",
            title = context.getString(R.string.settings_miniplayer_artwork_size),
            description = context.getString(R.string.settings_miniplayer_artwork_size_desc),
            keywords = listOf("artwork size", "image size", "cover size", "miniplayer"),
            icon = MaterialSymbolIcon("photo_size_select_large"),
            route = SettingsRoutes.MINIPLAYER_CUSTOMIZATION,
            parentScreen = "MiniPlayer"
        ))
        add(SearchableSettingItem(
            id = "miniplayer_corner_radius",
            title = context.getString(R.string.settings_miniplayer_corner_radius),
            description = context.getString(R.string.settings_miniplayer_corner_radius_desc),
            keywords = listOf("corner", "radius", "rounded", "shape", "miniplayer"),
            icon = MaterialSymbolIcon("rounded_corner"),
            route = SettingsRoutes.MINIPLAYER_CUSTOMIZATION,
            parentScreen = "MiniPlayer"
        ))
        add(SearchableSettingItem(
            id = "miniplayer_show_time",
            title = context.getString(R.string.settings_miniplayer_show_time),
            description = context.getString(R.string.settings_miniplayer_show_time_desc),
            keywords = listOf("time", "duration", "elapsed", "remaining", "miniplayer"),
            icon = MaterialSymbolIcon("timer"),
            route = SettingsRoutes.MINIPLAYER_CUSTOMIZATION,
            parentScreen = "MiniPlayer"
        ))
        add(SearchableSettingItem(
            id = "miniplayer_tablet_layout",
            title = context.getString(R.string.settings_miniplayer_tablet_layout),
            description = context.getString(R.string.settings_miniplayer_tablet_layout_desc),
            keywords = listOf("tablet", "layout", "phone", "style", "miniplayer"),
            icon = MaterialSymbolIcon("tablet"),
            route = SettingsRoutes.MINIPLAYER_CUSTOMIZATION,
            parentScreen = "MiniPlayer"
        ))
        
        // ======================== GESTURES SCREEN ========================
        add(SearchableSettingItem(
            id = "gesture_miniplayer_swipe",
            title = context.getString(R.string.settings_gesture_miniplayer_swipe),
            description = context.getString(R.string.settings_gesture_miniplayer_swipe_desc),
            keywords = listOf("swipe", "gesture", "miniplayer", "up", "down", "left", "right", "skip"),
            icon = MaterialSymbolIcon("swipe", filled = true),
            route = SettingsRoutes.GESTURES,
            parentScreen = "Gestures"
        ))
        add(SearchableSettingItem(
            id = "gesture_player_dismiss",
            title = context.getString(R.string.settings_gesture_player_dismiss),
            description = context.getString(R.string.settings_gesture_player_dismiss_desc),
            keywords = listOf("swipe down", "dismiss", "close", "player", "gesture"),
            icon = MaterialSymbolIcon("swipe_down", filled = true),
            route = SettingsRoutes.GESTURES,
            parentScreen = "Gestures"
        ))
        add(SearchableSettingItem(
            id = "gesture_artwork_swipe",
            title = context.getString(R.string.settings_gesture_artwork_swipe),
            description = context.getString(R.string.settings_gesture_artwork_swipe_desc),
            keywords = listOf("swipe", "artwork", "album art", "skip", "next", "previous"),
            icon = MaterialSymbolIcon("swipe_left", filled = true),
            route = SettingsRoutes.GESTURES,
            parentScreen = "Gestures"
        ))
        add(SearchableSettingItem(
            id = "gesture_double_tap",
            title = context.getString(R.string.settings_gesture_double_tap),
            description = context.getString(R.string.settings_gesture_double_tap_desc),
            keywords = listOf("double tap", "artwork", "play", "pause", "tap gesture"),
            icon = MaterialSymbolIcon("touch_app", filled = true),
            route = SettingsRoutes.GESTURES,
            parentScreen = "Gestures"
        ))
        add(SearchableSettingItem(
            id = "gesture_artwork_single_tap",
            title = context.getString(R.string.settings_tap_artwork_lyrics),
            description = context.getString(R.string.settings_tap_artwork_lyrics_desc),
            keywords = listOf("tap", "artwork", "album art", "lyrics", "gesture", "toggle"),
            icon = MaterialSymbolIcon("music_note", filled = true),
            route = SettingsRoutes.GESTURES,
            parentScreen = "Gestures"
        ))
        add(SearchableSettingItem(
            id = "gesture_miniplayer_swipe_tracks",
            title = context.getString(R.string.settings_miniplayer_swipe_tracks),
            description = context.getString(R.string.settings_miniplayer_swipe_tracks_desc),
            keywords = listOf("miniplayer", "swipe", "tracks", "skip", "next", "previous", "gesture"),
            icon = MaterialSymbolIcon("fast_forward", filled = true),
            route = SettingsRoutes.GESTURES,
            parentScreen = "Gestures"
        ))
        add(SearchableSettingItem(
            id = "gesture_miniplayer_swipe_dismiss",
            title = context.getString(R.string.settings_miniplayer_swipe_dismiss),
            description = context.getString(R.string.settings_miniplayer_swipe_dismiss_desc),
            keywords = listOf("miniplayer", "swipe", "dismiss", "open", "close", "expand", "gesture"),
            icon = MaterialSymbolIcon("swipe_vertical", filled = true),
            route = SettingsRoutes.GESTURES,
            parentScreen = "Gestures"
        ))
        add(SearchableSettingItem(
            id = "gesture_queue_swipe_remove",
            title = context.getString(R.string.settings_queue_swipe_remove),
            description = context.getString(R.string.settings_queue_swipe_remove_desc),
            keywords = listOf("queue", "swipe", "remove", "delete", "dismiss", "gesture"),
            icon = MaterialSymbolIcon("delete_sweep", filled = true),
            route = SettingsRoutes.GESTURES,
            parentScreen = "Gestures"
        ))
        add(SearchableSettingItem(
            id = "gesture_library_swipe_tabs",
            title = context.getString(R.string.settings_library_swipe_tabs),
            description = context.getString(R.string.settings_library_swipe_tabs_desc),
            keywords = listOf("library", "swipe", "tabs", "switch", "pager", "gesture"),
            icon = MaterialSymbolIcon("tab", filled = true),
            route = SettingsRoutes.GESTURES,
            parentScreen = "Gestures"
        ))
        add(SearchableSettingItem(
            id = "gesture_lyrics_tap_seek",
            title = context.getString(R.string.settings_lyrics_tap_seek),
            description = context.getString(R.string.settings_lyrics_tap_seek_desc),
            keywords = listOf("lyrics", "tap", "seek", "jump", "playback", "gesture"),
            icon = MaterialSymbolIcon("ads_click", filled = true),
            route = SettingsRoutes.GESTURES,
            parentScreen = "Gestures"
        ))
        add(SearchableSettingItem(
            id = "gesture_disable_unused",
            title = context.getString(R.string.settings_disable_unused_gestures),
            description = context.getString(R.string.settings_gesture_disable_unused_desc),
            keywords = listOf("gestures", "disable", "unused", "off", "customize"),
            icon = MaterialSymbolIcon("gesture"),
            route = SettingsRoutes.GESTURES,
            parentScreen = "Gestures"
        ))
        
        // ======================== QUEUE & PLAYBACK SCREEN ========================
        add(SearchableSettingItem(
            id = "queue_exoplayer_shuffle",
            title = context.getString(R.string.settings_use_exoplayer_shuffle),
            description = context.getString(R.string.settings_use_exoplayer_shuffle_desc),
            keywords = listOf("shuffle", "exoplayer", "random", "playback", "algorithm", "shuffle engine", "shuffle timeline", "shuffle mode", "shuffle algorithm"),
            icon = RhythmIcons.Shuffle,
            route = SettingsRoutes.QUEUE,
            parentScreen = "Queue",
            settingKey = "shuffleUsesExoplayer"
        ))
        add(SearchableSettingItem(
            id = "queue_auto_add",
            title = context.getString(R.string.settings_auto_queue),
            description = context.getString(R.string.settings_auto_queue_desc),
            keywords = listOf("auto add", "auto queue", "add", "related", "similar songs", "automatic"),
            icon = RhythmIcons.AddToQueue,
            route = SettingsRoutes.QUEUE,
            parentScreen = "Queue",
            settingKey = "autoAddToQueue"
        ))
        add(SearchableSettingItem(
            id = "queue_clear_on_new",
            title = context.getString(R.string.settings_clear_queue_on_new_song),
            description = context.getString(R.string.settings_clear_queue_on_new_song_desc),
            keywords = listOf("clear queue", "new song", "replace", "reset", "empty", "fresh"),
            icon = RhythmIcons.Delete,
            route = SettingsRoutes.QUEUE,
            parentScreen = "Queue",
            settingKey = "clearQueueOnNewSong"
        ))
        add(SearchableSettingItem(
            id = "queue_hide_played",
            title = context.getString(R.string.settings_show_played_queue_songs),
            description = context.getString(R.string.settings_show_played_queue_songs_desc),
            keywords = listOf("queue", "played", "history", "show", "finished songs", "already played"),
            icon = RhythmIcons.Queue,
            route = SettingsRoutes.QUEUE,
            parentScreen = "Queue",
            settingKey = "hidePlayedQueueSongs"
        ))
        add(SearchableSettingItem(
            id = "queue_action_dialog",
            title = context.getString(R.string.settings_queue_action_dialog),
            description = context.getString(R.string.settings_queue_action_dialog_desc),
            keywords = listOf("queue", "new song", "playing", "ask", "prompt", "add", "dialog"),
            icon = MaterialSymbolIcon("help", filled = true),
            route = SettingsRoutes.QUEUE,
            parentScreen = "Queue",
            settingKey = "showQueueDialog"
        ))
        add(SearchableSettingItem(
            id = "queue_behavior",
            title = context.getString(R.string.list_queue_behavior_title),
            description = context.getString(R.string.list_queue_behavior_desc),
            keywords = listOf("queue behavior", "play next", "add to end", "replace", "ask", "tap song"),
            icon = MaterialSymbolIcon("queue_music"),
            route = SettingsRoutes.QUEUE,
            parentScreen = "Queue",
            settingKey = "listQueueActionBehavior"
        ))
        add(SearchableSettingItem(
            id = "queue_context_persistence",
            title = context.getString(R.string.settings_context_queue_persistence),
            description = context.getString(R.string.settings_context_queue_persistence_desc),
            keywords = listOf("context queue", "persistence", "ephemeral", "persistent", "auto queue"),
            icon = MaterialSymbolIcon("history"),
            route = SettingsRoutes.QUEUE,
            parentScreen = "Queue",
            settingKey = "contextQueuePersistence"
        ))
        add(SearchableSettingItem(
            id = "queue_context_preference",
            title = context.getString(R.string.settings_context_queue_preference),
            description = context.getString(R.string.settings_context_queue_preference_desc),
            keywords = listOf("auto add", "similar", "related", "artist", "genre", "preference", "match"),
            icon = MaterialSymbolIcon("tune", filled = true),
            route = SettingsRoutes.QUEUE,
            parentScreen = "Queue",
            settingKey = "contextQueuePreference"
        ))
        add(SearchableSettingItem(
            id = "queue_repeat_persistence",
            title = context.getString(R.string.settings_queue_repeat_persistence),
            description = context.getString(R.string.settings_queue_repeat_persistence_desc),
            keywords = listOf("repeat", "remember", "save", "persistence", "loop"),
            icon = RhythmIcons.Repeat,
            route = SettingsRoutes.PLAYBACK,
            parentScreen = "Playback",
            settingKey = "repeatModePersistence"
        ))
        add(SearchableSettingItem(
            id = "queue_shuffle_persistence",
            title = context.getString(R.string.settings_queue_shuffle_persistence),
            description = context.getString(R.string.settings_queue_shuffle_persistence_desc),
            keywords = listOf("shuffle", "remember", "save", "persistence", "random"),
            icon = RhythmIcons.Shuffle,
            route = SettingsRoutes.PLAYBACK,
            parentScreen = "Playback",
            settingKey = "shuffleModePersistence"
        ))
        add(SearchableSettingItem(
            id = "keep_shuffle_on_selection",
            title = context.getString(R.string.settings_keep_shuffle_on_selection),
            description = context.getString(R.string.settings_keep_shuffle_on_selection_desc),
            keywords = listOf("shuffle", "persist", "library", "select", "song", "keep", "queue", "random"),
            icon = RhythmIcons.Shuffle,
            route = SettingsRoutes.PLAYBACK,
            parentScreen = "Playback",
            settingKey = "keepShuffleOnSelection"
        ))
        add(SearchableSettingItem(
            id = "queue_stop_on_close",
            title = context.getString(R.string.settings_queue_stop_on_close),
            description = context.getString(R.string.settings_queue_stop_on_close_desc),
            keywords = listOf("stop", "playback", "close", "exit", "quit"),
            icon = RhythmIcons.Stop,
            route = SettingsRoutes.PLAYBACK,
            parentScreen = "Playback",
            settingKey = "stopPlaybackOnAppClose"
        ))
        add(SearchableSettingItem(
            id = "sleep_timer",
            title = context.getString(R.string.settings_sleep_timer_search),
            description = context.getString(R.string.settings_sleep_timer_search_desc),
            keywords = listOf("sleep", "timer", "auto stop", "automatic", "fade out", "pause", "bedtime"),
            icon = MaterialSymbolIcon("timer"),
            route = SettingsRoutes.SLEEP_TIMER,
            parentScreen = "Playback"
        ))
        add(SearchableSettingItem(
            id = "queue_hours_format",
            title = context.getString(R.string.settings_queue_hours_format),
            description = context.getString(R.string.settings_queue_hours_format_desc),
            keywords = listOf("hours", "time", "format", "duration", "display"),
            icon = RhythmIcons.AccessTime,
            route = SettingsRoutes.PLAYBACK,
            parentScreen = "Playback",
            settingKey = "useHoursInTimeFormat"
        ))
        add(SearchableSettingItem(
            id = "show_remaining_time",
            title = context.getString(R.string.settings_show_remaining_time),
            description = context.getString(R.string.settings_show_remaining_time_desc),
            keywords = listOf("remaining", "time", "duration", "display", "countdown", "total"),
            icon = RhythmIcons.AccessTime,
            route = SettingsRoutes.PLAYBACK,
            parentScreen = "Playback",
            settingKey = "showRemainingTime"
        ))
        add(SearchableSettingItem(
            id = "gapless_playback",
            title = context.getString(R.string.settings_gapless_playback),
            description = context.getString(R.string.settings_gapless_playback_desc),
            keywords = listOf("gapless", "transition", "silence", "playback", "next track"),
            icon = MaterialSymbolIcon("graphic_eq"),
            route = SettingsRoutes.PLAYBACK,
            parentScreen = "Playback",
            settingKey = "gaplessPlayback"
        ))
        add(SearchableSettingItem(
            id = "crossfade",
            title = context.getString(R.string.settings_crossfade),
            description = context.getString(R.string.settings_crossfade_desc),
            keywords = listOf("crossfade", "transition", "fade", "overlap", "smooth", "songs", "playback"),
            icon = MaterialSymbolIcon("linear_scale"),
            route = SettingsRoutes.PLAYBACK,
            parentScreen = "Playback",
            settingKey = "crossfade"
        ))
        add(SearchableSettingItem(
            id = "crossfade_repeat_one",
            title = context.getString(R.string.settings_crossfade_repeat_one),
            description = context.getString(R.string.settings_crossfade_repeat_one_desc),
            keywords = listOf("crossfade", "repeat one", "loop one", "transition", "single track"),
            icon = RhythmIcons.Repeat,
            route = SettingsRoutes.PLAYBACK,
            parentScreen = "Playback",
            settingKey = "crossfadeRepeatOne"
        ))
        add(SearchableSettingItem(
            id = "crossfade_duration",
            title = context.getString(R.string.settings_crossfade_duration),
            description = context.getString(R.string.settings_crossfade_duration_desc, 4.0f),
            keywords = listOf("crossfade", "duration", "seconds", "time", "length", "transition"),
            icon = MaterialSymbolIcon("linear_scale"),
            route = SettingsRoutes.PLAYBACK,
            parentScreen = "Playback",
            settingKey = "crossfadeDuration"
        ))
        add(SearchableSettingItem(
            id = "crossfade_on_skip",
            title = context.getString(R.string.settings_crossfade_on_skip),
            description = context.getString(R.string.settings_crossfade_on_skip_desc),
            keywords = listOf("crossfade", "skip", "next", "previous", "manual", "transition", "fade"),
            icon = MaterialSymbolIcon("skip_next"),
            route = SettingsRoutes.PLAYBACK,
            parentScreen = "Playback",
            settingKey = "crossfadeOnSkip"
        ))
        add(SearchableSettingItem(
            id = "queue_persistence",
            title = context.getString(R.string.settings_remember_queue),
            description = context.getString(R.string.settings_remember_queue_desc),
            keywords = listOf("queue", "remember", "save", "restore", "persistence", "restart", "reopen", "app"),
            icon = RhythmIcons.Queue,
            route = SettingsRoutes.QUEUE,
            parentScreen = "Queue",
            settingKey = "queuePersistenceEnabled"
        ))
        add(SearchableSettingItem(
            id = "respect_album_on_play",
            title = context.getString(R.string.settings_respect_album_on_play),
            description = context.getString(R.string.settings_respect_album_on_play_desc),
            keywords = listOf("album", "artist", "context", "queue", "play", "respect"),
            icon = RhythmIcons.Album,
            route = SettingsRoutes.QUEUE,
            parentScreen = "Queue",
            settingKey = "respectAlbumOnPlay"
        ))
        
        // ======================== LABS & RELOCATED SETTINGS ========================
        
        // Skip Silence (Moved to Playback Settings)
        add(SearchableSettingItem(
            id = "skip_silence",
            title = context.getString(R.string.settings_skip_silence),
            description = context.getString(R.string.settings_skip_silence_desc),
            keywords = listOf("skip silence", "silence", "cut silence", "audio effects", "smart play", "gapless", "playback"),
            icon = MaterialSymbolIcon("hearing"),
            route = SettingsRoutes.PLAYBACK,
            parentScreen = context.getString(R.string.settings_playback_title),
            settingKey = "skipSilenceEnabled"
        ))
        
        // Replay Gain
        add(SearchableSettingItem(
            id = "replay_gain",
            title = context.getString(R.string.replay_gain),
            description = context.getString(R.string.replay_gain_desc),
            keywords = listOf("replay gain", "replaygain", "volume normalization", "normalization", "gain", "audio effects"),
            icon = MaterialSymbolIcon("volume_up"),
            route = SettingsRoutes.REPLAY_GAIN,
            parentScreen = context.getString(R.string.settings_playback_title),
            settingKey = "replayGain"
        ))
        add(SearchableSettingItem(
            id = "replay_gain_mode",
            title = context.getString(R.string.replay_gain_mode_title),
            description = context.getString(R.string.replay_gain_mode_desc),
            keywords = listOf("replay gain", "mode", "album", "track", "volume normalization"),
            icon = MaterialSymbolIcon("graphic_eq"),
            route = SettingsRoutes.REPLAY_GAIN,
            parentScreen = context.getString(R.string.settings_playback_title),
            settingKey = "replayGainMode"
        ))
        add(SearchableSettingItem(
            id = "replay_gain_preamp_tagged",
            title = context.getString(R.string.replay_gain_preamp_tagged),
            description = context.getString(R.string.replay_gain_preamp_tagged_desc),
            keywords = listOf("replay gain", "preamp", "tagged", "gain offset", "boost"),
            icon = MaterialSymbolIcon("volume_up"),
            route = SettingsRoutes.REPLAY_GAIN,
            parentScreen = context.getString(R.string.settings_playback_title),
            settingKey = "replayGainPreamp"
        ))
        add(SearchableSettingItem(
            id = "replay_gain_preamp_untagged",
            title = context.getString(R.string.replay_gain_preamp_untagged),
            description = context.getString(R.string.replay_gain_preamp_untagged_desc),
            keywords = listOf("replay gain", "preamp", "untagged", "no tags", "offset"),
            icon = MaterialSymbolIcon("volume_up"),
            route = SettingsRoutes.REPLAY_GAIN,
            parentScreen = context.getString(R.string.settings_playback_title),
            settingKey = "replayGainPreampUntagged"
        ))
        add(SearchableSettingItem(
            id = "replay_gain_prevent_clipping",
            title = context.getString(R.string.replay_gain_prevent_clipping),
            description = context.getString(R.string.replay_gain_prevent_clipping_desc),
            keywords = listOf("replay gain", "clipping", "prevent", "drc", "limiter"),
            icon = MaterialSymbolIcon("volume_off"),
            route = SettingsRoutes.REPLAY_GAIN,
            parentScreen = context.getString(R.string.settings_playback_title),
            settingKey = "replayGainDrc"
        ))

        
        add(SearchableSettingItem(
            id = "exp_festive_theme",
            title = context.getString(R.string.settings_exp_festive_theme),
            description = context.getString(R.string.settings_exp_festive_theme_desc),
            keywords = listOf("festive", "christmas", "new year", "decoration", "snow", "snowflake"),
            icon = MaterialSymbolIcon("celebration"),
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = context.getString(R.string.settings_theme)
        ))
        add(SearchableSettingItem(
            id = "exp_auto_detect_holidays",
            title = context.getString(R.string.settings_exp_auto_detect_holidays),
            description = context.getString(R.string.settings_exp_auto_detect_holidays_desc),
            keywords = listOf("auto detect", "holiday", "automatic", "festive", "seasonal"),
            icon = RhythmIcons.AutoAwesome,
            route = SettingsRoutes.THEME_CUSTOMIZATION,
            parentScreen = context.getString(R.string.settings_theme)
        ))
        add(SearchableSettingItem(
            id = "exp_ignore_mediastore",
            title = context.getString(R.string.settings_exp_ignore_mediastore),
            description = context.getString(R.string.settings_exp_ignore_mediastore_desc),
            keywords = listOf("song art", "song artwork", "mediastore", "album art", "cover", "extract", "embedded"),
            icon = RhythmIcons.Album,
            route = SettingsRoutes.LIBRARY_SETTINGS,
            parentScreen = context.getString(R.string.settings_library_settings),
            settingKey = "preferSongArtwork"
        ))
        add(SearchableSettingItem(
            id = "exp_codec_monitoring",
            title = context.getString(R.string.settings_exp_codec_monitoring),
            description = context.getString(R.string.settings_exp_codec_monitoring_desc),
            keywords = listOf("codec", "debug", "log", "monitoring", "audio format"),
            icon = RhythmIcons.Code,
            route = SettingsRoutes.LABS,
            parentScreen = context.getString(R.string.settings_labs)
        ))
        add(SearchableSettingItem(
            id = "exp_audio_device_logging",
            title = context.getString(R.string.settings_exp_audio_device_logging),
            description = context.getString(R.string.settings_exp_audio_device_logging_desc),
            keywords = listOf("audio device", "bluetooth", "headphones", "log", "debug"),
            icon = RhythmIcons.Headphones,
            route = SettingsRoutes.LABS,
            parentScreen = context.getString(R.string.settings_labs)
        ))
        add(SearchableSettingItem(
            id = "exp_force_compact_mode",
            title = context.getString(R.string.exp_force_player_compact_mode),
            description = context.getString(R.string.exp_force_player_compact_mode_desc),
            keywords = listOf("compact mode", "force", "player", "expressive", "experimental"),
            icon = MaterialSymbolIcon("developer_mode"),
            route = SettingsRoutes.LABS,
            parentScreen = context.getString(R.string.settings_labs),
            settingKey = "forcePlayerCompactMode"
        ))
        add(SearchableSettingItem(
            id = "exp_track_error_checker",
            title = context.getString(R.string.exp_track_error_checker),
            description = context.getString(R.string.exp_track_error_checker_desc),
            keywords = listOf("track error", "checker", "validation", "debug", "experimental"),
            icon = MaterialSymbolIcon("bug_report"),
            route = SettingsRoutes.LABS,
            parentScreen = context.getString(R.string.settings_labs),
            settingKey = "trackErrorCheckerEnabled"
        ))
        add(SearchableSettingItem(
            id = "app_launch_onboarding",
            title = context.getString(R.string.about_replay_tour),
            description = context.getString(R.string.about_replay_tour_desc),
            keywords = listOf("rhythm tour", "tour", "reset", "restart", "welcome", "setup", "intro", "onboarding", "guide"),
            icon = MaterialSymbolIcon("restart_alt"),
            route = SettingsRoutes.ABOUT,
            parentScreen = "About"
        ))
        add(SearchableSettingItem(
            id = "exp_test_crash",
            title = context.getString(R.string.settings_exp_test_crash),
            description = context.getString(R.string.settings_exp_test_crash_desc),
            keywords = listOf("crash", "test", "debug", "error", "reporting"),
            icon = RhythmIcons.BugReport,
            route = SettingsRoutes.LABS,
            parentScreen = context.getString(R.string.settings_labs)
        ))

        add(SearchableSettingItem(
            id = "broadcast_status_enabled",
            title = context.getString(R.string.settings_broadcast_status_enabled),
            description = context.getString(R.string.settings_broadcast_status_enabled_desc),
            keywords = listOf("broadcast", "status", "playback", "share", "other apps"),
            icon = RhythmIcons.Info,
            route = SettingsRoutes.NOTIFICATIONS,
            parentScreen = context.getString(R.string.settings_notifications),
            settingKey = "broadcastStatusEnabled"
        ))
        add(SearchableSettingItem(
            id = "bluetooth_lyrics_enabled",
            title = context.getString(R.string.settings_bluetooth_lyrics_enabled),
            description = context.getString(R.string.settings_bluetooth_lyrics_enabled_desc),
            keywords = listOf("notification lyrics", "lyrics", "notification", "bluetooth", "broadcast", "avrcp", "metadata", "rokid", "smart glasses", "wearables", "ticker", "smart watch"),
            icon = MaterialSymbolIcon("lyrics"),
            route = SettingsRoutes.NOTIFICATIONS,
            parentScreen = context.getString(R.string.settings_notifications),
            settingKey = "bluetoothLyricsEnabled"
        ))
        add(SearchableSettingItem(
            id = "enable_album_editing",
            title = context.getString(R.string.settings_enable_album_editing),
            description = context.getString(R.string.settings_enable_album_editing_desc),
            keywords = listOf("album editing", "batch edit", "album metadata", "edit album", "artwork", "batch"),
            icon = MaterialSymbolIcon("edit"),
            route = SettingsRoutes.LABS,
            parentScreen = context.getString(R.string.settings_labs),
            settingKey = "enableAlbumEditing"
        ))
        add(SearchableSettingItem(
            id = "home_section_order",
            title = context.getString(R.string.settings_home_section_order),
            description = context.getString(R.string.settings_home_section_order_desc),
            keywords = listOf("section order", "home", "reorder", "arrange", "layout"),
            icon = MaterialSymbolIcon("reorder"),
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home"
        ))
        add(SearchableSettingItem(
            id = "home_greeting",
            title = context.getString(R.string.settings_home_greeting_search),
            description = context.getString(R.string.settings_home_greeting_search_desc),
            keywords = listOf("greeting", "hello", "welcome", "message", "home"),
            icon = RhythmIcons.Info,
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home"
        ))
        add(SearchableSettingItem(
            id = "home_recently_played",
            title = context.getString(R.string.settings_home_recently_played),
            description = context.getString(R.string.settings_home_recently_played_desc),
            keywords = listOf("recently played", "history", "recent", "last played"),
            icon = RhythmIcons.AccessTime,
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home"
        ))
        add(SearchableSettingItem(
            id = "home_discover_carousel",
            title = context.getString(R.string.settings_home_discover_carousel),
            description = context.getString(R.string.settings_home_discover_carousel_desc),
            keywords = listOf("discover", "carousel", "featured", "slider", "banner"),
            icon = RhythmIcons.AutoAwesome,
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home"
        ))
        add(SearchableSettingItem(
            id = "home_carousel_auto_scroll",
            title = context.getString(R.string.settings_home_carousel_auto_scroll),
            description = context.getString(R.string.settings_home_carousel_auto_scroll_desc),
            keywords = listOf("auto scroll", "carousel", "automatic", "slide"),
            icon = MaterialSymbolIcon("play_circle_filled"),
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home"
        ))
        add(SearchableSettingItem(
            id = "home_carousel_style",
            title = context.getString(R.string.settings_carousel_style),
            description = context.getString(R.string.settings_carousel_style_desc),
            keywords = listOf("carousel", "style", "hero", "default", "discover", "banner"),
            icon = MaterialSymbolIcon("view_carousel"),
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home"
        ))
        add(SearchableSettingItem(
            id = "home_always_start_collapsed",
            title = context.getString(R.string.settings_always_start_collapsed),
            description = context.getString(R.string.settings_home_start_collapsed_desc),
            keywords = listOf("collapsed", "header", "start", "expand", "home layout"),
            icon = MaterialSymbolIcon("unfold_less"),
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home",
            settingKey = "headerCollapseBehavior"
        ))
        add(SearchableSettingItem(
            id = "home_choose_header_content",
            title = context.getString(R.string.settings_choose_header_content),
            description = context.getString(R.string.settings_home_header_content_desc),
            keywords = listOf("header", "greeting", "content", "display", "home"),
            icon = RhythmIcons.Info,
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home",
            settingKey = "homeHeaderDisplayMode"
        ))
        add(SearchableSettingItem(
            id = "home_discover_album_name",
            title = context.getString(R.string.settings_discover_album_name),
            description = context.getString(R.string.settings_discover_album_name_desc),
            keywords = listOf("discover", "album name", "carousel", "label"),
            icon = MaterialSymbolIcon("album"),
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home",
            settingKey = "homeDiscoverShowAlbumName"
        ))
        add(SearchableSettingItem(
            id = "home_discover_artist_name",
            title = context.getString(R.string.settings_discover_artist_name),
            description = context.getString(R.string.settings_discover_artist_name_desc),
            keywords = listOf("discover", "artist name", "carousel", "label"),
            icon = RhythmIcons.Artist,
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home",
            settingKey = "homeDiscoverShowArtistName"
        ))
        add(SearchableSettingItem(
            id = "home_discover_gradient_overlay",
            title = context.getString(R.string.settings_discover_gradient_overlay),
            description = context.getString(R.string.settings_discover_gradient_overlay_desc),
            keywords = listOf("discover", "gradient", "overlay", "carousel", "text contrast"),
            icon = MaterialSymbolIcon("gradient"),
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home",
            settingKey = "homeDiscoverShowGradient"
        ))
        add(SearchableSettingItem(
            id = "home_discover_play_button",
            title = context.getString(R.string.settings_discover_play_button),
            description = context.getString(R.string.settings_discover_play_button_desc),
            keywords = listOf("discover", "play button", "carousel", "quick play"),
            icon = MaterialSymbolIcon("play_circle_filled"),
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home",
            settingKey = "homeDiscoverShowPlayButton"
        ))
        add(SearchableSettingItem(
            id = "home_discover_release_year",
            title = context.getString(R.string.settings_discover_release_year),
            description = context.getString(R.string.settings_discover_release_year_desc),
            keywords = listOf("discover", "release year", "carousel", "date"),
            icon = MaterialSymbolIcon("calendar_today"),
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home",
            settingKey = "homeDiscoverShowYear"
        ))
        add(SearchableSettingItem(
            id = "home_disable_unused_sections",
            title = context.getString(R.string.settings_disable_unused_sections),
            description = context.getString(R.string.settings_home_hide_unused_sections_desc),
            keywords = listOf("sections", "hide", "disable", "home layout", "visibility"),
            icon = MaterialSymbolIcon("visibility_off"),
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home"
        ))
        add(SearchableSettingItem(
            id = "home_widget_item_counts",
            title = context.getString(R.string.settings_widget_item_counts),
            description = context.getString(R.string.settings_home_widget_counts_desc),
            keywords = listOf("widget", "counts", "numbers", "item count", "home"),
            icon = MaterialSymbolIcon("data_usage"),
            route = SettingsRoutes.HOME_SCREEN,
            parentScreen = "Home"
        ))
        
        // ======================== NOTIFICATIONS SCREEN ========================
        add(SearchableSettingItem(
            id = "notifications_updates",
            title = context.getString(R.string.settings_update_notifications),
            description = context.getString(R.string.settings_update_notifications_merged_desc),
            keywords = listOf("updates", "update notifications", "new version", "release", "update available", "up to date", "update error", "check result"),
            icon = RhythmIcons.Update,
            route = SettingsRoutes.NOTIFICATIONS,
            parentScreen = "Notifications",
            settingKey = "updateNotificationsEnabled"
        ))
        add(SearchableSettingItem(
            id = "notifications_rhythm_guard_alerts",
            title = context.getString(R.string.settings_notifications_rhythm_guard_alerts),
            description = context.getString(R.string.settings_notifications_rhythm_guard_alerts_desc),
            keywords = listOf("rhythm guard", "safety alert", "hearing warning", "volume risk", "exposure alert"),
            icon = RhythmIcons.Warning,
            route = SettingsRoutes.NOTIFICATIONS,
            parentScreen = "Notifications",
            settingKey = "rhythmGuardAlertNotificationsEnabled"
        ))
        add(SearchableSettingItem(
            id = "notifications_rhythm_guard_timers",
            title = context.getString(R.string.settings_notifications_rhythm_guard_timers),
            description = context.getString(R.string.settings_notifications_rhythm_guard_timers_desc),
            keywords = listOf("rhythm guard timer", "break timer", "timeout", "resume countdown", "listening break"),
            icon = MaterialSymbolIcon("timer"),
            route = SettingsRoutes.NOTIFICATIONS,
            parentScreen = "Notifications",
            settingKey = "rhythmGuardTimerNotificationsEnabled"
        ))
        add(SearchableSettingItem(
            id = "notifications_rhythm_pulse",
            title = context.getString(R.string.settings_notifications_rhythm_pulse),
            description = context.getString(R.string.settings_notifications_rhythm_pulse_desc),
            keywords = listOf("rhythm tips", "greetings", "comic tips", "music tips", "motivational notifications"),
            icon = MaterialSymbolIcon("celebration"),
            route = SettingsRoutes.NOTIFICATIONS,
            parentScreen = "Notifications",
            settingKey = "rhythmPulseNotificationsEnabled"
        ))
        add(SearchableSettingItem(
            id = "notifications_rhythm_pulse_interval",
            title = context.getString(R.string.settings_notifications_rhythm_pulse_interval),
            description = context.getString(R.string.settings_notifications_rhythm_pulse_interval_desc),
            keywords = listOf("tips interval", "rhythm tips frequency", "hours", "6 hours", "24 hours", "72 hours"),
            icon = RhythmIcons.AccessTime,
            route = SettingsRoutes.NOTIFICATIONS,
            parentScreen = "Notifications",
            settingKey = "rhythmPulseNotificationIntervalHours"
        ))
        add(SearchableSettingItem(
            id = "notifications_library_operations",
            title = context.getString(R.string.settings_library_operations_notifications),
            description = context.getString(R.string.settings_library_operations_notifications_desc),
            keywords = listOf("library", "operations", "scan", "notifications", "sync"),
            icon = RhythmIcons.Notifications,
            route = SettingsRoutes.NOTIFICATIONS,
            parentScreen = "Notifications",
            settingKey = "libraryOperationsNotificationsEnabled"
        ))
        add(SearchableSettingItem(
            id = "notifications_system_channels",
            title = context.getString(R.string.settings_system_notification_channels),
            description = context.getString(R.string.settings_system_notification_channels_desc),
            keywords = listOf("system", "notification channels", "categories", "android channels"),
            icon = MaterialSymbolIcon("settings"),
            route = SettingsRoutes.NOTIFICATIONS,
            parentScreen = "Notifications"
        ))
        
        // ======================== EXPRESSIVE SHAPES SCREEN ========================
        add(SearchableSettingItem(
            id = "expressive_shapes_enabled",
            title = context.getString(R.string.settings_expressive_shapes_enabled),
            description = context.getString(R.string.settings_expressive_shapes_enabled_search_desc),
            keywords = listOf("shapes", "expressive", "custom", "ui", "design", "artwork", "corners"),
            icon = RhythmIcons.Palette,
            route = SettingsRoutes.EXPRESSIVE_SHAPES,
            parentScreen = "Settings",
            settingKey = "expressiveShapesEnabled"
        ))
        add(SearchableSettingItem(
            id = "shape_preset",
            title = context.getString(R.string.settings_shape_preset),
            description = context.getString(R.string.settings_shape_preset_desc),
            keywords = listOf("preset", "shapes", "collection", "playful", "organic", "geometric", "retro", "custom"),
            icon = MaterialSymbolIcon("color_lens"),
            route = SettingsRoutes.EXPRESSIVE_SHAPES,
            parentScreen = "Shapes",
            settingKey = "expressiveShapePreset"
        ))
        add(SearchableSettingItem(
            id = "shape_album_art",
            title = context.getString(R.string.settings_shape_album_art),
            description = context.getString(R.string.settings_shape_album_art_desc),
            keywords = listOf("album", "artwork", "shape", "cover", "image", "display"),
            icon = RhythmIcons.Album,
            route = SettingsRoutes.EXPRESSIVE_SHAPES,
            parentScreen = "Shapes",
            settingKey = "expressiveShapeAlbumArt"
        ))
        add(SearchableSettingItem(
            id = "shape_player_art",
            title = context.getString(R.string.settings_shape_player_art),
            description = context.getString(R.string.settings_shape_player_art_desc),
            keywords = listOf("player", "artwork", "shape", "screen", "display", "now playing"),
            icon = MaterialSymbolIcon("play_circle_filled"),
            route = SettingsRoutes.EXPRESSIVE_SHAPES,
            parentScreen = "Shapes",
            settingKey = "expressiveShapePlayerArt"
        ))
        add(SearchableSettingItem(
            id = "shape_song_art",
            title = context.getString(R.string.settings_shape_song_art),
            description = context.getString(R.string.settings_shape_song_art_desc),
            keywords = listOf("song", "artwork", "shape", "list", "thumbnail", "image"),
            icon = RhythmIcons.MusicNote,
            route = SettingsRoutes.EXPRESSIVE_SHAPES,
            parentScreen = "Shapes",
            settingKey = "expressiveShapeSongArt"
        ))
        add(SearchableSettingItem(
            id = "shape_playlist_art",
            title = context.getString(R.string.settings_shape_playlist_art),
            description = context.getString(R.string.settings_shape_playlist_art_desc),
            keywords = listOf("playlist", "artwork", "shape", "cover", "collection"),
            icon = MaterialSymbolIcon("playlist_add_check_circle"),
            route = SettingsRoutes.EXPRESSIVE_SHAPES,
            parentScreen = "Shapes",
            settingKey = "expressiveShapePlaylistArt"
        ))
        add(SearchableSettingItem(
            id = "shape_artist_art",
            title = context.getString(R.string.settings_shape_artist_art),
            description = context.getString(R.string.settings_shape_artist_art_desc),
            keywords = listOf("artist", "artwork", "shape", "image", "profile", "photo"),
            icon = RhythmIcons.Artist,
            route = SettingsRoutes.EXPRESSIVE_SHAPES,
            parentScreen = "Shapes",
            settingKey = "expressiveShapeArtistArt"
        ))
        add(SearchableSettingItem(
            id = "shape_player_controls",
            title = context.getString(R.string.settings_shape_player_controls),
            description = context.getString(R.string.settings_shape_player_controls_desc),
            keywords = listOf("player", "controls", "shape", "buttons", "play", "pause", "skip"),
            icon = MaterialSymbolIcon("play_circle_filled"),
            route = SettingsRoutes.EXPRESSIVE_SHAPES,
            parentScreen = "Shapes",
            settingKey = "expressiveShapePlayerControls"
        ))
        add(SearchableSettingItem(
            id = "shape_mini_player",
            title = context.getString(R.string.settings_shape_mini_player),
            description = context.getString(R.string.settings_shape_mini_player_desc),
            keywords = listOf("mini player", "artwork", "shape", "compact", "bottom bar"),
            icon = MaterialSymbolIcon("play_circle_filled"),
            route = SettingsRoutes.EXPRESSIVE_SHAPES,
            parentScreen = "Shapes",
            settingKey = "expressiveShapeMiniPlayer"
        ))
    }
}

/**
 * Performs search on the settings index
 */
fun searchSettings(query: String, index: List<SearchableSettingItem>): List<SearchableSettingItem> {
    if (query.isBlank()) return emptyList()
    
    val normalizedQuery = query.lowercase().trim()
    val queryWords = normalizedQuery.split(" ").filter { it.isNotBlank() }
    
    return index.filter { item ->
        val titleMatch = item.title.lowercase().contains(normalizedQuery)
        val descMatch = item.description.lowercase().contains(normalizedQuery)
        val keywordMatch = item.keywords.any { keyword ->
            keyword.lowercase().contains(normalizedQuery) ||
            queryWords.any { word -> keyword.lowercase().contains(word) }
        }
        val parentMatch = item.parentScreen.lowercase().contains(normalizedQuery)
        
        titleMatch || descMatch || keywordMatch || parentMatch
    }.sortedByDescending { item ->
        // Prioritize exact title matches, then keyword matches
        when {
            item.title.lowercase() == normalizedQuery -> 100
            item.title.lowercase().startsWith(normalizedQuery) -> 90
            item.title.lowercase().contains(normalizedQuery) -> 80
            item.keywords.any { it.lowercase() == normalizedQuery } -> 70
            item.keywords.any { it.lowercase().startsWith(normalizedQuery) } -> 60
            item.keywords.any { it.lowercase().contains(normalizedQuery) } -> 50
            item.description.lowercase().contains(normalizedQuery) -> 40
            else -> 30
        }
    }
}

/**
 * Search bar composable for settings
 */
@Composable
fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: (Boolean) -> Unit = {},
    hint: String = LocalContext.current.getString(R.string.search_settings_hint),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = RhythmIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Icon(
                    imageVector = MaterialSymbolIcon("clear"),
                    contentDescription = context.getString(R.string.clear_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable {
                            HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                            onQueryChange("")
                        }
                )
            }
        }
    }
}

/**
 * Search results list composable
 */
@Composable
fun SettingsSearchResults(
    results: List<SearchableSettingItem>,
    onResultClick: (SearchableSettingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp + LocalMiniPlayerPadding.current.calculateBottomPadding()),
        modifier = modifier.fillMaxSize()
    ) {
        if (results.isEmpty()) {
            item {
                ExpressiveCookieEmptyState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    title = context.getString(R.string.no_results_found),
                    subtitle = context.getString(R.string.try_different_search),
                    mainIcon = RhythmIcons.Search,
                    accentIcon = RhythmIcons.Search,
                    cornerIcon = RhythmIcons.Tune,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else {
            // Group results by parent screen
            val groupedResults = results.groupBy { it.parentScreen }
            
            groupedResults.forEach { (screenName, items) ->
                item(key = "group_$screenName") {
                    Spacer(modifier = Modifier.height(16.dp))

                    val materialItems = items.map { setting ->
                        Material3SettingsItem(
                            icon = setting.icon,
                            title = { Text(setting.title) },
                            description = { Text(setting.description) },
                            trailingContent = {
                                Icon(
                                    imageVector = MaterialSymbolIcon("arrow_forward_ios", filled = true),
                                    contentDescription = context.getString(R.string.cd_navigate),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                HapticUtils.performHapticFeedback(context, haptic, HapticType.LIGHT)
                                onResultClick(setting)
                            }
                        )
                    }

                    Material3SettingsGroup(
                        title = screenName,
                        items = materialItems,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}


