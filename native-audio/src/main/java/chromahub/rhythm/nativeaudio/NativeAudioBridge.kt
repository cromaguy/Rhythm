/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.nativeaudio

import android.util.Log
import java.io.Closeable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class AAudioDirectProbeResult(
    val available: Boolean,
    val statusCode: Int,
    val exclusive: Boolean,
    val mmap: Boolean,
    val sampleRateHz: Int,
    val channelCount: Int,
    val format: Int,
    val deviceId: Int,
    val framesPerBurst: Int,
    val bufferCapacityFrames: Int
)

/**
 * JNI bridge to `librhythm_native_audio.so` with safety-guarded loading and pure-Kotlin fallbacks
 * so JVM tests and hardened devices operate identically without native crashes.
 */
object NativeAudioBridge {
    private const val TAG = "NativeAudioBridge"

    @Volatile
    private var loadAttempted = false

    @Volatile
    var isLoaded: Boolean = false
        private set

    fun ensureLoaded(): Boolean {
        if (isLoaded) return true
        if (loadAttempted) return isLoaded
        synchronized(this) {
            if (isLoaded) return true
            if (loadAttempted) return isLoaded
            loadAttempted = true
            if (!NativeAudioSafetyGuard.canLoadNativeLibrary()) {
                return false
            }
            isLoaded = runCatching {
                System.loadLibrary("rhythm_native_audio")
                true
            }.onFailure { err ->
                Log.w(TAG, "Unable to load librhythm_native_audio.so; using Kotlin fallback", err)
            }.getOrDefault(false)
            return isLoaded
        }
    }

    fun probeAAudioDirectOutput(preferredChannels: Int = 2): AAudioDirectProbeResult {
        if (!ensureLoaded()) {
            return AAudioDirectProbeResult(
                available = false,
                statusCode = -1,
                exclusive = false,
                mmap = false,
                sampleRateHz = 0,
                channelCount = 0,
                format = 0,
                deviceId = 0,
                framesPerBurst = 0,
                bufferCapacityFrames = 0
            )
        }
        val raw = runCatching { nativeProbeAAudioDirectOutput(preferredChannels) }.getOrNull()
        if (raw == null || raw.size < 9) {
            return AAudioDirectProbeResult(false, -1, false, false, 0, 0, 0, 0, 0, 0)
        }
        return AAudioDirectProbeResult(
            available = raw[0] == 0,
            statusCode = raw[0],
            exclusive = raw[1] == 1,
            mmap = raw[2] == 1,
            sampleRateHz = raw[3],
            channelCount = raw[4],
            format = raw[5],
            deviceId = raw[6],
            framesPerBurst = raw[7],
            bufferCapacityFrames = raw[8]
        )
    }

    internal external fun nativeCreateDsdDecimator(channelCount: Int, decimationFactor: Int, lsbFirst: Boolean): Long
    internal external fun nativeProcessDsd(
        handle: Long,
        input: ByteArray,
        offset: Int,
        length: Int,
        planar: Boolean,
        blockBytesPerChannel: Int,
        outputPcm: FloatArray
    ): Int
    internal external fun nativeResetDsdDecimator(handle: Long)
    internal external fun nativeDestroyDsdDecimator(handle: Long)

    internal external fun nativeCreateSincResampler(channelCount: Int, inputRateHz: Int, outputRateHz: Int): Long
    internal external fun nativeProcessSincResampler(
        handle: Long,
        inputPcm: FloatArray,
        inputFrames: Int,
        outputPcm: FloatArray,
        maxOutputCapacityFrames: Int
    ): Int
    internal external fun nativeResetSincResampler(handle: Long)
    internal external fun nativeDestroySincResampler(handle: Long)

    internal external fun nativeProbeAAudioDirectOutput(preferredChannels: Int): IntArray
}

