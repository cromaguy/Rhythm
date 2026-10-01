/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import chromahub.rhythm.app.features.streaming.domain.repository.StreamingMusicRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LargeCatalogCacheTest {

    @Test
    fun libraryLimitCoversLargeLibraries() {
        // A 36k-song Navidrome library was cut off at 5,000 songs, i.e. at the first artists.
        assertTrue(StreamingMusicRepository.MAX_LIBRARY_SONGS >= 36_000)
    }

    @Test
    fun fullSizeCatalogRoundTripsThroughTheStreamedCacheFile() {
        val songs = (0 until StreamingMusicRepository.MAX_LIBRARY_SONGS).map { song(it) }
        val cache = StreamingCatalogCache(
            serviceId = "subsonic",
            songs = songs,
            likedSongIds = songs.filter { it.isFavorite }.map { it.id },
            lastSyncTimestamp = 1L
        )
        val dir = Files.createTempDirectory("catalog-cache").toFile()
        try {
            val file = dir.resolve("streaming_catalog_subsonic.json")

            CatalogCacheCodec().writeFile(file, cache)
            val restored = CatalogCacheCodec().readFile(file)

            assertEquals(cache, restored)
            // Under 1 KB per song, the figure MAX_LIBRARY_SONGS is sized by.
            assertTrue(file.length() < StreamingMusicRepository.MAX_LIBRARY_SONGS * 1_024L)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun song(index: Int): StreamingSong {
        val id = "%022x".format(index.toLong() * 7919)
        val albumId = "%022x".format((index / 10).toLong() * 104729)
        val auth = "u=someuser&t=0123456789abcdef0123456789abcdef&s=a1b2c3d4e5f6&v=1.16.1&c=Rhythm&f=json"
        return StreamingSong(
            id = "subsonic::$id",
            title = "Song title $index",
            artist = "Artist ${index / 40}",
            album = "Album ${index / 10}",
            duration = 215_000L,
            artworkUri = "https://music.example.net/rest/getCoverArt.view?id=al-$albumId&size=512&$auth",
            sourceType = SourceType.SUBSONIC,
            streamingUrl = "https://music.example.net/rest/stream.view?id=$id&$auth",
            previewUrl = null,
            albumId = "subsonic::$albumId",
            albumArtist = "Artist ${index / 40}",
            isFavorite = index % 50 == 0,
            trackNumber = index % 10,
            year = 2001,
            genre = "Hip-Hop",
            bitrate = 320,
            codec = "mp3"
        )
    }
}
