/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * Downmixes multi-channel surround PCM (`channelCount > 2`, e.g., 5.1 / 7.1 EAC3/DTS/FLAC/ALAC)
 * down to stereo using ITU-R BS.775 coefficients when enabled by the user or when routing to a
 * stereo DAC. Passes through with `NOT_SET` (inactive) when disabled or already <= 2 channels.
 */
@OptIn(UnstableApi::class)
class SurroundDownmixAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var parentProcessor: SurroundDownmixAudioProcessor? = null

    private var scratch = DoubleArray(0)

    fun setParent(parent: SurroundDownmixAudioProcessor?) {
        this.parentProcessor = parent
    }

    fun setEnabled(enable: Boolean) {
        this.enabled = enable
    }

    fun isDownmixEnabled(): Boolean = parentProcessor?.isDownmixEnabled() ?: enabled

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!RhythmDoubleDspPipeline.isSupportedEncoding(inputAudioFormat.encoding)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (!isDownmixEnabled() || inputAudioFormat.channelCount <= 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            2,
            inputAudioFormat.encoding
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
        if (frames <= 0) return

        val channels = inputAudioFormat.channelCount
        val encoding = inputAudioFormat.encoding
        val inSamplesNeeded = frames * channels
        val outSamplesNeeded = frames * 2

        if (scratch.size < inSamplesNeeded + outSamplesNeeded) {
            scratch = DoubleArray(inSamplesNeeded + outSamplesNeeded)
        }

        val read = RhythmDoubleDspPipeline.readToDoubles(inputBuffer, encoding, scratch)
        val actualFrames = read / channels
        val outOffset = inSamplesNeeded

        for (f in 0 until actualFrames) {
            val base = f * channels
            val left: Double
            val right: Double
            when (channels) {
                6 -> {
                    // ITU 5.1: FL, FR, FC, LFE, Ls, Rs
                    left = (scratch[base] + 0.70710678 * (scratch[base + 2] + scratch[base + 4])) * 0.5
                    right = (scratch[base + 1] + 0.70710678 * (scratch[base + 2] + scratch[base + 5])) * 0.5
                }
                8 -> {
                    // ITU 7.1: FL, FR, FC, LFE, BL, BR, SL, SR
                    left = (scratch[base] + 0.70710678 * (scratch[base + 2] + scratch[base + 4] + scratch[base + 6])) * 0.4
                    right = (scratch[base + 1] + 0.70710678 * (scratch[base + 2] + scratch[base + 5] + scratch[base + 7])) * 0.4
                }
                else -> {
                    var sumL = 0.0
                    var sumR = 0.0
                    for (c in 0 until channels) {
                        val s = scratch[base + c]
                        if (c == 2 && (channels == 3 || channels == 5)) {
                            sumL += 0.70710678 * s
                            sumR += 0.70710678 * s
                        } else if ((c and 1) == 0) {
                            sumL += s
                        } else {
                            sumR += s
                        }
                    }
                    val norm = 1.0 / sqrt(channels / 2.0).coerceAtLeast(1.0)
                    left = sumL * norm
                    right = sumR * norm
                }
            }
            scratch[outOffset + f * 2] = left.coerceIn(-1.0, 1.0)
            scratch[outOffset + f * 2 + 1] = right.coerceIn(-1.0, 1.0)
        }

        val outputBuffer = replaceOutputBuffer(actualFrames * outputAudioFormat.bytesPerFrame)
        val writeView = if (outOffset > 0) DoubleArray(actualFrames * 2) { scratch[outOffset + it] } else scratch
        RhythmDoubleDspPipeline.writeFromDoubles(
            samples = writeView,
            sampleCount = actualFrames * 2,
            encoding = encoding,
            output = outputBuffer,
            ditherRng = null
        )
        outputBuffer.flip()
    }
}
