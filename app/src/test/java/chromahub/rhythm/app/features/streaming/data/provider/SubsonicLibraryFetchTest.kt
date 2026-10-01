/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.provider

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SubsonicLibraryFetchTest {

    private lateinit var server: MockWebServer
    private val albumRequests = ConcurrentHashMap<String, AtomicInteger>()
    private val albumsInFlight = AtomicInteger(0)
    private val maxAlbumsInFlight = AtomicInteger(0)

    /** Album ids whose first N getAlbum requests time out. */
    private val timeoutsBeforeSuccess = ConcurrentHashMap<String, Int>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                return when (url.encodedPath) {
                    "/rest/ping.view" -> ok()
                    "/rest/getAlbumList2.view" -> albumList(url.queryParameter("offset")!!.toInt())
                    "/rest/getAlbum.view" -> album(url.queryParameter("id")!!)
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** A successful Subsonic response; [fields] are extra JSON members, each starting with a comma. */
    private fun ok(fields: String = "") = MockResponse.Builder()
        .body("""{"subsonic-response":{"status":"ok","version":"1.16.1"$fields}}""")
        .build()

    private fun albumList(offset: Int): MockResponse {
        val albums = if (offset == 0) {
            (1..ALBUM_COUNT).joinToString(",") { """{"id":"al-$it","name":"Album $it","artist":"Artist"}""" }
        } else {
            ""
        }
        return ok(""","albumList2":{"album":[$albums]}""")
    }

    private fun album(id: String): MockResponse {
        val attempt = albumRequests.getOrPut(id) { AtomicInteger(0) }.incrementAndGet()
        maxAlbumsInFlight.accumulateAndGet(albumsInFlight.incrementAndGet(), ::maxOf)
        try {
            // Keep the request open briefly so concurrent requests overlap.
            Thread.sleep(20)
        } finally {
            albumsInFlight.decrementAndGet()
        }
        if (attempt <= (timeoutsBeforeSuccess[id] ?: 0)) {
            // Longer than the client's read timeout.
            return MockResponse.Builder().body("{}").headersDelay(2, TimeUnit.SECONDS).build()
        }
        val song = """{"id":"$id-s1","title":"Song","album":"Album","artist":"Artist","albumId":"$id","duration":180}"""
        return ok(""","album":{"id":"$id","name":"Album","song":[$song]}""")
    }

    private fun connectedClient(): SubsonicApiClient {
        val httpClient = OkHttpClient.Builder()
            .readTimeout(300, TimeUnit.MILLISECONDS)
            .build()
        val client = SubsonicApiClient(FakeContext(), httpClient, libraryFetchRetryDelayMs = 10)
        val login = runBlocking {
            client.login(server.url("/").toString(), "user", "secret", saveCredentials = false)
        }
        assertTrue("login failed: ${login.exceptionOrNull()}", login.isSuccess)
        return client
    }

    @Test
    fun timedOutAlbumIsRetriedAndKept() = runBlocking {
        timeoutsBeforeSuccess["al-3"] = 2

        val songs = connectedClient().fetchLibrarySongs().getOrThrow()

        assertEquals(ALBUM_COUNT, songs.size)
        assertEquals(3, albumRequests["al-3"]!!.get())
        assertTrue(songs.any { it.providerId == "al-3-s1" })
    }

    @Test
    fun albumFailingEveryRetryIsSkippedAndTheRestIsKept() = runBlocking {
        timeoutsBeforeSuccess["al-5"] = Int.MAX_VALUE

        val songs = connectedClient().fetchLibrarySongs().getOrThrow()

        // One initial attempt plus two retries, then the album is skipped.
        assertEquals(3, albumRequests["al-5"]!!.get())
        assertEquals(ALBUM_COUNT - 1, songs.size)
        assertTrue(songs.none { it.providerId == "al-5-s1" })
    }

    @Test
    fun albumRequestsAreBounded() = runBlocking {
        val songs = connectedClient().fetchLibrarySongs().getOrThrow()

        assertEquals(ALBUM_COUNT, songs.size)
        assertTrue("max in flight was ${maxAlbumsInFlight.get()}", maxAlbumsInFlight.get() in 2..4)
    }

    /** Context that only provides in-memory SharedPreferences. */
    private class FakeContext : ContextWrapper(null) {
        private val prefs = FakeSharedPreferences()
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val values = ConcurrentHashMap<String, Any>()

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

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private var clear = false
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: Set<String>?) = apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clear) values.clear()
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            }
        }
    }

    private companion object {
        const val ALBUM_COUNT = 12
    }
}
