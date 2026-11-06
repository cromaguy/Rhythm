// SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
// SPDX-License-Identifier: GPL-3.0-or-later
package chromahub.rhythm.app.offline.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineFilesCoversTest {
    @Test
    fun anAlbumCoverIsNamedByTheAlbumIdWhenThereIsOne() {
        assertEquals("subsonic__album__abc_album_art.jpg", OfflineFiles.albumCoverName("SUBSONIC::album::ABC", "A", "B"))
    }

    @Test
    fun withoutAnAlbumIdItIsNamedByArtistAndAlbum() {
        assertEquals("kanye_west_graduation_album_art.jpg", OfflineFiles.albumCoverName(null, "Kanye West", "Graduation"))
    }

    @Test
    fun aSongsOwnCoverKeepsTheCaseOfItsId() {
        assertEquals("SUBSONIC__Xy_art.jpg", OfflineFiles.songCoverName("SUBSONIC::Xy"))
    }

    @Test
    fun coversNoDownloadedSongNeedsAreOrphans() {
        val keepAlbums = setOf(OfflineFiles.albumCoverName("SUBSONIC::album::a", "x", "y"))
        val keepSongs = setOf(OfflineFiles.songCoverName("SUBSONIC::s1"))
        val names = listOf(
            "subsonic__album__a_album_art.jpg", // still needed
            "subsonic__album__gone_album_art.jpg", // its album left
            "SUBSONIC__s1_art.jpg", // still needed
            "SUBSONIC__s2_art.jpg", // its song left
            "SUBSONIC__s1.opus", // audio is never a cover
            "download_state.json",
        )
        assertEquals(
            listOf("subsonic__album__gone_album_art.jpg", "SUBSONIC__s2_art.jpg"),
            OfflineFiles.orphanedCovers(names, keepAlbums, keepSongs),
        )
    }
}
