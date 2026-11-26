/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.viper.dsp

import android.content.Context
import android.net.Uri
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Hybrid FIR / Partitioned Overlap-Add FFT Impulse Response (IRS) Convolver for ViPER4Rhythm.
 *
 * - Built-in 64-tap analog presets execute via zero-latency direct time-domain FIR convolution.
 * - Custom `.wav` / `.irs` impulses longer than 64 taps automatically switch to uniform-block
 *   partitioned FFT overlap-add (`B = 256`, `N = 512`) powered by [FftProcessor], supporting arbitrary
 *   room/headphone impulse lengths in real time with zero steady-state allocations.
 */
class FastConvolver {

    companion object {
        const val PRESET_STUDIO_MASTER = "Studio Master"
        const val PRESET_WARM_TUBE = "Warm Tube"
        const val PRESET_ANALOG_TAPE = "Analog Tape"
        const val PRESET_ACOUSTIC_ROOM = "Acoustic Room"
        const val PRESET_CUSTOM_IR = "Custom IR"

        const val FFT_BLOCK_SIZE = 256
        const val FFT_SIZE = FFT_BLOCK_SIZE * 2

        val BUILTIN_PRESETS = listOf(
            PRESET_STUDIO_MASTER,
            PRESET_WARM_TUBE,
            PRESET_ANALOG_TAPE,
            PRESET_ACOUSTIC_ROOM
        )
    }

    private var kernelL: FloatArray = FloatArray(64)
    private var kernelR: FloatArray = FloatArray(64)
    private var historyL = FloatArray(64)
    private var historyR = FloatArray(64)
    private var historyIndex = 0

    @Volatile
    private var useFftMode: Boolean = false
    private val fft = FftProcessor(FFT_SIZE)
    private var numPartitions = 0
    private var irSpecRealL: Array<DoubleArray> = emptyArray()
    private var irSpecImagL: Array<DoubleArray> = emptyArray()
    private var irSpecRealR: Array<DoubleArray> = emptyArray()
    private var irSpecImagR: Array<DoubleArray> = emptyArray()
    private var fdlRealL: Array<DoubleArray> = emptyArray()
    private var fdlImagL: Array<DoubleArray> = emptyArray()
    private var fdlRealR: Array<DoubleArray> = emptyArray()
    private var fdlImagR: Array<DoubleArray> = emptyArray()
    private var fdlHead = 0

    private val blockInL = DoubleArray(FFT_BLOCK_SIZE)
    private val blockInR = DoubleArray(FFT_BLOCK_SIZE)
    private val blockOutL = DoubleArray(FFT_BLOCK_SIZE)
    private val blockOutR = DoubleArray(FFT_BLOCK_SIZE)
    private val overlapL = DoubleArray(FFT_BLOCK_SIZE)
    private val overlapR = DoubleArray(FFT_BLOCK_SIZE)
    private val accumRealL = DoubleArray(FFT_SIZE)
    private val accumImagL = DoubleArray(FFT_SIZE)
    private val accumRealR = DoubleArray(FFT_SIZE)
    private val accumImagR = DoubleArray(FFT_SIZE)
    private val workReal = DoubleArray(FFT_SIZE)
    private val workImag = DoubleArray(FFT_SIZE)
    private var blockPos = 0

    @Volatile
    var mixRatio: Float = 1.0f
        private set

    @Volatile
    var activeIrTapCount: Int = 64
        private set

    init {
        loadPreset(PRESET_STUDIO_MASTER, 44100)
    }

    @Synchronized
    fun reset() {
        historyL.fill(0f)
        historyR.fill(0f)
        historyIndex = 0
        blockInL.fill(0.0)
        blockInR.fill(0.0)
        blockOutL.fill(0.0)
        blockOutR.fill(0.0)
        overlapL.fill(0.0)
        overlapR.fill(0.0)
        for (arr in fdlRealL) arr.fill(0.0)
        for (arr in fdlImagL) arr.fill(0.0)
        for (arr in fdlRealR) arr.fill(0.0)
        for (arr in fdlImagR) arr.fill(0.0)
        fdlHead = 0
        blockPos = 0
    }

    @Synchronized
    fun setMix(mix: Float) {
        mixRatio = mix.coerceIn(0f, 1f)
    }

