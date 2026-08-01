package chromahub.rhythm.app.infrastructure.service

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.widget.Toast
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import chromahub.rhythm.app.shared.data.model.TransitionSettings
import chromahub.rhythm.app.shared.data.model.TransitionMode
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import chromahub.rhythm.app.activities.MainActivity
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.BluetoothLyricsTextMode
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.infrastructure.service.player.RhythmPlayerEngine
import chromahub.rhythm.app.infrastructure.service.player.TransitionController
import chromahub.rhythm.app.infrastructure.service.player.PreloadController
import chromahub.rhythm.app.infrastructure.widget.WidgetUpdater
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes as ExoAudioAttributes
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import chromahub.rhythm.app.util.BluetoothLyricsFormatter
import chromahub.rhythm.app.util.GsonUtils
import chromahub.rhythm.app.util.LyricsRomanizationPolicy
import chromahub.rhythm.app.util.LyricsTranslationPolicy
import chromahub.rhythm.app.util.hasUsableTimedRomanization
import chromahub.rhythm.app.util.hasUsableTimedTranslation
import chromahub.rhythm.app.shared.data.model.Playlist
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import chromahub.rhythm.app.shared.data.repository.StatsTimeRange
import chromahub.rhythm.app.shared.presentation.screens.settings.rhythmGuardFormatDurationFromMinutes
import chromahub.rhythm.app.activities.RhythmGuardTimeoutActivity

