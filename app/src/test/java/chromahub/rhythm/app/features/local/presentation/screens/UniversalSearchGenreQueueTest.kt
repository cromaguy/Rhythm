/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens

import android.net.TestUri
import chromahub.rhythm.app.shared.data.model.Song
import chromahub.rhythm.app.util.GenreUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSearchGenreQueueTest {

    private fun createSong(id: String, title: String, genre: String): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            duration = 180000L,
            uri = TestUri("file", "/storage/emulated/0/Music/dummy$id.mp3"),
            genre = genre
        )
    }

    @Test
    fun testGenreSelectionScopingQueuesOnlyMatchingGenreSongs() {
        val rock1 = createSong("1", "Rock Anthem", "Rock")
        val rock2 = createSong("2", "Hard Rocker", "Classic Rock")
        val jazz1 = createSong("3", "Smooth Jazz", "Jazz")
        val pop1 = createSong("4", "Pop Beat", "Pop")
        val metal1 = createSong("5", "Metal Riff", "Rock / Metal")

        val library = listOf(rock1, rock2, jazz1, pop1, metal1)

        // User clicks "Rock" in Genre Browse section
        val targetGenre = "rock"
        val matchedSongs = library.filter { song ->
            GenreUtils.matchesGenreQuery(song.genre, targetGenre)
        }

        // Must match rock1, rock2, metal1
        assertEquals("Should match exactly 3 rock-related songs", 3, matchedSongs.size)
        assertTrue(matchedSongs.contains(rock1))
        assertTrue(matchedSongs.contains(rock2))
        assertTrue(matchedSongs.contains(metal1))
        assertFalse(matchedSongs.contains(jazz1))
        assertFalse(matchedSongs.contains(pop1))

        // When user clicks rock2 in search/genre results:
        val clickedSong = rock2
        val startIndex = matchedSongs.indexOfFirst { it.id == clickedSong.id }
        assertEquals("Start index should be 1 within genre queue", 1, startIndex)

        // Queue must NOT contain entire library
        assertEquals(3, matchedSongs.size)
        assertEquals(5, library.size)
    }

    @Test
    fun testSearchContextQueueResolution() {
        fun resolveQueue(song: Song, contextSongs: List<Song>, fullLibrary: List<Song>): Pair<List<Song>, Int> {
            val resolvedList = if (contextSongs.isNotEmpty()) contextSongs else fullLibrary
            val index = resolvedList.indexOfFirst { it.id == song.id }
            return Pair(resolvedList, if (index >= 0) index else 0)
        }

        val s1 = createSong("1", "Song 1", "Jazz")
        val s2 = createSong("2", "Song 2", "Jazz")
        val s3 = createSong("3", "Song 3", "Pop")
        val fullLibrary = listOf(s1, s2, s3)
        val genreContext = listOf(s1, s2)

        // When playing s2 from genre results
        val (queue, startIndex) = resolveQueue(s2, genreContext, fullLibrary)

        assertEquals("Queue should be scoped to genre context", 2, queue.size)
        assertEquals("Queue songs should match genre context", genreContext, queue)
        assertEquals("Start index must match s2 in genre context", 1, startIndex)
        assertFalse("Queue must not contain non-genre song s3", queue.contains(s3))
    }
}
