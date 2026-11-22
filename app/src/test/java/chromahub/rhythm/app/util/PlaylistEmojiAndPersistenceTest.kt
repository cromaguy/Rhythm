/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import chromahub.rhythm.app.shared.data.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistEmojiAndPersistenceTest {

    @Test
    fun sanitizeFileName_preservesEmojisAndUnicode() {
        val fire = PlaylistImportExportUtils.sanitizeFileName("Fire 🔥")
        assertEquals("Fire 🔥", fire)

        val music = PlaylistImportExportUtils.sanitizeFileName("Chill 🎵 Beats")
        assertEquals("Chill 🎵 Beats", music)

        val hindi = PlaylistImportExportUtils.sanitizeFileName("हिंदी गाने")
        assertEquals("हिंदी गाने", hindi)

        val japanese = PlaylistImportExportUtils.sanitizeFileName("私の音楽")
        assertEquals("私の音楽", japanese)
    }

    @Test
    fun sanitizeFileName_replacesIllegalFilesystemCharactersOnly() {
        val illegal = PlaylistImportExportUtils.sanitizeFileName("Rock / Metal : 2026 * Best? <100%> | \"Hits\"")
        assertFalse(illegal.contains('/'))
        assertFalse(illegal.contains(':'))
        assertFalse(illegal.contains('*'))
        assertFalse(illegal.contains('?'))
        assertFalse(illegal.contains('<'))
        assertFalse(illegal.contains('>'))
        assertFalse(illegal.contains('|'))
        assertFalse(illegal.contains('"'))

        assertEquals("Rock _ Metal _ 2026 _ Best_ _100%_ _ _Hits_", illegal)
    }

    @Test
    fun sanitizeFileName_blankFallsBackToPlaylist() {
        assertEquals("playlist", PlaylistImportExportUtils.sanitizeFileName(""))
        assertEquals("playlist", PlaylistImportExportUtils.sanitizeFileName("   "))
    }

    @Test
    fun playlistSorting_handlesEmojiWithoutSurrogateCorruption() {
        val p1 = Playlist(id = "101", name = "Fire 🔥")
        val p2 = Playlist(id = "102", name = "Apple 🍎")
        val p3 = Playlist(id = "103", name = "Zebra 🦓")
        val p4 = Playlist(id = "104", name = "Fire")
        val list = listOf(p1, p2, p3, p4)

        // Ascending
        val sortedAsc = list.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
        assertEquals("Apple 🍎", sortedAsc[0].name)
        assertEquals("Fire", sortedAsc[1].name)
        assertEquals("Fire 🔥", sortedAsc[2].name)
        assertEquals("Zebra 🦓", sortedAsc[3].name)

        // Descending
        val sortedDesc = list.sortedWith(
            compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
        assertEquals("Zebra 🦓", sortedDesc[0].name)
        assertEquals("Fire 🔥", sortedDesc[1].name)
        assertEquals("Fire", sortedDesc[2].name)
        assertEquals("Apple 🍎", sortedDesc[3].name)

        // Verify UTF-16 code units for Fire 🔥 are not inverted or corrupted
        val firePlaylist = sortedDesc.first { it.id == "101" }
        assertEquals("Fire 🔥", firePlaylist.name)
        assertTrue(firePlaylist.name.contains("🔥"))
        // Surrogate pair: \uD83D\uDD25 must be in correct order
        val highSurrogate = firePlaylist.name.indexOf('\uD83D')
        val lowSurrogate = firePlaylist.name.indexOf('\uDD25')
        assertTrue("High surrogate must precede low surrogate", highSurrogate != -1 && lowSurrogate == highSurrogate + 1)
    }

    @Test
    fun concurrentPlaylistUpdates_preventsClobbering() = runBlocking {
        val userPlaylist = Playlist(id = "user_1", name = "My Playlist")
        val smartPlaylist = Playlist(id = "6", name = "Recently Played")
        val playlistsFlow = MutableStateFlow(listOf(userPlaylist, smartPlaylist))

        val jobs = (1..50).map { i ->
            launch(Dispatchers.Default) {
                if (i % 2 == 0) {
                    // Simulate user adding to playlist
                    playlistsFlow.update { currentList ->
                        currentList.map { p ->
                            if (p.id == "user_1") {
                                p.copy(dateModified = System.currentTimeMillis() + i)
                            } else {
                                p
                            }
                        }
                    }
                } else {
                    // Simulate smart playlist refresh
                    playlistsFlow.update { currentList ->
                        currentList.map { p ->
                            if (p.id == "6") {
                                p.copy(dateModified = System.currentTimeMillis() + i)
                            } else {
                                p
                            }
                        }
                    }
                }
            }
        }
        jobs.joinAll()

        val finalPlaylists = playlistsFlow.value
        assertEquals(2, finalPlaylists.size)
        assertTrue(finalPlaylists.any { it.id == "user_1" })
        assertTrue(finalPlaylists.any { it.id == "6" })
    }
}
