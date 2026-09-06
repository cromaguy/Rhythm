/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSpec
import android.os.Build
import androidx.media3.common.TrackSelectionParameters
import chromahub.rhythm.app.shared.data.model.AppSettings
import android.net.Uri
import chromahub.rhythm.app.features.streaming.di.StreamingMusicModule
import kotlinx.coroutines.runBlocking
import androidx.media3.datasource.cache.CacheDataSource
import chromahub.rhythm.app.infrastructure.audio.RhythmBassBoostProcessor
import chromahub.rhythm.app.infrastructure.audio.RhythmSpatializationProcessor
import chromahub.rhythm.app.infrastructure.audio.RhythmMonoAudioProcessor
import chromahub.rhythm.app.shared.data.model.TransitionSettings
import chromahub.rhythm.app.infrastructure.service.player.replaygain.ReplayGainAudioProcessor
import chromahub.rhythm.app.infrastructure.service.player.replaygain.ReplayGainUtil
import chromahub.rhythm.app.util.envelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.core.net.toUri

/**
 * Manages two ExoPlayer instances (A and B) to enable seamless crossfade transitions.
 *
 * Player A is the designated "master" player, which is exposed to the MediaSession.
 * Player B is the auxiliary player used to pre-buffer and fade in the next track.
 * After a transition, the players swap roles — Player A adopts the state of Player B,
 * ensuring continuity for the MediaSession.
 */
