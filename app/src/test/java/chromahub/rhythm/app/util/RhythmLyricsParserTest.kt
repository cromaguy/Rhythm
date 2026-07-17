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

    @Test
    fun timedRomanizationTrack_staysAttachedToItsJapaneseLine() {
        val parsed = LyricsParser.parseLyrics(
            "[00:00.93]どんな時だって\n" +
                "[00:06.14]運命忘れて\n" +
                "[00:00.93][do n na to ki da tte]\n" +
                "[00:06.14][u n me i wa su re te]"
        )

        assertEquals(2, parsed.size)
        assertEquals("どんな時だって", parsed[0].text)
        assertEquals("do n na to ki da tte", parsed[0].romanization)
        assertEquals("運命忘れて", parsed[1].text)
        assertEquals("u n me i wa su re te", parsed[1].romanization)
    }
}