    @Synchronized
    fun loadFromUri(context: Context, uriString: String?, fallbackPreset: String, sampleRate: Int, mix: Float = 1.0f): Boolean {
        setMix(mix)
        if (!uriString.isNullOrBlank()) {
            val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            val parsed = if (uri != null) WavIrParser.loadFromUri(context, uri) else null
            if (parsed != null && parsed.left.isNotEmpty()) {
                loadCustomImpulse(parsed.left, parsed.right, mix)
                return true
            }
        }
        loadPreset(fallbackPreset, sampleRate)
        return false
    }

    @Synchronized
    fun loadCustomImpulse(leftIr: FloatArray, rightIr: FloatArray = leftIr, mix: Float = this.mixRatio) {
        setMix(mix)
        val len = min(leftIr.size, rightIr.size).coerceAtLeast(1)
        activeIrTapCount = len

        if (len <= 64) {
            useFftMode = false
            kernelL = leftIr.copyOf(len)
            kernelR = rightIr.copyOf(len)
            historyL = FloatArray(len)
            historyR = FloatArray(len)
            historyIndex = 0
            return
        }

        val partitions = ceil(len.toDouble() / FFT_BLOCK_SIZE).toInt().coerceAtLeast(1)
        val specRL = Array(partitions) { DoubleArray(FFT_SIZE) }
        val specIL = Array(partitions) { DoubleArray(FFT_SIZE) }
        val specRR = Array(partitions) { DoubleArray(FFT_SIZE) }
        val specIR = Array(partitions) { DoubleArray(FFT_SIZE) }

        for (p in 0 until partitions) {
            val start = p * FFT_BLOCK_SIZE
            val end = min(start + FFT_BLOCK_SIZE, len)
            val reL = specRL[p]
            val imL = specIL[p]
            val reR = specRR[p]
            val imR = specIR[p]
            for (i in start until end) {
                reL[i - start] = leftIr[i].toDouble()
                reR[i - start] = rightIr[i].toDouble()
            }
            fft.fft(reL, imL)
            fft.fft(reR, imR)
        }

        irSpecRealL = specRL
        irSpecImagL = specIL
        irSpecRealR = specRR
        irSpecImagR = specIR
        fdlRealL = Array(partitions) { DoubleArray(FFT_SIZE) }
        fdlImagL = Array(partitions) { DoubleArray(FFT_SIZE) }
        fdlRealR = Array(partitions) { DoubleArray(FFT_SIZE) }
        fdlImagR = Array(partitions) { DoubleArray(FFT_SIZE) }
        numPartitions = partitions
        useFftMode = true
        reset()
    }

    @Synchronized
    fun loadPreset(name: String, sampleRate: Int) {
        val size = 64
        val safeFs = sampleRate.coerceAtLeast(8000).toFloat()
        val newKernel = FloatArray(size)

        when (name) {
            PRESET_WARM_TUBE -> {
                for (i in 0 until size) {
                    val t = i.toFloat() / safeFs
                    val decay = exp(-t * 2200f)
                    val osc = cos(2.0 * PI * 180.0 * t).toFloat()
                    newKernel[i] = if (i == 0) 0.85f else 0.15f * decay * osc
                }
            }
            PRESET_ANALOG_TAPE -> {
                for (i in 0 until size) {
                    val t = i.toFloat() / safeFs
                    val decay = exp(-t * 3500f)
                    val osc = sin(2.0 * PI * 80.0 * t).toFloat()
                    newKernel[i] = if (i == 0) 0.90f else 0.10f * decay * osc
                }
            }
            PRESET_ACOUSTIC_ROOM -> {
                for (i in 0 until size) {
                    val t = i.toFloat() / safeFs
                    val decay = exp(-t * 1800f)
                    val reflection = (if (i % 7 == 0) 0.15f else 0.03f) * (if (i % 2 == 0) 1f else -1f)
                    newKernel[i] = if (i == 0) 0.80f else reflection * decay
                }
            }
            else -> {
                for (i in 0 until size) {
                    val t = i.toFloat() / safeFs
                    val decay = exp(-t * 5000f)
                    newKernel[i] = if (i == 0) 0.94f else 0.06f * decay
                }
            }
        }

        var sum = 0f
        for (v in newKernel) sum += abs(v)
        if (sum > 0.0001f) {
            val scale = 1.0f / sum
            for (i in newKernel.indices) newKernel[i] *= scale
        }

        useFftMode = false
        activeIrTapCount = size
        kernelL = newKernel
        kernelR = newKernel
        historyL = FloatArray(size)
        historyR = FloatArray(size)
        historyIndex = 0
    }

