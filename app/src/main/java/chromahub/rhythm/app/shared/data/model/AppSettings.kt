/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.model

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import chromahub.rhythm.app.R
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import chromahub.rhythm.app.util.GsonUtils
import chromahub.rhythm.app.util.ArtistSeparator
import chromahub.rhythm.app.worker.BackupWorker
import chromahub.rhythm.app.worker.RhythmPulseNotificationWorker
import chromahub.rhythm.app.worker.UpdateNotificationWorker
import chromahub.rhythm.app.BuildConfig
import java.io.File
import java.util.Date // Import Date for timestamp
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import androidx.core.net.toUri

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
 * Enum for artist artwork source preferences
 */
enum class ArtistArtworkSource {
    PREFER_LOCAL_THEN_API,
    LOCAL_ONLY,
    API_ONLY,
    DISABLED;

    companion object {
        fun fromName(name: String?): ArtistArtworkSource {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: PREFER_LOCAL_THEN_API
        }
    }
}

data class RhythmAuraPolicyBand(
    val minAge: Int,
    val maxAge: Int,
    val maxVolumeThreshold: Float,
    val recommendedDailyMinutes: Int,
    val stopPlaybackOnZeroVolume: Boolean,
    val enforceHapticFeedback: Boolean
)

typealias RhythmGuardPolicyBand = RhythmAuraPolicyBand

private val RHYTHM_AURA_POLICY_BANDS = listOf(
    RhythmAuraPolicyBand(8, 12, 0.50f, 40, true, true),
    RhythmAuraPolicyBand(13, 15, 0.56f, 55, true, true),
    RhythmAuraPolicyBand(16, 17, 0.60f, 70, true, true),
    RhythmAuraPolicyBand(18, 25, 0.68f, 95, false, false),
    RhythmAuraPolicyBand(26, 40, 0.72f, 120, false, false),
    RhythmAuraPolicyBand(41, 55, 0.70f, 105, false, false),
    RhythmAuraPolicyBand(56, 80, 0.65f, 90, true, true)
)

private val RHYTHM_GUARD_POLICY_BANDS = RHYTHM_AURA_POLICY_BANDS


/**
 * Priority order for lyrics APIs
 */
enum class LyricsApiPriority(val displayName: String) {
    LYRICALLY_FIRST("Lyrically"),
    LRCLIB_FIRST("LRCLib"),
    BETTERLYRICS_FIRST("Better Lyrics");

    companion object {
        fun fromOrdinal(ordinal: Int): LyricsApiPriority {
            return values().getOrElse(ordinal) { LYRICALLY_FIRST }
        }
    }
}

/**
 * Singleton class to manage all app settings using SharedPreferences
 */
class AppSettings private constructor(context: Context) {
    companion object {
        private const val PREFS_NAME = "rhythm_preferences"
        
        // Playback Settings
        private const val KEY_GAPLESS_PLAYBACK = "gapless_playback"
        private const val KEY_CROSSFADE = "crossfade"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_CROSSFADE_REPEAT_ONE = "crossfade_repeat_one"
        private const val KEY_CROSSFADE_ON_SKIP = "crossfade_on_skip"
        private const val KEY_AUDIO_NORMALIZATION = "audio_normalization"
        private const val KEY_REPLAY_GAIN = "replay_gain"
        private const val KEY_REPLAY_GAIN_MODE = "replay_gain_mode"
        private const val KEY_REPLAY_GAIN_DRC = "replay_gain_drc"
        private const val KEY_REPLAY_GAIN_PREAMP = "replay_gain_preamp"
        private const val KEY_REPLAY_GAIN_PREAMP_UNTAGGED = "replay_gain_preamp_untagged"
        private const val KEY_SKIP_SILENCE = "skip_silence_enabled"
        private const val KEY_AUDIO_ROUTING_MODE = "audio_routing_mode" // "default", "app", "system"
        private const val KEY_RESUME_ON_DEVICE_RECONNECT = "resume_on_device_reconnect"
        private const val KEY_AUDIO_OFFLOAD_ENABLED = "audio_offload_enabled"
        
        // Battery Saver Settings
        private const val KEY_BATTERY_SAVER_ENABLED = "battery_saver_enabled"
        private const val KEY_BATTERY_SAVER_MODE = "battery_saver_mode" // "auto" or "manual"
        private const val KEY_BATTERY_SAVER_DISABLE_HAPTICS = "battery_saver_disable_haptics"
        private const val KEY_BATTERY_SAVER_ENABLE_OFFLOAD = "battery_saver_enable_offload"
        private const val KEY_BATTERY_SAVER_DISABLE_MARQUEE = "battery_saver_disable_marquee"
        private const val KEY_BATTERY_SAVER_DISABLE_LOSSLESS_ARTWORK = "battery_saver_disable_lossless_artwork"
        private const val KEY_BATTERY_SAVER_DISABLE_AUTO_FETCH_ARTWORK = "battery_saver_disable_auto_fetch_artwork"
        private const val KEY_GLOBAL_MARQUEE_ENABLED = "global_marquee_enabled"
        
        private const val KEY_PRELOAD_LIMIT = "preload_limit"
        
        // Lyrics Settings
        private const val KEY_SHOW_LYRICS = "show_lyrics"
        private const val KEY_ONLINE_ONLY_LYRICS = "online_only_lyrics" // Deprecated, kept for migration
        private const val KEY_LYRICS_SOURCE_PREFERENCE = "lyrics_source_preference"
        private const val KEY_SHOW_LYRICS_BACKGROUND_ARTWORK = "show_lyrics_background_artwork"
        private const val KEY_SHOW_LYRICS_TRANSLATION = "show_lyrics_translation"
        private const val KEY_SHOW_LYRICS_ROMANIZATION = "show_lyrics_romanization"
        private const val KEY_KEEP_SCREEN_ON_LYRICS = "keep_screen_on_lyrics"
        private const val KEY_TAP_LYRICS_TO_FULL_SCREEN = "tap_lyrics_to_full_screen"
        private const val KEY_LYRICS_API_PRIORITY = "lyrics_api_priority"
        private const val KEY_LYRICS_API_FALLBACK_RETRY = "lyrics_api_fallback_retry"
        private const val KEY_AUTO_HIDE_LYRICS_CONTROLS = "auto_hide_lyrics_controls"
        private const val KEY_LYRIC_BOLD = "lyric_bold"
        private const val KEY_TRIM_LYRICS = "trim_lyrics"
        private const val KEY_LYRIC_NO_ANIMATION = "lyric_no_animation"
        private const val KEY_TRANSLATION_AUTO_WORD = "translation_auto_word"
        
        // Theme Settings
        private const val KEY_USE_SYSTEM_THEME = "use_system_theme"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_AMOLED_THEME = "amoled_theme"
        private const val KEY_FLOATING_NAVIGATION_BAR = "floating_navigation_bar"
        private const val KEY_USE_DYNAMIC_COLORS = "use_dynamic_colors"
        private const val KEY_CUSTOM_COLOR_SCHEME = "custom_color_scheme"
        private const val KEY_CUSTOM_FONT = "custom_font"
        private const val KEY_COLOR_SOURCE = "color_source" // ALBUM_ART, MONET, or CUSTOM
        private const val KEY_EXTRACTED_ALBUM_COLORS = "extracted_album_colors" // JSON string with color values
        private const val KEY_FONT_SOURCE = "font_source" // SYSTEM or CUSTOM
        private const val KEY_CUSTOM_FONT_PATH = "custom_font_path" // Path to imported font file
        private const val KEY_CUSTOM_FONT_FAMILY = "custom_font_family" // Display name of custom font
        
        // Player Theme Settings
        private const val KEY_PLAYER_THEME_ID = "player_theme_id" // ID of the selected player theme (default, compact, large, minimal)
        private const val KEY_MINI_PLAYER_THEME_ID = "miniplayer_theme_id"
        private const val KEY_USE_EXPERIMENTAL_PLAYER_UI = "use_experimental_player_ui"
        private const val KEY_ENABLE_ALBUM_EDITING = "enable_album_editing"
        
        // Library Settings
        private const val KEY_ALBUM_VIEW_TYPE = "album_view_type"
        private const val KEY_ARTIST_VIEW_TYPE = "artist_view_type"
        private const val KEY_PLAYLIST_VIEW_TYPE = "playlist_view_type"
        private const val KEY_ALBUM_SORT_ORDER = "album_sort_order"
        private const val KEY_PLAYLIST_SORT_ORDER = "playlist_sort_order"
        private const val KEY_PLAYLIST_DETAIL_SORT_ORDER = "playlist_detail_sort_order"
        private const val KEY_ARTIST_COLLABORATION_MODE = "artist_collaboration_mode"
        private const val KEY_LIBRARY_TAB_ORDER = "library_tab_order"
        private const val KEY_LIBRARY_COMBINE_DISCS = "library_combine_discs"
        private const val KEY_PLAYER_CHIP_ORDER = "player_chip_order"
        private const val KEY_HIDDEN_LIBRARY_TABS = "hidden_library_tabs"
        private const val KEY_HIDDEN_PLAYER_CHIPS = "hidden_player_chips"
        private const val KEY_EXPRESSIVE_BOTTOM_BUTTONS_NORMAL = "expressive_bottom_buttons_normal"
        private const val KEY_EXPRESSIVE_HIDDEN_BOTTOM_BUTTONS_NORMAL = "expressive_hidden_bottom_buttons_normal"
        private const val KEY_EXPRESSIVE_BOTTOM_BUTTONS_MERGE = "expressive_bottom_buttons_merge"
        private const val KEY_EXPRESSIVE_HIDDEN_BOTTOM_BUTTONS_MERGE = "expressive_hidden_bottom_buttons_merge"
        private const val KEY_LYRICALLY_SOURCES_ORDER = "lyrically_sources_order"
        private const val KEY_DISABLED_LYRICALLY_SOURCES = "disabled_lyrically_sources"
        private const val KEY_GROUP_BY_ALBUM_ARTIST = "group_by_album_artist" // New setting for album artist grouping
        private const val KEY_PREFER_SONG_ARTWORK = "prefer_song_artwork" // Prefer per-song embedded artwork over shared album art
        private const val KEY_IGNORE_MEDIASTORE_COVERS = "ignore_mediastore_covers" // Legacy key kept for migration compatibility
        private const val KEY_LOSSLESS_ARTWORK = "lossless_artwork" // Show cover art without downscaling/compression
        private const val KEY_SHOW_LIBRARY_SECTION_HEADERS = "show_library_section_headers"
        private const val KEY_SHOW_LIBRARY_BOTTOM_BAR_ALWAYS = "show_library_bottom_bar_always"
        
        // Audio Device Settings
        private const val KEY_LAST_AUDIO_DEVICE = "last_audio_device"
        private const val KEY_AUTO_CONNECT_DEVICE = "auto_connect_device"
        private const val KEY_USE_SYSTEM_VOLUME = "use_system_volume"
        private const val KEY_APP_VOLUME = "app_volume"
        private const val KEY_STOP_PLAYBACK_ON_ZERO_VOLUME = "stop_playback_on_zero_volume"
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
        private const val KEY_MONO_AUDIO_ENABLED = "mono_audio_enabled"
        
        // Cache Settings
        private const val KEY_MAX_CACHE_SIZE = "max_cache_size"

        
        // Search History
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_SHOW_KEYBOARD_ON_SEARCH_OPEN = "show_keyboard_on_search_open"
        
        // Playlists
        private const val KEY_PLAYLISTS = "playlists"
        private const val KEY_FAVORITE_SONGS = "favorite_songs"
        private const val KEY_DEFAULT_PLAYLISTS_ENABLED = "default_playlists_enabled"
        
        // User Statistics
        private const val KEY_LISTENING_TIME = "listening_time"
        private const val KEY_SONGS_PLAYED = "songs_played"
        private const val KEY_UNIQUE_ARTISTS = "unique_artists"
        private const val KEY_GENRE_PREFERENCES = "genre_preferences"
        private const val KEY_TIME_BASED_PREFERENCES = "time_based_preferences"

        // Rhythm Guard (listening health)
        private const val KEY_RHYTHM_GUARD_MODE = "rhythm_guard_mode"
        private const val KEY_RHYTHM_GUARD_AGE = "rhythm_guard_age"
        private const val KEY_RHYTHM_GUARD_MANUAL_WARNINGS_ENABLED = "rhythm_guard_manual_warnings_enabled"
        private const val KEY_RHYTHM_GUARD_MANUAL_VOLUME_THRESHOLD = "rhythm_guard_manual_volume_threshold"
        private const val KEY_RHYTHM_GUARD_APPLY_VOLUME_LIMIT_ON_SPEAKER = "rhythm_guard_apply_volume_limit_on_speaker"
        private const val KEY_RHYTHM_GUARD_LAST_AUTO_APPLIED_AT = "rhythm_guard_last_auto_applied_at"
        private const val KEY_RHYTHM_GUARD_ALERT_THRESHOLD_MINUTES = "rhythm_guard_alert_threshold_minutes"
        private const val KEY_RHYTHM_GUARD_WARNING_TIMEOUT_MINUTES = "rhythm_guard_warning_timeout_minutes"
        private const val KEY_RHYTHM_GUARD_POST_TIMEOUT_COOLDOWN_MINUTES = "rhythm_guard_post_timeout_cooldown_minutes"
        private const val KEY_RHYTHM_GUARD_BREAK_RESUME_MINUTES = "rhythm_guard_break_resume_minutes"
        private const val KEY_RHYTHM_GUARD_TIMEOUT_UNTIL_MS = "rhythm_guard_timeout_until_ms"
        private const val KEY_RHYTHM_GUARD_TIMEOUT_REASON = "rhythm_guard_timeout_reason"
        private const val KEY_RHYTHM_GUARD_TIMEOUT_STARTED_AT_MS = "rhythm_guard_timeout_started_at_ms"
        private const val KEY_RHYTHM_GUARD_TIMEOUT_COOLDOWN_UNTIL_MS = "rhythm_guard_timeout_cooldown_until_ms"
        private const val KEY_RHYTHM_GUARD_NEXT_ALLOWED_LIMIT_MINUTES = "rhythm_guard_next_allowed_limit_minutes"
        private const val KEY_RHYTHM_GUARD_FIRST_BREAK_SEEN = "rhythm_guard_first_break_seen"
        private const val KEY_HOME_SHOW_RHYTHM_GUARD = "home_show_rhythm_guard"

        // Legacy keys kept for migration compatibility.
        private const val KEY_RHYTHM_AURA_MODE = "rhythm_aura_mode"
        private const val KEY_RHYTHM_AURA_AGE = "rhythm_aura_age"
        private const val KEY_RHYTHM_AURA_MANUAL_WARNINGS_ENABLED = "rhythm_aura_manual_warnings_enabled"
        private const val KEY_RHYTHM_AURA_MANUAL_VOLUME_THRESHOLD = "rhythm_aura_manual_volume_threshold"
        private const val KEY_RHYTHM_AURA_LAST_AUTO_APPLIED_AT = "rhythm_aura_last_auto_applied_at"

        const val RHYTHM_GUARD_MODE_OFF = "OFF"
        const val RHYTHM_GUARD_MODE_AUTO = "AUTO"
        const val RHYTHM_GUARD_MODE_MANUAL = "MANUAL"

        @Deprecated("Use RHYTHM_GUARD_MODE_OFF")
        const val RHYTHM_AURA_MODE_OFF = RHYTHM_GUARD_MODE_OFF
        @Deprecated("Use RHYTHM_GUARD_MODE_AUTO")
        const val RHYTHM_AURA_MODE_AUTO = RHYTHM_GUARD_MODE_AUTO
        @Deprecated("Use RHYTHM_GUARD_MODE_MANUAL")
        const val RHYTHM_AURA_MODE_MANUAL = RHYTHM_GUARD_MODE_MANUAL
        
        // Recently Played
        private const val KEY_RECENTLY_PLAYED = "recently_played"
        private const val KEY_RECENTLY_PLAYED_SONG_CACHE = "recently_played_song_cache"
        private const val KEY_LAST_PLAYED_TIMESTAMP = "last_played_timestamp"
        
        // API Integration
        private const val KEY_DEEZER_API_ENABLED = "deezer_api_enabled"
        private const val KEY_LRCLIB_API_ENABLED = "lrclib_api_enabled"
        private const val KEY_BETTERLYRICS_API_ENABLED = "better_lyrics_api_enabled"
        private const val KEY_YTMUSIC_API_ENABLED = "ytmusic_api_enabled"
        private const val KEY_SPOTIFY_API_ENABLED = "spotify_api_enabled"
        private const val KEY_SPOTIFY_CLIENT_ID = "spotify_client_id"
        private const val KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"
        private const val KEY_LYRICALLY_API_ENABLED = "lyrically_api_enabled"
        private const val KEY_WIKIPEDIA_API_ENABLED = "wikipedia_api_enabled"
        private const val KEY_AUTO_FETCH_ARTWORK = "auto_fetch_artwork"
        private const val KEY_ARTIST_ARTWORK_SOURCE = "artist_artwork_source"
        private const val KEY_APPLE_CANVAS_ENABLED = "apple_canvas_enabled"
        private const val KEY_APPLE_CANVAS_NETWORK_MODE = "apple_canvas_network_mode"
        
        // General Broadcast Status Settings (for Tasker, KWGT, etc.)
        private const val KEY_BROADCAST_STATUS_ENABLED = "broadcast_status_enabled"
        private const val KEY_BLUETOOTH_LYRICS_ENABLED = "bluetooth_lyrics_enabled"
        
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
        private const val KEY_AUDIO_METADATA_EXTRACTION_COMPLETED = "audio_metadata_extraction_completed"
        private const val KEY_EMBEDDED_ARTWORK_EXTRACTION_COMPLETED = "embedded_artwork_extraction_completed"
        private const val KEY_EMBEDDED_ARTWORK_EXTRACTION_LOSSLESS_STATUS = "embedded_artwork_extraction_lossless_status"

        // App Updater Settings
        private const val KEY_AUTO_CHECK_FOR_UPDATES = "auto_check_for_updates"
        private const val KEY_UPDATE_CHANNEL = "update_channel" // New key for update channel
        private const val KEY_UPDATE_SOURCE = "update_source" // New key for OTA source flavor
        private const val KEY_UPDATES_ENABLED = "updates_enabled" // Master switch for updates
        private const val KEY_UPDATE_NOTIFICATIONS_ENABLED = "update_notifications_enabled" // Push-style notifications
        private const val KEY_UPDATE_STATUS_NOTIFICATIONS_ENABLED = "update_status_notifications_enabled" // Notify for no-update/error states
        private const val KEY_MEDIA_SCAN_MODE = "media_scan_mode" // Mode for media scanning: "blacklist" or "whitelist"
        private const val KEY_INCLUDE_HIDDEN_WHITELISTED_MEDIA = "include_hidden_whitelisted_media"
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
    private const val KEY_LIBRARY_OPERATIONS_NOTIFICATIONS_ENABLED = "library_operations_notifications_enabled"
    private const val KEY_SLEEP_TIMER_NOTIFICATIONS_ENABLED = "sleep_timer_notifications_enabled"
    private const val KEY_STREAMING_NOTIFICATIONS_ENABLED = "streaming_notifications_enabled"
    private const val KEY_RHYTHM_GUARD_ALERT_NOTIFICATIONS_ENABLED = "rhythm_guard_alert_notifications_enabled"
    private const val KEY_RHYTHM_GUARD_TIMER_NOTIFICATIONS_ENABLED = "rhythm_guard_timer_notifications_enabled"
    private const val KEY_RHYTHM_PULSE_NOTIFICATIONS_ENABLED = "rhythm_pulse_notifications_enabled"
    private const val KEY_RHYTHM_PULSE_NOTIFICATION_INTERVAL_HOURS = "rhythm_pulse_notification_interval_hours"
    
    // UI Settings
    private const val KEY_USE_SETTINGS = "use_settings"
    private const val KEY_DEFAULT_SCREEN = "default_screen"
    private const val KEY_FORCE_PLAYER_COMPACT_MODE = "force_player_compact_mode"
    
        // Codec Monitoring & Enhanced Seeking
        private const val KEY_CODEC_MONITORING_ENABLED = "codec_monitoring_enabled"
        private const val KEY_SHOW_CODEC_NOTIFICATIONS = "show_codec_notifications"
        private const val KEY_ENHANCED_SEEKING_ENABLED = "enhanced_seeking_enabled"
        
        // Media3 1.9.0 Features
        private const val KEY_USE_CUSTOM_COMMAND_BUTTONS = "use_custom_command_buttons"
        private const val KEY_SCRUBBING_MODE_ENABLED = "scrubbing_mode_enabled"
        private const val KEY_STUCK_PLAYER_DETECTION_ENABLED = "stuck_player_detection_enabled"
        private const val KEY_TRACK_ERROR_CHECKER_ENABLED = "track_error_checker_enabled"
        
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
        private const val KEY_PENDING_FULL_MEDIA_RESCAN = "pending_full_media_rescan"
        private const val KEY_LAST_EMBEDDED_ART_SELF_HEAL_MS = "last_embedded_art_self_heal_ms"
        
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
        private const val KEY_REMEMBER_STREAMING_PASSWORDS = "remember_streaming_passwords"
        
        // Queue & Playback Behavior
        private const val KEY_SHUFFLE_USES_EXOPLAYER = "shuffle_uses_exoplayer"
        private const val KEY_AUTO_ADD_TO_QUEUE = "auto_add_to_queue"
        private const val KEY_RESPECT_ALBUM_ON_PLAY = "respect_album_on_play"
        private const val KEY_CLEAR_QUEUE_ON_NEW_SONG = "clear_queue_on_new_song"
        private const val KEY_CONTEXT_QUEUE_PREFERENCE = "context_queue_preference" // ARTIST_FIRST | GENRE_FIRST | ARTIST_THEN_GENRE
        private const val KEY_CONTEXT_QUEUE_PERSISTENCE = "context_queue_persistence" // EPHEMERAL | PERSISTENT
        private const val KEY_CONTEXT_QUEUE_SIZE = "context_queue_size" // default number of contextual tracks to build
        private const val KEY_HIDE_PLAYED_SONGS_IN_QUEUE = "hide_played_songs_in_queue"
        private const val KEY_SHOW_QUEUE_DIALOG = "show_queue_dialog"
        private const val KEY_LIST_QUEUE_ACTION_BEHAVIOR = "list_queue_action_behavior" // "replace", "ask", "play_next", "add_to_end"
        private const val KEY_REPEAT_MODE_PERSISTENCE = "repeat_mode_persistence"
        private const val KEY_SHUFFLE_MODE_PERSISTENCE = "shuffle_mode_persistence"
        private const val KEY_KEEP_SHUFFLE_ON_SELECTION = "keep_shuffle_on_selection"
        private const val KEY_SAVED_SHUFFLE_STATE = "saved_shuffle_state"
        private const val KEY_SAVED_REPEAT_MODE = "saved_repeat_mode"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed"
        private const val KEY_USE_DEFAULT_PLAYBACK_SPEED = "use_default_playback_speed"
        private const val KEY_PLAYBACK_PITCH = "playback_pitch"
        private const val KEY_SYNC_SPEED_AND_PITCH = "sync_speed_and_pitch"
        private const val KEY_USE_HOURS_IN_TIME_FORMAT = "use_hours_in_time_format"
        private const val KEY_SHOW_REMAINING_TIME = "show_remaining_time"
        private const val KEY_USE_EXACT_ARTWORK_COLORS = "use_exact_artwork_colors"
        private const val KEY_STOP_PLAYBACK_ON_APP_CLOSE = "stop_playback_on_app_close"
        private const val KEY_QUEUE_PERSISTENCE_ENABLED = "queue_persistence_enabled" // Enable/disable queue persistence
        private const val KEY_SAVED_QUEUE = "saved_queue" // Queue persistence - list of song IDs
        private const val KEY_SAVED_QUEUE_INDEX = "saved_queue_index" // Current position in queue
        private const val KEY_SAVED_PLAYBACK_POSITION = "saved_playback_position" // Current playback position in ms
        private const val KEY_HIDE_PLAYED_QUEUE_SONGS = "hide_played_queue_songs" // Hide already-played songs in queue
        
        // Widget Settings
        private const val KEY_WIDGET_SHOW_ALBUM_ART = "widget_show_album_art"
        private const val KEY_WIDGET_SHOW_ARTIST = "widget_show_artist"
        private const val KEY_WIDGET_SHOW_ALBUM = "widget_show_album"
        private const val KEY_WIDGET_CORNER_RADIUS = "widget_corner_radius"
        private const val KEY_WIDGET_AUTO_UPDATE = "widget_auto_update"
        private const val KEY_WIDGET_SHOW_FAVORITE_BUTTON = "widget_show_favorite_button"
        private const val KEY_WIDGET_THEME = "widget_theme"
        // Rhythm Cookie widget corner actions: 0=skip, 1=shuffle, 2=repeat, 3=favorite, 4=none
        private const val KEY_WIDGET_COOKIE_BOTTOM_LEFT = "widget_cookie_bottom_left"
        private const val KEY_WIDGET_COOKIE_BOTTOM_RIGHT = "widget_cookie_bottom_right"
        // Rhythm Stats widget: 0=all time, 1=today, 2=week, 3=month
        private const val KEY_WIDGET_STATS_RANGE = "widget_stats_range"
        // Rhythm Stats gem: 0=longest streak, 1=current streak, 2=active days, 3=total sessions
        private const val KEY_WIDGET_STATS_GEM = "widget_stats_gem"
        
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
        
        // Home Screen Customization Settings - Discover Widget
        private const val KEY_HOME_DISCOVER_AUTO_SCROLL = "home_discover_auto_scroll"
        private const val KEY_HOME_DISCOVER_AUTO_SCROLL_INTERVAL = "home_discover_auto_scroll_interval"
        private const val KEY_HOME_DISCOVER_ITEM_COUNT = "home_discover_item_count"
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

        // Streaming Home Screen Customization
        private const val KEY_STREAMING_HOME_SHOW_GREETING = "streaming_home_show_greeting"
        private const val KEY_STREAMING_HOME_SHOW_RHYTHM_GUARD = "streaming_home_show_rhythm_guard"
        private const val KEY_STREAMING_HOME_SHOW_RHYTHM_STATS = "streaming_home_show_rhythm_stats"
        private const val KEY_STREAMING_HOME_SHOW_RECENTLY_PLAYED = "streaming_home_show_recently_played"
        private const val KEY_STREAMING_HOME_SHOW_ARTISTS = "streaming_home_show_artists"
        private const val KEY_STREAMING_HOME_SHOW_RECOMMENDED = "streaming_home_show_recommended"
        private const val KEY_STREAMING_HOME_SHOW_NEW_RELEASES = "streaming_home_show_new_releases"
        private const val KEY_STREAMING_HOME_SHOW_PLAYLISTS = "streaming_home_show_playlists"
        private const val KEY_STREAMING_HOME_SHOW_RECOMMENDATIONS = "streaming_home_show_recommendations"
        private const val KEY_STREAMING_HOME_SHOW_TOP_CHARTS = "streaming_home_show_top_charts"
        private const val KEY_STREAMING_HOME_SECTION_ORDER = "streaming_home_section_order"

        private const val KEY_ALBUM_BOTTOM_SHEET_GRADIENT_BLUR = "album_bottom_sheet_gradient_blur"
        private const val KEY_ALBUM_BOTTOM_SHEET_DISC_FILTER = "album_bottom_sheet_disc_filter"
        private const val KEY_ALBUM_HIDE_ABOUT = "album_hide_about"
        
        // Artist Separator Settings
        const val DEFAULT_ARTIST_SEPARATOR_DELIMITERS = ";/"
        private const val KEY_ARTIST_SEPARATOR_ENABLED = "artist_separator_enabled"
        private const val KEY_ARTIST_SEPARATOR_DELIMITERS = "artist_separator_delimiters" // Delimiter string or JSON array
        private const val KEY_ARTIST_SEPARATOR_CUSTOM_DELIMITERS = "artist_separator_custom_delimiters"
        private const val KEY_ARTIST_SEPARATOR_CACHE_SIGNATURE = "artist_separator_cache_signature"
        
        // Player Screen Customization Settings
        private const val KEY_PLAYER_SHOW_GRADIENT_OVERLAY = "player_show_gradient_overlay"
        private const val KEY_PLAYER_ART_OVERLAY_TYPE = "player_art_overlay_type" // 0=Gradient, 1=Blur
        private const val KEY_PLAYER_ART_OVERLAY_INTENSITY = "player_art_overlay_intensity" // Float 0.0-1.0
        private const val KEY_PLAYER_LYRICS_OVERLAY_TYPE = "player_lyrics_overlay_type" // 0=Gradient, 1=Blur
        private const val KEY_PLAYER_LYRICS_OVERLAY_INTENSITY = "player_lyrics_overlay_intensity" // Float 0.0-1.0
        private const val KEY_PLAYER_AMBIENT_BACKDROP_ENABLED = "player_ambient_backdrop_enabled" // Ambient backdrop from artwork
        private const val KEY_PLAYER_AMBIENT_BACKDROP_INTENSITY = "player_ambient_backdrop_intensity" // Float 0.0-1.0, controls container transparency
        private const val KEY_PLAYER_AMBIENT_INFINITE_ZOOM = "player_ambient_infinite_zoom" // Boolean, continuous slow zoom on ambient backdrop
        private const val KEY_PLAYER_ACCENT_BACKGROUND_ENABLED = "player_accent_background_enabled" // Use accent color as player bg (normal mode)
        private const val KEY_PLAYER_MERGE_CONTROLS_TO_BOTTOM = "player_merge_controls_to_bottom" // Merge lyrics/favorite into centered bottom icon controls
        private const val KEY_PLAYER_GLASS_INTENSITY = "player_glass_intensity" // Float 0.0-2.0, glass effect opacity multiplier
        private const val KEY_PLAYER_LYRICS_TRANSITION = "player_lyrics_transition" // 0=SlideVertical, 1=Fade, 2=Scale, 3=SlideHorizontal
        private const val KEY_PLAYER_LYRICS_TEXT_SIZE = "player_lyrics_text_size" // Float sp multiplier, default 1.0
        private const val KEY_PLAYER_LYRICS_ALIGNMENT = "player_lyrics_alignment" // "CENTER", "START", "END"
        private const val KEY_PLAYER_SHOW_ART_BELOW_LYRICS = "player_show_art_below_lyrics" // Boolean
        private const val KEY_PLAYER_SHOW_SEEK_BUTTONS = "player_show_seek_buttons"
        private const val KEY_PLAYER_TEXT_ALIGNMENT = "player_text_alignment" // "START", "CENTER", "END"
        private const val KEY_PLAYER_SHOW_SONG_INFO_ON_ARTWORK = "player_show_song_info_on_artwork"
        private const val KEY_PLAYER_ARTWORK_CORNER_RADIUS = "player_artwork_corner_radius" // 0-40 dp
        private const val KEY_PLAYER_SHOW_AUDIO_QUALITY_BADGES = "player_show_audio_quality_badges"
        private const val KEY_PLAYER_PROGRESS_STYLE = "player_progress_style" // "NORMAL", "WAVY", "ROUNDED", "THIN", "THICK"
        private const val KEY_PLAYER_PROGRESS_THUMB_STYLE = "player_progress_thumb_style" // "NONE", "DEFAULT", "CIRCLE", "SQUARE", "PILL", "DIAMOND", "FLOWER", "HEART", "COOKIE", "PUFFY"
        private const val KEY_PLAYER_PROGRESS_THUMB_ROTATE = "player_progress_thumb_rotate" // Boolean
        
        // MiniPlayer Customization Settings
        private const val KEY_MINIPLAYER_PROGRESS_STYLE = "miniplayer_progress_style" // "NORMAL", "WAVY", "ROUNDED", "THIN", "GRADIENT"
        private const val KEY_MINIPLAYER_SHOW_PROGRESS = "miniplayer_show_progress"
        private const val KEY_MINIPLAYER_SHOW_ARTWORK = "miniplayer_show_artwork"
        private const val KEY_MINIPLAYER_ARTWORK_SIZE = "miniplayer_artwork_size" // 40-72 dp
        private const val KEY_MINIPLAYER_CORNER_RADIUS = "miniplayer_corner_radius" // 0-28 dp
        private const val KEY_MINIPLAYER_SHOW_TIME = "miniplayer_show_time"
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
        
        // Expressive MaterialShapes Settings (M3 Expressive API)
        private const val KEY_EXPRESSIVE_SHAPES_ENABLED = "expressive_shapes_enabled" // Master toggle for expressive shapes
        private const val KEY_EXPRESSIVE_SHAPE_PRESET = "expressive_shape_preset" // Preset: DEFAULT, PLAYFUL, ORGANIC, GEOMETRIC, RETRO, CUSTOM
        private const val KEY_EXPRESSIVE_SHAPE_ALBUM_ART = "expressive_shape_album_art" // Shape for album artwork
        private const val KEY_EXPRESSIVE_SHAPE_PLAYER_ART = "expressive_shape_player_art" // Shape for player artwork
        private const val KEY_EXPRESSIVE_SHAPE_SONG_ART = "expressive_shape_song_art" // Shape for song artwork
        private const val KEY_EXPRESSIVE_SHAPE_PLAYLIST_ART = "expressive_shape_playlist_art" // Shape for playlist artwork
        private const val KEY_EXPRESSIVE_SHAPE_ARTIST_ART = "expressive_shape_artist_art" // Shape for artist artwork
        private const val KEY_EXPRESSIVE_SHAPE_PLAYER_CONTROLS = "expressive_shape_player_controls" // Shape for player controls
        private const val KEY_EXPRESSIVE_SHAPE_MINI_PLAYER = "expressive_shape_mini_player" // Shape for mini player
        private const val KEY_SHOW_SETTINGS_SUGGESTIONS = "show_settings_suggestions"
        private const val KEY_INITIAL_SETTINGS_SUBROUTE = "initial_settings_subroute"
        private const val KEY_INITIAL_STREAMING_ROUTE = "initial_streaming_route"
        
        const val KEY_SONG_LYRICS_PREFERENCES = "song_lyrics_preferences"
        const val KEY_SONG_CUSTOM_LRC_FILES = "song_custom_lrc_files"
        const val KEY_LRC_RENAME_BEHAVIOR = "lrc_rename_behavior"
        
        @Volatile
        private var INSTANCE: AppSettings? = null
        
        fun getInstance(context: Context): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun defaultAllowedFormats(): Set<String> = setOf(
            "mp3", "flac", "ogg", "m4a", "opus", "opa", "wav", "aac", "alac", "aiff", "aif", "wma",
            "mka", "ac3", "ac4", "oga", "mid", "midi", "adts", "m4b", "eac", "eac3", "mhm", "mhm1",
            "dts", "dtshd", "dtsx", "truehd", "ape", "wv", "tta", "tak", "dsf", "dff", "dsd"
        )
    }
    
    private val context: Context = context.applicationContext
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setInitialSettingsSubroute(route: String?) {
        prefs.edit { putString(KEY_INITIAL_SETTINGS_SUBROUTE, route) }
    }

    fun consumeInitialSettingsSubroute(): String? {
        val v = prefs.getString(KEY_INITIAL_SETTINGS_SUBROUTE, null)
        if (v != null) prefs.edit { remove(KEY_INITIAL_SETTINGS_SUBROUTE) }
        return v
    }

    fun setInitialStreamingRoute(route: String?) {
        prefs.edit { putString(KEY_INITIAL_STREAMING_ROUTE, route) }
    }

    fun consumeInitialStreamingRoute(): String? {
        val v = prefs.getString(KEY_INITIAL_STREAMING_ROUTE, null)
        if (v != null) prefs.edit { remove(KEY_INITIAL_STREAMING_ROUTE) }
        return v
    }
    
