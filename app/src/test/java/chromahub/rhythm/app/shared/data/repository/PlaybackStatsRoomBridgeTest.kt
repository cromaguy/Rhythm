/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStatsRoomBridgeTest {

    @Test
    fun mapEventsToEntities_aggregatesSpansAndSongEngagementsCorrectly() {
        val events = listOf(
            PlaybackStatsRepository.PlaybackEvent(
                songId = "song_a",
                timestamp = 100_000L,
                durationMs = 45_000L,
                startTimestamp = 55_000L,
                endTimestamp = 100_000L,
                songTitle = "Track A",
                artistName = "Artist A",
                albumName = "Album A",
                genre = "Electronic"
            ),
            PlaybackStatsRepository.PlaybackEvent(
                songId = "song_a",
                timestamp = 220_000L,
                durationMs = 15_000L,
                songTitle = "Track A",
                artistName = "Artist A",
                albumName = "Album A",
                genre = "Electronic"
            ),
            PlaybackStatsRepository.PlaybackEvent(
                songId = "",
                timestamp = 300_000L,
                durationMs = 20_000L
            )
        )

        val mapped = PlaybackStatsRoomBridge.mapEventsToEntities(events)
        assertEquals(2, mapped.spans.size)
        assertEquals(true, mapped.spans[0].completed)
        assertEquals(false, mapped.spans[1].completed)

        assertEquals(1, mapped.engagements.size)
        val eng = mapped.engagements[0]
        assertEquals("song_a", eng.songId)
        assertEquals(2L, eng.playCount)
        assertEquals(60_000L, eng.totalPlayDurationMs)
        assertEquals(220_000L, eng.lastPlayedAtMs)
    }
}
