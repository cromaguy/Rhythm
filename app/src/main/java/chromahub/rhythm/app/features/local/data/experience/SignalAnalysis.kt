// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import androidx.annotation.Keep
import kotlin.math.*

@Keep
data class AudioFeatures(
    val version: Int = 2,
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
    val estimatedBpm: Double? = null,
    val tempoConfidence: Double = 0.0,
    val estimatedKey: String? = null,
    val keyConfidence: Double = 0.0,
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
    private val chroma = DoubleArray(12)
    private val envelope = ArrayList<Double>()
    private var envelopeSquares = 0.0
    private var envelopeCount = 0
    fun add(value: Double) {
        val v = if (value.isFinite()) value.coerceIn(-1.0, 1.0) else 0.0
        squares += v * v; peak = max(peak, abs(v))
        envelopeSquares += v * v; envelopeCount++
        if (envelopeCount >= (sampleRate / 50).coerceAtLeast(1)) {
            if (envelope.size < 60_000) envelope += sqrt(envelopeSquares / envelopeCount)
            envelopeSquares = 0.0; envelopeCount = 0
        }
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
            if (frequency in 65.0..2000.0) {
                val midi = (69 + 12 * log2(frequency / 440)).roundToInt()
                chroma[((midi % 12) + 12) % 12] += sqrt(real[i] * real[i] + imaginary[i] * imaginary[i])
            }
        }
    }
    fun finish(key: String, hash: String, channels: Int): AudioFeatures {
        require(samples > 0)
        val total = bands.sum().coerceAtLeast(1e-20)
        fun db(value: Double) = 20 * log10(value.coerceAtLeast(1e-8))
        val tempo = tempo(envelope)
        val keyEstimate = SignalAnalysis.key(chroma.toList())
        return AudioFeatures(songKey = key, pcmSha256 = hash, sampleRate = sampleRate, channels = channels,
            decodedDurationMs = samples * 1000 / sampleRate, rmsDb = db(sqrt(squares / samples)), peakDb = db(peak),
            zeroCrossingRate = crossings.toDouble() / samples, spectralBands = bands.map { it / total },
            waveform = waveEnergy.indices.map { if (waveCounts[it] == 0L) 0.0 else sqrt(waveEnergy[it] / waveCounts[it]) },
            estimatedBpm = tempo.first, tempoConfidence = tempo.second,
            estimatedKey = keyEstimate.first, keyConfidence = keyEstimate.second)
    }
    companion object {
        /** These are DSP estimates, not authoritative tags or neural audio embeddings. */
        fun tempo(envelope: List<Double>): Pair<Double?, Double> {
            if (envelope.size < 400) return null to 0.0
            val onset = envelope.zipWithNext { a, b -> (b - a).coerceAtLeast(0.0) }
            val power = onset.sumOf { it * it }; if (power < 1e-8) return null to 0.0
            val scores = (15..50).associateWith { lag ->
                var sum = 0.0; var before = 0.0; var after = 0.0
                for (i in lag until onset.size) { sum += onset[i] * onset[i - lag]; before += onset[i] * onset[i]; after += onset[i - lag] * onset[i - lag] }
                sum / sqrt(before * after).coerceAtLeast(1e-12)
            }
            val best = scores.maxBy { it.value }; val confidence = best.value.coerceIn(0.0, 1.0)
            return if (confidence < .25) null to confidence else (3000.0 / best.key) to confidence
        }
        fun key(chroma: List<Double>): Pair<String?, Double> {
            if (chroma.size != 12 || chroma.count { it > 1e-6 } < 4) return null to 0.0
            val major = listOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
            val minor = listOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
            fun correlation(template: List<Double>, shift: Int): Double {
                val a = chroma.average(); val b = template.average()
                val denominator = sqrt(chroma.sumOf { (it - a).pow(2) } * template.sumOf { (it - b).pow(2) })
                return if (denominator < 1e-10) 0.0 else chroma.indices.sumOf { (chroma[it] - a) * (template[(it - shift + 12) % 12] - b) } / denominator
            }
            val scores = (0..11).flatMap { note -> listOf(Triple(note, "major", correlation(major, note)), Triple(note, "minor", correlation(minor, note))) }.sortedByDescending { it.third }
            val best = scores.first(); val confidence = ((best.third - scores[1].third) * 4).coerceIn(0.0, 1.0)
            val notes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            return if (best.third < .5 || confidence < .1) null to confidence else "${notes[best.first]} ${best.second}" to confidence
        }
        fun similarity(a: AudioFeatures?, b: AudioFeatures?): Double {
            if (a == null || b == null || a.version !in 1..2 || b.version !in 1..2) return 0.0
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