    // Playback Settings
    private val _audioOffloadEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUDIO_OFFLOAD_ENABLED, false))
    val audioOffloadEnabled: StateFlow<Boolean> = _audioOffloadEnabled.asStateFlow()
    
    private val _preloadLimit = MutableStateFlow(prefs.getInt(KEY_PRELOAD_LIMIT, 3))
    val preloadLimit: StateFlow<Int> = _preloadLimit.asStateFlow()
    
    private val _gaplessPlayback = MutableStateFlow(prefs.getBoolean(KEY_GAPLESS_PLAYBACK, true))
    val gaplessPlayback: StateFlow<Boolean> = _gaplessPlayback.asStateFlow()
    
    private val _resumeOnDeviceReconnect = MutableStateFlow(prefs.getBoolean(KEY_RESUME_ON_DEVICE_RECONNECT, false))
    val resumeOnDeviceReconnect: StateFlow<Boolean> = _resumeOnDeviceReconnect.asStateFlow()
    
    private val _crossfade = MutableStateFlow(prefs.getBoolean(KEY_CROSSFADE, true))
    val crossfade: StateFlow<Boolean> = _crossfade.asStateFlow()
    
    private val _crossfadeDuration = MutableStateFlow(prefs.getFloat(KEY_CROSSFADE_DURATION, 4f))
    val crossfadeDuration: StateFlow<Float> = _crossfadeDuration.asStateFlow()

    private val _crossfadeRepeatOne = MutableStateFlow(prefs.getBoolean(KEY_CROSSFADE_REPEAT_ONE, true))
    val crossfadeRepeatOne: StateFlow<Boolean> = _crossfadeRepeatOne.asStateFlow()
    
    private val _crossfadeOnSkip = MutableStateFlow(prefs.getBoolean(KEY_CROSSFADE_ON_SKIP, true))
    val crossfadeOnSkip: StateFlow<Boolean> = _crossfadeOnSkip.asStateFlow()
    
    private val _audioNormalization = MutableStateFlow(prefs.getBoolean(KEY_AUDIO_NORMALIZATION, true))
    val audioNormalization: StateFlow<Boolean> = _audioNormalization.asStateFlow()
    
    private val _replayGain = MutableStateFlow(prefs.getBoolean(KEY_REPLAY_GAIN, false))
    val replayGain: StateFlow<Boolean> = _replayGain.asStateFlow()

    private val _replayGainMode = MutableStateFlow(prefs.getInt(KEY_REPLAY_GAIN_MODE, 1)) // 1 = Track, 2 = Album, 0 = None
    val replayGainMode: StateFlow<Int> = _replayGainMode.asStateFlow()

    private val _replayGainDrc = MutableStateFlow(prefs.getBoolean(KEY_REPLAY_GAIN_DRC, true))
    val replayGainDrc: StateFlow<Boolean> = _replayGainDrc.asStateFlow()

    private val _replayGainPreamp = MutableStateFlow(prefs.getFloat(KEY_REPLAY_GAIN_PREAMP, 0f))
    val replayGainPreamp: StateFlow<Float> = _replayGainPreamp.asStateFlow()

    private val _replayGainPreampUntagged = MutableStateFlow(prefs.getFloat(KEY_REPLAY_GAIN_PREAMP_UNTAGGED, 0f))
    val replayGainPreampUntagged: StateFlow<Float> = _replayGainPreampUntagged.asStateFlow()
    
    private val _skipSilenceEnabled = MutableStateFlow(prefs.getBoolean(KEY_SKIP_SILENCE, false))
    val skipSilenceEnabled: StateFlow<Boolean> = _skipSilenceEnabled.asStateFlow()
    
    private val _audioRoutingMode = MutableStateFlow(prefs.getString(KEY_AUDIO_ROUTING_MODE, "default") ?: "default")
    val audioRoutingMode: StateFlow<String> = _audioRoutingMode.asStateFlow()
    
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
    
    private val _showLyricsBackgroundArtwork = MutableStateFlow(prefs.getBoolean(KEY_SHOW_LYRICS_BACKGROUND_ARTWORK, true))
    val showLyricsBackgroundArtwork: StateFlow<Boolean> = _showLyricsBackgroundArtwork.asStateFlow()

    private val _showLyricsTranslation = MutableStateFlow(prefs.getBoolean(KEY_SHOW_LYRICS_TRANSLATION, true))
    val showLyricsTranslation: StateFlow<Boolean> = _showLyricsTranslation.asStateFlow()
    
    private val _showLyricsRomanization = MutableStateFlow(prefs.getBoolean(KEY_SHOW_LYRICS_ROMANIZATION, true))
    val showLyricsRomanization: StateFlow<Boolean> = _showLyricsRomanization.asStateFlow()
    
    private val _keepScreenOnLyrics = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON_LYRICS, false))
    val keepScreenOnLyrics: StateFlow<Boolean> = _keepScreenOnLyrics.asStateFlow()
    
    private val _tapLyricsToFullScreen = MutableStateFlow(prefs.getBoolean(KEY_TAP_LYRICS_TO_FULL_SCREEN, true))
    val tapLyricsToFullScreen: StateFlow<Boolean> = _tapLyricsToFullScreen.asStateFlow()

    private val _lyricsApiPriority = MutableStateFlow(
        LyricsApiPriority.fromOrdinal(prefs.getInt(KEY_LYRICS_API_PRIORITY, LyricsApiPriority.LYRICALLY_FIRST.ordinal))
    )
    val lyricsApiPriority: StateFlow<LyricsApiPriority> = _lyricsApiPriority.asStateFlow()

    private val _lyricsApiFallbackRetry = MutableStateFlow(prefs.getBoolean(KEY_LYRICS_API_FALLBACK_RETRY, true))
    val lyricsApiFallbackRetry: StateFlow<Boolean> = _lyricsApiFallbackRetry.asStateFlow()
    
    private val _autoHideLyricsControls = MutableStateFlow(prefs.getBoolean(KEY_AUTO_HIDE_LYRICS_CONTROLS, true))
    val autoHideLyricsControls: StateFlow<Boolean> = _autoHideLyricsControls.asStateFlow()
    
    private val _lyricBold = MutableStateFlow(prefs.getBoolean(KEY_LYRIC_BOLD, false))
    val lyricBold: StateFlow<Boolean> = _lyricBold.asStateFlow()

    private val _trimLyrics = MutableStateFlow(prefs.getBoolean(KEY_TRIM_LYRICS, true))
    val trimLyrics: StateFlow<Boolean> = _trimLyrics.asStateFlow()

    private val _lyricNoAnimation = MutableStateFlow(prefs.getBoolean(KEY_LYRIC_NO_ANIMATION, false))
    val lyricNoAnimation: StateFlow<Boolean> = _lyricNoAnimation.asStateFlow()

    private val _translationAutoWord = MutableStateFlow(prefs.getBoolean(KEY_TRANSLATION_AUTO_WORD, false))
    val translationAutoWord: StateFlow<Boolean> = _translationAutoWord.asStateFlow()
    
    // Theme Settings
    private val _useSystemTheme = MutableStateFlow(prefs.getBoolean(KEY_USE_SYSTEM_THEME, true))
    val useSystemTheme: StateFlow<Boolean> = _useSystemTheme.asStateFlow()
    
    private val _darkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, true))
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()
    
    private val _amoledTheme = MutableStateFlow(prefs.getBoolean(KEY_AMOLED_THEME, false))
    val amoledTheme: StateFlow<Boolean> = _amoledTheme.asStateFlow()

    private val _floatingNavigationBar = MutableStateFlow(prefs.getBoolean(KEY_FLOATING_NAVIGATION_BAR, true))
    val floatingNavigationBar: StateFlow<Boolean> = _floatingNavigationBar.asStateFlow()
    
    private val _useDynamicColors = MutableStateFlow(prefs.getBoolean(KEY_USE_DYNAMIC_COLORS, true))
    val useDynamicColors: StateFlow<Boolean> = _useDynamicColors.asStateFlow()
    
    // Artist Separator Settings
    private val _artistSeparatorEnabled = MutableStateFlow(prefs.getBoolean(KEY_ARTIST_SEPARATOR_ENABLED, true))
    val artistSeparatorEnabled: StateFlow<Boolean> = _artistSeparatorEnabled.asStateFlow()
    
    // Default delimiters: ; /
    private val _artistSeparatorDelimiters = MutableStateFlow(
        prefs.getString(KEY_ARTIST_SEPARATOR_DELIMITERS, DEFAULT_ARTIST_SEPARATOR_DELIMITERS) ?: DEFAULT_ARTIST_SEPARATOR_DELIMITERS
    )
    val artistSeparatorDelimiters: StateFlow<String> = _artistSeparatorDelimiters.asStateFlow()

    private val _artistSeparatorCustomDelimiters = MutableStateFlow<List<String>>(
        prefs.getString(KEY_ARTIST_SEPARATOR_CUSTOM_DELIMITERS, null)?.let {
            ArtistSeparator.parseDelimiters(it)
        } ?: emptyList()
    )
    val artistSeparatorCustomDelimiters: StateFlow<List<String>> = _artistSeparatorCustomDelimiters.asStateFlow()
    
    private val _customColorScheme = MutableStateFlow(prefs.getString(KEY_CUSTOM_COLOR_SCHEME, "Default") ?: "Default")
    val customColorScheme: StateFlow<String> = _customColorScheme.asStateFlow()
    
    private val _customFont = MutableStateFlow(prefs.getString(KEY_CUSTOM_FONT, "Geom") ?: "Geom")
    val customFont: StateFlow<String> = _customFont.asStateFlow()
    
    private val _colorSource = MutableStateFlow(prefs.getString(KEY_COLOR_SOURCE, "MONET") ?: "MONET")
    val colorSource: StateFlow<String> = _colorSource.asStateFlow()
    
    private val _extractedAlbumColors = MutableStateFlow(prefs.getString(KEY_EXTRACTED_ALBUM_COLORS, null))
    val extractedAlbumColors: StateFlow<String?> = _extractedAlbumColors.asStateFlow()
    
    private val _fontSource = MutableStateFlow(prefs.getString(KEY_FONT_SOURCE, "SYSTEM") ?: "SYSTEM")
    val fontSource: StateFlow<String> = _fontSource.asStateFlow()
    
    private val _customFontPath = MutableStateFlow(prefs.getString(KEY_CUSTOM_FONT_PATH, null))
    val customFontPath: StateFlow<String?> = _customFontPath.asStateFlow()
    
    private val _customFontFamily = MutableStateFlow(prefs.getString(KEY_CUSTOM_FONT_FAMILY, "System") ?: "System")
    val customFontFamily: StateFlow<String> = _customFontFamily.asStateFlow()
    
    // Player Theme Settings
    private val _playerThemeId = MutableStateFlow(prefs.getString(KEY_PLAYER_THEME_ID, "EXPRESSIVE") ?: "EXPRESSIVE")
    val playerThemeId: StateFlow<String> = _playerThemeId.asStateFlow()
    
    private val _miniPlayerThemeId = MutableStateFlow(prefs.getString(KEY_MINI_PLAYER_THEME_ID, "EXPRESSIVE") ?: "EXPRESSIVE")
    val miniPlayerThemeId: StateFlow<String> = _miniPlayerThemeId.asStateFlow()
    
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
    
    // Playlist Detail Sort Order (for songs within a playlist)
    private val _playlistDetailSortOrder = MutableStateFlow(prefs.getString(KEY_PLAYLIST_DETAIL_SORT_ORDER, "TITLE_ASC") ?: "TITLE_ASC")
    val playlistDetailSortOrder: StateFlow<String> = _playlistDetailSortOrder.asStateFlow()
    
    // Artist Collaboration Mode
    private val _artistCollaborationMode = MutableStateFlow(prefs.getBoolean(KEY_ARTIST_COLLABORATION_MODE, false))
    val artistCollaborationMode: StateFlow<Boolean> = _artistCollaborationMode.asStateFlow()
    
    // Library Tab Order
    private val defaultTabOrder = listOf("SONGS", "LIKED", "PLAYLISTS", "ALBUMS", "ARTISTS", "ALBUM_ARTISTS", "EXPLORER")
    private val _libraryTabOrder = MutableStateFlow(
        prefs.getString(KEY_LIBRARY_TAB_ORDER, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { order ->
                val withLiked = if ("LIKED" in order) order
                else {
                    val songsIndex = order.indexOf("SONGS")
                    if (songsIndex >= 0) order.toMutableList().apply { add(songsIndex + 1, "LIKED") }
                    else order + "LIKED"
                }
                if ("ALBUM_ARTISTS" in withLiked) withLiked
                else {
                    val artistsIndex = withLiked.indexOf("ARTISTS")
                    if (artistsIndex >= 0) withLiked.toMutableList().apply { add(artistsIndex + 1, "ALBUM_ARTISTS") }
                    else withLiked + "ALBUM_ARTISTS"
                }
            }
            ?: defaultTabOrder
    )
    val libraryTabOrder: StateFlow<List<String>> = _libraryTabOrder.asStateFlow()

    private val _libraryCombineDiscs = MutableStateFlow(prefs.getBoolean(KEY_LIBRARY_COMBINE_DISCS, false))
    val libraryCombineDiscs: StateFlow<Boolean> = _libraryCombineDiscs.asStateFlow()
    
    // Player Chip Order (Add to Playlist and Edit chips are not reorderable - they stay fixed)
    private val defaultChipOrder = listOf("FAVORITE", "SPEED", "EQUALIZER", "SLEEP_TIMER", "LYRICS", "ALBUM", "ARTIST", "SHARE")
    private val _playerChipOrder = MutableStateFlow(
        prefs.getString(KEY_PLAYER_CHIP_ORDER, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.map { if (it == "PITCH" || it == "SPEED_PITCH") "SPEED" else it }
            ?.distinct()
            ?.filter { it in defaultChipOrder }
            ?.takeIf { it.isNotEmpty() }
            ?.let { existingChips ->
                var updated = existingChips
                if (!updated.contains("SHARE")) {
                    updated = updated + "SHARE"
                }
                updated
            }
            ?: defaultChipOrder
    )
    val playerChipOrder: StateFlow<List<String>> = _playerChipOrder.asStateFlow()
    
    // Default Landing Screen
    private val _defaultScreen = MutableStateFlow(prefs.getString(KEY_DEFAULT_SCREEN, "home") ?: "home")
    val defaultScreen: StateFlow<String> = _defaultScreen.asStateFlow()
    
    // Hidden Library Tabs
    private val _hiddenLibraryTabs = MutableStateFlow(
        (prefs.getString(KEY_HIDDEN_LIBRARY_TABS, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()) + if ("ALBUM_ARTISTS" in (prefs.getString(KEY_LIBRARY_TAB_ORDER, null) ?: "")) {
            // User already has an order that includes Album Artists (they enabled it explicitly)
            emptySet()
        } else {
            // Legacy or fresh installs: hide the Album Artists tab by default
            setOf("ALBUM_ARTISTS")
        }
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

    // Expressive Player Bottom Buttons
    val defaultExpressiveBottomButtonsNormal = listOf("DEVICE", "QUEUE", "MORE")
    val defaultExpressiveBottomButtonsMerge = listOf("LYRICS", "FAVORITE", "DEVICE", "QUEUE", "MORE")
    val allExpressiveBottomButtons = listOf(
        "LYRICS", "FAVORITE", "DEVICE", "QUEUE", "MORE",
        "SHUFFLE", "REPEAT", "EQUALIZER", "SPEED", "SLEEP_TIMER",
        "ADD_TO_PLAYLIST", "ALBUM", "ARTIST", "SONG_INFO", "SHARE"
    )

    private val _expressiveBottomButtonsNormal = MutableStateFlow(
        prefs.getString(KEY_EXPRESSIVE_BOTTOM_BUTTONS_NORMAL, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.filter { it in allExpressiveBottomButtons }
            ?.takeIf { it.isNotEmpty() }
            ?: defaultExpressiveBottomButtonsNormal
    )
    val expressiveBottomButtonsNormal: StateFlow<List<String>> = _expressiveBottomButtonsNormal.asStateFlow()

    private val _expressiveHiddenBottomButtonsNormal = MutableStateFlow(
        prefs.getString(KEY_EXPRESSIVE_HIDDEN_BOTTOM_BUTTONS_NORMAL, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.filter { it in allExpressiveBottomButtons }
            ?.toSet()
            ?: emptySet()
    )
    val expressiveHiddenBottomButtonsNormal: StateFlow<Set<String>> = _expressiveHiddenBottomButtonsNormal.asStateFlow()

    private val _expressiveBottomButtonsMerge = MutableStateFlow(
        prefs.getString(KEY_EXPRESSIVE_BOTTOM_BUTTONS_MERGE, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.filter { it in allExpressiveBottomButtons }
            ?.takeIf { it.isNotEmpty() }
            ?: defaultExpressiveBottomButtonsMerge
    )
    val expressiveBottomButtonsMerge: StateFlow<List<String>> = _expressiveBottomButtonsMerge.asStateFlow()

    private val _expressiveHiddenBottomButtonsMerge = MutableStateFlow(
        prefs.getString(KEY_EXPRESSIVE_HIDDEN_BOTTOM_BUTTONS_MERGE, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.filter { it in allExpressiveBottomButtons }
            ?.toSet()
            ?: emptySet()
    )
    val expressiveHiddenBottomButtonsMerge: StateFlow<Set<String>> = _expressiveHiddenBottomButtonsMerge.asStateFlow()
    
    // Lyrically Sources Order
    val defaultLyricallySources = listOf(
        "APPLE_MUSIC",
        "SPOTIFY",
        "NETEASE",
        "QQ_MUSIC",
        "KUGOU",
        "YOUTUBE",
        "DEEZER",
        "MUSIXMATCH",
        "GENIUS"
    )
    val defaultDisabledLyricallySources = setOf(
        "SPOTIFY",
        "QQ_MUSIC",
        "YOUTUBE",
        "DEEZER",
        "MUSIXMATCH",
        "GENIUS"
    )
    private val _lyricallySourcesOrder = MutableStateFlow(
        prefs.getString(KEY_LYRICALLY_SOURCES_ORDER, null)
            ?.split(",")
            ?.filter { it.isNotBlank() && it in defaultLyricallySources }
            ?.takeIf { it.isNotEmpty() }
            ?: defaultLyricallySources
    )
    val lyricallySourcesOrder: StateFlow<List<String>> = _lyricallySourcesOrder.asStateFlow()

    private val _disabledLyricallySources = MutableStateFlow(
        prefs.getString(KEY_DISABLED_LYRICALLY_SOURCES, null)
            ?.split(",")
            ?.filter { it.isNotBlank() && it in defaultLyricallySources }
            ?.toSet()
            ?: defaultDisabledLyricallySources
    )
    val disabledLyricallySources: StateFlow<Set<String>> = _disabledLyricallySources.asStateFlow()
    
    // Group By Album Artist
    private val _groupByAlbumArtist = MutableStateFlow(false)
    val groupByAlbumArtist: StateFlow<Boolean> = _groupByAlbumArtist.asStateFlow()
    
    // Prefer per-song embedded artwork over shared MediaStore album art.
    private val _preferSongArtwork = MutableStateFlow(
        if (prefs.contains(KEY_PREFER_SONG_ARTWORK)) {
            prefs.getBoolean(KEY_PREFER_SONG_ARTWORK, true)
        } else {
            prefs.getBoolean(KEY_IGNORE_MEDIASTORE_COVERS, true)
        }
    )
    val preferSongArtwork: StateFlow<Boolean> = _preferSongArtwork.asStateFlow()

    @Deprecated("Use preferSongArtwork")
    val ignoreMediaStoreCovers: StateFlow<Boolean> = preferSongArtwork

    // Lossless Artwork - Show cover art as-is without compression
    private val _losslessArtwork = MutableStateFlow(prefs.getBoolean(KEY_LOSSLESS_ARTWORK, true))
    val losslessArtwork: StateFlow<Boolean> = _losslessArtwork.asStateFlow()

    // Show Library Section Headers
    private val _showLibrarySectionHeaders = MutableStateFlow(prefs.getBoolean(KEY_SHOW_LIBRARY_SECTION_HEADERS, true))
    val showLibrarySectionHeaders: StateFlow<Boolean> = _showLibrarySectionHeaders.asStateFlow()

    // Show Library Bottom Bar Always
    private val _showLibraryBottomBarAlways = MutableStateFlow(prefs.getBoolean(KEY_SHOW_LIBRARY_BOTTOM_BAR_ALWAYS, false))
    val showLibraryBottomBarAlways: StateFlow<Boolean> = _showLibraryBottomBarAlways.asStateFlow()
    
    // Alphabet Bar Settings
    private val _showAlphabetBar = MutableStateFlow(prefs.getBoolean(KEY_SHOW_ALPHABET_BAR, false))
    val showAlphabetBar: StateFlow<Boolean> = _showAlphabetBar.asStateFlow()
    
    private val _showScrollToTop = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SCROLL_TO_TOP, false))
    val showScrollToTop: StateFlow<Boolean> = _showScrollToTop.asStateFlow()
    
    // App Mode Settings (Local vs Streaming)
    private val _appMode = MutableStateFlow(prefs.getString(KEY_APP_MODE, "LOCAL") ?: "LOCAL")
    val appMode: StateFlow<String> = _appMode.asStateFlow()
    
    private val _streamingService = MutableStateFlow(prefs.getString(KEY_STREAMING_SERVICE, "SUBSONIC") ?: "SUBSONIC")
    val streamingService: StateFlow<String> = _streamingService.asStateFlow()
    
    // Default to LOSSLESS so the server streams the original file at full quality.
    // Users can downgrade in Go Settings if they want to save data.
    private val _streamingQuality = MutableStateFlow(prefs.getString(KEY_STREAMING_QUALITY, "LOSSLESS") ?: "LOSSLESS")
    val streamingQuality: StateFlow<String> = _streamingQuality.asStateFlow()
    
    private val _allowCellularStreaming = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_CELLULAR_STREAMING, true))
    val allowCellularStreaming: StateFlow<Boolean> = _allowCellularStreaming.asStateFlow()
    
    private val _offlineMode = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_MODE, false))
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()

    private val _rememberStreamingPasswords = MutableStateFlow(prefs.getBoolean(KEY_REMEMBER_STREAMING_PASSWORDS, true))
    val rememberStreamingPasswords: StateFlow<Boolean> = _rememberStreamingPasswords.asStateFlow()
    
    // Audio Device Settings
    private val _lastAudioDevice = MutableStateFlow(prefs.getString(KEY_LAST_AUDIO_DEVICE, null))
    val lastAudioDevice: StateFlow<String?> = _lastAudioDevice.asStateFlow()
    
    private val _autoConnectDevice = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CONNECT_DEVICE, true))
    val autoConnectDevice: StateFlow<Boolean> = _autoConnectDevice.asStateFlow()
    
    private val _useSystemVolume = MutableStateFlow(prefs.getBoolean(KEY_USE_SYSTEM_VOLUME, true))
    val useSystemVolume: StateFlow<Boolean> = _useSystemVolume.asStateFlow()
    
    private val _appVolume = MutableStateFlow(prefs.getFloat(KEY_APP_VOLUME, 1.0f))
    val appVolume: StateFlow<Float> = _appVolume.asStateFlow()
    
    private val _stopPlaybackOnZeroVolume = MutableStateFlow(prefs.getBoolean(KEY_STOP_PLAYBACK_ON_ZERO_VOLUME, false))
    val stopPlaybackOnZeroVolume: StateFlow<Boolean> = _stopPlaybackOnZeroVolume.asStateFlow()
    
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
    
    private val _bassBoostStrength = MutableStateFlow(prefs.getInt(KEY_BASS_BOOST_STRENGTH, 0))
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()
    
    private val _virtualizerEnabled = MutableStateFlow(prefs.getBoolean(KEY_VIRTUALIZER_ENABLED, false))
    val virtualizerEnabled: StateFlow<Boolean> = _virtualizerEnabled.asStateFlow()
    
    private val _virtualizerStrength = MutableStateFlow(prefs.getInt(KEY_VIRTUALIZER_STRENGTH, 0))
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()
    
    private val _monoAudioEnabled = MutableStateFlow(prefs.getBoolean(KEY_MONO_AUDIO_ENABLED, false))
    val monoAudioEnabled: StateFlow<Boolean> = _monoAudioEnabled.asStateFlow()
    
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
    private val _respectAlbumOnPlay = MutableStateFlow(prefs.getBoolean(KEY_RESPECT_ALBUM_ON_PLAY, true))
    val respectAlbumOnPlay: StateFlow<Boolean> = _respectAlbumOnPlay.asStateFlow()
    
    private val _clearQueueOnNewSong = MutableStateFlow(prefs.getBoolean(KEY_CLEAR_QUEUE_ON_NEW_SONG, false))
    val clearQueueOnNewSong: StateFlow<Boolean> = _clearQueueOnNewSong.asStateFlow()

    private val initialHidePlayedQueueValue = prefs.getBoolean(
        KEY_HIDE_PLAYED_QUEUE_SONGS,
        prefs.getBoolean(KEY_HIDE_PLAYED_SONGS_IN_QUEUE, false)
    )

    private val _hidePlayedSongsInQueue = MutableStateFlow(initialHidePlayedQueueValue)
    val hidePlayedSongsInQueue: StateFlow<Boolean> = _hidePlayedSongsInQueue.asStateFlow()
    
    private val _showQueueDialog = MutableStateFlow(prefs.getBoolean(KEY_SHOW_QUEUE_DIALOG, true))
    val showQueueDialog: StateFlow<Boolean> = _showQueueDialog.asStateFlow()

    private val _listQueueActionBehavior = MutableStateFlow(
        prefs.getString(KEY_LIST_QUEUE_ACTION_BEHAVIOR, "replace") ?: "replace"
    )
    val listQueueActionBehavior: StateFlow<String> = _listQueueActionBehavior.asStateFlow()
    
    private val _hidePlayedQueueSongs = MutableStateFlow(initialHidePlayedQueueValue)
    val hidePlayedQueueSongs: StateFlow<Boolean> = _hidePlayedQueueSongs.asStateFlow()
    
    private val _repeatModePersistence = MutableStateFlow(prefs.getBoolean(KEY_REPEAT_MODE_PERSISTENCE, true))
    val repeatModePersistence: StateFlow<Boolean> = _repeatModePersistence.asStateFlow()
    
    private val _shuffleModePersistence = MutableStateFlow(prefs.getBoolean(KEY_SHUFFLE_MODE_PERSISTENCE, true))
    val shuffleModePersistence: StateFlow<Boolean> = _shuffleModePersistence.asStateFlow()
    
    private val _keepShuffleOnSelection = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SHUFFLE_ON_SELECTION, false))
    val keepShuffleOnSelection: StateFlow<Boolean> = _keepShuffleOnSelection.asStateFlow()
    
    private val _savedShuffleState = MutableStateFlow(prefs.getBoolean(KEY_SAVED_SHUFFLE_STATE, false))
    val savedShuffleState: StateFlow<Boolean> = _savedShuffleState.asStateFlow()
    
    private val _savedRepeatMode = MutableStateFlow(prefs.getInt(KEY_SAVED_REPEAT_MODE, 0)) // 0 = OFF, 1 = ALL, 2 = ONE
    val savedRepeatMode: StateFlow<Int> = _savedRepeatMode.asStateFlow()
    
    private val _playbackSpeed = MutableStateFlow(prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f))
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _defaultPlaybackSpeed = MutableStateFlow(prefs.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, 1.0f))
    val defaultPlaybackSpeed: StateFlow<Float> = _defaultPlaybackSpeed.asStateFlow()

    private val _useDefaultPlaybackSpeed = MutableStateFlow(prefs.getBoolean(KEY_USE_DEFAULT_PLAYBACK_SPEED, false))
    val useDefaultPlaybackSpeed: StateFlow<Boolean> = _useDefaultPlaybackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(prefs.getFloat(KEY_PLAYBACK_PITCH, 1.0f))
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _syncSpeedAndPitch = MutableStateFlow(prefs.getBoolean(KEY_SYNC_SPEED_AND_PITCH, false))
    val syncSpeedAndPitch: StateFlow<Boolean> = _syncSpeedAndPitch.asStateFlow()
    
    // Time Format Settings - Show hours:minutes:seconds for longer tracks (>60 min)
    private val _useHoursInTimeFormat = MutableStateFlow(prefs.getBoolean(KEY_USE_HOURS_IN_TIME_FORMAT, true))
    val useHoursInTimeFormat: StateFlow<Boolean> = _useHoursInTimeFormat.asStateFlow()
    
    private val _showRemainingTime = MutableStateFlow(prefs.getBoolean(KEY_SHOW_REMAINING_TIME, false))
    val showRemainingTime: StateFlow<Boolean> = _showRemainingTime.asStateFlow()
    
    // Exact extracted colors from artwork settings
    private val _useExactArtworkColors = MutableStateFlow(prefs.getBoolean(KEY_USE_EXACT_ARTWORK_COLORS, false))
    val useExactArtworkColors: StateFlow<Boolean> = _useExactArtworkColors.asStateFlow()
    
    // Stop Playback on App Close
    private val _stopPlaybackOnAppClose = MutableStateFlow(prefs.getBoolean(KEY_STOP_PLAYBACK_ON_APP_CLOSE, false))
    val stopPlaybackOnAppClose: StateFlow<Boolean> = _stopPlaybackOnAppClose.asStateFlow()
    
    // Queue Persistence
    private val _queuePersistenceEnabled = MutableStateFlow(prefs.getBoolean(KEY_QUEUE_PERSISTENCE_ENABLED, true))
    val queuePersistenceEnabled: StateFlow<Boolean> = _queuePersistenceEnabled.asStateFlow()

    // Context queue preferences
    private val _contextQueuePreference = MutableStateFlow(prefs.getString(KEY_CONTEXT_QUEUE_PREFERENCE, "ARTIST_THEN_GENRE")!!)
    val contextQueuePreference: StateFlow<String> = _contextQueuePreference.asStateFlow()

    private val _contextQueuePersistence = MutableStateFlow(prefs.getString(KEY_CONTEXT_QUEUE_PERSISTENCE, "EPHEMERAL")!!)
    val contextQueuePersistence: StateFlow<String> = _contextQueuePersistence.asStateFlow()

    private val _contextQueueSize = MutableStateFlow(prefs.getInt(KEY_CONTEXT_QUEUE_SIZE, 50))
    val contextQueueSize: StateFlow<Int> = _contextQueueSize.asStateFlow()
    
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
    private val _maxCacheSize = MutableStateFlow(safeLong(KEY_MAX_CACHE_SIZE, 300L * 1024L * 1024L)) // 300MB default
    val maxCacheSize: StateFlow<Long> = _maxCacheSize.asStateFlow()
    
    // Search History
    private val _searchHistory = MutableStateFlow<String?>(prefs.getString(KEY_SEARCH_HISTORY, null))
    val searchHistory: StateFlow<String?> = _searchHistory.asStateFlow()

    // Search UX
    private val _showKeyboardOnSearchOpen = MutableStateFlow(prefs.getBoolean(KEY_SHOW_KEYBOARD_ON_SEARCH_OPEN, true))
    val showKeyboardOnSearchOpen: StateFlow<Boolean> = _showKeyboardOnSearchOpen.asStateFlow()
    
    // Playlists
    private val _playlists = MutableStateFlow<String?>(prefs.getString(KEY_PLAYLISTS, null))
    val playlists: StateFlow<String?> = _playlists.asStateFlow()

    private val _favoriteSongs = MutableStateFlow<String?>(prefs.getString(KEY_FAVORITE_SONGS, null))
    val favoriteSongs: StateFlow<String?> = _favoriteSongs.asStateFlow()
    
    // Song Lyrics Preferences - Map of songId to source preference ("online", "embedded", "lrc")
    private val _songLyricsPreferences = MutableStateFlow<Map<String, String>>(
        try {
            val json = prefs.getString(KEY_SONG_LYRICS_PREFERENCES, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    )
    val songLyricsPreferences: StateFlow<Map<String, String>> = _songLyricsPreferences.asStateFlow()

    // Custom LRC Files - Map of songId to custom LRC filename
    private val _songCustomLrcFiles = MutableStateFlow<Map<String, String>>(
        try {
            val json = prefs.getString(KEY_SONG_CUSTOM_LRC_FILES, null)
            if (json != null) {
                Gson().fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    )
    val songCustomLrcFiles: StateFlow<Map<String, String>> = _songCustomLrcFiles.asStateFlow()

    // LRC Rename Behavior - "ask", "always", "never"
    private val _lrcRenameBehavior = MutableStateFlow(prefs.getString(KEY_LRC_RENAME_BEHAVIOR, "ask") ?: "ask")
    val lrcRenameBehavior: StateFlow<String> = _lrcRenameBehavior.asStateFlow()
    
    private val _defaultPlaylistsEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEFAULT_PLAYLISTS_ENABLED, true))
    val defaultPlaylistsEnabled: StateFlow<Boolean> = _defaultPlaylistsEnabled.asStateFlow()
    
    // User Statistics
    private val _listeningTime = MutableStateFlow(safeLong(KEY_LISTENING_TIME, 0L))
    val listeningTime: StateFlow<Long> = _listeningTime.asStateFlow()
    
    private val _songsPlayed = MutableStateFlow(prefs.getInt(KEY_SONGS_PLAYED, 0))
    val songsPlayed: StateFlow<Int> = _songsPlayed.asStateFlow()

    private val _rhythmAuraMode = MutableStateFlow(
        sanitizeRhythmGuardMode(
            prefs.getString(
                KEY_RHYTHM_GUARD_MODE,
                prefs.getString(KEY_RHYTHM_AURA_MODE, RHYTHM_GUARD_MODE_OFF)
            )
        )
    )
    val rhythmGuardMode: StateFlow<String> = _rhythmAuraMode.asStateFlow()
    @Deprecated("Use rhythmGuardMode")
    val rhythmAuraMode: StateFlow<String> = rhythmGuardMode

    private val _rhythmAuraAge = MutableStateFlow(
        prefs.getInt(KEY_RHYTHM_GUARD_AGE, prefs.getInt(KEY_RHYTHM_AURA_AGE, 18)).coerceIn(8, 80)
    )
    val rhythmGuardAge: StateFlow<Int> = _rhythmAuraAge.asStateFlow()
    @Deprecated("Use rhythmGuardAge")
    val rhythmAuraAge: StateFlow<Int> = rhythmGuardAge

    private val _rhythmAuraManualWarningsEnabled = MutableStateFlow(
        prefs.getBoolean(
            KEY_RHYTHM_GUARD_MANUAL_WARNINGS_ENABLED,
            prefs.getBoolean(KEY_RHYTHM_AURA_MANUAL_WARNINGS_ENABLED, true)
        )
    )
    val rhythmGuardManualWarningsEnabled: StateFlow<Boolean> = _rhythmAuraManualWarningsEnabled.asStateFlow()
    @Deprecated("Use rhythmGuardManualWarningsEnabled")
    val rhythmAuraManualWarningsEnabled: StateFlow<Boolean> = rhythmGuardManualWarningsEnabled

    private val _rhythmAuraManualVolumeThreshold = MutableStateFlow(
        prefs.getFloat(
            KEY_RHYTHM_GUARD_MANUAL_VOLUME_THRESHOLD,
            prefs.getFloat(KEY_RHYTHM_AURA_MANUAL_VOLUME_THRESHOLD, 0.68f)
        ).coerceIn(0.40f, 0.95f)
    )
    val rhythmGuardManualVolumeThreshold: StateFlow<Float> = _rhythmAuraManualVolumeThreshold.asStateFlow()
    @Deprecated("Use rhythmGuardManualVolumeThreshold")
    val rhythmAuraManualVolumeThreshold: StateFlow<Float> = rhythmGuardManualVolumeThreshold

    private val _rhythmGuardApplyVolumeLimitOnSpeaker = MutableStateFlow(
        prefs.getBoolean(KEY_RHYTHM_GUARD_APPLY_VOLUME_LIMIT_ON_SPEAKER, false)
    )
    val rhythmGuardApplyVolumeLimitOnSpeaker: StateFlow<Boolean> = _rhythmGuardApplyVolumeLimitOnSpeaker.asStateFlow()

    private val _rhythmAuraLastAutoAppliedAt = MutableStateFlow(
        safeLong(KEY_RHYTHM_GUARD_LAST_AUTO_APPLIED_AT, safeLong(KEY_RHYTHM_AURA_LAST_AUTO_APPLIED_AT, 0L))
    )
    val rhythmGuardLastAutoAppliedAt: StateFlow<Long> = _rhythmAuraLastAutoAppliedAt.asStateFlow()
    @Deprecated("Use rhythmGuardLastAutoAppliedAt")
    val rhythmAuraLastAutoAppliedAt: StateFlow<Long> = rhythmGuardLastAutoAppliedAt

    private val _rhythmGuardAlertThresholdMinutes = MutableStateFlow(
        prefs.getInt(KEY_RHYTHM_GUARD_ALERT_THRESHOLD_MINUTES, -1).coerceIn(-1, 24 * 60)
    )
    val rhythmGuardAlertThresholdMinutes: StateFlow<Int> = _rhythmGuardAlertThresholdMinutes.asStateFlow()

    private val _rhythmGuardWarningTimeoutMinutes = MutableStateFlow(
        prefs.getInt(KEY_RHYTHM_GUARD_WARNING_TIMEOUT_MINUTES, 2).coerceIn(1, 60)
    )
    val rhythmGuardWarningTimeoutMinutes: StateFlow<Int> = _rhythmGuardWarningTimeoutMinutes.asStateFlow()

    private val _rhythmGuardPostTimeoutCooldownMinutes = MutableStateFlow(
        prefs.getInt(KEY_RHYTHM_GUARD_POST_TIMEOUT_COOLDOWN_MINUTES, 10).coerceIn(1, 60)
    )
    val rhythmGuardPostTimeoutCooldownMinutes: StateFlow<Int> = _rhythmGuardPostTimeoutCooldownMinutes.asStateFlow()

    private val _rhythmGuardBreakResumeMinutes = MutableStateFlow(
        prefs.getInt(KEY_RHYTHM_GUARD_BREAK_RESUME_MINUTES, 15).coerceIn(1, 180)
    )
    val rhythmGuardBreakResumeMinutes: StateFlow<Int> = _rhythmGuardBreakResumeMinutes.asStateFlow()

    private val _rhythmGuardTimeoutUntilMs = MutableStateFlow(
        safeLong(KEY_RHYTHM_GUARD_TIMEOUT_UNTIL_MS, 0L).coerceAtLeast(0L)
    )
    val rhythmGuardTimeoutUntilMs: StateFlow<Long> = _rhythmGuardTimeoutUntilMs.asStateFlow()

    private val _rhythmGuardTimeoutReason = MutableStateFlow(
        prefs.getString(KEY_RHYTHM_GUARD_TIMEOUT_REASON, null).orEmpty()
    )
    val rhythmGuardTimeoutReason: StateFlow<String> = _rhythmGuardTimeoutReason.asStateFlow()

    private val _rhythmGuardTimeoutStartedAtMs = MutableStateFlow(
        safeLong(KEY_RHYTHM_GUARD_TIMEOUT_STARTED_AT_MS, 0L).coerceAtLeast(0L)
    )
    val rhythmGuardTimeoutStartedAtMs: StateFlow<Long> = _rhythmGuardTimeoutStartedAtMs.asStateFlow()

    private val _rhythmGuardTimeoutCooldownUntilMs = MutableStateFlow(
        safeLong(KEY_RHYTHM_GUARD_TIMEOUT_COOLDOWN_UNTIL_MS, 0L).coerceAtLeast(0L)
    )
    val rhythmGuardTimeoutCooldownUntilMs: StateFlow<Long> = _rhythmGuardTimeoutCooldownUntilMs.asStateFlow()
    
    private val _rhythmGuardNextAllowedLimitMinutes = MutableStateFlow(
        prefs.getInt(KEY_RHYTHM_GUARD_NEXT_ALLOWED_LIMIT_MINUTES, 0)
    )
    val rhythmGuardNextAllowedLimitMinutes: StateFlow<Int> = _rhythmGuardNextAllowedLimitMinutes.asStateFlow()
    fun setRhythmGuardNextAllowedLimitMinutes(value: Int) {
        _rhythmGuardNextAllowedLimitMinutes.value = value
        prefs.edit { putInt(KEY_RHYTHM_GUARD_NEXT_ALLOWED_LIMIT_MINUTES, value) }
    }

    private val _rhythmGuardFirstBreakSeen = MutableStateFlow(
        prefs.getBoolean(KEY_RHYTHM_GUARD_FIRST_BREAK_SEEN, false)
    )
    val rhythmGuardFirstBreakSeen: StateFlow<Boolean> = _rhythmGuardFirstBreakSeen.asStateFlow()
    fun setRhythmGuardFirstBreakSeen(seen: Boolean) {
        _rhythmGuardFirstBreakSeen.value = seen
        prefs.edit { putBoolean(KEY_RHYTHM_GUARD_FIRST_BREAK_SEEN, seen) }
    }

    private val _homeShowRhythmGuard = MutableStateFlow(
        prefs.getBoolean(KEY_HOME_SHOW_RHYTHM_GUARD, true)
    )
    val homeShowRhythmGuard: StateFlow<Boolean> = _homeShowRhythmGuard.asStateFlow()
    fun setHomeShowRhythmGuard(value: Boolean) {
        _homeShowRhythmGuard.value = value
        prefs.edit { putBoolean(KEY_HOME_SHOW_RHYTHM_GUARD, value) }
    }
    
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

    private data class RecentSongSnapshot(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val uri: String,
        val artworkUri: String?
    )

    private fun Song.toRecentSongSnapshot(): RecentSongSnapshot {
        return RecentSongSnapshot(
            id = id,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            uri = uri.toString(),
            artworkUri = artworkUri?.toString()
        )
    }

    private fun RecentSongSnapshot.toSongOrNull(): Song? {
        return runCatching {
            Song(
                id = id,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                uri = uri.toUri(),
                artworkUri = artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            )
        }.getOrNull()
    }

    private fun loadRecentlyPlayedSongCache(): Map<String, Song> {
        return try {
            val json = prefs.getString(KEY_RECENTLY_PLAYED_SONG_CACHE, null)
            if (json.isNullOrBlank()) {
                emptyMap()
            } else {
                Gson().fromJson<List<RecentSongSnapshot>>(
                    json,
                    object : TypeToken<List<RecentSongSnapshot>>() {}.type
                )
                    .orEmpty()
                    .mapNotNull { snapshot ->
                        snapshot.toSongOrNull()?.let { song -> song.id to song }
                    }
                    .toMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
    
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

    private val _recentlyPlayedSongCache = MutableStateFlow(loadRecentlyPlayedSongCache())
    val recentlyPlayedSongCache: StateFlow<Map<String, Song>> = _recentlyPlayedSongCache.asStateFlow()
    
    private val _lastPlayedTimestamp = MutableStateFlow(safeLong(KEY_LAST_PLAYED_TIMESTAMP, 0L))
    val lastPlayedTimestamp: StateFlow<Long> = _lastPlayedTimestamp.asStateFlow()
    
    // API Enable/Disable States
    private val _deezerApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_DEEZER_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val deezerApiEnabled: StateFlow<Boolean> = _deezerApiEnabled.asStateFlow()
    
    private val _lrclibApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_LRCLIB_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val lrclibApiEnabled: StateFlow<Boolean> = _lrclibApiEnabled.asStateFlow()
    
    private val _betterLyricsApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_BETTERLYRICS_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val betterLyricsApiEnabled: StateFlow<Boolean> = _betterLyricsApiEnabled.asStateFlow()
    
    private val _ytMusicApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_YTMUSIC_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val ytMusicApiEnabled: StateFlow<Boolean> = _ytMusicApiEnabled.asStateFlow()
    
    private val _spotifyApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_SPOTIFY_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val spotifyApiEnabled: StateFlow<Boolean> = _spotifyApiEnabled.asStateFlow()
    
    private val _lyricallyApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_LYRICALLY_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val lyricallyApiEnabled: StateFlow<Boolean> = _lyricallyApiEnabled.asStateFlow()

    private val _wikipediaApiEnabled = MutableStateFlow(prefs.getBoolean(KEY_WIKIPEDIA_API_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val wikipediaApiEnabled: StateFlow<Boolean> = _wikipediaApiEnabled.asStateFlow()
    
    private val _autoFetchArtwork = MutableStateFlow(prefs.getBoolean(KEY_AUTO_FETCH_ARTWORK, true))
    val autoFetchArtwork: StateFlow<Boolean> = _autoFetchArtwork.asStateFlow()

    private val _artistArtworkSource = MutableStateFlow(
        ArtistArtworkSource.fromName(prefs.getString(KEY_ARTIST_ARTWORK_SOURCE, ArtistArtworkSource.PREFER_LOCAL_THEN_API.name))
    )
    val artistArtworkSource: StateFlow<ArtistArtworkSource> = _artistArtworkSource.asStateFlow()

    private val _appleCanvasEnabled = MutableStateFlow(prefs.getBoolean(KEY_APPLE_CANVAS_ENABLED, true))
    val appleCanvasEnabled: StateFlow<Boolean> = _appleCanvasEnabled.asStateFlow()

    private val _appleCanvasNetworkMode = MutableStateFlow(
        CanvasNetworkMode.fromOrdinal(prefs.getInt(KEY_APPLE_CANVAS_NETWORK_MODE, CanvasNetworkMode.BOTH.ordinal))
    )
    val appleCanvasNetworkMode: StateFlow<CanvasNetworkMode> = _appleCanvasNetworkMode.asStateFlow()
    
    private val _spotifyClientId = MutableStateFlow(prefs.getString(KEY_SPOTIFY_CLIENT_ID, "") ?: "")
    val spotifyClientId: StateFlow<String> = _spotifyClientId.asStateFlow()
    
    private val _spotifyClientSecret = MutableStateFlow(prefs.getString(KEY_SPOTIFY_CLIENT_SECRET, "") ?: "")
    val spotifyClientSecret: StateFlow<String> = _spotifyClientSecret.asStateFlow()


    // General Broadcast Status Settings
    private val _broadcastStatusEnabled = MutableStateFlow(prefs.getBoolean(KEY_BROADCAST_STATUS_ENABLED, false))
    val broadcastStatusEnabled: StateFlow<Boolean> = _broadcastStatusEnabled.asStateFlow()

    private val _bluetoothLyricsEnabled = MutableStateFlow(prefs.getBoolean(KEY_BLUETOOTH_LYRICS_ENABLED, false))
    val bluetoothLyricsEnabled: StateFlow<Boolean> = _bluetoothLyricsEnabled.asStateFlow()

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

    private val _audioMetadataExtractionCompleted = MutableStateFlow(
        prefs.getBoolean(KEY_AUDIO_METADATA_EXTRACTION_COMPLETED, false)
    )
    val audioMetadataExtractionCompleted: StateFlow<Boolean> = _audioMetadataExtractionCompleted.asStateFlow()

    private val _embeddedArtworkExtractionCompleted = MutableStateFlow(
        prefs.getBoolean(KEY_EMBEDDED_ARTWORK_EXTRACTION_COMPLETED, false)
    )
    val embeddedArtworkExtractionCompleted: StateFlow<Boolean> = _embeddedArtworkExtractionCompleted.asStateFlow()

    private val _embeddedArtworkExtractionLosslessStatus = MutableStateFlow(
        prefs.getBoolean(KEY_EMBEDDED_ARTWORK_EXTRACTION_LOSSLESS_STATUS, false)
    )
    val embeddedArtworkExtractionLosslessStatus: StateFlow<Boolean> = _embeddedArtworkExtractionLosslessStatus.asStateFlow()

    // App Updater Settings
private val _autoCheckForUpdates = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK_FOR_UPDATES, BuildConfig.FLAVOR != "fdroid"))
    val autoCheckForUpdates: StateFlow<Boolean> = _autoCheckForUpdates.asStateFlow()
    
    private val _updateChannel = MutableStateFlow(prefs.getString(KEY_UPDATE_CHANNEL, "stable") ?: "stable")
    val updateChannel: StateFlow<String> = _updateChannel.asStateFlow()

    private val _updateSource = MutableStateFlow(prefs.getString(KEY_UPDATE_SOURCE, "installed") ?: "installed")
    val updateSource: StateFlow<String> = _updateSource.asStateFlow()
    
    private val _updatesEnabled = MutableStateFlow(prefs.getBoolean(KEY_UPDATES_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val updatesEnabled: StateFlow<Boolean> = _updatesEnabled.asStateFlow()
    
    private val _updateNotificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_UPDATE_NOTIFICATIONS_ENABLED, BuildConfig.FLAVOR != "fdroid"))
    val updateNotificationsEnabled: StateFlow<Boolean> = _updateNotificationsEnabled.asStateFlow()

    private val _updateStatusNotificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_UPDATE_STATUS_NOTIFICATIONS_ENABLED, false))
    val updateStatusNotificationsEnabled: StateFlow<Boolean> = _updateStatusNotificationsEnabled.asStateFlow()

    // Media Scan Mode
    private val _mediaScanMode = MutableStateFlow(
        MediaScanMode.fromValue(prefs.getString(KEY_MEDIA_SCAN_MODE, "blacklist") ?: "blacklist")
    )
    val mediaScanMode: StateFlow<MediaScanMode> = _mediaScanMode.asStateFlow()

    private val _includeHiddenWhitelistedMedia = MutableStateFlow(
        prefs.getBoolean(KEY_INCLUDE_HIDDEN_WHITELISTED_MEDIA, true)
    )
    val includeHiddenWhitelistedMedia: StateFlow<Boolean> = _includeHiddenWhitelistedMedia.asStateFlow()

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

    private val _libraryOperationsNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LIBRARY_OPERATIONS_NOTIFICATIONS_ENABLED, true)
    )
    val libraryOperationsNotificationsEnabled: StateFlow<Boolean> = _libraryOperationsNotificationsEnabled.asStateFlow()

    private val _sleepTimerNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_SLEEP_TIMER_NOTIFICATIONS_ENABLED, true)
    )
    val sleepTimerNotificationsEnabled: StateFlow<Boolean> = _sleepTimerNotificationsEnabled.asStateFlow()

    private val _streamingNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_STREAMING_NOTIFICATIONS_ENABLED, true)
    )
    val streamingNotificationsEnabled: StateFlow<Boolean> = _streamingNotificationsEnabled.asStateFlow()

    private val _rhythmGuardAlertNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_RHYTHM_GUARD_ALERT_NOTIFICATIONS_ENABLED, true)
    )
    val rhythmGuardAlertNotificationsEnabled: StateFlow<Boolean> = _rhythmGuardAlertNotificationsEnabled.asStateFlow()

    private val _rhythmGuardTimerNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_RHYTHM_GUARD_TIMER_NOTIFICATIONS_ENABLED, true)
    )
    val rhythmGuardTimerNotificationsEnabled: StateFlow<Boolean> = _rhythmGuardTimerNotificationsEnabled.asStateFlow()

    private val _rhythmPulseNotificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_RHYTHM_PULSE_NOTIFICATIONS_ENABLED, false)
    )
    val rhythmPulseNotificationsEnabled: StateFlow<Boolean> = _rhythmPulseNotificationsEnabled.asStateFlow()

    private val _rhythmPulseNotificationIntervalHours = MutableStateFlow(
        prefs.getInt(KEY_RHYTHM_PULSE_NOTIFICATION_INTERVAL_HOURS, 24).coerceIn(6, 72)
    )
    val rhythmPulseNotificationIntervalHours: StateFlow<Int> = _rhythmPulseNotificationIntervalHours.asStateFlow()
    
    // UI Settings
    private val _useSettings = MutableStateFlow(prefs.getBoolean(KEY_USE_SETTINGS, true))
    val useSettings: StateFlow<Boolean> = _useSettings.asStateFlow()
    
    private val _forcePlayerCompactMode = MutableStateFlow(prefs.getBoolean(KEY_FORCE_PLAYER_COMPACT_MODE, false))
    val forcePlayerCompactMode: StateFlow<Boolean> = _forcePlayerCompactMode.asStateFlow()

    private val _useExperimentalPlayerUi = MutableStateFlow(prefs.getBoolean(KEY_USE_EXPERIMENTAL_PLAYER_UI, false))
    val useExperimentalPlayerUi: StateFlow<Boolean> = _useExperimentalPlayerUi.asStateFlow()

    private val _enableAlbumEditing = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_ALBUM_EDITING, false))
    val enableAlbumEditing: StateFlow<Boolean> = _enableAlbumEditing.asStateFlow()
    
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
    private val _trackErrorCheckerEnabled = MutableStateFlow(prefs.getBoolean(KEY_TRACK_ERROR_CHECKER_ENABLED, true))
    val trackErrorCheckerEnabled: StateFlow<Boolean> = _trackErrorCheckerEnabled.asStateFlow()

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

    // Battery Saver StateFlows
    private val _batterySaverEnabled = MutableStateFlow(prefs.getBoolean(KEY_BATTERY_SAVER_ENABLED, false))
    val batterySaverEnabled: StateFlow<Boolean> = _batterySaverEnabled.asStateFlow()

    private val _batterySaverMode = MutableStateFlow(prefs.getString(KEY_BATTERY_SAVER_MODE, "auto") ?: "auto")
    val batterySaverMode: StateFlow<String> = _batterySaverMode.asStateFlow()

    private val _batterySaverDisableHaptics = MutableStateFlow(prefs.getBoolean(KEY_BATTERY_SAVER_DISABLE_HAPTICS, true))
    val batterySaverDisableHaptics: StateFlow<Boolean> = _batterySaverDisableHaptics.asStateFlow()

    private val _batterySaverEnableOffload = MutableStateFlow(prefs.getBoolean(KEY_BATTERY_SAVER_ENABLE_OFFLOAD, true))
    val batterySaverEnableOffload: StateFlow<Boolean> = _batterySaverEnableOffload.asStateFlow()

    private val _batterySaverDisableMarquee = MutableStateFlow(prefs.getBoolean(KEY_BATTERY_SAVER_DISABLE_MARQUEE, true))
    val batterySaverDisableMarquee: StateFlow<Boolean> = _batterySaverDisableMarquee.asStateFlow()

    private val _batterySaverDisableLosslessArtwork = MutableStateFlow(prefs.getBoolean(KEY_BATTERY_SAVER_DISABLE_LOSSLESS_ARTWORK, true))
    val batterySaverDisableLosslessArtwork: StateFlow<Boolean> = _batterySaverDisableLosslessArtwork.asStateFlow()

    private val _batterySaverDisableAutoFetchArtwork = MutableStateFlow(prefs.getBoolean(KEY_BATTERY_SAVER_DISABLE_AUTO_FETCH_ARTWORK, true))
    val batterySaverDisableAutoFetchArtwork: StateFlow<Boolean> = _batterySaverDisableAutoFetchArtwork.asStateFlow()

    private val _globalMarqueeEnabled = MutableStateFlow(prefs.getBoolean(KEY_GLOBAL_MARQUEE_ENABLED, true))
    val globalMarqueeEnabled: StateFlow<Boolean> = _globalMarqueeEnabled.asStateFlow()

    // Derived settings
    private val _isHapticEnabled = MutableStateFlow(calculateIsHapticEnabled())
    val isHapticEnabled: StateFlow<Boolean> = _isHapticEnabled.asStateFlow()

    private val _isAudioOffloadActive = MutableStateFlow(calculateIsAudioOffloadActive())
    val isAudioOffloadActive: StateFlow<Boolean> = _isAudioOffloadActive.asStateFlow()

    private val _isMarqueeActive = MutableStateFlow(calculateIsMarqueeActive())
    val isMarqueeActive: StateFlow<Boolean> = _isMarqueeActive.asStateFlow()

    private val _isLosslessArtworkActive = MutableStateFlow(calculateIsLosslessArtworkActive())
    val isLosslessArtworkActive: StateFlow<Boolean> = _isLosslessArtworkActive.asStateFlow()

    private val _isAutoFetchArtworkActive = MutableStateFlow(calculateIsAutoFetchArtworkActive())
    val isAutoFetchArtworkActive: StateFlow<Boolean> = _isAutoFetchArtworkActive.asStateFlow()

    private fun calculateIsHapticEnabled(): Boolean {
        val masterEnabled = _batterySaverEnabled.value
        val mode = _batterySaverMode.value
        val disableHapticsInManual = _batterySaverDisableHaptics.value
        val baseHaptics = _hapticFeedbackEnabled.value
        
        return baseHaptics && !(masterEnabled && (mode == "auto" || disableHapticsInManual))
    }

    private fun calculateIsAudioOffloadActive(): Boolean {
        val masterEnabled = _batterySaverEnabled.value
        val mode = _batterySaverMode.value
        val enableOffloadInManual = _batterySaverEnableOffload.value
        val baseOffload = _audioOffloadEnabled.value
        
        return baseOffload || (masterEnabled && (mode == "auto" || enableOffloadInManual))
    }

    private fun calculateIsMarqueeActive(): Boolean {
        val masterEnabled = _batterySaverEnabled.value
        val mode = _batterySaverMode.value
        val disableMarqueeInManual = _batterySaverDisableMarquee.value
        val baseMarquee = _globalMarqueeEnabled.value
        
        return baseMarquee && !(masterEnabled && (mode == "auto" || disableMarqueeInManual))
    }

    private fun calculateIsLosslessArtworkActive(): Boolean {
        val masterEnabled = _batterySaverEnabled.value
        val mode = _batterySaverMode.value
        val disableLosslessInManual = _batterySaverDisableLosslessArtwork.value
        val baseLossless = _losslessArtwork.value
        
        return baseLossless && !(masterEnabled && (mode == "auto" || disableLosslessInManual))
    }

    private fun calculateIsAutoFetchArtworkActive(): Boolean {
        val masterEnabled = _batterySaverEnabled.value
        val mode = _batterySaverMode.value
        val disableAutoFetchInManual = _batterySaverDisableAutoFetchArtwork.value
        val baseAutoFetch = _autoFetchArtwork.value
        
        return baseAutoFetch && !(masterEnabled && (mode == "auto" || disableAutoFetchInManual))
    }

    fun updateDerivedSettings() {
        _isHapticEnabled.value = calculateIsHapticEnabled()
        _isAudioOffloadActive.value = calculateIsAudioOffloadActive()
        _isMarqueeActive.value = calculateIsMarqueeActive()
        _isLosslessArtworkActive.value = calculateIsLosslessArtworkActive()
        _isAutoFetchArtworkActive.value = calculateIsAutoFetchArtworkActive()
    }
    
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
        prefs.getStringSet(KEY_ALLOWED_FORMATS, defaultAllowedFormats())?.toSet() ?: defaultAllowedFormats()
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
        migrateLegacyArtworkPreferenceIfNeeded()
        normalizeArtworkPreferenceStateIfNeeded()

        // Defer non-critical WorkManager scheduling to background thread
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            if (prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)) {
                scheduleAutoBackup()
            }
            if (prefs.getBoolean(KEY_UPDATES_ENABLED, false) &&
                prefs.getBoolean(KEY_AUTO_CHECK_FOR_UPDATES, false) &&
                (
                    prefs.getBoolean(KEY_UPDATE_NOTIFICATIONS_ENABLED, false) ||
                        prefs.getBoolean(KEY_UPDATE_STATUS_NOTIFICATIONS_ENABLED, false)
                    )) {
                scheduleUpdateNotificationWorker()
            }
            if (prefs.getBoolean(KEY_RHYTHM_PULSE_NOTIFICATIONS_ENABLED, false)) {
                scheduleRhythmPulseNotificationWorker()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun migrateLegacyArtworkPreferenceIfNeeded() {
        if (prefs.contains(KEY_PREFER_SONG_ARTWORK)) {
            return
        }

        if (!prefs.contains(KEY_IGNORE_MEDIASTORE_COVERS)) {
            return
        }

        val legacyValue = prefs.getBoolean(KEY_IGNORE_MEDIASTORE_COVERS, false)
        prefs.edit { putBoolean(KEY_PREFER_SONG_ARTWORK, legacyValue) }
        _preferSongArtwork.value = legacyValue
        Log.d("AppSettings", "Migrated legacy ignore_mediastore_covers to prefer_song_artwork")
    }

    private fun normalizeArtworkPreferenceStateIfNeeded() {
        if (_losslessArtwork.value && !_preferSongArtwork.value) {
            prefs.edit { putBoolean(KEY_LOSSLESS_ARTWORK, false) }
            _losslessArtwork.value = false
            Log.d("AppSettings", "Normalized artwork preferences by disabling lossless mode while song-based artwork is off")
        }
    }
    
    fun setAudioOffloadEnabled(enable: Boolean) {
        prefs.edit { putBoolean(KEY_AUDIO_OFFLOAD_ENABLED, enable) }
        _audioOffloadEnabled.value = enable
        updateDerivedSettings()
    }

    // Battery Saver Methods
    fun setBatterySaverEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BATTERY_SAVER_ENABLED, enabled) }
        _batterySaverEnabled.value = enabled
        updateDerivedSettings()
    }

    fun setBatterySaverMode(mode: String) {
        prefs.edit { putString(KEY_BATTERY_SAVER_MODE, mode) }
        _batterySaverMode.value = mode
        updateDerivedSettings()
    }

    fun setBatterySaverDisableHaptics(disable: Boolean) {
        prefs.edit { putBoolean(KEY_BATTERY_SAVER_DISABLE_HAPTICS, disable) }
        _batterySaverDisableHaptics.value = disable
        updateDerivedSettings()
    }

    fun setBatterySaverEnableOffload(enable: Boolean) {
        prefs.edit { putBoolean(KEY_BATTERY_SAVER_ENABLE_OFFLOAD, enable) }
        _batterySaverEnableOffload.value = enable
        updateDerivedSettings()
    }

    fun setBatterySaverDisableMarquee(disable: Boolean) {
        prefs.edit { putBoolean(KEY_BATTERY_SAVER_DISABLE_MARQUEE, disable) }
        _batterySaverDisableMarquee.value = disable
        updateDerivedSettings()
    }

    fun setBatterySaverDisableLosslessArtwork(disable: Boolean) {
        prefs.edit { putBoolean(KEY_BATTERY_SAVER_DISABLE_LOSSLESS_ARTWORK, disable) }
        _batterySaverDisableLosslessArtwork.value = disable
        updateDerivedSettings()
    }

    fun setBatterySaverDisableAutoFetchArtwork(disable: Boolean) {
        prefs.edit { putBoolean(KEY_BATTERY_SAVER_DISABLE_AUTO_FETCH_ARTWORK, disable) }
        _batterySaverDisableAutoFetchArtwork.value = disable
        updateDerivedSettings()
    }

    fun setGlobalMarqueeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_GLOBAL_MARQUEE_ENABLED, enabled) }
        _globalMarqueeEnabled.value = enabled
        updateDerivedSettings()
    }
    
    fun setPreloadLimit(limit: Int) {
        prefs.edit { putInt(KEY_PRELOAD_LIMIT, limit) }
        _preloadLimit.value = limit
    }


    fun setGaplessPlayback(enable: Boolean) {
        prefs.edit { putBoolean(KEY_GAPLESS_PLAYBACK, enable) }
        _gaplessPlayback.value = enable
    }
    
    fun setResumeOnDeviceReconnect(enable: Boolean) {
        prefs.edit { putBoolean(KEY_RESUME_ON_DEVICE_RECONNECT, enable) }
        _resumeOnDeviceReconnect.value = enable
    }
    
    fun setCrossfade(enable: Boolean) {
        prefs.edit { putBoolean(KEY_CROSSFADE, enable) }
        _crossfade.value = enable
    }
    
    fun setCrossfadeDuration(duration: Float) {
        if (isValidCrossfadeDuration(duration)) {
            prefs.edit { putFloat(KEY_CROSSFADE_DURATION, duration) }
            _crossfadeDuration.value = duration
        } else {
            Log.w("AppSettings", "Invalid crossfade duration: $duration, keeping current value")
        }
    }

    fun setCrossfadeRepeatOne(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CROSSFADE_REPEAT_ONE, enabled) }
        _crossfadeRepeatOne.value = enabled
    }
    
    fun setCrossfadeOnSkip(enable: Boolean) {
        prefs.edit { putBoolean(KEY_CROSSFADE_ON_SKIP, enable) }
        _crossfadeOnSkip.value = enable
    }
    
    fun setSkipSilenceEnabled(enable: Boolean) {
        prefs.edit { putBoolean(KEY_SKIP_SILENCE, enable) }
        _skipSilenceEnabled.value = enable
        Log.d("AppSettings", "Skip silence ${if (enable) "enabled" else "disabled"}")
    }
    
    fun setAudioRoutingMode(mode: String) {
        require(mode in listOf("default", "app", "system")) { "Invalid audio routing mode: $mode" }
        prefs.edit { putString(KEY_AUDIO_ROUTING_MODE, mode) }
        _audioRoutingMode.value = mode
        Log.d("AppSettings", "Audio routing mode set to: $mode")
    }
    
    fun setAudioNormalization(enable: Boolean) {
        prefs.edit { putBoolean(KEY_AUDIO_NORMALIZATION, enable) }
        _audioNormalization.value = enable
    }
    
    fun setReplayGain(enable: Boolean) {
        prefs.edit { putBoolean(KEY_REPLAY_GAIN, enable) }
        _replayGain.value = enable
    }

    fun setReplayGainMode(mode: Int) {
        prefs.edit { putInt(KEY_REPLAY_GAIN_MODE, mode) }
        _replayGainMode.value = mode
    }

    fun setReplayGainDrc(enable: Boolean) {
        prefs.edit { putBoolean(KEY_REPLAY_GAIN_DRC, enable) }
        _replayGainDrc.value = enable
    }

    fun setReplayGainPreamp(preamp: Float) {
        prefs.edit { putFloat(KEY_REPLAY_GAIN_PREAMP, preamp) }
        _replayGainPreamp.value = preamp
    }

    fun setReplayGainPreampUntagged(preamp: Float) {
        prefs.edit { putFloat(KEY_REPLAY_GAIN_PREAMP_UNTAGGED, preamp) }
        _replayGainPreampUntagged.value = preamp
    }
    
    // Lyrics Settings Methods
    fun setShowLyrics(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_LYRICS, show) }
        _showLyrics.value = show
    }
    
    fun setLyricsSourcePreference(preference: LyricsSourcePreference) {
        prefs.edit { putInt(KEY_LYRICS_SOURCE_PREFERENCE, preference.ordinal) }
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
    
    fun setShowLyricsBackgroundArtwork(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_LYRICS_BACKGROUND_ARTWORK, show) }
        _showLyricsBackgroundArtwork.value = show
    }

    fun setShowLyricsTranslation(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_LYRICS_TRANSLATION, show) }
        _showLyricsTranslation.value = show
    }
    
    fun setShowLyricsRomanization(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_LYRICS_ROMANIZATION, show) }
        _showLyricsRomanization.value = show
    }
    
    fun setKeepScreenOnLyrics(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON_LYRICS, enabled) }
        _keepScreenOnLyrics.value = enabled
    }
    
    fun setTapLyricsToFullScreen(enable: Boolean) {
        prefs.edit { putBoolean(KEY_TAP_LYRICS_TO_FULL_SCREEN, enable) }
        _tapLyricsToFullScreen.value = enable
    }

    fun setLyricsApiPriority(priority: LyricsApiPriority) {
        prefs.edit { putInt(KEY_LYRICS_API_PRIORITY, priority.ordinal) }
        _lyricsApiPriority.value = priority
    }
    
    fun isLyricsApiPrioritySet(): Boolean {
        return prefs.contains(KEY_LYRICS_API_PRIORITY)
    }

    fun setLyricsApiFallbackRetry(enable: Boolean) {
        prefs.edit { putBoolean(KEY_LYRICS_API_FALLBACK_RETRY, enable) }
        _lyricsApiFallbackRetry.value = enable
    }

    fun setAutoHideLyricsControls(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_HIDE_LYRICS_CONTROLS, enabled) }
        _autoHideLyricsControls.value = enabled
    }

    fun setLyricBold(enable: Boolean) {
        prefs.edit { putBoolean(KEY_LYRIC_BOLD, enable) }
        _lyricBold.value = enable
    }

    fun setTrimLyrics(enable: Boolean) {
        prefs.edit { putBoolean(KEY_TRIM_LYRICS, enable) }
        _trimLyrics.value = enable
    }

    fun setLyricNoAnimation(enable: Boolean) {
        prefs.edit { putBoolean(KEY_LYRIC_NO_ANIMATION, enable) }
        _lyricNoAnimation.value = enable
    }

    fun setTranslationAutoWord(enable: Boolean) {
        prefs.edit { putBoolean(KEY_TRANSLATION_AUTO_WORD, enable) }
        _translationAutoWord.value = enable
    }
    
    // Theme Settings Methods
    fun setUseSystemTheme(use: Boolean) {
        prefs.edit { putBoolean(KEY_USE_SYSTEM_THEME, use) }
        _useSystemTheme.value = use
    }
    
    fun setDarkMode(dark: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_MODE, dark) }
        _darkMode.value = dark
    }
    
    fun setUseDynamicColors(use: Boolean) {
        prefs.edit { putBoolean(KEY_USE_DYNAMIC_COLORS, use) }
        _useDynamicColors.value = use
    }
    
    fun setAmoledTheme(amoled: Boolean) {
        prefs.edit { putBoolean(KEY_AMOLED_THEME, amoled) }
        _amoledTheme.value = amoled
    }

    fun setFloatingNavigationBar(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FLOATING_NAVIGATION_BAR, enabled) }
        _floatingNavigationBar.value = enabled
    }
    
    // Artist Separator Settings Methods
    fun setArtistSeparatorEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ARTIST_SEPARATOR_ENABLED, enabled) }
        _artistSeparatorEnabled.value = enabled
    }
    
    fun setArtistSeparatorDelimiters(delimiters: String) {
        val sanitized = delimiters.trim().ifEmpty { DEFAULT_ARTIST_SEPARATOR_DELIMITERS }
        prefs.edit { putString(KEY_ARTIST_SEPARATOR_DELIMITERS, sanitized) }
        _artistSeparatorDelimiters.value = sanitized
    }

    fun setArtistSeparatorCustomDelimiters(tokens: List<String>) {
        val clean = tokens.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val json = if (clean.isEmpty()) "" else ArtistSeparator.serializeDelimiters(clean)
        prefs.edit {
            if (json.isEmpty()) {
                remove(KEY_ARTIST_SEPARATOR_CUSTOM_DELIMITERS)
            } else {
                putString(KEY_ARTIST_SEPARATOR_CUSTOM_DELIMITERS, json)
            }
        }
        _artistSeparatorCustomDelimiters.value = clean
    }

    fun getArtistSeparatorCacheSignature(): String? {
        return prefs.getString(KEY_ARTIST_SEPARATOR_CACHE_SIGNATURE, null)
    }

    fun setArtistSeparatorCacheSignature(signature: String?) {
        prefs.edit {
            if (signature.isNullOrBlank()) {
                remove(KEY_ARTIST_SEPARATOR_CACHE_SIGNATURE)
            } else {
                putString(KEY_ARTIST_SEPARATOR_CACHE_SIGNATURE, signature)
            }
        }
    }
    
    fun setCustomColorScheme(scheme: String) {
        prefs.edit { putString(KEY_CUSTOM_COLOR_SCHEME, scheme) }
        _customColorScheme.value = scheme
    }
    
    fun setCustomFont(font: String) {
        prefs.edit { putString(KEY_CUSTOM_FONT, font) }
        _customFont.value = font
    }
    
    fun setColorSource(source: String) {
        prefs.edit { putString(KEY_COLOR_SOURCE, source) }
        _colorSource.value = source
    }
    
    fun setExtractedAlbumColors(colorsJson: String?) {
        prefs.edit { putString(KEY_EXTRACTED_ALBUM_COLORS, colorsJson) }
        _extractedAlbumColors.value = colorsJson
    }
    
    fun setFontSource(source: String) {
        prefs.edit { putString(KEY_FONT_SOURCE, source) }
        _fontSource.value = source
    }
    
    fun setCustomFontPath(path: String?) {
        prefs.edit { putString(KEY_CUSTOM_FONT_PATH, path) }
        _customFontPath.value = path
    }
    
    fun setCustomFontFamily(family: String) {
        prefs.edit { putString(KEY_CUSTOM_FONT_FAMILY, family) }
        _customFontFamily.value = family
    }
    
    // Player Theme Settings Methods
    fun setPlayerThemeId(themeId: String) {
        prefs.edit { putString(KEY_PLAYER_THEME_ID, themeId) }
        _playerThemeId.value = themeId
    }
    
    fun setMiniPlayerThemeId(themeId: String) {
        prefs.edit { putString(KEY_MINI_PLAYER_THEME_ID, themeId) }
        _miniPlayerThemeId.value = themeId
    }
    
    // Library Settings Methods
    fun setAlbumViewType(viewType: AlbumViewType) {
        prefs.edit { putString(KEY_ALBUM_VIEW_TYPE, viewType.name) }
        _albumViewType.value = viewType
    }
    
    fun setArtistViewType(viewType: ArtistViewType) {
        prefs.edit { putString(KEY_ARTIST_VIEW_TYPE, viewType.name) }
        _artistViewType.value = viewType
    }
    
    fun setPlaylistViewType(viewType: PlaylistViewType) {
        prefs.edit { putString(KEY_PLAYLIST_VIEW_TYPE, viewType.name) }
        _playlistViewType.value = viewType
    }
    
    fun setAlbumSortOrder(sortOrder: String) {
        prefs.edit { putString(KEY_ALBUM_SORT_ORDER, sortOrder) }
        _albumSortOrder.value = sortOrder
    }
    
    fun setPlaylistSortOrder(sortOrder: String) {
        prefs.edit { putString(KEY_PLAYLIST_SORT_ORDER, sortOrder) }
        _playlistSortOrder.value = sortOrder
    }
    
    fun setPlaylistDetailSortOrder(sortOrder: String) {
        prefs.edit { putString(KEY_PLAYLIST_DETAIL_SORT_ORDER, sortOrder) }
        _playlistDetailSortOrder.value = sortOrder
    }
    
    fun setArtistCollaborationMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ARTIST_COLLABORATION_MODE, enabled) }
        _artistCollaborationMode.value = enabled
    }
    
    fun setLibraryTabOrder(tabOrder: List<String>) {
        val orderString = tabOrder.joinToString(",")
        prefs.edit { putString(KEY_LIBRARY_TAB_ORDER, orderString) }
        _libraryTabOrder.value = tabOrder
    }
    
    fun resetLibraryTabOrder() {
        prefs.edit { remove(KEY_LIBRARY_TAB_ORDER) }
        _libraryTabOrder.value = defaultTabOrder
    }

    fun setLibraryCombineDiscs(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LIBRARY_COMBINE_DISCS, enabled) }
        _libraryCombineDiscs.value = enabled
    }
    
    fun setPlayerChipOrder(chipOrder: List<String>) {
        val sanitizedOrder = chipOrder.filter { it in defaultChipOrder }
        val orderString = sanitizedOrder.joinToString(",")
        prefs.edit { putString(KEY_PLAYER_CHIP_ORDER, orderString) }
        _playerChipOrder.value = sanitizedOrder
    }
    
    fun resetPlayerChipOrder() {
        prefs.edit { remove(KEY_PLAYER_CHIP_ORDER) }
        _playerChipOrder.value = defaultChipOrder
    }
    
    fun setDefaultScreen(screen: String) {
        prefs.edit { putString(KEY_DEFAULT_SCREEN, screen) }
        _defaultScreen.value = screen
    }
    
    fun setHiddenLibraryTabs(hiddenTabs: Set<String>) {
        val hiddenString = hiddenTabs.joinToString(",")
        prefs.edit { putString(KEY_HIDDEN_LIBRARY_TABS, hiddenString) }
        _hiddenLibraryTabs.value = hiddenTabs
    }
    
    fun setHiddenPlayerChips(hiddenChips: Set<String>) {
        val sanitizedHiddenChips = hiddenChips.filter { it in defaultChipOrder }.toSet()
        val hiddenString = sanitizedHiddenChips.joinToString(",")
        prefs.edit { putString(KEY_HIDDEN_PLAYER_CHIPS, hiddenString) }
        _hiddenPlayerChips.value = sanitizedHiddenChips
    }

    fun setExpressiveBottomButtonsNormal(order: List<String>) {
        val sanitized = order.filter { it in allExpressiveBottomButtons }
        val orderString = sanitized.joinToString(",")
        prefs.edit { putString(KEY_EXPRESSIVE_BOTTOM_BUTTONS_NORMAL, orderString) }
        _expressiveBottomButtonsNormal.value = sanitized
    }

    fun setExpressiveHiddenBottomButtonsNormal(hidden: Set<String>) {
        val sanitized = hidden.filter { it in allExpressiveBottomButtons }.toSet()
        val hiddenString = sanitized.joinToString(",")
        prefs.edit { putString(KEY_EXPRESSIVE_HIDDEN_BOTTOM_BUTTONS_NORMAL, hiddenString) }
        _expressiveHiddenBottomButtonsNormal.value = sanitized
    }

    fun resetExpressiveBottomButtonsNormal() {
        prefs.edit {
            remove(KEY_EXPRESSIVE_BOTTOM_BUTTONS_NORMAL)
            remove(KEY_EXPRESSIVE_HIDDEN_BOTTOM_BUTTONS_NORMAL)
        }
        _expressiveBottomButtonsNormal.value = defaultExpressiveBottomButtonsNormal
        _expressiveHiddenBottomButtonsNormal.value = emptySet()
    }

    fun setExpressiveBottomButtonsMerge(order: List<String>) {
        val sanitized = order.filter { it in allExpressiveBottomButtons }
        val orderString = sanitized.joinToString(",")
        prefs.edit { putString(KEY_EXPRESSIVE_BOTTOM_BUTTONS_MERGE, orderString) }
        _expressiveBottomButtonsMerge.value = sanitized
    }

    fun setExpressiveHiddenBottomButtonsMerge(hidden: Set<String>) {
        val sanitized = hidden.filter { it in allExpressiveBottomButtons }.toSet()
        val hiddenString = sanitized.joinToString(",")
        prefs.edit { putString(KEY_EXPRESSIVE_HIDDEN_BOTTOM_BUTTONS_MERGE, hiddenString) }
        _expressiveHiddenBottomButtonsMerge.value = sanitized
    }

    fun resetExpressiveBottomButtonsMerge() {
        prefs.edit {
            remove(KEY_EXPRESSIVE_BOTTOM_BUTTONS_MERGE)
            remove(KEY_EXPRESSIVE_HIDDEN_BOTTOM_BUTTONS_MERGE)
        }
        _expressiveBottomButtonsMerge.value = defaultExpressiveBottomButtonsMerge
        _expressiveHiddenBottomButtonsMerge.value = emptySet()
    }

    fun setLyricallySourcesOrder(order: List<String>) {
        val sanitized = order.filter { it in defaultLyricallySources }
        val orderString = sanitized.joinToString(",")
        prefs.edit { putString(KEY_LYRICALLY_SOURCES_ORDER, orderString) }
        _lyricallySourcesOrder.value = sanitized
    }

    fun resetLyricallySourcesOrder() {
        prefs.edit { remove(KEY_LYRICALLY_SOURCES_ORDER) }
        _lyricallySourcesOrder.value = defaultLyricallySources
    }

    fun setDisabledLyricallySources(disabled: Set<String>) {
        val sanitized = disabled.filter { it in defaultLyricallySources }.toSet()
        val disabledString = sanitized.joinToString(",")
        prefs.edit { putString(KEY_DISABLED_LYRICALLY_SOURCES, disabledString) }
        _disabledLyricallySources.value = sanitized
    }
    
    fun setGroupByAlbumArtist(enable: Boolean) {
        prefs.edit { putBoolean(KEY_GROUP_BY_ALBUM_ARTIST, false) }
        _groupByAlbumArtist.value = false
    }

    fun setPreferSongArtwork(enabled: Boolean) {
        val changed = _preferSongArtwork.value != enabled
        val disableLossless = !enabled && _losslessArtwork.value
        prefs.edit {
    putBoolean(KEY_PREFER_SONG_ARTWORK, enabled)
    putBoolean(KEY_IGNORE_MEDIASTORE_COVERS, enabled)
    putBoolean(KEY_LOSSLESS_ARTWORK, if (disableLossless) false else _losslessArtwork.value)
}
        _preferSongArtwork.value = enabled
        if (disableLossless) {
            _losslessArtwork.value = false
        }

        if (changed || disableLossless) {
            val reason = when {
                enabled -> "prefer_song_artwork_enabled"
                disableLossless -> "prefer_song_artwork_disabled_and_lossless_reset"
                else -> "prefer_song_artwork_disabled"
            }
            requestFullMediaRescanOnNextLaunch(reason = reason)
        }
    }

    @Deprecated("Use setPreferSongArtwork")
    fun setIgnoreMediaStoreCovers(enabled: Boolean) {
        setPreferSongArtwork(enabled)
    }

    fun setLosslessArtwork(enabled: Boolean) {
        val changed = _losslessArtwork.value != enabled
        prefs.edit { putBoolean(KEY_LOSSLESS_ARTWORK, enabled) }
        _losslessArtwork.value = enabled
        updateDerivedSettings()

        // Lossless artwork requires per-song artwork mode to take effect.
        if (enabled && !_preferSongArtwork.value) {
            setPreferSongArtwork(true)
        } else if (changed) {
            val reason = if (enabled) {
                "lossless_artwork_enabled"
            } else {
                "lossless_artwork_disabled"
            }
            requestFullMediaRescanOnNextLaunch(reason = reason)
        }
    }

    fun setShowLibrarySectionHeaders(enable: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_LIBRARY_SECTION_HEADERS, enable) }
        _showLibrarySectionHeaders.value = enable
    }

    fun setShowLibraryBottomBarAlways(enable: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_LIBRARY_BOTTOM_BAR_ALWAYS, enable) }
        _showLibraryBottomBarAlways.value = enable
    }

    fun requestFullMediaRescanOnNextLaunch(reason: String = "unspecified") {
        prefs.edit {
    putBoolean(KEY_PENDING_FULL_MEDIA_RESCAN, true)
    putBoolean(KEY_INITIAL_MEDIA_SCAN_COMPLETED, false)
    putBoolean(KEY_GENRE_DETECTION_COMPLETED, false)
    putBoolean(KEY_EMBEDDED_ARTWORK_EXTRACTION_COMPLETED, false)
    putBoolean(KEY_EMBEDDED_ARTWORK_EXTRACTION_LOSSLESS_STATUS, false)
    putLong(KEY_LAST_SCAN_TIMESTAMP, 0L)
    putLong(KEY_LAST_SCAN_DURATION, 0L)
}

        _initialMediaScanCompleted.value = false
        _genreDetectionCompleted.value = false
        _audioMetadataExtractionCompleted.value = false
        _embeddedArtworkExtractionCompleted.value = false
        _embeddedArtworkExtractionLosslessStatus.value = false
        _lastScanTimestamp.value = 0L
        _lastScanDuration.value = 0L

        Log.i("AppSettings", "Full media rescan scheduled for next launch (reason=$reason)")
    }

    fun consumePendingFullMediaRescanRequest(): Boolean {
        val pending = prefs.getBoolean(KEY_PENDING_FULL_MEDIA_RESCAN, false)
        if (pending) {
            prefs.edit { putBoolean(KEY_PENDING_FULL_MEDIA_RESCAN, false) }
            Log.i("AppSettings", "Consuming pending full media rescan request")
        }
        return pending
    }

    /**
     * Self-heal guard gate: allows the one-time embedded artwork re-extraction
     * at most once per 24h, so a wiped artwork cache self-heals on the next
     * launch without ever turning into a per-launch extraction loop.
     */
    fun shouldRunEmbeddedArtSelfHeal(): Boolean {
        val lastRun = prefs.getLong(KEY_LAST_EMBEDDED_ART_SELF_HEAL_MS, 0L)
        return System.currentTimeMillis() - lastRun >= 24L * 60 * 60 * 1000
    }

    fun markEmbeddedArtSelfHealDone() {
        prefs.edit { putLong(KEY_LAST_EMBEDDED_ART_SELF_HEAL_MS, System.currentTimeMillis()) }
        Log.i("AppSettings", "Marked embedded artwork self-heal as done")
    }
    
    fun setShowAlphabetBar(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_ALPHABET_BAR, show) }
        _showAlphabetBar.value = show
    }
    
    fun setShowScrollToTop(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_SCROLL_TO_TOP, show) }
        _showScrollToTop.value = show
    }
    
    // App Mode setter methods
    fun setAppMode(mode: String) {
        prefs.edit { putString(KEY_APP_MODE, mode) }
        _appMode.value = mode
    }
    
    fun setStreamingService(service: String) {
        prefs.edit { putString(KEY_STREAMING_SERVICE, service) }
        _streamingService.value = service
    }
    
    fun setStreamingQuality(quality: String) {
        prefs.edit { putString(KEY_STREAMING_QUALITY, quality) }
        _streamingQuality.value = quality
    }
    
    fun setAllowCellularStreaming(allow: Boolean) {
        prefs.edit { putBoolean(KEY_ALLOW_CELLULAR_STREAMING, allow) }
        _allowCellularStreaming.value = allow
    }
    
    fun setOfflineMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_OFFLINE_MODE, enabled) }
        _offlineMode.value = enabled
    }

    fun setRememberStreamingPasswords(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_REMEMBER_STREAMING_PASSWORDS, enabled) }
        _rememberStreamingPasswords.value = enabled
    }
    
    fun setSongsSortOrder(sortOrder: String) {
        prefs.edit { putString(KEY_SONGS_SORT_ORDER, sortOrder) }
        _songsSortOrder.value = sortOrder
    }
    
    // Audio Device Settings Methods
    fun setLastAudioDevice(deviceId: String?) {
        if (deviceId == null) {
            prefs.edit { remove(KEY_LAST_AUDIO_DEVICE) }
        } else {
            prefs.edit { putString(KEY_LAST_AUDIO_DEVICE, deviceId) }
        }
        _lastAudioDevice.value = deviceId
    }
    
    fun setAutoConnectDevice(enable: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_CONNECT_DEVICE, enable) }
        _autoConnectDevice.value = enable
    }
    
    fun setUseSystemVolume(enable: Boolean) {
        prefs.edit { putBoolean(KEY_USE_SYSTEM_VOLUME, enable) }
        _useSystemVolume.value = enable
    }
    
    fun setAppVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        prefs.edit { putFloat(KEY_APP_VOLUME, clamped) }
        _appVolume.value = clamped
    }
    
    fun setStopPlaybackOnZeroVolume(enable: Boolean) {
        prefs.edit { putBoolean(KEY_STOP_PLAYBACK_ON_ZERO_VOLUME, enable) }
        _stopPlaybackOnZeroVolume.value = enable
    }
    
    // Equalizer Settings Methods
    fun setEqualizerEnabled(enable: Boolean) {
        prefs.edit { putBoolean(KEY_EQUALIZER_ENABLED, enable) }
        _equalizerEnabled.value = enable
    }
    
    fun setEqualizerPreset(preset: String) {
        prefs.edit { putString(KEY_EQUALIZER_PRESET, preset) }
        _equalizerPreset.value = preset
    }
    
    fun setEqualizerBandLevels(levels: String) {
        prefs.edit { putString(KEY_EQUALIZER_BAND_LEVELS, levels) }
        _equalizerBandLevels.value = levels
    }
    
    fun setAutoEQProfile(profileName: String) {
        prefs.edit { putString(KEY_AUTOEQ_PROFILE, profileName) }
        _autoEQProfile.value = profileName
    }
    
    fun setUserAudioDevices(devicesJson: String?) {
        prefs.edit { putString(KEY_USER_AUDIO_DEVICES, devicesJson) }
        _userAudioDevices.value = devicesJson
    }
    
    fun setActiveAudioDeviceId(deviceId: String?) {
        prefs.edit { putString(KEY_ACTIVE_AUDIO_DEVICE_ID, deviceId) }
        _activeAudioDeviceId.value = deviceId
    }
    
    fun setDismissedAutoEQSuggestions(dismissedDevices: String?) {
        prefs.edit { putString(KEY_DISMISSED_AUTOEQ_SUGGESTIONS, dismissedDevices) }
        _dismissedAutoEQSuggestions.value = dismissedDevices
    }
    
    fun setBassBoostEnabled(enable: Boolean) {
        prefs.edit { putBoolean(KEY_BASS_BOOST_ENABLED, enable) }
        _bassBoostEnabled.value = enable
    }
    
    fun setBassBoostStrength(strength: Int) {
        prefs.edit { putInt(KEY_BASS_BOOST_STRENGTH, strength) }
        _bassBoostStrength.value = strength
    }
    
    fun setBassBoostAvailable(available: Boolean) {
        prefs.edit { putBoolean(KEY_BASS_BOOST_AVAILABLE, available) }
    }
    
    fun isBassBoostAvailable(): Boolean {
        return prefs.getBoolean(KEY_BASS_BOOST_AVAILABLE, true)
    }
    
    fun setVirtualizerEnabled(enable: Boolean) {
        prefs.edit { putBoolean(KEY_VIRTUALIZER_ENABLED, enable) }
        _virtualizerEnabled.value = enable
    }
    
    fun setVirtualizerStrength(strength: Int) {
        prefs.edit { putInt(KEY_VIRTUALIZER_STRENGTH, strength) }
        _virtualizerStrength.value = strength
    }
    
    fun setMonoAudioEnabled(enable: Boolean) {
        prefs.edit { putBoolean(KEY_MONO_AUDIO_ENABLED, enable) }
        _monoAudioEnabled.value = enable
    }
    
    // Sleep Timer Methods
    fun setSleepTimerActive(active: Boolean) {
        prefs.edit { putBoolean(KEY_SLEEP_TIMER_ACTIVE, active) }
        _sleepTimerActive.value = active
    }
    
    fun setSleepTimerRemainingSeconds(seconds: Long) {
        prefs.edit { putLong(KEY_SLEEP_TIMER_REMAINING_SECONDS, seconds) }
        _sleepTimerRemainingSeconds.value = seconds
    }
    
    fun setSleepTimerAction(action: String) {
        prefs.edit { putString(KEY_SLEEP_TIMER_ACTION, action) }
        _sleepTimerAction.value = action
    }
    
    // Queue & Playback Behavior Methods
    fun setShuffleUsesExoplayer(useExoplayer: Boolean) {
        prefs.edit { putBoolean(KEY_SHUFFLE_USES_EXOPLAYER, useExoplayer) }
        _shuffleUsesExoplayer.value = useExoplayer
    }
    
    fun setAutoAddToQueue(autoAdd: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_ADD_TO_QUEUE, autoAdd) }
        _autoAddToQueue.value = autoAdd
    }
    
    fun setRespectAlbumOnPlay(respect: Boolean) {
        prefs.edit { putBoolean(KEY_RESPECT_ALBUM_ON_PLAY, respect) }
        _respectAlbumOnPlay.value = respect
    }
    
    fun setClearQueueOnNewSong(clearQueue: Boolean) {
        prefs.edit { putBoolean(KEY_CLEAR_QUEUE_ON_NEW_SONG, clearQueue) }
        _clearQueueOnNewSong.value = clearQueue
    }

    fun setHidePlayedSongsInQueue(hidePlayedSongs: Boolean) {
        prefs.edit {
    putBoolean(KEY_HIDE_PLAYED_SONGS_IN_QUEUE, hidePlayedSongs)
    putBoolean(KEY_HIDE_PLAYED_QUEUE_SONGS, hidePlayedSongs)
}
        _hidePlayedSongsInQueue.value = hidePlayedSongs
        _hidePlayedQueueSongs.value = hidePlayedSongs
    }
    
    fun setShowQueueDialog(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_QUEUE_DIALOG, show) }
        _showQueueDialog.value = show
    }

    fun setListQueueActionBehavior(behavior: String) {
        if (behavior in listOf("replace", "ask", "play_next", "add_to_end")) {
            prefs.edit { putString(KEY_LIST_QUEUE_ACTION_BEHAVIOR, behavior) }
            _listQueueActionBehavior.value = behavior
        }
    }
    
    fun setHidePlayedQueueSongs(hide: Boolean) {
        prefs.edit {
    putBoolean(KEY_HIDE_PLAYED_QUEUE_SONGS, hide)
    putBoolean(KEY_HIDE_PLAYED_SONGS_IN_QUEUE, hide)
}
        _hidePlayedQueueSongs.value = hide
        _hidePlayedSongsInQueue.value = hide
    }
    
    fun setRepeatModePersistence(persist: Boolean) {
        prefs.edit { putBoolean(KEY_REPEAT_MODE_PERSISTENCE, persist) }
        _repeatModePersistence.value = persist
    }
    
    fun setShuffleModePersistence(persist: Boolean) {
        prefs.edit { putBoolean(KEY_SHUFFLE_MODE_PERSISTENCE, persist) }
        _shuffleModePersistence.value = persist
    }
    
    fun setKeepShuffleOnSelection(keep: Boolean) {
        prefs.edit { putBoolean(KEY_KEEP_SHUFFLE_ON_SELECTION, keep) }
        _keepShuffleOnSelection.value = keep
    }
    
    fun setSavedShuffleState(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SAVED_SHUFFLE_STATE, enabled) }
        _savedShuffleState.value = enabled
    }
    
    fun setSavedRepeatMode(mode: Int) {
        prefs.edit { putInt(KEY_SAVED_REPEAT_MODE, mode) }
        _savedRepeatMode.value = mode
    }
    
    fun setPlaybackSpeed(speed: Float) {
        prefs.edit { putFloat(KEY_PLAYBACK_SPEED, speed) }
        _playbackSpeed.value = speed
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        prefs.edit { putFloat(KEY_DEFAULT_PLAYBACK_SPEED, speed) }
        _defaultPlaybackSpeed.value = speed
    }

    fun setUseDefaultPlaybackSpeed(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_DEFAULT_PLAYBACK_SPEED, enabled) }
        _useDefaultPlaybackSpeed.value = enabled
    }

    fun setPlaybackPitch(pitch: Float) {
        prefs.edit { putFloat(KEY_PLAYBACK_PITCH, pitch) }
        _playbackPitch.value = pitch
    }

    fun setSyncSpeedAndPitch(sync: Boolean) {
        prefs.edit { putBoolean(KEY_SYNC_SPEED_AND_PITCH, sync) }
        _syncSpeedAndPitch.value = sync
    }
    
    // Time Format Settings Methods
    fun setUseHoursInTimeFormat(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_HOURS_IN_TIME_FORMAT, enabled) }
        _useHoursInTimeFormat.value = enabled
    }
    
    fun setShowRemainingTime(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_REMAINING_TIME, enabled) }
        _showRemainingTime.value = enabled
    }
    
    // Exact extracted colors from artwork settings methods
    fun setUseExactArtworkColors(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_EXACT_ARTWORK_COLORS, enabled) }
        _useExactArtworkColors.value = enabled
    }
    
    // Stop Playback on App Close Methods
    fun setStopPlaybackOnAppClose(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_STOP_PLAYBACK_ON_APP_CLOSE, enabled) }
        _stopPlaybackOnAppClose.value = enabled
    }
    
    // Queue Persistence Methods
    fun setQueuePersistenceEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_QUEUE_PERSISTENCE_ENABLED, enabled) }
        _queuePersistenceEnabled.value = enabled
        
        // Clear saved queue if persistence is disabled
        if (!enabled) {
            clearSavedQueue()
        }
    }
    
    fun setSavedQueue(songIds: List<String>) {
        val json = Gson().toJson(songIds)
        prefs.edit { putString(KEY_SAVED_QUEUE, json) }
        _savedQueue.value = songIds
    }
    
    fun setSavedQueueIndex(index: Int) {
        prefs.edit { putInt(KEY_SAVED_QUEUE_INDEX, index) }
        _savedQueueIndex.value = index
    }
    
    fun setSavedPlaybackPosition(position: Long) {
        prefs.edit { putLong(KEY_SAVED_PLAYBACK_POSITION, position) }
        _savedPlaybackPosition.value = position
    }
    
    fun clearSavedQueue() {
        prefs.edit { remove(KEY_SAVED_QUEUE) }
        prefs.edit { remove(KEY_SAVED_QUEUE_INDEX) }
        prefs.edit { remove(KEY_SAVED_PLAYBACK_POSITION) }
        _savedQueue.value = emptyList()
        _savedQueueIndex.value = -1
        _savedPlaybackPosition.value = 0L
    }

    // Context queue settings
    fun setContextQueuePreference(pref: String) {
        prefs.edit { putString(KEY_CONTEXT_QUEUE_PREFERENCE, pref) }
        _contextQueuePreference.value = pref
    }

    fun setContextQueuePersistence(value: String) {
        prefs.edit { putString(KEY_CONTEXT_QUEUE_PERSISTENCE, value) }
        _contextQueuePersistence.value = value
    }

    fun setContextQueueSize(size: Int) {
        val safeSize = size.coerceAtLeast(1)
        prefs.edit { putInt(KEY_CONTEXT_QUEUE_SIZE, safeSize) }
        _contextQueueSize.value = safeSize
    }
    
    // Cache Settings Methods
    fun setMaxCacheSize(size: Long) {
        if (isValidCacheSize(size)) {
            prefs.edit { putLong(KEY_MAX_CACHE_SIZE, size) }
            _maxCacheSize.value = size
        } else {
            Log.w("AppSettings", "Invalid cache size: $size, keeping current value")
        }
    }
    
    // Search History Methods
    fun setSearchHistory(history: String?) {
        if (history == null) {
            prefs.edit { remove(KEY_SEARCH_HISTORY) }
        } else {
            prefs.edit { putString(KEY_SEARCH_HISTORY, history) }
        }
        _searchHistory.value = history
    }

    fun setShowKeyboardOnSearchOpen(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_KEYBOARD_ON_SEARCH_OPEN, enabled) }
        _showKeyboardOnSearchOpen.value = enabled
    }

    // Playlists
    fun setPlaylists(playlistsJson: String?) {
        if (playlistsJson == null) {
            prefs.edit { remove(KEY_PLAYLISTS) }
        } else {
            prefs.edit { putString(KEY_PLAYLISTS, playlistsJson) }
        }
        _playlists.value = playlistsJson
    }

    fun setFavoriteSongs(favoriteSongsJson: String?) {
        if (favoriteSongsJson == null) {
            prefs.edit { remove(KEY_FAVORITE_SONGS) } // Use apply() to prevent ANR
        } else {
            prefs.edit { putString(KEY_FAVORITE_SONGS, favoriteSongsJson) } // Use apply() to prevent ANR
        }
        _favoriteSongs.value = favoriteSongsJson
    }
    
    fun setDefaultPlaylistsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DEFAULT_PLAYLISTS_ENABLED, enabled) }
        _defaultPlaylistsEnabled.value = enabled
    }
    
    // User Statistics Methods
    fun setListeningTime(time: Long) {
        prefs.edit { putLong(KEY_LISTENING_TIME, time) }
        _listeningTime.value = time
    }
    
    fun setSongsPlayed(count: Int) {
        prefs.edit { putInt(KEY_SONGS_PLAYED, count) }
        _songsPlayed.value = count
    }

    fun setRhythmGuardMode(mode: String) {
        val normalizedMode = sanitizeRhythmGuardMode(mode)
        prefs.edit {
    putString(KEY_RHYTHM_GUARD_MODE, normalizedMode)
    putString(KEY_RHYTHM_AURA_MODE, normalizedMode)
}
        _rhythmAuraMode.value = normalizedMode

        if (normalizedMode == RHYTHM_GUARD_MODE_AUTO) {
            applyRhythmGuardAutoProfileForAge(_rhythmAuraAge.value)
        }
    }

    @Deprecated("Use setRhythmGuardMode")
    fun setRhythmAuraMode(mode: String) = setRhythmGuardMode(mode)

    fun setRhythmGuardAge(age: Int) {
        val safeAge = age.coerceIn(8, 80)
        prefs.edit {
    putInt(KEY_RHYTHM_GUARD_AGE, safeAge)
    putInt(KEY_RHYTHM_AURA_AGE, safeAge)
}
        _rhythmAuraAge.value = safeAge

        if (_rhythmAuraMode.value == RHYTHM_GUARD_MODE_AUTO) {
            applyRhythmGuardAutoProfileForAge(safeAge)
        }
    }

    @Deprecated("Use setRhythmGuardAge")
    fun setRhythmAuraAge(age: Int) = setRhythmGuardAge(age)

    fun setRhythmGuardManualWarningsEnabled(enabled: Boolean) {
        prefs.edit {
    putBoolean(KEY_RHYTHM_GUARD_MANUAL_WARNINGS_ENABLED, enabled)
    putBoolean(KEY_RHYTHM_AURA_MANUAL_WARNINGS_ENABLED, enabled)
}
        _rhythmAuraManualWarningsEnabled.value = enabled
    }

    @Deprecated("Use setRhythmGuardManualWarningsEnabled")
    fun setRhythmAuraManualWarningsEnabled(enabled: Boolean) = setRhythmGuardManualWarningsEnabled(enabled)

    fun setRhythmGuardManualVolumeThreshold(threshold: Float) {
        val safeThreshold = threshold.coerceIn(0.40f, 0.95f)
        prefs.edit {
    putFloat(KEY_RHYTHM_GUARD_MANUAL_VOLUME_THRESHOLD, safeThreshold)
    putFloat(KEY_RHYTHM_AURA_MANUAL_VOLUME_THRESHOLD, safeThreshold)
}
        _rhythmAuraManualVolumeThreshold.value = safeThreshold
    }

    fun setRhythmGuardApplyVolumeLimitOnSpeaker(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RHYTHM_GUARD_APPLY_VOLUME_LIMIT_ON_SPEAKER, enabled) }
        _rhythmGuardApplyVolumeLimitOnSpeaker.value = enabled
    }

    @Deprecated("Use setRhythmGuardManualVolumeThreshold")
    fun setRhythmAuraManualVolumeThreshold(threshold: Float) = setRhythmGuardManualVolumeThreshold(threshold)

    fun setRhythmGuardLastAutoAppliedAt(timestamp: Long) {
        prefs.edit {
    putLong(KEY_RHYTHM_GUARD_LAST_AUTO_APPLIED_AT, timestamp)
    putLong(KEY_RHYTHM_AURA_LAST_AUTO_APPLIED_AT, timestamp)
}
        _rhythmAuraLastAutoAppliedAt.value = timestamp
    }

    @Deprecated("Use setRhythmGuardLastAutoAppliedAt")
    fun setRhythmAuraLastAutoAppliedAt(timestamp: Long) = setRhythmGuardLastAutoAppliedAt(timestamp)

    fun setRhythmGuardAlertThresholdMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceIn(-1, 24 * 60)
        prefs.edit { putInt(KEY_RHYTHM_GUARD_ALERT_THRESHOLD_MINUTES, safeMinutes) }
        _rhythmGuardAlertThresholdMinutes.value = safeMinutes
    }

    fun setRhythmGuardWarningTimeoutMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 60)
        prefs.edit { putInt(KEY_RHYTHM_GUARD_WARNING_TIMEOUT_MINUTES, safeMinutes) }
        _rhythmGuardWarningTimeoutMinutes.value = safeMinutes
    }

    fun setRhythmGuardPostTimeoutCooldownMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 60)
        prefs.edit { putInt(KEY_RHYTHM_GUARD_POST_TIMEOUT_COOLDOWN_MINUTES, safeMinutes) }
        _rhythmGuardPostTimeoutCooldownMinutes.value = safeMinutes
    }

    fun setRhythmGuardBreakResumeMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 180)
        prefs.edit { putInt(KEY_RHYTHM_GUARD_BREAK_RESUME_MINUTES, safeMinutes) }
        _rhythmGuardBreakResumeMinutes.value = safeMinutes
    }

    fun setRhythmGuardListeningTimeout(
        untilEpochMs: Long,
        reason: String = "",
        startedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        val safeUntil = untilEpochMs.coerceAtLeast(0L)
        val safeReason = reason.trim()
        val safeStartedAt = when {
            safeUntil <= 0L -> 0L
            startedAtEpochMs <= 0L -> System.currentTimeMillis().coerceAtMost(safeUntil)
            else -> startedAtEpochMs.coerceAtMost(safeUntil)
        }.coerceAtLeast(0L)
        prefs.edit {
            putLong(KEY_RHYTHM_GUARD_TIMEOUT_UNTIL_MS, safeUntil)
            putString(KEY_RHYTHM_GUARD_TIMEOUT_REASON, safeReason)
            putLong(KEY_RHYTHM_GUARD_TIMEOUT_STARTED_AT_MS, safeStartedAt)
            putInt(KEY_RHYTHM_GUARD_NEXT_ALLOWED_LIMIT_MINUTES, 0)

            if (safeUntil > 0L) {
                putLong(KEY_RHYTHM_GUARD_TIMEOUT_COOLDOWN_UNTIL_MS, 0L)
            }
        }
        _rhythmGuardTimeoutUntilMs.value = safeUntil
        _rhythmGuardTimeoutReason.value = safeReason
        _rhythmGuardTimeoutStartedAtMs.value = safeStartedAt
        _rhythmGuardNextAllowedLimitMinutes.value = 0
        if (safeUntil > 0L) {
            _rhythmGuardTimeoutCooldownUntilMs.value = 0L
        }
    }

    fun clearRhythmGuardListeningTimeout() {
        prefs.edit {
    putLong(KEY_RHYTHM_GUARD_TIMEOUT_UNTIL_MS, 0L)
    putString(KEY_RHYTHM_GUARD_TIMEOUT_REASON, "")
    putLong(KEY_RHYTHM_GUARD_TIMEOUT_STARTED_AT_MS, 0L)
}
        _rhythmGuardTimeoutUntilMs.value = 0L
        _rhythmGuardTimeoutReason.value = ""
        _rhythmGuardTimeoutStartedAtMs.value = 0L
    }

    fun setRhythmGuardTimeoutCooldownUntilMs(untilEpochMs: Long) {
        val safeUntil = untilEpochMs.coerceAtLeast(0L)
        prefs.edit { putLong(KEY_RHYTHM_GUARD_TIMEOUT_COOLDOWN_UNTIL_MS, safeUntil) }
        _rhythmGuardTimeoutCooldownUntilMs.value = safeUntil
    }

    fun setRhythmGuardTimeoutCooldownWithLimit(cooldownUntilMs: Long, nextLimitMinutes: Int) {
        val safeUntil = cooldownUntilMs.coerceAtLeast(0L)
        prefs.edit {
            putLong(KEY_RHYTHM_GUARD_TIMEOUT_COOLDOWN_UNTIL_MS, safeUntil)
            if (nextLimitMinutes > 0) {
                putInt(KEY_RHYTHM_GUARD_NEXT_ALLOWED_LIMIT_MINUTES, nextLimitMinutes)
            }
        }
        if (nextLimitMinutes > 0) {
            _rhythmGuardNextAllowedLimitMinutes.value = nextLimitMinutes
        }
        _rhythmGuardTimeoutCooldownUntilMs.value = safeUntil
    }

    fun clearRhythmGuardTimeoutCooldown() {
        setRhythmGuardTimeoutCooldownUntilMs(0L)
    }

    fun isRhythmGuardTimeoutActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        val timeoutUntil = _rhythmGuardTimeoutUntilMs.value
        return timeoutUntil > nowMs
    }

    fun getRhythmGuardPolicyBands(): List<RhythmGuardPolicyBand> = RHYTHM_GUARD_POLICY_BANDS

    @Deprecated("Use getRhythmGuardPolicyBands")
    fun getRhythmAuraPolicyBands(): List<RhythmAuraPolicyBand> = RHYTHM_AURA_POLICY_BANDS

    fun getRhythmGuardPolicy(age: Int = _rhythmAuraAge.value): RhythmGuardPolicyBand {
        val safeAge = age.coerceIn(8, 80)
        return RHYTHM_GUARD_POLICY_BANDS.firstOrNull { safeAge in it.minAge..it.maxAge }
            ?: RHYTHM_GUARD_POLICY_BANDS.last()
    }

    @Deprecated("Use getRhythmGuardPolicy")
    fun getRhythmAuraPolicy(age: Int = _rhythmAuraAge.value): RhythmAuraPolicyBand = getRhythmGuardPolicy(age)

    fun estimateRhythmGuardTodayListeningMinutes(
        dailyListeningStats: Map<String, Long>,
        songsPlayed: Int,
        listeningTimeMs: Long
    ): Int {
        val today = java.time.LocalDate.now().toString()
        val songsToday = (dailyListeningStats[today] ?: 0L).coerceAtLeast(0L)
        if (songsToday == 0L) return 0

        val safeSongsPlayed = songsPlayed.coerceAtLeast(1)
        val averageSongMinutes = ((listeningTimeMs / (1000f * 60f)) / safeSongsPlayed)
            .coerceIn(2.0f, 6.0f)

        return (songsToday * averageSongMinutes).toInt().coerceAtLeast(0)
    }

    @Deprecated("Use estimateRhythmGuardTodayListeningMinutes")
    fun estimateRhythmAuraTodayListeningMinutes(
        dailyListeningStats: Map<String, Long>,
        songsPlayed: Int,
        listeningTimeMs: Long
    ): Int = estimateRhythmGuardTodayListeningMinutes(dailyListeningStats, songsPlayed, listeningTimeMs)

    fun applyRhythmGuardAutoProfileForAge(age: Int) {
        val safeAge = age.coerceIn(8, 80)
        val policy = getRhythmGuardPolicy(safeAge)

        setAudioNormalization(true)
        setReplayGain(false)
        setUseSystemVolume(true)
        setStopPlaybackOnZeroVolume(policy.stopPlaybackOnZeroVolume)

        if (policy.enforceHapticFeedback && !_hapticFeedbackEnabled.value) {
            setHapticFeedbackEnabled(true)
        }

        setRhythmGuardLastAutoAppliedAt(System.currentTimeMillis())
    }

    @Deprecated("Use applyRhythmGuardAutoProfileForAge")
    fun applyRhythmAuraAutoProfileForAge(age: Int) = applyRhythmGuardAutoProfileForAge(age)

    private fun sanitizeRhythmGuardMode(mode: String?): String {
        return when (mode) {
            RHYTHM_GUARD_MODE_AUTO,
            RHYTHM_GUARD_MODE_MANUAL,
            RHYTHM_GUARD_MODE_OFF -> mode
            else -> RHYTHM_GUARD_MODE_OFF
        }
    }

    @Deprecated("Use sanitizeRhythmGuardMode")
    private fun sanitizeRhythmAuraMode(mode: String?): String = sanitizeRhythmGuardMode(mode)
    
    fun setUniqueArtists(count: Int) {
        prefs.edit { putInt(KEY_UNIQUE_ARTISTS, count) }
        _uniqueArtists.value = count
    }
    
    fun setGenrePreferences(preferences: Map<String, Int>) {
        val json = Gson().toJson(preferences)
        prefs.edit { putString(KEY_GENRE_PREFERENCES, json) }
        _genrePreferences.value = preferences
    }
    
    fun setTimeBasedPreferences(preferences: Map<Int, List<String>>) {
        val json = Gson().toJson(preferences)
        prefs.edit { putString(KEY_TIME_BASED_PREFERENCES, json) }
        _timeBasedPreferences.value = preferences
    }

    // Recently Played Methods
    fun updateRecentlyPlayed(songIds: List<String>) {
        val json = Gson().toJson(songIds)
        prefs.edit { putString(KEY_RECENTLY_PLAYED, json) }
        _recentlyPlayed.value = songIds
    }

    fun updateRecentlyPlayedSongCache(songs: List<Song>) {
        val snapshots = songs
            .distinctBy { it.id }
            .take(50)
            .map { it.toRecentSongSnapshot() }

        prefs.edit { putString(KEY_RECENTLY_PLAYED_SONG_CACHE, Gson().toJson(snapshots)) }
        _recentlyPlayedSongCache.value = snapshots
            .mapNotNull { snapshot ->
                snapshot.toSongOrNull()?.let { song -> song.id to song }
            }
            .toMap()
    }
    
    fun updateLastPlayedTimestamp(timestamp: Long) {
        prefs.edit { putLong(KEY_LAST_PLAYED_TIMESTAMP, timestamp) }
        _lastPlayedTimestamp.value = timestamp
    }
    
    
    // API Enable/Disable Methods
    fun setAppleCanvasEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_APPLE_CANVAS_ENABLED, enabled) }
        _appleCanvasEnabled.value = enabled
    }

    fun setAppleCanvasNetworkMode(mode: CanvasNetworkMode) {
        prefs.edit { putInt(KEY_APPLE_CANVAS_NETWORK_MODE, mode.ordinal) }
        _appleCanvasNetworkMode.value = mode
    }

    fun setArtistArtworkSource(source: ArtistArtworkSource) {
        prefs.edit { putString(KEY_ARTIST_ARTWORK_SOURCE, source.name) }
        _artistArtworkSource.value = source
    }

    fun setDeezerApiEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DEEZER_API_ENABLED, enabled) }
        _deezerApiEnabled.value = enabled
    }
    
    fun setLrcLibApiEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LRCLIB_API_ENABLED, enabled) }
        _lrclibApiEnabled.value = enabled
    }
    
    fun setBetterLyricsApiEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BETTERLYRICS_API_ENABLED, enabled) }
        _betterLyricsApiEnabled.value = enabled
    }
    
    fun setYTMusicApiEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_YTMUSIC_API_ENABLED, enabled) }
        _ytMusicApiEnabled.value = enabled
    }
    
    fun setSpotifyApiEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SPOTIFY_API_ENABLED, enabled) }
        _spotifyApiEnabled.value = enabled
    }
    
    fun setLyricallyApiEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LYRICALLY_API_ENABLED, enabled) }
        _lyricallyApiEnabled.value = enabled
    }

    fun setWikipediaApiEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_WIKIPEDIA_API_ENABLED, enabled) }
        _wikipediaApiEnabled.value = enabled
    }
    
    fun setAutoFetchArtwork(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_FETCH_ARTWORK, enabled) }
        _autoFetchArtwork.value = enabled
        updateDerivedSettings()
    }
    
    fun setSpotifyClientId(clientId: String) {
        prefs.edit { putString(KEY_SPOTIFY_CLIENT_ID, clientId) }
        _spotifyClientId.value = clientId
    }
    
    fun setSpotifyClientSecret(clientSecret: String) {
        prefs.edit { putString(KEY_SPOTIFY_CLIENT_SECRET, clientSecret) }
        _spotifyClientSecret.value = clientSecret
    }


    // General Broadcast Status Methods
    fun setBroadcastStatusEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BROADCAST_STATUS_ENABLED, enabled) }
        _broadcastStatusEnabled.value = enabled
    }

    fun setBluetoothLyricsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BLUETOOTH_LYRICS_ENABLED, enabled) }
        _bluetoothLyricsEnabled.value = enabled
    }

    // Enhanced User Preferences Methods
    fun updateFavoriteGenres(genres: Map<String, Int>) {
        val json = Gson().toJson(genres)
        prefs.edit { putString(KEY_FAVORITE_GENRES, json) }
        _favoriteGenres.value = genres
    }
    
    fun updateDailyListeningStats(stats: Map<String, Long>) {
        val json = Gson().toJson(stats)
        prefs.edit { putString(KEY_DAILY_LISTENING_STATS, json) }
        _dailyListeningStats.value = stats
    }
    
    fun updateWeeklyTopArtists(artists: Map<String, Int>) {
        val json = Gson().toJson(artists)
        prefs.edit { putString(KEY_WEEKLY_TOP_ARTISTS, json) }
        _weeklyTopArtists.value = artists
    }
    
    fun updateMoodPreferences(preferences: Map<String, List<String>>) {
        val json = Gson().toJson(preferences)
        prefs.edit { putString(KEY_MOOD_PREFERENCES, json) }
        _moodPreferences.value = preferences
    }

    // Song Play Counts Methods
    fun setSongPlayCounts(counts: Map<String, Int>) {
        val json = Gson().toJson(counts)
        prefs.edit { putString(KEY_SONG_PLAY_COUNTS, json) }
        _songPlayCounts.value = counts
    }

    // Onboarding Methods
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, completed) }
        _onboardingCompleted.value = completed
    }

    fun setInitialMediaScanCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_INITIAL_MEDIA_SCAN_COMPLETED, completed) }
        _initialMediaScanCompleted.value = completed
    }

    fun setGenreDetectionCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_GENRE_DETECTION_COMPLETED, completed) }
        _genreDetectionCompleted.value = completed
    }

    fun setAudioMetadataExtractionCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_AUDIO_METADATA_EXTRACTION_COMPLETED, completed) }
        _audioMetadataExtractionCompleted.value = completed
    }

    fun setEmbeddedArtworkExtractionCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_EMBEDDED_ARTWORK_EXTRACTION_COMPLETED, completed) }
        _embeddedArtworkExtractionCompleted.value = completed
    }

    fun setEmbeddedArtworkExtractionLosslessStatus(lossless: Boolean) {
        prefs.edit { putBoolean(KEY_EMBEDDED_ARTWORK_EXTRACTION_LOSSLESS_STATUS, lossless) }
        _embeddedArtworkExtractionLosslessStatus.value = lossless
    }

    // App Updater Settings Methods
    fun setAutoCheckForUpdates(enable: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_CHECK_FOR_UPDATES, enable) }
        _autoCheckForUpdates.value = enable

        if (shouldRunUpdateNotificationWorker()) {
            scheduleUpdateNotificationWorker()
        } else {
            cancelUpdateNotificationWorker()
        }
    }

    fun setUpdateChannel(channel: String) {
        prefs.edit { putString(KEY_UPDATE_CHANNEL, channel) }
        _updateChannel.value = channel
    }

    fun setUpdateSource(source: String) {
        prefs.edit { putString(KEY_UPDATE_SOURCE, source) }
        _updateSource.value = source
    }

    fun setUpdatesEnabled(enable: Boolean) {
        prefs.edit { putBoolean(KEY_UPDATES_ENABLED, enable) }
        _updatesEnabled.value = enable
        
        // Update WorkManager scheduling based on new state
        if (shouldRunUpdateNotificationWorker()) {
            scheduleUpdateNotificationWorker()
        } else {
            cancelUpdateNotificationWorker()
        }
    }

    fun setUpdateNotificationsEnabled(enable: Boolean) {
        prefs.edit { putBoolean(KEY_UPDATE_NOTIFICATIONS_ENABLED, enable) }
        _updateNotificationsEnabled.value = enable
        
        // Update WorkManager scheduling
        if (shouldRunUpdateNotificationWorker()) {
            scheduleUpdateNotificationWorker()
        } else {
            cancelUpdateNotificationWorker()
        }
    }

    fun setUpdateStatusNotificationsEnabled(enable: Boolean) {
        prefs.edit { putBoolean(KEY_UPDATE_STATUS_NOTIFICATIONS_ENABLED, enable) }
        _updateStatusNotificationsEnabled.value = enable

        if (shouldRunUpdateNotificationWorker()) {
            scheduleUpdateNotificationWorker()
        } else {
            cancelUpdateNotificationWorker()
        }
    }

    fun setMediaScanMode(mode: MediaScanMode) {
        val changed = _mediaScanMode.value != mode
        prefs.edit { putString(KEY_MEDIA_SCAN_MODE, mode.value) }
        _mediaScanMode.value = mode
        if (changed) {
            requestFullMediaRescanOnNextLaunch(reason = "media_scan_mode_changed")
        }
    }

    fun setIncludeHiddenWhitelistedMedia(include: Boolean) {
        val changed = _includeHiddenWhitelistedMedia.value != include
        prefs.edit { putBoolean(KEY_INCLUDE_HIDDEN_WHITELISTED_MEDIA, include) }
        _includeHiddenWhitelistedMedia.value = include
        if (changed) {
            requestFullMediaRescanOnNextLaunch(reason = "hidden_nomedia_scan_toggle_changed")
        }
    }

    fun setUpdateCheckIntervalHours(hours: Int) {
        prefs.edit { putInt(KEY_UPDATE_CHECK_INTERVAL_HOURS, hours) }
        _updateCheckIntervalHours.value = hours

        if (shouldRunUpdateNotificationWorker()) {
            scheduleUpdateNotificationWorker()
        }
    }

    // Beta Program Methods
    fun setHasShownBetaPopup(shown: Boolean) {
        prefs.edit { putBoolean(KEY_HAS_SHOWN_BETA_POPUP, shown) }
        _hasShownBetaPopup.value = shown
    }

    // Crash Reporting Methods
    fun setLastCrashLog(log: String?) {
        // Use commit() instead of apply() to ensure immediate persistence for crash logs
        if (log == null) {
            prefs.edit { remove(KEY_LAST_CRASH_LOG) }
        } else {
            prefs.edit { putString(KEY_LAST_CRASH_LOG, log) }
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
            prefs.edit { putString(KEY_CRASH_LOG_HISTORY, json) }
        } catch (e: Exception) {
            // If commit fails (shouldn't happen), at least log it
            Log.e("CrashLog", "Failed to persist crash log to SharedPreferences", e)
        }
    }

    fun clearCrashLogHistory() {
        prefs.edit { remove(KEY_CRASH_LOG_HISTORY) }
        _crashLogHistory.value = emptyList()
    }
    
    // Haptic Feedback Methods
    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, enabled) }
        _hapticFeedbackEnabled.value = enabled
        updateDerivedSettings()
    }
    
    // Notification Settings Methods
    fun setUseCustomNotification(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_CUSTOM_NOTIFICATION, enabled) }
        _useCustomNotification.value = enabled
    }

    fun setLibraryOperationsNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_LIBRARY_OPERATIONS_NOTIFICATIONS_ENABLED, enabled) }
        _libraryOperationsNotificationsEnabled.value = enabled
    }

    fun setSleepTimerNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SLEEP_TIMER_NOTIFICATIONS_ENABLED, enabled) }
        _sleepTimerNotificationsEnabled.value = enabled
    }

    fun setStreamingNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_STREAMING_NOTIFICATIONS_ENABLED, enabled) }
        _streamingNotificationsEnabled.value = enabled
    }

    fun setRhythmGuardAlertNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RHYTHM_GUARD_ALERT_NOTIFICATIONS_ENABLED, enabled) }
        _rhythmGuardAlertNotificationsEnabled.value = enabled
    }

    fun setRhythmGuardTimerNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RHYTHM_GUARD_TIMER_NOTIFICATIONS_ENABLED, enabled) }
        _rhythmGuardTimerNotificationsEnabled.value = enabled
    }

    fun setRhythmPulseNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_RHYTHM_PULSE_NOTIFICATIONS_ENABLED, enabled) }
        _rhythmPulseNotificationsEnabled.value = enabled

        if (enabled) {
            scheduleRhythmPulseNotificationWorker()
        } else {
            cancelRhythmPulseNotificationWorker()
        }
    }

    fun setRhythmPulseNotificationIntervalHours(hours: Int) {
        val safeHours = hours.coerceIn(6, 72)
        prefs.edit { putInt(KEY_RHYTHM_PULSE_NOTIFICATION_INTERVAL_HOURS, safeHours) }
        _rhythmPulseNotificationIntervalHours.value = safeHours

        if (_rhythmPulseNotificationsEnabled.value) {
            scheduleRhythmPulseNotificationWorker()
        }
    }
    
    fun setForcePlayerCompactMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FORCE_PLAYER_COMPACT_MODE, enabled) }
        _forcePlayerCompactMode.value = enabled
    }

    fun setUseExperimentalPlayerUi(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_EXPERIMENTAL_PLAYER_UI, enabled) }
        _useExperimentalPlayerUi.value = enabled
    }

    fun setEnableAlbumEditing(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLE_ALBUM_EDITING, enabled) }
        _enableAlbumEditing.value = enabled
    }
    
    // Codec Monitoring & Enhanced Seeking Methods
    fun setCodecMonitoringEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CODEC_MONITORING_ENABLED, enabled) }
        _codecMonitoringEnabled.value = enabled
    }
    
    fun setShowCodecNotifications(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_CODEC_NOTIFICATIONS, enabled) }
        _showCodecNotifications.value = enabled
    }
    
    fun setEnhancedSeekingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENHANCED_SEEKING_ENABLED, enabled) }
        _enhancedSeekingEnabled.value = enabled
    }
    
    fun setAudioDeviceLoggingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("audio_device_logging_enabled", enabled) }
        _audioDeviceLoggingEnabled.value = enabled
    }
    
    fun setTrackErrorCheckerEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_TRACK_ERROR_CHECKER_ENABLED, enabled) }
        _trackErrorCheckerEnabled.value = enabled
    }
    
    // Media3 1.9.0 Feature Methods
    fun setUseCustomCommandButtons(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_CUSTOM_COMMAND_BUTTONS, enabled) }
        _useCustomCommandButtons.value = enabled
    }
    
    fun setScrubbingModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SCRUBBING_MODE_ENABLED, enabled) }
        _scrubbingModeEnabled.value = enabled
    }
    
    fun setStuckPlayerDetectionEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_STUCK_PLAYER_DETECTION_ENABLED, enabled) }
        _stuckPlayerDetectionEnabled.value = enabled
    }
    
    // UI Settings Methods
    fun setUseSettings(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_SETTINGS, enabled) }
        _useSettings.value = enabled
    }
    
    // Festive Theme Settings Methods
    fun setFestiveThemeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FESTIVE_THEME_ENABLED, enabled) }
        _festiveThemeEnabled.value = enabled
    }
    
    fun setFestiveThemeType(type: String) {
        prefs.edit { putString(KEY_FESTIVE_THEME_TYPE, type) }
        _festiveThemeType.value = type
    }
    
    fun setFestiveThemeIntensity(intensity: Float) {
        prefs.edit { putFloat(KEY_FESTIVE_THEME_INTENSITY, intensity) }
        _festiveThemeIntensity.value = intensity
    }
    
    fun setFestiveThemeAutoDetect(autoDetect: Boolean) {
        prefs.edit { putBoolean(KEY_FESTIVE_THEME_AUTO_DETECT, autoDetect) }
        _festiveThemeAutoDetect.value = autoDetect
    }
    
    fun setFestiveSnowflakeSize(size: Float) {
        prefs.edit { putFloat(KEY_FESTIVE_SNOWFLAKE_SIZE, size) }
        _festiveSnowflakeSize.value = size
    }
    
    fun setFestiveSnowflakeArea(area: String) {
        prefs.edit { putString(KEY_FESTIVE_SNOWFLAKE_AREA, area) }
        _festiveSnowflakeArea.value = area
    }
    
    // Festive Decoration Position Methods
    fun setFestiveShowTopLights(show: Boolean) {
        prefs.edit { putBoolean(KEY_FESTIVE_SHOW_TOP_LIGHTS, show) }
        _festiveShowTopLights.value = show
    }
    
    fun setFestiveShowSideGarland(show: Boolean) {
        prefs.edit { putBoolean(KEY_FESTIVE_SHOW_SIDE_GARLAND, show) }
        _festiveShowSideGarland.value = show
    }
    
    fun setFestiveShowBottomSnow(show: Boolean) {
        prefs.edit { putBoolean(KEY_FESTIVE_SHOW_BOTTOM_SNOW, show) }
        _festiveShowBottomSnow.value = show
    }
    
    fun setFestiveShowSnowfall(show: Boolean) {
        prefs.edit { putBoolean(KEY_FESTIVE_SHOW_SNOWFALL, show) }
        _festiveShowSnowfall.value = show
    }
    
    // Blacklisted Songs Methods
    fun addToBlacklist(songId: String) {
        val currentList = _blacklistedSongs.value.toMutableList()
        if (!currentList.contains(songId)) {
            currentList.add(songId)
            val json = Gson().toJson(currentList)
            prefs.edit { putString(KEY_BLACKLISTED_SONGS, json) }
            _blacklistedSongs.value = currentList
        }
    }
    
    fun removeFromBlacklist(songId: String) {
        val currentList = _blacklistedSongs.value.toMutableList()
        if (currentList.remove(songId)) {
            val json = Gson().toJson(currentList)
            prefs.edit { putString(KEY_BLACKLISTED_SONGS, json) }
            _blacklistedSongs.value = currentList
        }
    }
    
    fun isBlacklisted(songId: String): Boolean {
        return _blacklistedSongs.value.contains(songId)
    }
    
    fun clearBlacklist() {
        prefs.edit { remove(KEY_BLACKLISTED_SONGS) }
        _blacklistedSongs.value = emptyList()
    }
    
    // Blacklisted Folders Methods
    fun addFolderToBlacklist(folderPath: String) {
        val currentList = _blacklistedFolders.value.toMutableList()
        if (!currentList.contains(folderPath)) {
            currentList.add(folderPath)
            val json = Gson().toJson(currentList)
            prefs.edit { putString(KEY_BLACKLISTED_FOLDERS, json) }
            _blacklistedFolders.value = currentList
        }
    }
    
    fun removeFolderFromBlacklist(folderPath: String) {
        val currentList = _blacklistedFolders.value.toMutableList()
        if (currentList.remove(folderPath)) {
            val json = Gson().toJson(currentList)
            prefs.edit { putString(KEY_BLACKLISTED_FOLDERS, json) }
            _blacklistedFolders.value = currentList
        }
    }
    
    fun normalizeStoragePath(path: String): String {
        var normalized = path.trim().replace('\\', '/')
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/")
        }
        if (normalized.length > 1 && normalized.endsWith('/')) {
            normalized = normalized.substring(0, normalized.length - 1)
        }
        // Resolve legacy symlink aliases (e.g. /sdcard, /storage/self/primary) to the
        // canonical external storage path using OS-level symlink resolution
        val externalStorage = android.os.Environment.getExternalStorageDirectory().absolutePath
        if (normalized.startsWith("/") && !normalized.startsWith(externalStorage)) {
            normalized = try {
                val candidate = File(normalized).canonicalPath
                if (candidate.startsWith(externalStorage)) candidate else normalized
            } catch (_: java.io.IOException) {
                normalized
            }
        }
        return normalized
    }

    private fun isFolderSubdirectoryOrEqual(parent: String, child: String): Boolean {
        val normParent = normalizeStoragePath(parent)
        val normChild = normalizeStoragePath(child)
        return normParent.equals(normChild, ignoreCase = true) ||
            normChild.startsWith(if (normParent.endsWith('/')) normParent else "$normParent/", ignoreCase = true)
    }

    fun isFolderBlacklisted(folderPath: String): Boolean {
        return _blacklistedFolders.value.any { blacklistedPath ->
            isFolderSubdirectoryOrEqual(blacklistedPath, folderPath)
        }
    }
    
    fun clearFolderBlacklist() {
        prefs.edit { remove(KEY_BLACKLISTED_FOLDERS) }
        _blacklistedFolders.value = emptyList()
    }
    
    // Bulk operations for better synchronization
    fun removeFolderAndRelatedSongs(folderPath: String, songsInFolder: List<String>) {
        // Remove folder from blacklist
        val currentFolders = _blacklistedFolders.value.toMutableList()
        if (currentFolders.remove(folderPath)) {
            val foldersJson = Gson().toJson(currentFolders)
            prefs.edit { putString(KEY_BLACKLISTED_FOLDERS, foldersJson) }
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
            prefs.edit { putString(KEY_BLACKLISTED_SONGS, songsJson) }
            _blacklistedSongs.value = currentSongs
        }
    }
    
    fun addFolderAndOptionalSong(folderPath: String, songId: String?) {
        // Add folder to blacklist
        val currentFolders = _blacklistedFolders.value.toMutableList()
        if (!currentFolders.contains(folderPath)) {
            currentFolders.add(folderPath)
            val foldersJson = Gson().toJson(currentFolders)
            prefs.edit { putString(KEY_BLACKLISTED_FOLDERS, foldersJson) }
            _blacklistedFolders.value = currentFolders
        }
        
        // Optionally add song to individual blacklist for immediate synchronization
        songId?.let { id ->
            val currentSongs = _blacklistedSongs.value.toMutableList()
            if (!currentSongs.contains(id)) {
                currentSongs.add(id)
                val songsJson = Gson().toJson(currentSongs)
                prefs.edit { putString(KEY_BLACKLISTED_SONGS, songsJson) }
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
                isFolderSubdirectoryOrEqual(folderPath, songPath)
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
            prefs.edit { putString(KEY_WHITELISTED_SONGS, json) }
            _whitelistedSongs.value = currentList
        }
    }
    
    fun removeFromWhitelist(songId: String) {
        val currentList = _whitelistedSongs.value.toMutableList()
        if (currentList.remove(songId)) {
            val json = Gson().toJson(currentList)
            prefs.edit { putString(KEY_WHITELISTED_SONGS, json) }
            _whitelistedSongs.value = currentList
        }
    }
    
    fun isSongWhitelisted(songId: String): Boolean {
        return _whitelistedSongs.value.contains(songId)
    }
    
    fun clearWhitelist() {
        prefs.edit { remove(KEY_WHITELISTED_SONGS) }
        _whitelistedSongs.value = emptyList()
    }
    
    // Song Rating Methods
    // Whitelisted Folders Methods
    fun addFolderToWhitelist(folderPath: String) {
        val currentList = _whitelistedFolders.value.toMutableList()
        if (!currentList.contains(folderPath)) {
            currentList.add(folderPath)
            val json = Gson().toJson(currentList)
            prefs.edit { putString(KEY_WHITELISTED_FOLDERS, json) }
            _whitelistedFolders.value = currentList
            requestFullMediaRescanOnNextLaunch(reason = "whitelist_folder_added")
        }
    }
    
    fun removeFolderFromWhitelist(folderPath: String) {
        val currentList = _whitelistedFolders.value.toMutableList()
        if (currentList.remove(folderPath)) {
            val json = Gson().toJson(currentList)
            prefs.edit { putString(KEY_WHITELISTED_FOLDERS, json) }
            _whitelistedFolders.value = currentList
            requestFullMediaRescanOnNextLaunch(reason = "whitelist_folder_removed")
        }
    }
    
    fun isFolderWhitelisted(folderPath: String): Boolean {
        return _whitelistedFolders.value.any { whitelistedPath ->
            isFolderSubdirectoryOrEqual(whitelistedPath, folderPath)
        }
    }
    
    fun clearFolderWhitelist() {
        val hadValues = _whitelistedFolders.value.isNotEmpty()
        prefs.edit { remove(KEY_WHITELISTED_FOLDERS) }
        _whitelistedFolders.value = emptyList()
        if (hadValues) {
            requestFullMediaRescanOnNextLaunch(reason = "whitelist_folders_cleared")
        }
    }

    // Pinned Folders Methods (Explorer)
    fun addFolderToPinned(folderPath: String) {
        val currentList = _pinnedFolders.value.toMutableList()
        if (!currentList.contains(folderPath)) {
            currentList.add(folderPath)
            val json = Gson().toJson(currentList)
            prefs.edit { putString(KEY_PINNED_FOLDERS, json) }
            _pinnedFolders.value = currentList
        }
    }

    fun removeFolderFromPinned(folderPath: String) {
        val currentList = _pinnedFolders.value.toMutableList()
        if (currentList.remove(folderPath)) {
            val json = Gson().toJson(currentList)
            prefs.edit { putString(KEY_PINNED_FOLDERS, json) }
            _pinnedFolders.value = currentList
        }
    }

    fun isFolderPinned(folderPath: String): Boolean {
        return _pinnedFolders.value.contains(folderPath)
    }

    fun clearPinnedFolders() {
        prefs.edit { remove(KEY_PINNED_FOLDERS) }
        _pinnedFolders.value = emptyList()
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
                isFolderSubdirectoryOrEqual(folderPath, songPath)
            }
        }
        
        return false
    }
    
    // Backup and Restore Methods
    fun setLastBackupTimestamp(timestamp: Long) {
        prefs.edit { putLong(KEY_LAST_BACKUP_TIMESTAMP, timestamp) }
        _lastBackupTimestamp.value = timestamp
    }
    
    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled) }
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
        prefs.edit { putLong(KEY_LAST_SCAN_TIMESTAMP, timestamp) }
        _lastScanTimestamp.value = timestamp
    }
    
    fun setLastScanDuration(duration: Long) {
        prefs.edit { putLong(KEY_LAST_SCAN_DURATION, duration) }
        _lastScanDuration.value = duration
    }
    
    fun setAllowedFormats(formats: Set<String>) {
        val changed = _allowedFormats.value != formats
        prefs.edit { putStringSet(KEY_ALLOWED_FORMATS, formats) }
        _allowedFormats.value = formats
        Log.d("AppSettings", "Allowed formats updated: $formats")
        if (changed) {
            requestFullMediaRescanOnNextLaunch(reason = "allowed_formats_changed")
        }
    }
    
    fun setMinimumBitrate(bitrate: Int) {
        val changed = _minimumBitrate.value != bitrate
        prefs.edit { putInt(KEY_MINIMUM_BITRATE, bitrate) }
        _minimumBitrate.value = bitrate
        Log.d("AppSettings", "Minimum bitrate set to: ${bitrate}kbps")
        if (changed) {
            requestFullMediaRescanOnNextLaunch(reason = "minimum_bitrate_changed")
        }
    }
    
    fun setMinimumDuration(duration: Long) {
        val changed = _minimumDuration.value != duration
        prefs.edit { putLong(KEY_MINIMUM_DURATION, duration) }
        _minimumDuration.value = duration
        Log.d("AppSettings", "Minimum duration set to: ${duration}ms")
        if (changed) {
            requestFullMediaRescanOnNextLaunch(reason = "minimum_duration_changed")
        }
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
    private fun shouldRunUpdateNotificationWorker(): Boolean {
        return _updatesEnabled.value &&
            _autoCheckForUpdates.value &&
            (_updateNotificationsEnabled.value || _updateStatusNotificationsEnabled.value)
    }

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

    private fun scheduleRhythmPulseNotificationWorker() {
        try {
            val intervalHours = _rhythmPulseNotificationIntervalHours.value.toLong().coerceIn(6L, 72L)

            val workRequest = PeriodicWorkRequestBuilder<RhythmPulseNotificationWorker>(
                intervalHours, TimeUnit.HOURS,
                1, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                RhythmPulseNotificationWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.d("AppSettings", "Rhythm tips worker scheduled: every $intervalHours hours")
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to schedule Rhythm tips worker", e)
        }
    }

    private fun cancelRhythmPulseNotificationWorker() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(RhythmPulseNotificationWorker.WORK_NAME)
            Log.d("AppSettings", "Rhythm tips worker cancelled")
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to cancel Rhythm tips worker", e)
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
            prefs.edit { remove(KEY_BACKUP_LOCATION) }
        } else {
            prefs.edit { putString(KEY_BACKUP_LOCATION, location) }
        }
        _backupLocation.value = location
    }

    data class BackupRestoreSections(
        val includeGeneralSettings: Boolean = true,
        val includeLibraryData: Boolean = true,
        val includeStatsAndRhythmGuard: Boolean = true
    ) {
        val hasAtLeastOneSectionSelected: Boolean
            get() = includeGeneralSettings || includeLibraryData || includeStatsAndRhythmGuard
    }

    private fun isLibraryBackupKey(key: String): Boolean {
        return key == KEY_PLAYLISTS ||
            key == KEY_FAVORITE_SONGS ||
            key == KEY_BLACKLISTED_SONGS ||
            key == KEY_BLACKLISTED_FOLDERS ||
            key == KEY_WHITELISTED_SONGS ||
            key == KEY_WHITELISTED_FOLDERS ||
            key == KEY_PINNED_FOLDERS ||
            key == KEY_SONG_LYRICS_PREFERENCES ||
            key == KEY_SONG_CUSTOM_LRC_FILES
    }

    fun isRhythmGuardTransientRuntimeKey(key: String): Boolean {
        return key == KEY_RHYTHM_GUARD_TIMEOUT_UNTIL_MS ||
            key == KEY_RHYTHM_GUARD_TIMEOUT_REASON ||
            key == KEY_RHYTHM_GUARD_TIMEOUT_STARTED_AT_MS ||
            key == KEY_RHYTHM_GUARD_TIMEOUT_COOLDOWN_UNTIL_MS ||
            key == KEY_RHYTHM_GUARD_NEXT_ALLOWED_LIMIT_MINUTES
    }

    fun isStatsAndRhythmGuardBackupKey(key: String): Boolean {
        if (isRhythmGuardTransientRuntimeKey(key)) {
            return false
        }

        return key == KEY_LAST_PLAYED_TIMESTAMP ||
            key == KEY_RECENTLY_PLAYED ||
            key == KEY_LISTENING_TIME ||
            key == KEY_SONGS_PLAYED ||
            key == KEY_UNIQUE_ARTISTS ||
            key == KEY_GENRE_PREFERENCES ||
            key == KEY_FAVORITE_GENRES ||
            key == KEY_DAILY_LISTENING_STATS ||
            key == KEY_WEEKLY_TOP_ARTISTS ||
            key == KEY_MOOD_PREFERENCES ||
            key == KEY_SONG_PLAY_COUNTS ||
            key == KEY_HOME_SHOW_LISTENING_STATS ||
            key == KEY_RHYTHM_GUARD_MODE ||
            key == KEY_RHYTHM_GUARD_AGE ||
            key == KEY_RHYTHM_GUARD_MANUAL_WARNINGS_ENABLED ||
            key == KEY_RHYTHM_GUARD_MANUAL_VOLUME_THRESHOLD ||
            key == KEY_RHYTHM_GUARD_LAST_AUTO_APPLIED_AT ||
            key == KEY_RHYTHM_GUARD_ALERT_THRESHOLD_MINUTES ||
            key == KEY_RHYTHM_GUARD_WARNING_TIMEOUT_MINUTES ||
            key == KEY_RHYTHM_GUARD_BREAK_RESUME_MINUTES ||
            key == KEY_RHYTHM_AURA_MODE ||
            key == KEY_RHYTHM_AURA_AGE ||
            key.startsWith("rhythm_guard_") ||
            key.startsWith("rhythm_aura_")
    }

    fun shouldIncludeKeyInBackupSections(
        key: String,
        sections: BackupRestoreSections
    ): Boolean {
        if (isRhythmGuardTransientRuntimeKey(key)) {
            return false
        }

        return when {
            isLibraryBackupKey(key) -> sections.includeLibraryData
            isStatsAndRhythmGuardBackupKey(key) -> sections.includeStatsAndRhythmGuard
            else -> sections.includeGeneralSettings
        }
    }
    
    sealed class BackupValidationResult {
        object Valid : BackupValidationResult()
        data class Invalid(val reason: String) : BackupValidationResult()
    }

    /**
     * Validates a backup JSON payload for structure, header keys, and parseable playlists data
     */
    fun validateBackupJson(
        backupJson: String,
        sections: BackupRestoreSections = BackupRestoreSections()
    ): BackupValidationResult {
        if (backupJson.isBlank()) {
            return BackupValidationResult.Invalid("Backup JSON payload is empty")
        }
        return try {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?>? = GsonUtils.gson.fromJson(backupJson, type)
            if (map == null || map.isEmpty()) {
                return BackupValidationResult.Invalid("Backup content is not a valid non-empty JSON object")
            }

            if (!map.containsKey("preferences")) {
                return BackupValidationResult.Invalid("Missing required 'preferences' key")
            }

            if (!map.containsKey("backup_version")) {
                return BackupValidationResult.Invalid("Missing required 'backup_version' key")
            }

            val effectiveSections = if (sections.hasAtLeastOneSectionSelected) sections else BackupRestoreSections()

            if (effectiveSections.includeLibraryData && map.containsKey("playlists_data")) {
                val playlistsData = map["playlists_data"] as? String
                if (!playlistsData.isNullOrBlank()) {
                    try {
                        val playlistListType = object : TypeToken<List<Playlist>>() {}.type
                        val parsedPlaylists: List<Playlist>? = GsonUtils.gson.fromJson(playlistsData, playlistListType)
                        if (parsedPlaylists == null) {
                            return BackupValidationResult.Invalid("playlists_data cannot be parsed into Playlist objects")
                        }
                    } catch (e: Exception) {
                        return BackupValidationResult.Invalid("Corrupted playlist structure inside backup: ${e.message}")
                    }
                }
            }

            BackupValidationResult.Valid
        } catch (e: Exception) {
            BackupValidationResult.Invalid("JSON parsing exception: ${e.message}")
        }
    }

    /**
     * Creates a complete backup of all app data as JSON
     */
    fun createBackup(sections: BackupRestoreSections = BackupRestoreSections()): String {
        val backupData = mutableMapOf<String, Any?>()
        val preferencesTypes = mutableMapOf<String, String>()

        val effectiveSections = if (sections.hasAtLeastOneSectionSelected) {
            sections
        } else {
            BackupRestoreSections()
        }
        
        // Get all preferences
        val allPrefs = prefs.all
        
        // Filter based on selected backup sections.
        val filteredPrefs = allPrefs.filterKeys { key ->
            shouldIncludeKeyInBackupSections(key, effectiveSections)
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
        backupData["backup_version"] = 4 // Includes explicit stats, Rhythm Guard payload, and playback_history.json
        backupData["selected_sections"] = mapOf(
            "general_settings" to effectiveSections.includeGeneralSettings,
            "library_data" to effectiveSections.includeLibraryData,
            "stats_rhythm_guard" to effectiveSections.includeStatsAndRhythmGuard
        )
        
        // Explicitly include playlist and favorite songs data even if already in preferences
        // This ensures they are properly backed up and restored
        if (effectiveSections.includeLibraryData) {
            try {
            val playlistsJson = try {
                kotlinx.coroutines.runBlocking {
                    val db = chromahub.rhythm.app.features.local.data.database.RhythmDatabase.getInstance(context)
                    val playlistDao = db.playlistDao()
                    val dbPlaylists = playlistDao.getAllPlaylists()
                    if (dbPlaylists.isNotEmpty()) {
                        val songDao = db.songDao()
                        val modelPlaylists = dbPlaylists.map { entity ->
                            val songIds = playlistDao.getSongIdsForPlaylist(entity.id)
                            val playlistSongs = songIds.map { songId ->
                                songDao.getSongById(songId)?.let { songEntity ->
                                    Song(
                                        id = songEntity.id,
                                        title = songEntity.title,
                                        artist = songEntity.artist,
                                        album = songEntity.album,
                                        albumId = songEntity.albumId,
                                        duration = songEntity.duration,
                                        uri = (songEntity.uri).toUri(),
                                        artworkUri = songEntity.artworkUri?.let { it.toUri() },
                                        trackNumber = songEntity.trackNumber,
                                        year = songEntity.year,
                                        genre = songEntity.genre,
                                        dateAdded = songEntity.dateAdded,
                                        dateModified = songEntity.dateModified.takeIf { it > 0L } ?: songEntity.dateAdded,
                                        albumArtist = songEntity.albumArtist,
                                        bitrate = songEntity.bitrate,
                                        sampleRate = songEntity.sampleRate,
                                        channels = songEntity.channels,
                                        codec = songEntity.codec,
                                        discNumber = songEntity.discNumber,
                                        path = songEntity.path
                                    )
                                } ?: Song(
                                    id = songId,
                                    title = context.getString(R.string.unresolved_track),
                                    artist = context.getString(R.string.unknown_artist_name),
                                    album = context.getString(R.string.unknown_album_name),
                                    albumId = "",
                                    duration = 0L,
                                    uri = Uri.EMPTY,
                                    artworkUri = null,
                                    trackNumber = 0,
                                    year = 0,
                                    genre = null,
                                    dateAdded = entity.dateCreated,
                                    dateModified = entity.dateModified,
                                    albumArtist = null,
                                    bitrate = null,
                                    sampleRate = null,
                                    channels = null,
                                    codec = null,
                                    discNumber = 1,
                                    path = null
                                )
                            }
                            Playlist(
                                id = entity.id,
                                name = entity.name,
                                songs = playlistSongs,
                                dateCreated = entity.dateCreated,
                                dateModified = entity.dateModified,
                                artworkUri = entity.artworkUri?.let { it.toUri() }
                            )
                        }
                        GsonUtils.gson.toJson(modelPlaylists)
                    } else {
                        prefs.getString(KEY_PLAYLISTS, "[]")
                    }
                }
            } catch (e: Exception) {
                Log.e("AppSettings", "Error reading database playlists for backup", e)
                prefs.getString(KEY_PLAYLISTS, "[]")
            }
            val favoriteSongsJson = prefs.getString(KEY_FAVORITE_SONGS, null)
            
            backupData["playlists_data"] = playlistsJson ?: "[]"
            Log.d("AppSettings", "Including playlists data in backup: ${backupData["playlists_data"].toString().length} characters")
            
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
        }

        if (effectiveSections.includeStatsAndRhythmGuard) {
            try {
                val statsPrefs = allPrefs.filterKeys { key ->
                    isStatsAndRhythmGuardBackupKey(key)
                }
                val statsPreferenceTypes = mutableMapOf<String, String>()
                statsPrefs.forEach { (key, value) ->
                    statsPreferenceTypes[key] = when (value) {
                        is Boolean -> "Boolean"
                        is Float -> "Float"
                        is Int -> "Int"
                        is Long -> "Long"
                        is String -> "String"
                        is Set<*> -> "StringSet"
                        else -> "Unknown"
                    }
                }

                backupData["stats_rhythm_guard_data"] = statsPrefs
                backupData["stats_rhythm_guard_types"] = statsPreferenceTypes
                Log.d("AppSettings", "Including stats & Rhythm Guard data in backup: ${statsPrefs.size} keys")

                val historyFile = File(context.filesDir, "playback_history.json")
                if (historyFile.exists()) {
                    val historyJson = historyFile.readText()
                    if (historyJson.isNotBlank()) {
                        backupData["playback_history"] = historyJson
                        Log.d("AppSettings", "Including playback_history.json in backup: ${historyJson.length} chars")
                    }
                }
            } catch (e: Exception) {
                Log.e("AppSettings", "Error including stats & Rhythm Guard data in backup", e)
            }
        }
        
        val backupJsonString = GsonUtils.gson.toJson(backupData)
        val validation = validateBackupJson(backupJsonString, effectiveSections)
        if (validation is BackupValidationResult.Invalid) {
            Log.e("AppSettings", "Created backup payload failed validation: ${validation.reason}")
            throw IllegalStateException("Backup payload validation failed: ${validation.reason}")
        }
        return backupJsonString
    }
    
    /**
     * Restores app data from a backup JSON string
     */
    fun restoreFromBackup(
        backupJson: String,
        sections: BackupRestoreSections = BackupRestoreSections()
    ): Boolean {
        val validation = validateBackupJson(backupJson, sections)
        if (validation is BackupValidationResult.Invalid) {
            Log.e("AppSettings", "Restore rejected due to invalid backup payload: ${validation.reason}")
            return false
        }

        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
            val backupData = GsonUtils.gson.fromJson<Map<String, Any?>>(backupJson, type) ?: return false
            val preferences = (backupData["preferences"] as? Map<*, *>)
                ?.mapNotNull { (key, value) ->
                    (key as? String)?.let { safeKey -> safeKey to value }
                }
                ?.toMap()
                ?: return false
            val preferencesTypes = (backupData["preferences_types"] as? Map<*, *>)
                ?.mapNotNull { (key, value) ->
                    val safeKey = key as? String ?: return@mapNotNull null
                    val safeValue = value as? String ?: return@mapNotNull null
                    safeKey to safeValue
                }
                ?.toMap()
                ?: emptyMap()
            val backupVersion = (backupData["backup_version"] as? Double)?.toInt() ?: 1

            if (!sections.hasAtLeastOneSectionSelected) {
                Log.w("AppSettings", "Restore skipped: no backup sections selected")
                return false
            }
            
            Log.d("AppSettings", "Backup version: $backupVersion")
            
            // Clear existing preferences (optional - you might want to merge instead)
            prefs.edit {
            
            // Restore all preferences with proper type handling
            preferences.forEach { (key, value) ->
                if (!shouldIncludeKeyInBackupSections(key, sections) || isRhythmGuardTransientRuntimeKey(key)) {
                    return@forEach
                }

                val originalType = preferencesTypes[key]
                applyBackupPreferenceValue(this, key, value, originalType)
                // Migrate legacy "rhythm_aura_" keys to new "rhythm_guard_" equivalents
                if (key.startsWith("rhythm_aura_") && !preferences.containsKey(key.replaceFirst("rhythm_aura_", "rhythm_guard_"))) {
                    val guardKey = key.replaceFirst("rhythm_aura_", "rhythm_guard_")
                    if (shouldIncludeKeyInBackupSections(guardKey, sections)) {
                        applyBackupPreferenceValue(this, guardKey, value, originalType)
                        Log.d("AppSettings", "Migrated legacy key $key -> $guardKey during restore")
                    }
                }
            }

            if (sections.includeStatsAndRhythmGuard) {
                val statsData = (backupData["stats_rhythm_guard_data"] as? Map<*, *>)
                    ?.mapNotNull { (key, value) ->
                        (key as? String)?.let { safeKey -> safeKey to value }
                    }
                    ?.toMap()
                    ?: emptyMap()
                val statsTypes = (backupData["stats_rhythm_guard_types"] as? Map<*, *>)
                    ?.mapNotNull { (key, value) ->
                        val safeKey = key as? String ?: return@mapNotNull null
                        val safeValue = value as? String ?: return@mapNotNull null
                        safeKey to safeValue
                    }
                    ?.toMap()
                    ?: emptyMap()

                statsData.forEach { (key, value) ->
                    if (isStatsAndRhythmGuardBackupKey(key) && !isRhythmGuardTransientRuntimeKey(key)) {
                        applyBackupPreferenceValue(this, key, value, statsTypes[key] ?: preferencesTypes[key])
                        // Also migrate legacy aura keys to rhythm_guard_ counterparts
                        if (key.startsWith("rhythm_aura_") && !statsData.containsKey(key.replaceFirst("rhythm_aura_", "rhythm_guard_"))) {
                            val guardKey = key.replaceFirst("rhythm_aura_", "rhythm_guard_")
                            applyBackupPreferenceValue(this, guardKey, value, statsTypes[guardKey] ?: statsTypes[key] ?: preferencesTypes[key])
                            Log.d("AppSettings", "Migrated legacy stats key $key -> $guardKey during restore")
                        }
                    }
                }

                val playbackHistoryJson = backupData["playback_history"] as? String
                if (!playbackHistoryJson.isNullOrBlank()) {
                    try {
                        val historyFile = File(context.filesDir, "playback_history.json")
                        historyFile.writeText(playbackHistoryJson)
                        Log.d("AppSettings", "Restored playback_history.json: ${playbackHistoryJson.length} chars")
                    } catch (e: Exception) {
                        Log.e("AppSettings", "Failed to restore playback_history.json", e)
                    }
                }
            }
            
            // Handle backup version 2 and above - explicit playlist data restoration
            if (backupVersion >= 2 && sections.includeLibraryData) {
                Log.d("AppSettings", "Restoring playlist data from backup version $backupVersion")
                
                // Restore playlists data explicitly
                val playlistsData = backupData["playlists_data"] as? String
                if (playlistsData != null) {
                    try {
                        val playlistListType = object : TypeToken<List<Playlist>>() {}.type
                        val restoredPlaylists: List<Playlist> = GsonUtils.gson.fromJson(playlistsData, playlistListType) ?: emptyList()
                        
                        kotlinx.coroutines.runBlocking {
                            val db = chromahub.rhythm.app.features.local.data.database.RhythmDatabase.getInstance(context)
                            val playlistDao = db.playlistDao()
                            val songDao = db.songDao()
                            
                            playlistDao.deleteAllPlaylists()
                            playlistDao.deleteAllPlaylistSongs()
                            
                            val songEntitiesToInsert = mutableListOf<chromahub.rhythm.app.features.local.data.database.entity.SongEntity>()
                            
                            restoredPlaylists.forEach { playlist ->
                                val playlistEntity = chromahub.rhythm.app.features.local.data.database.entity.PlaylistEntity(
                                    id = playlist.id,
                                    name = playlist.name,
                                    dateCreated = playlist.dateCreated,
                                    dateModified = playlist.dateModified,
                                    artworkUri = playlist.artworkUri?.toString()
                                )
                                playlistDao.insertPlaylist(playlistEntity)
                                
                                playlist.songs.forEach { song ->
                                    val songEntity = chromahub.rhythm.app.features.local.data.database.entity.SongEntity(
                                        id = song.id,
                                        title = song.title,
                                        artist = song.artist,
                                        album = song.album,
                                        albumId = song.albumId,
                                        duration = song.duration,
                                        uri = song.uri.toString(),
                                        artworkUri = song.artworkUri?.toString(),
                                        trackNumber = song.trackNumber,
                                        year = song.year,
                                        genre = song.genre,
                                        dateAdded = song.dateAdded,
                                        dateModified = song.dateModified,
                                        albumArtist = song.albumArtist,
                                        bitrate = song.bitrate,
                                        sampleRate = song.sampleRate,
                                        channels = song.channels,
                                        codec = song.codec,
                                        discNumber = song.discNumber,
                                        path = song.path
                                    )
                                    songEntitiesToInsert.add(songEntity)
                                }
                                
                                val songEntities = playlist.songs.mapIndexed { index, song ->
                                    chromahub.rhythm.app.features.local.data.database.entity.PlaylistSongEntity(
                                        playlistId = playlist.id,
                                        songId = song.id,
                                        orderIndex = index
                                    )
                                }
                                playlistDao.insertPlaylistSongs(songEntities)
                            }
                            
                            if (songEntitiesToInsert.isNotEmpty()) {
                                val distinctSongs = songEntitiesToInsert.distinctBy { it.id }
                                songDao.insertAll(distinctSongs)
                                Log.d("AppSettings", "Restored ${distinctSongs.size} unique song metadata records directly to Room database")
                            }
                        }
                        Log.d("AppSettings", "Restored playlists directly to Room database")
                    } catch (e: Exception) {
                        Log.e("AppSettings", "Failed to restore playlists to Room database", e)
                        putString(KEY_PLAYLISTS, playlistsData)
                    }
                }
                
                // Restore favorite songs data explicitly
                val favoriteSongsData = backupData["favorite_songs_data"] as? String
                if (favoriteSongsData != null) {
                    putString(KEY_FAVORITE_SONGS, favoriteSongsData)
                    Log.d("AppSettings", "Restored favorite songs data: ${favoriteSongsData.length} characters")
                }
                
                // Restore blacklisted songs and folders
                val blacklistedSongsData = backupData["blacklisted_songs_data"] as? String
                if (blacklistedSongsData != null) {
                    putString(KEY_BLACKLISTED_SONGS, blacklistedSongsData)
                    Log.d("AppSettings", "Restored blacklisted songs data")
                }
                
                val blacklistedFoldersData = backupData["blacklisted_folders_data"] as? String
                if (blacklistedFoldersData != null) {
                    putString(KEY_BLACKLISTED_FOLDERS, blacklistedFoldersData)
                    Log.d("AppSettings", "Restored blacklisted folders data")
                }
                
                // Restore whitelisted songs and folders
                val whitelistedSongsData = backupData["whitelisted_songs_data"] as? String
                if (whitelistedSongsData != null) {
                    putString(KEY_WHITELISTED_SONGS, whitelistedSongsData)
                    Log.d("AppSettings", "Restored whitelisted songs data")
                }
                
                val whitelistedFoldersData = backupData["whitelisted_folders_data"] as? String
                if (whitelistedFoldersData != null) {
                    putString(KEY_WHITELISTED_FOLDERS, whitelistedFoldersData)
                    Log.d("AppSettings", "Restored whitelisted folders data")
                }
                
                // Restore pinned folders
                val pinnedFoldersData = backupData["pinned_folders_data"] as? String
                if (pinnedFoldersData != null) {
                    putString(KEY_PINNED_FOLDERS, pinnedFoldersData)
                    Log.d("AppSettings", "Restored pinned folders data")
                }
            } else if (backupVersion < 2) {
                Log.d("AppSettings", "Backup version $backupVersion - using preferences-based restoration")
            }
            
            }
            
            // Refresh all StateFlows to reflect the restored data
            refreshAllStateFlows()
            clearRhythmGuardListeningTimeout()
            clearRhythmGuardTimeoutCooldown()
            
            Log.d("AppSettings", "Backup restoration completed successfully")
            true
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to restore backup", e)
            false
        }
    }

    fun applyBackupPreferenceValue(
        editor: SharedPreferences.Editor,
        key: String,
        value: Any?,
        originalType: String?
    ) {
        val existingValue = prefs.all[key]
        val resolvedType = originalType ?: when (existingValue) {
            is Boolean -> "Boolean"
            is Float -> "Float"
            is Int -> "Int"
            is Long -> "Long"
            is String -> "String"
            is Set<*> -> "StringSet"
            else -> {
                when {
                    key == "listening_time" || key.endsWith("_timestamp") || key.endsWith("_ms") || key.endsWith("_at") -> "Long"
                    key == "songs_played" || key == "unique_artists" || key == "rhythm_guard_age" || key.endsWith("_count") || key.endsWith("_limit") || key.endsWith("_index") -> "Int"
                    key == "gapless_playback" || key == "crossfade" || key.endsWith("_enabled") || key.endsWith("_active") -> "Boolean"
                    key == "crossfade_duration" || key.endsWith("_strength") || key.endsWith("_threshold") || key.endsWith("_speed") || key.endsWith("_pitch") -> "Float"
                    else -> null
                }
            }
        }

        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Float -> {
                when (resolvedType) {
                    "Long" -> editor.putLong(key, value.toLong())
                    "Int" -> editor.putInt(key, value.toInt())
                    else -> editor.putFloat(key, value)
                }
            }
            is Int -> {
                when (resolvedType) {
                    "Long" -> editor.putLong(key, value.toLong())
                    "Float" -> editor.putFloat(key, value.toFloat())
                    else -> editor.putInt(key, value)
                }
            }
            is Long -> {
                when (resolvedType) {
                    "Int" -> editor.putInt(key, value.toInt())
                    "Float" -> editor.putFloat(key, value.toFloat())
                    else -> editor.putLong(key, value)
                }
            }
            is String -> editor.putString(key, value)
            is List<*> -> {
                if (resolvedType == "StringSet") {
                    editor.putStringSet(key, value.mapNotNull { it?.toString() }.toSet())
                }
            }
            is Set<*> -> {
                if (resolvedType == "StringSet") {
                    editor.putStringSet(key, value.mapNotNull { it?.toString() }.toSet())
                }
            }
            is Double -> {
                when (resolvedType) {
                    "Float" -> editor.putFloat(key, value.toFloat())
                    "Long" -> editor.putLong(key, value.toLong())
                    "Int" -> editor.putInt(key, value.toInt())
                    "Boolean" -> editor.putBoolean(key, value != 0.0)
                    else -> editor.putFloat(key, value.toFloat())
                }
            }
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
                prefs.edit { putLong(key, defaultValue) }
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
            
            prefs.edit {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    else -> {
                        Log.w("AppSettings", "Unsupported value type for key $key")
                        return false
                    }
                }
            }
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
        return duration in 0.1f..12.0f
    }
    
    /**
     * Validates update check interval
     */
    private fun isValidUpdateInterval(hours: Int): Boolean {
        return hours in 1..168 // 1 hour to 1 week
    }
    
    /**
     * Refreshes all StateFlows to reflect current SharedPreferences values
     */
    private fun refreshAllStateFlows() {
        // Playback Settings
        _gaplessPlayback.value = prefs.getBoolean(KEY_GAPLESS_PLAYBACK, true)
        _crossfade.value = prefs.getBoolean(KEY_CROSSFADE, true)
        _crossfadeDuration.value = prefs.getFloat(KEY_CROSSFADE_DURATION, 4f)
        _crossfadeRepeatOne.value = prefs.getBoolean(KEY_CROSSFADE_REPEAT_ONE, true)
        _audioNormalization.value = prefs.getBoolean(KEY_AUDIO_NORMALIZATION, true)
        _replayGain.value = prefs.getBoolean(KEY_REPLAY_GAIN, false)
        _replayGainMode.value = prefs.getInt(KEY_REPLAY_GAIN_MODE, 1)
        _replayGainDrc.value = prefs.getBoolean(KEY_REPLAY_GAIN_DRC, true)
        _replayGainPreamp.value = prefs.getFloat(KEY_REPLAY_GAIN_PREAMP, 0f)
        _replayGainPreampUntagged.value = prefs.getFloat(KEY_REPLAY_GAIN_PREAMP_UNTAGGED, 0f)
        _audioOffloadEnabled.value = prefs.getBoolean(KEY_AUDIO_OFFLOAD_ENABLED, true)
        _skipSilenceEnabled.value = prefs.getBoolean(KEY_SKIP_SILENCE, false)
        _batterySaverEnabled.value = prefs.getBoolean(KEY_BATTERY_SAVER_ENABLED, false)
        _batterySaverMode.value = prefs.getString(KEY_BATTERY_SAVER_MODE, "auto") ?: "auto"
        _batterySaverDisableHaptics.value = prefs.getBoolean(KEY_BATTERY_SAVER_DISABLE_HAPTICS, true)
        _batterySaverEnableOffload.value = prefs.getBoolean(KEY_BATTERY_SAVER_ENABLE_OFFLOAD, true)
        _batterySaverDisableMarquee.value = prefs.getBoolean(KEY_BATTERY_SAVER_DISABLE_MARQUEE, true)
        _batterySaverDisableLosslessArtwork.value = prefs.getBoolean(KEY_BATTERY_SAVER_DISABLE_LOSSLESS_ARTWORK, true)
        _batterySaverDisableAutoFetchArtwork.value = prefs.getBoolean(KEY_BATTERY_SAVER_DISABLE_AUTO_FETCH_ARTWORK, true)
        _globalMarqueeEnabled.value = prefs.getBoolean(KEY_GLOBAL_MARQUEE_ENABLED, true)
        updateDerivedSettings()
        
        // Theme Settings
        _useSystemTheme.value = prefs.getBoolean(KEY_USE_SYSTEM_THEME, true)
        _darkMode.value = prefs.getBoolean(KEY_DARK_MODE, true)
        _amoledTheme.value = prefs.getBoolean(KEY_AMOLED_THEME, false)
        _floatingNavigationBar.value = prefs.getBoolean(KEY_FLOATING_NAVIGATION_BAR, true)
        _useDynamicColors.value = prefs.getBoolean(KEY_USE_DYNAMIC_COLORS, false)
        _customColorScheme.value = prefs.getString(KEY_CUSTOM_COLOR_SCHEME, "Default") ?: "Default"
        _customFont.value = prefs.getString(KEY_CUSTOM_FONT, "Geom") ?: "Geom"
        
        // Library Settings
        _albumViewType.value = AlbumViewType.valueOf(prefs.getString(KEY_ALBUM_VIEW_TYPE, AlbumViewType.GRID.name) ?: AlbumViewType.GRID.name)
        _artistViewType.value = ArtistViewType.valueOf(prefs.getString(KEY_ARTIST_VIEW_TYPE, ArtistViewType.GRID.name) ?: ArtistViewType.GRID.name)
        _albumSortOrder.value = prefs.getString(KEY_ALBUM_SORT_ORDER, "TRACK_NUMBER") ?: "TRACK_NUMBER"
        _artistCollaborationMode.value = prefs.getBoolean(KEY_ARTIST_COLLABORATION_MODE, false)
        _songsSortOrder.value = prefs.getString(KEY_SONGS_SORT_ORDER, "TITLE_ASC") ?: "TITLE_ASC"
        _libraryCombineDiscs.value = prefs.getBoolean(KEY_LIBRARY_COMBINE_DISCS, false)
        _preferSongArtwork.value = if (prefs.contains(KEY_PREFER_SONG_ARTWORK)) {
            prefs.getBoolean(KEY_PREFER_SONG_ARTWORK, true)
        } else {
            prefs.getBoolean(KEY_IGNORE_MEDIASTORE_COVERS, true)
        }
        _albumBottomSheetDiscFilter.value = prefs.getInt(KEY_ALBUM_BOTTOM_SHEET_DISC_FILTER, 0).coerceAtLeast(0)
        _albumBottomSheetGradientBlur.value = prefs.getBoolean(KEY_ALBUM_BOTTOM_SHEET_GRADIENT_BLUR, true)
        _showLibrarySectionHeaders.value = prefs.getBoolean(KEY_SHOW_LIBRARY_SECTION_HEADERS, true)
        _showLibraryBottomBarAlways.value = prefs.getBoolean(KEY_SHOW_LIBRARY_BOTTOM_BAR_ALWAYS, false)
        
        // Audio Device Settings
        _lastAudioDevice.value = prefs.getString(KEY_LAST_AUDIO_DEVICE, null)
        _autoConnectDevice.value = prefs.getBoolean(KEY_AUTO_CONNECT_DEVICE, true)
        _useSystemVolume.value = prefs.getBoolean(KEY_USE_SYSTEM_VOLUME, true)
        _appVolume.value = prefs.getFloat(KEY_APP_VOLUME, 1.0f)
        _stopPlaybackOnZeroVolume.value = prefs.getBoolean(KEY_STOP_PLAYBACK_ON_ZERO_VOLUME, false)
        
        // Cache Settings
        _maxCacheSize.value = safeLong(KEY_MAX_CACHE_SIZE, 300L * 1024L * 1024L)
        
        // Other settings
        _showLyrics.value = prefs.getBoolean(KEY_SHOW_LYRICS, true)
        _tapLyricsToFullScreen.value = prefs.getBoolean(KEY_TAP_LYRICS_TO_FULL_SCREEN, true)
        _lyricsApiPriority.value = LyricsApiPriority.fromOrdinal(
            prefs.getInt(KEY_LYRICS_API_PRIORITY, LyricsApiPriority.LYRICALLY_FIRST.ordinal)
        )
        _lyricsApiFallbackRetry.value = prefs.getBoolean(KEY_LYRICS_API_FALLBACK_RETRY, true)
        _onlineOnlyLyrics.value = prefs.getBoolean(KEY_ONLINE_ONLY_LYRICS, true)
        _lyricBold.value = prefs.getBoolean(KEY_LYRIC_BOLD, false)
        _trimLyrics.value = prefs.getBoolean(KEY_TRIM_LYRICS, true)
        _lyricNoAnimation.value = prefs.getBoolean(KEY_LYRIC_NO_ANIMATION, false)
        _translationAutoWord.value = prefs.getBoolean(KEY_TRANSLATION_AUTO_WORD, false)
        _showLyricsBackgroundArtwork.value = prefs.getBoolean(KEY_SHOW_LYRICS_BACKGROUND_ARTWORK, true)
        _searchHistory.value = prefs.getString(KEY_SEARCH_HISTORY, null)
        _showKeyboardOnSearchOpen.value = prefs.getBoolean(KEY_SHOW_KEYBOARD_ON_SEARCH_OPEN, true)
        _playlists.value = prefs.getString(KEY_PLAYLISTS, null)
        _favoriteSongs.value = prefs.getString(KEY_FAVORITE_SONGS, null)
        
        // User Statistics
        _listeningTime.value = safeLong(KEY_LISTENING_TIME, 0L)
        _songsPlayed.value = prefs.getInt(KEY_SONGS_PLAYED, 0)
        _uniqueArtists.value = prefs.getInt(KEY_UNIQUE_ARTISTS, 0)
        _rhythmAuraMode.value = sanitizeRhythmGuardMode(
            prefs.getString(KEY_RHYTHM_GUARD_MODE, prefs.getString(KEY_RHYTHM_AURA_MODE, RHYTHM_GUARD_MODE_OFF))
        )
        _rhythmAuraAge.value = prefs.getInt(KEY_RHYTHM_GUARD_AGE, prefs.getInt(KEY_RHYTHM_AURA_AGE, 18)).coerceIn(8, 80)
        _rhythmAuraManualWarningsEnabled.value = prefs.getBoolean(
            KEY_RHYTHM_GUARD_MANUAL_WARNINGS_ENABLED,
            prefs.getBoolean(KEY_RHYTHM_AURA_MANUAL_WARNINGS_ENABLED, true)
        )
        _rhythmAuraManualVolumeThreshold.value = prefs.getFloat(
            KEY_RHYTHM_GUARD_MANUAL_VOLUME_THRESHOLD,
            prefs.getFloat(KEY_RHYTHM_AURA_MANUAL_VOLUME_THRESHOLD, 0.68f)
        ).coerceIn(0.40f, 0.95f)
        _rhythmGuardApplyVolumeLimitOnSpeaker.value = prefs.getBoolean(
            KEY_RHYTHM_GUARD_APPLY_VOLUME_LIMIT_ON_SPEAKER,
            false
        )
        _rhythmAuraLastAutoAppliedAt.value = safeLong(
            KEY_RHYTHM_GUARD_LAST_AUTO_APPLIED_AT,
            safeLong(KEY_RHYTHM_AURA_LAST_AUTO_APPLIED_AT, 0L)
        )
        _rhythmGuardAlertThresholdMinutes.value = prefs.getInt(KEY_RHYTHM_GUARD_ALERT_THRESHOLD_MINUTES, -1).coerceIn(-1, 24 * 60)
        _rhythmGuardWarningTimeoutMinutes.value = prefs.getInt(KEY_RHYTHM_GUARD_WARNING_TIMEOUT_MINUTES, 2).coerceIn(1, 60)
        _rhythmGuardPostTimeoutCooldownMinutes.value = prefs.getInt(KEY_RHYTHM_GUARD_POST_TIMEOUT_COOLDOWN_MINUTES, 10).coerceIn(1, 60)
        _rhythmGuardBreakResumeMinutes.value = prefs.getInt(KEY_RHYTHM_GUARD_BREAK_RESUME_MINUTES, 15).coerceIn(1, 180)
        _rhythmGuardTimeoutUntilMs.value = safeLong(KEY_RHYTHM_GUARD_TIMEOUT_UNTIL_MS, 0L).coerceAtLeast(0L)
        _rhythmGuardTimeoutReason.value = prefs.getString(KEY_RHYTHM_GUARD_TIMEOUT_REASON, null).orEmpty()
        _rhythmGuardTimeoutStartedAtMs.value = safeLong(KEY_RHYTHM_GUARD_TIMEOUT_STARTED_AT_MS, 0L).coerceAtLeast(0L)
        _rhythmGuardTimeoutCooldownUntilMs.value = safeLong(KEY_RHYTHM_GUARD_TIMEOUT_COOLDOWN_UNTIL_MS, 0L).coerceAtLeast(0L)
        _rhythmGuardNextAllowedLimitMinutes.value = prefs.getInt(KEY_RHYTHM_GUARD_NEXT_ALLOWED_LIMIT_MINUTES, 0)
        _rhythmGuardFirstBreakSeen.value = prefs.getBoolean(KEY_RHYTHM_GUARD_FIRST_BREAK_SEEN, false)
        _homeShowRhythmGuard.value = prefs.getBoolean(KEY_HOME_SHOW_RHYTHM_GUARD, true)
        
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
        _recentlyPlayedSongCache.value = loadRecentlyPlayedSongCache()
        
        _lastPlayedTimestamp.value = safeLong(KEY_LAST_PLAYED_TIMESTAMP, 0L)
        
        // API Enable/Disable States
        _deezerApiEnabled.value = prefs.getBoolean(KEY_DEEZER_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _lrclibApiEnabled.value = prefs.getBoolean(KEY_LRCLIB_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _betterLyricsApiEnabled.value = prefs.getBoolean(KEY_BETTERLYRICS_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _ytMusicApiEnabled.value = prefs.getBoolean(KEY_YTMUSIC_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _spotifyApiEnabled.value = prefs.getBoolean(KEY_SPOTIFY_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _lyricallyApiEnabled.value = prefs.getBoolean(KEY_LYRICALLY_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _wikipediaApiEnabled.value = prefs.getBoolean(KEY_WIKIPEDIA_API_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _appleCanvasEnabled.value = prefs.getBoolean(KEY_APPLE_CANVAS_ENABLED, true)
        _appleCanvasNetworkMode.value = CanvasNetworkMode.fromOrdinal(
            prefs.getInt(KEY_APPLE_CANVAS_NETWORK_MODE, CanvasNetworkMode.BOTH.ordinal)
        )
        _lyricallySourcesOrder.value = prefs.getString(KEY_LYRICALLY_SOURCES_ORDER, null)
            ?.split(",")
            ?.filter { it.isNotBlank() && it in defaultLyricallySources }
            ?.takeIf { it.isNotEmpty() }
            ?: defaultLyricallySources
        _disabledLyricallySources.value = prefs.getString(KEY_DISABLED_LYRICALLY_SOURCES, null)
            ?.split(",")
            ?.filter { it.isNotBlank() && it in defaultLyricallySources }
            ?.toSet()
            ?: defaultDisabledLyricallySources
        _autoFetchArtwork.value = prefs.getBoolean(KEY_AUTO_FETCH_ARTWORK, true)
        _spotifyClientId.value = prefs.getString(KEY_SPOTIFY_CLIENT_ID, "") ?: ""
        _spotifyClientSecret.value = prefs.getString(KEY_SPOTIFY_CLIENT_SECRET, "") ?: ""

        // General Broadcast Status Settings
        _broadcastStatusEnabled.value = prefs.getBoolean(KEY_BROADCAST_STATUS_ENABLED, false)
        _bluetoothLyricsEnabled.value = prefs.getBoolean(KEY_BLUETOOTH_LYRICS_ENABLED, false)
        
        // App Updates
        _autoCheckForUpdates.value = prefs.getBoolean(KEY_AUTO_CHECK_FOR_UPDATES, BuildConfig.FLAVOR != "fdroid")
        _updateChannel.value = prefs.getString(KEY_UPDATE_CHANNEL, "stable") ?: "stable"
        _updateSource.value = prefs.getString(KEY_UPDATE_SOURCE, "installed") ?: "installed"
        _updatesEnabled.value = prefs.getBoolean(KEY_UPDATES_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _updateNotificationsEnabled.value = prefs.getBoolean(KEY_UPDATE_NOTIFICATIONS_ENABLED, BuildConfig.FLAVOR != "fdroid")
        _updateStatusNotificationsEnabled.value = prefs.getBoolean(KEY_UPDATE_STATUS_NOTIFICATIONS_ENABLED, false)
        _mediaScanMode.value = MediaScanMode.fromValue(prefs.getString(KEY_MEDIA_SCAN_MODE, "blacklist") ?: "blacklist")
        _includeHiddenWhitelistedMedia.value = prefs.getBoolean(KEY_INCLUDE_HIDDEN_WHITELISTED_MEDIA, true)
        _updateCheckIntervalHours.value = prefs.getInt(KEY_UPDATE_CHECK_INTERVAL_HOURS, 6)
        
        // Re-schedule update notification worker if settings changed
        if (shouldRunUpdateNotificationWorker()) {
            scheduleUpdateNotificationWorker()
        } else {
            cancelUpdateNotificationWorker()
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
        _useCustomNotification.value = prefs.getBoolean(KEY_USE_CUSTOM_NOTIFICATION, false)
        _rhythmGuardAlertNotificationsEnabled.value = prefs.getBoolean(KEY_RHYTHM_GUARD_ALERT_NOTIFICATIONS_ENABLED, true)
        _rhythmGuardTimerNotificationsEnabled.value = prefs.getBoolean(KEY_RHYTHM_GUARD_TIMER_NOTIFICATIONS_ENABLED, true)
        _rhythmPulseNotificationsEnabled.value = prefs.getBoolean(KEY_RHYTHM_PULSE_NOTIFICATIONS_ENABLED, false)
        _rhythmPulseNotificationIntervalHours.value = prefs.getInt(KEY_RHYTHM_PULSE_NOTIFICATION_INTERVAL_HOURS, 24).coerceIn(6, 72)
        _forcePlayerCompactMode.value = prefs.getBoolean(KEY_FORCE_PLAYER_COMPACT_MODE, false)
        _useExperimentalPlayerUi.value = prefs.getBoolean(KEY_USE_EXPERIMENTAL_PLAYER_UI, false)
        _enableAlbumEditing.value = prefs.getBoolean(KEY_ENABLE_ALBUM_EDITING, false)
        _onboardingCompleted.value = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        _initialMediaScanCompleted.value = prefs.getBoolean(KEY_INITIAL_MEDIA_SCAN_COMPLETED, false)
        _genreDetectionCompleted.value = prefs.getBoolean(KEY_GENRE_DETECTION_COMPLETED, false)

        if (_rhythmPulseNotificationsEnabled.value) {
            scheduleRhythmPulseNotificationWorker()
        } else {
            cancelRhythmPulseNotificationWorker()
        }
        
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
        _allowedFormats.value = prefs.getStringSet(KEY_ALLOWED_FORMATS, defaultAllowedFormats())?.toSet() ?: defaultAllowedFormats()
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
        _widgetShowAlbum.value = prefs.getBoolean(KEY_WIDGET_SHOW_ALBUM, false)
        _widgetCornerRadius.value = prefs.getInt(KEY_WIDGET_CORNER_RADIUS, 28)
        _widgetAutoUpdate.value = prefs.getBoolean(KEY_WIDGET_AUTO_UPDATE, true)
        _widgetShowFavoriteButton.value = prefs.getBoolean(KEY_WIDGET_SHOW_FAVORITE_BUTTON, true)
        _widgetTheme.value = prefs.getInt(KEY_WIDGET_THEME, 0)
        _widgetCookieBottomLeft.value = prefs.getInt(KEY_WIDGET_COOKIE_BOTTOM_LEFT, 3)
        _widgetCookieBottomRight.value = prefs.getInt(KEY_WIDGET_COOKIE_BOTTOM_RIGHT, 4)
        _widgetStatsRange.value = prefs.getInt(KEY_WIDGET_STATS_RANGE, 0)
        _widgetStatsGem.value = prefs.getInt(KEY_WIDGET_STATS_GEM, 0)
        
        // Player Screen Customization Settings
        _playerShowGradientOverlay.value = prefs.getBoolean(KEY_PLAYER_SHOW_GRADIENT_OVERLAY, true)
        _playerArtOverlayType.value = prefs.getInt(KEY_PLAYER_ART_OVERLAY_TYPE, 0)
        _playerArtOverlayIntensity.value = prefs.getFloat(KEY_PLAYER_ART_OVERLAY_INTENSITY, 1.0f)
        _playerLyricsOverlayType.value = prefs.getInt(KEY_PLAYER_LYRICS_OVERLAY_TYPE, 0)
        _playerLyricsOverlayIntensity.value = prefs.getFloat(KEY_PLAYER_LYRICS_OVERLAY_INTENSITY, 0.1f)
        _playerLyricsTransition.value = prefs.getInt(KEY_PLAYER_LYRICS_TRANSITION, 2) // 2 = Scale
        _playerAmbientBackdropEnabled.value = prefs.getBoolean(KEY_PLAYER_AMBIENT_BACKDROP_ENABLED, false)
        _playerAmbientBackdropIntensity.value = prefs.getFloat(KEY_PLAYER_AMBIENT_BACKDROP_INTENSITY, 0.85f)
        _playerAmbientInfiniteZoom.value = prefs.getBoolean(KEY_PLAYER_AMBIENT_INFINITE_ZOOM, true)
        _playerAccentBackgroundEnabled.value = prefs.getBoolean(KEY_PLAYER_ACCENT_BACKGROUND_ENABLED, true)
        // Normalize legacy state: ambient and accent cannot both be on — ambient wins.
        if (_playerAmbientBackdropEnabled.value && _playerAccentBackgroundEnabled.value) {
            _playerAccentBackgroundEnabled.value = false
            prefs.edit { putBoolean(KEY_PLAYER_ACCENT_BACKGROUND_ENABLED, false) }
        }
        _playerMergeControlsToBottom.value = prefs.getBoolean(KEY_PLAYER_MERGE_CONTROLS_TO_BOTTOM, true)
        _playerGlassIntensity.value = prefs.getFloat(KEY_PLAYER_GLASS_INTENSITY, 1.0f)
        _playerLyricsTextSize.value = prefs.getFloat(KEY_PLAYER_LYRICS_TEXT_SIZE, 1.0f)
        _playerLyricsAlignment.value = prefs.getString(KEY_PLAYER_LYRICS_ALIGNMENT, "CENTER") ?: "CENTER"
        _playerShowArtBelowLyrics.value = prefs.getBoolean(KEY_PLAYER_SHOW_ART_BELOW_LYRICS, true)
        _playerShowSeekButtons.value = prefs.getBoolean(KEY_PLAYER_SHOW_SEEK_BUTTONS, true)
        _playerTextAlignment.value = prefs.getString(KEY_PLAYER_TEXT_ALIGNMENT, "CENTER") ?: "CENTER"
        _playerShowSongInfoOnArtwork.value = prefs.getBoolean(KEY_PLAYER_SHOW_SONG_INFO_ON_ARTWORK, true)
        _playerArtworkCornerRadius.value = prefs.getInt(KEY_PLAYER_ARTWORK_CORNER_RADIUS, 28)
        _playerShowAudioQualityBadges.value = prefs.getBoolean(KEY_PLAYER_SHOW_AUDIO_QUALITY_BADGES, true)
        
        // Lyrics song preferences and renaming behavior
        _lrcRenameBehavior.value = prefs.getString(KEY_LRC_RENAME_BEHAVIOR, "ask") ?: "ask"
        _songLyricsPreferences.value = try {
            val json = prefs.getString(KEY_SONG_LYRICS_PREFERENCES, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<Map<String, String>>() {}.type) else emptyMap()
        } catch (e: Exception) { emptyMap() }
        _songCustomLrcFiles.value = try {
            val json = prefs.getString(KEY_SONG_CUSTOM_LRC_FILES, null)
            if (json != null) Gson().fromJson(json, object : TypeToken<Map<String, String>>() {}.type) else emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    fun getSongLyricsPreference(songId: String): String? {
        return _songLyricsPreferences.value[songId]
    }

    fun setSongLyricsPreference(songId: String, preference: String?) {
        val currentPrefs = _songLyricsPreferences.value.toMutableMap()
        if (preference == null) {
            currentPrefs.remove(songId)
        } else {
            currentPrefs[songId] = preference
        }
        _songLyricsPreferences.value = currentPrefs
        prefs.edit { putString(KEY_SONG_LYRICS_PREFERENCES, Gson().toJson(currentPrefs)) }
    }

    fun getSongCustomLrcFile(songId: String): String? {
        return _songCustomLrcFiles.value[songId]
    }

    fun setSongCustomLrcFile(songId: String, path: String?) {
        val currentFiles = _songCustomLrcFiles.value.toMutableMap()
        if (path == null) {
            currentFiles.remove(songId)
        } else {
            currentFiles[songId] = path
        }
        _songCustomLrcFiles.value = currentFiles
        prefs.edit { putString(KEY_SONG_CUSTOM_LRC_FILES, Gson().toJson(currentFiles)) }
    }

    fun setLrcRenameBehavior(value: String) {
        _lrcRenameBehavior.value = value
        prefs.edit { putString(KEY_LRC_RENAME_BEHAVIOR, value) }
    }

    fun exportLyricsPreferencesToCsv(context: Context, uri: Uri): Boolean {
        return try {
            val contentResolver = context.contentResolver
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(outputStream))
                writer.write("Song ID,Title,Artist,Source Preference,Custom LRC Filename\n")
                
                val currentPrefs = _songLyricsPreferences.value
                val customFiles = _songCustomLrcFiles.value
                val allSongIds = currentPrefs.keys + customFiles.keys
                
                val songMeta = mutableMapOf<String, Pair<String, String>>()
                try {
                    val projection = arrayOf(android.provider.MediaStore.Audio.Media._ID, android.provider.MediaStore.Audio.Media.TITLE, android.provider.MediaStore.Audio.Media.ARTIST)
                    context.contentResolver.query(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media._ID)
                        val titleCol = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE)
                        val artistCol = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST)
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(idCol)
                            val title = cursor.getString(titleCol).orEmpty()
                            val artist = cursor.getString(artistCol).orEmpty()
                            songMeta[id] = Pair(title, artist)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AppSettings", "Error querying song metadata for CSV export", e)
                }

                for (id in allSongIds) {
                    val meta = songMeta[id]
                    val title = meta?.first?.replace("\"", "\"\"") ?: ""
                    val artist = meta?.second?.replace("\"", "\"\"") ?: ""
                    val pref = currentPrefs[id] ?: ""
                    val file = customFiles[id] ?: ""
                    
                    writer.write("\"$id\",\"$title\",\"$artist\",\"$pref\",\"$file\"\n")
                }
                writer.flush()
            }
            true
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to export lyrics preferences to CSV", e)
            false
        }
    }

    fun importLyricsPreferencesFromCsv(context: Context, uri: Uri): Boolean {
        return try {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                val header = reader.readLine() // Read header
                
                val currentPrefs = _songLyricsPreferences.value.toMutableMap()
                val currentFiles = _songCustomLrcFiles.value.toMutableMap()
                
                var line = reader.readLine()
                while (line != null) {
                    val tokens = parseCsvLine(line)
                    if (tokens.size >= 5) {
                        val id = tokens[0].trim()
                        val pref = tokens[3].trim()
                        val file = tokens[4].trim()
                        
                        if (id.isNotEmpty()) {
                            if (pref.isNotEmpty()) {
                                currentPrefs[id] = pref
                            } else {
                                currentPrefs.remove(id)
                            }
                            
                            if (file.isNotEmpty()) {
                                currentFiles[id] = file
                            } else {
                                currentFiles.remove(id)
                            }
                        }
                    }
                    line = reader.readLine()
                }
                
                _songLyricsPreferences.value = currentPrefs
            prefs.edit {
                putString(KEY_SONG_LYRICS_PREFERENCES, Gson().toJson(currentPrefs))
            }
                
                _songCustomLrcFiles.value = currentFiles
                prefs.edit { putString(KEY_SONG_CUSTOM_LRC_FILES, Gson().toJson(currentFiles)) }
            }
            true
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to import lyrics preferences from CSV", e)
            false
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        var currentToken = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    currentToken.append('"')
                    i++ // Skip next quote
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(currentToken.toString())
                currentToken = StringBuilder()
            } else {
                currentToken.append(c)
            }
            i++
        }
        result.add(currentToken.toString())
        return result
    }
    
    // ==================== Widget Settings ====================
    
    private val _widgetShowAlbumArt = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_SHOW_ALBUM_ART, true))
    val widgetShowAlbumArt: StateFlow<Boolean> = _widgetShowAlbumArt.asStateFlow()
    fun setWidgetShowAlbumArt(value: Boolean) {
        _widgetShowAlbumArt.value = value
        prefs.edit { putBoolean(KEY_WIDGET_SHOW_ALBUM_ART, value) }
    }
    
    private val _widgetShowArtist = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_SHOW_ARTIST, true))
    val widgetShowArtist: StateFlow<Boolean> = _widgetShowArtist.asStateFlow()
    fun setWidgetShowArtist(value: Boolean) {
        _widgetShowArtist.value = value
        prefs.edit { putBoolean(KEY_WIDGET_SHOW_ARTIST, value) }
    }
    
    private val _widgetShowAlbum = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_SHOW_ALBUM, false))
    val widgetShowAlbum: StateFlow<Boolean> = _widgetShowAlbum.asStateFlow()
    fun setWidgetShowAlbum(value: Boolean) {
        _widgetShowAlbum.value = value
        prefs.edit { putBoolean(KEY_WIDGET_SHOW_ALBUM, value) }
    }
    
    private val _widgetCornerRadius = MutableStateFlow(prefs.getInt(KEY_WIDGET_CORNER_RADIUS, 28))
    val widgetCornerRadius: StateFlow<Int> = _widgetCornerRadius.asStateFlow()
    fun setWidgetCornerRadius(value: Int) {
        if (value in 0..60) {
            _widgetCornerRadius.value = value
            prefs.edit { putInt(KEY_WIDGET_CORNER_RADIUS, value) }
        }
    }
    
    private val _widgetAutoUpdate = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_AUTO_UPDATE, true))
    val widgetAutoUpdate: StateFlow<Boolean> = _widgetAutoUpdate.asStateFlow()
    fun setWidgetAutoUpdate(value: Boolean) {
        _widgetAutoUpdate.value = value
        prefs.edit { putBoolean(KEY_WIDGET_AUTO_UPDATE, value) }
    }
    
    private val _widgetShowFavoriteButton = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_SHOW_FAVORITE_BUTTON, true))
    val widgetShowFavoriteButton: StateFlow<Boolean> = _widgetShowFavoriteButton.asStateFlow()
    fun setWidgetShowFavoriteButton(value: Boolean) {
        _widgetShowFavoriteButton.value = value
        prefs.edit { putBoolean(KEY_WIDGET_SHOW_FAVORITE_BUTTON, value) }
    }
    
    private val _widgetTheme = MutableStateFlow(prefs.getInt(KEY_WIDGET_THEME, 0))
    val widgetTheme: StateFlow<Int> = _widgetTheme.asStateFlow()
    fun setWidgetTheme(value: Int) {
        _widgetTheme.value = value
        prefs.edit { putInt(KEY_WIDGET_THEME, value) }
    }
    
    private val _widgetCookieBottomLeft = MutableStateFlow(prefs.getInt(KEY_WIDGET_COOKIE_BOTTOM_LEFT, 3))
    val widgetCookieBottomLeft: StateFlow<Int> = _widgetCookieBottomLeft.asStateFlow()
    fun setWidgetCookieBottomLeft(value: Int) {
        _widgetCookieBottomLeft.value = value
        prefs.edit { putInt(KEY_WIDGET_COOKIE_BOTTOM_LEFT, value) }
    }
    
    private val _widgetCookieBottomRight = MutableStateFlow(prefs.getInt(KEY_WIDGET_COOKIE_BOTTOM_RIGHT, 4))
    val widgetCookieBottomRight: StateFlow<Int> = _widgetCookieBottomRight.asStateFlow()
    fun setWidgetCookieBottomRight(value: Int) {
        _widgetCookieBottomRight.value = value
        prefs.edit { putInt(KEY_WIDGET_COOKIE_BOTTOM_RIGHT, value) }
    }
    
    private val _widgetStatsRange = MutableStateFlow(prefs.getInt(KEY_WIDGET_STATS_RANGE, 0))
    val widgetStatsRange: StateFlow<Int> = _widgetStatsRange.asStateFlow()
    fun setWidgetStatsRange(value: Int) {
        _widgetStatsRange.value = value
        prefs.edit { putInt(KEY_WIDGET_STATS_RANGE, value) }
    }
    
    private val _widgetStatsGem = MutableStateFlow(prefs.getInt(KEY_WIDGET_STATS_GEM, 0))
    val widgetStatsGem: StateFlow<Int> = _widgetStatsGem.asStateFlow()
    fun setWidgetStatsGem(value: Int) {
        _widgetStatsGem.value = value
        prefs.edit { putInt(KEY_WIDGET_STATS_GEM, value) }
    }
    
    // ==================== Global Header Settings ====================
    
    private val _headerCollapseBehavior = MutableStateFlow(prefs.getInt(KEY_HEADER_COLLAPSE_BEHAVIOR, 0))
    val headerCollapseBehavior: StateFlow<Int> = _headerCollapseBehavior.asStateFlow()
    fun setHeaderCollapseBehavior(value: Int) {
        _headerCollapseBehavior.value = value
        prefs.edit { putInt(KEY_HEADER_COLLAPSE_BEHAVIOR, value) }
    }
    
    // ==================== Home Screen Customization Settings ====================
    
    private val _homeHeaderDisplayMode = MutableStateFlow(prefs.getInt(KEY_HOME_HEADER_DISPLAY_MODE, 1))
    val homeHeaderDisplayMode: StateFlow<Int> = _homeHeaderDisplayMode.asStateFlow()
    fun setHomeHeaderDisplayMode(value: Int) {
        _homeHeaderDisplayMode.value = value
        prefs.edit { putInt(KEY_HOME_HEADER_DISPLAY_MODE, value) }
    }
    
    private val _homeShowAppIcon = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_APP_ICON, false))
    val homeShowAppIcon: StateFlow<Boolean> = _homeShowAppIcon.asStateFlow()
    fun setHomeShowAppIcon(value: Boolean) {
        _homeShowAppIcon.value = value
        prefs.edit { putBoolean(KEY_HOME_SHOW_APP_ICON, value) }
    }
    
    private val _homeAppIconVisibility = MutableStateFlow(prefs.getInt(KEY_HOME_APP_ICON_VISIBILITY, 0))
    val homeAppIconVisibility: StateFlow<Int> = _homeAppIconVisibility.asStateFlow()
    fun setHomeAppIconVisibility(value: Int) {
        _homeAppIconVisibility.value = value
        prefs.edit { putInt(KEY_HOME_APP_ICON_VISIBILITY, value) }
    }
    
    private val _homeShowGreeting = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_GREETING, true))
    val homeShowGreeting: StateFlow<Boolean> = _homeShowGreeting.asStateFlow()
    fun setHomeShowGreeting(value: Boolean) {
        _homeShowGreeting.value = value
        prefs.edit { putBoolean(KEY_HOME_SHOW_GREETING, value) }
    }
    
    private val _homeShowRecentlyPlayed = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_RECENTLY_PLAYED, true))
    val homeShowRecentlyPlayed: StateFlow<Boolean> = _homeShowRecentlyPlayed.asStateFlow()
    fun setHomeShowRecentlyPlayed(value: Boolean) {
        _homeShowRecentlyPlayed.value = value
        prefs.edit { putBoolean(KEY_HOME_SHOW_RECENTLY_PLAYED, value) }
    }
    
    private val _homeShowDiscoverCarousel = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_DISCOVER_CAROUSEL, true))
    val homeShowDiscoverCarousel: StateFlow<Boolean> = _homeShowDiscoverCarousel.asStateFlow()
    fun setHomeShowDiscoverCarousel(value: Boolean) {
        _homeShowDiscoverCarousel.value = value
        prefs.edit { putBoolean(KEY_HOME_SHOW_DISCOVER_CAROUSEL, value) }
    }
    
    private val _homeShowArtists = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_ARTISTS, true))
    val homeShowArtists: StateFlow<Boolean> = _homeShowArtists.asStateFlow()
    fun setHomeShowArtists(value: Boolean) {
        _homeShowArtists.value = value
        prefs.edit { putBoolean(KEY_HOME_SHOW_ARTISTS, value) }
    }
    
    private val _homeShowNewReleases = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_NEW_RELEASES, true))
    val homeShowNewReleases: StateFlow<Boolean> = _homeShowNewReleases.asStateFlow()
    fun setHomeShowNewReleases(value: Boolean) {
        _homeShowNewReleases.value = value
        prefs.edit { putBoolean(KEY_HOME_SHOW_NEW_RELEASES, value) }
    }
    
    private val _homeShowRecentlyAdded = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_RECENTLY_ADDED, true))
    val homeShowRecentlyAdded: StateFlow<Boolean> = _homeShowRecentlyAdded.asStateFlow()
    fun setHomeShowRecentlyAdded(value: Boolean) {
        _homeShowRecentlyAdded.value = value
        prefs.edit { putBoolean(KEY_HOME_SHOW_RECENTLY_ADDED, value) }
    }
    
    private val _homeShowRecommended = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_RECOMMENDED, true))
    val homeShowRecommended: StateFlow<Boolean> = _homeShowRecommended.asStateFlow()
    fun setHomeShowRecommended(value: Boolean) {
        _homeShowRecommended.value = value
        prefs.edit { putBoolean(KEY_HOME_SHOW_RECOMMENDED, value) }
    }
    
    private val _homeShowListeningStats = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_LISTENING_STATS, true))
    val homeShowListeningStats: StateFlow<Boolean> = _homeShowListeningStats.asStateFlow()
    fun setHomeShowListeningStats(value: Boolean) {
        _homeShowListeningStats.value = value
        prefs.edit { putBoolean(KEY_HOME_SHOW_LISTENING_STATS, value) }
    }

    private val _streamingHomeShowGreeting = MutableStateFlow(prefs.getBoolean(KEY_STREAMING_HOME_SHOW_GREETING, true))
    val streamingHomeShowGreeting: StateFlow<Boolean> = _streamingHomeShowGreeting.asStateFlow()
    fun setStreamingHomeShowGreeting(value: Boolean) {
        _streamingHomeShowGreeting.value = value
        prefs.edit { putBoolean(KEY_STREAMING_HOME_SHOW_GREETING, value) }
    }

    private val _streamingHomeShowRhythmGuard = MutableStateFlow(prefs.getBoolean(KEY_STREAMING_HOME_SHOW_RHYTHM_GUARD, true))
    val streamingHomeShowRhythmGuard: StateFlow<Boolean> = _streamingHomeShowRhythmGuard.asStateFlow()
    fun setStreamingHomeShowRhythmGuard(value: Boolean) {
        _streamingHomeShowRhythmGuard.value = value
        prefs.edit { putBoolean(KEY_STREAMING_HOME_SHOW_RHYTHM_GUARD, value) }
    }

    private val _streamingHomeShowRhythmStats = MutableStateFlow(prefs.getBoolean(KEY_STREAMING_HOME_SHOW_RHYTHM_STATS, true))
    val streamingHomeShowRhythmStats: StateFlow<Boolean> = _streamingHomeShowRhythmStats.asStateFlow()
    fun setStreamingHomeShowRhythmStats(value: Boolean) {
        _streamingHomeShowRhythmStats.value = value
        prefs.edit { putBoolean(KEY_STREAMING_HOME_SHOW_RHYTHM_STATS, value) }
    }

    private val _streamingHomeShowRecentlyPlayed = MutableStateFlow(prefs.getBoolean(KEY_STREAMING_HOME_SHOW_RECENTLY_PLAYED, true))
    val streamingHomeShowRecentlyPlayed: StateFlow<Boolean> = _streamingHomeShowRecentlyPlayed.asStateFlow()
    fun setStreamingHomeShowRecentlyPlayed(value: Boolean) {
        _streamingHomeShowRecentlyPlayed.value = value
        prefs.edit { putBoolean(KEY_STREAMING_HOME_SHOW_RECENTLY_PLAYED, value) }
    }

    private val _streamingHomeShowArtists = MutableStateFlow(prefs.getBoolean(KEY_STREAMING_HOME_SHOW_ARTISTS, true))
    val streamingHomeShowArtists: StateFlow<Boolean> = _streamingHomeShowArtists.asStateFlow()
    fun setStreamingHomeShowArtists(value: Boolean) {
        _streamingHomeShowArtists.value = value
        prefs.edit { putBoolean(KEY_STREAMING_HOME_SHOW_ARTISTS, value) }
    }

    private val _streamingHomeShowRecommended = MutableStateFlow(prefs.getBoolean(KEY_STREAMING_HOME_SHOW_RECOMMENDED, true))
    val streamingHomeShowRecommended: StateFlow<Boolean> = _streamingHomeShowRecommended.asStateFlow()
    fun setStreamingHomeShowRecommended(value: Boolean) {
        _streamingHomeShowRecommended.value = value
        prefs.edit { putBoolean(KEY_STREAMING_HOME_SHOW_RECOMMENDED, value) }
    }

    private val _streamingHomeShowNewReleases = MutableStateFlow(prefs.getBoolean(KEY_STREAMING_HOME_SHOW_NEW_RELEASES, true))
    val streamingHomeShowNewReleases: StateFlow<Boolean> = _streamingHomeShowNewReleases.asStateFlow()
    fun setStreamingHomeShowNewReleases(value: Boolean) {
        _streamingHomeShowNewReleases.value = value
        prefs.edit { putBoolean(KEY_STREAMING_HOME_SHOW_NEW_RELEASES, value) }
    }

    private val _streamingHomeShowPlaylists = MutableStateFlow(prefs.getBoolean(KEY_STREAMING_HOME_SHOW_PLAYLISTS, true))
    val streamingHomeShowPlaylists: StateFlow<Boolean> = _streamingHomeShowPlaylists.asStateFlow()
    fun setStreamingHomeShowPlaylists(value: Boolean) {
        _streamingHomeShowPlaylists.value = value
        prefs.edit { putBoolean(KEY_STREAMING_HOME_SHOW_PLAYLISTS, value) }
    }

    private val _streamingHomeShowRecommendations = MutableStateFlow(prefs.getBoolean(KEY_STREAMING_HOME_SHOW_RECOMMENDATIONS, true))
    val streamingHomeShowRecommendations: StateFlow<Boolean> = _streamingHomeShowRecommendations.asStateFlow()
    fun setStreamingHomeShowRecommendations(value: Boolean) {
        _streamingHomeShowRecommendations.value = value
        prefs.edit { putBoolean(KEY_STREAMING_HOME_SHOW_RECOMMENDATIONS, value) }
    }

    private val _streamingHomeShowTopCharts = MutableStateFlow(prefs.getBoolean(KEY_STREAMING_HOME_SHOW_TOP_CHARTS, true))
    val streamingHomeShowTopCharts: StateFlow<Boolean> = _streamingHomeShowTopCharts.asStateFlow()
    fun setStreamingHomeShowTopCharts(value: Boolean) {
        _streamingHomeShowTopCharts.value = value
        prefs.edit { putBoolean(KEY_STREAMING_HOME_SHOW_TOP_CHARTS, value) }
    }
    
    // ==================== Player Screen Customization Settings ====================
    
    private val _playerShowGradientOverlay = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_SHOW_GRADIENT_OVERLAY, true))
    val playerShowGradientOverlay: StateFlow<Boolean> = _playerShowGradientOverlay.asStateFlow()
    fun setPlayerShowGradientOverlay(value: Boolean) {
        _playerShowGradientOverlay.value = value
        prefs.edit { putBoolean(KEY_PLAYER_SHOW_GRADIENT_OVERLAY, value) }
    }

    private val _playerAmbientBackdropEnabled = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_AMBIENT_BACKDROP_ENABLED, false))
    val playerAmbientBackdropEnabled: StateFlow<Boolean> = _playerAmbientBackdropEnabled.asStateFlow()
    fun setPlayerAmbientBackdropEnabled(enabled: Boolean) {
        val disableAccent = enabled && _playerAccentBackgroundEnabled.value
        prefs.edit {
            putBoolean(KEY_PLAYER_AMBIENT_BACKDROP_ENABLED, enabled)
            if (disableAccent) putBoolean(KEY_PLAYER_ACCENT_BACKGROUND_ENABLED, false)
        }
        _playerAmbientBackdropEnabled.value = enabled
        // Ambient and accent backgrounds are mutually exclusive — enabling one disables the other.
        if (disableAccent) _playerAccentBackgroundEnabled.value = false
    }

    private val _playerAccentBackgroundEnabled = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_ACCENT_BACKGROUND_ENABLED, true))
    val playerAccentBackgroundEnabled: StateFlow<Boolean> = _playerAccentBackgroundEnabled.asStateFlow()
    fun setPlayerAccentBackgroundEnabled(enabled: Boolean) {
        val disableAmbient = enabled && _playerAmbientBackdropEnabled.value
        prefs.edit {
            putBoolean(KEY_PLAYER_ACCENT_BACKGROUND_ENABLED, enabled)
            if (disableAmbient) putBoolean(KEY_PLAYER_AMBIENT_BACKDROP_ENABLED, false)
        }
        _playerAccentBackgroundEnabled.value = enabled
        // Ambient and accent backgrounds are mutually exclusive — enabling one disables the other.
        if (disableAmbient) _playerAmbientBackdropEnabled.value = false
    }

    private val _playerMergeControlsToBottom = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_MERGE_CONTROLS_TO_BOTTOM, true))
    val playerMergeControlsToBottom: StateFlow<Boolean> = _playerMergeControlsToBottom.asStateFlow()
    fun setPlayerMergeControlsToBottom(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PLAYER_MERGE_CONTROLS_TO_BOTTOM, enabled) }
        _playerMergeControlsToBottom.value = enabled
    }

    private val _playerAmbientBackdropIntensity = MutableStateFlow(prefs.getFloat(KEY_PLAYER_AMBIENT_BACKDROP_INTENSITY, 0.85f))
    val playerAmbientBackdropIntensity: StateFlow<Float> = _playerAmbientBackdropIntensity.asStateFlow()
    fun setPlayerAmbientBackdropIntensity(value: Float) {
        _playerAmbientBackdropIntensity.value = value.coerceIn(0f, 1f)
        prefs.edit { putFloat(KEY_PLAYER_AMBIENT_BACKDROP_INTENSITY, _playerAmbientBackdropIntensity.value) }
    }

    private val _playerAmbientInfiniteZoom = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_AMBIENT_INFINITE_ZOOM, true))
    val playerAmbientInfiniteZoom: StateFlow<Boolean> = _playerAmbientInfiniteZoom.asStateFlow()
    fun setPlayerAmbientInfiniteZoom(enabled: Boolean) {
        _playerAmbientInfiniteZoom.value = enabled
        prefs.edit { putBoolean(KEY_PLAYER_AMBIENT_INFINITE_ZOOM, enabled) }
    }

    private val _playerGlassIntensity = MutableStateFlow(prefs.getFloat(KEY_PLAYER_GLASS_INTENSITY, 1.0f))
    val playerGlassIntensity: StateFlow<Float> = _playerGlassIntensity.asStateFlow()
    fun setPlayerGlassIntensity(value: Float) {
        _playerGlassIntensity.value = value
        prefs.edit { putFloat(KEY_PLAYER_GLASS_INTENSITY, value) }
    }
    
    private val _playerArtOverlayType = MutableStateFlow(prefs.getInt(KEY_PLAYER_ART_OVERLAY_TYPE, 0))
    val playerArtOverlayType: StateFlow<Int> = _playerArtOverlayType.asStateFlow()
    fun setPlayerArtOverlayType(value: Int) {
        _playerArtOverlayType.value = value
        prefs.edit { putInt(KEY_PLAYER_ART_OVERLAY_TYPE, value) }
    }

    private val _playerArtOverlayIntensity = MutableStateFlow(prefs.getFloat(KEY_PLAYER_ART_OVERLAY_INTENSITY, 1.0f))
    val playerArtOverlayIntensity: StateFlow<Float> = _playerArtOverlayIntensity.asStateFlow()
    fun setPlayerArtOverlayIntensity(value: Float) {
        _playerArtOverlayIntensity.value = value
        prefs.edit { putFloat(KEY_PLAYER_ART_OVERLAY_INTENSITY, value) }
    }

    private val _playerLyricsOverlayType = MutableStateFlow(prefs.getInt(KEY_PLAYER_LYRICS_OVERLAY_TYPE, 0))
    val playerLyricsOverlayType: StateFlow<Int> = _playerLyricsOverlayType.asStateFlow()
    fun setPlayerLyricsOverlayType(value: Int) {
        _playerLyricsOverlayType.value = value
        prefs.edit { putInt(KEY_PLAYER_LYRICS_OVERLAY_TYPE, value) }
    }

    private val _playerLyricsOverlayIntensity = MutableStateFlow(prefs.getFloat(KEY_PLAYER_LYRICS_OVERLAY_INTENSITY, 0.1f))
    val playerLyricsOverlayIntensity: StateFlow<Float> = _playerLyricsOverlayIntensity.asStateFlow()
    fun setPlayerLyricsOverlayIntensity(value: Float) {
        _playerLyricsOverlayIntensity.value = value
        prefs.edit { putFloat(KEY_PLAYER_LYRICS_OVERLAY_INTENSITY, value) }
    }

    private val _playerLyricsTransition = MutableStateFlow(prefs.getInt(KEY_PLAYER_LYRICS_TRANSITION, 2)) // 2 = Scale
    val playerLyricsTransition: StateFlow<Int> = _playerLyricsTransition.asStateFlow()
    fun setPlayerLyricsTransition(value: Int) {
        _playerLyricsTransition.value = value
        prefs.edit { putInt(KEY_PLAYER_LYRICS_TRANSITION, value) }
    }

    private val _playerLyricsTextSize = MutableStateFlow(prefs.getFloat(KEY_PLAYER_LYRICS_TEXT_SIZE, 1.0f))
    val playerLyricsTextSize: StateFlow<Float> = _playerLyricsTextSize.asStateFlow()
    fun setPlayerLyricsTextSize(value: Float) {
        _playerLyricsTextSize.value = value.coerceIn(0.5f, 2.0f)
        prefs.edit { putFloat(KEY_PLAYER_LYRICS_TEXT_SIZE, _playerLyricsTextSize.value) }
    }

    private val _playerLyricsAlignment = MutableStateFlow(prefs.getString(KEY_PLAYER_LYRICS_ALIGNMENT, "CENTER") ?: "CENTER")
    val playerLyricsAlignment: StateFlow<String> = _playerLyricsAlignment.asStateFlow()
    fun setPlayerLyricsAlignment(value: String) {
        _playerLyricsAlignment.value = value
        prefs.edit { putString(KEY_PLAYER_LYRICS_ALIGNMENT, value) }
    }

    private val _playerShowArtBelowLyrics = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_SHOW_ART_BELOW_LYRICS, true))
    val playerShowArtBelowLyrics: StateFlow<Boolean> = _playerShowArtBelowLyrics.asStateFlow()
    fun setPlayerShowArtBelowLyrics(value: Boolean) {
        _playerShowArtBelowLyrics.value = value
        prefs.edit { putBoolean(KEY_PLAYER_SHOW_ART_BELOW_LYRICS, value) }
    }
    
    private val _playerShowSeekButtons = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_SHOW_SEEK_BUTTONS, true))
    val playerShowSeekButtons: StateFlow<Boolean> = _playerShowSeekButtons.asStateFlow()
    fun setPlayerShowSeekButtons(value: Boolean) {
        _playerShowSeekButtons.value = value
        prefs.edit { putBoolean(KEY_PLAYER_SHOW_SEEK_BUTTONS, value) }
    }
    
    private val _playerTextAlignment = MutableStateFlow(prefs.getString(KEY_PLAYER_TEXT_ALIGNMENT, "CENTER") ?: "CENTER")
    val playerTextAlignment: StateFlow<String> = _playerTextAlignment.asStateFlow()
    fun setPlayerTextAlignment(value: String) {
        _playerTextAlignment.value = value
        prefs.edit { putString(KEY_PLAYER_TEXT_ALIGNMENT, value) }
    }
    
    private val _playerShowSongInfoOnArtwork = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_SHOW_SONG_INFO_ON_ARTWORK, true))
    val playerShowSongInfoOnArtwork: StateFlow<Boolean> = _playerShowSongInfoOnArtwork.asStateFlow()
    fun setPlayerShowSongInfoOnArtwork(value: Boolean) {
        _playerShowSongInfoOnArtwork.value = value
        prefs.edit { putBoolean(KEY_PLAYER_SHOW_SONG_INFO_ON_ARTWORK, value) }
    }
    
    private val _playerArtworkCornerRadius = MutableStateFlow(prefs.getInt(KEY_PLAYER_ARTWORK_CORNER_RADIUS, 28))
    val playerArtworkCornerRadius: StateFlow<Int> = _playerArtworkCornerRadius.asStateFlow()
    fun setPlayerArtworkCornerRadius(value: Int) {
        _playerArtworkCornerRadius.value = value.coerceIn(0, 40)
        prefs.edit { putInt(KEY_PLAYER_ARTWORK_CORNER_RADIUS, _playerArtworkCornerRadius.value) }
    }
    
    private val _playerShowAudioQualityBadges = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_SHOW_AUDIO_QUALITY_BADGES, true))
    val playerShowAudioQualityBadges: StateFlow<Boolean> = _playerShowAudioQualityBadges.asStateFlow()
    fun setPlayerShowAudioQualityBadges(value: Boolean) {
        _playerShowAudioQualityBadges.value = value
        prefs.edit { putBoolean(KEY_PLAYER_SHOW_AUDIO_QUALITY_BADGES, value) }
    }
    
    // Player Progress Bar Style
    private val _playerProgressStyle = MutableStateFlow(prefs.getString(KEY_PLAYER_PROGRESS_STYLE, "WAVY") ?: "WAVY")
    val playerProgressStyle: StateFlow<String> = _playerProgressStyle.asStateFlow()
    fun setPlayerProgressStyle(value: String) {
        _playerProgressStyle.value = value
        prefs.edit { putString(KEY_PLAYER_PROGRESS_STYLE, value) }
    }
    
    // Player Progress Thumb Style (migrates legacy names to the M3-native set)
    private fun migrateThumbStyle(value: String): String = when (value) {
        "GLOW", "ARROW" -> "DEFAULT"
        "LINE" -> "PILL"
        "OUTLINE", "DOT", "RING" -> "CIRCLE"
        else -> value
    }
    private val _playerProgressThumbStyle = MutableStateFlow(
        migrateThumbStyle(prefs.getString(KEY_PLAYER_PROGRESS_THUMB_STYLE, "DEFAULT") ?: "DEFAULT")
    )
    val playerProgressThumbStyle: StateFlow<String> = _playerProgressThumbStyle.asStateFlow()
    fun setPlayerProgressThumbStyle(value: String) {
        _playerProgressThumbStyle.value = value
        prefs.edit { putString(KEY_PLAYER_PROGRESS_THUMB_STYLE, value) }
    }
    
    // Player Progress Thumb Rotate While Playing
    private val _playerProgressThumbRotate = MutableStateFlow(prefs.getBoolean(KEY_PLAYER_PROGRESS_THUMB_ROTATE, false))
    val playerProgressThumbRotate: StateFlow<Boolean> = _playerProgressThumbRotate.asStateFlow()
    fun setPlayerProgressThumbRotate(value: Boolean) {
        _playerProgressThumbRotate.value = value
        prefs.edit { putBoolean(KEY_PLAYER_PROGRESS_THUMB_ROTATE, value) }
    }
    
    // ==================== MiniPlayer Customization Settings ====================
    
    private val _miniPlayerProgressStyle = MutableStateFlow(prefs.getString(KEY_MINIPLAYER_PROGRESS_STYLE, "NORMAL") ?: "NORMAL")
    val miniPlayerProgressStyle: StateFlow<String> = _miniPlayerProgressStyle.asStateFlow()
    fun setMiniPlayerProgressStyle(value: String) {
        _miniPlayerProgressStyle.value = value
        prefs.edit { putString(KEY_MINIPLAYER_PROGRESS_STYLE, value) }
    }
    
    private val _miniPlayerShowProgress = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SHOW_PROGRESS, true))
    val miniPlayerShowProgress: StateFlow<Boolean> = _miniPlayerShowProgress.asStateFlow()
    fun setMiniPlayerShowProgress(value: Boolean) {
        _miniPlayerShowProgress.value = value
        prefs.edit { putBoolean(KEY_MINIPLAYER_SHOW_PROGRESS, value) }
    }
    
    private val _miniPlayerShowArtwork = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SHOW_ARTWORK, true))
    val miniPlayerShowArtwork: StateFlow<Boolean> = _miniPlayerShowArtwork.asStateFlow()
    fun setMiniPlayerShowArtwork(value: Boolean) {
        _miniPlayerShowArtwork.value = value
        prefs.edit { putBoolean(KEY_MINIPLAYER_SHOW_ARTWORK, value) }
    }
    
    private val _miniPlayerArtworkSize = MutableStateFlow(prefs.getInt(KEY_MINIPLAYER_ARTWORK_SIZE, 56))
    val miniPlayerArtworkSize: StateFlow<Int> = _miniPlayerArtworkSize.asStateFlow()
    fun setMiniPlayerArtworkSize(value: Int) {
        _miniPlayerArtworkSize.value = value
        prefs.edit { putInt(KEY_MINIPLAYER_ARTWORK_SIZE, value) }
    }
    
    private val _miniPlayerCornerRadius = MutableStateFlow(prefs.getInt(KEY_MINIPLAYER_CORNER_RADIUS, 14))
    val miniPlayerCornerRadius: StateFlow<Int> = _miniPlayerCornerRadius.asStateFlow()
    fun setMiniPlayerCornerRadius(value: Int) {
        _miniPlayerCornerRadius.value = value
        prefs.edit { putInt(KEY_MINIPLAYER_CORNER_RADIUS, value) }
    }
    
    private val _miniPlayerShowTime = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SHOW_TIME, true))
    val miniPlayerShowTime: StateFlow<Boolean> = _miniPlayerShowTime.asStateFlow()
    fun setMiniPlayerShowTime(value: Boolean) {
        _miniPlayerShowTime.value = value
        prefs.edit { putBoolean(KEY_MINIPLAYER_SHOW_TIME, value) }
    }
    
    private val _miniPlayerArtworkStyle = MutableStateFlow(prefs.getString(KEY_MINIPLAYER_ARTWORK_STYLE, "ROUNDED") ?: "ROUNDED")
    val miniPlayerArtworkStyle: StateFlow<String> = _miniPlayerArtworkStyle.asStateFlow()
    fun setMiniPlayerArtworkStyle(value: String) {
        _miniPlayerArtworkStyle.value = value
        prefs.edit { putString(KEY_MINIPLAYER_ARTWORK_STYLE, value) }
    }
    
    private val _miniPlayerShowSkipButtons = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SHOW_SKIP_BUTTONS, true))
    val miniPlayerShowSkipButtons: StateFlow<Boolean> = _miniPlayerShowSkipButtons.asStateFlow()
    fun setMiniPlayerShowSkipButtons(value: Boolean) {
        _miniPlayerShowSkipButtons.value = value
        prefs.edit { putBoolean(KEY_MINIPLAYER_SHOW_SKIP_BUTTONS, value) }
    }
    
    private val _miniPlayerTextAlignment = MutableStateFlow(prefs.getString(KEY_MINIPLAYER_TEXT_ALIGNMENT, "START") ?: "START")
    val miniPlayerTextAlignment: StateFlow<String> = _miniPlayerTextAlignment.asStateFlow()
    fun setMiniPlayerTextAlignment(value: String) {
        _miniPlayerTextAlignment.value = value
        prefs.edit { putString(KEY_MINIPLAYER_TEXT_ALIGNMENT, value) }
    }
    
    private val _miniPlayerSwipeGestures = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SWIPE_GESTURES, true))
    val miniPlayerSwipeGestures: StateFlow<Boolean> = _miniPlayerSwipeGestures.asStateFlow()
    fun setMiniPlayerSwipeGestures(value: Boolean) {
        _miniPlayerSwipeGestures.value = value
        prefs.edit { putBoolean(KEY_MINIPLAYER_SWIPE_GESTURES, value) }
    }
    
    private val _miniPlayerShowArtist = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_SHOW_ARTIST, true))
    val miniPlayerShowArtist: StateFlow<Boolean> = _miniPlayerShowArtist.asStateFlow()
    fun setMiniPlayerShowArtist(value: Boolean) {
        _miniPlayerShowArtist.value = value
        prefs.edit { putBoolean(KEY_MINIPLAYER_SHOW_ARTIST, value) }
    }
    
    private val _miniPlayerAlwaysShowTablet = MutableStateFlow(prefs.getBoolean(KEY_MINIPLAYER_ALWAYS_SHOW_TABLET, false))
    val miniPlayerAlwaysShowTablet: StateFlow<Boolean> = _miniPlayerAlwaysShowTablet.asStateFlow()
    fun setMiniPlayerAlwaysShowTablet(value: Boolean) {
        _miniPlayerAlwaysShowTablet.value = value
        prefs.edit { putBoolean(KEY_MINIPLAYER_ALWAYS_SHOW_TABLET, value) }
    }
    
    private val _homeDiscoverAutoScroll = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_AUTO_SCROLL, true))
    val homeDiscoverAutoScroll: StateFlow<Boolean> = _homeDiscoverAutoScroll.asStateFlow()
    fun setHomeDiscoverAutoScroll(value: Boolean) {
        _homeDiscoverAutoScroll.value = value
        prefs.edit { putBoolean(KEY_HOME_DISCOVER_AUTO_SCROLL, value) }
    }
    
    private val _homeDiscoverAutoScrollInterval = MutableStateFlow(prefs.getInt(KEY_HOME_DISCOVER_AUTO_SCROLL_INTERVAL, 5))
    val homeDiscoverAutoScrollInterval: StateFlow<Int> = _homeDiscoverAutoScrollInterval.asStateFlow()
    fun setHomeDiscoverAutoScrollInterval(value: Int) {
        if (value in 2..15) {
            _homeDiscoverAutoScrollInterval.value = value
            prefs.edit { putInt(KEY_HOME_DISCOVER_AUTO_SCROLL_INTERVAL, value) }
        }
    }
    
    private val _homeDiscoverItemCount = MutableStateFlow(prefs.getInt(KEY_HOME_DISCOVER_ITEM_COUNT, 6))
    val homeDiscoverItemCount: StateFlow<Int> = _homeDiscoverItemCount.asStateFlow()
    fun setHomeDiscoverItemCount(value: Int) {
        if (value in 3..12) {
            _homeDiscoverItemCount.value = value
            prefs.edit { putInt(KEY_HOME_DISCOVER_ITEM_COUNT, value) }
        }
    }
    
    private val _homeRecentlyPlayedCount = MutableStateFlow(prefs.getInt(KEY_HOME_RECENTLY_PLAYED_COUNT, 6))
    val homeRecentlyPlayedCount: StateFlow<Int> = _homeRecentlyPlayedCount.asStateFlow()
    fun setHomeRecentlyPlayedCount(value: Int) {
        if (value in 3..12) {
            _homeRecentlyPlayedCount.value = value
            prefs.edit { putInt(KEY_HOME_RECENTLY_PLAYED_COUNT, value) }
        }
    }
    
    private val _homeArtistsCount = MutableStateFlow(prefs.getInt(KEY_HOME_ARTISTS_COUNT, 10))
    val homeArtistsCount: StateFlow<Int> = _homeArtistsCount.asStateFlow()
    fun setHomeArtistsCount(value: Int) {
        if (value in 4..20) {
            _homeArtistsCount.value = value
            prefs.edit { putInt(KEY_HOME_ARTISTS_COUNT, value) }
        }
    }
    
    private val _homeNewReleasesCount = MutableStateFlow(prefs.getInt(KEY_HOME_NEW_RELEASES_COUNT, 10))
    val homeNewReleasesCount: StateFlow<Int> = _homeNewReleasesCount.asStateFlow()
    fun setHomeNewReleasesCount(value: Int) {
        if (value in 4..20) {
            _homeNewReleasesCount.value = value
            prefs.edit { putInt(KEY_HOME_NEW_RELEASES_COUNT, value) }
        }
    }
    
    private val _homeRecentlyAddedCount = MutableStateFlow(prefs.getInt(KEY_HOME_RECENTLY_ADDED_COUNT, 10))
    val homeRecentlyAddedCount: StateFlow<Int> = _homeRecentlyAddedCount.asStateFlow()
    fun setHomeRecentlyAddedCount(value: Int) {
        if (value in 4..20) {
            _homeRecentlyAddedCount.value = value
            prefs.edit { putInt(KEY_HOME_RECENTLY_ADDED_COUNT, value) }
        }
    }
    
    private val _homeRecommendedCount = MutableStateFlow(prefs.getInt(KEY_HOME_RECOMMENDED_COUNT, 4))
    val homeRecommendedCount: StateFlow<Int> = _homeRecommendedCount.asStateFlow()
    fun setHomeRecommendedCount(value: Int) {
        if (value in 2..8) {
            _homeRecommendedCount.value = value
            prefs.edit { putInt(KEY_HOME_RECOMMENDED_COUNT, value) }
        }
    }
    
    private val _homeCompactCards = MutableStateFlow(prefs.getBoolean(KEY_HOME_COMPACT_CARDS, false))
    val homeCompactCards: StateFlow<Boolean> = _homeCompactCards.asStateFlow()
    fun setHomeCompactCards(value: Boolean) {
        _homeCompactCards.value = value
        prefs.edit { putBoolean(KEY_HOME_COMPACT_CARDS, value) }
    }
    
    private val _homeShowPlayButtons = MutableStateFlow(prefs.getBoolean(KEY_HOME_SHOW_PLAY_BUTTONS, true))
    val homeShowPlayButtons: StateFlow<Boolean> = _homeShowPlayButtons.asStateFlow()
    fun setHomeShowPlayButtons(value: Boolean) {
        _homeShowPlayButtons.value = value
        prefs.edit { putBoolean(KEY_HOME_SHOW_PLAY_BUTTONS, value) }
    }
    

    
    // Carousel Style: 0=Default (CenteredHero, 2 side peeks), 1=Hero (Uncontained, 1 side peek)
    private val _homeDiscoverCarouselStyle = MutableStateFlow(prefs.getInt(KEY_HOME_DISCOVER_CAROUSEL_STYLE, 0))
    val homeDiscoverCarouselStyle: StateFlow<Int> = _homeDiscoverCarouselStyle.asStateFlow()
    fun setHomeDiscoverCarouselStyle(value: Int) {
        if (value in 0..1) {
            _homeDiscoverCarouselStyle.value = value
            prefs.edit { putInt(KEY_HOME_DISCOVER_CAROUSEL_STYLE, value) }
        }
    }
    
    // Discover Widget - Content Visibility Settings
    private val _homeDiscoverShowAlbumName = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_SHOW_ALBUM_NAME, true))
    val homeDiscoverShowAlbumName: StateFlow<Boolean> = _homeDiscoverShowAlbumName.asStateFlow()
    fun setHomeDiscoverShowAlbumName(value: Boolean) {
        _homeDiscoverShowAlbumName.value = value
        prefs.edit { putBoolean(KEY_HOME_DISCOVER_SHOW_ALBUM_NAME, value) }
    }
    
    private val _homeDiscoverShowArtistName = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_SHOW_ARTIST_NAME, true))
    val homeDiscoverShowArtistName: StateFlow<Boolean> = _homeDiscoverShowArtistName.asStateFlow()
    fun setHomeDiscoverShowArtistName(value: Boolean) {
        _homeDiscoverShowArtistName.value = value
        prefs.edit { putBoolean(KEY_HOME_DISCOVER_SHOW_ARTIST_NAME, value) }
    }
    
    private val _homeDiscoverShowYear = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_SHOW_YEAR, true))
    val homeDiscoverShowYear: StateFlow<Boolean> = _homeDiscoverShowYear.asStateFlow()
    fun setHomeDiscoverShowYear(value: Boolean) {
        _homeDiscoverShowYear.value = value
        prefs.edit { putBoolean(KEY_HOME_DISCOVER_SHOW_YEAR, value) }
    }
    
    private val _homeDiscoverShowPlayButton = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_SHOW_PLAY_BUTTON, true))
    val homeDiscoverShowPlayButton: StateFlow<Boolean> = _homeDiscoverShowPlayButton.asStateFlow()
    fun setHomeDiscoverShowPlayButton(value: Boolean) {
        _homeDiscoverShowPlayButton.value = value
        prefs.edit { putBoolean(KEY_HOME_DISCOVER_SHOW_PLAY_BUTTON, value) }
    }
    
    private val _homeDiscoverShowGradient = MutableStateFlow(prefs.getBoolean(KEY_HOME_DISCOVER_SHOW_GRADIENT, true))
    val homeDiscoverShowGradient: StateFlow<Boolean> = _homeDiscoverShowGradient.asStateFlow()
    fun setHomeDiscoverShowGradient(value: Boolean) {
        _homeDiscoverShowGradient.value = value
        prefs.edit { putBoolean(KEY_HOME_DISCOVER_SHOW_GRADIENT, value) }
    }
    
    // Gesture Settings
    private val _gesturePlayerSwipeDismiss = MutableStateFlow(prefs.getBoolean(KEY_GESTURE_PLAYER_SWIPE_DISMISS, true))
    val gesturePlayerSwipeDismiss: StateFlow<Boolean> = _gesturePlayerSwipeDismiss.asStateFlow()
    fun setGesturePlayerSwipeDismiss(value: Boolean) {
        _gesturePlayerSwipeDismiss.value = value
        prefs.edit { putBoolean(KEY_GESTURE_PLAYER_SWIPE_DISMISS, value) }
    }
    
    private val _gesturePlayerSwipeTracks = MutableStateFlow(prefs.getBoolean(KEY_GESTURE_PLAYER_SWIPE_TRACKS, true))
    val gesturePlayerSwipeTracks: StateFlow<Boolean> = _gesturePlayerSwipeTracks.asStateFlow()
    fun setGesturePlayerSwipeTracks(value: Boolean) {
        _gesturePlayerSwipeTracks.value = value
        prefs.edit { putBoolean(KEY_GESTURE_PLAYER_SWIPE_TRACKS, value) }
    }
    
    private val _gestureArtworkDoubleTap = MutableStateFlow(prefs.getBoolean(KEY_GESTURE_ARTWORK_DOUBLE_TAP, true))
    val gestureArtworkDoubleTap: StateFlow<Boolean> = _gestureArtworkDoubleTap.asStateFlow()
    fun setGestureArtworkDoubleTap(value: Boolean) {
        _gestureArtworkDoubleTap.value = value
        prefs.edit { putBoolean(KEY_GESTURE_ARTWORK_DOUBLE_TAP, value) }
    }
    
    // Default section order for home screen
    private val defaultHomeSectionOrder = listOf(
        "DISCOVER", "RECENTLY_PLAYED", "ARTISTS", "RHYTHM_GUARD",
        "NEW_RELEASES", "RECENTLY_ADDED", "RECOMMENDED", "STATS", "MOOD"
    )
    private val _homeSectionOrder = MutableStateFlow(
        prefs.getString(KEY_HOME_SECTION_ORDER, null)
            ?.split(",")
            ?.map(String::trim)
            ?.filter { it.isNotBlank() && it != "GREETING" }
            ?.takeIf { it.isNotEmpty() }
            ?: defaultHomeSectionOrder
    )
    val homeSectionOrder: StateFlow<List<String>> = _homeSectionOrder.asStateFlow()
    fun setHomeSectionOrder(value: List<String>) {
        _homeSectionOrder.value = value
        prefs.edit { putString(KEY_HOME_SECTION_ORDER, value.joinToString(",")) }
    }

    private val defaultStreamingHomeSectionOrder = listOf(
        "DISCOVER", "RECENTLY_PLAYED", "ARTISTS", "RHYTHM_GUARD", "RHYTHM_STATS", "NEW_RELEASES"
    )

    private fun normalizeStreamingHomeSectionOrder(rawSections: List<String>): List<String> {
        val normalized = rawSections
            .map(String::trim)
            .filter { it != "PLAYLISTS" && it.isNotBlank() }
            .map {
                when (it) {
                    "STATS" -> "RHYTHM_STATS"
                    "RECOMMENDED" -> "DISCOVER"
                    "GREETING" -> "DISCOVER"
                    else -> it
                }
            }

        return (normalized + defaultStreamingHomeSectionOrder).distinct()
    }

    private val _streamingHomeSectionOrder = MutableStateFlow(
        normalizeStreamingHomeSectionOrder(
            prefs.getString(KEY_STREAMING_HOME_SECTION_ORDER, null)
                ?.split(",")
                .orEmpty()
        )
    )
    val streamingHomeSectionOrder: StateFlow<List<String>> = _streamingHomeSectionOrder.asStateFlow()
    fun setStreamingHomeSectionOrder(value: List<String>) {
        val normalizedValue = normalizeStreamingHomeSectionOrder(value)
        _streamingHomeSectionOrder.value = normalizedValue
        prefs.edit { putString(KEY_STREAMING_HOME_SECTION_ORDER, normalizedValue.joinToString(",")) }
    }
    
    // Album Bottom Sheet Appearance Settings
    private val _albumBottomSheetGradientBlur = MutableStateFlow(prefs.getBoolean(KEY_ALBUM_BOTTOM_SHEET_GRADIENT_BLUR, true))
    val albumBottomSheetGradientBlur: StateFlow<Boolean> = _albumBottomSheetGradientBlur.asStateFlow()
    fun setAlbumBottomSheetGradientBlur(value: Boolean) {
        _albumBottomSheetGradientBlur.value = value
        prefs.edit { putBoolean(KEY_ALBUM_BOTTOM_SHEET_GRADIENT_BLUR, value) }
    }

    private val _albumBottomSheetDiscFilter = MutableStateFlow(prefs.getInt(KEY_ALBUM_BOTTOM_SHEET_DISC_FILTER, 0).coerceAtLeast(0))
    val albumBottomSheetDiscFilter: StateFlow<Int> = _albumBottomSheetDiscFilter.asStateFlow()
    fun setAlbumBottomSheetDiscFilter(value: Int) {
        val normalizedValue = value.coerceAtLeast(0)
        _albumBottomSheetDiscFilter.value = normalizedValue
        prefs.edit { putInt(KEY_ALBUM_BOTTOM_SHEET_DISC_FILTER, normalizedValue) }
    }

    private val _albumHideAbout = MutableStateFlow(prefs.getBoolean(KEY_ALBUM_HIDE_ABOUT, false))
    val albumHideAbout: StateFlow<Boolean> = _albumHideAbout.asStateFlow()
    fun setAlbumHideAbout(value: Boolean) {
        _albumHideAbout.value = value
        prefs.edit { putBoolean(KEY_ALBUM_HIDE_ABOUT, value) }
    }
    
    // ==================== Expressive MaterialShapes Settings ====================
    
    // Master toggle for expressive shapes feature
    private val _expressiveShapesEnabled = MutableStateFlow(prefs.getBoolean(KEY_EXPRESSIVE_SHAPES_ENABLED, true))
    val expressiveShapesEnabled: StateFlow<Boolean> = _expressiveShapesEnabled.asStateFlow()
    fun setExpressiveShapesEnabled(value: Boolean) {
        _expressiveShapesEnabled.value = value
        prefs.edit { putBoolean(KEY_EXPRESSIVE_SHAPES_ENABLED, value) }
    }
    
    // Shape preset selection
    private val _expressiveShapePreset = MutableStateFlow(prefs.getString(KEY_EXPRESSIVE_SHAPE_PRESET, "DEFAULT") ?: "DEFAULT")
    val expressiveShapePreset: StateFlow<String> = _expressiveShapePreset.asStateFlow()




    private val _showSettingsSuggestions = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SETTINGS_SUGGESTIONS, true))
    val showSettingsSuggestions: StateFlow<Boolean> = _showSettingsSuggestions.asStateFlow()
    fun setShowSettingsSuggestions(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_SETTINGS_SUGGESTIONS, show) }
        _showSettingsSuggestions.value = show
    }

    fun setExpressiveShapePreset(value: String) {
        _expressiveShapePreset.value = value
        prefs.edit { putString(KEY_EXPRESSIVE_SHAPE_PRESET, value) }
    }
    
    // Individual shape settings for each artwork target
    private val _expressiveShapeAlbumArt = MutableStateFlow(prefs.getString(KEY_EXPRESSIVE_SHAPE_ALBUM_ART, "GHOSTISH") ?: "GHOSTISH")
    val expressiveShapeAlbumArt: StateFlow<String> = _expressiveShapeAlbumArt.asStateFlow()
    fun setExpressiveShapeAlbumArt(value: String) {
        _expressiveShapeAlbumArt.value = value
        prefs.edit { putString(KEY_EXPRESSIVE_SHAPE_ALBUM_ART, value) }
        // Auto-switch to custom preset when individual shape is changed
        if (_expressiveShapePreset.value != "CUSTOM") {
            setExpressiveShapePreset("CUSTOM")
        }
    }
    
    private val _expressiveShapePlayerArt = MutableStateFlow(prefs.getString(KEY_EXPRESSIVE_SHAPE_PLAYER_ART, "BUN") ?: "BUN")
    val expressiveShapePlayerArt: StateFlow<String> = _expressiveShapePlayerArt.asStateFlow()
    fun setExpressiveShapePlayerArt(value: String) {
        _expressiveShapePlayerArt.value = value
        prefs.edit { putString(KEY_EXPRESSIVE_SHAPE_PLAYER_ART, value) }
        if (_expressiveShapePreset.value != "CUSTOM") {
            setExpressiveShapePreset("CUSTOM")
        }
    }
    
    private val _expressiveShapeSongArt = MutableStateFlow(prefs.getString(KEY_EXPRESSIVE_SHAPE_SONG_ART, "CLOVER_8_LEAF") ?: "CLOVER_8_LEAF")
    val expressiveShapeSongArt: StateFlow<String> = _expressiveShapeSongArt.asStateFlow()
    fun setExpressiveShapeSongArt(value: String) {
        _expressiveShapeSongArt.value = value
        prefs.edit { putString(KEY_EXPRESSIVE_SHAPE_SONG_ART, value) }
        if (_expressiveShapePreset.value != "CUSTOM") {
            setExpressiveShapePreset("CUSTOM")
        }
    }
    
    private val _expressiveShapePlaylistArt = MutableStateFlow(prefs.getString(KEY_EXPRESSIVE_SHAPE_PLAYLIST_ART, "CLOVER_4_LEAF") ?: "CLOVER_4_LEAF")
    val expressiveShapePlaylistArt: StateFlow<String> = _expressiveShapePlaylistArt.asStateFlow()
    fun setExpressiveShapePlaylistArt(value: String) {
        _expressiveShapePlaylistArt.value = value
        prefs.edit { putString(KEY_EXPRESSIVE_SHAPE_PLAYLIST_ART, value) }
        if (_expressiveShapePreset.value != "CUSTOM") {
            setExpressiveShapePreset("CUSTOM")
        }
    }
    
    private val _expressiveShapeArtistArt = MutableStateFlow(prefs.getString(KEY_EXPRESSIVE_SHAPE_ARTIST_ART, "PIXEL_CIRCLE") ?: "PIXEL_CIRCLE")
    val expressiveShapeArtistArt: StateFlow<String> = _expressiveShapeArtistArt.asStateFlow()
    fun setExpressiveShapeArtistArt(value: String) {
        _expressiveShapeArtistArt.value = value
        prefs.edit { putString(KEY_EXPRESSIVE_SHAPE_ARTIST_ART, value) }
        if (_expressiveShapePreset.value != "CUSTOM") {
            setExpressiveShapePreset("CUSTOM")
        }
    }
    
    private val _expressiveShapePlayerControls = MutableStateFlow(prefs.getString(KEY_EXPRESSIVE_SHAPE_PLAYER_CONTROLS, "COOKIE_12") ?: "COOKIE_12")
    val expressiveShapePlayerControls: StateFlow<String> = _expressiveShapePlayerControls.asStateFlow()
    fun setExpressiveShapePlayerControls(value: String) {
        _expressiveShapePlayerControls.value = value
        prefs.edit { putString(KEY_EXPRESSIVE_SHAPE_PLAYER_CONTROLS, value) }
        if (_expressiveShapePreset.value != "CUSTOM") {
            setExpressiveShapePreset("CUSTOM")
        }
    }
    
    private val _expressiveShapeMiniPlayer = MutableStateFlow(prefs.getString(KEY_EXPRESSIVE_SHAPE_MINI_PLAYER, "COOKIE_4") ?: "COOKIE_4")
    val expressiveShapeMiniPlayer: StateFlow<String> = _expressiveShapeMiniPlayer.asStateFlow()
    fun setExpressiveShapeMiniPlayer(value: String) {
        _expressiveShapeMiniPlayer.value = value
        prefs.edit { putString(KEY_EXPRESSIVE_SHAPE_MINI_PLAYER, value) }
        if (_expressiveShapePreset.value != "CUSTOM") {
            setExpressiveShapePreset("CUSTOM")
        }
    }
    
    /**
     * Apply a preset to all artwork and player shape targets
     */
    fun applyExpressiveShapePreset(preset: String) {
        when (preset) {
            "DEFAULT" -> {
                _expressiveShapeAlbumArt.value = "GHOSTISH"
                _expressiveShapePlayerArt.value = "BUN"
                _expressiveShapeSongArt.value = "CLOVER_8_LEAF"
                _expressiveShapePlaylistArt.value = "CLOVER_4_LEAF"
                _expressiveShapeArtistArt.value = "PIXEL_CIRCLE"
                _expressiveShapePlayerControls.value = "COOKIE_12"
                _expressiveShapeMiniPlayer.value = "COOKIE_4"
            }
            "FRIENDLY" -> {
                _expressiveShapeAlbumArt.value = "CLOVER_8_LEAF"
                _expressiveShapePlayerArt.value = "CLOVER_8_LEAF"
                _expressiveShapeSongArt.value = "COOKIE_6"
                _expressiveShapePlaylistArt.value = "OVAL"
                _expressiveShapeArtistArt.value = "HEART"
                _expressiveShapePlayerControls.value = "CIRCLE"
                _expressiveShapeMiniPlayer.value = "OVAL"
            }
            "MODERN" -> {
                _expressiveShapeAlbumArt.value = "SLANTED"
                _expressiveShapePlayerArt.value = "SLANTED"
                _expressiveShapeSongArt.value = "COOKIE_7"
                _expressiveShapePlaylistArt.value = "DIAMOND"
                _expressiveShapeArtistArt.value = "CIRCLE"
                _expressiveShapePlayerControls.value = "PENTAGON"
                _expressiveShapeMiniPlayer.value = "SLANTED"
            }
            "PLAYFUL" -> {
                _expressiveShapeAlbumArt.value = "FLOWER"
                _expressiveShapePlayerArt.value = "SOFT_BURST"
                _expressiveShapeSongArt.value = "COOKIE_6"
                _expressiveShapePlaylistArt.value = "CLOVER_4_LEAF"
                _expressiveShapeArtistArt.value = "HEART"
                _expressiveShapePlayerControls.value = "SUNNY"
                _expressiveShapeMiniPlayer.value = "CLOVER_8_LEAF"
            }
            "ORGANIC" -> {
                _expressiveShapeAlbumArt.value = "CLOVER_4_LEAF"
                _expressiveShapePlayerArt.value = "FLOWER"
                _expressiveShapeSongArt.value = "CLOVER_8_LEAF"
                _expressiveShapePlaylistArt.value = "COOKIE_4"
                _expressiveShapeArtistArt.value = "OVAL"
                _expressiveShapePlayerControls.value = "CIRCLE"
                _expressiveShapeMiniPlayer.value = "OVAL"
            }
            "GEOMETRIC" -> {
                _expressiveShapeAlbumArt.value = "SQUARE"
                _expressiveShapePlayerArt.value = "DIAMOND"
                _expressiveShapeSongArt.value = "PENTAGON"
                _expressiveShapePlaylistArt.value = "SQUARE"
                _expressiveShapeArtistArt.value = "CIRCLE"
                _expressiveShapePlayerControls.value = "PENTAGON"
                _expressiveShapeMiniPlayer.value = "SQUARE"
            }
            "RETRO" -> {
                _expressiveShapeAlbumArt.value = "PIXEL_CIRCLE"
                _expressiveShapePlayerArt.value = "PIXEL_CIRCLE"
                _expressiveShapeSongArt.value = "SQUARE"
                _expressiveShapePlaylistArt.value = "PIXEL_TRIANGLE"
                _expressiveShapeArtistArt.value = "PIXEL_CIRCLE"
                _expressiveShapePlayerControls.value = "PIXEL_CIRCLE"
                _expressiveShapeMiniPlayer.value = "SQUARE"
            }
            "CHEERFUL" -> {
                _expressiveShapeAlbumArt.value = "FLOWER"
                _expressiveShapePlayerArt.value = "SUNNY"
                _expressiveShapeSongArt.value = "PUFFY"
                _expressiveShapePlaylistArt.value = "CLOVER_4_LEAF"
                _expressiveShapeArtistArt.value = "HEART"
                _expressiveShapePlayerControls.value = "CIRCLE"
                _expressiveShapeMiniPlayer.value = "PUFFY"
            }
            // CUSTOM - don't change individual shapes
        }
        // Save all shape values to preferences
        prefs.edit {
            putString(KEY_EXPRESSIVE_SHAPE_PRESET, preset)
            putString(KEY_EXPRESSIVE_SHAPE_ALBUM_ART, _expressiveShapeAlbumArt.value)
            putString(KEY_EXPRESSIVE_SHAPE_PLAYER_ART, _expressiveShapePlayerArt.value)
            putString(KEY_EXPRESSIVE_SHAPE_SONG_ART, _expressiveShapeSongArt.value)
            putString(KEY_EXPRESSIVE_SHAPE_PLAYLIST_ART, _expressiveShapePlaylistArt.value)
            putString(KEY_EXPRESSIVE_SHAPE_ARTIST_ART, _expressiveShapeArtistArt.value)
            putString(KEY_EXPRESSIVE_SHAPE_PLAYER_CONTROLS, _expressiveShapePlayerControls.value)
            putString(KEY_EXPRESSIVE_SHAPE_MINI_PLAYER, _expressiveShapeMiniPlayer.value)
        }
        _expressiveShapePreset.value = preset
    }
    
    /**
     * Synchronously commits all pending SharedPreferences edits to disk.
     * This blocks the calling thread until all outstanding asynchronous apply() writes
     * have been fully written to storage.
     */
    fun commit() {
        try {
            prefs.edit(commit = true) { }
            Log.d("AppSettings", "Synchronously committed all pending preference changes to disk")
        } catch (e: Exception) {
            Log.e("AppSettings", "Failed to commit preferences", e)
        }
    }

    /**
     * Randomize all expressive shape targets with randomly picked shapes.
     * Sets the preset to CUSTOM after randomizing.
     */
    fun randomizeExpressiveShapes() {
        val allShapes = listOf(
            "CIRCLE", "SQUARE", "OVAL", "PILL", "DIAMOND", "TRIANGLE", "PENTAGON",
            "FLOWER", "CLOVER_4_LEAF", "CLOVER_8_LEAF", "HEART", "BOOM", "SOFT_BOOM",
            "BURST", "SOFT_BURST", "SUNNY", "VERY_SUNNY",
            "COOKIE_4", "COOKIE_6", "COOKIE_7", "COOKIE_9", "COOKIE_12",
            "GHOSTISH", "PUFFY", "PUFFY_DIAMOND", "BUN", "FAN",
            "ARCH", "CLAM_SHELL", "GEM", "SLANTED", "PIXEL_CIRCLE", "PIXEL_TRIANGLE"
        )
        
        _expressiveShapeAlbumArt.value = allShapes.random()
        _expressiveShapePlayerArt.value = allShapes.random()
        _expressiveShapeSongArt.value = allShapes.random()
        _expressiveShapePlaylistArt.value = allShapes.random()
        _expressiveShapeArtistArt.value = allShapes.random()
        _expressiveShapePlayerControls.value = allShapes.random()
        _expressiveShapeMiniPlayer.value = allShapes.random()
        
        // Save as CUSTOM preset
        prefs.edit {
            putString(KEY_EXPRESSIVE_SHAPE_PRESET, "CUSTOM")
            putString(KEY_EXPRESSIVE_SHAPE_ALBUM_ART, _expressiveShapeAlbumArt.value)
            putString(KEY_EXPRESSIVE_SHAPE_PLAYER_ART, _expressiveShapePlayerArt.value)
            putString(KEY_EXPRESSIVE_SHAPE_SONG_ART, _expressiveShapeSongArt.value)
            putString(KEY_EXPRESSIVE_SHAPE_PLAYLIST_ART, _expressiveShapePlaylistArt.value)
            putString(KEY_EXPRESSIVE_SHAPE_ARTIST_ART, _expressiveShapeArtistArt.value)
            putString(KEY_EXPRESSIVE_SHAPE_PLAYER_CONTROLS, _expressiveShapePlayerControls.value)
            putString(KEY_EXPRESSIVE_SHAPE_MINI_PLAYER, _expressiveShapeMiniPlayer.value)
        }
        _expressiveShapePreset.value = "CUSTOM"
    }
}
