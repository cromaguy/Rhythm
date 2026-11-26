/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.discovery

import android.net.TestUri
import chromahub.rhythm.app.features.local.domain.library.DuplicateFinder
import chromahub.rhythm.app.features.local.domain.library.LibraryAudioStats
import chromahub.rhythm.app.infrastructure.network.scrobble.MultiScrobblerManager
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.shared.data.repository.ListeningPersonality
import chromahub.rhythm.app.shared.data.repository.ListeningRecapMath
import chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DiscoveryAndRecapTest {

    private fun song(
        id: String,
        title: String,
        artist: String,
        duration: Long = 200_000L,
        genre: String? = null,
        year: Int = 2020,
        codec: String? = "MP3",
        sampleRate: Int? = 44_100,
        bitrate: Int? = 320_000,
        channels: Int? = 2
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        duration = duration,
        uri = TestUri("file", "/music/$id.audio"),
        genre = genre,
        year = year,
        codec = codec,
        sampleRate = sampleRate,
        bitrate = bitrate,
        channels = channels
    )

    @Test
    fun duplicateFinderClustersSameDurationAndRecommendsHiResLossless() {
        val mp3Copy = song("1", "Midnight City", "M83", duration = 243_000L, codec = "MP3", sampleRate = 44_100, bitrate = 192_000)
        val flacCopy = song("2", " midnight  city ", "m83", duration = 244_500L, codec = "FLAC", sampleRate = 96_000, bitrate = 2_800_000)
        val liveCut = song("3", "Midnight City", "M83", duration = 340_000L, codec = "FLAC", sampleRate = 48_000)

        val groups = DuplicateFinder.findDuplicates(listOf(mp3Copy, flacCopy, liveCut))
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].songs.size)
        assertEquals("2", groups[0].recommendedKeepId)

        val stats = LibraryAudioStats.computeBreakdown(listOf(mp3Copy, flacCopy, liveCut))
        assertEquals(3, stats.totalTracks)
        assertEquals(2, stats.losslessTracks)
        assertEquals(1, stats.hiResTracks)
        assertEquals(1, stats.lossyTracks)
    }

    @Test
    fun recapMathClassifiesPersonalityDominantDecadeAndHourlyBuckets() {
        val zone = ZoneId.of("UTC")
        val nightStart = ZonedDateTime.of(2026, 6, 15, 23, 0, 0, 0, zone).toInstant().toEpochMilli()
        val nextHour = ZonedDateTime.of(2026, 6, 16, 1, 0, 0, 0, zone).toInstant().toEpochMilli()

        val events = listOf(
            PlaybackStatsRepository.PlaybackEvent(
                songId = "s1",
                timestamp = nextHour,
                durationMs = nextHour - nightStart,
                startTimestamp = nightStart,
                endTimestamp = nextHour,
                songTitle = "Starlight",
                artistName = "Muse",
                albumName = "Black Holes",
                genre = "Rock"
            )
        )
        val library = listOf(song("s1", "Starlight", "Muse", year = 2006))
        val recap = ListeningRecapMath.buildRecap(2026, events, library, zone, nextHour + 1_000L)
        assertNotNull(recap)
        assertEquals(ListeningPersonality.NIGHT_OWL, recap!!.personality)
        assertEquals(2000, recap.topDecade?.decade)
        assertTrue(recap.hourlyDurationsMs[23] > 0L)
        assertTrue(recap.hourlyDurationsMs[0] > 0L)
    }

    @Test
    fun multiScrobblerFormatsListenBrainzAndSignsLibreFm() {
        val json = MultiScrobblerManager.buildListenBrainzPayload(
            listenType = "single",
            artist = "Portishead",
            track = "Roads",
            album = "Dummy",
            listenedAtSec = 1_700_000_000L
        )
        assertTrue(json.contains("\"listen_type\":\"single\""))
        assertTrue(json.contains("\"listened_at\":1700000000"))
        assertTrue(json.contains("\"artist_name\":\"Portishead\""))
        assertTrue(json.contains("\"media_player\":\"Rhythm\""))

        val sigA = MultiScrobblerManager.signGnuFm(
            mapOf("method" to "track.scrobble", "artist[0]" to "Muse"),
            "secret"
        )
        val sigB = MultiScrobblerManager.signGnuFm(
            mapOf("artist[0]" to "Muse", "method" to "track.scrobble"),
            "secret"
        )
        assertEquals(32, sigA.length)
        assertEquals(sigA, sigB)
    }
}
