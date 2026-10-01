/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.repository

import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.streaming.domain.model.StreamingAlbum
import chromahub.rhythm.app.features.streaming.domain.model.StreamingArtist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingPlaylist
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

class CatalogCacheCodecTest {

    /** Stands in for SubsonicApiClient: signed URLs on the server become "endpoint?id=..". */
    private object FakeRefs : CatalogUrlRefs {
        private const val BASE = "https://music.example.net/rest/"
        private const val AUTH = "u=user&t=0123456789abcdef&s=abc123&v=1.16.1&c=Rhythm&f=json"

        fun cover(id: String) = "${BASE}getCoverArt.view?$AUTH&id=$id&size=500"
        fun stream(id: String) = "${BASE}stream.view?$AUTH&id=$id&maxBitRate=320"

        override fun toRef(url: String): String? {
            if (!url.startsWith(BASE)) return null
            val endpoint = url.removePrefix(BASE).substringBefore(".view")
            return "$endpoint?id=" + url.substringAfter("&id=").substringBefore('&')
        }

        override fun resolver(): (String) -> String? = { ref ->
            val id = ref.substringAfter("id=")
            when (ref.substringBefore('?')) {
                "getCoverArt" -> cover(id)
                "stream" -> stream(id)
                else -> null
            }
        }
    }

    private fun song(i: Int, favorite: Boolean = false) = StreamingSong(
        id = "subsonic::tr-$i",
        title = "Song $i",
        artist = "Artist ${i / 10}",
        album = "Album ${i / 5}",
        duration = 200_000L + i,
        artworkUri = FakeRefs.cover("al-${i / 5}"),
        sourceType = SourceType.SUBSONIC,
        streamingUrl = FakeRefs.stream("tr-$i"),
        previewUrl = null,
        externalId = "tr-$i",
        albumId = "subsonic::al-${i / 5}",
        albumArtist = "Artist ${i / 10}",
        isFavorite = favorite,
        trackNumber = i % 12,
        year = 2001,
        genre = "Hip-Hop",
        bitrate = 320,
        codec = "mp3"
    )

    private fun catalog(songCount: Int = 50) = StreamingCatalogCache(
        serviceId = "subsonic",
        songs = (0 until songCount).map { song(it, favorite = it % 7 == 0) },
        albums = (0 until songCount / 5).map {
            StreamingAlbum(
                id = "subsonic::al-$it", title = "Album $it", artist = "Artist", artworkUri = FakeRefs.cover("al-$it"),
                songCount = 5, year = 2001, sourceType = SourceType.SUBSONIC
            )
        },
        artists = listOf(
            // One provider cover (compacted) and one external image URL (kept as is).
            StreamingArtist(id = "subsonic::ar-1", name = "Artist 1", artworkUri = FakeRefs.cover("ar-1"), songCount = 10, albumCount = 2, sourceType = SourceType.SUBSONIC),
            StreamingArtist(id = "subsonic::ar-2", name = "Artist 2", artworkUri = "https://e-cdns-images.dzcdn.net/images/artist/x/500x500.jpg", songCount = 3, albumCount = 1, sourceType = SourceType.SUBSONIC)
        ),
        playlists = listOf(
            StreamingPlaylist(id = "subsonic::pl-1", name = "Mix", description = null, artworkUri = null, songCount = 0, isEditable = true, sourceType = SourceType.SUBSONIC)
        ),
        likedSongIds = listOf("subsonic::tr-0", "subsonic::tr-7"),
        lastSyncTimestamp = 123L
    )

    private fun write(codec: CatalogCacheCodec, cache: StreamingCatalogCache) = StringWriter().also { codec.write(it, cache) }.toString()

    @Test
    fun roundTripRestoresEveryField() {
        val codec = CatalogCacheCodec(FakeRefs)
        val cache = catalog()

        val restored = codec.read(StringReader(write(codec, cache)))

        assertEquals(cache, restored)
    }

    @Test
    fun storesNoSignedUrls() {
        val json = write(CatalogCacheCodec(FakeRefs), catalog())

        assertFalse("auth params must not be stored", json.contains("t=0123456789abcdef"))
        assertTrue(json.contains("\"ref:getCoverArt?id=al-0\""))
        // External (non-provider) URLs are kept verbatim.
        assertTrue(json.contains("dzcdn.net"))
    }

    @Test
    fun oldVerboseFormatStillLoads() {
        // Caches written before the compact format: plain reflective Gson, full signed URLs.
        val cache = catalog()
        val oldJson = Gson().toJson(cache)

        val restored = CatalogCacheCodec(FakeRefs).read(StringReader(oldJson))

        assertEquals(cache, restored)
    }

    @Test
    fun oldFormatIsRewrittenCompactly() {
        val codec = CatalogCacheCodec(FakeRefs)
        val cache = catalog(songCount = 500)
        val oldJson = Gson().toJson(cache)

        val rewritten = write(codec, codec.read(StringReader(oldJson))!!)

        assertTrue("compact ${rewritten.length} vs old ${oldJson.length}", rewritten.length < oldJson.length / 2)
        assertEquals(cache, codec.read(StringReader(rewritten)))
    }

    @Test
    fun unresolvableReferenceBecomesNull() {
        val json = write(CatalogCacheCodec(FakeRefs), catalog(songCount = 5))

        // E.g. logged out: nothing can be signed.
        val noCredentials = object : CatalogUrlRefs {
            override fun toRef(url: String): String? = null
            override fun resolver(): (String) -> String? = { null }
        }
        val restored = CatalogCacheCodec(noCredentials).read(StringReader(json))!!

        assertNull(restored.songs.first().artworkUri)
        assertNull(restored.songs.first().streamingUrl)
        assertEquals("Song 0", restored.songs.first().title)
    }

    @Test
    fun missingCacheFieldsKeepTheirDefaults() {
        val restored = CatalogCacheCodec().read(StringReader("""{"serviceId":"subsonic","songs":[{"i":"subsonic::x","t":"T","a":"A","l":"L","d":1,"s":"SUBSONIC"}]}"""))!!

        val song = restored.songs.single()
        assertEquals("x", song.externalId)
        assertTrue(song.isPlayable)
        assertFalse(song.isFavorite)
        assertNull(song.streamingUrl)
    }
}
