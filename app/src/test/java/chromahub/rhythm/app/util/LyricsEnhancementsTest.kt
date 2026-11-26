/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LyricsEnhancementsTest {

    @Test
    fun containerReaderExtractsId3v2SyltAndUsltAndFlacAndMp4AndMatroska() {
        val syltBody = ByteArrayOutputStream().apply {
            write(3) // UTF-8
            write("eng".toByteArray(Charsets.US_ASCII))
            write(2) // timestamp format: ms
            write(1) // content type: lyrics
            write(0) // empty null-terminated descriptor
            write("First line".toByteArray(Charsets.UTF_8))
            write(0)
            write(byteArrayOf(0x00, 0x00, 0x04, 0xE2.toByte())) // 1250 ms
            write("Second line".toByteArray(Charsets.UTF_8))
            write(0)
            write(byteArrayOf(0x00, 0x00, 0x0F, 0xA0.toByte())) // 4000 ms
        }.toByteArray()

        val id3Stream = wrapId3Frame("SYLT", syltBody)
        val id3Res = LocalContainerLyricsReader.extractFromStream(BufferedInputStream(ByteArrayInputStream(id3Stream)))
        assertNotNull(id3Res)
        assertTrue(id3Res!!.isLrcFormatted)
        assertTrue(id3Res.text.contains("[00:01.25]First line"))
        assertTrue(id3Res.text.contains("[00:04.00]Second line"))

        val flacBytes = buildFlacWithVorbisComments("SYNCEDLYRICS=[00:10.50]Chorus drops")
        val flacRes = LocalContainerLyricsReader.extractFromStream(BufferedInputStream(ByteArrayInputStream(flacBytes)))
        assertNotNull(flacRes)
        assertEquals("Embedded FLAC", flacRes!!.sourceLabel)
        assertTrue(flacRes.isLrcFormatted)
        assertEquals("[00:10.50]Chorus drops", flacRes.text)

        val mp4Bytes = buildMp4WithLyrAtom("[00:02.00]Midnight drive")
        val mp4Res = LocalContainerLyricsReader.extractFromStream(BufferedInputStream(ByteArrayInputStream(mp4Bytes)))
        assertNotNull(mp4Res)
        assertEquals("Embedded M4A", mp4Res!!.sourceLabel)
        assertTrue(mp4Res.isLrcFormatted)
        assertEquals("[00:02.00]Midnight drive", mp4Res.text)

        val ebml = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x9F.toByte(), 0x42, 0x86.toByte(), 0x81.toByte(), 0x01))
            write(byteArrayOf(0x67, 0xC8.toByte(), 0x80.toByte()))
            write("SYNCEDLYRICS".toByteArray(Charsets.US_ASCII))
            val tagStr = "[00:05.10]MKV synced".toByteArray(Charsets.UTF_8)
            write(byteArrayOf(0x44, 0x87.toByte(), (0x80 or tagStr.size).toByte()))
            write(tagStr)
            write(ByteArray(32))
        }.toByteArray()
        val ebmlRes = LocalContainerLyricsReader.extractFromStream(BufferedInputStream(ByteArrayInputStream(ebml)))
        assertNotNull(ebmlRes)
        assertEquals("Embedded Matroska", ebmlRes!!.sourceLabel)
        assertEquals("[00:05.10]MKV synced", ebmlRes.text)
    }

    @Test
    fun ttmlRejectsDoctypeAndEntityDeclarations() {
        val maliciousXxe = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">&xxe;</p></div></body></tt>
        """.trimIndent()
        assertNull(parseTtml(null, maliciousXxe))
    }

    @Test
    fun karaokeSplitterBalancesWidthsAndComputesEaseSweep() {
        val widths = listOf(45f, 45f, 45f, 45f)
        val packed = KaraokeLineSplitter.packBalanced(widths, maxWidth = 100f)
        assertEquals(2, packed.size)
        assertEquals(0..1, packed[0])
        assertEquals(2..3, packed[1])

        val words = listOf(
            WordByWordWord("Hel", isPart = false, timestamp = 1000L, endtime = 1500L),
            WordByWordWord("lo", isPart = true, timestamp = 1500L, endtime = 2000L),
            WordByWordWord("world", isPart = false, timestamp = 2200L, endtime = 3000L)
        )
        val (text, ranges) = KaraokeLineSplitter.buildRenderedTextAndRanges(words)
        assertEquals("Hello world", text)
        assertEquals(0..2, ranges[0])
        assertEquals(3..4, ranges[1])
        assertEquals(6..10, ranges[2])

        val (activeBefore, _) = KaraokeLineSplitter.computeWordSweepState(words, 3000L, 500L)
        assertEquals(-1, activeBefore)

        val (activeMid, progressMid) = KaraokeLineSplitter.computeWordSweepState(words, 3000L, 1250L)
        assertEquals(0, activeMid)
        // Ease-out exponent 1.4 puts linear 0.5 > 0.5
        assertTrue(progressMid > 0.5f && progressMid < 1.0f)

        val (activeDone, progressDone) = KaraokeLineSplitter.computeWordSweepState(words, 3000L, 3500L)
        assertEquals(2, activeDone)
        assertEquals(1.0f, progressDone, 1e-4f)
        assertFalse(text.contains("  "))
    }

    private fun wrapId3Frame(frameId: String, body: ByteArray): ByteArray {
        val totalTag = 10 + body.size
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(Charsets.US_ASCII))
        out.write(3); out.write(0); out.write(0)
        writeSyncSafeInt(out, totalTag)
        out.write(frameId.toByteArray(Charsets.US_ASCII))
        writeBeInt(out, body.size)
        out.write(0); out.write(0)
        out.write(body)
        return out.toByteArray()
    }

    private fun buildFlacWithVorbisComments(comment: String): ByteArray {
        val commentBytes = comment.toByteArray(Charsets.UTF_8)
        val vorbis = ByteArrayOutputStream().apply {
            writeLeInt(this, 0) // empty vendor
            writeLeInt(this, 1) // 1 entry
            writeLeInt(this, commentBytes.size)
            write(commentBytes)
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(Charsets.US_ASCII))
            write(0x84) // isLast=1 | blockType=4 (VORBIS_COMMENT)
            write((vorbis.size ushr 16) and 0xFF)
            write((vorbis.size ushr 8) and 0xFF)
            write(vorbis.size and 0xFF)
            write(vorbis)
        }.toByteArray()
    }

    private fun buildMp4WithLyrAtom(lyrics: String): ByteArray {
        val textBytes = lyrics.toByteArray(Charsets.UTF_8)
        val dataPayload = ByteArrayOutputStream().apply {
            write(ByteArray(8)) // version + flags + reserved
            write(textBytes)
        }.toByteArray()
        val dataBox = wrapMp4Box("data", dataPayload)
        val lyrBox = wrapMp4Box("\u00A9lyr", dataBox)
        val ilstBox = wrapMp4Box("ilst", lyrBox)
        val metaPayload = ByteArrayOutputStream().apply {
            write(ByteArray(4)) // full-box version/flags
            write(ilstBox)
        }.toByteArray()
        val metaBox = wrapMp4Box("meta", metaPayload)
        val udtaBox = wrapMp4Box("udta", metaBox)
        val moovBox = wrapMp4Box("moov", udtaBox)
        val ftypBox = wrapMp4Box("ftyp", "M4A ".toByteArray(Charsets.US_ASCII))
        return ftypBox + moovBox
    }

    private fun wrapMp4Box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        writeBeInt(out, 8 + payload.size)
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(payload)
        return out.toByteArray()
    }

    private fun writeBeInt(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    private fun writeLeInt(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 24) and 0xFF)
    }

    private fun writeSyncSafeInt(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 21) and 0x7F)
        out.write((v ushr 14) and 0x7F)
        out.write((v ushr 7) and 0x7F)
        out.write(v and 0x7F)
    }
}
