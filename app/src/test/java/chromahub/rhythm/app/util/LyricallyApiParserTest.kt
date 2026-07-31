package chromahub.rhythm.app.util

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricallyApiParserTest {

    @Test
    fun appleV2_keepsCjkWordsAndTimedTranslationAndRomanization() {
        val payload = JsonParser.parseString(
            """
            {
              "provider": "apple_music",
              "syncType": "Syllable",
              "lyrics": [
                {
                  "timestamp": 1000,
                  "endtime": 1800,
                  "text": [
                    {"text": "君", "timestamp": 1000, "endtime": 1300, "part": true},
                    {"text": "が", "timestamp": 1300, "endtime": 1500, "part": true},
                    {"text": "好き", "timestamp": 1500, "endtime": 1800, "part": false}
                  ]
                }
              ],
              "metadata": {
                "translations": [
                  {
                    "lang": "en",
                    "lines": [{"timestamp": 1000, "text": "I love you"}]
                  }
                ],
                "transliterations": [
                  {
                    "lang": "ja-Latn",
                    "lines": [{"timestamp": 1000, "text": "kimi ga suki"}]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val lyrics = LyricallyApiParser.parseLyricsResponse(
            payload = payload,
            sourceName = "Lyrically (Apple Music)",
            preferredTranslationLanguage = "en"
        )

        assertNotNull(lyrics)
        assertEquals("君が好き", lyrics?.plainLyrics)
        assertTrue(lyrics?.syncedLyrics.orEmpty().contains("(I love you)"))
        assertTrue(lyrics?.syncedLyrics.orEmpty().contains("[kimi ga suki]"))
        assertTrue(lyrics?.hasWordByWordLyrics() == true)

        val parsedWordByWord = RhythmLyricsParser.parseWordByWordLyrics(
            lyrics?.wordByWordLyrics.orEmpty()
        )
        assertEquals("I love you", parsedWordByWord.single().translation)
        assertEquals("kimi ga suki", parsedWordByWord.single().romanization)
    }

    @Test
    fun collapsedNormalizedTimeline_fallsBackToProvidersValidLrc() {
        val payload = JsonParser.parseString(
            """
            {
              "syncType": "Line",
              "lyrics": [
                {"timestamp": 0, "text": "第一行"},
                {"timestamp": 0, "text": "第二行"}
              ],
              "metadata": {
                "lrc": "[00:02.50]第一行\n[00:07.90]第二行"
              }
            }
            """.trimIndent()
        )

        val lyrics = LyricallyApiParser.parseLyricsResponse(payload, "Apple")

        assertEquals("[00:02.50]第一行\n[00:07.90]第二行", lyrics?.syncedLyrics)
        assertTrue(lyrics?.hasUsableSyncedTimeline() == true)
    }

    @Test
    fun collapsedTimelineWithoutFallback_isNotAdvertisedAsSyncedLyrics() {
        val payload = JsonParser.parseString(
            """
            {
              "syncType": "Line",
              "lyrics": [
                {"timestamp": 0, "text": "第一行"},
                {"timestamp": 0, "text": "第二行"}
              ]
            }
            """.trimIndent()
        )

        assertNull(LyricallyApiParser.parseLyricsResponse(payload, "Apple"))
    }

    @Test
    fun youtubeV2_usesMetadataLrcWhenNormalizedArrayIsEmpty() {
        val payload = JsonParser.parseString(
            """
            {
              "provider": "youtube",
              "syncType": "Line",
              "lyrics": [],
              "metadata": {
                "lrc": "[00:01.00]光\n[00:03.00]君を待つ"
              }
            }
            """.trimIndent()
        )

        val lyrics = LyricallyApiParser.parseLyricsResponse(payload, "Lyrically (YouTube)")

        assertEquals("光\n君を待つ", lyrics?.plainLyrics)
        assertEquals("[00:01.00]光\n[00:03.00]君を待つ", lyrics?.syncedLyrics)
    }

    @Test
    fun neteaseV2_extractsNativeOriginalTranslationAndRomajiTracks() {
        val payload = JsonParser.parseString(
            """
            {
              "provider": "netease",
              "syncType": "Line",
              "lyrics": [],
              "metadata": {
                "rawData": {
                  "lrc": {"lyric": "[00:01.00]君が好き"},
                  "tlyric": {"lyric": "[00:01.00]I love you"},
                  "romalrc": {"lyric": "[00:01.00]kimi ga suki"}
                }
              }
            }
            """.trimIndent()
        )

        val lyrics = LyricallyApiParser.parseLyricsResponse(payload, "Lyrically (NetEase)")
        val parsed = LyricsParser.parseLyrics(lyrics?.syncedLyrics.orEmpty()).single()

        assertEquals("君が好き", parsed.text)
        assertEquals("I love you", parsed.translation)
        assertEquals("kimi ga suki", parsed.romanization)
    }

    @Test
    fun legacyJsonString_isAcceptedInsteadOfFailingObjectDeserialization() {
        val payload = JsonParser.parseString(
            "\"[00:01.00]First line\\n[00:03.00]Second line\""
        )

        val lyrics = LyricallyApiParser.parseLyricsResponse(payload, "Lyrically (YouTube)")

        assertEquals("First line\nSecond line", lyrics?.plainLyrics)
        assertFalse(lyrics?.syncedLyrics.isNullOrBlank())
    }

    @Test
    fun legacyLineTextString_isNormalizedToOneTimedWord() {
        val payload = JsonParser.parseString(
            """
            {
              "type": "Line",
              "content": [
                {"timestamp": 1000, "endtime": 2000, "text": "A whole line"}
              ]
            }
            """.trimIndent()
        )

        val lyrics = LyricallyApiParser.parseLyricsResponse(payload, "Legacy")

        assertEquals("A whole line", lyrics?.plainLyrics)
        assertTrue(lyrics?.syncedLyrics.orEmpty().startsWith("[00:01.00]A whole line"))
    }

    @Test
    fun providerErrorObject_returnsNoLyricsAndAllowsFallback() {
        val payload = JsonParser.parseString(
            """{"provider":"musixmatch","error":{"code":400,"message":"Unavailable"}}"""
        )

        assertNull(LyricallyApiParser.parseLyricsResponse(payload, "Musixmatch"))
    }

    @Test
    fun genericSearch_acceptsNumericIdsWithoutRigidRetrofitMapping() {
        val payload = JsonParser.parseString(
            """
            [
              {"id": 9657574, "name": "Hikari", "artist": "Hikaru Utada"}
            ]
            """.trimIndent()
        )

        val result = LyricallyApiParser.parseGenericSearch(payload).single()

        assertEquals("9657574", result.getCanonicalId())
        assertEquals("Hikari", result.getCanonicalName())
        assertEquals("Hikaru Utada", result.getCanonicalArtist())
    }
}
