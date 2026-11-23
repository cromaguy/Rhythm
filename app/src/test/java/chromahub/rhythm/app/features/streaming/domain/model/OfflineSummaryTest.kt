/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSummaryTest {

    private data class Track(val id: String, val codec: String?)

    private fun summary(tracks: List<Track>, downloaded: Set<String>) =
        OfflineSummary.of(tracks, downloaded, id = { it.id }, codec = { it.codec })

    @Test
    fun of_countsDistinctTracksAndLosslessDownloads() {
        val tracks = listOf(Track("a", "flac"), Track("b", "mp3"), Track("c", "flac"), Track("a", "flac"))

        val s = summary(tracks, downloaded = setOf("a", "b"))

        assertEquals(OfflineSummary(total = 3, downloaded = 2, losslessDownloaded = 1), s)
        assertTrue(s.isPartial)
        assertFalse(s.isComplete)
        assertFalse(s.isAllLossless)
    }

    @Test
    fun of_completeAndAllLossless() {
        val tracks = listOf(Track("a", "FLAC"), Track("b", "alac"))

        val s = summary(tracks, downloaded = setOf("a", "b"))

        assertTrue(s.isComplete)
        assertFalse(s.isPartial)
        assertTrue(s.isAllLossless)
    }

    @Test
    fun of_emptyPlaylistIsNeitherCompleteNorPartial() {
        val s = summary(emptyList(), downloaded = setOf("a"))

        assertFalse(s.isComplete)
        assertFalse(s.isPartial)
        assertEquals(OfflineSummary.NONE, s)
    }

    @Test
    fun isLossless_recognisesCodecsCaseInsensitivelyAndMimeTypes() {
        assertTrue(AudioQuality.isLossless("flac"))
        assertTrue(AudioQuality.isLossless("FLAC"))
        assertTrue(AudioQuality.isLossless("audio/flac"))
        assertTrue(AudioQuality.isLossless(" alac "))
        assertFalse(AudioQuality.isLossless("mp3"))
        assertFalse(AudioQuality.isLossless("aac"))
        assertFalse(AudioQuality.isLossless(null))
        assertFalse(AudioQuality.isLossless(""))
    }

    @Test
    fun label_losslessShowsSampleRateNotBitrate() {
        assertEquals("FLAC 44.1k", AudioQuality.label("flac", 1_050_000, 44_100))
        assertEquals("FLAC 96k", AudioQuality.label("flac", null, 96_000))
        assertEquals("FLAC", AudioQuality.label("flac", 900_000, null))
    }

    @Test
    fun label_lossyShowsFormatAndKbps() {
        assertEquals("MP3 320", AudioQuality.label("mp3", 320_000, 44_100))
        assertEquals("OPUS", AudioQuality.label("opus", null, null))
        assertEquals("256 kbps", AudioQuality.label(null, 256_000, null))
        assertNull(AudioQuality.label(null, null, null))
        assertNull(AudioQuality.label("", 0, 0))
    }
}
