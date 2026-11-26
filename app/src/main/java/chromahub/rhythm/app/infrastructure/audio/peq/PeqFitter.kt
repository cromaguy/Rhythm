/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.peq

import chromahub.rhythm.app.shared.data.model.AutoEQProfile
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Levenberg-Marquardt parametric EQ optimizer fitting arbitrary target/headphone response curves
 * onto up to 10 RBJ filters (fixed 105 Hz low shelf + 8 free peaking filters + free high shelf)
 * plus automatic negative preamp headroom.
 */
object PeqFitter {
    private const val PEAKS = 8
    private const val MIN_PK_FC = 20.0
    private const val MAX_PK_FC = 10_000.0
    private const val MIN_Q = 0.18248
    private const val MAX_Q = 6.0
    private const val MAX_GAIN = 20.0
    private const val SHELF_FC = 105.0
    private const val SHELF_Q = 0.7
    private const val HS_MIN_FC = 5_000.0
    private const val HS_MAX_FC = 12_000.0
    private const val HS_MIN_Q = 0.4
    private const val HS_MAX_Q = 0.7

    /**
     * Evaluates a desired correction curve on [PeqMath.LOG_GRID] from a 10-band AutoEQ/graphic profile,
     * optionally adding custom target deltas, bass shelf boost, and 1 kHz spectral tilt.
     */
    fun curveFrom10BandProfile(
        bandGainsDb: List<Float>,
        bandFrequenciesHz: List<Int> = AutoEQProfile.BAND_FREQUENCIES,
        targetDeltaDb: DoubleArray? = null,
        bassGainDb: Double = 0.0,
        tiltDbPerOctave: Double = 0.0,
        grid: DoubleArray = PeqMath.LOG_GRID
    ): DoubleArray {
        val pairs = bandFrequenciesHz.indices
            .mapNotNull { idx ->
                val gain = bandGainsDb.getOrNull(idx)?.toDouble()?.takeIf { it.isFinite() } ?: return@mapNotNull null
                bandFrequenciesHz[idx].toDouble() to gain
            }
            .sortedBy { it.first }
        if (pairs.isEmpty()) return DoubleArray(grid.size)

        val logFreqs = DoubleArray(pairs.size) { ln(pairs[it].first) }
        val gains = DoubleArray(pairs.size) { pairs[it].second }

        return DoubleArray(grid.size) { i ->
            val f = grid[i]
            val lf = ln(f)
            val baseGain = when {
                lf <= logFreqs.first() -> gains.first()
                lf >= logFreqs.last() -> gains.last()
                else -> {
                    var idx = 0
                    while (idx < logFreqs.lastIndex && logFreqs[idx + 1] < lf) idx++
                    val span = (logFreqs[idx + 1] - logFreqs[idx]).coerceAtLeast(1e-6)
                    val t = (lf - logFreqs[idx]) / span
                    gains[idx] + (gains[idx + 1] - gains[idx]) * t
                }
            }
            val extraTarget = targetDeltaDb?.getOrNull(i) ?: 0.0
            val bass = if (abs(bassGainDb) >= 0.01) PeqMath.bassShelfDb(bassGainDb, f) else 0.0
            val tilt = if (abs(tiltDbPerOctave) >= 0.01) PeqMath.tiltDb(tiltDbPerOctave, f) else 0.0
            (baseGain + extraTarget + bass + tilt).coerceIn(-MAX_GAIN, MAX_GAIN)
        }
    }

    /** Direct 1:1 10-band peaking mapping with auto-preamp (fast synchronous update for manual sliders). */
    fun from10BandDirect(
        bandGainsDb: List<Float>,
        bandFrequenciesHz: List<Int> = AutoEQProfile.BAND_FREQUENCIES,
        sourceName: String? = null
    ): PeqSettings {
        val filters = bandFrequenciesHz.mapIndexedNotNull { idx, fc ->
            val g = bandGainsDb.getOrNull(idx)?.toDouble()?.takeIf { it.isFinite() } ?: 0.0
            PeqFilter(
                type = PeqFilterType.PEAKING,
                frequencyHz = fc.toDouble(),
                gainDb = g.coerceIn(-15.0, 15.0),
                q = 1.414,
                enabled = true
            )
        }
        return PeqSettings(
            preampDb = PeqMath.autoPreampDb(filters),
            filters = filters,
            sourceName = sourceName
        )
    }

