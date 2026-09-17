/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.infrastructure.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerInterpolationTest {

    private fun interpolateBands(inputLevels: FloatArray, outputBands: Int): FloatArray {
        if (outputBands <= 0 || inputLevels.isEmpty()) return FloatArray(outputBands.coerceAtLeast(0))
        if (outputBands == 1) {
            return FloatArray(1) { inputLevels.average().toFloat() }
        }

        val result = FloatArray(outputBands)
        val inputBands = inputLevels.size

        if (outputBands == 5 && inputBands == 10) {
            result[0] = (inputLevels[0] * 0.25f + inputLevels[1] * 0.5f + inputLevels[2] * 0.25f)
            result[1] = (inputLevels[3] * 0.5f + inputLevels[4] * 0.5f)
            result[2] = (inputLevels[5] * 0.5f + inputLevels[6] * 0.5f)
            result[3] = (inputLevels[7] * 0.5f + inputLevels[8] * 0.5f)
            result[4] = (inputLevels[8] * 0.25f + inputLevels[9] * 0.75f)
        } else {
            val ratio = (inputBands - 1).toFloat() / (outputBands - 1).toFloat()
            for (i in 0 until outputBands) {
                val srcPos = i * ratio
                val lowerIndex = srcPos.toInt().coerceIn(0, inputBands - 1)
                val upperIndex = (lowerIndex + 1).coerceIn(0, inputBands - 1)
                val fraction = srcPos - lowerIndex
                result[i] = inputLevels[lowerIndex] * (1 - fraction) + inputLevels[upperIndex] * fraction
            }
        }

        return result
    }

    @Test
    fun interpolateBands_guardsAgainstZeroOrSingleBand() {
        val input = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)

        // 0 bands returns empty
        val zeroResult = interpolateBands(input, 0)
        assertEquals(0, zeroResult.size)

        // Negative bands returns empty
        val negativeResult = interpolateBands(input, -5)
        assertEquals(0, negativeResult.size)

        // 1 band returns average without DivisionByZero NaN
        val oneResult = interpolateBands(input, 1)
        assertEquals(1, oneResult.size)
        assertEquals(5.5f, oneResult[0], 0.01f)
        assertTrue(!oneResult[0].isNaN() && !oneResult[0].isInfinite())
    }

    @Test
    fun interpolateBands_10to5MappingPreservesFrequencyShapes() {
        // Flat input yields flat output
        val flatInput = FloatArray(10) { 0f }
        val flatOutput = interpolateBands(flatInput, 5)
        assertEquals(5, flatOutput.size)
        for (level in flatOutput) {
            assertEquals(0f, level, 0.01f)
        }

        // Bass boost (bands 0, 1, 2 have +4dB)
        val bassBoost = floatArrayOf(4f, 4f, 4f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val bassOutput = interpolateBands(bassBoost, 5)
        assertEquals(4.0f, bassOutput[0], 0.01f)
        assertEquals(0.0f, bassOutput[1], 0.01f)
        assertEquals(0.0f, bassOutput[4], 0.01f)

        // Treble boost (bands 8, 9 have +4dB)
        val trebleBoost = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 4f, 4f)
        val trebleOutput = interpolateBands(trebleBoost, 5)
        assertEquals(0.0f, trebleOutput[0], 0.01f)
        assertEquals(2.0f, trebleOutput[3], 0.01f) // Band 3 incorporates band 8
        assertEquals(4.0f, trebleOutput[4], 0.01f) // Band 4 incorporates band 8 and 9
    }

    @Test
    fun interpolateBands_arbitraryBandCountLinear() {
        val input = floatArrayOf(0f, 10f)
        val output = interpolateBands(input, 5)
        assertEquals(5, output.size)
        assertEquals(0.0f, output[0], 0.01f)
        assertEquals(2.5f, output[1], 0.01f)
        assertEquals(5.0f, output[2], 0.01f)
        assertEquals(7.5f, output[3], 0.01f)
        assertEquals(10.0f, output[4], 0.01f)
    }
}
