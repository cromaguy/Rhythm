/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.TransitionMode
import chromahub.rhythm.app.shared.data.model.TransitionSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orchestrates crossfade transitions by monitoring the master player's playback position
 * and firing transitions at the right moment via [RhythmPlayerEngine].
 *
 * Listens for media item transitions, play/pause changes, timeline changes, and repeat mode
 * changes to schedule or cancel transitions dynamically.
 *
 * Uses global crossfade settings from [AppSettings] (no per-playlist rules in Rhythm).
 */
@OptIn(UnstableApi::class)
class TransitionController(
    private val engine: RhythmPlayerEngine,
    private val appSettings: AppSettings,
) {
    companion object {
        private const val TAG = "TransitionController"
    }

    interface TransitionListener {
        fun onTransitionCompleted()
        fun onTransitionCancelled()
    }

    enum class TransitionState {
        IDLE,
        SCHEDULED,
        PREPARING,
        TRANSITIONING,
        CLEANUP
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionListener: Player.Listener? = null
    private var transitionSchedulerJob: Job? = null
    private var transitionCompletionWatchJob: Job? = null
    private var currentObservedPlayer: Player? = null
    private var scheduleGeneration: Long = 0L
    private var currentState: TransitionState = TransitionState.IDLE
    private var completionListener: TransitionListener? = null
    private var pendingScheduleMediaItem: MediaItem? = null

    fun setTransitionListener(listener: TransitionListener) {
        completionListener = listener
    }

    private fun nextScheduleGeneration(): Long {
        scheduleGeneration += 1L
        return scheduleGeneration
    }

    private fun setState(newState: TransitionState) {
        Log.d(TAG, "Transition state: $currentState -> $newState")
        currentState = newState
        
        if (newState == TransitionState.IDLE) {
            completionListener?.onTransitionCompleted()
        }
    }

    /**
     * Check if currently in a destructive state where cancellation would cause issues
     */
    fun isInDestructiveState(): Boolean {
        return currentState == TransitionState.TRANSITIONING || currentState == TransitionState.CLEANUP
    }

    /**
     * Update state based on engine status
     */
    fun updateTransitionState() {
        if (currentState == TransitionState.TRANSITIONING && !engine.isTransitionRunning()) {
            setState(TransitionState.CLEANUP)
            // Reset to IDLE after a short delay to allow cleanup
            scope.launch {
                kotlinx.coroutines.delay(100)
                setState(TransitionState.IDLE)
            }
        }
    }

    private fun watchForTransitionCompletion(generation: Long) {
        transitionCompletionWatchJob?.cancel()
        transitionCompletionWatchJob = scope.launch {
            while (isActive && engine.isTransitionRunning()) {
                delay(100)
            }

            if (isActive) {
                setState(TransitionState.IDLE)
                val itemToSchedule = pendingScheduleMediaItem ?: engine.masterPlayer.currentMediaItem
                pendingScheduleMediaItem = null
                val player = engine.masterPlayer
                if (itemToSchedule != null && (player.isPlaying || player.playWhenReady)) {
                    Log.d(TAG, "Transition finished. Rescheduling transition for track: ${itemToSchedule.mediaId}")
                    scheduleTransitionFor(itemToSchedule)
                }
            }
        }
    }

    private fun invalidateScheduledTransitions() {
        scheduleGeneration += 1L
    }

    private fun isLatestSchedule(
        generation: Long,
        expectedMediaId: String,
        player: Player = engine.masterPlayer,
    ): Boolean {
        return generation == scheduleGeneration && player.currentMediaItem?.mediaId == expectedMediaId
    }

    /** Re-attaches the listener when RhythmPlayerEngine swaps players */
    private val swapListener: (Player) -> Unit = { newPlayer ->
        Log.d(TAG, "Controller detected player swap. Moving listener.")
        transitionListener?.let { listener ->
            currentObservedPlayer?.removeListener(listener)
            currentObservedPlayer = newPlayer
            newPlayer.addListener(listener)

            val currentItem = newPlayer.currentMediaItem
            if (currentItem != null) {
                if (currentState == TransitionState.TRANSITIONING) {
                    pendingScheduleMediaItem = currentItem
                } else if (newPlayer.isPlaying || newPlayer.playWhenReady) {
                    scheduleTransitionFor(currentItem)
                }
            }
        }
    }

    /**
     * Attaches the transition listener to the master player.
     * Should be called once after the engine is initialized.
     */
    fun initialize() {
        if (transitionListener != null) return

        Log.d(TAG, "Initializing TransitionController...")

        transitionListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d(TAG, "onMediaItemTransition: ${mediaItem?.mediaId} (reason=$reason)")
                engine.setPauseAtEndOfMediaItems(false)

                if (mediaItem != null) {
                    scheduleTransitionFor(mediaItem)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val job = transitionSchedulerJob
                if (isPlaying && (job == null || job.isCompleted)) {
                    Log.d(TAG, "Playback resumed. Checking if transition needs scheduling.")
                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    if (isInDestructiveState()) {
                        Log.d(TAG, "Timeline changed (reason=$reason). Ignore cancellation since transitioning.")
                        return
                    }
                    Log.d(TAG, "Timeline changed (reason=$reason). Cancelling pending transition.")
                    invalidateScheduledTransitions()
                    transitionSchedulerJob?.cancel()
                    engine.cancelNext()
                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (isInDestructiveState()) {
                    Log.d(TAG, "Repeat mode changed. Ignore cancellation since transitioning.")
                    return
                }
                Log.d(TAG, "Repeat mode changed to $repeatMode. Rescheduling transition.")
                invalidateScheduledTransitions()
                transitionSchedulerJob?.cancel()
                engine.cancelNext()
                engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    Log.d(TAG, "onPositionDiscontinuity: SEEK detected. Old pos=${oldPosition.positionMs}, New pos=${newPosition.positionMs}")
                    handleSeek(newPosition.positionMs)
                }
            }
        }

        currentObservedPlayer = engine.masterPlayer
        currentObservedPlayer?.addListener(transitionListener!!)
        engine.addPlayerSwapListener(swapListener)
    }

    /**
     * Schedules a crossfade transition for the given [currentMediaItem].
     *
     * This method:
     * 1. Waits for any active transition to finish
     * 2. Resolves the next track (respecting repeat mode)
     * 3. Pre-buffers the next track on Player B
     * 4. Reads crossfade settings from AppSettings
     * 5. Calculates the transition point
     * 6. Polls playback position until the transition point, then fires
     */
    private fun scheduleTransitionFor(currentMediaItem: MediaItem) {
        if (currentState == TransitionState.TRANSITIONING) {
            Log.d(TAG, "Cannot schedule immediately while actively transitioning; stashing ${currentMediaItem.mediaId} for post-transition.")
            pendingScheduleMediaItem = currentMediaItem
            return
        }

        val expectedMediaId = currentMediaItem.mediaId
        val generation = nextScheduleGeneration()
        transitionSchedulerJob?.cancel()

        setState(TransitionState.SCHEDULED)
        transitionSchedulerJob = scope.launch {
            // Wait for any active transition to finish
            while (engine.isTransitionRunning()) {
                if (!isActive || !isLatestSchedule(generation, expectedMediaId)) {
                    Log.d(TAG, "Aborting stale schedule while waiting for active transition to finish.")
                    return@launch
                }
                Log.d(TAG, "Waiting for active transition to finish...")
                delay(500)
            }

            val player = engine.masterPlayer
            if (!isLatestSchedule(generation, expectedMediaId, player)) {
                Log.d(TAG, "Aborting stale schedule before transition preparation.")
                return@launch
            }

            val repeatMode = player.repeatMode

            if (repeatMode == Player.REPEAT_MODE_ONE && !appSettings.crossfadeRepeatOne.value) {
                Log.d(TAG, "Repeat-one active and crossfade-for-repeat-one disabled. Skipping transition.")
                if (currentState != TransitionState.TRANSITIONING) {
                    engine.cancelNext()
                }
                engine.setPauseAtEndOfMediaItems(false)
                setState(TransitionState.IDLE)
                return@launch
            }
            
            // Use ExoPlayer's timeline to get the next track correctly (respects shuffle mode)
            val currentWindowIndex = player.currentMediaItemIndex
            val timeline = player.currentTimeline
            val nextIndex: Int
            
            // Get the next window index using ExoPlayer's timeline (shuffle-aware)
            if (timeline.isEmpty || currentWindowIndex == C.INDEX_UNSET) {
                Log.d(TAG, "Timeline is empty or current index is unset. No transition.")
                engine.cancelNext()
                return@launch
            }
            
            val window = Timeline.Window()
            timeline.getWindow(currentWindowIndex, window)
            nextIndex = timeline.getNextWindowIndex(
                currentWindowIndex,
                repeatMode,
                player.shuffleModeEnabled
            )
            
            Log.d(TAG, "Current index: $currentWindowIndex, Next index (shuffle-aware): $nextIndex, Shuffle enabled: ${player.shuffleModeEnabled}")

            // Resolve the next media item based on repeat mode and timeline
            val nextMediaItem = when {
                repeatMode == Player.REPEAT_MODE_ONE -> currentMediaItem
                nextIndex != C.INDEX_UNSET -> player.getMediaItemAt(nextIndex)
                else -> null
            }

            if (nextMediaItem == null) {
                Log.d(TAG, "No next track (repeat=$repeatMode). No transition.")
                if (currentState != TransitionState.TRANSITIONING) {
                    engine.cancelNext()
                }
                setState(TransitionState.IDLE)
                return@launch
            }

            if (!isLatestSchedule(generation, expectedMediaId, player)) {
                Log.d(TAG, "Aborting stale schedule before preparing next track.")
                return@launch
            }

            // Check if crossfade is globally enabled
            val isCrossfadeEnabled = appSettings.crossfade.value
            if (!isCrossfadeEnabled) {
                Log.d(TAG, "Crossfade globally disabled. Using default gap.")
                engine.setPauseAtEndOfMediaItems(false)
                return@launch
            }

            Log.d(TAG, "Preparing next track: ${nextMediaItem.mediaId}")
            setState(TransitionState.PREPARING)
            engine.prepareNext(nextMediaItem)

            // Build transition settings from global preferences
            val crossfadeDurationMs = (appSettings.crossfadeDuration.value * 1000).toInt()
            val settings = TransitionSettings(
                mode = TransitionMode.OVERLAP,
                durationMs = crossfadeDurationMs,
            )

            if (settings.mode == TransitionMode.NONE || settings.durationMs <= 0) {
                Log.d(TAG, "Transition disabled or zero duration.")
                engine.setPauseAtEndOfMediaItems(false)
                return@launch
            }

            // Wait for track duration to become available
            var duration = player.duration
            while ((duration == C.TIME_UNSET || duration <= 0) && isActive) {
                if (!isLatestSchedule(generation, expectedMediaId, player)) {
                    Log.d(TAG, "Aborting stale schedule while waiting for duration.")
                    return@launch
                }
                delay(500)
                duration = player.duration
            }

            if (!isActive || !isLatestSchedule(generation, expectedMediaId, player)) return@launch

            val minFade = 500L
            val guardWindow = 150L

            if (duration < minFade + guardWindow) {
                Log.w(TAG, "Track too short for crossfade (duration=$duration).")
                engine.setPauseAtEndOfMediaItems(false)
                return@launch
            }

            val maxFadeDuration = (duration - guardWindow).coerceAtLeast(minFade)
            val effectiveDuration = settings.durationMs.toLong()
                .coerceAtLeast(minFade)
                .coerceAtMost(maxFadeDuration)

            val transitionPoint = duration - effectiveDuration

            Log.d(TAG, "Scheduled crossfade at ${transitionPoint}ms (trackDur: $duration). Fade: ${effectiveDuration}ms")

            // Prevent ExoPlayer's auto-advance; we control the transition manually
            engine.setPauseAtEndOfMediaItems(true)
            Log.d(TAG, "Enabled pauseAtEndOfMediaItems to prevent auto-skip.")

            // Handle case where we're already past the transition point
            if (transitionPoint <= player.currentPosition) {
                val remaining = duration - player.currentPosition
                val adjustedDuration = (remaining - guardWindow).coerceAtLeast(minFade)
                if (remaining > guardWindow + minFade / 2) {
                    if (isLatestSchedule(generation, expectedMediaId, player)) {
                        Log.w(TAG, "Already past transition point! Triggering immediately.")
                        engine.performTransition(settings.copy(durationMs = adjustedDuration.toInt()))
                    } else {
                        Log.d(TAG, "Skipping immediate trigger for stale schedule.")
                    }
                } else {
                    Log.w(TAG, "Too close to end (${remaining}ms left). Skipping to avoid glitch.")
                    engine.setPauseAtEndOfMediaItems(false)
                }
                return@launch
            }

            // Adaptive polling — sleep longer when far from transition point
            while (player.currentPosition < transitionPoint && isActive) {
                if (!isLatestSchedule(generation, expectedMediaId, player)) {
                    Log.d(TAG, "Aborting stale schedule while polling transition point.")
                    return@launch
                }
                val remaining = transitionPoint - player.currentPosition
                val sleep = when {
                    remaining > 5000 -> 1000L
                    remaining > 1000 -> 250L
                    else -> 50L
                }
                delay(sleep)
            }

            if (isActive && isLatestSchedule(generation, expectedMediaId, player)) {
                setState(TransitionState.TRANSITIONING)
                Log.d(TAG, "FIRING TRANSITION NOW!")
                engine.performTransition(settings.copy(durationMs = effectiveDuration.toInt()))
                watchForTransitionCompletion(generation)
            } else {
                Log.d(TAG, "Job cancelled before firing.")
                engine.setPauseAtEndOfMediaItems(false)
            }
        }
    }

    private fun handleSeek(positionMs: Long) {
        val player = engine.masterPlayer
        val mediaItem = player.currentMediaItem ?: return

        if (isInDestructiveState()) {
            Log.d(TAG, "Seek during active transition. Let transition finish.")
            return
        }

        Log.d(TAG, "Handling seek to ${positionMs}ms for ${mediaItem.mediaId}")

        // Cancel any pending transitions
        pendingScheduleMediaItem = null
        invalidateScheduledTransitions()
        transitionSchedulerJob?.cancel()

        // Check if crossfade is globally enabled
        val isCrossfadeEnabled = appSettings.crossfade.value
        if (!isCrossfadeEnabled) {
            Log.d(TAG, "Crossfade globally disabled. Skipping seek transition handling.")
            engine.cancelNext()
            engine.setPauseAtEndOfMediaItems(false)
            setState(TransitionState.IDLE)
            return
        }

        // Calculate the transition point
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) {
            Log.d(TAG, "Duration unset. Rescheduling transition normally.")
            engine.cancelNext()
            scheduleTransitionFor(mediaItem)
            return
        }

        val crossfadeDurationMs = (appSettings.crossfadeDuration.value * 1000).toInt()
        val minFade = 500L
        val guardWindow = 150L

        val maxFadeDuration = (duration - guardWindow).coerceAtLeast(minFade)
        val effectiveDuration = crossfadeDurationMs.toLong()
            .coerceAtLeast(minFade)
            .coerceAtMost(maxFadeDuration)

        val transitionPoint = duration - effectiveDuration

        if (positionMs >= transitionPoint) {
            Log.d(TAG, "Seek is PAST the transition point ($transitionPoint <= $positionMs). Skipping crossfade to avoid premature skip.")
            engine.cancelNext()
            engine.setPauseAtEndOfMediaItems(false)
            setState(TransitionState.IDLE)
        } else {
            Log.d(TAG, "Seek is BEFORE the transition point ($positionMs < $transitionPoint). Rescheduling transition.")
            engine.cancelNext()
            scheduleTransitionFor(mediaItem)
        }
    }

    fun cancelPendingTransition() {
        Log.d(TAG, "cancelPendingTransition requested")
        pendingScheduleMediaItem = null
        invalidateScheduledTransitions()
        transitionSchedulerJob?.cancel()
        transitionCompletionWatchJob?.cancel()
        engine.cancelNext()
        setState(TransitionState.IDLE)
    }

    fun setManualTransitioning() {
        Log.d(TAG, "setManualTransitioning requested")
        invalidateScheduledTransitions()
        transitionSchedulerJob?.cancel()
        
        setState(TransitionState.TRANSITIONING)
        watchForTransitionCompletion(scheduleGeneration)
    }

    /**
     * Releases the controller, removing all listeners and cancelling jobs.
     */
    fun release() {
        Log.d(TAG, "Releasing controller.")
        invalidateScheduledTransitions()
        transitionSchedulerJob?.cancel()
        transitionCompletionWatchJob?.cancel()
        engine.removePlayerSwapListener(swapListener)
        transitionListener?.let { currentObservedPlayer?.removeListener(it) }
        transitionListener = null
        currentObservedPlayer = null
        scope.cancel()
    }
}
