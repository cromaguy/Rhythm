/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import chromahub.rhythm.app.core.device.HiFiCapabilityChecker
import chromahub.rhythm.app.core.device.DeviceCapabilitiesProvider
import androidx.media3.exoplayer.DecoderReuseEvaluation
import java.util.concurrent.TimeUnit
import android.os.Build
import androidx.media3.common.TrackSelectionParameters
import chromahub.rhythm.app.shared.data.model.AppSettings
import android.net.Uri
import chromahub.rhythm.app.infrastructure.audio.RhythmBassBoostProcessor
import chromahub.rhythm.app.infrastructure.audio.RhythmSpatializationProcessor
import chromahub.rhythm.app.infrastructure.audio.RhythmMonoAudioProcessor
import chromahub.rhythm.app.infrastructure.audio.dsp.AudioPipelineTelemetry
import chromahub.rhythm.app.infrastructure.audio.dsp.RhythmAudioSinkCoordinator
import chromahub.rhythm.app.infrastructure.audio.peq.RhythmParametricEqAudioProcessor
import chromahub.rhythm.app.shared.data.model.TransitionSettings
import chromahub.rhythm.app.infrastructure.service.player.replaygain.ReplayGainAudioProcessor
import chromahub.rhythm.app.infrastructure.service.player.replaygain.ReplayGainCache
import chromahub.rhythm.app.infrastructure.service.player.replaygain.ReplayGainUtil
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import chromahub.rhythm.app.util.calculateVolumeIn
import chromahub.rhythm.app.util.calculateVolumeOut
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
    private val monoProcessor: RhythmMonoAudioProcessor? = null,
    private val viperProcessor: chromahub.rhythm.app.infrastructure.audio.viper.ViperAudioProcessor? = null,
    private val parametricEqProcessor: RhythmParametricEqAudioProcessor? = null
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
    private var playerAViper: chromahub.rhythm.app.infrastructure.audio.viper.ViperAudioProcessor? = null
    private var playerBViper: chromahub.rhythm.app.infrastructure.audio.viper.ViperAudioProcessor? = null
    private var playerAParametricEq: RhythmParametricEqAudioProcessor? = null
    private var playerBParametricEq: RhythmParametricEqAudioProcessor? = null
    private lateinit var playerAReplayGain: ReplayGainAudioProcessor
    private lateinit var playerBReplayGain: ReplayGainAudioProcessor
    private var activeReplayGainProcessor: ReplayGainAudioProcessor? = null
    private var userVolume: Float = 1.0f
    @Volatile
    var isInternalVolumeAdjustment: Boolean = false

    private val _pipelineTelemetry = MutableStateFlow(AudioPipelineTelemetry())
    val pipelineTelemetry: StateFlow<AudioPipelineTelemetry> = _pipelineTelemetry.asStateFlow()

    private val onPlayerSwappedListeners = mutableListOf<(Player) -> Unit>()

    // Active Audio Session ID Flow — used for equalizer re-attachment
    private val _activeAudioSessionId = MutableStateFlow(0)
    val activeAudioSessionId: StateFlow<Int> = _activeAudioSessionId.asStateFlow()

    private val _currentEngineFormat = MutableStateFlow<EngineAudioFormat?>(null)
    val currentEngineFormat: StateFlow<EngineAudioFormat?> = _currentEngineFormat.asStateFlow()

    private val telemetryTracker = PlayerTelemetryTracker<Player>()
    private val analyticsListenersByPlayer = java.util.IdentityHashMap<ExoPlayer, AnalyticsListener>()

    private fun attachAnalyticsListener(targetPlayer: ExoPlayer) {
        detachAnalyticsListener(targetPlayer)
        val listener = object : AnalyticsListener {
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                telemetryTracker.recordDecoderInitialized(targetPlayer, decoderName)
                val format = targetPlayer.audioFormat
                    ?: targetPlayer.currentTracks.getFirstSelectedTrackFormatByType(C.TRACK_TYPE_AUDIO)
                if (format != null && masterPlayer === targetPlayer) {
                    _currentEngineFormat.value = EngineAudioFormat(format, decoderName)
                }
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: androidx.media3.common.Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                if (masterPlayer === targetPlayer) {
                    val decoderName = telemetryTracker.getDecoderName(targetPlayer)
                    _currentEngineFormat.value = EngineAudioFormat(format, decoderName)
                }
            }
        }
        analyticsListenersByPlayer[targetPlayer] = listener
        targetPlayer.addAnalyticsListener(listener)
    }

    private fun detachAnalyticsListener(targetPlayer: ExoPlayer) {
        analyticsListenersByPlayer.remove(targetPlayer)?.let { listener ->
            targetPlayer.removeAnalyticsListener(listener)
        }
        telemetryTracker.clearPlayer(targetPlayer)
    }

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
                if (transitionRunning && ::playerB.isInitialized && playerB.playbackState != Player.STATE_ENDED) {
                    playerB.playWhenReady = true
                }
            } else {
                if (transitionRunning && ::playerB.isInitialized) {
                    playerB.playWhenReady = false
                }
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
            val format = tracks.getFirstSelectedTrackFormatByType(C.TRACK_TYPE_AUDIO) ?: (masterPlayer as? ExoPlayer)?.audioFormat
            if (format != null) {
                val info = ReplayGainUtil.parse(format)
                masterPlayer.currentMediaItem?.mediaId?.let { id ->
                    ReplayGainCache.put(id, info)
                }
                activeReplayGainProcessor?.setRootFormat(format)
                val currentDec = telemetryTracker.getDecoderName(masterPlayer)
                _currentEngineFormat.value = EngineAudioFormat(format, currentDec)
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

    @Volatile
    private var preferredAudioDevice: AudioDeviceInfo? = null

    fun setPreferredAudioDevice(device: AudioDeviceInfo?) {
        preferredAudioDevice = device
        if (::playerA.isInitialized) {
            try { playerA.setPreferredAudioDevice(device) } catch (e: Exception) { Log.w(TAG, "Failed to set preferred device on playerA", e) }
        }
        if (::playerB.isInitialized) {
            try { playerB.setPreferredAudioDevice(device) } catch (e: Exception) { Log.w(TAG, "Failed to set preferred device on playerB", e) }
        }
    }

    private var isReleased = false

    fun initialize() {
        if (!isReleased && ::playerA.isInitialized && playerA.applicationLooper.thread.isAlive) return

        // Recreate the scope if it was cancelled by a previous release()
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }

        if (::playerA.isInitialized) {
            try {
                detachAnalyticsListener(playerA)
                playerA.release()
            } catch (_: Exception) {}
        }
        if (::playerB.isInitialized) {
            try {
                detachAnalyticsListener(playerB)
                playerB.release()
            } catch (_: Exception) {}
        }

        // Instantiate child processors for Player A
        val aBass = bassBoostProcessor?.let { RhythmBassBoostProcessor().apply { setParent(it) } }
        val aSpatial = spatializationProcessor?.let { RhythmSpatializationProcessor().apply { setParent(it) } }
        val aMono = monoProcessor?.let { RhythmMonoAudioProcessor().apply { setParent(it) } }
        val aViper = viperProcessor?.let { chromahub.rhythm.app.infrastructure.audio.viper.ViperAudioProcessor().apply { setParent(it) } }
        val aPeq = parametricEqProcessor?.let { RhythmParametricEqAudioProcessor().apply { setParent(it) } }
        val aReplayGain = ReplayGainAudioProcessor()
        playerABassBoost = aBass
        playerASpatialization = aSpatial
        playerAMono = aMono
        playerAViper = aViper
        playerAParametricEq = aPeq
        playerAReplayGain = aReplayGain

        // Instantiate child processors for Player B
        val bBass = bassBoostProcessor?.let { RhythmBassBoostProcessor().apply { setParent(it) } }
        val bSpatial = spatializationProcessor?.let { RhythmSpatializationProcessor().apply { setParent(it) } }
        val bMono = monoProcessor?.let { RhythmMonoAudioProcessor().apply { setParent(it) } }
        val bViper = viperProcessor?.let { chromahub.rhythm.app.infrastructure.audio.viper.ViperAudioProcessor().apply { setParent(it) } }
        val bPeq = parametricEqProcessor?.let { RhythmParametricEqAudioProcessor().apply { setParent(it) } }
        val bReplayGain = ReplayGainAudioProcessor()
        playerBBassBoost = bBass
        playerBSpatialization = bSpatial
        playerBMono = bMono
        playerBViper = bViper
        playerBParametricEq = bPeq
        playerBReplayGain = bReplayGain

        // Apply settings initially
        val appSettings = AppSettings.getInstance(context)
        applyReplayGainSettingsOnProcessor(aReplayGain, appSettings.replayGain.value)
        applyReplayGainSettingsOnProcessor(bReplayGain, appSettings.replayGain.value)

        playerA = buildPlayer(handleAudioFocus = false, bassProcessor = aBass, spatialProcessor = aSpatial, monoProcessor = aMono, replayGainProcessor = aReplayGain, viperProcessor = aViper, parametricEqProcessor = aPeq)
        playerB = buildPlayer(handleAudioFocus = false, bassProcessor = bBass, spatialProcessor = bSpatial, monoProcessor = bMono, replayGainProcessor = bReplayGain, viperProcessor = bViper, parametricEqProcessor = bPeq)

        val initVolume = if (appSettings.useSystemVolume.value) 1.0f else appSettings.appVolume.value
        userVolume = initVolume
        playerA.volume = initVolume

        val initSpeed = appSettings.playbackSpeed.value.coerceIn(0.25f, 3.0f)
        val initPitch = appSettings.playbackPitch.value.coerceIn(0.5f, 2.0f)
        val initParams = PlaybackParameters(initSpeed, initPitch)
        playerA.playbackParameters = initParams
        playerB.playbackParameters = initParams

        playerA.addListener(masterPlayerListener)
        attachAnalyticsListener(playerA)
        attachAnalyticsListener(playerB)
        activeReplayGainProcessor = aReplayGain

        _activeAudioSessionId.value = playerA.audioSessionId

        isReleased = false
        Log.d(TAG, "RhythmPlayerEngine initialized. SessionA=${playerA.audioSessionId}, initialVolume=$initVolume, speed=$initSpeed, pitch=$initPitch")

        // Reactively update track selection parameters when settings change
        scope.launch {
            launch { appSettings.crossfade.collect { updateTrackSelectionParameters() } }
            launch { appSettings.equalizerEnabled.collect { updateTrackSelectionParameters() } }
            launch { appSettings.replayGain.collect { updateTrackSelectionParameters() } }
            launch { appSettings.bassBoostEnabled.collect { updateTrackSelectionParameters() } }
            launch { appSettings.virtualizerEnabled.collect { updateTrackSelectionParameters() } }
            launch { appSettings.monoAudioEnabled.collect { updateTrackSelectionParameters() } }
            launch { appSettings.isAudioOffloadActive.collect { updateTrackSelectionParameters() } }
            launch {
                appSettings.playbackSpeed.collect {
                    updatePlaybackParameters()
                    updateTrackSelectionParameters()
                }
            }
            launch {
                appSettings.playbackPitch.collect {
                    updatePlaybackParameters()
                    updateTrackSelectionParameters()
                }
            }
            launch { appSettings.gaplessPlayback.collect { updateTrackSelectionParameters() } }
            launch { appSettings.skipSilenceEnabled.collect { updateTrackSelectionParameters() } }
            launch {
                try {
                    chromahub.rhythm.app.infrastructure.audio.viper.ViperSettingsRepository.getInstance(context)
                        .configFlow.collect { updateTrackSelectionParameters() }
                } catch (_: Exception) {}
            }
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
        replayGainProcessor: ReplayGainAudioProcessor? = null,
        viperProcessor: chromahub.rhythm.app.infrastructure.audio.viper.ViperAudioProcessor? = null,
        parametricEqProcessor: RhythmParametricEqAudioProcessor? = null
    ): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 30_000, 1_500, 2_500)
            .setBackBuffer(10_000, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val appSettings = AppSettings.getInstance(context)
        val isPlaybackRateModified =
            kotlin.math.abs(appSettings.playbackSpeed.value - 1f) > 0.001f ||
            kotlin.math.abs(appSettings.playbackPitch.value - 1f) > 0.001f
        val isBitPerfectRouting = appSettings.audioRoutingMode.value == "app"

        // Hi-Res Float stays unlocked alongside Double-precision DSP processors (EQ/PEQ/Bass/Spatial/Mono/ViPER);
        // only SonicAudioProcessor (speed/pitch) or Skip Silence require 16-bit fallback when active.
        val requires16BitOnly = appSettings.skipSilenceEnabled.value || isPlaybackRateModified
        val floatOutputPref = appSettings.audioEngineFloatOutput.value &&
            HiFiCapabilityChecker.isSupported() &&
            !isBitPerfectRouting &&
            !requires16BitOnly

        val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaultDecoders = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )
            val disabled = appSettings.audioEngineDisabledDecoders.value
            val preferHw = appSettings.audioEnginePreferHardware.value

            val filtered = if (disabled.isEmpty()) defaultDecoders else defaultDecoders.filter { it.name !in disabled }
            if (preferHw) {
                filtered.sortedByDescending { if (DeviceCapabilitiesProvider.isLikelyHardwareDecoder(it.name)) 1 else 0 }
            } else {
                filtered
            }
        }

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                val processors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
                if (replayGainProcessor != null) {
                    processors.add(replayGainProcessor)
                }
                if (parametricEqProcessor != null) {
                    processors.add(parametricEqProcessor)
                }
                if (bassProcessor != null) {
                    processors.add(bassProcessor)
                }
                if (spatialProcessor != null) {
                    processors.add(spatialProcessor)
                }
                if (monoProcessor != null) {
                    processors.add(monoProcessor)
                }
                if (viperProcessor != null) {
                    processors.add(viperProcessor)
                }
                
                val silenceSkippingDelegate = SilenceSkippingAudioProcessor(
                    /* minimumSilenceDurationUs = */ 500_000L,
                    /* silenceRetentionRatio = */ 0.2f,
                    /* maxSilenceToKeepDurationUs = */ 1_000_000L,
                    /* minVolumeToKeepPercentageWhenMuting = */ 5,
                    /* silenceThresholdLevel = */ 40.toShort()
                )
                val safeSilenceSkippingProcessor = object : androidx.media3.common.audio.AudioProcessor by silenceSkippingDelegate {
                    override fun configure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
                        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
                            silenceSkippingDelegate.configure(inputAudioFormat)
                        } else {
                            androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET
                        }
                    }
                }
                val sonicAudioProcessor = SonicAudioProcessor()
                val allProcessors: Array<androidx.media3.common.audio.AudioProcessor> =
                    (listOf(safeSilenceSkippingProcessor) + processors + sonicAudioProcessor).toTypedArray()
                val processorChain = object : androidx.media3.common.audio.AudioProcessorChain {
                    override fun getAudioProcessors(): Array<androidx.media3.common.audio.AudioProcessor> = allProcessors

                    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
                        sonicAudioProcessor.setSpeed(playbackParameters.speed)
                        sonicAudioProcessor.setPitch(playbackParameters.pitch)
                        return playbackParameters
                    }

                    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
                        silenceSkippingDelegate.setEnabled(skipSilenceEnabled)
                        return skipSilenceEnabled
                    }

                    override fun getMediaDuration(playoutDuration: Long): Long {
                        return if (sonicAudioProcessor.isActive) sonicAudioProcessor.getMediaDuration(playoutDuration) else playoutDuration
                    }

                    override fun getSkippedOutputFrameCount(): Long {
                        return silenceSkippingDelegate.skippedFrames
                    }
                }
                val baseSink = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(floatOutputPref)
                    .setEnableAudioOutputPlaybackParameters(true)
                    .setAudioProcessorChain(processorChain)
                    .build()
                val fallbackSink = if (floatOutputPref) {
                    DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(false)
                        .setEnableAudioOutputPlaybackParameters(true)
                        .setAudioProcessorChain(processorChain)
                        .build()
                } else {
                    null
                }

                return RhythmAudioSinkCoordinator(
                    primarySink = baseSink,
                    fallbackSink = fallbackSink,
                    replayGainProcessor = replayGainProcessor,
                    isBitPerfectActive = {
                        appSettings.audioRoutingMode.value == "app"
                    },
                    onTelemetryChanged = { _pipelineTelemetry.value = it }
                )
            }
        }.apply {
            val extensionMode = if (appSettings.decoderExtensionsEnabled.value) {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            } else {
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            }
            setExtensionRendererMode(extensionMode)
            setMediaCodecSelector(mediaCodecSelector)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val mediaSourceFactory = RhythmMediaDataSourceFactory.createMediaSourceFactory(context)

        val defaultParams = TrackSelectionParameters.DEFAULT
        val trackSelectionParameters = buildTrackSelectionParameters(defaultParams, appSettings)

        val player = ExoPlayer.Builder(context, renderersFactory)
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
                preferredAudioDevice?.let { dev ->
                    try { setPreferredAudioDevice(dev) } catch (e: Exception) { Log.w(TAG, "Failed to apply preferred device", e) }
                }
            }

        if (replayGainProcessor != null) {
            player.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    val currentMediaId = player.currentMediaItem?.mediaId
                    val format = tracks.getFirstSelectedTrackFormatByType(C.TRACK_TYPE_AUDIO)
                    if (format != null) {
                        val info = ReplayGainUtil.parse(format)
                        if (currentMediaId != null) {
                            ReplayGainCache.put(currentMediaId, info)
                        }
                        replayGainProcessor.setRootFormat(format)
                    }
                }
            })
        }

        return player
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
     *
     * When [fullQueue] is supplied, sets the complete queue on Player B with [targetIndex]
     * so Player B has 100% of the queue context in advance, eliminating timeline manipulation
     * latency during the transition and preventing history loss on interruption.
     */
    fun prepareNext(
        mediaItem: MediaItem,
        targetIndex: Int = 0,
        fullQueue: List<MediaItem>? = null,
        shuffleIndices: IntArray? = null,
        startPositionMs: Long = 0L
    ) {
        try {
            Log.d(TAG, "prepareNext called for ${mediaItem.mediaId} (targetIndex=$targetIndex, queueSize=${fullQueue?.size ?: 1})")
            ReplayGainCache.get(mediaItem.mediaId)?.let { cachedInfo ->
                playerBReplayGain.setTags(cachedInfo)
            }
            playerB.stop()
            playerB.clearMediaItems()
            playerB.playWhenReady = false

            if (!fullQueue.isNullOrEmpty() && targetIndex in fullQueue.indices) {
                playerB.setMediaItems(fullQueue, targetIndex, if (startPositionMs > 0) startPositionMs else 0L)
                if (shuffleIndices != null && shuffleIndices.size == fullQueue.size) {
                    playerB.setShuffleOrder(RhythmShuffleOrder(shuffleIndices))
                }
            } else {
                playerB.setMediaItem(mediaItem)
                if (startPositionMs > 0) {
                    playerB.seekTo(startPositionMs)
                } else {
                    playerB.seekTo(0)
                }
            }

            if (::playerA.isInitialized) {
                playerB.playbackParameters = playerA.playbackParameters
            }
            playerB.prepare()
            playerB.volume = 0f
            playerB.pause()
            Log.d(TAG, "Player B prepared, paused, volume=0")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare next player", e)
        }
    }

    /**
     * Extracts the active shuffle sequence of window indices from [player].
     */
    fun extractShuffleIndices(player: Player = masterPlayer): IntArray? {
        if (!player.shuffleModeEnabled || player.mediaItemCount <= 0) return null
        val count = player.mediaItemCount
        val shuffledIndices = IntArray(count)
        val timeline = player.currentTimeline
        var idx = 0
        var windowIndex = timeline.getFirstWindowIndex(true)
        val visited = BooleanArray(count)
        while (windowIndex != C.INDEX_UNSET && windowIndex in visited.indices && !visited[windowIndex] && idx < count) {
            shuffledIndices[idx++] = windowIndex
            visited[windowIndex] = true
            windowIndex = timeline.getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)
        }
        return if (idx == count) shuffledIndices else null
    }

    /**
     * Instantly finishes any active transition, snapping Player A to full volume
     * and stopping Player B cleanly. Used when the user seeks or manually skips during a transition.
     */
    @Synchronized
    fun snapCompleteTransition() {
        if (!transitionRunning) return
        Log.d(TAG, "snapCompleteTransition: force-completing active transition")
        transitionRunning = false
        transitionJob?.cancel()
        if (::playerB.isInitialized) {
            playerB.volume = 0f
            playerB.stop()
            playerB.clearMediaItems()
        }
        if (::playerA.isInitialized) {
            playerA.volume = userVolume
            setPauseAtEndOfMediaItems(false)
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
     * 1. Uses pre-buffered Player B with full queue context for zero-latency start
     * 2. Swaps player references and updates MediaSession / effects
     * 3. Runs an equal-power or configured shaped curve fade loop
     * 4. Pauses and resumes both players synchronously if user pauses during transition
     * 5. Releases outgoing player and resets it fresh for subsequent transitions
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

        val outgoingMediaItemCount = outgoingPlayer.mediaItemCount
        val currentOutgoingIndex = outgoingPlayer.currentMediaItemIndex
            .takeIf { it in 0 until outgoingMediaItemCount }
            ?: 0

        val incomingMediaId = incomingPlayer.currentMediaItem?.mediaId
        val isSelfTransition = outgoingPlayer.currentMediaItem?.mediaId == incomingMediaId
        if (incomingPlayer.mediaItemCount <= 1 && outgoingMediaItemCount > 1) {
            val fullQueue = (0 until outgoingMediaItemCount).map { outgoingPlayer.getMediaItemAt(it) }
            val incomingQueueIndex = when {
                isSelfTransition -> currentOutgoingIndex
                incomingMediaId != null -> (0 until outgoingMediaItemCount)
                    .firstOrNull { index -> outgoingPlayer.getMediaItemAt(index).mediaId == incomingMediaId }
                    ?: currentOutgoingIndex
                else -> currentOutgoingIndex
            }
            incomingPlayer.setMediaItems(fullQueue, incomingQueueIndex, 0L)
            if (outgoingPlayer.shuffleModeEnabled) {
                extractShuffleIndices(outgoingPlayer)?.let { indices ->
                    incomingPlayer.setShuffleOrder(RhythmShuffleOrder(indices))
                }
            }
        }

        incomingPlayer.repeatMode = outgoingPlayer.repeatMode
        incomingPlayer.shuffleModeEnabled = outgoingPlayer.shuffleModeEnabled
        incomingPlayer.playbackParameters = outgoingPlayer.playbackParameters

        outgoingPlayer.removeListener(masterPlayerListener)

        val outgoingReplayGain = playerAReplayGain
        val incomingReplayGain = playerBReplayGain
        val outgoingBassBoost = playerABassBoost
        val incomingBassBoost = playerBBassBoost
        val outgoingSpatial = playerASpatialization
        val incomingSpatial = playerBSpatialization
        val outgoingMono = playerAMono
        val incomingMono = playerBMono
        val outgoingViper = playerAViper
        val incomingViper = playerBViper
        playerA = incomingPlayer
        playerB = outgoingPlayer
        playerAReplayGain = incomingReplayGain
        playerBReplayGain = outgoingReplayGain
        playerABassBoost = incomingBassBoost
        playerBBassBoost = outgoingBassBoost
        playerASpatialization = incomingSpatial
        playerBSpatialization = outgoingSpatial
        playerAMono = incomingMono
        playerBMono = outgoingMono
        playerAViper = incomingViper
        playerBViper = outgoingViper
        activeReplayGainProcessor = incomingReplayGain

        // Sync ReplayGain formats for the incoming player
        val mediaIdToSync = playerA.currentMediaItem?.mediaId ?: incomingMediaId
        val cachedTags = ReplayGainCache.get(mediaIdToSync)
        if (cachedTags != null) {
            incomingReplayGain.setTags(cachedTags)
        } else {
            val tracks = playerA.currentTracks
            val format = tracks.getFirstSelectedTrackFormatByType(C.TRACK_TYPE_AUDIO)
            if (format != null) {
                val info = ReplayGainUtil.parse(format)
                if (mediaIdToSync != null) {
                    ReplayGainCache.put(mediaIdToSync, info)
                }
                incomingReplayGain.setRootFormat(format)
            }
        }

        playerB.pauseAtEndOfMediaItems = true
        playerA.pauseAtEndOfMediaItems = false

        playerA.addListener(masterPlayerListener)
        if (playerA.playWhenReady) {
            requestAudioFocus()
        }

        onPlayerSwappedListeners.forEach { it(playerA) }

        _activeAudioSessionId.value = playerA.audioSessionId

        val swappedFormat = playerA.audioFormat ?: playerA.currentTracks.getFirstSelectedTrackFormatByType(C.TRACK_TYPE_AUDIO)
        val swappedDecoder = telemetryTracker.getDecoderName(playerA)
        if (swappedFormat != null) {
            _currentEngineFormat.value = EngineAudioFormat(swappedFormat, swappedDecoder)
        }

        if (playerA.playbackState == Player.STATE_IDLE) {
            playerA.prepare()
        }

        var readinessChecks = 0
        val maxReadinessChecks = if (settings.isManualSkip) 16 else 60
        while (playerA.playbackState == Player.STATE_BUFFERING && readinessChecks < maxReadinessChecks) {
            delay(20)
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
            isFading = true
        }

        if (isFading) {
            val configuredDuration = settings.durationMs.toLong().coerceAtLeast(500L)
            val remainingAudio = if (playerB.duration > 0 && playerB.currentPosition >= 0) {
                (playerB.duration - playerB.currentPosition).coerceAtLeast(500L)
            } else {
                configuredDuration
            }
            val fadeDuration = if (!settings.isManualSkip) {
                configuredDuration.coerceAtMost(remainingAudio)
            } else {
                configuredDuration
            }

            val stepMs = 16L
            var elapsed = 0L

            while (elapsed <= fadeDuration && transitionRunning) {
                while (!playerA.playWhenReady && transitionRunning) {
                    if (playerB.playWhenReady) {
                        playerB.playWhenReady = false
                    }
                    delay(50)
                }
                if (playerA.playWhenReady && !playerB.playWhenReady && playerB.playbackState != Player.STATE_ENDED) {
                    playerB.playWhenReady = true
                }

                if (!transitionRunning) break

                val progress = (elapsed.toFloat() / fadeDuration).coerceIn(0f, 1f)
                val volIn = calculateVolumeIn(progress, settings.curveIn) * targetVolume
                val volOut = calculateVolumeOut(progress, settings.curveOut) * targetVolume

                playerA.volume = volIn.coerceIn(0f, targetVolume)
                playerB.volume = volOut.coerceIn(0f, targetVolume)

                if (playerA.playbackState == Player.STATE_ENDED) {
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
            if (!playerB.currentTimeline.isEmpty) {
                playerB.seekTo(0)
            }
            playerB.playbackParameters = playerA.playbackParameters
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset playerB standby parameters, falling back to clean rebuild", e)
            try {
                detachAnalyticsListener(playerB)
                playerB.release()
            } catch (_: Exception) {}
            val otherReplayGain = if (incomingReplayGain === playerAReplayGain) playerBReplayGain else playerAReplayGain
            val otherBassBoost = if (incomingReplayGain === playerAReplayGain) playerBBassBoost else playerABassBoost
            val otherSpatial = if (incomingReplayGain === playerAReplayGain) playerBSpatialization else playerASpatialization
            val otherMono = if (incomingReplayGain === playerAReplayGain) playerBMono else playerAMono
            val otherViper = if (incomingReplayGain === playerAReplayGain) playerBViper else playerAViper
            val otherPeq = if (incomingReplayGain === playerAReplayGain) playerBParametricEq else playerAParametricEq
            playerB = buildPlayer(
                handleAudioFocus = false,
                bassProcessor = otherBassBoost,
                spatialProcessor = otherSpatial,
                monoProcessor = otherMono,
                replayGainProcessor = otherReplayGain,
                viperProcessor = otherViper,
                parametricEqProcessor = otherPeq
            ).apply {
                playbackParameters = playerA.playbackParameters
                attachAnalyticsListener(this)
            }
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

    private fun buildTrackSelectionParameters(
        baseParameters: TrackSelectionParameters,
        appSettings: AppSettings
    ): TrackSelectionParameters {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return baseParameters
        }

        // Software audio effects, crossfade, and variable playback rates are incompatible
        // with hardware audio offload. If any of them is active, offload must be disabled
        // to prevent conflicts or pitch/resampling distortions on buggy OEM DSP implementations.
        val isCrossfadeEnabled = appSettings.crossfade.value
        val isEqualizerEnabled = appSettings.equalizerEnabled.value
        val isReplayGainEnabled = appSettings.replayGain.value
        val isBassBoostEnabled = appSettings.bassBoostEnabled.value
        val isVirtualizerEnabled = appSettings.virtualizerEnabled.value
        val isMonoAudioEnabled = appSettings.monoAudioEnabled.value
        val isPlaybackRateModified =
            kotlin.math.abs(appSettings.playbackSpeed.value - 1f) > 0.001f ||
            kotlin.math.abs(appSettings.playbackPitch.value - 1f) > 0.001f

        val isSkipSilenceEnabled = appSettings.skipSilenceEnabled.value
        val isViperActive = viperProcessor?.isEnabled() ?: (
            appSettings.audioRoutingMode.value != "app" &&
                try {
                    chromahub.rhythm.app.infrastructure.audio.viper.ViperSettingsRepository
                        .getInstance(context).configFlow.value.masterEnabled
                } catch (_: Exception) { false }
        )

        val isOffloadSupported = appSettings.isAudioOffloadActive.value &&
            (!isCrossfadeEnabled && !isEqualizerEnabled && !isReplayGainEnabled &&
             !isBassBoostEnabled && !isVirtualizerEnabled && !isMonoAudioEnabled &&
             !isPlaybackRateModified && !isSkipSilenceEnabled && !isViperActive)

        val audioOffloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(
                if (isOffloadSupported) {
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                } else {
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                }
            )
            .setIsSpeedChangeSupportRequired(isPlaybackRateModified)
            .setIsGaplessSupportRequired(appSettings.gaplessPlayback.value)
            .build()

        return baseParameters.buildUpon()
            .setAudioOffloadPreferences(audioOffloadPreferences)
            .build()
    }

    fun updatePlaybackParameters() {
        if (!::playerA.isInitialized || !::playerB.isInitialized) return
        val appSettings = AppSettings.getInstance(context)
        val speed = appSettings.playbackSpeed.value.coerceIn(0.25f, 3.0f)
        val pitch = appSettings.playbackPitch.value.coerceIn(0.5f, 2.0f)
        val params = PlaybackParameters(speed, pitch)
        if (playerA.playbackParameters != params) {
            playerA.playbackParameters = params
            Log.d(TAG, "Applied playback parameters on playerA: speed=$speed, pitch=$pitch")
        }
        if (playerB.playbackParameters != params) {
            playerB.playbackParameters = params
            Log.d(TAG, "Applied playback parameters on playerB: speed=$speed, pitch=$pitch")
        }
    }

    fun updateTrackSelectionParameters() {
        scope.launch(Dispatchers.Main) {
            if (!::playerA.isInitialized || !::playerB.isInitialized) return@launch
            val appSettings = AppSettings.getInstance(context)
            val paramsA = buildTrackSelectionParameters(playerA.trackSelectionParameters, appSettings)
            val paramsB = buildTrackSelectionParameters(playerB.trackSelectionParameters, appSettings)
            if (playerA.trackSelectionParameters != paramsA) {
                playerA.trackSelectionParameters = paramsA
            }
            if (playerB.trackSelectionParameters != paramsB) {
                playerB.trackSelectionParameters = paramsB
            }
            Log.d(TAG, "Updated track selection parameters: offloadActive=${appSettings.isAudioOffloadActive.value}, crossfadeEnabled=${appSettings.crossfade.value}, eqEnabled=${appSettings.equalizerEnabled.value}, viperActive=${viperProcessor?.isEnabled() == true}, bassEnabled=${appSettings.bassBoostEnabled.value}, virtualizerEnabled=${appSettings.virtualizerEnabled.value}, monoAudioEnabled=${appSettings.monoAudioEnabled.value}, speed=${appSettings.playbackSpeed.value}, pitch=${appSettings.playbackPitch.value}")
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
            detachAnalyticsListener(playerA)
            playerA.removeListener(masterPlayerListener)
            playerA.release()
        }
        if (::playerB.isInitialized) {
            detachAnalyticsListener(playerB)
            playerB.release()
        }
        isReleased = true
        Log.d(TAG, "RhythmPlayerEngine released.")
    }

    fun getCurrentAudioFormat(): androidx.media3.common.Format? =
        _currentEngineFormat.value?.format ?: (masterPlayer as? ExoPlayer)?.audioFormat

    fun getCurrentDecoderName(): String =
        _currentEngineFormat.value?.decoderName.orEmpty()
}

data class EngineAudioFormat(
    val format: androidx.media3.common.Format,
    val decoderName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

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
