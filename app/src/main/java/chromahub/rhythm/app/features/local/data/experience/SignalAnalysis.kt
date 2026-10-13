// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import androidx.annotation.Keep
import kotlin.math.*

@Keep
data class AudioFeatures(
    val version: Int = 1,
    val songKey: String,
    val pcmSha256: String,
    val sampleRate: Int,
    val channels: Int,
    val decodedDurationMs: Long,
    val rmsDb: Double,
    val peakDb: Double,
    val zeroCrossingRate: Double,
    val spectralBands: List<Double>,
    val waveform: List<Double>,
    val analyzedAt: Long = System.currentTimeMillis(),
    val sourceSignature: String = "",
)

/** Bounded streaming analysis: no complete PCM waveform is ever held in memory. */
class SignalAnalysis(private val sampleRate: Int, private val expectedDurationMs: Long) {
    private var samples = 0L
    private var squares = 0.0
    private var peak = 0.0
    private var crossings = 0L
    private var previous = 0.0
    private val waveEnergy = DoubleArray(256)
    private val waveCounts = LongArray(256)
    private val bands = DoubleArray(8)
    private val window = DoubleArray(1024)
    private var windowCount = 0
    private var nextWindowAt = 0L
    fun add(value: Double) {
        val v = if (value.isFinite()) value.coerceIn(-1.0, 1.0) else 0.0
        squares += v * v; peak = max(peak, abs(v))
        if (samples > 0 && v * previous < 0) crossings++
        previous = v
        val expectedSamples = (expectedDurationMs * sampleRate / 1000).coerceAtLeast(1)
        val bin = (samples * 256 / expectedSamples).coerceIn(0, 255).toInt()
        waveEnergy[bin] += v * v; waveCounts[bin]++
        if (samples >= nextWindowAt) {
            window[windowCount++] = v
            if (windowCount == window.size) { spectrum(); windowCount = 0; nextWindowAt = samples + sampleRate / 6 }
        }
        samples++
    }
    private fun spectrum() {
        val real = DoubleArray(window.size) { i -> window[i] * (0.5 - 0.5 * cos(2 * PI * i / (window.size - 1))) }
        val imaginary = DoubleArray(window.size)
        var j = 0
        for (i in 1 until real.size) {
            var bit = real.size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val v = real[i]; real[i] = real[j]; real[j] = v }
        }
        var length = 2
        while (length <= real.size) {
            val angle = -2 * PI / length
            for (start in real.indices step length) for (k in 0 until length / 2) {
                val even = start + k; val odd = even + length / 2
                val c = cos(angle * k); val s = sin(angle * k)
                val tr = real[odd] * c - imaginary[odd] * s
                val ti = real[odd] * s + imaginary[odd] * c
                real[odd] = real[even] - tr; imaginary[odd] = imaginary[even] - ti
                real[even] += tr; imaginary[even] += ti
            }
            length *= 2
        }
        val edges = doubleArrayOf(80.0, 160.0, 320.0, 640.0, 1280.0, 2560.0, 5120.0)
        for (i in 1 until real.size / 2) {
            val frequency = i.toDouble() * sampleRate / real.size
            val band = edges.indexOfFirst { frequency < it }.takeIf { it >= 0 } ?: 7
            bands[band] += real[i] * real[i] + imaginary[i] * imaginary[i]
        }
    }
    fun finish(key: String, hash: String, channels: Int): AudioFeatures {
        require(samples > 0)
        val total = bands.sum().coerceAtLeast(1e-20)
        fun db(value: Double) = 20 * log10(value.coerceAtLeast(1e-8))
        return AudioFeatures(songKey = key, pcmSha256 = hash, sampleRate = sampleRate, channels = channels,
            decodedDurationMs = samples * 1000 / sampleRate, rmsDb = db(sqrt(squares / samples)), peakDb = db(peak),
            zeroCrossingRate = crossings.toDouble() / samples, spectralBands = bands.map { it / total },
            waveform = waveEnergy.indices.map { if (waveCounts[it] == 0L) 0.0 else sqrt(waveEnergy[it] / waveCounts[it]) })
    }
    companion object {
        fun similarity(a: AudioFeatures?, b: AudioFeatures?): Double {
            if (a == null || b == null || a.version != b.version) return 0.0
            if (a.pcmSha256 == b.pcmSha256) return 1.0
            val spectral = a.spectralBands.zip(b.spectralBands).sumOf { (x, y) -> sqrt(x * y) }.coerceIn(0.0, 1.0)
            val energy = exp(-abs(a.rmsDb - b.rmsDb) / 12)
            val texture = exp(-abs(a.zeroCrossingRate - b.zeroCrossingRate) * 10)
            val pairs = a.waveform.zip(b.waveform)
            val meanA = pairs.map { it.first }.average().takeIf { it.isFinite() } ?: 0.0
            val meanB = pairs.map { it.second }.average().takeIf { it.isFinite() } ?: 0.0
            val varianceA = pairs.sumOf { (x, _) -> (x - meanA).pow(2) }
            val varianceB = pairs.sumOf { (_, y) -> (y - meanB).pow(2) }
            val envelope = if (varianceA * varianceB <= 1e-20) .5 else
                ((pairs.sumOf { (x, y) -> (x - meanA) * (y - meanB) } / sqrt(varianceA * varianceB)).coerceIn(-1.0, 1.0) + 1) / 2
            return (spectral * .5 + energy * .2 + texture * .15 + envelope * .15).coerceIn(0.0, 1.0)
        }
    }
}
