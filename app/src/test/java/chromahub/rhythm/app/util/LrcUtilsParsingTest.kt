/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import android.net.TestUri
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class LrcUtilsParsingTest {

    @Test
    fun extractAndParseLyrics_ranksWordSyncedBeforeLineSyncedBeforeUnsynced() {
        val unsynced = VorbisComment("LYRICS", "Just plain lyrics\nWithout timestamps")
        val lineSynced = VorbisComment("LYRICS", "[00:05.00]Line synced only\n[00:10.00]Second line")
        val wordSynced = VorbisComment("LYRICS", "[00:05.00]<00:05.00>Word <00:05.50>synced <00:06.00>line")

        val metadata = Metadata(unsynced, lineSynced, wordSynced)
        val ranked = LrcUtils.extractAndParseLyrics(
            sampleRate = 44100,
            audioMimeType = "audio/flac",
            metadata = metadata,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = false,
                errorText = null,
                autoWordSync = false
            )
        )

        assertEquals(3, ranked.size)
        val first = ranked[0] as SemanticLyrics.SyncedLyrics
        assertTrue(
            "Highest ranked entry must contain real word timings",
            first.text.any { it.words != null }
        )
        assertTrue(ranked[1] is SemanticLyrics.SyncedLyrics)
        assertTrue(ranked[2] is SemanticLyrics.UnsyncedLyrics)
    }

    @Test
    fun decodeLyricsBytes_detectsUtf8BomUtf16BomsAndLegacyCharsets() {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "[00:01.00]Café UTF-8".toByteArray(Charsets.UTF_8)
        assertEquals("[00:01.00]Café UTF-8", LrcUtils.decodeLyricsBytes(utf8Bom))

        val utf16Le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "[00:01.00]Unicode LE".toByteArray(Charsets.UTF_16LE)
        assertEquals("[00:01.00]Unicode LE", LrcUtils.decodeLyricsBytes(utf16Le))

        val utf16Be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "[00:01.00]Unicode BE".toByteArray(Charsets.UTF_16BE)
        assertEquals("[00:01.00]Unicode BE", LrcUtils.decodeLyricsBytes(utf16Be))

        // Invalid UTF-8 bytes in Windows-1252 (0xE9 = é) must fall back cleanly instead of throwing or turning into U+FFFD
        val win1252 = "[00:01.00]Caf".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0xE9.toByte())
        val decodedWin1252 = LrcUtils.decodeLyricsBytes(win1252)
        assertTrue(decodedWin1252.startsWith("[00:01.00]Caf"))
        assertTrue(decodedWin1252.endsWith("é"))

        // Verify GB18030 CJK sidecar bytes (0xC4 0xE3 0xBA 0xC3 = 你好 in GBK/GB18030) decode cleanly
        val gb18030 = runCatching { Charset.forName("GB18030") }.getOrNull()
        if (gb18030 != null) {
            val cjkBytes = "[00:02.00]".toByteArray(Charsets.US_ASCII) + "你好".toByteArray(gb18030)
            assertEquals("[00:02.00]你好", LrcUtils.decodeLyricsBytes(cjkBytes))
        }
    }
}
