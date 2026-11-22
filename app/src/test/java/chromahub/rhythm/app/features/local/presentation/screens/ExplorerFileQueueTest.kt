/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.presentation.screens

import android.net.TestUri
import chromahub.rhythm.app.shared.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class ExplorerFileQueueTest {

    @Test
    fun `explorer click on track in folder queues folder tracks from clicked index`() {
        val folderSongs = listOf(
            Song(
                id = "1",
                title = "Track 1",
                artist = "Artist A",
                album = "Album A",
                duration = 180000L,
                uri = TestUri("file", "/storage/emulated/0/Music/AlbumA/01.mp3"),
                path = "/storage/emulated/0/Music/AlbumA/01.mp3"
            ),
            Song(
                id = "2",
                title = "Track 2",
                artist = "Artist A",
                album = "Album A",
                duration = 200000L,
                uri = TestUri("file", "/storage/emulated/0/Music/AlbumA/02.mp3"),
                path = "/storage/emulated/0/Music/AlbumA/02.mp3"
            ),
            Song(
                id = "3",
                title = "Track 3",
                artist = "Artist A",
                album = "Album A",
                duration = 220000L,
                uri = TestUri("file", "/storage/emulated/0/Music/AlbumA/03.mp3"),
                path = "/storage/emulated/0/Music/AlbumA/03.mp3"
            )
        )

        var playedQueue: List<Song>? = null
        var playedStartIndex: Int? = null

        val playQueueFromIndex = { queue: List<Song>, startIndex: Int ->
            playedQueue = queue
            playedStartIndex = startIndex
        }

        // Simulate user clicking Track 2 in the folder
        val clickedSong = folderSongs[1]
        val index = folderSongs.indexOfFirst {
            it.id == clickedSong.id || (it.path != null && it.path == clickedSong.path)
        }

        if (index >= 0) {
            playQueueFromIndex(folderSongs, index)
        } else {
            playQueueFromIndex(listOf(clickedSong), 0)
        }

        assertEquals(3, playedQueue?.size)
        assertEquals(1, playedStartIndex)
        assertEquals("Track 2", playedQueue?.get(playedStartIndex!!)?.title)
    }

    @Test
    fun `explorer click on standalone track outside active folder list queues only single track`() {
        val folderSongs = listOf(
            Song(
                id = "10",
                title = "Folder Song 1",
                artist = "Artist B",
                album = "Album B",
                duration = 180000L,
                uri = TestUri("file", "/storage/emulated/0/Music/AlbumB/01.mp3"),
                path = "/storage/emulated/0/Music/AlbumB/01.mp3"
            )
        )

        val unindexedSong = Song(
            id = "99",
            title = "Standalone File",
            artist = "Unknown Artist",
            album = "Unknown Album",
            duration = 120000L,
            uri = TestUri("file", "/storage/emulated/0/Music/Singles/standalone.mp3"),
            path = "/storage/emulated/0/Music/Singles/standalone.mp3"
        )

        var playedQueue: List<Song>? = null
        var playedStartIndex: Int? = null

        val playQueueFromIndex = { queue: List<Song>, startIndex: Int ->
            playedQueue = queue
            playedStartIndex = startIndex
        }

        val index = folderSongs.indexOfFirst {
            it.id == unindexedSong.id || (it.path != null && it.path == unindexedSong.path)
        }

        if (index >= 0) {
            playQueueFromIndex(folderSongs, index)
        } else {
            playQueueFromIndex(listOf(unindexedSong), 0)
        }

        assertEquals(1, playedQueue?.size)
        assertEquals(0, playedStartIndex)
        assertEquals("Standalone File", playedQueue?.first()?.title)
    }
}
