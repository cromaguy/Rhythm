/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.audio

import chromahub.rhythm.app.infrastructure.audio.peq.ParametricEqProcessor
import chromahub.rhythm.app.infrastructure.audio.peq.ParametricEqText
import chromahub.rhythm.app.infrastructure.audio.peq.PeqFilter
import chromahub.rhythm.app.infrastructure.audio.peq.PeqFilterType
import chromahub.rhythm.app.infrastructure.audio.peq.PeqFitter
import chromahub.rhythm.app.infrastructure.audio.peq.PeqMath
import chromahub.rhythm.app.infrastructure.audio.peq.PeqSettings
import chromahub.rhythm.app.shared.data.model.DeviceAutoEqMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class PeqFitterTest {

    @Test
    fun equalApoParserAndFormatterRoundTrip() {
        val raw = """
            Preamp: -4.2 dB
            Filter 1: ON LSC Fc 105 Hz Gain 3.5 dB Q 0.70
            Filter 2: ON PK Fc 2500 Hz Gain -2.1 dB Q 1.85
            Filter 3: OFF HSC Fc 9000 Hz Gain 1.5 dB Q 0.60
            Filter 4: ON LPQ Fc 14000 Hz Q 0.71
        """.trimIndent()

        val parsed = ParametricEqText.parse(raw, sourceName = "TestPhone")
        assertNotNull(parsed)
        parsed!!
        assertEquals(-4.2, parsed.preampDb, 0.01)
        assertEquals(4, parsed.filters.size)
        assertEquals(PeqFilterType.LOW_SHELF, parsed.filters[0].type)
        assertTrue(parsed.filters[0].enabled)
        assertFalse(parsed.filters[2].enabled)
        assertEquals(PeqFilterType.LOW_PASS, parsed.filters[3].type)

        val formatted = ParametricEqText.format(parsed)
        val reparsed = ParametricEqText.parse(formatted)!!
        assertEquals(parsed.preampDb, reparsed.preampDb, 0.05)
        assertEquals(parsed.filters.size, reparsed.filters.size)
    }

    @Test
    fun peqFitterMinimizesErrorAcrossAutoEqCurveAndComputesNegativePreamp() {
        val target10Band = listOf(4.5f, 3.0f, 1.0f, -1.5f, -2.5f, 0.5f, 2.0f, 3.5f, -1.0f, 0.0f)
        val desiredCurve = PeqFitter.curveFrom10BandProfile(target10Band)
        val fitted = PeqFitter.fit(desiredCurve, sourceName = "Synthetic")

        assertEquals(10, fitted.filters.size)
        val rms = PeqFitter.rmsError(fitted, desiredCurve)
        assertTrue("Expected tight RMS fit (< 0.4 dB), was $rms", rms < 0.4)
        assertTrue("Expected negative preamp for positive peaks, was ${fitted.preampDb}", fitted.preampDb < -1.0)
    }

    @Test
    fun parametricEqProcessorShapesTargetFrequencyAccurately() {
        val processor = ParametricEqProcessor().apply {
            configure(48_000, 2)
            updateSettings(
                PeqSettings(
                    preampDb = 0.0,
                    filters = listOf(
                        PeqFilter(PeqFilterType.PEAKING, frequencyHz = 1000.0, gainDb = -12.0, q = 2.0)
                    )
                )
            )
            setEnabled(true)
        }

        val frameCount = 4800
        val samples1k = DoubleArray(frameCount * 2)
        val samples100 = DoubleArray(frameCount * 2)
        for (i in 0 until frameCount) {
            val v1k = 0.5 * sin(2.0 * PI * 1000.0 * i / 48_000.0)
            val v100 = 0.5 * sin(2.0 * PI * 100.0 * i / 48_000.0)
            samples1k[i * 2] = v1k
            samples1k[i * 2 + 1] = v1k
            samples100[i * 2] = v100
            samples100[i * 2 + 1] = v100
        }

        processor.process(samples1k, samples1k.size)
        processor.resetState()
        processor.process(samples100, samples100.size)

        // Steady-state tail RMS (skip initial transient)
        val tailStart = frameCount
        var sumSq1k = 0.0
        var sumSq100 = 0.0
        for (i in tailStart until frameCount * 2) {
            sumSq1k += samples1k[i] * samples1k[i]
            sumSq100 += samples100[i] * samples100[i]
        }
        val rms1k = sqrt(sumSq1k / (samples1k.size - tailStart))
        val rms100 = sqrt(sumSq100 / (samples100.size - tailStart))

        assertTrue("1 kHz notch should heavily attenuate ($rms1k vs $rms100)", rms1k < rms100 * 0.4)
        assertTrue("100 Hz outside peak should stay near input RMS", abs(rms100 - 0.3535) < 0.03)
    }

    @Test
    fun deviceAutoEqMapRoundTripPersistsBindings() {
        val json1 = DeviceAutoEqMap.withBinding("{}", "usb:FiiO_KA13", "Sennheiser HD 600")
        val json2 = DeviceAutoEqMap.withBinding(json1, "bt:Sony_XM5", "Sony WH-1000XM5")
        assertEquals("Sennheiser HD 600", DeviceAutoEqMap.profileForDevice(json2, "usb:FiiO_KA13"))
        assertEquals("Sony WH-1000XM5", DeviceAutoEqMap.profileForDevice(json2, "bt:Sony_XM5"))

        val removed = DeviceAutoEqMap.withBinding(json2, "usb:FiiO_KA13", "None")
        assertEquals(null, DeviceAutoEqMap.profileForDevice(removed, "usb:FiiO_KA13"))
        assertEquals("Sony WH-1000XM5", DeviceAutoEqMap.profileForDevice(removed, "bt:Sony_XM5"))
    }

    @Test
    fun peqJsonHandlesEmptyAndValidPayloads() {
        assertEquals(null, PeqSettings.fromJson("[]"))
        val settings = PeqSettings(preampDb = -2.0, filters = listOf(PeqFilter(frequencyHz = 500.0, gainDb = 2.0)))
        val encoded = PeqSettings.toJson(settings)
        val decoded = PeqSettings.fromJson(encoded)
        assertNotNull(decoded)
        assertEquals(-2.0, decoded!!.preampDb, 0.001)
        assertEquals(500.0, decoded.filters.first().frequencyHz, 0.001)
        assertEquals(2.0, PeqMath.filterDb(decoded.filters.first(), 500.0), 0.15)
    }
}
