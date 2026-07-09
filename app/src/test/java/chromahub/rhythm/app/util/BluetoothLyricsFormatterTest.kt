package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothLyricsFormatterTest {

    @Test
    fun telephoneLengthLine_splitsInsideItsOwnTimestampWindow() {
        val text = "alpha beta gamma delta epsilon zeta eta theta"
        assertEquals(45, text.length)

        val chunks = BluetoothLyricsFormatter.chunksForLine(text, durationMs = 3700L)

        assertEquals(listOf("alpha beta gamma delta epsilon", "zeta eta theta"), chunks)

        val timestamps = longArrayOf(51_470L, 55_170L)
        val texts = listOf(text, "next phrase")
        val start = BluetoothLyricsFormatter.resolveLine(51_470L, timestamps, texts)
        val later = BluetoothLyricsFormatter.resolveLine(53_500L, timestamps, texts)

        assertEquals("alpha beta gamma delta epsilon", start?.text)
        assertEquals("zeta eta theta", later?.text)
        assertEquals(0, start?.sourceLineIndex)
        assertEquals(2, start?.chunkCount)
    }

    @Test
    fun monsterLengthOpeningLine_splitsBeforeSlowScrollWouldMissTheEnd() {
        val text = "alpha beta gamma delta epsilon zeta eta theta iota kappaa"
        assertEquals(57, text.length)

        val chunks = BluetoothLyricsFormatter.chunksForLine(text, durationMs = 3670L)

        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.length <= BluetoothLyricsFormatter.DEFAULT_MAX_CHUNK_CHARS })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun shortLine_doesNotGetGluedToTheFollowingLine() {
        val timestamps = longArrayOf(63_200L, 64_470L, 65_760L)
        val texts = listOf(
            "brief hook",
            "alpha beta gamma delta epsilon zeta eta theta",
            "next phrase"
        )

        val shortLine = BluetoothLyricsFormatter.resolveLine(63_500L, timestamps, texts)
        val nextLine = BluetoothLyricsFormatter.resolveLine(64_700L, timestamps, texts)

        assertEquals("brief hook", shortLine?.text)
        assertFalse(shortLine?.text.orEmpty().contains("alpha"))
        assertEquals(1, nextLine?.sourceLineIndex)
    }

    @Test
    fun longLine_staysWholeWhenDurationGivesEnoughScrollTime() {
        val text = "alpha beta gamma delta epsilon zeta eta theta"

        val chunks = BluetoothLyricsFormatter.chunksForLine(text, durationMs = 8000L)

        assertEquals(listOf(text), chunks)
    }

    @Test
    fun compressedVeryLongLine_keepsAllTextAcrossChunks() {
        val text = (1..24).joinToString(" ") { "aaaa" }

        val chunks = BluetoothLyricsFormatter.chunksForLine(text, durationMs = 3000L)

        assertTrue(chunks.size > 1)
        assertEquals(text, chunks.joinToString(" "))
    }
}
