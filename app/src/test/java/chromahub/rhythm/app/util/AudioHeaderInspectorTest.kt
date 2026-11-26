/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class AudioHeaderInspectorTest {

    @Test
    fun inspectBytes_readsExact24Bit96kHzFlacStreamInfo() {
        val streamInfo = ByteArray(34)
        // Sample rate = 96000 (0x17700), channels = 2 -> ch-1 = 1, bps = 24 -> bps-1 = 23 (0x17), totalSamples = 960000
        streamInfo[10] = 0x17
        streamInfo[11] = 0x70
        streamInfo[12] = ((0x0 shl 4) or (1 shl 1) or (23 ushr 4)).toByte()
        streamInfo[13] = ((23 and 0x0F) shl 4).toByte()
        streamInfo[16] = 0x0E.toByte()
        streamInfo[17] = 0xA6.toByte()

        val bytes = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(Charsets.US_ASCII))
            write(0x80) // STREAMINFO last block = true, type = 0
            write(0x00); write(0x00); write(34)
            write(streamInfo)
        }.toByteArray()

        val inspected = AudioHeaderInspector.inspectBytes(bytes)
        assertNotNull(inspected)
        assertEquals("FLAC", inspected?.codec)
        assertTrue(inspected?.isLossless == true)
        assertEquals(96_000, inspected?.sampleRateHz)
        assertEquals(24, inspected?.bitDepth)
        assertEquals(2, inspected?.channelCount)
    }

    @Test
    fun inspectBytes_reads32BitFloatWavFmtChunk() {
        val wav = ByteArray(44)
        "RIFF".toByteArray().copyInto(wav, 0)
        "WAVE".toByteArray().copyInto(wav, 8)
        "fmt ".toByteArray().copyInto(wav, 12)
        wav[16] = 16 // fmt chunk size LE
        wav[20] = 3  // IEEE_FLOAT formatTag = 3
        wav[22] = 2  // channels
        // sampleRate = 48000 (0x0000BB80)
        wav[24] = 0x80.toByte(); wav[25] = 0xBB.toByte()
        wav[34] = 32 // 32-bit float

        val inspected = AudioHeaderInspector.inspectBytes(wav)
        assertNotNull(inspected)
        assertEquals("WAV", inspected?.codec)
        assertTrue(inspected?.isLossless == true)
        assertEquals(48_000, inspected?.sampleRateHz)
        assertEquals(32, inspected?.bitDepth)
        assertEquals(2, inspected?.channelCount)
    }

    @Test
    fun inspectBytes_readsAiffExtended80SampleRateAndBitDepth() {
        // 44100.0 Hz in IEEE-754 80-bit extended: exp=0x400E (14 + 16383), mant=0xAC440000_00000000
        val aiff = ByteArray(40)
        "FORM".toByteArray().copyInto(aiff, 0)
        "AIFF".toByteArray().copyInto(aiff, 8)
        "COMM".toByteArray().copyInto(aiff, 12)
        aiff[19] = 18 // size = 18 BE
        aiff[21] = 2  // 2 channels
        aiff[27] = 16 // 16-bit
        aiff[28] = 0x40; aiff[29] = 0x0E
        aiff[30] = 0xAC.toByte(); aiff[31] = 0x44

        val inspected = AudioHeaderInspector.inspectBytes(aiff)
        assertNotNull(inspected)
        assertEquals("AIFF", inspected?.codec)
        assertEquals(44_100, inspected?.sampleRateHz)
        assertEquals(16, inspected?.bitDepth)
        assertEquals(2, inspected?.channelCount)
    }

    @Test
    fun inspectBytes_readsDsd64DsfHeaderAndOpusOggHeader() {
        val dsf = ByteArray(80)
        "DSD ".toByteArray().copyInto(dsf, 0)
        "fmt ".toByteArray().copyInto(dsf, 28)
        dsf[52] = 2 // 2 channels LE
        // sampleRate = 2_822_400 (0x002B1100)
        dsf[56] = 0x00; dsf[57] = 0x11; dsf[58] = 0x2B; dsf[59] = 0x00

        val dsfInfo = AudioHeaderInspector.inspectBytes(dsf)
        assertEquals("DSD", dsfInfo?.codec)
        assertEquals(2_822_400, dsfInfo?.sampleRateHz)
        assertEquals(1, dsfInfo?.bitDepth)

        val opus = ByteArray(48)
        "OggS".toByteArray().copyInto(opus, 0)
        opus[26] = 1 // 1 segment
        opus[27] = 19
        "OpusHead".toByteArray().copyInto(opus, 28)
        opus[37] = 2 // stereo

        val opusInfo = AudioHeaderInspector.inspectBytes(opus)
        assertEquals("Opus", opusInfo?.codec)
        assertEquals(48_000, opusInfo?.sampleRateHz)
        assertEquals(2, opusInfo?.channelCount)
    }
}
