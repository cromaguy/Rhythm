/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.service.coordinator

import kotlin.math.ln

/**
 * Pure math & DSP band-mapping coordinator extracted from [MediaPlaybackService] so
 * equalizer band interpolation, frequency logarithmic mapping, and clamp logic remain
 * decoupled from Android framework lifecycle plumbing.
 */
object AudioEffectsCoordinator {

    val STANDARD_10_BAND_FREQS_HZ = floatArrayOf(
        31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
    )

    fun interpolateBands(
        inputLevels: FloatArray,
        outputBands: Int,
        hardwareCenterFreqsHz: FloatArray? = null
    ): FloatArray {
        if (outputBands <= 0 || inputLevels.isEmpty()) return FloatArray(outputBands.coerceAtLeast(0))
        if (outputBands == 1) {
            return FloatArray(1) { inputLevels.average().toFloat() }
        }

        val result = FloatArray(outputBands)
        val inputBands = inputLevels.size

        if (inputBands == 10 && hardwareCenterFreqsHz != null && hardwareCenterFreqsHz.size == outputBands) {
            for (i in 0 until outputBands) {
                val targetFreq = hardwareCenterFreqsHz[i].coerceAtLeast(20f)
                val logTarget = ln(targetFreq.toDouble())
                when {
                    targetFreq <= STANDARD_10_BAND_FREQS_HZ.first() -> result[i] = inputLevels.first()
                    targetFreq >= STANDARD_10_BAND_FREQS_HZ.last() -> result[i] = inputLevels.last()
                    else -> {
                        var idx = 0
                        while (idx < STANDARD_10_BAND_FREQS_HZ.size - 1 &&
                            STANDARD_10_BAND_FREQS_HZ[idx + 1] < targetFreq
                        ) {
                            idx++
                        }
                        val logLow = ln(STANDARD_10_BAND_FREQS_HZ[idx].toDouble())
                        val logHigh = ln(STANDARD_10_BAND_FREQS_HZ[idx + 1].toDouble())
                        val fraction = ((logTarget - logLow) / (logHigh - logLow)).toFloat().coerceIn(0f, 1f)
                        result[i] = inputLevels[idx] * (1f - fraction) + inputLevels[idx + 1] * fraction
                    }
                }
            }
            return result
        }

        if (outputBands == 5 && inputBands == 10) {
            result[0] = inputLevels[0] * 0.25f + inputLevels[1] * 0.5f + inputLevels[2] * 0.25f
            result[1] = inputLevels[3] * 0.5f + inputLevels[4] * 0.5f
            result[2] = inputLevels[5] * 0.5f + inputLevels[6] * 0.5f
            result[3] = inputLevels[7] * 0.5f + inputLevels[8] * 0.5f
            result[4] = inputLevels[8] * 0.25f + inputLevels[9] * 0.75f
        } else {
            val ratio = (inputBands - 1).toFloat() / (outputBands - 1).toFloat()
            for (i in 0 until outputBands) {
                val srcPos = i * ratio
                val lowerIndex = srcPos.toInt().coerceIn(0, inputBands - 1)
                val upperIndex = (lowerIndex + 1).coerceIn(0, inputBands - 1)
                val fraction = srcPos - lowerIndex
                result[i] = inputLevels[lowerIndex] * (1f - fraction) + inputLevels[upperIndex] * fraction
            }
        }

        return result
    }

    fun clampMillibel(levelMb: Int, minMb: Short, maxMb: Short): Short =
        levelMb.coerceIn(minMb.toInt(), maxMb.toInt()).toShort()
}
