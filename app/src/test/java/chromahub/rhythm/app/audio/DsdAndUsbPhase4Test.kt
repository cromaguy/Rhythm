/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.audio

import chromahub.rhythm.app.infrastructure.audio.codec.DsdMedia3Extractor
import chromahub.rhythm.app.infrastructure.audio.codec.DsdPcmDecimator
import chromahub.rhythm.app.infrastructure.audio.usb.UsbDacDescriptorProber
import androidx.media3.extractor.ExtractorInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DsdAndUsbPhase4Test {

    @Test
    fun dsdPcmDecimatorOutputsSilenceOn0x55AndOppositePolarityForAllOnesVsAllZeros() {
        val dec = DsdPcmDecimator(channelCount = 2, dsdSampleRateHz = 2_822_400, lsbFirst = true)
        assertEquals(176_400, dec.outputSampleRateHz)

        val silenceInput = ByteArray(128) { 0x55.toByte() }
        val silenceOut = ByteArray(dec.maxOutputBytes(silenceInput.size, planar = false, blockSizePerChannel = 0))
        val silenceBytes = dec.process(silenceInput, 0, silenceInput.size, planar = false, blockSizePerChannel = 0, outputPcmBytes = silenceOut)
        assertTrue(silenceBytes > 0)
        val silBuf = ByteBuffer.wrap(silenceOut, 0, silenceBytes).order(ByteOrder.LITTLE_ENDIAN)
        while (silBuf.remaining() >= 4) {
            val v = silBuf.getFloat()
            assertTrue("Expected near-zero DC on 0x55 pattern, got $v", kotlin.math.abs(v) < 1e-4f)
        }

        val onesDec = DsdPcmDecimator(channelCount = 1, dsdSampleRateHz = 2_822_400, lsbFirst = false)
        val onesIn = ByteArray(64) { 0xFF.toByte() }
        val onesOut = ByteArray(onesDec.maxOutputBytes(onesIn.size, planar = false, blockSizePerChannel = 0))
        val onesBytes = onesDec.process(onesIn, 0, onesIn.size, planar = false, blockSizePerChannel = 0, outputPcmBytes = onesOut)
        val posVal = ByteBuffer.wrap(onesOut, onesBytes - 4, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat()
        assertTrue("Expected positive amplitude for all-1 PDM, got $posVal", posVal > 0.25f)

        onesDec.reset()
        val zerosIn = ByteArray(64) { 0x00.toByte() }
        val zerosBytes = onesDec.process(zerosIn, 0, zerosIn.size, planar = false, blockSizePerChannel = 0, outputPcmBytes = onesOut)
        val negVal = ByteBuffer.wrap(onesOut, zerosBytes - 4, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat()
        assertTrue("Expected negative amplitude for all-0 PDM, got $negVal", negVal < -0.25f)
    }

    @Test
    fun dsdExtractorSniffsDsfAndDffHeadersOnly() {
        val dsfMagic = "DSD ".toByteArray(Charsets.US_ASCII) + ByteArray(12)
        val dffMagic = ("FRM8" + "\u0000".repeat(8) + "DSD ").toByteArray(Charsets.ISO_8859_1)
        val wavMagic = ("RIFF" + "\u0000".repeat(4) + "WAVE" + "fmt ").toByteArray(Charsets.US_ASCII)

        val extractor = DsdMedia3Extractor()
        assertTrue(extractor.sniff(FakePeekInput(dsfMagic)))
        assertTrue(extractor.sniff(FakePeekInput(dffMagic)))
        assertFalse(extractor.sniff(FakePeekInput(wavMagic)))
    }

    @Test
    fun usbDescriptorParserReadsUac2AndDiscreteRates() {
        // CS_INTERFACE (0x24) AC_HEADER UAC 2.0 + CS_INTERFACE AS_FORMAT_TYPE TYPE_I 32-bit 96k/192k
        val descriptors = byteArrayOf(
            // CS_INTERFACE AC_HEADER (len=9, type=0x24, subtype=0x01, bcdADC=0x00 0x02 => UAC 2.0)
            0x09, 0x24, 0x01, 0x00, 0x02, 0x01, 0x09, 0x00, 0x00,
            // CS_INTERFACE UAC1-style TYPE_I with 2 discrete rates: 96000 (0x017700) and 192000 (0x02EE00)
            14, 0x24, 0x02, 0x01, 0x02, 0x04, 32, 0x02,
            0x00, 0x77, 0x01, // 96000 Hz LE24
            0x00, 0xEE.toByte(), 0x02 // 192000 Hz LE24
        )
        val profile = UsbDacDescriptorProber.parseAudioClassDescriptors(
            productName = "FiiO KA13",
            vendorId = 0x2972,
            productId = 0x0047,
            rawDescriptors = descriptors
        )
        assertEquals("UAC 2.0", profile.uacVersionLabel)
        assertEquals(32, profile.advertisedBitResolution)
        assertEquals(listOf(96_000, 192_000), profile.supportedRatesHz)
    }

    private class FakePeekInput(private val bytes: ByteArray) : ExtractorInput {
        override fun peekFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean {
            if (bytes.size < length) return false
            System.arraycopy(bytes, 0, target, offset, length)
            return true
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        override fun readFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean): Boolean = false
        override fun readFully(target: ByteArray, offset: Int, length: Int) {}
        override fun skip(length: Int): Int = 0
        override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean = false
        override fun skipFully(length: Int) {}
        override fun peek(target: ByteArray, offset: Int, length: Int): Int = 0
        override fun peekFully(target: ByteArray, offset: Int, length: Int) {}
        override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean): Boolean = false
        override fun advancePeekPosition(length: Int) {}
        override fun resetPeekPosition() {}
        override fun getPeekPosition(): Long = 0L
        override fun getPosition(): Long = 0L
        override fun getLength(): Long = bytes.size.toLong()
        override fun <E : Throwable?> setRetryPosition(position: Long, e: E) {}
    }
}
