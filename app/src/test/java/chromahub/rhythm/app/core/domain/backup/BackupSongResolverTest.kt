/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.backup

import android.net.TestUri
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BackupSongResolverTest {

    private fun song(
        id: String,
        title: String,
        artist: String,
        album: String = "Album",
        durationMs: Long = 210_000L,
        path: String? = null,
        scheme: String = "content"
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = durationMs,
        uri = TestUri(scheme, "/media/$id"),
        path = path
    )

    @Test
    fun resolver_matchesAllFourTiersAndDisambiguatesAlbums() {
        val flacLive = song(
            id = "101",
            title = "Midnight City",
            artist = "M83",
            album = "Live Tour",
            durationMs = 245_000L,
            path = "/storage/emulated/0/Music/M83/Live/01_midnight_city.flac"
        )
        val studioVersion = song(
            id = "102",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            durationMs = 243_000L,
            path = "/storage/emulated/0/Music/M83/Studio/midnight_city.flac"
        )
        val frenchTrack = song(
            id = "103",
            title = "Café de Flore",
            artist = "Doctor Rockit",
            album = "Indoor Fireworks",
            durationMs = 315_000L,
            path = "/storage/emulated/0/Music/Cafe.mp3"
        )
        val index = BackupSongResolver.SongIndex(listOf(flacLive, studioVersion, frenchTrack))

        assertEquals("102", index.resolve(BackupSongResolver.ResolutionQuery(id = "102"))?.id)

        val moved = index.resolve(
            BackupSongResolver.ResolutionQuery(filePath = "/mnt/sdcard1/Audio/M83/Studio/midnight%20city.mp3")
        )
        assertEquals("102", moved?.id)

        val cafe = index.resolve(
            BackupSongResolver.ResolutionQuery(title = "Cafe de Flore!", artist = "Doctor   Rockit")
        )
        assertEquals("103", cafe?.id)

        val swapped = index.resolve(
            BackupSongResolver.ResolutionQuery(title = "M83 - Midnight City", durationMs = 244_500L)
        )
        assertNotNull(swapped)
    }

    @Test
    fun resolvePlaylists_migratesMovedSongsAndPreservesCloudIds() {
        val newDeviceSong = song("local_500", "Teardrop", "Massive Attack", album = "Mezzanine", durationMs = 329_000L, path = "/sdcard/Music/Teardrop.flac")
        val oldDeviceSong = song("old_99", "Teardrop", "Massive Attack", album = "Mezzanine", durationMs = 328_500L, path = "/storage/9999/Music/Teardrop.flac")
        val cloudSong = song("subsonic::77", "Cloud Jam", "Cloud Artist", durationMs = 180_000L, scheme = "streaming")

        val backupPlaylist = Playlist(id = "pl1", name = "Favorites", songs = listOf(oldDeviceSong, cloudSong))
        val (resolved, stats) = BackupSongResolver.resolvePlaylists(listOf(backupPlaylist), listOf(newDeviceSong))

        assertEquals(1, stats.matchedCount)
        assertEquals(listOf("local_500", "subsonic::77"), resolved.first().songs.map { it.id })
    }
}
