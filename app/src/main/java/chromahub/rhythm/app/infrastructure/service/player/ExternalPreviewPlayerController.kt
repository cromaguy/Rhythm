/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import chromahub.rhythm.app.shared.data.model.LyricsData
import chromahub.rhythm.app.shared.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pure decision helper determining whether closing or leaving [ExternalPlaybackActivity]
 * should hand playback off to [MediaPlaybackService] and at what position.
 */
data class ExternalPreviewHandoffPlan(
    val shouldHandOffToMainService: Boolean,
    val song: Song?,
    val startPositionMs: Long
) {
    companion object {
        fun planForOpenInApp(song: Song?, currentPositionMs: Long): ExternalPreviewHandoffPlan {
            if (song == null) return ExternalPreviewHandoffPlan(false, null, 0L)
            return ExternalPreviewHandoffPlan(
                shouldHandOffToMainService = true,
                song = song,
                startPositionMs = currentPositionMs.coerceAtLeast(0L)
            )
        }

        fun planForClose(
            song: Song?,
            currentPositionMs: Long,
            continueOnClose: Boolean,
            alreadyOpenedInRhythm: Boolean
        ): ExternalPreviewHandoffPlan {
            if (song == null || !continueOnClose || alreadyOpenedInRhythm) {
                return ExternalPreviewHandoffPlan(false, null, 0L)
            }
            return ExternalPreviewHandoffPlan(
                shouldHandOffToMainService = true,
                song = song,
                startPositionMs = currentPositionMs.coerceAtLeast(0L)
            )
        }
    }
}

/**
 * Isolated Media3 player for external `ACTION_VIEW` previews.
 *
 * Keeps external audio file previews completely separate from [MediaPlaybackService]
 * so the user's active Rhythm queue, shuffle state, and saved position are never
 * wiped unless the user explicitly hands playback off via "Open in Rhythm" or
 * enables "Continue playback on close".
 */
@OptIn(UnstableApi::class)
class ExternalPreviewPlayerController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ExternalPreviewPlayer"
        private const val PROGRESS_POLL_INTERVAL_MS = 100L
    }

    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private var lyricsJob: Job? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _isSeeking = MutableStateFlow(false)
    val isSeeking: StateFlow<Boolean> = _isSeeking.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentLyrics = MutableStateFlow<LyricsData?>(null)
    val currentLyrics: StateFlow<LyricsData?> = _currentLyrics.asStateFlow()

    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }

        val renderersFactory = DefaultRenderersFactory(context.applicationContext).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { player ->
                player.addListener(playerListener)
                exoPlayer = player
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressLoop()
            } else {
                stopProgressLoop()
                syncProgressFromPlayer()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _isBuffering.value = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) {
                syncSongDurationIfNeeded()
                syncProgressFromPlayer()
            } else if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
                _progress.value = 1f
                stopProgressLoop()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Isolated external preview playback error", error)
            _isBuffering.value = false
            _isPlaying.value = false
            stopProgressLoop()
        }
    }

    fun loadSong(
        song: Song,
        fetchLyrics: suspend (Song) -> LyricsData?,
        autoPlay: Boolean = true
    ) {
        _currentSong.value = song
        _progress.value = 0f
        _isBuffering.value = true
        _currentLyrics.value = null

        val player = ensurePlayer()
        player.setMediaItem(MediaItem.fromUri(song.uri))
        player.prepare()
        player.playWhenReady = autoPlay

        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            val loadedLyrics = try {
                fetchLyrics(song)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load lyrics for preview song ${song.title}", e)
                null
            }
            _currentLyrics.value = loadedLyrics
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
            player.play()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val clamped = positionMs.coerceAtLeast(0L)
        _isSeeking.value = true
        player.seekTo(clamped)
        syncProgressFromPlayer()
        scope.launch {
            delay(150L)
            _isSeeking.value = false
        }
    }

    fun restartOrZero() {
        seekTo(0L)
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun currentPositionMs(): Long {
        return exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
    }

    fun createOpenInAppHandoff(): ExternalPreviewHandoffPlan {
        return ExternalPreviewHandoffPlan.planForOpenInApp(
            song = _currentSong.value,
            currentPositionMs = currentPositionMs()
        )
    }

    fun createCloseHandoff(continueOnClose: Boolean, alreadyOpenedInRhythm: Boolean): ExternalPreviewHandoffPlan {
        return ExternalPreviewHandoffPlan.planForClose(
            song = _currentSong.value,
            currentPositionMs = currentPositionMs(),
            continueOnClose = continueOnClose,
            alreadyOpenedInRhythm = alreadyOpenedInRhythm
        )
    }

    fun release() {
        stopProgressLoop()
        lyricsJob?.cancel()
        exoPlayer?.let { player ->
            try {
                player.removeListener(playerListener)
                player.stop()
                player.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing isolated preview player", e)
            }
        }
        exoPlayer = null
    }

    private fun startProgressLoop() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                syncProgressFromPlayer()
                delay(PROGRESS_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun syncSongDurationIfNeeded() {
        val player = exoPlayer ?: return
        val duration = player.duration
        val song = _currentSong.value ?: return
        if (duration > 0L && duration != C.TIME_UNSET && song.duration <= 0L) {
            _currentSong.value = song.copy(duration = duration)
        }
    }

    private fun syncProgressFromPlayer() {
        val player = exoPlayer ?: return
        val playerDuration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
        val fallbackDuration = _currentSong.value?.duration?.takeIf { it > 0L }
        val duration = playerDuration ?: fallbackDuration ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        _progress.value = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
}
