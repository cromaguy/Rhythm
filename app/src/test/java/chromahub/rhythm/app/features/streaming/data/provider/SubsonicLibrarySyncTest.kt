/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.features.streaming.data.provider

import android.content.ContextWrapper
import android.content.SharedPreferences
import chromahub.rhythm.app.core.domain.model.SourceType
import chromahub.rhythm.app.features.streaming.data.provider.SubsonicApiClient.LibraryChangeState
import chromahub.rhythm.app.features.streaming.data.repository.CatalogCacheWriter
import chromahub.rhythm.app.features.streaming.data.repository.ResumableLibraryFetch
import chromahub.rhythm.app.features.streaming.data.repository.StarredSongs
import chromahub.rhythm.app.features.streaming.data.repository.StreamingCatalogCache
import chromahub.rhythm.app.features.streaming.domain.model.StreamingSong
import chromahub.rhythm.app.features.streaming.infrastructure.notification.SyncProgressThrottle
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SubsonicLibrarySyncTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var tempDir: File
    private val albumRequests = ConcurrentHashMap<String, AtomicInteger>()
    private val albumsInFlight = AtomicInteger(0)
    private val maxAlbumsInFlight = AtomicInteger(0)
    private val timeoutsBeforeSuccess = ConcurrentHashMap<String, Int>()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("subsonic-sync-test").toFile()
        mockServer = MockWebServer()
        mockServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.url
                return when (url.encodedPath) {
                    "/rest/ping.view" -> subsonicResponse()
                    "/rest/getAlbumList2.view" -> {
                        val offset = url.queryParameter("offset")?.toIntOrNull() ?: 0
                        val albums = if (offset == 0) {
                            (1..TEST_ALBUM_COUNT).joinToString(",") {
                                """{"id":"al-$it","name":"Album $it","artist":"Artist"}"""
                            }
                        } else {
                            ""
                        }
                        subsonicResponse(""","albumList2":{"album":[$albums]}""")
                    }
                    "/rest/getAlbum.view" -> {
                        val albumId = url.queryParameter("id").orEmpty()
                        val attempt = albumRequests.getOrPut(albumId) { AtomicInteger(0) }.incrementAndGet()
                        maxAlbumsInFlight.accumulateAndGet(albumsInFlight.incrementAndGet(), ::maxOf)
                        try {
                            Thread.sleep(15)
                        } finally {
                            albumsInFlight.decrementAndGet()
                        }
                        if (attempt <= (timeoutsBeforeSuccess[albumId] ?: 0)) {
                            MockResponse.Builder().body("{}").headersDelay(2, TimeUnit.SECONDS).build()
                        } else {
                            val track = """{"id":"$albumId-track1","title":"Track 1","album":"Album","artist":"Artist","duration":180}"""
                            subsonicResponse(""","album":{"id":"$albumId","name":"Album","song":[$track]}""")
                        }
                    }
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        mockServer.start()
    }

    @After
    fun tearDown() {
        mockServer.close()
        tempDir.deleteRecursively()
    }

    private fun subsonicResponse(customFields: String = ""): MockResponse = MockResponse.Builder()
        .body("""{"subsonic-response":{"status":"ok","version":"1.16.1"$customFields}}""")
        .build()

    private fun createConnectedClient(): SubsonicApiClient {
        val httpClient = OkHttpClient.Builder()
            .readTimeout(300, TimeUnit.MILLISECONDS)
            .build()
        val client = SubsonicApiClient(MockContext(), httpClient, libraryFetchRetryDelayMs = 10)
        val loginResult = runBlocking {
            client.login(mockServer.url("/").toString(), "testuser", "testpass", saveCredentials = false)
        }
        assertTrue("Subsonic login failed", loginResult.isSuccess)
        return client
    }

    // -------------------------------------------------------------------------
    // 1. LowerHex Tests
    // -------------------------------------------------------------------------

    @Test
    fun testToLowerHex_matchesStandardFormatting() {
        val allBytes = ByteArray(256) { it.toByte() }
        val expected = allBytes.joinToString("") { "%02x".format(it) }
        assertEquals(expected, allBytes.toLowerHex())
        assertEquals("", ByteArray(0).toLowerHex())
    }

    @Test
    fun testToLowerHex_matchesSubsonicSpecAuthDigest() {
        // Subsonic spec MD5 test vector: "sesame" + salt "c19b2d"
        val md5 = MessageDigest.getInstance("MD5").digest("sesamec19b2d".toByteArray(Charsets.UTF_8))
        assertEquals("26719a1196d2a940705a59634eb18eab", md5.toLowerHex())
    }

    // -------------------------------------------------------------------------
    // 2. Library Marker & Change Detection Tests
    // -------------------------------------------------------------------------

    @Test
    fun testLibraryMarker_stabilityAndChangeDetection() {
        val state1 = LibraryChangeState(lastModified = 1_700_000_000_000L, scanning = false)
        val state2 = LibraryChangeState(lastModified = 1_700_000_000_000L, scanning = false)
        assertEquals(state1.catalogMarker("acc-1"), state2.catalogMarker("acc-1"))

        // Changes with timestamp or account
        assertNotEquals(
            state1.catalogMarker("acc-1"),
            state1.copy(lastModified = 1_700_000_000_001L).catalogMarker("acc-1")
        )
        assertNotEquals(state1.catalogMarker("acc-1"), state1.catalogMarker("acc-2"))

        // Null when scanning or lastModified is 0
        assertNull(LibraryChangeState(lastModified = 1_700_000_000_000L, scanning = true).catalogMarker("acc-1"))
        assertNull(LibraryChangeState(lastModified = 0L, scanning = false).catalogMarker("acc-1"))
        assertNull(LibraryChangeState(lastModified = 1_700_000_000_000L, scanning = false).catalogMarker(null))
    }

    // -------------------------------------------------------------------------
    // 3. Subsonic URL References Tests
    // -------------------------------------------------------------------------

    @Test
    fun testSubsonicUrlRefs_coverAndStreamUrlReconstruction() {
        val client = SubsonicApiClient(
            MockContext(mapOf("server_url" to "https://subsonic.example.com", "username" to "user", "password" to "secret"))
        )

        val coverUrl = client.buildCoverArtUrl("al-99", 500)!!
        val coverRef = client.toUrlRef(coverUrl)
        assertEquals("getCoverArt?id=al-99&size=500", coverRef)
        assertEquals(coverUrl, client.urlRefResolver()(coverRef!!))

        val streamUrl = client.buildStreamUrl("song-123", maxBitRateKbps = 320)!!
        val streamRef = client.toUrlRef(streamUrl)!!
        assertEquals("stream?id=song-123&maxBitRate=320", streamRef)

        val rebuiltStream = client.urlRefResolver()(streamRef)!!
        val parsedOriginal = streamUrl.toHttpUrl()
        val parsedRebuilt = rebuiltStream.toHttpUrl()
        assertEquals(parsedOriginal.host, parsedRebuilt.host)
        assertEquals(parsedOriginal.encodedPath, parsedRebuilt.encodedPath)
        assertEquals("song-123", parsedRebuilt.queryParameter("id"))
        assertEquals("320", parsedRebuilt.queryParameter("maxBitRate"))
    }

    @Test
    fun testSubsonicUrlRefs_foreignUrlsIgnored() {
        val client = SubsonicApiClient(MockContext(mapOf("server_url" to "https://subsonic.example.com")))
        assertNull(client.toUrlRef("https://cdn.example.com/art.jpg"))
        assertNull(client.toUrlRef("https://other-subsonic.com/rest/getCoverArt.view?id=1"))
    }

    // -------------------------------------------------------------------------
    // 4. SubsonicApiClient Network Resiliency & Bounded Concurrency
    // -------------------------------------------------------------------------

    @Test
    fun testFetchLibrarySongs_retriesTimedOutAlbums() = runBlocking {
        timeoutsBeforeSuccess["al-2"] = 2
        val client = createConnectedClient()

        val songs = client.fetchLibrarySongs().getOrThrow()
        assertEquals(TEST_ALBUM_COUNT, songs.size)
        assertEquals(3, albumRequests["al-2"]?.get())
        assertTrue(songs.any { it.providerId == "al-2-track1" })
    }

    @Test
    fun testFetchLibrarySongs_skipsPermanentlyFailingAlbumWithoutDroppingOthers() = runBlocking {
        timeoutsBeforeSuccess["al-3"] = Int.MAX_VALUE
        val client = createConnectedClient()

        val songs = client.fetchLibrarySongs().getOrThrow()
        assertEquals(TEST_ALBUM_COUNT - 1, songs.size)
        assertEquals(3, albumRequests["al-3"]?.get())
        assertTrue(songs.none { it.providerId == "al-3-track1" })
    }

    @Test
    fun testFetchLibrarySongs_boundsAlbumFetchConcurrency() = runBlocking {
        val client = createConnectedClient()
        val songs = client.fetchLibrarySongs().getOrThrow()

        assertEquals(TEST_ALBUM_COUNT, songs.size)
        assertTrue(
            "Max concurrent album requests must be bounded (was ${maxAlbumsInFlight.get()})",
            maxAlbumsInFlight.get() in 1..4
        )
    }

    // -------------------------------------------------------------------------
    // 5. ResumableLibraryFetch Tests
    // -------------------------------------------------------------------------

    @Test
    fun testResumableLibraryFetch_resumesInterruptedSync() = runBlocking {
        val checkpointDir = File(tempDir, "sync_checkpoints")
        val writer = CatalogCacheWriter()
        val fetcher = ResumableLibraryFetch(checkpointDir, writer)
        val fetchedPages = Collections.synchronizedList(mutableListOf<Int>())
        val pagesCheckpointed = CompletableDeferred<Unit>()

        val fakePagedFetch = object : ResumableLibraryFetch.PagedFetch {
            override suspend fun fetch(
                startAlbumOffset: Int,
                limit: Int,
                onProgress: ((current: Int, total: Int, songsCount: Int) -> Unit)?,
                onPage: suspend (pageSongs: List<ProviderSong>, nextAlbumOffset: Int) -> Unit
            ): Result<List<ProviderSong>> {
                val songs = mutableListOf<ProviderSong>()
                var offset = startAlbumOffset
                var pagesFetched = 0
                while (offset < 5 * 10 && songs.size < limit) {
                    if (pagesFetched == 2) {
                        pagesCheckpointed.complete(Unit)
                        awaitCancellation()
                    }
                    val page = (offset until offset + 10).map {
                        ProviderSong(providerId = "song-$it", title = "T-$it", artist = "A", album = "Al", durationMs = 180000L)
                    }
                    fetchedPages.add(offset / 10)
                    songs += page
                    offset += 10
                    pagesFetched++
                    onPage(page, offset)
                }
                return Result.success(songs)
            }
        }

        // Simulate interruption after 2 pages
        coroutineScope {
            val job = launch(Dispatchers.IO) {
                fetcher.fetch(1700000000000L, 50, null, fakePagedFetch)
            }
            pagesCheckpointed.await()
            job.cancelAndJoin()
        }
        assertEquals(listOf(0, 1), fetchedPages)

        // Second run resumes from page 2 without refetching pages 0 and 1
        fetchedPages.clear()
        val completingFetch = object : ResumableLibraryFetch.PagedFetch {
            override suspend fun fetch(
                startAlbumOffset: Int,
                limit: Int,
                onProgress: ((current: Int, total: Int, songsCount: Int) -> Unit)?,
                onPage: suspend (pageSongs: List<ProviderSong>, nextAlbumOffset: Int) -> Unit
            ): Result<List<ProviderSong>> {
                val songs = mutableListOf<ProviderSong>()
                var offset = startAlbumOffset
                while (offset < 5 * 10 && songs.size < limit) {
                    val page = (offset until offset + 10).map {
                        ProviderSong(providerId = "song-$it", title = "T-$it", artist = "A", album = "Al", durationMs = 180000L)
                    }
                    fetchedPages.add(offset / 10)
                    songs += page
                    offset += 10
                    onPage(page, offset)
                }
                return Result.success(songs)
            }
        }

        val allSongs = fetcher.fetch(1700000000000L, 50, null, completingFetch).getOrThrow()
        assertEquals(50, allSongs.size)
        assertEquals(listOf(2, 3, 4), fetchedPages)
        // Checkpoints cleaned up on success
        assertFalse(checkpointDir.exists())
    }

    // -------------------------------------------------------------------------
    // 6. StarredSongs Merging Tests
    // -------------------------------------------------------------------------

    @Test
    fun testStarredSongs_mergesPreservingOtherServices() {
        val existingLiked = listOf("jellyfin::song-j1", "subsonic::old-song", "jellyfin::song-j2")
        val refreshedSubsonic = listOf(
            StreamingSong(id = "subsonic::new-s1", title = "S1", artist = "A", album = "Al", duration = 100L, artworkUri = null, sourceType = SourceType.SUBSONIC, streamingUrl = null, previewUrl = null),
            StreamingSong(id = "subsonic::new-s2", title = "S2", artist = "A", album = "Al", duration = 100L, artworkUri = null, sourceType = SourceType.SUBSONIC, streamingUrl = null, previewUrl = null)
        )

        val merged = StarredSongs.mergeLikedIds(existingLiked, "subsonic::", refreshedSubsonic)
        assertEquals(
            listOf("jellyfin::song-j1", "jellyfin::song-j2", "subsonic::new-s1", "subsonic::new-s2"),
            merged.toList()
        )
    }

    @Test
    fun testStarredSongs_includesStarredMissingFromCatalog() {
        val catalogSong = StreamingSong(id = "subsonic::1", title = "Catalog Title", artist = "A", album = "Al", duration = 100L, artworkUri = null, sourceType = SourceType.SUBSONIC, streamingUrl = null, previewUrl = null)
        val uncatalogedStarred = StreamingSong(id = "subsonic::2", title = "Starred Only", artist = "B", album = "Al", duration = 100L, artworkUri = null, sourceType = SourceType.SUBSONIC, streamingUrl = null, previewUrl = null)

        val liked = StarredSongs.likedSongs(
            likedIds = listOf("subsonic::1", "subsonic::2"),
            catalog = mapOf("subsonic::1" to catalogSong),
            starred = mapOf("subsonic::2" to uncatalogedStarred)
        )

        assertEquals(2, liked.size)
        assertEquals("Catalog Title", liked[0].title)
        assertEquals("Starred Only", liked[1].title)
    }

    // -------------------------------------------------------------------------
    // 7. SyncProgressThrottle Tests
    // -------------------------------------------------------------------------

    @Test
    fun testSyncProgressThrottle_rateLimitsNotificationsWithoutDroppingFinal() {
        var simulatedTime = 0L
        val throttle = SyncProgressThrottle(minIntervalMs = 500L, clock = { simulatedTime })

        assertTrue("First progress update must be emitted", throttle.tryAcquire())

        simulatedTime += 200L
        assertFalse("Subsequent progress update within interval must be dropped", throttle.tryAcquire())

        simulatedTime += 300L
        assertTrue("Progress update at interval boundary must be emitted", throttle.tryAcquire())

        simulatedTime += 50L
        assertTrue("Forced progress update must always emit", throttle.tryAcquire(force = true))
    }

    companion object {
        private const val TEST_ALBUM_COUNT = 6
    }

    private class MockContext(private val values: Map<String, Any> = emptyMap()) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = MockPreferences(values)
    }

    private class MockPreferences(initial: Map<String, Any>) : SharedPreferences {
        private val storage = ConcurrentHashMap(initial)
        override fun getAll(): Map<String, *> = storage
        override fun getString(key: String, defValue: String?) = storage[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?) =
            @Suppress("UNCHECKED_CAST") (storage[key] as? Set<String> ?: defValues)
        override fun getInt(key: String, defValue: Int) = storage[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = storage[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = storage[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = storage[key] as? Boolean ?: defValue
        override fun contains(key: String) = storage.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun edit(): SharedPreferences.Editor = MockEditor(storage)
    }

    private class MockEditor(private val storage: MutableMap<String, Any>) : SharedPreferences.Editor {
        private val modifications = HashMap<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            modifications[key] = value
            return this
        }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            modifications[key] = values
            return this
        }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            modifications[key] = value
            return this
        }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            modifications[key] = value
            return this
        }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            modifications[key] = value
            return this
        }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            modifications[key] = value
            return this
        }
        override fun remove(key: String): SharedPreferences.Editor {
            modifications[key] = this
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clearRequested) storage.clear()
            modifications.forEach { (k, v) ->
                if (v === this) storage.remove(k)
                else if (v != null) storage[k] = v
                else storage.remove(k)
            }
        }
    }
}