    var outLeft: Float = 0.0f
        private set
    var outRight: Float = 0.0f
        private set

    fun process(left: Float, right: Float, crossChannel: Float) {
        val wetL: Float
        val wetR: Float
        if (useFftMode && numPartitions > 0) {
            blockInL[blockPos] = left.toDouble()
            blockInR[blockPos] = right.toDouble()
            wetL = blockOutL[blockPos].toFloat()
            wetR = blockOutR[blockPos].toFloat()
            blockPos++
            if (blockPos >= FFT_BLOCK_SIZE) {
                convolveFftBlock()
                blockPos = 0
            }
        } else {
            val size = kernelL.size
            historyL[historyIndex] = left
            historyR[historyIndex] = right

            var convL = 0.0f
            var convR = 0.0f
            var kIdx = 0
            var hIdx = historyIndex
            while (kIdx < size) {
                convL += historyL[hIdx] * kernelL[kIdx]
                convR += historyR[hIdx] * kernelR[kIdx]
                hIdx--
                if (hIdx < 0) hIdx = size - 1
                kIdx++
            }
            historyIndex++
            if (historyIndex >= size) historyIndex = 0
            wetL = convL
            wetR = convR
        }

        val mix = mixRatio
        val mixedL = left * (1f - mix) + wetL * mix
        val mixedR = right * (1f - mix) + wetR * mix
        val cc = crossChannel.coerceIn(0f, 1f)
        outLeft = mixedL * (1.0f - cc) + mixedR * cc
        outRight = mixedR * (1.0f - cc) + mixedL * cc
    }

    private fun convolveFftBlock() {
        val partitions = numPartitions
        val head = fdlHead

        workReal.fill(0.0)
        workImag.fill(0.0)
        System.arraycopy(blockInL, 0, workReal, 0, FFT_BLOCK_SIZE)
        fft.fft(workReal, workImag)
        System.arraycopy(workReal, 0, fdlRealL[head], 0, FFT_SIZE)
        System.arraycopy(workImag, 0, fdlImagL[head], 0, FFT_SIZE)

        workReal.fill(0.0)
        workImag.fill(0.0)
        System.arraycopy(blockInR, 0, workReal, 0, FFT_BLOCK_SIZE)
        fft.fft(workReal, workImag)
        System.arraycopy(workReal, 0, fdlRealR[head], 0, FFT_SIZE)
        System.arraycopy(workImag, 0, fdlImagR[head], 0, FFT_SIZE)

        accumRealL.fill(0.0)
        accumImagL.fill(0.0)
        accumRealR.fill(0.0)
        accumImagR.fill(0.0)

        for (p in 0 until partitions) {
            val histIdx = (head - p + partitions) % partitions
            val xRL = fdlRealL[histIdx]
            val xIL = fdlImagL[histIdx]
            val hRL = irSpecRealL[p]
            val hIL = irSpecImagL[p]

            val xRR = fdlRealR[histIdx]
            val xIR = fdlImagR[histIdx]
            val hRR = irSpecRealR[p]
            val hIR = irSpecImagR[p]

            for (k in 0 until FFT_SIZE) {
                accumRealL[k] += xRL[k] * hRL[k] - xIL[k] * hIL[k]
                accumImagL[k] += xRL[k] * hIL[k] + xIL[k] * hRL[k]
                accumRealR[k] += xRR[k] * hRR[k] - xIR[k] * hIR[k]
                accumImagR[k] += xRR[k] * hIR[k] + xIR[k] * hRR[k]
            }
        }

        fft.ifft(accumRealL, accumImagL)
        fft.ifft(accumRealR, accumImagR)

        for (i in 0 until FFT_BLOCK_SIZE) {
            blockOutL[i] = accumRealL[i] + overlapL[i]
            overlapL[i] = accumRealL[FFT_BLOCK_SIZE + i]
            blockOutR[i] = accumRealR[i] + overlapR[i]
            overlapR[i] = accumRealR[FFT_BLOCK_SIZE + i]
        }

        fdlHead = (head + 1) % partitions
    }
}
