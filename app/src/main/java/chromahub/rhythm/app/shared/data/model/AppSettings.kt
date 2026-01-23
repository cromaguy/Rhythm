package chromahub.rhythm.app.shared.data.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import chromahub.rhythm.app.worker.BackupWorker
import chromahub.rhythm.app.worker.UpdateNotificationWorker
import chromahub.rhythm.app.BuildConfig
import java.util.Date // Import Date for timestamp
import java.util.concurrent.TimeUnit

/**
 * Data class to represent a single crash log entry
 */
data class CrashLogEntry(
    val timestamp: Long,
    val log: String
)

/**
 * Enum for album view types in the library
 */
enum class AlbumViewType {
    LIST, GRID
}

/**
 * Enum for artist view types in the library
 */
enum class ArtistViewType {
    LIST, GRID
}

/**
 * Enum for playlist view types in the library
 */
enum class PlaylistViewType {
    LIST, GRID
}

/**
 * Singleton class to manage all app settings using SharedPreferences
 */
class AppSettings private constructor(context: Context) {
    companion object {
        private const val PREFS_NAME = "rhythm_preferences"
        
        // Playback Settings
        private const val KEY_HIGH_QUALITY_AUDIO = "high_quality_audio"
        private const val KEY_GAPLESS_PLAYBACK = "gapless_playback"
        private const val KEY_CROSSFADE = "crossfade"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_AUDIO_NORMALIZATION = "audio_normalization"
        private const val KEY_REPLAY_GAIN = "replay_gain"
        
        // Lyrics Settings
        private const val KEY_SHOW_LYRICS = "show_lyrics"
        private const val KEY_ONLINE_ONLY_LYRICS = "online_only_lyrics" // Deprecated, kept for migration
        private const val KEY_LYRICS_SOURCE_PREFERENCE = "lyrics_source_preference"
        
        // Theme Settings
        private const val KEY_USE_SYSTEM_THEME = "use_system_theme"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_USE_DYNAMIC_COLORS = "use_dynamic_colors"
        private const val KEY_CUSTOM_COLOR_SCHEME = "custom_color_scheme"
        private const val KEY_CUSTOM_FONT = "custom_font"
        private const val KEY_COLOR_SOURCE = "color_source" // ALBUM_ART, MONET, or CUSTOM
        private const val KEY_EXTRACTED_ALBUM_COLORS = "extracted_album_colors" // JSON string with color values
        private const val KEY_FONT_SOURCE = "font_source" // SYSTEM or CUSTOM
        private const val KEY_CUSTOM_FONT_PATH = "custom_font_path" // Path to imported font file
        private const val KEY_CUSTOM_FONT_FAMILY = "custom_font_family" // Display name of custom font
        
        // Library Settings
        private const val KEY_ALBUM_VIEW_TYPE = "album_view_type"
        private const val KEY_ARTIST_VIEW_TYPE = "artist_view_type"
        private const val KEY_PLAYLIST_VIEW_TYPE = "playlist_view_type"
        private const val KEY_ALBUM_SORT_ORDER = "album_sort_order"
        private const val KEY_PLAYLIST_SORT_ORDER = "playlist_sort_order"
        private const val KEY_ARTIST_COLLABORATION_MODE = "artist_collaboration_mode"
        private const val KEY_LIBRARY_TAB_ORDER = "library_tab_order"
        private const val KEY_PLAYER_CHIP_ORDER = "player_chip_order"
        private const val KEY_HIDDEN_LIBRARY_TABS = "hidden_library_tabs"
        private const val KEY_HIDDEN_PLAYER_CHIPS = "hidden_player_chips"
        private const val KEY_GROUP_BY_ALBUM_ARTIST = "group_by_album_artist" // New setting for album artist grouping
        private const val KEY_IGNORE_MEDIASTORE_COVERS = "ignore_mediastore_covers" // Ignore Android MediaStore covers, read embedded art directly
        
        // Audio Device Settings
        private const val KEY_LAST_AUDIO_DEVICE = "last_audio_device"
        private const val KEY_AUTO_CONNECT_DEVICE = "auto_connect_device"
        private const val KEY_USE_SYSTEM_VOLUME = "use_system_volume"
        private const val KEY_DISMISSED_AUTOEQ_SUGGESTIONS = "dismissed_autoeq_suggestions"
        
        // Equalizer Settings
        private const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"
        private const val KEY_EQUALIZER_PRESET = "equalizer_preset"
        private const val KEY_EQUALIZER_BAND_LEVELS = "equalizer_band_levels"
        private const val KEY_AUTOEQ_PROFILE = "autoeq_profile"
        private const val KEY_USER_AUDIO_DEVICES = "user_audio_devices"
        private const val KEY_ACTIVE_AUDIO_DEVICE_ID = "active_audio_device_id"
        private const val KEY_BASS_BOOST_ENABLED = "bass_boost_enabled"
        private const val KEY_BASS_BOOST_STRENGTH = "bass_boost_strength"
        private const val KEY_BASS_BOOST_AVAILABLE = "bass_boost_available"
        private const val KEY_VIRTUALIZER_ENABLED = "virtualizer_enabled"
        private const val KEY_VIRTUALIZER_STRENGTH = "virtualizer_strength"
        
        // Cache Settings
        private const val KEY_MAX_CACHE_SIZE = "max_cache_size"
        private const val KEY_CLEAR_CACHE_ON_EXIT = "clear_cache_on_exit"
        
        // Search History
        private const val KEY_SEARCH_HISTORY = "search_history"
        
        // Playlists
        private const val KEY_PLAYLISTS = "playlists"
        private const val KEY_FAVORITE_SONGS = "favorite_songs"
        private const val KEY_SONG_RATINGS = "song_ratings" // Map of songId to rating (0-5)
        private const val KEY_ENABLE_RATING_SYSTEM = "enable_rating_system" // Enable/disable rating system
        private const val KEY_DEFAULT_PLAYLISTS_ENABLED = "default_playlists_enabled"
        
        // User Statistics
        private const val KEY_LISTENING_TIME = "listening_time"
        private const val KEY_SONGS_PLAYED = "songs_played"
        private const val KEY_UNIQUE_ARTISTS = "unique_artists"
        private const val KEY_GENRE_PREFERENCES = "genre_preferences"
        private const val KEY_TIME_BASED_PREFERENCES = "time_based_preferences"
        
        // Recently Played
        private const val KEY_RECENTLY_PLAYED = "recently_played"
        private const val KEY_LAST_PLAYED_TIMESTAMP = "last_played_timestamp"
        
        // API Integration
        private const val KEY_DEEZER_API_ENABLED = "deezer_api_enabled"
        private const val KEY_CANVAS_API_ENABLED = "canvas_api_enabled"
        private const val KEY_LRCLIB_API_ENABLED = "lrclib_api_enabled"
        private const val KEY_YTMUSIC_API_ENABLED = "ytmusic_api_enabled"
        private const val KEY_SPOTIFY_API_ENABLED = "spotify_api_enabled"
        private const val KEY_SPOTIFY_CLIENT_ID = "spotify_client_id"
        private const val KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"
        
        // Scrobbling Settings
        private const val KEY_SCROBBLING_ENABLED = "scrobbling_enabled"
        
        // Discord Rich Presence Settings
        private const val KEY_DISCORD_RICH_PRESENCE_ENABLED = "discord_rich_presence_enabled"
        
        // General Broadcast Status Settings (for Tasker, KWGT, etc.)
        private const val KEY_BROADCAST_STATUS_ENABLED = "broadcast_status_enabled"
        
        // Enhanced User Preferences
        private const val KEY_FAVORITE_GENRES = "favorite_genres"
        private const val KEY_DAILY_LISTENING_STATS = "daily_listening_stats"
        private const val KEY_WEEKLY_TOP_ARTISTS = "weekly_top_artists"
        private const val KEY_MOOD_PREFERENCES = "mood_preferences"
        
        // Song Play Counts
        private const val KEY_SONG_PLAY_COUNTS = "song_play_counts"

        // Onboarding
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_INITIAL_MEDIA_SCAN_COMPLETED = "initial_media_scan_completed"
        private const val KEY_GENRE_DETECTION_COMPLETED = "genre_detection_completed"

        // App Updater Settings
        private const val KEY_AUTO_CHECK_FOR_UPDATES = "auto_check_for_updates"
        private const val KEY_UPDATE_CHANNEL = "update_channel" // New key for update channel
        private const val KEY_UPDATES_ENABLED = "updates_enabled" // Master switch for updates
        private const val KEY_UPDATE_NOTIFICATIONS_ENABLED = "update_notifications_enabled" // Push-style notifications
        private const val KEY_USE_SMART_UPDATE_POLLING = "use_smart_update_polling" // Use ETag/conditional requests
        private const val KEY_MEDIA_SCAN_MODE = "media_scan_mode" // Mode for media scanning: "blacklist" or "whitelist"
        private const val KEY_UPDATE_CHECK_INTERVAL_HOURS = "update_check_interval_hours" // Configurable interval

        // Beta Program
        private const val KEY_HAS_SHOWN_BETA_POPUP = "has_shown_beta_popup"

        // Crash Reporting
        private const val KEY_LAST_CRASH_LOG = "last_crash_log"
        private const val KEY_CRASH_LOG_HISTORY = "crash_log_history" // New key for crash log history
        
        // Haptic Feedback
        private const val KEY_HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
        
        // Notification Settings
    private const val KEY_USE_CUSTOM_NOTIFICATION = "use_custom_notification"
    
    // UI Settings
    private const val KEY_USE_SETTINGS = "use_settings"
    private const val KEY_DEFAULT_SCREEN = "default_screen"
    
        // Codec Monitoring & Enhanced Seeking
        private const val KEY_CODEC_MONITORING_ENABLED = "codec_monitoring_enabled"
        private const val KEY_SHOW_CODEC_NOTIFICATIONS = "show_codec_notifications"
        private const val KEY_ENHANCED_SEEKING_ENABLED = "enhanced_seeking_enabled"
        
        // Media3 1.9.0 Features
        private const val KEY_USE_CUSTOM_COMMAND_BUTTONS = "use_custom_command_buttons"
        private const val KEY_SCRUBBING_MODE_ENABLED = "scrubbing_mode_enabled"
        private const val KEY_STUCK_PLAYER_DETECTION_ENABLED = "stuck_player_detection_enabled"
        
        // Festive Theme Settings
        private const val KEY_FESTIVE_THEME_ENABLED = "festive_theme_enabled"
        private const val KEY_FESTIVE_THEME_TYPE = "festive_theme_type"
        private const val KEY_FESTIVE_THEME_INTENSITY = "festive_theme_intensity"
        private const val KEY_FESTIVE_THEME_AUTO_DETECT = "festive_theme_auto_detect"
        private const val KEY_FESTIVE_SNOWFLAKE_SIZE = "festive_snowflake_size"
        private const val KEY_FESTIVE_SNOWFLAKE_AREA = "festive_snowflake_area"
        
        // Festive Decoration Position Settings
        private const val KEY_FESTIVE_SHOW_TOP_LIGHTS = "festive_show_top_lights"
        private const val KEY_FESTIVE_SHOW_SIDE_GARLAND = "festive_show_side_garland"
        private const val KEY_FESTIVE_SHOW_BOTTOM_SNOW = "festive_show_bottom_snow"
        private const val KEY_FESTIVE_SHOW_SNOWFALL = "festive_show_snowfall"
        
        // Blacklisted Songs
        private const val KEY_BLACKLISTED_SONGS = "blacklisted_songs"
        
        // Blacklisted Folders
        private const val KEY_BLACKLISTED_FOLDERS = "blacklisted_folders"
        
        // Whitelisted Songs
        private const val KEY_WHITELISTED_SONGS = "whitelisted_songs"
        
        // Whitelisted Folders
        private const val KEY_WHITELISTED_FOLDERS = "whitelisted_folders"

        // Pinned Folders (Explorer)
        private const val KEY_PINNED_FOLDERS = "pinned_folders"
        
        // Playlist Playback Behavior
        private const val KEY_PLAYLIST_CLICK_BEHAVIOR = "playlist_click_behavior" // "ask", "play_all", "play_one"
        
        // Backup and Restore
        private const val KEY_LAST_BACKUP_TIMESTAMP = "last_backup_timestamp"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_BACKUP_LOCATION = "backup_location"
        
        // Sleep Timer
        private const val KEY_SLEEP_TIMER_ACTIVE = "sleep_timer_active"
        private const val KEY_SLEEP_TIMER_REMAINING_SECONDS = "sleep_timer_remaining_seconds"
        private const val KEY_SLEEP_TIMER_ACTION = "sleep_timer_action"
        
        // Media Scan Tracking
        private const val KEY_LAST_SCAN_TIMESTAMP = "last_scan_timestamp"
        private const val KEY_LAST_SCAN_DURATION = "last_scan_duration"
        
        // Media Scan Filtering
        private const val KEY_ALLOWED_FORMATS = "allowed_formats"
        private const val KEY_MINIMUM_BITRATE = "minimum_bitrate"
        private const val KEY_MINIMUM_DURATION = "minimum_duration"
        
        // Library Sort Order
        private const val KEY_SONGS_SORT_ORDER = "songs_sort_order"
        
        // Alphabet Bar Settings
        private const val KEY_SHOW_ALPHABET_BAR = "show_alphabet_bar"
        private const val KEY_SHOW_SCROLL_TO_TOP = "show_scroll_to_top"
        
        // App Mode Settings (Local vs Streaming)
        private const val KEY_APP_MODE = "app_mode" // "LOCAL" or "STREAMING"
        private const val KEY_STREAMING_SERVICE = "streaming_service" // "SPOTIFY", "APPLE_MUSIC", etc.
        private const val KEY_STREAMING_QUALITY = "streaming_quality" // "LOW", "MEDIUM", "HIGH", "LOSSLESS"
        private const val KEY_ALLOW_CELLULAR_STREAMING = "allow_cellular_streaming"
        private const val KEY_OFFLINE_MODE = "offline_mode"
        
        // Queue & Playback Behavior
        private const val KEY_SHUFFLE_USES_EXOPLAYER = "shuffle_uses_exoplayer"
        private const val KEY_AUTO_ADD_TO_QUEUE = "auto_add_to_queue"
        private const val KEY_CLEAR_QUEUE_ON_NEW_SONG = "clear_queue_on_new_song"
        private const val KEY_SHOW_QUEUE_DIALOG = "show_queue_dialog"
        private const val KEY_REPEAT_MODE_PERSISTENCE = "repeat_mode_persistence"
        private const val KEY_SHUFFLE_MODE_PERSISTENCE = "shuffle_mode_persistence"
        private const val KEY_SAVED_SHUFFLE_STATE = "saved_shuffle_state"
        private const val KEY_SAVED_REPEAT_MODE = "saved_repeat_mode"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_USE_HOURS_IN_TIME_FORMAT = "use_hours_in_time_format"
        private const val KEY_STOP_PLAYBACK_ON_APP_CLOSE = "stop_playback_on_app_close"
        private const val KEY_QUEUE_PERSISTENCE_ENABLED = "queue_persistence_enabled" // Enable/disable queue persistence
        private const val KEY_SAVED_QUEUE = "saved_queue" // Queue persistence - list of song IDs
        private const val KEY_SAVED_QUEUE_INDEX = "saved_queue_index" // Current position in queue
        private const val KEY_SAVED_PLAYBACK_POSITION = "saved_playback_position" // Current playback position in ms
        
        // Widget Settings
        private const val KEY_WIDGET_SHOW_ALBUM_ART = "widget_show_album_art"
        private const val KEY_WIDGET_SHOW_ARTIST = "widget_show_artist"
        private const val KEY_WIDGET_SHOW_ALBUM = "widget_show_album"
        private const val KEY_WIDGET_CORNER_RADIUS = "widget_corner_radius"
        private const val KEY_WIDGET_AUTO_UPDATE = "widget_auto_update"
        
        // Global Header Settings
        private const val KEY_HEADER_COLLAPSE_BEHAVIOR = "header_collapse_behavior" // 0=Normal, 1=Always Collapsed (applies to all screens)
        
        // Home Screen Customization Settings - Header
        private const val KEY_HOME_HEADER_DISPLAY_MODE = "home_header_display_mode" // 0=Icon Only, 1=Name Only, 2=Both
        private const val KEY_HOME_SHOW_APP_ICON = "home_show_app_icon" // Deprecated - kept for migration
        private const val KEY_HOME_APP_ICON_VISIBILITY = "home_app_icon_visibility" // 0=Both, 1=Expanded, 2=Collapsed
        
        // Home Screen Customization Settings - Section Visibility
        private const val KEY_HOME_SHOW_GREETING = "home_show_greeting"
        private const val KEY_HOME_SHOW_RECENTLY_PLAYED = "home_show_recently_played"
        private const val KEY_HOME_SHOW_DISCOVER_CAROUSEL = "home_show_discover_carousel"
        private const val KEY_HOME_SHOW_ARTISTS = "home_show_artists"
        private const val KEY_HOME_SHOW_NEW_RELEASES = "home_show_new_releases"
        private const val KEY_HOME_SHOW_RECENTLY_ADDED = "home_show_recently_added"
        private const val KEY_HOME_SHOW_RECOMMENDED = "home_show_recommended"
        private const val KEY_HOME_SHOW_LISTENING_STATS = "home_show_listening_stats"
        private const val KEY_HOME_SHOW_MOOD_SECTIONS = "home_show_mood_sections"
        
        // Home Screen Customization Settings - Discover Widget
        private const val KEY_HOME_DISCOVER_AUTO_SCROLL = "home_discover_auto_scroll"
        private const val KEY_HOME_DISCOVER_AUTO_SCROLL_INTERVAL = "home_discover_auto_scroll_interval"
        private const val KEY_HOME_DISCOVER_ITEM_COUNT = "home_discover_item_count"
        private const val KEY_HOME_CAROUSEL_HEIGHT = "home_carousel_height"
        private const val KEY_HOME_DISCOVER_CAROUSEL_STYLE = "home_discover_carousel_style" // 0=Default (2 side peeks), 1=Hero (1 side peek)
        private const val KEY_HOME_DISCOVER_SHOW_ALBUM_NAME = "home_discover_show_album_name"
        private const val KEY_HOME_DISCOVER_SHOW_ARTIST_NAME = "home_discover_show_artist_name"
        private const val KEY_HOME_DISCOVER_SHOW_YEAR = "home_discover_show_year"
        private const val KEY_HOME_DISCOVER_SHOW_PLAY_BUTTON = "home_discover_show_play_button"
        private const val KEY_HOME_DISCOVER_SHOW_GRADIENT = "home_discover_show_gradient"
        
        // Home Screen Customization Settings - Section Item Counts
        private const val KEY_HOME_RECENTLY_PLAYED_COUNT = "home_recently_played_count"
        private const val KEY_HOME_ARTISTS_COUNT = "home_artists_count"
        private const val KEY_HOME_NEW_RELEASES_COUNT = "home_new_releases_count"
        private const val KEY_HOME_RECENTLY_ADDED_COUNT = "home_recently_added_count"
        private const val KEY_HOME_RECOMMENDED_COUNT = "home_recommended_count"
        
        // Home Screen Customization Settings - Card Appearance
        private const val KEY_HOME_COMPACT_CARDS = "home_compact_cards"
        private const val KEY_HOME_SHOW_PLAY_BUTTONS = "home_show_play_buttons"
        private const val KEY_HOME_SECTION_ORDER = "home_section_order"
        private const val KEY_ALBUM_BOTTOM_SHEET_GRADIENT_BLUR = "album_bottom_sheet_gradient_blur"
        
        // Artist Separator Settings
        private const val KEY_ARTIST_SEPARATOR_ENABLED = "artist_separator_enabled"
        private const val KEY_ARTIST_SEPARATOR_DELIMITERS = "artist_separator_delimiters" // Comma-separated string of delimiters
        
        // Player Screen Customization Settings
        private const val KEY_PLAYER_SHOW_GRADIENT_OVERLAY = "player_show_gradient_overlay"
        private const val KEY_PLAYER_SHOW_SEEK_BUTTONS = "player_show_seek_buttons"
        private const val KEY_PLAYER_TEXT_ALIGNMENT = "player_text_alignment" // "START", "CENTER", "END"
        private const val KEY_PLAYER_SHOW_SONG_INFO_ON_ARTWORK = "player_show_song_info_on_artwork"
        private const val KEY_PLAYER_ARTWORK_CORNER_RADIUS = "player_artwork_corner_radius" // 0-40 dp
        private const val KEY_PLAYER_SHOW_AUDIO_QUALITY_BADGES = "player_show_audio_quality_badges"
        private const val KEY_PLAYER_PROGRESS_STYLE = "player_progress_style" // "NORMAL", "WAVY", "ROUNDED", "THIN", "THICK"
        private const val KEY_PLAYER_PROGRESS_THUMB_STYLE = "player_progress_thumb_style" // "NONE", "CIRCLE", "PILL", "DIAMOND", "LINE"
        
        // MiniPlayer Customization Settings
        private const val KEY_MINIPLAYER_PROGRESS_STYLE = "miniplayer_progress_style" // "NORMAL", "WAVY", "ROUNDED", "THIN", "GRADIENT"
        private const val KEY_MINIPLAYER_SHOW_PROGRESS = "miniplayer_show_progress"
        private const val KEY_MINIPLAYER_SHOW_ARTWORK = "miniplayer_show_artwork"
        private const val KEY_MINIPLAYER_ARTWORK_SIZE = "miniplayer_artwork_size" // 40-72 dp
        private const val KEY_MINIPLAYER_CORNER_RADIUS = "miniplayer_corner_radius" // 0-28 dp
        private const val KEY_MINIPLAYER_SHOW_TIME = "miniplayer_show_time"
        private const val KEY_MINIPLAYER_USE_CIRCULAR_PROGRESS = "miniplayer_use_circular_progress"
        private const val KEY_MINIPLAYER_ARTWORK_STYLE = "miniplayer_artwork_style" // "ROUNDED", "CIRCLE", "SQUARE"
        private const val KEY_MINIPLAYER_SHOW_SKIP_BUTTONS = "miniplayer_show_skip_buttons"
        private const val KEY_MINIPLAYER_TEXT_ALIGNMENT = "miniplayer_text_alignment" // "START", "CENTER"
        private const val KEY_MINIPLAYER_SWIPE_GESTURES = "miniplayer_swipe_gestures"
        private const val KEY_MINIPLAYER_SHOW_ARTIST = "miniplayer_show_artist"
        private const val KEY_MINIPLAYER_ALWAYS_SHOW_TABLET = "miniplayer_always_show_tablet"
        
        // Gesture Settings
        private const val KEY_GESTURE_PLAYER_SWIPE_DISMISS = "gesture_player_swipe_dismiss" // Swipe down to dismiss full player
        private const val KEY_GESTURE_PLAYER_SWIPE_TRACKS = "gesture_player_swipe_tracks" // Swipe left/right to change tracks in full player
        private const val KEY_GESTURE_ARTWORK_DOUBLE_TAP = "gesture_artwork_double_tap" // Double tap on artwork to play/pause
        
        @Volatile
        private var INSTANCE: AppSettings? = null
        
        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val context: Context = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Playback Settings
    private val _highQualityAudio = MutableStateFlow(prefs.getBoolean(KEY_HIGH_QUALITY_AUDIO, true))
    val highQualityAudio: StateFlow<Boolean> = _highQualityAudio.asStateFlow()
    
    private val _gaplessPlayback = MutableStateFlow(prefs.getBoolean(KEY_GAPLESS_PLAYBACK, true))
    val gaplessPlayback: StateFlow<Boolean> = _gaplessPlayback.asStateFlow()
    
    private val _crossfade = MutableStateFlow(prefs.getBoolean(KEY_CROSSFADE, false))
    val crossfade: StateFlow<Boolean> = _crossfade.asStateFlow()
    
    private val _crossfadeDuration = MutableStateFlow(prefs.getFloat(KEY_CROSSFADE_DURATION, 2f))
    val crossfadeDuration: StateFlow<Float> = _crossfadeDuration.asStateFlow()
    
    private val _audioNormalization = MutableStateFlow(prefs.getBoolean(KEY_AUDIO_NORMALIZATION, true))
    val audioNormalization: StateFlow<Boolean> = _audioNormalization.asStateFlow()
    
    private val _replayGain = MutableStateFlow(prefs.getBoolean(KEY_REPLAY_GAIN, false))
    val replayGain: StateFlow<Boolean> = _replayGain.asStateFlow()
    
    // Lyrics Settings
    private val _showLyrics = MutableStateFlow(prefs.getBoolean(KEY_SHOW_LYRICS, true))
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()
    
    // Migrate from old online_only_lyrics setting to new preference system
    private val migratedLyricsPreference = run {
        val hasOldSetting = prefs.contains(KEY_ONLINE_ONLY_LYRICS)
        if (hasOldSetting && !prefs.contains(KEY_LYRICS_SOURCE_PREFERENCE)) {
            // Migrate: online_only = true means API_FIRST, false means allow all sources
            val onlineOnly = prefs.getBoolean(KEY_ONLINE_ONLY_LYRICS, true)
            if (onlineOnly) LyricsSourcePreference.API_FIRST.ordinal else LyricsSourcePreference.EMBEDDED_FIRST.ordinal
        } else {
            prefs.getInt(KEY_LYRICS_SOURCE_PREFERENCE, LyricsSourcePreference.LOCAL_FIRST.ordinal)
        }
    }
    
    private val _lyricsSourcePreference = MutableStateFlow(LyricsSourcePreference.fromOrdinal(migratedLyricsPreference))
    val lyricsSourcePreference: StateFlow<LyricsSourcePreference> = _lyricsSourcePreference.asStateFlow()
    
    // Keep for backward compatibility but make it read from new preference
    private val _onlineOnlyLyrics = MutableStateFlow(_lyricsSourcePreference.value == LyricsSourcePreference.API_FIRST)
    val onlineOnlyLyrics: StateFlow<Boolean> = _onlineOnlyLyrics.asStateFlow()
    
    // Theme Settings
    private val _useSystemTheme = MutableStateFlow(prefs.getBoolean(KEY_USE_SYSTEM_THEME, true))
    val useSystemTheme: StateFlow<Boolean> = _useSystemTheme.asStateFlow()
    
    private val _darkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, true))
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()
    
    private val _useDynamicColors = MutableStateFlow(prefs.getBoolean(KEY_USE_DYNAMIC_COLORS, false))
    val useDynamicColors: StateFlow<Boolean> = _useDynamicColors.asStateFlow()
    
    // Artist Separator Settings
    private val _artistSeparatorEnabled = MutableStateFlow(prefs.getBoolean(KEY_ARTIST_SEPARATOR_ENABLED, true))
    val artistSeparatorEnabled: StateFlow<Boolean> = _artistSeparatorEnabled.asStateFlow()
    
    // Default delimiters: / ; , + &
    private val _artistSeparatorDelimiters = MutableStateFlow(
        prefs.getString(KEY_ARTIST_SEPARATOR_DELIMITERS, "/;,+&") ?: "/;,+&"
    )
    val artistSeparatorDelimiters: StateFlow<String> = _artistSeparatorDelimiters.asStateFlow()
    
    private val _customColorScheme = MutableStateFlow(prefs.getString(KEY_CUSTOM_COLOR_SCHEME, "Default") ?: "Default")
    val customColorScheme: StateFlow<String> = _customColorScheme.asStateFlow()
    
    private val _customFont = MutableStateFlow(prefs.getString(KEY_CUSTOM_FONT, "Geom") ?: "Geom")
    val customFont: StateFlow<String> = _customFont.asStateFlow()
    
    private val _colorSource = MutableStateFlow(prefs.getString(KEY_COLOR_SOURCE, "CUSTOM") ?: "CUSTOM")
    val colorSource: StateFlow<String> = _colorSource.asStateFlow()
    
    private val _extractedAlbumColors = MutableStateFlow(prefs.getString(KEY_EXTRACTED_ALBUM_COLORS, null))
    val extractedAlbumColors: StateFlow<String?> = _extractedAlbumColors.asStateFlow()
    
    private val _fontSource = MutableStateFlow(prefs.getString(KEY_FONT_SOURCE, "SYSTEM") ?: "SYSTEM")
    val fontSource: StateFlow<String> = _fontSource.asStateFlow()
    
    private val _customFontPath = MutableStateFlow(prefs.getString(KEY_CUSTOM_FONT_PATH, null))
    val customFontPath: StateFlow<String?> = _customFontPath.asStateFlow()
    
    private val _customFontFamily = MutableStateFlow(prefs.getString(KEY_CUSTOM_FONT_FAMILY, "System") ?: "System")
    val customFontFamily: StateFlow<String> = _customFontFamily.asStateFlow()
    
    // Library Settings
    private val _albumViewType = MutableStateFlow(
        AlbumViewType.valueOf(prefs.getString(KEY_ALBUM_VIEW_TYPE, AlbumViewType.GRID.name) ?: AlbumViewType.GRID.name)
    )
    val albumViewType: StateFlow<AlbumViewType> = _albumViewType.asStateFlow()
    
    private val _artistViewType = MutableStateFlow(
        ArtistViewType.valueOf(prefs.getString(KEY_ARTIST_VIEW_TYPE, ArtistViewType.GRID.name) ?: ArtistViewType.GRID.name)
    )
    val artistViewType: StateFlow<ArtistViewType> = _artistViewType.asStateFlow()
    
    private val _playlistViewType = MutableStateFlow(
        PlaylistViewType.valueOf(prefs.getString(KEY_PLAYLIST_VIEW_TYPE, PlaylistViewType.LIST.name) ?: PlaylistViewType.LIST.name)
    )
    val playlistViewType: StateFlow<PlaylistViewType> = _playlistViewType.asStateFlow()
    
    // Album Sort Order
    private val _albumSortOrder = MutableStateFlow(prefs.getString(KEY_ALBUM_SORT_ORDER, "TRACK_NUMBER") ?: "TRACK_NUMBER")
    val albumSortOrder: StateFlow<String> = _albumSortOrder.asStateFlow()
    
    // Playlist Sort Order
    private val _playlistSortOrder = MutableStateFlow(prefs.getString(KEY_PLAYLIST_SORT_ORDER, "NAME_ASC") ?: "NAME_ASC")
    val playlistSortOrder: StateFlow<String> = _playlistSortOrder.asStateFlow()
    
    // Artist Collaboration Mode
    private val _artistCollaborationMode = MutableStateFlow(prefs.getBoolean(KEY_ARTIST_COLLABORATION_MODE, false))
    val artistCollaborationMode: StateFlow<Boolean> = _artistCollaborationMode.asStateFlow()
    
    // Library Tab Order
    private val defaultTabOrder = listOf("SONGS", "PLAYLISTS", "ALBUMS", "ARTISTS", "EXPLORER")
    private val _libraryTabOrder = MutableStateFlow(
        prefs.getString(KEY_LIBRARY_TAB_ORDER, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: defaultTabOrder
    )
    val libraryTabOrder: StateFlow<List<String>> = _libraryTabOrder.asStateFlow()
    
    // Player Chip Order (Add to Playlist and Edit chips are not reorderable - they stay fixed)
    private val defaultChipOrder = listOf("FAVORITE", "SPEED", "EQUALIZER", "SLEEP_TIMER", "LYRICS", "ALBUM", "ARTIST", "CAST")
    private val _playerChipOrder = MutableStateFlow(
        prefs.getString(KEY_PLAYER_CHIP_ORDER, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { existingChips ->
                // Add CAST if not present in existing order
                if (!existingChips.contains("CAST")) {
                    existingChips + "CAST"
                } else {
                    existingChips
                }
            }
            ?: defaultChipOrder
    )
    val playerChipOrder: StateFlow<List<String>> = _playerChipOrder.asStateFlow()
    
    // Default Landing Screen
    private val _defaultScreen = MutableStateFlow(prefs.getString(KEY_DEFAULT_SCREEN, "home") ?: "home")
    val defaultScreen: StateFlow<String> = _defaultScreen.asStateFlow()
    
    // Hidden Library Tabs
    private val _hiddenLibraryTabs = MutableStateFlow(
        prefs.getString(KEY_HIDDEN_LIBRARY_TABS, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    )
    val hiddenLibraryTabs: StateFlow<Set<String>> = _hiddenLibraryTabs.asStateFlow()
    
    // Hidden Player Chips
    private val _hiddenPlayerChips = MutableStateFlow(
        prefs.getString(KEY_HIDDEN_PLAYER_CHIPS, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    )
    val hiddenPlayerChips: StateFlow<Set<String>> = _hiddenPlayerChips.asStateFlow()
    
    // Group By Album Artist
    private val _groupByAlbumArtist = MutableStateFlow(prefs.getBoolean(KEY_GROUP_BY_ALBUM_ARTIST, true)) // Default true for better organization
    val groupByAlbumArtist: StateFlow<Boolean> = _groupByAlbumArtist.asStateFlow()
    
    // Ignore MediaStore Covers - Read embedded album art directly
    private val _ignoreMediaStoreCovers = MutableStateFlow(prefs.getBoolean(KEY_IGNORE_MEDIASTORE_COVERS, false))
    val ignoreMediaStoreCovers: StateFlow<Boolean> = _ignoreMediaStoreCovers.asStateFlow()
    
    // Alphabet Bar Settings
    private val _showAlphabetBar = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ALPHABET_BAR, false))
    val showAlphabetBar: StateFlow<Boolean> = _showAlphabetBar.asStateFlow()
    
    private val _showScrollToTop = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SCROLL_TO_TOP, false))
    val showScrollToTop: StateFlow<Boolean> = _showScrollToTop.asStateFlow()
    
    // App Mode Settings (Local vs Streaming)
    private val _appMode = MutableStateFlow(prefs.getString(KEY_APP_MODE, "LOCAL") ?: "LOCAL")
    val appMode: StateFlow<String> = _appMode.asStateFlow()
    
    private val _streamingService = MutableStateFlow(prefs.getString(KEY_STREAMING_SERVICE, "SPOTIFY") ?: "SPOTIFY")
    val streamingService: StateFlow<String> = _streamingService.asStateFlow()
    
    private val _streamingQuality = MutableStateFlow(prefs.getString(KEY_STREAMING_QUALITY, "HIGH") ?: "HIGH")
    val streamingQuality: StateFlow<String> = _streamingQuality.asStateFlow()
    
    private val _allowCellularStreaming = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_CELLULAR_STREAMING, true))
    val allowCellularStreaming: StateFlow<Boolean> = _allowCellularStreaming.asStateFlow()
    
    private val _offlineMode = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_MODE, false))
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()
    
    // Audio Device Settings
    private val _lastAudioDevice = MutableStateFlow(prefs.getString(KEY_LAST_AUDIO_DEVICE, null))
    val lastAudioDevice: StateFlow<String?> = _lastAudioDevice.asStateFlow()
    
    private val _autoConnectDevice = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CONNECT_DEVICE, true))
    val autoConnectDevice: StateFlow<Boolean> = _autoConnectDevice.asStateFlow()
    
    private val _useSystemVolume = MutableStateFlow(prefs.getBoolean(KEY_USE_SYSTEM_VOLUME, false))
    val useSystemVolume: StateFlow<Boolean> = _useSystemVolume.asStateFlow()
    
    // Equalizer Settings
    private val _equalizerEnabled = MutableStateFlow(prefs.getBoolean(KEY_EQUALIZER_ENABLED, false))
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()
    
    private val _equalizerPreset = MutableStateFlow(prefs.getString(KEY_EQUALIZER_PRESET, "Custom") ?: "Custom")
    val equalizerPreset: StateFlow<String> = _equalizerPreset.asStateFlow()
    
    private val _equalizerBandLevels = MutableStateFlow(prefs.getString(KEY_EQUALIZER_BAND_LEVELS, "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0") ?: "0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0")
    val equalizerBandLevels: StateFlow<String> = _equalizerBandLevels.asStateFlow()
    
    private val _autoEQProfile = MutableStateFlow(prefs.getString(KEY_AUTOEQ_PROFILE, "") ?: "")
    val autoEQProfile: StateFlow<String> = _autoEQProfile.asStateFlow()
    
    private val _userAudioDevices = MutableStateFlow(prefs.getString(KEY_USER_AUDIO_DEVICES, null))
    val userAudioDevices: StateFlow<String?> = _userAudioDevices.asStateFlow()
    
    private val _activeAudioDeviceId = MutableStateFlow(prefs.getString(KEY_ACTIVE_AUDIO_DEVICE_ID, null))
    val activeAudioDeviceId: StateFlow<String?> = _activeAudioDeviceId.asStateFlow()
    
    private val _dismissedAutoEQSuggestions = MutableStateFlow(prefs.getString(KEY_DISMISSED_AUTOEQ_SUGGESTIONS, null))
    val dismissedAutoEQSuggestions: StateFlow<String?> = _dismissedAutoEQSuggestions.asStateFlow()
    
    private val _bassBoostEnabled = MutableStateFlow(prefs.getBoolean(KEY_BASS_BOOST_ENABLED, false))
    val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()
    
    private val _bassBoostStrength = MutableStateFlow(prefs.getInt(KEY_BASS_BOOST_STRENGTH, 500))
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()
    
    private val _virtualizerEnabled = MutableStateFlow(prefs.getBoolean(KEY_VIRTUALIZER_ENABLED, false))
    val virtualizerEnabled: StateFlow<Boolean> = _virtualizerEnabled.asStateFlow()
    
    private val _virtualizerStrength = MutableStateFlow(prefs.getInt(KEY_VIRTUALIZER_STRENGTH, 500))
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()
    
    // Sleep Timer
    private val _sleepTimerActive = MutableStateFlow(prefs.getBoolean(KEY_SLEEP_TIMER_ACTIVE, false))
    val sleepTimerActive: StateFlow<Boolean> = _sleepTimerActive.asStateFlow()
    
    private val _sleepTimerRemainingSeconds = MutableStateFlow(prefs.getLong(KEY_SLEEP_TIMER_REMAINING_SECONDS, 0L))
    val sleepTimerRemainingSeconds: StateFlow<Long> = _sleepTimerRemainingSeconds.asStateFlow()
    
    private val _sleepTimerAction = MutableStateFlow(prefs.getString(KEY_SLEEP_TIMER_ACTION, "FADE_OUT") ?: "FADE_OUT")
    val sleepTimerAction: StateFlow<String> = _sleepTimerAction.asStateFlow()
    
    // Queue & Playback Behavior Settings
    private val _shuffleUsesExoplayer = MutableStateFlow(prefs.getBoolean(KEY_SHUFFLE_USES_EXOPLAYER, false))
    val shuffleUsesExoplayer: StateFlow<Boolean> = _shuffleUsesExoplayer.asStateFlow()
    
    private val _autoAddToQueue = MutableStateFlow(prefs.getBoolean(KEY_AUTO_ADD_TO_QUEUE, true))
    val autoAddToQueue: StateFlow<Boolean> = _autoAddToQueue.asStateFlow()
    
    private val _clearQueueOnNewSong = MutableStateFlow(prefs.getBoolean(KEY_CLEAR_QUEUE_ON_NEW_SONG, false))
    val clearQueueOnNewSong: StateFlow<Boolean> = _clearQueueOnNewSong.asStateFlow()
    
    private val _showQueueDialog = MutableStateFlow(prefs.getBoolean(KEY_SHOW_QUEUE_DIALOG, true))
    val showQueueDialog: StateFlow<Boolean> = _showQueueDialog.asStateFlow()
    
    private val _repeatModePersistence = MutableStateFlow(prefs.getBoolean(KEY_REPEAT_MODE_PERSISTENCE, true))
    val repeatModePersistence: StateFlow<Boolean> = _repeatModePersistence.asStateFlow()
    
    private val _shuffleModePersistence = MutableStateFlow(prefs.getBoolean(KEY_SHUFFLE_MODE_PERSISTENCE, true))
    val shuffleModePersistence: StateFlow<Boolean> = _shuffleModePersistence.asStateFlow()
    
    private val _savedShuffleState = MutableStateFlow(prefs.getBoolean(KEY_SAVED_SHUFFLE_STATE, false))
    val savedShuffleState: StateFlow<Boolean> = _savedShuffleState.asStateFlow()
    
    private val _savedRepeatMode = MutableStateFlow(prefs.getInt(KEY_SAVED_REPEAT_MODE, 0)) // 0 = OFF, 1 = ALL, 2 = ONE
    val savedRepeatMode: StateFlow<Int> = _savedRepeatMode.asStateFlow()
    
    private val _playbackSpeed = MutableStateFlow(prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    
    // Time Format Settings - Show hours:minutes:seconds for longer tracks (>60 min)
    private val _useHoursInTimeFormat = MutableStateFlow(prefs.getBoolean(KEY_USE_HOURS_IN_TIME_FORMAT, true))
    val useHoursInTimeFormat: StateFlow<Boolean> = _useHoursInTimeFormat.asStateFlow()
    
    // Stop Playback on App Close
    private val _stopPlaybackOnAppClose = MutableStateFlow(prefs.getBoolean(KEY_STOP_PLAYBACK_ON_APP_CLOSE, false))
    val stopPlaybackOnAppClose: StateFlow<Boolean> = _stopPlaybackOnAppClose.asStateFlow()
    
    // Queue Persistence
    private val _queuePersistenceEnabled = MutableStateFlow(prefs.getBoolean(KEY_QUEUE_PERSISTENCE_ENABLED, true))
    val queuePersistenceEnabled: StateFlow<Boolean> = _queuePersistenceEnabled.asStateFlow()
    
    private val _savedQueue = MutableStateFlow<List<String>>(
        try {
            val json = prefs.getString(KEY_SAVED_QUEUE, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    )
    val savedQueue: StateFlow<List<String>> = _savedQueue.asStateFlow()
    
    private val _savedQueueIndex = MutableStateFlow(prefs.getInt(KEY_SAVED_QUEUE_INDEX, -1))
    val savedQueueIndex: StateFlow<Int> = _savedQueueIndex.asStateFlow()
    
    private val _savedPlaybackPosition = MutableStateFlow(prefs.getLong(KEY_SAVED_PLAYBACK_POSITION, 0L))
    val savedPlaybackPosition: StateFlow<Long> = _savedPlaybackPosition.asStateFlow()
    
    // Cache Settings
    private val _maxCacheSize = MutableStateFlow(safeLong(KEY_MAX_CACHE_SIZE, 1024L * 1024L * 512L)) // 512MB default
    val maxCacheSize: StateFlow<Long> = _maxCacheSize.asStateFlow()
    
    private val _clearCacheOnExit = MutableStateFlow(prefs.getBoolean(KEY_CLEAR_CACHE_ON_EXIT, false))
    val clearCacheOnExit: StateFlow<Boolean> = _clearCacheOnExit.asStateFlow()
    
    // Search History
    private val _searchHistory = MutableStateFlow<String?>(prefs.getString(KEY_SEARCH_HISTORY, null))
    val searchHistory: StateFlow<String?> = _searchHistory.asStateFlow()
    
    // Playlists
    private val _playlists = MutableStateFlow<String?>(prefs.getString(KEY_PLAYLISTS, null))
    val playlists: StateFlow<String?> = _playlists.asStateFlow()

    private val _favoriteSongs = MutableStateFlow<String?>(prefs.getString(KEY_FAVORITE_SONGS, null))
    val favoriteSongs: StateFlow<String?> = _favoriteSongs.asStateFlow()
    
    // Song Ratings - Map of songId to rating (0-5)
    private val _songRatings = MutableStateFlow<Map<String, Int>>(
        try {
            val json = prefs.getString(KEY_SONG_RATINGS, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    )
    val songRatings: StateFlow<Map<String, Int>> = _songRatings.asStateFlow()
    
    // Enable/Disable Rating System
    private val _enableRatingSystem = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_RATING_SYSTEM, true))
    val enableRatingSystem: StateFlow<Boolean> = _enableRatingSystem.asStateFlow()
    
    private val _defaultPlaylistsEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEFAULT_PLAYLISTS_ENABLED, true))
    val defaultPlaylistsEnabled: StateFlow<Boolean> = _defaultPlaylistsEnabled.asStateFlow()
    
    // User Statistics
    private val _listeningTime = MutableStateFlow(safeLong(KEY_LISTENING_TIME, 0L))
    val listeningTime: StateFlow<Long> = _listeningTime.asStateFlow()
    
    private val _songsPlayed = MutableStateFlow(prefs.getInt(KEY_SONGS_PLAYED, 0))
    val songsPlayed: StateFlow<Int> = _songsPlayed.asStateFlow()
    
    private val _uniqueArtists = MutableStateFlow(prefs.getInt(KEY_UNIQUE_ARTISTS, 0))
    val uniqueArtists: StateFlow<Int> = _uniqueArtists.asStateFlow()
    
    private val _genrePreferences = MutableStateFlow<Map<String, Int>>(
        try {
            val json = prefs.getString(KEY_GENRE_PREFERENCES, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    )
    val genrePreferences: StateFlow<Map<String, Int>> = _genrePreferences.asStateFlow()
    
    private val _timeBasedPreferences = MutableStateFlow<Map<Int, List<String>>>(
        try {
            val json = prefs.getString(KEY_TIME_BASED_PREFERENCES, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<Map<Int, List<String>>>() {}.type)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    )
    val timeBasedPreferences: StateFlow<Map<Int, List<String>>> = _timeBasedPreferences.asStateFlow()
    
    // Recently Played
    private val _recentlyPlayed = MutableStateFlow<List<String>>(
        try {
            val json = prefs.getString(KEY_RECENTLY_PLAYED, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    )
    val recentlyPlayed: StateFlow<List<String>> = _recentlyPlayed.asStateFlow()
    
    private val _lastPlayedTimestamp = MutableStateFlow(safeLong(KEY_LAST_PLAYED_TIMESTAMP, 0L))
    val lastPlayedTimestamp: StateFlow<Long> = _lastPlayedTimestamp.asStateFlow()
    
    // API Enable/Disable States
    private val _deezerApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEEZER_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val deezerApiEnabled: StateFlow<Boolean> = _deezerApiEnabled.asStateFlow()
    
    private val _canvasApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_CANVAS_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val canvasApiEnabled: StateFlow<Boolean> = _canvasApiEnabled.asStateFlow()
    
    private val _lrclibApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_LRCLIB_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val lrclibApiEnabled: StateFlow<Boolean> = _lrclibApiEnabled.asStateFlow()
    
    private val _ytMusicApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_YTMUSIC_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val ytMusicApiEnabled: StateFlow<Boolean> = _ytMusicApiEnabled.asStateFlow()
    
    private val _spotifyApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_SPOTIFY_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val spotifyApiEnabled: StateFlow<Boolean> = _spotifyApiEnabled.asStateFlow()
    
    private val _spotifyClientId = MutableStateFlow(prefs.getString(KEY_SPOTIFY_CLIENT_ID, "") ?: "")
    val spotifyClientId: StateFlow<String> = _spotifyClientId.asStateFlow()
    
    private val _spotifyClientSecret = MutableStateFlow(prefs.getString(KEY_SPOTIFY_CLIENT_SECRET, "") ?: "")
    val spotifyClientSecret: StateFlow<String> = _spotifyClientSecret.asStateFlow()

    // Scrobbling Settings
    private val _scrobblingEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCROBBLING_ENABLED, false))
    val scrobblingEnabled: StateFlow<Boolean> = _scrobblingEnabled.asStateFlow()
    
    // Discord Rich Presence Settings
    private val _discordRichPresenceEnabled = MutableStateFlow(prefs.getBoolean(KEY_DISCORD_RICH_PRESENCE_ENABLED, false))
    val discordRichPresenceEnabled: StateFlow<Boolean> = _discordRichPresenceEnabled.asStateFlow()
    
    // General Broadcast Status Settings
    private val _broadcastStatusEnabled = MutableStateFlow(prefs.getBoolean(KEY_BROADCAST_STATUS_ENABLED, false))
    val broadcastStatusEnabled: StateFlow<Boolean> = _broadcastStatusEnabled.asStateFlow()

    // Enhanced User Preferences
    private val _favoriteGenres = MutableStateFlow<Map<String, Int>>(
        try {
            val json = prefs.getString(KEY_FAVORITE_GENRES, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    )
    val favoriteGenres: StateFlow<Map<String, Int>> = _favoriteGenres.asStateFlow()
    
    private val _dailyListeningStats = MutableStateFlow<Map<String, Long>>(
        try {
            val json = prefs.getString(KEY_DAILY_LISTENING_STATS, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<Map<String, Long>>() {}.type)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    )
    val dailyListeningStats: StateFlow<Map<String, Long>> = _dailyListeningStats.asStateFlow()
    
    private val _weeklyTopArtists = MutableStateFlow<Map<String, Int>>(
        try {
            val json = prefs.getString(KEY_WEEKLY_TOP_ARTISTS, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    )
    val weeklyTopArtists: StateFlow<Map<String, Int>> = _weeklyTopArtists.asStateFlow()
    
    private val _moodPreferences = MutableStateFlow<Map<String, List<String>>>(
        try {
            val json = prefs.getString(KEY_MOOD_PREFERENCES, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<Map<String, List<String>>>() {}.type)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    )
    val moodPreferences: StateFlow<Map<String, List<String>>> = _moodPreferences.asStateFlow()

    private val _songPlayCounts = MutableStateFlow<Map<String, Int>>(
        try {
            val json = prefs.getString(KEY_SONG_PLAY_COUNTS, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    )
    val songPlayCounts: StateFlow<Map<String, Int>> = _songPlayCounts.asStateFlow()

    // Onboarding
    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _initialMediaScanCompleted = MutableStateFlow(prefs.getBoolean(KEY_INITIAL_MEDIA_SCAN_COMPLETED, false))
    val initialMediaScanCompleted: StateFlow<Boolean> = _initialMediaScanCompleted.asStateFlow()

    private val _genreDetectionCompleted = MutableStateFlow(prefs.getBoolean(KEY_GENRE_DETECTION_COMPLETED, false))
    val genreDetectionCompleted: StateFlow<Boolean> = _genreDetectionCompleted.asStateFlow()

    // App Updater Settings
private val _autoCheckForUpdates = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK_FOR_UPDATES, BuildConfig.FLAVOR != "fdroid"))
    val autoCheckForUpdates: StateFlow<Boolean> = _autoCheckForUpdates.asStateFlow()
    
    private val _updateChannel = MutableStateFlow(prefs.getString(KEY_UPDATE_CHANNEL, "stable") ?: "stable")
    val updateChannel: StateFlow<String> = _updateChannel.asStateFlow()
    
    private val _updatesEnabled = MutableStateFlow(prefs.getBoolean(KEY_UPDATES_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val updatesEnabled: StateFlow<Boolean> = _updatesEnabled.asStateFlow()
    
    private val _updateNotificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_UPDATE_NOTIFICATIONS_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val updateNotificationsEnabled: StateFlow<Boolean> = _updateNotificationsEnabled.asStateFlow()
    
    private val _useSmartUpdatePolling = MutableStateFlow(prefs.getBoolean(KEY_USE_SMART_UPDATE_POLLING, BuildConfig.FLAVOR != "fdroid"))
    val useSmartUpdatePolling: StateFlow<Boolean> = _useSmartUpdatePolling.asStateFlow()

    // Media Scan Mode
    private val _mediaScanMode = MutableStateFlow(prefs.getString(KEY_MEDIA_SCAN_MODE, "blacklist") ?: "blacklist")
    val mediaScanMode: StateFlow<String> = _mediaScanMode.asStateFlow()

    private val _updateCheckIntervalHours = MutableStateFlow(prefs.getInt(KEY_UPDATE_CHECK_INTERVAL_HOURS, 6))
    val updateCheckIntervalHours: StateFlow<Int> = _updateCheckIntervalHours.asStateFlow()

    // Beta Program
    private val _hasShownBetaPopup = MutableStateFlow(prefs.getBoolean(KEY_HAS_SHOWN_BETA_POPUP, false))
    val hasShownBetaPopup: StateFlow<Boolean> = _hasShownBetaPopup.asStateFlow()

    // Crash Reporting
    private val _lastCrashLog = MutableStateFlow<String?>(prefs.getString(KEY_LAST_CRASH_LOG, null))
    val lastCrashLog: StateFlow<String?> = _lastCrashLog.asStateFlow()

    private val _crashLogHistory = MutableStateFlow<List<CrashLogEntry>>(
        try {
            val json = prefs.getString(KEY_CRASH_LOG_HISTORY, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<List<CrashLogEntry>>() {}.type)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    )
    val crashLogHistory: StateFlow<List<CrashLogEntry>> = _crashLogHistory.asStateFlow()
    
    // Haptic Feedback Settings
    private val _hapticFeedbackEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true))
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()
    
    // Notification Settings
    private val _useCustomNotification = MutableStateFlow(prefs.getBoolean(KEY_USE_CUSTOM_NOTIFICATION, false))
    val useCustomNotification: StateFlow<Boolean> = _useCustomNotification.asStateFlow()
    
    // UI Settings
    private val _useSettings = MutableStateFlow(prefs.getBoolean(KEY_USE_SETTINGS, true))
    val useSettings: StateFlow<Boolean> = _useSettings.asStateFlow()
    
    // Festive Theme Settings
    private val _festiveThemeEnabled = MutableStateFlow(prefs.getBoolean(KEY_FESTIVE_THEME_ENABLED, true))
    val festiveThemeEnabled: StateFlow<Boolean> = _festiveThemeEnabled.asStateFlow()
    
    private val _festiveThemeType = MutableStateFlow(prefs.getString(KEY_FESTIVE_THEME_TYPE, "CHRISTMAS") ?: "CHRISTMAS")
    val festiveThemeType: StateFlow<String> = _festiveThemeType.asStateFlow()
    
    private val _festiveThemeIntensity = MutableStateFlow(prefs.getFloat(KEY_FESTIVE_THEME_INTENSITY, 0.5f))
    val festiveThemeIntensity: StateFlow<Float> = _festiveThemeIntensity.asStateFlow()
    
    private val _festiveThemeAutoDetect = MutableStateFlow(prefs.getBoolean(KEY_FESTIVE_THEME_AUTO_DETECT, true))
    val festiveThemeAutoDetect: StateFlow<Boolean> = _festiveThemeAutoDetect.asStateFlow()
    
    private val _festiveSnowflakeSize = MutableStateFlow(prefs.getFloat(KEY_FESTIVE_SNOWFLAKE_SIZE, 1.0f))
    val festiveSnowflakeSize: StateFlow<Float> = _festiveSnowflakeSize.asStateFlow()
    
    private val _festiveSnowflakeArea = MutableStateFlow(prefs.getString(KEY_FESTIVE_SNOWFLAKE_AREA, "FULL_SCREEN") ?: "FULL_SCREEN")
    val festiveSnowflakeArea: StateFlow<String> = _festiveSnowflakeArea.asStateFlow()
    
    // Developer & Debugging Settings
    private val _codecMonitoringEnabled = MutableStateFlow(prefs.getBoolean("codec_monitoring_enabled", false))
    val codecMonitoringEnabled: StateFlow<Boolean> = _codecMonitoringEnabled.asStateFlow()
    
    private val _audioDeviceLoggingEnabled = MutableStateFlow(prefs.getBoolean("audio_device_logging_enabled", false))
    val audioDeviceLoggingEnabled: StateFlow<Boolean> = _audioDeviceLoggingEnabled.asStateFlow()
    
    private val _showCodecNotifications = MutableStateFlow(prefs.getBoolean(KEY_SHOW_CODEC_NOTIFICATIONS, false))
    val showCodecNotifications: StateFlow<Boolean> = _showCodecNotifications.asStateFlow()
    
    private val _enhancedSeekingEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENHANCED_SEEKING_ENABLED, true))
    val enhancedSeekingEnabled: StateFlow<Boolean> = _enhancedSeekingEnabled.asStateFlow()
    
    // Media3 1.9.0 Features
    private val _useCustomCommandButtons = MutableStateFlow(prefs.getBoolean(KEY_USE_CUSTOM_COMMAND_BUTTONS, true))
    val useCustomCommandButtons: StateFlow<Boolean> = _useCustomCommandButtons.asStateFlow()
    
    private val _scrubbingModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_SCRUBBING_MODE_ENABLED, true))
    val scrubbingModeEnabled: StateFlow<Boolean> = _scrubbingModeEnabled.asStateFlow()
    
    private val _stuckPlayerDetectionEnabled = MutableStateFlow(prefs.getBoolean(KEY_STUCK_PLAYER_DETECTION_ENABLED, true))
    val stuckPlayerDetectionEnabled: StateFlow<Boolean> = _stuckPlayerDetectionEnabled.asStateFlow()
    
    // Festive Decoration Position Settings
    private val _festiveShowTopLights = MutableStateFlow(prefs.getBoolean(KEY_FESTIVE_SHOW_TOP_LIGHTS, true))
    val festiveShowTopLights: StateFlow<Boolean> = _festiveShowTopLights.asStateFlow()
    
    private val _festiveShowSideGarland = MutableStateFlow(prefs.getBoolean(KEY_FESTIVE_SHOW_SIDE_GARLAND, true))
    val festiveShowSideGarland: StateFlow<Boolean> = _festiveShowSideGarland.asStateFlow()
    
    private val _festiveShowBottomSnow = MutableStateFlow(prefs.getBoolean(KEY_FESTIVE_SHOW_BOTTOM_SNOW, true))
    val festiveShowBottomSnow: StateFlow<Boolean> = _festiveShowBottomSnow.asStateFlow()
    
    private val _festiveShowSnowfall = MutableStateFlow(prefs.getBoolean(KEY_FESTIVE_SHOW_SNOWFALL, true))
    val festiveShowSnowfall: StateFlow<Boolean> = _festiveShowSnowfall.asStateFlow()
    
    // Blacklisted Songs
    private val _blacklistedSongs = MutableStateFlow<List<String>>(
        try {
            val json = prefs.getString(KEY_BLACKLISTED_SONGS, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    )
    val blacklistedSongs: StateFlow<List<String>> = _blacklistedSongs.asStateFlow()
    
    // Blacklisted Folders
    private val _blacklistedFolders = MutableStateFlow<List<String>>(
        try {
            val json = prefs.getString(KEY_BLACKLISTED_FOLDERS, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    )
    val blacklistedFolders: StateFlow<List<String>> = _blacklistedFolders.asStateFlow()
    
    // Whitelisted Songs
    private val _whitelistedSongs = MutableStateFlow<List<String>>(
        try {
            val json = prefs.getString(KEY_WHITELISTED_SONGS, null)
            if (json != null) {
                val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    )
    val whitelistedSongs: StateFlow<List<String>> = _whitelistedSongs.asStateFlow()
    
    // Whitelisted Folders
    private val _whitelistedFolders = MutableStateFlow<List<String>>(
        try {
            val json = prefs.getString(KEY_WHITELISTED_FOLDERS, null)
            if (json != null) {
                val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    )
    val whitelistedFolders: StateFlow<List<String>> = _whitelistedFolders.asStateFlow()

    // Pinned Folders (Explorer)
    private val _pinnedFolders = MutableStateFlow<List<String>>(
        try {
            val json = prefs.getString(KEY_PINNED_FOLDERS, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    )
    val pinnedFolders: StateFlow<List<String>> = _pinnedFolders.asStateFlow()

    // Playlist Click Behavior
    private val _playlistClickBehavior = MutableStateFlow(prefs.getString(KEY_PLAYLIST_CLICK_BEHAVIOR, "ask") ?: "ask")
    val playlistClickBehavior: StateFlow<String> = _playlistClickBehavior.asStateFlow()

    // Backup and Restore Settings
    private val _lastBackupTimestamp = MutableStateFlow(safeLong(KEY_LAST_BACKUP_TIMESTAMP, 0L))
    val lastBackupTimestamp: StateFlow<Long> = _lastBackupTimestamp.asStateFlow()
    
    private val _autoBackupEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false))
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()
    
    private val _backupLocation = MutableStateFlow(prefs.getString(KEY_BACKUP_LOCATION, null))
    val backupLocation: StateFlow<String?> = _backupLocation.asStateFlow()
    
    // Media Scan Tracking
    private val _lastScanTimestamp = MutableStateFlow(safeLong(KEY_LAST_SCAN_TIMESTAMP, 0L))
    val lastScanTimestamp: StateFlow<Long> = _lastScanTimestamp.asStateFlow()
    
    private val _lastScanDuration = MutableStateFlow(safeLong(KEY_LAST_SCAN_DURATION, 0L))
    val lastScanDuration: StateFlow<Long> = _lastScanDuration.asStateFlow()
    
    // Media Scan Filtering
    private val _allowedFormats = MutableStateFlow(
        prefs.getStringSet(KEY_ALLOWED_FORMATS, setOf("mp3", "flac", "ogg", "m4a", "opus", "wav", "aac", "wma"))
            ?.toSet() ?: setOf("mp3", "flac", "ogg", "m4a", "opus", "wav", "aac", "wma")
    )
    val allowedFormats: StateFlow<Set<String>> = _allowedFormats.asStateFlow()
    
    private val _minimumBitrate = MutableStateFlow(prefs.getInt(KEY_MINIMUM_BITRATE, 0)) // 0 = no filter
    val minimumBitrate: StateFlow<Int> = _minimumBitrate.asStateFlow()
    
    private val _minimumDuration = MutableStateFlow(safeLong(KEY_MINIMUM_DURATION, 0L)) // 0 = no filter
    val minimumDuration: StateFlow<Long> = _minimumDuration.asStateFlow()
    
    // Library Sort Order
    private val _songsSortOrder = MutableStateFlow(prefs.getString(KEY_SONGS_SORT_ORDER, "TITLE_ASC") ?: "TITLE_ASC")
    val songsSortOrder: StateFlow<String> = _songsSortOrder.asStateFlow()
    
    /**
     * Initialize scheduled workers after all StateFlow properties are initialized
     * This must be done after all MutableStateFlow declarations to avoid NullPointerException
     */
    init {
        // Schedule auto-backup if enabled
        if (prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)) {
            scheduleAutoBackup()
        }
        
        // Schedule update notification worker if enabled
        if (prefs.getBoolean(KEY_UPDATES_ENABLED, false) &&
            prefs.getBoolean(KEY_UPDATE_NOTIFICATIONS_ENABLED, false) &&
            prefs.getBoolean(KEY_USE_SMART_UPDATE_POLLING, false)) {
            scheduleUpdateNotificationWorker()
        }
    }
    
    // Playback Settings Methods
    fun setHighQualityAudio(enable: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_QUALITY_AUDIO, enable).apply()
        _highQualityAudio.value = enable
    }
    
    fun setGaplessPlayback(enable: Boolean) {
        prefs.edit().putBoolean(KEY_GAPLESS_PLAYBACK, enable).apply()
        _gaplessPlayback.value = enable
    }
    
    fun setCrossfade(enable: Boolean) {
        prefs.edit().putBoolean(KEY_CROSSFADE, enable).apply()
        _crossfade.value = enable
    }
    
    fun setCrossfadeDuration(duration: Float) {
        if (isValidCrossfadeDuration(duration)) {
            prefs.edit().putFloat(KEY_CROSSFADE_DURATION, duration).apply()
            _crossfadeDuration.value = duration
        } else {
            Log.w("AppSettings", "Invalid crossfade duration: $duration, keeping current value")
        }
    }
    
    fun setAudioNormalization(enable: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_NORMALIZATION, enable).apply()
        _audioNormalization.value = enable
    }
    
    fun setReplayGain(enable: Boolean) {
        prefs.edit().putBoolean(KEY_REPLAY_GAIN, enable).apply()
        _replayGain.value = enable
    }
    
    // Lyrics Settings Methods
    fun setShowLyrics(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_LYRICS, show).apply()
        _showLyrics.value = show
    }
    
    fun setLyricsSourcePreference(preference: LyricsSourcePreference) {
        prefs.edit().putInt(KEY_LYRICS_SOURCE_PREFERENCE, preference.ordinal).apply()
        _lyricsSourcePreference.value = preference
        // Update the backward compatibility flag
        _onlineOnlyLyrics.value = (preference == LyricsSourcePreference.API_FIRST)
    }
    
    @Deprecated("Use setLyricsSourcePreference instead")
    fun setOnlineOnlyLyrics(onlineOnly: Boolean) {
        // Convert to new preference system
        val preference = if (onlineOnly) LyricsSourcePreference.API_FIRST else LyricsSourcePreference.EMBEDDED_FIRST
        setLyricsSourcePreference(preference)
    }
    
    // Theme Settings Methods
    fun setUseSystemTheme(use: Boolean) {
        prefs.edit().putBoolean(KEY_USE_SYSTEM_THEME, use).apply()
        _useSystemTheme.value = use
    }
    
    fun setDarkMode(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, dark).apply()
        _darkMode.value = dark
    }
    
    fun setUseDynamicColors(use: Boolean) {
        prefs.edit().putBoolean(KEY_USE_DYNAMIC_COLORS, use).apply()
        _useDynamicColors.value = use
    }
    
    // Artist Separator Settings Methods
    fun setArtistSeparatorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ARTIST_SEPARATOR_ENABLED, enabled).apply()
        _artistSeparatorEnabled.value = enabled
    }
    
    fun setArtistSeparatorDelimiters(delimiters: String) {
        prefs.edit().putString(KEY_ARTIST_SEPARATOR_DELIMITERS, delimiters).apply()
        _artistSeparatorDelimiters.value = delimiters
    }
    
    fun setCustomColorScheme(scheme: String) {
        prefs.edit().putString(KEY_CUSTOM_COLOR_SCHEME, scheme).apply()
        _customColorScheme.value = scheme
    }
    
    fun setCustomFont(font: String) {
        prefs.edit().putString(KEY_CUSTOM_FONT, font).apply()
        _customFont.value = font
    }
    
    fun setColorSource(source: String) {
        prefs.edit().putString(KEY_COLOR_SOURCE, source).apply()
        _colorSource.value = source
    }
    
    fun setExtractedAlbumColors(colorsJson: String?) {
        prefs.edit().putString(KEY_EXTRACTED_ALBUM_COLORS, colorsJson).apply()
        _extractedAlbumColors.value = colorsJson
    }
    
    fun setFontSource(source: String) {
        prefs.edit().putString(KEY_FONT_SOURCE, source).apply()
        _fontSource.value = source
    }
    
    fun setCustomFontPath(path: String?) {
        prefs.edit().putString(KEY_CUSTOM_FONT_PATH, path).apply()
        _customFontPath.value = path
    }
    
    fun setCustomFontFamily(family: String) {
        prefs.edit().putString(KEY_CUSTOM_FONT_FAMILY, family).apply()
        _customFontFamily.value = family
    }
    
    // Library Settings Methods
    fun setAlbumViewType(viewType: AlbumViewType) {
        prefs.edit().putString(KEY_ALBUM_VIEW_TYPE, viewType.name).apply()
        _albumViewType.value = viewType
    }
    
    fun setArtistViewType(viewType: ArtistViewType) {
        prefs.edit().putString(KEY_ARTIST_VIEW_TYPE, viewType.name).apply()
        _artistViewType.value = viewType
    }
    
    fun setPlaylistViewType(viewType: PlaylistViewType) {
        prefs.edit().putString(KEY_PLAYLIST_VIEW_TYPE, viewType.name).apply()
        _playlistViewType.value = viewType
    }
    
    fun setAlbumSortOrder(sortOrder: String) {
        prefs.edit().putString(KEY_ALBUM_SORT_ORDER, sortOrder).apply()
        _albumSortOrder.value = sortOrder
    }
    
    fun setPlaylistSortOrder(sortOrder: String) {
        prefs.edit().putString(KEY_PLAYLIST_SORT_ORDER, sortOrder).apply()
        _playlistSortOrder.value = sortOrder
    }
    
    fun setArtistCollaborationMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ARTIST_COLLABORATION_MODE, enabled).apply()
        _artistCollaborationMode.value = enabled
    }
    
    fun setLibraryTabOrder(tabOrder: List<String>) {
        val orderString = tabOrder.joinToString(",")
        prefs.edit().putString(KEY_LIBRARY_TAB_ORDER, orderString).apply()
        _libraryTabOrder.value = tabOrder
    }
    
    fun resetLibraryTabOrder() {
        prefs.edit().remove(KEY_LIBRARY_TAB_ORDER).apply()
        _libraryTabOrder.value = defaultTabOrder
    }
    
    fun setPlayerChipOrder(chipOrder: List<String>) {
        val orderString = chipOrder.joinToString(",")
        prefs.edit().putString(KEY_PLAYER_CHIP_ORDER, orderString).apply()
        _playerChipOrder.value = chipOrder
    }
    
    fun resetPlayerChipOrder() {
        prefs.edit().remove(KEY_PLAYER_CHIP_ORDER).apply()
        _playerChipOrder.value = defaultChipOrder
    }
    
    fun setDefaultScreen(screen: String) {
        prefs.edit().putString(KEY_DEFAULT_SCREEN, screen).apply()
        _defaultScreen.value = screen
    }
    
    fun setHiddenLibraryTabs(hiddenTabs: Set<String>) {
        val hiddenString = hiddenTabs.joinToString(",")
        prefs.edit().putString(KEY_HIDDEN_LIBRARY_TABS, hiddenString).apply()
        _hiddenLibraryTabs.value = hiddenTabs
    }
    
    fun setHiddenPlayerChips(hiddenChips: Set<String>) {
        val hiddenString = hiddenChips.joinToString(",")
        prefs.edit().putString(KEY_HIDDEN_PLAYER_CHIPS, hiddenString).apply()
        _hiddenPlayerChips.value = hiddenChips
    }
    
    fun setGroupByAlbumArtist(enable: Boolean) {
        prefs.edit().putBoolean(KEY_GROUP_BY_ALBUM_ARTIST, enable).apply()
        _groupByAlbumArtist.value = enable
    }
    
    fun setIgnoreMediaStoreCovers(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IGNORE_MEDIASTORE_COVERS, enabled).apply()
        _ignoreMediaStoreCovers.value = enabled
    }
    
    fun setShowAlphabetBar(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ALPHABET_BAR, show).apply()
        _showAlphabetBar.value = show
    }
    
    fun setShowScrollToTop(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SCROLL_TO_TOP, show).apply()
        _showScrollToTop.value = show
    }
    
    // App Mode setter methods
    fun setAppMode(mode: String) {
        prefs.edit().putString(KEY_APP_MODE, mode).apply()
        _appMode.value = mode
    }
    
    fun setStreamingService(service: String) {
        prefs.edit().putString(KEY_STREAMING_SERVICE, service).apply()
        _streamingService.value = service
    }
    
    fun setStreamingQuality(quality: String) {
        prefs.edit().putString(KEY_STREAMING_QUALITY, quality).apply()
        _streamingQuality.value = quality
    }
    
    fun setAllowCellularStreaming(allow: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_CELLULAR_STREAMING, allow).apply()
        _allowCellularStreaming.value = allow
    }
    
    fun setOfflineMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply()
        _offlineMode.value = enabled
    }
    
    fun setSongsSortOrder(sortOrder: String) {
        prefs.edit().putString(KEY_SONGS_SORT_ORDER, sortOrder).apply()
        _songsSortOrder.value = sortOrder
    }
    
    // Audio Device Settings Methods
    fun setLastAudioDevice(deviceId: String?) {
        if (deviceId == null) {
            prefs.edit().remove(KEY_LAST_AUDIO_DEVICE).apply()
        } else {
            prefs.edit().putString(KEY_LAST_AUDIO_DEVICE, deviceId).apply()
        }
        _lastAudioDevice.value = deviceId
    }
    
    fun setAutoConnectDevice(enable: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CONNECT_DEVICE, enable).apply()
        _autoConnectDevice.value = enable
    }
    
    fun setUseSystemVolume(enable: Boolean) {
        prefs.edit().putBoolean(KEY_USE_SYSTEM_VOLUME, enable).apply()
        _useSystemVolume.value = enable
    }
    
    // Equalizer Settings Methods
    fun setEqualizerEnabled(enable: Boolean) {
        prefs.edit().putBoolean(KEY_EQUALIZER_ENABLED, enable).apply()
        _equalizerEnabled.value = enable
    }
    
    fun setEqualizerPreset(preset: String) {
        prefs.edit().putString(KEY_EQUALIZER_PRESET, preset).apply()
        _equalizerPreset.value = preset
    }
    
    fun setEqualizerBandLevels(levels: String) {
        prefs.edit().putString(KEY_EQUALIZER_BAND_LEVELS, levels).apply()
        _equalizerBandLevels.value = levels
    }
    
    fun setAutoEQProfile(profileName: String) {
        prefs.edit().putString(KEY_AUTOEQ_PROFILE, profileName).apply()
        _autoEQProfile.value = profileName
    }
    
    fun setUserAudioDevices(devicesJson: String?) {
        prefs.edit().putString(KEY_USER_AUDIO_DEVICES, devicesJson).apply()
        _userAudioDevices.value = devicesJson
    }
    
    fun setActiveAudioDeviceId(deviceId: String?) {
        prefs.edit().putString(KEY_ACTIVE_AUDIO_DEVICE_ID, deviceId).apply()
        _activeAudioDeviceId.value = deviceId
    }
    
    fun setDismissedAutoEQSuggestions(dismissedDevices: String?) {
        prefs.edit().putString(KEY_DISMISSED_AUTOEQ_SUGGESTIONS, dismissedDevices).apply()
        _dismissedAutoEQSuggestions.value = dismissedDevices
    }
    
    fun setBassBoostEnabled(enable: Boolean) {
        prefs.edit().putBoolean(KEY_BASS_BOOST_ENABLED, enable).apply()
        _bassBoostEnabled.value = enable
    }
    
    fun setBassBoostStrength(strength: Int) {
        prefs.edit().putInt(KEY_BASS_BOOST_STRENGTH, strength).apply()
        _bassBoostStrength.value = strength
    }
    
    fun setBassBoostAvailable(available: Boolean) {
        prefs.edit().putBoolean(KEY_BASS_BOOST_AVAILABLE, available).apply()
    }
    
    fun isBassBoostAvailable(): Boolean {
        return prefs.getBoolean(KEY_BASS_BOOST_AVAILABLE, true)
    }
    
    fun setVirtualizerEnabled(enable: Boolean) {
        prefs.edit().putBoolean(KEY_VIRTUALIZER_ENABLED, enable).apply()
        _virtualizerEnabled.value = enable
    }
    
    fun setVirtualizerStrength(strength: Int) {
        prefs.edit().putInt(KEY_VIRTUALIZER_STRENGTH, strength).apply()
        _virtualizerStrength.value = strength
    }
    
    // Sleep Timer Methods
    fun setSleepTimerActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_SLEEP_TIMER_ACTIVE, active).apply()
        _sleepTimerActive.value = active
    }
    
    fun setSleepTimerRemainingSeconds(seconds: Long) {
        prefs.edit().putLong(KEY_SLEEP_TIMER_REMAINING_SECONDS, seconds).apply()
        _sleepTimerRemainingSeconds.value = seconds
    }
    
    fun setSleepTimerAction(action: String) {
        prefs.edit().putString(KEY_SLEEP_TIMER_ACTION, action).apply()
        _sleepTimerAction.value = action
    }
    
    // Queue & Playback Behavior Methods
    fun setShuffleUsesExoplayer(useExoplayer: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE_USES_EXOPLAYER, useExoplayer).apply()
        _shuffleUsesExoplayer.value = useExoplayer
    }
    
    fun setAutoAddToQueue(autoAdd: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ADD_TO_QUEUE, autoAdd).apply()
        _autoAddToQueue.value = autoAdd
    }
    
    fun setClearQueueOnNewSong(clearQueue: Boolean) {
        prefs.edit().putBoolean(KEY_CLEAR_QUEUE_ON_NEW_SONG, clearQueue).apply()
        _clearQueueOnNewSong.value = clearQueue
    }
    
    fun setShowQueueDialog(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_QUEUE_DIALOG, show).apply()
        _showQueueDialog.value = show
    }
    
    fun setRepeatModePersistence(persist: Boolean) {
        prefs.edit().putBoolean(KEY_REPEAT_MODE_PERSISTENCE, persist).apply()
        _repeatModePersistence.value = persist
    }
    
    fun setShuffleModePersistence(persist: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE_MODE_PERSISTENCE, persist).apply()
        _shuffleModePersistence.value = persist
    }
    
    fun setSavedShuffleState(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVED_SHUFFLE_STATE, enabled).apply()
        _savedShuffleState.value = enabled
    }
    
    fun setSavedRepeatMode(mode: Int) {
        prefs.edit().putInt(KEY_SAVED_REPEAT_MODE, mode).apply()
        _savedRepeatMode.value = mode
    }
    
    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()
        _playbackSpeed.value = speed
    }
    
    // Time Format Settings Methods
    fun setUseHoursInTimeFormat(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_HOURS_IN_TIME_FORMAT, enabled).apply()
        _useHoursInTimeFormat.value = enabled
    }
    
    // Stop Playback on App Close Methods
    fun setStopPlaybackOnAppClose(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STOP_PLAYBACK_ON_APP_CLOSE, enabled).apply()
        _stopPlaybackOnAppClose.value = enabled
    }
    
    // Queue Persistence Methods
    fun setQueuePersistenceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QUEUE_PERSISTENCE_ENABLED, enabled).apply()
        _queuePersistenceEnabled.value = enabled
        
        // Clear saved queue if persistence is disabled
        if (!enabled) {
            clearSavedQueue()
        }
    }
    
    fun setSavedQueue(songIds: List<String>) {
        val json = Gson().toJson(songIds)
        prefs.edit().putString(KEY_SAVED_QUEUE, json).apply()
        _savedQueue.value = songIds
    }
    
    fun setSavedQueueIndex(index: Int) {
        prefs.edit().putInt(KEY_SAVED_QUEUE_INDEX, index).apply()
        _savedQueueIndex.value = index
    }
    
    fun setSavedPlaybackPosition(position: Long) {
        prefs.edit().putLong(KEY_SAVED_PLAYBACK_POSITION, position).apply()
        _savedPlaybackPosition.value = position
    }
    
    fun clearSavedQueue() {
        prefs.edit().remove(KEY_SAVED_QUEUE).apply()
        prefs.edit().remove(KEY_SAVED_QUEUE_INDEX).apply()
        prefs.edit().remove(KEY_SAVED_PLAYBACK_POSITION).apply()
        _savedQueue.value = emptyList()
        _savedQueueIndex.value = -1
        _savedPlaybackPosition.value = 0L
    }
    
    // Cache Settings Methods
    fun setMaxCacheSize(size: Long) {
        if (isValidCacheSize(size)) {
            prefs.edit().putLong(KEY_MAX_CACHE_SIZE, size).apply()
            _maxCacheSize.value = size
        } else {
            Log.w("AppSettings", "Invalid cache size: $size, keeping current value")
        }
    }
    
    fun setClearCacheOnExit(clear: Boolean) {
        prefs.edit().putBoolean(KEY_CLEAR_CACHE_ON_EXIT, clear).apply()
        _clearCacheOnExit.value = clear
    }
    
    // Search History Methods
    fun setSearchHistory(history: String?) {
        if (history == null) {
            prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
        } else {
            prefs.edit().putString(KEY_SEARCH_HISTORY, history).apply()
        }
        _searchHistory.value = history
    }

    // Playlists
    fun setPlaylists(playlistsJson: String?) {
        if (playlistsJson == null) {
            prefs.edit().remove(KEY_PLAYLISTS).apply() // Use apply() to prevent ANR
        } else {
            prefs.edit().putString(KEY_PLAYLISTS, playlistsJson).apply() // Use apply() to prevent ANR
        }
        _playlists.value = playlistsJson
    }

    fun setFavoriteSongs(favoriteSongsJson: String?) {
        if (favoriteSongsJson == null) {
            prefs.edit().remove(KEY_FAVORITE_SONGS).apply() // Use apply() to prevent ANR
        } else {
            prefs.edit().putString(KEY_FAVORITE_SONGS, favoriteSongsJson).apply() // Use apply() to prevent ANR
        }
        _favoriteSongs.value = favoriteSongsJson
    }
    
    fun setDefaultPlaylistsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_PLAYLISTS_ENABLED, enabled).apply()
        _defaultPlaylistsEnabled.value = enabled
    }
    
    fun setEnableRatingSystem(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_RATING_SYSTEM, enabled).apply()
        _enableRatingSystem.value = enabled
    }

    // User Statistics Methods
    fun setListeningTime(time: Long) {
        prefs.edit().putLong(KEY_LISTENING_TIME, time).apply()
        _listeningTime.value = time
    }
    
    fun setSongsPlayed(count: Int) {
        prefs.edit().putInt(KEY_SONGS_PLAYED, count).apply()
        _songsPlayed.value = count
    }
    
    fun setUniqueArtists(count: Int) {
        prefs.edit().putInt(KEY_UNIQUE_ARTISTS, count).apply()
        _uniqueArtists.value = count
    }
    
    fun setGenrePreferences(preferences: Map<String, Int>) {
        val json = Gson().toJson(preferences)
        prefs.edit().putString(KEY_GENRE_PREFERENCES, json).apply()
        _genrePreferences.value = preferences
    }
    
    fun setTimeBasedPreferences(preferences: Map<Int, List<String>>) {
        val json = Gson().toJson(preferences)
        prefs.edit().putString(KEY_TIME_BASED_PREFERENCES, json).apply()
        _timeBasedPreferences.value = preferences
    }

    // Recently Played Methods
    fun updateRecentlyPlayed(songIds: List<String>) {
        val json = Gson().toJson(songIds)
        prefs.edit().putString(KEY_RECENTLY_PLAYED, json).apply()
        _recentlyPlayed.value = songIds
    }
    
    fun updateLastPlayedTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_PLAYED_TIMESTAMP, timestamp).apply()
        _lastPlayedTimestamp.value = timestamp
    }
    
    
    // API Enable/Disable Methods
    fun setDeezerApiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEEZER_API_ENABLED, enabled).apply()
        _deezerApiEnabled.value = enabled
    }
    
    fun setCanvasApiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CANVAS_API_ENABLED, enabled).apply()
        _canvasApiEnabled.value = enabled
    }
    
    fun setLrcLibApiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LRCLIB_API_ENABLED, enabled).apply()
        _lrclibApiEnabled.value = enabled
    }
    
    fun setYTMusicApiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_YTMUSIC_API_ENABLED, enabled).apply()
        _ytMusicApiEnabled.value = enabled
    }
    
    fun setSpotifyApiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPOTIFY_API_ENABLED, enabled).apply()
        _spotifyApiEnabled.value = enabled
    }
    
    fun setSpotifyClientId(clientId: String) {
        prefs.edit().putString(KEY_SPOTIFY_CLIENT_ID, clientId).apply()
        _spotifyClientId.value = clientId
    }
    
    fun setSpotifyClientSecret(clientSecret: String) {
        prefs.edit().putString(KEY_SPOTIFY_CLIENT_SECRET, clientSecret).apply()
        _spotifyClientSecret.value = clientSecret
    }

    // Scrobbling Methods
    fun setScrobblingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCROBBLING_ENABLED, enabled).apply()
        _scrobblingEnabled.value = enabled
    }
    
    // Discord Rich Presence Methods
    fun setDiscordRichPresenceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISCORD_RICH_PRESENCE_ENABLED, enabled).apply()
        _discordRichPresenceEnabled.value = enabled
    }
    
    // General Broadcast Status Methods
    fun setBroadcastStatusEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BROADCAST_STATUS_ENABLED, enabled).apply()
        _broadcastStatusEnabled.value = enabled
    }

    // Enhanced User Preferences Methods
    fun updateFavoriteGenres(genres: Map<String, Int>) {
        val json = Gson().toJson(genres)
        prefs.edit().putString(KEY_FAVORITE_GENRES, json).apply()
        _favoriteGenres.value = genres
    }
    
    fun updateDailyListeningStats(stats: Map<String, Long>) {
        val json = Gson().toJson(stats)
        prefs.edit().putString(KEY_DAILY_LISTENING_STATS, json).apply()
        _dailyListeningStats.value = stats
    }
    
    fun updateWeeklyTopArtists(artists: Map<String, Int>) {
        val json = Gson().toJson(artists)
        prefs.edit().putString(KEY_WEEKLY_TOP_ARTISTS, json).apply()
        _weeklyTopArtists.value = artists
    }
    
    fun updateMoodPreferences(preferences: Map<String, List<String>>) {
        val json = Gson().toJson(preferences)
        prefs.edit().putString(KEY_MOOD_PREFERENCES, json).apply()
        _moodPreferences.value = preferences
    }

    // Song Play Counts Methods
    fun setSongPlayCounts(counts: Map<String, Int>) {
        val json = Gson().toJson(counts)
        prefs.edit().putString(KEY_SONG_PLAY_COUNTS, json).apply()
        _songPlayCounts.value = counts
    }

    // Onboarding Methods
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _onboardingCompleted.value = completed
    }

    fun setInitialMediaScanCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_INITIAL_MEDIA_SCAN_COMPLETED, completed).apply()
        _initialMediaScanCompleted.value = completed
    }

    fun setGenreDetectionCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_GENRE_DETECTION_COMPLETED, completed).apply()
        _genreDetectionCompleted.value = completed
    }

    // App Updater Settings Methods
    fun setAutoCheckForUpdates(enable: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_FOR_UPDATES, enable).apply()
        _autoCheckForUpdates.value = enable
    }

    fun setUpdateChannel(channel: String) {
        prefs.edit().putString(KEY_UPDATE_CHANNEL, channel).apply()
        _updateChannel.value = channel
    }

    fun setUpdatesEnabled(enable: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATES_ENABLED, enable).apply()
        _updatesEnabled.value = enable
        
        // Update WorkManager scheduling based on new state
        if (enable && _updateNotificationsEnabled.value && _useSmartUpdatePolling.value) {
            scheduleUpdateNotificationWorker()
        } else {
            cancelUpdateNotificationWorker()
        }
    }

    fun setUpdateNotificationsEnabled(enable: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_NOTIFICATIONS_ENABLED, enable).apply()
        _updateNotificationsEnabled.value = enable
        
        // Update WorkManager scheduling
        if (enable && _updatesEnabled.value && _useSmartUpdatePolling.value) {
            scheduleUpdateNotificationWorker()
        } else {
            cancelUpdateNotificationWorker()
        }
    }

    fun setUseSmartUpdatePolling(enable: Boolean) {
        prefs.edit().putBoolean(KEY_USE_SMART_UPDATE_POLLING, enable).apply()
        _useSmartUpdatePolling.value = enable
        
        // Update WorkManager scheduling
        if (enable && _updatesEnabled.value && _updateNotificationsEnabled.value) {
            scheduleUpdateNotificationWorker()
        } else {
            cancelUpdateNotificationWorker()
        }
    }

    fun setMediaScanMode(mode: String) {
        prefs.edit().putString(KEY_MEDIA_SCAN_MODE, mode).apply()
        _mediaScanMode.value = mode
    }

    fun setUpdateCheckIntervalHours(hours: Int) {
        prefs.edit().putInt(KEY_UPDATE_CHECK_INTERVAL_HOURS, hours).apply()
        _updateCheckIntervalHours.value = hours
    }

    // Beta Program Methods
    fun setHasShownBetaPopup(shown: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SHOWN_BETA_POPUP, shown).apply()
        _hasShownBetaPopup.value = shown
    }

    // Crash Reporting Methods
    fun setLastCrashLog(log: String?) {
        // Use commit() instead of apply() to ensure immediate persistence for crash logs
        if (log == null) {
            prefs.edit().remove(KEY_LAST_CRASH_LOG).commit()
        } else {
            prefs.edit().putString(KEY_LAST_CRASH_LOG, log).commit()
        }
        _lastCrashLog.value = log
    }

    fun addCrashLogEntry(log: String) {
        val currentHistory = _crashLogHistory.value.toMutableList()
        val newEntry = CrashLogEntry(System.currentTimeMillis(), log)
        currentHistory.add(0, newEntry) // Add to the beginning
        // Keep only the last 6 crash logs to prevent excessive storage
        val limitedHistory = currentHistory.take(6)
        val json = Gson().toJson(limitedHistory)
        // Update in-memory state first for immediate UI feedback
        _crashLogHistory.value = limitedHistory
        // Use commit() instead of apply() for crash logs to ensure persistence before process exits
        // This is critical - crash logs must be written synchronously or they'll be lost
        try {
            prefs.edit().putString(KEY_CRASH_LOG_HISTORY, json).commit()
        } catch (e: Exception) {
            // If commit fails (shouldn't happen), at least log it
            Log.e("CrashLog", "Failed to persist crash log to SharedPreferences", e)
        }
    }

    fun clearCrashLogHistory() {
        prefs.edit().remove(KEY_CRASH_LOG_HISTORY).apply()
        _crashLogHistory.value = emptyList()
    }
    
    // Haptic Feedback Methods
    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, enabled).apply()
        _hapticFeedbackEnabled.value = enabled
    }
    
    // Notification Settings Methods
    fun setUseCustomNotification(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_CUSTOM_NOTIFICATION, enabled).apply()
        _useCustomNotification.value = enabled
    }
    
    // Codec Monitoring & Enhanced Seeking Methods
    fun setCodecMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CODEC_MONITORING_ENABLED, enabled).apply()
        _codecMonitoringEnabled.value = enabled
    }
    
    fun setShowCodecNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CODEC_NOTIFICATIONS, enabled).apply()
        _showCodecNotifications.value = enabled
    }
    
    fun setEnhancedSeekingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENHANCED_SEEKING_ENABLED, enabled).apply()
        _enhancedSeekingEnabled.value = enabled
    }
    
    fun setAudioDeviceLoggingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("audio_device_logging_enabled", enabled).apply()
        _audioDeviceLoggingEnabled.value = enabled
    }
    
    // Media3 1.9.0 Feature Methods
    fun setUseCustomCommandButtons(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_CUSTOM_COMMAND_BUTTONS, enabled).apply()
        _useCustomCommandButtons.value = enabled
    }
    
    fun setScrubbingModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCRUBBING_MODE_ENABLED, enabled).apply()
        _scrubbingModeEnabled.value = enabled
    }
    
    fun setStuckPlayerDetectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STUCK_PLAYER_DETECTION_ENABLED, enabled).apply()
        _stuckPlayerDetectionEnabled.value = enabled
    }
    
    // UI Settings Methods
    fun setUseSettings(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_SETTINGS, enabled).apply()
        _useSettings.value = enabled
    }
    
    // Festive Theme Settings Methods
    fun setFestiveThemeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FESTIVE_THEME_ENABLED, enabled).apply()
        _festiveThemeEnabled.value = enabled
    }
    
    fun setFestiveThemeType(type: String) {
        prefs.edit().putString(KEY_FESTIVE_THEME_TYPE, type).apply()
        _festiveThemeType.value = type
    }
    
    fun setFestiveThemeIntensity(intensity: Float) {
        prefs.edit().putFloat(KEY_FESTIVE_THEME_INTENSITY, intensity).apply()
        _festiveThemeIntensity.value = intensity
    }
    
    fun setFestiveThemeAutoDetect(autoDetect: Boolean) {
        prefs.edit().putBoolean(KEY_FESTIVE_THEME_AUTO_DETECT, autoDetect).apply()
        _festiveThemeAutoDetect.value = autoDetect
    }
    
    fun setFestiveSnowflakeSize(size: Float) {
        prefs.edit().putFloat(KEY_FESTIVE_SNOWFLAKE_SIZE, size).apply()
        _festiveSnowflakeSize.value = size
    }
    
    fun setFestiveSnowflakeArea(area: String) {
        prefs.edit().putString(KEY_FESTIVE_SNOWFLAKE_AREA, area).apply()
        _festiveSnowflakeArea.value = area
    }
    
    // Festive Decoration Position Methods
    fun setFestiveShowTopLights(show: Boolean) {
        prefs.edit().putBoolean(KEY_FESTIVE_SHOW_TOP_LIGHTS, show).apply()
        _festiveShowTopLights.value = show
    }
    
    fun setFestiveShowSideGarland(show: Boolean) {
        prefs.edit().putBoolean(KEY_FESTIVE_SHOW_SIDE_GARLAND, show).apply()
        _festiveShowSideGarland.value = show
    }
    
    fun setFestiveShowBottomSnow(show: Boolean) {
        prefs.edit().putBoolean(KEY_FESTIVE_SHOW_BOTTOM_SNOW, show).apply()
        _festiveShowBottomSnow.value = show
    }
    
    fun setFestiveShowSnowfall(show: Boolean) {
        prefs.edit().putBoolean(KEY_FESTIVE_SHOW_SNOWFALL, show).apply()
        _festiveShowSnowfall.value = show
    }
    
    // Blacklisted Songs Methods
    fun addToBlacklist(songId: String) {
        val currentList = _blacklistedSongs.value.toMutableList()
        if (!currentList.contains(songId)) {
            currentList.add(songId)
            val json = Gson().toJson(currentList)
            prefs.edit().putString(KEY_BLACKLISTED_SONGS, json).apply()
            _blacklistedSongs.value = currentList
        }
    }
    
    fun removeFromBlacklist(songId: String) {
        val currentList = _blacklistedSongs.value.toMutableList()
        if (currentList.remove(songId)) {
            val json = Gson().toJson(currentList)
            prefs.edit().putString(KEY_BLACKLISTED_SONGS, json).apply()
            _blacklistedSongs.value = currentList
        }
    }
    
    fun isBlacklisted(songId: String): Boolean {
        return _blacklistedSongs.value.contains(songId)
    }
    
    fun clearBlacklist() {
        prefs.edit().remove(KEY_BLACKLISTED_SONGS).apply()
        _blacklistedSongs.value = emptyList()
    }
    
    // Blacklisted Folders Methods
    fun addFolderToBlacklist(folderPath: String) {
        val currentList = _blacklistedFolders.value.toMutableList()
        if (!currentList.contains(folderPath)) {
            currentList.add(folderPath)
            val json = Gson().toJson(currentList)
            prefs.edit().putString(KEY_BLACKLISTED_FOLDERS, json).apply()
            _blacklistedFolders.value = currentList
        }
    }
    
    fun removeFolderFromBlacklist(folderPath: String) {
        val currentList = _blacklistedFolders.value.toMutableList()
        if (currentList.remove(folderPath)) {
            val json = Gson().toJson(currentList)
            prefs.edit().putString(KEY_BLACKLISTED_FOLDERS, json).apply()
            _blacklistedFolders.value = currentList
        }
    }
    
    fun isFolderBlacklisted(folderPath: String): Boolean {
        return _blacklistedFolders.value.any { blacklistedPath ->
            folderPath.startsWith(blacklistedPath, ignoreCase = true)
        }
    }
    
    fun clearFolderBlacklist() {
        prefs.edit().remove(KEY_BLACKLISTED_FOLDERS).apply()
        _blacklistedFolders.value = emptyList()
    }
    
    // Bulk operations for better synchronization
    fun removeFolderAndRelatedSongs(folderPath: String, songsInFolder: List<String>) {
        // Remove folder from blacklist
        val currentFolders = _blacklistedFolders.value.toMutableList()
        if (currentFolders.remove(folderPath)) {
            val foldersJson = Gson().toJson(currentFolders)
            prefs.edit().putString(KEY_BLACKLISTED_FOLDERS, foldersJson).apply()
            _blacklistedFolders.value = currentFolders
        }
        
        // Remove related songs from individual blacklist
        val currentSongs = _blacklistedSongs.value.toMutableList()
        var songsRemoved = false
        songsInFolder.forEach { songId ->
            if (currentSongs.remove(songId)) {
                songsRemoved = true
            }
        }
        
        if (songsRemoved) {
            val songsJson = Gson().toJson(currentSongs)
            prefs.edit().putString(KEY_BLACKLISTED_SONGS, songsJson).apply()
            _blacklistedSongs.value = currentSongs
        }
    }
    
    fun addFolderAndOptionalSong(folderPath: String, songId: String?) {
        // Add folder to blacklist
        val currentFolders = _blacklistedFolders.value.toMutableList()
        if (!currentFolders.contains(folderPath)) {
            currentFolders.add(folderPath)
            val foldersJson = Gson().toJson(currentFolders)
            prefs.edit().putString(KEY_BLACKLISTED_FOLDERS, foldersJson).apply()
            _blacklistedFolders.value = currentFolders
        }
        
        // Optionally add song to individual blacklist for immediate synchronization
        songId?.let { id ->
            val currentSongs = _blacklistedSongs.value.toMutableList()
            if (!currentSongs.contains(id)) {
                currentSongs.add(id)
                val songsJson = Gson().toJson(currentSongs)
                prefs.edit().putString(KEY_BLACKLISTED_SONGS, songsJson).apply()
                _blacklistedSongs.value = currentSongs
            }
        }
    }
    
    // Helper method to check if a song would be filtered by current blacklist rules
    fun isEffectivelyBlacklisted(songId: String, songPath: String?): Boolean {
        // Check individual song blacklist
        if (_blacklistedSongs.value.contains(songId)) return true
        
        // Check folder blacklist
        if (songPath != null) {
            return _blacklistedFolders.value.any { folderPath ->
                songPath.startsWith(folderPath, ignoreCase = true)
            }
        }
        
        return false
    }
    
    // Whitelisted Songs Methods
    fun addToWhitelist(songId: String) {
        val currentList = _whitelistedSongs.value.toMutableList()
        if (!currentList.contains(songId)) {
            currentList.add(songId)
            val json = Gson().toJson(currentList)
            prefs.edit().putString(KEY_WHITELISTED_SONGS, json).apply()
            _whitelistedSongs.value = currentList
        }
    }
    
    fun removeFromWhitelist(songId: String) {
        val currentList = _whitelistedSongs.value.toMutableList()
        if (currentList.remove(songId)) {
            val json = Gson().toJson(currentList)
            prefs.edit().putString(KEY_WHITELISTED_SONGS, json).apply()
            _whitelistedSongs.value = currentList
        }
    }
    
    fun isSongWhitelisted(songId: String): Boolean {
        return _whitelistedSongs.value.contains(songId)
    }
    
    fun clearWhitelist() {
        prefs.edit().remove(KEY_WHITELISTED_SONGS).apply()
        _whitelistedSongs.value = emptyList()
    }
    
    // Song Rating Methods
    /**
     * Set rating for a song (0-5 stars)
     * 0 = No rating/removed from favorites
     * 1-5 = Rating levels, with 5 being "Absolute Favorite"
     */
    fun setSongRating(songId: String, rating: Int) {
        if (rating !in 0..5) {
            Log.w("AppSettings", "Invalid rating value: $rating. Must be 0-5")
            return
        }
        
        val currentRatings = _songRatings.value.toMutableMap()
        if (rating == 0) {
            // Rating 0 means remove rating
            currentRatings.remove(songId)
        } else {
            currentRatings[songId] = rating
        }
        
        val json = Gson().toJson(currentRatings)
        prefs.edit().putString(KEY_SONG_RATINGS, json).apply()
        _songRatings.value = currentRatings
        
        Log.d("AppSettings", "Set rating for song $songId: $rating")
    }
    
    /**
     * Get rating for a specific song (0-5)
     * Returns 0 if song has no rating
     */
    fun getSongRating(songId: String): Int {
        return _songRatings.value[songId] ?: 0
    }
    
    /**
     * Get all rated songs as a map of songId to rating
     */
    fun getAllRatedSongs(): Map<String, Int> {
        return _songRatings.value.toMap()
    }
    
    /**
     * Get songs filtered by minimum rating
     */
    fun getSongsByMinimumRating(minRating: Int): List<String> {
        if (minRating !in 1..5) return emptyList()
        return _songRatings.value.filter { it.value >= minRating }.keys.toList()
    }
    
    /**
     * Get songs with a specific rating
     */
    fun getSongsByRating(rating: Int): List<String> {
        if (rating !in 1..5) return emptyList()
        return _songRatings.value.filter { it.value == rating }.keys.toList()
    }
    
    /**
     * Clear all song ratings
     */
    fun clearAllRatings() {
        prefs.edit().remove(KEY_SONG_RATINGS).apply()
        _songRatings.value = emptyMap()
        Log.d("AppSettings", "Cleared all song ratings")
    }
    
    /**
     * Check if a song has any rating (1-5)
     */
    fun isSongRated(songId: String): Boolean {
        return _songRatings.value.containsKey(songId)
    }
    
    /**
     * Get count of songs by rating level
     */
    fun getRatingDistribution(): Map<Int, Int> {
        val distribution = mutableMapOf<Int, Int>()
        for (rating in 1..5) {
            distribution[rating] = _songRatings.value.count { it.value == rating }
        }
        return distribution
    }
    
    // Whitelisted Folders Methods
    fun addFolderToWhitelist(folderPath: String) {
        val currentList = _whitelistedFolders.value.toMutableList()
        if (!currentList.contains(folderPath)) {
            currentList.add(folderPath)
            val json = Gson().toJson(currentList)
            prefs.edit().putString(KEY_WHITELISTED_FOLDERS, json).apply()
            _whitelistedFolders.value = currentList
        }
    }
    
    fun removeFolderFromWhitelist(folderPath: String) {
        val currentList = _whitelistedFolders.value.toMutableList()
        if (currentList.remove(folderPath)) {
            val json = Gson().toJson(currentList)
            prefs.edit().putString(KEY_WHITELISTED_FOLDERS, json).apply()
            _whitelistedFolders.value = currentList
        }
    }
    
    fun isFolderWhitelisted(folderPath: String): Boolean {
        return _whitelistedFolders.value.any { whitelistedPath ->
            folderPath.startsWith(whitelistedPath, ignoreCase = true)
        }
    }
    
    fun clearFolderWhitelist() {
        prefs.edit().remove(KEY_WHITELISTED_FOLDERS).apply()
        _whitelistedFolders.value = emptyList()
    }

    // Pinned Folders Methods (Explorer)
    fun addFolderToPinned(folderPath: String) {
        val currentList = _pinnedFolders.value.toMutableList()
        if (!currentList.contains(folderPath)) {
            currentList.add(folderPath)
            val json = Gson().toJson(currentList)
            prefs.edit().putString(KEY_PINNED_FOLDERS, json).apply()
            _pinnedFolders.value = currentList
        }
    }

    fun removeFolderFromPinned(folderPath: String) {
        val currentList = _pinnedFolders.value.toMutableList()
        if (currentList.remove(folderPath)) {
            val json = Gson().toJson(currentList)
            prefs.edit().putString(KEY_PINNED_FOLDERS, json).apply()
            _pinnedFolders.value = currentList
        }
    }

    fun isFolderPinned(folderPath: String): Boolean {
        return _pinnedFolders.value.contains(folderPath)
    }

    fun clearPinnedFolders() {
        prefs.edit().remove(KEY_PINNED_FOLDERS).apply()
        _pinnedFolders.value = emptyList()
    }
    
    // Playlist Click Behavior Methods
    fun setPlaylistClickBehavior(behavior: String) {
        if (behavior in listOf("ask", "play_all", "play_one")) {
            prefs.edit().putString(KEY_PLAYLIST_CLICK_BEHAVIOR, behavior).apply()
            _playlistClickBehavior.value = behavior
        }
    }
    
    // Helper method to check if a song would be filtered by current whitelist rules
    fun isEffectivelyWhitelisted(songId: String, songPath: String?): Boolean {
        // If no whitelist exists, all songs are effectively whitelisted
        if (_whitelistedSongs.value.isEmpty() && _whitelistedFolders.value.isEmpty()) {
            return true
        }
        
        // Check individual song whitelist
        if (_whitelistedSongs.value.contains(songId)) return true
        
        // Check folder whitelist
        if (songPath != null) {
            return _whitelistedFolders.value.any { folderPath ->
                songPath.startsWith(folderPath, ignoreCase = true)
            }
        }
        
        return false
    }
    
    // Backup and Restore Methods
    fun setLastBackupTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIMESTAMP, timestamp).apply()
        _lastBackupTimestamp.value = timestamp
    }
    
    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
        _autoBackupEnabled.value = enabled
        
        // Schedule or cancel auto-backup worker
        if (enabled) {
            scheduleAutoBackup()
            // Trigger an immediate backup when enabling auto-backup
            triggerImmediateBackup()
            Log.d("AppSettings", "Auto-backup enabled: triggering immediate backup")
        } else {
            cancelAutoBackup()
        }
    }
    
    // Media Scanning Methods
    fun setLastScanTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SCAN_TIMESTAMP, timestamp).apply()
        _lastScanTimestamp.value = timestamp
    }
    
    fun setLastScanDuration(duration: Long) {
        prefs.edit().putLong(KEY_LAST_SCAN_DURATION, duration).apply()
        _lastScanDuration.value = duration
    }
    
    fun setAllowedFormats(formats: Set<String>) {
        prefs.edit().putStringSet(KEY_ALLOWED_FORMATS, formats).apply()
        _allowedFormats.value = formats
        Log.d("AppSettings", "Allowed formats updated: $formats")
    }
    
    fun setMinimumBitrate(bitrate: Int) {
        prefs.edit().putInt(KEY_MINIMUM_BITRATE, bitrate).apply()
        _minimumBitrate.value = bitrate
        Log.d("AppSettings", "Minimum bitrate set to: ${bitrate}kbps")
    }
    
    fun setMinimumDuration(duration: Long) {
        prefs.edit().putLong(KEY_MINIMUM_DURATION, duration).apply()
        _minimumDuration.value = duration
        Log.d("AppSettings", "Minimum duration set to: ${duration}ms")
    }
    
    /**
     * Schedule weekly automatic backups using WorkManager
     */
    private fun scheduleAutoBackup() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(
                7, TimeUnit.DAYS, // Repeat every 7 days
                1, TimeUnit.HOURS  // Flex interval of 1 hour
            ).build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                BackupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule if already running
                workRequest
            )
            
            Log.d("AppSettings", "Auto-backup scheduled: weekly backups enabled")
            Log.d("AppSettings", "Next backup will occur within the next 7 days")
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to schedule auto-backup", e)
        }
    }
    
    /**
     * Trigger an immediate one-time backup (useful for testing)
     */
    fun triggerImmediateBackup() {
        try {
            val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d("AppSettings", "Immediate backup triggered")
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to trigger immediate backup", e)
        }
    }
    
    /**
     * Cancel automatic backups
     */
    private fun cancelAutoBackup() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(BackupWorker.WORK_NAME)
            Log.d("AppSettings", "Auto-backup cancelled")
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to cancel auto-backup", e)
        }
    }
    
    /**
     * Schedule periodic update notification checks using WorkManager
     * This implements a webhook-style system using smart polling
     */
    private fun scheduleUpdateNotificationWorker() {
        try {
            // Get the check interval from settings (default 6 hours)
            val intervalHours = _updateCheckIntervalHours.value.toLong()
            
            val workRequest = PeriodicWorkRequestBuilder<chromahub.rhythm.app.worker.UpdateNotificationWorker>(
                intervalHours, TimeUnit.HOURS,
                30, TimeUnit.MINUTES // Flex interval
            ).build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                chromahub.rhythm.app.worker.UpdateNotificationWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE, // Update if interval changes
                workRequest
            )
            
            Log.d("AppSettings", "Update notification worker scheduled: checks every $intervalHours hours")
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to schedule update notification worker", e)
        }
    }
    
    /**
     * Cancel update notification checks
     */
    private fun cancelUpdateNotificationWorker() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(
                chromahub.rhythm.app.worker.UpdateNotificationWorker.WORK_NAME
            )
            Log.d("AppSettings", "Update notification worker cancelled")
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to cancel update notification worker", e)
        }
    }
    
    /**
     * Trigger an immediate update check (useful for testing)
     */
    fun triggerImmediateUpdateCheck() {
        try {
            val workRequest = OneTimeWorkRequestBuilder<chromahub.rhythm.app.worker.UpdateNotificationWorker>()
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d("AppSettings", "Immediate update check triggered")
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to trigger immediate update check", e)
        }
    }
    
    fun setBackupLocation(location: String?) {
        if (location == null) {
            prefs.edit().remove(KEY_BACKUP_LOCATION).apply()
        } else {
            prefs.edit().putString(KEY_BACKUP_LOCATION, location).apply()
        }
        _backupLocation.value = location
    }
    
    /**
     * Creates a complete backup of all app data as JSON
     */
    fun createBackup(): String {
        val backupData = mutableMapOf<String, Any?>()
        val preferencesTypes = mutableMapOf<String, String>()
        
        // Get all preferences
        val allPrefs = prefs.all
        
        // Filter out sensitive or temporary data if needed
        val filteredPrefs = allPrefs.filterKeys { key ->
            // Include all keys for now, but you could exclude sensitive data here
            true
        }
        
        // Store type information for each preference
        filteredPrefs.forEach { (key, value) ->
            preferencesTypes[key] = when (value) {
                is Boolean -> "Boolean"
                is Float -> "Float"
                is Int -> "Int"
                is Long -> "Long"
                is String -> "String"
                is Set<*> -> "StringSet"
                else -> "Unknown"
            }
        }
        
        backupData["preferences"] = filteredPrefs
        backupData["preferences_types"] = preferencesTypes
        backupData["timestamp"] = System.currentTimeMillis()
        backupData["app_version"] = "1.0.0" // You might want to get this dynamically
        backupData["backup_version"] = 2 // Increment version to handle playlist data properly
        
        // Explicitly include playlist and favorite songs data even if already in preferences
        // This ensures they are properly backed up and restored
        try {
            val playlistsJson = prefs.getString(KEY_PLAYLISTS, null)
            val favoriteSongsJson = prefs.getString(KEY_FAVORITE_SONGS, null)
            
            if (playlistsJson != null) {
                backupData["playlists_data"] = playlistsJson
                Log.d("AppSettings", "Including playlists data in backup: ${playlistsJson.length} characters")
            }
            
            if (favoriteSongsJson != null) {
                backupData["favorite_songs_data"] = favoriteSongsJson
                Log.d("AppSettings", "Including favorite songs data in backup: ${favoriteSongsJson.length} characters")
            }
            
            // Also include blacklisted songs and folders
            val blacklistedSongsJson = prefs.getString(KEY_BLACKLISTED_SONGS, null)
            val blacklistedFoldersJson = prefs.getString(KEY_BLACKLISTED_FOLDERS, null)
            
            if (blacklistedSongsJson != null) {
                backupData["blacklisted_songs_data"] = blacklistedSongsJson
            }
            
            if (blacklistedFoldersJson != null) {
                backupData["blacklisted_folders_data"] = blacklistedFoldersJson
            }
            
            // Include whitelisted songs and folders
            val whitelistedSongsJson = prefs.getString(KEY_WHITELISTED_SONGS, null)
            val whitelistedFoldersJson = prefs.getString(KEY_WHITELISTED_FOLDERS, null)
            
            if (whitelistedSongsJson != null) {
                backupData["whitelisted_songs_data"] = whitelistedSongsJson
            }
            
            if (whitelistedFoldersJson != null) {
                backupData["whitelisted_folders_data"] = whitelistedFoldersJson
            }
            
            // Include pinned folders
            val pinnedFoldersJson = prefs.getString(KEY_PINNED_FOLDERS, null)
            if (pinnedFoldersJson != null) {
                backupData["pinned_folders_data"] = pinnedFoldersJson
            }
            
        } catch (e: Exception) {
            Log.e("AppSettings", "Error including playlist data in backup", e)
        }
        
        return Gson().toJson(backupData)
    }
    
    /**
     * Restores app data from a backup JSON string
     */
    fun restoreFromBackup(backupJson: String): Boolean {
        return try {
            Log.d("AppSettings", "Attempting to restore from backup...")
            val backupData = Gson().fromJson(backupJson, Map::class.java) as Map<String, Any?>
            val preferences = backupData["preferences"] as? Map<String, Any?> ?: return false
            val preferencesTypes = backupData["preferences_types"] as? Map<String, String> ?: emptyMap()
            val backupVersion = (backupData["backup_version"] as? Double)?.toInt() ?: 1
            
            Log.d("AppSettings", "Backup version: $backupVersion")
            
            // Clear existing preferences (optional - you might want to merge instead)
            val editor = prefs.edit()
            
            // Restore all preferences with proper type handling
            preferences.forEach { (key, value) ->
                val originalType = preferencesTypes[key]
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> {
                        // Check if this should be a Long or Int based on original type
                        when (originalType) {
                            "Long" -> editor.putLong(key, value.toLong())
                            "Int" -> editor.putInt(key, value.toInt())
                            else -> editor.putFloat(key, value)
                        }
                    }
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is Double -> {
                        // JSON deserializes numbers as Double, convert based on original type
                        when (originalType) {
                            "Float" -> editor.putFloat(key, value.toFloat())
                            "Long" -> editor.putLong(key, value.toLong())
                            "Int" -> editor.putInt(key, value.toInt())
                            else -> editor.putFloat(key, value.toFloat()) // Default fallback
                        }
                    }
                    // Handle any other types as needed
                }
            }
            
            // Handle backup version 2 and above - explicit playlist data restoration
            if (backupVersion >= 2) {
                Log.d("AppSettings", "Restoring playlist data from backup version $backupVersion")
                
                // Restore playlists data explicitly
                val playlistsData = backupData["playlists_data"] as? String
                if (playlistsData != null) {
                    editor.putString(KEY_PLAYLISTS, playlistsData)
                    Log.d("AppSettings", "Restored playlists data: ${playlistsData.length} characters")
                }
                
                // Restore favorite songs data explicitly
                val favoriteSongsData = backupData["favorite_songs_data"] as? String
                if (favoriteSongsData != null) {
                    editor.putString(KEY_FAVORITE_SONGS, favoriteSongsData)
                    Log.d("AppSettings", "Restored favorite songs data: ${favoriteSongsData.length} characters")
                }
                
                // Restore blacklisted songs and folders
                val blacklistedSongsData = backupData["blacklisted_songs_data"] as? String
                if (blacklistedSongsData != null) {
                    editor.putString(KEY_BLACKLISTED_SONGS, blacklistedSongsData)
                    Log.d("AppSettings", "Restored blacklisted songs data")
                }
                
                val blacklistedFoldersData = backupData["blacklisted_folders_data"] as? String
                if (blacklistedFoldersData != null) {
                    editor.putString(KEY_BLACKLISTED_FOLDERS, blacklistedFoldersData)
                    Log.d("AppSettings", "Restored blacklisted folders data")
                }
                
                // Restore whitelisted songs and folders
                val whitelistedSongsData = backupData["whitelisted_songs_data"] as? String
                if (whitelistedSongsData != null) {
                    editor.putString(KEY_WHITELISTED_SONGS, whitelistedSongsData)
                    Log.d("AppSettings", "Restored whitelisted songs data")
                }
                
                val whitelistedFoldersData = backupData["whitelisted_folders_data"] as? String
                if (whitelistedFoldersData != null) {
                    editor.putString(KEY_WHITELISTED_FOLDERS, whitelistedFoldersData)
                    Log.d("AppSettings", "Restored whitelisted folders data")
                }
                
                // Restore pinned folders
                val pinnedFoldersData = backupData["pinned_folders_data"] as? String
                if (pinnedFoldersData != null) {
                    editor.putString(KEY_PINNED_FOLDERS, pinnedFoldersData)
                    Log.d("AppSettings", "Restored pinned folders data")
                }
            } else {
                Log.d("AppSettings", "Backup version $backupVersion - using preferences-based restoration")
            }
            
            editor.apply()
            
            // Refresh all StateFlows to reflect the restored data
            refreshAllStateFlows()
            
            Log.d("AppSettings", "Backup restoration completed successfully")
            true
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to restore backup", e)
            false
        }
    }
    
    /**
     * Safely get a Long value from SharedPreferences, handling JSON restore type casting issues
     */
    private fun safeLong(key: String, defaultValue: Long): Long {
        return try {
            prefs.getLong(key, defaultValue)
        } catch (e: ClassCastException) {
            // Handle case where value was stored as Float/Double from JSON restore
            try {
                prefs.getFloat(key, defaultValue.toFloat()).toLong()
            } catch (e2: Exception) {
                // If all else fails, use default and fix the stored value
                prefs.edit().putLong(key, defaultValue).apply()
                defaultValue
            }
        }
    }
    
    /**
     * Helper function to safely set values with validation
     */
    private fun safeSetValue(key: String, value: Any, validator: ((Any) -> Boolean)? = null): Boolean {
        return try {
            if (validator != null && !validator(value)) {
                Log.w("AppSettings", "Invalid value for key $key: $value")
                return false
            }
            
            val editor = prefs.edit()
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                else -> {
                    Log.w("AppSettings", "Unsupported value type for key $key")
                    return false
                }
            }
            
            editor.apply()
            true
        } catch (e: Exception) {
            Log.e("AppSettings", "Error setting preference $key", e)
            false
        }
    }
    
    /**
     * Validates cache size value
     */
    private fun isValidCacheSize(size: Long): Boolean {
        val minSize = 50L * 1024 * 1024 // 50MB minimum
        val maxSize = 10L * 1024 * 1024 * 1024 // 10GB maximum
        return size in minSize..maxSize
    }
    
    /**
     * Validates crossfade duration
     */
    private fun isValidCrossfadeDuration(duration: Float): Boolean {
        return duration in 0.1f..10.0f
    }
    
    /**
     * Validates update check interval
     */
    private fun isValidUpdateInterval(hours: Int): Boolean {
        return hours in 1..168 // 1 hour to 1 week
    }
    
    /**
     * Clears app cache if the clear cache on exit setting is enabled
     * This should be called when the app is being destroyed
     * 
     * @param context Application context
     * @param musicRepository Optional MusicRepository instance to clear in-memory caches
     */
    suspend fun performCacheCleanupOnExit(
        context: Context, 
        musicRepository: Any? = null
    ) {
        if (_clearCacheOnExit.value) {
            try {
                Log.d("AppSettings", "Performing cache cleanup on app exit...")
                
                // Clear file system caches
                chromahub.rhythm.app.util.CacheManager.clearAllCache(context)
                
                // Clear in-memory caches from MusicRepository
                if (musicRepository != null && musicRepository::class.simpleName == "MusicRepository") {
                    try {
                        musicRepository::class.java.getMethod("clearInMemoryCaches").invoke(musicRepository)
                    } catch (e: Exception) {
                        Log.w("AppSettings", "Could not call clearInMemoryCaches on repository", e)
                    }
                }
                
                Log.d("AppSettings", "Cache cleanup completed successfully")
            } catch (e: Exception) {
                Log.e("AppSettings", "Error during cache cleanup on exit", e)
            }
        }
    }
    
    /**
     * Refreshes all StateFlows to reflect current SharedPreferences values
     */
    private fun refreshAllStateFlows() {
        // Playback Settings
        _highQualityAudio.value = prefs.getBoolean(KEY_HIGH_QUALITY_AUDIO, true)
        _gaplessPlayback.value = prefs.getBoolean(KEY_GAPLESS_PLAYBACK, true)
        _crossfade.value = prefs.getBoolean(KEY_CROSSFADE, false)
        _crossfadeDuration.value = prefs.getFloat(KEY_CROSSFADE_DURATION, 2f)
        _audioNormalization.value = prefs.getBoolean(KEY_AUDIO_NORMALIZATION, true)
        _replayGain.value = prefs.getBoolean(KEY_REPLAY_GAIN, false)
        
        // Theme Settings
        _useSystemTheme.value = prefs.getBoolean(KEY_USE_SYSTEM_THEME, true)
        _darkMode.value = prefs.getBoolean(KEY_DARK_MODE, true)
        _useDynamicColors.value = prefs.getBoolean(KEY_USE_DYNAMIC_COLORS, false)
        _customColorScheme.value = prefs.getString(KEY_CUSTOM_COLOR_SCHEME, "Default") ?: "Default"
        _customFont.value = prefs.getString(KEY_CUSTOM_FONT, "Geom") ?: "Geom"
        
        // Library Settings
        _albumViewType.value = AlbumViewType.valueOf(prefs.getString(KEY_ALBUM_VIEW_TYPE, AlbumViewType.GRID.name) ?: AlbumViewType.GRID.name)
        _artistViewType.value = ArtistViewType.valueOf(prefs.getString(KEY_ARTIST_VIEW_TYPE, ArtistViewType.GRID.name) ?: ArtistViewType.GRID.name)
        _albumSortOrder.value = prefs.getString(KEY_ALBUM_SORT_ORDER, "TRACK_NUMBER") ?: "TRACK_NUMBER"
        _artistCollaborationMode.value = prefs.getBoolean(KEY_ARTIST_COLLABORATION_MODE, false)
        _songsSortOrder.value = prefs.getString(KEY_SONGS_SORT_ORDER, "TITLE_ASC") ?: "TITLE_ASC"
        
        // Audio Device Settings
        _lastAudioDevice.value = prefs.getString(KEY_LAST_AUDIO_DEVICE, null)
        _autoConnectDevice.value = prefs.getBoolean(KEY_AUTO_CONNECT_DEVICE, true)
        _useSystemVolume.value = prefs.getBoolean(KEY_USE_SYSTEM_VOLUME, false)
        
        // Cache Settings
        _maxCacheSize.value = safeLong(KEY_MAX_CACHE_SIZE, 500L * 1024L * 1024L)
        _clearCacheOnExit.value = prefs.getBoolean(KEY_CLEAR_CACHE_ON_EXIT, false)
        
        // Other settings
        _showLyrics.value = prefs.getBoolean(KEY_SHOW_LYRICS, true)
        _onlineOnlyLyrics.value = prefs.getBoolean(KEY_ONLINE_ONLY_LYRICS, true)
        _searchHistory.value = prefs.getString(KEY_SEARCH_HISTORY, null)
        _playlists.value = prefs.getString(KEY_PLAYLISTS, null)
        _favoriteSongs.value = prefs.getString(KEY_FAVORITE_SONGS, null)
        
        // User Statistics
        _listeningTime.value = safeLong(KEY_LISTENING_TIME, 0L)
        _songsPlayed.value = prefs.getInt(KEY_SONGS_PLAYED, 0)
        _uniqueArtists.value = prefs.getInt(KEY_UNIQUE_ARTISTS, 0)
        
        // Enhanced User Preferences
        _favoriteGenres.value = try {
            val json = prefs.getString(KEY_FAVORITE_GENRES, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type) else emptyMap()
        } catch (e: Exception) { emptyMap() }
        
        _dailyListeningStats.value = try {
            val json = prefs.getString(KEY_DAILY_LISTENING_STATS, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<Map<String, Long>>() {}.type) else emptyMap()
        } catch (e: Exception) { emptyMap() }
        
        _weeklyTopArtists.value = try {
            val json = prefs.getString(KEY_WEEKLY_TOP_ARTISTS, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type) else emptyMap()
        } catch (e: Exception) { emptyMap() }
        
        _moodPreferences.value = try {
            val json = prefs.getString(KEY_MOOD_PREFERENCES, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<Map<String, List<String>>>() {}.type) else emptyMap()
        } catch (e: Exception) { emptyMap() }
        
        _songPlayCounts.value = try {
            val json = prefs.getString(KEY_SONG_PLAY_COUNTS, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<Map<String, Int>>() {}.type) else emptyMap()
        } catch (e: Exception) { emptyMap() }
        
        // Recently Played
        _recentlyPlayed.value = try {
            val json = prefs.getString(KEY_RECENTLY_PLAYED, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<List<String>>() {}.type) else emptyList()
        } catch (e: Exception) { emptyList() }
        
        _lastPlayedTimestamp.value = safeLong(KEY_LAST_PLAYED_TIMESTAMP, 0L)
        
        // API Enable/Disable States
        _deezerApiEnabled.value = prefs.getBoolean(KEY_DEEZER_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _canvasApiEnabled.value = prefs.getBoolean(KEY_CANVAS_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _lrclibApiEnabled.value = prefs.getBoolean(KEY_LRCLIB_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _ytMusicApiEnabled.value = prefs.getBoolean(KEY_YTMUSIC_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _spotifyApiEnabled.value = prefs.getBoolean(KEY_SPOTIFY_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _spotifyClientId.value = prefs.getString(KEY_SPOTIFY_CLIENT_ID, "") ?: ""
        _spotifyClientSecret.value = prefs.getString(KEY_SPOTIFY_CLIENT_SECRET, "") ?: ""
        
        // Scrobbling Settings
        _scrobblingEnabled.value = prefs.getBoolean(KEY_SCROBBLING_ENABLED, false)
        
        // Discord Rich Presence Settings
        _discordRichPresenceEnabled.value = prefs.getBoolean(KEY_DISCORD_RICH_PRESENCE_ENABLED, false)
        
        // General Broadcast Status Settings
        _broadcastStatusEnabled.value = prefs.getBoolean(KEY_BROADCAST_STATUS_ENABLED, false)
        
        // App Updates
        _autoCheckForUpdates.value = prefs.getBoolean(KEY_AUTO_CHECK_FOR_UPDATES, BuildConfig.FLAVOR != "fdroid")
        _updateChannel.value = prefs.getString(KEY_UPDATE_CHANNEL, "stable") ?: "stable"
        _updatesEnabled.value = prefs.getBoolean(KEY_UPDATES_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _updateNotificationsEnabled.value = prefs.getBoolean(KEY_UPDATE_NOTIFICATIONS_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _useSmartUpdatePolling.value = prefs.getBoolean(KEY_USE_SMART_UPDATE_POLLING, BuildConfig.FLAVOR != "fdroid")
        _mediaScanMode.value = prefs.getString(KEY_MEDIA_SCAN_MODE, "blacklist") ?: "blacklist"
        _updateCheckIntervalHours.value = prefs.getInt(KEY_UPDATE_CHECK_INTERVAL_HOURS, 24)
        
        // Re-schedule update notification worker if settings changed
        if (_updatesEnabled.value && _updateNotificationsEnabled.value && _useSmartUpdatePolling.value) {
            scheduleUpdateNotificationWorker()
        }
        
        // Beta Program
        _hasShownBetaPopup.value = prefs.getBoolean(KEY_HAS_SHOWN_BETA_POPUP, false)
        
        // Crash Reporting
        _lastCrashLog.value = prefs.getString(KEY_LAST_CRASH_LOG, null)
        _crashLogHistory.value = try {
            val json = prefs.getString(KEY_CRASH_LOG_HISTORY, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<List<CrashLogEntry>>() {}.type) else emptyList()
        } catch (e: Exception) { emptyList() }
        
        // Other settings
        _hapticFeedbackEnabled.value = prefs.getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true)
        _onboardingCompleted.value = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        _initialMediaScanCompleted.value = prefs.getBoolean(KEY_INITIAL_MEDIA_SCAN_COMPLETED, false)
        _genreDetectionCompleted.value = prefs.getBoolean(KEY_GENRE_DETECTION_COMPLETED, false)
        
        // Blacklisted items
        _blacklistedSongs.value = try {
            val json = prefs.getString(KEY_BLACKLISTED_SONGS, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<List<String>>() {}.type) else emptyList()
        } catch (e: Exception) { emptyList() }
        
        _blacklistedFolders.value = try {
            val json = prefs.getString(KEY_BLACKLISTED_FOLDERS, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<List<String>>() {}.type) else emptyList()
        } catch (e: Exception) { emptyList() }
        
        // Backup settings
        _lastBackupTimestamp.value = safeLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)
        _autoBackupEnabled.value = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
        _backupLocation.value = prefs.getString(KEY_BACKUP_LOCATION, null)
        
        // Ensure auto-backup is scheduled if enabled
        if (_autoBackupEnabled.value) {
            scheduleAutoBackup()
        }
        
        // Sleep Timer settings
        _sleepTimerActive.value = prefs.getBoolean(KEY_SLEEP_TIMER_ACTIVE, false)
        _sleepTimerRemainingSeconds.value = prefs.getLong(KEY_SLEEP_TIMER_REMAINING_SECONDS, 0L)
        _sleepTimerAction.value = prefs.getString(KEY_SLEEP_TIMER_ACTION, "FADE_OUT") ?: "FADE_OUT"
        
        // Media Scan Tracking
        _lastScanTimestamp.value = safeLong(KEY_LAST_SCAN_TIMESTAMP, 0L)
        _lastScanDuration.value = safeLong(KEY_LAST_SCAN_DURATION, 0L)
        
        // Media Scan Filtering
        _allowedFormats.value = prefs.getStringSet(KEY_ALLOWED_FORMATS, setOf("mp3", "flac", "ogg", "m4a", "opus", "wav", "aac", "wma"))
            ?.toSet() ?: setOf("mp3", "flac", "ogg", "m4a", "opus", "wav", "aac", "wma")
        _minimumBitrate.value = prefs.getInt(KEY_MINIMUM_BITRATE, 0)
        _minimumDuration.value = safeLong(KEY_MINIMUM_DURATION, 0L)

        // Pinned Folders
        _pinnedFolders.value = try {
            val json = prefs.getString(KEY_PINNED_FOLDERS, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<List<String>>() {}.type) else emptyList()
        } catch (e: Exception) { emptyList() }
        
        // Widget Settings
        _widgetShowAlbumArt.value = prefs.getBoolean(KEY_WIDGET_SHOW_ALBUM_ART, true)
        _widgetShowArtist.value = prefs.getBoolean(KEY_WIDGET_SHOW_ARTIST, true)
        _widgetShowAlbum.value = prefs.getBoolean(KEY_WIDGET_SHOW_ALBUM, true)
        _widgetCornerRadius.value = prefs.getInt(KEY_WIDGET_CORNER_RADIUS, 24)
        _widgetAutoUpdate.value = prefs.getBoolean(KEY_WIDGET_AUTO_UPDATE, true)
        
        // Player Screen Customization Settings
        _playerShowGradientOverlay.value = prefs.getBoolean(KEY_PLAYER_SHOW_GRADIENT_OVERLAY, true)
        _playerShowSeekButtons.value = prefs.getBoolean(KEY_PLAYER_SHOW_SEEK_BUTTONS, true)
        _playerTextAlignment.value = prefs.getString(KEY_PLAYER_TEXT_ALIGNMENT, "CENTER") ?: "CENTER"
        _playerShowSongInfoOnArtwork.value = prefs.getBoolean(KEY_PLAYER_SHOW_SONG_INFO_ON_ARTWORK, true)
        _playerArtworkCornerRadius.value = prefs.getInt(KEY_PLAYER_ARTWORK_CORNER_RADIUS, 28)
        _playerShowAudioQualityBadges.value = prefs.getBoolean(KEY_PLAYER_SHOW_AUDIO_QUALITY_BADGES, true)
    }
    
    // ==================== Widget Settings ====================
    
    private val _widgetShowAlbumArt = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_SHOW_ALBUM_ART, true))
    val widgetShowAlbumArt: StateFlow<Boolean> = _widgetShowAlbumArt.asStateFlow()
    fun setWidgetShowAlbumArt(value: Boolean) {
        _widgetShowAlbumArt.value = value
        prefs.edit().putBoolean(KEY_WIDGET_SHOW_ALBUM_ART, value).apply()
    }
    
    private val _widgetShowArtist = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_SHOW_ARTIST, true))
    val widgetShowArtist: StateFlow<Boolean> = _widgetShowArtist.asStateFlow()
    fun setWidgetShowArtist(value: Boolean) {
        _widgetShowArtist.value = value
        prefs.edit().putBoolean(KEY_WIDGET_SHOW_ARTIST, value).apply()
    }
    
    private val _widgetShowAlbum = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_SHOW_ALBUM, true))
    val widgetShowAlbum: StateFlow<Boolean> = _widgetShowAlbum.asStateFlow()
    fun setWidgetShowAlbum(value: Boolean) {
        _widgetShowAlbum.value = value
        prefs.edit().putBoolean(KEY_WIDGET_SHOW_ALBUM, value).apply()
    }
    
    private val _widgetCornerRadius = MutableStateFlow(prefs.getInt(KEY_WIDGET_CORNER_RADIUS, 24))
    val widgetCornerRadius: StateFlow<Int> = _widgetCornerRadius.asStateFlow()
    fun setWidgetCornerRadius(value: Int) {
        if (value in 0..40) {
            _widgetCornerRadius.value = value
            prefs.edit().putInt(KEY_WIDGET_CORNER_RADIUS, value).apply()
        }
    }
    
    private val _widgetAutoUpdate = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_AUTO_UPDATE, true))
    val widgetAutoUpdate: StateFlow<Boolean> = _widgetAutoUpdate.asStateFlow()
    fun setWidgetAutoUpdate(value: Boolean) {
        _widgetAutoUpdate.value = value
        prefs.edit().putBoolean(KEY_WIDGET_AUTO_UPDATE, value).apply()
    }
    
    // ==================== Global Header Settings ====================
    
    private val _headerCollapseBehavior = MutableStateFlow(prefs.getInt(KEY_HEADER_COLLAPSE_BEHAVIOR, 0))
    val headerCollapseBehavior: StateFlow<Int> = _headerCollapseBehavior.asStateFlow()
    fun setHeaderCollapseBehavior(value: Int) {
        _headerCollapseBehavior.value = value
        prefs.edit().putInt(KEY_HEADER_COLLAPSE_BEHAVIOR, value).apply()
    }
    
    // ==================== Home Screen Customization Settings ====================
    
    private val _homeHeaderDisplayMode = MutableStateFlow(prefs.getInt(KEY_HOME_HEADER_DISPLAY_MODE, 1))
    val homeHeaderDisplayMode: StateFlow<Int> = _homeHeaderDisplayMode.asStateFlow()
    fun setHomeHeaderDisplayMode(value: Int) {
        _homeHeaderDisplayMode.value = value
        prefs.edit().putInt(KEY_HOME_HEADER_DISPLAY_MODE, value).apply()
    }
    
    private val _homeShowAppIcon = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_APP_ICON, false))
    val homeShowAppIcon: StateFlow<Boolean> = _homeShowAppIcon.asStateFlow()
    fun setHomeShowAppIcon(value: Boolean) {
        _homeShowAppIcon.value = value
        prefs.edit().putBoolean(KEY_HOME_SHOW_APP_ICON, value).apply()
    }
    
    private val _homeAppIconVisibility = MutableStateFlow(prefs.getInt(KEY_HOME_APP_ICON_VISIBILITY, 0))
    val homeAppIconVisibility: StateFlow<Int> = _homeAppIconVisibility.asStateFlow()
    fun setHomeAppIconVisibility(value: Int) {
        _homeAppIconVisibility.value = value
        prefs.edit().putInt(KEY_HOME_APP_ICON_VISIBILITY, value).apply()
    }
    
    private val _homeShowGreeting = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_GREETING, true))
    val homeShowGreeting: StateFlow<Boolean> = _homeShowGreeting.asStateFlow()
    fun setHomeShowGreeting(value: Boolean) {
        _homeShowGreeting.value = value
        prefs.edit().putBoolean(KEY_HOME_SHOW_GREETING, value).apply()
    }
    
    private val _homeShowRecentlyPlayed = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_RECENTLY_PLAYED, true))
    val homeShowRecentlyPlayed: StateFlow<Boolean> = _homeShowRecentlyPlayed.asStateFlow()
    fun setHomeShowRecentlyPlayed(value: Boolean) {
        _homeShowRecentlyPlayed.value = value
        prefs.edit().putBoolean(KEY_HOME_SHOW_RECENTLY_PLAYED, value).apply()
    }
    
    private val _homeShowDiscoverCarousel = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_DISCOVER_CAROUSEL, true))
    val homeShowDiscoverCarousel: StateFlow<Boolean> = _homeShowDiscoverCarousel.asStateFlow()
    fun setHomeShowDiscoverCarousel(value: Boolean) {
        _homeShowDiscoverCarousel.value = value
        prefs.edit().putBoolean(KEY_HOME_SHOW_DISCOVER_CAROUSEL, value).apply()
    }
    
    private val _homeShowArtists = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_ARTISTS, true))
    val homeShowArtists: StateFlow<Boolean> = _homeShowArtists.asStateFlow()
    fun setHomeShowArtists(value: Boolean) {
        _homeShowArtists.value = value
        prefs.edit().putBoolean(KEY_HOME_SHOW_ARTISTS, value).apply()
    }
    
    private val _homeShowNewReleases = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_NEW_RELEASES, true))
    val homeShowNewReleases: StateFlow<Boolean> = _homeShowNewReleases.asStateFlow()
    fun setHomeShowNewReleases(value: Boolean) {
        _homeShowNewReleases.value = value
        prefs.edit().putBoolean(KEY_HOME_SHOW_NEW_RELEASES, value).apply()
    }
    
    private val _homeShowRecentlyAdded = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_RECENTLY_ADDED, true))
    val homeShowRecentlyAdded: StateFlow<Boolean> = _homeShowRecentlyAdded.asStateFlow()
    fun setHomeShowRecentlyAdded(value: Boolean) {
        _homeShowRecentlyAdded.value = value
        prefs.edit().putBoolean(KEY_HOME_SHOW_RECENTLY_ADDED, value).apply()
    }
    
    private val _homeShowRecommended = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_RECOMMENDED, true))
    val homeShowRecommended: StateFlow<Boolean> = _homeShowRecommended.asStateFlow()
    fun setHomeShowRecommended(value: Boolean) {
        _homeShowRecommended.value = value
        prefs.edit().putBoolean(KEY_HOME_SHOW_RECOMMENDED, value).apply()
    }
    
    private val _homeShowListeningStats = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_LISTENING_STATS, true))
    val homeShowListeningStats: StateFlow<Boolean> = _homeShowListeningStats.asStateFlow()
    fun setHomeShowListeningStats(value: Boolean) {
        _homeShowListeningStats.value = value
        prefs.edit().putBoolean(KEY_HOME_SHOW_LISTENING_STATS, value).apply()
    }
    
    private val _homeShowMoodSections = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_MOOD_SECTIONS, true))
    val homeShowMoodSections: StateFlow<Boolean> = _homeShowMoodSections.asStateFlow()
    fun setHomeShowMoodSections(value: Boolean) {
        _homeShowMoodSections.value = value
        prefs.edit().putBoolean(KEY_HOME_SHOW_MOOD_SECTIONS, value).apply()
    }
    
    // ==================== Player Screen Customization Settings ====================
    
    private val _playerShowGradientOverlay = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_SHOW_GRADIENT_OVERLAY, true))
    val playerShowGradientOverlay: StateFlow<Boolean> = _playerShowGradientOverlay.asStateFlow()
    fun setPlayerShowGradientOverlay(value: Boolean) {
        _playerShowGradientOverlay.value = value
        prefs.edit().putBoolean(KEY_PLAYER_SHOW_GRADIENT_OVERLAY, value).apply()
    }
    
    private val _playerShowSeekButtons = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_SHOW_SEEK_BUTTONS, true))
    val playerShowSeekButtons: StateFlow<Boolean> = _playerShowSeekButtons.asStateFlow()
    fun setPlayerShowSeekButtons(value: Boolean) {
        _playerShowSeekButtons.value = value
        prefs.edit().putBoolean(KEY_PLAYER_SHOW_SEEK_BUTTONS, value).apply()
    }
    
    private val _playerTextAlignment = MutableStateFlow(prefs.getString(KEY_PLAYER_TEXT_ALIGNMENT, "CENTER") ?: "CENTER")
    val playerTextAlignment: StateFlow<String> = _playerTextAlignment.asStateFlow()
    fun setPlayerTextAlignment(value: String) {
        _playerTextAlignment.value = value
        prefs.edit().putString(KEY_PLAYER_TEXT_ALIGNMENT, value).apply()
    }
    
    private val _playerShowSongInfoOnArtwork = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_SHOW_SONG_INFO_ON_ARTWORK, true))
    val playerShowSongInfoOnArtwork: StateFlow<Boolean> = _playerShowSongInfoOnArtwork.asStateFlow()
    fun setPlayerShowSongInfoOnArtwork(value: Boolean) {
        _playerShowSongInfoOnArtwork.value = value
        prefs.edit().putBoolean(KEY_PLAYER_SHOW_SONG_INFO_ON_ARTWORK, value).apply()
    }
    
    private val _playerArtworkCornerRadius = MutableStateFlow(prefs.getInt(KEY_PLAYER_ARTWORK_CORNER_RADIUS, 28))
    val playerArtworkCornerRadius: StateFlow<Int> = _playerArtworkCornerRadius.asStateFlow()
    fun setPlayerArtworkCornerRadius(value: Int) {
        _playerArtworkCornerRadius.value = value.coerceIn(0, 40)
        prefs.edit().putInt(KEY_PLAYER_ARTWORK_CORNER_RADIUS, _playerArtworkCornerRadius.value).apply()
    }
    
    private val _playerShowAudioQualityBadges = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_SHOW_AUDIO_QUALITY_BADGES, true))
    val playerShowAudioQualityBadges: StateFlow<Boolean> = _playerShowAudioQualityBadges.asStateFlow()
    fun setPlayerShowAudioQualityBadges(value: Boolean) {
        _playerShowAudioQualityBadges.value = value
        prefs.edit().putBoolean(KEY_PLAYER_SHOW_AUDIO_QUALITY_BADGES, value).apply()
    }
    
    // Player Progress Bar Style
    private val _playerProgressStyle = MutableStateFlow(prefs.getString(KEY_PLAYER_PROGRESS_STYLE, "WAVY") ?: "WAVY")
    val playerProgressStyle: StateFlow<String> = _playerProgressStyle.asStateFlow()
    fun setPlayerProgressStyle(value: String) {
        _playerProgressStyle.value = value
        prefs.edit().putString(KEY_PLAYER_PROGRESS_STYLE, value).apply()
    }
    
    // Player Progress Thumb Style
    private val _playerProgressThumbStyle = MutableStateFlow(prefs.getString(KEY_PLAYER_PROGRESS_THUMB_STYLE, "CIRCLE") ?: "CIRCLE")
    val playerProgressThumbStyle: StateFlow<String> = _playerProgressThumbStyle.asStateFlow()
    fun setPlayerProgressThumbStyle(value: String) {
        _playerProgressThumbStyle.value = value
        prefs.edit().putString(KEY_PLAYER_PROGRESS_THUMB_STYLE, value).apply()
    }
    
    // ==================== MiniPlayer Customization Settings ====================
    
    private val _miniPlayerProgressStyle = MutableStateFlow(prefs.getString(KEY_MINIPLAYER_PROGRESS_STYLE, "NORMAL") ?: "NORMAL")
    val miniPlayerProgressStyle: StateFlow<String> = _miniPlayerProgressStyle.asStateFlow()
    fun setMiniPlayerProgressStyle(value: String) {
        _miniPlayerProgressStyle.value = value
        prefs.edit().putString(KEY_MINIPLAYER_PROGRESS_STYLE, value).apply()
    }
    
    private val _miniPlayerShowProgress = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SHOW_PROGRESS, true))
    val miniPlayerShowProgress: StateFlow<Boolean> = _miniPlayerShowProgress.asStateFlow()
    fun setMiniPlayerShowProgress(value: Boolean) {
        _miniPlayerShowProgress.value = value
        prefs.edit().putBoolean(KEY_MINIPLAYER_SHOW_PROGRESS, value).apply()
    }
    
    private val _miniPlayerShowArtwork = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SHOW_ARTWORK, true))
    val miniPlayerShowArtwork: StateFlow<Boolean> = _miniPlayerShowArtwork.asStateFlow()
    fun setMiniPlayerShowArtwork(value: Boolean) {
        _miniPlayerShowArtwork.value = value
        prefs.edit().putBoolean(KEY_MINIPLAYER_SHOW_ARTWORK, value).apply()
    }
    
    private val _miniPlayerArtworkSize = MutableStateFlow(prefs.getInt(KEY_MINIPLAYER_ARTWORK_SIZE, 56))
    val miniPlayerArtworkSize: StateFlow<Int> = _miniPlayerArtworkSize.asStateFlow()
    fun setMiniPlayerArtworkSize(value: Int) {
        _miniPlayerArtworkSize.value = value
        prefs.edit().putInt(KEY_MINIPLAYER_ARTWORK_SIZE, value).apply()
    }
    
    private val _miniPlayerCornerRadius = MutableStateFlow(prefs.getInt(KEY_MINIPLAYER_CORNER_RADIUS, 14))
    val miniPlayerCornerRadius: StateFlow<Int> = _miniPlayerCornerRadius.asStateFlow()
    fun setMiniPlayerCornerRadius(value: Int) {
        _miniPlayerCornerRadius.value = value
        prefs.edit().putInt(KEY_MINIPLAYER_CORNER_RADIUS, value).apply()
    }
    
    private val _miniPlayerShowTime = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SHOW_TIME, true))
    val miniPlayerShowTime: StateFlow<Boolean> = _miniPlayerShowTime.asStateFlow()
    fun setMiniPlayerShowTime(value: Boolean) {
        _miniPlayerShowTime.value = value
        prefs.edit().putBoolean(KEY_MINIPLAYER_SHOW_TIME, value).apply()
    }
    
    private val _miniPlayerUseCircularProgress = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_USE_CIRCULAR_PROGRESS, false))
    val miniPlayerUseCircularProgress: StateFlow<Boolean> = _miniPlayerUseCircularProgress.asStateFlow()
    fun setMiniPlayerUseCircularProgress(value: Boolean) {
        _miniPlayerUseCircularProgress.value = value
        prefs.edit().putBoolean(KEY_MINIPLAYER_USE_CIRCULAR_PROGRESS, value).apply()
    }
    
    private val _miniPlayerArtworkStyle = MutableStateFlow(prefs.getString(KEY_MINIPLAYER_ARTWORK_STYLE, "ROUNDED") ?: "ROUNDED")
    val miniPlayerArtworkStyle: StateFlow<String> = _miniPlayerArtworkStyle.asStateFlow()
    fun setMiniPlayerArtworkStyle(value: String) {
        _miniPlayerArtworkStyle.value = value
        prefs.edit().putString(KEY_MINIPLAYER_ARTWORK_STYLE, value).apply()
    }
    
    private val _miniPlayerShowSkipButtons = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SHOW_SKIP_BUTTONS, true))
    val miniPlayerShowSkipButtons: StateFlow<Boolean> = _miniPlayerShowSkipButtons.asStateFlow()
    fun setMiniPlayerShowSkipButtons(value: Boolean) {
        _miniPlayerShowSkipButtons.value = value
        prefs.edit().putBoolean(KEY_MINIPLAYER_SHOW_SKIP_BUTTONS, value).apply()
    }
    
    private val _miniPlayerTextAlignment = MutableStateFlow(prefs.getString(KEY_MINIPLAYER_TEXT_ALIGNMENT, "START") ?: "START")
    val miniPlayerTextAlignment: StateFlow<String> = _miniPlayerTextAlignment.asStateFlow()
    fun setMiniPlayerTextAlignment(value: String) {
        _miniPlayerTextAlignment.value = value
        prefs.edit().putString(KEY_MINIPLAYER_TEXT_ALIGNMENT, value).apply()
    }
    
    private val _miniPlayerSwipeGestures = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SWIPE_GESTURES, true))
    val miniPlayerSwipeGestures: StateFlow<Boolean> = _miniPlayerSwipeGestures.asStateFlow()
    fun setMiniPlayerSwipeGestures(value: Boolean) {
        _miniPlayerSwipeGestures.value = value
        prefs.edit().putBoolean(KEY_MINIPLAYER_SWIPE_GESTURES, value).apply()
    }
    
    private val _miniPlayerShowArtist = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SHOW_ARTIST, true))
    val miniPlayerShowArtist: StateFlow<Boolean> = _miniPlayerShowArtist.asStateFlow()
    fun setMiniPlayerShowArtist(value: Boolean) {
        _miniPlayerShowArtist.value = value
        prefs.edit().putBoolean(KEY_MINIPLAYER_SHOW_ARTIST, value).apply()
    }
    
    private val _miniPlayerAlwaysShowTablet = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_ALWAYS_SHOW_TABLET, false))
    val miniPlayerAlwaysShowTablet: StateFlow<Boolean> = _miniPlayerAlwaysShowTablet.asStateFlow()
    fun setMiniPlayerAlwaysShowTablet(value: Boolean) {
        _miniPlayerAlwaysShowTablet.value = value
        prefs.edit().putBoolean(KEY_MINIPLAYER_ALWAYS_SHOW_TABLET, value).apply()
    }
    
    private val _homeDiscoverAutoScroll = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_AUTO_SCROLL, true))
    val homeDiscoverAutoScroll: StateFlow<Boolean> = _homeDiscoverAutoScroll.asStateFlow()
    fun setHomeDiscoverAutoScroll(value: Boolean) {
        _homeDiscoverAutoScroll.value = value
        prefs.edit().putBoolean(KEY_HOME_DISCOVER_AUTO_SCROLL, value).apply()
    }
    
    private val _homeDiscoverAutoScrollInterval = MutableStateFlow(prefs.getInt(KEY_HOME_DISCOVER_AUTO_SCROLL_INTERVAL, 5))
    val homeDiscoverAutoScrollInterval: StateFlow<Int> = _homeDiscoverAutoScrollInterval.asStateFlow()
    fun setHomeDiscoverAutoScrollInterval(value: Int) {
        if (value in 2..15) {
            _homeDiscoverAutoScrollInterval.value = value
            prefs.edit().putInt(KEY_HOME_DISCOVER_AUTO_SCROLL_INTERVAL, value).apply()
        }
    }
    
    private val _homeDiscoverItemCount = MutableStateFlow(prefs.getInt(KEY_HOME_DISCOVER_ITEM_COUNT, 6))
    val homeDiscoverItemCount: StateFlow<Int> = _homeDiscoverItemCount.asStateFlow()
    fun setHomeDiscoverItemCount(value: Int) {
        if (value in 3..12) {
            _homeDiscoverItemCount.value = value
            prefs.edit().putInt(KEY_HOME_DISCOVER_ITEM_COUNT, value).apply()
        }
    }
    
    private val _homeRecentlyPlayedCount = MutableStateFlow(prefs.getInt(KEY_HOME_RECENTLY_PLAYED_COUNT, 6))
    val homeRecentlyPlayedCount: StateFlow<Int> = _homeRecentlyPlayedCount.asStateFlow()
    fun setHomeRecentlyPlayedCount(value: Int) {
        if (value in 3..12) {
            _homeRecentlyPlayedCount.value = value
            prefs.edit().putInt(KEY_HOME_RECENTLY_PLAYED_COUNT, value).apply()
        }
    }
    
    private val _homeArtistsCount = MutableStateFlow(prefs.getInt(KEY_HOME_ARTISTS_COUNT, 10))
    val homeArtistsCount: StateFlow<Int> = _homeArtistsCount.asStateFlow()
    fun setHomeArtistsCount(value: Int) {
        if (value in 4..20) {
            _homeArtistsCount.value = value
            prefs.edit().putInt(KEY_HOME_ARTISTS_COUNT, value).apply()
        }
    }
    
    private val _homeNewReleasesCount = MutableStateFlow(prefs.getInt(KEY_HOME_NEW_RELEASES_COUNT, 10))
    val homeNewReleasesCount: StateFlow<Int> = _homeNewReleasesCount.asStateFlow()
    fun setHomeNewReleasesCount(value: Int) {
        if (value in 4..20) {
            _homeNewReleasesCount.value = value
            prefs.edit().putInt(KEY_HOME_NEW_RELEASES_COUNT, value).apply()
        }
    }
    
    private val _homeRecentlyAddedCount = MutableStateFlow(prefs.getInt(KEY_HOME_RECENTLY_ADDED_COUNT, 10))
    val homeRecentlyAddedCount: StateFlow<Int> = _homeRecentlyAddedCount.asStateFlow()
    fun setHomeRecentlyAddedCount(value: Int) {
        if (value in 4..20) {
            _homeRecentlyAddedCount.value = value
            prefs.edit().putInt(KEY_HOME_RECENTLY_ADDED_COUNT, value).apply()
        }
    }
    
    private val _homeRecommendedCount = MutableStateFlow(prefs.getInt(KEY_HOME_RECOMMENDED_COUNT, 4))
    val homeRecommendedCount: StateFlow<Int> = _homeRecommendedCount.asStateFlow()
    fun setHomeRecommendedCount(value: Int) {
        if (value in 2..8) {
            _homeRecommendedCount.value = value
            prefs.edit().putInt(KEY_HOME_RECOMMENDED_COUNT, value).apply()
        }
    }
    
    private val _homeCompactCards = MutableStateFlow(prefs.getBoolean(KEY_HOME_COMPACT_CARDS, false))
    val homeCompactCards: StateFlow<Boolean> = _homeCompactCards.asStateFlow()
    fun setHomeCompactCards(value: Boolean) {
        _homeCompactCards.value = value
        prefs.edit().putBoolean(KEY_HOME_COMPACT_CARDS, value).apply()
    }
    
    private val _homeShowPlayButtons = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_PLAY_BUTTONS, true))
    val homeShowPlayButtons: StateFlow<Boolean> = _homeShowPlayButtons.asStateFlow()
    fun setHomeShowPlayButtons(value: Boolean) {
        _homeShowPlayButtons.value = value
        prefs.edit().putBoolean(KEY_HOME_SHOW_PLAY_BUTTONS, value).apply()
    }
    
    private val _homeCarouselHeight = MutableStateFlow(prefs.getInt(KEY_HOME_CAROUSEL_HEIGHT, 260))
    val homeCarouselHeight: StateFlow<Int> = _homeCarouselHeight.asStateFlow()
    fun setHomeCarouselHeight(value: Int) {
        if (value in 180..320) {
            _homeCarouselHeight.value = value
            prefs.edit().putInt(KEY_HOME_CAROUSEL_HEIGHT, value).apply()
        }
    }
    
    // Carousel Style: 0=Default (CenteredHero, 2 side peeks), 1=Hero (Uncontained, 1 side peek)
    private val _homeDiscoverCarouselStyle = MutableStateFlow(prefs.getInt(KEY_HOME_DISCOVER_CAROUSEL_STYLE, 0))
    val homeDiscoverCarouselStyle: StateFlow<Int> = _homeDiscoverCarouselStyle.asStateFlow()
    fun setHomeDiscoverCarouselStyle(value: Int) {
        if (value in 0..1) {
            _homeDiscoverCarouselStyle.value = value
            prefs.edit().putInt(KEY_HOME_DISCOVER_CAROUSEL_STYLE, value).apply()
        }
    }
    
    // Discover Widget - Content Visibility Settings
    private val _homeDiscoverShowAlbumName = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_SHOW_ALBUM_NAME, true))
    val homeDiscoverShowAlbumName: StateFlow<Boolean> = _homeDiscoverShowAlbumName.asStateFlow()
    fun setHomeDiscoverShowAlbumName(value: Boolean) {
        _homeDiscoverShowAlbumName.value = value
        prefs.edit().putBoolean(KEY_HOME_DISCOVER_SHOW_ALBUM_NAME, value).apply()
    }
    
    private val _homeDiscoverShowArtistName = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_SHOW_ARTIST_NAME, true))
    val homeDiscoverShowArtistName: StateFlow<Boolean> = _homeDiscoverShowArtistName.asStateFlow()
    fun setHomeDiscoverShowArtistName(value: Boolean) {
        _homeDiscoverShowArtistName.value = value
        prefs.edit().putBoolean(KEY_HOME_DISCOVER_SHOW_ARTIST_NAME, value).apply()
    }
    
    private val _homeDiscoverShowYear = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_SHOW_YEAR, true))
    val homeDiscoverShowYear: StateFlow<Boolean> = _homeDiscoverShowYear.asStateFlow()
    fun setHomeDiscoverShowYear(value: Boolean) {
        _homeDiscoverShowYear.value = value
        prefs.edit().putBoolean(KEY_HOME_DISCOVER_SHOW_YEAR, value).apply()
    }
    
    private val _homeDiscoverShowPlayButton = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_SHOW_PLAY_BUTTON, true))
    val homeDiscoverShowPlayButton: StateFlow<Boolean> = _homeDiscoverShowPlayButton.asStateFlow()
    fun setHomeDiscoverShowPlayButton(value: Boolean) {
        _homeDiscoverShowPlayButton.value = value
        prefs.edit().putBoolean(KEY_HOME_DISCOVER_SHOW_PLAY_BUTTON, value).apply()
    }
    
    private val _homeDiscoverShowGradient = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_SHOW_GRADIENT, true))
    val homeDiscoverShowGradient: StateFlow<Boolean> = _homeDiscoverShowGradient.asStateFlow()
    fun setHomeDiscoverShowGradient(value: Boolean) {
        _homeDiscoverShowGradient.value = value
        prefs.edit().putBoolean(KEY_HOME_DISCOVER_SHOW_GRADIENT, value).apply()
    }
    
    // Gesture Settings
    private val _gesturePlayerSwipeDismiss = MutableStateFlow(prefs.getBoolean(KEY_GESTURE_PLAYER_SWIPE_DISMISS, true))
    val gesturePlayerSwipeDismiss: StateFlow<Boolean> = _gesturePlayerSwipeDismiss.asStateFlow()
    fun setGesturePlayerSwipeDismiss(value: Boolean) {
        _gesturePlayerSwipeDismiss.value = value
        prefs.edit().putBoolean(KEY_GESTURE_PLAYER_SWIPE_DISMISS, value).apply()
    }
    
    private val _gesturePlayerSwipeTracks = MutableStateFlow(prefs.getBoolean(KEY_GESTURE_PLAYER_SWIPE_TRACKS, true))
    val gesturePlayerSwipeTracks: StateFlow<Boolean> = _gesturePlayerSwipeTracks.asStateFlow()
    fun setGesturePlayerSwipeTracks(value: Boolean) {
        _gesturePlayerSwipeTracks.value = value
        prefs.edit().putBoolean(KEY_GESTURE_PLAYER_SWIPE_TRACKS, value).apply()
    }
    
    private val _gestureArtworkDoubleTap = MutableStateFlow(prefs.getBoolean(KEY_GESTURE_ARTWORK_DOUBLE_TAP, true))
    val gestureArtworkDoubleTap: StateFlow<Boolean> = _gestureArtworkDoubleTap.asStateFlow()
    fun setGestureArtworkDoubleTap(value: Boolean) {
        _gestureArtworkDoubleTap.value = value
        prefs.edit().putBoolean(KEY_GESTURE_ARTWORK_DOUBLE_TAP, value).apply()
    }
    
    // Default section order for home screen
    private val defaultHomeSectionOrder = listOf(
        "GREETING", "DISCOVER", "RECENTLY_PLAYED", "ARTISTS", 
        "NEW_RELEASES", "RECENTLY_ADDED", "RECOMMENDED", "STATS", "MOOD"
    )
    private val _homeSectionOrder = MutableStateFlow(
        prefs.getString(KEY_HOME_SECTION_ORDER, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: defaultHomeSectionOrder
    )
    val homeSectionOrder: StateFlow<List<String>> = _homeSectionOrder.asStateFlow()
    fun setHomeSectionOrder(value: List<String>) {
        _homeSectionOrder.value = value
        prefs.edit().putString(KEY_HOME_SECTION_ORDER, value.joinToString(",")).apply()
    }
    
    // Album Bottom Sheet Appearance Settings
    private val _albumBottomSheetGradientBlur = MutableStateFlow(prefs.getBoolean(KEY_ALBUM_BOTTOM_SHEET_GRADIENT_BLUR, true))
    val albumBottomSheetGradientBlur: StateFlow<Boolean> = _albumBottomSheetGradientBlur.asStateFlow()
    fun setAlbumBottomSheetGradientBlur(value: Boolean) {
        _albumBottomSheetGradientBlur.value = value
        prefs.edit().putBoolean(KEY_ALBUM_BOTTOM_SHEET_GRADIENT_BLUR, value).apply()
    }
}
