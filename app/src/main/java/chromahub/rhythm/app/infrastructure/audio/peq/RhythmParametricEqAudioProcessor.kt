/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.peq

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import chromahub.rhythm.app.infrastructure.audio.RhythmAudioProcessor
import kotlin.math.roundToInt

/**
 * Media3 [AudioProcessor] executing Rhythm's 20-band 64-bit Double-precision [ParametricEqProcessor]
 * directly in the decoded PCM stream (supporting 8/16/24/32-bit integer & 32-bit float formats)
 * with neutral zero-copy bypass when flat or disabled.
 */
@OptIn(UnstableApi::class)
class RhythmParametricEqAudioProcessor : RhythmAudioProcessor() {

    @Volatile
    private var parentProcessor: RhythmParametricEqAudioProcessor? = null

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var activeSettings: PeqSettings = PeqSettings()

    private val dspEngine = ParametricEqProcessor()

    fun setParent(parent: RhythmParametricEqAudioProcessor?) {
        this.parentProcessor = parent
    }

    fun setEnabled(enable: Boolean) {
        this.enabled = enable
        if (!enable) dspEngine.resetState()
    }

    fun updateSettings(settings: PeqSettings) {
        this.activeSettings = settings.sanitized()
    }

    fun currentSettings(): PeqSettings = parentProcessor?.currentSettings() ?: activeSettings

    override fun isEnabled(): Boolean = parentProcessor?.isEnabled() ?: enabled

    override fun needs16BitDither(): Boolean = true

    override fun isBypassed(): Boolean {
        if (!isEnabled()) return true
        return currentSettings().isFlat
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val out = super.configure(inputAudioFormat)
        dspEngine.configure(out.sampleRate, out.channelCount)
        return out
    }

    override fun flush() {
        super.flush()
        dspEngine.resetState()
    }

    override fun processDoubleSamples(samples: DoubleArray, sampleCount: Int) {
        val cfg = currentSettings()
        val active = isEnabled() && !cfg.isFlat
        dspEngine.setEnabled(active)
        if (!active) return
        dspEngine.updateSettings(cfg)
        dspEngine.configure(sampleRate, channelCount)
        dspEngine.process(samples, sampleCount)
    }

    override fun processSamples(samples: ShortArray, sampleCount: Int) {
        // Fallback bridge only if invoked directly outside Double pipeline
        val doubles = DoubleArray(sampleCount) { samples[it].toDouble() / 32768.0 }
        processDoubleSamples(doubles, sampleCount)
        for (i in 0 until sampleCount) {
            samples[i] = (doubles[i].coerceIn(-1.0, 1.0) * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
