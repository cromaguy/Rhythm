package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.LyricsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsEnrichmentMergerTest {

    @Test
    fun localCjkBase_borrowsApiTranslationAndRomajiWithoutReplacingOriginal() {
        val local = LyricsData(
            plainLyrics = "君が好き\n光を待つ",
            syncedLyrics = """
                [00:01.00]君が好き
                [00:03.00]光を待つ
            """.trimIndent(),
            source = "Local LRC"
        )
        val api = LyricsData(
            plainLyrics = "君が好き\n光を待つ",
            syncedLyrics = """
                [00:01.08]君が好き
                [00:01.08](I love you)
                [00:01.08][kimi ga suki]
                [00:03.12]光を待つ
                [00:03.12](Waiting for the light)
                [00:03.12][hikari o matsu]
            """.trimIndent(),
            source = "Apple Music"
        )

        val merged = LyricsEnrichmentMerger.merge(local, api)
        val parsed = LyricsParser.parseLyrics(merged.syncedLyrics.orEmpty())

        assertEquals(listOf("君が好き", "光を待つ"), parsed.map { it.text })
        assertEquals(listOf("I love you", "Waiting for the light"), parsed.map { it.translation })
        assertEquals(listOf("kimi ga suki", "hikari o matsu"), parsed.map { it.romanization })
        assertTrue(merged.source.orEmpty().startsWith("Local LRC + Translation/Romaji"))
    }

    @Test
    fun romajiOnlyLocalBase_isRetainedAsRomajiWhenApiSuppliesCjkOriginal() {
        val localRomaji = LyricsData(
            plainLyrics = "kimi ga suki\nhikari o matsu",
            syncedLyrics = """
                [00:01.00]kimi ga suki
                [00:03.00]hikari o matsu
            """.trimIndent(),
            source = "Local LRC"
        )
        val apiCjk = LyricsData(
            plainLyrics = "君が好き\n光を待つ",
            syncedLyrics = """
                [00:01.10]君が好き
                [00:01.10](I love you)
                [00:03.10]光を待つ
                [00:03.10](Waiting for the light)
            """.trimIndent(),
            source = "Apple Music"
        )

        val merged = LyricsEnrichmentMerger.merge(localRomaji, apiCjk)
        val parsed = LyricsParser.parseLyrics(merged.syncedLyrics.orEmpty())

        assertEquals(listOf("君が好き", "光を待つ"), parsed.map { it.text })
        assertEquals(listOf("I love you", "Waiting for the light"), parsed.map { it.translation })
        assertEquals(listOf("kimi ga suki", "hikari o matsu"), parsed.map { it.romanization })
        assertTrue(merged.hasCjkOriginalCoverage())
        assertTrue(merged.hasUsableTimedRomanization())
        assertTrue(merged.hasUsableTimedTranslation())
    }
}
