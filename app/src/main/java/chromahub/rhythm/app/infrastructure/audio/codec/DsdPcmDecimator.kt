/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.codec

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin LUT-accelerated Kaiser-windowed FIR 1-bit PDM/DSD to Float32 PCM decimator.
 *
 * Converts DSD64 (2.8224 MHz), DSD128 (5.6448 MHz), DSD256 (11.2896 MHz), and DSD512 (22.5792 MHz)
 * into 176.4 kHz (or 192 kHz for 48k-family DSD) IEEE Float PCM using an 8-bit polyphase lookup
 * table so every 8 consecutive 1-bit samples evaluate via a single array addition without NDK/JNI.
 */
class DsdPcmDecimator(
    val channelCount: Int,
    val dsdSampleRateHz: Int,
    val lsbFirst: Boolean
) {
    companion object {
        private const val LUT_BYTES_PER_16X = 8 // 64 taps per 16x block; scaled cleanly for higher rates
        private const val SACD_NOMINAL_GAIN = 0.70710678 // -3 dB headroom matching SACD +6 dB calibration
        private const val KAISER_BETA = 9.0

        private fun reverseBits(value: Int): Int {
            var b = value and 0xFF
            b = ((b and 0xF0) ushr 4) or ((b and 0x0F) shl 4)
            b = ((b and 0xCC) ushr 2) or ((b and 0x33) shl 2)
            b = ((b and 0xAA) ushr 1) or ((b and 0x55) shl 1)
            return b
        }

        private fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            val halfX = x * 0.5
            for (k in 1..24) {
                val ratio = halfX / k.toDouble()
                term *= ratio * ratio
                sum += term
                if (term < sum * 1e-15) break
            }
            return sum
        }

        fun resolveDecimationFactor(dsdSampleRateHz: Int): Int = when {
            dsdSampleRateHz >= 20_000_000 -> 64  // DSD512 -> 352.8 / 384 kHz
            dsdSampleRateHz >= 10_000_000 -> 64  // DSD256 -> 176.4 / 192 kHz
            dsdSampleRateHz >= 5_000_000 -> 32   // DSD128 -> 176.4 / 192 kHz
            else -> 16                           // DSD64  -> 176.4 / 192 kHz
        }
    }

    val safeChannels: Int = channelCount.coerceIn(1, 8)
    val decimationFactor: Int = resolveDecimationFactor(dsdSampleRateHz)
    val outputSampleRateHz: Int = (dsdSampleRateHz / decimationFactor).coerceAtLeast(44_100)
    private val stepBytes: Int = decimationFactor / 8
    private val filterBytes: Int = LUT_BYTES_PER_16X * max(1, decimationFactor / 16)
    private val history = ByteArray(safeChannels * filterBytes)
    private val lut = FloatArray(filterBytes * 256)
    private val nativeDecimator = chromahub.rhythm.nativeaudio.NativeDsdDecimator(
        channelCount = safeChannels,
        decimationFactor = decimationFactor,
        lsbFirst = lsbFirst
    )
    private var floatScratch = FloatArray(0)

    private var ringWritePos = 0
    private var bytePhase = 0

    init {
        if (!nativeDecimator.isUsingNative) {
            buildFilterTables()
        }
        reset()
    }

    fun reset() {
        nativeDecimator.reset()
        // Alternating 01010101 bits represent zero DC energy in 1-bit PDM/DSD
        history.fill(0x55.toByte())
        ringWritePos = 0
        bytePhase = 0
    }

    fun maxOutputBytes(inputBytes: Int, planar: Boolean, blockSizePerChannel: Int): Int {
        val framesPerChannel = if (planar) blockSizePerChannel else inputBytes / safeChannels
        val maxSamplesPerChannel = (framesPerChannel + stepBytes) / stepBytes + 2
        return maxSamplesPerChannel * safeChannels * 4
    }

    /**
     * Decimates DSF planar or DFF interleaved 1-bit DSD bytes into little-endian 32-bit IEEE Float PCM.
     *
     * @return number of PCM bytes written into [outputPcmBytes].
     */
    fun process(
        input: ByteArray,
        offset: Int,
        length: Int,
        planar: Boolean,
        blockSizePerChannel: Int,
        outputPcmBytes: ByteArray
    ): Int {
        if (length <= 0) return 0
        if (nativeDecimator.isUsingNative) {
            val maxFloats = outputPcmBytes.size / 4
            if (floatScratch.size < maxFloats) {
                floatScratch = FloatArray(maxFloats)
            }
            val producedFloats = nativeDecimator.processToFloats(
                input = input,
                offset = offset,
                length = length,
                planar = planar,
                blockBytesPerChannel = blockSizePerChannel,
                outputPcm = floatScratch
            )
            val outBuf = ByteBuffer.wrap(outputPcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until producedFloats) {
                outBuf.putFloat(i * 4, floatScratch[i])
            }
            return producedFloats * 4
        }
        val framesPerChannel = if (planar) blockSizePerChannel else length / safeChannels
        if (framesPerChannel <= 0) return 0

        val outBuf = ByteBuffer.wrap(outputPcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        var writtenBytes = 0

        for (i in 0 until framesPerChannel) {
            if (planar) {
                for (ch in 0 until safeChannels) {
                    val idx = offset + ch * blockSizePerChannel + i
                    history[ch * filterBytes + ringWritePos] = if (idx < input.size) input[idx] else 0x55
                }
            } else {
                val base = offset + i * safeChannels
                for (ch in 0 until safeChannels) {
                    history[ch * filterBytes + ringWritePos] = input[base + ch]
                }
            }
            ringWritePos++
            if (ringWritePos == filterBytes) ringWritePos = 0

            bytePhase++
            if (bytePhase >= stepBytes) {
                bytePhase = 0
                for (ch in 0 until safeChannels) {
                    val sample = convolveChannel(ch)
                    if (writtenBytes + 4 <= outputPcmBytes.size) {
                        outBuf.putFloat(writtenBytes, sample)
                        writtenBytes += 4
                    }
                }
            }
        }
        return writtenBytes
    }

    private fun convolveChannel(ch: Int): Float {
        val chBase = ch * filterBytes
        var acc = 0f
        var ringIdx = ringWritePos
        for (g in 0 until filterBytes) {
            val byteVal = history[chBase + ringIdx].toInt() and 0xFF
            acc += lut[g * 256 + byteVal]
            ringIdx++
            if (ringIdx == filterBytes) ringIdx = 0
        }
        return acc.coerceIn(-1f, 1f)
    }

    private fun buildFilterTables() {
        val numTaps = filterBytes * 8
        val taps = DoubleArray(numTaps)
        val cutoffNormalized = 0.25 / decimationFactor.toDouble()
        val i0Beta = besselI0(KAISER_BETA)
        val halfN = (numTaps - 1).toDouble() * 0.5

        var tapSum = 0.0
        for (i in 0 until numTaps) {
            val n = i.toDouble() - halfN
            val sinc = if (abs(n) < 1e-9) {
                2.0 * cutoffNormalized
            } else {
                sin(2.0 * PI * cutoffNormalized * n) / (PI * n)
            }
            val ratio = n / halfN
            val arg = max(0.0, 1.0 - ratio * ratio)
            val win = besselI0(KAISER_BETA * sqrt(arg)) / i0Beta
            val w = sinc * win
            taps[i] = w
            tapSum += w
        }

        val gainScale = if (tapSum > 1e-12) SACD_NOMINAL_GAIN / tapSum else 1.0
        for (i in 0 until numTaps) {
            taps[i] *= gainScale
        }

        for (byteIdx in 0 until filterBytes) {
            val rowBase = byteIdx * 256
            val tapBase = byteIdx * 8
            for (byteVal in 0 until 256) {
                val ordered = if (lsbFirst) reverseBits(byteVal) else byteVal
                var acc = 0.0
                for (bit in 0 until 8) {
                    val isOne = ((ordered ushr (7 - bit)) and 0x01) != 0
                    val sym = if (isOne) 1.0 else -1.0
                    acc += sym * taps[tapBase + bit]
                }
                lut[rowBase + byteVal] = acc.toFloat()
            }
        }
    }
}
