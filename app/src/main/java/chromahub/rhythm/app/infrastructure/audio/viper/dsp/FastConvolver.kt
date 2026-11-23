/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 Rhythm Audio Enhancement Contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.viper.dsp

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Fast FIR / Impulse Response (IRS) Convolution Engine for ViPER4Android.
 * Includes built-in studio grade impulses (Studio Tube Master, Analog Tape, Acoustic Space)
 * and partitioned time-domain FIR convolution for zero latency.
 */
class FastConvolver {

    companion object {
        const val PRESET_STUDIO_MASTER = "Studio Master"
        const val PRESET_WARM_TUBE = "Warm Tube"
        const val PRESET_ANALOG_TAPE = "Analog Tape"
        const val PRESET_ACOUSTIC_ROOM = "Acoustic Room"

        val BUILTIN_PRESETS = listOf(
            PRESET_STUDIO_MASTER,
            PRESET_WARM_TUBE,
            PRESET_ANALOG_TAPE,
            PRESET_ACOUSTIC_ROOM
        )
    }

    private var kernel: FloatArray = FloatArray(64)
    private var historyL = FloatArray(64)
    private var historyR = FloatArray(64)
    private var historyIndex = 0

    init {
        loadPreset(PRESET_STUDIO_MASTER, 44100)
    }

    fun reset() {
        historyL.fill(0f)
        historyR.fill(0f)
        historyIndex = 0
    }

    fun loadPreset(name: String, sampleRate: Int) {
        val size = 64
        val safeFs = sampleRate.coerceAtLeast(8000).toFloat()
        val newKernel = FloatArray(size)

        when (name) {
            PRESET_WARM_TUBE -> {
                // Tube saturation impulse response with slight low-order decay
                for (i in 0 until size) {
                    val t = i.toFloat() / safeFs
                    val decay = exp(-t * 2200f)
                    val osc = cos(2.0 * Math.PI * 180.0 * t).toFloat()
                    newKernel[i] = if (i == 0) 0.85f else 0.15f * decay * osc
                }
            }
            PRESET_ANALOG_TAPE -> {
                // Tape head resonance and soft high-frequency absorption
                for (i in 0 until size) {
                    val t = i.toFloat() / safeFs
                    val decay = exp(-t * 3500f)
                    val osc = sin(2.0 * Math.PI * 80.0 * t).toFloat()
                    newKernel[i] = if (i == 0) 0.90f else 0.10f * decay * osc
                }
            }
            PRESET_ACOUSTIC_ROOM -> {
                // Acoustic studio room reflections
                for (i in 0 until size) {
                    val t = i.toFloat() / safeFs
                    val decay = exp(-t * 1800f)
                    val reflection = (if (i % 7 == 0) 0.15f else 0.03f) * (if (i % 2 == 0) 1f else -1f)
                    newKernel[i] = if (i == 0) 0.80f else reflection * decay
                }
            }
            else -> {
                // Studio Master - Clean transient with slight analog phase coherence
                for (i in 0 until size) {
                    val t = i.toFloat() / safeFs
                    val decay = exp(-t * 5000f)
                    newKernel[i] = if (i == 0) 0.94f else 0.06f * decay
                }
            }
        }

        // Normalize kernel gain to unity
        var sum = 0f
        for (v in newKernel) sum += Math.abs(v)
        if (sum > 0.0001f) {
            val scale = 1.0f / sum
            for (i in newKernel.indices) newKernel[i] *= scale
        }

        kernel = newKernel
        historyL = FloatArray(size)
        historyR = FloatArray(size)
        historyIndex = 0
    }

    var outLeft: Float = 0.0f
        private set
    var outRight: Float = 0.0f
        private set

    fun process(left: Float, right: Float, crossChannel: Float) {
        val size = kernel.size
        historyL[historyIndex] = left
        historyR[historyIndex] = right

        var convL = 0.0f
        var convR = 0.0f

        var kIdx = 0
        var hIdx = historyIndex
        while (kIdx < size) {
            val k = kernel[kIdx]
            convL += historyL[hIdx] * k
            convR += historyR[hIdx] * k

            hIdx--
            if (hIdx < 0) hIdx = size - 1
            kIdx++
        }

        historyIndex++
        if (historyIndex >= size) historyIndex = 0

        outLeft = convL * (1.0f - crossChannel) + convR * crossChannel
        outRight = convR * (1.0f - crossChannel) + convL * crossChannel
    }
}
