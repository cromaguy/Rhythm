// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.features.local.data.experience

import kotlin.math.*

/** Compact DSP descriptors; explicitly not a downloaded or trained neural embedding model. */
object AudioVectors {
    fun describe(feature: AudioFeatures): List<Double> = normalize(
        feature.spectralBands.take(8).map { sqrt(it.coerceAtLeast(0.0)) } +
            listOf(((feature.rmsDb + 80) / 80).coerceIn(0.0, 1.0),
                (feature.zeroCrossingRate * 4).coerceIn(0.0, 1.0), ((feature.peakDb - feature.rmsDb) / 30).coerceIn(0.0, 1.0)) +
            feature.waveform.chunked(16).take(16).map { it.average() }.let { it + List((16 - it.size).coerceAtLeast(0)) { 0.0 } })
    fun normalize(vector: List<Double>): List<Double> {
        val norm = sqrt(vector.sumOf { it * it }); return if (norm <= 1e-12) vector.map { 0.0 } else vector.map { it / norm }
    }
    fun cosine(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        val denominator = sqrt(a.sumOf { it * it } * b.sumOf { it * it })
        return if (denominator <= 1e-12) 0.0 else (a.zip(b).sumOf { (x, y) -> x * y } / denominator).coerceIn(0.0, 1.0)
    }
    fun taste(features: List<Pair<AudioFeatures, Double>>): List<Double>? {
        val samples = features.filter { it.second.isFinite() && it.second > 0 }.map { describe(it.first) to it.second }
        val dimension = samples.firstOrNull()?.first?.size ?: return null
        val compatible = samples.filter { it.first.size == dimension }; val total = compatible.sumOf { it.second }
        if (total <= 0) return null
        return normalize(List(dimension) { i -> compatible.sumOf { it.first[i] * it.second } / total })
    }
}
