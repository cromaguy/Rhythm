/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.dsp

import androidx.media3.common.C
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.tanh

/**
 * Exponential 25 ms parameter glide smoothing so filter gain/cut/pan changes never click or zipper.
 */
class ParameterGlide(
    initialValue: Double = 0.0,
    private val glideSeconds: Double = 0.025
) {
    private var current = initialValue
    private var target = initialValue
    private var alpha = 0.002

    init {
        setSampleRate(48_000)
    }

    fun setSampleRate(sampleRate: Int) {
        val fs = sampleRate.coerceAtLeast(8_000).toDouble()
        alpha = (1.0 - exp(-1.0 / (glideSeconds * fs))).coerceIn(1e-5, 1.0)
    }

    fun setTarget(newTarget: Double) {
        target = if (newTarget.isFinite()) newTarget else 0.0
    }

    fun snapTo(value: Double) {
        val safe = if (value.isFinite()) value else 0.0
        current = safe
        target = safe
    }

    fun next(): Double {
        val diff = target - current
        if (abs(diff) < 1e-9) {
            current = target
            return current
        }
        current += diff * alpha
        return current
    }
}

/**
 * High-precision PCM codec utilities supporting 8/16/24/32-bit integer and 32-bit float streams,
 * lossless conversion to/from normalized `[-1.0, 1.0]` IEEE-754 Double arrays, triangular TPDF dither
 * on 16-bit down-quantization, and soft-knee ceiling limiting.
 */
object RhythmDoubleDspPipeline {
    private const val SCALE_16 = 32768.0
    private const val MAX_16 = 32767.0
    private const val SCALE_24 = 8_388_608.0
    private const val SCALE_32 = 2_147_483_648.0

    fun isSupportedEncoding(encoding: Int): Boolean = when (encoding) {
        C.ENCODING_PCM_8BIT,
        C.ENCODING_PCM_16BIT,
        C.ENCODING_PCM_24BIT,
        C.ENCODING_PCM_32BIT,
        C.ENCODING_PCM_FLOAT -> true
        else -> false
    }

    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }

    /** Soft-knee limiter preserving transients without hard digital clipping. */
    fun applySoftKneeLimiter(samples: DoubleArray, sampleCount: Int, ceilingDb: Double = -0.1) {
        val ceiling = 10.0.pow(ceilingDb / 20.0).coerceIn(0.1, 1.0)
        val knee = ceiling * 0.88
        val headroom = (ceiling - knee).coerceAtLeast(1e-5)
        for (i in 0 until sampleCount) {
            val v = samples[i]
            val mag = abs(v)
            if (mag > knee) {
                val excess = (mag - knee) / headroom
                samples[i] = (knee + tanh(excess) * headroom) * sign(v)
            }
        }
    }

    /** Reads interleaved PCM from [input] into normalized [outSamples] `[-1.0, 1.0]`. Returns sample count. */
    fun readToDoubles(input: ByteBuffer, encoding: Int, outSamples: DoubleArray): Int {
        val bps = bytesPerSample(encoding)
        val totalSamples = (input.remaining() / bps).coerceAtMost(outSamples.size)
        when (encoding) {
            C.ENCODING_PCM_8BIT -> {
                for (i in 0 until totalSamples) {
                    outSamples[i] = ((input.get().toInt() and 0xFF) - 128) / 128.0
                }
            }
            C.ENCODING_PCM_16BIT -> {
                for (i in 0 until totalSamples) {
                    outSamples[i] = input.getShort().toDouble() / SCALE_16
                }
            }
            C.ENCODING_PCM_24BIT -> {
                for (i in 0 until totalSamples) {
                    val b0 = input.get().toInt() and 0xFF
                    val b1 = input.get().toInt() and 0xFF
                    val b2 = input.get().toInt() and 0xFF
                    var v = b0 or (b1 shl 8) or (b2 shl 16)
                    if ((v and 0x800000) != 0) v = v or -0x1000000
                    outSamples[i] = v.toDouble() / SCALE_24
                }
            }
            C.ENCODING_PCM_32BIT -> {
                for (i in 0 until totalSamples) {
                    outSamples[i] = input.getInt().toDouble() / SCALE_32
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                for (i in 0 until totalSamples) {
                    val f = input.getFloat()
                    outSamples[i] = if (f.isFinite()) f.toDouble() else 0.0
                }
            }
        }
        return totalSamples
    }

    /** Writes normalized Double samples back into [output], applying triangular TPDF dither for 16-bit PCM. */
    fun writeFromDoubles(
        samples: DoubleArray,
        sampleCount: Int,
        encoding: Int,
        output: ByteBuffer,
        ditherRng: TpdfDitherState? = null
    ) {
        when (encoding) {
            C.ENCODING_PCM_8BIT -> {
                for (i in 0 until sampleCount) {
                    val v = (samples[i].coerceIn(-1.0, 1.0) * 127.0 + 128.0).toInt().coerceIn(0, 255)
                    output.put(v.toByte())
                }
            }
            C.ENCODING_PCM_16BIT -> {
                for (i in 0 until sampleCount) {
                    val scaled = samples[i].coerceIn(-1.0, 1.0) * SCALE_16
                    val dither = ditherRng?.nextTriangularLsb() ?: 0.0
                    val s = kotlin.math.round(scaled + dither).toInt().coerceIn(-32768, 32767)
                    output.putShort(s.toShort())
                }
            }
            C.ENCODING_PCM_24BIT -> {
                for (i in 0 until sampleCount) {
                    val v = kotlin.math.round(samples[i].coerceIn(-1.0, 1.0) * SCALE_24)
                        .toInt().coerceIn(-8_388_608, 8_388_607)
                    output.put((v and 0xFF).toByte())
                    output.put(((v shr 8) and 0xFF).toByte())
                    output.put(((v shr 16) and 0xFF).toByte())
                }
            }
            C.ENCODING_PCM_32BIT -> {
                for (i in 0 until sampleCount) {
                    val v = kotlin.math.round(samples[i].coerceIn(-1.0, 1.0) * SCALE_32)
                        .toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                    output.putInt(v)
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                for (i in 0 until sampleCount) {
                    val v = samples[i]
                    output.putFloat(if (v.isFinite()) v.coerceIn(-1.5, 1.5).toFloat() else 0f)
                }
            }
        }
    }
}

/** Fast deterministic Xorshift64 triangular-PDF (TPDF) noise generator in `[-1.0, +1.0]` LSB. */
class TpdfDitherState(seed: Long = -0x61C8864680B583EBL) {
    private var state: Long = if (seed == 0L) -0x61C8864680B583EBL else seed

    private fun nextUniform(): Double {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        val mantissa = (x ushr 11) and 0x1FFFFFFFFFFFFFL
        return mantissa.toDouble() / 9007199254740992.0 - 0.5
    }

    fun nextTriangularLsb(): Double = nextUniform() + nextUniform()
}
