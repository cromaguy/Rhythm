/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.local.data.repository

import android.net.TestUri
import chromahub.rhythm.app.shared.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDeduplicationGroupingTest {

    private fun createSong(
        id: String,
        title: String,
        artist: String,
        album: String,
        path: String?,
        albumArtist: String? = null,
        albumId: String = ""
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            albumArtist = albumArtist,
            duration = 180000L,
            uri = TestUri("file", path ?: "/storage/emulated/0/dummy.mp3"),
            path = path
        )
    }

    @Test
    fun testDownloadedTracksWithDisparateMediaStoreAlbumIdsInSameFolderGroupTogether() {
        val track1 = createSong(
            id = "1",
            title = "Strangers by Nature",
            artist = "Adele",
            album = "30",
            path = "/storage/emulated/0/Music/Adele - 30/01 - Strangers by Nature.mp3",
            albumArtist = null,
            albumId = "1001"
        )
        val track2 = createSong(
            id = "2",
            title = "Easy on Me",
            artist = "Adele feat. Erroll",
            album = "30",
            path = "/storage/emulated/0/Music/Adele - 30/02 - Easy on Me.mp3",
            albumArtist = null,
            albumId = "9999" // MediaStore assigned a disparate albumId
        )

        val key1 = MusicRepository.getAlbumGroupingKey(track1)
        val key2 = MusicRepository.getAlbumGroupingKey(track2)

        assertEquals("Tracks in dedicated album folder must share the same album grouping key", key1, key2)
        assertEquals("folder_/storage/emulated/0/music/adele - 30|30", key1)
    }

    @Test
    fun testMultiDiscAlbumWithDiscSubfoldersNormalizeToRootAlbumFolder() {
        val disc1Track = createSong(
            id = "1",
            title = "In the Flesh?",
            artist = "Pink Floyd",
            album = "The Wall",
            path = "/storage/emulated/0/Music/Pink Floyd - The Wall/Disc 1/01.mp3",
            albumArtist = "Pink Floyd"
        )
        val disc2Track = createSong(
            id = "2",
            title = "Hey You",
            artist = "Pink Floyd",
            album = "The Wall",
            path = "/storage/emulated/0/Music/Pink Floyd - The Wall/Disc 2/01.mp3",
            albumArtist = "Pink Floyd"
        )
        val cd1Track = createSong(
            id = "3",
            title = "Another Brick in the Wall",
            artist = "Pink Floyd",
            album = "The Wall",
            path = "/storage/emulated/0/Music/Pink Floyd - The Wall/CD 1/02.mp3",
            albumArtist = "Pink Floyd"
        )

        val folder1 = MusicRepository.getRootAlbumFolder(disc1Track.path)
        val folder2 = MusicRepository.getRootAlbumFolder(disc2Track.path)
        val folder3 = MusicRepository.getRootAlbumFolder(cd1Track.path)

        assertEquals("/storage/emulated/0/music/pink floyd - the wall", folder1)
        assertEquals(folder1, folder2)
        assertEquals(folder1, folder3)

        val key1 = MusicRepository.getAlbumGroupingKey(disc1Track)
        val key2 = MusicRepository.getAlbumGroupingKey(disc2Track)
        val key3 = MusicRepository.getAlbumGroupingKey(cd1Track)

        assertEquals(key1, key2)
        assertEquals(key1, key3)
    }

    @Test
    fun testGenericFolderDownloadsGroupedByPrimaryArtistAndDoNotFragment() {
        // Files downloaded directly to /storage/emulated/0/Download with missing albumArtist
        val track1 = createSong(
            id = "1",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "Greatest Hits",
            path = "/storage/emulated/0/Download/01_bohemian.mp3",
            albumArtist = null,
            albumId = "555"
        )
        val track2 = createSong(
            id = "2",
            title = "Under Pressure",
            artist = "Queen & David Bowie",
            album = "Greatest Hits",
            path = "/storage/emulated/0/Download/02_under_pressure.mp3",
            albumArtist = null,
            albumId = "777" // MediaStore generated different ID due to featuring artist
        )

        val key1 = MusicRepository.getAlbumGroupingKey(track1)
        val key2 = MusicRepository.getAlbumGroupingKey(track2)

        assertEquals("Both tracks must resolve to primary artist Queen and share grouping key", key1, key2)
        assertEquals("artist_queen|greatest hits", key1)
    }

    @Test
    fun testDifferentArtistsWithSameAlbumNameInGenericFolderDoNotCollide() {
        val queenTrack = createSong(
            id = "1",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "Greatest Hits",
            path = "/storage/emulated/0/Download/queen.mp3"
        )
        val abbaTrack = createSong(
            id = "2",
            title = "Dancing Queen",
            artist = "ABBA",
            album = "Greatest Hits",
            path = "/storage/emulated/0/Download/abba.mp3"
        )

        val queenKey = MusicRepository.getAlbumGroupingKey(queenTrack)
        val abbaKey = MusicRepository.getAlbumGroupingKey(abbaTrack)

        assertNotEquals("Different artists sharing common album name must not collide", queenKey, abbaKey)
        assertEquals("artist_queen|greatest hits", queenKey)
        assertEquals("artist_abba|greatest hits", abbaKey)
    }

    @Test
    fun testExplicitAlbumArtistTakesPrecedenceInGenericFolder() {
        val compilationTrack = createSong(
            id = "1",
            title = "Main Theme",
            artist = "Hans Zimmer",
            album = "Inception OST",
            path = "/storage/emulated/0/Download/inception.mp3",
            albumArtist = "Various Artists"
        )

        val key = MusicRepository.getAlbumGroupingKey(compilationTrack)
        assertEquals("albumartist_various artists|inception ost", key)
    }

    @Test
    fun testGenericRootDirectoryDetection() {
        assertTrue(MusicRepository.isGenericRootDirectory("/storage/emulated/0"))
        assertTrue(MusicRepository.isGenericRootDirectory("/storage/emulated/0/Download"))
        assertTrue(MusicRepository.isGenericRootDirectory("/storage/emulated/0/Downloads"))
        assertTrue(MusicRepository.isGenericRootDirectory("/storage/emulated/0/Music"))
        assertTrue(MusicRepository.isGenericRootDirectory("/sdcard/Music"))
        assertTrue(MusicRepository.isGenericRootDirectory("/storage/1234-5678/Download"))

        assertFalse(MusicRepository.isGenericRootDirectory("/storage/emulated/0/Music/Adele - 30"))
        assertFalse(MusicRepository.isGenericRootDirectory("/storage/emulated/0/Download/The Beatles"))
        assertFalse(MusicRepository.isGenericRootDirectory("/sdcard/Music/Rock/Queen"))
    }
}
