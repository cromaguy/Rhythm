/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import chromahub.rhythm.app.features.local.data.database.RhythmDatabase
import chromahub.rhythm.app.infrastructure.service.player.automix.SmartMixInput
import chromahub.rhythm.app.infrastructure.service.player.automix.SmartTransitionPlanner
import chromahub.rhythm.app.infrastructure.service.player.automix.TagBpmReader
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.data.model.Curve
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
import kotlinx.coroutines.withContext

/**
 * Orchestrates gapless / crossfade / two-tier Smart AutoMix transitions by monitoring playback
 * position, resolving global/per-playlist transition rules and BPM/Camelot metadata off the main
 * thread, and triggering [RhythmPlayerEngine] with smooth bass-swap & tempo lock.
 */
@OptIn(UnstableApi::class)
class TransitionController(
    private val engine: RhythmPlayerEngine,
    private val appSettings: AppSettings,
    private val context: Context? = null
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

        scope.launch {
            appSettings.crossfade.collect { enabled ->
                Log.d(TAG, "Crossfade setting changed: $enabled")
                if (!isInDestructiveState()) {
                    if (enabled) {
                        engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                    } else {
                        cancelPendingTransition()
                        engine.setPauseAtEndOfMediaItems(false)
                    }
                }
            }
        }
        scope.launch {
            appSettings.crossfadeDuration.collect { duration ->
                Log.d(TAG, "Crossfade duration changed: $duration s")
                if (!isInDestructiveState() && appSettings.crossfade.value) {
                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                }
            }
        }
        scope.launch {
            appSettings.crossfadeRepeatOne.collect { repeatOne ->
                Log.d(TAG, "Crossfade repeat-one setting changed: $repeatOne")
                if (!isInDestructiveState() && appSettings.crossfade.value) {
                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                }
            }
        }
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

            val fullQueue = if (player.mediaItemCount > 0) {
                (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
            } else {
                listOf(nextMediaItem)
            }
            val targetIdx = if (repeatMode == Player.REPEAT_MODE_ONE) {
                currentWindowIndex
            } else {
                nextIndex.takeIf { it in fullQueue.indices } ?: 0
            }
            val shuffleIndices = if (player.shuffleModeEnabled) {
                engine.extractShuffleIndices(player)
            } else {
                null
            }

            Log.d(TAG, "Preparing next track: ${nextMediaItem.mediaId} at targetIndex=$targetIdx (queueSize=${fullQueue.size})")
            setState(TransitionState.PREPARING)
            engine.prepareNext(
                mediaItem = nextMediaItem,
                targetIndex = targetIdx,
                fullQueue = fullQueue,
                shuffleIndices = shuffleIndices
            )

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

            val smartPlan = resolveSmartMixPlan(
                outgoingItem = currentMediaItem,
                incomingItem = nextMediaItem,
                outgoingDurationMs = duration,
                baseDurationMs = settings.durationMs,
                baseMode = settings.mode
            )
            val resolvedSettings = smartPlan.settings
            if (resolvedSettings.mode == TransitionMode.NONE || resolvedSettings.durationMs <= 0) {
                Log.d(TAG, "Smart AutoMix preserved gapless playback.")
                engine.setPauseAtEndOfMediaItems(false)
                return@launch
            }

            val minFade = 500L
            val guardWindow = 150L
            val leadTimeMs = 50L

            if (duration < minFade * 2) {
                Log.w(TAG, "Track too short for crossfade (duration=$duration).")
                engine.setPauseAtEndOfMediaItems(false)
                return@launch
            }

            val maxAllowedFade = (duration * 0.45f).toLong().coerceAtLeast(minFade)
            val effectiveDuration = resolvedSettings.durationMs.toLong()
                .coerceIn(minFade, maxAllowedFade)
                .coerceAtMost((duration - guardWindow).coerceAtLeast(minFade))

            val transitionPoint = (duration - effectiveDuration - leadTimeMs).coerceAtLeast(0L)

            Log.d(TAG, "Scheduled crossfade at ${transitionPoint}ms (trackDur: $duration, leadTime: ${leadTimeMs}ms, bpmRatio=${smartPlan.tempoRatio}, bassSwap=${smartPlan.useBassSwap}). Fade: ${effectiveDuration}ms")

            // Handle case where we're already past the transition point
            if (transitionPoint <= player.currentPosition) {
                val remaining = duration - player.currentPosition
                val adjustedDuration = (remaining - guardWindow).coerceAtLeast(minFade)
                if (remaining > guardWindow + minFade / 2) {
                    if (isLatestSchedule(generation, expectedMediaId, player)) {
                        Log.w(TAG, "Already past transition point! Triggering immediately.")
                        engine.setPauseAtEndOfMediaItems(true)
                        engine.performTransition(resolvedSettings.copy(durationMs = adjustedDuration.toInt()))
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
                if (remaining <= 2000L) {
                    engine.setPauseAtEndOfMediaItems(true)
                }
                val sleep = when {
                    remaining > 5000 -> 1000L
                    remaining > 2000 -> 250L
                    remaining > 500 -> 50L
                    else -> 20L
                }
                delay(sleep)
            }

            if (isActive && isLatestSchedule(generation, expectedMediaId, player)) {
                engine.setPauseAtEndOfMediaItems(true)
                val preFireDelayMs = ((transitionPoint + leadTimeMs) - player.currentPosition)
                    .coerceIn(0L, leadTimeMs)
                if (preFireDelayMs > 0L) {
                    delay(preFireDelayMs)
                }
            }

            if (isActive && isLatestSchedule(generation, expectedMediaId, player)) {
                setState(TransitionState.TRANSITIONING)
                Log.d(TAG, "FIRING TRANSITION NOW!")
                engine.setPauseAtEndOfMediaItems(true)
                engine.performTransition(resolvedSettings.copy(durationMs = effectiveDuration.toInt()))
                watchForTransitionCompletion(generation)
            } else {
                Log.d(TAG, "Job cancelled before firing.")
                engine.setPauseAtEndOfMediaItems(false)
            }
        }
    }

    private suspend fun resolveSmartMixPlan(
        outgoingItem: MediaItem,
        incomingItem: MediaItem,
        outgoingDurationMs: Long,
        baseDurationMs: Int,
        baseMode: TransitionMode
    ) = withContext(Dispatchers.IO) {
        val ctx = context
        var ruleDuration = baseDurationMs
        var ruleMode = baseMode
        var curveIn = Curve.EQUAL_POWER
        var curveOut = Curve.EQUAL_POWER

        var ruleBpmSync = appSettings.playbackPrefs.autoMixBpmSyncEnabled.value

        if (ctx != null) {
            runCatching {
                val rule = RhythmDatabase.getInstance(ctx).transitionRuleDao().resolveBestRule(
                    fromSongId = outgoingItem.mediaId,
                    toSongId = incomingItem.mediaId,
                    playlistId = null
                )
                if (rule != null) {
                    ruleDuration = rule.durationMs
                    ruleMode = if (rule.mode.equals("NONE", true)) TransitionMode.NONE else TransitionMode.OVERLAP
                    curveIn = runCatching { Curve.valueOf(rule.curveIn) }.getOrDefault(Curve.EQUAL_POWER)
                    curveOut = runCatching { Curve.valueOf(rule.curveOut) }.getOrDefault(Curve.EQUAL_POWER)
                    ruleBpmSync = rule.bpmSync
                }
            }
        }

        val outTags = if (ctx != null) outgoingItem.localConfiguration?.uri?.let { TagBpmReader.readFromUri(ctx, it) } else null
        val inTags = if (ctx != null) incomingItem.localConfiguration?.uri?.let { TagBpmReader.readFromUri(ctx, it) } else null
        val sameAlbum = outgoingItem.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() } != null &&
            outgoingItem.mediaMetadata.albumTitle?.toString() == incomingItem.mediaMetadata.albumTitle?.toString() &&
            outgoingItem.mediaMetadata.artist?.toString() == incomingItem.mediaMetadata.artist?.toString()
        val hasVocalClash = if (ctx != null) {
            val outTimestamps = readCachedSyncedLyricsTimestamps(ctx, outgoingItem)
            val inTimestamps = readCachedSyncedLyricsTimestamps(ctx, incomingItem)
            SmartTransitionPlanner.detectVocalOverlap(
                outgoingVocalTimestampsMs = outTimestamps,
                incomingVocalTimestampsMs = inTimestamps,
                outgoingDurationMs = outgoingDurationMs,
                overlapDurationMs = ruleDuration
            )
        } else {
            false
        }

        SmartTransitionPlanner.plan(
            SmartMixInput(
                baseDurationMs = ruleDuration,
                baseMode = ruleMode,
                curveIn = curveIn,
                curveOut = curveOut,
                outgoingDurationMs = outgoingDurationMs,
                incomingDurationMs = outgoingDurationMs,
                outgoingBpm = outTags?.bpm,
                incomingBpm = inTags?.bpm,
                outgoingKey = outTags?.initialKey,
                incomingKey = inTags?.initialKey,
                bpmSyncEnabled = ruleBpmSync,
                sameAlbumSequence = sameAlbum,
                hasVocalClash = hasVocalClash
            )
        )
    }

    private fun readCachedSyncedLyricsTimestamps(
        context: android.content.Context,
        item: MediaItem
    ): List<Long> {
        val artist = item.mediaMetadata.artist?.toString()?.trim().orEmpty()
        val title = item.mediaMetadata.title?.toString()?.trim().orEmpty()
        if (artist.isBlank() || title.isBlank()) return emptyList()
        return runCatching {
            val fileName = chromahub.rhythm.app.features.local.data.repository.LyricsRepositoryDelegate.cacheFileName(
                artist = artist,
                title = title
            )
            val cacheFile = java.io.File(java.io.File(context.filesDir, "lyrics"), fileName)
            if (!cacheFile.exists() || !cacheFile.isFile) return emptyList()
            val lyrics = com.google.gson.Gson().fromJson(
                cacheFile.readText(),
                chromahub.rhythm.app.shared.data.model.LyricsData::class.java
            ) ?: return emptyList()
            SmartTransitionPlanner.parseSyncedTimestampsMs(lyrics.syncedLyrics)
        }.getOrDefault(emptyList())
    }

    private fun handleSeek(positionMs: Long) {
        val player = engine.masterPlayer
        val mediaItem = player.currentMediaItem ?: return

        if (isInDestructiveState()) {
            Log.d(TAG, "Seek during active transition. Snap-completing transition immediately.")
            engine.snapCompleteTransition()
            setState(TransitionState.IDLE)
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