@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaLibraryService(), Player.Listener {
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var player: Player
    private lateinit var customCommands: List<CommandButton>
    private lateinit var preloadController: PreloadController

    private var controller: MediaController? = null
    
    // Service-scoped coroutine scope for background operations
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Track current custom layout state to avoid unnecessary updates
    private var lastShuffleState: Boolean? = null
    private var lastRepeatMode: Int? = null
    private var lastFavoriteState: Boolean? = null
    private var lastWidgetSnapshotKey: String? = null
    private var lastHandledPlayerTransitionMediaId: String? = null
    private var lastHandledControllerTransitionMediaId: String? = null
    
    // Debounce custom layout updates to prevent flickering
    private var updateLayoutJob: Job? = null
    
    // Rhythm player engine (dual-player crossfade) and transition controller
    private lateinit var rhythmPlayerEngine: RhythmPlayerEngine
    private lateinit var transitionController: TransitionController
    
    // Sleep Timer functionality
    private var sleepTimerJob: Job? = null
    private var sleepTimerDurationMs: Long = 0L
    private var sleepTimerStartTime: Long = 0L
    private var fadeOutEnabled: Boolean = true
    private var pauseOnlyEnabled: Boolean = false
    private var lastVolumeExtendTimeMs: Long = 0L
    private var pausedByZeroVolume: Boolean = false
    
    // Audio effects (for equalizer integration)
    private var equalizer: android.media.audiofx.Equalizer? = null
    
    // Rhythm audio processors (replaced Android BassBoost and Spatializer for better quality)
    private var rhythmBassBoostProcessor: chromahub.rhythm.app.infrastructure.audio.RhythmBassBoostProcessor? = null
    private var rhythmSpatializationProcessor: chromahub.rhythm.app.infrastructure.audio.RhythmSpatializationProcessor? = null
    
    private var virtualizerStrength: Short = 0 // Store strength for virtualizer
    private var isInitializingAudioEffects: Boolean = false // Prevent concurrent initialization
    private var audioEffectsInitialized: Boolean = false // Track if effects have been successfully initialized
    private var isBassBoostAvailable: Boolean = true // Rhythm bass boost is always available
    private val audioEffectsInitMutex = Mutex()
    private var audioEffectsInitJob: Job? = null
    private var equalizerVolumeTransitionJob: Job? = null
    private var equalizerVolumeRestoreTarget: Float? = null

    // Only the current transition may restore player.volume.
    private var equalizerVolumeTransitionGeneration = 0L
    @Volatile
    private var pendingAudioEffectsSessionId: Int = 0
    
    // Player listener reference for proper cleanup
    private var playerListener: Player.Listener? = null
    
    // BroadcastReceiver to listen for favorite changes from ViewModel
    private val favoriteChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "chromahub.rhythm.app.action.FAVORITE_CHANGED" -> {
                    Log.d(TAG, "Received favorite change notification from ViewModel")
                    // Update notification custom layout
                    scheduleCustomLayoutUpdate(250) // Longer delay for external changes
                    // Also update widget
                    updateWidgetFromMediaItem(player.currentMediaItem)
                }
            }
        }
    }

    private var wasPlayingBeforeTimeout = false

    private val volumeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (streamType == AudioManager.STREAM_MUSIC) {
                    checkAndClampVolumeForRhythmGuard()
                    checkAndPauseOnZeroSystemVolume()
                    extendSleepTimer()
                }
            }
        }
    }

    private var btInfo: chromahub.rhythm.app.util.BtCodecInfo? = null
    private var btProxy: chromahub.rhythm.app.util.BtCodecInfo.Companion.Proxy? = null

    var currentLyricTexts: List<String> = emptyList()
    var currentLyricTranslations: List<String> = emptyList()
    var currentLyricRomanizations: List<String> = emptyList()
    var currentLyricTimestamps: LongArray = longArrayOf()
    var currentPlainLyricsLines: List<String> = emptyList()
    var currentLyricIndex: Int = -1
    private var currentLyricsSource: String? = null

    private fun getProcessedLyricTexts(): List<String> {
        val showTranslation = appSettings.showLyricsTranslation.value
        val showRomanization = appSettings.showLyricsRomanization.value
        
        return currentLyricTexts.mapIndexed { index, text ->
            val translation = currentLyricTranslations.getOrNull(index)
            val romanization = currentLyricRomanizations.getOrNull(index)
            
            val displayTranslation = chromahub.rhythm.app.util.LyricsTranslationPolicy
                .selectLine(text, translation)

            buildString {
                append(text)
                if (showTranslation && displayTranslation != null) {
                    append("\n")
                    append(displayTranslation)
                }
                if (showRomanization && !romanization.isNullOrBlank()) {
                    append("\n")
                    append(romanization)
                }
            }
        }
    }

    private fun clearLyricsState() {
        currentLyricTexts = emptyList()
        currentLyricTranslations = emptyList()
        currentLyricRomanizations = emptyList()
        currentLyricTimestamps = longArrayOf()
        currentPlainLyricsLines = emptyList()
        currentLyricIndex = -1
        currentLyricsSource = null
        serviceBtCanonicalSong = null
        serviceLyricsLoadedSongId = null
        serviceRomanizationLoadedSongId = null
        serviceRomanizationAttemptCount = 0
        serviceRomanizationNextRetryAtMs = 0L
        serviceTranslationLoadedSongId = null
        serviceTranslationAttemptCount = 0
        serviceTranslationNextRetryAtMs = 0L
        serviceLyricsLoadJob?.cancel()
        chromahub.rhythm.app.infrastructure.widget.glance.GlanceWidgetUpdater.updateLyrics(this, emptyList(), -1)
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED" &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                val codecStatus = intent.getParcelableExtra("android.bluetooth.extra.CODEC_STATUS", android.bluetooth.BluetoothCodecStatus::class.java)
                val newBtInfo = chromahub.rhythm.app.util.BtCodecInfo.fromCodecConfig(codecStatus?.codecConfig)
                if (newBtInfo != null && newBtInfo != btInfo) {
                    btInfo = newBtInfo
                    Log.d(TAG, "New Bluetooth codec config: $btInfo")
                    if (appSettings.codecMonitoringEnabled.value && appSettings.showCodecNotifications.value) {
                        showCodecNotification(newBtInfo)
                    }
                }
            }
        }
    }

    private fun showCodecNotification(info: chromahub.rhythm.app.util.BtCodecInfo) {
        val codecName = info.codec ?: "Unknown"
        val sampleRate = info.sampleRateHz?.let { "$it Hz" } ?: "Unknown Rate"
        val bits = info.bitsPerSample?.let { "$it bits" } ?: ""
        val quality = info.quality?.let { " ($it)" } ?: ""
        val message = "Bluetooth Codec: $codecName, $sampleRate, $bits$quality"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * When "Pause on Zero Volume" is enabled and system-volume mode is active,
     * pause playback as soon as the system music stream reaches 0.
     * Also broadcasts ACTION_ZERO_VOLUME_PAUSE so the UI can show the dialog
     * regardless of whether MaterialPlayerScreen is currently in composition.
     */
    private fun checkAndPauseOnZeroSystemVolume() {
        try {
            if (!::appSettings.isInitialized) return
            if (!appSettings.useSystemVolume.value) return
            if (!appSettings.stopPlaybackOnZeroVolume.value) return

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            // Auto-resume when system volume rises above 0 after a zero-volume pause
            if (current > 0 && pausedByZeroVolume && !player.isPlaying) {
                Log.d(TAG, "System volume rose above 0 — resuming from zero-volume pause")
                pausedByZeroVolume = false
                player.play()
                val broadcastIntent = Intent(ACTION_ZERO_VOLUME_RESUME).apply {
                    `package` = packageName
                }
                sendBroadcast(broadcastIntent)
                return
            }

            if (current == 0 && player.isPlaying) {
                Log.d(TAG, "System volume hit 0 while playing — pausing (pause-on-zero active)")
                pausedByZeroVolume = true
                player.pause()
                val broadcastIntent = Intent(ACTION_ZERO_VOLUME_PAUSE).apply {
                    `package` = packageName
                }
                sendBroadcast(broadcastIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAndPauseOnZeroSystemVolume", e)
        }
    }

    private fun checkAndClampVolumeForRhythmGuard() {
        try {
            if (!::appSettings.isInitialized) return
            val mode = appSettings.rhythmGuardMode.value
            if (mode == AppSettings.RHYTHM_GUARD_MODE_AUTO) {
                val age = appSettings.rhythmGuardAge.value
                val policy = appSettings.getRhythmGuardPolicy(age)
                val activeThreshold = policy.maxVolumeThreshold
                
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val isSpeaker = isSpeakerOutputActive(audioManager)
                val applyVolumeLimitOnSpeaker = appSettings.rhythmGuardApplyVolumeLimitOnSpeaker.value
                val shouldApply = applyVolumeLimitOnSpeaker || !isSpeaker
                
                if (shouldApply) {
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val currentVolumeFraction = if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 0f
                    
                    val volumeOvershoot = currentVolumeFraction - activeThreshold
                    if (volumeOvershoot > 0.01f) {
                        val targetVolume = Math.round(activeThreshold * maxVolume).coerceIn(0, maxVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                        Log.d(TAG, "Rhythm Guard Auto: clamped system volume from $currentVolumeFraction to $activeThreshold")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clamping volume in background", e)
        }
    }

    private fun isSpeakerOutputActive(audioManager: AudioManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } &&
                    !devices.any {
                        (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                         it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                         it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                         it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) &&
                        it.isSink
                    }
        } else {
            @Suppress("DEPRECATION")
            !audioManager.isBluetoothA2dpOn && !audioManager.isWiredHeadsetOn
        }
    }

    private fun showRhythmGuardAlertNotification(title: String, text: String, riskLevel: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureRhythmGuardNotificationChannels(notificationManager)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_player", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            8101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when (riskLevel) {
            "SEVERE", "HIGH" -> NotificationCompat.PRIORITY_HIGH
            "MODERATE" -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }

        val notification = NotificationCompat.Builder(this, "rhythm_guard_alerts")
            .setSmallIcon(chromahub.rhythm.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(priority)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1301, notification)
    }

    private fun showRhythmGuardTimerNotification(title: String, text: String, remainingSeconds: Long, totalSeconds: Long) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureRhythmGuardNotificationChannels(notificationManager)

        val safeTotal = totalSeconds.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val safeRemaining = remainingSeconds.coerceIn(0L, safeTotal.toLong()).toInt()
        val completed = (safeTotal - safeRemaining).coerceIn(0, safeTotal)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_player", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            8102,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "rhythm_guard_timers")
            .setSmallIcon(chromahub.rhythm.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$text\n${getString(chromahub.rhythm.app.R.string.settings_rhythm_guard_notification_tap_open)}"
                )
            )
            .setProgress(safeTotal, completed, false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1302, notification)
    }

    private fun ensureRhythmGuardNotificationChannels(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val alertChannel = NotificationChannel(
            "rhythm_guard_alerts",
            getString(chromahub.rhythm.app.R.string.service_rhythm_guard_alerts),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(chromahub.rhythm.app.R.string.service_rhythm_guard_alerts_desc)
            enableVibration(true)
        }

        val timerChannel = NotificationChannel(
            "rhythm_guard_timers",
            getString(chromahub.rhythm.app.R.string.service_rhythm_guard_timers),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(chromahub.rhythm.app.R.string.service_rhythm_guard_timers_desc)
            enableVibration(false)
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(alertChannel)
        notificationManager.createNotificationChannel(timerChannel)
    }

    private fun cancelRhythmGuardTimerNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1302)
    }

    private fun cancelRhythmGuardAlertNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1301)
    }


    private val repeatCommand: CommandButton
        get() = when (val mode = controller?.repeatMode ?: Player.REPEAT_MODE_OFF) {
            Player.REPEAT_MODE_OFF -> customCommands[2]
            Player.REPEAT_MODE_ALL -> customCommands[3]
            Player.REPEAT_MODE_ONE -> customCommands[4]
            else -> customCommands[2] // Fallback to REPEAT_MODE_OFF command
        }

    private val shuffleCommand: CommandButton
        get() = if (controller?.shuffleModeEnabled == true) {
            customCommands[1]
        } else {
            customCommands[0]
        }

    private fun getCurrentFavoriteCommand(): CommandButton {
        return if (isCurrentSongFavorite()) {
            customCommands[6] // Remove from favorites (filled heart)
        } else {
            customCommands[5] // Add to favorites (heart outline)
        }
    }

    // Track external files that have been played
    private val externalUriCache = ConcurrentHashMap<String, MediaItem>()

    // Settings manager
    private lateinit var appSettings: AppSettings
    
    // Status broadcaster for Tasker, KWGT, and other automation apps
    private lateinit var statusBroadcaster: chromahub.rhythm.app.utils.StatusBroadcaster
    
    // SharedPreferences keys
    companion object {
        var instanceForWidgetAndLyricsOnly: MediaPlaybackService? = null
        private const val TAG = "MediaPlaybackService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "RhythmMediaPlayback"
        private const val SLEEP_TIMER_NOTIFICATION_ID = 1002
        private const val SLEEP_TIMER_CHANNEL_ID = "RhythmSleepTimer"
        private const val EQ_TOGGLE_DUCK_FACTOR = 0.12f
        private const val EQ_TOGGLE_SETTLE_DELAY_MS = 45L
        private const val EQ_TOGGLE_RAMP_STEPS = 6
        private const val EQ_TOGGLE_RAMP_STEP_DELAY_MS = 22L

        private const val PREF_NAME = "rhythm_preferences"
        private const val PREF_GAPLESS_PLAYBACK = "gapless_playback"
        private const val PREF_CROSSFADE = "crossfade"
        private const val PREF_CROSSFADE_DURATION = "crossfade_duration"
        private const val PREF_AUDIO_NORMALIZATION = "audio_normalization"
        private const val PREF_REPLAY_GAIN = "replay_gain"
        
        // Intent action for updating settings
        const val ACTION_UPDATE_SETTINGS = "chromahub.rhythm.app.action.UPDATE_SETTINGS"
        
        // Intent action for playing external files
        const val ACTION_PLAY_EXTERNAL_FILE = "chromahub.rhythm.app.action.PLAY_EXTERNAL_FILE"
        
        // Intent action for initializing the service
        const val ACTION_INIT_SERVICE = "chromahub.rhythm.app.action.INIT_SERVICE"
        
        // Intent actions for sleep timer
        const val ACTION_START_SLEEP_TIMER = "chromahub.rhythm.app.action.START_SLEEP_TIMER"
        const val ACTION_STOP_SLEEP_TIMER = "chromahub.rhythm.app.action.STOP_SLEEP_TIMER"
        const val ACTION_EXTEND_SLEEP_TIMER = "chromahub.rhythm.app.action.EXTEND_SLEEP_TIMER"
        
        // Intent actions for equalizer
        const val ACTION_SET_EQUALIZER_ENABLED = "chromahub.rhythm.app.action.SET_EQUALIZER_ENABLED"
        const val ACTION_SET_EQUALIZER_BAND = "chromahub.rhythm.app.action.SET_EQUALIZER_BAND"
        const val ACTION_SET_BASS_BOOST = "chromahub.rhythm.app.action.SET_BASS_BOOST"
        const val ACTION_SET_VIRTUALIZER = "chromahub.rhythm.app.action.SET_VIRTUALIZER"
        const val ACTION_APPLY_EQUALIZER_PRESET = "chromahub.rhythm.app.action.APPLY_EQUALIZER_PRESET"
        const val ACTION_GET_EQUALIZER_DIAGNOSTICS = "chromahub.rhythm.app.action.GET_EQUALIZER_DIAGNOSTICS"
        
        // Widget control actions
        const val ACTION_PLAY_PAUSE = "chromahub.rhythm.app.action.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "chromahub.rhythm.app.action.SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "chromahub.rhythm.app.action.SKIP_PREVIOUS"
        const val ACTION_TOGGLE_FAVORITE = "chromahub.rhythm.app.action.TOGGLE_FAVORITE"
        
        // Broadcast actions for status updates
        const val BROADCAST_SLEEP_TIMER_STATUS = "chromahub.rhythm.app.broadcast.SLEEP_TIMER_STATUS"
        const val EXTRA_TIMER_ACTIVE = "timer_active"
        const val EXTRA_REMAINING_TIME = "remaining_time"
        const val EXTRA_TOTAL_TIME = "total_time"

        // Broadcast actions for shuffle updates
        const val ACTION_SHUFFLE_STATE_CHANGED = "chromahub.rhythm.app.action.SHUFFLE_STATE_CHANGED"
        const val EXTRA_SHUFFLE_ENABLED = "shuffle_enabled"
        
        // Audio session ID
        const val ACTION_GET_AUDIO_SESSION_ID = "chromahub.rhythm.app.action.GET_AUDIO_SESSION_ID"
        const val BROADCAST_AUDIO_SESSION_ID = "chromahub.rhythm.app.broadcast.AUDIO_SESSION_ID"
        const val EXTRA_AUDIO_SESSION_ID = "audio_session_id"
        
        // Mute/Unmute actions (Media3 1.9.0 feature)
        const val ACTION_MUTE = "chromahub.rhythm.app.action.MUTE"
        const val ACTION_UNMUTE = "chromahub.rhythm.app.action.UNMUTE"
        const val ACTION_TOGGLE_MUTE = "chromahub.rhythm.app.action.TOGGLE_MUTE"

        const val SESSION_COMMAND_BT_VIRTUAL_SKIP_NEXT =
            "chromahub.rhythm.app.command.BT_VIRTUAL_SKIP_NEXT"
        const val SESSION_COMMAND_BT_VIRTUAL_SKIP_PREVIOUS =
            "chromahub.rhythm.app.command.BT_VIRTUAL_SKIP_PREVIOUS"

        // Zero-volume pause/resume broadcasts — sent by service, received by UI to show/dismiss dialog
        const val ACTION_ZERO_VOLUME_PAUSE = "chromahub.rhythm.app.action.ZERO_VOLUME_PAUSE"
        const val ACTION_ZERO_VOLUME_RESUME = "chromahub.rhythm.app.action.ZERO_VOLUME_RESUME"

        // Playback custom commands
        const val REPEAT_MODE_ALL = "repeat_all"
        const val REPEAT_MODE_ONE = "repeat_one"
        const val REPEAT_MODE_OFF = "repeat_off"
        const val SHUFFLE_MODE_ON = "shuffle_on"
        const val SHUFFLE_MODE_OFF = "shuffle_off"
        const val FAVORITE_ON = "favorite_on"
        const val FAVORITE_OFF = "favorite_off"

        private const val METADATA_EXTRA_ORIGINAL_TITLE = "chromahub.rhythm.app.extra.original_title"
        private const val METADATA_EXTRA_ORIGINAL_ARTIST = "chromahub.rhythm.app.extra.original_artist"
        private const val METADATA_EXTRA_ORIGINAL_ALBUM = "chromahub.rhythm.app.extra.original_album"
    }

    override fun onCreate() {
        super.onCreate()
        instanceForWidgetAndLyricsOnly = this
        Log.d(TAG, "Service created")

        setMediaNotificationProvider(
            androidx.media3.session.DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(chromahub.rhythm.app.R.drawable.ic_notification)
            }
        )

        // Create notification channel first (required for Android 8.0+)
        createNotificationChannel()

        // Try foreground promotion early; on newer Android versions this can be blocked
        // when the service is started from background contexts.
        startForegroundWithNotification(
            getString(chromahub.rhythm.app.R.string.service_rhythm_music),
            getString(chromahub.rhythm.app.R.string.service_starting)
        )

        // Initialize settings manager (fast operation)
        updateForegroundNotification(
            getString(chromahub.rhythm.app.R.string.service_rhythm_music),
            getString(chromahub.rhythm.app.R.string.service_loading_settings)
        )
        appSettings = AppSettings.getInstance(applicationContext)
        
        // Initialize preloader
        preloadController = PreloadController()
        
        // Initialize Rhythm audio processors early (before player creation)
        try {
            rhythmBassBoostProcessor = chromahub.rhythm.app.infrastructure.audio.RhythmBassBoostProcessor()
            rhythmSpatializationProcessor = chromahub.rhythm.app.infrastructure.audio.RhythmSpatializationProcessor()
            isBassBoostAvailable = true
            appSettings.setBassBoostAvailable(true)
            Log.d(TAG, "Rhythm audio processors initialized early")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Rhythm processors", e)
            rhythmBassBoostProcessor = null
            rhythmSpatializationProcessor = null
            isBassBoostAvailable = false
            appSettings.setBassBoostAvailable(false)
        }
        
        // Initialize status broadcaster for Tasker/KWGT
        statusBroadcaster = chromahub.rhythm.app.utils.StatusBroadcaster(applicationContext)

        // Register BroadcastReceiver for favorite changes
        updateForegroundNotification(
            getString(chromahub.rhythm.app.R.string.service_rhythm_music),
            getString(chromahub.rhythm.app.R.string.service_setup_components)
        )
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            favoriteChangeReceiver,
            IntentFilter("chromahub.rhythm.app.action.FAVORITE_CHANGED"),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val volumeFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            volumeChangeReceiver,
            volumeFilter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )

        try {
            val btFilter = IntentFilter("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED")
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                btReceiver,
                btFilter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                btProxy = chromahub.rhythm.app.util.BtCodecInfo.getCodec(this) { info ->
                    if (info != null) {
                        btInfo = info
                        Log.d(TAG, "First Bluetooth codec config: $btInfo")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Bluetooth codec monitoring", e)
        }

        try {
            // Initialize core components on main thread (required for media service)
            updateForegroundNotification(
                getString(chromahub.rhythm.app.R.string.service_rhythm_music),
                getString(chromahub.rhythm.app.R.string.service_initializing_player)
            )
            initializePlayer()

            updateForegroundNotification(
                getString(chromahub.rhythm.app.R.string.service_rhythm_music),
                getString(chromahub.rhythm.app.R.string.service_creating_controls)
            )
            createCustomCommands()

            // Create the media session (required synchronously)
            updateForegroundNotification(
                getString(chromahub.rhythm.app.R.string.service_rhythm_music),
                getString(chromahub.rhythm.app.R.string.service_setup_media_session)
            )
            mediaSession = createMediaSession()

            // Initialize controller asynchronously to avoid blocking
            updateForegroundNotification(
                getString(chromahub.rhythm.app.R.string.service_rhythm_music),
                getString(chromahub.rhythm.app.R.string.service_initializing_controller)
            )
            createController()

            // Rhythm Guard background check loop (every 10 seconds)
            serviceScope.launch {
                val statsRepository = PlaybackStatsRepository.getInstance(applicationContext)
                while (isActive) {
                    try {
                        val mode = appSettings.rhythmGuardMode.value
                        if (mode != AppSettings.RHYTHM_GUARD_MODE_OFF) {
                            val now = System.currentTimeMillis()
                            val timeoutUntil = appSettings.rhythmGuardTimeoutUntilMs.value
                            val cooldownUntil = appSettings.rhythmGuardTimeoutCooldownUntilMs.value
                            if (timeoutUntil > 0L && now >= timeoutUntil && cooldownUntil <= 0L) {
                                val cooldownMinutes = appSettings.rhythmGuardPostTimeoutCooldownMinutes.value.coerceIn(1, 60)
                                val cooldownUntilMs = now + cooldownMinutes.toLong() * 60_000L
                                
                                val todaySummary = runCatching {
                                    statsRepository.loadSummary(StatsTimeRange.TODAY)
                                }.getOrNull()
                                val dbDurationMs = todaySummary?.totalDurationMs ?: 0L
                                val currentPositionMs = player.currentPosition
                                val totalMs = dbDurationMs + currentPositionMs
                                val currentMinutes = (totalMs / 60000L).toInt().coerceAtLeast(0)
                                
                                appSettings.setRhythmGuardTimeoutCooldownWithLimit(cooldownUntilMs, currentMinutes + 15)
                                appSettings.clearRhythmGuardListeningTimeout()
                                cancelRhythmGuardTimerNotification()
                                if (wasPlayingBeforeTimeout) {
                                    wasPlayingBeforeTimeout = false
                                    withContext(Dispatchers.Main) {
                                        player.play()
                                    }
                                }
                            } else if (now < timeoutUntil) {
                                // Timeout is active, ensure playback is paused
                                if (player.isPlaying) {
                                    wasPlayingBeforeTimeout = true
                                    withContext(Dispatchers.Main) {
                                        player.pause()
                                    }
                                }
                            } else if (now < cooldownUntil) {
                                // Cooldown is active, do not trigger a new timeout
                            } else {
                                if (cooldownUntil > 0L) {
                                    appSettings.clearRhythmGuardTimeoutCooldown()
                                }
                                if (player.isPlaying) {
                                    // Check active daily exposure limit
                                    val age = appSettings.rhythmGuardAge.value
                                    val policy = appSettings.getRhythmGuardPolicy(age)
                                    val alertThresholdMinutes = appSettings.rhythmGuardAlertThresholdMinutes.value
                                    val effectiveLimitMinutes = if (mode == AppSettings.RHYTHM_GUARD_MODE_AUTO) {
                                        policy.recommendedDailyMinutes
                                    } else if (alertThresholdMinutes > 0) {
                                        alertThresholdMinutes
                                    } else {
                                        policy.recommendedDailyMinutes
                                    }

                                    val todaySummary = runCatching {
                                        statsRepository.loadSummary(StatsTimeRange.TODAY)
                                    }.getOrNull()
                                    val dbDurationMs = todaySummary?.totalDurationMs ?: 0L
                                    val currentPositionMs = player.currentPosition
                                    val totalMs = dbDurationMs + currentPositionMs
                                    val currentMinutes = (totalMs / 60000L).toInt().coerceAtLeast(0)

                                    // If listening minutes are below the daily limit (e.g., new day), reset the next allowed limit
                                    if (currentMinutes <= effectiveLimitMinutes) {
                                        if (appSettings.rhythmGuardNextAllowedLimitMinutes.value != 0) {
                                            appSettings.setRhythmGuardNextAllowedLimitMinutes(0)
                                        }
                                    }

                                    val nextAllowedLimit = appSettings.rhythmGuardNextAllowedLimitMinutes.value
                                    val activeLimit = if (nextAllowedLimit > effectiveLimitMinutes) {
                                        nextAllowedLimit
                                    } else {
                                        effectiveLimitMinutes
                                    }

                                    if (currentMinutes > activeLimit) {
                                        val breakResumeMinutes = appSettings.rhythmGuardBreakResumeMinutes.value.coerceIn(1, 180)
                                        val newTimeoutUntilMs = now + breakResumeMinutes * 60_000L
                                        val formattedToday = rhythmGuardFormatDurationFromMinutes(currentMinutes)
                                        val formattedLimit = rhythmGuardFormatDurationFromMinutes(effectiveLimitMinutes)
                                        val timeoutReason = getString(
                                            chromahub.rhythm.app.R.string.settings_rhythm_guard_timeout_reason_auto,
                                            formattedToday,
                                            formattedLimit
                                        )

                                        appSettings.setRhythmGuardListeningTimeout(
                                            untilEpochMs = newTimeoutUntilMs,
                                            reason = timeoutReason,
                                            startedAtEpochMs = now
                                        )

                                        if (appSettings.rhythmGuardAlertNotificationsEnabled.value) {
                                            showRhythmGuardAlertNotification(
                                                title = getString(chromahub.rhythm.app.R.string.settings_rhythm_guard_notification_alert_title),
                                                text = timeoutReason,
                                                riskLevel = "HIGH"
                                            )
                                        }

                                        if (appSettings.rhythmGuardTimerNotificationsEnabled.value) {
                                            showRhythmGuardTimerNotification(
                                                title = getString(chromahub.rhythm.app.R.string.settings_rhythm_guard_notification_timer_active_title),
                                                text = getString(
                                                    chromahub.rhythm.app.R.string.settings_rhythm_guard_notification_timer_active_text,
                                                    rhythmGuardFormatDurationFromMinutes(breakResumeMinutes)
                                                ),
                                                remainingSeconds = breakResumeMinutes.toLong() * 60L,
                                                totalSeconds = breakResumeMinutes.toLong() * 60L
                                            )
                                        }

                                        withContext(Dispatchers.Main) {
                                            val wasPlaying = player.isPlaying
                                            if (wasPlaying) {
                                                wasPlayingBeforeTimeout = true
                                                player.pause()
                                            }
                                            try {
                                                RhythmGuardTimeoutActivity.start(
                                                    context = applicationContext,
                                                    reason = timeoutReason,
                                                    timeoutUntilMs = newTimeoutUntilMs,
                                                    timeoutStartedAtMs = now
                                                )
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Failed to start RhythmGuardTimeoutActivity from background", e)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in Rhythm Guard background loop", e)
                    }
                    delay(10000L)
                }
            }

            updateForegroundNotification(
                getString(chromahub.rhythm.app.R.string.service_rhythm_music),
                getString(chromahub.rhythm.app.R.string.service_ready)
            )

            Log.d(TAG, "Service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing service", e)
            updateForegroundNotification(
                getString(chromahub.rhythm.app.R.string.service_rhythm_music),
                getString(chromahub.rhythm.app.R.string.service_init_failed)
            )
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(chromahub.rhythm.app.R.string.media3_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(chromahub.rhythm.app.R.string.media3_notification_channel_description)
                setShowBadge(false)
            }

            val sleepTimerChannel = NotificationChannel(
                SLEEP_TIMER_CHANNEL_ID,
                getString(chromahub.rhythm.app.R.string.notification_sleep_timer_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(chromahub.rhythm.app.R.string.notification_sleep_timer_channel_desc)
                setShowBadge(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(sleepTimerChannel)
        }
    }
    
    private fun startForegroundWithNotification(title: String = "Rhythm Music", content: String = "Rhythm is starting.") {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(chromahub.rhythm.app.R.drawable.ic_notification)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        // CRITICAL: startForeground() MUST be called within 5 seconds when service is
        // started via startForegroundService(), or Android will ANR the app.
        // Even if an exception occurs, we must attempt the call.
        var foregroundStartSucceeded = false
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                super.startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                super.startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStartSucceeded = true
            Log.d(TAG, "Started foreground service: $title - $content")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground() failed with exception: ${e.javaClass.simpleName}: ${e.message}", e)
            
            // Check if this is a background restriction issue
            val isForegroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e.javaClass.name.contains("ForegroundServiceStartNotAllowedException")
            
            if (isForegroundRestricted) {
                Log.w(TAG, "Foreground service start blocked by system (background restriction). Service will run with standard notification.")
                // In this case, we can't satisfy the startForegroundService() contract,
                // but we'll continue anyway and show a regular notification.
                // This is not ideal but better than crashing.
            } else {
                // For other exceptions, log them but try to continue
                Log.w(TAG, "Unexpected exception during startForeground(), will attempt notification fallback.", e)
            }
            
            // Attempt to show notification as fallback (will not satisfy startForegroundService contract)
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.w(TAG, "Posted notification as fallback. Service may experience issues on some Android versions.")
            } catch (notifyError: Exception) {
                Log.e(TAG, "Failed to post fallback notification", notifyError)
            }
        }
        
        if (!foregroundStartSucceeded) {
            Log.w(TAG, "WARNING: startForeground() was not successfully called. This service may be terminated if it stays in background.")
        }
    }

    private fun updateForegroundNotification(title: String, content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(chromahub.rhythm.app.R.drawable.ic_notification)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Updated foreground notification: $title - $content")
    }
    
    private fun initializePlayer() {
        // Initialize RhythmPlayerEngine for crossfade support
        val audioRoutingMode = appSettings.audioRoutingMode.value
        applyUsbExclusiveRoutingPreference()
        Log.d(TAG, "Initializing player (routing: $audioRoutingMode)")
        rhythmPlayerEngine = RhythmPlayerEngine(
            this, 
            bassBoostProcessor = rhythmBassBoostProcessor,
            spatializationProcessor = rhythmSpatializationProcessor
        )
        rhythmPlayerEngine.initialize()
        
        // The master player is exposed to MediaSession and used everywhere
        player = wrapPlayer(rhythmPlayerEngine.masterPlayer)
        
        // Restore saved shuffle and repeat states on startup
        if (appSettings.shuffleModePersistence.value) {
            val savedShuffle = appSettings.savedShuffleState.value
            val useExoPlayerShuffle = appSettings.shuffleUsesExoplayer.value
            player.shuffleModeEnabled = savedShuffle && useExoPlayerShuffle
            Log.d(TAG, "Restored player shuffle mode: ${player.shuffleModeEnabled} (useExoPlayerShuffle=$useExoPlayerShuffle)")
        }
        if (appSettings.repeatModePersistence.value) {
            player.repeatMode = appSettings.savedRepeatMode.value
            Log.d(TAG, "Restored player repeat mode: ${player.repeatMode}")
        }
        
        // Register player swap listener for crossfade transitions
        rhythmPlayerEngine.addPlayerSwapListener { newPlayer ->
            Log.d(TAG, "Player swapped during crossfade transition")
            val oldPlayer = player
            val wrappedNewPlayer = wrapPlayer(newPlayer)
            player = wrappedNewPlayer
            
            // Move the service-level player listener to the new player
            playerListener?.let { listener ->
                oldPlayer.removeListener(listener)
                wrappedNewPlayer.addListener(listener)
            }
            
            // Update the MediaSession to use the new player
            mediaSession?.player = wrappedNewPlayer
            
            // Force custom layout update for the new player
            scheduleCustomLayoutUpdate(50)
            
            // Update widget with current song info
            updateWidgetFromMediaItem(newPlayer.currentMediaItem)
            
            // Reinitialize audio effects with new session ID
            if ((newPlayer as? ExoPlayer)?.audioSessionId != 0) {
                initializeAudioEffects()
            }
        }
            
        // Add listener to initialize audio effects when session ID is ready and handle errors
        // Store reference for proper cleanup in onDestroy
        playerListener = object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (::preloadController.isInitialized) {
                    val mediaItems = mutableListOf<MediaItem>()
                    for (i in 0 until player.mediaItemCount) {
                        mediaItems.add(player.getMediaItemAt(i))
                    }
                    preloadController.addOrUpdateQueue(mediaItems)
                }
                scheduleCollapseForBtLyrics()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // Collapsed legacy queues need service-owned advancement.
                if (playbackState == Player.STATE_ENDED) {
                    handleBtVirtualPlaybackEnded()
                }
                if (playbackState == Player.STATE_READY && getPlayerAudioSessionId() != 0) {
                    // Reinitialize audio effects with valid session ID
                    val previouslyEnabled = getEqualizerEnabledSafe()
                    Log.d(TAG, "Player ready with session ID ${getPlayerAudioSessionId()}, reinitializing effects (EQ was: $previouslyEnabled)")
                    initializeAudioEffects()
                    
                    // Force reload audio effects settings to fix cold boot issue
                    // This ensures bass boost and spatial audio are properly applied on first playback
                    // Increased delay to ensure player is fully ready and processors are connected
                    serviceScope.launch {
                        delay(200) // Increased delay to ensure audio pipeline is fully initialized
                        Log.d(TAG, "Force-reloading audio effects settings after player ready")
                        loadSavedAudioEffects()
                        
                        // Additional verification: Re-apply Rhythm processor settings after another small delay
                        // This fixes the issue where processors don't receive settings on cold boot
                        delay(100)
                        Log.d(TAG, "Re-applying Rhythm processor settings for cold boot fix")
                        
                        // Re-apply bass boost if enabled
                        if (appSettings.bassBoostEnabled.value && rhythmBassBoostProcessor != null) {
                            rhythmBassBoostProcessor?.setEnabled(true)
                            rhythmBassBoostProcessor?.setStrength(appSettings.bassBoostStrength.value.toShort())
                            Log.d(TAG, "Cold boot: Re-applied bass boost - enabled=true, strength=${appSettings.bassBoostStrength.value}")
                        }
                        
                        // Re-apply spatial audio if enabled
                        if (appSettings.virtualizerEnabled.value && rhythmSpatializationProcessor != null) {
                            rhythmSpatializationProcessor?.setEnabled(true)
                            rhythmSpatializationProcessor?.setStrength(appSettings.virtualizerStrength.value.toShort())
                            Log.d(TAG, "Cold boot: Re-applied spatial audio - enabled=true, strength=${appSettings.virtualizerStrength.value}")
                        }
                    }
                    
                    // Verify state was preserved — ensure EQ hardware stays enabled to avoid DSP burst on disable
                    val currentlyEnabled = getEqualizerEnabledSafe()
                    if (previouslyEnabled != currentlyEnabled) {
                        if (appSettings.equalizerEnabled.value) {
                            Log.w(TAG, "Equalizer state changed after reinitialization! Was: $previouslyEnabled, Now: $currentlyEnabled, Expected: true")
                            setEqualizerEnabled(true)
                        } else {
                            Log.d(TAG, "Re-enabling EQ hardware with flat bands after reinitialization")
                            withEqualizerSafe("re-enable eq on reinit", Unit) { eq ->
                                eq.enabled = true
                                val numberOfBands = eq.numberOfBands.toInt()
                                for (i in 0 until numberOfBands) {
                                    eq.setBandLevel(i.toShort(), 0)
                                }
                            }
                        }
                    }
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) {
                    val now = System.currentTimeMillis()
                    if (appSettings.rhythmGuardTimeoutUntilMs.value > now) {
                        Log.d(TAG, "Blocking player start due to active Rhythm Guard timeout")
                        try {
                            // Force pause to prevent playback
                            player.pause()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to pause player while enforcing Rhythm Guard", e)
                        }
                    }
                }
            }
            
            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackError(error)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (!btVirtualQueueActive() || isMutatingBtVirtualQueue) return
                // The virtual queue owns repeat state while the legacy player has one item.
                btVirtualOriginalRepeatMode = repeatMode
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (btVirtualQueueActive() && !isMutatingBtVirtualQueue) {
                    // Shuffle has no effect on the temporary one-item player, but its
                    // user-selected value belongs to the original playlist snapshot.
                    btVirtualOriginalShuffleMode = shuffleModeEnabled
                }
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                
                // Broadcast status for Tasker/KWGT/automation apps
                if (appSettings.broadcastStatusEnabled.value) {
                    statusBroadcaster.broadcastPlaystateChanged(isPlaying, player.currentPosition)
                }

                // Persist playback position when paused and queue persistence is enabled
                if (!isPlaying && appSettings.queuePersistenceEnabled.value) {
                    val currentIndex = player.currentMediaItemIndex
                    if (currentIndex != androidx.media3.common.C.INDEX_UNSET) {
                        appSettings.setSavedQueueIndex(currentIndex)
                        appSettings.setSavedPlaybackPosition(player.currentPosition)
                        Log.d(TAG, "Persisted queue index $currentIndex and position ${player.currentPosition} on pause")
                    }
                }
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (::preloadController.isInitialized) {
                    val currentIndex = player.currentMediaItemIndex
                    if (currentIndex != androidx.media3.common.C.INDEX_UNSET) {
                        preloadController.setPlayingIndex(currentIndex)
                    }
                }

                if (appSettings.queuePersistenceEnabled.value) {
                    val currentIndex = player.currentMediaItemIndex
                    if (currentIndex != androidx.media3.common.C.INDEX_UNSET) {
                        appSettings.setSavedQueueIndex(currentIndex)
                        appSettings.setSavedPlaybackPosition(0L) // Reset position for new track
                        Log.d(TAG, "Persisted queue index $currentIndex on track transition")
                    }
                }
                
                val transitionMediaId = mediaItem?.mediaId
                if (isBluetoothMetadataTransition(
                        mediaItem = mediaItem,
                        reason = reason,
                        lastHandledMediaId = lastHandledPlayerTransitionMediaId
                    )
                ) {
                    Log.d(TAG, "Ignoring metadata-only player transition for mediaId=$transitionMediaId")
                    return
                }


                
                // Broadcast status for Tasker/KWGT/automation apps
                if (appSettings.broadcastStatusEnabled.value && mediaItem != null) {
                    try {
                        val song = convertMediaItemToSong(mediaItem)
                        if (song != null) {
                            statusBroadcaster.broadcastNowPlaying(
                                song,
                                player.isPlaying,
                                player.currentPosition,
                                player.mediaItemCount,
                                player.currentMediaItemIndex,
                                bluetoothLyricsMode = appSettings.bluetoothLyricsEnabled.value
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error broadcasting status on track change", e)
                    }
                }
                
                // Update widget when media item changes
                serviceScope.launch {
                    updateWidgetFromMediaItem(mediaItem)
                }

                lastHandledPlayerTransitionMediaId = transitionMediaId
            }
            
            // NEW in Media3 1.9.0: Monitor audio capabilities changes
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Log.d(TAG, "Audio session ID changed: $audioSessionId")
                // Reinitialize audio effects with new session
                if (audioSessionId != 0) {
                    initializeAudioEffects()
                }
            }
        }
        playerListener?.let { player.addListener(it) }
        
        // Initialize transition controller for crossfade scheduling
        transitionController = TransitionController(rhythmPlayerEngine, appSettings)
        transitionController.initialize()
        
        // Apply current settings
        applyPlayerSettings()
        
        // Try to initialize audio effects (might fail if session ID not ready)
        initializeAudioEffects()

        // Collect replayGain setting reactively
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                appSettings.replayGain,
                appSettings.replayGainMode,
                appSettings.replayGainDrc,
                appSettings.replayGainPreamp,
                appSettings.replayGainPreampUntagged
            ) { enabled, _, _, _, _ ->
                enabled
            }.collect { enabled ->
                rhythmPlayerEngine.applyReplayGainSettings(enabled)
            }
        }

        // Collect widget lyrics settings reactively
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                appSettings.showLyricsTranslation,
                appSettings.showLyricsRomanization
            ) { _, _ -> }.collect {
                if (currentLyricTexts.isNotEmpty()) {
                    chromahub.rhythm.app.infrastructure.widget.glance.GlanceWidgetUpdater.updateLyrics(
                        this@MediaPlaybackService,
                        getProcessedLyricTexts(),
                        currentLyricIndex
                    )
                }
            }
        }

        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                appSettings.bluetoothLyricsEnabled,
                appSettings.bluetoothLyricsLegacyCarModeEnabled
            ) { bluetoothLyricsEnabled, legacyCarModeEnabled ->
                bluetoothLyricsEnabled && legacyCarModeEnabled
            }.collect { legacyQueueEnabled ->
                if (::player.isInitialized) {
                    if (legacyQueueEnabled) collapseQueueForBtLyrics() else restoreQueueFromBtVirtual()
                }
                refreshNotificationControllerQueueExposure()
            }
        }

        // Car text mode is independent from the phone lyric display.
        serviceScope.launch {
            var previous = appSettings.bluetoothLyricsTextMode.value
            appSettings.bluetoothLyricsTextMode.collect { mode ->
                if (mode == previous) return@collect
                previous = mode
                lastServiceBtLyricLine = null

                val needsRomanization =
                    mode == BluetoothLyricsTextMode.ROMANIZATION &&
                        !currentLyricsHaveUsableRomanization()
                val needsTranslation =
                    mode == BluetoothLyricsTextMode.TRANSLATION &&
                        !currentLyricsHaveUsableTranslation()
                if (needsRomanization || needsTranslation) {
                    serviceLyricsLoadJob?.cancel()
                    serviceLyricsLoadJob = null
                    if (needsRomanization) {
                        serviceRomanizationLoadedSongId = null
                        serviceRomanizationAttemptCount = 0
                        serviceRomanizationNextRetryAtMs = 0L
                    }
                    if (needsTranslation) {
                        serviceTranslationLoadedSongId = null
                        serviceTranslationAttemptCount = 0
                        serviceTranslationNextRetryAtMs = 0L
                    }
                    player.currentMediaItem
                        ?.let(::convertMediaItemToSong)
                        ?.let { ensureServiceLyricsLoaded(it, initialDelayMs = 0L) }
                }
            }
        }
    }

    private inline fun <T> withEqualizerSafe(
        operation: String,
        defaultValue: T,
        block: (android.media.audiofx.Equalizer) -> T
    ): T {
        val eq = equalizer ?: return defaultValue
        return try {
            block(eq)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Skipping equalizer $operation because effect is not initialized", e)
            defaultValue
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer $operation failed", e)
            defaultValue
        }
    }

    private fun getEqualizerEnabledSafe(): Boolean {
        return withEqualizerSafe("enabled state read", false) { it.enabled }
    }

    private fun setEqualizerEnabledSafe(enabled: Boolean): Boolean {
        return withEqualizerSafe("enabled state write", false) { eq ->
            eq.enabled = enabled
            eq.enabled
        }
    }

    private fun setEqualizerEnabledWithVolumeGuard(enabled: Boolean): Boolean {
        if (!::player.isInitialized || !player.isPlaying) {
            if (!enabled) {
                // When disabling: just zero bands — don't toggle hardware EQ to avoid DSP burst
                withEqualizerSafe("set flat bands on disable", Unit) { eq ->
                    val numberOfBands = eq.numberOfBands.toInt()
                    for (i in 0 until numberOfBands) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                }
                return false
            }
            return setEqualizerEnabledSafe(true)
        }

        val restoreVolume = equalizerVolumeRestoreTarget ?: player.volume
        if (restoreVolume <= 0f) {
            if (!enabled) {
                withEqualizerSafe("set flat bands on disable", Unit) { eq ->
                    val numberOfBands = eq.numberOfBands.toInt()
                    for (i in 0 until numberOfBands) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                }
                return false
            }
            return setEqualizerEnabledSafe(true)
        }

        equalizerVolumeTransitionJob?.cancel()
        equalizerVolumeRestoreTarget = restoreVolume

        // 1. Duck the player volume to 0.0f to completely silence any transient audio during the hardware transition
        player.volume = 0.0f
        var actualState = enabled

        val transitionGeneration = ++equalizerVolumeTransitionGeneration
        equalizerVolumeTransitionJob = serviceScope.launch(Dispatchers.Main.immediate) {
            var rampCompleted = false
            try {
                // If disabling, set the band levels to 0 (flat) first before waiting,
                // so the DSP coefficients transition smoothly in the background.
                if (!enabled) {
                    withEqualizerSafe("set flat bands on disable", Unit) { eq ->
                        val numberOfBands = eq.numberOfBands.toInt()
                        for (i in 0 until numberOfBands) {
                            eq.setBandLevel(i.toShort(), 0)
                        }
                    }
                }

                // 2. Wait for the silent/ducked volume state to propagate and the audio track's buffer to clear
                // Use a longer delay (300ms) when disabling to ensure the audio buffer is completely drained
                val drainDelay = if (enabled) 120L else 300L
                delay(drainDelay)
                
                // 3. Safely toggle the hardware equalizer enabled state while fully silent
                // When disabling: don't toggle hardware EQ — zeroing bands avoids AudioFlinger DSP reset bursts
                if (enabled) {
                    actualState = setEqualizerEnabledSafe(true)
                } else {
                    actualState = false
                }
                
                // 4. Settle delay to allow Android AudioFlinger / hardware DSP to fully transition
                // Extend deactivation settle delay to 550ms so driver pops finish in complete silence before volume ramps up
                val settleDelay = if (enabled) 45L else 550L
                delay(settleDelay)
                
                // 5. Smoothly ramp the volume back up to the original target from 0.0f
                val startVolume = player.volume
                repeat(EQ_TOGGLE_RAMP_STEPS) { step ->
                    val fraction = (step + 1).toFloat() / EQ_TOGGLE_RAMP_STEPS.toFloat()
                    player.volume = startVolume + (restoreVolume - startVolume) * fraction
                    delay(EQ_TOGGLE_RAMP_STEP_DELAY_MS)
                }
                rampCompleted = true
            } finally {
                // Cancellation must not leave a transition-owned volume duck in place.
                if (!rampCompleted && transitionGeneration == equalizerVolumeTransitionGeneration) {
                    player.volume = restoreVolume
                }
                if (equalizerVolumeRestoreTarget == restoreVolume) {
                    equalizerVolumeRestoreTarget = null
                }
            }
        }

        return actualState
    }
    
    private fun handlePlaybackError(error: PlaybackException) {
        val message = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                "Audio codec not supported on this device"
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                "Cannot read audio file"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "Audio format not supported"
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "Audio file not found"
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                "Permission denied to access audio file"
            else -> "Playback error: ${error.message}"
        }
        Log.e(TAG, "Playback error: $message", error)
        
        // Prevent auto skip and looping loading on corrupted songs by pausing/stopping the player
        try {
            player.pause()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause player on error", e)
        }
    }

    private fun createController() {
        // Build the controller asynchronously to avoid blocking the main thread
        val controllerFuture = MediaController.Builder(this, mediaSession!!.token)
            .buildAsync()
        
        controllerFuture.addListener({
            try {
                controller = controllerFuture.get()
                controller?.addListener(this)
                // Only set custom layout if controller is properly initialized
                controller?.let {
                    forceCustomLayoutUpdate() // Use force update for initial setup
                }
                Log.d(TAG, "MediaController initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MediaController", e)
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
    }

    private fun createCustomCommands() {
        customCommands = listOf(
            CommandButton.Builder(CommandButton.ICON_SHUFFLE_OFF)
                .setDisplayName("Shuffle mode")
                .setSessionCommand(
                    SessionCommand(SHUFFLE_MODE_ON, Bundle.EMPTY)
                )
                .build(),
            CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setDisplayName("Shuffle mode")
                .setSessionCommand(
                    SessionCommand(SHUFFLE_MODE_OFF, Bundle.EMPTY)
                )
                .build(),
            CommandButton.Builder(CommandButton.ICON_REPEAT_OFF)
                .setDisplayName("Repeat mode")
                .setSessionCommand(
                    SessionCommand(REPEAT_MODE_ALL, Bundle.EMPTY)
                )
                .build(),
            CommandButton.Builder(CommandButton.ICON_REPEAT_ALL)
                .setDisplayName("Repeat mode")
                .setSessionCommand(
                    SessionCommand(REPEAT_MODE_ONE, Bundle.EMPTY)
                )
                .build(),
            CommandButton.Builder(CommandButton.ICON_REPEAT_ONE)
                .setDisplayName("Repeat mode")
                .setSessionCommand(
                    SessionCommand(REPEAT_MODE_OFF, Bundle.EMPTY)
                )
                .build(),
            // Favorite commands - use custom icons via extras bundle
            createCustomIconButton(
                "Add to favorites",
                FAVORITE_ON,
                chromahub.rhythm.app.R.drawable.ic_favorite_border
            ),
            createCustomIconButton(
                "Remove from favorites",
                FAVORITE_OFF,
                chromahub.rhythm.app.R.drawable.ic_favorite_filled
            )
        )
    }

    private fun createCustomIconButton(displayName: String, commandAction: String, iconResId: Int): CommandButton {
        val extras = Bundle().apply {
            putInt("iconResId", iconResId)
        }
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(displayName)
            .setSessionCommand(SessionCommand(commandAction, extras))
            .setExtras(extras)
            .build()
    }

    private fun createMediaSession(): MediaLibrarySession {
        // PendingIntent that launches MainActivity when user taps media controls
        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return MediaLibrarySession.Builder(
            this,
            player,
            MediaSessionCallback()
        ).setSessionActivity(pendingIntent)
            .build()
    }

    private fun playerCommandsForNotificationController(): Player.Commands {
        return MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
    }

    private fun refreshNotificationControllerQueueExposure() {
        val session = mediaSession ?: return
        val playerCommands = playerCommandsForNotificationController()
        session.connectedControllers.forEach { controller ->
            if (session.isMediaNotificationController(controller)) {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    .also { builder ->
                        for (commandButton in customCommands) {
                            commandButton.sessionCommand?.let { builder.add(it) }
                        }
                        builder.add(SessionCommand("UPDATE_ACTIVE_LYRIC", Bundle.EMPTY))
                        builder.add(SessionCommand("UPDATE_LYRICS_DATA", Bundle.EMPTY))
                        builder.add(SessionCommand(SESSION_COMMAND_BT_VIRTUAL_SKIP_NEXT, Bundle.EMPTY))
                        builder.add(SessionCommand(SESSION_COMMAND_BT_VIRTUAL_SKIP_PREVIOUS, Bundle.EMPTY))
                    }
                    .build()
                session.setAvailableCommands(controller, sessionCommands, playerCommands)
            }
        }
    }

    private fun isCurrentSongFavorite(): Boolean {
        val currentMediaItem = player.currentMediaItem
        return if (currentMediaItem != null) {
            // Get favorite songs from settings
            val favoriteSongsJson = appSettings.favoriteSongs.value
            if (favoriteSongsJson != null && favoriteSongsJson.isNotEmpty()) {
                try {
                    val type = object : TypeToken<Set<String>>() {}.type
                    val favoriteSongs: Set<String> = GsonUtils.gson.fromJson(favoriteSongsJson, type)
                    favoriteSongs.contains(currentMediaItem.mediaId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing favorite songs", e)
                    false
                }
            } else {
                false
            }
        } else {
            false
        }
    }
    
    private fun toggleCurrentSongFavorite() {
        val currentMediaItem = player.currentMediaItem
        val songId = currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: run {
            // FALLBACK: Read song_id from widget preferences if player has no active song
            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val id = prefs.getString("song_id", null)
            if (id.isNullOrEmpty()) null else id
        }

        if (songId != null) {
            try {
                val favoriteSongsJson = appSettings.favoriteSongs.value
                val currentFavorites = if (favoriteSongsJson != null && favoriteSongsJson.isNotEmpty()) {
                    try {
                        val type = object : TypeToken<Set<String>>() {}.type
                        GsonUtils.gson.fromJson<Set<String>>(favoriteSongsJson, type).toMutableSet()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing favorite songs", e)
                        mutableSetOf<String>()
                    }
                } else {
                    mutableSetOf<String>()
                }

                val isAdding = !currentFavorites.contains(songId)

                if (isAdding) {
                    currentFavorites.add(songId)
                    Log.d(TAG, "Added song to favorites via widget/notification: $songId")
                } else {
                    currentFavorites.remove(songId)
                    Log.d(TAG, "Removed song from favorites via widget/notification: $songId")
                }

                appSettings.setFavoriteSongs(GsonUtils.gson.toJson(currentFavorites))
                
                // Fetch song details from mediaItem or construct fallback Song using preferences
                val song = if (currentMediaItem != null) {
                    convertMediaItemToSong(currentMediaItem)
                } else {
                    val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                    Song(
                        id = songId,
                        title = prefs.getString("song_title", "Rhythm") ?: "Rhythm",
                        artist = prefs.getString("artist_name", "") ?: "",
                        album = prefs.getString("album_name", "") ?: "",
                        uri = Uri.EMPTY,
                        artworkUri = prefs.getString("artwork_uri", null)?.let { 
                            try { Uri.parse(it) } catch (_: Exception) { null } 
                        },
                        duration = 0L,
                        trackNumber = 0,
                        year = 0,
                        genre = "",
                        albumId = ""
                    )
                }
                updateFavoritesPlaylist(songId = songId, song = song, isAdding = isAdding)

                val notifyIntent = Intent("chromahub.rhythm.app.action.FAVORITE_CHANGED").apply {
                    setPackage(packageName)
                }
                sendBroadcast(notifyIntent)
                Log.d(TAG, "Sent FAVORITE_CHANGED broadcast to notify ViewModel")

                scheduleCustomLayoutUpdate(120)
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling favorite", e)
            }
        }
    }

    private fun updateFavoritesPlaylist(songId: String, song: Song?, isAdding: Boolean) {
        try {
            val playlistsJson = appSettings.playlists.value
            if (playlistsJson.isNullOrEmpty()) return
            
            val type = object : TypeToken<List<Playlist>>() {}.type
            val playlists: MutableList<Playlist> = GsonUtils.gson.fromJson(playlistsJson, type)
            
            val favoritesPlaylist = playlists.find { it.id == "1" && it.name == "Liked" } ?: return
            val existingSongs = favoritesPlaylist.songs
            val updatedSongs = when {
                isAdding && song != null && existingSongs.none { it.id == songId } -> existingSongs + song
                isAdding -> existingSongs
                else -> existingSongs.filterNot { it.id == songId }
            }

            if (updatedSongs == existingSongs) return

            val updatedPlaylist = favoritesPlaylist.copy(
                songs = updatedSongs,
                dateModified = System.currentTimeMillis()
            )

            val updatedPlaylists = playlists.map { if (it.id == "1") updatedPlaylist else it }
            appSettings.setPlaylists(GsonUtils.gson.toJson(updatedPlaylists))
            Log.d(TAG, "Updated Liked playlist: ${if (isAdding) "added" else "removed"} song $songId")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating favorites playlist", e)
        }
    }
    
    private fun updateCustomLayout() {
        try {
            // Create a new instance of the favorite command to avoid reference issues
            val currentFavoriteCommand = getCurrentFavoriteCommand()
            val currentShuffleCommand = shuffleCommand
            val currentRepeatCommand = repeatCommand
            
            mediaSession?.setCustomLayout(ImmutableList.of(currentShuffleCommand, currentRepeatCommand))
            
            // Update state tracking after successful update
            lastShuffleState = controller?.shuffleModeEnabled ?: false
            lastRepeatMode = controller?.repeatMode ?: Player.REPEAT_MODE_OFF
            lastFavoriteState = isCurrentSongFavorite()
            
            val currentMediaItem = player.currentMediaItem
            Log.d(TAG, "Updated custom layout - Song: ${currentMediaItem?.mediaMetadata?.title}, " +
                      "Favorite state: ${lastFavoriteState}, " +
                      "Shuffle: ${lastShuffleState}, " +
                      "Repeat: ${lastRepeatMode}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating custom layout", e)
        }
    }
    
    private fun updateCustomLayoutSmart() {
        // Only update if layout actually needs to change
        // This helps prevent unnecessary recreations and flickering
        mediaSession?.let { session ->
            try {
                val currentShuffleState = controller?.shuffleModeEnabled ?: false
                val currentRepeatMode = controller?.repeatMode ?: Player.REPEAT_MODE_OFF
                val currentFavoriteState = isCurrentSongFavorite()
                
                // Check if anything actually changed
                if (currentShuffleState == lastShuffleState &&
                    currentRepeatMode == lastRepeatMode &&
                    currentFavoriteState == lastFavoriteState) {
                    Log.d(TAG, "Custom layout state unchanged, skipping update")
                    return
                }
                
                // Update state tracking
                lastShuffleState = currentShuffleState
                lastRepeatMode = currentRepeatMode
                lastFavoriteState = currentFavoriteState
                
                val currentFavoriteCommand = getCurrentFavoriteCommand()
                val currentShuffleCommand = shuffleCommand
                val currentRepeatCommand = repeatCommand
                
                // Create the layout
                session.setCustomLayout(ImmutableList.of(currentShuffleCommand, currentRepeatCommand))
                
                Log.d(TAG, "Smart updated custom layout - Favorite: $currentFavoriteState, " +
                          "Shuffle: $currentShuffleState, Repeat: $currentRepeatMode")
            } catch (e: Exception) {
                Log.e(TAG, "Error in smart custom layout update", e)
            }
        }
    }
    
    private fun scheduleCustomLayoutUpdate(delayMs: Long = 150) {
        // Cancel any pending update
        updateLayoutJob?.cancel()
        
        // Schedule a new update with debouncing
        updateLayoutJob = serviceScope.launch {
            kotlinx.coroutines.delay(delayMs)
            updateCustomLayoutSmart()
        }
    }
    
    private fun forceCustomLayoutUpdate() {
        // Force an immediate update without debouncing (for initial setup)
        serviceScope.launch {
            updateCustomLayout()
        }
    }

    /**
     * Requests Android 14+ preferred mixer attributes for USB output when app routing is selected.
     * This is the platform-side requirement for exclusive/bit-perfect mixer behavior when available.
     */
    private fun applyUsbExclusiveRoutingPreference() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mediaAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        if (appSettings.audioRoutingMode.value != "app") {
            clearUsbPreferredMixerAttributes(audioManager, mediaAttributes)
            return
        }

        val usbOutput = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }

        if (usbOutput == null) {
            Log.i(TAG, "App routing enabled but no USB output device is connected")
            return
        }

        try {
            val supportedMixerAttributes = audioManager.getSupportedMixerAttributes(usbOutput)
            val bitPerfectMixer = supportedMixerAttributes.firstOrNull {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
            }

            if (bitPerfectMixer == null) {
                Log.w(TAG, "USB device does not expose a bit-perfect mixer profile")
                return
            }

            audioManager.setPreferredMixerAttributes(mediaAttributes, usbOutput, bitPerfectMixer)
            Log.i(TAG, "Requested bit-perfect USB mixer attributes for app routing mode")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request USB preferred mixer attributes", e)
        }
    }

    private fun clearUsbPreferredMixerAttributes(
        audioManager: AudioManager,
        mediaAttributes: android.media.AudioAttributes
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }

        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            .forEach { device ->
                try {
                    audioManager.clearPreferredMixerAttributes(mediaAttributes, device)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear preferred mixer attributes for USB device", e)
                }
            }
    }
    
    private fun applyPlayerSettings() {
        applyUsbExclusiveRoutingPreference()
        player.apply {
            // Audio normalization - NOT IMPLEMENTED
            // if (appSettings.audioNormalization.value) {
            //     volume = 1.0f
            // }
        }

        // Apply Replay Gain settings
        rhythmPlayerEngine.applyReplayGainSettings(appSettings.replayGain.value)

        // Apply gapless playback setting
        rhythmPlayerEngine.setGaplessPlayback(appSettings.gaplessPlayback.value)

        // Apply skip silence setting
        rhythmPlayerEngine.setSkipSilenceEnabled(appSettings.skipSilenceEnabled.value)

        // Crossfade is now managed by TransitionController + RhythmPlayerEngine
        // Settings are read reactively from AppSettings by the controller

        Log.d(TAG, "Applied player settings: " +
                "Gapless=${appSettings.gaplessPlayback.value}, " +
                "SkipSilence=${appSettings.skipSilenceEnabled.value}, " +
                "Crossfade=${appSettings.crossfade.value} (${appSettings.crossfadeDuration.value}s)")
                // Normalization and ReplayGain removed as not implemented
    }
    
    // Crossfade is now handled by RhythmPlayerEngine + TransitionController
    // See: infrastructure/service/player/RhythmPlayerEngine.kt
    // See: infrastructure/service/player/TransitionController.kt

    // Skip debounce state for widget actions
    private var lastServiceSkipTime = 0L
    private val SERVICE_SKIP_DEBOUNCE_MS = 400L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with command: ${intent?.action}")

        // Media3 owns notification 1001 after a media item loads; the placeholder is only for cold starts.
        if (!::player.isInitialized || player.currentMediaItem == null) {
            startForegroundWithNotification(
                getString(chromahub.rhythm.app.R.string.service_rhythm_music),
                getString(chromahub.rhythm.app.R.string.service_starting)
            )
        }
        
        when (intent?.action) {
            ACTION_UPDATE_SETTINGS -> {
                Log.d(TAG, "Updating service settings")
                applyPlayerSettings()
            }
            ACTION_PLAY_EXTERNAL_FILE -> {
                intent.data?.let { uri ->
                    playExternalFile(uri)
                }
            }
            ACTION_INIT_SERVICE -> {
                Log.d(TAG, "Service initialization requested")
                // Load and apply settings when service starts
                applyPlayerSettings()
            }
            ACTION_START_SLEEP_TIMER -> {
                val durationMs = intent.getLongExtra("duration", 0L)
                val fadeOut = intent.getBooleanExtra("fadeOut", true)
                val pauseOnly = intent.getBooleanExtra("pauseOnly", false)
                if (durationMs > 0) {
                    startSleepTimer(durationMs, fadeOut, pauseOnly)
                }
            }
            ACTION_STOP_SLEEP_TIMER -> {
                stopSleepTimer()
            }
            ACTION_EXTEND_SLEEP_TIMER -> {
                extendSleepTimer()
            }
            ACTION_SET_EQUALIZER_ENABLED -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                Log.d(TAG, "Received intent to set equalizer enabled: $enabled")
                setEqualizerEnabled(enabled)
                
                // Broadcast current state back for UI verification
                val actualState = getEqualizerEnabledSafe()
                if (actualState != enabled) {
                    if (enabled) {
                        Log.w(TAG, "Equalizer state verification failed. Requested: $enabled, Actual: $actualState")
                    } else {
                        Log.d(TAG, "Equalizer hardware kept enabled (software off) to avoid DSP burst")
                    }
                }
            }
            ACTION_SET_EQUALIZER_BAND -> {
                val band = intent.getShortExtra("band", 0)
                val level = intent.getShortExtra("level", 0)
                if (equalizer == null) {
                    Log.e(TAG, "Cannot set band level: equalizer is null")
                    return START_NOT_STICKY
                }
                setEqualizerBandLevel(band, level)
            }
            ACTION_SET_BASS_BOOST -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                val strength = intent.getShortExtra("strength", 0)
                Log.d(TAG, "Received intent to set bass boost - enabled: $enabled, strength: $strength")
                
                if (rhythmBassBoostProcessor == null) {
                    Log.d(TAG, "Rhythm bass boost processor is null, attempting initialization")
                    initializeRhythmProcessors()
                }
                
                setBassBoostEnabled(enabled)
                if (enabled) setBassBoostStrength(strength)
            }
            ACTION_SET_VIRTUALIZER -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                val strength = intent.getShortExtra("strength", 0)
                Log.d(TAG, "Received intent to set virtualizer - enabled: $enabled, strength: $strength")
                
                if (rhythmSpatializationProcessor == null) {
                    Log.d(TAG, "Rhythm spatialization processor is null, attempting initialization")
                    initializeRhythmProcessors()
                }
                
                setVirtualizerEnabled(enabled)
                if (enabled) setVirtualizerStrength(strength)
            }
            ACTION_APPLY_EQUALIZER_PRESET -> {
                val preset = intent.getStringExtra("preset") ?: ""
                val levels = intent.getFloatArrayExtra("levels")
                if (levels != null) {
                    if (equalizer == null) {
                        Log.e(TAG, "Cannot apply preset: equalizer is null")
                        // Try to initialize if session ID is available
                        if (getPlayerAudioSessionId() != 0) {
                            Log.d(TAG, "Attempting to initialize equalizer before applying preset")
                            initializeAudioEffects()
                            // Try applying again after initialization
                            if (equalizer != null) {
                                applyEqualizerPreset(levels)
                                Log.d(TAG, "Applied equalizer preset after initialization: $preset with ${levels.size} bands")
                            } else {
                                Log.e(TAG, "Failed to initialize equalizer, cannot apply preset")
                            }
                        }
                    } else {
                        applyEqualizerPreset(levels)
                        Log.d(TAG, "Applied equalizer preset: $preset with ${levels.size} bands")
                    }
                }
            }
            ACTION_GET_EQUALIZER_DIAGNOSTICS -> {
                val diagnostics = getEqualizerDiagnostics()
                Log.i(TAG, diagnostics)
            }
            ACTION_PLAY_PAUSE -> {
                Log.d(TAG, "Widget play/pause action")
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
                // Update widget immediately after action
                updateWidgetFromMediaItem(player.currentMediaItem)
            }
            ACTION_SKIP_NEXT -> {
                Log.d(TAG, "Widget skip next action")
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastServiceSkipTime >= SERVICE_SKIP_DEBOUNCE_MS) {
                    lastServiceSkipTime = currentTime
                    player.seekToNext()
                    // Update widget immediately after action
                    serviceScope.launch {
                        kotlinx.coroutines.delay(100) // Small delay for track change
                        updateWidgetFromMediaItem(player.currentMediaItem)
                    }
                }
            }
            ACTION_SKIP_PREVIOUS -> {
                Log.d(TAG, "Widget skip previous action")
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastServiceSkipTime >= SERVICE_SKIP_DEBOUNCE_MS) {
                    lastServiceSkipTime = currentTime
                    player.seekToPrevious()
                    // Update widget immediately after action
                    serviceScope.launch {
                        kotlinx.coroutines.delay(100) // Small delay for track change
                        updateWidgetFromMediaItem(player.currentMediaItem)
                    }
                }
            }
            ACTION_TOGGLE_FAVORITE -> {
                Log.d(TAG, "Widget toggle favorite action")
                toggleCurrentSongFavorite()
                val currentMediaItem = player.currentMediaItem
                if (currentMediaItem != null) {
                    updateWidgetFromMediaItem(currentMediaItem)
                } else {
                    // Update only the favorite state in the widget without clearing it!
                    val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                    val songId = prefs.getString("song_id", null)
                    if (songId != null && songId.isNotEmpty()) {
                        val favoriteSongsJson = appSettings.favoriteSongs.value
                        val currentFavorites = if (favoriteSongsJson != null && favoriteSongsJson.isNotEmpty()) {
                            try {
                                val type = object : TypeToken<Set<String>>() {}.type
                                GsonUtils.gson.fromJson<Set<String>>(favoriteSongsJson, type)
                            } catch (e: Exception) {
                                emptySet()
                            }
                        } else {
                            emptySet()
                        }
                        val isFavorite = currentFavorites.contains(songId)
                        
                        val song = Song(
                            id = songId,
                            title = prefs.getString("song_title", "Rhythm") ?: "Rhythm",
                            artist = prefs.getString("artist_name", "") ?: "",
                            album = prefs.getString("album_name", "") ?: "",
                            uri = Uri.EMPTY,
                            artworkUri = prefs.getString("artwork_uri", null)?.let { 
                                try { Uri.parse(it) } catch (_: Exception) { null }
                            },
                            duration = 0L,
                            trackNumber = 0,
                            year = 0,
                            genre = "",
                            albumId = ""
                        )
                        val isPlaying = prefs.getBoolean("is_playing", false)
                        val hasPrevious = prefs.getBoolean("has_previous", false)
                        val hasNext = prefs.getBoolean("has_next", false)
                        
                        WidgetUpdater.updateWidget(this, song, isPlaying, hasPrevious, hasNext, isFavorite)
                    }
                }
            }
            ACTION_MUTE -> {
                Log.d(TAG, "Mute action")
                mutePlayer()
            }
            ACTION_UNMUTE -> {
                Log.d(TAG, "Unmute action")
                unmutePlayer()
            }
            ACTION_TOGGLE_MUTE -> {
                Log.d(TAG, "Toggle mute action")
                toggleMute()
            }
        }
        
        // We make sure to call the super implementation
        return super.onStartCommand(intent, flags, startId)
    }
    
    private fun wrapPlayer(rawPlayer: Player): Player = RhythmForwardingPlayer(rawPlayer)

    private inner class RhythmForwardingPlayer(rawPlayer: Player) : ForwardingPlayer(rawPlayer) {
        private val injectionListeners = java.util.concurrent.CopyOnWriteArrayList<Player.Listener>()
        @Volatile private var injectedTitle: CharSequence? = null
        @Volatile private var injectedArtist: CharSequence? = null

        override fun addListener(listener: Player.Listener) {
            injectionListeners.add(listener)
            super.addListener(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            injectionListeners.remove(listener)
            super.removeListener(listener)
        }

        override fun getMediaMetadata(): androidx.media3.common.MediaMetadata {
            val base = super.getMediaMetadata()
            val title = injectedTitle ?: return base
            return base.buildUpon()
                .setTitle(title)
                .setArtist(injectedArtist ?: base.artist)
                .build()
        }

        override fun getRepeatMode(): Int =
            if (btVirtualOriginalQueue != null && btVirtualQueueActive()) {
                btVirtualOriginalRepeatMode
            } else {
                super.getRepeatMode()
            }

        override fun setRepeatMode(repeatMode: Int) {
            if (btVirtualOriginalQueue == null || !btVirtualQueueActive() || isMutatingBtVirtualQueue) {
                super.setRepeatMode(repeatMode)
                return
            }

            val safeMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF,
                Player.REPEAT_MODE_ONE,
                Player.REPEAT_MODE_ALL -> repeatMode

                else -> Player.REPEAT_MODE_OFF
            }
            if (btVirtualOriginalRepeatMode == safeMode) return

            btVirtualOriginalRepeatMode = safeMode
            injectionListeners.forEach { it.onRepeatModeChanged(safeMode) }
            notifyBtCommandsChanged()
            Log.d(TAG, "Legacy car mode: effective repeat mode changed to $safeMode")
        }

        fun injectLyricMetadata(title: CharSequence?, artist: CharSequence?) {
            injectedTitle = title
            injectedArtist = artist
            val md = getMediaMetadata()
            injectionListeners.forEach { it.onMediaMetadataChanged(md) }
        }

        fun clearLyricMetadata() {
            if (injectedTitle == null && injectedArtist == null) return
            injectedTitle = null
            injectedArtist = null
            val md = getMediaMetadata()
            injectionListeners.forEach { it.onMediaMetadataChanged(md) }
        }

        /**
         * Replaces metadata without treating the service-owned lyric update as an external queue
         * edit. The normal override must continue restoring the real queue for app/controller
         * edits while legacy car mode exposes its temporary single-item queue.
         */
        fun replaceLyricMetadata(index: Int, mediaItem: MediaItem) {
            super.replaceMediaItem(index, mediaItem)
        }

        /**
         * Queue edits originate from the app's normal controller. Restore the real playlist
         * before forwarding one so adding, removing, or reordering while legacy mode is on
         * never applies to the temporary single-item AVRCP view.
         */
        private fun restoreBeforeExternalQueueMutation() {
            if (btVirtualQueueActive() && !isMutatingBtVirtualQueue && btVirtualOriginalQueue != null) {
                restoreQueueFromBtVirtual()
            }
        }

        override fun setMediaItems(mediaItems: List<MediaItem>) {
            restoreBeforeExternalQueueMutation()
            super.setMediaItems(mediaItems)
        }

        override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
            restoreBeforeExternalQueueMutation()
            super.setMediaItems(mediaItems, resetPosition)
        }

        override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
            restoreBeforeExternalQueueMutation()
            super.setMediaItems(mediaItems, startIndex, startPositionMs)
        }

        override fun setMediaItem(mediaItem: MediaItem) {
            restoreBeforeExternalQueueMutation()
            super.setMediaItem(mediaItem)
        }

        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
            restoreBeforeExternalQueueMutation()
            super.setMediaItem(mediaItem, startPositionMs)
        }

        override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
            restoreBeforeExternalQueueMutation()
            super.setMediaItem(mediaItem, resetPosition)
        }

        override fun addMediaItem(mediaItem: MediaItem) {
            restoreBeforeExternalQueueMutation()
            super.addMediaItem(mediaItem)
        }

        override fun addMediaItem(index: Int, mediaItem: MediaItem) {
            restoreBeforeExternalQueueMutation()
            super.addMediaItem(index, mediaItem)
        }

        override fun addMediaItems(mediaItems: List<MediaItem>) {
            restoreBeforeExternalQueueMutation()
            super.addMediaItems(mediaItems)
        }

        override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
            restoreBeforeExternalQueueMutation()
            super.addMediaItems(index, mediaItems)
        }

        override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
            restoreBeforeExternalQueueMutation()
            super.moveMediaItem(currentIndex, newIndex)
        }

        override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
            restoreBeforeExternalQueueMutation()
            super.moveMediaItems(fromIndex, toIndex, newIndex)
        }

        override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
            restoreBeforeExternalQueueMutation()
            super.replaceMediaItem(index, mediaItem)
        }

        override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
            restoreBeforeExternalQueueMutation()
            super.replaceMediaItems(fromIndex, toIndex, mediaItems)
        }

        override fun removeMediaItem(index: Int) {
            restoreBeforeExternalQueueMutation()
            super.removeMediaItem(index)
        }

        override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
            restoreBeforeExternalQueueMutation()
            super.removeMediaItems(fromIndex, toIndex)
        }

        override fun clearMediaItems() {
            restoreBeforeExternalQueueMutation()
            super.clearMediaItems()
        }

        override fun getAvailableCommands(): Player.Commands {
            val base = super.getAvailableCommands()
            if (!btVirtualQueueActive()) return base
            val builder = base.buildUpon()
            if (btVirtualUpcoming.isNotEmpty()) {
                builder.add(Player.COMMAND_SEEK_TO_NEXT)
                builder.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            if (btVirtualHistory.isNotEmpty()) {
                builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
                builder.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }
            return builder.build()
        }

        fun notifyBtCommandsChanged() {
            val cmds = getAvailableCommands()
            injectionListeners.forEach { it.onAvailableCommandsChanged(cmds) }
        }

        override fun seekToNext() {
            if (btVirtualQueueActive() && btVirtualAdvance()) return
            if (!skipWithCrossfade(toNext = true)) super.seekToNext()
        }

        override fun seekToNextMediaItem() {
            if (btVirtualQueueActive() && btVirtualAdvance()) return
            if (!skipWithCrossfade(toNext = true)) super.seekToNextMediaItem()
        }

        override fun seekToPrevious() {
            if (btVirtualQueueActive() && btVirtualHistory.isNotEmpty()) {
                if (currentPosition > 3000L) { seekTo(0); return }
                if (btVirtualPrevious()) return
            }
            if (!skipWithCrossfade(toNext = false)) super.seekToPrevious()
        }

        override fun seekToPreviousMediaItem() {
            if (btVirtualQueueActive() && btVirtualPrevious()) return
            if (!skipWithCrossfade(toNext = false)) super.seekToPreviousMediaItem()
        }
    }

    private var lastGlobalSkipTime = 0L
    private val GLOBAL_SKIP_DEBOUNCE_MS = 600L

    private fun skipWithCrossfade(toNext: Boolean): Boolean {
        try {
            if (!appSettings.crossfade.value || !appSettings.crossfadeOnSkip.value) {
                return false
            }

            // Rate-limiting check to prevent ExoPlayer looper lockup under rapid spam clicks
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastGlobalSkipTime < GLOBAL_SKIP_DEBOUNCE_MS) {
                Log.d(TAG, "Ignored rapid skipWithCrossfade to prevent lockup. Falling back to standard skip.")
                return false
            }
            lastGlobalSkipTime = currentTime

            if (rhythmPlayerEngine.isTransitionRunning()) {
                Log.d(TAG, "Transition is running during skip request. Force completing it first and falling back to standard skip.")
                if (::transitionController.isInitialized) {
                    transitionController.cancelPendingTransition()
                } else {
                    rhythmPlayerEngine.cancelNext()
                }
                return false
            }

            val playerToUse = rhythmPlayerEngine.masterPlayer
            if (!playerToUse.isPlaying) {
                Log.d(TAG, "Player is not playing, skipping instant without crossfade")
                return false
            }

            val repeatMode = playerToUse.repeatMode
            val currentWindowIndex = playerToUse.currentMediaItemIndex
            val timeline = playerToUse.currentTimeline

            if (timeline.isEmpty || currentWindowIndex == C.INDEX_UNSET) {
                return false
            }

            // Handled case: previous skip when track has played for over 5s (restarts track)
            if (!toNext && playerToUse.currentPosition > 5000) {
                Log.d(TAG, "Previous skip past 5s, restarting track")
                return false
            }

            val nextIndex = if (toNext) {
                timeline.getNextWindowIndex(
                    currentWindowIndex,
                    repeatMode,
                    playerToUse.shuffleModeEnabled
                )
            } else {
                timeline.getPreviousWindowIndex(
                    currentWindowIndex,
                    repeatMode,
                    playerToUse.shuffleModeEnabled
                )
            }

            if (nextIndex == C.INDEX_UNSET) {
                return false
            }

            val nextMediaItem = playerToUse.getMediaItemAt(nextIndex)

            Log.d(TAG, "Skipping with crossfade. Target track: ${nextMediaItem.mediaId}")

            // Cancel any pending transitions
            if (::transitionController.isInitialized) {
                transitionController.cancelPendingTransition()
            }

            // Prepare the next song
            rhythmPlayerEngine.prepareNext(nextMediaItem)

            val settings = TransitionSettings(
                mode = TransitionMode.OVERLAP,
                durationMs = 1000,
                isManualSkip = true,
                isSkipPrevious = !toNext
            )

            if (::transitionController.isInitialized) {
                transitionController.setManualTransitioning()
            }

            rhythmPlayerEngine.performTransition(settings)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error performing skip with crossfade, falling back to standard skip", e)
            return false
        }
    }

    /**
     * Play an external audio file
     */
    private fun playExternalFile(uri: Uri) {
        Log.d(TAG, "Playing external file: $uri")

        // Use service-scoped coroutine to handle operations without blocking the main thread
        serviceScope.launch {
            try {
                // Respect Rhythm Guard timeout: do not start external playback if a timeout is active
                val now = System.currentTimeMillis()
                if (appSettings.rhythmGuardTimeoutUntilMs.value > now) {
                    Log.d(TAG, "Refusing to play external file due to active Rhythm Guard timeout: $uri")
                    return@launch
                }
                // Check if we've seen this URI before (on main thread - quick cache lookup)
                val cachedItem = externalUriCache[uri.toString()]
                if (cachedItem != null) {
                    Log.d(TAG, "Using cached media item for URI: $uri")
                    
                    // Clear the player first to avoid conflicts with existing items
                    player.clearMediaItems()
                    
                    // Play the media item
                    player.setMediaItem(cachedItem)
                    player.prepare()
                    player.play()
                    
                    return@launch
                }
                
                // Add a small delay before processing to allow previous operations to complete
                delay(500)
                
                // Extract metadata from the audio file in a background thread
                val mediaItem = withContext(Dispatchers.IO) {
                    val resolvedMimeType = chromahub.rhythm.app.util.MediaUtils.getMediaMimeType(this@MediaPlaybackService, uri)
                    try {
                        val song = chromahub.rhythm.app.util.MediaUtils.extractMetadataFromUri(this@MediaPlaybackService, uri)
                        Log.d(TAG, "Extracted metadata for external file: ${song.title} by ${song.artist}")
                        
                        // Create a media item with the extracted metadata
                        MediaItem.Builder()
                            .setUri(uri)
                            .setMediaId(uri.toString())
                            .apply {
                                if (resolvedMimeType != null) {
                                    setMimeType(resolvedMimeType)
                                }
                            }
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(song.title)
                                    .setArtist(song.artist)
                                    .setAlbumTitle(song.album)
                                    .setArtworkUri(song.artworkUri)
                                    .build()
                            )
                            .build()
                            
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting metadata from external file", e)
                        
                        // Fall back to basic playback if metadata extraction fails
                        Log.d(TAG, "Falling back to basic playback with mime type: $resolvedMimeType")
                        
                        MediaItem.Builder()
                            .setUri(uri)
                            .apply {
                                if (resolvedMimeType != null) {
                                    setMimeType(resolvedMimeType)
                                }
                            }
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(uri.lastPathSegment ?: "Unknown")
                                    .build()
                            )
                            .build()
                    }
                }
                
                // Back on main thread - set up playback
                player.clearMediaItems()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
                
                // Cache the media item
                externalUriCache[uri.toString()] = mediaItem
                
                // Force a recheck of playback state in case it doesn't start
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            Log.d(TAG, "Playback ready, ensuring play is called")
                            player.play()
                            player.removeListener(this)
                        }
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in playExternalFile coroutine", e)
            }
        }
    }

    override fun onDestroy() {
        instanceForWidgetAndLyricsOnly = null
        Log.d(TAG, "Service being destroyed")

        // Persist final playback position and index on destroy
        if (::player.isInitialized && appSettings.queuePersistenceEnabled.value) {
            val currentIndex = player.currentMediaItemIndex
            if (currentIndex != androidx.media3.common.C.INDEX_UNSET) {
                appSettings.setSavedQueueIndex(currentIndex)
                appSettings.setSavedPlaybackPosition(player.currentPosition)
                Log.d(TAG, "Persisted queue index $currentIndex and position ${player.currentPosition} on service destroy")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val mediaAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            clearUsbPreferredMixerAttributes(audioManager, mediaAttributes)
        }
        
        // Unregister BroadcastReceiver
        try {
            unregisterReceiver(favoriteChangeReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering favorite change receiver", e)
        }
        try {
            unregisterReceiver(btReceiver)
        } catch (_: Exception) {}
        btProxy = null
        try {
            unregisterReceiver(volumeChangeReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering volume change receiver", e)
        }
        
        // Cancel all coroutines and pending jobs
        updateLayoutJob?.cancel()
        sleepTimerJob?.cancel()
        audioEffectsInitJob?.cancel()
        cancelSleepTimerProgressNotification()
        serviceScope.cancel()
        
        // Release preloader
        if (::preloadController.isInitialized) {
            preloadController.release()
        }
        
        // Release crossfade engine and transition controller
        transitionController.release()
        rhythmPlayerEngine.release()
        
        // Release audio effects
        releaseAudioEffects()
        
        // Remove player listener before releasing player
        playerListener?.let { player.removeListener(it) }
        playerListener = null
        
        // Remove service as listener from controller
        controller?.removeListener(this)
        
        mediaSession?.run {
            player.release()
            controller?.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession
    
    @OptIn(UnstableApi::class)
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Let Media3 handle notification updates but ensure our icon is used
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private inner class MediaSessionCallback : MediaLibrarySession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            Log.d(TAG, "onConnect: ${controller.packageName}")
            val availableCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
            if (session.isMediaNotificationController(controller) ||
                session.isAutoCompanionController(controller) ||
                session.isAutomotiveController(controller)
            ) {
                for (commandButton in customCommands) {
                    commandButton.sessionCommand?.let { availableCommands.add(it) }
                }
            }
            availableCommands.add(SessionCommand("UPDATE_ACTIVE_LYRIC", Bundle.EMPTY))
            availableCommands.add(SessionCommand("UPDATE_LYRICS_DATA", Bundle.EMPTY))
            availableCommands.add(SessionCommand(SESSION_COMMAND_BT_VIRTUAL_SKIP_NEXT, Bundle.EMPTY))
            availableCommands.add(SessionCommand(SESSION_COMMAND_BT_VIRTUAL_SKIP_PREVIOUS, Bundle.EMPTY))
            val resultBuilder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableCommands.build())
            if (session.isMediaNotificationController(controller)) {
                resultBuilder.setAvailablePlayerCommands(playerCommandsForNotificationController())
            }
            return resultBuilder.build()
        }

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val serviceController = this@MediaPlaybackService.controller
            if (serviceController == null) {
                Log.w(TAG, "Controller not ready for custom command: ${customCommand.customAction}")
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_SESSION_DISCONNECTED))
            }
            
            return Futures.immediateFuture(
                when (customCommand.customAction) {
                    "UPDATE_LYRICS_DATA" -> {
                        val commandSongId = args.getString("song_id")
                        val currentSongId = player.currentMediaItem?.mediaId
                        if (commandSongId != null && commandSongId != currentSongId) {
                            Log.d(TAG, "Ignoring stale lyrics data for mediaId=$commandSongId")
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }

                        val texts = args.getStringArrayList("lyric_texts") ?: emptyList()
                        val translations = args.getStringArrayList("lyric_translations") ?: emptyList()
                        val romanizations = args.getStringArrayList("lyric_romanizations") ?: emptyList()
                        val timestamps = args.getLongArray("lyric_timestamps") ?: longArrayOf()
                        val plainLyrics = args.getString("plain_lyrics")
                        val incomingSource = args.getString("lyrics_source")
                        val plainLines = if (!plainLyrics.isNullOrBlank()) {
                            plainLyrics.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        } else {
                            emptyList()
                        }
                        val incomingHasLyrics = texts.isNotEmpty() || plainLines.isNotEmpty()
                        val currentHasLyrics = currentLyricTexts.isNotEmpty() || currentPlainLyricsLines.isNotEmpty()
                        if (!incomingHasLyrics) {
                            if (currentHasLyrics) {
                                Log.d(TAG, "Ignoring empty lyrics update for mediaId=$currentSongId")
                            }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }

                        val incomingHasRomanization = LyricsRomanizationPolicy.hasUsableCoverage(
                            original = texts,
                            supplemental = romanizations
                        )
                        val incomingHasTranslation = LyricsTranslationPolicy.hasUsableCoverage(
                            original = texts,
                            supplemental = translations
                        )
                        val currentHasRomanization = currentLyricsHaveUsableRomanization()
                        val currentHasTranslation = currentLyricsHaveUsableTranslation()
                        if (
                            (appSettings.bluetoothLyricsTextMode.value ==
                                BluetoothLyricsTextMode.ROMANIZATION &&
                                currentHasRomanization &&
                                !incomingHasRomanization) ||
                            (appSettings.bluetoothLyricsTextMode.value ==
                                BluetoothLyricsTextMode.TRANSLATION &&
                                currentHasTranslation &&
                                !incomingHasTranslation)
                        ) {
                            Log.d(
                                TAG,
                                "Ignoring lyrics downgrade for mediaId=$currentSongId: " +
                                        "currentSource=$currentLyricsSource, incomingSource=$incomingSource"
                            )
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }

                        currentLyricTexts = texts
                        currentLyricTranslations = translations
                        currentLyricRomanizations = romanizations
                        currentLyricTimestamps = timestamps
                        currentPlainLyricsLines = plainLines
                        currentLyricsSource = incomingSource

                        currentLyricIndex = -1
                        serviceLyricsLoadedSongId = currentSongId
                        if (incomingHasRomanization) {
                            serviceRomanizationLoadedSongId = currentSongId
                            serviceRomanizationAttemptCount = 0
                            serviceRomanizationNextRetryAtMs = 0L
                        }
                        if (incomingHasTranslation) {
                            serviceTranslationLoadedSongId = currentSongId
                            serviceTranslationAttemptCount = 0
                            serviceTranslationNextRetryAtMs = 0L
                        }
                        if (incomingHasRomanization || incomingHasTranslation) {
                            serviceLyricsLoadJob?.cancel()
                        }
                        Log.d(
                            TAG,
                            "Applied lyrics data for mediaId=$currentSongId: " +
                                "source=$incomingSource, " +
                                "usableRomanization=$incomingHasRomanization, " +
                                "usableTranslation=$incomingHasTranslation"
                        )
                        chromahub.rhythm.app.infrastructure.widget.glance.GlanceWidgetUpdater.updateLyrics(this@MediaPlaybackService, getProcessedLyricTexts(), -1)
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }

                    "UPDATE_ACTIVE_LYRIC" -> {
                        val commandSongId = args.getString("song_id")
                        if (commandSongId != null && commandSongId != player.currentMediaItem?.mediaId) {
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        val lyricLine = args.getString("lyric_line")
                        val lyricIndex = args.getInt("lyric_index", -1)
                        currentLyricIndex = lyricIndex
                        
                        // Update widgets
                        chromahub.rhythm.app.infrastructure.widget.glance.GlanceWidgetUpdater.updateLyrics(this@MediaPlaybackService, getProcessedLyricTexts(), lyricIndex)
                        
                        // Update Bluetooth metadata lyrics
                        if (appSettings.bluetoothLyricsEnabled.value) {
                            val currentMediaItem = player.currentMediaItem
                            if (currentMediaItem != null) {
                                val song = convertMediaItemToSong(currentMediaItem)
                                if (song != null) {
                                    val bluetoothPosition = player.currentPosition +
                                        resolvedBluetoothLyricsOffsetMs()
                                    val bluetoothLine = if (currentLyricTimestamps.isNotEmpty()) {
                                        resolveServiceBtLyricLine(
                                            positionMs = bluetoothPosition,
                                            updateCurrentIndex = false
                                        )
                                    } else {
                                        lyricLine
                                    }
                                    statusBroadcaster.broadcastNowPlaying(
                                        song = song,
                                        isPlaying = player.isPlaying,
                                        position = player.currentPosition,
                                        queueSize = player.mediaItemCount,
                                        queuePosition = player.currentMediaItemIndex,
                                        bluetoothLyricsMode = true,
                                        currentLyricLine = bluetoothLine
                                    )
                                }
                            }
                        }
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }

                    SESSION_COMMAND_BT_VIRTUAL_SKIP_NEXT -> {
                        if (btVirtualAdvance()) {
                            SessionResult(SessionResult.RESULT_SUCCESS)
                        } else {
                            SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                        }
                    }

                    SESSION_COMMAND_BT_VIRTUAL_SKIP_PREVIOUS -> {
                        if (btVirtualPrevious()) {
                            SessionResult(SessionResult.RESULT_SUCCESS)
                        } else {
                            SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                        }
                    }

                    SHUFFLE_MODE_ON -> {
                        serviceController.shuffleModeEnabled = true
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }

                    SHUFFLE_MODE_OFF -> {
                        serviceController.shuffleModeEnabled = false
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }

                    REPEAT_MODE_OFF -> {
                        serviceController.repeatMode = Player.REPEAT_MODE_OFF
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }

                    REPEAT_MODE_ONE -> {
                        serviceController.repeatMode = Player.REPEAT_MODE_ONE
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }

                    REPEAT_MODE_ALL -> {
                        serviceController.repeatMode = Player.REPEAT_MODE_ALL
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }

                    FAVORITE_ON -> {
                        // Add current song to favorites
                        Log.d(TAG, "Favorite ON command received")
                        toggleCurrentSongFavorite()
                        // Immediate UI feedback for responsive feel
                        serviceScope.launch {
                            kotlinx.coroutines.delay(50) // Very short delay for immediate response
                            updateCustomLayoutSmart()
                        }
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }

                    FAVORITE_OFF -> {
                        // Remove current song from favorites  
                        Log.d(TAG, "Favorite OFF command received")
                        toggleCurrentSongFavorite()
                        // Immediate UI feedback for responsive feel
                        serviceScope.launch {
                            kotlinx.coroutines.delay(50) // Very short delay for immediate response
                            updateCustomLayoutSmart()
                        }
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    }

                    else -> {
                        SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                    }
                })
        }

        // NOTE: we avoid overriding onPlay directly because MediaLibrarySession callback
        // signatures vary across Media3 versions. Instead we enforce Rhythm Guard at the
        // player level via the Player.Listener implementation (see onPlayWhenReadyChanged).

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            Log.d(TAG, "onDisconnected: ${controller.packageName}")
            super.onDisconnected(session, controller)
        }
        
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            Log.d(TAG, "onAddMediaItems: ${mediaItems.size} items")
            
            val updatedMediaItems = mediaItems.map { mediaItem ->
                if (mediaItem.requestMetadata.searchQuery != null) {
                    // This is a search request
                    Log.d(TAG, "Search request: ${mediaItem.requestMetadata.searchQuery}")
                    mediaItem
                } else if (mediaItem.mediaId.isNotEmpty()) {
                    // Check if this is an external URI that we've cached
                    val cachedItem = externalUriCache[mediaItem.mediaId]
                    cachedItem ?: mediaItem
                } else {
                    mediaItem
                }
            }
            
            return Futures.immediateFuture(updatedMediaItems)
        }
        
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<androidx.media3.session.LibraryResult<MediaItem>> {
            Log.d(TAG, "onGetLibraryRoot from ${browser.packageName}")
            
            // Create a root media item
            val rootItem = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Rhythm Music Library")
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .build()
                )
                .build()
                
            return Futures.immediateFuture(androidx.media3.session.LibraryResult.ofItem(rootItem, params))
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled)
        Log.d(TAG, "Shuffle mode changed to: $shuffleModeEnabled")

        // Broadcast explicit shuffle state updates so UI can reconcile queue order immediately.
        val intent = Intent(ACTION_SHUFFLE_STATE_CHANGED).apply {
            setPackage(packageName)
            putExtra(EXTRA_SHUFFLE_ENABLED, shuffleModeEnabled)
        }
        sendBroadcast(intent)

        // Use debounced update to prevent rapid UI changes
        scheduleCustomLayoutUpdate(100)
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        super<Player.Listener>.onRepeatModeChanged(repeatMode)
        Log.d(TAG, "Repeat mode changed to: $repeatMode")
        // Use debounced update to prevent rapid UI changes
        scheduleCustomLayoutUpdate(100)
    }
    
    // Mute state tracking
    private var volumeBeforeMute: Float = 1.0f
    private var isMuted: Boolean = false
    
    /**
     * Mute the player while preserving the volume level
     * Manual implementation since mute()/unmute() require newer Media3 version
     */
    private fun mutePlayer() {
        if (!isMuted) {
            volumeBeforeMute = player.volume
            player.volume = 0f
            isMuted = true
            Log.d(TAG, "Player muted (volume $volumeBeforeMute preserved)")
        }
    }
    
    /**
     * Unmute the player and restore the previous volume
     */
    private fun unmutePlayer() {
        if (isMuted) {
            player.volume = volumeBeforeMute
            isMuted = false
            Log.d(TAG, "Player unmuted (volume $volumeBeforeMute restored)")
        }
    }
    
    /**
     * Toggle mute state
     */
    private fun toggleMute() {
        if (isMuted) {
            unmutePlayer()
        } else {
            mutePlayer()
        }
    }

    // AVRCP legacy workaround state. The compatibility view may expose one item to a
    // problematic car, but the user's actual playlist must survive intact underneath it.
    private data class BtVirtualQueueItem(val mediaItem: MediaItem, val originalIndex: Int)
    private val btVirtualUpcoming = java.util.concurrent.CopyOnWriteArrayList<BtVirtualQueueItem>()
    private val btVirtualHistory = java.util.concurrent.CopyOnWriteArrayList<BtVirtualQueueItem>()
    private var btVirtualOriginalQueue: List<MediaItem>? = null
    private var btVirtualOriginalRepeatMode = Player.REPEAT_MODE_OFF
    private var btVirtualOriginalShuffleMode = false
    private var btVirtualCurrentOriginalIndex = androidx.media3.common.C.INDEX_UNSET
    private var lastBtVirtualQueueMoveMs = 0L
    private val BT_VIRTUAL_QUEUE_MOVE_DEBOUNCE_MS = 400L
    @Volatile private var isMutatingBtVirtualQueue = false
    private var btCollapseJob: kotlinx.coroutines.Job? = null

    private fun btVirtualQueueActive(): Boolean =
        ::appSettings.isInitialized &&
            appSettings.bluetoothLyricsEnabled.value &&
            appSettings.bluetoothLyricsLegacyCarModeEnabled.value

    private fun notifyBtVirtualCommands() {
        (player as? RhythmForwardingPlayer)?.notifyBtCommandsChanged()
    }

    private fun scheduleCollapseForBtLyrics() {
        if (!btVirtualQueueActive()) return
        btCollapseJob?.cancel()
        btCollapseJob = serviceScope.launch {
            kotlinx.coroutines.delay(450)
            collapseQueueForBtLyrics()
        }
    }

    private fun collapseQueueForBtLyrics() {
        if (!::player.isInitialized || !btVirtualQueueActive() || isMutatingBtVirtualQueue) return
        val count = player.mediaItemCount
        val idx = player.currentMediaItemIndex
        if (count <= 1 || idx == androidx.media3.common.C.INDEX_UNSET) return
        isMutatingBtVirtualQueue = true
        try {
            btVirtualOriginalQueue = List(count) { player.getMediaItemAt(it) }
            btVirtualOriginalRepeatMode = player.repeatMode
            btVirtualOriginalShuffleMode = player.shuffleModeEnabled
            btVirtualCurrentOriginalIndex = idx
            lastBtVirtualQueueMoveMs = 0L

            val timeline = player.currentTimeline
            fun collectNavigationOrder(next: Boolean): List<BtVirtualQueueItem> {
                val result = mutableListOf<BtVirtualQueueItem>()
                val visited = mutableSetOf(idx)
                var cursor = idx
                while (true) {
                    val adjacent = if (next) {
                        timeline.getNextWindowIndex(cursor, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
                    } else {
                        timeline.getPreviousWindowIndex(cursor, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
                    }
                    if (adjacent == androidx.media3.common.C.INDEX_UNSET || !visited.add(adjacent)) break
                    result += BtVirtualQueueItem(player.getMediaItemAt(adjacent), adjacent)
                    cursor = adjacent
                }
                return result
            }

            btVirtualHistory.clear()
            btVirtualHistory.addAll(collectNavigationOrder(next = false).asReversed())
            btVirtualUpcoming.clear()
            btVirtualUpcoming.addAll(collectNavigationOrder(next = true))

            if (idx + 1 < count) player.removeMediaItems(idx + 1, count)
            if (idx > 0) player.removeMediaItems(0, idx)
            // A one-item player must not repeat by itself; the service emulates the
            // saved repeat mode below and restores it verbatim when legacy mode ends.
            player.repeatMode = Player.REPEAT_MODE_OFF
            Log.d(TAG, "Legacy car mode: exposed one item, preserving $count-item queue; ${btVirtualUpcoming.size} upcoming / ${btVirtualHistory.size} history")
        } catch (e: Exception) {
            Log.w(TAG, "Legacy car mode collapse failed", e)
        } finally {
            isMutatingBtVirtualQueue = false
        }
        notifyBtVirtualCommands()
    }

    private fun btVirtualAdvance(): Boolean {
        if (!::player.isInitialized || !btVirtualQueueActive()) return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastBtVirtualQueueMoveMs < BT_VIRTUAL_QUEUE_MOVE_DEBOUNCE_MS) {
            Log.d(TAG, "Legacy car mode: ignored duplicate next command")
            return true
        }
        if (btVirtualUpcoming.isEmpty() && btVirtualOriginalRepeatMode == Player.REPEAT_MODE_ALL) {
            btVirtualUpcoming.addAll(btVirtualHistory)
            btVirtualHistory.clear()
        }
        if (btVirtualUpcoming.isEmpty()) return false
        lastBtVirtualQueueMoveMs = now
        val next = btVirtualUpcoming.removeAt(0)
        player.currentMediaItem?.let {
            btVirtualHistory.add(BtVirtualQueueItem(it, btVirtualCurrentOriginalIndex))
        }
        isMutatingBtVirtualQueue = true
        try {
            val playWhenReady = player.playWhenReady
            player.setMediaItem(next.mediaItem)
            btVirtualCurrentOriginalIndex = next.originalIndex
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.prepare()
            player.playWhenReady = playWhenReady
            Log.d(TAG, "Legacy car mode: advanced virtual queue, ${btVirtualUpcoming.size} remaining")
        } finally {
            isMutatingBtVirtualQueue = false
        }
        notifyBtVirtualCommands()
        return true
    }

    private fun handleBtVirtualPlaybackEnded() {
        if (!btVirtualQueueActive()) return
        if (btVirtualOriginalRepeatMode == Player.REPEAT_MODE_ONE) {
            isMutatingBtVirtualQueue = true
            try {
                player.seekTo(0)
                player.playWhenReady = true
            } finally {
                isMutatingBtVirtualQueue = false
            }
            return
        }
        btVirtualAdvance()
    }

    private fun btVirtualPrevious(): Boolean {
        if (!::player.isInitialized || !btVirtualQueueActive()) return false
        val now = SystemClock.elapsedRealtime()
        if (now - lastBtVirtualQueueMoveMs < BT_VIRTUAL_QUEUE_MOVE_DEBOUNCE_MS) {
            Log.d(TAG, "Legacy car mode: ignored duplicate previous command")
            return true
        }
        if (btVirtualHistory.isEmpty() && btVirtualOriginalRepeatMode == Player.REPEAT_MODE_ALL) {
            btVirtualHistory.addAll(btVirtualUpcoming)
            btVirtualUpcoming.clear()
            player.currentMediaItem?.let {
                btVirtualUpcoming.add(BtVirtualQueueItem(it, btVirtualCurrentOriginalIndex))
            }
        }
        if (btVirtualHistory.isEmpty()) return false
        lastBtVirtualQueueMoveMs = now
        val prev = btVirtualHistory.removeAt(btVirtualHistory.size - 1)
        player.currentMediaItem?.let {
            btVirtualUpcoming.add(0, BtVirtualQueueItem(it, btVirtualCurrentOriginalIndex))
        }
        isMutatingBtVirtualQueue = true
        try {
            val playWhenReady = player.playWhenReady
            player.setMediaItem(prev.mediaItem)
            btVirtualCurrentOriginalIndex = prev.originalIndex
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.prepare()
            player.playWhenReady = playWhenReady
            Log.d(TAG, "Legacy car mode: stepped back virtual queue, ${btVirtualHistory.size} history left")
        } finally {
            isMutatingBtVirtualQueue = false
        }
        notifyBtVirtualCommands()
        return true
    }

    private fun restoreQueueFromBtVirtual() {
        if (!::player.isInitialized) return
        val originalQueue = btVirtualOriginalQueue ?: return
        isMutatingBtVirtualQueue = true
        try {
            val pos = player.currentPosition
            val originalCurrentIndex = btVirtualCurrentOriginalIndex
            btVirtualHistory.clear()
            btVirtualUpcoming.clear()
            btVirtualOriginalQueue = null
            btVirtualCurrentOriginalIndex = androidx.media3.common.C.INDEX_UNSET
            lastBtVirtualQueueMoveMs = 0L
            if (originalQueue.isNotEmpty()) {
                val currentIdx = originalCurrentIndex
                    .takeIf { it != androidx.media3.common.C.INDEX_UNSET }
                    ?.coerceIn(0, originalQueue.lastIndex)
                    ?: 0
                player.setMediaItems(originalQueue, currentIdx, pos)
                player.shuffleModeEnabled = btVirtualOriginalShuffleMode
                player.repeatMode = btVirtualOriginalRepeatMode
                player.prepare()
            }
            Log.d(TAG, "Legacy car mode: restored original ${originalQueue.size}-item queue on mode off")
        } catch (e: Exception) {
            Log.w(TAG, "Legacy car mode restore failed", e)
        } finally {
            isMutatingBtVirtualQueue = false
        }
        notifyBtVirtualCommands()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        val transitionMediaId = mediaItem?.mediaId
        if (isBluetoothMetadataTransition(
                mediaItem = mediaItem,
                reason = reason,
                lastHandledMediaId = lastHandledControllerTransitionMediaId
            )
        ) {
            Log.d(TAG, "Ignoring metadata-only controller transition for mediaId=$transitionMediaId")
            return
        }

        clearLyricsState()
        (player as? RhythmForwardingPlayer)?.clearLyricMetadata()
        lastServiceBtLyricLine = null
        lastServiceBtLyricSongId = null
        lastServiceBtAppliedSongId = null
        Log.d(TAG, "Media item transitioned: ${mediaItem?.mediaMetadata?.title}, reason=$reason")
        
        // Update custom layout when song changes to reflect correct favorite state
        scheduleCustomLayoutUpdate(50) // Shorter delay for song transitions
        
        // Update widget with new song info
        updateWidgetFromMediaItem(mediaItem)

        lastHandledControllerTransitionMediaId = transitionMediaId
    }
    
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        Log.d(TAG, "Is playing changed: $isPlaying")
        // Update widget when play/pause state changes
        updateWidgetFromMediaItem(player.currentMediaItem)
        if (isPlaying) startBluetoothLyricsLoop() else stopBluetoothLyricsLoop()
    }

    /**
     * Android 16's legacy MediaSession -> AVRCP bridge is more reliable when it observes a real
     * same-item metadata replacement. Older releases keep the listener-only path that is already
     * proven on HyperOS/Android 14. The replacement is metadata-only and preserves playback.
     */
    private fun useForwardingBluetoothMetadata(): Boolean = Build.VERSION.SDK_INT < 36
    private var bluetoothLyricsLoopJob: kotlinx.coroutines.Job? = null
    private var lastServiceBtLyricLine: String? = null
    private var lastServiceBtLyricSongId: String? = null
    private var lastServiceBtAppliedSongId: String? = null
    private var serviceBtCanonicalSong: Song? = null

    /**
     * Replacing only the current item's metadata is reported by ExoPlayer as a SEEK transition
     * when its window UID changes, even though playback never seeks. Some player implementations
     * instead report PLAYLIST_CHANGED. Ignore either form only while it still targets the song
     * whose Bluetooth lyric metadata the service owns.
     */
    private fun isBluetoothMetadataTransition(
        mediaItem: MediaItem?,
        reason: Int,
        lastHandledMediaId: String?
    ): Boolean {
        val mediaId = mediaItem?.mediaId ?: return false
        val isMetadataReason =
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
        if (!isMetadataReason) return false

        val targetsServiceOwnedSong =
            mediaId == lastServiceBtAppliedSongId ||
                mediaId == lastServiceBtLyricSongId ||
                mediaId == serviceBtCanonicalSong?.id
        val repeatedPlaylistCallback =
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                mediaId == lastHandledMediaId
        return targetsServiceOwnedSong || repeatedPlaylistCallback
    }

    private val serviceMusicRepository by lazy {
        chromahub.rhythm.app.features.local.data.repository.MusicRepository(applicationContext)
    }
    @Volatile private var serviceLyricsLoadedSongId: String? = null
    @Volatile
    private var serviceRomanizationLoadedSongId: String? = null
    private var serviceRomanizationAttemptCount: Int = 0
    private var serviceRomanizationNextRetryAtMs: Long = 0L
    @Volatile
    private var serviceTranslationLoadedSongId: String? = null
    private var serviceTranslationAttemptCount: Int = 0
    private var serviceTranslationNextRetryAtMs: Long = 0L
    private var serviceLyricsLoadJob: kotlinx.coroutines.Job? = null
    private val serviceEnrichmentRetryDelaysMs =
        longArrayOf(3_000L, 10_000L, 30_000L, 60_000L)

    private fun currentLyricsHaveUsableRomanization(): Boolean =
        LyricsRomanizationPolicy.hasUsableCoverage(
            original = currentLyricTexts,
            supplemental = currentLyricRomanizations
        )

    private fun currentLyricsHaveUsableTranslation(): Boolean =
        LyricsTranslationPolicy.hasUsableCoverage(
            original = currentLyricTexts,
            supplemental = currentLyricTranslations
        )

    private fun startBluetoothLyricsLoop() {
        if (bluetoothLyricsLoopJob?.isActive == true) return
        Log.i(
            TAG,
            "Bluetooth lyrics loop started: sdk=${Build.VERSION.SDK_INT}, " +
                "metadataPath=${if (useForwardingBluetoothMetadata()) "forwarding" else "replace"}"
        )
        bluetoothLyricsLoopJob = serviceScope.launch {
            while (isActive) {
                try {
                    tickBluetoothLyrics()
                } catch (e: Exception) {
                    Log.w(TAG, "Bluetooth lyrics tick failed", e)
                }
                delay(350)
            }
        }
    }

    private fun stopBluetoothLyricsLoop() {
        bluetoothLyricsLoopJob?.cancel()
        bluetoothLyricsLoopJob = null
        Log.i(TAG, "Bluetooth lyrics loop stopped")
    }

    private fun ensureServiceLyricsLoaded(song: Song, initialDelayMs: Long = 1200L) {
        val hasLyrics = currentLyricTexts.isNotEmpty() || currentPlainLyricsLines.isNotEmpty()
        val needsBaseLyrics = serviceLyricsLoadedSongId != song.id && !hasLyrics
        val now = SystemClock.elapsedRealtime()
        val needsRomanization =
            appSettings.bluetoothLyricsTextMode.value == BluetoothLyricsTextMode.ROMANIZATION &&
                    serviceRomanizationLoadedSongId != song.id &&
                    !currentLyricsHaveUsableRomanization() &&
                    now >= serviceRomanizationNextRetryAtMs
        val needsTranslation =
            appSettings.bluetoothLyricsTextMode.value == BluetoothLyricsTextMode.TRANSLATION &&
                serviceTranslationLoadedSongId != song.id &&
                !currentLyricsHaveUsableTranslation() &&
                now >= serviceTranslationNextRetryAtMs
        if (!needsBaseLyrics && !needsRomanization && !needsTranslation) return
        if (serviceLyricsLoadJob?.isActive == true) return
        serviceLyricsLoadJob = serviceScope.launch {
            if (initialDelayMs > 0L) delay(initialDelayMs)
            if (player.currentMediaItem?.mediaId != song.id) return@launch
            try {
                val stillHasLyrics = currentLyricTexts.isNotEmpty() || currentPlainLyricsLines.isNotEmpty()
                if (serviceLyricsLoadedSongId != song.id && !stillHasLyrics) {
                    val baseLyrics = serviceMusicRepository.fetchLyrics(
                        artist = song.artist,
                        title = song.title,
                        songId = song.id,
                        songUri = song.uri,
                        sourcePreference = appSettings.lyricsSourcePreference.value,
                        requireRomanization = false,
                        requireTranslation = false
                    )
                    if (player.currentMediaItem?.mediaId != song.id) return@launch
                    applyServiceLyrics(
                        song = song,
                        data = baseLyrics,
                        requireRomanization = false,
                        requireTranslation = false
                    )
                    serviceLyricsLoadedSongId = song.id
                }

                if (
                    appSettings.bluetoothLyricsTextMode.value == BluetoothLyricsTextMode.ROMANIZATION &&
                    serviceRomanizationLoadedSongId != song.id &&
                    !currentLyricsHaveUsableRomanization() &&
                    SystemClock.elapsedRealtime() >= serviceRomanizationNextRetryAtMs
                ) {
                    val attempt = ++serviceRomanizationAttemptCount
                    val retryDelay = serviceEnrichmentRetryDelaysMs[
                        (attempt - 1).coerceAtMost(serviceEnrichmentRetryDelaysMs.lastIndex)
                    ]
                    Log.d(TAG, "Romaji lookup attempt $attempt for '${song.title}'")
                    val romanizedLyrics = serviceMusicRepository.fetchLyrics(
                        artist = song.artist,
                        title = song.title,
                        songId = song.id,
                        songUri = song.uri,
                        sourcePreference = appSettings.lyricsSourcePreference.value,
                        requireRomanization = true
                    )
                    if (player.currentMediaItem?.mediaId != song.id) return@launch
                    if (
                        applyServiceLyrics(
                            song = song,
                            data = romanizedLyrics,
                            requireRomanization = true,
                            requireTranslation = false
                        )
                    ) {
                        serviceRomanizationLoadedSongId = song.id
                        serviceRomanizationAttemptCount = 0
                        serviceRomanizationNextRetryAtMs = 0L
                    } else {
                        serviceRomanizationNextRetryAtMs =
                            SystemClock.elapsedRealtime() + retryDelay
                        Log.d(
                            TAG,
                            "Romaji lookup attempt $attempt found no usable track; retry in ${retryDelay}ms"
                        )
                    }
                }

                if (
                    appSettings.bluetoothLyricsTextMode.value ==
                        BluetoothLyricsTextMode.TRANSLATION &&
                    serviceTranslationLoadedSongId != song.id &&
                    !currentLyricsHaveUsableTranslation() &&
                    SystemClock.elapsedRealtime() >= serviceTranslationNextRetryAtMs
                ) {
                    val attempt = ++serviceTranslationAttemptCount
                    val retryDelay = serviceEnrichmentRetryDelaysMs[
                        (attempt - 1).coerceAtMost(serviceEnrichmentRetryDelaysMs.lastIndex)
                    ]
                    Log.d(TAG, "Translation lookup attempt $attempt for '${song.title}'")
                    val translatedLyrics = serviceMusicRepository.fetchLyrics(
                        artist = song.artist,
                        title = song.title,
                        songId = song.id,
                        songUri = song.uri,
                        sourcePreference = appSettings.lyricsSourcePreference.value,
                        requireRomanization = true,
                        requireTranslation = true
                    )
                    if (player.currentMediaItem?.mediaId != song.id) return@launch
                    val hasTranslation = translatedLyrics?.hasUsableTimedTranslation() == true
                    val hasRomanization = translatedLyrics?.hasUsableTimedRomanization() == true
                    val applied = when {
                        hasTranslation -> applyServiceLyrics(
                            song = song,
                            data = translatedLyrics,
                            requireRomanization = false,
                            requireTranslation = true
                        )
                        hasRomanization -> applyServiceLyrics(
                            song = song,
                            data = translatedLyrics,
                            requireRomanization = true,
                            requireTranslation = false
                        )
                        else -> false
                    }
                    if (hasTranslation && applied) {
                        serviceTranslationLoadedSongId = song.id
                        serviceTranslationAttemptCount = 0
                        serviceTranslationNextRetryAtMs = 0L
                    } else {
                        serviceTranslationNextRetryAtMs =
                            SystemClock.elapsedRealtime() + retryDelay
                        Log.d(
                            TAG,
                            "Translation lookup attempt $attempt found no usable track; " +
                                "retry in ${retryDelay}ms"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (
                    appSettings.bluetoothLyricsTextMode.value == BluetoothLyricsTextMode.ROMANIZATION &&
                    !currentLyricsHaveUsableRomanization()
                ) {
                    val retryDelay = serviceEnrichmentRetryDelaysMs[
                        (serviceRomanizationAttemptCount - 1)
                            .coerceAtLeast(0)
                            .coerceAtMost(serviceEnrichmentRetryDelaysMs.lastIndex)
                    ]
                    serviceRomanizationNextRetryAtMs = SystemClock.elapsedRealtime() + retryDelay
                }
                if (
                    appSettings.bluetoothLyricsTextMode.value ==
                        BluetoothLyricsTextMode.TRANSLATION &&
                    !currentLyricsHaveUsableTranslation()
                ) {
                    val retryDelay = serviceEnrichmentRetryDelaysMs[
                        (serviceTranslationAttemptCount - 1)
                            .coerceAtLeast(0)
                            .coerceAtMost(serviceEnrichmentRetryDelaysMs.lastIndex)
                    ]
                    serviceTranslationNextRetryAtMs = SystemClock.elapsedRealtime() + retryDelay
                }
                Log.w(TAG, "Service lyric load failed for '${song.title}'", e)
            }
        }
    }

    private fun applyServiceLyrics(
        song: Song,
        data: chromahub.rhythm.app.shared.data.model.LyricsData?,
        requireRomanization: Boolean,
        requireTranslation: Boolean
    ): Boolean {
        val synced = data?.syncedLyrics ?: return false
        val parsed = chromahub.rhythm.app.util.LyricsParser.parseLyrics(synced)
        if (parsed.isEmpty()) return false
        if (requireRomanization && !data.hasUsableTimedRomanization()) return false
        if (requireTranslation && !data.hasUsableTimedTranslation()) return false

        currentLyricTexts = parsed.map { it.text }
        currentLyricTranslations = parsed.map { it.translation ?: "" }
        currentLyricRomanizations = parsed.map { it.romanization ?: "" }
        currentLyricTimestamps = parsed.map { it.timestamp }.toLongArray()
        currentPlainLyricsLines = data.plainLyrics
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        currentLyricsSource = data.source
        currentLyricIndex = -1
        if (data.hasUsableTimedRomanization()) {
            serviceRomanizationLoadedSongId = song.id
            serviceRomanizationAttemptCount = 0
            serviceRomanizationNextRetryAtMs = 0L
        }
        if (data.hasUsableTimedTranslation()) {
            serviceTranslationLoadedSongId = song.id
            serviceTranslationAttemptCount = 0
            serviceTranslationNextRetryAtMs = 0L
        }
        chromahub.rhythm.app.infrastructure.widget.glance.GlanceWidgetUpdater
            .updateLyrics(this@MediaPlaybackService, getProcessedLyricTexts(), -1)
        Log.d(
            TAG,
            "Service-loaded synced lyrics for '${song.title}' " +
                "(${parsed.size} lines, source=${data.source}, " +
                "romanization=$requireRomanization, translation=$requireTranslation)"
        )
        return true
    }

    /** Returns the active Bluetooth device's offset, or zero for non-Bluetooth output. */
    private fun resolvedBluetoothLyricsOffsetMs(): Long {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0L
        val deviceName = chromahub.rhythm.app.util.AudioCapabilitiesMonitor
            .activeBluetoothOutputName(audioManager) ?: return 0L
        return appSettings.effectiveBluetoothLyricsOffsetMs(deviceName).toLong()
    }

    private fun tickBluetoothLyrics() {
        if (!::player.isInitialized || !::appSettings.isInitialized) return
        val item = player.currentMediaItem ?: return
        val observedSong = convertMediaItemToSong(item) ?: return
        val song = serviceBtCanonicalSong
            ?.takeIf { it.id == observedSong.id }
            ?: observedSong.also { serviceBtCanonicalSong = it }
        val enabled = appSettings.bluetoothLyricsEnabled.value
        if (!enabled) {
            if (lastServiceBtAppliedSongId != null) restoreServiceBtMetadata(song)
            lastServiceBtLyricLine = null
            lastServiceBtLyricSongId = null
            return
        }
        ensureServiceLyricsLoaded(song)
        val offsetMs = resolvedBluetoothLyricsOffsetMs()
        val playbackPositionMs = player.currentPosition
        val line = resolveServiceBtLyricLine(playbackPositionMs + offsetMs)
        if (song.id == lastServiceBtLyricSongId && line == lastServiceBtLyricLine) return
        lastServiceBtLyricSongId = song.id
        lastServiceBtLyricLine = line
        Log.i(
            TAG,
            "Bluetooth lyric publish: mediaId=${song.id}, positionMs=$playbackPositionMs, " +
                "offsetMs=$offsetMs, source=$currentLyricsSource, " +
                "mode=${appSettings.bluetoothLyricsTextMode.value}, line=${line.orEmpty()}"
        )
        if (appSettings.broadcastStatusEnabled.value) {
            statusBroadcaster.broadcastMetadataChanged(
                song = song,
                position = player.currentPosition,
                queueSize = player.mediaItemCount,
                queuePosition = player.currentMediaItemIndex,
                bluetoothLyricsMode = true,
                currentLyricLine = line
            )
        }
        applyServiceBtMetadata(song, line)
    }

    private fun resolveServiceBtLyricLine(
        positionMs: Long,
        updateCurrentIndex: Boolean = true
    ): String? {
        val ts = currentLyricTimestamps
        if (ts.isEmpty() || currentLyricTexts.isEmpty()) return null
        val pos = positionMs.coerceAtLeast(0L)
        var idx = -1
        for (i in ts.indices) {
            if (ts[i] <= pos) idx = i else break
        }
        if (idx < 0) return null
        val effectiveTexts = BluetoothLyricsFormatter.selectTexts(
            mode = appSettings.bluetoothLyricsTextMode.value,
            original = currentLyricTexts,
            translations = currentLyricTranslations,
            romanizations = currentLyricRomanizations
        )
        val resolved = BluetoothLyricsFormatter.resolveLine(
            positionMs = pos,
            timestamps = ts,
            texts = effectiveTexts,
            tuning = BluetoothLyricsFormatter.Tuning(
                maxChunkChars = appSettings.bluetoothLyricsMaxChunkChars.value,
                scrollCharsPerSecond = appSettings.bluetoothLyricsScrollCharsPerSecond.value,
                minChunkHoldMs = appSettings.bluetoothLyricsMinChunkHoldMs.value
            )
        ) ?: return null
        if (updateCurrentIndex) currentLyricIndex = resolved.sourceLineIndex
        return resolved.text
    }

    private fun applyServiceBtMetadata(song: Song, line: String?) {
        try {
            val idx = player.currentMediaItemIndex
            if (idx < 0 || idx >= player.mediaItemCount) return
            val current = player.getMediaItemAt(idx)
            if (current.mediaId != song.id) return
            val merged = listOf(song.title, song.artist)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" - ")
                .ifBlank { song.title }
            // Avoid leaving AVRCP displays stuck on "No lyrics" during instrumental gaps.
            val title = line?.takeIf { it.isNotBlank() } ?: song.title.ifBlank { merged }
            val forwarding = player as? RhythmForwardingPlayer
            // Player transition callbacks can be asynchronous. Mark this before dispatch so the
            // first lyric update after a cold start cannot clear its own freshly loaded lyrics.
            lastServiceBtAppliedSongId = song.id
            if (useForwardingBluetoothMetadata() && forwarding != null) {
                forwarding.injectLyricMetadata(title, merged)
            } else {
                val updated = current.mediaMetadata.buildUpon()
                    .setTitle(title)
                    .setArtist(merged)
                    .setExtras(buildServiceBtCanonicalMetadataExtras(song, current.mediaMetadata.extras))
                    .build()
                val updatedItem = current.buildUpon().setMediaMetadata(updated).build()
                if (forwarding != null) {
                    forwarding.replaceLyricMetadata(idx, updatedItem)
                } else {
                    player.replaceMediaItem(idx, updatedItem)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply Bluetooth lyric metadata", e)
        }
    }

    private fun restoreServiceBtMetadata(song: Song) {
        try {
            val forwarding = player as? RhythmForwardingPlayer
            if (useForwardingBluetoothMetadata() && forwarding != null) {
                forwarding.clearLyricMetadata()
                lastServiceBtAppliedSongId = null
                return
            }
            val idx = player.currentMediaItemIndex
            if (idx < 0 || idx >= player.mediaItemCount) return
            val current = player.getMediaItemAt(idx)
            val md = current.mediaMetadata
            if (md.title?.toString() == song.title && md.artist?.toString() == song.artist) {
                lastServiceBtAppliedSongId = null
                return
            }
            val restored = md.buildUpon()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setExtras(buildServiceBtCanonicalMetadataExtras(song, md.extras))
                .build()
            val restoredItem = current.buildUpon().setMediaMetadata(restored).build()
            if (forwarding != null) {
                forwarding.replaceLyricMetadata(idx, restoredItem)
            } else {
                player.replaceMediaItem(idx, restoredItem)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore standard metadata", e)
        } finally {
            lastServiceBtAppliedSongId = null
        }
    }

    private fun buildServiceBtCanonicalMetadataExtras(
        song: Song,
        existingExtras: Bundle?
    ): Bundle = Bundle(existingExtras ?: Bundle()).apply {
        putString(METADATA_EXTRA_ORIGINAL_TITLE, song.title)
        putString(METADATA_EXTRA_ORIGINAL_ARTIST, song.artist)
        putString(METADATA_EXTRA_ORIGINAL_ALBUM, song.album)
    }
    
    /**
     * Helper function to convert MediaItem to Song for scrobbling and widgets
     */
    private fun convertMediaItemToSong(mediaItem: MediaItem): Song? {
        return try {
            val extras = mediaItem.mediaMetadata.extras
            val canonicalTitle = extras?.getString(METADATA_EXTRA_ORIGINAL_TITLE)
            val canonicalArtist = extras?.getString(METADATA_EXTRA_ORIGINAL_ARTIST)
            val canonicalAlbum = extras?.getString(METADATA_EXTRA_ORIGINAL_ALBUM)

            Song(
                id = mediaItem.mediaId,
                title = canonicalTitle
                    ?.takeIf { it.isNotBlank() }
                    ?: mediaItem.mediaMetadata.title?.toString()
                    ?: "Unknown",
                artist = canonicalArtist
                    ?.takeIf { it.isNotBlank() }
                    ?: mediaItem.mediaMetadata.artist?.toString()
                    ?: "Unknown",
                album = canonicalAlbum
                    ?.takeIf { it.isNotBlank() }
                    ?: mediaItem.mediaMetadata.albumTitle?.toString()
                    ?: "",
                uri = mediaItem.requestMetadata.mediaUri ?: Uri.EMPTY,
                artworkUri = mediaItem.mediaMetadata.artworkUri,
                duration = player.duration.takeIf { it > 0 } ?: 0L,
                trackNumber = 0,
                year = 0,
                genre = "",
                albumId = "",
                albumArtist = mediaItem.mediaMetadata.albumArtist?.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting MediaItem to Song", e)
            null
        }
    }
    
    private fun updateWidgetFromMediaItem(mediaItem: MediaItem?) {
        if (!appSettings.widgetAutoUpdate.value) {
            return
        }
        if (mediaItem != null) {
            val song = convertMediaItemToSong(mediaItem)
            if (song != null) {
                val isFavorite = isCurrentSongFavorite()
                val hasPrevious = player.hasPreviousMediaItem()
                val hasNext = player.hasNextMediaItem()
                val snapshotKey = buildString {
                    append(song.id)
                    append('|')
                    append(player.isPlaying)
                    append('|')
                    append(hasPrevious)
                    append('|')
                    append(hasNext)
                    append('|')
                    append(isFavorite)
                }

                if (snapshotKey == lastWidgetSnapshotKey) {
                    return
                }
                lastWidgetSnapshotKey = snapshotKey

                WidgetUpdater.updateWidget(this, song, player.isPlaying, hasPrevious, hasNext, isFavorite)
            } else {
                if (lastWidgetSnapshotKey == "empty|false") {
                    return
                }
                lastWidgetSnapshotKey = "empty|false"
                WidgetUpdater.updateWidget(this, null, false)
            }
        } else {
            if (lastWidgetSnapshotKey == "empty|false") {
                return
            }
            lastWidgetSnapshotKey = "empty|false"
            WidgetUpdater.updateWidget(this, null, false)
        }
    }

    private fun playRandomFromCurrentQueue() {
        val queueSize = player.mediaItemCount
        if (queueSize <= 0) {
            Log.w(TAG, "Cannot start explore playback: queue is empty")
            return
        }

        val randomIndex = if (queueSize == 1) 0 else kotlin.random.Random.nextInt(queueSize)
        player.seekTo(randomIndex, 0L)
        player.playWhenReady = true
        player.play()

        serviceScope.launch {
            delay(120)
            updateWidgetFromMediaItem(player.currentMediaItem)
        }
    }

    // Sleep Timer functionality
    private fun launchTimerCoroutine(startTime: Long, durationMs: Long, fadeOut: Boolean, pauseOnly: Boolean): Job {
        return serviceScope.launch {
            val localStartTime = startTime
            val localFadeOut = fadeOut
            val localPauseOnly = pauseOnly

            try {
                if (localFadeOut && durationMs > 10000) {
                    var remainingTime = durationMs
                    while (remainingTime > 10000) {
                        delay(1000)
                        remainingTime = durationMs - (System.currentTimeMillis() - localStartTime)
                        if (remainingTime <= 0) break
                        broadcastSleepTimerStatus()
                    }

                    val originalVolume = player.volume
                    val fadeSteps = 100
                    val fadeInterval = 10000L / fadeSteps

                    for (i in fadeSteps downTo 0) {
                        val volume = originalVolume * (i.toFloat() / fadeSteps)
                        player.volume = volume
                        delay(fadeInterval)
                        if (i % 10 == 0) {
                            broadcastSleepTimerStatus()
                        }
                    }
                } else {
                    var remainingTime = durationMs
                    while (remainingTime > 0) {
                        delay(1000)
                        remainingTime = durationMs - (System.currentTimeMillis() - localStartTime)
                        if (remainingTime <= 0) break
                        broadcastSleepTimerStatus()
                    }
                }

                if (localPauseOnly) {
                    player.pause()
                    Log.d(TAG, "Sleep timer paused playback")
                } else {
                    player.stop()
                    Log.d(TAG, "Sleep timer stopped playback")
                }

                if (localFadeOut) {
                    player.volume = 1.0f
                }

                resetSleepTimer()

            } catch (e: CancellationException) {
                Log.d(TAG, "Sleep timer was cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in sleep timer", e)
                resetSleepTimer()
            } finally {
                broadcastSleepTimerStatus()
            }
        }
    }

    fun startSleepTimer(durationMs: Long, fadeOut: Boolean = true, pauseOnly: Boolean = false) {
        Log.d(TAG, "Starting sleep timer: ${durationMs}ms, fadeOut: $fadeOut, pauseOnly: $pauseOnly")
        stopSleepTimer()

        if (durationMs <= 0) {
            Log.e(TAG, "Invalid sleep timer duration: $durationMs")
            return
        }

        sleepTimerDurationMs = durationMs
        sleepTimerStartTime = System.currentTimeMillis()
        fadeOutEnabled = fadeOut
        pauseOnlyEnabled = pauseOnly

        broadcastSleepTimerStatus()

        sleepTimerJob = launchTimerCoroutine(sleepTimerStartTime, durationMs, fadeOut, pauseOnly)
        Log.d(TAG, "Sleep timer job started for ${durationMs}ms")
    }

    fun extendSleepTimer() {
        if (sleepTimerDurationMs <= 0L || sleepTimerStartTime <= 0L) return

        val now = System.currentTimeMillis()
        if (now - lastVolumeExtendTimeMs < 3000) return
        lastVolumeExtendTimeMs = now

        Log.d(TAG, "Extending sleep timer due to volume change")

        val duration = sleepTimerDurationMs
        val fadeOut = fadeOutEnabled
        val pauseOnly = pauseOnlyEnabled

        sleepTimerJob?.cancel()
        sleepTimerJob = null

        sleepTimerStartTime = now
        sleepTimerJob = launchTimerCoroutine(now, duration, fadeOut, pauseOnly)
        broadcastSleepTimerStatus()
        Log.d(TAG, "Sleep timer extended for ${duration}ms")
    }

    fun stopSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        if (fadeOutEnabled) {
            player.volume = 1.0f
        }

        resetSleepTimer()
        broadcastSleepTimerStatus()
        Log.d(TAG, "Sleep timer stopped")
    }

    fun getRemainingTimeMs(): Long {
        if (sleepTimerDurationMs <= 0L || sleepTimerStartTime <= 0L) {
            return 0L
        }

        val elapsed = System.currentTimeMillis() - sleepTimerStartTime
        return maxOf(0L, sleepTimerDurationMs - elapsed)
    }

    private fun resetSleepTimer() {
        sleepTimerDurationMs = 0L
        sleepTimerStartTime = 0L
        fadeOutEnabled = true
        pauseOnlyEnabled = false
    }
    
    private fun broadcastSleepTimerStatus() {
        val timerActive = isSleepTimerActive()
        val remainingTimeMs = getRemainingTimeMs()

        val intent = Intent(BROADCAST_SLEEP_TIMER_STATUS).apply {
            putExtra(EXTRA_TIMER_ACTIVE, timerActive)
            putExtra(EXTRA_REMAINING_TIME, remainingTimeMs)
            putExtra(EXTRA_TOTAL_TIME, sleepTimerDurationMs)
            setPackage(packageName)
        }
        sendBroadcast(intent)

        if (timerActive && sleepTimerDurationMs > 0L) {
            updateSleepTimerProgressNotification(
                remainingMs = remainingTimeMs,
                totalMs = sleepTimerDurationMs,
                pauseOnly = pauseOnlyEnabled
            )
        } else {
            cancelSleepTimerProgressNotification()
        }
    }

    private fun updateSleepTimerProgressNotification(
        remainingMs: Long,
        totalMs: Long,
        pauseOnly: Boolean
    ) {
        val safeTotalSeconds = (totalMs / 1000L).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val remainingSeconds = (remainingMs / 1000L).coerceIn(0L, safeTotalSeconds.toLong()).toInt()
        val completedSeconds = (safeTotalSeconds - remainingSeconds).coerceIn(0, safeTotalSeconds)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            2001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getString(chromahub.rhythm.app.R.string.notification_sleep_timer_title)
        val timeText = formatSleepTimerDuration(remainingMs)
        val content = if (pauseOnly) {
            getString(chromahub.rhythm.app.R.string.notification_sleep_timer_pause_in, timeText)
        } else {
            getString(chromahub.rhythm.app.R.string.notification_sleep_timer_stop_in, timeText)
        }

        val notification = NotificationCompat.Builder(this, SLEEP_TIMER_CHANNEL_ID)
            .setSmallIcon(chromahub.rhythm.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setProgress(safeTotalSeconds, completedSeconds, false)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SLEEP_TIMER_NOTIFICATION_ID, notification)
    }

    private fun cancelSleepTimerProgressNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(SLEEP_TIMER_NOTIFICATION_ID)
    }

    private fun formatSleepTimerDuration(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return if (hours > 0L) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    private fun getPlayerAudioSessionId(): Int {
        return if (::rhythmPlayerEngine.isInitialized) rhythmPlayerEngine.getAudioSessionId() else 0
    }

    // Audio Effects (Equalizer) functionality
    fun getAudioSessionId(): Int {
        return try {
            getPlayerAudioSessionId()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio session ID", e)
            0
        }
    }
    
    private fun initializeRhythmProcessors() {
        if (rhythmBassBoostProcessor == null) {
            Log.w(TAG, "Rhythm bass boost processor is null, creating new instance")
            try {
                rhythmBassBoostProcessor = chromahub.rhythm.app.infrastructure.audio.RhythmBassBoostProcessor()
                isBassBoostAvailable = true
                appSettings.setBassBoostAvailable(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create bass boost processor", e)
                isBassBoostAvailable = false
                appSettings.setBassBoostAvailable(false)
            }
        }
        
        if (rhythmSpatializationProcessor == null) {
            Log.w(TAG, "Rhythm spatialization processor is null, creating new instance")
            try {
                rhythmSpatializationProcessor = chromahub.rhythm.app.infrastructure.audio.RhythmSpatializationProcessor()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create spatialization processor", e)
            }
        }
    }

    fun initializeAudioEffects() {
        // Initialize Rhythm processors unconditionally, they don't need session IDs
        initializeRhythmProcessors()

        val requestedSessionId = try {
            getPlayerAudioSessionId()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading audio session ID", e)
            0
        }

        if (requestedSessionId == 0) {
            Log.w(TAG, "Invalid audio session ID (0), skipping effects initialization")
            return
        }

        pendingAudioEffectsSessionId = requestedSessionId

        if (audioEffectsInitJob?.isActive == true) {
            Log.d(TAG, "Audio effects initialization already in progress; queued latest session: $requestedSessionId")
            return
        }

        audioEffectsInitJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                val audioSessionId = pendingAudioEffectsSessionId
                pendingAudioEffectsSessionId = 0
                if (audioSessionId == 0) {
                    break
                }

                audioEffectsInitMutex.withLock {
                    initializeAudioEffectsInternal(audioSessionId)
                }
            }
        }
    }

    private suspend fun initializeAudioEffectsInternal(audioSessionId: Int) {
        try {
            isInitializingAudioEffects = true
            Log.d(TAG, "Initializing audio effects with session ID: $audioSessionId (previously initialized: $audioEffectsInitialized)")

            // CRITICAL: Release ALL existing effects BEFORE creating new ones to prevent AudioFlinger error -38
            try {
                equalizer?.release()
                equalizer = null

                // Reset Rhythm processors
                rhythmBassBoostProcessor?.reset()
                rhythmSpatializationProcessor?.reset()

                Log.d(TAG, "Released existing audio effects before reinitialization")

                // Small non-blocking delay to allow Android AudioFlinger to fully release resources.
                delay(50)
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing existing effects: ${e.message}")
            }

            // Initialize equalizer directly (no dummy checks - they waste effect slots)
            try {
                equalizer = android.media.audiofx.Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
                Log.d(TAG, "Equalizer initialized with ${equalizer?.numberOfBands} bands for session $audioSessionId")
            } catch (e: Exception) {
                Log.w(TAG, "Equalizer is not available on this device: ${e.message}")
                equalizer = null
            }

            // Initialize Rhythm audio processors (replaces Android BassBoost and Spatializer)
            // Processors are created unconditionally now, just load their settings here
            Log.d(TAG, "Loading Rhythm processor settings")

            // Load saved settings and apply them
            loadSavedAudioEffects()

            // Mark as successfully initialized
            audioEffectsInitialized = true
            Log.d(TAG, "Audio effects initialization completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing audio effects", e)
            audioEffectsInitialized = false
        } finally {
            isInitializingAudioEffects = false
        }
    }
    
    private fun loadSavedAudioEffects() {
        try {
            // Load saved settings and apply them to equalizer if available
            if (equalizer != null) {
                val shouldBeEnabled = appSettings.equalizerEnabled.value
                Log.d(TAG, "Loading saved effects - EQ should be enabled: $shouldBeEnabled")
                
                if (shouldBeEnabled) {
                    // Load band levels (supports both 5-band legacy and 10-band)
                    val bandLevelsString = appSettings.equalizerBandLevels.value
                    val bandLevels = bandLevelsString.split(",").mapNotNull { it.toFloatOrNull() }
                    if (bandLevels.isNotEmpty()) {
                        // Apply band levels first, then enable
                        // Use the same interpolation logic as applyEqualizerPreset
                        applyEqualizerPreset(bandLevels.toFloatArray())
                    }
                    // Enable equalizer AFTER applying levels to avoid audio glitches
                    setEqualizerEnabledSafe(true)
                } else {
                    // Zero all bands — don't disable hardware EQ to avoid AudioFlinger DSP burst
                    withEqualizerSafe("set flat bands on load", Unit) { eq ->
                        val numberOfBands = eq.numberOfBands.toInt()
                        for (i in 0 until numberOfBands) {
                            eq.setBandLevel(i.toShort(), 0)
                        }
                    }
                    // Keep hardware EQ enabled — software controls the "off" state via flat bands
                    setEqualizerEnabledSafe(true)
                }
                
                val actualState = getEqualizerEnabledSafe()
                if (!actualState) {
                    Log.w(TAG, "EQ was unexpectedly disabled after load, re-enabling")
                    setEqualizerEnabledSafe(true)
                }
            } else {
                Log.w(TAG, "Cannot load saved equalizer settings: equalizer is null")
            }
            
            // Load Rhythm bass boost settings
            val bassBoostShouldBeEnabled = appSettings.bassBoostEnabled.value
            if (rhythmBassBoostProcessor != null) {
                rhythmBassBoostProcessor?.setEnabled(bassBoostShouldBeEnabled)
                if (bassBoostShouldBeEnabled) {
                    rhythmBassBoostProcessor?.setStrength(appSettings.bassBoostStrength.value.toShort())
                }
                Log.d(TAG, "Rhythm bass boost loaded: enabled=$bassBoostShouldBeEnabled, strength=${rhythmBassBoostProcessor?.getStrength()}")
            } else {
                Log.w(TAG, "Cannot load bass boost settings: Rhythm processor is null")
            }
            
            // Load Rhythm spatialization settings
            val virtualizerEnabled = appSettings.virtualizerEnabled.value
            virtualizerStrength = appSettings.virtualizerStrength.value.toShort()
            if (rhythmSpatializationProcessor != null) {
                rhythmSpatializationProcessor?.setEnabled(virtualizerEnabled)
                rhythmSpatializationProcessor?.setStrength(virtualizerStrength)
                Log.d(TAG, "Rhythm spatialization loaded: enabled=$virtualizerEnabled, strength=$virtualizerStrength")
            } else {
                Log.d(TAG, "Cannot load spatialization settings: Rhythm processor is null")
            }
            
            Log.d(TAG, "Loaded saved audio effects - EQ: ${appSettings.equalizerEnabled.value}, Bass: ${appSettings.bassBoostEnabled.value}, Virtualizer: ${appSettings.virtualizerEnabled.value}")
            if (::rhythmPlayerEngine.isInitialized) {
                rhythmPlayerEngine.updateTrackSelectionParameters()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved audio effects", e)
        }
    }
    
    fun setEqualizerEnabled(enabled: Boolean) {
        if (equalizer == null) {
            Log.w(TAG, "Attempting to enable equalizer but equalizer is null. Will reinitialize.")
            // Try to initialize if we have a valid session ID
            if (getPlayerAudioSessionId() != 0) {
                initializeAudioEffects()
            } else {
                Log.e(TAG, "Cannot enable equalizer: invalid audio session ID")
                return
            }
        }
        
        if (enabled) {
            // Restore saved band levels first before enabling to avoid dynamic transition glitch
            val bandLevelsString = appSettings.equalizerBandLevels.value
            val bandLevels = bandLevelsString.split(",").mapNotNull { it.toFloatOrNull() }
            if (bandLevels.isNotEmpty()) {
                applyEqualizerPreset(bandLevels.toFloatArray())
            }
            
            val actualState = setEqualizerEnabledWithVolumeGuard(true)
            Log.d(TAG, "Equalizer enabled: true, actual state: $actualState")
            if (!actualState) {
                Log.e(TAG, "Equalizer state mismatch! Requested: true, Actual: false")
            }
        } else {
            val actualState = setEqualizerEnabledWithVolumeGuard(false)
            Log.d(TAG, "Equalizer disabled (hardware kept enabled with flat bands to avoid DSP burst)")
        }
    }
    
    fun setEqualizerBandLevel(band: Short, level: Short) {
        try {
            // When a single band is changed in a 10-band UI but we only have 5 hardware bands,
            // we need to reload and re-interpolate all bands from saved settings
            val bandLevelsString = appSettings.equalizerBandLevels.value
            val bandLevels = bandLevelsString.split(",").mapNotNull { it.toFloatOrNull() }
            
            if (bandLevels.size == 10 && (equalizer?.numberOfBands?.toInt() ?: 0) < 10) {
                // Re-apply all bands with interpolation
                applyEqualizerPreset(bandLevels.toFloatArray())
                Log.d(TAG, "Re-applied 10-band EQ with interpolation after band $band change")
            } else {
                // Direct band setting when counts match
                equalizer?.setBandLevel(band, level)
                Log.d(TAG, "Set equalizer band $band to level $level")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting equalizer band level", e)
        }
    }
    
    fun getEqualizerBandLevel(band: Short): Short {
        return withEqualizerSafe("band level read", 0) { it.getBandLevel(band) }
    }
    
    fun getNumberOfBands(): Short {
        return withEqualizerSafe("band count read", 0) { it.numberOfBands }
    }
    
    fun getBandFreqRange(band: Short): IntArray? {
        return withEqualizerSafe("band frequency range read", null) { it.getBandFreqRange(band) }
    }
    
    fun isEqualizerSupported(): Boolean {
        // All devices now support equalizer with software implementation
        return true
    }
    
    /**
     * Get diagnostic information about audio effects state for debugging
     */
    fun getEqualizerDiagnostics(): String {
        return buildString {
            appendLine("=== Audio Effects Diagnostics ===")
            appendLine("Audio effects initialized: $audioEffectsInitialized")
            appendLine("Currently initializing: $isInitializingAudioEffects")
            appendLine("Audio session ID: ${getPlayerAudioSessionId()}")
            appendLine("")
            appendLine("--- Equalizer ---")
            appendLine("Equalizer object: ${if (equalizer != null) "initialized" else "null"}")
            equalizer?.let { eq ->
                appendLine("Enabled state: ${withEqualizerSafe("diagnostics enabled read", false) { it.enabled }}")
                val bandCount = withEqualizerSafe("diagnostics band count read", 0) { it.numberOfBands.toInt() }
                appendLine("Number of bands: $bandCount")
                appendLine("Band levels: ${(0 until bandCount).map { bandIndex -> withEqualizerSafe("diagnostics band level read", 0) { it.getBandLevel(bandIndex.toShort()) } }}")
            }
            appendLine("Settings - Enabled: ${appSettings.equalizerEnabled.value}")
            appendLine("Settings - Preset: ${appSettings.equalizerPreset.value}")
            appendLine("Settings - AutoEQ: ${appSettings.autoEQProfile.value}")
            appendLine("Settings - Band levels: ${appSettings.equalizerBandLevels.value}")
            appendLine("")
            appendLine("--- Rhythm Bass Boost ---")
            appendLine("Processor: ${if (rhythmBassBoostProcessor != null) "initialized" else "null"}")
            rhythmBassBoostProcessor?.let { bb ->
                appendLine("Enabled state: ${bb.isEnabled()}")
                appendLine("Strength: ${bb.getStrength()}")
            }
            appendLine("Settings - Enabled: ${appSettings.bassBoostEnabled.value}")
            appendLine("Settings - Strength: ${appSettings.bassBoostStrength.value}")
            appendLine("Available: $isBassBoostAvailable")
            appendLine("")
            appendLine("--- Rhythm Spatialization ---")
            appendLine("Processor: ${if (rhythmSpatializationProcessor != null) "initialized" else "null"}")
            rhythmSpatializationProcessor?.let { sp ->
                appendLine("Enabled state: ${sp.isEnabled()}")
                appendLine("Strength: ${sp.getStrength()}")
            }
            appendLine("Settings - Enabled: ${appSettings.virtualizerEnabled.value}")
            appendLine("Settings - Strength: ${appSettings.virtualizerStrength.value}")
        }
    }
    
    fun isBassBoostSupported(): Boolean {
        return isBassBoostAvailable
    }
    
    fun applyEqualizerPreset(levels: FloatArray) {
        try {
            if (equalizer == null) {
                Log.w(TAG, "Cannot apply preset: equalizer is null")
                return
            }
            
            equalizer?.let { eq ->
                val numberOfBands = eq.numberOfBands.toInt()
                val inputBands = levels.size
                
                if (inputBands == numberOfBands) {
                    val bandRange = eq.bandLevelRange
                    // Direct mapping if bands match
                    for (i in 0 until numberOfBands) {
                        val rawLevel = (levels[i] * 100).toInt().toShort()
                        val level = rawLevel.coerceIn(bandRange[0], bandRange[1])
                        eq.setBandLevel(i.toShort(), level)
                    }
                } else if (inputBands > numberOfBands) {
                    val bandRange = eq.bandLevelRange
                    // Map 10 UI bands to available hardware bands using interpolation
                    // This handles the case where UI has 10 bands but hardware has 5
                    val mappedLevels = interpolateBands(levels, numberOfBands)
                    for (i in 0 until numberOfBands) {
                        val rawLevel = (mappedLevels[i] * 100).toInt().toShort()
                        val level = rawLevel.coerceIn(bandRange[0], bandRange[1])
                        eq.setBandLevel(i.toShort(), level)
                    }
                } else {
                    val bandRange = eq.bandLevelRange
                    // If hardware has more bands than UI, apply what we have
                    for (i in 0 until inputBands) {
                        val rawLevel = (levels[i] * 100).toInt().toShort()
                        val level = rawLevel.coerceIn(bandRange[0], bandRange[1])
                        eq.setBandLevel(i.toShort(), level)
                    }
                }
                Log.d(TAG, "Applied equalizer preset: ${levels.size} UI bands -> $numberOfBands hardware bands")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying equalizer preset", e)
        }
    }
    
    /**
     * Interpolates 10-band EQ settings to the available hardware bands.
     * Uses weighted averaging based on frequency proximity.
     * 
     * Standard 10-band frequencies: 31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz
     * Standard 5-band frequencies: ~60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz (varies by device)
     */
    private fun interpolateBands(inputLevels: FloatArray, outputBands: Int): FloatArray {
        if (outputBands <= 0 || inputLevels.isEmpty()) return FloatArray(outputBands)
        
        val result = FloatArray(outputBands)
        val inputBands = inputLevels.size
        
        // Define the mapping of 10-band to 5-band (approximate frequency groupings)
        // Band 0 (60Hz): avg of 31Hz, 62Hz, 125Hz
        // Band 1 (230Hz): avg of 250Hz, 500Hz
        // Band 2 (910Hz): avg of 1kHz, 2kHz
        // Band 3 (3.6kHz): avg of 4kHz, 8kHz
        // Band 4 (14kHz): 16kHz
        
        if (outputBands == 5 && inputBands == 10) {
            // Optimized mapping for the common 10->5 case
            result[0] = (inputLevels[0] * 0.3f + inputLevels[1] * 0.4f + inputLevels[2] * 0.3f)
            result[1] = (inputLevels[3] * 0.5f + inputLevels[4] * 0.5f)
            result[2] = (inputLevels[5] * 0.5f + inputLevels[6] * 0.5f)
            result[3] = (inputLevels[7] * 0.5f + inputLevels[8] * 0.5f)
            result[4] = inputLevels[9]
        } else {
            // General linear interpolation for other cases
            val ratio = (inputBands - 1).toFloat() / (outputBands - 1).toFloat()
            for (i in 0 until outputBands) {
                val srcPos = i * ratio
                val lowerIndex = srcPos.toInt().coerceIn(0, inputBands - 1)
                val upperIndex = (lowerIndex + 1).coerceIn(0, inputBands - 1)
                val fraction = srcPos - lowerIndex
                result[i] = inputLevels[lowerIndex] * (1 - fraction) + inputLevels[upperIndex] * fraction
            }
        }
        
        return result
    }
    
    fun setBassBoostEnabled(enabled: Boolean) {
        if (rhythmBassBoostProcessor == null) {
            Log.w(TAG, "Attempting to enable bass boost but Rhythm processor is null. Will reinitialize.")
            if (getPlayerAudioSessionId() != 0) {
                initializeAudioEffects()
            } else {
                Log.e(TAG, "Cannot enable bass boost: invalid audio session ID")
                return
            }
        }
        
        rhythmBassBoostProcessor?.setEnabled(enabled)
        Log.d(TAG, "Rhythm bass boost enabled: $enabled (applies to next audio buffer)")
        if (::rhythmPlayerEngine.isInitialized) {
            rhythmPlayerEngine.updateTrackSelectionParameters()
        }
    }
    
    fun setBassBoostStrength(strength: Short) {
        try {
            if (rhythmBassBoostProcessor == null) {
                Log.w(TAG, "Cannot set bass boost strength: Rhythm processor is null")
                return
            }
            rhythmBassBoostProcessor?.setStrength(strength)
            Log.d(TAG, "Rhythm bass boost strength set to $strength (applies to next audio buffer)")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting bass boost strength", e)
        }
    }
    
    fun getBassBoostStrength(): Short {
        return rhythmBassBoostProcessor?.getStrength() ?: 0
    }
    
    fun setVirtualizerEnabled(enabled: Boolean) {
        if (rhythmSpatializationProcessor == null && getPlayerAudioSessionId() != 0) {
            Log.w(TAG, "Rhythm spatialization processor is null, attempting reinitialization")
            initializeAudioEffects()
        }
        
        rhythmSpatializationProcessor?.setEnabled(enabled)
        virtualizerStrength = if (enabled) virtualizerStrength else 0
        Log.d(TAG, "Rhythm spatialization enabled: $enabled (applies to next audio buffer)")
        if (::rhythmPlayerEngine.isInitialized) {
            rhythmPlayerEngine.updateTrackSelectionParameters()
        }
    }
    
    fun setVirtualizerStrength(strength: Short) {
        try {
            virtualizerStrength = strength
            rhythmSpatializationProcessor?.setStrength(strength)
            Log.d(TAG, "Rhythm spatialization strength set to $strength (applies to next audio buffer)")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting virtualizer strength", e)
        }
    }
    
    fun getVirtualizerStrength(): Short {
        return rhythmSpatializationProcessor?.getStrength() ?: virtualizerStrength
    }
    
    fun isSpatializationAvailable(): Boolean {
        // Rhythm spatialization is always available
        return rhythmSpatializationProcessor != null
    }
    
    fun getSpatializationStatus(): String {
        return when {
            rhythmSpatializationProcessor == null -> "Not initialized"
            !rhythmSpatializationProcessor!!.isEnabled() -> "Available (Rhythm-based)"
            else -> "Active (Rhythm-based)"
        }
    }
    
    // Public methods for external access
    fun getMediaSession(): MediaLibrarySession? = mediaSession
    
    fun getSleepTimerRemainingTime(): Long = sleepTimerDurationMs - (System.currentTimeMillis() - sleepTimerStartTime)
    
    fun isSleepTimerActive(): Boolean =
        sleepTimerDurationMs > 0L && sleepTimerStartTime > 0L && getRemainingTimeMs() > 0L
    
    private fun releaseAudioEffects() {
        try {
            equalizer?.release()
            equalizer = null
            
            // Reset Rhythm processors
            rhythmBassBoostProcessor?.reset()
            rhythmSpatializationProcessor?.reset()
            rhythmBassBoostProcessor = null
            rhythmSpatializationProcessor = null
            
            Log.d(TAG, "Audio effects released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio effects", e)
        }
    }
    
    /**
     * Called when the app is removed from recents (swiped away).
     * Implements the "stop playback on app close" setting.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val shouldStopPlayback = appSettings.stopPlaybackOnAppClose.value
        
        Log.d(TAG, "onTaskRemoved called - stopPlaybackOnAppClose: $shouldStopPlayback")
        
        if (shouldStopPlayback) {
            // User wants playback to stop when app is closed
            player.apply {
                playWhenReady = false
                stop()
                clearMediaItems()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            super.onTaskRemoved(rootIntent)
            return
        }
        
        // If not stopping on close, check if we should keep the service alive
        // Only keep alive if actually playing or has media
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            // Nothing playing, stop the service
            Log.d(TAG, "No active playback, stopping service")
            stopSelf()
        } else {
            Log.d(TAG, "Continuing playback in background")
        }
        
        super.onTaskRemoved(rootIntent)
    }
}
