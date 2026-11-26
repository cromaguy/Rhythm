/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.dsp

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Software Double-precision Catmull-Rom cubic resampler enforcing the user's maximum sample rate
 * ceiling (`sampleRateCapHz`, e.g., 48 kHz / 96 kHz / 192 kHz) to prevent choppy Android mixer
 * resampling or incompatible DAC rates. Returns `AudioFormat.NOT_SET` when the cap is disabled
 * (`<= 0`) or the stream is already within the ceiling.
 */
@OptIn(UnstableApi::class)
class SampleRateCapAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var maxSampleRateHz: Int = 0

    @Volatile
    private var parentProcessor: SampleRateCapAudioProcessor? = null

    private var prevHistory = Array(8) { DoubleArray(4) }
    private var fracPos: Double = 0.0
    private var stepRatio: Double = 1.0
    private var inScratch = DoubleArray(0)
    private var outScratch = DoubleArray(0)
    private val dither = TpdfDitherState()

    fun setParent(parent: SampleRateCapAudioProcessor?) {
        this.parentProcessor = parent
    }

    fun setMaxSampleRateHz(capHz: Int) {
        this.maxSampleRateHz = capHz.coerceAtLeast(0)
    }

    fun getMaxSampleRateHz(): Int = parentProcessor?.getMaxSampleRateHz() ?: maxSampleRateHz

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!RhythmDoubleDspPipeline.isSupportedEncoding(inputAudioFormat.encoding)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val cap = getMaxSampleRateHz()
        if (cap <= 0 || inputAudioFormat.sampleRate <= cap) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val chCount = inputAudioFormat.channelCount.coerceIn(1, 8)
        prevHistory = Array(chCount) { DoubleArray(4) }
        fracPos = 0.0
        stepRatio = inputAudioFormat.sampleRate.toDouble() / cap.toDouble()
        return AudioProcessor.AudioFormat(cap, chCount, inputAudioFormat.encoding)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onFlush() {
        for (hist in prevHistory) hist.fill(0.0)
        fracPos = 0.0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
        if (frames <= 0) return

        val ch = inputAudioFormat.channelCount.coerceIn(1, 8)
        val encoding = inputAudioFormat.encoding
        val totalInSamples = frames * ch
        if (inScratch.size < totalInSamples) inScratch = DoubleArray(totalInSamples)

        val read = RhythmDoubleDspPipeline.readToDoubles(inputBuffer, encoding, inScratch)
        val validFrames = read / ch
        if (validFrames <= 0) return

        val maxOutFrames = ((validFrames.toDouble() / stepRatio) + 4).toInt().coerceAtLeast(1)
        if (outScratch.size < maxOutFrames * ch) outScratch = DoubleArray(maxOutFrames * ch)

        var outFrames = 0
        var consumedSrcFrame = 0

        fun sampleAt(frameIdx: Int, channel: Int): Double = when {
            frameIdx < 0 -> {
                val histIdx = (3 + frameIdx).coerceIn(0, 3)
                prevHistory[channel][histIdx]
            }
            frameIdx < validFrames -> inScratch[frameIdx * ch + channel]
            else -> inScratch[(validFrames - 1) * ch + channel]
        }

        while (fracPos < validFrames.toDouble() && outFrames < maxOutFrames) {
            val i0 = fracPos.toInt()
            val mu = fracPos - i0.toDouble()
            for (c in 0 until ch) {
                val ym1 = sampleAt(i0 - 1, c)
                val y0 = sampleAt(i0, c)
                val y1 = sampleAt(i0 + 1, c)
                val y2 = sampleAt(i0 + 2, c)
                val interp = cubicHermite(ym1, y0, y1, y2, mu)
                outScratch[outFrames * ch + c] = interp.coerceIn(-1.0, 1.0)
            }
            outFrames++
            fracPos += stepRatio
        }

        // Preserve trailing 4 frames in ring history for seamless cross-buffer interpolation
        for (c in 0 until ch) {
            for (k in 0 until 4) {
                val srcIdx = validFrames - 4 + k
                prevHistory[c][k] = if (srcIdx >= 0) {
                    inScratch[srcIdx * ch + c]
                } else {
                    val shiftIdx = 4 + srcIdx
                    if (shiftIdx in 0..3) prevHistory[c][shiftIdx] else 0.0
                }
            }
        }
        consumedSrcFrame = validFrames
        fracPos -= consumedSrcFrame.toDouble()

        val outputBuffer = replaceOutputBuffer(outFrames * outputAudioFormat.bytesPerFrame)
        RhythmDoubleDspPipeline.writeFromDoubles(
            samples = outScratch,
            sampleCount = outFrames * ch,
            encoding = encoding,
            output = outputBuffer,
            ditherRng = if (encoding == androidx.media3.common.C.ENCODING_PCM_16BIT) dither else null
        )
        outputBuffer.flip()
    }

    private fun cubicHermite(ym1: Double, y0: Double, y1: Double, y2: Double, t: Double): Double {
        val c0 = y0
        val c1 = 0.5 * (y1 - ym1)
        val c2 = ym1 - 2.5 * y0 + 2.0 * y1 - 0.5 * y2
        val c3 = 0.5 * (y2 - ym1) + 1.5 * (y0 - y1)
        return ((c3 * t + c2) * t + c1) * t + c0
    }
}
