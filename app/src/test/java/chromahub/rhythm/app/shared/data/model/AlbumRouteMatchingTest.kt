/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumRouteMatchingTest {

    @Test
    fun findAlbumForRoute_matchesAlbumWithPercentSignInTitle() {
        val album = Album(
            id = "album_100_percent",
            title = "100% Hits",
            artist = "Various Artists"
        )
        val albums = listOf(album)

        // Route decoded directly (as Jetpack Navigation provides it without double-decoding)
        val matched = albums.findAlbumForRoute("album_100_percent", "100% Hits")
        assertNotNull(matched)
        assertEquals("album_100_percent", matched?.id)
        assertEquals("100% Hits", matched?.title)
    }

    @Test
    fun findAlbumForRoute_matchesAlbumWithPercentSignWhenRouteParamWasUrlEncoded() {
        val album = Album(
            id = "album_100_percent",
            title = "100% Hits",
            artist = "Various Artists"
        )
        val albums = listOf(album)

        // Even if route parameter arrived as URL-encoded "100%25%20Hits"
        val matched = albums.findAlbumForRoute("album_100_percent", "100%25%20Hits")
        assertNotNull(matched)
        assertEquals("100% Hits", matched?.title)
    }

    @Test
    fun findAlbumForRoute_matchesAlbumWithPercentLiteralInFolderId() {
        val album = Album(
            id = "folder_100%_music",
            title = "Best of 2026",
            artist = "Artist"
        )
        val albums = listOf(album)

        // Matches raw ID containing %
        val matchedDirect = albums.findAlbumForRoute("folder_100%_music", "Best of 2026")
        assertNotNull(matchedDirect)
        assertEquals("folder_100%_music", matchedDirect?.id)

        // Matches when ID arrived URL-encoded
        val matchedEncoded = albums.findAlbumForRoute("folder_100%25_music", "Best of 2026")
        assertNotNull(matchedEncoded)
        assertEquals("folder_100%_music", matchedEncoded?.id)
    }

    @Test
    fun findAlbumForRoute_handlesSlashToUnderscoreInId() {
        val album = Album(
            id = "Music/Rock/Classic",
            title = "Classic Rock",
            artist = "Artist"
        )
        val albums = listOf(album)

        val matched = albums.findAlbumForRoute("Music_Rock_Classic", "Classic Rock")
        assertNotNull(matched)
        assertEquals("Music/Rock/Classic", matched?.id)
    }

    @Test
    fun findAlbumForRoute_handlesLegacyUnknownPrefix() {
        val album = Album(
            id = "unknown_AlbumName",
            title = "AlbumName",
            artist = "Artist"
        )
        val albums = listOf(album)

        val matched = albums.findAlbumForRoute("unknown_AlbumName", "")
        assertNotNull(matched)
        assertEquals("AlbumName", matched?.title)
    }

    @Test
    fun findAlbumForRoute_returnsNullWhenNoMatch() {
        val album = Album(
            id = "album_1",
            title = "Album One",
            artist = "Artist"
        )
        val albums = listOf(album)

        val matched = albums.findAlbumForRoute("album_2", "Nonexistent")
        assertNull(matched)
    }
}
