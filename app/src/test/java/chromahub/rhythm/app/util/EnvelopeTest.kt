/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.Curve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EnvelopeTest {

    @Test
    fun equalPower_maintainsConstantAcousticPower() {
        // Equal power requires: volIn^2 + volOut^2 == 1.0 across the entire transition
        var p = 0.0f
        while (p <= 1.001f) {
            val volIn = calculateVolumeIn(p, Curve.EQUAL_POWER)
            val volOut = calculateVolumeOut(p, Curve.EQUAL_POWER)
            val totalPower = (volIn * volIn) + (volOut * volOut)

            assertEquals(
                "Total acoustic energy must equal 1.0 (0 dB dip) at progress $p",
                1.0f,
                totalPower,
                0.002f
            )
            p += 0.05f
        }
    }

    @Test
    fun equalPower_boundaryValues() {
        val startIn = calculateVolumeIn(0.0f, Curve.EQUAL_POWER)
        val startOut = calculateVolumeOut(0.0f, Curve.EQUAL_POWER)
        assertEquals(0.0f, startIn, 0.0001f)
        assertEquals(1.0f, startOut, 0.0001f)

        val endIn = calculateVolumeIn(1.0f, Curve.EQUAL_POWER)
        val endOut = calculateVolumeOut(1.0f, Curve.EQUAL_POWER)
        assertEquals(1.0f, endIn, 0.0001f)
        assertEquals(0.0f, endOut, 0.0001f)

        val midIn = calculateVolumeIn(0.5f, Curve.EQUAL_POWER)
        val midOut = calculateVolumeOut(0.5f, Curve.EQUAL_POWER)
        // At 50%, both signals must be at ~0.7071 (-3 dB) so sum of powers is 1.0
        assertEquals(0.7071f, midIn, 0.001f)
        assertEquals(0.7071f, midOut, 0.001f)
    }

    @Test
    fun linearAndSCurve_behaveCorrectly() {
        assertEquals(0.5f, calculateVolumeIn(0.5f, Curve.LINEAR), 0.001f)
        assertEquals(0.5f, calculateVolumeOut(0.5f, Curve.LINEAR), 0.001f)

        assertEquals(0.5f, calculateVolumeIn(0.5f, Curve.S_CURVE), 0.001f)
        assertEquals(0.5f, calculateVolumeOut(0.5f, Curve.S_CURVE), 0.001f)
    }
}
