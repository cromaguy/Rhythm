/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddedLyricsExtractionTest {

    @Test
    fun `extractLyricsFromPropertyMap extracts lyrics from Vorbis UNSYNCEDLYRICS and SYNCEDLYRICS`() {
        val vorbisMap = mapOf(
            "TITLE" to arrayOf("Sample Title"),
            "UNSYNCEDLYRICS" to arrayOf("Line 1\nLine 2\nLine 3")
        )
        val extracted = MediaUtils.extractLyricsFromPropertyMap(vorbisMap)
        assertEquals("Line 1\nLine 2\nLine 3", extracted)

        val syncedMap = mapOf(
            "SYNCEDLYRICS" to arrayOf("[00:10.00] Synced line")
        )
        assertEquals("[00:10.00] Synced line", MediaUtils.extractLyricsFromPropertyMap(syncedMap))
    }

    @Test
    fun `extractLyricsFromPropertyMap extracts lyrics from MP4 atom tags`() {
        // Standard iTunes atom
        val mp4Map = mapOf(
            "©lyr" to arrayOf("MP4 embedded lyrics\nChorus")
        )
        assertEquals("MP4 embedded lyrics\nChorus", MediaUtils.extractLyricsFromPropertyMap(mp4Map))

        // Unicode encoded atom
        val unicodeMp4Map = mapOf(
            "\u00A9lyr" to arrayOf("Unicode MP4 lyrics")
        )
        assertEquals("Unicode MP4 lyrics", MediaUtils.extractLyricsFromPropertyMap(unicodeMp4Map))

        // Alternative atom name
        val lyrMap = mapOf(
            "lyr" to arrayOf("Alternative lyr atom")
        )
        assertEquals("Alternative lyr atom", MediaUtils.extractLyricsFromPropertyMap(lyrMap))
    }

    @Test
    fun `extractLyricsFromPropertyMap handles case-insensitive Vorbis comment tags`() {
        val lowerCaseMap = mapOf(
            "lyrics" to arrayOf("Lower case lyrics tag")
        )
        assertEquals("Lower case lyrics tag", MediaUtils.extractLyricsFromPropertyMap(lowerCaseMap))

        val mixedCaseMap = mapOf(
            "UnsyncedLyrics" to arrayOf("Mixed case unsynced lyrics")
        )
        assertEquals("Mixed case unsynced lyrics", MediaUtils.extractLyricsFromPropertyMap(mixedCaseMap))
    }

    @Test
    fun `extractLyricsFromPropertyMap extracts ID3 USLT and SYLT tags`() {
        val id3Map = mapOf(
            "USLT" to arrayOf("Unsynchronized lyric text")
        )
        assertEquals("Unsynchronized lyric text", MediaUtils.extractLyricsFromPropertyMap(id3Map))

        val syltMap = mapOf(
            "SYLT" to arrayOf("[00:05.00] Synchronized ID3 lyric")
        )
        assertEquals("[00:05.00] Synchronized ID3 lyric", MediaUtils.extractLyricsFromPropertyMap(syltMap))
    }

    @Test
    fun `extractLyricsFromPropertyMap returns null when no lyrics or empty strings present`() {
        val emptyMap = emptyMap<String, Array<String>>()
        assertNull(MediaUtils.extractLyricsFromPropertyMap(emptyMap))

        val blankMap = mapOf(
            "LYRICS" to arrayOf("   "),
            "©lyr" to arrayOf("")
        )
        assertNull(MediaUtils.extractLyricsFromPropertyMap(blankMap))

        val nonLyricsMap = mapOf(
            "TITLE" to arrayOf("Song"),
            "ARTIST" to arrayOf("Singer")
        )
        assertNull(MediaUtils.extractLyricsFromPropertyMap(nonLyricsMap))
    }
}