    /** Fits [desiredDb] over [grid] into parametric EQ filters via Levenberg-Marquardt. */
    fun fit(
        desiredDb: DoubleArray,
        grid: DoubleArray = PeqMath.LOG_GRID,
        iterations: Int = 80,
        sourceName: String? = null
    ): PeqSettings {
        require(desiredDb.size == grid.size) { "curve/grid size mismatch" }
        val weights = DoubleArray(grid.size) { i ->
            when {
                grid[i] > 16_000.0 -> 0.0
                grid[i] > MAX_PK_FC -> 0.35
                else -> 1.0
            }
        }
        var params = initialGuess(desiredDb, grid)
        var lambda = 1e-2
        var error = cost(params, desiredDb, grid, weights)

        repeat(iterations) {
            val residual = residuals(params, desiredDb, grid, weights)
            val jacobian = jacobian(params, desiredDb, grid, weights, residual)
            val n = params.size
            val jtj = Array(n) { DoubleArray(n) }
            val jtr = DoubleArray(n)
            for (k in grid.indices) {
                for (a in 0 until n) {
                    val ja = jacobian[k][a]
                    if (ja == 0.0) continue
                    jtr[a] += ja * residual[k]
                    for (b in 0 until n) jtj[a][b] += ja * jacobian[k][b]
                }
            }
            var improved = false
            var attempts = 0
            while (!improved && attempts < 6) {
                val system = Array(n) { a ->
                    DoubleArray(n) { b ->
                        jtj[a][b] + if (a == b) lambda * (jtj[a][a] + 1e-6) else 0.0
                    }
                }
                val step = solve(system, DoubleArray(n) { -jtr[it] }) ?: break
                val candidate = clampParams(DoubleArray(n) { params[it] + step[it] })
                val candidateError = cost(candidate, desiredDb, grid, weights)
                if (candidateError < error) {
                    params = candidate
                    error = candidateError
                    lambda = (lambda * 0.4).coerceAtLeast(1e-7)
                    improved = true
                } else {
                    lambda = (lambda * 4.0).coerceAtMost(1e6)
                }
                attempts++
            }
            if (!improved) return@repeat
        }

        val filters = toFilters(params)
        val settings = PeqSettings(
            preampDb = PeqMath.autoPreampDb(filters, grid),
            filters = filters,
            sourceName = sourceName
        )
        return settings.copy(fitRmsErrorDb = rmsError(settings, desiredDb, grid))
    }

    /** Root-mean-square error (dB, up to 10 kHz) between fitted filters and target curve. */
    fun rmsError(settings: PeqSettings, desiredDb: DoubleArray, grid: DoubleArray = PeqMath.LOG_GRID): Double {
        var sum = 0.0
        var count = 0
        for (i in grid.indices) {
            if (grid[i] > MAX_PK_FC) continue
            val d = PeqMath.filtersDb(settings.filters, grid[i]) - desiredDb[i]
            sum += d * d
            count++
        }
        return sqrt(sum / count.coerceAtLeast(1))
    }

    private fun toFilters(p: DoubleArray): List<PeqFilter> = buildList {
        add(PeqFilter(PeqFilterType.LOW_SHELF, SHELF_FC, round1(p[0]), SHELF_Q))
        for (i in 0 until PEAKS) {
            val base = 4 + i * 3
            add(
                PeqFilter(
                    type = PeqFilterType.PEAKING,
                    frequencyHz = roundFc(exp(p[base])),
                    gainDb = round1(p[base + 2]),
                    q = round2(exp(p[base + 1]))
                )
            )
        }
        add(PeqFilter(PeqFilterType.HIGH_SHELF, roundFc(exp(p[1])), round1(p[3]), round2(exp(p[2]))))
    }

    private fun responseAt(p: DoubleArray, f: Double): Double {
        var total = PeqMath.filterDb(PeqFilter(PeqFilterType.LOW_SHELF, SHELF_FC, p[0], SHELF_Q), f)
        total += PeqMath.filterDb(PeqFilter(PeqFilterType.HIGH_SHELF, exp(p[1]), p[3], exp(p[2])), f)
        for (i in 0 until PEAKS) {
            val base = 4 + i * 3
            total += PeqMath.filterDb(PeqFilter(PeqFilterType.PEAKING, exp(p[base]), p[base + 2], exp(p[base + 1])), f)
        }
        return total
    }

    private fun residuals(p: DoubleArray, desired: DoubleArray, grid: DoubleArray, w: DoubleArray): DoubleArray =
        DoubleArray(grid.size) { i -> if (w[i] == 0.0) 0.0 else (responseAt(p, grid[i]) - desired[i]) * w[i] }

    private fun cost(p: DoubleArray, desired: DoubleArray, grid: DoubleArray, w: DoubleArray): Double =
        residuals(p, desired, grid, w).sumOf { it * it }

