/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio.viper.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Iterative in-place radix-2 Cooley-Tukey FFT / IFFT in 64-bit Double precision.
 * Zero allocations during [fft] / [ifft] execution once initialized for power-of-two size [n].
 */
class FftProcessor(val n: Int) {
    init {
        require(n >= 2 && (n and (n - 1)) == 0) { "FFT size must be a power of 2 >= 2, got $n" }
    }

    private val bits: Int = Integer.numberOfTrailingZeros(n)
    private val bitRev = IntArray(n) { i ->
        var x = i
        var r = 0
        for (b in 0 until bits) {
            r = (r shl 1) or (x and 1)
            x = x ushr 1
        }
        r
    }
    private val cosTable = DoubleArray(n / 2) { k -> cos(2.0 * PI * k / n) }
    private val sinTable = DoubleArray(n / 2) { k -> sin(2.0 * PI * k / n) }

    fun fft(real: DoubleArray, imag: DoubleArray) {
        transform(real, imag, inverse = false)
    }

    fun ifft(real: DoubleArray, imag: DoubleArray) {
        transform(real, imag, inverse = true)
        val invN = 1.0 / n.toDouble()
        for (i in 0 until n) {
            real[i] *= invN
            imag[i] *= invN
        }
    }

    private fun transform(real: DoubleArray, imag: DoubleArray, inverse: Boolean) {
        for (i in 0 until n) {
            val j = bitRev[i]
            if (j > i) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var tableIdx = 0
                for (k in start until start + half) {
                    val wr = cosTable[tableIdx]
                    val wi = if (inverse) sinTable[tableIdx] else -sinTable[tableIdx]
                    val l = k + half
                    val tr = wr * real[l] - wi * imag[l]
                    val ti = wr * imag[l] + wi * real[l]
                    real[l] = real[k] - tr
                    imag[l] = imag[k] - ti
                    real[k] += tr
                    imag[k] += ti
                    tableIdx += step
                }
                start += size
            }
            size = size shl 1
        }
    }
}