class NativeDsdDecimator(
    channelCount: Int,
    decimationFactor: Int,
    private val lsbFirst: Boolean
) : Closeable {
    val safeChannels: Int = channelCount.coerceIn(1, 8)
    val safeDecimationFactor: Int = when (decimationFactor) {
        8, 16, 32, 64, 128 -> decimationFactor
        else -> 16
    }

    private val nativeHandle: Long = if (NativeAudioBridge.ensureLoaded()) {
        runCatching {
            NativeAudioBridge.nativeCreateDsdDecimator(safeChannels, safeDecimationFactor, lsbFirst)
        }.getOrDefault(0L)
    } else {
        0L
    }

    val isUsingNative: Boolean
        get() = nativeHandle != 0L

    private val stepBytes: Int = safeDecimationFactor / 8
    private val filterBytes: Int = 8 * max(1, safeDecimationFactor / 16)
    private val fallbackHistory = ByteArray(safeChannels * filterBytes) { 0x55.toByte() }
    private val fallbackLut: FloatArray by lazy { buildFallbackLut() }
    private var ringWritePos = 0
    private var bytePhase = 0

    fun processToFloats(
        input: ByteArray,
        offset: Int,
        length: Int,
        planar: Boolean,
        blockBytesPerChannel: Int,
        outputPcm: FloatArray
    ): Int {
        if (length <= 0 || offset < 0 || offset + length > input.size) return 0
        if (nativeHandle != 0L) {
            return NativeAudioBridge.nativeProcessDsd(
                nativeHandle,
                input,
                offset,
                length,
                planar,
                blockBytesPerChannel,
                outputPcm
            )
        }
        return processFallback(input, offset, length, planar, blockBytesPerChannel, outputPcm)
    }

    fun reset() {
        if (nativeHandle != 0L) {
            NativeAudioBridge.nativeResetDsdDecimator(nativeHandle)
        }
        fallbackHistory.fill(0x55.toByte())
        ringWritePos = 0
        bytePhase = 0
    }

    override fun close() {
        if (nativeHandle != 0L) {
            NativeAudioBridge.nativeDestroyDsdDecimator(nativeHandle)
        }
    }

    private fun processFallback(
        input: ByteArray,
        offset: Int,
        length: Int,
        planar: Boolean,
        blockBytesPerChannel: Int,
        outputPcm: FloatArray
    ): Int {
        val frames = if (planar && blockBytesPerChannel > 0) blockBytesPerChannel else length / safeChannels
        var writtenSamples = 0
        for (i in 0 until frames) {
            for (ch in 0 until safeChannels) {
                val idx = if (planar && blockBytesPerChannel > 0) {
                    offset + ch * blockBytesPerChannel + i
                } else {
                    offset + i * safeChannels + ch
                }
                fallbackHistory[ch * filterBytes + ringWritePos] = if (idx < input.size) input[idx] else 0x55.toByte()
            }
            ringWritePos = (ringWritePos + 1) % filterBytes
            bytePhase++
            if (bytePhase >= stepBytes) {
                bytePhase = 0
                for (ch in 0 until safeChannels) {
                    if (writtenSamples < outputPcm.size) {
                        var acc = 0f
                        var ringIdx = ringWritePos
                        val base = ch * filterBytes
                        for (g in 0 until filterBytes) {
                            acc += fallbackLut[g * 256 + (fallbackHistory[base + ringIdx].toInt() and 0xFF)]
                            ringIdx = (ringIdx + 1) % filterBytes
                        }
                        outputPcm[writtenSamples++] = acc.coerceIn(-1f, 1f)
                    }
                }
            }
        }
        return writtenSamples
    }

    private fun buildFallbackLut(): FloatArray {
        val numTaps = filterBytes * 8
        val taps = DoubleArray(numTaps)
        val cutoff = 0.25 / safeDecimationFactor.toDouble()
        val beta = 9.0
        val i0Beta = besselI0(beta)
        val halfN = (numTaps - 1).toDouble() * 0.5
        var sum = 0.0
        for (i in 0 until numTaps) {
            val n = i.toDouble() - halfN
            val sinc = if (abs(n) < 1e-9) 2.0 * cutoff else sin(2.0 * PI * cutoff * n) / (PI * n)
            val ratio = n / halfN
            val win = besselI0(beta * sqrt(max(0.0, 1.0 - ratio * ratio))) / i0Beta
            taps[i] = sinc * win
            sum += taps[i]
        }
        val gain = if (sum > 1e-12) 0.70710678 / sum else 1.0
        for (i in 0 until numTaps) taps[i] *= gain

        val table = FloatArray(filterBytes * 256)
        for (g in 0 until filterBytes) {
            val tapBase = g * 8
            for (b in 0 until 256) {
                val ordered = if (lsbFirst) reverseBits(b) else b
                var acc = 0.0
                for (bit in 0 until 8) {
                    val sym = if (((ordered ushr (7 - bit)) and 1) != 0) 1.0 else -1.0
                    acc += sym * taps[tapBase + bit]
                }
                table[g * 256 + b] = acc.toFloat()
            }
        }
        return table
    }

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
            val r = halfX / k.toDouble()
            term *= r * r
            sum += term
            if (term < sum * 1e-15) break
        }
        return sum
    }
}

