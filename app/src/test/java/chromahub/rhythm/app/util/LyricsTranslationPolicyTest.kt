package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.LyricsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTranslationPolicyTest {

    @Test
    fun distinctTranslation_isAcceptedForCjkAndLatinOriginals() {
        assertEquals("I love you", LyricsTranslationPolicy.selectLine("君が好き", "I love you"))
        assertEquals(
            "Eu amo você",
            LyricsTranslationPolicy.selectLine("I love you", "Eu amo você")
        )
    }

    @Test
    fun blankOrDuplicateTranslation_isRejected() {
        assertNull(LyricsTranslationPolicy.selectLine("I love you", " "))
        assertNull(LyricsTranslationPolicy.selectLine("I love you", "i LOVE YOU"))
    }

    @Test
    fun timedTranslation_requiresUsefulTrackCoverage() {
        assertTrue(
            LyricsData(
                plainLyrics = "君が好き\n光",
                syncedLyrics = "[00:01.00]君が好き\n[00:01.00](I love you)\n" +
                    "[00:03.00]光\n[00:03.00](Light)"
            ).hasUsableTimedTranslation()
        )
        assertFalse(
            LyricsTranslationPolicy.hasUsableCoverage(
                original = listOf("line one", "line two", "line three"),
                supplemental = listOf("translated one", "", "")
            )
        )
    }
}