    private fun jacobian(
        p: DoubleArray,
        desired: DoubleArray,
        grid: DoubleArray,
        w: DoubleArray,
        base: DoubleArray
    ): Array<DoubleArray> {
        val n = p.size
        val j = Array(grid.size) { DoubleArray(n) }
        for (a in 0 until n) {
            val h = if (isGain(a)) 1e-3 else 1e-4
            val shifted = p.copyOf()
            shifted[a] += h
            val r = residuals(shifted, desired, grid, w)
            for (k in grid.indices) j[k][a] = (r[k] - base[k]) / h
        }
        return j
    }

    private fun isGain(index: Int): Boolean = index == 0 || index == 3 || (index >= 4 && (index - 4) % 3 == 2)

    private fun clampParams(p: DoubleArray): DoubleArray {
        val out = p.copyOf()
        out[0] = out[0].coerceIn(-MAX_GAIN, MAX_GAIN)
        out[1] = out[1].coerceIn(ln(HS_MIN_FC), ln(HS_MAX_FC))
        out[2] = out[2].coerceIn(ln(HS_MIN_Q), ln(HS_MAX_Q))
        out[3] = out[3].coerceIn(-MAX_GAIN, MAX_GAIN)
        for (i in 0 until PEAKS) {
            val b = 4 + i * 3
            out[b] = out[b].coerceIn(ln(MIN_PK_FC), ln(MAX_PK_FC))
            out[b + 1] = out[b + 1].coerceIn(ln(MIN_Q), ln(MAX_Q))
            out[b + 2] = out[b + 2].coerceIn(-MAX_GAIN, MAX_GAIN)
        }
        return out
    }

    private fun initialGuess(desired: DoubleArray, grid: DoubleArray): DoubleArray {
        val p = DoubleArray(4 + PEAKS * 3)
        val lowIdx = grid.indices.filter { grid[it] <= 60.0 }
        p[0] = (lowIdx.map { desired[it] }.average().takeIf { it.isFinite() } ?: 0.0).coerceIn(-12.0, 12.0)
        p[1] = ln(10_000.0)
        p[2] = ln(0.7)
        val highIdx = grid.indices.filter { grid[it] in 11_000.0..16_000.0 }
        p[3] = (highIdx.map { desired[it] }.average().takeIf { it.isFinite() } ?: 0.0).coerceIn(-12.0, 12.0)
        val current = DoubleArray(grid.size) { i -> responseAt(p, grid[i]) }
        for (i in 0 until PEAKS) {
            var bestIndex = -1
            var bestValue = 0.0
            for (k in grid.indices) {
                if (grid[k] < MIN_PK_FC || grid[k] > MAX_PK_FC) continue
                val r = desired[k] - current[k]
                if (abs(r) > abs(bestValue)) {
                    bestValue = r
                    bestIndex = k
                }
            }
            val b = 4 + i * 3
            if (bestIndex < 0 || abs(bestValue) < 0.05) {
                p[b] = ln(1000.0 * (i + 1))
                p[b + 1] = ln(1.0)
                p[b + 2] = 0.0
                continue
            }
            p[b] = ln(grid[bestIndex])
            p[b + 1] = ln(1.4)
            p[b + 2] = bestValue.coerceIn(-MAX_GAIN, MAX_GAIN)
            val added = PeqFilter(PeqFilterType.PEAKING, grid[bestIndex], p[b + 2], 1.4)
            for (k in grid.indices) current[k] += PeqMath.filterDb(added, grid[k])
        }
        return clampParams(p)
    }

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { a[it].copyOf() }
        val v = b.copyOf()
        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) if (abs(m[row][col]) > abs(m[pivot][col])) pivot = row
            if (abs(m[pivot][col]) < 1e-12) return null
            if (pivot != col) {
                val t = m[pivot]; m[pivot] = m[col]; m[col] = t
                val tv = v[pivot]; v[pivot] = v[col]; v[col] = tv
            }
            for (row in col + 1 until n) {
                val factor = m[row][col] / m[col][col]
                if (factor == 0.0) continue
                for (k in col until n) m[row][k] -= factor * m[col][k]
                v[row] -= factor * v[col]
            }
        }
        val x = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var sum = v[row]
            for (k in row + 1 until n) sum -= m[row][k] * x[k]
            x[row] = sum / m[row][row]
        }
        return x
    }

    private fun round1(v: Double) = PeqMath.roundTo(v, 0.1)
    private fun round2(v: Double) = PeqMath.roundTo(v, 0.01)
    private fun roundFc(v: Double) = PeqMath.roundTo(v, 1.0)
}
