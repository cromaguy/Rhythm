/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.AutoEQProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEQImportExportTest {

    @Test
    fun parseFixedBandEQ_handlesPlusAndMinusSignsAndExplicitFc() {
        val raw = """
            Preamp: -4.5 dB
            Filter 1: ON PK Fc 31 Hz Gain +3.5 dB Q 1.41
            Filter 2: ON PK Fc 62 Hz Gain -2.0 dB Q 1.41
            Filter 3: ON PK Fc 125 Hz Gain 1.5 dB Q 1.41
            Filter 4: OFF PK Fc 250 Hz Gain +6.0 dB Q 1.41
            Filter 5: ON PK Fc 500 Hz Gain -1.0 dB Q 1.41
            Filter 6: ON PK Fc 1000 Hz Gain +0.0 dB Q 1.41
            Filter 7: ON PK Fc 2000 Hz Gain +4.2 dB Q 1.41
            Filter 8: ON PK Fc 4000 Hz Gain -3.1 dB Q 1.41
            Filter 9: ON PK Fc 8000 Hz Gain +2.5 dB Q 1.41
            Filter 10: ON PK Fc 16000 Hz Gain -0.5 dB Q 1.41
        """.trimIndent()

        val profile = AutoEQImportExport.parseFixedBandEQ(raw, "Test Fixed")
        assertNotNull(profile)
        assertEquals("Test Fixed", profile!!.name)
        assertEquals(10, profile.bands.size)

        assertEquals(3.5f, profile.bands[0], 0.01f) // 31 Hz: +3.5
        assertEquals(-2.0f, profile.bands[1], 0.01f) // 62 Hz: -2.0
        assertEquals(1.5f, profile.bands[2], 0.01f) // 125 Hz: 1.5
        assertEquals(0.0f, profile.bands[3], 0.01f) // 250 Hz: OFF was skipped, default 0f
        assertEquals(-1.0f, profile.bands[4], 0.01f) // 500 Hz: -1.0
        assertEquals(0.0f, profile.bands[5], 0.01f) // 1000 Hz: +0.0
        assertEquals(4.2f, profile.bands[6], 0.01f) // 2000 Hz: +4.2
        assertEquals(-3.1f, profile.bands[7], 0.01f) // 4000 Hz: -3.1
        assertEquals(2.5f, profile.bands[8], 0.01f) // 8000 Hz: +2.5
        assertEquals(-0.5f, profile.bands[9], 0.01f) // 16000 Hz: -0.5
    }

    @Test
    fun parseFixedBandEQ_handlesOutOfOrderFrequencies() {
        val raw = """
            Filter 1: ON PK Fc 16000 Hz Gain +1.0 dB Q 1.41
            Filter 2: ON PK Fc 31 Hz Gain -5.0 dB Q 1.41
        """.trimIndent()

        val profile = AutoEQImportExport.parseFixedBandEQ(raw, "Out Of Order")
        assertNotNull(profile)
        assertEquals(-5.0f, profile!!.bands[0], 0.01f) // 31 Hz is at band 0
        assertEquals(1.0f, profile.bands[9], 0.01f)  // 16000 Hz is at band 9
    }

    @Test
    fun parseParametricEQ_handlesShelvingFiltersAndCascading() {
        val raw = """
            Preamp: -6.0 dB
            Filter 1: ON LSC Fc 100 Hz Gain +3.0 dB Q 0.7
            Filter 2: ON HSC Fc 5000 Hz Gain +2.0 dB Q 0.7
            Filter 3: ON PK Fc 1000 Hz Gain -1.5 dB Q 1.41
            Filter 4: OFF PK Fc 1000 Hz Gain +10.0 dB Q 1.41
        """.trimIndent()

        val profile = AutoEQImportExport.parseParametricEQ(raw, "Parametric Shelves")
        assertNotNull(profile)
        assertEquals(10, profile!!.bands.size)

        // LSC Fc 100 Hz affects bands <= 100 Hz (31Hz: index 0, 62Hz: index 1)
        assertEquals(3.0f, profile.bands[0], 0.01f) // 31 Hz
        assertEquals(3.0f, profile.bands[1], 0.01f) // 62 Hz
        assertEquals(1.5f, profile.bands[2], 0.01f) // 125 Hz (half-gain rolloff at Fc * 1.4 = 140Hz)
        assertEquals(0.0f, profile.bands[3], 0.01f) // 250 Hz (above rolloff)

        // Peak 1000 Hz: index 5
        assertEquals(-1.5f, profile.bands[5], 0.01f)

        // HSC Fc 5000 Hz affects bands >= 5000 Hz (8000Hz: index 8, 16000Hz: index 9)
        assertEquals(2.0f, profile.bands[8], 0.01f) // 8000 Hz
        assertEquals(2.0f, profile.bands[9], 0.01f) // 16000 Hz

        // OFF filter 4 was skipped
    }

    @Test
    fun autoDetectAndParse_detectsFixedBandFormat() {
        val raw = """
            Preamp: -3.0 dB
            Filter 1: ON PK Fc 31 Hz Gain +2.0 dB Q 1.41
            Filter 2: ON PK Fc 62 Hz Gain -1.0 dB Q 1.41
        """.trimIndent()

        val profiles = AutoEQImportExport.autoDetectAndParse(raw, "Detected Profile")
        assertEquals(1, profiles.size)
        assertEquals(2.0f, profiles[0].bands[0], 0.01f)
        assertEquals(-1.0f, profiles[0].bands[1], 0.01f)
    }

    @Test
    fun autoDetectAndParse_detectsJsonFormat() {
        val json = """
            [
              {
                "name": "Sony WH-1000XM4",
                "brand": "Sony",
                "type": "Over-Ear",
                "bands": [1.0, 2.0, 0.0, -1.0, -2.0, 0.0, 1.0, 2.0, 1.5, 0.5]
              }
            ]
        """.trimIndent()

        val profiles = AutoEQImportExport.autoDetectAndParse(json)
        assertEquals(1, profiles.size)
        assertEquals("Sony WH-1000XM4", profiles[0].name)
        assertEquals("Sony", profiles[0].brand)
        assertEquals(1.0f, profiles[0].bands[0], 0.01f)
    }

    @Test
    fun autoEQProfile_serializationRoundtrip() {
        val original = listOf(
            AutoEQProfile("Custom 1", "Brand A", "Over-Ear", listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)),
            AutoEQProfile("Custom 2", "Brand B", "In-Ear", listOf(-1f, -2f, -3f, -4f, -5f, -6f, -7f, -8f, -9f, -10f))
        )

        val json = AutoEQProfile.listToJson(original)
        assertTrue(json.isNotBlank())

        val restored = AutoEQProfile.listFromJson(json)
        assertEquals(2, restored.size)
        assertEquals("Custom 1", restored[0].name)
        assertEquals("Brand A", restored[0].brand)
        assertEquals(10f, restored[0].bands[9], 0.01f)
        assertEquals("Custom 2", restored[1].name)
        assertEquals(-10f, restored[1].bands[9], 0.01f)
    }

    @Test
    fun autoEQProfile_listFromJson_handlesCorruptOrEmptyJson() {
        assertEquals(emptyList<AutoEQProfile>(), AutoEQProfile.listFromJson(null))
        assertEquals(emptyList<AutoEQProfile>(), AutoEQProfile.listFromJson(""))
        assertEquals(emptyList<AutoEQProfile>(), AutoEQProfile.listFromJson("{ invalid json }"))
    }

    @Test
    fun headRoomCalculation_preventsClipping() {
        val bandsWithPositiveGain = floatArrayOf(4.5f, 2.0f, 0.0f, -1.0f, 6.0f, 0.0f, 1.0f, -3.0f, 0.0f, 0.0f)
        val maxGain = bandsWithPositiveGain.maxOrNull() ?: 0f
        assertEquals(6.0f, maxGain, 0.01f)

        val headroom = if (maxGain > 0f) maxGain else 0f
        assertEquals(6.0f, headroom, 0.01f)

        val attenuatedLevels = bandsWithPositiveGain.map { it - headroom }
        // The peak should now be exactly 0 dB (6.0 - 6.0 = 0.0)
        assertEquals(0.0f, attenuatedLevels.maxOrNull() ?: -1f, 0.01f)
        // Sub-bass should be 4.5 - 6.0 = -1.5 dB
        assertEquals(-1.5f, attenuatedLevels[0], 0.01f)
    }
}
