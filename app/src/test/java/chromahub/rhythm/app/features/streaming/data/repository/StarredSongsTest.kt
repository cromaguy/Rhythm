/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import org.junit.Assert.assertEquals
import org.junit.Test

class StarredSongsTest {

    private fun song(id: String, artist: String, title: String = "Song $id") = StreamingSong(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        duration = 180_000L,
        artworkUri = null,
        sourceType = SourceType.SUBSONIC,
        streamingUrl = null,
        previewUrl = null
    )

    @Test
    fun likedSongs_includeStarredSongsMissingFromAPartialCatalog() {
        // A sync cut short after the first albums in name order: only the "21 Savage" song is in.
        val catalog = linkedMapOf("SUBSONIC::1" to song("SUBSONIC::1", "21 Savage"))
        val starred = listOf(
            song("SUBSONIC::1", "21 Savage"),
            song("SUBSONIC::2", "Clairo"),
            song("SUBSONIC::3", "Radiohead")
        )

        val likedIds = StarredSongs.mergeLikedIds(emptyList(), "SUBSONIC::", starred)
        val liked = StarredSongs.likedSongs(likedIds, catalog, starred.associateBy { it.id })

        assertEquals(listOf("21 Savage", "Clairo", "Radiohead"), liked.map { it.artist })
    }

    @Test
    fun likedSongs_preferTheCatalogCopyOfASong() {
        val catalogCopy = song("SUBSONIC::1", "Artist", title = "Catalog title")
        val starredCopy = song("SUBSONIC::1", "Artist", title = "Starred title")

        val liked = StarredSongs.likedSongs(
            listOf("SUBSONIC::1"),
            mapOf(catalogCopy.id to catalogCopy),
            mapOf(starredCopy.id to starredCopy)
        )

        assertEquals(listOf("Catalog title"), liked.map { it.title })
    }

    @Test
    fun mergeLikedIds_replacesOnlyTheRefreshedService() {
        val likedIds = listOf("JELLYFIN::a", "SUBSONIC::unstarred", "JELLYFIN::b")
        val starred = listOf(song("SUBSONIC::2", "X"), song("SUBSONIC::1", "Y"))

        val merged = StarredSongs.mergeLikedIds(likedIds, "SUBSONIC::", starred)

        assertEquals(listOf("JELLYFIN::a", "JELLYFIN::b", "SUBSONIC::2", "SUBSONIC::1"), merged.toList())
    }

    @Test
    fun likedSongs_skipIdsFoundNowhere() {
        val liked = StarredSongs.likedSongs(listOf("SUBSONIC::gone"), emptyMap(), emptyMap())

        assertEquals(emptyList<StreamingSong>(), liked)
    }
}
