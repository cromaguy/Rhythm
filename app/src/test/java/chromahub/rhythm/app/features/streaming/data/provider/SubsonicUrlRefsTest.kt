/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.provider

import android.content.ContextWrapper
import android.content.SharedPreferences
import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.streaming.data.repository.CatalogCacheCodec
import chromahub.rhythm.app.features.streaming.data.repository.CatalogUrlRefs
import chromahub.rhythm.app.features.streaming.data.repository.StreamingCatalogCache
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

class SubsonicUrlRefsTest {

    private val client = SubsonicApiClient(
        FakeContext(mapOf("server_url" to "https://music.example.net", "username" to "user", "password" to "secret"))
    )

    /** The URL without the per-request auth params (salt and token). */
    private fun withoutSaltAndToken(url: String): String {
        val parsed = url.toHttpUrl()
        return parsed.newBuilder().removeAllQueryParameters("t").removeAllQueryParameters("s").build().toString()
    }

    @Test
    fun coverArtUrlRoundTripsExactly() {
        val url = client.buildCoverArtUrl("al-123", 500)!!

        val ref = client.toUrlRef(url)

        assertEquals("getCoverArt?id=al-123&size=500", ref)
        // Cover-art URLs use a stable token, so the rebuilt URL is identical.
        assertEquals(url, client.urlRefResolver()(ref!!))
    }

    @Test
    fun streamUrlRebuildsInTheSameFormatWithAFreshSaltAndToken() {
        val url = client.buildStreamUrl("tr-9", maxBitRateKbps = 320)!!

        val ref = client.toUrlRef(url)!!
        val rebuilt = client.urlRefResolver()(ref)!!

        assertEquals("stream?id=tr-9&maxBitRate=320", ref)
        assertEquals(withoutSaltAndToken(url), withoutSaltAndToken(rebuilt))
        assertEquals(url.toHttpUrl().queryParameterNames, rebuilt.toHttpUrl().queryParameterNames)
    }

    @Test
    fun oneResolverSignsManyIdsWithOneToken() {
        val resolve = client.urlRefResolver()

        val a = resolve("stream?id=a&maxBitRate=320")!!.toHttpUrl()
        val b = resolve("stream?id=b&maxBitRate=320")!!.toHttpUrl()

        assertEquals("a", a.queryParameter("id"))
        assertEquals("b", b.queryParameter("id"))
        assertEquals(a.queryParameter("t"), b.queryParameter("t"))
    }

    @Test
    fun idsThatNeedEncodingRoundTrip() {
        val url = client.buildCoverArtUrl("mf-a b&c=d", 300)!!

        val rebuilt = client.urlRefResolver()(client.toUrlRef(url)!!)!!

        assertEquals("mf-a b&c=d", rebuilt.toHttpUrl().queryParameter("id"))
        assertEquals(url, rebuilt)
    }

    @Test
    fun foreignUrlsAreNotReferenced() {
        assertNull(client.toUrlRef("https://e-cdns-images.dzcdn.net/images/artist/x/500x500.jpg"))
        assertNull(client.toUrlRef("https://other.example.net/rest/getCoverArt.view?id=1"))
        assertNull(client.toUrlRef(client.buildCoverArtUrl("x")!!.replace("getCoverArt", "getAvatar")))
    }

    /** 36k songs as the repository maps them: two signed URLs each. */
    private fun library(size: Int) = StreamingCatalogCache(
        serviceId = "SUBSONIC",
        songs = List(size) { i ->
            val id = "%022x".format(i.toLong() * 7919)
            val albumId = "%022x".format((i / 10).toLong() * 104729)
            StreamingSong(
                id = "SUBSONIC::$id", title = "Song title $i", artist = "Artist ${i / 40}", album = "Album ${i / 10}",
                duration = 215_000L, artworkUri = client.buildCoverArtUrl(albumId, 500), sourceType = SourceType.SUBSONIC,
                streamingUrl = client.buildStreamUrl(id, 320), previewUrl = null, externalId = id,
                albumId = "SUBSONIC::$albumId", albumArtist = "Artist ${i / 40}", isFavorite = i % 50 == 0,
                trackNumber = i % 10, year = 2001, genre = "Hip-Hop", bitrate = 320, codec = "mp3"
            )
        },
        likedSongIds = List(size / 50) { "SUBSONIC::%022x".format(it * 50L * 7919) },
        lastSyncTimestamp = 1L
    )

    @Test
    fun largeLibraryCacheIsSmallerAndLoadsNoSlower() {
        val refs = object : CatalogUrlRefs {
            override fun toRef(url: String): String? = client.toUrlRef(url)
            override fun resolver(): (String) -> String? = client.urlRefResolver()
        }
        val codec = CatalogCacheCodec(refs)
        val gson = Gson()
        val cache = library(36_000)
        val oldJson = gson.toJson(cache)
        val compactJson = StringWriter().also { codec.write(it, cache) }.toString()

        repeat(2) { gson.fromJson(oldJson, StreamingCatalogCache::class.java); codec.read(StringReader(compactJson)) } // warm-up
        val oldMs = bestOf(3) { gson.fromJson(StringReader(oldJson), StreamingCatalogCache::class.java) }
        val compactMs = bestOf(3) { codec.read(StringReader(compactJson)) }
        println("36k songs: old ${oldJson.length / 1_000_000.0} MB, $oldMs ms; compact ${compactJson.length / 1_000_000.0} MB, $compactMs ms (incl. re-signing URLs)")

        val restored = codec.read(StringReader(compactJson))!!
        assertEquals(cache.songs.size, restored.songs.size)
        assertEquals(cache.songs[123].artworkUri, restored.songs[123].artworkUri)
        assertNotEquals(null, restored.songs[123].streamingUrl)
        assertTrue(compactJson.length < oldJson.length / 2)
        // Re-signing every URL must not make loading slower than parsing the old, 2.5x larger file
        // (with margin for timing noise).
        assertTrue("compact load $compactMs ms vs old $oldMs ms", compactMs <= oldMs * 3 / 2)
    }

    private fun bestOf(runs: Int, block: () -> Unit): Long =
        (1..runs).minOf { val start = System.nanoTime(); block(); (System.nanoTime() - start) / 1_000_000 }

    /** Context that only provides in-memory SharedPreferences with the given credentials. */
    private class FakeContext(values: Map<String, Any>) : ContextWrapper(null) {
        private val prefs = FakeSharedPreferences(values)
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    private class FakeSharedPreferences(initial: Map<String, Any>) : SharedPreferences {
        private val values = HashMap(initial)
        override fun getAll(): Map<String, *> = values
        override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?) =
            @Suppress("UNCHECKED_CAST") (values[key] as? Set<String> ?: defValues)
        override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
        override fun contains(key: String) = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
    }
}
