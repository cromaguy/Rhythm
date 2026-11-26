/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/**
 * Rhythm Mono Audio Processor - Real-time downmixing of stereo channels to mono.
 *
 * Used for single earpiece listening (center panning) so that left and right audio channels
 * are mixed evenly into both channels.
 */
@OptIn(UnstableApi::class)
class RhythmMonoAudioProcessor : RhythmAudioProcessor() {

    companion object {
        private const val TAG = "RhythmMonoAudio"
    }

    @Volatile
    private var parentProcessor: RhythmMonoAudioProcessor? = null
    @Volatile
    private var enabled: Boolean = false

    /**
     * Set the parent processor for dynamic synchronization
     */
    fun setParent(parent: RhythmMonoAudioProcessor?) {
        this.parentProcessor = parent
    }

    /**
     * Enable or disable mono audio downmixing
     */
    fun setEnabled(enable: Boolean) {
        Log.d(TAG, "Mono Audio enabled: $enable")
        this.enabled = enable
    }

    override fun isEnabled(): Boolean = parentProcessor?.isEnabled() ?: enabled

    override fun isBypassed(): Boolean {
        return !isEnabled() || channelCount != 2
    }

    override fun processSamples(samples: ShortArray, sampleCount: Int) {
        if (!isEnabled() || channelCount != 2) return

        // Process stereo pairs (L, R, L, R, ...)
        for (i in 0 until sampleCount - 1 step 2) {
            val left = samples[i].toInt()
            val right = samples[i + 1].toInt()
            val mono = ((left + right) / 2).toShort()
            samples[i] = mono
            samples[i + 1] = mono
        }
    }

    override fun processDoubleSamples(samples: DoubleArray, sampleCount: Int) {
        if (!isEnabled() || channelCount != 2) return

        for (i in 0 until sampleCount - 1 step 2) {
            val mono = (samples[i] + samples[i + 1]) * 0.5
            samples[i] = mono
            samples[i + 1] = mono
        }
    }
}
