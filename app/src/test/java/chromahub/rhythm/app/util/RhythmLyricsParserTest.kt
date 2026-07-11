package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RhythmLyricsParserTest {

    @Test
    fun lrcCompatibilityOutput_preservesRomanizationAtTheSameTimestamp() {
        val lines = listOf(
            WordByWordLyricLine(
                words = listOf(
                    WordByWordWord("君", false, 1_000L, 1_200L),
                    WordByWordWord("が", true, 1_200L, 1_400L),
                    WordByWordWord("好き", true, 1_400L, 1_800L)
                ),
                lineTimestamp = 1_000L,
                lineEndtime = 1_800L,
                romanization = "kimi ga suki"
            )
        )

        assertEquals(
            "[00:01.00]君が好き\n[00:01.00][kimi ga suki]",
            RhythmLyricsParser.toLRCFormat(lines)
        )
    }

    @Test
    fun lrcCompatibilityOutput_isReadBackWithRomanization() {
        val parsed = LyricsParser.parseLyrics(
            "[00:01.00]君が好き\n[00:01.00][kimi ga suki]"
        )

        assertEquals("君が好き", parsed.single().text)
        assertEquals("kimi ga suki", parsed.single().romanization)
    }
}
