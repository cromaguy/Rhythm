/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.haptics

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Coordinates hardware vibration feedback synchronized to [MusicHapticsAnalyzer] pulses while
 * respecting Android Power Saver mode, playing/paused state, bit-perfect exclusivity, and cooldowns.
 * Also publishes [rhythmLevel] (`0..1`) for beat-reactive visual shaders.
 */
class MusicHapticsController(context: Context) : HapticPulseListener {

    companion object {
        private const val TAG = "MusicHaptics"
        private const val MIN_PULSE_SPACING_MS = 95L

        private val _globalRhythmLevel = MutableStateFlow(0f)
        val globalRhythmLevel: StateFlow<Float> = _globalRhythmLevel.asStateFlow()
    }

    private val appContext = context.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val hasAmplitudeControl: Boolean = vibrator?.hasAmplitudeControl() == true

    private val hapticThread = HandlerThread("Rhythm-MusicHaptics").apply { start() }
    private val hapticHandler = Handler(hapticThread.looper)

    @Volatile
    var config: MusicHapticsConfig = MusicHapticsConfig()
        private set

    @Volatile
    var isPlaybackActive: Boolean = false

    @Volatile
    var isBitPerfectActive: Boolean = false

    @Volatile
    private var lastPulseUptimeMs = 0L

    @Volatile
    private var lastPulseWasKick = false

    val analyzer = MusicHapticsAnalyzer(this)

    fun updateConfig(newConfig: MusicHapticsConfig) {
        this.config = newConfig.copy(intensityScale = newConfig.intensityScale.coerceIn(0.1f, 1.0f))
        if (!newConfig.enabled) {
            _globalRhythmLevel.value = 0f
            hapticHandler.removeCallbacksAndMessages(null)
        }
    }

    fun shouldAnalyze(): Boolean {
        if (!config.enabled || !isPlaybackActive || isBitPerfectActive) return false
        if (vibrator == null || !vibrator.hasVibrator()) return false
        if (powerManager?.isPowerSaveMode == true) return false
        return true
    }

    override fun onAnalysisFlushed() {
        _globalRhythmLevel.value = 0f
        hapticHandler.removeCallbacksAndMessages(null)
    }

    override fun onRhythmLevel(level: Float) {
        _globalRhythmLevel.value = level.coerceIn(0f, 1f)
    }

    override fun onHapticPulse(
        bassEnergy: Float,
        vocalEnergy: Float,
        isKick: Boolean,
        isSharpTransient: Boolean
    ) {
        if (!shouldAnalyze()) return
        val cfg = config
        val rawDrive = when (cfg.mode) {
            MusicHapticsMode.BASS_AND_BEATS -> if (isKick) bassEnergy else return
            MusicHapticsMode.VOCALS_ONLY -> if (isKick) return else vocalEnergy
            MusicHapticsMode.WHOLE_SONG -> if (isKick) bassEnergy else vocalEnergy * 0.85f
        }
        if (rawDrive < 0.08f) return

        hapticHandler.post {
            if (!shouldAnalyze()) return@post
            val now = SystemClock.uptimeMillis()
            val preempts = isKick && !lastPulseWasKick
            if (!preempts && now - lastPulseUptimeMs < MIN_PULSE_SPACING_MS) return@post
            lastPulseUptimeMs = now
            lastPulseWasKick = isKick
            vibratePulse(rawDrive, isKick, isSharpTransient, cfg.intensityScale)
        }
    }

    private fun vibratePulse(drive: Float, isKick: Boolean, sharp: Boolean, intensity: Float) {
        val vib = vibrator ?: return
        try {
            val strength = (drive * intensity).coerceIn(0f, 1f)
            val amp = (45 + (210 * strength)).roundToInt().coerceIn(25, 255)
            val effect = if (hasAmplitudeControl) {
                when {
                    isKick && sharp -> VibrationEffect.createWaveform(
                        longArrayOf(34L, 20L),
                        intArrayOf(amp, (amp * 0.35f).toInt().coerceAtLeast(1)),
                        -1
                    )
                    isKick -> VibrationEffect.createWaveform(
                        longArrayOf(28L, 18L),
                        intArrayOf((amp * 0.85f).toInt().coerceAtLeast(1), (amp * 0.25f).toInt().coerceAtLeast(1)),
                        -1
                    )
                    else -> VibrationEffect.createOneShot(22L, (amp * 0.7f).toInt().coerceAtLeast(1))
                }
            } else {
                val dur = if (isKick) 32L else 20L
                VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vib.vibrate(effect)
        } catch (e: Exception) {
            Log.v(TAG, "Vibration pulse skipped", e)
        }
    }

    fun release() {
        hapticHandler.removeCallbacksAndMessages(null)
        _globalRhythmLevel.value = 0f
        hapticThread.quitSafely()
    }
}