@OptIn(UnstableApi::class)
class RhythmPlayerEngine(
    private val context: Context,
    private val bassBoostProcessor: RhythmBassBoostProcessor? = null,
    private val spatializationProcessor: RhythmSpatializationProcessor? = null,
    private val monoProcessor: RhythmMonoAudioProcessor? = null
) {
    companion object {
        private const val TAG = "RhythmPlayerEngine"
    }

    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionJob: Job? = null
    @Volatile
    private var transitionRunning = false

    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer
    
    // Player-specific child audio processors to avoid shared-state concurrency bugs in crossfades
    private var playerABassBoost: RhythmBassBoostProcessor? = null
    private var playerBBassBoost: RhythmBassBoostProcessor? = null
    private var playerASpatialization: RhythmSpatializationProcessor? = null
    private var playerBSpatialization: RhythmSpatializationProcessor? = null
    private var playerAMono: RhythmMonoAudioProcessor? = null
    private var playerBMono: RhythmMonoAudioProcessor? = null
    private lateinit var playerAReplayGain: ReplayGainAudioProcessor
    private lateinit var playerBReplayGain: ReplayGainAudioProcessor
    private var activeReplayGainProcessor: ReplayGainAudioProcessor? = null
    private var userVolume: Float = 1.0f
    @Volatile
    var isInternalVolumeAdjustment: Boolean = false

    private val onPlayerSwappedListeners = mutableListOf<(Player) -> Unit>()

    // Active Audio Session ID Flow — used for equalizer re-attachment
    private val _activeAudioSessionId = MutableStateFlow(0)
    val activeAudioSessionId: StateFlow<Int> = _activeAudioSessionId.asStateFlow()

    // Audio Focus Management — managed manually so both players share a single focus request
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isFocusLossPause = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "AudioFocus LOSS. Pausing both players.")
                isFocusLossPause = false
                playerA.playWhenReady = false
                playerB.playWhenReady = false
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "AudioFocus LOSS_TRANSIENT. Pausing.")
                isFocusLossPause = true
                playerA.playWhenReady = false
                playerB.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "AudioFocus GAIN. Resuming if paused by loss.")
                if (isFocusLossPause) {
                    isFocusLossPause = false
                    playerA.playWhenReady = true
                    if (transitionRunning) playerB.playWhenReady = true
                }
            }
        }
    }

    // Listener attached to the active master player (playerA) for audio focus management
    private val masterPlayerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                requestAudioFocus()
            } else {
                if (!isFocusLossPause) {
                    abandonAudioFocus()
                }
            }
        }
        
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            _activeAudioSessionId.value = audioSessionId
            Log.d(TAG, "Audio session ID changed: $audioSessionId")
        }
        
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            Log.d(TAG, "Tracks changed")
            val format = tracks.getFirstSelectedTrackFormatByType(C.TRACK_TYPE_AUDIO)
            if (format != null) {
                activeReplayGainProcessor?.setRootFormat(format)
            }
        }

        override fun onVolumeChanged(volume: Float) {
            if (!transitionRunning && !isInternalVolumeAdjustment) {
                userVolume = volume
                Log.d(TAG, "User volume updated: $userVolume")
            }
        }
    }

    fun setUserVolume(volume: Float) {
        userVolume = volume.coerceIn(0f, 1f)
        if (::playerA.isInitialized) {
            playerA.volume = userVolume
        }
    }

    fun getUserVolume(): Float = userVolume

    fun addPlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.add(listener)
    }

    fun removePlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.remove(listener)
    }

    /** The master player instance that should be connected to the MediaSession. */
    val masterPlayer: Player
        get() = playerA

    fun isTransitionRunning(): Boolean = transitionRunning || transitionJob?.isActive == true

    fun getAudioSessionId(): Int = if (::playerA.isInitialized) playerA.audioSessionId else 0

    private var isReleased = false

    fun initialize() {
        if (!isReleased && ::playerA.isInitialized && playerA.applicationLooper.thread.isAlive) return

        // Recreate the scope if it was cancelled by a previous release()
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }

        if (::playerA.isInitialized) {
            try { playerA.release() } catch (_: Exception) {}
        }
        if (::playerB.isInitialized) {
            try { playerB.release() } catch (_: Exception) {}
        }

        // Instantiate child processors for Player A
        val aBass = bassBoostProcessor?.let { RhythmBassBoostProcessor().apply { setParent(it) } }
        val aSpatial = spatializationProcessor?.let { RhythmSpatializationProcessor().apply { setParent(it) } }
        val aMono = monoProcessor?.let { RhythmMonoAudioProcessor().apply { setParent(it) } }
        val aReplayGain = ReplayGainAudioProcessor()
        playerABassBoost = aBass
        playerASpatialization = aSpatial
        playerAMono = aMono
        playerAReplayGain = aReplayGain

        // Instantiate child processors for Player B
        val bBass = bassBoostProcessor?.let { RhythmBassBoostProcessor().apply { setParent(it) } }
        val bSpatial = spatializationProcessor?.let { RhythmSpatializationProcessor().apply { setParent(it) } }
        val bMono = monoProcessor?.let { RhythmMonoAudioProcessor().apply { setParent(it) } }
        val bReplayGain = ReplayGainAudioProcessor()
        playerBBassBoost = bBass
        playerBSpatialization = bSpatial
        playerBMono = bMono
        playerBReplayGain = bReplayGain

        // Apply settings initially
        val appSettings = AppSettings.getInstance(context)
        applyReplayGainSettingsOnProcessor(aReplayGain, appSettings.replayGain.value)
        applyReplayGainSettingsOnProcessor(bReplayGain, appSettings.replayGain.value)

        playerA = buildPlayer(handleAudioFocus = false, bassProcessor = aBass, spatialProcessor = aSpatial, monoProcessor = aMono, replayGainProcessor = aReplayGain)
        playerB = buildPlayer(handleAudioFocus = false, bassProcessor = bBass, spatialProcessor = bSpatial, monoProcessor = bMono, replayGainProcessor = bReplayGain)

        val initVolume = if (appSettings.useSystemVolume.value) 1.0f else appSettings.appVolume.value
        userVolume = initVolume
        playerA.volume = initVolume

        playerA.addListener(masterPlayerListener)
        activeReplayGainProcessor = aReplayGain

        _activeAudioSessionId.value = playerA.audioSessionId

        isReleased = false
        Log.d(TAG, "RhythmPlayerEngine initialized. SessionA=${playerA.audioSessionId}, initialVolume=$initVolume")

        // Reactively update track selection parameters when settings change
        scope.launch {
            launch { appSettings.crossfade.collect { updateTrackSelectionParameters() } }
            launch { appSettings.equalizerEnabled.collect { updateTrackSelectionParameters() } }
            launch { appSettings.replayGain.collect { updateTrackSelectionParameters() } }
            launch { appSettings.bassBoostEnabled.collect { updateTrackSelectionParameters() } }
            launch { appSettings.virtualizerEnabled.collect { updateTrackSelectionParameters() } }
            launch { appSettings.monoAudioEnabled.collect { updateTrackSelectionParameters() } }
            launch { appSettings.isAudioOffloadActive.collect { updateTrackSelectionParameters() } }
        }
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return

        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()

        val result = audioManager.requestAudioFocus(request)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
        } else {
            Log.w(TAG, "AudioFocus Request Failed: $result")
            playerA.playWhenReady = false
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }

    private fun buildPlayer(
        handleAudioFocus: Boolean,
        bassProcessor: RhythmBassBoostProcessor? = null,
        spatialProcessor: RhythmSpatializationProcessor? = null,
        monoProcessor: RhythmMonoAudioProcessor? = null,
        replayGainProcessor: ReplayGainAudioProcessor? = null
    ): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 30_000, 1_500, 2_500)
            .setBackBuffer(10_000, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                val processors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
                if (bassProcessor != null) {
                    processors.add(bassProcessor)
                }
                if (spatialProcessor != null) {
                    processors.add(spatialProcessor)
                }
                if (monoProcessor != null) {
                    processors.add(monoProcessor)
                }
                if (replayGainProcessor != null) {
                    processors.add(replayGainProcessor)
                }
                
                return if (processors.isNotEmpty()) {
                    androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(processors.toTypedArray())
                        .build()
                } else {
                    super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
                }
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val baseDataSourceFactory = DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory())
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(AudioCacheManager.getCache(context))
            .setUpstreamDataSourceFactory(baseDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            cacheDataSourceFactory,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    if (dataSpec.uri.scheme == "streaming") {
                        val trackId = dataSpec.uri.lastPathSegment
                        if (!trackId.isNullOrBlank()) {
                            val repository = StreamingMusicModule.provideStreamingMusicRepository(context)
                            // Run blocking is safe here as ExoPlayer calls resolveDataSpec on a background thread
                            val freshUrl = runBlocking { repository.getStreamingUrl(trackId) }
                            if (!freshUrl.isNullOrBlank()) {
                                return dataSpec.withUri((freshUrl).toUri())
                            }
                        }
                    }
                    return dataSpec
                }
            }
        )

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(resolvingDataSourceFactory)

        val appSettings = AppSettings.getInstance(context)
        val trackSelectionParametersBuilder = TrackSelectionParameters.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Software audio effects and crossfade are incompatible with hardware audio offload.
            // If any of them is enabled, offload must be disabled to prevent conflicts.
            val isCrossfadeEnabled = appSettings.crossfade.value
            val isEqualizerEnabled = appSettings.equalizerEnabled.value
            val isReplayGainEnabled = appSettings.replayGain.value
            val isBassBoostEnabled = appSettings.bassBoostEnabled.value
            val isVirtualizerEnabled = appSettings.virtualizerEnabled.value
            val isMonoAudioEnabled = appSettings.monoAudioEnabled.value
            val isOffloadSupported = appSettings.isAudioOffloadActive.value &&
                (!isCrossfadeEnabled && !isEqualizerEnabled && !isReplayGainEnabled && !isBassBoostEnabled && !isVirtualizerEnabled && !isMonoAudioEnabled)
            val audioOffloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (isOffloadSupported) {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    } else {
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    }
                )
                .build()
            trackSelectionParametersBuilder.setAudioOffloadPreferences(audioOffloadPreferences)
        }
        val trackSelectionParameters = trackSelectionParametersBuilder.build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build().apply {
                setShuffleOrder(RhythmShuffleOrder(0))
                this.trackSelectionParameters = trackSelectionParameters
                setAudioAttributes(audioAttributes, handleAudioFocus)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
                setSkipSilenceEnabled(appSettings.skipSilenceEnabled.value)
                setSeekParameters(SeekParameters.EXACT)
                playWhenReady = false
            }
    }

    fun setPauseAtEndOfMediaItems(shouldPause: Boolean) {
        playerA.pauseAtEndOfMediaItems = shouldPause
    }

    /**
     * Enables or disables gapless playback.
     * When enabled, uses ExoPlayer's native gapless mechanism.
     */
    fun setGaplessPlayback(enabled: Boolean) {
        if (::playerA.isInitialized) {
            playerA.pauseAtEndOfMediaItems = !enabled
        }
        if (::playerB.isInitialized) {
            playerB.pauseAtEndOfMediaItems = !enabled
        }
        Log.d(TAG, "Gapless playback ${if (enabled) "enabled" else "disabled"}")
    }

    fun setSkipSilenceEnabled(enabled: Boolean) {
        if (::playerA.isInitialized) {
            playerA.skipSilenceEnabled = enabled
        }
        if (::playerB.isInitialized) {
            playerB.skipSilenceEnabled = enabled
        }
        Log.d(TAG, "Skip silence ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Pre-buffers the next track on Player B.
     * Sets volume to 0 and pauses, ready for the crossfade transition.
     */
    fun prepareNext(mediaItem: MediaItem, startPositionMs: Long = 0L) {
        try {
            Log.d(TAG, "prepareNext called for ${mediaItem.mediaId}")
            playerB.stop()
            playerB.clearMediaItems()
            playerB.playWhenReady = false
            playerB.setMediaItem(mediaItem)
            playerB.prepare()
            playerB.volume = 0f
            if (startPositionMs > 0) {
                playerB.seekTo(startPositionMs)
            } else {
                playerB.seekTo(0)
            }
            playerB.pause()
            Log.d(TAG, "Player B prepared, paused, volume=0")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare next player", e)
        }
    }

    /**
     * Cancels any pending transition and resets Player B.
     */
    fun cancelNext() {
        transitionJob?.cancel()
        val wasTransitioning = transitionRunning
        transitionRunning = false
        if (::playerB.isInitialized && playerB.mediaItemCount > 0) {
            Log.d(TAG, "Cancelling next player")
            playerB.stop()
            playerB.clearMediaItems()
        }
        if (::playerA.isInitialized) {
            if (wasTransitioning) {
                playerA.volume = userVolume
            }
            setPauseAtEndOfMediaItems(false)
        }
    }

    /**
     * Performs the crossfade transition using the given settings.
     */
    @Synchronized
    fun performTransition(settings: TransitionSettings) {
        if (isTransitionRunning()) {
            Log.w(TAG, "Ignoring duplicate transition request; a transition is already active.")
            return
        }

        transitionRunning = true
        transitionJob = scope.launch {
            try {
                performOverlapTransition(settings)
            } catch (_: CancellationException) {
                Log.d(TAG, "Transition cancelled before completion.")
            } catch (e: Exception) {
                Log.e(TAG, "Error performing transition", e)
                playerA.volume = userVolume
                setPauseAtEndOfMediaItems(false)
                playerB.stop()
            } finally {
                transitionRunning = false
            }
        }
    }

    /**
     * Core crossfade logic:
     * 1. Waits for Player B to be ready
     * 2. Starts Player B at volume 0
     * 3. Swaps players EARLY so UI immediately shows the new song
     * 4. Transfers queue history/future and playback settings
     * 5. Runs a fade loop with shaped curves
     * 6. Releases old player and recreates it fresh
     */
    private suspend fun performOverlapTransition(settings: TransitionSettings) {
        if (playerB.mediaItemCount == 0) {
            playerA.volume = userVolume
            setPauseAtEndOfMediaItems(false)
            return
        }

        val outgoingPlayer = playerA
        val incomingPlayer = playerB
        val targetVolume = userVolume

        val isSelfTransition = outgoingPlayer.currentMediaItem?.mediaId == incomingPlayer.currentMediaItem?.mediaId
        val outgoingMediaItemCount = outgoingPlayer.mediaItemCount
        val currentOutgoingIndex = outgoingPlayer.currentMediaItemIndex
            .takeIf { it in 0 until outgoingMediaItemCount }
            ?: 0

        val outgoingTimeline = outgoingPlayer.currentTimeline
        val timelineTargetIndex = if (!outgoingTimeline.isEmpty && currentOutgoingIndex != C.INDEX_UNSET) {
            if (settings.isSkipPrevious) {
                outgoingTimeline.getPreviousWindowIndex(
                    currentOutgoingIndex,
                    outgoingPlayer.repeatMode,
                    outgoingPlayer.shuffleModeEnabled
                )
            } else {
                outgoingTimeline.getNextWindowIndex(
                    currentOutgoingIndex,
                    outgoingPlayer.repeatMode,
                    outgoingPlayer.shuffleModeEnabled
                )
            }
        } else {
            C.INDEX_UNSET
        }
        val incomingMediaId = incomingPlayer.currentMediaItem?.mediaId
        val incomingQueueIndex = when {
            isSelfTransition -> currentOutgoingIndex
            timelineTargetIndex in 0 until outgoingMediaItemCount && 
                outgoingPlayer.getMediaItemAt(timelineTargetIndex).mediaId == incomingMediaId -> timelineTargetIndex
            incomingMediaId != null -> (0 until outgoingMediaItemCount)
            .firstOrNull { index -> outgoingPlayer.getMediaItemAt(index).mediaId == incomingMediaId }
            ?: currentOutgoingIndex
            else -> currentOutgoingIndex
        }

        val historyToTransfer = mutableListOf<MediaItem>()
        for (i in 0 until incomingQueueIndex) {
            historyToTransfer.add(outgoingPlayer.getMediaItemAt(i))
        }

        val futureToTransfer = mutableListOf<MediaItem>()
        for (i in (incomingQueueIndex + 1) until outgoingMediaItemCount) {
            futureToTransfer.add(outgoingPlayer.getMediaItemAt(i))
        }

        incomingPlayer.repeatMode = outgoingPlayer.repeatMode
        incomingPlayer.shuffleModeEnabled = outgoingPlayer.shuffleModeEnabled
        incomingPlayer.playbackParameters = outgoingPlayer.playbackParameters

        if (historyToTransfer.isNotEmpty()) {
            incomingPlayer.addMediaItems(0, historyToTransfer)
        }

        if (futureToTransfer.isNotEmpty()) {
            incomingPlayer.addMediaItems(futureToTransfer)
        }

        if (outgoingPlayer.shuffleModeEnabled && outgoingMediaItemCount > 0) {
            val count = outgoingMediaItemCount
            val shuffledIndices = IntArray(count)
            val timeline = outgoingPlayer.currentTimeline
            var idx = 0
            var windowIndex = timeline.getFirstWindowIndex(true)
            val visited = BooleanArray(count)
            while (windowIndex != C.INDEX_UNSET && windowIndex in visited.indices && !visited[windowIndex] && idx < count) {
                shuffledIndices[idx++] = windowIndex
                visited[windowIndex] = true
                windowIndex = timeline.getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)
            }
            if (idx == count) {
                incomingPlayer.setShuffleOrder(RhythmShuffleOrder(shuffledIndices))
            }
        }

        incomingPlayer.seekTo(incomingQueueIndex, 0)

        outgoingPlayer.removeListener(masterPlayerListener)

        val incomingReplayGain = if (incomingPlayer === playerA) playerAReplayGain else playerBReplayGain
        playerA = incomingPlayer
        playerB = outgoingPlayer
        activeReplayGainProcessor = incomingReplayGain

        // Sync ReplayGain formats for the incoming player
        val tracks = playerA.currentTracks
        val format = tracks.getFirstSelectedTrackFormatByType(C.TRACK_TYPE_AUDIO)
        if (format != null) {
            incomingReplayGain.setRootFormat(format)
        }

        playerB.pauseAtEndOfMediaItems = true
        playerA.pauseAtEndOfMediaItems = false

        playerA.addListener(masterPlayerListener)
        if (playerA.playWhenReady) {
            requestAudioFocus()
        }

        onPlayerSwappedListeners.forEach { it(playerA) }

        _activeAudioSessionId.value = playerA.audioSessionId

        if (playerA.playbackState == Player.STATE_IDLE) {
            playerA.prepare()
        }

        var readinessChecks = 0
        val maxReadinessChecks = if (settings.isManualSkip) 16 else 120
        while (playerA.playbackState == Player.STATE_BUFFERING && readinessChecks < maxReadinessChecks) {
            delay(25)
            readinessChecks++
        }

        val incomingReady = playerA.playbackState == Player.STATE_READY
        var isFading = false

        if (incomingReady || settings.isManualSkip) {
            playerA.volume = 0f
            playerB.volume = targetVolume
            if (!playerB.isPlaying && playerB.playbackState == Player.STATE_READY) {
                playerB.play()
            }

            playerA.playWhenReady = true
            playerA.play()

            var playChecks = 0
            val maxPlayChecks = if (settings.isManualSkip) 16 else 80
            while (!playerA.isPlaying && playChecks < maxPlayChecks) {
                delay(25)
                playChecks++
            }

            if (playerA.isPlaying) {
                isFading = true
                delay(75)
            }
        }

        if (isFading) {
            val duration = settings.durationMs.toLong().coerceAtLeast(500L)
            val stepMs = 16L
            var elapsed = 0L

            while (elapsed <= duration) {
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                val volIn = envelope(progress, settings.curveIn) * targetVolume
                val volOut = (1f - envelope(progress, settings.curveOut)) * targetVolume

                playerA.volume = volIn.coerceIn(0f, targetVolume)
                playerB.volume = volOut.coerceIn(0f, targetVolume)

                if (playerA.playbackState == Player.STATE_ENDED || playerB.playbackState == Player.STATE_ENDED) {
                    break
                }

                delay(stepMs)
                elapsed += stepMs
            }
        } else {
            playerA.volume = targetVolume
            if (playerA.playbackState == Player.STATE_READY) {
                playerA.play()
            } else {
                playerA.playWhenReady = true
            }
        }

        playerB.volume = 0f
        playerA.volume = targetVolume

        playerB.pause()
        playerB.stop()
        playerB.clearMediaItems()

        try {
            playerB.seekTo(0)
            playerB.setPlaybackSpeed(1.0f)
            playerB.setPlaybackParameters(playerB.playbackParameters)
        } catch (e: Exception) {
            playerB.release()
            val otherReplayGain = if (incomingReplayGain === playerAReplayGain) playerBReplayGain else playerAReplayGain
            val otherBassBoost = if (incomingReplayGain === playerAReplayGain) playerBBassBoost else playerABassBoost
            val otherSpatial = if (incomingReplayGain === playerAReplayGain) playerBSpatialization else playerASpatialization
            val otherMono = if (incomingReplayGain === playerAReplayGain) playerBMono else playerAMono
            playerB = buildPlayer(
                handleAudioFocus = false,
                bassProcessor = otherBassBoost,
                spatialProcessor = otherSpatial,
                monoProcessor = otherMono,
                replayGainProcessor = otherReplayGain
            )
        }
    }

    fun getActiveReplayGainProcessor(): ReplayGainAudioProcessor? = activeReplayGainProcessor

    private fun applyReplayGainSettingsOnProcessor(processor: ReplayGainAudioProcessor, enabled: Boolean) {
        val appSettings = AppSettings.getInstance(context)
        val mode = if (enabled) {
            when (appSettings.replayGainMode.value) {
                1 -> ReplayGainUtil.Mode.Track
                2 -> ReplayGainUtil.Mode.Album
                else -> ReplayGainUtil.Mode.None
            }
        } else {
            ReplayGainUtil.Mode.None
        }
        processor.setMode(mode, false)
        processor.setReduceGain(appSettings.replayGainDrc.value) // clipping prevention (drc)
        processor.setRgGain(appSettings.replayGainPreamp.value.toInt()) // preamp in dB
        processor.setNonRgGain(appSettings.replayGainPreampUntagged.value.toInt()) // preamp in dB when no tag is present
    }

    fun applyReplayGainSettings(enabled: Boolean) {
        if (::playerAReplayGain.isInitialized) {
            applyReplayGainSettingsOnProcessor(playerAReplayGain, enabled)
        }
        if (::playerBReplayGain.isInitialized) {
            applyReplayGainSettingsOnProcessor(playerBReplayGain, enabled)
        }
        Log.d(TAG, "Replay Gain settings applied on both player processors: enabled=$enabled")
        updateTrackSelectionParameters()
    }

    fun updateTrackSelectionParameters() {
        scope.launch(Dispatchers.Main) {
            if (!::playerA.isInitialized || !::playerB.isInitialized) return@launch
            val appSettings = AppSettings.getInstance(context)
            val trackSelectionParametersBuilder = TrackSelectionParameters.Builder()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val isCrossfadeEnabled = appSettings.crossfade.value
                val isEqualizerEnabled = appSettings.equalizerEnabled.value
                val isReplayGainEnabled = appSettings.replayGain.value
                val isBassBoostEnabled = appSettings.bassBoostEnabled.value
                val isVirtualizerEnabled = appSettings.virtualizerEnabled.value
                val isMonoAudioEnabled = appSettings.monoAudioEnabled.value
                val isOffloadSupported = appSettings.isAudioOffloadActive.value &&
                    (!isCrossfadeEnabled && !isEqualizerEnabled && !isReplayGainEnabled && !isBassBoostEnabled && !isVirtualizerEnabled && !isMonoAudioEnabled)
                val audioOffloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        if (isOffloadSupported) {
                            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                        } else {
                            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                        }
                    )
                    .build()
                trackSelectionParametersBuilder.setAudioOffloadPreferences(audioOffloadPreferences)
            }
            val params = trackSelectionParametersBuilder.build()
            playerA.trackSelectionParameters = params
            playerB.trackSelectionParameters = params
            Log.d(TAG, "Updated track selection parameters: offloadActive=${appSettings.isAudioOffloadActive.value}, crossfadeEnabled=${appSettings.crossfade.value}, eqEnabled=${appSettings.equalizerEnabled.value}, bassEnabled=${appSettings.bassBoostEnabled.value}, virtualizerEnabled=${appSettings.virtualizerEnabled.value}, monoAudioEnabled=${appSettings.monoAudioEnabled.value}")
        }
    }

    fun release() {
        setPauseAtEndOfMediaItems(false)
        transitionJob?.cancel()
        // Cancel the engine scope so long-running StateFlow collections
        // (e.g. appSettings.bassBoostEnabled) don't keep this engine and its
        // service context alive after the service is destroyed.
        scope.cancel()
        abandonAudioFocus()
        if (::playerA.isInitialized) {
            playerA.removeListener(masterPlayerListener)
            playerA.release()
        }
        if (::playerB.isInitialized) playerB.release()
        isReleased = true
        Log.d(TAG, "RhythmPlayerEngine released.")
    }
}

private fun androidx.media3.common.Tracks.getFirstSelectedTrackFormatByType(trackType: Int): androidx.media3.common.Format? {
    for (group in groups) {
        if (group.type == trackType && group.isSelected) {
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) {
                    return group.getTrackFormat(i)
                }
            }
        }
    }
    return null
}
