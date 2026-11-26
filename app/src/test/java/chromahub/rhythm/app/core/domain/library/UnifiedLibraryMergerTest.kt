/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.core.domain.library

import android.net.TestUri
import chromahub.rhythm.app.shared.data.model.Album
import chromahub.rhythm.app.shared.data.model.Artist
import chromahub.rhythm.app.shared.data.model.Playlist
import chromahub.rhythm.app.shared.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedLibraryMergerTest {

    private fun song(id: String, title: String, scheme: String = "content"): Song = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        duration = 200_000L,
        uri = TestUri(scheme, "/track/$id")
    )

    @Test
    fun mergeSongs_combinesAndDeduplicatesInUnifiedModeRespectingLocalOnlyAndStreamingOnly() {
        val localA = song("local_1", "Local Song")
        val shared = song("shared_1", "Shared Track")
        val cloudB = song("subsonic::42", "Cloud Track", scheme = "streaming")

        assertEquals(listOf(localA, shared), UnifiedLibraryMerger.mergeSongs(listOf(localA, shared), listOf(cloudB), unifiedEnabled = false, isStreamingMode = false))
        assertEquals(listOf(cloudB), UnifiedLibraryMerger.mergeSongs(listOf(localA), listOf(cloudB), unifiedEnabled = false, isStreamingMode = true))

        val unified = UnifiedLibraryMerger.mergeSongs(
            local = listOf(localA, shared),
            streaming = listOf(shared, cloudB),
            unifiedEnabled = true,
            isStreamingMode = false
        )
        assertEquals(listOf(localA, shared, cloudB), unified)
    }

    @Test
    fun classifiers_accuratelyDistinguishStreamingVsLocalEntities() {
        val localSong = song("local_99", "On Device")
        val cloudSong = song("jellyfin::88", "Remote Lossless", scheme = "streaming")
        val cloudIds = setOf("jellyfin::88")

        assertFalse(UnifiedLibraryMerger.isStreamingSong(localSong, cloudIds))
        assertTrue(UnifiedLibraryMerger.isStreamingSong(cloudSong, emptySet()))
        assertTrue(UnifiedLibraryMerger.isStreamingSong(song("track_1", "URI cloud", scheme = "streaming"), emptySet()))

        val localAlbum = Album(id = "alb_1", title = "Local Alb", artist = "Art", songs = listOf(localSong))
        val cloudAlbum = Album(id = "alb_2", title = "Cloud Alb", artist = "Art", songs = listOf(cloudSong))
        assertFalse(UnifiedLibraryMerger.isStreamingAlbum(localAlbum, emptySet(), cloudIds))
        assertTrue(UnifiedLibraryMerger.isStreamingAlbum(cloudAlbum, emptySet(), cloudIds))

        val derivedArtist = Artist(id = "derived::Daft Punk", name = "Daft Punk")
        assertTrue(UnifiedLibraryMerger.isStreamingArtist(derivedArtist, emptySet()))
        assertTrue(UnifiedLibraryMerger.isStreamingPlaylist(Playlist(id = "subsonic::pl1", name = "Mix"), emptySet()))
    }
}
