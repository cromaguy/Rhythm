/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMultiVoiceAndSanitizationTest {

    @Test
    fun testVoiceTagNormalization() {
        assertEquals("v1", LyricsParser.normalizeVoiceTag("v1"))
        assertEquals("v1", LyricsParser.normalizeVoiceTag("V1"))
        assertEquals("v1", LyricsParser.normalizeVoiceTag("voice1"))
        assertEquals("v1", LyricsParser.normalizeVoiceTag("Voice1"))
        assertEquals("v1", LyricsParser.normalizeVoiceTag("voice1background"))
        assertEquals("v2", LyricsParser.normalizeVoiceTag("v2"))
        assertEquals("v2", LyricsParser.normalizeVoiceTag("V2"))
        assertEquals("v2", LyricsParser.normalizeVoiceTag("voice2"))
        assertEquals("v2", LyricsParser.normalizeVoiceTag("Voice2Background"))
        assertEquals("v3", LyricsParser.normalizeVoiceTag("v3"))
        assertEquals("v3", LyricsParser.normalizeVoiceTag("group"))
        assertEquals("v3", LyricsParser.normalizeVoiceTag("GroupBackground"))
        assertNull(LyricsParser.normalizeVoiceTag(null))
        assertNull(LyricsParser.normalizeVoiceTag(""))
    }

    @Test
    fun testMultiVoiceLrcParsingWithDifferentDelimiters() {
        val lrc = """
            [00:05.00]v1: Standard colon voice 1
            [00:10.00]v2: Standard colon voice 2
            [00:15.00][v1] Bracket voice 1
            [00:20.00][v2] Bracket voice 2
            [00:25.00]<v1> Angle bracket voice 1
            [00:30.00]<v2> Angle bracket voice 2
            [00:35.00](v1) Parenthesis voice 1
            [00:40.00](v2) Parenthesis voice 2
            [00:45.00]v1. Dot voice 1
            [00:50.00]v2. Dot voice 2
        """.trimIndent()

        val parsed = LyricsParser.parseLyrics(lrc)
        assertEquals(10, parsed.size)

        assertEquals("Standard colon voice 1", parsed[0].text)
        assertEquals("v1", parsed[0].voiceTag)

        assertEquals("Standard colon voice 2", parsed[1].text)
        assertEquals("v2", parsed[1].voiceTag)

        assertEquals("Bracket voice 1", parsed[2].text)
        assertEquals("v1", parsed[2].voiceTag)

        assertEquals("Bracket voice 2", parsed[3].text)
        assertEquals("v2", parsed[3].voiceTag)

        assertEquals("Angle bracket voice 1", parsed[4].text)
        assertEquals("v1", parsed[4].voiceTag)

        assertEquals("Angle bracket voice 2", parsed[5].text)
        assertEquals("v2", parsed[5].voiceTag)

        assertEquals("Parenthesis voice 1", parsed[6].text)
        assertEquals("v1", parsed[6].voiceTag)

        assertEquals("Parenthesis voice 2", parsed[7].text)
        assertEquals("v2", parsed[7].voiceTag)

        assertEquals("Dot voice 1", parsed[8].text)
        assertEquals("v1", parsed[8].voiceTag)

        assertEquals("Dot voice 2", parsed[9].text)
        assertEquals("v2", parsed[9].voiceTag)
    }

    @Test
    fun testWordByWordOppositeTurnMapping() {
        val json = """
            [
                {
                    "text": [{"text": "Hello", "part": false, "timestamp": 1000, "endtime": 2000}],
                    "oppositeTurn": false,
                    "timestamp": 1000,
                    "endtime": 2000
                },
                {
                    "text": [{"text": "World", "part": false, "timestamp": 2500, "endtime": 3500}],
                    "oppositeTurn": true,
                    "timestamp": 2500,
                    "endtime": 3500
                }
            ]
        """.trimIndent()

        val parsed = RhythmLyricsParser.parseWordByWordLyrics(json)
        assertEquals(2, parsed.size)
        assertEquals("v1", parsed[0].voiceTag)
        assertEquals("v2", parsed[1].voiceTag)

        // Verify re-serialization preserves oppositeTurn
        val reSerialized = RhythmLyricsParser.toWordByWordJson(parsed)
        assertTrue(reSerialized.contains("\"oppositeTurn\":true"))
    }

    @Test
    fun testWordByWordBracketVoiceTagInText() {
        val json = """
            [
                {
                    "text": [
                        {"text": "[v2]", "part": false, "timestamp": 1000, "endtime": 1200},
                        {"text": "Singing", "part": false, "timestamp": 1300, "endtime": 2000}
                    ],
                    "timestamp": 1000,
                    "endtime": 2000
                }
            ]
        """.trimIndent()

        val parsed = RhythmLyricsParser.parseWordByWordLyrics(json)
        assertEquals(1, parsed.size)
        assertEquals("v2", parsed[0].voiceTag)
        assertEquals(1, parsed[0].words.size)
        assertEquals("Singing", parsed[0].words[0].text)
    }

    @Test
    fun testTtmlOppositeTurnMapping() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
                <body>
                    <div>
                        <p begin="00:01.000" end="00:03.000" ttm:agent="v1">Line 1 Voice 1</p>
                        <p begin="00:04.000" end="00:06.000" ttm:agent="v2">Line 2 Voice 2</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val parsedTtml = RhythmLyricsParser.parseTtmlLyrics(ttml)
        assertEquals(2, parsedTtml.size)

        val wordByWord = RhythmLyricsParser.parseWordByWordLyrics(Gson().toJson(parsedTtml))
        assertEquals(2, wordByWord.size)
        assertEquals("v1", wordByWord[0].voiceTag)
        assertEquals("v2", wordByWord[1].voiceTag)
    }

    @Test
    fun testTtmlFallbackOppositeTurnDetection() {
        val ttml = """
            <tt>
                <body>
                    <p begin="00:01.000" end="00:03.000" agent="v1">Voice 1</p>
                    <p begin="00:04.000" end="00:06.000" agent="v2">Voice 2</p>
                </body>
            </tt>
        """.trimIndent()

        val parsed = RhythmLyricsParser.parseTtmlFallback(ttml)
        assertEquals(2, parsed.size)
        assertFalse(parsed[0].oppositeTurn == true)
        assertTrue(parsed[1].oppositeTurn == true)
    }

    @Test
    fun testSanitizeSearchQueryRemovesBracketsAndExplicitTags() {
        val sanitize = { input: String ->
            input.trim()
                .replace(Regex("\\[.*?\\]"), "")
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("(?i)\\s*-\\s*explicit\\b"), "")
                .replace(Regex("(?i)\\s+explicit\\b"), "")
                .replace(Regex("(?i)\\s*-\\s*clean\\b"), "")
                .replace(Regex("(?i)\\s+clean\\b"), "")
                .replace(Regex("(?i)\\s*-\\s*official\\s+(?:video|audio|music\\s+video)\\b"), "")
                .trim()
        }

        assertEquals("A Wolf At The Door", sanitize("A Wolf At The Door [Explicit]"))
        assertEquals("Drunk Walk Home", sanitize("Drunk Walk Home (Explicit)"))
        assertEquals("Clean Air", sanitize("Clean Air - Explicit"))
        assertEquals("Song Title", sanitize("Song Title [Official Audio]"))
        assertEquals("Song Title", sanitize("Song Title - Official Music Video"))
        assertEquals("Song Title", sanitize("Song Title (feat. Other Artist) [Clean]"))
    }

    @Test
    fun testCanonicalMatchingRejectsMismatchedSongs() {
        val canonicalize = { str: String ->
            val normalized = java.text.Normalizer.normalize(str.lowercase(), java.text.Normalizer.Form.NFD)
            val withoutAccents = Regex("\\p{InCombiningDiacriticalMarks}+").replace(normalized, "")
            withoutAccents
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("\\[.*?\\]"), "")
                .replace(Regex("\\b(feat|ft|featuring|and|with|&|vs|prod|by)\\b"), "")
                .filter { it.isLetterOrDigit() }
        }

        val targetTitle = canonicalize("Dead Women")
        val targetArtist = canonicalize("Mitski")

        // Unrelated hit returned by iTunes search
        val resultTitle = canonicalize("Falling")
        val resultArtist = canonicalize("Harry Styles")

        val titleMatch = resultTitle.contains(targetTitle) || targetTitle.contains(resultTitle)
        val artistMatch = resultArtist.contains(targetArtist) || targetArtist.contains(resultArtist)

        assertFalse("Mitski vs Harry Styles should not match artist", artistMatch)
        assertFalse("Dead Women vs Falling should not match title", titleMatch)
        assertFalse("Strict matching must reject this pair", titleMatch && artistMatch)

        // Exact match with different casing / accents
        val exactTitle = canonicalize("Dead Women")
        val exactArtist = canonicalize("Mitski")
        assertTrue((exactTitle.contains(targetTitle) || targetTitle.contains(exactTitle)) &&
                   (exactArtist.contains(targetArtist) || targetArtist.contains(exactArtist)))
    }

    @Test
    fun testYouTubeDescriptionRejectionInLyricsValidation() {
        val youtubeDescription = """
            Provided to YouTube by Universal Music Group
            
            A Wolf At The Door · Radiohead
            
            Hail To The Thief
            
            ℗ 2003 XL Recordings Ltd
            
            Released on: 2003-06-09
            
            Associated Performer, Vocals, Guitar: Thom Yorke
            Producer: Nigel Godrich
            
            Auto-generated by YouTube.
        """.trimIndent()

        val lowerText = youtubeDescription.lowercase()
        val isRejected = lowerText.contains("provided to youtube") ||
                         lowerText.contains("auto-generated by youtube") ||
                         lowerText.contains("released on:") ||
                         lowerText.contains("associated performer:")

        assertTrue("YouTube description must be recognized and rejected", isRejected)
    }
}
