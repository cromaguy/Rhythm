package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.LyricsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRomanizationPolicyTest {

    @Test
    fun plainRomajiLrc_isAcceptedWithoutSupplementalMarkers() {
        val lyrics = LyricsData(
            plainLyrics = "houkago getabako hibiku amaoto\nkasa naku kakedasu",
            syncedLyrics = "[00:00.62]houkago getabako hibiku amaoto\n" +
                "[00:06.62]kasa naku kakedasu shatsu ga nureru",
            source = "Local .lrc"
        )

        assertTrue(lyrics.hasUsableTimedRomanization())
        assertEquals(
            "houkago getabako hibiku amaoto",
            LyricsRomanizationPolicy.selectLine("houkago getabako hibiku amaoto", null)
        )
    }

    @Test
    fun explicitRomajiTrack_isAcceptedOverCjkOriginal() {
        val lyrics = LyricsData(
            plainLyrics = "放課後 下駄箱 響く雨音",
            syncedLyrics = "[00:00.62]放課後 下駄箱 響く雨音\n" +
                "[00:00.62][houkago getabako hibiku amaoto]",
            source = "Lyrically (NetEase)"
        )

        assertTrue(lyrics.hasUsableTimedRomanization())
        assertEquals(
            "houkago getabako hibiku amaoto",
            LyricsRomanizationPolicy.selectLine("放課後 下駄箱 響く雨音", "houkago getabako hibiku amaoto")
        )
    }

    @Test
    fun cjkOriginal_isNeverUsedAsRomajiFallback() {
        assertNull(LyricsRomanizationPolicy.selectLine("好きなんです！", null))
        assertFalse(LyricsRomanizationPolicy.isLatinDominant("好きなんです！"))
    }

    @Test
    fun isolatedEnglishLine_doesNotMakeMostlyCjkTrackRomanized() {
        assertFalse(
            LyricsRomanizationPolicy.hasUsableCoverage(
                original = listOf("放課後", "雨音", "I love you", "好きなんです"),
                supplemental = emptyList()
            )
        )
    }

    @Test
    fun isolatedCjkLine_doesNotMakeMostlyLatinTrackFullyRomanized() {
        assertFalse(
            LyricsRomanizationPolicy.hasUsableCoverage(
                original = listOf(
                    "Coming at you live",
                    "Here to light it up",
                    "모두 날 따라 해",
                    "Gonna break rules",
                    "To live on the edge"
                ),
                supplemental = emptyList()
            )
        )
    }
}