class NativeSincResampler(
    channelCount: Int,
    inputRateHz: Int,
    outputRateHz: Int
) : Closeable {
    val safeChannels: Int = channelCount.coerceIn(1, 8)
    val safeInputRate: Int = inputRateHz.coerceAtLeast(8_000)
    val safeOutputRate: Int = outputRateHz.coerceAtLeast(8_000)

    private val nativeHandle: Long = if (NativeAudioBridge.ensureLoaded()) {
        runCatching {
            NativeAudioBridge.nativeCreateSincResampler(safeChannels, safeInputRate, safeOutputRate)
        }.getOrDefault(0L)
    } else {
        0L
    }

    val isUsingNative: Boolean
        get() = nativeHandle != 0L

    private val halfWidth = 16
    private val windowTaps = halfWidth * 2
    private val ringSize = windowTaps + 4
    private val ring = FloatArray(ringSize * safeChannels)
    private var writePos = 0
    private var phase = 0.0
    private val stepInputPerOutput = safeInputRate.toDouble() / safeOutputRate.toDouble()
    private val cutoffScale = minOf(1.0, safeOutputRate.toDouble() / safeInputRate.toDouble()) * 0.92

    fun maxOutputFrames(inputFrames: Int): Int {
        val ratio = safeOutputRate.toDouble() / safeInputRate.toDouble()
        return (inputFrames.toDouble() * ratio).toInt() + 4
    }

    fun processInterleaved(inputPcm: FloatArray, inputFrames: Int, outputPcm: FloatArray): Int {
        if (inputFrames <= 0) return 0
        val maxCap = outputPcm.size / safeChannels
        if (maxCap <= 0) return 0
        if (nativeHandle != 0L) {
            return NativeAudioBridge.nativeProcessSincResampler(
                nativeHandle,
                inputPcm,
                inputFrames,
                outputPcm,
                maxCap
            )
        }
        return processFallback(inputPcm, inputFrames, outputPcm, maxCap)
    }

    fun reset() {
        if (nativeHandle != 0L) {
            NativeAudioBridge.nativeResetSincResampler(nativeHandle)
        }
        ring.fill(0f)
        writePos = 0
        phase = 0.0
    }

    override fun close() {
        if (nativeHandle != 0L) {
            NativeAudioBridge.nativeDestroySincResampler(nativeHandle)
        }
    }

    private fun processFallback(
        inputPcm: FloatArray,
        inputFrames: Int,
        outputPcm: FloatArray,
        maxCap: Int
    ): Int {
        if (safeInputRate == safeOutputRate) {
            val copyFrames = minOf(inputFrames, maxCap)
            inputPcm.copyInto(outputPcm, endIndex = copyFrames * safeChannels)
            return copyFrames
        }
        var outFrames = 0
        for (i in 0 until inputFrames) {
            for (ch in 0 until safeChannels) {
                ring[writePos * safeChannels + ch] = inputPcm[i * safeChannels + ch]
            }
            writePos = (writePos + 1) % ringSize
            while (phase < 1.0 && outFrames < maxCap) {
                for (ch in 0 until safeChannels) {
                    outputPcm[outFrames * safeChannels + ch] = interpolateFallback(ch, phase)
                }
                outFrames++
                phase += stepInputPerOutput
            }
            phase -= 1.0
        }
        return outFrames
    }

    private fun interpolateFallback(ch: Int, frac: Double): Float {
        val beta = 8.5
        val i0Beta = besselI0(beta)
        val oldestBase = (writePos + ringSize - windowTaps) % ringSize
        var sum = 0.0
        var wSum = 0.0
        for (tap in 0 until windowTaps) {
            val offset = tap.toDouble() - halfWidth.toDouble() - frac
            val normPos = offset / halfWidth.toDouble()
            if (abs(normPos) >= 1.0) continue
            val x = PI * cutoffScale * offset
            val sinc = if (abs(x) < 1e-9) cutoffScale else cutoffScale * sin(x) / x
            val win = besselI0(beta * sqrt(max(0.0, 1.0 - normPos * normPos))) / i0Beta
            val w = sinc * win
            val frameIdx = (oldestBase + tap) % ringSize
            sum += ring[frameIdx * safeChannels + ch].toDouble() * w
            wSum += w
        }
        val normalized = if (wSum > 1e-9) sum / wSum else 0.0
        return normalized.coerceIn(-1.0, 1.0).toFloat()
    }

    private fun besselI0(x: Double): Double {
        var sum = 1.0
        var term = 1.0
        val halfX = x * 0.5
        for (k in 1..20) {
            val r = halfX / k.toDouble()
            term *= r * r
            sum += term
            if (term < sum * 1e-12) break
        }
        return sum
    }
}
